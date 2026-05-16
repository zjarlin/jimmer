package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.codegen.LsiClassName

/**
 * immutable implementor 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../ImplementorGenerator`
 *
 * 迁移说明：
 * - 将 Implementor 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutableImplementorTypeMetadata(
    val className: LsiClassName,
    val producerClassName: LsiClassName,
    val typeDescription: String,
    val propertyOrderNames: List<String>,
    val getCases: List<ImmutableImplementorPropCaseMetadata>,
    val deeperPropIds: List<ImmutableImplementorDeepPropIdMetadata>,
)

data class ImmutableImplementorPropCaseMetadata(
    val name: String,
    val slotName: String,
)

data class ImmutableImplementorDeepPropIdMetadata(
    val constantName: String,
    val propName: String,
)
