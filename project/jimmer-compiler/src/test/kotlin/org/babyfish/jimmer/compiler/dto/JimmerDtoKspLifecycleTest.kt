package org.babyfish.jimmer.compiler.dto

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
import org.babyfish.jimmer.compiler.lsi.ksp.KspLsiCompilerDriver
import site.addzero.lsi.core.LsiSymbolId

class JimmerDtoKspLifecycleTest {

    @Test
    fun `real ksp retries pending dto when another processor creates a round`() {
        val result = compileKsp(generateBook = true)

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.messages.joinToString("\n"))
        assertTrue(result.logger.errors.isEmpty())
        assertEquals(JimmerDtoCompilerFeatureStatus.PENDING, result.capture.round(0).status)
        assertTrue(result.capture.round(0).unresolvedSymbols.isEmpty())
        assertTrue(result.capture.round(0).diagnosticCodes.isEmpty())
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.capture.round(1).status)
        assertEquals(setOf(BOOK_ID), result.capture.round(1).processedSymbols)
        assertEquals(listOf("BookView"), result.capture.round(1).dtoTypeNames)
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.capture.finalRound().status)
    }

    @Test
    fun `real ksp reports a pending dto only from finish when no new round occurs`() {
        val result = compileKsp(generateBook = false)

        assertEquals(
            KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR,
            result.exitCode,
            result.logger.messages.joinToString("\n"),
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.PENDING, result.capture.round(0).status)
        assertTrue(result.capture.round(0).unresolvedSymbols.isEmpty())
        assertTrue(result.capture.round(0).diagnosticCodes.isEmpty())
        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, result.capture.finalRound().status)
        assertEquals(listOf("jimmer.dto.unresolved"), result.capture.finalRound().diagnosticCodes)
        assertTrue(result.logger.errors.single().contains("[jimmer.dto.unresolved]"))
    }

    private fun compileKsp(generateBook: Boolean): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-ksp-lifecycle").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Anchor.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("package demo\nfun anchor() = Unit")
        }
        projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText("BookView { id name }")
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val capture = LifecycleCapture()
        val logger = CapturingKspLogger()
        val providers = buildList {
            add(LifecycleDriverProvider(capture))
            if (generateBook) {
                add(BookGeneratingProvider())
            }
        }
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "dto-ksp-lifecycle"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(configuration, providers, logger).execute()
        return KspCompilationResult(exitCode, capture, logger)
    }

    private class LifecycleDriverProvider(
        private val capture: LifecycleCapture,
    ) : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            val driver = KspLsiCompilerDriver(
                environment = environment,
                providers = listOf(
                    JimmerImmutableCompilerFeatureProvider(),
                    CapturingDtoFeatureProvider(capture),
                ),
                sessionId = "dto-real-ksp-lifecycle",
            )
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    return driver.process(resolver)
                }

                override fun finish() {
                    driver.finish()
                }
            }
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

    private class BookGeneratingProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                private var generated = false

                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (generated) {
                        return emptyList()
                    }
                    val sourceFiles = resolver.getAllFiles().toList().toTypedArray()
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies(aggregating = true, *sourceFiles),
                        packageName = "demo",
                        fileName = "Book",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(BOOK_SOURCE)
                    }
                    generated = true
                    return emptyList()
                }
            }
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
                processedSymbols = result.processedSymbols,
                unresolvedSymbols = result.unresolvedSymbols,
                diagnosticCodes = result.diagnostics.map { diagnostic -> diagnostic.code },
                dtoTypeNames = state.graphs.flatMap { graph ->
                    graph.rootTypeIds.mapNotNull { typeId ->
                        graph.typesById.getValue(typeId).name
                    }
                },
            )
        }

        fun round(number: Int): CapturedRound {
            return requireNotNull(rounds[number to false]) { "KSP round $number was not captured" }
        }

        fun finalRound(): CapturedRound {
            return requireNotNull(rounds.entries.singleOrNull { entry -> entry.key.second }?.value) {
                "KSP final round was not captured"
            }
        }
    }

    private class CapturingKspLogger : KSPLogger {
        val messages = mutableListOf<String>()

        val errors = mutableListOf<String>()

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
            errors += message
        }

        override fun exception(exception: Throwable) {
            throw exception
        }
    }

    private data class CapturedRound(
        val status: JimmerDtoCompilerFeatureStatus,
        val processedSymbols: Set<LsiSymbolId>,
        val unresolvedSymbols: Set<LsiSymbolId>,
        val diagnosticCodes: List<String>,
        val dtoTypeNames: List<String>,
    )

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val capture: LifecycleCapture,
        val logger: CapturingKspLogger,
    )

    private companion object {
        val BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.Book")

        val BOOK_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Book {
                @Id
                val id: Long

                val name: String
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }
    }
}
