package org.babyfish.jimmer.compiler.module

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerSessionSnapshot
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ModuleFeature : CompilerFeature<EmptyCompilerFeatureState, ModuleFeatureState> {

    override val key = Key

    override val dependencies = setOf(ImmutableFeature.Key)

    override val metadata = CompilerFeatureMetadata(
        supportedOptions = setOf(
            FETCHERS_OPTION,
            IMMUTABLES_OPTION,
            TABLE_EXES_OPTION,
            TABLES_OPTION,
            MODULE_REQUIRED_OPTION,
            IGNORE_RESOURCE_GENERATION_OPTION,
            "jimmer.source.excludes",
            "jimmer.source.includes",
        ),
        inputResourcePaths = setOf(
            ENTITIES_RESOURCE_PATH,
            IMMUTABLES_RESOURCE_PATH,
        ),
    )

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ModuleFeatureState>,
    ): CompilerFeaturePrecompileResult<ModuleFeatureState> {
        val immutableState = context.dependencyStates.getValue(ImmutableFeature.Key)
        when (immutableState.status) {
            CompilerResolutionStatus.DEFERRED -> {
                return CompilerFeaturePrecompileResult(
                    state = ModuleFeatureState.blocked(
                        status = ModuleFeatureStatus.DEPENDENCY_DEFERRED,
                        dependencyFingerprint = immutableState.fingerprint,
                    )
                )
            }
            CompilerResolutionStatus.INVALID -> {
                return CompilerFeaturePrecompileResult(
                    state = ModuleFeatureState.blocked(
                        status = ModuleFeatureStatus.DEPENDENCY_INVALID,
                        dependencyFingerprint = immutableState.fingerprint,
                    )
                )
            }
            CompilerResolutionStatus.RESOLVED -> Unit
        }

        val platform = context.round.platform
        if (platform == CompilerPlatform.UNKNOWN) {
            return CompilerFeaturePrecompileResult(
                state = ModuleFeatureState.blocked(
                    status = ModuleFeatureStatus.UNSUPPORTED_PLATFORM,
                    dependencyFingerprint = immutableState.fingerprint,
                )
            )
        }
        val scope = JimmerModuleCompilationScope(
            cumulativeImmutableTypeIds = immutableState.targetTypeIds.sorted(),
            currentImmutableTypeIds = immutableState.currentTypeIds.sorted(),
            compilationSourceTypeIds = context.round.currentRootTypeIds.sorted(),
        )
        val schema = JimmerModulePrecompiler(
            context.round.options.toModuleOptions(platform)
        ).compile(
            immutableSchema = immutableState.schema,
            resourceState = context.round.inputResources.toModuleResourceState(),
            compilationScope = scope,
        )
        val sourcePlanFingerprint = schema.sourcePlanFingerprint()
        val previousState = context.previousState
        val stability = previousState.nextStability(
            roundNumber = context.round.number,
            isFinal = context.round.isFinal,
            sourcePlanFingerprint = sourcePlanFingerprint,
        )
        return CompilerFeaturePrecompileResult(
            state = ModuleFeatureState.ready(
                schema = schema,
                sourcePlanFingerprint = sourcePlanFingerprint,
                stableNonFinalRoundCount = stability.count,
                observedNonFinalRoundNumber = stability.observedRoundNumber,
                dependencyFingerprint = immutableState.fingerprint,
            )
        )
    }

    override fun render(
        context: CompilerRenderContext<EmptyCompilerFeatureState, ModuleFeatureState>,
    ): CompilerFeatureRenderResult {
        val state = context.state
        val schema = state.schema ?: return CompilerFeatureRenderResult()
        if (state.status != ModuleFeatureStatus.READY) {
            return CompilerFeatureRenderResult()
        }
        if (context.round.isFinal) {
            return CompilerFeatureRenderResult(
                artifacts = JimmerModuleResourceRenderer().render(schema, context.round.workspace)
            )
        }
        if (!state.sourceReady || context.session.hasRenderedModuleSources()) {
            return CompilerFeatureRenderResult()
        }
        val renderer: LsiPoetRenderer = when (context.round.platform) {
            CompilerPlatform.APT -> LsiJavaPoetRenderer()
            CompilerPlatform.KSP -> LsiKotlinPoetRenderer()
            CompilerPlatform.UNKNOWN -> return CompilerFeatureRenderResult()
        }
        val artifacts = schema.toLsiPoetArtifacts(context.round.workspace).map(renderer::render)
        return CompilerFeatureRenderResult(artifacts = artifacts)
    }

    companion object {
        val Key = compilerFeatureKey<ModuleFeature, EmptyCompilerFeatureState, ModuleFeatureState>(
            EmptyCompilerFeatureState
        )
    }
}

