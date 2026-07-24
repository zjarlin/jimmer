package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.collectTypeRefDependencies
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 为冻结的 DTO 类型引用解析完整且精确的源码名称表。 */
internal fun LsiWorkspace.dtoTypeRefPoetTypeNames(
    type: LsiTypeRef,
    generatedTypeNames: Collection<LsiPoetTypeName> = emptyList(),
): List<LsiPoetTypeName> {
    val typeIds = sortedSetOf<LsiSymbolId>().apply {
        collectTypeRefDependencies(type)
    }.filterTo(sortedSetOf(), LsiSymbolId::isTypeId)
    return toLsiPoetTypeNames(
        typeIds = typeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + generatedTypeNames,
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
