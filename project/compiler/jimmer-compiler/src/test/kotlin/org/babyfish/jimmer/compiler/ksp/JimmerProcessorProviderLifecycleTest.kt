package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JimmerProcessorProviderLifecycleTest {

    @Test
    fun `main provider never defers a valid entity processed by immutable generator`() {
        val result = compileKsp(source = VALID_ENTITY_SOURCE)

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.text())
        assertTrue(result.capture.rounds.first().deferredNames.isEmpty())
        assertTrue(result.generatedKotlinFile("demo/SourceDraft.kt").isFile)
        assertTrue(result.generatedKotlinFile("demo/SourceFetcher.kt").isFile)
    }

    @Test
    fun `main provider waits for the next real round before generating dto source`() {
        val result = compileKsp(
            source = VALID_ENTITY_SOURCE,
            dtoSource = DTO_SOURCE,
        )

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.text())
        assertTrue(
            result.capture.roundOf("demo.BookDraft") < result.capture.roundOf("demo.dto.BookView"),
            result.capture.toString(),
        )
    }

    @Test
    fun `main provider defers invalid entity without passing it to immutable generator`() {
        val result = compileKsp(source = INVALID_ENTITY_SOURCE)

        assertTrue(result.capture.rounds.first().deferredNames.contains("demo.BrokenBook"))
        assertFalse(result.generatedKotlinFile("demo/SourceDraft.kt").exists())
    }

    @Test
    fun `main provider rejects several immutable types in one kotlin source`() {
        val result = compileKsp(source = MULTIPLE_ENTITY_SOURCE)

        assertEquals(KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR, result.exitCode)
        assertTrue(result.logger.text().contains("declares several Jimmer immutable types"))
        assertFalse(result.generatedKotlinFile("demo/SourceFetcher.kt").exists())
    }

    @Test
    fun `main provider processes entities and implicit api generated in a later real round`() {
        val result = compileKsp(
            source = "package demo\nfun anchor() = Unit",
            additionalProviders = listOf(LaterModelAndApiProvider()),
        )

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.text())
        assertTrue(result.capture.rounds.size >= 3)
        assertFalse(result.capture.rounds.first().newDeclarationNames.contains("demo.GeneratedBook"))
        val generatedEntityRoundIndex = result.capture.rounds.indexOfFirst { round ->
            round.newDeclarationNames.contains("demo.GeneratedBook")
        }
        assertTrue(generatedEntityRoundIndex > 0)
        assertTrue(result.capture.rounds.lastIndex > generatedEntityRoundIndex)
        val generatedEntityRound = result.capture.rounds[generatedEntityRoundIndex]
        assertTrue(generatedEntityRound.deferredNames.isEmpty())
        assertTrue(result.generatedKotlinFile("demo/GeneratedModelAndApiDraft.kt").isFile)
        assertTrue(result.generatedKotlinFile("demo/GeneratedModelAndApiFetcher.kt").isFile)
        val clientFile = result.generatedResourceFile("META-INF/jimmer/client")
        assertTrue(clientFile.isFile)
        assertTrue(clientFile.readText().contains("\"typeName\" : \"demo.GeneratedApi\""))
    }

    @Test
    fun `main provider generates embeddable props once after the first round`() {
        val result = compileKsp(
            source = "package demo\nfun anchor() = Unit",
            additionalProviders = listOf(LaterEmbeddableProvider()),
        )

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, result.exitCode, result.logger.text())
        val generatedEmbeddableRoundIndex = result.capture.rounds.indexOfFirst { round ->
            round.newDeclarationNames.contains("demo.Location")
        }
        assertTrue(generatedEmbeddableRoundIndex > 0, result.capture.toString())
        assertTrue(result.capture.rounds[generatedEmbeddableRoundIndex].deferredNames.isEmpty())
        val outputName = "GeneratedEmbeddableProps.kt"
        assertTrue(result.generatedKotlinFile("demo/$outputName").isFile)
        assertEquals(1, result.generatedKotlinFilesNamed(outputName).size)
    }

    @Test
    fun `main provider drops early resource snapshot when a later round is invalid`() {
        val result = compileKsp(
            source = "package demo\nfun anchor() = Unit",
            additionalProviders = listOf(LaterInvalidEntityProvider()),
        )

        assertTrue(result.capture.rounds.first().deferredNames.isEmpty())
        assertTrue(result.capture.rounds.drop(1).any { round ->
            round.deferredNames.contains("demo.LaterBrokenBook")
        })
        assertFalse(result.generatedResourceFile("META-INF/jimmer/client").exists())
    }

    @Test
    fun `main provider fails when another ksp provider creates the same draft source`() {
        val exception = assertFails {
            compileKsp(
                source = VALID_ENTITY_SOURCE,
                providersBeforeJimmer = listOf(CollidingSourceDraftProvider()),
            )
        }

        val causes = exception.causes().toList()
        val causeText = causes.joinToString("\n") { cause ->
            "${cause::class.qualifiedName}: ${cause.message.orEmpty()}"
        }
        assertTrue(causes.any { cause -> cause is FileAlreadyExistsException }, causeText)
        assertTrue(causes.any { cause -> cause.message.orEmpty().contains("SourceDraft.kt") }, causeText)
    }

    private fun compileKsp(
        source: String,
        dtoSource: String? = null,
        providersBeforeJimmer: List<SymbolProcessorProvider> = emptyList(),
        additionalProviders: List<SymbolProcessorProvider> = emptyList(),
    ): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-main-ksp-lifecycle").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Source.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        dtoSource?.let { content ->
            projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val capture = LifecycleCapture()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-main-ksp-lifecycle"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            this.resourceOutputDir = resourceOutputDir
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val providers = providersBeforeJimmer + CapturingMainProvider(capture) + additionalProviders
        val exitCode = KotlinSymbolProcessing(configuration, providers, logger).execute()
        return KspCompilationResult(
            exitCode = exitCode,
            capture = capture,
            logger = logger,
            kotlinOutputDir = kotlinOutputDir,
            resourceOutputDir = resourceOutputDir,
        )
    }

    private class CapturingMainProvider(
        private val capture: LifecycleCapture,
    ) : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            val delegate = JimmerProcessorProvider().create(environment)
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    val newDeclarationNames = resolver.getNewFiles()
                        .flatMap { file -> file.declarations }
                        .filterIsInstance<KSDeclaration>()
                        .mapNotNull { declaration -> declaration.qualifiedName?.asString() }
                        .toSet()
                    val deferred = delegate.process(resolver)
                    capture.rounds += CapturedRound(
                        newDeclarationNames = newDeclarationNames,
                        deferredNames = deferred.mapNotNullTo(sortedSetOf()) { symbol ->
                            (symbol as? KSDeclaration)?.qualifiedName?.asString()
                        },
                    )
                    return deferred
                }

                override fun finish() {
                    delegate.finish()
                }

                override fun onError() {
                    delegate.onError()
                }
            }
        }
    }

    private class CollidingSourceDraftProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                private var generated = false

                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (generated) {
                        return emptyList()
                    }
                    val sourceFile = resolver.getAllFiles().firstOrNull() ?: return emptyList()
                    environment.codeGenerator.createNewFileByPath(
                        dependencies = Dependencies(aggregating = false, sourceFile),
                        path = "demo/SourceDraft",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write("package demo\ninterface SourceDraft")
                    }
                    generated = true
                    return emptyList()
                }
            }
        }
    }

    private class LaterModelAndApiProvider : SymbolProcessorProvider {
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
                        packageName = "org.springframework.web.bind.annotation",
                        fileName = "WebAnnotations",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(WEB_ANNOTATION_SOURCE)
                    }
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies(aggregating = true, *sourceFiles),
                        packageName = "demo",
                        fileName = "GeneratedModelAndApi",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(GENERATED_MODEL_AND_API_SOURCE)
                    }
                    generated = true
                    return emptyList()
                }
            }
        }
    }

    private class LaterInvalidEntityProvider : SymbolProcessorProvider {
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
                        fileName = "LaterInvalidEntity",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(LATER_INVALID_ENTITY_SOURCE)
                    }
                    generated = true
                    return emptyList()
                }
            }
        }
    }

    private class LaterEmbeddableProvider : SymbolProcessorProvider {
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
                        fileName = "GeneratedEmbeddable",
                        extensionName = "kt",
                    ).bufferedWriter().use { writer ->
                        writer.write(GENERATED_EMBEDDABLE_SOURCE)
                    }
                    generated = true
                    return emptyList()
                }
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
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private class LifecycleCapture {
        val rounds = mutableListOf<CapturedRound>()

        fun roundOf(qualifiedName: String): Int {
            return rounds.indexOfFirst { round -> qualifiedName in round.newDeclarationNames }
                .takeIf { round -> round >= 0 }
                ?: error("Type '$qualifiedName' did not become a round root: $this")
        }

        override fun toString(): String = rounds.withIndex().joinToString { (round, capture) ->
            "$round=${capture.newDeclarationNames.sorted()} deferred=${capture.deferredNames.sorted()}"
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this, Throwable::cause)

    private data class CapturedRound(
        val newDeclarationNames: Set<String>,
        val deferredNames: Set<String>,
    )

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val capture: LifecycleCapture,
        val logger: CapturingKspLogger,
        val kotlinOutputDir: File,
        val resourceOutputDir: File,
    ) {
        fun generatedKotlinFile(path: String): File = kotlinOutputDir.resolve(path)

        fun generatedKotlinFilesNamed(name: String): List<File> {
            return kotlinOutputDir.walkTopDown()
                .filter { file -> file.isFile && file.name == name }
                .toList()
        }

        fun generatedResourceFile(path: String): File = resourceOutputDir.resolve(path)
    }

    private companion object {
        val VALID_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Book {
                @Id
                val id: Long
            }
        """.trimIndent()

        val INVALID_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface BrokenBook {
                @Id
                val id: MissingId
            }
        """.trimIndent()

        val WEB_ANNOTATION_SOURCE = """
            package org.springframework.web.bind.annotation

            @Target(AnnotationTarget.CLASS)
            annotation class RestController

            @Target(AnnotationTarget.FUNCTION)
            annotation class GetMapping
        """.trimIndent()

        val GENERATED_MODEL_AND_API_SOURCE = """
            package demo

            import org.babyfish.jimmer.client.EnableImplicitApi
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RestController

            @EnableImplicitApi
            class ApiMarker

            @Entity
            interface GeneratedBook {
                @Id
                val id: Long
            }

            @RestController
            interface GeneratedApi {
                @GetMapping
                fun find(): GeneratedBook
            }
        """.trimIndent()

        val LATER_INVALID_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface LaterBrokenBook {
                @Id
                val id: MissingId
            }
        """.trimIndent()

        val GENERATED_EMBEDDABLE_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Embeddable

            @Embeddable
            interface Location {
                val city: String

                val zipCode: Int?
            }
        """.trimIndent()

        val DTO_SOURCE = """
            BookView {
                id
            }
        """.trimIndent()

        val MULTIPLE_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface FirstBook {
                @Id
                val id: Long
            }

            @Entity
            interface SecondBook {
                @Id
                val id: Long
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