enum class ModuleFeatureStatus {
    READY,
    DEPENDENCY_DEFERRED,
    DEPENDENCY_INVALID,
    UNSUPPORTED_PLATFORM,
}

data class ModuleFeatureState(
    val status: ModuleFeatureStatus,
    val schema: JimmerModuleSchema?,
    val sourcePlanFingerprint: String,
    val stableNonFinalRoundCount: Int,
    val observedNonFinalRoundNumber: Int?,
    val dependencyFingerprint: String,
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(schema?.fingerprint().orEmpty())
        append(':')
        append(sourcePlanFingerprint)
        append(':')
        append(stableNonFinalRoundCount)
        append(':')
        append(dependencyFingerprint)
    },
) : CompilerFeatureState {

    val sourceReady: Boolean
        get() = status == ModuleFeatureStatus.READY && stableNonFinalRoundCount >= 2

    init {
        require(stableNonFinalRoundCount in 0..2) {
            "Jimmer module stable non-final round count must be between zero and two"
        }
        require(status == ModuleFeatureStatus.READY || schema == null) {
            "Blocked Jimmer module state cannot contain a schema"
        }
        require(status != ModuleFeatureStatus.READY || schema != null) {
            "Ready Jimmer module state requires a schema"
        }
        require(status != ModuleFeatureStatus.READY || sourcePlanFingerprint.isNotBlank()) {
            "Ready Jimmer module state requires a source plan fingerprint"
        }
        require(stableNonFinalRoundCount == 0 || observedNonFinalRoundNumber != null) {
            "Stable Jimmer module state requires an observed non-final round"
        }
    }

    companion object {
        fun ready(
            schema: JimmerModuleSchema,
            sourcePlanFingerprint: String,
            stableNonFinalRoundCount: Int,
            observedNonFinalRoundNumber: Int?,
            dependencyFingerprint: String,
        ): ModuleFeatureState {
            return ModuleFeatureState(
                status = ModuleFeatureStatus.READY,
                schema = schema,
                sourcePlanFingerprint = sourcePlanFingerprint,
                stableNonFinalRoundCount = stableNonFinalRoundCount,
                observedNonFinalRoundNumber = observedNonFinalRoundNumber,
                dependencyFingerprint = dependencyFingerprint,
            )
        }

        fun blocked(
            status: ModuleFeatureStatus,
            dependencyFingerprint: String,
        ): ModuleFeatureState {
            require(status != ModuleFeatureStatus.READY) {
                "Ready Jimmer module state must be created with a schema"
            }
            return ModuleFeatureState(
                status = status,
                schema = null,
                sourcePlanFingerprint = "blocked",
                stableNonFinalRoundCount = 0,
                observedNonFinalRoundNumber = null,
                dependencyFingerprint = dependencyFingerprint,
            )
        }
    }
}

private data class JimmerModuleStability(
    val count: Int,
    val observedRoundNumber: Int?,
)

