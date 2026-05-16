package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiTypeName

/**
 * immutable builder 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../BuilderGenerator`
 *
 * 迁移说明：
 * - 将 Builder 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutableBuilderTypeMetadata(
    val className: LsiClassName,
    val producerClassName: LsiClassName,
    val draftImplClassName: LsiClassName,
    val visibleSlotNames: List<String>,
    val setters: List<ImmutableBuilderSetterMetadata>,
)

data class ImmutableBuilderSetterMetadata(
    val name: String,
    val parameterLsiTypeName: LsiTypeName,
    val returnTypeName: LsiClassName,
    val ownerProducerClassName: LsiClassName,
    val slotName: String,
    val isNullable: Boolean,
    val lsiAnnotations: List<LsiAnnotationSpec>,
)
