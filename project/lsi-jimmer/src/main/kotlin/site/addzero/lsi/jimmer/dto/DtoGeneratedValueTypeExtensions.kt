package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef

/**
 * 返回 DTO 属性最终写入生成声明的值类型。
 *
 * 匿名、命名、可复用、递归及多态提升目标的源码 occurrence 由调用方显式解析。
 */
fun DtoProp.generatedValueType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): LsiTypeRef {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    val language = targetLanguage.requireDtoTargetLanguage()
    val valueType = when (this) {
        is DtoBaseProp -> generatedBaseValueType(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = language,
            generatedTargetType = generatedTargetType,
        )
        is DtoFoldProp -> generatedTargetType(this)
        is DtoUserProp -> type.toLsiType(language)
    }
    val nullable = nullable ||
        (this is DtoFoldProp && DtoModifier.SPECIFICATION in ownerType.modifiers)
    return valueType.toDtoTargetType(language)
        .withDtoRootNullability(nullable)
        .withDtoJavaBoxing(language, force = false)
}

/**
 * 返回基础属性写入 DTO 访问器泛型位置的元素类型。
 *
 * 该类型不携带 DTO 属性自身的根可空性；Java primitive 会在属性可空时装箱。
 */
fun DtoBaseProp.generatedElementValueType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): LsiTypeRef {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val language = targetLanguage.requireDtoTargetLanguage()
    val tailProp = tailProp(graph)
    val valueType = if (tailProp.hasGeneratedValueTarget()) {
        generatedTargetType(this)
    } else {
        dtoClientType(graph, immutableSchema)
    }
    return valueType.toDtoTargetType(language)
        .withDtoRootNullability(nullable = false)
        .withDtoJavaBoxing(language, force = nullable)
}

private fun DtoBaseProp.generatedBaseValueType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): LsiTypeRef {
    val tailProp = tailProp(graph)
    val immutableProp = tailProp.boundImmutableProp(graph, immutableSchema)
    enumType?.let { enumType -> return enumType.scalarType(targetLanguage) }
    val converterTargetType = dtoConverterTargetTypeOrNull(graph, immutableSchema)
    val elementType = generatedElementValueType(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
        generatedTargetType = generatedTargetType,
    )
    val ownerType = graph.typesById.getValue(ownerTypeId)
    if (DtoModifier.SPECIFICATION in ownerType.modifiers) {
        when (tailProp.functionName) {
            "null", "notNull" -> return LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
            "valueIn", "valueNotIn" -> {
                val argumentType = converterTargetType ?: if (immutableProp.list) {
                    elementType.toDtoListType(targetLanguage)
                } else {
                    elementType
                }
                return argumentType.toDtoCollectionType(targetLanguage)
            }
            "id", "associatedIdEq", "associatedIdNe" ->
                return tailProp.dtoAssociatedIdClientType(graph, immutableSchema)
            "associatedIdIn", "associatedIdNotIn" ->
                return tailProp
                    .dtoAssociatedIdClientType(graph, immutableSchema)
                    .toDtoCollectionType(targetLanguage)
        }
        if (immutableSchema.isEntityAssociation(immutableProp)) {
            return elementType
        }
    }
    converterTargetType?.let { return it }
    val normalizedElementType = elementType.toDtoTargetType(targetLanguage)
    if (!immutableProp.list || normalizedElementType.isDtoListType()) {
        return normalizedElementType
    }
    return normalizedElementType.toDtoListType(targetLanguage)
}

private fun DtoBaseProp.hasGeneratedValueTarget(): Boolean {
    return targetTypeId != null || targetTypeReference != null
}
