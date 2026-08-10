package org.babyfish.jimmer.compiler.tuple

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
import org.babyfish.jimmer.compiler.dto.DtoFeature
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.qualifiedNameOrNull
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleValidationException
import site.addzero.lsi.jimmer.tuple.fingerprint
import site.addzero.lsi.jimmer.tuple.toTypedTupleSchema
import site.addzero.lsi.jimmer.tuple.typedTupleTypeIds
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TupleFeature : CompilerFeature<EmptyCompilerFeatureState, TupleFeatureState> {

    override val key = Key

    override val dependencies = setOf(ImmutableFeature.Key, DtoFeature.Key)

    override val metadata = CompilerFeatureMetadata(
        aptAnnotationTypes = setOf("org.babyfish.jimmer.sql.TypedTuple"),
    )

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, TupleFeatureState>,
    ): CompilerFeaturePrecompileResult<TupleFeatureState> {
        return try {
            val immutableState = context.dependencyStates.getValue(ImmutableFeature.Key)
            val dtoState = context.dependencyStates.getValue(DtoFeature.Key)
            val entityTypeIds = immutableState.schema.types
                .filter { type -> type.kind == ImmutableTypeKind.ENTITY }
                .mapTo(sortedSetOf(), ImmutableType::id)
            val dtoTypeIds = dtoState.graphs
                .flatMap(DtoGraph::types)
                .mapNotNull(DtoType::qualifiedNameOrNull)
                .mapTo(sortedSetOf()) { qualifiedName -> LsiSymbolId.type(qualifiedName) }
            val schema = context.round.workspace.toTypedTupleSchema(
                entityTypeIds = entityTypeIds,
                dtoTypeIds = dtoTypeIds,
            )
            schema.validateCodegenNames()
            CompilerFeaturePrecompileResult(
                state = TupleFeatureState(schema),
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
            CompilerFeaturePrecompileResult(
                state = if (deferred) {
                    TupleFeatureState.deferred(exception)
                } else {
                    TupleFeatureState.invalid(exception)
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
        context: CompilerRenderContext<EmptyCompilerFeatureState, TupleFeatureState>,
    ): CompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state
        if (!state.renderable || state.schema.tuples.isEmpty()) {
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
        val Key = compilerFeatureKey<TupleFeature, EmptyCompilerFeatureState, TupleFeatureState>(
            EmptyCompilerFeatureState
        )
    }
}

data class TupleFeatureState(
    val schema: TypedTupleSchema,
    val renderable: Boolean = true,
    override val fingerprint: String = schema.fingerprint(),
) : CompilerFeatureState {

    companion object {
        fun deferred(exception: TypedTupleValidationException): TupleFeatureState {
            return failed("deferred", exception)
        }

        fun invalid(exception: TypedTupleValidationException): TupleFeatureState {
            return failed("invalid", exception)
        }

        private fun failed(
            status: String,
            exception: TypedTupleValidationException,
        ): TupleFeatureState {
            return TupleFeatureState(
                schema = TypedTupleSchema(emptyList()),
                renderable = false,
                fingerprint = "$status:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
