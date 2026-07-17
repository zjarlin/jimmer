package org.babyfish.jimmer.compiler.lsi.apt

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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiUnresolvedType

class AptLsiCompilerDriverTest {

    @Test
    fun `freezes real rounds anchors diagnostics and writes final resources`() {
        val projectDir = createTempDirectory(prefix = "jimmer-lsi-apt-driver-test").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        val generatedDir = projectDir.resolve("build/generated")
        val sourceFile = sourceDir.resolve("demo/Model.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
                package demo;

                interface Model {
                    Generated value();

                    boolean isActive();
                }
            """.trimIndent(),
        )
        classesDir.mkdirs()
        generatedDir.mkdirs()

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val provider = DriverFeatureProvider()
        val processor = DriverProcessor(provider)
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
                    "-Ajimmer.keepIsPrefix=true",
                    "-classpath",
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }

        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        assertTrue(generatedDir.resolve("demo/ModelGenerated.java").isFile)
        assertEquals(
            "final",
            classesDir.resolve("META-INF/jimmer/driver-final").readText(),
        )
        assertTrue(provider.rounds.size >= 3)
        val firstRoundProperty = assertIs<LsiProperty>(provider.rounds.first().workspace[PROPERTY_ID])
        assertIs<LsiUnresolvedType>(firstRoundProperty.type)
        assertEquals(
            "isActive",
            assertIs<LsiProperty>(provider.rounds.first().workspace[ACTIVE_PROPERTY_ID]).name,
        )
        val refreshedRound = provider.rounds.single { round -> round.number == 1 }
        assertTrue(refreshedRound.currentWorkspace.contains(MODEL_ID))
        assertEquals(
            GENERATED_ID,
            assertIs<LsiDeclaredType>(
                assertIs<LsiProperty>(refreshedRound.currentWorkspace[PROPERTY_ID]).type
            ).declarationId,
        )
        assertTrue(provider.rounds.last().isFinal)
        assertTrue(provider.rounds.last().workspace.contains(MODEL_ID))
        assertTrue(provider.rounds.last().currentWorkspace.declarations.isEmpty())
        val warning = diagnostics.diagnostics.single { diagnostic ->
            diagnostic.kind == Diagnostic.Kind.WARNING &&
                diagnostic.getMessage(null).contains("[driver.warning]")
        }
        assertTrue(warning.source.name.endsWith("demo/Model.java"))
        assertTrue(warning.lineNumber > 0)
    }

    private class DriverProcessor(
        private val provider: DriverFeatureProvider,
    ) : AbstractProcessor() {
        private lateinit var driver: AptLsiCompilerDriver

        override fun init(processingEnvironment: javax.annotation.processing.ProcessingEnvironment) {
            super.init(processingEnvironment)
            driver = AptLsiCompilerDriver(
                processingEnvironment = processingEnvironment,
                providers = listOf(provider),
                sessionId = "apt-driver-test",
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

    private class DriverFeatureProvider : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor("apt-driver-test")

        val rounds = mutableListOf<org.babyfish.jimmer.compiler.CompilerRound>()

        override fun collect(
            context: org.babyfish.jimmer.compiler.JimmerCompilerCollectContext,
        ): JimmerCompilerFeatureCollection {
            rounds += context.round
            return JimmerCompilerFeatureCollection()
        }

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(
                state = DriverFeatureState("${context.round.number}:${context.round.isFinal}"),
                unresolvedSymbols = if (context.round.number == 0) setOf(PROPERTY_ID) else emptySet(),
            )
        }

        override fun render(
            context: JimmerCompilerRenderContext,
        ): JimmerCompilerFeatureRenderResult {
            if (context.round.isFinal) {
                return JimmerCompilerFeatureRenderResult(
                    artifacts = listOf(
                        GeneratedArtifact.create(
                            kind = ArtifactKind.RESOURCE,
                            path = "META-INF/jimmer/driver-final",
                            content = "final",
                            aggregationMode = ArtifactAggregationMode.AGGREGATING,
                        ),
                    ),
                )
            }
            if (!context.round.currentWorkspace.contains(MODEL_ID)) {
                return JimmerCompilerFeatureRenderResult()
            }
            if (context.round.number == 0) {
                return JimmerCompilerFeatureRenderResult(
                    artifacts = listOf(
                        GeneratedArtifact.source(
                            kind = ArtifactKind.JAVA_SOURCE,
                            qualifiedName = "demo.Generated",
                            content = "package demo; public interface Generated {}",
                            aggregationMode = ArtifactAggregationMode.ISOLATING,
                            originatingSymbols = setOf(PROPERTY_ID),
                        ),
                    ),
                )
            }
            return JimmerCompilerFeatureRenderResult(
                artifacts = listOf(
                    GeneratedArtifact.source(
                        kind = ArtifactKind.JAVA_SOURCE,
                        qualifiedName = "demo.ModelGenerated",
                        content = "package demo; public interface ModelGenerated {}",
                        aggregationMode = ArtifactAggregationMode.ISOLATING,
                        originatingSymbols = setOf(PROPERTY_ID),
                    ),
                ),
                diagnostics = listOf(
                    LsiDiagnostic(
                        code = "driver.warning",
                        severity = LsiDiagnosticSeverity.WARNING,
                        message = "model generated",
                        symbolId = PROPERTY_ID,
                    ),
                ),
            )
        }
    }

    private data class DriverFeatureState(
        override val fingerprint: String,
    ) : JimmerCompilerFeatureState

    private companion object {
        val MODEL_ID = LsiSymbolId.type("demo.Model")
        val GENERATED_ID = LsiSymbolId.type("demo.Generated")
        val PROPERTY_ID = LsiSymbolId.property(MODEL_ID, "value")
        val ACTIVE_PROPERTY_ID = LsiSymbolId.property(MODEL_ID, "isActive")
    }
}
