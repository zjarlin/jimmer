package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Constants
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

/**
 * 返回 Specification 的 like/notLike 谓词需要追加的匹配参数。
 *
 * 参数顺序固定为忽略大小写、匹配开头、匹配结尾；其余谓词返回空。
 */
fun DtoBaseProp.specificationLikeOptionArguments(graph: DtoGraph): List<Boolean>? {
    val tailProp = specificationTailProp(graph)
    if (tailProp.functionName != "like" && tailProp.functionName != "notLike") {
        return null
    }
    return listOf(
        DtoLikeOption.INSENSITIVE in tailProp.likeOptions,
        DtoLikeOption.MATCH_START in tailProp.likeOptions,
        DtoLikeOption.MATCH_END in tailProp.likeOptions,
    )
}

/** 返回 Specification 谓词调用使用的规范操作名。 */
fun DtoBaseProp.specificationOperationName(graph: DtoGraph): String {
    return when (val predicateName = specificationPredicateName(graph)) {
        "null" -> "isNull"
        "notNull" -> "isNotNull"
        else -> predicateName
    }
}

/** 返回 Specification converter 函数的生成名称。 */
fun DtoBaseProp.specificationConverterName(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
): String {
    specificationTailProp(graph)
    val prefix = when (targetLanguage) {
        LsiLanguage.JAVA -> "__convert"
        LsiLanguage.KOTLIN -> "_convert"
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "DTO specification converter name requires Java or Kotlin target language",
        )
    }
    return dtoIdentifier(prefix, name)
}

/** 返回 Specification converter 接收的 DTO 值类型。 */
fun DtoBaseProp.specificationConverterInputType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    val tailProp = specificationTailProp(graph)
    require(requiresSpecificationConverter(graph, immutableSchema)) {
        "DTO specification property does not require a converter: ${id.value}"
    }
    val valueType = enumType?.scalarType(language) ?: when (tailProp.functionName) {
        "valueIn", "valueNotIn" -> specificationCollectionType(
            language,
            requireNotNull(dtoConverterTargetTypeOrNull(graph, immutableSchema)) {
                "DTO specification value collection has no converter target type: ${id.value}"
            },
        )
        "id", "associatedIdEq", "associatedIdNe" ->
            tailProp.dtoAssociatedIdClientType(graph, immutableSchema)
        "associatedIdIn", "associatedIdNotIn" -> specificationCollectionType(
            language,
            tailProp.dtoAssociatedIdClientType(graph, immutableSchema),
        )
        else -> requireNotNull(dtoConverterTargetTypeOrNull(graph, immutableSchema)) {
            "DTO specification property has no converter target type: ${id.value}"
        }
    }
    return valueType.toSpecificationTargetType(language)
        .withSpecificationRootNullability(nullable)
        .withSpecificationJavaBoxing(language, force = false)
}

/** 返回 Specification converter 产出的不可变属性值类型。 */
fun DtoBaseProp.specificationConverterOutputType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    val tailProp = specificationTailProp(graph)
    require(requiresSpecificationConverter(graph, immutableSchema)) {
        "DTO specification property does not require a converter: ${id.value}"
    }
    val immutableProp = tailProp.boundImmutableProp(graph, immutableSchema)
    val valueType = when (tailProp.functionName) {
        "id", "associatedIdEq", "associatedIdNe" ->
            immutableSchema.requireSpecificationTargetIdProp(immutableProp).type
        "null", "notNull" -> LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
        "valueIn", "valueNotIn" -> specificationListType(language, immutableProp.type)
        "associatedIdIn", "associatedIdNotIn" -> specificationListType(
            language,
            immutableSchema.requireSpecificationTargetIdProp(immutableProp).type,
        )
        else -> immutableProp.type
    }
    return valueType.toSpecificationTargetType(language)
        .withSpecificationRootNullability(nullable)
        .withSpecificationJavaBoxing(language, force = true)
}

