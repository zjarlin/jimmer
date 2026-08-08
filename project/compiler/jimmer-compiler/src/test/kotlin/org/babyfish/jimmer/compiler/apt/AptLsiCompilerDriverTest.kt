package org.babyfish.jimmer.compiler.apt

import site.addzero.lsi.jimmer.input.*

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.CompilerTypeSeedContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPackageAnnotationScope
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiUnresolvedType
import org.babyfish.jimmer.compiler.input.JimmerCompilerWiring
import site.addzero.lsi.apt.AptLsiCompilerDriver

class AptLsiCompilerDriverTest {

    @Test
    fun `keeps javac error state through the final round`() {
        val projectDir = createTempDirectory(prefix = "jimmer-lsi-apt-error-state").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val sourceFile = sourceDir.resolve("demo/Broken.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText("package demo; interface Broken { Missing value(); }")
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val feature = InputDocumentFeature()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", System.getProperty("java.class.path")),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(DriverProcessor(feature)))
            task.call()
        }

        assertFalse(success)
        assertTrue(feature.rounds.last().isFinal)
        assertTrue(feature.rounds.last().frontendDeferred)
    }

    @Test
    fun `freezes feature requested classpath declaration closure in current round`() {
        val projectDir = createTempDirectory(prefix = "jimmer-lsi-apt-type-seeds").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        val generatedDir = projectDir.resolve("build/generated")
        val sourceFile = sourceDir.resolve("demo/Service.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText("package demo; interface Service { CharSequence value(); }")
        classesDir.mkdirs()
        generatedDir.mkdirs()

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val feature = TypeSeedFeature()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", System.getProperty("java.class.path")),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(DriverProcessor(feature)))
            task.call()
        }

        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        val firstRound = feature.rounds.first()
        assertTrue(!firstRound.isFinal)
        assertTrue(
            assertIs<LsiTypeDeclaration>(firstRound.workspace[CHAR_SEQUENCE_ID]).memberIds.isNotEmpty(),
        )
        assertTrue(
            assertIs<LsiTypeDeclaration>(firstRound.workspace[RUNNABLE_ID]).memberIds.isNotEmpty(),
        )
    }

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
        val packageInfoFile = sourceDir.resolve("demo/package-info.java")
        packageInfoFile.writeText(
            """
                @Deprecated
                package demo;
            """.trimIndent(),
        )
        projectDir.resolve("src/main/dto/Model.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText("export Model")
        }
        classesDir.mkdirs()
        generatedDir.mkdirs()

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val feature = DriverFeature()
        val processor = DriverProcessor(feature)
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
                fileManager.getJavaFileObjects(sourceFile, packageInfoFile),
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
        assertTrue(feature.rounds.size >= 3)
        assertEquals("export Model", feature.rounds.first().inputDocumentSnapshots.single().document.content)
        assertEquals(
            feature.rounds.first().inputDocumentSnapshots.single(),
            feature.rounds.last().inputDocumentSnapshots.single(),
        )
        val firstRoundProperty = assertIs<LsiProperty>(feature.rounds.first().workspace[PROPERTY_ID])
        assertIs<LsiPackageAnnotationScope>(
            feature.rounds.first().workspace.annotationScope(LsiSymbolId.packageScope("demo")),
        )
        assertIs<LsiUnresolvedType>(firstRoundProperty.type)
        assertTrue(feature.rounds.first().frontendDeferred)
        assertEquals(
            "isActive",
            assertIs<LsiProperty>(feature.rounds.first().workspace[ACTIVE_PROPERTY_ID]).name,
        )
        val refreshedRound = feature.rounds.single { round -> round.number == 1 }
        assertFalse(refreshedRound.frontendDeferred)
        assertTrue(refreshedRound.currentWorkspace.contains(MODEL_ID))
        assertEquals(setOf(MODEL_ID, GENERATED_ID), refreshedRound.currentRootTypeIds)
        assertEquals(
            GENERATED_ID,
            assertIs<LsiDeclaredType>(
                assertIs<LsiProperty>(refreshedRound.currentWorkspace[PROPERTY_ID]).type
            ).declarationId,
        )
        assertTrue(feature.rounds.last().isFinal)
        assertFalse(feature.rounds.last().frontendDeferred)
        assertTrue(feature.rounds.last().workspace.contains(MODEL_ID))
        assertTrue(feature.rounds.last().currentWorkspace.declarations.isEmpty())
        assertTrue(feature.rounds.last().currentRootTypeIds.isEmpty())
        assertEquals(setOf(JAVA_STRING_ID), feature.rounds.first().availableTypeIds)
        assertEquals(setOf(JAVA_STRING_ID), feature.rounds.last().availableTypeIds)
        val warning = diagnostics.diagnostics.single { diagnostic ->
            diagnostic.kind == Diagnostic.Kind.WARNING &&
                diagnostic.getMessage(null).contains("[driver.warning]")
        }
        assertTrue(warning.source.name.endsWith("demo/Model.java"))
        assertTrue(warning.lineNumber > 0)
    }

    @Test
    fun `freezes types referenced only by dto documents`() {
        val projectDir = createTempDirectory(prefix = "jimmer-lsi-apt-document-seeds").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        val generatedDir = projectDir.resolve("build/generated")
        val sourceFile = sourceDir.resolve("demo/Model.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText("package demo; interface Model {}")
        projectDir.resolve("src/main/dto/Model.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    export demo.Model
                    @java.lang.Deprecated
                    ModelView implements java.lang.Runnable {
                        value: java.lang.CharSequence
                        retention: java.lang.annotation.RetentionPolicy
                    }
                """.trimIndent(),
            )
        }
        classesDir.mkdirs()
        generatedDir.mkdirs()

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val feature = InputDocumentFeature()
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
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(DriverProcessor(feature)))
            task.call()
        }

        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        val workspace = feature.rounds.first().workspace
        assertTrue(workspace.contains(LsiSymbolId.type("demo.Model")))
        assertTrue(workspace.contains(LsiSymbolId.type("java.lang.Deprecated")))
        assertTrue(workspace.contains(LsiSymbolId.type("java.lang.Runnable")))
        assertTrue(workspace.contains(LsiSymbolId.type("java.lang.CharSequence")))
        assertTrue(
            assertIs<site.addzero.lsi.model.LsiTypeDeclaration>(
                workspace[LsiSymbolId.type("java.lang.Runnable")],
            ).memberIds.isNotEmpty(),
        )
        assertTrue(
            assertIs<site.addzero.lsi.model.LsiTypeDeclaration>(
                workspace[LsiSymbolId.type("java.lang.CharSequence")],
            ).memberIds.isEmpty(),
        )
        assertEquals(
            listOf("SOURCE", "CLASS", "RUNTIME"),
            assertIs<site.addzero.lsi.model.LsiTypeDeclaration>(
                workspace[LsiSymbolId.type("java.lang.annotation.RetentionPolicy")],
            ).enumEntries.map { entry -> entry.name },
        )
    }

    private class DriverProcessor(
        private val feature: CompilerFeature<*, *>,
    ) : AbstractProcessor() {
        private lateinit var driver: AptLsiCompilerDriver

        override fun init(processingEnvironment: javax.annotation.processing.ProcessingEnvironment) {
            super.init(processingEnvironment)
            driver = AptLsiCompilerDriver(
                processingEnvironment = processingEnvironment,
                features = listOf(feature),
                wiring = JimmerCompilerWiring,
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

    private class InputDocumentFeature : EmptyFeature() {
        override val key = Key

        override val metadata = CompilerFeatureMetadata(
            inputDocumentKinds = setOf(DTO_INPUT_DOCUMENT_KIND),
        )

        val rounds = mutableListOf<site.addzero.lsi.compiler.CompilerRound>()

        override fun collect(
            context: site.addzero.lsi.compiler.CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        companion object {
            val Key = compilerFeatureKey<
                InputDocumentFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class TypeSeedFeature : EmptyFeature() {
        override val key = Key

        val rounds = mutableListOf<site.addzero.lsi.compiler.CompilerRound>()

        override fun requestTypeSeeds(
            context: CompilerTypeSeedContext,
        ): Collection<LsiTypeSeed> {
            val charSequence = context.round.workspace[CHAR_SEQUENCE_ID] as? LsiTypeDeclaration
            return if (charSequence?.memberIds.isNullOrEmpty()) {
                listOf(LsiTypeSeed(CHAR_SEQUENCE_ID, LsiTypeSeedMode.FULL_DECLARATION))
            } else {
                listOf(LsiTypeSeed(RUNNABLE_ID, LsiTypeSeedMode.FULL_DECLARATION))
            }
        }

        override fun collect(
            context: site.addzero.lsi.compiler.CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        companion object {
            val Key = compilerFeatureKey<
                TypeSeedFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class DriverFeature : CompilerFeature<EmptyCompilerFeatureState, DriverFeatureState> {
        override val key = Key

        override val metadata = CompilerFeatureMetadata(
            classpathTypeIds = setOf(JAVA_STRING_ID, MISSING_TYPE_ID),
            inputDocumentKinds = setOf(DTO_INPUT_DOCUMENT_KIND),
        )

        val rounds = mutableListOf<site.addzero.lsi.compiler.CompilerRound>()

        override fun collect(
            context: site.addzero.lsi.compiler.CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, DriverFeatureState>,
        ): CompilerFeaturePrecompileResult<DriverFeatureState> {
            return CompilerFeaturePrecompileResult(
                state = DriverFeatureState("${context.round.number}:${context.round.isFinal}"),
                unresolvedSymbols = if (context.round.number == 0) setOf(PROPERTY_ID) else emptySet(),
            )
        }

        override fun render(
            context: CompilerRenderContext<EmptyCompilerFeatureState, DriverFeatureState>,
        ): CompilerFeatureRenderResult {
            if (context.round.isFinal) {
                return CompilerFeatureRenderResult(
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
                return CompilerFeatureRenderResult()
            }
            if (context.round.number == 0) {
                return CompilerFeatureRenderResult(
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
            return CompilerFeatureRenderResult(
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

        companion object {
            val Key = compilerFeatureKey<
                DriverFeature,
                EmptyCompilerFeatureState,
                DriverFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private abstract class EmptyFeature : CompilerFeature<
        EmptyCompilerFeatureState,
        EmptyCompilerFeatureState,
    > {
        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }
    }

    private data class DriverFeatureState(
        override val fingerprint: String,
    ) : CompilerFeatureState

    private companion object {
        val MODEL_ID = LsiSymbolId.type("demo.Model")
        val GENERATED_ID = LsiSymbolId.type("demo.Generated")
        val PROPERTY_ID = LsiSymbolId.property(MODEL_ID, "value")
        val ACTIVE_PROPERTY_ID = LsiSymbolId.property(MODEL_ID, "isActive")
        val JAVA_STRING_ID = LsiSymbolId.type("java.lang.String")
        val MISSING_TYPE_ID = LsiSymbolId.type("missing.NotThere")
        val CHAR_SEQUENCE_ID = LsiSymbolId.type("java.lang.CharSequence")
        val RUNNABLE_ID = LsiSymbolId.type("java.lang.Runnable")
    }
}
