package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.generatedTableType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.collectTypeRefDependencies
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.generatedSiblingClass
import site.addzero.lsi.clazz.toLsiClasses

/** 为冻结的 DTO 类型引用解析完整且精确的源码名称表。 */
internal fun LsiWorkspace.dtoTypeRefPoetTypeNames(
    type: LsiType,
    generatedTypeNames: Collection<LsiClass> = emptyList(),
): List<LsiClass> {
    val typeIds = sortedSetOf<LsiSymbolId>().apply {
        collectTypeRefDependencies(type)
    }.filterTo(sortedSetOf(), LsiSymbolId::isTypeId)
    return toLsiClasses(
        typeIds = typeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + generatedTypeNames,
    )
}

/** 为 DTO 基础契约补齐当前轮尚未进入 workspace 的生成查询类型名。 */
internal fun LsiWorkspace.dtoBaseContractPoetTypeNames(
    contractType: LsiDeclaredType,
    baseType: ImmutableType,
): List<LsiClass> {
    val generatedTypeNames = if (baseType.kind == ImmutableTypeKind.ENTITY) {
        val tableType = baseType.generatedTableType()
        listOf(
            generatedSiblingClass(
                sourceTypeId = baseType.id,
                generatedTypeId = tableType.declarationId,
                simpleNameSuffix = "Table",
            ),
        )
    } else {
        emptyList()
    }
    return dtoTypeRefPoetTypeNames(
        contractType,
        DTO_BASE_CONTRACT_POET_TYPE_NAMES + generatedTypeNames,
    )
}

internal val DTO_COMMON_POET_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer", listOf("Input")),
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer.internal", listOf("GeneratedBy")),
    JimmerDtoPoetTypeNames.create("java.lang", listOf("Object")),
    JimmerDtoPoetTypeNames.create("java.lang", listOf("String")),
    JimmerDtoPoetTypeNames.create("java.lang", listOf("Iterable")),
    JimmerDtoPoetTypeNames.create("java.util", listOf("Collection")),
    JimmerDtoPoetTypeNames.create("java.util", listOf("List")),
    JimmerDtoPoetTypeNames.create("java.util", listOf("Set")),
    JimmerDtoPoetTypeNames.create("java.util", listOf("Map")),
    JimmerDtoPoetTypeNames.create("java.util", listOf("Objects")),
    JimmerDtoPoetTypeNames.create("kotlin", listOf("Any")),
    JimmerDtoPoetTypeNames.create("kotlin", listOf("String")),
    JimmerDtoPoetTypeNames.create("kotlin", listOf("Array")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("Iterable")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("MutableIterable")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("Collection")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("MutableCollection")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("List")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("MutableList")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("Set")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("MutableSet")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("Map")),
    JimmerDtoPoetTypeNames.create("kotlin.collections", listOf("MutableMap")),
)

private val DTO_BASE_CONTRACT_POET_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer", listOf("View")),
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer", listOf("EmbeddableDto")),
    JimmerDtoPoetTypeNames.create(
        "org.babyfish.jimmer.sql.ast.query.specification",
        listOf("JSpecification"),
    ),
    JimmerDtoPoetTypeNames.create(
        "org.babyfish.jimmer.sql.kt.ast.query.specification",
        listOf("KSpecification"),
    ),
)
