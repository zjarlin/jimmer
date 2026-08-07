package org.babyfish.jimmer.compiler.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerResolutionStatus
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerRoundResult
import org.babyfish.jimmer.compiler.CompilerSession
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureProvider
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenOptions
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenPrecompiler
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenSchema
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleCompilerFeatureProviderTest {

    @Test
    fun `module feature is registered after immutable dependency`() {
        val providers = JimmerCompilerFeatureProviders.load()
        val featureIds = providers.map { provider -> provider.descriptor.id }
        val module = providers.single { provider -> provider.descriptor.id == MODULE_FEATURE_ID }

        assertEquals(setOf(IMMUTABLE_FEATURE_ID), module.descriptor.dependsOn)
        assertEquals(
            setOf(ENTITIES_RESOURCE_PATH, IMMUTABLES_RESOURCE_PATH),
            module.descriptor.inputResourcePaths,
        )
        assertTrue(featureIds.indexOf(IMMUTABLE_FEATURE_ID) < featureIds.indexOf(MODULE_FEATURE_ID))
    }

    @Test
    fun `apt keeps cumulative content and renders sources only after second stable real round`() {
        val workspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val bookId = LsiSymbolId.type("demo.Book")
        val session = session("module-apt-stability")

        val first = session.execute(
            round(0, workspace, workspace, CompilerPlatform.APT)
        )
        val firstState = first.moduleState()
        assertEquals(1, firstState.stableNonFinalRoundCount)
        assertFalse(firstState.sourceReady)
        assertTrue(first.moduleArtifacts().isEmpty())
        assertEquals(
            listOf("demo.Book"),
            firstState.requireSchema().summaries.first().members.map(
                JimmerModuleSummaryMember::qualifiedTypeName
            ),
        )
        assertEquals(
            listOf(bookId),
            firstState.requireSchema().summaries.first().dependencies.originatingTypeIds,
        )

        val second = session.execute(
            round(1, workspace, LsiWorkspace.EMPTY, CompilerPlatform.APT)
        )
        val secondState = second.moduleState()
        assertEquals(2, secondState.stableNonFinalRoundCount)
        assertTrue(secondState.sourceReady)
        assertEquals(
            listOf("demo.Book"),
            secondState.requireSchema().summaries.first().members.map(
                JimmerModuleSummaryMember::qualifiedTypeName
            ),
        )
        assertTrue(
            secondState.requireSchema().summaries.all { summary ->
                summary.dependencies.originatingTypeIds.isEmpty()
            }
        )
        assertEquals(4, second.moduleArtifacts().size)
        assertTrue(second.moduleArtifacts().all { artifact -> artifact.kind == ArtifactKind.JAVA_SOURCE })

        val final = session.execute(
            round(
                number = 2,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.APT,
                isFinal = true,
            )
        )
        assertEquals(2, final.moduleArtifacts().size)
        assertTrue(final.moduleArtifacts().all { artifact -> artifact.kind == ArtifactKind.RESOURCE })
        assertTrue(final.moduleArtifacts().none { artifact -> artifact.kind.isSource })
    }

    @Test
    fun `ksp uses module renderer after stability and writes only entity resource at finish`() {
        val workspace = workspace(LsiLanguage.KOTLIN, "demo.Book")
        val options = mapOf(MODULE_REQUIRED_OPTION to "true")
        val session = session("module-ksp-stability")

        val first = session.execute(
            round(0, workspace, workspace, CompilerPlatform.KSP, options = options)
        )
        assertTrue(first.moduleArtifacts().isEmpty())

        val second = session.execute(
            round(1, workspace, LsiWorkspace.EMPTY, CompilerPlatform.KSP, options = options)
        )
        assertEquals(listOf("demo/JimmerModule.kt"), second.moduleArtifacts().map { artifact -> artifact.path })
        assertEquals(ArtifactKind.KOTLIN_SOURCE, second.moduleArtifacts().single().kind)

        val final = session.execute(
            round(
                number = 2,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.KSP,
                isFinal = true,
                options = options,
            )
        )
        assertEquals(listOf("META-INF/jimmer/entities"), final.moduleArtifacts().map { artifact -> artifact.path })
        assertEquals(ArtifactKind.RESOURCE, final.moduleArtifacts().single().kind)
    }

    @Test
    fun `changed schema restarts stability while preserving cumulative models`() {
        val bookWorkspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val storeWorkspace = workspace(LsiLanguage.JAVA, "demo.Store")
        val cumulativeWorkspace = bookWorkspace.merge(storeWorkspace)
        val session = session("module-schema-change")

        val first = session.execute(
            round(0, bookWorkspace, bookWorkspace, CompilerPlatform.APT)
        )
        assertEquals(1, first.moduleState().stableNonFinalRoundCount)
        assertTrue(first.moduleArtifacts().isEmpty())

        val changed = session.execute(
            round(
                number = 1,
                workspace = cumulativeWorkspace,
                currentWorkspace = cumulativeWorkspace,
                platform = CompilerPlatform.APT,
                currentRootTypeIds = setOf(LsiSymbolId.type("demo.Store")),
            )
        )
        val changedState = changed.moduleState()
        assertEquals(1, changedState.stableNonFinalRoundCount)
        assertTrue(changed.moduleArtifacts().isEmpty())
        assertEquals(
            listOf("demo.Book", "demo.Store"),
            changedState.requireSchema().summaries.first().members.map(
                JimmerModuleSummaryMember::qualifiedTypeName
            ),
        )
        assertEquals(
            listOf(LsiSymbolId.type("demo.Store")),
            changedState.requireSchema().summaries.first().dependencies.originatingTypeIds,
        )

        val stable = session.execute(
            round(2, cumulativeWorkspace, LsiWorkspace.EMPTY, CompilerPlatform.APT)
        )
        assertEquals(2, stable.moduleState().stableNonFinalRoundCount)
        assertEquals(4, stable.moduleArtifacts().size)
    }

    @Test
    fun `deferred and invalid immutable states block module with stable empty output`() {
        val brokenId = LsiSymbolId.type("demo.Broken")
        val deferredDependency = JimmerImmutableCompilerFeatureState(
            schema = ImmutableSchema(emptyList()),
            draftCodegenSchema = JimmerImmutableDraftCodegenSchema(
                jacksonFamily = JacksonFamily.JACKSON_2,
                types = emptyList(),
            ),
            targetTypeIds = setOf(brokenId),
            semanticRootTypeIds = setOf(brokenId),
            currentTypeIds = setOf(brokenId),
            unresolvedRootTypeIds = setOf(brokenId),
            status = CompilerResolutionStatus.DEFERRED,
        )
        val deferred = PROVIDER.precompile(featureContext(deferredDependency))
        val deferredAgain = PROVIDER.precompile(
            featureContext(
                dependencyState = deferredDependency,
                previousState = deferred.state as JimmerModuleCompilerFeatureState,
            )
        )
        val deferredState = assertIs<JimmerModuleCompilerFeatureState>(deferred.state)
        assertEquals(JimmerModuleCompilerFeatureStatus.DEPENDENCY_DEFERRED, deferredState.status)
        assertNull(deferredState.schema)
        assertEquals(deferredState.fingerprint, deferredAgain.state.fingerprint)
        assertTrue(render(deferredState, deferredDependency).artifacts.isEmpty())

        val invalidDependency = deferredDependency.copy(
            status = CompilerResolutionStatus.INVALID,
            failure = "invalid immutable",
        )
        val invalid = PROVIDER.precompile(featureContext(invalidDependency))
        val invalidState = assertIs<JimmerModuleCompilerFeatureState>(invalid.state)
        assertEquals(JimmerModuleCompilerFeatureStatus.DEPENDENCY_INVALID, invalidState.status)
        assertNull(invalidState.schema)
        assertTrue(render(invalidState, invalidDependency).artifacts.isEmpty())
    }

    @Test
    fun `unknown compiler platform blocks module before precompilation`() {
        val dependencyState = resolvedDependencyState("demo.Book")
        val result = PROVIDER.precompile(
            featureContext(
                dependencyState = dependencyState,
                platform = CompilerPlatform.UNKNOWN,
            )
        )

        val state = assertIs<JimmerModuleCompilerFeatureState>(result.state)
        assertEquals(JimmerModuleCompilerFeatureStatus.UNSUPPORTED_PLATFORM, state.status)
        assertNull(state.schema)
    }

    @Test
    fun `provider respects entry resource and module switches`() {
        val workspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val dependencyState = resolvedDependencyState("demo.Book")
        val apt = PROVIDER.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.APT,
                options = mapOf(
                    IMMUTABLES_OPTION to "DomainObjects",
                    TABLES_OPTION to "DomainTables",
                    TABLE_EXES_OPTION to "DomainTableExes",
                    FETCHERS_OPTION to "DomainFetchers",
                    IGNORE_RESOURCE_GENERATION_OPTION to "true",
                ),
            )
        )
        val aptSchema = assertIs<JimmerModuleCompilerFeatureState>(apt.state).requireSchema()
        assertEquals(
            listOf("DomainObjects", "DomainTables", "DomainTableExes", "DomainFetchers"),
            aptSchema.summaries.map(JimmerModuleSummary::simpleName),
        )
        assertTrue(aptSchema.resources.isEmpty())

        val kspWithoutModule = PROVIDER.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.KSP,
            )
        )
        assertNull(assertIs<JimmerModuleCompilerFeatureState>(kspWithoutModule.state).requireSchema().module)

        val kspWithModule = PROVIDER.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.KSP,
                options = mapOf(MODULE_REQUIRED_OPTION to "true"),
            )
        )
        assertNotNull(assertIs<JimmerModuleCompilerFeatureState>(kspWithModule.state).requireSchema().module)
    }

    @Test
    fun `provider merges trimmed existing indexes without duplicate retained types`() {
        val workspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val dependencyState = resolvedDependencyState("demo.Book")
        val result = PROVIDER.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.APT,
                inputResources = mapOf(
                    ENTITIES_RESOURCE_PATH to " legacy.Store\n\nlegacy.Store \n",
                    IMMUTABLES_RESOURCE_PATH to " legacy.Address \n",
                ),
            )
        )
        val schema = assertIs<JimmerModuleCompilerFeatureState>(result.state).requireSchema()

        assertEquals(
            listOf("demo.Book", "legacy.Store"),
            schema.resources.single { resource -> resource.kind == JimmerModuleResourceKind.ENTITIES }
                .qualifiedTypeNames,
        )
        assertEquals(
            listOf("legacy.Address"),
            schema.resources.single { resource -> resource.kind == JimmerModuleResourceKind.IMMUTABLES }
                .qualifiedTypeNames,
        )
        assertEquals(
            listOf("demo.Book", "legacy.Address", "legacy.Store"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.IMMUTABLES }
                .members.map(JimmerModuleSummaryMember::qualifiedTypeName),
        )
    }

    private fun session(id: String): CompilerSession {
        return CompilerSession(
            id = id,
            providers = listOf(JimmerImmutableCompilerFeatureProvider(), PROVIDER),
        )
    }

    private fun round(
        number: Int,
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace,
        platform: CompilerPlatform,
        currentRootTypeIds: Set<LsiSymbolId> = currentWorkspace.declarations
            .filterIsInstance<LsiTypeDeclaration>()
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
        isFinal: Boolean = false,
        options: Map<String, String> = emptyMap(),
        inputResources: Map<String, String> = emptyMap(),
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = workspace,
            currentWorkspace = currentWorkspace,
            currentRootTypeIds = currentRootTypeIds,
            platform = platform,
            isFinal = isFinal,
            options = options,
            inputResources = inputResources,
            inputDocumentSnapshots = emptyList(),
        )
    }

    private fun CompilerRoundResult.moduleState(): JimmerModuleCompilerFeatureState {
        return assertIs(featureResults.getValue(MODULE_FEATURE_ID).state)
    }

    private fun CompilerRoundResult.moduleArtifacts() =
        featureResults.getValue(MODULE_FEATURE_ID).artifacts

    private fun JimmerModuleCompilerFeatureState.requireSchema(): JimmerModuleSchema {
        return requireNotNull(schema)
    }

    private fun featureContext(
        dependencyState: JimmerImmutableCompilerFeatureState,
        previousState: JimmerModuleCompilerFeatureState? = null,
        workspace: LsiWorkspace = LsiWorkspace.EMPTY,
        platform: CompilerPlatform = CompilerPlatform.APT,
        options: Map<String, String> = emptyMap(),
        inputResources: Map<String, String> = emptyMap(),
    ): JimmerCompilerPrecompileContext {
        return JimmerCompilerPrecompileContext(
            session = CompilerSessionSnapshot("module-feature-direct", emptyList()),
            round = round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = platform,
                options = options,
                inputResources = inputResources,
            ),
            collection = JimmerCompilerFeatureCollection(),
            previousState = previousState,
            dependencyStates = mapOf(IMMUTABLE_FEATURE_ID to dependencyState),
        )
    }

    private fun render(
        state: JimmerModuleCompilerFeatureState,
        dependencyState: JimmerImmutableCompilerFeatureState,
    ) = PROVIDER.render(
        JimmerCompilerRenderContext(
            session = CompilerSessionSnapshot("module-feature-render", emptyList()),
            round = round(0, LsiWorkspace.EMPTY, LsiWorkspace.EMPTY, CompilerPlatform.APT),
            collection = JimmerCompilerFeatureCollection(),
            state = state,
            dependencyStates = mapOf(IMMUTABLE_FEATURE_ID to dependencyState),
        )
    )

    private fun resolvedDependencyState(qualifiedName: String): JimmerImmutableCompilerFeatureState {
        val typeId = LsiSymbolId.type(qualifiedName)
        val props = completeEntityProps(typeId)
        val schema = ImmutableSchema(
            listOf(
                ImmutableType(
                    id = typeId,
                    qualifiedName = qualifiedName,
                    kind = ImmutableTypeKind.ENTITY,
                    documentation = null,
                    annotations = emptyList(),
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = props,
                    primarySuperTypeId = null,
                    inheritanceRootTypeId = null,
                    inheritanceStrategy = null,
                    joinedTableDissociateAction = null,
                    instantiable = true,
                    discriminatorValue = null,
                    discriminatorPropId = null,
                    idPropId = props.single().id,
                    versionPropId = null,
                    logicalDeletedPropId = null,
                    acrossMicroServices = false,
                    microServiceName = "",
                )
            )
        )
        val workspace = workspace(LsiLanguage.JAVA, qualifiedName)
        return JimmerImmutableCompilerFeatureState(
            schema = schema,
            draftCodegenSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
                schema,
                workspace,
                JimmerImmutableDraftCodegenOptions.DEFAULT,
            ),
            targetTypeIds = setOf(typeId),
            semanticRootTypeIds = setOf(typeId),
            currentTypeIds = setOf(typeId),
        )
    }

    private fun workspace(
        language: LsiLanguage,
        vararg qualifiedNames: String,
    ): LsiWorkspace {
        val declarations = mutableListOf<LsiDeclaration>()
        val sources = mutableListOf<LsiSource>()
        qualifiedNames.forEach { qualifiedName ->
            val source = LsiSource.of(
                "src/main/${language.name.lowercase()}/${qualifiedName.replace('.', '/')}.${language.extension()}",
                language,
            )
            sources += source
            val typeId = LsiSymbolId.type(qualifiedName)
            val idPropId = LsiSymbolId.property(typeId, "id")
            val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
            declarations += LsiTypeDeclaration(
                id = typeId,
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                memberIds = listOf(idPropId),
                annotations = listOf(LsiAnnotation(ENTITY)),
                origin = origin,
            )
            declarations += LsiProperty(
                id = idPropId,
                name = "id",
                ownerId = typeId,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                annotations = listOf(LsiAnnotation(ID)),
                origin = origin,
            )
        }
        return LsiWorkspace(sources = sources, declarations = declarations)
    }

    private fun LsiLanguage.extension(): String {
        return when (this) {
            LsiLanguage.JAVA -> "java"
            LsiLanguage.KOTLIN -> "kt"
            LsiLanguage.UNKNOWN -> "txt"
        }
    }

    private companion object {
        const val MODULE_FEATURE_ID = "module"
        const val IMMUTABLE_FEATURE_ID = "immutable"
        const val IMMUTABLES_OPTION = "jimmer.entry.immutables"
        const val TABLES_OPTION = "jimmer.entry.tables"
        const val TABLE_EXES_OPTION = "jimmer.entry.tableExes"
        const val FETCHERS_OPTION = "jimmer.entry.fetchers"
        const val MODULE_REQUIRED_OPTION = "jimmer.immutable.isModuleRequired"
        const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"
        const val ENTITIES_RESOURCE_PATH = "META-INF/jimmer/entities"
        const val IMMUTABLES_RESOURCE_PATH = "META-INF/jimmer/immutables"
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val PROVIDER = JimmerModuleCompilerFeatureProvider()
    }
}
