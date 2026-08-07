package org.babyfish.jimmer.compiler.exportdoc

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class ExportDocCompilerParityTest {

    @Test
    fun `apt and ksp write identical deterministic export docs`() {
        val aptResult = compileApt(STATIC_JAVA_SOURCES)
        val kspResult = compileKsp(STATIC_KOTLIN_SOURCES)

        assertTrue(aptResult.success, aptResult.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, kspResult.exitCode, kspResult.logger.text())
        val aptBytes = aptResult.docBytes()
        val kspBytes = kspResult.docBytes()

        assertMigrationGolden("apt", aptBytes)
        assertMigrationGolden("ksp", kspBytes)
        assertContentEquals(aptBytes, kspBytes)
        assertNoTimestamp(aptBytes)
        val docs = aptBytes.loadProperties()
        assertEquals(STATIC_EXPECTED_DOCS, docs)
        assertFalse(DESCRIPTION_ONLY_KEY in docs)
    }

    @Test
    fun `package and file markers apply to types generated in a later round`() {
        val aptCapture = AptRoundCapture()
        val kspCapture = KspRoundCapture()
        val aptResult = compileApt(
            sources = mapOf("demo/package-info.java" to JAVA_PACKAGE_MARKER),
            additionalProcessors = listOf(LaterAptExportedTypeProcessor(aptCapture)),
        )
        val kspResult = compileKsp(
            sources = mapOf("demo/package.kt" to KOTLIN_FILE_MARKER),
            additionalProviders = listOf(LaterKspExportedTypeProvider(kspCapture)),
        )

        assertTrue(aptResult.success, aptResult.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, kspResult.exitCode, kspResult.logger.text())
        assertTrue(aptCapture.rounds.first().isEmpty(), aptCapture.toString())
        assertTrue(aptCapture.rounds.drop(1).any { names -> GENERATED_TYPE_NAME in names }, aptCapture.toString())
        assertTrue(kspCapture.rounds.first().isEmpty(), kspCapture.toString())
        assertTrue(kspCapture.rounds.drop(1).any { names -> GENERATED_TYPE_NAME in names }, kspCapture.toString())

        val aptBytes = aptResult.docBytes()
        val kspBytes = kspResult.docBytes()
        assertContentEquals(aptBytes, kspBytes)
        assertNoTimestamp(aptBytes)
        assertEquals(GENERATED_EXPECTED_DOCS, aptBytes.loadProperties())
    }

    @Test
    fun `non exportable enclosing declarations stop nested traversal`() {
        val aptResult = compileApt(NON_EXPORTABLE_ENCLOSING_JAVA_SOURCES)
        val kspResult = compileKsp(NON_EXPORTABLE_ENCLOSING_KOTLIN_SOURCES)

        assertTrue(aptResult.success, aptResult.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, kspResult.exitCode, kspResult.logger.text())
        assertFalse(aptResult.hasDocResource())
        assertFalse(kspResult.hasDocResource())
    }

    private fun compileApt(
        sources: Map<String, String>,
        additionalProcessors: List<Processor> = emptyList(),
    ): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-export-doc-apt").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val sourceFiles = sources.map { (path, content) ->
            sourceDir.resolve(path).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(additionalProcessors + JimmerProcessor())
            task.call()
        }
        return AptCompilationResult(
            success = success,
            diagnostics = diagnostics,
            classesDir = classesDir,
        )
    }

    private fun compileKsp(
        sources: Map<String, String>,
        additionalProviders: List<SymbolProcessorProvider> = emptyList(),
    ): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-export-doc-ksp").toFile()
        val sourceDir = projectDir.resolve("src/main/kotlin")
        val sourceFiles = sources.map { (path, content) ->
            sourceDir.resolve(path).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-export-doc-parity"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            this.resourceOutputDir = resourceOutputDir
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val providers = listOf(JimmerProcessorProvider()) + additionalProviders
        val exitCode = KotlinSymbolProcessing(configuration, providers, logger).execute()
        return KspCompilationResult(
            exitCode = exitCode,
            logger = logger,
            resourceOutputDir = resourceOutputDir,
        )
    }

    private class LaterAptExportedTypeProcessor(
        private val capture: AptRoundCapture,
    ) : AbstractProcessor() {
        private var generated = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            capture.record(roundEnvironment)
            if (generated || roundEnvironment.processingOver()) {
                return false
            }
            processingEnv.filer.createSourceFile(GENERATED_TYPE_NAME).openWriter().use { writer ->
                writer.write(GENERATED_JAVA_SOURCE)
            }
            generated = true
            return false
        }
    }

    private class LaterKspExportedTypeProvider(
        private val capture: KspRoundCapture,
    ) : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                private var generated = false

                override fun process(resolver: Resolver): List<KSAnnotated> {
                    capture.record(resolver)
                    if (generated) {
                        return emptyList()
                    }
                    val sourceFiles = resolver.getAllFiles().toList().toTypedArray()
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies(aggregating = true, *sourceFiles),
                        packageName = "demo",
                        fileName = "GeneratedBook",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(GENERATED_KOTLIN_SOURCE)
                    }
                    generated = true
                    return emptyList()
                }
            }
        }
    }

    private class AptRoundCapture {
        val rounds = mutableListOf<Set<String>>()

        fun record(roundEnvironment: RoundEnvironment) {
            rounds += roundEnvironment.rootElements
                .filterIsInstance<TypeElement>()
                .mapTo(sortedSetOf()) { type -> type.qualifiedName.toString() }
        }

        override fun toString(): String = rounds.withIndex().joinToString { (round, names) ->
            "$round=${names.joinToString(prefix = "[", postfix = "]")}"
        }
    }

    private class KspRoundCapture {
        val rounds = mutableListOf<Set<String>>()

        fun record(resolver: Resolver) {
            rounds += resolver.getNewFiles()
                .flatMap { file -> file.declarations }
                .filterIsInstance<KSDeclaration>()
                .mapNotNullTo(sortedSetOf()) { declaration -> declaration.qualifiedName?.asString() }
        }

        override fun toString(): String = rounds.withIndex().joinToString { (round, names) ->
            "$round=${names.joinToString(prefix = "[", postfix = "]")}"
        }
    }

    private class CapturingKspLogger : KSPLogger {
        private val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun exception(e: Throwable) {
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class AptCompilationResult(
        val success: Boolean,
        val diagnostics: DiagnosticCollector<JavaFileObject>,
        val classesDir: File,
    ) {
        fun hasDocResource(): Boolean = classesDir.resolve(DOC_RESOURCE_PATH).isFile

        fun docBytes(): ByteArray {
            val file = classesDir.resolve(DOC_RESOURCE_PATH)
            assertTrue(file.isFile, "Missing ExportDoc resource: ${file.absolutePath}")
            return file.readBytes()
        }
    }

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
        val resourceOutputDir: File,
    ) {
        fun hasDocResource(): Boolean = resourceOutputDir.resolve(DOC_RESOURCE_PATH).isFile

        fun docBytes(): ByteArray {
            val file = resourceOutputDir.resolve(DOC_RESOURCE_PATH)
            assertTrue(file.isFile, "Missing ExportDoc resource: ${file.absolutePath}\n${logger.text()}")
            return file.readBytes()
        }
    }

    private fun ByteArray.loadProperties(): Map<String, String> {
        val properties = Properties()
        inputStream().reader(StandardCharsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        return properties.stringPropertyNames()
            .sorted()
            .associateWith { name -> requireNotNull(properties.getProperty(name)) }
    }

    private fun assertMigrationGolden(platform: String, actual: ByteArray) {
        val resourcePath = "/exportdoc/$platform/doc.properties"
        val expected = requireNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Missing ExportDoc migration golden: $resourcePath"
        }.use { stream -> stream.readBytes() }
        assertContentEquals(expected, actual, "ExportDoc $platform resource differs from migration golden")
    }

    private fun assertNoTimestamp(bytes: ByteArray) {
        val content = String(bytes, StandardCharsets.UTF_8)
        assertFalse(
            content.lineSequence().any(PROPERTIES_TIMESTAMP::matches),
            "ExportDoc resource contains a Properties.store timestamp:\n$content",
        )
    }

    private fun DiagnosticCollector<JavaFileObject>.errorMessage(): String {
        return diagnostics.joinToString(separator = "\n") { diagnostic ->
            val source = diagnostic.source?.name.orEmpty()
            val position = if (diagnostic.lineNumber > 0) {
                "${diagnostic.lineNumber}:${diagnostic.columnNumber}"
            } else {
                "?:?"
            }
            "${diagnostic.kind} $source:$position ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        const val DOC_RESOURCE_PATH = "META-INF/jimmer/doc.properties"
        const val GENERATED_TYPE_NAME = "demo.GeneratedBook"
        const val DESCRIPTION_ONLY_KEY = "demo.Book.descriptionOnly"

        val PROPERTIES_TIMESTAMP = Regex(
            "^#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) " +
                "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) " +
                "\\d{2} \\d{2}:\\d{2}:\\d{2} .+ \\d{4}$",
        )

        val STATIC_EXPECTED_DOCS = mapOf(
            "demo.Book" to "Book documentation.",
            "demo.Book.id" to "Identifier documentation.",
            "demo.Book.name" to "Name documentation.",
        )

        val GENERATED_EXPECTED_DOCS = mapOf(
            "demo.GeneratedBook" to "Generated book documentation.",
            "demo.GeneratedBook.id" to "Generated identifier documentation.",
            "demo.GeneratedBook.name" to "Generated name documentation.",
        )

        val JAVA_PACKAGE_MARKER = """
            @ExportDoc
            package demo;

            import org.babyfish.jimmer.client.ExportDoc;
        """.trimIndent()

        val KOTLIN_FILE_MARKER = """
            @file:ExportDoc

            package demo

            import org.babyfish.jimmer.client.ExportDoc
        """.trimIndent()

        val STATIC_JAVA_SOURCES = mapOf(
            "demo/package-info.java" to JAVA_PACKAGE_MARKER,
            "demo/Book.java" to """
                package demo;

                import org.babyfish.jimmer.client.Description;

                /** Book documentation. */
                public class Book {

                    /** Identifier documentation. */
                    private long id;

                    /** Name documentation. */
                    public String getName() {
                        return "";
                    }

                    @Description("Description only documentation.")
                    public String getDescriptionOnly() {
                        return "";
                    }
                }
            """.trimIndent(),
        )

        val STATIC_KOTLIN_SOURCES = mapOf(
            "demo/Book.kt" to """
                @file:ExportDoc

                package demo

                import org.babyfish.jimmer.client.Description
                import org.babyfish.jimmer.client.ExportDoc

                /** Book documentation. */
                class Book {

                    /** Identifier documentation. */
                    private val id: Long = 0L

                    /** Name documentation. */
                    val name: String = ""

                    @Description("Description only documentation.")
                    val descriptionOnly: String = ""
                }
            """.trimIndent(),
        )

        val NON_EXPORTABLE_ENCLOSING_JAVA_SOURCES = mapOf(
            "demo/package-info.java" to JAVA_PACKAGE_MARKER,
            "demo/Holder.java" to """
                package demo;

                public @interface Holder {
                    class Nested {}
                }
            """.trimIndent(),
        )

        val NON_EXPORTABLE_ENCLOSING_KOTLIN_SOURCES = mapOf(
            "demo/Holder.kt" to """
                @file:ExportDoc

                package demo

                import org.babyfish.jimmer.client.ExportDoc

                object Holder {
                    class Nested
                }
            """.trimIndent(),
        )

        val GENERATED_JAVA_SOURCE = """
            package demo;

            /** Generated book documentation. */
            public class GeneratedBook {

                /** Generated identifier documentation. */
                private long id;

                /** Generated name documentation. */
                public String getName() {
                    return "";
                }
            }
        """.trimIndent()

        val GENERATED_KOTLIN_SOURCE = """
            package demo

            /** Generated book documentation. */
            class GeneratedBook {

                /** Generated identifier documentation. */
                private val id: Long = 0L

                /** Generated name documentation. */
                val name: String = ""
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }
    }
}
