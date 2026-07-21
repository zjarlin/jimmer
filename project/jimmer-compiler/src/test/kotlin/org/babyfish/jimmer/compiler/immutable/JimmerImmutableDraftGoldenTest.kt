package org.babyfish.jimmer.compiler.immutable

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

class JimmerImmutableDraftGoldenTest {

    @Test
    fun `apt basic draft matches legacy golden`() {
        assertGoldens("apt/basic", compileApt(BASIC_APT_FIXTURE))
    }

    @Test
    fun `ksp basic draft matches legacy golden`() {
        assertGoldens("ksp/basic", compileKsp(BASIC_KSP_FIXTURE))
    }

    @Test
    fun `apt mapped superclass override draft matches legacy golden`() {
        assertGoldens("apt/override", compileApt(OVERRIDE_APT_FIXTURE))
    }

    @Test
    fun `ksp mapped superclass override draft matches legacy golden`() {
        assertGoldens("ksp/override", compileKsp(OVERRIDE_KSP_FIXTURE))
    }

    private fun compileApt(fixture: DraftFixture): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-draft-apt-golden").toFile()
        val sourceDir = projectDir.resolve("src/main/java/demo")
        val sourceFiles = fixture.sources.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable Draft APT golden tests")
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
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
            task.setProcessors(listOf(JimmerProcessor()))
            assertTrue(task.call(), diagnostics.toErrorMessage())
        }
        return fixture.generatedFiles.associateWith { name ->
            val generatedFile = generatedDir.resolve("demo/$name")
            assertTrue(generatedFile.isFile, "APT Draft output is missing: ${generatedFile.absolutePath}")
            generatedFile.readBytes()
        }
    }

    private fun compileKsp(fixture: DraftFixture): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-draft-ksp-golden").toFile()
        val sourceDir = projectDir.resolve("src/main/kotlin/demo")
        val sourceFiles = fixture.sources.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-draft-ksp-golden"
            sourceRoots = sourceFiles
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
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.text())
        return fixture.generatedFiles.associateWith { name ->
            val generatedFile = kotlinOutputDir.resolve("demo/$name")
            assertTrue(generatedFile.isFile, "KSP Draft output is missing: ${generatedFile.absolutePath}")
            generatedFile.readBytes()
        }
    }

    private fun assertGoldens(
        platformFixture: String,
        actualFiles: Map<String, ByteArray>,
    ) {
        val missing = mutableListOf<String>()
        actualFiles.forEach { (name, actual) ->
            val resourcePath = "/immutable/draft/$platformFixture/$name"
            val expected = javaClass.getResourceAsStream(resourcePath)?.use { stream -> stream.readBytes() }
            if (expected == null) {
                val outputFile = File(
                    System.getProperty("java.io.tmpdir"),
                    "jimmer-immutable-draft-golden/$platformFixture/$name",
                )
                outputFile.parentFile.mkdirs()
                outputFile.writeBytes(actual)
                missing += "$resourcePath -> ${outputFile.absolutePath}"
            } else {
                assertContentEquals(expected, actual, resourcePath)
            }
        }
        assertTrue(missing.isEmpty(), "Missing golden resources:\n${missing.joinToString("\n")}")
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

    private data class DraftFixture(
        val sources: Map<String, String>,
        val generatedFiles: List<String>,
    )

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val BASIC_APT_FIXTURE = DraftFixture(
            sources = mapOf(
                "BasicBook.java" to """
                    package demo;

                    import org.babyfish.jimmer.sql.Entity;
                    import org.babyfish.jimmer.sql.Id;

                    @Entity
                    public interface BasicBook {
                        @Id
                        long id();

                        String name();
                    }
                """.trimIndent(),
            ),
            generatedFiles = listOf("BasicBookDraft.java"),
        )

        val BASIC_KSP_FIXTURE = DraftFixture(
            sources = mapOf(
                "BasicBook.kt" to """
                    package demo

                    import org.babyfish.jimmer.sql.Entity
                    import org.babyfish.jimmer.sql.Id

                    @Entity
                    interface BasicBook {
                        @Id
                        val id: Long

                        val name: String
                    }
                """.trimIndent(),
            ),
            generatedFiles = listOf("BasicBookDraft.kt"),
        )

        val OVERRIDE_APT_FIXTURE = DraftFixture(
            sources = linkedMapOf(
                "BaseOnlyOneSwitch.java" to """
                    package demo;

                    import org.babyfish.jimmer.sql.Default;
                    import org.babyfish.jimmer.sql.MappedSuperclass;

                    @MappedSuperclass
                    public interface BaseOnlyOneSwitch {
                        @Default("0")
                        int status();
                    }
                """.trimIndent(),
                "OverrideBook.java" to """
                    package demo;

                    import org.babyfish.jimmer.sql.Default;
                    import org.babyfish.jimmer.sql.Entity;
                    import org.babyfish.jimmer.sql.Id;

                    @Entity
                    public interface OverrideBook extends BaseOnlyOneSwitch {
                        @Id
                        long id();

                        @Override
                        @Default("1")
                        int status();
                    }
                """.trimIndent(),
            ),
            generatedFiles = listOf(
                "BaseOnlyOneSwitchDraft.java",
                "OverrideBookDraft.java",
            ),
        )

        val OVERRIDE_KSP_FIXTURE = DraftFixture(
            sources = linkedMapOf(
                "BaseOnlyOneSwitch.kt" to """
                    package demo

                    import org.babyfish.jimmer.sql.Default
                    import org.babyfish.jimmer.sql.MappedSuperclass

                    @MappedSuperclass
                    interface BaseOnlyOneSwitch {
                        @Default("0")
                        val status: Int
                    }
                """.trimIndent(),
                "OverrideBook.kt" to """
                    package demo

                    import org.babyfish.jimmer.sql.Default
                    import org.babyfish.jimmer.sql.Entity
                    import org.babyfish.jimmer.sql.Id

                    @Entity
                    interface OverrideBook : BaseOnlyOneSwitch {
                        @Id
                        val id: Long

                        @Default("1")
                        override val status: Int
                    }
                """.trimIndent(),
            ),
            generatedFiles = listOf(
                "BaseOnlyOneSwitchDraft.kt",
                "OverrideBookDraft.kt",
            ),
        )

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
