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

class JimmerImmutableQueryCompilationTest {

    @Test
    fun `apt generated query sources compile with javac`() {
        val projectDir = createTempDirectory(prefix = "jimmer-query-apt-compilation").toFile()
        val sourceFiles = writeSources(projectDir.resolve("src/main/java"), JAVA_SOURCES)
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable query compilation tests")
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
        val bookProps = generatedFiles.single { file -> file.name == "BookProps.java" }.readText()
        val bookTable = generatedFiles.single { file -> file.name == "BookTable.java" }.readText()
        val bookTableEx = generatedFiles.single { file -> file.name == "BookTableEx.java" }.readText()
        assertTrue(generatedFiles.any { file -> file.name == "LabelProps.java" })
        assertContains(bookProps, "Predicate children(Function<BookTableEx, Predicate> block)")
        assertContains(bookTable, "implements BookProps, PolymorphicTable<Book>")
        assertContains(bookTable, "StoreTable store()")
        assertContains(bookTable, "LocationPropExpression location()")
        assertContains(bookTable, "PropExpression<int[]> scores()")
        assertContains(bookTable, "PropExpression<Integer[]> ratings()")
        assertContains(bookTable, "static class Remote")
        assertContains(bookTableEx, " TT weakJoin(")

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
    fun `ksp generated query sources compile with k2`() {
        val projectDir = createTempDirectory(prefix = "jimmer-query-ksp-compilation").toFile()
        val result = processKsp(
            projectDir = projectDir,
            moduleName = "jimmer-query-ksp-compilation",
            sources = KOTLIN_SOURCES,
        )
        val sourceFiles = result.sourceFiles
        val generatedFiles = result.generatedFiles
        val bookPropsFile = generatedFiles.single { file -> file.name == "order-item.partProps.kt" }
        val bookProps = bookPropsFile.readText()
        assertContains(bookProps, "KProps<Book>.`parent?`")
        assertContains(bookProps, "KProps<Book>.children(")
        assertContains(bookProps, "KTableEx<Book>.`children?`")
        assertContains(bookProps, "inline fun <reified S : Book>")
        assertContains(bookProps, "KNonNullTable<Book>.fetchBy(")
        assertContains(bookProps, "KProps<Book>.store")
        assertContains(bookProps, "KNonNullEmbeddedPropExpression<Location>")

        compileWithK2(
            projectDir = projectDir,
            sourceFiles = sourceFiles + generatedFiles,
        )
    }

    @Test
    fun `ksp escaped query declarations compile with k2`() {
        val projectDir = createTempDirectory(prefix = "jimmer-query-ksp-escaped-compilation").toFile()
        val result = processKsp(
            projectDir = projectDir,
            moduleName = "jimmer-query-ksp-escaped-compilation",
            sources = ESCAPED_KOTLIN_SOURCES,
        )
        val queryFiles = result.generatedFiles.filter { file -> file.name.endsWith("Props.kt") }
        val escapedProps = queryFiles.single { file -> file.name == "escaped-queryProps.kt" }.readText()
        assertContains(escapedProps, "KNonNullProps<`Order-Item`>.`display-name`")
        assertContains(escapedProps, "object `Order-ItemProps`")

        compileWithK2(
            projectDir = projectDir,
            sourceFiles = result.sourceFiles + queryFiles,
        )
    }

    private fun processKsp(
        projectDir: File,
        moduleName: String,
        sources: Map<String, String>,
    ): KspProcessingResult {
        val sourceFiles = writeSources(projectDir.resolve("src/main/kotlin"), sources)
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
        return KspProcessingResult(sourceFiles, generatedFiles)
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

    private data class KspProcessingResult(
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
            "demo/BaseNode.java" to """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.IdView;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.MappedSuperclass;
                import org.babyfish.jimmer.sql.OneToMany;
                import org.jspecify.annotations.Nullable;

                @MappedSuperclass
                public interface BaseNode<T extends BaseNode<T>> {
                    @ManyToOne
                    @Nullable
                    T parent();

                    @IdView("parent")
                    @Nullable
                    Long parentId();

                    @OneToMany(mappedBy = "parent")
                    List<T> children();
                }
            """.trimIndent(),
            "catalog/Store.java" to """
                package catalog;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface Store {
                    @Id
                    long id();

                    String name();
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
                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.IdView;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.jspecify.annotations.Nullable;

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                public interface Book extends BaseNode<Book> {
                    @Id
                    long id();

                    @Discriminator
                    String kind();

                    String name();

                    int[] scores();

                    Integer[] ratings();

                    @ManyToOne
                    @Nullable
                    Store store();

                    @IdView("store")
                    @Nullable
                    Long storeId();

                    Location location();
                }
            """.trimIndent(),
            "demo/SpecialBook.java" to """
                package demo;

                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;

                @Entity
                @DiscriminatorValue("SPECIAL")
                public interface SpecialBook extends Book {
                    String specialName();
                }
            """.trimIndent(),
            "demo/Label.java" to """
                package demo;

                import org.babyfish.jimmer.Immutable;

                @Immutable
                public interface Label {
                    String value();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "demo/BaseNode.kt" to """
                package demo

                import org.babyfish.jimmer.sql.IdView
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.MappedSuperclass
                import org.babyfish.jimmer.sql.OneToMany

                @MappedSuperclass
                interface BaseNode<T : BaseNode<T>> {
                    @ManyToOne
                    val parent: T?

                    @IdView("parent")
                    val parentId: Long?

                    @OneToMany(mappedBy = "parent")
                    val children: List<T>
                }
            """.trimIndent(),
            "catalog/Store.kt" to """
                package catalog

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Store {
                    @Id
                    val id: Long

                    val name: String
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
            "demo/order-item.part.kt" to """
                package demo

                import catalog.Store
                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.IdView
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType
                import org.babyfish.jimmer.sql.ManyToOne

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface Book : BaseNode<Book> {
                    @Id
                    val id: Long

                    @Discriminator
                    val kind: String

                    val name: String

                    @ManyToOne
                    val store: Store?

                    @IdView("store")
                    val storeId: Long?

                    val location: Location
                }
            """.trimIndent(),
            "demo/SpecialBook.kt" to """
                package demo

                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity

                @Entity
                @DiscriminatorValue("SPECIAL")
                interface SpecialBook : Book {
                    val specialName: String
                }
            """.trimIndent(),
        )

        val ESCAPED_KOTLIN_SOURCES = linkedMapOf(
            "demo/escaped-query.kt" to """
                package demo

                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface `Order-Item` {
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
