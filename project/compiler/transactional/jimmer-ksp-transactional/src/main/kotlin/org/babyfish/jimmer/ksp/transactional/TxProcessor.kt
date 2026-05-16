package org.babyfish.jimmer.ksp.transactional

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.context.Settings
import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class TxProcessor : ProcessorSpi<Context, Unit> {
    override var ctx = Context
    private val collectedTypes = linkedMapOf<String, TxTypeMetadata>()

    override fun onRound() {
        if (Settings.jimmerBuddyIgnoreResourceGeneration) {
            return
        }
        // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor.process
        // 原 process() 单阶段“扫描+校验+生成”路径中扫描校验部分，迁移到 onRound() 收集阶段
        // 原 resolver.getNewFiles() + file.declarations 增量扫描路径，替换为 metadata extractor + lsiResolver.newClasses()
        val extraction = TxProcessorSupport.collectNewTypes(ctx.lsiResolver)
        for (metadata in extraction.types) {
            // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor.onFinish 生成阶段声明缓存
            // 迁移说明：收尾阶段缓存对象从 `LsiClass` 下沉为纯 `TxTypeMetadata`，processor 只保留 orchestration
            collectedTypes.putIfAbsent(metadata.id, metadata)
        }
    }

    override fun onFinish() {
        if (Settings.jimmerBuddyIgnoreResourceGeneration) {
            collectedTypes.clear()
            return
        }
        // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor.process
        // 原 process() 单阶段“扫描+校验+生成”路径中的生成部分，迁移到 onFinish() 收尾阶段统一生成
        if (collectedTypes.isEmpty()) {
            return
        }
        for (fileSpec in TxProcessorSupport.generateFileSpecs(collectedTypes.values)) {
            writeFileSpec(fileSpec)
        }
        collectedTypes.clear()
    }

    private fun writeFileSpec(fileSpec: LsiFileSpec) {
        // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxGenerator.generate 输出文件创建
        // 迁移说明：processor 主链路只向 `LsiFiler` 交付 `LsiFileSpec`，KotlinPoet 渲染收敛到 adapter 内部
        ctx.lsiFiler.createSourceFile(fileSpec)
    }
}
