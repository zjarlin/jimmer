package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.PROP_COMPARABLE_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_DATE_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_NUMERIC_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_STRING_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_TEMPORAL_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeRefMetadata
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiTypeName

internal fun ImmutablePropsTypeRefMetadata.toPropExpressionTypeName(): LsiTypeName {
    val scalarType = toLsiTypeName(nullableOverride = false)
    val rawQualifiedName = normalizedQualifiedName()
    return when {
        scalarType.isPrimitiveNumeric() -> PROP_NUMERIC_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
        rawQualifiedName == "kotlin.String" || rawQualifiedName == "java.lang.String" -> PROP_STRING_EXPRESSION_LSI_CLASS_NAME
        subtypeOfNumber -> PROP_NUMERIC_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
        subtypeOfDate -> PROP_DATE_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
        subtypeOfTemporal -> PROP_TEMPORAL_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
        subtypeOfComparable -> PROP_COMPARABLE_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
        else -> PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(scalarType)
    }
}

internal fun ImmutablePropsTypeRefMetadata.toPropExpressionClassName(): LsiClassName =
    toEntityClassName().copy(
        simpleNames = toEntityClassName().simpleNames.dropLast(1) + "${toEntityClassName().simpleName}PropExpression"
    )

internal fun ImmutablePropsTypeRefMetadata.toTableClassName(): LsiClassName =
    toEntityClassName().copy(
        simpleNames = toEntityClassName().simpleNames.dropLast(1) + "${toEntityClassName().simpleName}Table"
    )

internal fun ImmutablePropsTypeRefMetadata.toTableExClassName(): LsiClassName =
    toEntityClassName().copy(
        simpleNames = toEntityClassName().simpleNames.dropLast(1) + "${toEntityClassName().simpleName}TableEx"
    )

internal fun ImmutablePropsTypeRefMetadata.toRemoteTableClassName(): LsiClassName =
    toTableClassName().nested("Remote")

private fun ImmutablePropsTypeRefMetadata.toEntityClassName(): LsiClassName {
    val rawQualifiedName = normalizedQualifiedName()
        ?: error("Props metadata bug: type reference must define qualifiedName")
    return LsiClassName.bestGuess(rawQualifiedName)
}

private fun ImmutablePropsTypeRefMetadata.normalizedQualifiedName(): String? =
    qualifiedName
        ?.substringBefore('<')
        ?.removeSuffix("?")
        ?.removeSuffix("!")

private fun LsiTypeName.isPrimitiveNumeric(): Boolean =
    this is LsiClassName &&
        copyNullable(false).canonicalName in setOf(
            "kotlin.Byte",
            "kotlin.Short",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Float",
            "kotlin.Double",
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
        )
