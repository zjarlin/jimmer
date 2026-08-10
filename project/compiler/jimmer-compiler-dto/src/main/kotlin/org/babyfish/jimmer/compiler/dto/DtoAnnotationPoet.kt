package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.model.sourceLsiAnnotation

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoBuilderSetterAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.propertyAnnotationApplications
import site.addzero.lsi.jimmer.dto.propertySourceAnnotationApplications
import site.addzero.lsi.jimmer.dto.typeAnnotationApplications
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentLayout
import site.addzero.lsi.model.LsiAnnotationArgumentNameStyle
import site.addzero.lsi.model.LsiAnnotationArrayStyle
import site.addzero.lsi.model.LsiClassLiteralStyle
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.toLsiTypeNames

/** 按冻结契约顺序将 DTO 类型注解降低为平台中立源码结构。 */
internal fun DtoType.typeAnnotationPoetAnnotations(
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
): List<LsiAnnotation> {
    return annotations.toDtoPoetAnnotations(
        applications = typeAnnotationApplications(annotationContract),
        annotationContract = annotationContract,
        targetLanguage = targetLanguage,
        targetDescription = "type",
        includeImmutableDefaultArguments = false,
    )
}

/** 按冻结契约顺序将 DTO 属性注解降低为平台中立源码结构。 */
internal fun DtoProp.propertyAnnotationPoetAnnotations(
    annotationContract: DtoAnnotationContract,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): List<LsiAnnotation> {
    val applications = if (targetLanguage == LsiLanguage.KOTLIN) {
        propertySourceAnnotationApplications(annotationContract, immutableSchema)
    } else {
        propertyAnnotationApplications(annotationContract)
    }
    return annotations.toDtoPoetAnnotations(
        applications = applications,
        annotationContract = annotationContract,
        targetLanguage = targetLanguage,
        targetDescription = "property",
        includeImmutableDefaultArguments = targetLanguage == LsiLanguage.KOTLIN,
    )
}

private fun List<DtoAnnotation>.toDtoPoetAnnotations(
    applications: List<DtoAnnotationApplication>,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    targetDescription: String,
    includeImmutableDefaultArguments: Boolean,
): List<LsiAnnotation> {
    targetLanguage.requireDtoAnnotationTargetLanguage()
    val dtoAnnotationsByTypeId = groupBy(DtoAnnotation::typeId)
    val nextDtoOccurrenceByTypeId = mutableMapOf<LsiSymbolId, Int>()
    return applications.map { application ->
        val dtoSourceAnnotation = if (application.origin == DtoAnnotationOrigin.DTO) {
            val occurrence = nextDtoOccurrenceByTypeId.getOrDefault(application.annotation.type, 0)
            nextDtoOccurrenceByTypeId[application.annotation.type] = occurrence + 1
            dtoAnnotationsByTypeId[application.annotation.type]
                ?.getOrNull(occurrence)
                ?: error(
                    "Frozen DTO $targetDescription annotation application has no matching source occurrence: " +
                        "${application.annotation.type.value}#$occurrence"
                )
        } else {
            null
        }
        application.toDtoPoetAnnotation(
            dtoSourceAnnotation = dtoSourceAnnotation,
            annotationContract = annotationContract,
            targetLanguage = targetLanguage,
            includeImmutableDefaultArguments = includeImmutableDefaultArguments,
        )
    }
}

/** 为一组 DTO 注解解析完整且精确的源码类型名称。 */
internal fun LsiWorkspace.dtoAnnotationPoetTypeNames(
    annotations: List<LsiAnnotation>,
): List<LsiTypeName> {
    val referencedTypeIds = annotations.flatMapTo(sortedSetOf()) { annotation ->
        annotation.referencedTypeIds
    }
    return toLsiTypeNames(referencedTypeIds)
}

/** 将冻结的类型注解应用降低为平台中立源码结构。 */
internal fun DtoAnnotationApplication.toDtoPoetAnnotation(
    dtoSourceAnnotation: DtoAnnotation?,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    includeImmutableDefaultArguments: Boolean = false,
): LsiAnnotation {
    return lowerDtoPoetAnnotation(
        annotation = annotation,
        origin = origin,
        dtoSourceAnnotation = dtoSourceAnnotation,
        annotationContract = annotationContract,
        targetLanguage = targetLanguage,
        includeImmutableDefaultArguments = includeImmutableDefaultArguments,
    )
}

