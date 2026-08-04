package org.babyfish.jimmer.compiler.lsi.apt

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
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.JimmerCompilerTypeSeedContext
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
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
        val provider = InputDocumentFeatureProvider("apt-error-state-test")
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
            task.setProcessors(listOf(DriverProcessor(provider)))
            task.call()
        }

        assertFalse(success)
        assertTrue(provider.rounds.last().isFinal)
        assertTrue(provider.rounds.last().frontendDeferred)
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
        val provider = TypeSeedFeatureProvider()
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
            task.setProcessors(listOf(DriverProcessor(provider)))
            task.call()
        }

        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        val firstRound = provider.rounds.first()
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
        assertTrue(provider.rounds.size >= 3)
        assertEquals("export Model", provider.rounds.first().inputDocumentSnapshots.single().document.content)
        assertEquals(
            provider.rounds.first().inputDocumentSnapshots.single(),
            provider.rounds.last().inputDocumentSnapshots.single(),
        )
        val firstRoundProperty = assertIs<LsiProperty>(provider.rounds.first().workspace[PROPERTY_ID])
        assertIs<LsiPackageAnnotationScope>(
            provider.rounds.first().workspace.annotationScope(LsiSymbolId.packageScope("demo")),
        )
        assertIs<LsiUnresolvedType>(firstRoundProperty.type)
        assertTrue(provider.rounds.first().frontendDeferred)
        assertEquals(
            "isActive",
            assertIs<LsiProperty>(provider.rounds.first().workspace[ACTIVE_PROPERTY_ID]).name,
        )
        val refreshedRound = provider.rounds.single { round -> round.number == 1 }
        assertFalse(refreshedRound.frontendDeferred)
        assertTrue(refreshedRound.currentWorkspace.contains(MODEL_ID))
        assertEquals(setOf(MODEL_ID, GENERATED_ID), refreshedRound.currentRootTypeIds)
        assertEquals(
            GENERATED_ID,
            assertIs<LsiDeclaredType>(
                assertIs<LsiProperty>(refreshedRound.currentWorkspace[PROPERTY_ID]).type
            ).declarationId,
        )
        assertTrue(provider.rounds.last().isFinal)
        assertFalse(provider.rounds.last().frontendDeferred)
        assertTrue(provider.rounds.last().workspace.contains(MODEL_ID))
        assertTrue(provider.rounds.last().currentWorkspace.declarations.isEmpty())
        assertTrue(provider.rounds.last().currentRootTypeIds.isEmpty())
        assertEquals(setOf(JAVA_STRING_ID), provider.rounds.first().availableTypeIds)
        assertEquals(setOf(JAVA_STRING_ID), provider.rounds.last().availableTypeIds)
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
        val provider = InputDocumentFeatureProvider("apt-document-seeds")
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
            task.setProcessors(listOf(DriverProcessor(provider)))
            task.call()
        }

        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        val workspace = provider.rounds.first().workspace
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
        private val provider: JimmerCompilerFeatureProvider,
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

    private class InputDocumentFeatureProvider(
        id: String,
    ) : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = id,
            inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
        )

        val rounds = mutableListOf<org.babyfish.jimmer.compiler.CompilerRound>()

        override fun collect(
            context: org.babyfish.jimmer.compiler.JimmerCompilerCollectContext,
        ): JimmerCompilerFeatureCollection {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
            return JimmerCompilerFeatureCollection()
        }
    }

    private class TypeSeedFeatureProvider : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id = "apt-type-seed-test")

        val rounds = mutableListOf<org.babyfish.jimmer.compiler.CompilerRound>()

        override fun requestTypeSeeds(
            context: JimmerCompilerTypeSeedContext,
        ): Collection<LsiTypeSeed> {
            val charSequence = context.round.workspace[CHAR_SEQUENCE_ID] as? LsiTypeDeclaration
            return if (charSequence?.memberIds.isNullOrEmpty()) {
                listOf(LsiTypeSeed(CHAR_SEQUENCE_ID, LsiTypeSeedMode.FULL_DECLARATION))
            } else {
                listOf(LsiTypeSeed(RUNNABLE_ID, LsiTypeSeedMode.FULL_DECLARATION))
            }
        }

        override fun collect(
            context: org.babyfish.jimmer.compiler.JimmerCompilerCollectContext,
        ): JimmerCompilerFeatureCollection {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
            return JimmerCompilerFeatureCollection()
        }
    }

    private class DriverFeatureProvider : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = "apt-driver-test",
            classpathTypeIds = setOf(JAVA_STRING_ID, MISSING_TYPE_ID),
            inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
        )

        val rounds = mutableListOf<org.babyfish.jimmer.compiler.CompilerRound>()

        override fun collect(
            context: org.babyfish.jimmer.compiler.JimmerCompilerCollectContext,
        ): JimmerCompilerFeatureCollection {
            if (rounds.lastOrNull()?.number != context.round.number) {
                rounds += context.round
            }
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
        val JAVA_STRING_ID = LsiSymbolId.type("java.lang.String")
        val MISSING_TYPE_ID = LsiSymbolId.type("missing.NotThere")
        val CHAR_SEQUENCE_ID = LsiSymbolId.type("java.lang.CharSequence")
        val RUNNABLE_ID = LsiSymbolId.type("java.lang.Runnable")
    }
}
