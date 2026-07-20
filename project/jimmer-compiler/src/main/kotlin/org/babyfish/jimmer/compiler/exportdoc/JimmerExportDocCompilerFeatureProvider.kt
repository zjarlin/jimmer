package org.babyfish.jimmer.compiler.exportdoc

import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class JimmerExportDocCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(EXPORT_DOC_FEATURE_ID)

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        return try {
            val schema = ExportDocPrecompiler().compile(context.round.workspace)
            JimmerCompilerFeaturePrecompileResult(
                state = JimmerExportDocCompilerFeatureState.resolved(schema),
                processedSymbols = schema.exportedTypeIds
                    .filterTo(sortedSetOf(), context.round.currentWorkspace::contains),
            )
        } catch (exception: ExportDocPrecompileException) {
            val failure = ExportDocCompilerFailure(
                configurationIds = exception.scopeIds,
                location = exception.location,
                message = exception.message ?: "Invalid @ExportDoc configuration",
            )
            JimmerCompilerFeaturePrecompileResult(
                state = JimmerExportDocCompilerFeatureState.invalid(failure),
                diagnostics = listOf(failure.toDiagnostic()),
            )
        }
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (!context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as JimmerExportDocCompilerFeatureState
        if (state.status != JimmerExportDocCompilerFeatureStatus.RESOLVED) {
            return JimmerCompilerFeatureRenderResult()
        }
        return JimmerCompilerFeatureRenderResult(
            artifacts = listOfNotNull(
                ExportDocResourceRenderer().render(state.schema, context.round.workspace)
            )
        )
    }
}

internal enum class JimmerExportDocCompilerFeatureStatus {
    RESOLVED,
    INVALID,
}

internal data class ExportDocCompilerFailure(
    val configurationIds: List<LsiSymbolId>,
    val location: LsiLocation?,
    val message: String,
) {
    init {
        require(configurationIds.isNotEmpty()) {
            "ExportDoc compiler failure must reference at least one configuration"
        }
        require(configurationIds == configurationIds.distinct().sorted()) {
            "ExportDoc compiler failure configuration ids must be distinct and sorted"
        }
        require(message.isNotBlank()) { "ExportDoc compiler failure message cannot be blank" }
    }

    fun toDiagnostic(): LsiDiagnostic {
        return LsiDiagnostic(
            code = "jimmer.export-doc.invalid",
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = configurationIds.first(),
            location = location,
        )
    }
}

internal data class JimmerExportDocCompilerFeatureState(
    val status: JimmerExportDocCompilerFeatureStatus,
    val schema: ExportDocPrecompiledSchema,
    val failures: List<ExportDocCompilerFailure>,
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(schema.fingerprint())
        failures.forEach { failure ->
            failure.configurationIds.forEach { configurationId ->
                append(':')
                append(configurationId.value)
            }
            append(':')
            append(failure.message.length)
            append(':')
            append(failure.message)
        }
    },
) : JimmerCompilerFeatureState {

    init {
        require(status != JimmerExportDocCompilerFeatureStatus.RESOLVED || failures.isEmpty()) {
            "Resolved ExportDoc state cannot contain failures"
        }
        require(status != JimmerExportDocCompilerFeatureStatus.INVALID || failures.isNotEmpty()) {
            "Invalid ExportDoc state requires failures"
        }
    }

    companion object {
        fun resolved(schema: ExportDocPrecompiledSchema): JimmerExportDocCompilerFeatureState {
            return JimmerExportDocCompilerFeatureState(
                status = JimmerExportDocCompilerFeatureStatus.RESOLVED,
                schema = schema,
                failures = emptyList(),
            )
        }

        fun invalid(failure: ExportDocCompilerFailure): JimmerExportDocCompilerFeatureState {
            return JimmerExportDocCompilerFeatureState(
                status = JimmerExportDocCompilerFeatureStatus.INVALID,
                schema = EMPTY_EXPORT_DOC_SCHEMA,
                failures = listOf(failure),
            )
        }
    }
}

const val EXPORT_DOC_FEATURE_ID = "export-doc"

private val EMPTY_EXPORT_DOC_SCHEMA = ExportDocPrecompiledSchema(
    effectiveConfigurationIds = emptyList(),
    exportedTypeIds = emptyList(),
    docs = emptyList(),
)