/** 将冻结的 Builder setter 注解应用降低为平台中立源码结构。 */
internal fun DtoBuilderSetterAnnotationApplication.toDtoPoetAnnotation(
    dtoSourceAnnotation: DtoAnnotation?,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
): LsiAnnotation {
    return lowerDtoPoetAnnotation(
        annotation = annotation,
        origin = origin,
        dtoSourceAnnotation = dtoSourceAnnotation,
        annotationContract = annotationContract,
        targetLanguage = targetLanguage,
        includeImmutableDefaultArguments = false,
    )
}

private fun lowerDtoPoetAnnotation(
    annotation: LsiAnnotation,
    origin: DtoAnnotationOrigin,
    dtoSourceAnnotation: DtoAnnotation?,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    includeImmutableDefaultArguments: Boolean,
): LsiAnnotation {
    targetLanguage.requireDtoAnnotationTargetLanguage()
    val declaration = annotationContract.declarationsByTypeId.getValue(annotation.type)
    return when (origin) {
        DtoAnnotationOrigin.DTO -> requireNotNull(dtoSourceAnnotation)
            .toDtoPoetAnnotation(
                frozen = annotation,
                declaration = declaration,
                declarationsByTypeId = annotationContract.declarationsByTypeId,
                targetLanguage = targetLanguage,
                nested = false,
            )
        DtoAnnotationOrigin.IMMUTABLE -> annotation.toImmutableDtoPoetAnnotation(
            declaration = declaration,
            declarationsByTypeId = annotationContract.declarationsByTypeId,
            targetLanguage = targetLanguage,
            includeDefaultArguments = includeImmutableDefaultArguments,
        )
    }
}

private fun DtoAnnotation.toDtoPoetAnnotation(
    frozen: LsiAnnotation,
    declaration: DtoAnnotationDeclaration,
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
    nested: Boolean,
): LsiAnnotation {
    require(typeId == frozen.type) {
        "DTO annotation source and frozen application types differ: ${typeId.value} and ${frozen.type.value}"
    }
    val sourceArgumentNames = arguments.map { argument -> argument.name }
    val frozenArgumentNames = frozen.arguments
        .filterValues { argument -> argument.isExplicit }
        .keys
    require(sourceArgumentNames.toSet() == frozenArgumentNames) {
        "DTO annotation source and frozen argument names differ: ${typeId.value}"
    }
    val namedArguments = arguments.map { sourceArgument ->
        val frozenArgument = requireNotNull(frozen[sourceArgument.name])
        LsiSourceAnnotationArgument.Named(
            name = sourceArgument.name,
            value = sourceArgument.value.toDtoPoetAnnotationValue(
                frozen = frozenArgument.value,
                declarationsByTypeId = declarationsByTypeId,
                targetLanguage = targetLanguage,
            ),
        )
    }
    val soleValue = namedArguments.singleOrNull()
        ?.takeIf { argument -> argument.name == "value" }
    val kotlinTopLevelValueVararg =
        !nested &&
            targetLanguage == LsiLanguage.KOTLIN &&
            declaration.kotlinValueVararg &&
            soleValue != null
    val renderedArguments = when {
        kotlinTopLevelValueVararg -> soleValue.value.toPositionalVarargArguments()
        nested && soleValue != null -> listOf(LsiSourceAnnotationArgument.Positional(soleValue.value))
        else -> namedArguments
    }
    return sourceLsiAnnotation(
        type = typeId,
        arguments = renderedArguments,
        argumentLayout = when {
            renderedArguments.isEmpty() || targetLanguage == LsiLanguage.JAVA -> {
                LsiAnnotationArgumentLayout.PLATFORM_DEFAULT
            }
            nested && soleValue != null -> LsiAnnotationArgumentLayout.SINGLE_LINE
            kotlinTopLevelValueVararg -> LsiAnnotationArgumentLayout.SINGLE_LINE
            else -> LsiAnnotationArgumentLayout.MULTI_LINE
        },
    )
}

