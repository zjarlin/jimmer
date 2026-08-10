package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.generatedTopLevelClass

/** 返回不可变 schema 中所有生成查询声明的精确源码类型名。 */
fun ImmutableSchema.toLsiGeneratedQueryPoetTypeNames(): List<LsiClass> {
    return types.flatMap { type ->
        listOf(
            generatedTopLevelClass(type.packageName, "${type.simpleName}Props"),
            generatedTopLevelClass(type.packageName, "${type.simpleName}Table"),
            generatedTopLevelClass(type.packageName, "${type.simpleName}TableEx"),
            generatedTopLevelClass(type.packageName, "${type.simpleName}FetcherDsl"),
            generatedTopLevelClass(type.packageName, "${type.simpleName}Draft"),
            generatedTopLevelClass(type.packageName, "${type.simpleName}PropExpression"),
            generatedNestedPoetTypeName(
                type.packageName,
                listOf("${type.simpleName}Table", "Remote"),
            ),
        )
    }.distinctBy(LsiClass::id)
}
