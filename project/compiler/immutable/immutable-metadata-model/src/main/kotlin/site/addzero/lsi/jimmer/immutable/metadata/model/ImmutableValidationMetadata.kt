package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.poet.LsiTypeName

/**
 * immutable validation 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../ValidationGenerator`
 *
 * 迁移说明：
 * - 将 validation 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutableValidationPropMetadata(
    val name: String,
    val slotName: String,
    val lsiField: LsiField,
    val validationMessages: Map<LsiClassName, String>,
    val validationAnnotationMirrorMultiMap: Map<String, List<LsiAnnotation>>,
    private val lsiTypeName: LsiTypeName,
    private val nonNullLsiTypeName: LsiTypeName,
    private val description: String,
    val isNullable: Boolean,
) {

    fun lsiTypeName(overrideNullable: Boolean? = null): LsiTypeName =
        when (overrideNullable) {
            true -> lsiTypeName.copyNullable(true)
            false -> nonNullLsiTypeName
            null -> lsiTypeName
        }

    override fun toString(): String = description
}
