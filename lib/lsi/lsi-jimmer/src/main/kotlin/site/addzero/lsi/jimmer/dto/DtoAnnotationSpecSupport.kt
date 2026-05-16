package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.dto.compiler.Anno.AnnoValue
import org.babyfish.jimmer.dto.compiler.Anno.ArrayValue
import org.babyfish.jimmer.dto.compiler.Anno.EnumValue
import org.babyfish.jimmer.dto.compiler.Anno.LiteralValue
import org.babyfish.jimmer.dto.compiler.Anno.TypeRefValue
import org.babyfish.jimmer.dto.compiler.TypeRef
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiAnnotationValue
import site.addzero.lsi.poet.LsiArrayAnnotationValue
import site.addzero.lsi.poet.LsiCharAnnotationValue
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiEnumAnnotationValue
import site.addzero.lsi.poet.LsiLiteralAnnotationValue
import site.addzero.lsi.poet.LsiNestedAnnotationValue
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiTypeAnnotationValue
import site.addzero.lsi.poet.LsiTypeName

fun Anno.toLsiAnnotationSpec(
    typeRefToLsiTypeName: (TypeRef) -> LsiTypeName,
    annotationClassProvider: (String) -> LsiClass? = { null },
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
): LsiAnnotationSpec {
    val annotationType = LsiClassName.bestGuess(qualifiedName)
    val singleValueArgument = valueMap.entries.singleOrNull()?.takeIf { it.key == "value" }?.value
    if (singleValueArgument != null) {
        return LsiAnnotationSpec(
            type = annotationType,
            positionalArguments =
                if (annotationClassProvider(qualifiedName).isVarargValueAnnotation()) {
                    when (singleValueArgument) {
                        is ArrayValue -> singleValueArgument.elements.map { element ->
                            element.toLsiAnnotationValue(typeRefToLsiTypeName, annotationClassProvider)
                        }
                        else -> listOf(
                            singleValueArgument.toLsiAnnotationValue(typeRefToLsiTypeName, annotationClassProvider)
                        )
                    }
                } else {
                    listOf(singleValueArgument.toLsiAnnotationValue(typeRefToLsiTypeName, annotationClassProvider))
                },
            useSiteTarget = useSiteTarget,
        )
    }
    return LsiAnnotationSpec(
        type = annotationType,
        members = valueMap.mapValues { (_, value) ->
            value.toLsiAnnotationValue(typeRefToLsiTypeName, annotationClassProvider)
        },
        useSiteTarget = useSiteTarget,
    )
}

private fun Anno.Value.toLsiAnnotationValue(
    typeRefToLsiTypeName: (TypeRef) -> LsiTypeName,
    annotationClassProvider: (String) -> LsiClass?,
): LsiAnnotationValue =
    when (this) {
        is ArrayValue -> LsiArrayAnnotationValue(
            elements.map { element ->
                element.toLsiAnnotationValue(typeRefToLsiTypeName, annotationClassProvider)
            }
        )
        is AnnoValue -> LsiNestedAnnotationValue(
            anno.toLsiAnnotationSpec(typeRefToLsiTypeName, annotationClassProvider)
        )
        is TypeRefValue -> LsiTypeAnnotationValue(typeRefToLsiTypeName(typeRef))
        is EnumValue -> LsiEnumAnnotationValue(
            enumType = LsiClassName.bestGuess(qualifiedName),
            constantName = constant,
        )
        is LiteralValue -> literalValueToLsiAnnotationValue(value)
        else -> error("Unsupported annotation value: ${this::class.qualifiedName}")
    }

private fun literalValueToLsiAnnotationValue(value: String): LsiAnnotationValue =
    if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        LsiStringAnnotationValue(value.substring(1, value.length - 1))
    } else if (value.length >= 3 && value.first() == '\'' && value.last() == '\'') {
        LsiCharAnnotationValue(value[1])
    } else {
        LsiLiteralAnnotationValue(value)
    }

private fun LsiClass?.isVarargValueAnnotation(): Boolean =
    this?.constructors?.any { constructor ->
        constructor.parameters.any { parameter ->
            parameter.name == "value" && parameter.isVararg
        }
    } == true
