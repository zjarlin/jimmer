package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.packageName
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.jimmer.isJimmerType
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCollectedSourceMetadata

fun Sequence<LsiClass>.toCollectedImmutableSourceMetadata(
    include: (LsiClass) -> Boolean,
): List<ImmutableCollectedSourceMetadata> {
    val modelMap = linkedMapOf<String, MutableList<String>>()
    for (lsiClass in this) {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap 的 `classDeclaration.include(...)`
        // 迁移说明：immutable round scan 前置过滤与 source-key 分组逻辑下沉到 extractor，processor 只保留 resolver 调度与纯 metadata 累积
        if (!include(lsiClass)) {
            continue
        }
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap
        // 迁移说明：扫描入口后的 Jimmer 类型判定也下沉到 extractor，统一围绕 LsiClass 做候选筛选
        if (lsiClass.qualifiedName == null || !lsiClass.isJimmerType) {
            continue
        }
        if (!lsiClass.isInterface) {
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap 的 `classDeclaration.classKind != INTERFACE`
            // 迁移说明：接口校验继续保持 LSI 锚点，但不再由 processor 内联实现
            throw MetaException(lsiClass, "it must be interface")
        }
        if (lsiClass.typeParameterCount > 0) {
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap 的 `classDeclaration.typeParameters.isNotEmpty()`
            // 迁移说明：泛型参数校验下沉到 extractor，processor 不再直接触碰这类 declaration-level 规则
            throw MetaException(lsiClass, "it cannot have type parameters")
        }
        if (lsiClass.isPrivate || lsiClass.isProtected) {
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap 的 `classDeclaration.isPrivate() || classDeclaration.isProtected()`
            // 迁移说明：可见性校验下沉到 extractor，processor 仅消费已通过校验的分组结果
            throw MetaException(lsiClass, "it cannot be private or protected")
        }
        modelMap.computeIfAbsent(lsiClass.sourceKey()) { mutableListOf() } += lsiClass.qualifiedName!!
    }
    return modelMap.map { (sourceKey, typeQualifiedNames) ->
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onRound 收集类型名
        // 迁移说明：round-stage 收集结果收口为纯 `sourceKey + qualifiedNames` metadata，process 阶段不再持有 `ImmutableType`
        ImmutableCollectedSourceMetadata(
            sourceKey = sourceKey,
            typeQualifiedNames = typeQualifiedNames,
        )
    }
}

private fun LsiClass.sourceKey(): String {
    val packageName = packageName.orEmpty()
    val fileName = fileName ?: simpleName ?: qualifiedName
        ?: error("Cannot resolve source file name for immutable type")
    return if (packageName.isEmpty()) {
        fileName
    } else {
        "$packageName/$fileName"
    }
}
