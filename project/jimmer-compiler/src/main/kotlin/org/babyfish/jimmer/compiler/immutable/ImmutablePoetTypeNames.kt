package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.poet.LsiPoetTypeName

/** 只用于调用方已经确认是顶层声明的运行时类型。 */
internal fun LsiSymbolId.topLevelPoetTypeName(): LsiPoetTypeName {
    val qualifiedName = requireTypeQualifiedName()
    val separator = qualifiedName.lastIndexOf('.')
    return if (separator < 0) {
        LsiPoetTypeName(this, "", listOf(qualifiedName))
    } else {
        LsiPoetTypeName(
            typeId = this,
            packageName = qualifiedName.substring(0, separator),
            simpleNames = listOf(qualifiedName.substring(separator + 1)),
        )
    }
}

internal fun generatedTopLevelPoetTypeName(
    packageName: String,
    simpleName: String,
): LsiPoetTypeName {
    val qualifiedName = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    return LsiPoetTypeName(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = listOf(simpleName),
    )
}

internal fun generatedNestedPoetTypeName(
    packageName: String,
    simpleNames: List<String>,
): LsiPoetTypeName {
    require(simpleNames.isNotEmpty()) { "Generated Poet nested type must have a declaration name" }
    val qualifiedName = (listOf(packageName).filter(String::isNotEmpty) + simpleNames)
        .joinToString(".")
    return LsiPoetTypeName(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = simpleNames,
    )
}
