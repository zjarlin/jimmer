package org.babyfish.jimmer.lsi.error

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.context.Settings
import site.addzero.context.matchesConfiguredSourceFilters
import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport
import site.addzero.lsi.jimmer.error.metadata.model.ErrorTypeMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class ErrorProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    private val collectedTypes = linkedMapOf<String, ErrorTypeMetadata>()

    override fun onRound() {
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的扫描部分，迁移到 onRound() 收集阶段
        val extraction = ErrorProcessorSupport.collectNewTypes(
            resolver = ctx.lsiResolver,
            include = { declaration -> declaration.matchesConfiguredSourceFilters() },
        )
        for (metadata in extraction.types) {
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.generateErrorTypes 声明缓存
            // 迁移说明：收尾阶段缓存对象从 `LsiClass` 下沉为纯 `ErrorTypeMetadata`，processor 只保留 orchestration
            collectedTypes.putIfAbsent(metadata.id, metadata)
        }
    }

    override fun onFinish(): Boolean {
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的生成部分，迁移到 onFinish() 收尾阶段统一生成
        if (collectedTypes.isEmpty()) {
            return false
        }
        for (fileSpec in ErrorProcessorSupport.generateFileSpecs(
            types = collectedTypes.values,
            checkedException = Settings.jimmerClientCheckedException,
        )) {
            writeFileSpec(fileSpec)
        }
        collectedTypes.clear()
        return true
    }

    private fun writeFileSpec(fileSpec: LsiFileSpec) {
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.generate 输出文件创建
        // 迁移说明：processor 主链路只向 `LsiFiler` 交付 `LsiFileSpec`，渲染留在 adapter 边界
        ctx.lsiFiler.createSourceFile(fileSpec)
    }
}
