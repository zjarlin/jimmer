package org.babyfish.jimmer.compiler.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerRoundResult
import site.addzero.lsi.compiler.CompilerSession
import site.addzero.lsi.compiler.CompilerSessionSnapshot
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeatureLoader
import site.addzero.lsi.compiler.CompilerFeatureStates
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import org.babyfish.jimmer.compiler.immutable.ImmutableFeatureState
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

class ModuleFeatureTest {

    @Test
    fun `module feature is registered after immutable dependency`() {
        val features = CompilerFeatureLoader.load()
        val featureKeys = features.map { feature -> feature.key }
        val module = features.single { feature -> feature.key == ModuleFeature.Key }

        assertEquals(setOf(ImmutableFeature.Key), module.dependencies)
        assertEquals(
            setOf(ENTITIES_RESOURCE_PATH, IMMUTABLES_RESOURCE_PATH),
            module.metadata.inputResourcePaths,
        )
        assertTrue(featureKeys.indexOf(ImmutableFeature.Key) < featureKeys.indexOf(ModuleFeature.Key))
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
        val deferredDependency = ImmutableFeatureState(
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
        val deferred = FEATURE.precompile(featureContext(deferredDependency))
        val deferredAgain = FEATURE.precompile(
            featureContext(
                dependencyState = deferredDependency,
                previousState = deferred.state,
            )
        )
        val deferredState = deferred.state
        assertEquals(ModuleFeatureStatus.DEPENDENCY_DEFERRED, deferredState.status)
        assertNull(deferredState.schema)
        assertEquals(deferredState.fingerprint, deferredAgain.state.fingerprint)
        assertTrue(render(deferredState, deferredDependency).artifacts.isEmpty())

        val invalidDependency = deferredDependency.copy(
            status = CompilerResolutionStatus.INVALID,
            failure = "invalid immutable",
        )
        val invalid = FEATURE.precompile(featureContext(invalidDependency))
        val invalidState = invalid.state
        assertEquals(ModuleFeatureStatus.DEPENDENCY_INVALID, invalidState.status)
        assertNull(invalidState.schema)
        assertTrue(render(invalidState, invalidDependency).artifacts.isEmpty())
    }

    @Test
    fun `unknown compiler platform blocks module before precompilation`() {
        val dependencyState = resolvedDependencyState("demo.Book")
        val result = FEATURE.precompile(
            featureContext(
                dependencyState = dependencyState,
                platform = CompilerPlatform.UNKNOWN,
            )
        )

        val state = result.state
        assertEquals(ModuleFeatureStatus.UNSUPPORTED_PLATFORM, state.status)
        assertNull(state.schema)
    }

    @Test
    fun `feature respects entry resource and module switches`() {
        val workspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val dependencyState = resolvedDependencyState("demo.Book")
        val apt = FEATURE.precompile(
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
        val aptSchema = apt.state.requireSchema()
        assertEquals(
            listOf("DomainObjects", "DomainTables", "DomainTableExes", "DomainFetchers"),
            aptSchema.summaries.map(JimmerModuleSummary::simpleName),
        )
        assertTrue(aptSchema.resources.isEmpty())

        val kspWithoutModule = FEATURE.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.KSP,
            )
        )
        assertNull(kspWithoutModule.state.requireSchema().module)

        val kspWithModule = FEATURE.precompile(
            featureContext(
                dependencyState = dependencyState,
                workspace = workspace,
                platform = CompilerPlatform.KSP,
                options = mapOf(MODULE_REQUIRED_OPTION to "true"),
            )
        )
        assertNotNull(kspWithModule.state.requireSchema().module)
    }

    @Test
    fun `feature merges trimmed existing indexes without duplicate retained types`() {
        val workspace = workspace(LsiLanguage.JAVA, "demo.Book")
        val dependencyState = resolvedDependencyState("demo.Book")
        val result = FEATURE.precompile(
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
        val schema = result.state.requireSchema()

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
            features = listOf(ImmutableFeature(), FEATURE),
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

    private fun CompilerRoundResult.moduleState(): ModuleFeatureState {
        return featureResults.getValue(ModuleFeature.Key).state
    }

    private fun CompilerRoundResult.moduleArtifacts() =
        featureResults.getValue(ModuleFeature.Key).artifacts

    private fun ModuleFeatureState.requireSchema(): JimmerModuleSchema {
        return requireNotNull(schema)
    }

    private fun featureContext(
        dependencyState: ImmutableFeatureState,
        previousState: ModuleFeatureState? = null,
        workspace: LsiWorkspace = LsiWorkspace.EMPTY,
        platform: CompilerPlatform = CompilerPlatform.APT,
        options: Map<String, String> = emptyMap(),
        inputResources: Map<String, String> = emptyMap(),
    ): CompilerPrecompileContext<EmptyCompilerFeatureState, ModuleFeatureState> {
        return CompilerPrecompileContext(
            session = CompilerSessionSnapshot("module-feature-direct", emptyList()),
            round = round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = platform,
                options = options,
                inputResources = inputResources,
            ),
            collection = CompilerFeatureCollection(EmptyCompilerFeatureState),
            previousState = previousState,
            dependencyStates = CompilerFeatureStates(
                mapOf(ImmutableFeature.Key to dependencyState)
            ),
        )
    }

    private fun render(
        state: ModuleFeatureState,
        dependencyState: ImmutableFeatureState,
    ) = FEATURE.render(
        CompilerRenderContext(
            session = CompilerSessionSnapshot("module-feature-render", emptyList()),
            round = round(0, LsiWorkspace.EMPTY, LsiWorkspace.EMPTY, CompilerPlatform.APT),
            collection = CompilerFeatureCollection(EmptyCompilerFeatureState),
            state = state,
            dependencyStates = CompilerFeatureStates(
                mapOf(ImmutableFeature.Key to dependencyState)
            ),
        )
    )

    private fun resolvedDependencyState(qualifiedName: String): ImmutableFeatureState {
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
        return ImmutableFeatureState(
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
        val FEATURE = ModuleFeature()
    }
}
