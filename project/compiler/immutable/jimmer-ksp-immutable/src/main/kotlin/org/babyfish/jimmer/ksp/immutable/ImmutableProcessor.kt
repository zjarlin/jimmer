package org.babyfish.jimmer.ksp.immutable

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceAccumulator
import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableProcessorSupport
import site.addzero.context.Settings
import site.addzero.context.matchesConfiguredSourceFilters
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class ImmutableProcessor : ProcessorSpi<Context, Unit> {
    override var ctx = Context
    private val collectedSources = ImmutableCollectedSourceAccumulator()

    override fun onRound() {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的扫描与校验部分，迁移到 onRound() 多轮收集阶段
        ImmutableProcessorSupport.collectRoundSources(
            accumulator = collectedSources,
            resolver = ctx.lsiResolver,
            include = java.util.function.Predicate { it.matchesConfiguredSourceFilters() },
        )
    }

    override fun onFinish() {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的代码生成与 SPI 通知，迁移到 onFinish() 收尾阶段统一执行
        if (collectedSources.isEmpty()) {
            return
        }
        val resolvedSources = ImmutableProcessorSupport.resolveCollectedSources(
            accumulator = collectedSources,
            resolver = ctx.lsiResolver,
            toImmutableType = java.util.function.Function(Context::typeOf),
        )
        if (!ImmutableProcessorSupport.hasImmutableTypes(resolvedSources)) {
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onFinish 的 `immutableTypeMultiMap.isEmpty()` 早退
            // 迁移说明：旧的并行 `Map<String, List<ImmutableType>>` 判空逻辑改为基于 resolved-source 聚合结构判断，保持“无可生成 immutable 类型时直接结束”的行为
            clearCollected()
            return
        }
        ctx.resolve()
        val generatedOutput = ImmutableProcessorSupport.generateKspOutput(
            resolvedSources = resolvedSources,
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes
            // 迁移说明：processor finish-stage 只消费一次解析后的 collected-source resolution，再统一落盘 artifact
            excludedUserTypePrefixes = Settings.jimmerExcludedUserAnnotationPrefixes,
            jacksonTypes = ctx.jacksonTypes,
            existingEntitiesResourceFile = ctx.guessGeneratedJimmerResourceFile("entities"),
            isResourceGenerationIgnored = Settings.jimmerBuddyIgnoreResourceGeneration,
            isModuleRequired = Settings.jimmerImmutableIsModuleRequired,
        )
        generatedOutput.sourceFileSpecs.forEach(::writeFileSpec)
        generatedOutput.resourceArtifacts.forEach(::writeResourceArtifact)
        ImmutableProcessorSupport.notifyEntityMetaConsumers(
            entities = resolvedSources.lsiClasses,
            infoLogger = java.util.function.Consumer { message ->
                ctx.logInfo(message)
            },
        )
        clearCollected()
    }

    private fun writeFileSpec(fileSpec: LsiFileSpec) {
        ctx.lsiFiler.createSourceFile(fileSpec)
    }

    private fun writeResourceArtifact(artifact: GeneratedResourceArtifact) {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../JimmerModuleGenerator.generate entities 资源输出
        // 迁移说明：immutable 顶层资源写入统一收口到 processor，generator 只返回纯 resource artifact
        ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)
    }

    private fun clearCollected() {
        collectedSources.clear()
    }
}
