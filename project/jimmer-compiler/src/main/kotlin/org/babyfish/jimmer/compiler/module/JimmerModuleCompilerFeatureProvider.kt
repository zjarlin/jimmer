package org.babyfish.jimmer.compiler.module

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import org.babyfish.jimmer.compiler.module.apt.JimmerModuleJavaRenderer
import org.babyfish.jimmer.compiler.module.ksp.JimmerModuleKotlinRenderer
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = MODULE_FEATURE_ID,
        dependsOn = setOf(IMMUTABLE_FEATURE_ID),
        inputResourcePaths = setOf(
            ENTITIES_RESOURCE_PATH,
            IMMUTABLES_RESOURCE_PATH,
        ),
    )

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        val immutableState = requireNotNull(
            context.dependencyStates[IMMUTABLE_FEATURE_ID] as? JimmerImmutableCompilerFeatureState
        ) {
            "Jimmer module feature requires immutable compiler state"
        }
        when (immutableState.status) {
            JimmerImmutableCompilerFeatureStatus.DEFERRED -> {
                return JimmerCompilerFeaturePrecompileResult(
                    state = JimmerModuleCompilerFeatureState.blocked(
                        status = JimmerModuleCompilerFeatureStatus.DEPENDENCY_DEFERRED,
                        dependencyFingerprint = immutableState.fingerprint,
                    )
                )
            }
            JimmerImmutableCompilerFeatureStatus.INVALID -> {
                return JimmerCompilerFeaturePrecompileResult(
                    state = JimmerModuleCompilerFeatureState.blocked(
                        status = JimmerModuleCompilerFeatureStatus.DEPENDENCY_INVALID,
                        dependencyFingerprint = immutableState.fingerprint,
                    )
                )
            }
            JimmerImmutableCompilerFeatureStatus.RESOLVED -> Unit
        }

        val platform = context.round.platform.toModulePlatform()
            ?: return JimmerCompilerFeaturePrecompileResult(
                state = JimmerModuleCompilerFeatureState.blocked(
                    status = JimmerModuleCompilerFeatureStatus.UNSUPPORTED_PLATFORM,
                    dependencyFingerprint = immutableState.fingerprint,
                )
            )
        val scope = JimmerModuleCompilationScope(
            cumulativeImmutableTypeIds = immutableState.targetTypeIds.sorted(),
            currentImmutableTypeIds = immutableState.currentTypeIds.sorted(),
            compilationSourceTypeIds = context.round.currentWorkspace.compilationSourceTypeIds(),
        )
        val schema = JimmerModulePrecompiler(
            context.round.options.toModuleOptions(platform)
        ).compile(
            immutableSchema = immutableState.schema,
            resourceState = context.round.inputResources.toModuleResourceState(),
            compilationScope = scope,
        )
        val sourcePlanFingerprint = schema.sourcePlanFingerprint()
        val previousState = context.previousState as? JimmerModuleCompilerFeatureState
        val stability = previousState.nextStability(
            roundNumber = context.round.number,
            isFinal = context.round.isFinal,
            sourcePlanFingerprint = sourcePlanFingerprint,
        )
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerModuleCompilerFeatureState.ready(
                schema = schema,
                sourcePlanFingerprint = sourcePlanFingerprint,
                stableNonFinalRoundCount = stability.count,
                observedNonFinalRoundNumber = stability.observedRoundNumber,
                dependencyFingerprint = immutableState.fingerprint,
            )
        )
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        val state = context.state as JimmerModuleCompilerFeatureState
        val schema = state.schema ?: return JimmerCompilerFeatureRenderResult()
        if (state.status != JimmerModuleCompilerFeatureStatus.READY) {
            return JimmerCompilerFeatureRenderResult()
        }
        if (context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult(
                artifacts = JimmerModuleResourceRenderer().render(schema, context.round.workspace)
            )
        }
        if (!state.sourceReady || context.session.hasRenderedModuleSources()) {
            return JimmerCompilerFeatureRenderResult()
        }
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> JimmerModuleJavaRenderer().render(schema, context.round.workspace)
            CompilerPlatform.KSP -> JimmerModuleKotlinRenderer().render(schema, context.round.workspace)
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

