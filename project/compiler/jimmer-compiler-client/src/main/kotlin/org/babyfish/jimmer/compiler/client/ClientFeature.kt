package org.babyfish.jimmer.compiler.client

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.compilerFeatureKey
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import site.addzero.lsi.compiler.CompilerTypeSeedContext
import org.babyfish.jimmer.compiler.error.ErrorFeatureState
import org.babyfish.jimmer.compiler.error.ErrorFeatureStatus
import org.babyfish.jimmer.compiler.error.ErrorFeature
import org.babyfish.jimmer.compiler.dto.DtoFeature
import org.babyfish.jimmer.compiler.dto.DtoFeatureState
import org.babyfish.jimmer.compiler.dto.DtoFeatureStatus
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import org.babyfish.jimmer.compiler.immutable.ImmutableFeatureState
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.client.ClientDefinitionDocumentation
import site.addzero.lsi.jimmer.client.ClientSchema
import site.addzero.lsi.jimmer.client.ClientSchemaDependencies
import site.addzero.lsi.jimmer.client.ClientSchemaOptions
import site.addzero.lsi.jimmer.client.ClientService
import site.addzero.lsi.jimmer.client.ClientTargets
import site.addzero.lsi.jimmer.client.ClientTypeDefinition
import site.addzero.lsi.jimmer.client.ClientValidationException
import site.addzero.lsi.jimmer.client.clientTargets
import site.addzero.lsi.jimmer.client.fingerprint
import site.addzero.lsi.jimmer.client.requestedClientTypeSeeds
import site.addzero.lsi.jimmer.client.toClientDefinitionDocumentation
import site.addzero.lsi.jimmer.client.toClientSchema
import site.addzero.lsi.jimmer.client.unresolvedClientTargetTypeIds
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.error.ErrorSchemaOptions
import site.addzero.lsi.jimmer.error.ErrorValidationException
import site.addzero.lsi.jimmer.error.toErrorSchema
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.jimmer.unresolvedJimmerImmutableTypeIds
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiWorkspace

class ClientFeature : CompilerFeature<EmptyCompilerFeatureState, ClientFeatureState> {

    override val key = Key

    override val dependencies = setOf(DtoFeature.Key, ErrorFeature.Key, ImmutableFeature.Key)

    override val metadata = CompilerFeatureMetadata(
        aptAnnotationTypes = setOf(
            "org.babyfish.jimmer.client.EnableImplicitApi",
            "org.babyfish.jimmer.client.meta.Api",
            "org.springframework.web.bind.annotation.RestController",
        ),
        supportedOptions = setOf(
            "jimmer.client.checkedException",
            IGNORE_RESOURCE_GENERATION_OPTION,
            "jimmer.source.excludes",
            "jimmer.source.includes",
        ),
    )

