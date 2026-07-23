package org.babyfish.jimmer.compiler.tuple

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
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleValidationException
import site.addzero.lsi.jimmer.tuple.fingerprint
import site.addzero.lsi.jimmer.tuple.toTypedTupleSchema
import site.addzero.lsi.jimmer.tuple.typedTupleTypeIds
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TypedTupleCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = TYPED_TUPLE_FEATURE_ID,
        dependsOn = setOf(IMMUTABLE_FEATURE_ID, DTO_FEATURE_ID),
    )

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        return try {
            val schema = context.round.workspace.toTypedTupleSchema()
            schema.validateCodegenNames()
            JimmerCompilerFeaturePrecompileResult(
                state = TypedTupleCompilerFeatureState(schema),
                processedSymbols = schema.tuples.mapTo(sortedSetOf()) { tuple -> tuple.id },
            )
        } catch (exception: TypedTupleValidationException) {
            val deferred = exception.recoverable &&
                context.round.platform == CompilerPlatform.APT &&
                !context.round.isFinal
            val unresolvedSymbols = if (deferred) {
                context.round.workspace.typedTupleTypeIds().ifEmpty { setOf(exception.declarationId) }
            } else {
                emptySet()
            }
            JimmerCompilerFeaturePrecompileResult(
                state = if (deferred) {
                    TypedTupleCompilerFeatureState.deferred(exception)
                } else {
                    TypedTupleCompilerFeatureState.invalid(exception)
                },
                diagnostics = if (deferred) {
                    emptyList()
                } else {
                    listOf(
                        LsiDiagnostic(
                            code = if (exception.recoverable) {
                                "jimmer.tuple.unresolved"
                            } else {
                                "jimmer.tuple.invalid"
                            },
                            severity = LsiDiagnosticSeverity.ERROR,
                            message = exception.message ?: "Invalid typed tuple",
                            symbolId = exception.declarationId,
                        )
                    )
                },
                unresolvedSymbols = unresolvedSymbols,
            )
        }
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as TypedTupleCompilerFeatureState
        if (!state.renderable || state.schema.tuples.isEmpty()) {
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

private data class TypedTupleCompilerFeatureState(
    val schema: TypedTupleSchema,
    val renderable: Boolean = true,
    override val fingerprint: String = schema.fingerprint(),
) : JimmerCompilerFeatureState {

    companion object {
        fun deferred(exception: TypedTupleValidationException): TypedTupleCompilerFeatureState {
            return failed("deferred", exception)
        }

        fun invalid(exception: TypedTupleValidationException): TypedTupleCompilerFeatureState {
            return failed("invalid", exception)
        }

        private fun failed(
            status: String,
            exception: TypedTupleValidationException,
        ): TypedTupleCompilerFeatureState {
            return TypedTupleCompilerFeatureState(
                schema = TypedTupleSchema(emptyList()),
                renderable = false,
                fingerprint = "$status:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}

private const val TYPED_TUPLE_FEATURE_ID = "tuple"
private const val IMMUTABLE_FEATURE_ID = "immutable"
private const val DTO_FEATURE_ID = "dto"
