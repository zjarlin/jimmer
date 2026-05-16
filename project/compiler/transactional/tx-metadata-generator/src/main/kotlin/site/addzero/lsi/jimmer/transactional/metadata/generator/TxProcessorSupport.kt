package site.addzero.lsi.jimmer.transactional.metadata.generator

import site.addzero.lsi.jimmer.transactional.metadata.extractor.TxMetadataExtraction
import site.addzero.lsi.jimmer.transactional.metadata.extractor.TxMetadataExtractor
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.resolver.LsiResolver

/**
 * transactional paired processor 的共享 orchestration 入口。
 *
 * 迁移说明：
 * - APT/KSP processor 统一通过 shared support 收集与生成
 * - processor 壳层只保留轮次差异与 `LsiFiler` 落盘
 */
object TxProcessorSupport {

    private val extractor = TxMetadataExtractor()
    private val generator = TxMetadataGenerator()

    @JvmStatic
    fun collectNewTypes(
        resolver: LsiResolver,
    ): TxMetadataExtraction =
        extractor.collectNewTypes(resolver)

    @JvmStatic
    fun generateFileSpecs(
        types: Collection<TxTypeMetadata>,
    ): List<LsiFileSpec> =
        types.map(generator::generate)
}
