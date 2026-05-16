package site.addzero.lsi.jimmer.tuple.metadata.generator

import site.addzero.lsi.jimmer.tuple.metadata.extractor.TypedTupleMetadataExtraction
import site.addzero.lsi.jimmer.tuple.metadata.extractor.TypedTupleMetadataExtractor
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.resolver.LsiResolver

/**
 * tuple paired processor 的共享 orchestration 入口。
 *
 * 迁移说明：
 * - delayed type replay 与 metadata 生成统一收口到 shared support
 * - APT/KSP processor 只保留生命周期调度与文件输出
 */
object TypedTupleProcessorSupport {

    private val extractor = TypedTupleMetadataExtractor()
    private val generator = TypedTupleMetadataGenerator()

    @JvmStatic
    fun collectRoundTypes(
        resolver: LsiResolver,
        delayedTypeNames: Collection<String>?,
    ): TypedTupleMetadataExtraction =
        extractor.collectRoundTypes(
            resolver = resolver,
            delayedTypeNames = delayedTypeNames,
        )

    @JvmStatic
    fun generateFileSpecs(
        types: Collection<TypedTupleMetadata>,
    ): List<LsiFileSpec> =
        types.map(generator::generate)
}
