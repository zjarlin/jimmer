package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Anno
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.getEnumListArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.resolver.LsiResolver
import java.lang.annotation.ElementType

data class DtoAnnotationTargets(
    val field: Boolean = false,
    val getter: Boolean = false,
    val setter: Boolean = false,
    val property: Boolean = false,
) {
    fun supportsJavaElementType(elementType: ElementType): Boolean =
        when (elementType) {
            ElementType.FIELD -> field
            ElementType.METHOD -> getter
            else -> false
        }
}

object DtoAnnotationSupport {

    const val KOTLIN_DTO_TYPE_NAME = "org.babyfish.jimmer.kt.dto.KotlinDto"

    @JvmStatic
    fun isCopyableAnnotation(
        annotation: LsiAnnotation,
        dtoAnnotations: Collection<Anno>,
        forMethod: Boolean? = null,
    ): Boolean {
        val qualifiedName = annotation.qualifiedName ?: return false
        if (qualifiedName.startsWith(KOTLIN_DTO_TYPE_NAME)) {
            return false
        }
        if (
            qualifiedName.startsWith("org.babyfish.jimmer.") &&
            !qualifiedName.startsWith("org.babyfish.jimmer.client.")
        ) {
            return false
        }
        if (isNullityAnnotation(qualifiedName)) {
            return false
        }
        if (forMethod != null) {
            val targets = resolveTargets(annotation.annotations)
            val accept = if (targets.getter) {
                forMethod
            } else if (!forMethod) {
                targets.field
            } else {
                false
            }
            if (!accept) {
                return false
            }
        }
        return dtoAnnotations.none { dtoAnnotation ->
            val dtoQualifiedName = dtoAnnotation.qualifiedName
            dtoQualifiedName == qualifiedName || dtoQualifiedName?.endsWith(qualifiedName) == true
        }
    }

    @JvmStatic
    fun resolveTargetsOrNull(
        resolver: LsiResolver,
        qualifiedName: String,
    ): DtoAnnotationTargets? =
        resolver.findClassByQualifiedName(qualifiedName)?.let(::resolveTargets)

    @JvmStatic
    fun resolveTargets(annotationClass: LsiClass): DtoAnnotationTargets =
        resolveTargets(annotationClass.annotations)

    @JvmStatic
    fun resolveTargets(annotations: List<LsiAnnotation>): DtoAnnotationTargets {
        var field = false
        var getter = false
        var setter = false
        var property = false
        annotations
            .firstOrNull { it.qualifiedName == kotlin.annotation.Target::class.qualifiedName }
            ?.getEnumListArgument(kotlin.annotation.Target::allowedTargets)
            ?.forEach { target ->
                when (target) {
                    kotlin.annotation.AnnotationTarget.FIELD -> field = true
                    kotlin.annotation.AnnotationTarget.PROPERTY_GETTER,
                    kotlin.annotation.AnnotationTarget.FUNCTION -> getter = true
                    kotlin.annotation.AnnotationTarget.PROPERTY_SETTER -> setter = true
                    kotlin.annotation.AnnotationTarget.PROPERTY -> property = true
                    else -> Unit
                }
            }
        annotations
            .firstOrNull { it.qualifiedName == java.lang.annotation.Target::class.qualifiedName }
            ?.getEnumListArgument(java.lang.annotation.Target::value)
            ?.forEach { target ->
                when (target) {
                    ElementType.FIELD -> field = true
                    ElementType.METHOD -> getter = true
                    else -> Unit
                }
            }
        return DtoAnnotationTargets(
            field = field,
            getter = getter,
            setter = setter,
            property = property,
        )
    }

    @JvmStatic
    fun isNullityAnnotation(qualifiedName: String): Boolean {
        val simpleName = qualifiedName.substringAfterLast('.')
        return when (simpleName) {
            "Null",
            "Nullable",
            "NotNull",
            "NonNull" -> true
            else -> qualifiedName == "org.babyfish.jimmer.sql.kt.ast.table.spi.PropExpressionImplementor.Nullity"
        }
    }
}