private fun DtoAnnotationValue.toDtoPoetAnnotationValue(
    frozen: LsiAnnotationValue,
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
): LsiAnnotationValue {
    if (this !is DtoAnnotationValue.ArrayValue && frozen is LsiAnnotationValue.ArrayValue) {
        require(frozen.elements.size == 1) {
            "Scalar DTO annotation source must freeze to one array element"
        }
        return toDtoPoetAnnotationValue(
            frozen = frozen.elements.single(),
            declarationsByTypeId = declarationsByTypeId,
            targetLanguage = targetLanguage,
        )
    }
    return when (this) {
        is DtoAnnotationValue.ArrayValue -> {
            require(frozen is LsiAnnotationValue.ArrayValue && elements.size == frozen.elements.size) {
                "DTO annotation array source does not match its frozen value"
            }
            LsiAnnotationValue.ArrayValue(
                elements = elements.zip(frozen.elements) { sourceElement, frozenElement ->
                    sourceElement.toDtoPoetAnnotationValue(
                        frozen = frozenElement,
                        declarationsByTypeId = declarationsByTypeId,
                        targetLanguage = targetLanguage,
                    )
                },
                sourceStyle = LsiAnnotationArrayStyle.MULTI_LINE_LITERAL,
            )
        }
        is DtoAnnotationValue.AnnotationValue -> {
            require(frozen is LsiAnnotationValue.NestedAnnotationValue) {
                "DTO nested annotation source does not match its frozen value"
            }
            LsiAnnotationValue.NestedAnnotationValue(
                annotation.toDtoPoetAnnotation(
                    frozen = frozen.annotation,
                    declaration = declarationsByTypeId.getValue(annotation.typeId),
                    declarationsByTypeId = declarationsByTypeId,
                    targetLanguage = targetLanguage,
                    nested = true,
                ),
            )
        }
        is DtoAnnotationValue.EnumValue -> {
            require(
                frozen is LsiAnnotationValue.EnumValue &&
                    enumTypeId == frozen.enumType &&
                    constant == frozen.entryName
            ) {
                "DTO enum annotation source does not match its frozen value"
            }
            LsiAnnotationValue.EnumValue(frozen.enumType, frozen.entryName)
        }
        is DtoAnnotationValue.TypeValue -> {
            require(frozen is LsiAnnotationValue.ClassValue) {
                "DTO class annotation source does not match its frozen value"
            }
            LsiAnnotationValue.ClassValue(
                type = frozen.type,
                sourceStyle = if (
                    targetLanguage == LsiLanguage.KOTLIN &&
                    (frozen.type as? LsiPrimitiveType)?.boxed == true
                ) {
                    LsiClassLiteralStyle.JAVA_BOXED_PRIMITIVE_QUALIFIED
                } else {
                    LsiClassLiteralStyle.PLATFORM_TYPE
                },
            )
        }
        is DtoAnnotationValue.LiteralValue -> frozen.toDtoLiteralPoetValue()
    }
}

