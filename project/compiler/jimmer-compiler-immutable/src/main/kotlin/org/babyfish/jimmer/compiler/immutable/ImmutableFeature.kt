package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.input.*

import site.addzero.lsi.compiler.CompilerInputDocumentReferenceKind
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerRound
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.input.selectOwnerTarget
import org.babyfish.jimmer.compiler.input.selectType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.JIMMER_KEEP_IS_PREFIX_OPTION
import site.addzero.lsi.jimmer.fingerprint
import site.addzero.lsi.jimmer.isJimmerImmutableType
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.jimmer.unresolvedJimmerImmutableTypeIds
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ImmutableFeature : CompilerFeature<EmptyCompilerFeatureState, ImmutableFeatureState> {

    override val key = Key

    override val metadata = CompilerFeatureMetadata(
        aptAnnotationTypes = setOf(
            "org.babyfish.jimmer.Immutable",
            "org.babyfish.jimmer.sql.Entity",
            "org.babyfish.jimmer.sql.MappedSuperclass",
            "org.babyfish.jimmer.sql.Embeddable",
            "org.babyfish.jimmer.internal.GeneratedBy",
        ),
        supportedOptions = setOf(
            "jimmer.excludedUserAnnotationPrefixes",
            "jimmer.jackson3",
            JIMMER_KEEP_IS_PREFIX_OPTION,
            "jimmer.source.excludes",
            "jimmer.source.includes",
        ),
    )

    override fun precompile(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ImmutableFeatureState>,
    ): CompilerFeaturePrecompileResult<ImmutableFeatureState> {
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
            CompilerFeaturePrecompileResult(
                state = ImmutableFeatureState(
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
        context: CompilerRenderContext<EmptyCompilerFeatureState, ImmutableFeatureState>,
    ): CompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state
        if (state.status == CompilerResolutionStatus.INVALID) {
            return CompilerFeatureRenderResult()
        }
        val resolvedTypeIds = state.schema.typesById.keys
        val resolvedTargetTypeIds = state.targetTypeIds.intersect(resolvedTypeIds)
        val resolvedCurrentTypeIds = state.currentTypeIds.intersect(resolvedTypeIds)
        val fetcherTypes = state.schema.generatedFetcherTypes(resolvedTargetTypeIds)
        val draftTypes = state.draftCodegenSchema.generatedDraftTypes(resolvedCurrentTypeIds)
        val embeddableTypes = state.schema.generatedEmbeddableTypes(resolvedCurrentTypeIds)
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> {
                val sharedRenderer: LsiPoetRenderer = LsiJavaPoetRenderer()
                val queryTypes = state.schema.generatedPropsTypes(resolvedTargetTypeIds)
                state.schema.toDraftPoetArtifacts(
                    draftSchema = state.draftCodegenSchema,
                    types = draftTypes,
                    language = LsiLanguage.JAVA,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toFetcherPoetArtifacts(
                    types = fetcherTypes,
                    language = LsiLanguage.JAVA,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toEmbeddablePoetArtifacts(
                    types = embeddableTypes,
                    language = LsiLanguage.JAVA,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toQueryPoetArtifacts(
                    types = queryTypes,
                    language = LsiLanguage.JAVA,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render)
            }
            CompilerPlatform.KSP -> {
                val sharedRenderer: LsiPoetRenderer = LsiKotlinPoetRenderer()
                val queryTypes = state.schema.generatedQueryTypes(resolvedTargetTypeIds)
                state.schema.toDraftPoetArtifacts(
                    draftSchema = state.draftCodegenSchema,
                    types = draftTypes,
                    language = LsiLanguage.KOTLIN,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toFetcherPoetArtifacts(
                    types = fetcherTypes,
                    language = LsiLanguage.KOTLIN,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toEmbeddablePoetArtifacts(
                    types = embeddableTypes,
                    language = LsiLanguage.KOTLIN,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render) + state.schema.toQueryPoetArtifacts(
                    types = queryTypes,
                    language = LsiLanguage.KOTLIN,
                    workspace = context.round.workspace,
                ).map(sharedRenderer::render)
            }
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return CompilerFeatureRenderResult(artifacts = artifacts)
    }

    private fun validateSourceLayout(
        round: CompilerRound,
        currentTypeIds: Set<LsiSymbolId>,
    ) {
        if (round.platform != CompilerPlatform.KSP) {
            return
        }
        val typesBySource = currentTypeIds
            .mapNotNull { typeId -> round.workspace[typeId] as? LsiClass }
            .mapNotNull { type -> type.origin.source?.let { source -> source to type } }
            .groupBy(
                keySelector = { (source, _) -> source },
                valueTransform = { (_, type) -> type },
            )
        val conflict = typesBySource.entries
            .firstOrNull { (_, types) -> types.size > 1 }
            ?: return
        val conflictingTypes = conflict.value.sortedBy(LsiClass::qualifiedName)
        throw ImmutablePrecompileException(
            declarationId = conflictingTypes.first().id,
            message = "Source '${conflict.key.path}' declares several Jimmer immutable types: " +
                conflictingTypes.joinToString { type -> type.qualifiedName },
        )
    }

    private fun unresolvedResult(
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ImmutableFeatureState>,
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        unresolvedTypeIds: Set<LsiSymbolId>,
    ): CompilerFeaturePrecompileResult<ImmutableFeatureState> {
        val deferred = context.round.canDeferUnresolvedTypes()
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
        return CompilerFeaturePrecompileResult(
            state = ImmutableFeatureState(
                schema = schema,
                draftCodegenSchema = draftCodegenSchema,
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = unresolvedTypeIds,
                status = if (deferred) {
                    CompilerResolutionStatus.DEFERRED
                } else {
                    CompilerResolutionStatus.INVALID
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
        context: CompilerPrecompileContext<EmptyCompilerFeatureState, ImmutableFeatureState>,
        targetTypeIds: Set<LsiSymbolId>,
        semanticRootTypeIds: Set<LsiSymbolId>,
        currentTypeIds: Set<LsiSymbolId>,
        exception: ImmutablePrecompileException,
        knownUnresolvedTypeIds: Set<LsiSymbolId> = emptySet(),
    ): CompilerFeaturePrecompileResult<ImmutableFeatureState> {
        val affectedTypeId = exception.declarationId.rootTypeId()
            .takeIf { typeId -> typeId in semanticRootTypeIds }
            ?: semanticRootTypeIds.firstOrNull()
            ?: exception.declarationId
        if (
            exception.recoverable &&
            context.round.canDeferUnresolvedTypes()
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
        return CompilerFeaturePrecompileResult(
            state = ImmutableFeatureState(
                schema = ImmutableSchema(emptyList()),
                draftCodegenSchema = JimmerImmutableDraftCodegenSchema(
                    jacksonFamily = JacksonFamily.JACKSON_2,
                    types = emptyList(),
                ),
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = if (exception.recoverable) setOf(affectedTypeId) else emptySet(),
                status = CompilerResolutionStatus.INVALID,
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
    ): CompilerFeaturePrecompileResult<ImmutableFeatureState> {
        return CompilerFeaturePrecompileResult(
            state = ImmutableFeatureState(
                schema = ImmutableSchema(emptyList()),
                draftCodegenSchema = JimmerImmutableDraftCodegenSchema(
                    jacksonFamily = JacksonFamily.JACKSON_2,
                    types = emptyList(),
                ),
                targetTypeIds = targetTypeIds,
                semanticRootTypeIds = semanticRootTypeIds,
                currentTypeIds = currentTypeIds,
                unresolvedRootTypeIds = unresolvedTypeIds,
                status = CompilerResolutionStatus.DEFERRED,
            ),
            processedSymbols = currentTypeIds - unresolvedTypeIds,
            unresolvedSymbols = unresolvedTypeIds,
        )
    }

    companion object {
        val Key = compilerFeatureKey<
            ImmutableFeature,
            EmptyCompilerFeatureState,
            ImmutableFeatureState,
        >(EmptyCompilerFeatureState)
    }

}

private fun CompilerRound.canDeferUnresolvedTypes(): Boolean {
    if (isFinal) {
        return false
    }
    return platform == CompilerPlatform.APT ||
        platform == CompilerPlatform.KSP && frontendDeferred
}

data class ImmutableFeatureState(
    val schema: ImmutableSchema,
    val draftCodegenSchema: JimmerImmutableDraftCodegenSchema,
    val targetTypeIds: Set<LsiSymbolId>,
    val semanticRootTypeIds: Set<LsiSymbolId>,
    val currentTypeIds: Set<LsiSymbolId>,
    val unresolvedRootTypeIds: Set<LsiSymbolId> = emptySet(),
    val status: CompilerResolutionStatus = CompilerResolutionStatus.RESOLVED,
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
) : CompilerFeatureState {
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
        require(status != CompilerResolutionStatus.RESOLVED || unresolvedRootTypeIds.isEmpty()) {
            "Resolved immutable state cannot contain unresolved roots"
        }
    }
}

private fun LsiWorkspace.immutableTargetTypeIds(
    platform: CompilerPlatform,
    sourceFilter: JimmerCompilerSourceFilter,
): Set<LsiSymbolId> {
    return declarationsOfType<LsiClass>()
        .asSequence()
        .filter(LsiClass::isJimmerImmutableType)
        .filter { type -> type.origin.kind in COMPILATION_ORIGIN_KINDS }
        .filter { type -> sourceFilter.accepts(type.qualifiedName) }
        .filter { type -> type.isCompilerTargetVisible(platform) }
        .mapTo(sortedSetOf(), LsiClass::id)
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
                    val declaration = workspace[typeId] as? LsiClass
                    if (declaration == null || declaration.isJimmerImmutableType()) {
                        add(typeId)
                    }
                }
                snapshot.references
                    .asSequence()
                    .filter { reference -> reference.kind == DTO_MODEL_TYPE_REFERENCE_KIND }
                    .filter { reference ->
                        val ownerSelection = reference.selectOwnerTarget(workspace)
                        ownerSelection == null ||
                            !ownerSelection.isAmbiguous &&
                            ownerSelection.selectedTypeId in activeTargetTypeIds
                    }
                    .mapNotNullTo(this) { reference -> reference.selectType(workspace).selectedTypeId }
            }
            semanticTypeIds.forEach referenceLoop@{ typeId ->
                val type = workspace[typeId] as? LsiClass ?: return@referenceLoop
                if (type.isJimmerImmutableType()) {
                    add(type.id)
                }
            }
        }
    }
}

private fun CompilerInputDocumentReferenceKind.isDtoTarget(): Boolean {
    return this == DTO_SUBJECT_TYPE_REFERENCE_KIND ||
        this == DTO_TARGET_TYPE_REFERENCE_KIND
}

private fun LsiWorkspace.isCompilerTargetVisible(
    typeId: LsiSymbolId,
    platform: CompilerPlatform,
): Boolean {
    val type = this[typeId] as? LsiClass ?: return true
    return type.isCompilerTargetVisible(platform)
}

private fun LsiClass.isCompilerTargetVisible(
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
