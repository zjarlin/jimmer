package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.generatedTopLevelPoetTypeName

/** 返回不可变 schema 中所有生成查询声明的精确源码类型名。 */
internal fun ImmutableSchema.toLsiGeneratedQueryPoetTypeNames(): List<LsiPoetTypeName> {
    return types.flatMap { type ->
        listOf(
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}Props"),
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}Table"),
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}TableEx"),
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}FetcherDsl"),
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}Draft"),
            generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}PropExpression"),
            generatedNestedPoetTypeName(
                type.packageName,
                listOf("${type.simpleName}Table", "Remote"),
            ),
        )
    }.distinctBy(LsiPoetTypeName::typeId)
}
