package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

/** 按 DTO 声明顺序返回全部可见属性。 */
fun DtoType.propsInDeclarationOrder(graph: DtoGraph): List<DtoProp> {
    require(graph.typesById[id] == this) {
        "DTO type does not belong to this graph: ${id.value}"
    }
    return propIds.map(graph.propsById::getValue)
}

/** 按 DTO 声明顺序返回基础属性，排除用户属性、折叠属性和隐藏展开属性。 */
fun DtoType.basePropsInDeclarationOrder(graph: DtoGraph): List<DtoBaseProp> {
    return propsInDeclarationOrder(graph).filterIsInstance<DtoBaseProp>()
}

/** 按 DTO 声明顺序返回 Serializer 需要写出的属性。 */
fun DtoType.serializerPropsInDeclarationOrder(graph: DtoGraph): List<DtoBaseProp> {
    require(DtoModifier.INPUT in modifiers) {
        "DTO serializer properties require an input DTO type: ${id.value}"
    }
    return basePropsInDeclarationOrder(graph)
}

/** 判断输入 DTO 是否需要按加载状态执行动态序列化。 */
fun DtoType.requiresDynamicInputSerialization(graph: DtoGraph): Boolean {
    return polymorphism == null &&
        DtoModifier.INPUT in modifiers &&
        basePropsInDeclarationOrder(graph).any { prop -> prop.inputModifier == DtoModifier.DYNAMIC }
}

/** 判断输入 DTO 是否需要生成 Builder。 */
fun DtoType.requiresInputBuilder(graph: DtoGraph): Boolean {
    if (polymorphism != null || DtoModifier.INPUT !in modifiers) {
        return false
    }
    return basePropsInDeclarationOrder(graph).any { prop ->
        prop.inputModifier == DtoModifier.FIXED || prop.inputModifier == DtoModifier.DYNAMIC
    }
}

/** 判断 DTO 是否需要生成 Hibernate Validator 动态属性增强协议。 */
fun DtoType.requiresHibernateValidatorEnhancement(
    graph: DtoGraph,
    enhancementEnabled: Boolean,
): Boolean {
    val baseProps = basePropsInDeclarationOrder(graph)
    return enhancementEnabled && baseProps.any { prop ->
        prop.inputModifier == DtoModifier.DYNAMIC
    }
}

/** 判断 DTO 是否为嵌套在实体 Specification 中的非实体过滤片段。 */
fun DtoType.isNestedSpecificationFragment(
    immutableSchema: ImmutableSchema,
): Boolean {
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO semantic classification requires a base immutable type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
    return DtoModifier.SPECIFICATION in modifiers &&
        baseType.kind != ImmutableTypeKind.ENTITY
}

/** 返回动态输入属性对应的加载状态访问器名称。 */
fun DtoBaseProp.loadedAccessorName(): String {
    require(inputModifier == DtoModifier.DYNAMIC && nullable) {
        "DTO loaded accessor requires a dynamic input property: ${id.value}"
    }
    return dtoIdentifier("is", name, "Loaded")
}

/** 返回 DTO 本体的加载状态存储名；当前属性无需独立状态时返回空。 */
fun DtoProp.dtoLoadedStateStorageNameOrNull(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
): String? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    if (
        id !in ownerType.propIds ||
        DtoModifier.INPUT !in ownerType.modifiers ||
        !nullable ||
        (this as? DtoBaseProp)?.inputModifier != DtoModifier.DYNAMIC
    ) {
        return null
    }
    return when (targetLanguage) {
        LsiLanguage.JAVA -> dtoIdentifier("_is", name, "Loaded")
        LsiLanguage.KOTLIN -> loadedAccessorName()
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "DTO target language must be Java or Kotlin",
        )
    }
}

/** 返回 Serializer 的加载状态访问器；非动态属性返回空。 */
fun DtoBaseProp.serializerLoadedAccessorNameOrNull(): String? {
    return if (inputModifier == DtoModifier.DYNAMIC) loadedAccessorName() else null
}

/** 返回目标语言的输入 DTO Serializer 访问属性值时使用的成员名称。 */
fun DtoBaseProp.serializerValueAccessorName(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): String {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(DtoModifier.INPUT in ownerType.modifiers) {
        "DTO serializer value accessor requires an input DTO type: ${ownerTypeId.value}"
    }
    if (targetLanguage == LsiLanguage.KOTLIN) {
        return name
    }
    require(targetLanguage == LsiLanguage.JAVA) {
        "DTO value accessor requires Java or Kotlin target language"
    }
    val primitiveBoolean = hasPrimitiveBooleanValue(graph, immutableSchema)
    val suffix = if (
        primitiveBoolean &&
        name.startsWith("is") &&
        name.length > 2 &&
        name[2].isUpperCase()
    ) {
        name.substring(2)
    } else {
        name
    }
    return dtoIdentifier(if (primitiveBoolean) "is" else "get", suffix)
}

private fun DtoBaseProp.hasPrimitiveBooleanValue(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    if (nullable || enumType != null || targetTypeId != null) {
        return false
    }
    val tailProp = graph.propsById.getValue(tailPropId) as? DtoBaseProp
        ?: error("DTO tail property is not a base property: ${tailPropId.value}")
    if (tailProp.enumType != null || tailProp.targetTypeId != null) {
        return false
    }
    val primitiveBooleanValues = tailProp.baseProps.map { binding ->
        val immutableProp = immutableSchema.propsById.getValue(binding.propId)
        immutableProp.hasPrimitiveBooleanValue(tailProp.functionName, immutableSchema)
    }.distinct()
    require(primitiveBooleanValues.size == 1) {
        "DTO base properties must have consistent Java boolean accessor semantics: ${tailProp.id.value}"
    }
    return primitiveBooleanValues.single()
}

private fun ImmutableProp.hasPrimitiveBooleanValue(
    functionName: String?,
    immutableSchema: ImmutableSchema,
): Boolean {
    if (list || converter != null) {
        return false
    }
    val valueProp = when {
        functionName == "id" -> requireNotNull(immutableSchema.targetIdPropOf(this)) {
            "DTO id function must reference an immutable association: ${id.value}"
        }
        view is ImmutableView.Id -> {
            view.targetIdPropId?.let(immutableSchema.propsById::getValue) ?: this
        }
        else -> this
    }
    if (valueProp.list || valueProp.converter != null) {
        return false
    }
    val valueType = valueProp.type
    return valueType is LsiPrimitiveType &&
        valueType.kind == LsiPrimitiveKind.BOOLEAN &&
        !valueType.boxed &&
        valueType.nullability == LsiNullability.NON_NULL
}

internal fun dtoIdentifier(vararg parts: String): String = buildString {
    var previousPartEndsWithLowerCase = false
    parts.forEach { part ->
        require(part.isNotEmpty()) { "DTO identifier part cannot be empty" }
        when {
            previousPartEndsWithLowerCase && part.first().isUpperCase() -> append(part)
            previousPartEndsWithLowerCase -> {
                append(part.first().uppercaseChar())
                append(part, 1, part.length)
            }
            part.first().isLowerCase() -> append(part)
            else -> {
                val normalized = part.toCharArray()
                for (index in normalized.indices) {
                    if (normalized[index].isLowerCase()) {
                        break
                    }
                    normalized[index] = normalized[index].lowercaseChar()
                }
                append(normalized)
            }
        }
        previousPartEndsWithLowerCase = part.last().isLowerCase()
    }
}
