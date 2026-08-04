package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.apt.dto.DtoProcessor as AptDtoProcessor
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentBundleRenderer
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.ksp.dto.DtoProcessor as KspDtoProcessor
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoTypeId

class JimmerDtoCompilerFeatureProvider : JimmerCompilerFeatureProvider {
    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = DTO_FEATURE_ID,
        dependsOn = setOf(IMMUTABLE_FEATURE_ID),
        classpathTypeIds = setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID),
        inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
        requiresSourceQuiescence = true,
    )

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        val immutableState = requireNotNull(
            context.dependencyStates[IMMUTABLE_FEATURE_ID] as? JimmerImmutableCompilerFeatureState
        ) {
            "DTO feature requires immutable compiler state"
        }
        val dependencyStatus = immutableState.status.toDtoDependencyStatus()
        val defaultNullableInputModifier = context.round.options.defaultNullableInputModifier()
        val rendererOptions = context.round.toJimmerDtoRendererOptions()
        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val resolution = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = context.round.inputDocumentSnapshots,
            immutableSchema = immutableState.schema,
            immutableSemanticRootTypeIds = immutableState.semanticRootTypeIds,
            workspace = context.round.workspace,
            sourceFilter = sourceFilter,
            defaultNullableInputModifier = defaultNullableInputModifier,
            platform = context.round.platform,
        )
        val effectiveKspMutableByRootTypeId = rendererOptions.effectiveKspMutableByRootTypeId(
            platform = context.round.platform,
            graphs = resolution.graphs,
        )
        val unresolvedStatus = dtoUnresolvedStatus(
            platform = context.round.platform,
            isFinal = context.round.isFinal,
            unresolved = resolution.unresolvedDocuments.isNotEmpty(),
        )
        val status = dtoStatus(
            dependencyStatus = dependencyStatus,
            unresolvedStatus = unresolvedStatus,
            invalid = resolution.failures.isNotEmpty(),
            inputDocumentDiscoveryComplete = context.round.inputDocumentDiscoveryComplete,
            isFinal = context.round.isFinal,
        )
        val state = JimmerDtoCompilerFeatureState(
            status = status,
            dependencyStatus = dependencyStatus,
            graphs = resolution.graphs,
            annotationContractsBySource = resolution.annotationContractsBySource,
            interfaceContractsBySource = resolution.interfaceContractsBySource,
            configContractsBySource = resolution.configContractsBySource,
            resolvedInputFingerprint = resolution.resolvedInputs.resolvedInputFingerprint(),
            unresolvedDocuments = resolution.unresolvedDocuments,
            failures = resolution.failures,
            defaultNullableInputModifier = defaultNullableInputModifier,
            rendererOptions = rendererOptions,
            effectiveKspMutableByRootTypeId = effectiveKspMutableByRootTypeId,
            inputDocumentDiscoveryComplete = context.round.inputDocumentDiscoveryComplete,
            immutableDependencyFingerprint = immutableState.fingerprint,
        )
        val unavailableTypeIds = buildSet {
            resolution.unresolvedDocuments.flatMapTo(this) { document -> document.targetTypeIds }
            resolution.failures.flatMapTo(this) { failure -> failure.targetTypeIds }
        }
        val processedTypeIds = if (context.round.isFinal) {
            emptySet()
        } else {
            resolution.resolvedInputs
                .flatMapTo(sortedSetOf()) { input -> input.targetTypeIds }
                .minus(unavailableTypeIds)
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = state,
            diagnostics = resolution.diagnostics(
                reportUnresolved = unresolvedStatus == JimmerDtoCompilerFeatureStatus.INVALID,
            ) + inputDiscoveryDiagnostics(context.round),
            processedSymbols = if (context.round.inputDocumentDiscoveryComplete) processedTypeIds else emptySet(),
            unresolvedSymbols = if (unresolvedStatus == JimmerDtoCompilerFeatureStatus.DEFERRED) {
                resolution.unresolvedDocuments.flatMapTo(sortedSetOf()) { document ->
                    document.unresolvedTypeIds
                }
            } else {
                emptySet()
            },
        )
    }

    override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return renderInputDocumentBundle(context)
        }
        val state = requireNotNull(context.state as? JimmerDtoCompilerFeatureState) {
            "DTO feature render requires the frozen DTO compiler state"
        }
        if (state.status != JimmerDtoCompilerFeatureStatus.RESOLVED) {
            return JimmerCompilerFeatureRenderResult()
        }
        val immutableState = requireNotNull(
            context.dependencyStates[IMMUTABLE_FEATURE_ID] as? JimmerImmutableCompilerFeatureState
        ) {
            "DTO feature render requires immutable compiler state"
        }
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> AptDtoProcessor(
                state.graphs,
                state.annotationContractsBySource,
                state.interfaceContractsBySource,
                state.configContractsBySource,
                immutableState.schema,
                context.round.workspace,
                state.rendererOptions,
            ).process()
            CompilerPlatform.KSP -> KspDtoProcessor(
                graphs = state.graphs,
                immutableSchema = immutableState.schema,
                rendererOptions = state.rendererOptions,
                effectiveMutableByRootTypeId = state.effectiveKspMutableByRootTypeId,
                workspace = context.round.workspace,
                annotationContractsBySource = state.annotationContractsBySource,
                interfaceContractsBySource = state.interfaceContractsBySource,
                configContractsBySource = state.configContractsBySource,
            ).process()
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }

    private fun renderInputDocumentBundle(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (!context.round.inputDocumentDiscoveryComplete) {
            return JimmerCompilerFeatureRenderResult()
        }
        val bundleId = context.round.options[CompilerInputDocumentBundleRenderer.BUNDLE_ID_OPTION]
            ?: return JimmerCompilerFeatureRenderResult()
        require(bundleId.isNotBlank() && bundleId == bundleId.trim()) {
            "Compiler option '${CompilerInputDocumentBundleRenderer.BUNDLE_ID_OPTION}' must be a canonical bundle id"
        }
        return JimmerCompilerFeatureRenderResult(
            artifacts = CompilerInputDocumentBundleRenderer().render(
                bundleId = bundleId,
                snapshots = context.round.inputDocumentSnapshots,
            ),
        )
    }
}

