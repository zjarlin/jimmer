package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.input.selectOwnerTarget
import org.babyfish.jimmer.compiler.input.selectType
import org.babyfish.jimmer.compiler.immutable.apt.JimmerImmutableEmbeddableJavaRenderer
import org.babyfish.jimmer.compiler.immutable.apt.JimmerImmutableDraftJavaRenderer
import org.babyfish.jimmer.compiler.immutable.apt.JimmerImmutableFetcherJavaRenderer
import org.babyfish.jimmer.compiler.immutable.apt.JimmerImmutableQueryJavaRenderer
import org.babyfish.jimmer.compiler.immutable.ksp.JimmerImmutableEmbeddableKotlinRenderer
import org.babyfish.jimmer.compiler.immutable.ksp.JimmerImmutableDraftKotlinRenderer
import org.babyfish.jimmer.compiler.immutable.ksp.JimmerImmutableFetcherKotlinRenderer
import org.babyfish.jimmer.compiler.immutable.ksp.JimmerImmutableQueryKotlinRenderer
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.fingerprint
import site.addzero.lsi.jimmer.isJimmerImmutableType
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.jimmer.unresolvedJimmerImmutableTypeIds
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(JIMMER_IMMUTABLE_FEATURE_ID)

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
        try {
            validateSourceLayout(context.round, currentTypeIds)
        } catch (exception: ImmutablePrecompileException) {
            return failedResult(
                context = context,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                exception = exception,
            )
        }
        val unresolvedTypeIds = context.round.workspace.unresolvedJimmerImmutableTypeIds(semanticRootTypeIds)
        if (unresolvedTypeIds.isNotEmpty()) {
            return unresolvedResult(
                context = context,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedTypeIds = unresolvedTypeIds,
            )
        }
        return try {
            val schema = context.round.workspace.toImmutableSchema(semanticRootTypeIds)
            val draftCodegenSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
                schema = schema,
                workspace = context.round.workspace,
                options = JimmerImmutableDraftCodegenOptions.from(
                    compilerOptions = context.round.options,
                    workspace = context.round.workspace,
                ),
            )
            schema.validateFetcherGenerationContracts(targetTypeIds)
            JimmerCompilerFeaturePrecompileResult(
                state = JimmerImmutableCompilerFeatureState(
                    schema = schema,
                    draftCodegenSchema = draftCodegenSchema,
                    targetTypeIds = targetTypeIds,
                    semanticRootTypeIds = semanticRootTypeIds,
                    currentTypeIds = currentTypeIds,
                ),
                processedSymbols = currentTypeIds,
            )
        } catch (exception: ImmutablePrecompileException) {
            failedResult(
                context = context,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                exception = exception,
            )
        }
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as JimmerImmutableCompilerFeatureState
        if (state.status != JimmerImmutableCompilerFeatureStatus.RESOLVED) {
            return JimmerCompilerFeatureRenderResult()
        }
        val fetcherTypes = state.schema.generatedFetcherTypes(state.targetTypeIds)
        val draftTypes = state.draftCodegenSchema.generatedDraftTypes(state.currentTypeIds)
        val embeddableTypes = state.schema.generatedEmbeddableTypes(state.currentTypeIds)
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> {
                val draftRenderer = JimmerImmutableDraftJavaRenderer()
                val fetcherRenderer = JimmerImmutableFetcherJavaRenderer()
                val embeddableRenderer = JimmerImmutableEmbeddableJavaRenderer()
                val queryRenderer = JimmerImmutableQueryJavaRenderer()
                val queryTypes = state.schema.generatedPropsTypes(state.targetTypeIds)
                draftTypes.map { type ->
                    draftRenderer.render(state.draftCodegenSchema, type)
                } + fetcherTypes.map { type ->
                    fetcherRenderer.render(state.schema, type, context.round.workspace)
                } + embeddableTypes.flatMap { type ->
                    embeddableRenderer.render(state.schema, type, context.round.workspace)
                } + queryTypes.flatMap { type ->
                    queryRenderer.render(state.schema, type, context.round.workspace)
                }
            }
            CompilerPlatform.KSP -> {
                val draftRenderer = JimmerImmutableDraftKotlinRenderer()
                val fetcherRenderer = JimmerImmutableFetcherKotlinRenderer()
                val embeddableRenderer = JimmerImmutableEmbeddableKotlinRenderer()
                val queryRenderer = JimmerImmutableQueryKotlinRenderer()
                val queryTypes = state.schema.generatedQueryTypes(state.targetTypeIds)
                draftTypes.map { type ->
                    draftRenderer.render(state.draftCodegenSchema, type)
                } + fetcherTypes.map { type ->
                    fetcherRenderer.render(state.schema, type, context.round.workspace)
                } + embeddableTypes.map { type ->
                    embeddableRenderer.render(state.schema, type, context.round.workspace)
                } + queryTypes.map { type ->
                    queryRenderer.render(state.schema, type, context.round.workspace)
                }
            }
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }

    private fun validateSourceLayout(
        round: CompilerRound,
        currentTypeIds: Set<LsiSymbolId>,
    ) {
        if (round.platform != CompilerPlatform.KSP) {
            return
        }
        val typesBySource = currentTypeIds
            .mapNotNull { typeId -> round.workspace[typeId] as? LsiTypeDeclaration }
            .mapNotNull { type -> type.origin.source?.let { source -> source to type } }
            .groupBy(
                keySelector = { (source, _) -> source },
                valueTransform = { (_, type) -> type },
            )
        val conflict = typesBySource.entries
            .firstOrNull { (_, types) -> types.size > 1 }
            ?: return
        val conflictingTypes = conflict.value.sortedBy(LsiTypeDeclaration::qualifiedName)
        throw ImmutablePrecompileException(
            declarationId = conflictingTypes.first().id,
            message = "Source '${conflict.key.path}' declares several Jimmer immutable types: " +
                conflictingTypes.joinToString { type -> type.qualifiedName },
        )
    }

    private fun unresolvedResult(
        context: JimmerCompilerPrecompileContext,
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        unresolvedTypeIds: Set<LsiSymbolId>,
    ): JimmerCompilerFeaturePrecompileResult {
        val deferred = context.round.platform == CompilerPlatform.APT && !context.round.isFinal
        val resolvedTypeIds = semanticRootTypeIds - unresolvedTypeIds
        val (schema, draftCodegenSchema) = try {
            val schema = context.round.workspace.toImmutableSchema(resolvedTypeIds)
            schema to JimmerImmutableDraftCodegenPrecompiler().compile(
                schema = schema,
                workspace = context.round.workspace,
                options = JimmerImmutableDraftCodegenOptions.from(
                    compilerOptions = context.round.options,
                    workspace = context.round.workspace,
                ),
            )
        } catch (exception: ImmutablePrecompileException) {
            return failedResult(
                context = context,
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
                draftCodegenSchema = draftCodegenSchema,
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
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        exception: ImmutablePrecompileException,
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
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedTypeIds = unresolvedTypeIds,
            )
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerImmutableCompilerFeatureState(
                schema = ImmutableSchema(emptyList()),
                draftCodegenSchema = JimmerImmutableDraftCodegenSchema(
                    jacksonFamily = JimmerImmutableJacksonFamily.JACKSON_2,
                    types = emptyList(),
                ),
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
                schema = ImmutableSchema(emptyList()),
                draftCodegenSchema = JimmerImmutableDraftCodegenSchema(
                    jacksonFamily = JimmerImmutableJacksonFamily.JACKSON_2,
                    types = emptyList(),
                ),
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
    val schema: ImmutableSchema,
    val draftCodegenSchema: JimmerImmutableDraftCodegenSchema,
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
        append(draftCodegenSchema.fingerprint())
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
        require(draftCodegenSchema.typesById.keys == schema.typesById.keys) {
            "Immutable draft codegen types must match immutable semantic types"
        }
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
        .filter(LsiTypeDeclaration::isJimmerImmutableType)
        .filter { type -> type.origin.kind in COMPILATION_ORIGIN_KINDS }
        .filter { type -> sourceFilter.accepts(type.qualifiedName) }
        .filter { type -> type.isCompilerTargetVisible(platform) }
        .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
}

private fun CompilerRound.dtoSemanticRootTypeIds(
    sourceFilter: JimmerCompilerSourceFilter,
): Set<LsiSymbolId> {
    return buildSet {
        inputDocumentSnapshots.forEach snapshotLoop@{ snapshot ->
            val activeTargetTypeIds = snapshot.references
                .asSequence()
                .filter { reference -> reference.kind.isDtoTarget() }
                .mapNotNull { reference -> reference.selectType(workspace).selectedTypeId }
                .filter { typeId -> sourceFilter.accepts(typeId.requireTypeQualifiedName()) }
                .filter { typeId -> workspace.isCompilerTargetVisible(typeId, platform) }
                .toSet()
            if (activeTargetTypeIds.isEmpty()) {
                return@snapshotLoop
            }
            val semanticTypeIds = buildSet {
                activeTargetTypeIds.forEach { typeId ->
                    val declaration = workspace[typeId] as? LsiTypeDeclaration
                    if (declaration == null || declaration.isJimmerImmutableType()) {
                        add(typeId)
                    }
                }
                snapshot.references
                    .asSequence()
                    .filter { reference -> reference.kind == CompilerInputDocumentReferenceKind.MODEL_TYPE }
                    .filter { reference ->
                        val ownerSelection = reference.selectOwnerTarget(workspace)
                        ownerSelection == null ||
                            !ownerSelection.isAmbiguous &&
                            ownerSelection.selectedTypeId in activeTargetTypeIds
                    }
                    .mapNotNullTo(this) { reference -> reference.selectType(workspace).selectedTypeId }
            }
            semanticTypeIds.forEach referenceLoop@{ typeId ->
                val type = workspace[typeId] as? LsiTypeDeclaration ?: return@referenceLoop
                if (type.isJimmerImmutableType()) {
                    add(type.id)
                }
            }
        }
    }
}

private fun CompilerInputDocumentReferenceKind.isDtoTarget(): Boolean {
    return this == CompilerInputDocumentReferenceKind.SUBJECT_TYPE ||
        this == CompilerInputDocumentReferenceKind.TARGET_TYPE
}

private fun LsiWorkspace.isCompilerTargetVisible(
    typeId: LsiSymbolId,
    platform: CompilerPlatform,
): Boolean {
    val type = this[typeId] as? LsiTypeDeclaration ?: return true
    return type.isCompilerTargetVisible(platform)
}

private fun LsiTypeDeclaration.isCompilerTargetVisible(
    platform: CompilerPlatform,
): Boolean {
    return when (platform) {
        CompilerPlatform.APT ->
            annotations.none { annotation -> annotation.type == KOTLIN_METADATA }
        CompilerPlatform.KSP -> origin.language != LsiLanguage.JAVA
        CompilerPlatform.UNKNOWN -> true
    }
}

private fun LsiSymbolId.rootTypeId(): LsiSymbolId = LsiSymbolId(value.substringBefore('/'))

private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)

private val KOTLIN_METADATA = LsiSymbolId.type("kotlin.Metadata")

private const val JIMMER_IMMUTABLE_FEATURE_ID = "immutable"
