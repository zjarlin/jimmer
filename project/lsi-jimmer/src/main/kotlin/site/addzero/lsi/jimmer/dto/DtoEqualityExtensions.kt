package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef

/** DTO 属性值参与相等性与哈希计算时采用的语义。 */
enum class DtoValueEqualityKind {
    VALUE,
    ARRAY_CONTENT,
}

/** 返回属性值应按普通值还是数组内容参与相等性与哈希计算。 */
fun DtoProp.valueEqualityKind(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoValueEqualityKind {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    return when (this) {
        is DtoFoldProp -> DtoValueEqualityKind.VALUE
        is DtoUserProp -> if (type.typeName in DTO_ARRAY_TYPE_NAMES) {
            DtoValueEqualityKind.ARRAY_CONTENT
        } else {
            type.toLsiType(LsiLanguage.KOTLIN).toDtoValueEqualityKind()
        }
        is DtoBaseProp -> baseValueEqualityKind(graph, immutableSchema)
    }
}

private fun DtoBaseProp.baseValueEqualityKind(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoValueEqualityKind {
    val tailProp = tailProp(graph)
    if (
        enumType != null ||
        targetTypeId != null ||
        targetTypeReference != null ||
        tailProp.targetTypeId != null ||
        tailProp.targetTypeReference != null
    ) {
        return DtoValueEqualityKind.VALUE
    }
    if (tailProp.functionName in SINGLE_ASSOCIATED_ID_FUNCTIONS) {
        return tailProp.associatedIdValueEqualityKind(graph, immutableSchema)
    }
    if (tailProp.functionName in VALUE_ONLY_FUNCTIONS) {
        return DtoValueEqualityKind.VALUE
    }
    val kinds = tailProp.baseProps.map { binding ->
        val immutableProp = requireNotNull(immutableSchema.propsById[binding.propId]) {
            "DTO property references a missing immutable property: ${binding.propId.value}"
        }
        (immutableProp.converter?.targetType ?: immutableProp.type).toDtoValueEqualityKind()
    }.distinct()
    require(kinds.size == 1) {
        "DTO property bindings must expose one equality kind: ${id.value}"
    }
    return kinds.single()
}

private fun DtoBaseProp.associatedIdValueEqualityKind(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoValueEqualityKind {
    val specification = DtoModifier.SPECIFICATION in graph.typesById.getValue(ownerTypeId).modifiers
    val associations = baseProps.map { binding ->
        requireNotNull(immutableSchema.propsById[binding.propId]) {
            "DTO associated-id property references a missing immutable property: ${binding.propId.value}"
        }
    }
    val idClientTypes = associations.map { association ->
        val targetIdProp = requireNotNull(immutableSchema.targetIdPropOf(association)) {
            "DTO associated-id property must reference a concrete entity association: ${association.id.value}"
        }
        targetIdProp.converter?.targetType ?: targetIdProp.elementTypeOrSelf()
    }
    val typeSignatures = idClientTypes
        .map { type -> type.jimmerTypeSignature(ignoreRootNullability = true) }
        .distinct()
    require(typeSignatures.size == 1) {
        "DTO associated-id bindings must expose one client type: ${id.value}"
    }
    val associationListKinds = associations.map { association -> association.list }.distinct()
    require(associationListKinds.size == 1) {
        "DTO associated-id bindings must use one association cardinality: ${id.value}"
    }
    if (!specification && functionName in ID_FUNCTIONS && associationListKinds.single()) {
        return DtoValueEqualityKind.VALUE
    }
    return idClientTypes.first().toDtoValueEqualityKind()
}

private fun LsiTypeRef.toDtoValueEqualityKind(): DtoValueEqualityKind {
    return if (
        this is LsiArrayType ||
        this is LsiDeclaredType && declarationId == KOTLIN_ARRAY_TYPE_ID
    ) {
        DtoValueEqualityKind.ARRAY_CONTENT
    } else {
        DtoValueEqualityKind.VALUE
    }
}

private val VALUE_ONLY_FUNCTIONS = setOf(
    "null",
    "notNull",
    "valueIn",
    "valueNotIn",
    "associatedIdIn",
    "associatedIdNotIn",
)

private val ID_FUNCTIONS = setOf("id", "associatedIdEq")

private val SINGLE_ASSOCIATED_ID_FUNCTIONS = ID_FUNCTIONS + "associatedIdNe"

private val DTO_ARRAY_TYPE_NAMES = setOf("Array", "kotlin.Array")

private val KOTLIN_ARRAY_TYPE_ID = LsiSymbolId.type("kotlin.Array")
