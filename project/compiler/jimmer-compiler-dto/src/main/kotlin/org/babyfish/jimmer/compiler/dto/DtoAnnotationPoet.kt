package org.babyfish.jimmer.compiler.dto

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
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentNameStyle
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetClassLiteralStyle
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 按冻结契约顺序将 DTO 类型注解降低为平台中立源码结构。 */
internal fun DtoType.typeAnnotationPoetAnnotations(
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
): List<LsiPoetAnnotation> {
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
): List<LsiPoetAnnotation> {
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
): List<LsiPoetAnnotation> {
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
    annotations: List<LsiPoetAnnotation>,
): List<LsiPoetTypeName> {
    val referencedTypeIds = annotations.flatMapTo(sortedSetOf()) { annotation ->
        annotation.referencedTypeIds
    }
    return toLsiPoetTypeNames(referencedTypeIds)
}

/** 将冻结的类型注解应用降低为平台中立源码结构。 */
internal fun DtoAnnotationApplication.toDtoPoetAnnotation(
    dtoSourceAnnotation: DtoAnnotation?,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    includeImmutableDefaultArguments: Boolean = false,
): LsiPoetAnnotation {
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
): LsiPoetAnnotation {
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
): LsiPoetAnnotation {
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
): LsiPoetAnnotation {
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
        LsiPoetAnnotationArgument.Named(
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
        nested && soleValue != null -> listOf(LsiPoetAnnotationArgument.Positional(soleValue.value))
        else -> namedArguments
    }
    return LsiPoetAnnotation(
        type = typeId,
        arguments = renderedArguments,
        argumentLayout = when {
            renderedArguments.isEmpty() || targetLanguage == LsiLanguage.JAVA -> {
                LsiPoetAnnotationArgumentLayout.PLATFORM_DEFAULT
            }
            nested && soleValue != null -> LsiPoetAnnotationArgumentLayout.SINGLE_LINE
            kotlinTopLevelValueVararg -> LsiPoetAnnotationArgumentLayout.SINGLE_LINE
            else -> LsiPoetAnnotationArgumentLayout.MULTI_LINE
        },
    )
}

private fun DtoAnnotationValue.toDtoPoetAnnotationValue(
    frozen: LsiAnnotationValue,
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
): LsiPoetAnnotationValue {
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
            LsiPoetAnnotationValue.ArrayValue(
                elements = elements.zip(frozen.elements) { sourceElement, frozenElement ->
                    sourceElement.toDtoPoetAnnotationValue(
                        frozen = frozenElement,
                        declarationsByTypeId = declarationsByTypeId,
                        targetLanguage = targetLanguage,
                    )
                },
                sourceStyle = LsiPoetAnnotationArrayStyle.MULTI_LINE_LITERAL,
            )
        }
        is DtoAnnotationValue.AnnotationValue -> {
            require(frozen is LsiAnnotationValue.NestedAnnotationValue) {
                "DTO nested annotation source does not match its frozen value"
            }
            LsiPoetAnnotationValue.NestedAnnotationValue(
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
            LsiPoetAnnotationValue.EnumValue(frozen.enumType, frozen.entryName)
        }
        is DtoAnnotationValue.TypeValue -> {
            require(frozen is LsiAnnotationValue.ClassValue) {
                "DTO class annotation source does not match its frozen value"
            }
            LsiPoetAnnotationValue.ClassValue(
                type = frozen.type,
                sourceStyle = if (
                    targetLanguage == LsiLanguage.KOTLIN &&
                    (frozen.type as? LsiPrimitiveType)?.boxed == true
                ) {
                    LsiPoetClassLiteralStyle.JAVA_BOXED_PRIMITIVE_QUALIFIED
                } else {
                    LsiPoetClassLiteralStyle.PLATFORM_TYPE
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
): LsiPoetAnnotation {
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
        LsiPoetAnnotationArgument.Named(
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
    val soleValueArray = soleValue?.value as? LsiPoetAnnotationValue.ArrayValue
    val soleArgumentArray = namedArguments.singleOrNull()?.value as? LsiPoetAnnotationValue.ArrayValue
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
                listOf(LsiPoetAnnotationArgument.Positional(element))
            } else {
                listOf(
                    soleValue.copy(
                        value = element,
                        nameStyle = if (
                            includeDefaultArguments &&
                            targetLanguage == LsiLanguage.KOTLIN &&
                            declaration.language == LsiLanguage.JAVA
                        ) {
                            LsiPoetAnnotationArgumentNameStyle.VERBATIM
                        } else {
                            soleValue.nameStyle
                        },
                    )
                )
            }
        }
        targetLanguage == LsiLanguage.KOTLIN && soleArgumentArray?.elements?.size == 1 -> {
            listOf(LsiPoetAnnotationArgument.Positional(soleArgumentArray.elements.single()))
        }
        else -> namedArguments
    }
    return LsiPoetAnnotation(
        type = type,
        arguments = renderedArguments,
    )
}

private fun LsiAnnotationValue.toImmutableDtoPoetValue(
    declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
    targetLanguage: LsiLanguage,
    includeDefaultArguments: Boolean,
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
            annotation.toImmutableDtoPoetAnnotation(
                declaration = declarationsByTypeId.getValue(annotation.type),
                declarationsByTypeId = declarationsByTypeId,
                targetLanguage = targetLanguage,
                includeDefaultArguments = includeDefaultArguments,
            ),
        )
        is LsiAnnotationValue.ArrayValue -> LsiPoetAnnotationValue.ArrayValue(
            elements = elements.map { element ->
                element.toImmutableDtoPoetValue(
                    declarationsByTypeId,
                    targetLanguage,
                    includeDefaultArguments,
                )
            },
            sourceStyle = if (targetLanguage == LsiLanguage.KOTLIN) {
                LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF
            } else {
                LsiPoetAnnotationArrayStyle.LITERAL
            },
        )
    }
}

private fun LsiPoetAnnotationValue.toPositionalVarargArguments(): List<LsiPoetAnnotationArgument> {
    val values = (this as? LsiPoetAnnotationValue.ArrayValue)?.elements ?: listOf(this)
    return values.map(LsiPoetAnnotationArgument::Positional)
}

private fun LsiAnnotationValue.toDtoLiteralPoetValue(): LsiPoetAnnotationValue {
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
