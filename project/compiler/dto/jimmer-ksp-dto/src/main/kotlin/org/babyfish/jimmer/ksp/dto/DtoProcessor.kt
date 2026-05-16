package org.babyfish.jimmer.ksp.dto

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.context.matchesConfiguredSourceFilters
import site.addzero.context.Settings
import site.addzero.lsi.jimmer.dto.LsiDtoModifier
import site.addzero.lsi.jimmer.dto.DtoProcessorSupport
import site.addzero.lsi.jimmer.dto.LsiDtoFile
import site.addzero.lsi.jimmer.processor.spi.IMMUTABLE_PROCESSOR
import site.addzero.lsi.processor.ProcessorSpi
import java.util.function.Function

@AutoService(ProcessorSpi::class)
class DtoProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    // 覆盖来源：`project/jimmer-ksp/.../JimmerProcessor` 中 dto 依赖 immutable 的调度规则
    override val dependsOn: Set<String> get() = setOf(IMMUTABLE_PROCESSOR)
    private val collectedDtoFiles = linkedSetOf<LsiDtoFile>()

    private val mutable: Boolean get() = Settings.jimmerDtoMutable
    private val dtoDirs: Collection<String>
        get() {
            // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.dtoDirs 的 `ctx.resolver.getAllFiles().first().filePath`
            // 迁移说明：DTO 目录判定只经由 Context 胶水层读取首文件路径，当前模块不再直接访问 KSP 文件 API
            val firstFilePath = ctx.firstSourceFilePath
            return if (firstFilePath != null && isTest(firstFilePath)) {
                Settings.jimmerDtoTestDirs
            } else {
                Settings.jimmerDtoDirs
            }
        }
    private val defaultNullableInputModifier: LsiDtoModifier
        get() = LsiDtoModifier.fromNullableInputOption(Settings.jimmerDtoDefaultNullableInputModifier)

    override fun onRound() {
        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的 DTO 文件扫描收集，迁移到 onRound()
        collectDtoFiles()
    }

    override fun onFinish(): Boolean {
        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.process
        // 原 process() 单阶段“扫描+生成”路径中的编译生成，迁移到 onFinish() 收尾阶段
        if (collectedDtoFiles.isEmpty()) {
            return false
        }
        val fileSpecs = DtoProcessorSupport.generateFileSpecs(
            dtoFiles = collectedDtoFiles,
            defaultNullableInputModifier = defaultNullableInputModifier,
            resolver = Context.lsiResolver,
            includeDtoSourceType = { it.matchesConfiguredSourceFilters() },
            toImmutableType = Function(Context::typeOf),
            resolveTypes = Runnable(Context::resolve),
            draftImplDocMapOf = { type, annotationQualifiedName, valueAttributeName ->
                Context.findDraftImplDocMap(type, annotationQualifiedName, valueAttributeName)
            },
            fallbackMutable = mutable,
        )
        fileSpecs.forEach { fileSpec ->
            Context.lsiFiler.createSourceFile(fileSpec)
        }
        collectedDtoFiles.clear()
        return fileSpecs.isNotEmpty()
    }

    private fun collectDtoFiles() {
        collectedDtoFiles += DtoProcessorSupport.collectDtoFiles(
            // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.collectDtoFiles 的 `KspLsiFile(Context.resolver, it)`
            // 迁移说明：DTO 文件扫描入口统一经由 shared `DtoProcessorSupport` 收口，KSP 壳层只负责提供项目锚点路径
            Context.sourceAnchorFilePath,
            dtoDirs,
        )
    }

    companion object {
        private fun isTest(path: String): Boolean {
            val testIndex = path.indexOf("/src/test/")
            if (testIndex == -1) return false
            val mainIndex = path.indexOf("/src/main/")
            return mainIndex == -1 || testIndex < mainIndex
        }
    }
}
