package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerFeatureDescriptor
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureProvider
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import org.babyfish.jimmer.compiler.dto.JimmerDtoCompilerFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
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

class TypedTupleCompilerFeatureProvider : CompilerFeatureProvider {

    override val descriptor = CompilerFeatureDescriptor(
        id = TYPED_TUPLE_FEATURE_ID,
        dependsOn = setOf(IMMUTABLE_FEATURE_ID, DTO_FEATURE_ID),
        aptAnnotationTypes = setOf("org.babyfish.jimmer.sql.TypedTuple"),
    )

    override fun precompile(
        context: CompilerPrecompileContext,
    ): CompilerFeaturePrecompileResult {
        return try {
            val immutableState = context.dependencyStates[IMMUTABLE_FEATURE_ID]
                as? JimmerImmutableCompilerFeatureState
            val dtoState = context.dependencyStates[DTO_FEATURE_ID]
                as? JimmerDtoCompilerFeatureState
            val entityTypeIds = immutableState?.schema?.types.orEmpty()
                .filter { type -> type.kind == ImmutableTypeKind.ENTITY }
                .mapTo(sortedSetOf(), ImmutableType::id)
            val dtoTypeIds = dtoState?.graphs.orEmpty()
                .flatMap(DtoGraph::types)
                .mapNotNull(DtoType::qualifiedNameOrNull)
                .mapTo(sortedSetOf()) { qualifiedName -> LsiSymbolId.type(qualifiedName) }
            val schema = context.round.workspace.toTypedTupleSchema(
                entityTypeIds = entityTypeIds,
                dtoTypeIds = dtoTypeIds,
            )
            schema.validateCodegenNames()
            CompilerFeaturePrecompileResult(
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
            CompilerFeaturePrecompileResult(
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
        context: CompilerRenderContext,
    ): CompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state as TypedTupleCompilerFeatureState
        if (!state.renderable || state.schema.tuples.isEmpty()) {
            return CompilerFeatureRenderResult()
        }
        val renderer: LsiPoetRenderer = when (context.round.platform) {
            CompilerPlatform.APT -> LsiJavaPoetRenderer()
            CompilerPlatform.KSP -> LsiKotlinPoetRenderer()
            CompilerPlatform.UNKNOWN -> return CompilerFeatureRenderResult()
        }
        val artifacts = state.schema
            .toLsiPoetArtifacts(context.round.workspace)
            .map(renderer::render)
        return CompilerFeatureRenderResult(artifacts = artifacts)
    }
}

private data class TypedTupleCompilerFeatureState(
    val schema: TypedTupleSchema,
    val renderable: Boolean = true,
    override val fingerprint: String = schema.fingerprint(),
) : CompilerFeatureState {

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
