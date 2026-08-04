package site.addzero.lsi.apt

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiFrontendOptions
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

fun AnnotationMirror.toLsiAnnotation(
    processingEnvironment: ProcessingEnvironment,
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    frontendOptions: LsiFrontendOptions = LsiFrontendOptions(),
): LsiAnnotation {
    return AptLsiContext(
        processingEnvironment,
        frontendOptions,
    ).toLsiAnnotation(this, useSiteTarget)
}

internal fun AptLsiContext.toLsiAnnotations(
    annotations: List<AnnotationMirror>,
    useSiteTarget: LsiAnnotationUseSiteTarget?,
): List<LsiAnnotation> {
    return annotations.map { annotation ->
        toLsiAnnotation(annotation, useSiteTarget)
    }
}

internal fun AptLsiContext.toLsiAnnotation(
    annotation: AnnotationMirror,
    useSiteTarget: LsiAnnotationUseSiteTarget?,
): LsiAnnotation {
    val annotationElement = annotation.annotationType.asElement() as TypeElement
    val explicitArgumentNames = annotation.elementValues.keys
        .map { method -> method.simpleName.toString() }
        .toSet()
    val arguments = elements.getElementValuesWithDefaults(annotation)
        .mapKeys { (method, _) -> method.simpleName.toString() }
        .mapValues { (method, value) ->
            LsiAnnotationArgument(
                value = toLsiAnnotationValue(value),
                origin = if (method in explicitArgumentNames) {
                    LsiAnnotationArgumentOrigin.EXPLICIT
                } else {
                    LsiAnnotationArgumentOrigin.DEFAULT
                },
            )
        }
    return LsiAnnotation(
        type = LsiSymbolId.type(annotationElement.qualifiedName.toString()),
        arguments = arguments,
        useSiteTarget = useSiteTarget,
        explicitArgumentNamesInSourceOrder = annotation.elementValues.keys.map { method ->
            method.simpleName.toString()
        },
    )
}

private fun AptLsiContext.toLsiAnnotationValue(
    annotationValue: AnnotationValue,
): LsiAnnotationValue {
    return when (val value = annotationValue.value) {
        is Boolean -> LsiAnnotationValue.BooleanValue(value)
        is Byte -> LsiAnnotationValue.ByteValue(value)
        is Short -> LsiAnnotationValue.ShortValue(value)
        is Int -> LsiAnnotationValue.IntValue(value)
        is Long -> LsiAnnotationValue.LongValue(value)
        is Float -> LsiAnnotationValue.FloatValue(value)
        is Double -> LsiAnnotationValue.DoubleValue(value)
        is Char -> LsiAnnotationValue.CharValue(value)
        is String -> LsiAnnotationValue.StringValue(value)
        is VariableElement -> value.toLsiEnumAnnotationValue()
        is TypeMirror -> LsiAnnotationValue.ClassValue(toLsiType(value))
        is AnnotationMirror -> LsiAnnotationValue.NestedAnnotationValue(
            toLsiAnnotation(value, null),
        )
        is List<*> -> LsiAnnotationValue.ArrayValue(
            value.map { element ->
                val nestedValue = element as? AnnotationValue
                    ?: error("Unsupported APT annotation array element: ${element?.javaClass?.name}")
                toLsiAnnotationValue(nestedValue)
            },
        )
        else -> error("Unsupported APT annotation value: ${value?.javaClass?.name}")
    }
}

private fun VariableElement.toLsiEnumAnnotationValue(): LsiAnnotationValue.EnumValue {
    val enumType = enclosingElement as TypeElement
    return LsiAnnotationValue.EnumValue(
        enumType = LsiSymbolId.type(enumType.qualifiedName.toString()),
        entryName = simpleName.toString(),
    )
}
