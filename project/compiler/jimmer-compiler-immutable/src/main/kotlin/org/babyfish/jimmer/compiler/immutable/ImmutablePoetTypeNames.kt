package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedDraftType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.generatedSiblingClass
import site.addzero.lsi.clazz.toLsiClasses

/** 返回不可变源码类型的精确 Poet 名称表。 */
internal fun LsiWorkspace.immutableSourcePoetTypeNames(type: ImmutableType): List<LsiClass> {
    return toLsiClasses(listOf(type.id))
}

/** 返回当前轮生成 Draft 类型的精确 Poet 名称表。 */
internal fun LsiWorkspace.immutableDraftPoetTypeNames(type: ImmutableType): List<LsiClass> {
    val draftType = type.generatedDraftType()
    return listOf(
        generatedSiblingClass(
            sourceTypeId = type.id,
            generatedTypeId = draftType.declarationId,
            simpleNameSuffix = "Draft",
        ),
    )
}

/** 只用于调用方已经确认是顶层声明的运行时类型。 */
internal fun LsiSymbolId.topLevelPoetTypeName(): LsiClass {
    val qualifiedName = requireTypeQualifiedName()
    val separator = qualifiedName.lastIndexOf('.')
    return if (separator < 0) {
        LsiClass(this, "", listOf(qualifiedName))
    } else {
        LsiClass(
            typeId = this,
            packageName = qualifiedName.substring(0, separator),
            simpleNames = listOf(qualifiedName.substring(separator + 1)),
        )
    }
}

internal fun generatedNestedPoetTypeName(
    packageName: String,
    simpleNames: List<String>,
): LsiClass {
    require(simpleNames.isNotEmpty()) { "Generated Poet nested type must have a declaration name" }
    val qualifiedName = (listOf(packageName).filter(String::isNotEmpty) + simpleNames)
        .joinToString(".")
    return LsiClass(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = simpleNames,
    )
}
