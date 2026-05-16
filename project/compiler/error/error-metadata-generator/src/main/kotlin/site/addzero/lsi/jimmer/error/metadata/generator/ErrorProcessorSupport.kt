package site.addzero.lsi.jimmer.error.metadata.generator

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.error.metadata.extractor.ErrorMetadataExtraction
import site.addzero.lsi.jimmer.error.metadata.extractor.ErrorMetadataExtractor
import site.addzero.lsi.jimmer.error.metadata.model.ErrorTypeMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.resolver.LsiResolver
import java.util.function.Predicate

/**
 * error paired processor 的共享 orchestration 入口。
 *
 * 迁移说明：
 * - APT/KSP processor 都通过这里进入同一条收集与生成路径
 * - processor 壳层只保留轮次控制、配置读取与文件落盘
 */
object ErrorProcessorSupport {

    private val extractor = ErrorMetadataExtractor()
    private val generator = ErrorMetadataGenerator()

    @JvmStatic
    fun collectNewTypes(
        resolver: LsiResolver,
        include: Predicate<LsiClass>,
    ): ErrorMetadataExtraction =
        extractor.collectNewTypes(resolver) { declaration ->
            include.test(declaration)
        }

    @JvmStatic
    fun generateFileSpecs(
        types: Collection<ErrorTypeMetadata>,
        checkedException: Boolean,
    ): List<LsiFileSpec> =
        types.map { metadata ->
            generator.generate(
                metadata = metadata,
                checkedException = checkedException,
            )
        }
}
