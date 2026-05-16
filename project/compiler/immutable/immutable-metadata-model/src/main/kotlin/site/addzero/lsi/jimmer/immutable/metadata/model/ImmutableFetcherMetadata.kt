package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.codegen.LsiClassName

/**
 * immutable fetcher 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../FetcherGenerator`
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../FetcherDslGenerator`
 *
 * 迁移说明：
 * - 将 Fetcher/FetcherDsl 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutableFetcherTypeMetadata(
    val simpleName: String,
    val className: LsiClassName,
    val fetcherDslClassName: LsiClassName,
    val byBlockMetadata: ImmutableCallbackMetadata,
    val properties: List<ImmutableFetcherPropMetadata>,
)

data class ImmutableFetcherPropMetadata(
    val name: String,
    val isId: Boolean,
    val isList: Boolean,
    val supportsIdOnlyFetchType: Boolean,
    val supportsReferenceFetchType: Boolean,
    val supportsRecursive: Boolean,
    val targetClassName: LsiClassName?,
    val targetTableClassName: LsiClassName? = null,
    val childBlockMetadata: ImmutableCallbackMetadata?,
    val fieldConfigBlockMetadata: ImmutableCallbackMetadata?,
    val recursiveConfigBlockMetadata: ImmutableCallbackMetadata?,
    val targetIsEntity: Boolean,
    val targetIsEmbeddable: Boolean,
    val configurable: Boolean,
    val fieldKind: ImmutableFetcherFieldKind,
)

enum class ImmutableFetcherFieldKind {
    SIMPLE,
    REFERENCE,
    LIST,
}
