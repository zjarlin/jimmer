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

class JimmerDtoPolymorphismGoldenTest {

    @Test
    fun `apt jackson 2 polymorphism matches legacy golden`() {
        assertGolden("apt/jackson2/ClientInput.java", compileApt(jackson3 = false))
    }

    @Test
    fun `apt jackson 3 polymorphism matches legacy golden`() {
        assertGolden("apt/jackson3/ClientInput.java", compileApt(jackson3 = true))
    }

    @Test
    fun `ksp jackson 2 polymorphism matches legacy golden`() {
        assertGolden("ksp/jackson2/ClientInput.kt", compileKsp(jackson3 = false))
    }

    @Test
    fun `ksp jackson 3 polymorphism matches legacy golden`() {
        assertGolden("ksp/jackson3/ClientInput.kt", compileKsp(jackson3 = true))
    }

    private fun compileApt(jackson3: Boolean): ByteArray {
        val projectDir = fixtureProject("jimmer-dto-polymorphism-apt")
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
        assertDtoFileSet(packageDir, "ClientInput.java", "APT")
        return packageDir.resolve("ClientInput.java").readBytes()
    }

    private fun compileKsp(jackson3: Boolean): ByteArray {
        val projectDir = fixtureProject("jimmer-dto-polymorphism-ksp")
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
            moduleName = "jimmer-dto-polymorphism-golden"
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
        assertDtoFileSet(packageDir, "ClientInput.kt", "KSP")
        return packageDir.resolve("ClientInput.kt").readBytes()
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/Client.dto").also { file ->
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
        val resourcePath = "/dto/polymorphism/$path"
        val expected = javaClass.getResourceAsStream(resourcePath)?.use { stream -> stream.readBytes() }
        if (expected == null) {
            val outputFile = File(System.getProperty("java.io.tmpdir"), "jimmer-dto-polymorphism-golden/$path")
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
            "Client.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                public interface Client {
                    @Id
                    long id();

                    @Discriminator
                    String type();

                    String name();
                }
            """.trimIndent(),
            "Organization.java" to """
                package demo;

                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;

                @Entity
                @DiscriminatorValue("ORG")
                public interface Organization extends Client {
                    String taxCode();
                }
            """.trimIndent(),
            "Person.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;

                @Entity
                public interface Person extends Client {
                    String firstName();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "Client.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface Client {
                    @Id
                    val id: Long

                    @Discriminator
                    val type: String

                    val name: String
                }
            """.trimIndent(),
            "Organization.kt" to """
                package demo

                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity

                @Entity
                @DiscriminatorValue("ORG")
                interface Organization : Client {
                    val taxCode: String
                }
            """.trimIndent(),
            "Person.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity

                @Entity
                interface Person : Client {
                    val firstName: String
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            package demo.dto

            input ClientInput {
                id
                type as kind
                name
                #types {
                    default {
                    }
                    Organization {
                        taxCode
                    }
                    Person {
                        firstName
                    }
                }
            }
        """.trimIndent()
    }
}
