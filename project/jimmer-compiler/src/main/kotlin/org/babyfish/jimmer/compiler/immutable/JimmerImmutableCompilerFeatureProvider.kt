package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor("immutable")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
        val targetTypeIds = context.round.workspace.immutableTargetTypeIds(
            platform = context.round.platform,
            sourceFilter = sourceFilter,
        )
        val semanticRootTypeIds = targetTypeIds + context.round.dtoSemanticRootTypeIds(sourceFilter)
        val currentTypeIds = context.round.currentRootTypeIds
            .filterTo(sortedSetOf(), targetTypeIds::contains)
        val precompiler = JimmerImmutablePrecompiler()
        val unresolvedTypeIds = precompiler.unresolvedTargetTypeIds(
            workspace = context.round.workspace,
            targetTypeIds = semanticRootTypeIds,
        )
        if (unresolvedTypeIds.isNotEmpty()) {
            return unresolvedResult(
                context = context,
                precompiler = precompiler,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedTypeIds = unresolvedTypeIds,
            )
        }
        return try {
            val schema = precompiler.compile(context.round.workspace, semanticRootTypeIds)
            JimmerCompilerFeaturePrecompileResult(
                state = JimmerImmutableCompilerFeatureState(
                    schema = schema,
                    targetTypeIds = targetTypeIds,
                    semanticRootTypeIds = semanticRootTypeIds,
                    currentTypeIds = currentTypeIds,
                ),
                processedSymbols = currentTypeIds,
            )
        } catch (exception: JimmerImmutablePrecompileException) {
            failedResult(
                context = context,
                precompiler = precompiler,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                exception = exception,
            )
        }
    }

    private fun unresolvedResult(
        context: JimmerCompilerPrecompileContext,
        precompiler: JimmerImmutablePrecompiler,
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        unresolvedTypeIds: Set<LsiSymbolId>,
    ): JimmerCompilerFeaturePrecompileResult {
        val deferred = context.round.platform == CompilerPlatform.APT && !context.round.isFinal
        val resolvedTypeIds = semanticRootTypeIds - unresolvedTypeIds
        val schema = try {
            precompiler.compile(context.round.workspace, resolvedTypeIds)
        } catch (exception: JimmerImmutablePrecompileException) {
            return failedResult(
                context = context,
                precompiler = precompiler,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                exception = exception,
                knownUnresolvedTypeIds = unresolvedTypeIds,
            )
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerImmutableCompilerFeatureState(
                schema = schema,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = unresolvedTypeIds,
                status = if (deferred) {
                    JimmerImmutableCompilerFeatureStatus.DEFERRED
                } else {
                    JimmerImmutableCompilerFeatureStatus.INVALID
                },
            ),
            diagnostics = if (deferred) {
                emptyList()
            } else {
                listOf(
                    LsiDiagnostic(
                        code = "jimmer.immutable.unresolved",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = "Cannot resolve immutable types: " +
                            unresolvedTypeIds.joinToString { typeId -> typeId.value },
                        symbolId = unresolvedTypeIds.first(),
                    )
                )
            },
            processedSymbols = currentTypeIds - unresolvedTypeIds,
            unresolvedSymbols = if (deferred) unresolvedTypeIds else emptySet(),
        )
    }

    private fun failedResult(
        context: JimmerCompilerPrecompileContext,
        precompiler: JimmerImmutablePrecompiler,
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        exception: JimmerImmutablePrecompileException,
        knownUnresolvedTypeIds: Set<LsiSymbolId> = emptySet(),
    ): JimmerCompilerFeaturePrecompileResult {
        val affectedTypeId = exception.declarationId.rootTypeId()
            .takeIf { typeId -> typeId in semanticRootTypeIds }
            ?: semanticRootTypeIds.firstOrNull()
            ?: exception.declarationId
        if (
            exception.recoverable &&
            context.round.platform == CompilerPlatform.APT &&
            !context.round.isFinal
        ) {
            val unresolvedTypeIds = knownUnresolvedTypeIds + affectedTypeId
            if (unresolvedTypeIds == knownUnresolvedTypeIds) {
                return deferredWithoutSchema(
                    targetTypeIds = targetTypeIds,
                    semanticRootTypeIds = semanticRootTypeIds,
                    currentTypeIds = currentTypeIds,
                    unresolvedTypeIds = unresolvedTypeIds,
                )
            }
            return unresolvedResult(
                context = context,
                precompiler = precompiler,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedTypeIds = unresolvedTypeIds,
            )
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerImmutableCompilerFeatureState(
                schema = JimmerImmutableSchema(emptyList()),
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = if (exception.recoverable) setOf(affectedTypeId) else emptySet(),
                status = JimmerImmutableCompilerFeatureStatus.INVALID,
                failure = exception.message.orEmpty(),
            ),
            diagnostics = listOf(
                LsiDiagnostic(
                    code = if (exception.recoverable) {
                        "jimmer.immutable.unresolved"
                    } else {
                        "jimmer.immutable.invalid"
                    },
                    severity = LsiDiagnosticSeverity.ERROR,
                    message = exception.message ?: "Invalid immutable type",
                    symbolId = exception.declarationId,
                )
            ),
        )
    }

    private fun deferredWithoutSchema(
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        unresolvedTypeIds: Set<LsiSymbolId>,
    ): JimmerCompilerFeaturePrecompileResult {
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerImmutableCompilerFeatureState(
                schema = JimmerImmutableSchema(emptyList()),
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = unresolvedTypeIds,
                status = JimmerImmutableCompilerFeatureStatus.DEFERRED,
            ),
            processedSymbols = currentTypeIds - unresolvedTypeIds,
            unresolvedSymbols = unresolvedTypeIds,
        )
    }
}

