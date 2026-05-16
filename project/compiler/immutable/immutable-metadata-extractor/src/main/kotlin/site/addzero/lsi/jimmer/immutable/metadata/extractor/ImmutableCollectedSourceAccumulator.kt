package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCollectedSourceMetadata
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.meta.ImmutableType

class ImmutableCollectedSourceAccumulator {

    private val sourceNames = linkedMapOf<String, LinkedHashSet<String>>()
    private val sourceMetadataExtractor = ImmutableSourceMetadataExtractor()

    fun collect(roundSources: Iterable<ImmutableCollectedSourceMetadata>) {
        for ((sourceKey, typeQualifiedNames) in roundSources) {
            val bucket = sourceNames.getOrPut(sourceKey) { linkedSetOf() }
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onRound 收集类型名
            // 迁移说明：processor 本地 `sourceKey -> qualifiedName set` 累积状态下沉到 immutable metadata-extractor，入口仅调用 collect/isEmpty/clear
            bucket += typeQualifiedNames
        }
    }

    fun isEmpty(): Boolean =
        sourceNames.isEmpty()

    fun resolveMetadata(
        findClassByQualifiedName: (String) -> LsiClass?,
    ): ImmutableCollectedSourceMetadataResolution {
        val metadataSources = mutableListOf<ImmutableResolvedSourceMetadata>()
        val lsiClasses = mutableListOf<LsiClass>()
        for ((sourceKey, typeQualifiedNames) in sourceNames) {
            val sourceLsiClasses = mutableListOf<LsiClass>()
            for (qualifiedName in typeQualifiedNames) {
                val lsiClass = findClassByQualifiedName(qualifiedName)
                    ?: continue
                // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onFinish 重建待生成类型
                // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onFinish EntityMetaConsumer 输入
                // 迁移说明：finish-stage 对同一批 collected-source metadata 的 `LsiClass` 回放统一收口到 shared metadata resolve，
                // 供 KSP/APT 后续各自补 platform-specific `ImmutableType` 投影
                lsiClasses += lsiClass
                sourceLsiClasses += lsiClass
            }
            val sourceMetadata = sourceMetadataExtractor.extract(
                // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes 的 source/file/sql/fetcher/entity 聚合
                // 覆盖来源：project/jimmer-apt/.../immutable/ImmutableProcessor.generateJimmerTypes 的 source/file/sql/fetcher/entity 聚合
                // 迁移说明：source-level 纯 metadata 解析提前到 collected-source resolve 阶段，APT/KSP 后续都不再自行回调 extractor 重建 source metadata
                sourceKey = sourceKey,
                lsiClasses = sourceLsiClasses,
            )
            metadataSources += ImmutableResolvedSourceMetadata(
                metadata = sourceMetadata,
                lsiClasses = sourceLsiClasses,
            )
        }
        return ImmutableCollectedSourceMetadataResolution(
            sources = metadataSources,
            lsiClasses = lsiClasses,
        )
    }

    fun resolve(
        findClassByQualifiedName: (String) -> LsiClass?,
        toImmutableType: (LsiClass) -> ImmutableType,
    ): ImmutableCollectedSourceResolution {
        val metadataResolution = resolveMetadata(findClassByQualifiedName)
        val sources = metadataResolution.sources.map { metadataSource ->
            val sourceImmutableTypes = metadataSource.lsiClasses.map(toImmutableType)
            val propsTypeMetadata = metadataSource.metadata.sqlTypeQualifiedName
                ?.let { qualifiedName -> sourceImmutableTypes.firstOrNull { it.qualifiedName == qualifiedName } }
                ?.toPropsTypeMetadata()
            val fetcherTypeMetadata = metadataSource.metadata.fetcherTypeQualifiedName
                ?.let { qualifiedName -> sourceImmutableTypes.firstOrNull { it.qualifiedName == qualifiedName } }
                ?.toFetcherTypeMetadata()
            ImmutableResolvedSource(
                metadata = metadataSource.metadata,
                immutableTypes = sourceImmutableTypes,
                // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes 的 PropsGenerator 目标类型选择
                // 迁移说明：KSP `ImmutableType` 投影下的 Props 目标类型解析继续前移到 resolve 阶段，但现在建立在共享 metadata resolve 之上
                propsTypeMetadata = propsTypeMetadata,
                // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes 的 FetcherGenerator 目标类型选择
                // 迁移说明：Fetcher 目标类型解析同样改为“共享 metadata resolve + KSP-specific ImmutableType 投影”两段式，避免 shared resolve 泄漏具体 immutable meta 类型
                fetcherTypeMetadata = fetcherTypeMetadata,
            )
        }
        return ImmutableCollectedSourceResolution(
            sources = sources,
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.notifyEntityMetaConsumers 的实体回放输入
            // 迁移说明：per-source resolved aggregate 已不再保留 `LsiClass`，仅在 resolution 顶层保留扁平 `lsiClasses` 供 EntityMetaConsumerSpi 回放
            lsiClasses = metadataResolution.lsiClasses,
        )
    }

    fun clear() {
        sourceNames.clear()
    }
}