private fun ModuleFeatureState?.nextStability(
    roundNumber: Int,
    isFinal: Boolean,
    sourcePlanFingerprint: String,
): JimmerModuleStability {
    if (isFinal) {
        return JimmerModuleStability(
            count = this?.stableNonFinalRoundCount ?: 0,
            observedRoundNumber = this?.observedNonFinalRoundNumber,
        )
    }
    if (
        this != null &&
        status == ModuleFeatureStatus.READY &&
        observedNonFinalRoundNumber == roundNumber
    ) {
        return JimmerModuleStability(stableNonFinalRoundCount, roundNumber)
    }
    val count = if (
        this != null &&
        status == ModuleFeatureStatus.READY &&
        this.sourcePlanFingerprint == sourcePlanFingerprint
    ) {
        minOf(2, stableNonFinalRoundCount + 1)
    } else {
        1
    }
    return JimmerModuleStability(count, roundNumber)
}

private fun Map<String, String>.toModuleOptions(
    platform: CompilerPlatform,
): JimmerModulePrecompileOptions {
    return JimmerModulePrecompileOptions(
        platform = platform,
        immutablesName = entryName(IMMUTABLES_OPTION, "Immutables"),
        tablesName = entryName(TABLES_OPTION, "Tables"),
        tableExesName = entryName(TABLE_EXES_OPTION, "TableExes"),
        fetchersName = entryName(FETCHERS_OPTION, "Fetchers"),
        moduleRequired = booleanOption(MODULE_REQUIRED_OPTION),
        resourceGeneration = !booleanOption(IGNORE_RESOURCE_GENERATION_OPTION),
    )
}

private fun Map<String, String>.entryName(
    optionName: String,
    defaultValue: String,
): String {
    return get(optionName)?.takeIf(String::isNotEmpty) ?: defaultValue
}

private fun Map<String, String>.booleanOption(optionName: String): Boolean {
    return get(optionName)?.trim() == "true"
}

private fun Map<String, String>.toModuleResourceState(): JimmerModuleResourceState {
    return JimmerModuleResourceState(
        entityQualifiedTypeNames = get(ENTITIES_RESOURCE_PATH).resourceTypeNames(),
        immutableQualifiedTypeNames = get(IMMUTABLES_RESOURCE_PATH).resourceTypeNames(),
    )
}

private fun String?.resourceTypeNames(): List<String> {
    return this
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.distinct()
        ?.sorted()
        ?.toList()
        .orEmpty()
}

private fun JimmerModuleSchema.sourcePlanFingerprint(): String {
    return copy(
        summaries = summaries.map { summary ->
            summary.copy(
                dependencies = summary.dependencies.withoutCurrentOrigins()
            )
        },
        module = module?.let { source ->
            source.copy(
                dependencies = source.dependencies.withoutCurrentOrigins()
            )
        },
        resources = resources.map { resource ->
            resource.copy(
                dependencies = resource.dependencies.withoutCurrentOrigins()
            )
        },
    ).fingerprint()
}

private fun JimmerModuleArtifactDependencies.withoutCurrentOrigins(): JimmerModuleArtifactDependencies {
    return copy(originatingTypeIds = emptyList())
}

private fun CompilerSessionSnapshot.hasRenderedModuleSources(): Boolean {
    return rounds.any { round ->
        round.featureResults[ModuleFeature.Key]
            ?.artifacts
            ?.any { artifact -> artifact.kind.isSource }
            ?: false
    }
}

private const val IMMUTABLES_OPTION = "jimmer.entry.immutables"
private const val TABLES_OPTION = "jimmer.entry.tables"
private const val TABLE_EXES_OPTION = "jimmer.entry.tableExes"
private const val FETCHERS_OPTION = "jimmer.entry.fetchers"
private const val MODULE_REQUIRED_OPTION = "jimmer.immutable.isModuleRequired"
private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"
private const val ENTITIES_RESOURCE_PATH = "META-INF/jimmer/entities"
private const val IMMUTABLES_RESOURCE_PATH = "META-INF/jimmer/immutables"
