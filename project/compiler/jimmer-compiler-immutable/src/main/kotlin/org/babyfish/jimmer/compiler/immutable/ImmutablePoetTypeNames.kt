package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedDraftType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.generatedSiblingTypeName
import site.addzero.lsi.model.toLsiTypeNames

/** 返回不可变源码类型的精确 Poet 名称表。 */
internal fun LsiWorkspace.immutableSourcePoetTypeNames(type: ImmutableType): List<LsiTypeName> {
    return toLsiTypeNames(listOf(type.id))
}

/** 返回当前轮生成 Draft 类型的精确 Poet 名称表。 */
internal fun LsiWorkspace.immutableDraftPoetTypeNames(type: ImmutableType): List<LsiTypeName> {
    val draftType = type.generatedDraftType()
    return listOf(
        generatedSiblingTypeName(
            sourceTypeId = type.id,
            generatedTypeId = draftType.declarationId,
            simpleNameSuffix = "Draft",
        ),
    )
}

/** 只用于调用方已经确认是顶层声明的运行时类型。 */
internal fun LsiSymbolId.topLevelPoetTypeName(): LsiTypeName {
    val qualifiedName = requireTypeQualifiedName()
    val separator = qualifiedName.lastIndexOf('.')
    return if (separator < 0) {
        LsiTypeName(this, "", listOf(qualifiedName))
    } else {
        LsiTypeName(
            typeId = this,
            packageName = qualifiedName.substring(0, separator),
            simpleNames = listOf(qualifiedName.substring(separator + 1)),
        )
    }
}

internal fun generatedNestedPoetTypeName(
    packageName: String,
    simpleNames: List<String>,
): LsiTypeName {
    require(simpleNames.isNotEmpty()) { "Generated Poet nested type must have a declaration name" }
    val qualifiedName = (listOf(packageName).filter(String::isNotEmpty) + simpleNames)
        .joinToString(".")
    return LsiTypeName(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = simpleNames,
    )
}
