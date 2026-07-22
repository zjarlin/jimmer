package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.collectImmutableValidationDependencies
import site.addzero.lsi.jimmer.semanticDependencySymbols
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.referencedSymbolIds

/**
 * 将 Draft 预编译语义降低为统一的 LSI Poet 产物。
 */
internal fun ImmutableSchema.toDraftPoetArtifacts(
    draftSchema: JimmerImmutableDraftCodegenSchema,
    types: List<JimmerImmutableDraftTypePlan>,
    language: LsiLanguage,
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
        "Immutable draft Poet generation requires Java or Kotlin"
    }
    return types.map { type ->
        require(draftSchema.typesById[type.typeId] == type) {
            "Immutable draft type '${type.typeId.value}' does not belong to the supplied draft schema"
        }
        require(typesById.containsKey(type.typeId)) {
            "Immutable draft type '${type.typeId.value}' does not belong to the supplied immutable schema"
        }
        val file = when (language) {
            LsiLanguage.JAVA -> draftSchema.toJavaDraftPoetFile(type)
            LsiLanguage.KOTLIN -> draftSchema.toKotlinDraftPoetFile(type)
            LsiLanguage.UNKNOWN -> error("Unsupported immutable draft Poet language")
        }
        draftArtifact(type, file, workspace)
    }
}

private fun ImmutableSchema.draftArtifact(
    type: JimmerImmutableDraftTypePlan,
    file: LsiPoetFile,
    workspace: LsiWorkspace,
): LsiPoetArtifact {
    val originatingSymbols = setOf(type.typeId)
    val originatingSources = workspace.nonBinarySources(originatingSymbols)
    val semanticProps = type.propsBySlot.map { prop ->
        requireNotNull(propsById[prop.propId]) {
            "Immutable draft property '${prop.propId.value}' is absent from its semantic schema"
        }
    }
    val semanticDependencySymbols = semanticDependencySymbols(
        rootTypeIds = originatingSymbols,
        rootProps = semanticProps,
        workspace = workspace,
    ).toSortedSet().apply {
        collectImmutableValidationDependencies(type.customValidations)
    }
    val dependencySymbols = sortedSetOf<LsiSymbolId>().apply {
        addAll(semanticDependencySymbols)
        addAll(file.referencedSymbolIds())
    }
    // 源文件增量关系只能由稳定语义输入决定。渲染 IR 还会引用本轮生成的 Draft
    // 类型；下一轮它们进入 workspace 后，不能反过来把自己的产物变成新增输入。
    val dependencySources = workspace.nonBinarySources(semanticDependencySymbols)
    return LsiPoetArtifact(
        file = file,
        aggregationMode = classifyArtifactAggregationMode(
            originatingSymbols = originatingSymbols,
            originatingSources = originatingSources,
            dependencySources = dependencySources,
        ),
        emissionMode = ArtifactEmissionMode.IMMEDIATE,
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = dependencySymbols,
        dependencySources = dependencySources,
    )
}

private fun LsiWorkspace.nonBinarySources(symbolIds: Collection<LsiSymbolId>): Set<LsiSource> {
    return originatingSources(symbolIds)
        .filterTo(sortedSetOf()) { source -> source.kind != LsiSourceKind.BINARY }
}

internal fun draftDeclaredType(
    id: LsiSymbolId,
    vararg arguments: LsiTypeRef,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = id,
        arguments = arguments.map(LsiTypeArgument::invariant),
    )
}

internal fun draftCode(block: LsiPoetCodeBuilder.() -> Unit): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build(block)
}
