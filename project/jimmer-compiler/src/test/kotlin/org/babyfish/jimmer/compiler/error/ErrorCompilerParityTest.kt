package org.babyfish.jimmer.compiler.error

import site.addzero.lsi.jimmer.toJimmerLsiFrontendOptions

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import site.addzero.lsi.apt.toLsiWorkspace
import site.addzero.lsi.ksp.toLsiWorkspace
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.error.fingerprint
import site.addzero.lsi.jimmer.error.normalizedSnapshot
import site.addzero.lsi.jimmer.error.toErrorSchema
import site.addzero.lsi.model.LsiWorkspace

class ErrorCompilerParityTest {

    @Test
    fun `real apt and ksp processors produce equivalent error semantics and sources`() {
        val apt = compileApt(JAVA_SOURCE)
        val ksp = compileKsp(KOTLIN_SOURCE)

        assertTrue(apt.success, apt.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, ksp.exitCode, ksp.logger.text())

        val aptSchema = apt.sourceWorkspace().toErrorSchema()
        val kspSchema = ksp.sourceWorkspace().toErrorSchema()
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())

        assertContentEquals(
            goldenBytes("apt/BookException.java"),
            apt.generatedSourceBytes(),
            "apt/BookException.java",
        )
        assertContentEquals(
            goldenBytes("ksp/BookException.kt"),
            ksp.generatedSourceBytes(),
            "ksp/BookException.kt",
        )
    }

    private fun compileApt(source: String): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-error-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/BookErrorCode.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val capture = CapturingAptProcessor()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Error compiler parity tests require a JDK compiler")
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
                    runtimeClasspath().joinToString(File.pathSeparator),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(capture, JimmerProcessor()))
            task.call()
        }
        return AptCompilationResult(
            success = success,
            diagnostics = diagnostics,
            workspaces = capture.workspaces,
            generatedDir = generatedDir,
        )
    }

    private fun compileKsp(source: String): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-error-ksp-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/BookErrorCode.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val capture = CapturingKspProvider()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-error-parity"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider(), capture),
            logger,
        ).execute()
        return KspCompilationResult(
            exitCode = exitCode,
            logger = logger,
            workspaces = capture.workspaces,
            kotlinOutputDir = kotlinOutputDir,
        )
    }

    private class CapturingAptProcessor : AbstractProcessor() {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (!roundEnvironment.processingOver()) {
                workspaces += roundEnvironment.toLsiWorkspace(
                    processingEnv,
                    processingEnv.options.toJimmerLsiFrontendOptions(),
                )
            }
            return false
        }
    }

    private class CapturingKspProvider : SymbolProcessorProvider {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    workspaces += resolver.toLsiWorkspace(environment.options.toJimmerLsiFrontendOptions())
                    return emptyList()
                }
            }
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
        val workspaces: List<LsiWorkspace>,
        val generatedDir: File,
    ) {
        fun sourceWorkspace(): LsiWorkspace {
            return workspaces.first { workspace ->
                workspace.declarations.any { declaration -> declaration.id == FAMILY_ID }
            }
        }

        fun generatedSourceBytes(): ByteArray {
            val file = generatedDir.resolve(JAVA_GENERATED_PATH)
            assertTrue(file.isFile, "Missing generated Error source: ${file.absolutePath}")
            return file.readBytes()
        }
    }

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
        val workspaces: List<LsiWorkspace>,
        val kotlinOutputDir: File,
    ) {
        fun sourceWorkspace(): LsiWorkspace {
            return workspaces.first { workspace ->
                workspace.declarations.any { declaration -> declaration.id == FAMILY_ID }
            }
        }

        fun generatedSourceBytes(): ByteArray {
            val file = kotlinOutputDir.resolve(KOTLIN_GENERATED_PATH)
            assertTrue(file.isFile, "Missing generated Error source: ${file.absolutePath}\n${logger.text()}")
            return file.readBytes()
        }
    }

    private fun goldenBytes(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream("/error/$path")).use { stream ->
            stream.readBytes()
        }
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
        val FAMILY_ID = LsiSymbolId.type("demo.BookErrorCode")
        const val JAVA_GENERATED_PATH = "demo/BookException.java"
        const val KOTLIN_GENERATED_PATH = "demo/BookException.kt"

        val JAVA_SOURCE = """
            package demo;

            import java.time.LocalDateTime;
            import org.babyfish.jimmer.error.ErrorFamily;
            import org.babyfish.jimmer.error.ErrorField;

            /** Book errors. */
            @ErrorFamily("BOOK")
            @ErrorField(name = "timestamp", type = LocalDateTime.class, doc = "Created time")
            public enum BookErrorCode {

                /** Out of range. */
                @ErrorField(name = "min", type = int.class)
                @ErrorField(name = "label", type = String.class)
                @ErrorField(name = "primitiveValues", type = int[].class)
                @ErrorField(name = "boxedValues", type = Integer[].class)
                OUT_OF_RANGE
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import java.time.LocalDateTime
            import org.babyfish.jimmer.error.ErrorFamily
            import org.babyfish.jimmer.error.ErrorField

            /** Book errors. */
            @ErrorFamily("BOOK")
            @ErrorField(name = "timestamp", type = LocalDateTime::class, doc = "Created time")
            enum class BookErrorCode {

                /** Out of range. */
                @ErrorField(name = "min", type = Int::class)
                @ErrorField(name = "label", type = String::class)
                @ErrorField(name = "primitiveValues", type = IntArray::class)
                @ErrorField(name = "boxedValues", type = Array<Int>::class)
                OUT_OF_RANGE
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
