package site.addzero.lsi.jimmer.client

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.API
import site.addzero.lsi.jimmer.API_IGNORE
import site.addzero.lsi.jimmer.AUTO_API_OPERATION_ANNOTATIONS
import site.addzero.lsi.jimmer.REST_CONTROLLER
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.resolver.LsiResolver

fun collectClientApiServiceTypeNames(
    resolver: LsiResolver,
    delayedTypeNames: Collection<String>?,
    explicitClientApi: Boolean,
    matchesSourceFilters: (LsiClass) -> Boolean,
): Set<String> {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.onRound
    // 原逻辑：扫描 allClasses + delayedClientTypeNames，再在 handleService 处做 isApiService 判定
    // 迁移说明：前置为 LSI 规则筛选，保留 delayed 名称回补
    val typeNames = linkedSetOf<String>()
    for (lsiClass in resolver.allClasses()) {
        val qualifiedName = lsiClass.qualifiedName ?: continue
        if (isClientApiService(lsiClass, explicitClientApi, matchesSourceFilters)) {
            typeNames += qualifiedName
        }
    }
    for (delayedTypeName in delayedTypeNames.orEmpty()) {
        val delayedClass = resolver.findClassByQualifiedName(delayedTypeName)
        if (delayedClass == null || isClientApiService(delayedClass, explicitClientApi, matchesSourceFilters)) {
            typeNames += delayedTypeName
        }
    }
    return typeNames
}

fun isClientApiService(
    declaration: LsiClass,
    explicitClientApi: Boolean,
    matchesSourceFilters: (LsiClass) -> Boolean,
): Boolean {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.isApiService
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.isApiService
    // 迁移说明：服务判定规则下沉到 LSI 规则函数，便于 APT/KSP 共用
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.isApiService 的 `classDeclaration.include()`
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.isApiService 的 `context.include(typeElement)`
    // 迁移说明：服务源码过滤规则改为由调用方显式注入，避免 extractor 再绑定 KSP Settings 或 APT Context
    if (!matchesSourceFilters(declaration)) {
        return false
    }
    if (declaration.annotations.any { it.qualifiedName == API_IGNORE }) {
        return false
    }
    if (declaration.annotations.any { it.qualifiedName == API }) {
        return true
    }
    if (!explicitClientApi) {
        return false
    }
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientApiRules.REST_CONTROLLER_ANNOTATION
    // 迁移说明：显式 client api 模式下的 Spring controller 判定改为复用 lsi-jimmer 常量
    return declaration.annotations.any { it.qualifiedName == REST_CONTROLLER }
}

fun isClientApiOperation(
    declaration: LsiMethod,
    explicitClientApi: Boolean,
    isPublic: Boolean,
): Boolean {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.isApiOperation
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.isApiOperation
    // 迁移说明：操作判定规则下沉到 LSI 规则函数，KSP/后续 APT 可复用同一语义
    if (!isPublic) {
        return false
    }
    if (declaration.annotations.any { it.qualifiedName == API_IGNORE }) {
        return false
    }
    if (declaration.annotations.any { it.qualifiedName == API }) {
        return true
    }
    if (!explicitClientApi) {
        return false
    }
    // 覆盖来源：project/jimmer-core/.../client/meta/ApiOperation.AUTO_OPERATION_ANNOTATIONS
    // 迁移说明：自动操作注解名单改为复用 lsi-jimmer 常量，移除 compiler -> runtime meta 依赖
    return AUTO_API_OPERATION_ANNOTATIONS.any { annotationName ->
        declaration.annotations.any { it.qualifiedName == annotationName }
    }
}
