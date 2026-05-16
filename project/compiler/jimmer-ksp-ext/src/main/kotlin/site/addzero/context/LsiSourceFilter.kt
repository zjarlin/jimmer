package site.addzero.context

import site.addzero.lsi.clazz.LsiClass

/**
 * 覆盖来源：project/jimmer-apt/.../Context.include(TypeElement)
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../utils.KSClassDeclaration.include(includes, excludes)
 * 迁移说明：源码 include/exclude 过滤收敛到配置语义层，避免业务模块继续通过 `org.babyfish.jimmer.ksp` 旧包名获取该能力
 */
fun LsiClass.matchesSourceFilters(
    includes: List<String>,
    excludes: List<String>
): Boolean {
    val qualifiedName = this.qualifiedName ?: return false
    if (includes.isNotEmpty() && !includes.any { qualifiedName.startsWith(it) }) {
        return false
    }
    if (excludes.isNotEmpty() && excludes.any { qualifiedName.startsWith(it) }) {
        return false
    }
    return true
}

/**
 * 覆盖来源：project/compiler/dto|error|immutable|client/... 对 `classDeclaration.include()` / `lsiClass.include()` 的调用
 * 迁移说明：默认源码过滤直接复用 `Settings.jimmerSourceIncludes/excludes`，业务模块不再依赖 `jimmer-ksp-ext/utils.kt`
 */
fun LsiClass.matchesConfiguredSourceFilters(): Boolean =
    matchesSourceFilters(Settings.jimmerSourceIncludes, Settings.jimmerSourceExcludes)
