package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.packageName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableSourceMetadata
import site.addzero.lsi.jimmer.isJimmerEmbeddable
import site.addzero.lsi.jimmer.isJimmerEntity
import site.addzero.lsi.jimmer.isJimmerMappedSuperclass

/**
 * immutable 源文件级 metadata 提取器。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes`
 *
 * 迁移说明：
 * - 将 finish-stage orchestration 所需的源文件级聚合信息前移到 extractor
 * - 先抽离 source/file/sql/fetcher/entity 这层纯元数据，后续再继续向 type/prop 级 metadata 扩展
 */
class ImmutableSourceMetadataExtractor {

    fun extract(
        sourceKey: String,
        lsiClasses: List<LsiClass>,
    ): ImmutableSourceMetadata {
        val sqlType = lsiClasses.firstOrNull {
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes SQL 类型筛选
            // 迁移说明：SQL 类型筛选规则继续留在 immutable extractor，但已从 `ImmutableType` 下沉为纯 LSI Jimmer 类语义
            it.isJimmerEntity || it.isJimmerMappedSuperclass || it.isJimmerEmbeddable
        }
        return ImmutableSourceMetadata(
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes 的 sourceKey/sourcePackageName/sourceFileName/entity/sql/fetcher 聚合
            // 迁移说明：immutable 源文件级 orchestration 所需的命名和筛选结果统一收口为纯 metadata，并且这一层已不再依赖 `ImmutableType`
            sourceKey = sourceKey,
            sourcePackageName = lsiClasses.firstOrNull()?.packageName.orEmpty(),
            sourceFileName = lsiClasses.firstOrNull()?.fileName
                ?: lsiClasses.firstOrNull()?.simpleName
                ?: error("Cannot resolve source file name for immutable metadata extraction"),
            typeQualifiedNames = lsiClasses.mapNotNull { it.qualifiedName },
            sqlTypeQualifiedName = sqlType?.qualifiedName,
            fetcherTypeQualifiedName = sqlType
                ?.takeIf { it.isJimmerEntity || it.isJimmerEmbeddable }
                ?.qualifiedName,
            entityQualifiedNames = lsiClasses
                .filter { it.isJimmerEntity }
                .mapNotNull { it.qualifiedName },
        )
    }
}
