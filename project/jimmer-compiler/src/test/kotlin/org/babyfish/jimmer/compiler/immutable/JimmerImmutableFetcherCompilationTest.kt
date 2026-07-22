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

class JimmerImmutableFetcherCompilationTest {

    @Test
    fun `apt generated fetcher sources compile with javac`() {
        val projectDir = createTempDirectory(prefix = "jimmer-fetcher-apt-compilation").toFile()
        val sourceFiles = writeSources(
            sourceRoot = projectDir.resolve("src/main/java"),
            sources = JAVA_SOURCES,
        )
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable fetcher compilation tests")
        val processingDiagnostics = DiagnosticCollector<JavaFileObject>()
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
                listOf("-proc:only", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(processingSucceeded, processingDiagnostics.toErrorMessage())

        val generatedFiles = generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        val bookFetcher = generatedFiles.single { file -> file.name == "BookFetcher.java" }.readText()
        assertContains(bookFetcher, "BookFetcher parent(Fetcher<Book> childFetcher)")
        assertContains(bookFetcher, "BookFetcher children(Fetcher<Book> childFetcher)")
        assertContains(bookFetcher, "BookFetcher store(Fetcher<Store> childFetcher)")
        assertContains(bookFetcher, "BookFetcher location(Fetcher<Location> childFetcher)")

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
                listOf("-proc:none", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles + generatedFiles),
            ).call()
        }
        assertTrue(compilationSucceeded, compilationDiagnostics.toErrorMessage())
    }

    @Test
    fun `ksp generated fetcher sources compile with k2`() {
        val result = processKsp(
            projectPrefix = "jimmer-fetcher-ksp-compilation",
            moduleName = "jimmer-fetcher-ksp-compilation",
            sources = KOTLIN_SOURCES,
        )
        val bookFetcherFile = result.generatedFiles.single { file -> file.name == "order-itemFetcher.kt" }
        val bookFetcher = bookFetcherFile.readText()
        assertContains(bookFetcher, "import catalog.`by`")
        assertContains(bookFetcher, "public fun `parent*`()")
        assertContains(bookFetcher, "public fun `children*`()")
        assertTrue(result.generatedFiles.any { file -> file.name == "LocationFetcher.kt" })

        compileWithK2(
            projectDir = result.projectDir,
            sourceFiles = result.sourceFiles + result.generatedFiles,
        )
    }

    @Test
    fun `ksp escaped entity and property fetcher compiles with k2`() {
        val result = processKsp(
            projectPrefix = "jimmer-fetcher-ksp-escaped-compilation",
            moduleName = "jimmer-fetcher-ksp-escaped-compilation",
            sources = ESCAPED_KOTLIN_SOURCES,
        )
        val fetcherFiles = result.generatedFiles.filter { file -> file.name.endsWith("Fetcher.kt") }
        val escapedFetcher = fetcherFiles.single { file -> file.name == "escaped-modelFetcher.kt" }.readText()
        assertContains(escapedFetcher, "public class `Order-ItemFetcherDsl`")
        assertContains(escapedFetcher, "public fun `display-name`(")
        assertContains(escapedFetcher, "private val `emptyOrder-ItemFetcher`")

        compileWithK2(
            projectDir = result.projectDir,
            sourceFiles = result.sourceFiles + fetcherFiles,
        )
    }

    private fun processKsp(
        projectPrefix: String,
        moduleName: String,
        sources: Map<String, String>,
    ): KspCompilationResult {
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
        return KspCompilationResult(projectDir, sourceFiles, generatedFiles)
    }

    private fun compileWithK2(
        projectDir: File,
        sourceFiles: List<File>,
    ) {
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
            sourceFiles.mapTo(this) { file -> file.absolutePath }
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

    private data class KspCompilationResult(
        val projectDir: File,
        val sourceFiles: List<File>,
        val generatedFiles: List<File>,
    )

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

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "catalog/Store.java" to """
                package catalog;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface Store {
                    @Id
                    long id();
                }
            """.trimIndent(),
            "demo/Location.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Embeddable;

                @Embeddable
                public interface Location {
                    String city();
                }
            """.trimIndent(),
            "demo/Book.java" to """
                package demo;

                import catalog.Store;
                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.OneToMany;

                @Entity
                public interface Book {
                    @Id
                    long id();

                    @ManyToOne
                    Book parent();

                    @OneToMany(mappedBy = "parent")
                    List<Book> children();

                    @ManyToOne
                    Store store();

                    Location location();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "catalog/Store.kt" to """
                package catalog

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Store {
                    @Id
                    val id: Long
                }
            """.trimIndent(),
            "demo/Location.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Location {
                    val city: String
                }
            """.trimIndent(),
            "demo/order-item.kt" to """
                package demo

                import catalog.Store
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.OneToMany

                @Entity
                interface Book {
                    @Id
                    val id: Long

                    @ManyToOne
                    val parent: Book

                    @OneToMany(mappedBy = "parent")
                    val children: List<Book>

                    @ManyToOne
                    val store: Store

                    val location: Location
                }
            """.trimIndent(),
        )

        val ESCAPED_KOTLIN_SOURCES = linkedMapOf(
            "demo/escaped-model.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface `Order-Item` {
                    @Id
                    val id: Long

                    val `display-name`: String
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
