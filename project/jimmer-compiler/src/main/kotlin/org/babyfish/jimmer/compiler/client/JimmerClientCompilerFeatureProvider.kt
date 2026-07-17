package org.babyfish.jimmer.compiler.client

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.error.ErrorCompilerFeatureState
import org.babyfish.jimmer.compiler.error.ErrorCompilerFeatureStatus
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

class JimmerClientCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = CLIENT_FEATURE_ID,
        dependsOn = setOf(ERROR_FEATURE_ID, IMMUTABLE_FEATURE_ID),
    )

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        val dependencies = context.clientDependencies()
        if (context.round.options[IGNORE_RESOURCE_GENERATION_OPTION] == "true") {
            return JimmerCompilerFeaturePrecompileResult(
                state = disabledClientState(dependencies),
            )
        }

        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val explicitApi = context.round.workspace.hasImplicitApiMarker(
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val precompiler = ClientPrecompiler(ClientPrecompileOptions(explicitApi))
        val targets = precompiler.targets(context.round.workspace).compilationTargets(
            workspace = context.round.workspace,
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val currentTargets = if (context.round.isFinal) {
            EMPTY_TARGETS
        } else {
            precompiler.targets(context.round.currentWorkspace).compilationTargets(
                workspace = context.round.currentWorkspace,
                platform = context.round.platform,
                sourceFilter = sourceFilter,
            )
        }
        val initialUnresolvedTypeIds = precompiler.unresolvedTargetTypeIds(
            workspace = context.round.workspace,
            targets = targets,
        )
        val outcome = precompileAvailableTargets(
            precompiler = precompiler,
            workspace = context.round.workspace,
            targets = targets.without(initialUnresolvedTypeIds),
            initialUnresolvedTypeIds = initialUnresolvedTypeIds,
        )
        val deferred = outcome.unresolvedRootTypeIds.isNotEmpty() &&
            context.round.platform == CompilerPlatform.APT &&
            !context.round.isFinal
        val status = clientStatus(
            dependencies = dependencies,
            deferred = deferred,
            unresolved = outcome.unresolvedRootTypeIds.isNotEmpty(),
            invalid = outcome.failures.isNotEmpty(),
        )
        val state = JimmerClientCompilerFeatureState(
            status = status,
            dependencyStatus = dependencies.status,
            schema = outcome.schema,
            explicitApi = explicitApi,
            targetServiceTypeIds = targets.serviceTypeIds,
            currentServiceTypeIds = currentTargets.serviceTypeIds,
            targetExportedTypeIds = targets.exportedTypeIds,
            currentExportedTypeIds = currentTargets.exportedTypeIds,
            unresolvedRootTypeIds = outcome.unresolvedRootTypeIds,
            invalidRootTypeIds = outcome.failures.mapTo(sortedSetOf(), ClientCompilerFailure::rootTypeId),
            failures = outcome.failures,
            immutableDependencyFingerprint = dependencies.immutableFingerprint,
            errorDependencyFingerprint = dependencies.errorFingerprint,
        )
        val unavailableRootTypeIds = state.unresolvedRootTypeIds + state.invalidRootTypeIds
        return JimmerCompilerFeaturePrecompileResult(
            state = state,
            diagnostics = outcome.diagnostics(deferred),
            processedSymbols = currentTargets.rootTypeIds - unavailableRootTypeIds,
            unresolvedSymbols = if (deferred) outcome.unresolvedRootTypeIds else emptySet(),
        )
    }
}

internal enum class JimmerClientCompilerFeatureStatus {
    RESOLVED,
    DEFERRED,
    INVALID,
    DEPENDENCY_DEFERRED,
    DEPENDENCY_INVALID,
    DISABLED,
}

internal enum class JimmerClientCompilerDependencyStatus {
    RESOLVED,
    DEFERRED,
    INVALID,
}

