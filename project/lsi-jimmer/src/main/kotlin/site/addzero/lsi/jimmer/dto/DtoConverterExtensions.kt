package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

/** 返回不可变属性暴露给 DTO 的冻结客户端类型。 */
fun ImmutableProp.dtoClientType(immutableSchema: ImmutableSchema): LsiTypeRef {
    declaredConverterTargetTypeOrNull()?.let { targetType -> return targetType }
    val idView = view as? ImmutableView.Id ?: return type.withoutDtoTypeAnnotations()
    val targetIdProp = idView.targetIdPropId
        ?.let(immutableSchema.propsById::get)
        ?: immutableSchema.propsById[idView.basePropId]
            ?.let(immutableSchema::targetIdPropOf)
    val targetType = targetIdProp?.declaredConverterTargetTypeOrNull()
        ?: return type.withoutDtoTypeAnnotations()
    return if (list) targetType.toDtoConverterListType() else targetType
}

/** 判断两个不可变属性暴露给 DTO 的冻结客户端类型是否一致。 */
fun ImmutableSchema.haveSameDtoClientType(
    firstOwnerTypeQualifiedName: String,
    firstPropName: String,
    secondOwnerTypeQualifiedName: String,
    secondPropName: String,
): Boolean {
    val firstProp = requireDtoClientProp(firstOwnerTypeQualifiedName, firstPropName)
    val secondProp = requireDtoClientProp(secondOwnerTypeQualifiedName, secondPropName)
    return firstProp.dtoClientType(this).jimmerTypeSignature(ignoreRootNullability = true) ==
        secondProp.dtoClientType(this).jimmerTypeSignature(ignoreRootNullability = true)
}

/** 返回 DTO 基础属性绑定的唯一不可变属性语义。 */
fun DtoBaseProp.boundImmutableProp(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): ImmutableProp {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val immutableProps = boundImmutableProps(immutableSchema)
    val clientTypeSignatures = immutableProps
        .map { prop -> prop.dtoClientType(immutableSchema).jimmerTypeSignature(ignoreRootNullability = true) }
        .distinct()
    require(clientTypeSignatures.size == 1) {
        "DTO base property bindings must expose one value type: ${id.value}"
    }
    return immutableProps.first()
}

/** 返回 DTO 属性实际需要的 converter 目标类型；无需 converter 时返回空。 */
fun DtoBaseProp.dtoConverterTargetTypeOrNull(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiTypeRef? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val specification = DtoModifier.SPECIFICATION in graph.typesById.getValue(ownerTypeId).modifiers
    val tailProp = tailProp(graph)
    val targetTypes = tailProp.boundImmutableProps(immutableSchema)
        .map { immutableProp ->
            immutableProp.dtoConverterTargetTypeOrNull(
                functionName = tailProp.functionName,
                specification = specification,
                immutableSchema = immutableSchema,
            )
        }
    val signatures = targetTypes
        .map { type -> type?.jimmerTypeSignature(ignoreRootNullability = true) }
        .distinct()
    require(signatures.size == 1) {
        "DTO base property bindings must expose one converter target type: ${id.value}"
    }
    return targetTypes.first()
}

/** 返回 DTO 普通属性或 id 函数在源码中使用的冻结客户端类型。 */
fun DtoBaseProp.dtoClientType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiTypeRef {
    val tailProp = tailProp(graph)
    return if (tailProp.functionName == "id") {
        tailProp.dtoAssociatedIdClientType(graph, immutableSchema)
    } else {
        tailProp.boundImmutableProp(graph, immutableSchema).dtoClientType(immutableSchema)
    }
}

/** 返回关联目标主键暴露给 DTO 的冻结客户端类型。 */
fun DtoBaseProp.dtoAssociatedIdClientType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiTypeRef {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val tailProp = tailProp(graph)
    val clientTypes = tailProp.boundImmutableProps(immutableSchema).map { immutableProp ->
        val targetIdProp = requireNotNull(immutableSchema.targetIdPropOf(immutableProp)) {
            "DTO associated id function must reference an immutable association: ${tailProp.id.value}"
        }
        targetIdProp.dtoClientType(immutableSchema)
    }
    val signatures = clientTypes
        .map { type -> type.jimmerTypeSignature(ignoreRootNullability = true) }
        .distinct()
    require(signatures.size == 1) {
        "DTO base property bindings must expose one associated id type: ${id.value}"
    }
    return clientTypes.first()
}

private fun DtoBaseProp.boundImmutableProps(immutableSchema: ImmutableSchema): List<ImmutableProp> {
    return baseProps
        .map(DtoBasePropBinding::propId)
        .distinct()
        .map { propId ->
            requireNotNull(immutableSchema.propsById[propId]) {
                "DTO base property references a missing immutable property: ${propId.value}"
            }
        }
}

private fun ImmutableSchema.requireDtoClientProp(
    ownerTypeQualifiedName: String,
    propName: String,
): ImmutableProp {
    val propId = LsiSymbolId.property(LsiSymbolId.type(ownerTypeQualifiedName), propName)
    return requireNotNull(propsById[propId]) {
        "Immutable DTO client property does not exist: ${propId.value}"
    }
}

private fun ImmutableProp.dtoConverterTargetTypeOrNull(
    functionName: String?,
    specification: Boolean,
    immutableSchema: ImmutableSchema,
): LsiTypeRef? {
    if (functionName == "null" || functionName == "notNull") {
        return null
    }
    declaredConverterTargetTypeOrNull()?.let { targetType -> return targetType }
    if (functionName == "id") {
        val targetType = immutableSchema.targetIdPropOf(this)
            ?.declaredConverterTargetTypeOrNull()
            ?: return null
        return if (list && !specification) targetType.toDtoConverterListType() else targetType
    }
    if (functionName == "associatedIdEq" || functionName == "associatedIdNe") {
        return immutableSchema.targetIdPropOf(this)?.declaredConverterTargetTypeOrNull()
    }
    if (functionName == "associatedIdIn" || functionName == "associatedIdNotIn") {
        return immutableSchema.targetIdPropOf(this)
            ?.declaredConverterTargetTypeOrNull()
            ?.toDtoConverterListType()
    }
    val idView = view as? ImmutableView.Id ?: return null
    val targetType = idView.targetIdPropId
        ?.let(immutableSchema.propsById::get)
        ?.declaredConverterTargetTypeOrNull()
        ?: immutableSchema.propsById[idView.basePropId]
            ?.let(immutableSchema::targetIdPropOf)
            ?.declaredConverterTargetTypeOrNull()
        ?: return null
    return if (list) targetType.toDtoConverterListType() else targetType
}

private fun ImmutableProp.declaredConverterTargetTypeOrNull(): LsiTypeRef? {
    val converter = converter ?: return null
    return requireNotNull(converter.targetType) {
        "Immutable converter has no target type: ${converter.converterTypeId.value}"
    }.withoutDtoTypeAnnotations()
}

private fun LsiTypeRef.toDtoConverterListType(): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = JAVA_LIST_TYPE_ID,
        arguments = listOf(LsiTypeArgument.invariant(this)),
    )
}

private fun LsiTypeRef.withoutDtoTypeAnnotations(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.withoutDtoTypeAnnotations())
            },
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiPrimitiveType -> copy(annotations = emptyList())
        is LsiArrayType -> copy(
            elementType = elementType.withoutDtoTypeAnnotations(),
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.withoutDtoTypeAnnotations(),
            receiverType = receiverType?.withoutDtoTypeAnnotations(),
            parameterTypes = parameterTypes.map(LsiTypeRef::withoutDtoTypeAnnotations),
            annotations = emptyList(),
        )
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}
