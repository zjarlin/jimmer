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
        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = context.round.inputDocumentSnapshots,
            immutableSchema = immutableState.schema,
            immutableSemanticRootTypeIds = immutableState.semanticRootTypeIds,
            workspace = context.round.workspace,
            sourceFilter = sourceFilter,
            defaultNullableInputModifier = defaultNullableInputModifier,
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
    val immutableDependencyFingerprint: String,
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(dependencyStatus.name)
        append(':')
        append(defaultNullableInputModifier.name)
        append(':')
        append(schema.fingerprint())
        unresolvedDocuments.forEach { document ->
            appendDtoDocumentState(
                kind = "unresolved",
                inputSnapshot = document.inputSnapshot,
                baseTypeId = document.baseTypeId,
                unresolvedTypeIds = document.unresolvedTypeIds,
                diagnosticLocation = null,
                message = document.message,
            )
        }
        failures.forEach { failure ->
            appendDtoDocumentState(
                kind = "failure",
                inputSnapshot = failure.inputSnapshot,
                baseTypeId = failure.baseTypeId,
                unresolvedTypeIds = emptyList(),
                diagnosticLocation = failure.location,
                message = failure.message,
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
        require(failures == failures.sortedBy(JimmerDtoCompilerFailure::inputSnapshot)) {
            "DTO compiler failures must use stable input order"
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
                    code = "jimmer.dto.invalid",
                    severity = LsiDiagnosticSeverity.ERROR,
                    message = failure.message,
                    symbolId = failure.baseTypeId,
                    location = failure.location,
                    details = mapOf("document" to failure.inputSnapshot.document.source.path),
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
    diagnosticLocation: LsiLocation?,
    message: String,
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
    append(diagnosticLocation?.start?.line?.toString().orEmpty())
    append(':')
    append(diagnosticLocation?.start?.column?.toString().orEmpty())
    append(':')
    append(message.length)
    append(':')
    append(message)
}

private const val DTO_FEATURE_ID = "dto"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION = "jimmer.dto.defaultNullableInputModifier"
