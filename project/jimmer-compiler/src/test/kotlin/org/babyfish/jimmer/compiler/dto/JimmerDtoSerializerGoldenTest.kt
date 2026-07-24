package org.babyfish.jimmer.compiler.dto

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

class JimmerDtoSerializerGoldenTest {

    @Test
    fun `apt jackson 2 serializer matches legacy golden`() {
        assertGolden("apt/jackson2/BookInput.java", compileApt(jackson3 = false))
    }

    @Test
    fun `apt jackson 3 serializer matches legacy golden`() {
        assertGolden("apt/jackson3/BookInput.java", compileApt(jackson3 = true))
    }

    @Test
    fun `ksp jackson 2 mutable serializer matches legacy golden`() {
        assertGolden("ksp/jackson2/BookInput.kt", compileKsp(jackson3 = false))
    }

    @Test
    fun `ksp jackson 3 mutable serializer matches legacy golden`() {
        assertGolden("ksp/jackson3/BookInput.kt", compileKsp(jackson3 = true))
    }

    private fun compileApt(jackson3: Boolean): ByteArray {
        val projectDir = fixtureProject("jimmer-dto-serializer-apt")
        val sourceDir = projectDir.resolve("src/main/java/demo")
        val sourceFiles = JAVA_SOURCES.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        writeDtoSource(projectDir)
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run DTO APT golden tests")
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-Ajimmer.jackson3=$jackson3",
                    "-classpath",
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertTrue(task.call(), diagnostics.toErrorMessage())
        }
        val packageDir = generatedDir.resolve("demo/dto")
        assertDtoFileSet(packageDir, "BookInput.java", "APT")
        return packageDir.resolve("BookInput.java").readBytes()
    }

    private fun compileKsp(jackson3: Boolean): ByteArray {
        val projectDir = fixtureProject("jimmer-dto-serializer-ksp")
        val sourceDir = projectDir.resolve("src/main/kotlin/demo")
        val sourceFiles = KOTLIN_SOURCES.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-serializer-golden"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            processorOptions = mapOf(
                "jimmer.jackson3" to jackson3.toString(),
                "jimmer.dto.mutable" to "true",
            )
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
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.text())
        val packageDir = kotlinOutputDir.resolve("demo/dto")
        assertDtoFileSet(packageDir, "BookInput.kt", "KSP")
        return packageDir.resolve("BookInput.kt").readBytes()
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(DTO_SOURCE)
        }
    }

    private fun assertDtoFileSet(packageDir: File, expectedName: String, platform: String) {
        val actualNames = packageDir.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .map(File::getName)
            .sorted()
        assertEquals(listOf(expectedName), actualNames, "$platform generated an unexpected DTO file set")
    }

    private fun assertGolden(path: String, actual: ByteArray) {
        val resourcePath = "/dto/serializer/$path"
        val expected = javaClass.getResourceAsStream(resourcePath)?.use { stream -> stream.readBytes() }
        if (expected == null) {
            val outputFile = File(System.getProperty("java.io.tmpdir"), "jimmer-dto-serializer-golden/$path")
            outputFile.parentFile.mkdirs()
            outputFile.writeBytes(actual)
            error("Missing golden resource: $resourcePath -> ${outputFile.absolutePath}")
        }
        assertContentEquals(expected, actual, resourcePath)
    }

    private fun runtimeClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
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

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "Point.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Embeddable;

                @Embeddable
                public interface Point {
                    long x();
                    long y();
                }
            """.trimIndent(),
            "Book.java" to """
                package demo;

                import com.fasterxml.jackson.annotation.JsonAlias;
                import java.math.BigDecimal;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.jspecify.annotations.Nullable;

                @Entity
                public interface Book {
                    @Id
                    long id();

                    boolean active();

                    @JsonAlias("base-name")
                    String name();

                    @Nullable
                    @JsonAlias("base-edition")
                    Integer edition();

                    BigDecimal price();

                    @Nullable
                    Point location();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "Point.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Point {
                    val x: Long
                    val y: Long
                }
            """.trimIndent(),
            "Book.kt" to """
                package demo

                import com.fasterxml.jackson.annotation.JsonAlias
                import java.math.BigDecimal
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Book {
                    @Id
                    val id: Long

                    val active: Boolean

                    @get:JsonAlias("base-name")
                    val name: String

                    @get:JsonAlias("base-edition")
                    val edition: Int?

                    val price: BigDecimal

                    val location: Point?
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            package demo.dto

            @com.fasterxml.jackson.databind.annotation.JsonNaming(
                value = com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy::class
            )
            @tools.jackson.databind.annotation.JsonNaming(
                value = tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy::class
            )
            dynamic input BookInput {
                fixed id?
                active as isEnabled
                @com.fasterxml.jackson.annotation.JsonAlias(value = ["dto-name"])
                static name?
                dynamic edition
                fuzzy price?
                location {
                    #allScalars?
                }
            }
        """.trimIndent()
    }
}
