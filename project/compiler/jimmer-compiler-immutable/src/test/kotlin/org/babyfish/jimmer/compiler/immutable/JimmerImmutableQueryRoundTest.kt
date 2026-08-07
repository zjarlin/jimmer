package org.babyfish.jimmer.compiler.immutable

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
import javax.annotation.processing.AbstractProcessor
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerImmutableQueryRoundTest {

    @Test
    fun `apt root query observes subtype generated in a later round`() {
        val result = compileApt()

        assertTrue(result.success, result.diagnostics.errorMessage())
        assertTrue(
            result.diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            result.diagnostics.errorMessage(),
        )
        val rootRound = result.capture.roundOf(ROOT_TYPE)
        val intermediateRound = result.capture.roundOf(INTERMEDIATE_SUBTYPE)
        val subtypeRound = result.capture.roundOf(SUBTYPE)
        val serviceRound = result.capture.roundOf(SERVICE_TYPE)
        assertEquals(0, rootRound, result.capture.toString())
        assertTrue(rootRound < intermediateRound, result.capture.toString())
        assertTrue(intermediateRound < subtypeRound, result.capture.toString())
        assertEquals(subtypeRound, serviceRound, result.capture.toString())
        val tableFiles = result.generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.name == "RootTable.java" }
            .toList()
        assertEquals(1, tableFiles.size, "Root query source must be generated exactly once")
        val tableSource = tableFiles.single().readText()
        assertContains(tableSource, "implements RootProps, PolymorphicTable<Root>")
        assertContains(tableSource, " TT treatAs(")
        assertContains(tableSource, " TT tryTreatAs(")
        assertContains(tableSource, "Predicate instanceOf(")
        assertContains(tableSource, "Predicate exactType(")
        val fetcherFiles = result.generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.name == "RootFetcher.java" }
            .toList()
        assertEquals(1, fetcherFiles.size, "Root fetcher source must be generated exactly once")
        assertContains(fetcherFiles.single().readText(), "RootFetcher forType(")
        val clientResource = result.classesDir.resolve("META-INF/jimmer/client")
        assertTrue(clientResource.isFile, "Missing client resource: ${clientResource.absolutePath}")
        assertContains(clientResource.readText(), SERVICE_TYPE)
    }

    @Test
    fun `ksp root query observes subtype generated in a later round`() {
        val result = compileKsp()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.text())
        val rootRound = result.capture.roundOf(ROOT_TYPE)
        val intermediateRound = result.capture.roundOf(INTERMEDIATE_SUBTYPE)
        val subtypeRound = result.capture.roundOf(SUBTYPE)
        val serviceRound = result.capture.roundOf(SERVICE_TYPE)
        assertEquals(0, rootRound, result.capture.toString())
        assertTrue(rootRound < intermediateRound, result.capture.toString())
        assertTrue(intermediateRound < subtypeRound, result.capture.toString())
        assertEquals(subtypeRound, serviceRound, result.capture.toString())
        val propsFiles = result.kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.name == "RootProps.kt" }
            .toList()
        assertEquals(1, propsFiles.size, "Root query source must be generated exactly once")
        val propsSource = propsFiles.single().readText()
        assertContains(propsSource, "KPolymorphicTables")
        assertContains(propsSource, ".treatAs(")
        assertContains(propsSource, ".tryTreatAs(")
        assertContains(propsSource, ".instanceOf(")
        assertContains(propsSource, ".exactType(")
        val fetcherFiles = result.kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.name == "RootFetcher.kt" }
            .toList()
        assertEquals(1, fetcherFiles.size, "Root fetcher source must be generated exactly once")
        val fetcherSource = fetcherFiles.single().readText()
        assertContains(fetcherSource, "public fun <S : Root> forType(")
        assertContains(fetcherSource, "SpecialRootFetcherDsl")
        val clientResource = result.resourceOutputDir.resolve("META-INF/jimmer/client")
        assertTrue(clientResource.isFile, "Missing client resource: ${clientResource.absolutePath}")
        assertContains(clientResource.readText(), "demo.BookService")
    }

    private fun compileApt(): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-query-apt-round").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Root.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(JAVA_ROOT_SOURCE)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val capture = AptRoundCapture()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable query APT round tests")
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
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(
                listOf(
                    LaterAptSubtypeProcessor(capture),
                    JimmerProcessor(),
                )
            )
            task.call()
        }
        return AptCompilationResult(success, diagnostics, classesDir, generatedDir, capture)
    }

    private fun compileKsp(): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-query-ksp-round").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Root.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(KOTLIN_ROOT_SOURCE)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val capture = KspRoundCapture()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-query-ksp-round"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            this.resourceOutputDir = resourceOutputDir
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(
                JimmerProcessorProvider(),
                LaterKspSubtypeProvider(capture),
            ),
            logger,
        ).execute()
        return KspCompilationResult(exitCode, logger, kotlinOutputDir, resourceOutputDir, capture)
    }

    private class LaterAptSubtypeProcessor(
        private val capture: AptRoundCapture,
    ) : AbstractProcessor() {
        private var generationStep = 0

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            capture.record(roundEnvironment)
            if (roundEnvironment.processingOver()) {
                return false
            }
            when (generationStep++) {
                0 -> generate(INTERMEDIATE_SUBTYPE, JAVA_INTERMEDIATE_SUBTYPE_SOURCE)
                1 -> {
                    generate(SUBTYPE, JAVA_SUBTYPE_SOURCE)
                    generate(SERVICE_TYPE, JAVA_SERVICE_SOURCE)
                }
            }
            return false
        }

        private fun generate(
            qualifiedName: String,
            content: String,
        ) {
            processingEnv.filer.createSourceFile(qualifiedName).openWriter().use { writer ->
                writer.write(content)
            }
        }
    }

    private class LaterKspSubtypeProvider(
        private val capture: KspRoundCapture,
    ) : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                private var generationStep = 0

                override fun process(resolver: Resolver): List<KSAnnotated> {
                    capture.record(resolver)
                    val generatedFile = when (generationStep++) {
                        0 -> "IntermediateRoot" to KOTLIN_INTERMEDIATE_SUBTYPE_SOURCE
                        1 -> "SpecialRoot" to KOTLIN_SUBTYPE_SOURCE
                        else -> null
                    }
                    if (generatedFile != null) {
                        val sourceFiles = resolver.getAllFiles().toList().toTypedArray()
                        environment.codeGenerator.createNewFile(
                            dependencies = Dependencies(aggregating = true, *sourceFiles),
                            packageName = "demo",
                            fileName = generatedFile.first,
                            extensionName = "kt",
                        ).bufferedWriter().use { writer ->
                            writer.write(generatedFile.second)
                        }
                    }
                    return emptyList()
                }
            }
        }
    }

    private class AptRoundCapture {
        private val rootTypeNamesByRound = mutableListOf<Set<String>>()

        fun record(roundEnvironment: RoundEnvironment) {
            rootTypeNamesByRound += roundEnvironment.rootElements
                .filterIsInstance<TypeElement>()
                .mapTo(sortedSetOf()) { type -> type.qualifiedName.toString() }
        }

        fun roundOf(qualifiedName: String): Int {
            return rootTypeNamesByRound.indexOfFirst { rootTypeNames -> qualifiedName in rootTypeNames }
                .takeIf { round -> round >= 0 }
                ?: error("Type '$qualifiedName' did not become an APT round root: $this")
        }

        override fun toString(): String = rootTypeNamesByRound.withIndex().joinToString { (round, roots) ->
            "$round=${roots.sorted()}"
        }
    }

    private class KspRoundCapture {
        private val declarationNamesByRound = mutableListOf<Set<String>>()

        fun record(resolver: Resolver) {
            declarationNamesByRound += resolver.getNewFiles()
                .flatMap { file -> file.declarations }
                .filterIsInstance<KSDeclaration>()
                .mapNotNullTo(sortedSetOf()) { declaration -> declaration.qualifiedName?.asString() }
        }

        fun roundOf(qualifiedName: String): Int {
            return declarationNamesByRound.indexOfFirst { names -> qualifiedName in names }
                .takeIf { round -> round >= 0 }
                ?: error("Type '$qualifiedName' did not become a KSP round root: $this")
        }

        override fun toString(): String = declarationNamesByRound.withIndex().joinToString { (round, names) ->
            "$round=${names.sorted()}"
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
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class AptCompilationResult(
        val success: Boolean,
        val diagnostics: DiagnosticCollector<JavaFileObject>,
        val classesDir: File,
        val generatedDir: File,
        val capture: AptRoundCapture,
    )

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
        val kotlinOutputDir: File,
        val resourceOutputDir: File,
        val capture: KspRoundCapture,
    )

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
        const val ROOT_TYPE = "demo.Root"
        const val INTERMEDIATE_SUBTYPE = "demo.IntermediateRoot"
        const val SUBTYPE = "demo.SpecialRoot"
        const val SERVICE_TYPE = "demo.BookService"

        val JAVA_ROOT_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Discriminator;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.Inheritance;
            import org.babyfish.jimmer.sql.InheritanceType;

            @Entity
            @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
            public interface Root {
                @Id
                long id();

                @Discriminator
                String type();

                String name();
            }
        """.trimIndent()

        val JAVA_SUBTYPE_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Entity;

            @Entity
            @DiscriminatorValue("SPECIAL")
            public interface SpecialRoot extends Root {
                String specialName();
            }
        """.trimIndent()

        val JAVA_INTERMEDIATE_SUBTYPE_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Entity;

            @Entity
            @DiscriminatorValue("INTERMEDIATE")
            public interface IntermediateRoot extends Root {
                String intermediateName();
            }
        """.trimIndent()

        val JAVA_SERVICE_SOURCE = """
            package demo;

            import org.babyfish.jimmer.client.meta.Api;

            @Api
            public interface BookService {
                @Api
                String findName();
            }
        """.trimIndent()

        val KOTLIN_ROOT_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Discriminator
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.Inheritance
            import org.babyfish.jimmer.sql.InheritanceType

            @Entity
            @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
            interface Root {
                @Id
                val id: Long

                @Discriminator
                val type: String

                val name: String
            }
        """.trimIndent()

        val KOTLIN_SUBTYPE_SOURCE = """
            package demo

            import org.babyfish.jimmer.client.meta.Api
            import org.babyfish.jimmer.sql.DiscriminatorValue
            import org.babyfish.jimmer.sql.Entity

            @Entity
            @DiscriminatorValue("SPECIAL")
            interface SpecialRoot : Root {
                val specialName: String
            }

            @Api
            interface BookService {
                @Api
                fun findName(): String
            }
        """.trimIndent()

        val KOTLIN_INTERMEDIATE_SUBTYPE_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.DiscriminatorValue
            import org.babyfish.jimmer.sql.Entity

            @Entity
            @DiscriminatorValue("INTERMEDIATE")
            interface IntermediateRoot : Root {
                val intermediateName: String
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