internal enum class JimmerDtoCompilerFeatureStatus {
    RESOLVED,
    PENDING,
    INPUT_PENDING,
    DEFERRED,
    INVALID,
    DEPENDENCY_DEFERRED,
    DEPENDENCY_INVALID,
}

internal enum class JimmerDtoCompilerDependencyStatus {
    RESOLVED,
    DEFERRED,
    INVALID,
}

internal data class JimmerDtoCompilerFeatureState(
    val status: JimmerDtoCompilerFeatureStatus,
    val dependencyStatus: JimmerDtoCompilerDependencyStatus,
    val graphs: List<DtoGraph>,
    val annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    val interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    val configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
    val resolvedInputFingerprint: String,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
    val defaultNullableInputModifier: DtoModifier,
    val rendererOptions: JimmerDtoRendererOptions,
    val effectiveKspMutableByRootTypeId: Map<DtoTypeId, Boolean>,
    val inputDocumentDiscoveryComplete: Boolean,
    val immutableDependencyFingerprint: String,
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(dependencyStatus.name)
        append(':')
        append(defaultNullableInputModifier.name)
        append(':')
        append(rendererOptions.fingerprint)
        append(':')
        appendEffectiveKspMutableByRootTypeId(effectiveKspMutableByRootTypeId)
        append(':')
        append(inputDocumentDiscoveryComplete)
        append(':')
        append(resolvedInputFingerprint)
        append(':')
        append(
            dtoSemanticFingerprint(
                graphs = graphs,
                annotationContractsBySource = annotationContractsBySource,
                interfaceContractsBySource = interfaceContractsBySource,
                configContractsBySource = configContractsBySource,
            )
        )
        unresolvedDocuments.forEach { document ->
            appendDtoDocumentState(
                kind = "unresolved",
                inputSnapshot = document.inputSnapshot,
                targetTypeIds = document.targetTypeIds,
                unresolvedTypeIds = document.unresolvedTypeIds,
                diagnosticCode = "jimmer.dto.unresolved",
                diagnosticSeverity = LsiDiagnosticSeverity.ERROR,
                diagnosticSymbolId = document.unresolvedTypeIds.first(),
                diagnosticLocation = null,
                message = document.message,
                diagnosticDetails = sortedMapOf(
                    "document" to document.inputSnapshot.document.source.path,
                ),
            )
        }
        failures.forEach { failure ->
            appendDtoDocumentState(
                kind = "failure",
                inputSnapshot = failure.inputSnapshot,
                targetTypeIds = failure.targetTypeIds,
                unresolvedTypeIds = emptyList(),
                diagnosticCode = failure.code,
                diagnosticSeverity = failure.severity,
                diagnosticSymbolId = failure.symbolId,
                diagnosticLocation = failure.location,
                message = failure.message,
                diagnosticDetails = failure.details,
            )
        }
        append(':')
        append(immutableDependencyFingerprint)
    },
) : JimmerCompilerFeatureState {
    init {
        require(defaultNullableInputModifier.isInputStrategy) {
            "DTO feature state requires an input strategy modifier"
        }
        require(unresolvedDocuments == unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot)) {
            "Unresolved DTO documents must use stable input order"
        }
        require(failures == failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR)) {
            "DTO compiler failures must use stable diagnostic order"
        }
        require(resolvedInputFingerprint.isNotBlank()) {
            "DTO resolved input fingerprint cannot be blank"
        }
        requireDtoResolvedContracts(
            graphs = graphs,
            annotationContractsBySource = annotationContractsBySource,
            interfaceContractsBySource = interfaceContractsBySource,
            configContractsBySource = configContractsBySource,
        )
        require(
            effectiveKspMutableByRootTypeId.keys.toList() ==
                effectiveKspMutableByRootTypeId.keys.sorted()
        ) {
            "DTO KSP renderer plan must use stable root type id order"
        }
        val rootTypeIds = graphs
            .flatMap(DtoGraph::rootTypeIds)
            .sorted()
        require(effectiveKspMutableByRootTypeId.keys.toList() == rootTypeIds) {
            "DTO KSP renderer plan must cover every frozen root type"
        }
        require(
            unresolvedDocuments.map { document -> document.inputSnapshot.document.source.path }.toSet()
                .intersect(failures.map { failure -> failure.inputSnapshot.document.source.path }.toSet())
                .isEmpty()
        ) {
            "DTO documents cannot be both unresolved and invalid"
        }
        require(status != JimmerDtoCompilerFeatureStatus.RESOLVED || dependencyStatus == JimmerDtoCompilerDependencyStatus.RESOLVED) {
            "Resolved DTO state requires resolved immutable dependency"
        }
        require(status != JimmerDtoCompilerFeatureStatus.RESOLVED || unresolvedDocuments.isEmpty()) {
            "Resolved DTO state cannot contain unresolved documents"
        }
        require(status != JimmerDtoCompilerFeatureStatus.RESOLVED || failures.isEmpty()) {
            "Resolved DTO state cannot contain failures"
        }
        require(status != JimmerDtoCompilerFeatureStatus.RESOLVED || inputDocumentDiscoveryComplete) {
            "Resolved DTO state requires complete input document discovery"
        }
        require(status != JimmerDtoCompilerFeatureStatus.PENDING || unresolvedDocuments.isNotEmpty()) {
            "Pending DTO state requires unresolved documents"
        }
        require(status != JimmerDtoCompilerFeatureStatus.DEFERRED || unresolvedDocuments.isNotEmpty()) {
            "Deferred DTO state requires unresolved documents"
        }
    }
}

