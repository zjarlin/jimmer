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
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.generatedTopLevelTypeName
import site.addzero.lsi.model.referencedSymbolIds
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.toLsiTypeNames

/**
 * 将 Draft 预编译语义降低为统一的 LSI Poet 产物。
 */
internal fun ImmutableSchema.toDraftPoetArtifacts(
    draftSchema: JimmerImmutableDraftCodegenSchema,
    types: List<JimmerImmutableDraftTypePlan>,
    language: LsiLanguage,
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
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
        draftArtifact(type, file, workspace, draftSchema)
    }
}

private fun ImmutableSchema.draftArtifact(
    type: JimmerImmutableDraftTypePlan,
    file: LsiFile,
    workspace: LsiWorkspace,
    draftSchema: JimmerImmutableDraftCodegenSchema,
): LsiSourceArtifact {
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
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiTypeNames(
            file.referencedTypeIds,
            additional = draftSchema.generatedPoetTypeNames() + DRAFT_RUNTIME_TYPE_NAMES,
        ),
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

private fun JimmerImmutableDraftCodegenSchema.generatedPoetTypeNames(): List<LsiTypeName> {
    return types.flatMap { type ->
        val packageName = type.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
        val simpleName = type.qualifiedName.substringAfterLast('.')
        val draftSimpleName = "$simpleName$DRAFT_TYPE_SUFFIX"
        listOf(
            generatedTopLevelTypeName(packageName, draftSimpleName),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "Producer")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "Producer", "Implementor")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "Producer", "Impl")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "Producer", "DraftImpl")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "Builder")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "$")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "$", "Implementor")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "$", "Impl")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "$", "DraftImpl")),
            generatedNestedPoetTypeName(packageName, listOf(draftSimpleName, "$", "Builder")),
        )
    }.distinctBy { typeName -> typeName.typeId }
}

private fun LsiWorkspace.nonBinarySources(symbolIds: Collection<LsiSymbolId>): Set<LsiSource> {
    return originatingSources(symbolIds)
        .filterTo(sortedSetOf()) { source -> source.kind != LsiSourceKind.BINARY }
}

internal fun draftDeclaredType(
    id: LsiSymbolId,
    vararg arguments: LsiType,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = id,
        arguments = arguments.map(LsiTypeArgument::invariant),
    )
}

private const val DRAFT_TYPE_SUFFIX = "Draft"

private val DRAFT_RUNTIME_TYPE_IDS = listOf(
    "java.lang.Object",
    "java.lang.String",
    "java.lang.System",
    "java.lang.Cloneable",
    "java.lang.CloneNotSupportedException",
    "java.lang.IllegalArgumentException",
    "java.lang.IllegalStateException",
    "java.lang.Override",
    "java.lang.SuppressWarnings",
    "java.io.Serializable",
    "java.util.Arrays",
    "java.util.Collections",
    "java.util.Objects",
    "java.util.ArrayList",
    "java.util.List",
    "kotlin.Any",
    "kotlin.String",
    "kotlin.Suppress",
    "kotlin.Cloneable",
    "kotlin.collections.MutableList",
    "org.babyfish.jimmer.Draft",
    "org.babyfish.jimmer.DraftConsumer",
    "org.babyfish.jimmer.CircularReferenceException",
    "org.babyfish.jimmer.ImmutableObjects",
    "org.babyfish.jimmer.UnloadedException",
    "org.babyfish.jimmer.internal.GeneratedBy",
    "org.babyfish.jimmer.lang.OldChain",
    "org.babyfish.jimmer.kt.DslScope",
    "org.babyfish.jimmer.kt.ImmutableCreator",
    "org.jspecify.annotations.NonNull",
    "org.jspecify.annotations.Nullable",
    "org.babyfish.jimmer.client.Description",
    "org.babyfish.jimmer.jackson.ImmutableModuleRequiredException",
    "org.babyfish.jimmer.meta.ImmutablePropCategory",
    "org.babyfish.jimmer.meta.PropId",
    "org.babyfish.jimmer.meta.ImmutableType",
    "org.babyfish.jimmer.runtime.DraftContext",
    "org.babyfish.jimmer.runtime.DraftSpi",
    "org.babyfish.jimmer.runtime.ImmutableSpi",
    "org.babyfish.jimmer.runtime.Internal",
    "org.babyfish.jimmer.runtime.NonSharedList",
    "org.babyfish.jimmer.runtime.Visibility",
    "org.babyfish.jimmer.sql.collection.IdViewList",
    "org.babyfish.jimmer.sql.collection.ManyToManyViewList",
    "org.babyfish.jimmer.sql.collection.MutableIdViewList",
    "com.fasterxml.jackson.annotation.JsonIgnore",
    "com.fasterxml.jackson.annotation.JsonPropertyOrder",
    "java.math.BigDecimal",
    "java.math.BigInteger",
    "java.time.Instant",
    "java.time.LocalDate",
    "java.time.LocalDateTime",
    "java.time.LocalTime",
    "java.util.regex.Pattern",
    "jakarta.validation.ValidationException",
    "javax.validation.ValidationException",
    "org.babyfish.jimmer.impl.validation.Validator",
    "org.babyfish.jimmer.sql.OneToOne",
    "org.babyfish.jimmer.sql.ManyToOne",
    "org.babyfish.jimmer.sql.OneToMany",
    "org.babyfish.jimmer.sql.ManyToMany",
)
    .map(LsiSymbolId::type)

private val DRAFT_RUNTIME_TYPE_NAMES = DRAFT_RUNTIME_TYPE_IDS.map(
    LsiSymbolId::topLevelPoetTypeName
) + generatedNestedPoetTypeName(
    "jakarta.validation.constraints",
    listOf("Pattern", "Flag"),
)

internal fun draftCode(block: LsiCodeBuilder.() -> Unit): LsiCodeBlock {
    return LsiCodeBlock.build(block)
}
