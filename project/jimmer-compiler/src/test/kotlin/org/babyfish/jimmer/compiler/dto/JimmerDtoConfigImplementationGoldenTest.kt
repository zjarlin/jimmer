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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerDtoConfigImplementationGoldenTest {

    @Test
    fun `apt renders nested config implementation types from frozen contracts`() {
        val generated = compileApt()
        assertGolden("apt.txt", generated.configConstructionSnapshot(includeImports = true))
        assertGolden("apt-full.txt", generated.configLambdaSnapshot(CompilerKind.APT))
    }

    @Test
    fun `ksp renders nested config implementation types from frozen contracts`() {
        val generated = compileKsp()
        assertGolden("ksp.txt", generated.configConstructionSnapshot(includeImports = false))
        assertGolden("ksp-full.txt", generated.configLambdaSnapshot(CompilerKind.KSP))
    }

    private fun compileApt(): String {
        val projectDir = fixtureProject("jimmer-dto-config-apt")
        val sourceFiles = writeSources(projectDir.resolve("src/main/java/demo"), JAVA_SOURCES)
        writeDtoSource(projectDir)
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("DTO config APT golden test requires a JDK compiler")
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
        return generatedDir.resolve("demo/dto/BookView.java").readText()
    }

    private fun compileKsp(): String {
        val projectDir = fixtureProject("jimmer-dto-config-ksp")
        val sourceFiles = writeSources(projectDir.resolve("src/main/kotlin/demo"), KOTLIN_SOURCES)
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-config-golden"
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
        return kotlinOutputDir.resolve("demo/dto/BookView.kt").readText()
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
        projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
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

    private fun String.configConstructionSnapshot(includeImports: Boolean): String {
        return lineSequence()
            .map(String::trim)
            .filter { line ->
                (includeImports && line == "import demo.Configurations;") ||
                    ".filter(new " in line ||
                    ".recursive(new " in line ||
                    line.startsWith("filter(demo.Configurations.") ||
                    line.startsWith("recursive(demo.Configurations.")
            }
            .joinToString("\n", postfix = "\n")
    }

    private fun String.configLambdaSnapshot(compilerKind: CompilerKind): String {
        val startMarker = when (compilerKind) {
            CompilerKind.APT -> "public static final DtoMetadata<Book, BookView> METADATA"
            CompilerKind.KSP -> "public val METADATA: DtoMetadata<Book, BookView>"
        }
        val endMarker = when (compilerKind) {
            CompilerKind.APT -> "    private static final DtoPropAccessor"
            CompilerKind.KSP -> "        private val "
        }
        val start = indexOf(startMarker)
        require(start >= 0) { "Missing config metadata start marker: $startMarker" }
        val end = indexOf(endMarker, start)
        require(end > start) { "Missing config metadata end marker: $endMarker" }
        return (substring(start, end).trimEnd() + "\n")
            .removeSuffix("\n")
            .split('\n')
            .joinToString(separator = "\n", postfix = "\n") { line -> "$line|" }
    }

    private fun assertGolden(name: String, actual: String) {
        val resourcePath = "/dto/config-implementation/$name"
        val expected = requireNotNull(javaClass.getResource(resourcePath)) {
            "Missing DTO config implementation golden: $resourcePath"
        }.readText()
        assertEquals(expected, actual, resourcePath)
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

    private enum class CompilerKind {
        APT,
        KSP,
    }

    private companion object {
        val JAVA_SOURCES = linkedMapOf(
            "AuditBase.java" to """
                package base;

                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                public interface AuditBase {
                    String inheritedName();
                }
            """.trimIndent(),
            "Author.java" to """
                package demo;

                import base.AuditBase;
                import java.math.BigDecimal;
                import java.math.BigInteger;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.jspecify.annotations.Nullable;

                @Entity
                public interface Author extends AuditBase {
                    @Id
                    long id();

                    @Nullable
                    String name();

                    boolean active();

                    int rank();

                    long score();

                    float ratio();

                    double rating();

                    BigInteger serial();

                    BigDecimal amount();

                    @Nullable
                    String nickname();

                    @Nullable
                    @ManyToOne
                    Publisher publisher();
                }
            """.trimIndent(),
            "Publisher.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface Publisher {
                    @Id
                    long id();
                }
            """.trimIndent(),
            "Book.java" to """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.ManyToMany;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.OneToMany;
                import org.jspecify.annotations.Nullable;

                @Entity
                public interface Book {
                    @Id
                    long id();

                    @ManyToMany
                    List<Author> authors();

                    @ManyToMany
                    List<Author> reviewers();

                    @Nullable
                    @ManyToOne
                    Book parent();

                    @OneToMany(mappedBy = "parent")
                    List<Book> childBooks();
                }
            """.trimIndent(),
            "Configurations.java" to """
                package demo;

                import org.babyfish.jimmer.sql.fetcher.FieldFilter;
                import org.babyfish.jimmer.sql.fetcher.FieldFilterArgs;
                import org.babyfish.jimmer.sql.fetcher.RecursionStrategy;

                public final class Configurations {
                    private Configurations() {}

                    public static class AuthorFilter implements FieldFilter<AuthorTable> {
                        @Override
                        public void apply(FieldFilterArgs<AuthorTable> args) {}
                    }

                    public static class BookRecursionStrategy implements RecursionStrategy<Book> {
                        @Override
                        public boolean isRecursive(Args<Book> args) {
                            return true;
                        }
                    }
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "AuditBase.kt" to """
                package base

                import org.babyfish.jimmer.sql.MappedSuperclass

                @MappedSuperclass
                interface AuditBase {
                    val inheritedName: String
                }
            """.trimIndent(),
            "Author.kt" to """
                package demo

                import base.AuditBase
                import java.math.BigDecimal
                import java.math.BigInteger
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.ManyToOne

                @Entity
                interface Author : AuditBase {
                    @Id
                    val id: Long

                    val name: String?

                    val active: Boolean

                    val rank: Int

                    val score: Long

                    val ratio: Float

                    val rating: Double

                    val serial: BigInteger

                    val amount: BigDecimal

                    val nickname: String?

                    @ManyToOne
                    val publisher: Publisher?
                }
            """.trimIndent(),
            "Publisher.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Publisher {
                    @Id
                    val id: Long
                }
            """.trimIndent(),
            "Book.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.ManyToMany
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.OneToMany

                @Entity
                interface Book {
                    @Id
                    val id: Long

                    @ManyToMany
                    val authors: List<Author>

                    @ManyToMany
                    val reviewers: List<Author>

                    @ManyToOne
                    val parent: Book?

                    @OneToMany(mappedBy = "parent")
                    val childBooks: List<Book>
                }
            """.trimIndent(),
            "Configurations.kt" to """
                package demo

                import org.babyfish.jimmer.sql.fetcher.RecursionStrategy
                import org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter
                import org.babyfish.jimmer.sql.kt.fetcher.KFieldFilterDsl

                object Configurations {
                    class AuthorFilter : KFieldFilter<Author> {
                        override fun KFieldFilterDsl<Author>.applyTo() = Unit
                    }

                    class BookRecursionStrategy : RecursionStrategy<Book> {
                        override fun isRecursive(args: RecursionStrategy.Args<Book>): Boolean = true
                    }
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            export demo.Book
                -> package demo.dto

            import demo.Configurations.AuthorFilter
            import demo.Configurations.BookRecursionStrategy

            BookView {
                id

                !where(
                    (inheritedName = 'BASE' and active = true and rank > 1 and rank >= 2 and rank < 10 and rank <= 9)
                    or
                    (name <> 'NONE' and name like 'A%' and name ilike 'a%' and score = 2 and
                    ratio = 1.5 and rating = 2.5 and serial = 12345678901234567890 and
                    amount = 49.99 and publisherId = 7 and nickname is null)
                )
                !orderBy(name asc, score desc)
                !limit(20, 5)
                !batch(16)
                authors {
                    id
                }

                !filter(AuthorFilter)
                reviewers {
                    id
                }

                !recursion(BookRecursionStrategy)
                childBooks*
            }
        """.trimIndent()
    }
}