internal data class ClientCompilerFailure(
    val rootTypeId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val message: String,
) {
    init {
        rootTypeId.requireTypeQualifiedName()
        require(message.isNotBlank()) { "Client compiler failure message cannot be blank" }
    }
}

internal data class JimmerClientCompilerFeatureState(
    val status: JimmerClientCompilerFeatureStatus,
    val dependencyStatus: JimmerClientCompilerDependencyStatus,
    val schema: ClientPrecompiledSchema,
    val explicitApi: Boolean,
    val targetServiceTypeIds: Set<LsiSymbolId>,
    val currentServiceTypeIds: Set<LsiSymbolId>,
    val targetExportedTypeIds: Set<LsiSymbolId>,
    val currentExportedTypeIds: Set<LsiSymbolId>,
    val unresolvedRootTypeIds: Set<LsiSymbolId>,
    val invalidRootTypeIds: Set<LsiSymbolId>,
    val failures: List<ClientCompilerFailure>,
    val immutableDependencyFingerprint: String,
    val errorDependencyFingerprint: String,
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(dependencyStatus.name)
        append(':')
        append(explicitApi)
        append(':')
        append(schema.fingerprint())
        appendIds(targetServiceTypeIds)
        appendIds(currentServiceTypeIds)
        appendIds(targetExportedTypeIds)
        appendIds(currentExportedTypeIds)
        appendIds(unresolvedRootTypeIds)
        appendIds(invalidRootTypeIds)
        failures.sortedWith(compareBy(ClientCompilerFailure::rootTypeId, ClientCompilerFailure::declarationId))
            .forEach { failure ->
                append(':')
                append(failure.rootTypeId.value)
                append(':')
                append(failure.declarationId.value)
                append(':')
                append(failure.message.length)
                append(':')
                append(failure.message)
            }
        append(':')
        append(immutableDependencyFingerprint)
        append(':')
        append(errorDependencyFingerprint)
    },
) : JimmerCompilerFeatureState {

    val renderable: Boolean
        get() = status == JimmerClientCompilerFeatureStatus.RESOLVED

    init {
        require(currentServiceTypeIds.all(targetServiceTypeIds::contains)) {
            "Current client service ids must be part of all target service ids"
        }
        require(currentExportedTypeIds.all(targetExportedTypeIds::contains)) {
            "Current exported client type ids must be part of all exported target ids"
        }
        val targetRootTypeIds = targetServiceTypeIds + targetExportedTypeIds
        require(unresolvedRootTypeIds.all(targetRootTypeIds::contains)) {
            "Unresolved client root ids must be part of client targets"
        }
        require(invalidRootTypeIds.all(targetRootTypeIds::contains)) {
            "Invalid client root ids must be part of client targets"
        }
        require(unresolvedRootTypeIds.intersect(invalidRootTypeIds).isEmpty()) {
            "Client roots cannot be both unresolved and invalid"
        }
        require(failures.mapTo(sortedSetOf(), ClientCompilerFailure::rootTypeId) == invalidRootTypeIds) {
            "Client failures must describe all invalid roots"
        }
        require(status != JimmerClientCompilerFeatureStatus.RESOLVED || dependencyStatus == JimmerClientCompilerDependencyStatus.RESOLVED) {
            "Resolved client state requires resolved dependencies"
        }
        require(status != JimmerClientCompilerFeatureStatus.RESOLVED || unresolvedRootTypeIds.isEmpty()) {
            "Resolved client state cannot contain unresolved roots"
        }
        require(status != JimmerClientCompilerFeatureStatus.RESOLVED || invalidRootTypeIds.isEmpty()) {
            "Resolved client state cannot contain invalid roots"
        }
    }
}

private data class ClientDependencies(
    val status: JimmerClientCompilerDependencyStatus,
    val immutableFingerprint: String,
    val errorFingerprint: String,
)

private data class ClientPrecompileOutcome(
    val schema: ClientPrecompiledSchema,
    val unresolvedRootTypeIds: Set<LsiSymbolId>,
    val failures: List<ClientCompilerFailure>,
)

