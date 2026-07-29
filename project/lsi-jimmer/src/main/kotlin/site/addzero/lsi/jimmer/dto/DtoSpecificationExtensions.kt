package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Constants
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef

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
        "valueIn", "valueNotIn" -> requireNotNull(
            dtoConverterTargetTypeOrNull(graph, immutableSchema)
        ) {
            "DTO specification value collection has no converter target type: ${id.value}"
        }.toDtoCollectionType(language)
        "id", "associatedIdEq", "associatedIdNe" ->
            tailProp.dtoAssociatedIdClientType(graph, immutableSchema)
        "associatedIdIn", "associatedIdNotIn" ->
            tailProp.dtoAssociatedIdClientType(graph, immutableSchema).toDtoCollectionType(language)
        else -> requireNotNull(dtoConverterTargetTypeOrNull(graph, immutableSchema)) {
            "DTO specification property has no converter target type: ${id.value}"
        }
    }
    return valueType.toDtoTargetType(language)
        .withDtoRootNullability(nullable)
        .withDtoJavaBoxing(language, force = false)
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
        "valueIn", "valueNotIn" -> immutableProp.type.toDtoListType(language)
        "associatedIdIn", "associatedIdNotIn" ->
            immutableSchema.requireSpecificationTargetIdProp(immutableProp).type.toDtoListType(language)
        else -> immutableProp.type
    }
    return valueType.toDtoTargetType(language)
        .withDtoRootNullability(nullable)
        .withDtoJavaBoxing(language, force = true)
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
