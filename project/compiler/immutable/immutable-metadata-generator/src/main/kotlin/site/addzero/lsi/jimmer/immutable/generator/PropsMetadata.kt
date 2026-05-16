package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.KOTLIN_ANY_LSI_CLASS_NAME as ANY_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeRefMetadata
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiStarTypeName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.toBuiltInLsiClassNameOrNull

fun ImmutablePropsTypeRefMetadata.toLsiTypeName(
    nullableOverride: Boolean? = null,
): LsiTypeName =
    when {
        array -> componentType?.let { componentTypeMetadata ->
            LsiArrayTypeName(componentType = componentTypeMetadata.toLsiTypeName())
        } ?: nonArrayLsiTypeName()
        else -> nonArrayLsiTypeName()
    }.let { typeName ->
        when (nullableOverride) {
            true -> typeName.copyNullable(true)
            false -> typeName.copyNullable(false)
            null -> if (nullable) typeName.copyNullable(true) else typeName
        }
    }

private fun ImmutablePropsTypeRefMetadata.nonArrayLsiTypeName(): LsiTypeName {
    val normalizedQualifiedName = qualifiedName?.substringBefore('<')?.removeSuffix("?")?.removeSuffix("!")
    val normalizedSimpleName = simpleName?.removeSuffix("?")?.removeSuffix("!")
    normalizedQualifiedName?.toBuiltInLsiClassNameOrNull()?.let { return it }
    normalizedSimpleName?.toBuiltInLsiClassNameOrNull()?.let { return it }
    val rawTypeName =
        when {
            normalizedQualifiedName != null -> LsiClassName.bestGuess(normalizedQualifiedName)
            normalizedSimpleName == "*" -> LsiStarTypeName
            normalizedSimpleName != null -> LsiTypeVariableName(normalizedSimpleName)
            else -> ANY_LSI_CLASS_NAME.copyNullable(true)
        }
    if (rawTypeName == LsiStarTypeName || typeArguments.isEmpty()) {
        return rawTypeName
    }
    return LsiParameterizedTypeName(
        rawType = rawTypeName as LsiClassName,
        typeArguments = typeArguments.map { argument ->
            if (argument.simpleName == "*" && argument.qualifiedName == null) {
                LsiStarTypeName
            } else {
                argument.toLsiTypeName()
            }
        }
    )
}
