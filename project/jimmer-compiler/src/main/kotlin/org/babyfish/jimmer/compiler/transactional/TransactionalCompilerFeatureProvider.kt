package org.babyfish.jimmer.compiler.transactional

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.transactional.apt.TransactionalJavaRenderer
import org.babyfish.jimmer.compiler.transactional.ksp.TransactionalKotlinRenderer
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class TransactionalCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor("transactional")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        if (context.round.options["jimmer.buddy.ignoreResourceGeneration"] == "true") {
            return JimmerCompilerFeaturePrecompileResult(
                state = TransactionalCompilerFeatureState(TransactionalPrecompiledSchema(emptyList())),
            )
        }
        return try {
            val schema = TransactionalPrecompiler().compile(context.round.workspace)
            JimmerCompilerFeaturePrecompileResult(
                state = TransactionalCompilerFeatureState(schema),
                processedSymbols = schema.types.mapTo(sortedSetOf()) { type -> type.id },
            )
        } catch (exception: TransactionalPrecompileException) {
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
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> TransactionalJavaRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.KSP -> TransactionalKotlinRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

private data class TransactionalCompilerFeatureState(
    val schema: TransactionalPrecompiledSchema,
    val invalid: Boolean = false,
    override val fingerprint: String = schema.fingerprint(),
) : JimmerCompilerFeatureState {

    companion object {
        fun invalid(exception: TransactionalPrecompileException): TransactionalCompilerFeatureState {
            return TransactionalCompilerFeatureState(
                schema = TransactionalPrecompiledSchema(emptyList()),
                invalid = true,
                fingerprint = "invalid:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
