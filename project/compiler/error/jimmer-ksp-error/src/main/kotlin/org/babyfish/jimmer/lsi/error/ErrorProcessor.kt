package org.babyfish.jimmer.ksp.error

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.context.Settings
import site.addzero.context.matchesConfiguredSourceFilters
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.jimmer.error.ErrorGenerator
import site.addzero.lsi.jimmer.ERROR_FAMILY
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class ErrorProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    private val collectedTypes = linkedMapOf<String, LsiClass>()

    override fun onRound() {
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的扫描部分，迁移到 onRound() 收集阶段
        for (errorType in findErrorTypes()) {
            val qualifiedName = errorType.qualifiedName
                ?: throw IllegalStateException("Error type must have qualified name")
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.generateErrorTypes 声明回查
            // 迁移说明：收尾阶段不再回查 KSClassDeclaration，直接缓存 LsiClass 并面向 LSI 生成
            collectedTypes.putIfAbsent(qualifiedName, errorType)
        }
    }

    override fun onFinish(): Boolean {
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的生成部分，迁移到 onFinish() 收尾阶段统一生成
        if (collectedTypes.isEmpty()) {
            return false
        }
        generateErrorTypes(collectedTypes.values)
        collectedTypes.clear()
        return true
    }

    private fun findErrorTypes(): List<LsiClass> =
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.findErrorTypes
        // 原 resolver.getNewFiles() + declarations.filterIsInstance<KSClassDeclaration>() 增量扫描路径
        // 替换为 lsiResolver.newClasses()；错误族识别改为 LSI 注解 FQ 查询，源码过滤改为配置层 helper，
        // 移除对 `ErrorFamily::class` 与 `org.babyfish.jimmer.ksp.include` 的编译期依赖
        ctx.lsiResolver
            .newClasses()
            .filter { it.isEnum && it.annotation(ERROR_FAMILY) != null && it.matchesConfiguredSourceFilters() }
            .toList()

    private fun generateErrorTypes(types: Collection<LsiClass>) {
        for (declaration in types) {
            ErrorGenerator(ctx, declaration, Settings.jimmerClientCheckedException).generate()
        }
    }
}
