package org.babyfish.jimmer.compiler.dto

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
import org.babyfish.jimmer.compiler.JimmerCompilerCollectContext
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureProvider
import org.babyfish.jimmer.compiler.lsi.apt.AptLsiCompilerDriver
import site.addzero.lsi.core.LsiSymbolId

class JimmerDtoAptConfigLifecycleTest {

    @Test
    fun `real apt defers config until another processor generates its table type`() {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-apt-config-lifecycle").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        val generatedDir = projectDir.resolve("build/generated")
        val sourceFiles = listOf(
            sourceDir.resolve("demo/Models.java").also { file ->
                file.parentFile.mkdirs()
                file.writeText(JAVA_SOURCE)
            },
            sourceDir.resolve("demo/AuthorFilter.java").also { file ->
                file.writeText(FILTER_SOURCE)
            },
        )
        projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(DTO_SOURCE)
        }
        classesDir.mkdirs()
        generatedDir.mkdirs()

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val capture = LifecycleCapture()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
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
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(
                listOf(
                    LifecycleDriverProcessor(capture),
                    AuthorTableGeneratingProcessor(),
                )
            )
            task.call()
        }

        assertTrue(success, diagnostics.errorMessage())
        assertTrue(
            diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            diagnostics.errorMessage(),
        )
        val firstRound = capture.round(0)
        assertEquals(JimmerDtoCompilerFeatureStatus.DEFERRED, firstRound.status)
        assertEquals(setOf(FILTER_ID), firstRound.unresolvedSymbols)
        assertEquals(listOf(FILTER_ID), firstRound.unresolvedTypeIds)
        assertTrue(firstRound.diagnosticCodes.isEmpty())

        val secondRound = capture.round(1)
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, secondRound.status)
        assertEquals(
            setOf(FILTER_ID, AUTHOR_TABLE_ID, AUTHOR_FETCHER_ID, BOOK_FETCHER_ID),
            secondRound.currentRootTypeIds,
        )
        assertTrue(secondRound.unresolvedSymbols.isEmpty())
        assertTrue(secondRound.diagnosticCodes.isEmpty())
        assertEquals(FILTER_ID, secondRound.contract?.implementationTypeId)
        assertEquals(AUTHOR_ID, secondRound.contract?.targetEntityTypeId)
        assertEquals(AUTHOR_TABLE_ID, secondRound.contract?.contractArgumentTypeId)
    }

    private class LifecycleDriverProcessor(
        private val capture: LifecycleCapture,
    ) : AbstractProcessor() {
        private lateinit var driver: AptLsiCompilerDriver

        override fun init(processingEnvironment: javax.annotation.processing.ProcessingEnvironment) {
            super.init(processingEnvironment)
            driver = AptLsiCompilerDriver(
                processingEnvironment = processingEnvironment,
                providers = listOf(
                    JimmerImmutableCompilerFeatureProvider(),
                    CapturingDtoFeatureProvider(capture),
                ),
                sessionId = "dto-real-apt-config-lifecycle",
            )
        }

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            driver.process(roundEnvironment)
            return false
        }
    }

    private class CapturingDtoFeatureProvider(
        private val capture: LifecycleCapture,
    ) : JimmerCompilerFeatureProvider {
        private val delegate = JimmerDtoCompilerFeatureProvider()

        override val descriptor = delegate.descriptor

        override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
            return delegate.collect(context)
        }

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            val result = delegate.precompile(context)
            capture.record(context, result)
            return result
        }

        override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
            return delegate.render(context)
        }
    }

    private class AuthorTableGeneratingProcessor : AbstractProcessor() {
        private var generated = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (generated || roundEnvironment.processingOver()) {
                return false
            }
            processingEnv.filer.createSourceFile(AUTHOR_TABLE_ID.requireTypeQualifiedName()).openWriter().use { writer ->
                writer.write(AUTHOR_TABLE_SOURCE)
            }
            generated = true
            return false
        }
    }

    private class LifecycleCapture {
        private val rounds = linkedMapOf<Pair<Int, Boolean>, CapturedRound>()

        fun record(
            context: JimmerCompilerPrecompileContext,
            result: JimmerCompilerFeaturePrecompileResult,
        ) {
            val state = result.state as JimmerDtoCompilerFeatureState
            rounds[context.round.number to context.round.isFinal] = CapturedRound(
                status = state.status,
                unresolvedSymbols = result.unresolvedSymbols,
                unresolvedTypeIds = state.unresolvedDocuments.flatMap { document -> document.unresolvedTypeIds },
                diagnosticCodes = result.diagnostics.map { diagnostic -> diagnostic.code },
                currentRootTypeIds = context.round.currentRootTypeIds,
                contract = state.schema.documents.singleOrNull()
                    ?.configContractResolution
                    ?.contracts
                    ?.singleOrNull(),
            )
        }

        fun round(number: Int): CapturedRound {
            return requireNotNull(rounds[number to false]) { "APT round $number was not captured" }
        }
    }

    private data class CapturedRound(
        val status: JimmerDtoCompilerFeatureStatus,
        val unresolvedSymbols: Set<LsiSymbolId>,
        val unresolvedTypeIds: List<LsiSymbolId>,
        val diagnosticCodes: List<String>,
        val currentRootTypeIds: Set<LsiSymbolId>,
        val contract: DtoConfigContract?,
    )

    private fun DiagnosticCollector<JavaFileObject>.errorMessage(): String {
        return diagnostics.joinToString(separator = "\n") { diagnostic ->
            val source = diagnostic.source?.name.orEmpty()
            val position = if (diagnostic.lineNumber > 0) {
                "${diagnostic.lineNumber}:${diagnostic.columnNumber}"
            } else {
                "?:?"
            }
            "${diagnostic.kind} $source:$position ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val AUTHOR_ID: LsiSymbolId = LsiSymbolId.type("demo.Author")
        val AUTHOR_FETCHER_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorFetcher")
        val AUTHOR_TABLE_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorTable")
        val BOOK_FETCHER_ID: LsiSymbolId = LsiSymbolId.type("demo.BookFetcher")
        val FILTER_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorFilter")

        val DTO_SOURCE = """
            BookView {
                !filter(demo.AuthorFilter)
                authors {
                    id
                }
            }
        """.trimIndent()

        val JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.ManyToMany;

            @Entity
            interface Author {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @Id
                long id();

                @ManyToMany
                List<Author> authors();
            }
        """.trimIndent()

        val FILTER_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.fetcher.FieldFilter;
            import org.babyfish.jimmer.sql.fetcher.FieldFilterArgs;

            public class AuthorFilter implements FieldFilter<AuthorTable> {
                @Override
                public void apply(FieldFilterArgs<AuthorTable> args) {}
            }
        """.trimIndent()

        val AUTHOR_TABLE_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.ast.table.Table;

            public interface AuthorTable extends Table<Author> {}
        """.trimIndent()
    }
}