private fun disabledClientState(
    dependencies: ClientDependencies,
): JimmerClientCompilerFeatureState {
    return JimmerClientCompilerFeatureState(
        status = JimmerClientCompilerFeatureStatus.DISABLED,
        dependencyStatus = dependencies.status,
        schema = EMPTY_SCHEMA,
        explicitApi = false,
        targetServiceTypeIds = emptySet(),
        currentServiceTypeIds = emptySet(),
        targetExportedTypeIds = emptySet(),
        currentExportedTypeIds = emptySet(),
        unresolvedRootTypeIds = emptySet(),
        invalidRootTypeIds = emptySet(),
        failures = emptyList(),
        immutableDependencyFingerprint = dependencies.immutableFingerprint,
        errorDependencyFingerprint = dependencies.errorFingerprint,
    )
}

private fun JimmerCompilerPrecompileContext.clientDependencies(): ClientDependencies {
    val immutableState = requireNotNull(
        dependencyStates[IMMUTABLE_FEATURE_ID] as? JimmerImmutableCompilerFeatureState
    ) {
        "Client feature requires immutable compiler state"
    }
    val errorState = requireNotNull(
        dependencyStates[ERROR_FEATURE_ID] as? ErrorCompilerFeatureState
    ) {
        "Client feature requires error compiler state"
    }
    val status = when {
        immutableState.status == JimmerImmutableCompilerFeatureStatus.INVALID ||
            errorState.status == ErrorCompilerFeatureStatus.INVALID -> {
            JimmerClientCompilerDependencyStatus.INVALID
        }
        immutableState.status == JimmerImmutableCompilerFeatureStatus.DEFERRED -> {
            JimmerClientCompilerDependencyStatus.DEFERRED
        }
        else -> JimmerClientCompilerDependencyStatus.RESOLVED
    }
    return ClientDependencies(
        status = status,
        immutableFingerprint = immutableState.fingerprint,
        errorFingerprint = errorState.fingerprint,
    )
}

private fun clientStatus(
    dependencies: ClientDependencies,
    deferred: Boolean,
    unresolved: Boolean,
    invalid: Boolean,
): JimmerClientCompilerFeatureStatus {
    return when {
        invalid -> JimmerClientCompilerFeatureStatus.INVALID
        dependencies.status == JimmerClientCompilerDependencyStatus.INVALID -> {
            JimmerClientCompilerFeatureStatus.DEPENDENCY_INVALID
        }
        unresolved && !deferred -> JimmerClientCompilerFeatureStatus.INVALID
        deferred -> JimmerClientCompilerFeatureStatus.DEFERRED
        dependencies.status == JimmerClientCompilerDependencyStatus.DEFERRED -> {
            JimmerClientCompilerFeatureStatus.DEPENDENCY_DEFERRED
        }
        else -> JimmerClientCompilerFeatureStatus.RESOLVED
    }
}

private fun precompileAvailableTargets(
    precompiler: ClientPrecompiler,
    workspace: LsiWorkspace,
    targets: ClientPrecompileTargets,
    initialUnresolvedTypeIds: Set<LsiSymbolId>,
): ClientPrecompileOutcome {
    var availableTargets = targets
    val unresolvedTypeIds = initialUnresolvedTypeIds.toCollection(sortedSetOf())
    val failures = mutableListOf<ClientCompilerFailure>()
    while (availableTargets.rootTypeIds.isNotEmpty()) {
        try {
            return ClientPrecompileOutcome(
                schema = precompiler.compile(workspace, availableTargets),
                unresolvedRootTypeIds = unresolvedTypeIds,
                failures = failures,
            )
        } catch (exception: ClientPrecompileException) {
            val affectedRootTypeId = exception.declarationId.rootTypeId()
                .takeIf { typeId -> typeId in availableTargets.rootTypeIds }
                ?: return ClientPrecompileOutcome(
                    schema = EMPTY_SCHEMA,
                    unresolvedRootTypeIds = unresolvedTypeIds,
                    failures = failures + exception.toFailure(exception.declarationId.rootTypeId()),
                )
            if (exception.recoverable) {
                unresolvedTypeIds += affectedRootTypeId
            } else {
                failures += exception.toFailure(affectedRootTypeId)
            }
            availableTargets = availableTargets.without(setOf(affectedRootTypeId))
        }
    }
    return ClientPrecompileOutcome(
        schema = EMPTY_SCHEMA,
        unresolvedRootTypeIds = unresolvedTypeIds,
        failures = failures,
    )
}

