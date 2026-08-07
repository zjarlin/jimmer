package org.babyfish.jimmer.compiler.dto

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

class JimmerDtoInheritedEnumCompilationTest {

    @Test
    fun `apt maps inherited enum property to scalar with bidirectional conversion`() {
        val source = compileApt()

        assertContains(source, "Integer stateCode;")
        assertContains(source, "public int getStateCode()")
        assertContains(source, "switch ((SwitchState)arg)")
        assertContains(source, "case ENABLED:")
        assertContains(source, "return 1;")
        assertContains(source, "case DISABLED:")
        assertContains(source, "return 0;")
        assertContains(source, "switch ((int)arg)")
        assertContains(source, "return SwitchState.ENABLED;")
        assertContains(source, "return SwitchState.DISABLED;")
        assertContains(source, "STATE_CODE_ACCESSOR.get(base)")
        assertContains(source, "STATE_CODE_ACCESSOR.set(__draft, this.stateCode)")
    }

    @Test
    fun `ksp maps inherited enum property to scalar with bidirectional conversion`() {
        val source = compileKsp()

        assertContains(source, "public var stateCode: Int")
        assertContains(source, "when (it as SwitchState)")
        assertContains(source, "SwitchState.ENABLED -> 1")
        assertContains(source, "SwitchState.DISABLED -> 0")
        assertContains(source, "when (it as Int)")
        assertContains(source, "1 -> SwitchState.ENABLED")
        assertContains(source, "0 -> SwitchState.DISABLED")
        assertContains(source, "STATE_CODE_ACCESSOR.get<Int>(base)")
        assertContains(source, "STATE_CODE_ACCESSOR.set(_draft, stateCode)")
    }

    private fun compileApt(): String {
        val projectDir = fixtureProject("jimmer-dto-inherited-enum-apt")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/java"),
            JAVA_SOURCES,
        )
        writeDtoSource(projectDir)
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("继承枚举 DTO 的 APT 测试需要 JDK 编译器")
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
                listOf(
                    "-proc:only",
                    "-classpath",
                    runtimeClasspathText(),
                    "-Ajimmer.dto.mutable=true",
                ),
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
        compileWithJavac(projectDir, sourceFiles + generatedFiles)
        return generatedDir.resolve("demo/dto/ChildSwitchInput.java").readText()
    }

    private fun compileKsp(): String {
        val projectDir = fixtureProject("jimmer-dto-inherited-enum-ksp")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/kotlin"),
            KOTLIN_SOURCES,
        )
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-inherited-enum-compilation"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            processorOptions = mapOf("jimmer.dto.mutable" to "true")
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val processingExitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, processingExitCode, logger.text())
        val generatedFiles = kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        compileWithK2(projectDir, sourceFiles + generatedFiles)
        return kotlinOutputDir.resolve("demo/dto/ChildSwitchInput.kt").readText()
    }

    private fun compileWithJavac(
        projectDir: File,
        sourceFiles: List<File>,
    ) {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("继承枚举 DTO 的 APT 测试需要 JDK 编译器")
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
        sourceDir: File,
        sources: Map<String, String>,
    ): List<File> {
        return sources.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/ChildSwitch.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(DTO_SOURCE)
        }
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun runtimeClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
    }

    private fun runtimeClasspathText(): String = runtimeClasspath().joinToString(File.pathSeparator)

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
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "demo/SwitchState.java" to """
                package demo;

                public enum SwitchState {
                    ENABLED,
                    DISABLED
                }
            """.trimIndent(),
            "demo/BaseSwitch.java" to """
                package demo;

                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                public interface BaseSwitch {
                    SwitchState state();
                }
            """.trimIndent(),
            "demo/ChildSwitch.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface ChildSwitch extends BaseSwitch {
                    @Id
                    long id();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "demo/SwitchState.kt" to """
                package demo

                enum class SwitchState {
                    ENABLED,
                    DISABLED,
                }
            """.trimIndent(),
            "demo/BaseSwitch.kt" to """
                package demo

                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface BaseSwitch {
                    val state: SwitchState
                }
            """.trimIndent(),
            "demo/ChildSwitch.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface ChildSwitch : BaseSwitch {
                    @Id
                    val id: Long
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            package demo.dto

            input ChildSwitchInput {
                id
                state as stateCode -> {
                    ENABLED: 1
                    DISABLED: 0
                }
            }
        """.trimIndent()
    }
}
