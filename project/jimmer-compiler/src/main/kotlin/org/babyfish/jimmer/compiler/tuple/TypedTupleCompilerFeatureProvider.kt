package org.babyfish.jimmer.compiler.tuple

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.tuple.apt.TypedTupleJavaRenderer
import org.babyfish.jimmer.compiler.tuple.ksp.TypedTupleKotlinRenderer
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration

class TypedTupleCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor("tuple")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        return try {
            val schema = TypedTuplePrecompiler().compile(context.round.workspace)
            JimmerCompilerFeaturePrecompileResult(
                state = TypedTupleCompilerFeatureState(schema),
                processedSymbols = schema.tuples.mapTo(sortedSetOf()) { tuple -> tuple.id },
            )
        } catch (exception: TypedTuplePrecompileException) {
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
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> TypedTupleJavaRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.KSP -> TypedTupleKotlinRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

private fun site.addzero.lsi.model.LsiWorkspace.typedTupleTypeIds(): Set<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .filter { type -> type.annotations.any { annotation -> annotation.type == TYPED_TUPLE_ANNOTATION } }
        .mapTo(sortedSetOf()) { type -> type.id }
}

private val TYPED_TUPLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.TypedTuple")

private data class TypedTupleCompilerFeatureState(
    val schema: TypedTuplePrecompiledSchema,
    val renderable: Boolean = true,
    override val fingerprint: String = schema.fingerprint(),
) : JimmerCompilerFeatureState {

    companion object {
        fun deferred(exception: TypedTuplePrecompileException): TypedTupleCompilerFeatureState {
            return failed("deferred", exception)
        }

        fun invalid(exception: TypedTuplePrecompileException): TypedTupleCompilerFeatureState {
            return failed("invalid", exception)
        }

        private fun failed(
            status: String,
            exception: TypedTuplePrecompileException,
        ): TypedTupleCompilerFeatureState {
            return TypedTupleCompilerFeatureState(
                schema = TypedTuplePrecompiledSchema(emptyList()),
                renderable = false,
                fingerprint = "$status:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
