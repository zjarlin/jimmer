package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableSourceMetadata

/**
 * immutable collected-source 的纯 metadata resolve 结果。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.onFinish`
 * - `project/jimmer-apt/.../immutable/ImmutableProcessor.parseImmutableTypes`
 *
 * 迁移说明：
 * - 原先 `ImmutableCollectedSourceAccumulator.resolve(...)` 直接泄漏 `site.addzero.lsi.jimmer.meta.ImmutableType`
 * - 这里先补一层只包含 `ImmutableSourceMetadata + LsiClass` 的中性 resolve 结果，让 APT/KSP 都能复用同一份 source 聚合解析
 * - 平台侧需要的 `ImmutableType` 回放继续留在各自入口或后续 projector，不把旧 meta 大对象抬进 shared metadata 边界
 */
data class ImmutableCollectedSourceMetadataResolution(
    val sources: List<ImmutableResolvedSourceMetadata>,
    val lsiClasses: List<LsiClass>,
)

data class ImmutableResolvedSourceMetadata(
    val metadata: ImmutableSourceMetadata,
    val lsiClasses: List<LsiClass>,
)
