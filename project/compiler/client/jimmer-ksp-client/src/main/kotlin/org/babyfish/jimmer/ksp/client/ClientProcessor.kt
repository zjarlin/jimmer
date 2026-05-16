package org.babyfish.jimmer.ksp.client

import com.google.auto.service.AutoService
import site.addzero.context.Context
import site.addzero.context.Context.delayedClientTypeNames
import site.addzero.context.Settings
import site.addzero.context.matchesConfiguredSourceFilters
import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport
import site.addzero.lsi.jimmer.processor.spi.IMMUTABLE_PROCESSOR
import site.addzero.lsi.jimmer.processor.spi.TYPED_TUPLE_PROCESSOR
import site.addzero.lsi.processor.ProcessorSpi

@AutoService(ProcessorSpi::class)
class ClientProcessor : ProcessorSpi<Context, Unit> {

    override val dependsOn: Set<String>
        get() = setOf(IMMUTABLE_PROCESSOR, TYPED_TUPLE_PROCESSOR)

    private val explicitClientApi get() = Context.explicitClientApi
    private val collectedServiceTypeNames = linkedSetOf<String>()

    override var ctx: Context
        get() = Context
        set(value) {}

    override fun onRound() {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.onRound
        // 迁移说明：服务候选收集继续收口到 shared support，processor 壳层只保留 round 生命周期与文件写入
        collectedServiceTypeNames += ClientProcessorSupport.collectClientSchemaServiceTypeNames(
            resolver = ctx.lsiResolver,
            delayedTypeNames = delayedClientTypeNames,
            explicitClientApi = explicitClientApi,
            matchesSourceFilters = { it.matchesConfiguredSourceFilters() }
        )
    }

    override fun onFinish() {
        if (Settings.jimmerBuddyIgnoreResourceGeneration) {
            collectedServiceTypeNames.clear()
            return
        }
        val artifact = ClientProcessorSupport.generateClientSchemaArtifact(
            resolver = ctx.lsiResolver,
            explicitClientApi = explicitClientApi,
            serviceTypeNames = collectedServiceTypeNames,
            existingSchemaFile = ctx.guessGeneratedJimmerResourceFile("client"),
            convertedLsiTypeNameOf = Context::convertedLsiTypeNameOf,
            draftImplDocMapOf = { type, annotationQualifiedName, valueAttributeName ->
                Context.findDraftImplDocMap(type, annotationQualifiedName, valueAttributeName)
            },
        )
        writeArtifact(artifact)
        delayedClientTypeNames = null
        collectedServiceTypeNames.clear()
    }

    private fun writeArtifact(
        artifact: GeneratedResourceArtifact,
    ) {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.onFinish resource 输出
        // 迁移说明：resource 写入职责回收到 processor，client metadata-generator 只返回纯 resource artifact
        ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)
    }
}
