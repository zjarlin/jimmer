package site.addzero.lsi.jimmer.client

import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.ApiOperationImpl
import org.babyfish.jimmer.client.meta.impl.ApiParameterImpl
import org.babyfish.jimmer.client.meta.impl.ApiServiceImpl
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder
import org.babyfish.jimmer.client.meta.impl.SchemaImpl
import org.babyfish.jimmer.client.meta.impl.TypeRefImpl
import site.addzero.lsi.anno.get
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.API
import site.addzero.lsi.jimmer.API_IGNORE
import site.addzero.lsi.jimmer.toJimmerDoc
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.poet.isLsiNoValueLikeQualifiedName
import site.addzero.lsi.type.LsiType

internal interface LsiClientSchemaTraversalHooks {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleService 的 method 枚举
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.handleService 的 method 枚举
    // 迁移说明：为 APT/KSP 差异保留显式 method 选择钩子
    fun operationCandidates(serviceClass: LsiClass, methods: List<LsiMethod>): List<LsiMethod> = methods

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.isApiOperation
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.isApiOperation
    // 迁移说明：规则判断可被平台钩子覆写（默认沿用 LSI 共用判定）
    fun isOperationAccepted(serviceClass: LsiClass, method: LsiMethod, explicitClientApi: Boolean): Boolean =
        isClientApiOperation(method, explicitClientApi, method.isPublic)

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation 参数类型填充
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.handleMethod 参数类型填充
    // 迁移说明：参数类型异常可通过平台钩子执行 fallback；返回 true 表示已处理并继续
    fun onParameterTypeFailure(
        serviceClass: LsiClass,
        method: LsiMethod,
        parameter: LsiParameter,
        throwable: Throwable
    ): Boolean = false

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation 返回类型填充
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.handleMethod 返回类型填充
    // 迁移说明：返回类型异常可通过平台钩子执行 fallback；返回 true 表示已处理并继续
    fun onReturnTypeFailure(
        serviceClass: LsiClass,
        method: LsiMethod,
        throwable: Throwable
    ): Boolean = false
}

internal object DefaultLsiClientSchemaTraversalHooks : LsiClientSchemaTraversalHooks

internal data class LsiClientSchemaTraversalInput(
    val explicitClientApi: Boolean,
    val docMetadata: DocMetadata,
    val getExceptionTypeNames: (LsiMethod) -> Set<TypeName>,
    val fillType: SchemaBuilder<LsiClass>.(LsiType) -> Unit,
    val throwMeta: (LsiClass, String) -> Nothing,
    val hooks: LsiClientSchemaTraversalHooks = DefaultLsiClientSchemaTraversalHooks
)

internal fun SchemaBuilder<LsiClass>.handleClientApiService(
    lsiClass: LsiClass,
    input: LsiClientSchemaTraversalInput
) {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleService
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.handleService
    // 迁移说明：服务/方法遍历流程下沉为 LSI 管线，复用到 APT/KSP 时只注入差异回调
    if (!lsiClass.isTopLevel) {
        input.throwMeta(lsiClass, "Client API service type cannot be inner type")
    }
    if (lsiClass.typeParameterCount > 0) {
        input.throwMeta(lsiClass, "Client API service cannot declare type parameters")
    }
    val schema = current<SchemaImpl<LsiClass>>()
    api(lsiClass, lsiClass.toClientTypeName()) { service: ApiServiceImpl<LsiClass> ->
        lsiClass.annotations
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleService @Api groups 读取
            // 迁移说明：Api 读取改为 LSI 注解 FQ 常量，移除对 `Api::class` 的编译期依赖
            .firstOrNull { it.qualifiedName == API }
            ?.get<List<String>>("value")
            ?.takeIf { it.isNotEmpty() }
            ?.let { service.groups = it }
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleService service.doc 赋值
        // 迁移说明：服务文档在 traversal 内部保持 LSI 文档读取，只有 runtime schema 落点才做 jimmer `Doc` 转换
        service.doc = input.docMetadata.getDoc(lsiClass)?.toJimmerDoc()
        val methods = input.hooks.operationCandidates(lsiClass, declaredClientMethods(lsiClass))
        for (method in methods) {
            if (input.hooks.isOperationAccepted(lsiClass, method, input.explicitClientApi)) {
                handleClientApiOperation(lsiClass, method, input)
            }
        }
        schema.addApiService(service)
    }
}

