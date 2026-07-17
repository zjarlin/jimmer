package org.babyfish.jimmer.compiler.error

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.error.apt.ErrorJavaRenderer
import org.babyfish.jimmer.compiler.error.ksp.ErrorKotlinRenderer
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class ErrorCompilerFeatureProvider : JimmerCompilerFeatureProvider {
    override val descriptor = JimmerCompilerFeatureDescriptor("error")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        return try {
            val schema = ErrorPrecompiler(
                ErrorPrecompileOptions(
                    checkedException = context.round.options["jimmer.client.checkedException"] == "true"
                )
            ).compile(context.round.workspace)
            val state = ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.RESOLVED,
                schema = schema,
            )
            JimmerCompilerFeaturePrecompileResult(
                state = state,
                processedSymbols = schema.families.mapTo(sortedSetOf()) { family -> family.id },
            )
        } catch (exception: ErrorPrecompileException) {
            JimmerCompilerFeaturePrecompileResult(
                state = ErrorCompilerFeatureState.invalid(exception),
                diagnostics = listOf(
                    LsiDiagnostic(
                        code = "jimmer.error.invalid",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = exception.message ?: "Invalid error family",
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
        val state = context.state as ErrorCompilerFeatureState
        if (state.status != ErrorCompilerFeatureStatus.RESOLVED || state.schema.families.isEmpty()) {
            return JimmerCompilerFeatureRenderResult()
        }
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> ErrorJavaRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.KSP -> ErrorKotlinRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

internal enum class ErrorCompilerFeatureStatus {
    RESOLVED,
    INVALID,
}

internal data class ErrorCompilerFeatureState(
    val status: ErrorCompilerFeatureStatus,
    val schema: ErrorPrecompiledSchema,
    override val fingerprint: String = "${status.name}:${schema.fingerprint()}",
) : JimmerCompilerFeatureState {
    companion object {
        fun invalid(exception: ErrorPrecompileException): ErrorCompilerFeatureState {
            return ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.INVALID,
                schema = ErrorPrecompiledSchema(emptyList()),
                fingerprint = "INVALID:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
