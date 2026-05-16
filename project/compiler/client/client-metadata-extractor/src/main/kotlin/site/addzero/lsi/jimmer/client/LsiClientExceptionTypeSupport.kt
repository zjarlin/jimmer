package site.addzero.lsi.jimmer.client

import org.babyfish.jimmer.client.meta.TypeName
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.jimmer.CLIENT_EXCEPTION
import site.addzero.lsi.method.LsiMethod

internal fun resolveClientExceptionTypeNames(
    method: LsiMethod,
    clientExceptionContext: ClientExceptionContext
): Set<TypeName> {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.getExceptionTypeNames
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.collectExceptionTypeNames
    // 迁移说明：异常类型名解析下沉到 client LSI helper，处理器入口只保留 traversal/input 装配。
    // 这里统一只消费 `LsiMethod.thrownTypes`，不再由 shared helper 重新解析 Kotlin `@Throws` 注解。
    val exceptionTypeNames = mutableSetOf<TypeName>()
    val declarations = method.thrownTypes
        .mapNotNull { it.lsiClass }
        .distinctBy { it.qualifiedName ?: it.simpleName }
    for (declaration in declarations) {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.getExceptionTypeNames @ClientException 判定
        // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.getExceptionTypeNames java throws 判定
        // 迁移说明：异常声明过滤统一只消费 LSI method.thrownTypes / annotation 语义，不在 helper 中引入任何 KSP/APT 回桥
        if (declaration.annotation(CLIENT_EXCEPTION) != null) {
            collectClientExceptionTypeNames(clientExceptionContext[declaration], exceptionTypeNames)
        }
    }
    return exceptionTypeNames
}

private fun collectClientExceptionTypeNames(
    metadata: ClientExceptionMetadata,
    exceptionTypeNames: MutableSet<TypeName>
) {
    if (metadata.code != null) {
        exceptionTypeNames += metadata.declaration.toClientTypeName()
    }
    for (subMetadata in metadata.subMetadatas) {
        collectClientExceptionTypeNames(subMetadata, exceptionTypeNames)
    }
}
