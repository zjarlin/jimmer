package org.babyfish.jimmer.compiler.immutable

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
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
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class JimmerImmutableDraftCompilationTest {

    @Test
    fun `apt generated draft sources compile with javac`() {
        val result = processApt(JAVA_SOURCES)
        val modelDraft = result.generatedFiles
            .single { file -> file.name == "DraftModelDraft.java" }
            .readText()
        assertContains(modelDraft, "DraftModelDraft setName(String name)")
        assertContains(modelDraft, "AddressDraft address(boolean autoCreate)")
        assertContains(modelDraft, "List<ContactDraft> contacts(boolean autoCreate)")
        assertContains(modelDraft, "title must not be blank")

        compileWithJavac(
            projectDir = result.projectDir,
            sourceFiles = result.sourceFiles + result.generatedFiles,
        )
    }

    @Test
    fun `ksp generated draft sources compile with k2`() {
        val result = processKsp(
            projectPrefix = "jimmer-draft-ksp-compilation",
            moduleName = "jimmer-draft-ksp-compilation",
            sources = KOTLIN_SOURCES,
        )
        val modelDraft = result.generatedFiles
            .single { file -> file.name == "DraftModelDraft.kt" }
            .readText()
        assertContains(modelDraft, "override var name: String")
        assertContains(modelDraft, "public fun address(): AddressDraft")
        assertContains(modelDraft, "public fun contacts(): MutableList<ContactDraft>")
        assertContains(modelDraft, "title must not be blank")

        compileWithK2(
            projectDir = result.projectDir,
            sourceFiles = result.sourceFiles + result.generatedFiles,
        )
    }

    @Test
    fun `ksp escaped immutable type and property draft compiles with k2`() {
        val result = processKsp(
            projectPrefix = "jimmer-draft-ksp-escaped-compilation",
            moduleName = "jimmer-draft-ksp-escaped-compilation",
            sources = ESCAPED_KOTLIN_SOURCES,
        )
        val draftFiles = result.generatedFiles.filter { file -> file.name.endsWith("Draft.kt") }
        val escapedDraft = draftFiles
            .single { file -> file.name == "escaped-draftDraft.kt" }
            .readText()
        assertContains(escapedDraft, "public interface `Draft-ModelDraft`")
        assertContains(escapedDraft, "override var `display-name`: String")
        assertContains(escapedDraft, "override var `item-values`: List<String>")

        compileWithK2(
            projectDir = result.projectDir,
            sourceFiles = result.sourceFiles + draftFiles,
        )
    }

    private fun processApt(sources: Map<String, String>): AptProcessingResult {
        val projectDir = createTempDirectory(prefix = "jimmer-draft-apt-compilation").toFile()
        val sourceFiles = writeSources(
            sourceRoot = projectDir.resolve("src/main/java"),
            sources = sources,
        )
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable draft compilation tests")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(processingClassesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
        val generatedFiles = generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertTrue(generatedFiles.isNotEmpty(), "APT did not generate Java sources")
        return AptProcessingResult(projectDir, sourceFiles, generatedFiles)
    }

    private fun processKsp(
        projectPrefix: String,
        moduleName: String,
        sources: Map<String, String>,
    ): KspProcessingResult {
        val projectDir = createTempDirectory(prefix = projectPrefix).toFile()
        val sourceFiles = writeSources(
            sourceRoot = projectDir.resolve("src/main/kotlin"),
            sources = sources,
        )
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            this.moduleName = moduleName
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
        val generatedFiles = kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertTrue(generatedFiles.isNotEmpty(), "KSP did not generate Kotlin sources")
        return KspProcessingResult(projectDir, sourceFiles, generatedFiles)
    }

    private fun compileWithJavac(
        projectDir: File,
        sourceFiles: List<File>,
    ) {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable draft compilation tests")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:none", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            ).call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
    }

    private fun compileWithK2(
        projectDir: File,
        sourceFiles: List<File>,
    ) {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val arguments = buildList {
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("17")
            add("-classpath")
            add(runtimeClasspathText())
            add("-d")
            add(classesDir.absolutePath)
            sourceFiles.mapTo(this) { file -> file.absolutePath }
        }
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(stream, *arguments.toTypedArray())
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private fun writeSources(
        sourceRoot: File,
        sources: Map<String, String>,
    ): List<File> {
        return sources.map { (relativePath, content) ->
            sourceRoot.resolve(relativePath).apply {
                parentFile.mkdirs()
                writeText(content)
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
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class AptProcessingResult(
        val projectDir: File,
        val sourceFiles: List<File>,
        val generatedFiles: List<File>,
    )

    private data class KspProcessingResult(
        val projectDir: File,
        val sourceFiles: List<File>,
        val generatedFiles: List<File>,
    )

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "demo/Address.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Embeddable;

                @Embeddable
                public interface Address {
                    String city();
                }
            """.trimIndent(),
            "demo/Contact.java" to """
                package demo;

                import org.babyfish.jimmer.Immutable;

                @Immutable
                public interface Contact {
                    String label();
                }
            """.trimIndent(),
            "demo/DraftModel.java" to """
                package demo;

                import java.util.List;
                import javax.validation.constraints.NotBlank;
                import org.babyfish.jimmer.Immutable;

                @Immutable
                public interface DraftModel {
                    @NotBlank(message = "title must not be blank")
                    String name();

                    Address address();

                    Contact contact();

                    List<Contact> contacts();

                    List<String> tags();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "demo/Address.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Address {
                    val city: String
                }
            """.trimIndent(),
            "demo/Contact.kt" to """
                package demo

                import org.babyfish.jimmer.Immutable

                @Immutable
                interface Contact {
                    val label: String
                }
            """.trimIndent(),
            "demo/DraftModel.kt" to """
                package demo

                import javax.validation.constraints.NotBlank
                import org.babyfish.jimmer.Immutable

                @Immutable
                interface DraftModel {
                    @get:NotBlank(message = "title must not be blank")
                    val name: String

                    val address: Address

                    val contact: Contact

                    val contacts: List<Contact>

                    val tags: List<String?>
                }
            """.trimIndent(),
        )

        val ESCAPED_KOTLIN_SOURCES = linkedMapOf(
            "demo/escaped-draft.kt" to """
                package demo

                import org.babyfish.jimmer.Immutable

                @Immutable
                interface `Draft-Model` {
                    val `display-name`: String

                    val `item-values`: List<String>
                }
            """.trimIndent(),
        )

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }

        fun runtimeClasspathText(): String = runtimeClasspath().joinToString(File.pathSeparator)
    }
}
