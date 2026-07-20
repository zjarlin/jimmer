package org.babyfish.jimmer.compiler.client

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
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

class ClientResourceGoldenAuditTest {

    @Test
    fun `apt simple resource`() {
        assertGolden("simple.json", compileJava(JAVA_SIMPLE_SOURCES))
    }

    @Test
    fun `ksp simple resource`() {
        assertGolden("simple.json", compileKotlin(KOTLIN_SIMPLE_SOURCE))
    }

    @Test
    fun `apt complex resource`() {
        assertGolden("complex.json", compileJava(JAVA_COMPLEX_SOURCES))
    }

    @Test
    fun `ksp complex resource`() {
        assertGolden("complex.json", compileKotlin(KOTLIN_COMPLEX_SOURCE))
    }

    @Test
    fun `apt generated resource`() {
        assertGolden(
            "complex.json",
            compileJava(
                sources = mapOf("audit/Anchor.java" to JAVA_ANCHOR_SOURCE),
                additionalProcessors = listOf(JavaGeneratingProcessor()),
            ),
        )
    }

    @Test
    fun `ksp generated resource`() {
        assertGolden(
            "complex.json",
            compileKotlin(
                source = KOTLIN_ANCHOR_SOURCE,
                additionalProviders = listOf(KotlinGeneratingProvider()),
            ),
        )
    }

