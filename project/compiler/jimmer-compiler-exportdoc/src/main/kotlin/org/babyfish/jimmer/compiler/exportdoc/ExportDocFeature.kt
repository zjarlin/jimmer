package org.babyfish.jimmer.compiler.exportdoc

import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.exportdoc.ExportDocSchema
import site.addzero.lsi.jimmer.exportdoc.ExportDocValidationException
import site.addzero.lsi.jimmer.exportdoc.fingerprint
import site.addzero.lsi.jimmer.exportdoc.toExportDocSchema

class ExportDocFeature : CompilerFeature<EmptyCompilerFeatureState, ExportDocFeatureState> {

    override val key = Key

    override val metadata = CompilerFeatureMetadata(
        aptAnnotationTypes = setOf("org.babyfish.jimmer.client.ExportDoc"),
        supportedOptions = setOf(
            "jimmer.buddy.ignoreResourceGeneration",
            "jimmer.source.excludes",
            "jimmer.source.includes",
        ),
    )

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ExportDocFeatureState>,
    ): CompilerFeaturePrecompileResult<ExportDocFeatureState> {
        return try {
            val schema = context.round.workspace.toExportDocSchema()
            CompilerFeaturePrecompileResult(
                state = ExportDocFeatureState.resolved(schema),
                processedSymbols = schema.exportedTypeIds
                    .filterTo(sortedSetOf(), context.round.currentWorkspace::contains),
            )
        } catch (exception: ExportDocValidationException) {
            val failure = ExportDocCompilerFailure(
                configurationIds = exception.scopeIds,
                location = exception.location,
                message = exception.message ?: "Invalid @ExportDoc configuration",
            )
            CompilerFeaturePrecompileResult(
                state = ExportDocFeatureState.invalid(failure),
                diagnostics = listOf(failure.toDiagnostic()),
            )
        }
    }

    override fun render(
        context: CompilerRenderContext<EmptyCompilerFeatureState, ExportDocFeatureState>,
    ): CompilerFeatureRenderResult {
        if (!context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state
        if (state.status != ExportDocFeatureStatus.RESOLVED) {
            return CompilerFeatureRenderResult()
        }
        return CompilerFeatureRenderResult(
            artifacts = listOfNotNull(
                ExportDocResourceRenderer().render(state.schema, context.round.workspace)
            )
        )
    }

    companion object {
        val Key = compilerFeatureKey<
            ExportDocFeature,
            EmptyCompilerFeatureState,
            ExportDocFeatureState,
        >(EmptyCompilerFeatureState)
    }
}

enum class ExportDocFeatureStatus {
    RESOLVED,
    INVALID,
}

data class ExportDocCompilerFailure(
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

data class ExportDocFeatureState(
    val status: ExportDocFeatureStatus,
    val schema: ExportDocSchema,
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
) : CompilerFeatureState {

    init {
        require(status != ExportDocFeatureStatus.RESOLVED || failures.isEmpty()) {
            "Resolved ExportDoc state cannot contain failures"
        }
        require(status != ExportDocFeatureStatus.INVALID || failures.isNotEmpty()) {
            "Invalid ExportDoc state requires failures"
        }
    }

    companion object {
        fun resolved(schema: ExportDocSchema): ExportDocFeatureState {
            return ExportDocFeatureState(
                status = ExportDocFeatureStatus.RESOLVED,
                schema = schema,
                failures = emptyList(),
            )
        }

        fun invalid(failure: ExportDocCompilerFailure): ExportDocFeatureState {
            return ExportDocFeatureState(
                status = ExportDocFeatureStatus.INVALID,
                schema = EMPTY_EXPORT_DOC_SCHEMA,
                failures = listOf(failure),
            )
        }
    }
}

private val EMPTY_EXPORT_DOC_SCHEMA = ExportDocSchema(
    effectiveConfigurationIds = emptyList(),
    exportedTypeIds = emptyList(),
    entries = emptyList(),
)
