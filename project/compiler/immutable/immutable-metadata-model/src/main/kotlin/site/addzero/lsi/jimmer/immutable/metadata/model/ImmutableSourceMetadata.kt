package site.addzero.lsi.jimmer.immutable.metadata.model

/**
 * immutable 源文件级元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiType`
 * - 不暴露 `ImmutableType` / `ImmutableProp`
 * - 不暴露 `TypeElement` / `KS*`
 * - 不暴露 `Context` / `LsiFiler`
 */
data class ImmutableSourceMetadata(
    val sourceKey: String,
    val sourcePackageName: String,
    val sourceFileName: String,
    val typeQualifiedNames: List<String>,
    val sqlTypeQualifiedName: String?,
    val fetcherTypeQualifiedName: String?,
    val entityQualifiedNames: List<String>,
)