private fun SchemaBuilder<LsiClass>.handleClientApiOperation(
    serviceClass: LsiClass,
    method: LsiMethod,
    input: LsiClientSchemaTraversalInput
) {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.handleMethod
    // 迁移说明：方法参数/返回值遍历迁移到 LSI 管线，类型填充由回调注入
    val service = current<ApiServiceImpl<LsiClass>>()
    if (method.typeParameterCount > 0) {
        input.throwMeta(serviceClass, "Client API function cannot declare type parameters")
    }
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation @Api 读取
    // 迁移说明：方法级 Api 读取改为 LSI 注解 FQ 常量，移除对 `Api::class` 的编译期依赖
    val api = method.annotations.firstOrNull { it.qualifiedName == API }
    operation(serviceClass, method.name ?: "<anonymous>") { operation: ApiOperationImpl<LsiClass> ->
        api?.get<List<String>>("value")?.takeIf { it.isNotEmpty() }?.let { groups ->
            service.groups?.let { parentGroups ->
                val illegalGroups = parentGroups.toMutableSet().apply { removeAll(groups) }
                if (illegalGroups.isNotEmpty()) {
                    input.throwMeta(
                        operation.source ?: serviceClass,
                        "It cannot be decorated by \"@$API\" with `groups` \"$illegalGroups\" " +
                            "because they are not declared in declaring type \"${service.typeName}\""
                    )
                }
            }
            operation.groups = groups
        }
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation operation.doc 赋值
        // 迁移说明：方法文档在 operation runtime 装配边界显式执行 `LsiDoc -> Doc` 转换
        operation.doc = input.docMetadata.getDoc(method)?.toJimmerDoc()
        var index = 0
        for (parameter in method.parameters) {
            val parameterName = parameter.name ?: continue
            parameter(serviceClass, parameterName) { apiParameter: ApiParameterImpl<LsiClass> ->
                apiParameter.originalIndex = index++
                // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation parameter @ApiIgnore 判定
                // 迁移说明：参数级 ApiIgnore 判定改为 LSI 注解 FQ 常量，移除对 `ApiIgnore::class` 的编译期依赖
                if (parameter.annotations.any { it.qualifiedName == API_IGNORE }) {
                    operation.addIgnoredParameter(apiParameter)
                } else {
                    val parameterType = parameter.type ?: return@parameter
                    try {
                        typeRef { type: TypeRefImpl<LsiClass> ->
                            input.fillType(this@handleClientApiOperation, parameterType)
                            apiParameter.setType(type)
                        }
                        operation.addParameter(apiParameter)
                    } catch (ex: Throwable) {
                        if (!input.hooks.onParameterTypeFailure(serviceClass, method, parameter, ex)) {
                            throw ex
                        }
                        operation.addIgnoredParameter(apiParameter)
                    }
                }
            }
        }
        method.returnType?.let { returnType ->
            val qualifiedName = returnType.qualifiedName
            if (!qualifiedName.isLsiNoValueLikeQualifiedName()) {
                try {
                    typeRef { type: TypeRefImpl<LsiClass> ->
                        input.fillType(this@handleClientApiOperation, returnType)
                        operation.setReturnType(type)
                    }
                } catch (ex: Throwable) {
                    if (!input.hooks.onReturnTypeFailure(serviceClass, method, ex)) {
                        throw ex
                    }
                }
            }
        }
        operation.setExceptionTypeNames(input.getExceptionTypeNames(method))
        service.addOperation(operation)
    }
}
