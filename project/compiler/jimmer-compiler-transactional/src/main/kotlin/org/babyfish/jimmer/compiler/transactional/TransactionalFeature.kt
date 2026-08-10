package org.babyfish.jimmer.compiler.transactional

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.transactional.TransactionalSchema
import site.addzero.lsi.jimmer.transactional.TransactionalValidationException
import site.addzero.lsi.jimmer.transactional.fingerprint
import site.addzero.lsi.jimmer.transactional.toTransactionalSchema
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TransactionalFeature : CompilerFeature<EmptyCompilerFeatureState, TransactionalFeatureState> {

    override val key = Key

    override val metadata = CompilerFeatureMetadata(
        aptAnnotationTypes = setOf("org.babyfish.jimmer.sql.transaction.Tx"),
        supportedOptions = setOf(IGNORE_RESOURCE_GENERATION_OPTION),
    )

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, TransactionalFeatureState>,
    ): CompilerFeaturePrecompileResult<TransactionalFeatureState> {
        if (context.round.options[IGNORE_RESOURCE_GENERATION_OPTION] == "true") {
            return CompilerFeaturePrecompileResult(
                state = TransactionalFeatureState(TransactionalSchema(emptyList())),
            )
        }
        return try {
            val schema = context.round.workspace.toTransactionalSchema()
            CompilerFeaturePrecompileResult(
                state = TransactionalFeatureState(schema),
                processedSymbols = schema.types.mapTo(sortedSetOf()) { type -> type.id },
            )
        } catch (exception: TransactionalValidationException) {
            CompilerFeaturePrecompileResult(
                state = TransactionalFeatureState.invalid(exception),
                diagnostics = listOf(
                    LsiDiagnostic(
                        code = "jimmer.transactional.invalid",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = exception.message ?: "Invalid transactional type",
                        symbolId = exception.declarationId,
                    )
                ),
            )
        }
    }

    override fun render(
        context: CompilerRenderContext<EmptyCompilerFeatureState, TransactionalFeatureState>,
    ): CompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state
        if (state.invalid || state.schema.types.isEmpty()) {
            return CompilerFeatureRenderResult()
        }
        val renderer: LsiPoetRenderer = when (context.round.platform) {
            CompilerPlatform.APT -> LsiJavaPoetRenderer()
            CompilerPlatform.KSP -> LsiKotlinPoetRenderer()
            CompilerPlatform.UNKNOWN -> return CompilerFeatureRenderResult()
        }
        val artifacts = state.schema
            .toLsiSourceArtifacts(context.round.workspace)
            .map(renderer::render)
        return CompilerFeatureRenderResult(artifacts = artifacts)
    }

    companion object {
        val Key = compilerFeatureKey<
            TransactionalFeature,
            EmptyCompilerFeatureState,
            TransactionalFeatureState,
        >(EmptyCompilerFeatureState)
    }
}

private const val IGNORE_RESOURCE_GENERATION_OPTION = "jimmer.buddy.ignoreResourceGeneration"

data class TransactionalFeatureState(
    val schema: TransactionalSchema,
    val invalid: Boolean = false,
    override val fingerprint: String = schema.fingerprint(),
) : CompilerFeatureState {

    companion object {
        fun invalid(exception: TransactionalValidationException): TransactionalFeatureState {
            return TransactionalFeatureState(
                schema = TransactionalSchema(emptyList()),
                invalid = true,
                fingerprint = "invalid:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
