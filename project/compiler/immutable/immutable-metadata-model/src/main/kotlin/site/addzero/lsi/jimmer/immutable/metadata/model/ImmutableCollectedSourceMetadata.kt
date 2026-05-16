package site.addzero.lsi.jimmer.immutable.metadata.model

/**
 * immutable 多轮收集阶段的源文件级候选元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiType`
 * - 不暴露 `ImmutableType`
 * - 不暴露 `TypeElement` / `KS*`
 * - 不暴露 `Context`
 */
data class ImmutableCollectedSourceMetadata(
    val sourceKey: String,
    val typeQualifiedNames: List<String>,
)
