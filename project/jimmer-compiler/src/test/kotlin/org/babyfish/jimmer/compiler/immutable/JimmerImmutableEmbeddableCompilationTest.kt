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

class JimmerImmutableEmbeddableCompilationTest {

    @Test
    fun `apt generated embeddable sources compile with javac`() {
        val projectDir = createTempDirectory(prefix = "jimmer-embeddable-apt-compilation").toFile()
        val sourceFiles = writeSources(
            sourceRoot = projectDir.resolve("src/main/java/demo"),
            sources = JAVA_SOURCES,
        )
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val processingDiagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable embeddable compilation tests")
        val processingSucceeded = compiler.getStandardFileManager(
            processingDiagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(processingClassesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                processingDiagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    runtimeClasspathText(),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(processingSucceeded, processingDiagnostics.toErrorMessage())

        val generatedFiles = generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        assertTrue(generatedFiles.isNotEmpty(), "APT did not generate Java sources")
        val propsSource = generatedFiles.single { file -> file.name == "LocationProps.java" }.readText()
        val expressionSource = generatedFiles
            .single { file -> file.name == "LocationPropExpression.java" }
            .readText()
        assertContains(propsSource, "TypedProp.Scalar<Location, String> CITY")
        assertContains(propsSource, "TypedProp.Scalar<Location, Integer> ZIP_CODE")
        assertContains(propsSource, "TypedProp.ScalarList<Location, String> TAGS")
        assertContains(propsSource, "TypedProp.Reference<Location, Geo> GEO")
        assertContains(expressionSource, "PropExpression.Str city()")
        assertContains(expressionSource, "PropExpression.Num<Integer> zipCode()")
        assertContains(expressionSource, "GeoPropExpression geo()")
        assertContains(expressionSource, "return new GeoPropExpression(__get(LocationProps.GEO.unwrap()))")

        val compiledClassesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val compilationDiagnostics = DiagnosticCollector<JavaFileObject>()
        val compilationSucceeded = compiler.getStandardFileManager(
            compilationDiagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(compiledClassesDir))
            compiler.getTask(
                null,
                fileManager,
                compilationDiagnostics,
                listOf(
                    "-proc:none",
                    "-classpath",
                    runtimeClasspathText(),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles + generatedFiles),
            ).call()
        }
        assertTrue(compilationSucceeded, compilationDiagnostics.toErrorMessage())
    }

    @Test
    fun `ksp generated embeddable sources compile with k2`() {
        val projectDir = createTempDirectory(prefix = "jimmer-embeddable-ksp-compilation").toFile()
        val sourceFiles = writeSources(
            sourceRoot = projectDir.resolve("src/main/kotlin/demo"),
            sources = KOTLIN_SOURCES,
        )
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-embeddable-ksp-compilation"
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
        val processingExitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, processingExitCode, logger.text())

        val generatedFiles = kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sortedBy { file -> file.absolutePath }
            .toList()
        assertTrue(generatedFiles.isNotEmpty(), "KSP did not generate Kotlin sources")
        val propsSource = generatedFiles.single { file -> file.name == "LocationProps.kt" }.readText()
        assertContains(propsSource, "KNonNullEmbeddedPropExpression<Location>.city")
        assertContains(propsSource, "KEmbeddedPropExpression<Location>.zipCode")
        assertContains(propsSource, "KNonNullEmbeddedPropExpression<Location>.geo")
        assertContains(propsSource, "TypedProp.Scalar<Location, String>")
        assertContains(propsSource, "TypedProp.Scalar<Location, Int?>")
        assertContains(propsSource, "TypedProp.ScalarList<Location, String>")
        assertContains(propsSource, "TypedProp.Reference<Location, Geo>")

        val compiledClassesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val compilerMessages = ByteArrayOutputStream()
        val compilerArguments = buildList {
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("17")
            add("-classpath")
            add(runtimeClasspathText())
            add("-d")
            add(compiledClassesDir.absolutePath)
            (sourceFiles + generatedFiles).mapTo(this) { file -> file.absolutePath }
        }
        val compilationExitCode = PrintStream(
            compilerMessages,
            true,
            StandardCharsets.UTF_8,
        ).use { stream ->
            K2JVMCompiler().exec(stream, *compilerArguments.toTypedArray())
        }
        assertEquals(
            ExitCode.OK,
            compilationExitCode,
            compilerMessages.toString(StandardCharsets.UTF_8),
        )
    }

    private fun writeSources(
        sourceRoot: File,
        sources: Map<String, String>,
    ): List<File> {
        sourceRoot.mkdirs()
        return sources.map { (name, content) ->
            sourceRoot.resolve(name).apply { writeText(content) }
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

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "Geo.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Embeddable;

                @Embeddable
                public interface Geo {
                    double longitude();
                }
            """.trimIndent(),
            "Location.java" to """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Embeddable;
                import org.jspecify.annotations.Nullable;

                @Embeddable
                public interface Location {
                    String city();

                    @Nullable
                    Integer zipCode();

                    List<String> tags();

                    Geo geo();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "Geo.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Geo {
                    val longitude: Double
                }
            """.trimIndent(),
            "Location.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Location {
                    val city: String

                    val zipCode: Int?

                    val tags: List<String>

                    val geo: Geo
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