private fun JimmerImmutableCompilerFeatureStatus.toDtoDependencyStatus(): JimmerDtoCompilerDependencyStatus {
    return when (this) {
        JimmerImmutableCompilerFeatureStatus.RESOLVED -> JimmerDtoCompilerDependencyStatus.RESOLVED
        JimmerImmutableCompilerFeatureStatus.DEFERRED -> JimmerDtoCompilerDependencyStatus.DEFERRED
        JimmerImmutableCompilerFeatureStatus.INVALID -> JimmerDtoCompilerDependencyStatus.INVALID
    }
}

private fun dtoStatus(
    dependencyStatus: JimmerDtoCompilerDependencyStatus,
    unresolvedStatus: JimmerDtoCompilerFeatureStatus?,
    invalid: Boolean,
    inputDocumentDiscoveryComplete: Boolean,
    isFinal: Boolean,
): JimmerDtoCompilerFeatureStatus {
    return when {
        invalid -> JimmerDtoCompilerFeatureStatus.INVALID
        dependencyStatus == JimmerDtoCompilerDependencyStatus.INVALID -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_INVALID
        }
        !inputDocumentDiscoveryComplete -> if (isFinal) {
            JimmerDtoCompilerFeatureStatus.INVALID
        } else {
            JimmerDtoCompilerFeatureStatus.INPUT_PENDING
        }
        unresolvedStatus != null -> unresolvedStatus
        dependencyStatus == JimmerDtoCompilerDependencyStatus.DEFERRED -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_DEFERRED
        }
        else -> JimmerDtoCompilerFeatureStatus.RESOLVED
    }
}

