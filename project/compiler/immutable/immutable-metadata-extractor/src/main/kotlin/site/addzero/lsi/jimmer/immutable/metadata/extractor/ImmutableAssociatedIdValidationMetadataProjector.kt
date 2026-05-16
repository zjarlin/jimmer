package site.addzero.lsi.jimmer.immutable.metadata.extractor

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp

private const val JAVAX_PREFIX = "javax.validation.constraints."

private const val JAKARTA_PREFIX = "jakarta.validation.constraints."

private val ImmutableProp.validationAnnotationMirrorMultiMap: Map<String, List<LsiAnnotation>>
    get() = mutableMapOf<String, MutableList<LsiAnnotation>>().apply {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../generator/Validations.validationAnnotationMirrorMultiMap
        // 迁移说明：Validation 约束注解聚合从 generator projector 前移到 extractor，后续 validation metadata 可在进入 generator 前直接构建完成
        val annotations = lsiType?.annotations ?: emptyList()
        for (annotation in annotations) {
            val qualifiedName = annotation.fullName
            if (qualifiedName.startsWith(JAVAX_PREFIX)) {
                val name = qualifiedName.substring(JAVAX_PREFIX.length)
                computeIfAbsent(name) { mutableListOf() } += annotation
            } else if (qualifiedName.startsWith(JAKARTA_PREFIX)) {
                val name = qualifiedName.substring(JAKARTA_PREFIX.length)
                computeIfAbsent(name) { mutableListOf() } += annotation
            }
        }
    }

fun ImmutableProp.toAssociatedIdMetadata(): ImmutableAssociatedIdMetadata? {
    if (!isAssociation(true) ||
        isList ||
        idViewProp != null
    ) {
        return null
    }
    val generatedName = StringUtil.identifier(name, "Id")
    if (declaringType.properties.containsKey(generatedName)) {
        return null
    }
    val associatedIdProp = targetType?.idProp ?: return null
    return ImmutableAssociatedIdMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../AssociatedIdGenerator.generate 的属性级 `ImmutableProp` 读取
        // 迁移说明：关联 ID 生成所需的派生命名、目标 id 类型与 setter/getter 语义前移到 metadata-extractor，generator 不再自行投影 ImmutableProp
        name = generatedName,
        associatedIdLsiTypeName = associatedIdProp.toLsiTypeName(overrideNullable = isNullable),
        ownerPropName = name,
        targetIdPropName = associatedIdProp.name,
        isNullable = isNullable,
    )
}

fun ImmutableProp.toValidationPropMetadata(): ImmutableValidationPropMetadata =
    ImmutableValidationPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ValidationGenerator 的属性级 `ImmutableProp` 读取
        // 迁移说明：Validation 生成链所需的属性名、错误锚点、类型形状、校验消息与约束注解前移到 metadata-extractor，generator 不再自行投影 ImmutableProp
        name = name,
        slotName = slotName,
        lsiField = lsiField,
        validationMessages = validationMessages,
        validationAnnotationMirrorMultiMap = validationAnnotationMirrorMultiMap,
        lsiTypeName = toLsiTypeName(),
        nonNullLsiTypeName = toLsiTypeName(overrideNullable = false),
        description = toString(),
        isNullable = isNullable,
    )
