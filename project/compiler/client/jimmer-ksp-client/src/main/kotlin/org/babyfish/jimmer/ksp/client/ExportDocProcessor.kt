package org.babyfish.jimmer.ksp.client

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport
import site.addzero.lsi.jimmer.processor.spi.IMMUTABLE_PROCESSOR
import site.addzero.lsi.jimmer.processor.spi.TYPED_TUPLE_PROCESSOR
import site.addzero.lsi.processor.ProcessorSpi


@AutoService(ProcessorSpi::class)
class ExportDocProcessor : ProcessorSpi<Context, Unit> {
    override var ctx = Context
    override val dependsOn: Set<String> get() = setOf(IMMUTABLE_PROCESSOR, TYPED_TUPLE_PROCESSOR)
    private val collectedTypeNames = linkedSetOf<String>()

    override fun onRound() {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的扫描部分，迁移到 onRound() 收集阶段
        // 迁移说明：导出文档扫描进一步收口到 shared support，processor 入口只保留 round 收集职责
        collectedTypeNames += ClientProcessorSupport.collectExportDocTypeNames(ctx.lsiResolver)
    }

    override fun onFinish() {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的生成部分，迁移到 onFinish() 收尾阶段统一生成
        if (collectedTypeNames.isEmpty()) {
            return
        }
        ClientProcessorSupport.generateExportDocArtifact(
            resolver = ctx.lsiResolver,
            typeNames = collectedTypeNames,
        )?.let(::writeArtifact)
        collectedTypeNames.clear()
    }

    private fun writeArtifact(artifact: GeneratedResourceArtifact) {
        ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)
    }
}