internal enum class JimmerModuleCompilerFeatureStatus {
    READY,
    DEPENDENCY_DEFERRED,
    DEPENDENCY_INVALID,
    UNSUPPORTED_PLATFORM,
}

internal data class JimmerModuleCompilerFeatureState(
    val status: JimmerModuleCompilerFeatureStatus,
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
) : JimmerCompilerFeatureState {

    val sourceReady: Boolean
        get() = status == JimmerModuleCompilerFeatureStatus.READY && stableNonFinalRoundCount >= 2

    init {
        require(stableNonFinalRoundCount in 0..2) {
            "Jimmer module stable non-final round count must be between zero and two"
        }
        require(status == JimmerModuleCompilerFeatureStatus.READY || schema == null) {
            "Blocked Jimmer module state cannot contain a schema"
        }
        require(status != JimmerModuleCompilerFeatureStatus.READY || schema != null) {
            "Ready Jimmer module state requires a schema"
        }
        require(status != JimmerModuleCompilerFeatureStatus.READY || sourcePlanFingerprint.isNotBlank()) {
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
        ): JimmerModuleCompilerFeatureState {
            return JimmerModuleCompilerFeatureState(
                status = JimmerModuleCompilerFeatureStatus.READY,
                schema = schema,
                sourcePlanFingerprint = sourcePlanFingerprint,
                stableNonFinalRoundCount = stableNonFinalRoundCount,
                observedNonFinalRoundNumber = observedNonFinalRoundNumber,
                dependencyFingerprint = dependencyFingerprint,
            )
        }

        fun blocked(
            status: JimmerModuleCompilerFeatureStatus,
            dependencyFingerprint: String,
        ): JimmerModuleCompilerFeatureState {
            require(status != JimmerModuleCompilerFeatureStatus.READY) {
                "Ready Jimmer module state must be created with a schema"
            }
            return JimmerModuleCompilerFeatureState(
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

private fun JimmerModuleCompilerFeatureState?.nextStability(
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
        status == JimmerModuleCompilerFeatureStatus.READY &&
        observedNonFinalRoundNumber == roundNumber
    ) {
        return JimmerModuleStability(stableNonFinalRoundCount, roundNumber)
    }
    val count = if (
        this != null &&
        status == JimmerModuleCompilerFeatureStatus.READY &&
        this.sourcePlanFingerprint == sourcePlanFingerprint
    ) {
        minOf(2, stableNonFinalRoundCount + 1)
    } else {
        1
    }
    return JimmerModuleStability(count, roundNumber)
}

private fun CompilerPlatform.toModulePlatform(): JimmerModulePlatform? {
    return when (this) {
        CompilerPlatform.APT -> JimmerModulePlatform.APT
        CompilerPlatform.KSP -> JimmerModulePlatform.KSP
        CompilerPlatform.UNKNOWN -> null
    }
}

private fun Map<String, String>.toModuleOptions(
    platform: JimmerModulePlatform,
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

private fun LsiWorkspace.compilationSourceTypeIds(): List<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .asSequence()
        .filter { type -> type.origin.kind in COMPILATION_ORIGIN_KINDS }
        .map(LsiTypeDeclaration::id)
        .distinct()
        .sorted()
        .toList()
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
        round.featureResults[MODULE_FEATURE_ID]
            ?.artifacts
            ?.any { artifact -> artifact.kind.isSource }
            ?: false
    }
}

private const val MODULE_FEATURE_ID = "module"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val IMMUTABLES_OPTION = "jimmer.entry.immutables"
private const val TABLES_OPTION = "jimmer.entry.tables"
private const val TABLE_EXES_OPTION = "jimmer.entry.tableExes"
private const val FETCHERS_OPTION = "jimmer.entry.fetchers"
private const val MODULE_REQUIRED_OPTION = "jimmer.immutable.isModuleRequired"
private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"
private const val ENTITIES_RESOURCE_PATH = "META-INF/jimmer/entities"
private const val IMMUTABLES_RESOURCE_PATH = "META-INF/jimmer/immutables"
private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)