private fun ClientPrecompileOutcome.diagnostics(
    deferred: Boolean,
): List<LsiDiagnostic> {
    return buildList {
        if (!deferred) {
            unresolvedRootTypeIds.sorted().forEach { typeId ->
                add(
                    LsiDiagnostic(
                        code = "jimmer.client.unresolved",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = "Client declaration '${typeId.value}' cannot be fully resolved",
                        symbolId = typeId,
                    )
                )
            }
        }
        failures.sortedWith(compareBy(ClientCompilerFailure::rootTypeId, ClientCompilerFailure::declarationId))
            .forEach { failure ->
                add(
                    LsiDiagnostic(
                        code = "jimmer.client.invalid",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = failure.message,
                        symbolId = failure.declarationId,
                    )
                )
            }
    }
}

private fun ClientPrecompileException.toFailure(
    rootTypeId: LsiSymbolId,
): ClientCompilerFailure {
    return ClientCompilerFailure(
        rootTypeId = rootTypeId,
        declarationId = declarationId,
        message = message ?: "Invalid client declaration '${declarationId.value}'",
    )
}

private fun ClientPrecompileTargets.compilationTargets(
    workspace: LsiWorkspace,
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): ClientPrecompileTargets {
    fun Set<LsiSymbolId>.accepted(): Set<LsiSymbolId> {
        return filterTo(sortedSetOf()) { typeId ->
            val type = workspace[typeId] as? LsiTypeDeclaration ?: return@filterTo false
            type.isCompilationTarget(platform, sourceFilter)
        }
    }
    return ClientPrecompileTargets(
        serviceTypeIds = serviceTypeIds.accepted(),
        exportedTypeIds = exportedTypeIds.accepted(),
    )
}

private fun LsiWorkspace.hasImplicitApiMarker(
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): Boolean {
    return declarationsOfType<LsiTypeDeclaration>().any { type ->
        type.isCompilationTarget(platform, sourceFilter) &&
            type.annotations.any { annotation -> annotation.type == ENABLE_IMPLICIT_API_ANNOTATION }
    }
}

private fun LsiTypeDeclaration.isCompilationTarget(
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): Boolean {
    if (origin.kind !in COMPILATION_ORIGIN_KINDS || !sourceFilter.accepts(qualifiedName)) {
        return false
    }
    return platform != CompilerPlatform.APT || annotations.none { annotation ->
        annotation.type == KOTLIN_METADATA_ANNOTATION
    }
}

private fun LsiSymbolId.rootTypeId(): LsiSymbolId = LsiSymbolId(value.substringBefore('/'))

private fun StringBuilder.appendIds(ids: Set<LsiSymbolId>) {
    append(':')
    append(ids.sorted().joinToString(",") { id -> id.value })
}

private const val CLIENT_FEATURE_ID = "client"
private const val ERROR_FEATURE_ID = "error"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"

private val EMPTY_SCHEMA = ClientPrecompiledSchema(emptyList(), emptyList())
private val EMPTY_TARGETS = ClientPrecompileTargets(emptySet(), emptySet())
private val ENABLE_IMPLICIT_API_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.client.EnableImplicitApi")
private val KOTLIN_METADATA_ANNOTATION = LsiSymbolId.type("kotlin.Metadata")
private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)
