package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureStatus
import org.babyfish.jimmer.dto.compiler.DtoModifier
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
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocuments = context.round.inputDocuments,
            immutableSchema = immutableState.schema,
            workspace = context.round.workspace,
            defaultNullableInputModifier = defaultNullableInputModifier,
        )
        val deferred = outcome.unresolvedDocuments.isNotEmpty() &&
            context.round.platform == CompilerPlatform.APT &&
            !context.round.isFinal
        val status = dtoStatus(
            dependencyStatus = dependencyStatus,
            deferred = deferred,
            unresolved = outcome.unresolvedDocuments.isNotEmpty(),
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
            diagnostics = outcome.diagnostics(deferred),
            processedSymbols = processedTypeIds,
            unresolvedSymbols = if (deferred) {
                outcome.unresolvedDocuments.mapTo(sortedSetOf()) { document -> document.baseTypeId }
            } else {
                emptySet()
            },
        )
    }
}

internal enum class JimmerDtoCompilerFeatureStatus {
    RESOLVED,
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
                inputPath = document.inputDocument.source.path,
                inputFingerprint = document.inputDocument.fingerprint,
                baseTypeId = document.baseTypeId,
                message = document.message,
            )
        }
        failures.forEach { failure ->
            appendDtoDocumentState(
                kind = "failure",
                inputPath = failure.inputDocument.source.path,
                inputFingerprint = failure.inputDocument.fingerprint,
                baseTypeId = failure.baseTypeId,
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
        require(unresolvedDocuments == unresolvedDocuments.sortedBy { document -> document.inputDocument }) {
            "Unresolved DTO documents must use stable input order"
        }
        require(failures == failures.sortedBy { failure -> failure.inputDocument }) {
            "DTO compiler failures must use stable input order"
        }
        require(
            unresolvedDocuments.map { document -> document.inputDocument.source.path }.toSet()
                .intersect(failures.map { failure -> failure.inputDocument.source.path }.toSet())
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
    deferred: Boolean,
    unresolved: Boolean,
    invalid: Boolean,
): JimmerDtoCompilerFeatureStatus {
    return when {
        invalid -> JimmerDtoCompilerFeatureStatus.INVALID
        dependencyStatus == JimmerDtoCompilerDependencyStatus.INVALID -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_INVALID
        }
        unresolved && !deferred -> JimmerDtoCompilerFeatureStatus.INVALID
        deferred -> JimmerDtoCompilerFeatureStatus.DEFERRED
        dependencyStatus == JimmerDtoCompilerDependencyStatus.DEFERRED -> {
            JimmerDtoCompilerFeatureStatus.DEPENDENCY_DEFERRED
        }
        else -> JimmerDtoCompilerFeatureStatus.RESOLVED
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
    deferred: Boolean,
): List<LsiDiagnostic> {
    return buildList {
        if (!deferred) {
            unresolvedDocuments.forEach { document ->
                add(
                    LsiDiagnostic(
                        code = "jimmer.dto.unresolved",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = document.message,
                        symbolId = document.baseTypeId,
                        details = mapOf("document" to document.inputDocument.source.path),
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
                    details = mapOf("document" to failure.inputDocument.source.path),
                )
            )
        }
    }
}

private fun StringBuilder.appendDtoDocumentState(
    kind: String,
    inputPath: String,
    inputFingerprint: String,
    baseTypeId: LsiSymbolId?,
    message: String,
) {
    append(':')
    append(kind)
    append(':')
    append(inputPath.length)
    append(':')
    append(inputPath)
    append(':')
    append(inputFingerprint)
    append(':')
    append(baseTypeId?.value.orEmpty())
    append(':')
    append(message.length)
    append(':')
    append(message)
}

private const val DTO_FEATURE_ID = "dto"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val DEFAULT_NULLABLE_INPUT_MODIFIER_OPTION = "jimmer.dto.defaultNullableInputModifier"
