package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class JimmerDtoCompilerFeatureProvider : JimmerCompilerFeatureProvider {
    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = DTO_FEATURE_ID,
        dependsOn = setOf(IMMUTABLE_FEATURE_ID),
        classpathTypeIds = setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID),
        inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
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
        val outcome = JimmerDtoPrecompiler().compile(
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
            schema = outcome.schema,
        )
        val unresolvedStatus = dtoUnresolvedStatus(
            platform = context.round.platform,
            isFinal = context.round.isFinal,
            unresolved = outcome.unresolvedDocuments.isNotEmpty(),
        )
        val status = dtoStatus(
            dependencyStatus = dependencyStatus,
            unresolvedStatus = unresolvedStatus,
            invalid = outcome.failures.isNotEmpty(),
        )
        val state = JimmerDtoCompilerFeatureState(
            status = status,
            dependencyStatus = dependencyStatus,
            schema = outcome.schema,
            unresolvedDocuments = outcome.unresolvedDocuments,
            failures = outcome.failures,
            defaultNullableInputModifier = defaultNullableInputModifier,
            rendererOptions = rendererOptions,
            effectiveKspMutableByRootTypeId = effectiveKspMutableByRootTypeId,
            immutableDependencyFingerprint = immutableState.fingerprint,
        )
        val unavailableTypeIds = buildSet {
            outcome.unresolvedDocuments.mapTo(this) { document -> document.baseTypeId }
            outcome.failures.mapNotNullTo(this) { failure -> failure.baseTypeId }
        }
        val processedTypeIds = if (context.round.isFinal) {
            emptySet()
        } else {
            outcome.schema.documents
                .mapTo(sortedSetOf()) { document -> document.baseTypeId }
                .minus(unavailableTypeIds)
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = state,
            diagnostics = outcome.diagnostics(
                reportUnresolved = unresolvedStatus == JimmerDtoCompilerFeatureStatus.INVALID,
            ),
            processedSymbols = processedTypeIds,
            unresolvedSymbols = if (unresolvedStatus == JimmerDtoCompilerFeatureStatus.DEFERRED) {
                outcome.unresolvedDocuments.flatMapTo(sortedSetOf()) { document ->
                    document.unresolvedTypeIds
                }
            } else {
                emptySet()
            },
        )
    }
}

internal enum class JimmerDtoCompilerFeatureStatus {
    RESOLVED,
    PENDING,
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
    val schema: JimmerDtoPrecompiledSchema,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
    val defaultNullableInputModifier: DtoModifier,
    val rendererOptions: JimmerDtoRendererOptions,
    val effectiveKspMutableByRootTypeId: Map<JimmerDtoTypeId, Boolean>,
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
        append(schema.fingerprint())
        unresolvedDocuments.forEach { document ->
            appendDtoDocumentState(
                kind = "unresolved",
                inputSnapshot = document.inputSnapshot,
                baseTypeId = document.baseTypeId,
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
                baseTypeId = failure.baseTypeId,
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
        require(
            effectiveKspMutableByRootTypeId.keys.toList() ==
                effectiveKspMutableByRootTypeId.keys.sorted()
        ) {
            "DTO KSP renderer plan must use stable root type id order"
        }
        val rootTypeIds = schema.documents
            .flatMap { document -> document.renderGraph.rootTypeIds }
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
): JimmerDtoCompilerFeatureStatus {
    return when {
        invalid -> JimmerDtoCompilerFeatureStatus.INVALID
        dependencyStatus == JimmerDtoCompilerDependencyStatus.INVALID -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_INVALID
        }
        unresolvedStatus != null -> unresolvedStatus
        dependencyStatus == JimmerDtoCompilerDependencyStatus.DEFERRED -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_DEFERRED
        }
        else -> JimmerDtoCompilerFeatureStatus.RESOLVED
    }
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

private fun JimmerDtoPrecompileOutcome.diagnostics(
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
                            reference.typeId == document.unresolvedTypeIds.first()
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
    baseTypeId: LsiSymbolId?,
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
        append(':')
        append(reference.typeId.value)
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
    append(baseTypeId?.value.orEmpty())
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

private fun StringBuilder.appendEffectiveKspMutableByRootTypeId(
    effectiveKspMutableByRootTypeId: Map<JimmerDtoTypeId, Boolean>,
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

private const val DTO_FEATURE_ID = "dto"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION = "jimmer.dto.defaultNullableInputModifier"
internal val JACKSON_3_OBJECT_MAPPER_TYPE_ID = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
