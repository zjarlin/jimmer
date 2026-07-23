package org.babyfish.jimmer.compiler.client

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.JimmerCompilerTypeSeedContext
import org.babyfish.jimmer.compiler.error.ErrorCompilerFeatureState
import org.babyfish.jimmer.compiler.error.ErrorCompilerFeatureStatus
import org.babyfish.jimmer.compiler.dto.JimmerDtoCompilerFeatureState
import org.babyfish.jimmer.compiler.dto.JimmerDtoCompilerFeatureStatus
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.jimmer.unresolvedJimmerImmutableTypeIds
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.error.ErrorSchemaOptions
import site.addzero.lsi.jimmer.error.ErrorValidationException
import site.addzero.lsi.jimmer.error.toErrorSchema
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiWorkspace

class JimmerClientCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = CLIENT_FEATURE_ID,
        dependsOn = setOf(DTO_FEATURE_ID, ERROR_FEATURE_ID, IMMUTABLE_FEATURE_ID),
    )

    override fun requestTypeSeeds(context: JimmerCompilerTypeSeedContext): Collection<LsiTypeSeed> {
        if (context.round.options[IGNORE_RESOURCE_GENERATION_OPTION] == "true") {
            return emptyList()
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
        if (targets.rootTypeIds.isEmpty()) {
            return emptyList()
        }
        return precompiler.requestedTypeSeeds(
            workspace = context.round.workspace,
            targets = targets,
            dependencies = context.round.previewClientDependencies(),
        )
    }

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
        val currentTargets = targets.only(context.round.currentRootTypeIds)
        val initialUnresolvedTypeIds = precompiler.unresolvedTargetTypeIds(
            workspace = context.round.workspace,
            targets = targets,
        )
        val outcome = precompileAvailableTargets(
            precompiler = precompiler,
            workspace = context.round.workspace,
            targets = targets.without(initialUnresolvedTypeIds),
            initialUnresolvedTypeIds = initialUnresolvedTypeIds,
            dependencies = ClientPrecompileDependencies(
                immutableSchema = dependencies.immutableSchema,
                errorSchema = dependencies.errorSchema,
                definitionDocumentationByTypeId = dependencies.definitionDocumentationByTypeId,
            ),
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
            unresolvedRootTypeIds = outcome.unresolvedRootTypeIds,
            invalidRootTypeIds = outcome.failures.mapTo(sortedSetOf(), ClientCompilerFailure::rootTypeId),
            failures = outcome.failures,
            immutableDependencyFingerprint = dependencies.immutableFingerprint,
            errorDependencyFingerprint = dependencies.errorFingerprint,
            dtoDependencyFingerprint = dependencies.dtoFingerprint,
        )
        val unavailableRootTypeIds = state.unresolvedRootTypeIds + state.invalidRootTypeIds
        return JimmerCompilerFeaturePrecompileResult(
            state = state,
            diagnostics = outcome.diagnostics(deferred),
            processedSymbols = currentTargets.rootTypeIds - unavailableRootTypeIds,
            unresolvedSymbols = if (deferred) outcome.unresolvedRootTypeIds else emptySet(),
        )
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        val state = context.state as JimmerClientCompilerFeatureState
        if (!context.round.isFinal || context.round.frontendDeferred || !state.renderable) {
            return JimmerCompilerFeatureRenderResult()
        }
        val originatingSymbols = buildSet {
            state.schema.services.mapTo(this, ClientService::id)
            state.schema.definitions.mapTo(this, ClientTypeDefinition::id)
        }
        val originatingSources = originatingSymbols.mapNotNullTo(sortedSetOf()) { symbolId ->
            context.round.workspace[symbolId]?.origin?.source
        }
        return JimmerCompilerFeatureRenderResult(
            artifacts = listOf(
                GeneratedArtifact.create(
                    kind = ArtifactKind.RESOURCE,
                    path = CLIENT_RESOURCE_PATH,
                    content = ClientResourceRenderer().render(state.schema),
                    aggregationMode = ArtifactAggregationMode.AGGREGATING,
                    originatingSymbols = originatingSymbols,
                    originatingSources = originatingSources,
                )
            ),
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
    val unresolvedRootTypeIds: Set<LsiSymbolId>,
    val invalidRootTypeIds: Set<LsiSymbolId>,
    val failures: List<ClientCompilerFailure>,
    val immutableDependencyFingerprint: String,
    val errorDependencyFingerprint: String,
    val dtoDependencyFingerprint: String,
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
        append(':')
        append(dtoDependencyFingerprint)
    },
) : JimmerCompilerFeatureState {

    val renderable: Boolean
        get() = status == JimmerClientCompilerFeatureStatus.RESOLVED

    init {
        require(currentServiceTypeIds.all(targetServiceTypeIds::contains)) {
            "Current client service ids must be part of all target service ids"
        }
        val targetRootTypeIds = targetServiceTypeIds
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
    val immutableSchema: ImmutableSchema,
    val errorSchema: ErrorSchema,
    val definitionDocumentationByTypeId: Map<LsiSymbolId, ClientDefinitionDocumentation>,
    val dtoFingerprint: String,
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
        unresolvedRootTypeIds = emptySet(),
        invalidRootTypeIds = emptySet(),
        failures = emptyList(),
        immutableDependencyFingerprint = dependencies.immutableFingerprint,
        errorDependencyFingerprint = dependencies.errorFingerprint,
        dtoDependencyFingerprint = dependencies.dtoFingerprint,
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
    val dtoState = requireNotNull(
        dependencyStates[DTO_FEATURE_ID] as? JimmerDtoCompilerFeatureState
    ) {
        "Client feature requires DTO compiler state"
    }
    val status = when {
        immutableState.status == JimmerImmutableCompilerFeatureStatus.INVALID ||
            errorState.status == ErrorCompilerFeatureStatus.INVALID ||
            dtoState.status in DTO_INVALID_STATUSES -> {
            JimmerClientCompilerDependencyStatus.INVALID
        }
        immutableState.status == JimmerImmutableCompilerFeatureStatus.DEFERRED ||
            dtoState.status in DTO_DEFERRED_STATUSES -> {
            JimmerClientCompilerDependencyStatus.DEFERRED
        }
        else -> JimmerClientCompilerDependencyStatus.RESOLVED
    }
    return ClientDependencies(
        status = status,
        immutableFingerprint = immutableState.fingerprint,
        errorFingerprint = errorState.fingerprint,
        immutableSchema = immutableState.schema,
        errorSchema = errorState.schema,
        definitionDocumentationByTypeId = dtoState.schema.toClientDefinitionDocumentation(immutableState.schema),
        dtoFingerprint = dtoState.fingerprint,
    )
}

private fun CompilerRound.previewClientDependencies(): ClientPrecompileDependencies {
    val immutableTypeIds = workspace.declarationsOfType<LsiTypeDeclaration>()
        .filter { type ->
            type.annotations.any { annotation -> annotation.type in IMMUTABLE_TYPE_ANNOTATIONS }
        }
        .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
    val resolvedImmutableTypeIds = immutableTypeIds - workspace.unresolvedJimmerImmutableTypeIds(immutableTypeIds)
    val immutableSchema = try {
        workspace.toImmutableSchema(resolvedImmutableTypeIds)
    } catch (_: ImmutablePrecompileException) {
        ImmutableSchema(emptyList())
    }
    val errorSchema = try {
        workspace.toErrorSchema(
            options = ErrorSchemaOptions(
                checkedException = options["jimmer.client.checkedException"] == "true",
            ),
        )
    } catch (_: ErrorValidationException) {
        ErrorSchema(emptyList())
    }
    return ClientPrecompileDependencies(
        immutableSchema = immutableSchema,
        errorSchema = errorSchema,
        definitionDocumentationByTypeId = emptyMap(),
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
    dependencies: ClientPrecompileDependencies,
): ClientPrecompileOutcome {
    var availableTargets = targets
    val unresolvedTypeIds = initialUnresolvedTypeIds.toCollection(sortedSetOf())
    val failures = mutableListOf<ClientCompilerFailure>()
    while (availableTargets.rootTypeIds.isNotEmpty()) {
        try {
            return ClientPrecompileOutcome(
                schema = precompiler.compile(workspace, availableTargets, dependencies),
                unresolvedRootTypeIds = unresolvedTypeIds,
                failures = failures,
            )
        } catch (exception: ClientPrecompileException) {
            val affectedRootTypeId = (exception.rootTypeId ?: exception.declarationId.rootTypeId())
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
    )
}

private fun ClientPrecompileTargets.only(typeIds: Set<LsiSymbolId>): ClientPrecompileTargets {
    return ClientPrecompileTargets(
        serviceTypeIds = serviceTypeIds intersect typeIds,
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
    return when (platform) {
        CompilerPlatform.APT -> annotations.none { annotation ->
            annotation.type == KOTLIN_METADATA_ANNOTATION
        }
        CompilerPlatform.KSP -> origin.language != LsiLanguage.JAVA
        CompilerPlatform.UNKNOWN -> true
    }
}

private fun LsiSymbolId.rootTypeId(): LsiSymbolId = LsiSymbolId(value.substringBefore('/'))

private fun StringBuilder.appendIds(ids: Set<LsiSymbolId>) {
    append(':')
    append(ids.sorted().joinToString(",") { id -> id.value })
}

private const val CLIENT_FEATURE_ID = "client"
private const val DTO_FEATURE_ID = "dto"
private const val ERROR_FEATURE_ID = "error"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"
private const val CLIENT_RESOURCE_PATH = "META-INF/jimmer/client"

private val EMPTY_SCHEMA = ClientPrecompiledSchema(emptyList(), emptyList())

private val DTO_DEFERRED_STATUSES = setOf(
    JimmerDtoCompilerFeatureStatus.PENDING,
    JimmerDtoCompilerFeatureStatus.DEFERRED,
    JimmerDtoCompilerFeatureStatus.DEPENDENCY_DEFERRED,
)

private val DTO_INVALID_STATUSES = setOf(
    JimmerDtoCompilerFeatureStatus.INVALID,
    JimmerDtoCompilerFeatureStatus.DEPENDENCY_INVALID,
)
private val ENABLE_IMPLICIT_API_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.client.EnableImplicitApi")
private val KOTLIN_METADATA_ANNOTATION = LsiSymbolId.type("kotlin.Metadata")
private val IMMUTABLE_TYPE_ANNOTATIONS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.Immutable"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Entity"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable"),
)
private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)