/** 判断 Specification 谓词是否使用属性数组参数。 */
fun DtoBaseProp.usesSpecificationPropArrayArgument(graph: DtoGraph): Boolean {
    return Constants.MULTI_ARGS_FUNC_NAMES.contains(specificationPredicateName(graph))
}

/** 返回 Specification 谓词参数引用的不可变属性，顺序与 DTO 绑定一致。 */
fun DtoBaseProp.specificationArgumentProps(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): List<ImmutableProp> {
    val tailProp = specificationTailProp(graph)
    val bindings = if (usesSpecificationPropArrayArgument(graph)) {
        tailProp.baseProps
    } else {
        listOf(tailProp.baseProps.first())
    }
    return bindings.map { binding ->
        requireNotNull(immutableSchema.propsById[binding.propId]) {
            "DTO specification property references a missing immutable property: ${binding.propId.value}"
        }
    }
}

/** 返回 Specification 属性在谓词 applier 中需要压入的不可变属性路径。 */
fun DtoBaseProp.specificationPath(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): List<ImmutableProp> {
    val tailProp = specificationTailProp(graph)
    val path = mutableListOf<ImmutableProp>()
    val visited = mutableSetOf<DtoPropId>()
    var current = this
    while (true) {
        require(visited.add(current.id)) {
            "DTO specification property path contains a cycle: ${id.value}"
        }
        if (current.id != tailProp.id || current.hasTarget()) {
            val binding = current.baseProps.first()
            path += requireNotNull(immutableSchema.propsById[binding.propId]) {
                "DTO specification path references a missing immutable property: ${binding.propId.value}"
            }
        }
        if (current.id == tailProp.id) {
            return path
        }
        current = requireNotNull(current.nextProp(graph)) {
            "DTO specification tail property is unreachable from '${id.value}': ${tailProp.id.value}"
        }
    }
}

/** 判断 Specification 属性尾部是否指向另一个 Specification。 */
fun DtoBaseProp.hasSpecificationTarget(graph: DtoGraph): Boolean {
    return specificationTailProp(graph).hasTarget()
}

/** 判断 Specification 目标是否通过实体关联进入子查询参数。 */
fun DtoBaseProp.specificationTargetIsEntityAssociation(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    val tailProp = specificationTailProp(graph)
    require(tailProp.hasTarget()) {
        "DTO specification property does not have a target: ${id.value}"
    }
    val decisions = tailProp.baseProps.map { binding ->
        val immutableProp = requireNotNull(immutableSchema.propsById[binding.propId]) {
            "DTO specification target references a missing immutable property: ${binding.propId.value}"
        }
        immutableSchema.isEntityAssociation(immutableProp)
    }.distinct()
    require(decisions.size == 1) {
        "DTO specification target bindings have inconsistent association semantics: ${id.value}"
    }
    return decisions.single()
}

/** 判断 Specification 属性是否需要先把 DTO 值转换为不可变属性值。 */
fun DtoBaseProp.requiresSpecificationConverter(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    specificationTailProp(graph)
    return enumType != null || dtoConverterTargetTypeOrNull(graph, immutableSchema) != null
}

private fun DtoBaseProp.specificationTailProp(graph: DtoGraph): DtoBaseProp {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(DtoModifier.SPECIFICATION in ownerType.modifiers) {
        "DTO property does not belong to a specification: ${id.value}"
    }
    return tailProp(graph)
}

private fun DtoBaseProp.specificationPredicateName(graph: DtoGraph): String {
    val tailProp = specificationTailProp(graph)
    require(!tailProp.hasTarget()) {
        "DTO specification target property does not have a predicate operation: ${id.value}"
    }
    return when (tailProp.functionName) {
        null -> "eq"
        "id" -> "associatedIdEq"
        else -> tailProp.functionName
    }
}

private fun DtoBaseProp.hasTarget(): Boolean {
    return targetTypeId != null || targetTypeReference != null
}

private fun ImmutableSchema.requireSpecificationTargetIdProp(prop: ImmutableProp): ImmutableProp {
    return requireNotNull(targetIdPropOf(prop)) {
        "DTO specification associated-id converter requires an entity association: ${prop.id.value}"
    }
}

