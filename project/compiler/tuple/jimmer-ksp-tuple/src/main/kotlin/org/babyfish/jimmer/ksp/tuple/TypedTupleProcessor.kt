package org.babyfish.jimmer.ksp.tuple

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.lsi.jimmer.processor.spi.DTO_PROCESSOR
import site.addzero.lsi.jimmer.processor.spi.IMMUTABLE_PROCESSOR
import site.addzero.lsi.jimmer.tuple.metadata.extractor.TypedTupleMetadataExtraction
import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class TypedTupleProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    // 覆盖来源：用户给定规则：tuple dependsOn [dto、immutable]
    override val dependsOn: Set<String> get() = setOf(DTO_PROCESSOR, IMMUTABLE_PROCESSOR)
    private val collectedTypes = linkedMapOf<String, TypedTupleMetadata>()

    override fun onRound() {
        // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.process
        // 原 process() 单阶段“扫描+校验+生成”路径中的扫描部分迁移到 onRound()
        collect(
            TypedTupleProcessorSupport.collectRoundTypes(
                resolver = ctx.lsiResolver,
                delayedTypeNames = ctx.delayedTupleTypeNames,
            )
        )
    }

    override fun onFinish(): Boolean {
        // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.process
        // 原 process() 单阶段“扫描+校验+生成”路径中的生成部分迁移到 onFinish() 收尾阶段
        if (collectedTypes.isEmpty()) {
            return false
        }
        for (fileSpec in TypedTupleProcessorSupport.generateFileSpecs(collectedTypes.values)) {
            // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.onFinish 生成入口
            // 迁移说明：onFinish 直接消费 onRound 收集的 `TypedTupleMetadata`，处理器入口只保留 orchestration
            writeFileSpec(fileSpec)
        }
        collectedTypes.clear()
        return true
    }

    private fun collect(
        extraction: TypedTupleMetadataExtraction,
    ) {
        for (metadata in extraction.types) {
            // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.onFinish 生成阶段声明缓存
            // 迁移说明：收尾阶段缓存对象从 `LsiClass` 下沉为纯 `TypedTupleMetadata`，processor 只保留调度与写文件职责
            collectedTypes.putIfAbsent(metadata.id, metadata)
        }
    }

    private fun writeFileSpec(
        fileSpec: LsiFileSpec,
    ) {
        // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleGenerator.generate 输出文件创建
        // 迁移说明：processor 主链路只向 `LsiFiler` 交付 `LsiFileSpec`，KotlinPoet 渲染收敛到 adapter 内部
        ctx.lsiFiler.createSourceFile(fileSpec)
    }
}
