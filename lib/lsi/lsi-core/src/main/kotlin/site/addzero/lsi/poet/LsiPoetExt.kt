package site.addzero.lsi.poet

import kotlin.reflect.KClass
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.toLsiClassName
import site.addzero.lsi.type.LsiType

fun LsiType.toLsiPoet(nullableOverride: Boolean? = null): LsiTypeName {
    val resolvedNullable = nullableOverride ?: isNullable
    if (isArray && componentType != null) {
        return LsiArrayTypeName(
            componentType = componentType!!.toLsiPoet(),
            nullable = resolvedNullable
        )
    }

    val rawQualifiedName = qualifiedName
        ?.substringBefore('<')
        ?.removeSuffix("?")
        ?.removeSuffix("!")
    val rawSimpleName = simpleName
        ?.removeSuffix("?")
        ?.removeSuffix("!")

    val baseType = when {
        rawSimpleName == "*" && rawQualifiedName == null -> LsiStarTypeName
        rawQualifiedName != null -> LsiClassName.bestGuess(rawQualifiedName)
        rawSimpleName != null -> LsiTypeVariableName(rawSimpleName)
        else -> LsiClassName.bestGuess("kotlin.Any", nullable = true)
    }

    if (baseType is LsiClassName && typeParameters.isNotEmpty()) {
        return LsiParameterizedTypeName(
            rawType = baseType,
            typeArguments = typeParameters.map { it.toLsiPoet() },
            nullable = resolvedNullable
        )
    }

    return baseType.copyNullable(resolvedNullable)
}

fun LsiAnnotation.toLsiPoet(): LsiAnnotationSpec {
    val annotationType = qualifiedName
        ?: error("LsiAnnotation.qualifiedName must not be null when converting to LsiAnnotationSpec")
    return LsiAnnotationSpec(
        type = LsiClassName.bestGuess(annotationType),
        members = attributes.mapValues { (_, value) -> value.toLsiPoetAnnotationValue() },
        useSiteTarget = useSiteTarget
    )
}

private fun Any?.toLsiPoetAnnotationValue(): LsiAnnotationValue =
    when (this) {
        null -> LsiNullAnnotationValue
        is String -> LsiStringAnnotationValue(this)
        is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double ->
            LsiLiteralAnnotationValue(this)
        is Char -> LsiCharAnnotationValue(this)
        is Enum<*> -> LsiEnumAnnotationValue(
            enumType = LsiClassName.bestGuess(this::class.java.name),
            constantName = name
        )
        is Class<*> -> LsiClassAnnotationValue(LsiClassName.bestGuess(name))
        is KClass<*> -> LsiClassAnnotationValue(LsiClassName.bestGuess(qualifiedName ?: java.name))
        is LsiClass -> LsiClassAnnotationValue(toLsiClassName())
        is LsiType -> LsiTypeAnnotationValue(toLsiPoet())
        is LsiClassName -> LsiClassAnnotationValue(this)
        is LsiTypeName -> LsiTypeAnnotationValue(this)
        is LsiAnnotation -> LsiNestedAnnotationValue(toLsiPoet())
        is List<*> -> LsiArrayAnnotationValue(map { it.toLsiPoetAnnotationValue() })
        else -> LsiRawAnnotationValue(this)
    }