private fun inputDiscoveryDiagnostics(
    round: org.babyfish.jimmer.compiler.CompilerRound,
): List<LsiDiagnostic> {
    if (round.inputDocumentDiscoveryComplete || !round.isFinal) {
        return emptyList()
    }
    return listOf(
        LsiDiagnostic(
            code = "jimmer.dto.input-discovery",
            severity = LsiDiagnosticSeverity.ERROR,
            message = "KSP could not discover project DTO directories because no source file was available; " +
                "set jimmer.dto.dirs=/ when only dependency DTO bundles are used",
        )
    )
}

private fun dtoUnresolvedStatus(
    platform: CompilerPlatform,
    isFinal: Boolean,
    unresolved: Boolean,
): JimmerDtoCompilerFeatureStatus? {
    if (!unresolved) {
        return null
    }
    if (isFinal) {
        return JimmerDtoCompilerFeatureStatus.INVALID
    }
    return when (platform) {
        CompilerPlatform.APT -> JimmerDtoCompilerFeatureStatus.DEFERRED
        CompilerPlatform.KSP -> JimmerDtoCompilerFeatureStatus.PENDING
        CompilerPlatform.UNKNOWN -> JimmerDtoCompilerFeatureStatus.INVALID
    }
}

private fun Map<String, String>.defaultNullableInputModifier(): DtoModifier {
    return when (this[DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION]) {
        null, "", "static" -> DtoModifier.STATIC
        "fixed" -> DtoModifier.FIXED
        "dynamic" -> DtoModifier.DYNAMIC
        "fuzzy" -> DtoModifier.FUZZY
        else -> throw IllegalArgumentException(
            "The compiler option `$DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION` can only be " +
                "\"fixed\", \"static\", \"dynamic\" or \"fuzzy\"",
        )
    }
}

