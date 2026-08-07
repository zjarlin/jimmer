package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.generatedJacksonPolymorphicRootAnnotations
import site.addzero.lsi.jimmer.dto.generatedJacksonPolymorphicTypeNameAnnotationOrNull
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentNameStyle
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetTypeName

/** 将完整的多态输入根 LSI 注解转换为平台中立的源码注解。 */
internal fun DtoType.toJacksonPolymorphicRootPoetAnnotations(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
    generatedRootTypeName: LsiPoetTypeName,
    targetLanguage: LsiLanguage,
): List<LsiPoetAnnotation> {
    targetLanguage.requireJacksonPolymorphismTargetLanguage()
    return generatedJacksonPolymorphicRootAnnotations(
        graph = graph,
        immutableSchema = immutableSchema,
        annotationContract = annotationContract,
        generatedRootTypeId = generatedRootTypeName.typeId,
    ).map { annotation ->
        annotation.toJacksonPolymorphismPoetAnnotation(targetLanguage)
    }
}

/** 将完整的分支 JsonTypeName LSI 注解转换为平台中立的源码注解。 */
internal fun DtoPolymorphicBranch.toJacksonPolymorphicTypeNamePoetAnnotationOrNull(
    rootType: DtoType,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
): LsiPoetAnnotation? {
    val annotation = generatedJacksonPolymorphicTypeNameAnnotationOrNull(
        rootType = rootType,
        graph = graph,
        immutableSchema = immutableSchema,
        annotationContract = annotationContract,
    ) ?: return null
    return annotation.toJacksonPolymorphismPoetAnnotation(
        targetLanguage = null,
        positionalValueArgument = true,
    )
}

/** 返回 Jackson 多态注解及生成分支引用需要的精确源码类型名。 */
internal fun DtoType.jacksonPolymorphismPoetTypeNames(
    generatedRootTypeName: LsiPoetTypeName,
): List<LsiPoetTypeName> {
    val generatedBranchTypeNames = polymorphism
        ?.branches
        .orEmpty()
        .map { branch -> generatedRootTypeName.nestedType(branch.className) }
    return (
        JACKSON_POLYMORPHISM_POET_TYPE_NAMES +
            generatedRootTypeName +
            generatedBranchTypeNames
        ).distinctBy(LsiPoetTypeName::typeId)
}

private fun LsiAnnotation.toJacksonPolymorphismPoetAnnotation(
    targetLanguage: LsiLanguage?,
    positionalValueArgument: Boolean = false,
): LsiPoetAnnotation {
    val argumentNames = if (explicitArgumentNamesInSourceOrder.isNotEmpty()) {
        explicitArgumentNamesInSourceOrder
    } else {
        arguments
            .filterValues { argument -> argument.isExplicit }
            .keys
            .sorted()
    }
    if (positionalValueArgument) {
        require(argumentNames == listOf("value")) {
            "Positional Jackson annotation must declare exactly one value argument: ${type.value}"
        }
    }
    val poetArguments = argumentNames.map { name ->
        val argument = requireNotNull(arguments[name]) {
            "Jackson annotation argument '$name' is absent: ${type.value}"
        }
        val value = argument.value.toJacksonPolymorphismPoetValue(targetLanguage)
        if (positionalValueArgument) {
            LsiPoetAnnotationArgument.Positional(value)
        } else {
            LsiPoetAnnotationArgument.Named(
                name = name,
                value = value,
                nameStyle = LsiPoetAnnotationArgumentNameStyle.VERBATIM,
            )
        }
    }
    return LsiPoetAnnotation(
        type = type,
        arguments = poetArguments,
        useSiteTarget = useSiteTarget,
    )
}

private fun LsiAnnotationValue.toJacksonPolymorphismPoetValue(
    targetLanguage: LsiLanguage?,
): LsiPoetAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiPoetAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiPoetAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiPoetAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiPoetAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiPoetAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiPoetAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiPoetAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiPoetAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiPoetAnnotationValue.StringValue(value)
        is LsiAnnotationValue.EnumValue -> LsiPoetAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> LsiPoetAnnotationValue.ClassValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> LsiPoetAnnotationValue.NestedAnnotationValue(
            annotation.toJacksonPolymorphismPoetAnnotation(targetLanguage)
        )
        is LsiAnnotationValue.ArrayValue -> LsiPoetAnnotationValue.ArrayValue(
            elements = elements.map { element ->
                element.toJacksonPolymorphismPoetValue(targetLanguage)
            },
            sourceStyle = targetLanguage.jacksonPolymorphismArrayStyle(elements.size),
        )
    }
}

private fun LsiLanguage?.jacksonPolymorphismArrayStyle(
    elementCount: Int,
): LsiPoetAnnotationArrayStyle {
    return when {
        this == LsiLanguage.JAVA -> LsiPoetAnnotationArrayStyle.COMPACT_MULTI_LINE_LITERAL
        this == LsiLanguage.KOTLIN && elementCount > 1 ->
            LsiPoetAnnotationArrayStyle.LINE_SEPARATED_LITERAL
        this == LsiLanguage.KOTLIN -> LsiPoetAnnotationArrayStyle.LITERAL
        else -> error("Jackson annotation array conversion requires a source target language")
    }
}

private fun LsiPoetTypeName.nestedType(simpleName: String): LsiPoetTypeName {
    return JimmerDtoPoetTypeNames.create(packageName, simpleNames + simpleName)
}

private fun LsiLanguage.requireJacksonPolymorphismTargetLanguage() {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "Jackson polymorphism source generation requires Java or Kotlin, got $this"
    }
}

private val JSON_TYPE_INFO_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonTypeInfo"),
)

private val JSON_TYPE_INFO_ID_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonTypeInfo", "Id"),
)

private val JSON_TYPE_INFO_AS_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonTypeInfo", "As"),
)

private val JSON_SUB_TYPES_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonSubTypes"),
)

private val JSON_SUB_TYPES_TYPE_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonSubTypes", "Type"),
)

private val JSON_TYPE_NAME_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "com.fasterxml.jackson.annotation",
    listOf("JsonTypeName"),
)

private val JACKSON_POLYMORPHISM_POET_TYPE_NAMES = listOf(
    JSON_TYPE_INFO_TYPE_NAME,
    JSON_TYPE_INFO_ID_TYPE_NAME,
    JSON_TYPE_INFO_AS_TYPE_NAME,
    JSON_SUB_TYPES_TYPE_NAME,
    JSON_SUB_TYPES_TYPE_TYPE_NAME,
    JSON_TYPE_NAME_TYPE_NAME,
)