    private fun compileJava(
        sources: Map<String, String>,
        additionalProcessors: List<javax.annotation.processing.Processor> = emptyList(),
    ): String {
        val projectDir = createTempDirectory(prefix = "client-golden-apt").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val sourceFiles = sources.map { (path, content) ->
            sourceDir.resolve(path).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT golden audit requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    testClasspath(),
                    "-Ajimmer.client.checkedException=true",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()) + additionalProcessors)
            task.call()
        }
        assertTrue(success, diagnostics.errorMessage())
        assertTrue(
            diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            diagnostics.errorMessage(),
        )
        val resource = classesDir.resolve("META-INF/jimmer/client")
        assertTrue(resource.isFile, "Missing APT client resource: ${resource.absolutePath}")
        return resource.readText()
    }

    private fun compileKotlin(
        source: String,
        additionalProviders: List<SymbolProcessorProvider> = emptyList(),
    ): String {
        val projectDir = createTempDirectory(prefix = "client-golden-ksp").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/audit/Source.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val logger = CollectingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "client-golden-audit"
            sourceRoots = listOf(sourceFile)
            libraries = testClasspathFiles()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            this.resourceOutputDir = resourceOutputDir
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()) + additionalProviders,
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.text())
        val resource = resourceOutputDir.resolve("META-INF/jimmer/client")
        assertTrue(resource.isFile, "Missing KSP client resource: ${resource.absolutePath}\n${logger.text()}")
        return resource.readText()
    }

    private fun assertGolden(name: String, actual: String) {
        val expected = requireNotNull(javaClass.getResource("/client/golden/$name")) {
            "Missing client golden resource: $name"
        }.readText().removeSuffix("\n")
        assertEquals(expected, actual)
    }

    private fun testClasspath(): String = testClasspathFiles().joinToString(File.pathSeparator)

    private fun testClasspathFiles(): List<File> = System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .map(::File)
        .filter(File::exists)

    private fun DiagnosticCollector<JavaFileObject>.errorMessage(): String = diagnostics.joinToString("\n") { diagnostic ->
        "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:${diagnostic.lineNumber}:${diagnostic.columnNumber} " +
            diagnostic.getMessage(null)
    }

    private class JavaGeneratingProcessor : AbstractProcessor() {
        private var generated = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("audit.GenerateClient")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (generated || roundEnvironment.processingOver()) {
                return false
            }
            JAVA_GENERATED_SOURCES.forEach { (typeName, content) ->
                processingEnv.filer.createSourceFile(typeName).openWriter().use { writer ->
                    writer.write(content)
                }
            }
            generated = true
            return false
        }
    }

    private class KotlinGeneratingProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                private var generated = false

                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (!generated) {
                        environment.codeGenerator.createNewFile(
                            dependencies = Dependencies(
                                aggregating = true,
                                *resolver.getAllFiles().toList().toTypedArray(),
                            ),
                            packageName = "audit",
                            fileName = "GeneratedClient",
                            extensionName = "kt",
                        ).bufferedWriter().use { writer ->
                            writer.write(KOTLIN_COMPLEX_SOURCE)
                        }
                        generated = true
                    }
                    return emptyList()
                }
            }
        }
    }

    private class CollectingKspLogger : KSPLogger {
        private val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += "LOG: $message"
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += "INFO: $message"
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += "WARN: $message"
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += "ERROR: $message"
        }

        override fun exception(e: Throwable) {
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private companion object {
        val JAVA_SIMPLE_SOURCES = mapOf(
            "audit/Book.java" to """
                package audit;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface Book {
                    @Id long id();
                    String name();
                }
            """.trimIndent(),
            "audit/BookService.java" to """
                package audit;

                import org.babyfish.jimmer.client.meta.Api;

                @Api
                public interface BookService {
                    @Api Book find(long id);
                }
            """.trimIndent(),
        )

        val JAVA_COMPLEX_SOURCES = JAVA_SIMPLE_SOURCES + mapOf(
            "audit/Genre.java" to """
                package audit;

                /** Available genres. */
                public enum Genre {
                    /** Science. */ SCIENCE,
                    /** History. */ HISTORY
                }
            """.trimIndent(),
            "audit/NotFoundException.java" to """
                package audit;

                import org.babyfish.jimmer.ClientException;
                import org.babyfish.jimmer.error.CodeBasedException;

                @ClientException(family = "AUDIT", code = "NOT_FOUND")
                public final class NotFoundException extends CodeBasedException {
                    private final long bookId;
                    public NotFoundException(long bookId) { this.bookId = bookId; }
                    public long getBookId() { return bookId; }
                }
            """.trimIndent(),
            "audit/SearchRequest.java" to """
                package audit;

                public final class SearchRequest {
                    public String getKeyword() { return ""; }
                    public int getPage() { return 0; }
                }
            """.trimIndent(),
            "audit/Book.java" to """
                package audit;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.jetbrains.annotations.Nullable;

                /** A book. */
                @Entity
                public interface Book {
                    @Id long id();
                    String name();
                    @Nullable String edition();
                    Genre genre();
                }
            """.trimIndent(),
            "audit/BookService.java" to """
                package audit;

                import java.util.List;
                import org.babyfish.jimmer.client.ApiIgnore;
                import org.babyfish.jimmer.client.FetchBy;
                import org.babyfish.jimmer.client.meta.Api;
                import org.babyfish.jimmer.sql.fetcher.Fetcher;

                /** Client service. */
                @Api("audit")
                public interface BookService {
                    /** Detailed book fetcher. */
                    Fetcher<Book> BOOK_FETCHER = null;

                    /** Search books. */
                    @Api
                    List<@FetchBy("BOOK_FETCHER") Book> search(
                        SearchRequest request,
                        @ApiIgnore String token
                    ) throws NotFoundException;
                }
            """.trimIndent(),
        )

        val JAVA_GENERATED_SOURCES = JAVA_COMPLEX_SOURCES.mapKeys { (path, _) ->
            path.removeSuffix(".java").replace('/', '.')
        }

        val KOTLIN_SIMPLE_SOURCE = """
            package audit

            import org.babyfish.jimmer.client.meta.Api
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Book {
                @Id val id: Long
                val name: String
            }

            @Api
            interface BookService {
                @Api fun find(id: Long): Book
            }
        """.trimIndent()

        val KOTLIN_COMPLEX_SOURCE = """
            package audit

            import org.babyfish.jimmer.ClientException
            import org.babyfish.jimmer.client.ApiIgnore
            import org.babyfish.jimmer.client.FetchBy
            import org.babyfish.jimmer.client.meta.Api
            import org.babyfish.jimmer.error.CodeBasedException
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.fetcher.Fetcher

            /** Available genres. */
            enum class Genre {
                /** Science. */ SCIENCE,
                /** History. */ HISTORY,
            }

            /** A book. */
            @Entity
            interface Book {
                @Id val id: Long
                val name: String
                val edition: String?
                val genre: Genre
            }

            @ClientException(family = "AUDIT", code = "NOT_FOUND")
            class NotFoundException(val bookId: Long) : CodeBasedException()

            class SearchRequest(
                val keyword: String,
                val page: Int,
            )

            /** Client service. */
            @Api("audit")
            interface BookService {
                /** Search books. */
                @Api
                @Throws(NotFoundException::class)
                fun search(request: SearchRequest, @ApiIgnore token: String): List<@FetchBy("BOOK_FETCHER") Book>

                companion object {
                    /** Detailed book fetcher. */
                    val BOOK_FETCHER: Fetcher<Book> = null!!
                }
            }
        """.trimIndent()

        val JAVA_ANCHOR_SOURCE = """
            package audit;
            @interface GenerateClient {}
            @GenerateClient public class Anchor {}
        """.trimIndent()

        val KOTLIN_ANCHOR_SOURCE = """
            package audit
            fun anchor() = Unit
        """.trimIndent()
    }
}