private fun JimmerDtoRoundResolution.diagnostics(
    reportUnresolved: Boolean,
): List<LsiDiagnostic> {
    return buildList {
        if (reportUnresolved) {
            unresolvedDocuments.forEach { document ->
                add(
                    LsiDiagnostic(
                        code = "jimmer.dto.unresolved",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = document.message,
                        symbolId = document.unresolvedTypeIds.first(),
                        location = document.inputSnapshot.references.firstOrNull { reference ->
                            val unresolvedTypeId = document.unresolvedTypeIds.first()
                            unresolvedTypeId in reference.typeSelector.candidateTypeIds ||
                                unresolvedTypeId in reference.ownerTargetSelector?.candidateTypeIds.orEmpty()
                        }?.location,
                        details = mapOf("document" to document.inputSnapshot.document.source.path),
                    )
                )
            }
        }
        failures.forEach { failure ->
            add(
                LsiDiagnostic(
                    code = failure.code,
                    severity = failure.severity,
                    message = failure.message,
                    symbolId = failure.symbolId,
                    location = failure.location,
                    details = failure.details,
                )
            )
        }
    }
}

private fun StringBuilder.appendDtoDocumentState(
    kind: String,
    inputSnapshot: CompilerInputDocumentSnapshot,
    targetTypeIds: List<LsiSymbolId>,
    unresolvedTypeIds: List<LsiSymbolId>,
    diagnosticCode: String,
    diagnosticSeverity: LsiDiagnosticSeverity,
    diagnosticSymbolId: LsiSymbolId?,
    diagnosticLocation: LsiLocation?,
    message: String,
    diagnosticDetails: Map<String, String>,
) {
    append(':')
    append(kind)
    append(':')
    append(inputSnapshot.document.source.path.length)
    append(':')
    append(inputSnapshot.document.source.path)
    append(':')
    append(inputSnapshot.document.fingerprint)
    inputSnapshot.references.forEach { reference ->
        append(':')
        append(reference.kind.name)
        appendLengthPrefixed(reference.typeSelector.canonicalText())
        appendLengthPrefixed(reference.ownerTargetSelector?.canonicalText().orEmpty())
        append(':')
        append(reference.location.start.line)
        append(':')
        append(reference.location.start.column)
        append(':')
        append(reference.location.end.line)
        append(':')
        append(reference.location.end.column)
    }
    append(':')
    append(targetTypeIds.joinToString(",") { typeId -> typeId.value })
    append(':')
    append(unresolvedTypeIds.joinToString(",") { typeId -> typeId.value })
    append(':')
    append(diagnosticCode)
    append(':')
    append(diagnosticSeverity.name)
    append(':')
    append(diagnosticSymbolId?.value.orEmpty())
    append(':')
    append(diagnosticLocation?.source?.path.orEmpty())
    append(':')
    append(diagnosticLocation?.source?.language?.name.orEmpty())
    append(':')
    append(diagnosticLocation?.source?.kind?.name.orEmpty())
    append(':')
    append(diagnosticLocation?.start?.line?.toString().orEmpty())
    append(':')
    append(diagnosticLocation?.start?.column?.toString().orEmpty())
    append(':')
    append(diagnosticLocation?.end?.line?.toString().orEmpty())
    append(':')
    append(diagnosticLocation?.end?.column?.toString().orEmpty())
    append(':')
    append(message.length)
    append(':')
    append(message)
    diagnosticDetails.toSortedMap().forEach { (name, value) ->
        append(':')
        append(name.length)
        append(':')
        append(name)
        append(':')
        append(value.length)
        append(':')
        append(value)
    }
}

private fun StringBuilder.appendLengthPrefixed(value: String) {
    append(':')
    append(value.length)
    append(':')
    append(value)
}

private fun StringBuilder.appendEffectiveKspMutableByRootTypeId(
    effectiveKspMutableByRootTypeId: Map<DtoTypeId, Boolean>,
) {
    append(effectiveKspMutableByRootTypeId.size)
    effectiveKspMutableByRootTypeId.forEach { (rootTypeId, mutable) ->
        append(':')
        append(rootTypeId.value.length)
        append(':')
        append(rootTypeId.value)
        append(':')
        append(mutable)
    }
}

internal const val DTO_FEATURE_ID = "dto"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION = "jimmer.dto.defaultNullableInputModifier"
internal val JACKSON_3_OBJECT_MAPPER_TYPE_ID = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