internal enum class JimmerImmutableCompilerFeatureStatus {
    RESOLVED,
    DEFERRED,
    INVALID,
}

internal data class JimmerImmutableCompilerFeatureState(
    val schema: JimmerImmutableSchema,
    val targetTypeIds: Set<LsiSymbolId>,
    val semanticRootTypeIds: Set<LsiSymbolId>,
    val currentTypeIds: Set<LsiSymbolId>,
    val unresolvedRootTypeIds: Set<LsiSymbolId> = emptySet(),
    val status: JimmerImmutableCompilerFeatureStatus = JimmerImmutableCompilerFeatureStatus.RESOLVED,
    val failure: String = "",
    override val fingerprint: String = buildString {
        append(status.name)
        append(':')
        append(schema.fingerprint())
        append(':')
        append(targetTypeIds.sorted().joinToString(",") { typeId -> typeId.value })
        append(':')
        append(semanticRootTypeIds.sorted().joinToString(",") { typeId -> typeId.value })
        append(':')
        append(currentTypeIds.sorted().joinToString(",") { typeId -> typeId.value })
        append(':')
        append(unresolvedRootTypeIds.sorted().joinToString(",") { typeId -> typeId.value })
        append(':')
        append(failure)
    },
) : JimmerCompilerFeatureState {
    init {
        require(currentTypeIds.all(targetTypeIds::contains)) {
            "Current immutable type ids must be part of all target type ids"
        }
        require(targetTypeIds.all(semanticRootTypeIds::contains)) {
            "Immutable generation target ids must be part of semantic root ids"
        }
        require(unresolvedRootTypeIds.all(semanticRootTypeIds::contains)) {
            "Unresolved immutable root ids must be part of semantic root ids"
        }
        require(status != JimmerImmutableCompilerFeatureStatus.RESOLVED || unresolvedRootTypeIds.isEmpty()) {
            "Resolved immutable state cannot contain unresolved roots"
        }
    }
}

private fun LsiWorkspace.immutableTargetTypeIds(
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): Set<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .asSequence()
        .filter(LsiTypeDeclaration::hasImmutableMarker)
        .filter { type -> type.origin.kind in COMPILATION_ORIGIN_KINDS }
        .filter { type -> sourceFilter.accepts(type.qualifiedName) }
        .filterNot { type ->
            platform == CompilerPlatform.APT &&
                type.annotations.any { annotation -> annotation.type == KOTLIN_METADATA }
        }
        .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
}

private fun CompilerRound.dtoSemanticRootTypeIds(
    sourceFilter: JimmerCompilerSourceFilter,
): Set<LsiSymbolId> {
    return buildSet {
        inputDocumentSnapshots.forEach { snapshot ->
            val subjectReference = snapshot.references.firstOrNull { reference ->
                reference.kind == CompilerInputDocumentReferenceKind.SUBJECT_TYPE
            } ?: return@forEach
            if (!sourceFilter.accepts(subjectReference.typeId.requireTypeQualifiedName())) {
                return@forEach
            }
            val subjectType = workspace[subjectReference.typeId] as? LsiTypeDeclaration
            if (
                platform == CompilerPlatform.APT &&
                subjectType?.annotations?.any { annotation -> annotation.type == KOTLIN_METADATA } == true
            ) {
                return@forEach
            }
            snapshot.references.forEach referenceLoop@{ reference ->
                if (
                    reference.kind != CompilerInputDocumentReferenceKind.SUBJECT_TYPE &&
                    reference.kind != CompilerInputDocumentReferenceKind.MODEL_TYPE
                ) {
                    return@referenceLoop
                }
                val type = workspace[reference.typeId] as? LsiTypeDeclaration ?: return@referenceLoop
                if (type.hasImmutableMarker()) {
                    add(type.id)
                }
            }
        }
    }
}

private fun LsiSymbolId.rootTypeId(): LsiSymbolId = LsiSymbolId(value.substringBefore('/'))

private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)

private val KOTLIN_METADATA = LsiSymbolId.type("kotlin.Metadata")
