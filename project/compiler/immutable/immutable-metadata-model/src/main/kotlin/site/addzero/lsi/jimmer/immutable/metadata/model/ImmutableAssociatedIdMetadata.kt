package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.poet.LsiTypeName

/**
 * immutable associated-id 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../AssociatedIdGenerator`
 *
 * 迁移说明：
 * - 将 associated-id 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutableAssociatedIdMetadata(
    val name: String,
    val associatedIdLsiTypeName: LsiTypeName,
    val ownerPropName: String,
    val targetIdPropName: String,
    val isNullable: Boolean,
)