    override fun requestTypeSeeds(context: CompilerTypeSeedContext): Collection<LsiTypeSeed> {
        if (context.round.options[IGNORE_RESOURCE_GENERATION_OPTION] == "true") {
            return emptyList()
        }
        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val explicitApi = context.round.workspace.hasImplicitApiMarker(
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val schemaOptions = ClientSchemaOptions(explicitApi)
        val targets = context.round.workspace.clientTargets(schemaOptions).compilationTargets(
            workspace = context.round.workspace,
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        if (targets.rootTypeIds.isEmpty()) {
            return emptyList()
        }
        return context.round.workspace.requestedClientTypeSeeds(
            targets = targets,
            dependencies = context.round.previewClientDependencies(),
            options = schemaOptions,
        )
    }

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ClientFeatureState>,
    ): CompilerFeaturePrecompileResult<ClientFeatureState> {
        val dependencies = context.clientDependencies()
        if (context.round.options[IGNORE_RESOURCE_GENERATION_OPTION] == "true") {
            return CompilerFeaturePrecompileResult(
                state = disabledClientState(dependencies),
            )
        }

        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val explicitApi = context.round.workspace.hasImplicitApiMarker(
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val schemaOptions = ClientSchemaOptions(explicitApi)
        val targets = context.round.workspace.clientTargets(schemaOptions).compilationTargets(
            workspace = context.round.workspace,
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val currentTargets = targets.only(context.round.currentRootTypeIds)
        val initialUnresolvedTypeIds = context.round.workspace.unresolvedClientTargetTypeIds(
            targets = targets,
            options = schemaOptions,
        )
        val outcome = buildAvailableClientSchema(
            workspace = context.round.workspace,
            targets = targets.without(initialUnresolvedTypeIds),
            initialUnresolvedTypeIds = initialUnresolvedTypeIds,
            dependencies = ClientSchemaDependencies(
                immutableSchema = dependencies.immutableSchema,
                errorSchema = dependencies.errorSchema,
                definitionDocumentationByTypeId = dependencies.definitionDocumentationByTypeId,
            ),
            options = schemaOptions,
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
        val state = ClientFeatureState(
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
        return CompilerFeaturePrecompileResult(
            state = state,
            diagnostics = outcome.diagnostics(deferred),
            processedSymbols = currentTargets.rootTypeIds - unavailableRootTypeIds,
            unresolvedSymbols = if (deferred) outcome.unresolvedRootTypeIds else emptySet(),
        )
    }

    override fun render(
        context: CompilerRenderContext<EmptyCompilerFeatureState, ClientFeatureState>,
    ): CompilerFeatureRenderResult {
        val state = context.state
        if (!context.round.isFinal || context.round.frontendDeferred || !state.renderable) {
            return CompilerFeatureRenderResult()
        }
        val originatingSymbols = buildSet {
            state.schema.services.mapTo(this, ClientService::id)
            state.schema.definitions.mapTo(this, ClientTypeDefinition::id)
        }
        val originatingSources = originatingSymbols.mapNotNullTo(sortedSetOf()) { symbolId ->
            context.round.workspace[symbolId]?.origin?.source
        }
        return CompilerFeatureRenderResult(
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

    companion object {
        val Key = compilerFeatureKey<ClientFeature, EmptyCompilerFeatureState, ClientFeatureState>(
            EmptyCompilerFeatureState,
        )
    }
}

enum class ClientFeatureStatus {
    RESOLVED,
    DEFERRED,
    INVALID,
    DEPENDENCY_DEFERRED,
    DEPENDENCY_INVALID,
    DISABLED,
}

data class ClientCompilerFailure(
    val rootTypeId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val message: String,
) {
    init {
        rootTypeId.requireTypeQualifiedName()
        require(message.isNotBlank()) { "Client compiler failure message cannot be blank" }
    }
}

data class ClientFeatureState(
    val status: ClientFeatureStatus,
    val dependencyStatus: CompilerResolutionStatus,
    val schema: ClientSchema,
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
) : CompilerFeatureState {

    val renderable: Boolean
        get() = status == ClientFeatureStatus.RESOLVED

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
        require(status != ClientFeatureStatus.RESOLVED || dependencyStatus == CompilerResolutionStatus.RESOLVED) {
            "Resolved client state requires resolved dependencies"
        }
        require(status != ClientFeatureStatus.RESOLVED || unresolvedRootTypeIds.isEmpty()) {
            "Resolved client state cannot contain unresolved roots"
        }
        require(status != ClientFeatureStatus.RESOLVED || invalidRootTypeIds.isEmpty()) {
            "Resolved client state cannot contain invalid roots"
        }
    }
}

private data class ClientDependencies(
    val status: CompilerResolutionStatus,
    val immutableFingerprint: String,
    val errorFingerprint: String,
    val immutableSchema: ImmutableSchema,
    val errorSchema: ErrorSchema,
    val definitionDocumentationByTypeId: Map<LsiSymbolId, ClientDefinitionDocumentation>,
    val dtoFingerprint: String,
)

private data class ClientSchemaOutcome(
    val schema: ClientSchema,
    val unresolvedRootTypeIds: Set<LsiSymbolId>,
    val failures: List<ClientCompilerFailure>,
)

private fun disabledClientState(
    dependencies: ClientDependencies,
): ClientFeatureState {
    return ClientFeatureState(
        status = ClientFeatureStatus.DISABLED,
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

private fun CompilerPrecompileContext<EmptyCompilerFeatureState, ClientFeatureState>.clientDependencies(): ClientDependencies {
    val immutableState = dependencyStates.getValue(ImmutableFeature.Key)
    val errorState = dependencyStates.getValue(ErrorFeature.Key)
    val dtoState = dependencyStates.getValue(DtoFeature.Key)
    val status = when {
        immutableState.status == CompilerResolutionStatus.INVALID ||
            errorState.status == ErrorFeatureStatus.INVALID ||
            dtoState.status in DTO_INVALID_STATUSES -> {
            CompilerResolutionStatus.INVALID
        }
        immutableState.status == CompilerResolutionStatus.DEFERRED ||
            dtoState.status in DTO_DEFERRED_STATUSES -> {
            CompilerResolutionStatus.DEFERRED
        }
        else -> CompilerResolutionStatus.RESOLVED
    }
    return ClientDependencies(
        status = status,
        immutableFingerprint = immutableState.fingerprint,
        errorFingerprint = errorState.fingerprint,
        immutableSchema = immutableState.schema,
        errorSchema = errorState.schema,
        definitionDocumentationByTypeId = dtoState.graphs
            .toClientDefinitionDocumentation(immutableState.schema),
        dtoFingerprint = dtoState.fingerprint,
    )
}

private fun CompilerRound.previewClientDependencies(): ClientSchemaDependencies {
    val immutableTypeIds = workspace.declarationsOfType<LsiClass>()
        .filter { type ->
            type.annotations.any { annotation -> annotation.type in IMMUTABLE_TYPE_ANNOTATIONS }
        }
        .mapTo(sortedSetOf(), LsiClass::id)
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
    return ClientSchemaDependencies(
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
): ClientFeatureStatus {
    return when {
        invalid -> ClientFeatureStatus.INVALID
        dependencies.status == CompilerResolutionStatus.INVALID -> {
            ClientFeatureStatus.DEPENDENCY_INVALID
        }
        unresolved && !deferred -> ClientFeatureStatus.INVALID
        deferred -> ClientFeatureStatus.DEFERRED
        dependencies.status == CompilerResolutionStatus.DEFERRED -> {
            ClientFeatureStatus.DEPENDENCY_DEFERRED
        }
        else -> ClientFeatureStatus.RESOLVED
    }
}

private fun buildAvailableClientSchema(
    workspace: LsiWorkspace,
    targets: ClientTargets,
    initialUnresolvedTypeIds: Set<LsiSymbolId>,
    dependencies: ClientSchemaDependencies,
    options: ClientSchemaOptions,
): ClientSchemaOutcome {
    var availableTargets = targets
    val unresolvedTypeIds = initialUnresolvedTypeIds.toCollection(sortedSetOf())
    val failures = mutableListOf<ClientCompilerFailure>()
    while (availableTargets.rootTypeIds.isNotEmpty()) {
        try {
            return ClientSchemaOutcome(
                schema = workspace.toClientSchema(availableTargets, dependencies, options),
                unresolvedRootTypeIds = unresolvedTypeIds,
                failures = failures,
            )
        } catch (exception: ClientValidationException) {
            val affectedRootTypeId = (exception.rootTypeId ?: exception.declarationId.rootTypeId())
                .takeIf { typeId -> typeId in availableTargets.rootTypeIds }
                ?: return ClientSchemaOutcome(
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
    return ClientSchemaOutcome(
        schema = EMPTY_SCHEMA,
        unresolvedRootTypeIds = unresolvedTypeIds,
        failures = failures,
    )
}

private fun ClientSchemaOutcome.diagnostics(
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

private fun ClientValidationException.toFailure(
    rootTypeId: LsiSymbolId,
): ClientCompilerFailure {
    return ClientCompilerFailure(
        rootTypeId = rootTypeId,
        declarationId = declarationId,
        message = message ?: "Invalid client declaration '${declarationId.value}'",
    )
}

private fun ClientTargets.compilationTargets(
    workspace: LsiWorkspace,
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): ClientTargets {
    fun Set<LsiSymbolId>.accepted(): Set<LsiSymbolId> {
        return filterTo(sortedSetOf()) { typeId ->
            val type = workspace[typeId] as? LsiClass ?: return@filterTo false
            type.isCompilationTarget(platform, sourceFilter)
        }
    }
    return ClientTargets(
        serviceTypeIds = serviceTypeIds.accepted(),
    )
}

private fun ClientTargets.only(typeIds: Set<LsiSymbolId>): ClientTargets {
    return ClientTargets(
        serviceTypeIds = serviceTypeIds intersect typeIds,
    )
}

private fun LsiWorkspace.hasImplicitApiMarker(
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): Boolean {
    return declarationsOfType<LsiClass>().any { type ->
        type.isCompilationTarget(platform, sourceFilter) &&
            type.annotations.any { annotation -> annotation.type == ENABLE_IMPLICIT_API_ANNOTATION }
    }
}

private fun LsiClass.isCompilationTarget(
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

private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"
private const val CLIENT_RESOURCE_PATH = "META-INF/jimmer/client"

private val EMPTY_SCHEMA = ClientSchema(emptyList(), emptyList())

private val DTO_DEFERRED_STATUSES = setOf(
    DtoFeatureStatus.PENDING,
    DtoFeatureStatus.INPUT_PENDING,
    DtoFeatureStatus.DEFERRED,
    DtoFeatureStatus.DEPENDENCY_DEFERRED,
)

private val DTO_INVALID_STATUSES = setOf(
    DtoFeatureStatus.INVALID,
    DtoFeatureStatus.DEPENDENCY_INVALID,
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