private fun LsiAnnotation.toImmutableDtoPoetAnnotation(
    declaration: DtoAnnotationDeclaration,
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
    includeDefaultArguments: Boolean,
): LsiAnnotation {
    require(type == declaration.typeId) {
        "Immutable annotation and declaration types differ: ${type.value} and ${declaration.typeId.value}"
    }
    val explicitArgumentNames = arguments
        .filterValues { argument -> argument.isExplicit }
        .keys
    val includedArgumentNames = if (includeDefaultArguments) {
        arguments.keys
    } else {
        explicitArgumentNames
    }
    val orderedArgumentNames = when {
        !includeDefaultArguments && explicitArgumentNamesInSourceOrder.isNotEmpty() -> {
            explicitArgumentNamesInSourceOrder
        }
        else -> declaration.argumentNamesInDeclarationOrder.filter(includedArgumentNames::contains)
    }
    require(orderedArgumentNames.toSet() == includedArgumentNames) {
        "Immutable annotation contains arguments outside its frozen declaration: ${type.value}"
    }
    val namedArguments = orderedArgumentNames.map { name ->
        LsiSourceAnnotationArgument.Named(
            name = name,
            value = arguments.getValue(name).value.toImmutableDtoPoetValue(
                declarationsByTypeId = declarationsByTypeId,
                targetLanguage = targetLanguage,
                includeDefaultArguments = includeDefaultArguments,
            ),
        )
    }
    val soleValue = namedArguments.singleOrNull()
        ?.takeIf { argument -> argument.name == "value" }
    val soleValueArray = soleValue?.value as? LsiAnnotationValue.ArrayValue
    val soleArgumentArray = namedArguments.singleOrNull()?.value as? LsiAnnotationValue.ArrayValue
    val renderedArguments = when {
        targetLanguage == LsiLanguage.KOTLIN &&
            declaration.kotlinValueVararg &&
            soleValueArray != null -> {
            soleValueArray.toPositionalVarargArguments()
        }
        soleValueArray?.elements?.size == 1 -> {
            val element = soleValueArray.elements.single()
            if (
                targetLanguage == LsiLanguage.KOTLIN &&
                (!includeDefaultArguments || declaration.language == LsiLanguage.KOTLIN)
            ) {
                listOf(LsiSourceAnnotationArgument.Positional(element))
            } else {
                listOf(
                    soleValue.copy(
                        value = element,
                        nameStyle = if (
                            includeDefaultArguments &&
                            targetLanguage == LsiLanguage.KOTLIN &&
                            declaration.language == LsiLanguage.JAVA
                        ) {
                            LsiAnnotationArgumentNameStyle.VERBATIM
                        } else {
                            soleValue.nameStyle
                        },
                    )
                )
            }
        }
        targetLanguage == LsiLanguage.KOTLIN && soleArgumentArray?.elements?.size == 1 -> {
            listOf(LsiSourceAnnotationArgument.Positional(soleArgumentArray.elements.single()))
        }
        else -> namedArguments
    }
    return sourceLsiAnnotation(
        type = type,
        arguments = renderedArguments,
    )
}

private fun LsiAnnotationValue.toImmutableDtoPoetValue(
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
    includeDefaultArguments: Boolean,
): LsiAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiAnnotationValue.StringValue(value)
        is LsiAnnotationValue.EnumValue -> LsiAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> LsiAnnotationValue.ClassValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> LsiAnnotationValue.NestedAnnotationValue(
            annotation.toImmutableDtoPoetAnnotation(
                declaration = declarationsByTypeId.getValue(annotation.type),
                declarationsByTypeId = declarationsByTypeId,
                targetLanguage = targetLanguage,
                includeDefaultArguments = includeDefaultArguments,
            ),
        )
        is LsiAnnotationValue.ArrayValue -> LsiAnnotationValue.ArrayValue(
            elements = elements.map { element ->
                element.toImmutableDtoPoetValue(
                    declarationsByTypeId,
                    targetLanguage,
                    includeDefaultArguments,
                )
            },
            sourceStyle = if (targetLanguage == LsiLanguage.KOTLIN) {
                LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF
            } else {
                LsiAnnotationArrayStyle.LITERAL
            },
        )
    }
}

private fun LsiAnnotationValue.toPositionalVarargArguments(): List<LsiSourceAnnotationArgument> {
    val values = (this as? LsiAnnotationValue.ArrayValue)?.elements ?: listOf(this)
    return values.map(LsiSourceAnnotationArgument::Positional)
}

private fun LsiAnnotationValue.toDtoLiteralPoetValue(): LsiAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiAnnotationValue.StringValue(value)
        is LsiAnnotationValue.ArrayValue,
        is LsiAnnotationValue.ClassValue,
        is LsiAnnotationValue.EnumValue,
        is LsiAnnotationValue.NestedAnnotationValue,
        -> error("DTO literal annotation source froze to a non-literal value")
    }
}

private fun LsiLanguage.requireDtoAnnotationTargetLanguage() {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO annotation rendering requires Java or Kotlin target language"
    }
}
