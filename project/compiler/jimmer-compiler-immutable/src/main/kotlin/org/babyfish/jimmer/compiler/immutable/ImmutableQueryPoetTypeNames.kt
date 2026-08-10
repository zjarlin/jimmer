package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.generatedTopLevelTypeName

/** 返回不可变 schema 中所有生成查询声明的精确源码类型名。 */
fun ImmutableSchema.toLsiGeneratedQueryPoetTypeNames(): List<LsiTypeName> {
    return types.flatMap { type ->
        listOf(
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}Props"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}Table"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}TableEx"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}FetcherDsl"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}Draft"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}PropExpression"),
            generatedNestedPoetTypeName(
                type.packageName,
                listOf("${type.simpleName}Table", "Remote"),
            ),
        )
    }.distinctBy(LsiTypeName::typeId)
}
