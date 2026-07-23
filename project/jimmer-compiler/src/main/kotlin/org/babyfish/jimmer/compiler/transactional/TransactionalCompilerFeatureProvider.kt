package org.babyfish.jimmer.compiler.transactional

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.transactional.TransactionalSchema
import site.addzero.lsi.jimmer.transactional.TransactionalValidationException
import site.addzero.lsi.jimmer.transactional.fingerprint
import site.addzero.lsi.jimmer.transactional.toTransactionalSchema
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TransactionalCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor("transactional")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        if (context.round.options["jimmer.buddy.ignoreResourceGeneration"] == "true") {
            return JimmerCompilerFeaturePrecompileResult(
                state = TransactionalCompilerFeatureState(TransactionalSchema(emptyList())),
            )
        }
        return try {
            val schema = context.round.workspace.toTransactionalSchema()
            JimmerCompilerFeaturePrecompileResult(
                state = TransactionalCompilerFeatureState(schema),
                processedSymbols = schema.types.mapTo(sortedSetOf()) { type -> type.id },
            )
        } catch (exception: TransactionalValidationException) {
            JimmerCompilerFeaturePrecompileResult(
                state = TransactionalCompilerFeatureState.invalid(exception),
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
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as TransactionalCompilerFeatureState
        if (state.invalid || state.schema.types.isEmpty()) {
            return JimmerCompilerFeatureRenderResult()
        }
        val renderer: LsiPoetRenderer = when (context.round.platform) {
            CompilerPlatform.APT -> LsiJavaPoetRenderer()
            CompilerPlatform.KSP -> LsiKotlinPoetRenderer()
            CompilerPlatform.UNKNOWN -> return JimmerCompilerFeatureRenderResult()
        }
        val artifacts = state.schema
            .toLsiPoetArtifacts(context.round.workspace)
            .map(renderer::render)
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

private data class TransactionalCompilerFeatureState(
    val schema: TransactionalSchema,
    val invalid: Boolean = false,
    override val fingerprint: String = schema.fingerprint(),
) : JimmerCompilerFeatureState {

    companion object {
        fun invalid(exception: TransactionalValidationException): TransactionalCompilerFeatureState {
            return TransactionalCompilerFeatureState(
                schema = TransactionalSchema(emptyList()),
                invalid = true,
                fingerprint = "invalid:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
