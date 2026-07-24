package site.addzero.lsi.jimmer.dto

/** Kotlin DTO 属性的生成形态。 */
enum class DtoKotlinPropertyShape {
    CONCRETE,
    ABSTRACT_ACCESSOR,
}

/** 按 Kotlin 属性形态选择当前注解应用的有效落点。 */
fun DtoAnnotationApplication.kotlinPropertyPlacement(
    shape: DtoKotlinPropertyShape,
): DtoAnnotationPlacement? {
    val annotationQualifiedName = annotation.type.requireTypeQualifiedName()
    if (
        origin == DtoAnnotationOrigin.DTO &&
        annotationQualifiedName.startsWith(JACKSON_ANNOTATION_PACKAGE_PREFIX)
    ) {
        require(DtoAnnotationPlacement.GETTER in placements) {
            "DTO Jackson annotation does not support GETTER placement: $annotationQualifiedName"
        }
        return DtoAnnotationPlacement.GETTER
    }
    val priorities = when (shape) {
        DtoKotlinPropertyShape.CONCRETE -> CONCRETE_PROPERTY_PLACEMENT_PRIORITIES
        DtoKotlinPropertyShape.ABSTRACT_ACCESSOR -> ABSTRACT_PROPERTY_PLACEMENT_PRIORITIES
    }
    return priorities.firstOrNull(placements::contains)
}

private val CONCRETE_PROPERTY_PLACEMENT_PRIORITIES = listOf(
    DtoAnnotationPlacement.FIELD,
    DtoAnnotationPlacement.GETTER,
    DtoAnnotationPlacement.SETTER,
    DtoAnnotationPlacement.PROPERTY,
)

private val ABSTRACT_PROPERTY_PLACEMENT_PRIORITIES = listOf(
    DtoAnnotationPlacement.GETTER,
    DtoAnnotationPlacement.PROPERTY,
)

private const val JACKSON_ANNOTATION_PACKAGE_PREFIX = "com.fasterxml.jackson."
