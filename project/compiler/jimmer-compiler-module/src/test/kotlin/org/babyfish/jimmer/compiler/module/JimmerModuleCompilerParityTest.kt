package org.babyfish.jimmer.compiler.module

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
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

class JimmerModuleCompilerParityTest {

    @Test
    fun `real apt and ksp processors match module byte goldens`() {
        val apt = compileApt()
        val ksp = compileKsp()

        assertTrue(apt.success, apt.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, ksp.exitCode, ksp.logger.text())

        APT_SOURCE_PATHS.forEach { path ->
            assertContentEquals(golden("apt/${path.substringAfterLast('/')}"), apt.source(path))
        }
        assertContentEquals(golden("resources/entities.txt"), apt.resource(ENTITIES_RESOURCE_PATH))
        assertContentEquals(byteArrayOf(), apt.resource(IMMUTABLES_RESOURCE_PATH))
        assertContentEquals(golden("ksp/JimmerModule.kt"), ksp.source(KSP_SOURCE_PATH))
        assertContentEquals(golden("resources/entities.txt"), ksp.resource(ENTITIES_RESOURCE_PATH))
    }

    private fun compileApt(): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-module-apt-parity").toFile()
        val sourceFiles = JAVA_SOURCES.map { (path, content) ->
            projectDir.resolve("src/main/java/$path").apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Module parity tests require a JDK compiler")
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
                    "-Ajimmer.source.includes=demo.",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        return AptCompilationResult(success, diagnostics, generatedDir, classesDir)
    }

    private fun compileKsp(): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-module-ksp-parity").toFile()
        val sourceFiles = KOTLIN_SOURCES.map { (path, content) ->
            projectDir.resolve("src/main/kotlin/$path").apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-module-parity"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            this.resourceOutputDir = resourceOutputDir
            processorOptions = mapOf(MODULE_REQUIRED_OPTION to "true")
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        return KspCompilationResult(exitCode, logger, kotlinOutputDir, resourceOutputDir)
    }

    private fun golden(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream("/module/$path")).use { input ->
            input.readBytes()
        }
    }

    private fun runtimeClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::exists)
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
        val generatedDir: File,
        val classesDir: File,
    ) {
        fun source(path: String): ByteArray {
            val file = generatedDir.resolve(path)
            assertTrue(file.isFile, "Missing generated APT module source: ${file.absolutePath}")
            return file.readBytes()
        }

        fun resource(path: String): ByteArray {
            val file = classesDir.resolve(path)
            assertTrue(file.isFile, "Missing generated APT module resource: ${file.absolutePath}")
            return file.readBytes()
        }
    }

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
        val kotlinOutputDir: File,
        val resourceOutputDir: File,
    ) {
        fun source(path: String): ByteArray {
            val file = kotlinOutputDir.resolve(path)
            assertTrue(file.isFile, "Missing generated KSP module source: ${file.absolutePath}\n${logger.text()}")
            return file.readBytes()
        }

        fun resource(path: String): ByteArray {
            val file = resourceOutputDir.resolve(path)
            assertTrue(file.isFile, "Missing generated KSP module resource: ${file.absolutePath}\n${logger.text()}")
            return file.readBytes()
        }
    }

    private companion object {
        const val MODULE_REQUIRED_OPTION = "jimmer.immutable.isModuleRequired"
        const val ENTITIES_RESOURCE_PATH = "META-INF/jimmer/entities"
        const val IMMUTABLES_RESOURCE_PATH = "META-INF/jimmer/immutables"
        const val KSP_SOURCE_PATH = "demo/JimmerModule.kt"
        val APT_SOURCE_PATHS = listOf(
            "demo/Immutables.java",
            "demo/Tables.java",
            "demo/TableExes.java",
            "demo/Fetchers.java",
        )
        val JAVA_SOURCES = linkedMapOf(
            "demo/alpha/Book.java" to javaBookSource("demo.alpha"),
            "demo/beta/Book.java" to javaBookSource("demo.beta"),
        )
        val KOTLIN_SOURCES = linkedMapOf(
            "demo/alpha/Book.kt" to kotlinBookSource("demo.alpha"),
            "demo/beta/Book.kt" to kotlinBookSource("demo.beta"),
        )

        fun javaBookSource(packageName: String): String = """
            package $packageName;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Entity
            public interface Book {
                @Id
                long id();
            }
        """.trimIndent()

        fun kotlinBookSource(packageName: String): String = """
            package $packageName

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Book {
                @Id
                val id: Long
            }
        """.trimIndent()
    }
}
