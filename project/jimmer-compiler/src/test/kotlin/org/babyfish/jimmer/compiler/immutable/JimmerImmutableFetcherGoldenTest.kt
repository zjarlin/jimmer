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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerImmutableFetcherGoldenTest {

    @Test
    fun `apt fetcher source matches migration golden`() {
        assertGolden("apt/BookFetcher.java", compileApt())
    }

    @Test
    fun `ksp fetcher source matches migration golden`() {
        assertGolden("ksp/SourceFetcher.kt", compileKsp())
    }

    private fun compileApt(): String {
        val projectDir = createTempDirectory(prefix = "jimmer-fetcher-apt-golden").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Book.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(JAVA_SOURCE)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable fetcher APT golden tests")
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
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertTrue(task.call(), diagnostics.toErrorMessage())
        }
        val fetcherFile = generatedDir.resolve("demo/BookFetcher.java")
        assertTrue(fetcherFile.isFile, "APT fetcher output is missing: ${fetcherFile.absolutePath}")
        return fetcherFile.readText()
    }

    private fun compileKsp(): String {
        val projectDir = createTempDirectory(prefix = "jimmer-fetcher-ksp-golden").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Source.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(KOTLIN_SOURCE)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-fetcher-ksp-golden"
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
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.text())
        val fetcherFile = kotlinOutputDir.resolve("demo/SourceFetcher.kt")
        assertTrue(fetcherFile.isFile, "KSP fetcher output is missing: ${fetcherFile.absolutePath}")
        return fetcherFile.readText()
    }

    private fun assertGolden(name: String, actual: String) {
        val resourcePath = "/immutable/fetcher/$name"
        val expected = javaClass.getResource(resourcePath)?.readText()
        if (expected == null) {
            val outputFile = File(System.getProperty("java.io.tmpdir"), "jimmer-$name")
            outputFile.parentFile.mkdirs()
            outputFile.writeText(actual)
            error("Missing golden resource '$resourcePath', actual output: ${outputFile.absolutePath}")
        }
        assertEquals(expected, actual)
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
        val JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.OneToMany;
            import org.babyfish.jimmer.sql.Transient;
            import org.jspecify.annotations.Nullable;

            @Entity
            public interface Book {
                @Id
                long id();

                /** Book title. */
                String title();

                @ManyToOne
                @Nullable
                Book parent();

                @IdView("parent")
                @Nullable
                Long parentId();

                @OneToMany(mappedBy = "parent")
                List<Book> children();

                @Transient
                String transientLabel();
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.OneToMany
            import org.babyfish.jimmer.sql.Transient

            @Entity
            interface Book {
                @Id
                val id: Long

                /** Book title. */
                val title: String

                @ManyToOne
                val parent: Book?

                @IdView("parent")
                val parentId: Long?

                @OneToMany(mappedBy = "parent")
                val children: List<Book>

                @Transient
                val transientLabel: String
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
