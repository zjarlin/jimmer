package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableCompilerFeatureProviderTest {

    @Test
    fun `source filter excludes generation target but preserves mapped superclass semantics`() {
        val baseId = LsiSymbolId.type("demo.shared.BaseSwitch")
        val childId = LsiSymbolId.type("demo.api.Switch")
        val basePropId = LsiSymbolId.property(baseId, "status")
        val childPropId = LsiSymbolId.property(childId, "status")
        val workspace = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                immutableType(baseId, MAPPED_SUPERCLASS, listOf(basePropId)),
                property(baseId, basePropId, listOf(default("0"))),
                immutableType(
                    childId,
                    ENTITY,
                    listOf(childPropId),
                    listOf(LsiDeclaredType(baseId)),
                ),
                property(
                    ownerId = childId,
                    id = childPropId,
                    annotations = listOf(default("1")),
                    overrides = listOf(LsiOverride(basePropId)),
                ),
            ),
        )

        val result = PROVIDER.precompile(
            context(
                workspace = workspace,
                options = mapOf("jimmer.source.includes" to "demo.api"),
            )
        )
        val state = assertIs<JimmerImmutableCompilerFeatureState>(result.state)

        assertEquals(JimmerImmutableCompilerFeatureStatus.RESOLVED, state.status)
        assertEquals(setOf(childId), state.targetTypeIds)
        assertEquals(setOf(childId), result.processedSymbols)
        assertEquals(setOf(baseId, childId), state.schema.types.mapTo(sortedSetOf()) { type -> type.id })
        val status = state.schema.types.single { type -> type.id == childId }.props.single()
        assertEquals("1", status.annotationString(DEFAULT, "value"))
    }

    @Test
    fun `apt defers unresolved immutable root and final round reports it`() {
        val workspace = unresolvedWorkspace()

        val deferred = PROVIDER.precompile(context(workspace, platform = CompilerPlatform.APT))
        val deferredState = assertIs<JimmerImmutableCompilerFeatureState>(deferred.state)
        assertEquals(JimmerImmutableCompilerFeatureStatus.DEFERRED, deferredState.status)
        assertEquals(setOf(BROKEN_ID), deferred.unresolvedSymbols)
        assertTrue(deferred.diagnostics.isEmpty())

        val final = PROVIDER.precompile(
            context(
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.APT,
                isFinal = true,
            )
        )
        val finalState = assertIs<JimmerImmutableCompilerFeatureState>(final.state)
        assertEquals(JimmerImmutableCompilerFeatureStatus.INVALID, finalState.status)
        assertTrue(final.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.immutable.unresolved", final.diagnostics.single().code)
    }

    @Test
    fun `ksp never defers a valid unresolved immutable declaration`() {
        val result = PROVIDER.precompile(
            context(unresolvedWorkspace(KOTLIN_ORIGIN), platform = CompilerPlatform.KSP)
        )

        assertTrue(result.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.immutable.unresolved", result.diagnostics.single().code)
        assertEquals(
            JimmerImmutableCompilerFeatureStatus.INVALID,
            assertIs<JimmerImmutableCompilerFeatureState>(result.state).status,
        )
    }

    @Test
    fun `current dependency closure does not pollute processed immutable roots`() {
        val firstId = LsiSymbolId.type("demo.First")
        val secondId = LsiSymbolId.type("demo.Second")
        val firstWorkspace = LsiWorkspace(
            sources = listOf(KOTLIN_SOURCE),
            declarations = listOf(immutableType(firstId, ENTITY, origin = KOTLIN_ORIGIN)),
        )
        val secondWorkspace = LsiWorkspace(
            sources = listOf(SECOND_SOURCE),
            declarations = listOf(immutableType(secondId, ENTITY, origin = SECOND_ORIGIN)),
        )
        val cumulative = firstWorkspace.merge(secondWorkspace)

        val result = PROVIDER.precompile(
            context(
                workspace = cumulative,
                currentWorkspace = cumulative,
                currentRootTypeIds = setOf(secondId),
                platform = CompilerPlatform.KSP,
            )
        )
        val state = assertIs<JimmerImmutableCompilerFeatureState>(result.state)

        assertEquals(setOf(firstId, secondId), state.targetTypeIds)
        assertEquals(setOf(secondId), state.currentTypeIds)
        assertEquals(setOf(secondId), result.processedSymbols)
    }

    @Test
    fun `binary dto subject and model roots enrich schema without becoming generation roots`() {
        val localId = LsiSymbolId.type("demo.LocalModel")
        val binaryBaseId = LsiSymbolId.type("demo.BinaryBook")
        val binaryBranchId = LsiSymbolId.type("demo.BinarySpecialBook")
        val localWorkspace = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(immutableType(localId, ENTITY)),
        )
        val binarySource = LsiSource.of(
            path = "dependencies/demo-models.jar",
            kind = LsiSourceKind.BINARY,
        )
        val binaryOrigin = LsiOrigin(LsiOriginKind.BINARY, binarySource)
        val binaryWorkspace = LsiWorkspace(
            sources = listOf(binarySource),
            declarations = listOf(
                immutableType(binaryBaseId, ENTITY, origin = binaryOrigin),
                immutableType(binaryBranchId, ENTITY, origin = binaryOrigin),
            ),
        )
        val snapshot = REFERENCE_FREEZER.freeze(
            CompilerInputDocument(
                kind = CompilerInputDocumentKind.DTO,
                sourceSet = CompilerSourceSet.MAIN,
                projectName = "demo-project",
                sourceRoot = "src/main/dto",
                relativePath = "demo/BinaryBook.dto",
                content = """
                    export demo.BinaryBook
                    BinaryBookView {
                        #types {
                            demo.BinarySpecialBook {}
                        }
                    }
                """.trimIndent(),
            )
        )

        val result = PROVIDER.precompile(
            context(
                workspace = localWorkspace.merge(binaryWorkspace),
                currentWorkspace = localWorkspace,
                currentRootTypeIds = setOf(localId),
                inputDocumentSnapshots = listOf(snapshot),
            )
        )
        val state = assertIs<JimmerImmutableCompilerFeatureState>(result.state)

        assertEquals(JimmerImmutableCompilerFeatureStatus.RESOLVED, state.status)
        assertEquals(setOf(localId), state.targetTypeIds)
        assertEquals(setOf(localId), state.currentTypeIds)
        assertEquals(setOf(localId), result.processedSymbols)
        assertEquals(
            setOf(localId, binaryBaseId, binaryBranchId),
            state.semanticRootTypeIds,
        )
        assertEquals(
            setOf(localId, binaryBaseId, binaryBranchId),
            state.schema.types.mapTo(sortedSetOf()) { type -> type.id },
        )
    }

    @Test
    fun `apt excludes kapt stubs carrying kotlin metadata`() {
        val typeId = LsiSymbolId.type("demo.KotlinModel")
        val workspace = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                immutableType(
                    id = typeId,
                    marker = ENTITY,
                    annotations = listOf(LsiAnnotation(KOTLIN_METADATA)),
                )
            ),
        )

        val result = PROVIDER.precompile(context(workspace, platform = CompilerPlatform.APT))
        val state = assertIs<JimmerImmutableCompilerFeatureState>(result.state)

        assertTrue(state.targetTypeIds.isEmpty())
        assertTrue(result.processedSymbols.isEmpty())
        assertFalse(state.schema.types.any { type -> type.id == typeId })
    }

    @Test
    fun `immutable feature is registered as a dependency-free shared stage`() {
        val descriptor = JimmerCompilerFeatureProviders.load()
            .single { provider -> provider.descriptor.id == "immutable" }
            .descriptor

        assertTrue(descriptor.dependsOn.isEmpty())
    }

    private fun context(
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace = workspace,
        currentRootTypeIds: Set<LsiSymbolId> = currentWorkspace.declarations
            .filterIsInstance<LsiTypeDeclaration>()
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
        platform: CompilerPlatform = CompilerPlatform.APT,
        isFinal: Boolean = false,
        options: Map<String, String> = emptyMap(),
        inputDocumentSnapshots: List<CompilerInputDocumentSnapshot> = emptyList(),
    ): JimmerCompilerPrecompileContext {
        return JimmerCompilerPrecompileContext(
            session = CompilerSessionSnapshot("immutable-feature-test", emptyList()),
            round = CompilerRound(
                number = 0,
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                currentRootTypeIds = currentRootTypeIds,
                platform = platform,
                isFinal = isFinal,
                options = options,
                inputDocumentSnapshots = inputDocumentSnapshots,
            ),
            collection = JimmerCompilerFeatureCollection(),
            previousState = null,
            dependencyStates = emptyMap(),
        )
    }

    private fun unresolvedWorkspace(origin: LsiOrigin = ORIGIN): LsiWorkspace {
        val propId = LsiSymbolId.property(BROKEN_ID, "value")
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = listOf(
                immutableType(BROKEN_ID, ENTITY, listOf(propId), origin = origin),
                LsiProperty(
                    id = propId,
                    name = "value",
                    ownerId = BROKEN_ID,
                    type = LsiUnresolvedType("demo.GeneratedValue"),
                    origin = origin,
                ),
            ),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        marker: LsiSymbolId,
        memberIds: List<LsiSymbolId> = emptyList(),
        superTypes: List<LsiDeclaredType> = emptyList(),
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = ORIGIN,
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = listOf(LsiAnnotation(marker)) + annotations,
            origin = origin,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        id: LsiSymbolId,
        annotations: List<LsiAnnotation>,
        overrides: List<LsiOverride> = emptyList(),
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = id.value.substringAfterLast('/'),
            ownerId = ownerId,
            type = LsiDeclaredType(STRING),
            annotations = annotations,
            overrides = overrides,
            origin = ORIGIN,
        )
    }

    private fun default(value: String): LsiAnnotation {
        return LsiAnnotation(
            type = DEFAULT,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue(value),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
    }

    private fun JimmerImmutableProp.annotationString(
        annotationType: LsiSymbolId,
        argumentName: String,
    ): String? {
        val annotation = annotations.singleOrNull { item -> item.type == annotationType } ?: return null
        return (annotation.arguments[argumentName]?.value as? LsiAnnotationValue.StringValue)?.value
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/java/demo/Model.java", LsiLanguage.JAVA)
        val KOTLIN_SOURCE = LsiSource.of("src/main/kotlin/demo/Model.kt", LsiLanguage.KOTLIN)
        val SECOND_SOURCE = LsiSource.of("build/generated/ksp/demo/Second.kt", LsiLanguage.KOTLIN)
        val ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, SOURCE)
        val KOTLIN_ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, KOTLIN_SOURCE)
        val SECOND_ORIGIN = LsiOrigin(LsiOriginKind.GENERATED, SECOND_SOURCE)
        val PROVIDER = JimmerImmutableCompilerFeatureProvider()
        val BROKEN_ID = LsiSymbolId.type("demo.Broken")
        val STRING = LsiSymbolId.type("java.lang.String")
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val MAPPED_SUPERCLASS = LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
        val DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        val KOTLIN_METADATA = LsiSymbolId.type("kotlin.Metadata")
        val REFERENCE_FREEZER = CompilerInputDocumentReferenceFreezer()
    }
}