private fun specificationCollectionType(
    targetLanguage: LsiLanguage,
    elementType: LsiTypeRef,
): LsiDeclaredType {
    return specificationContainerType(targetLanguage, collectionTypeId(targetLanguage), elementType)
}

private fun specificationListType(
    targetLanguage: LsiLanguage,
    elementType: LsiTypeRef,
): LsiDeclaredType {
    return specificationContainerType(targetLanguage, listTypeId(targetLanguage), elementType)
}

private fun specificationContainerType(
    targetLanguage: LsiLanguage,
    typeId: LsiSymbolId,
    elementType: LsiTypeRef,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = typeId,
        arguments = listOf(
            LsiTypeArgument.invariant(
                elementType
                    .toSpecificationTargetType(targetLanguage)
                    .boxedForTypeArgument(targetLanguage),
            ),
        ),
    )
}

private fun collectionTypeId(targetLanguage: LsiLanguage) = when (targetLanguage) {
    LsiLanguage.JAVA -> JAVA_COLLECTION_TYPE_ID
    LsiLanguage.KOTLIN -> KOTLIN_COLLECTION_TYPE_ID
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun listTypeId(targetLanguage: LsiLanguage) = when (targetLanguage) {
    LsiLanguage.JAVA -> JAVA_LIST_TYPE_ID
    LsiLanguage.KOTLIN -> KOTLIN_LIST_TYPE_ID
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun LsiTypeRef.toSpecificationTargetType(targetLanguage: LsiLanguage): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            declarationId = declarationId.toSpecificationTargetTypeId(targetLanguage),
            arguments = arguments.map { argument ->
                argument.copy(
                    type = argument.type
                        ?.toSpecificationTargetType(targetLanguage)
                        ?.boxedForTypeArgument(targetLanguage),
                )
            },
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(
            boxed = targetLanguage == LsiLanguage.JAVA &&
                (boxed || nullability == LsiNullability.NULLABLE),
            annotations = emptyList(),
        )
        is LsiArrayType -> copy(
            elementType = elementType.toSpecificationTargetType(targetLanguage),
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toSpecificationTargetType(targetLanguage),
            receiverType = receiverType?.toSpecificationTargetType(targetLanguage),
            parameterTypes = parameterTypes.map { type -> type.toSpecificationTargetType(targetLanguage) },
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

private fun LsiSymbolId.toSpecificationTargetTypeId(
    targetLanguage: LsiLanguage,
): LsiSymbolId {
    val qualifiedName = requireTypeQualifiedName()
    val targetName = when (targetLanguage) {
        LsiLanguage.JAVA -> JAVA_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.KOTLIN -> KOTLIN_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
    return targetName?.let(LsiSymbolId::type) ?: this
}

private fun LsiTypeRef.withSpecificationRootNullability(nullable: Boolean): LsiTypeRef {
    val nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
    return when (this) {
        is LsiDeclaredType -> copy(nullability = nullability)
        is LsiTypeParameterRef -> copy(nullability = nullability)
        is LsiPrimitiveType -> copy(nullability = nullability)
        is LsiArrayType -> copy(nullability = nullability)
        is LsiFunctionType -> copy(nullability = nullability)
        is LsiUnresolvedType -> copy(nullability = nullability)
    }
}

private fun LsiTypeRef.withSpecificationJavaBoxing(
    targetLanguage: LsiLanguage,
    force: Boolean,
): LsiTypeRef {
    if (targetLanguage != LsiLanguage.JAVA || this !is LsiPrimitiveType) {
        return this
    }
    return copy(boxed = boxed || force || nullability == LsiNullability.NULLABLE)
}

private val JAVA_COLLECTION_TYPE_ID = LsiSymbolId.type("java.util.Collection")
private val KOTLIN_COLLECTION_TYPE_ID = LsiSymbolId.type("kotlin.collections.Collection")
