package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.generatedTableType
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef

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

/** 判断属性是否需要标记为固定输入字段。 */
fun DtoProp.requiresFixedInputField(graph: DtoGraph): Boolean {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    return this is DtoBaseProp &&
        DtoModifier.INPUT in ownerType.modifiers &&
        inputModifier == DtoModifier.FIXED
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

/** 返回 Specification 的冻结基础不可变类型。 */
fun DtoType.specificationBaseType(immutableSchema: ImmutableSchema): ImmutableType {
    require(DtoModifier.SPECIFICATION in modifiers) {
        "DTO type is not a specification: ${id.value}"
    }
    return immutableBaseType(immutableSchema)
}

/** 返回 DTO 声明绑定的冻结基础不可变类型。 */
fun DtoType.immutableBaseType(immutableSchema: ImmutableSchema): ImmutableType {
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO semantic classification requires a base immutable type: ${id.value}"
    }
    return requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
}

/** 判断 DTO 是否为输入类型。 */
fun DtoType.isInput(): Boolean {
    return DtoModifier.INPUT in modifiers
}

/** 判断 DTO 是否为 Specification。 */
fun DtoType.isSpecification(): Boolean {
    return DtoModifier.SPECIFICATION in modifiers
}

/** 判断 DTO 是否声明为密封多态根。 */
fun DtoType.isSealed(): Boolean {
    return DtoModifier.SEALED in modifiers
}

/** 判断 DTO 是否为多态根类型。 */
fun DtoType.isPolymorphicRoot(): Boolean {
    return polymorphism != null
}

/** 判断 DTO 是否绑定实体基础类型。 */
fun DtoType.hasEntityBase(immutableSchema: ImmutableSchema): Boolean {
    return immutableBaseType(immutableSchema).kind == ImmutableTypeKind.ENTITY
}

/** 判断 DTO 是否为嵌套在实体 Specification 中的非实体过滤片段。 */
fun DtoType.isNestedSpecificationFragment(
    immutableSchema: ImmutableSchema,
): Boolean {
    val baseType = immutableBaseType(immutableSchema)
    return DtoModifier.SPECIFICATION in modifiers && baseType.kind != ImmutableTypeKind.ENTITY
}

/** DTO 生成类需要实现的 Jimmer 基础契约。 */
enum class DtoGeneratedBaseContractKind {
    ENTITY_INPUT,
    ENTITY_VIEW,
    ENTITY_SPECIFICATION,
    EMBEDDABLE,
}

/** 返回 DTO 生成类需要实现的 Jimmer 基础契约；无需基础契约时返回空。 */
fun DtoType.generatedBaseContractKind(
    immutableSchema: ImmutableSchema,
): DtoGeneratedBaseContractKind? {
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO base contract resolution requires a base immutable type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
    if (DtoModifier.SPECIFICATION in modifiers && baseType.kind != ImmutableTypeKind.ENTITY) {
        return null
    }
    return when (baseType.kind) {
        ImmutableTypeKind.ENTITY -> when {
            polymorphism == null && DtoModifier.SPECIFICATION in modifiers ->
                DtoGeneratedBaseContractKind.ENTITY_SPECIFICATION
            DtoModifier.INPUT in modifiers -> DtoGeneratedBaseContractKind.ENTITY_INPUT
            else -> DtoGeneratedBaseContractKind.ENTITY_VIEW
        }
        ImmutableTypeKind.EMBEDDABLE -> DtoGeneratedBaseContractKind.EMBEDDABLE
        ImmutableTypeKind.IMMUTABLE,
        ImmutableTypeKind.MAPPED_SUPERCLASS,
        -> null
    }
}

/** 返回指定源码语言下 DTO 生成类需要实现的完整基础契约类型。 */
fun DtoType.generatedBaseContractType(
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiDeclaredType? {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO base contract type requires Java or Kotlin: $targetLanguage"
    }
    val kind = generatedBaseContractKind(immutableSchema) ?: return null
    val baseType = immutableBaseType(immutableSchema)
    val baseTypeRef = LsiDeclaredType(baseType.id)
    val rawTypeId = when (kind) {
        DtoGeneratedBaseContractKind.ENTITY_INPUT -> INPUT_CONTRACT_TYPE_ID
        DtoGeneratedBaseContractKind.ENTITY_VIEW -> VIEW_CONTRACT_TYPE_ID
        DtoGeneratedBaseContractKind.ENTITY_SPECIFICATION -> when (targetLanguage) {
            LsiLanguage.JAVA -> JAVA_SPECIFICATION_CONTRACT_TYPE_ID
            LsiLanguage.KOTLIN -> KOTLIN_SPECIFICATION_CONTRACT_TYPE_ID
            LsiLanguage.UNKNOWN -> error("Unreachable")
        }
        DtoGeneratedBaseContractKind.EMBEDDABLE -> EMBEDDABLE_DTO_CONTRACT_TYPE_ID
    }
    val arguments = buildList {
        add(LsiTypeArgument.invariant(baseTypeRef))
        if (
            kind == DtoGeneratedBaseContractKind.ENTITY_SPECIFICATION &&
            targetLanguage == LsiLanguage.JAVA
        ) {
            add(LsiTypeArgument.invariant(baseType.generatedTableType()))
        }
    }
    return LsiDeclaredType(rawTypeId, arguments)
}

/** 判断 DTO 是否为需要生成多态输入注解的实体根类型。 */
fun DtoType.isPolymorphicInputRoot(
    immutableSchema: ImmutableSchema,
): Boolean {
    if (DtoModifier.INPUT !in modifiers || polymorphism == null) {
        return false
    }
    val baseTypeId = requireNotNull(baseTypeId) {
        "Polymorphic input DTO requires a base immutable type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
    return baseType.kind == ImmutableTypeKind.ENTITY
}

/** 返回 DTO 基础实体所在继承根的判别属性名；实体没有继承时返回空。 */
fun DtoType.polymorphicRootDiscriminatorPropNameOrNull(
    immutableSchema: ImmutableSchema,
): String? {
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO discriminator resolution requires a base immutable type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
    val rootTypeId = baseType.inheritanceRootTypeId ?: return null
    val rootType = requireNotNull(immutableSchema.typesById[rootTypeId]) {
        "No immutable inheritance root '${rootTypeId.value}' for DTO type: ${id.value}"
    }
    val discriminatorPropId = requireNotNull(rootType.discriminatorPropId) {
        "Immutable inheritance root has no discriminator property: ${rootType.id.value}"
    }
    return requireNotNull(immutableSchema.propsById[discriminatorPropId]) {
        "No immutable discriminator property '${discriminatorPropId.value}' for DTO type: ${id.value}"
    }.name
}

/** 返回多态输入 DTO 显式选择的判别属性；未选择时返回空。 */
fun DtoType.selectedPolymorphicInputDiscriminatorPropOrNull(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoBaseProp? {
    if (DtoModifier.INPUT !in modifiers) {
        return null
    }
    val baseTypeId = requireNotNull(baseTypeId) {
        "Polymorphic input discriminator resolution requires a base immutable type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO type: ${id.value}"
    }
    if (baseType.kind != ImmutableTypeKind.ENTITY || baseType.inheritanceRootTypeId == null) {
        return null
    }
    var result: DtoBaseProp? = null
    for (prop in basePropsInDeclarationOrder(graph)) {
        if (prop.nextPropId != null) {
            continue
        }
        val basePropId = prop.baseProps.first().propId
        val immutableProp = requireNotNull(immutableSchema.propsById[basePropId]) {
            "No immutable base property '${basePropId.value}' for DTO property '${prop.id.value}'"
        }
        if (immutableProp.primaryMapping != PrimaryMapping.DISCRIMINATOR) {
            continue
        }
        val previous = result
        require(previous == null || previous.name == prop.name) {
            "Discriminator property cannot be selected by polymorphic input DTO " +
                "\"${name ?: id.value}\" more than once"
        }
        result = prop
    }
    return result
}

/** DTO 属性访问器需要执行的值转换语义。 */
enum class DtoAccessorConversionKind {
    NONE,
    ASSOCIATED_ID,
    OBJECT_CONSTRUCTOR,
    OBJECT_METADATA,
    ENUM,
    CONVERTER,
}

/** 按展开顺序返回 DTO 属性访问器需要读取的冻结不可变属性。 */
fun DtoBaseProp.accessorPath(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): List<ImmutableProp> {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val path = mutableListOf<ImmutableProp>()
    val visitedPropIds = mutableSetOf<DtoPropId>()
    var current: DtoBaseProp? = this
    var tailDtoProp: DtoBaseProp? = null
    while (current != null) {
        require(visitedPropIds.add(current.id)) {
            "DTO accessor path cannot contain a cycle: ${id.value}"
        }
        val immutableProp = current.boundImmutableProp(graph, immutableSchema)
        val slotIdentities = current.baseProps.map { binding ->
            val boundProp = requireNotNull(immutableSchema.propsById[binding.propId]) {
                "DTO base property references a missing immutable property: ${binding.propId.value}"
            }
            Triple(boundProp.declaringTypeId, boundProp.declarationId, boundProp.name)
        }.distinct()
        require(slotIdentities.size == 1) {
            "DTO base property bindings must resolve one Draft slot: ${current.id.value}"
        }
        path += immutableProp
        tailDtoProp = current
        current = current.nextProp(graph)
    }
    require(tailDtoProp?.id == tailPropId) {
        "DTO accessor path tail does not match frozen tail property: ${id.value}"
    }
    return path
}

/** 返回 DTO 属性访问器需要执行的值转换语义。 */
fun DtoBaseProp.accessorConversionKind(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoAccessorConversionKind {
    accessorPath(graph, immutableSchema)
    val tailProp = tailProp(graph)
    require(functionName == tailProp.functionName) {
        "DTO accessor head and tail must use the same function: ${id.value}"
    }
    require(enumType == tailProp.enumType) {
        "DTO accessor head and tail must use the same enum mapping: ${id.value}"
    }
    if (functionName == "id") {
        return DtoAccessorConversionKind.ASSOCIATED_ID
    }
    if (tailProp.targetTypeReference != null) {
        return DtoAccessorConversionKind.OBJECT_METADATA
    }
    val targetDtoType = tailProp.targetTypeId?.let(graph.typesById::getValue)
    if (targetDtoType != null) {
        return if (targetDtoType.polymorphism != null) {
            DtoAccessorConversionKind.OBJECT_METADATA
        } else {
            DtoAccessorConversionKind.OBJECT_CONSTRUCTOR
        }
    }
    if (enumType != null) {
        return DtoAccessorConversionKind.ENUM
    }
    if (dtoConverterTargetTypeOrNull(graph, immutableSchema) != null) {
        return DtoAccessorConversionKind.CONVERTER
    }
    return DtoAccessorConversionKind.NONE
}

/** 判断 DTO 属性是否可以直接读取和写入不可变属性。 */
fun DtoBaseProp.usesDirectBaseAccess(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): Boolean {
    requireVisibleProp(graph)
    val language = targetLanguage.requireDtoTargetLanguage()
    if (nextPropId != null) {
        return false
    }
    val immutableProp = boundImmutableProp(graph, immutableSchema)
    if (immutableProp.primaryMapping == PrimaryMapping.DISCRIMINATOR) {
        return false
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    val specification = DtoModifier.SPECIFICATION in ownerType.modifiers
    if (nullable && (!baseNullable || specification)) {
        return false
    }
    if (
        immutableProp.converter != null &&
        DtoModifier.INPUT !in ownerType.modifiers &&
        !specification
    ) {
        return false
    }
    val immutableValueType = immutableProp.type
        .withDtoRootNullability(immutableProp.nullable)
        .withDtoJavaBoxing(language, force = immutableProp.nullable)
    return generatedValueType(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = language,
        generatedTargetType = generatedTargetType,
    ).hasSameDtoSourceType(immutableValueType, language)
}

/** 判断 DTO 属性是否需要生成运行时属性访问器。 */
fun DtoBaseProp.requiresDtoPropAccessor(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): Boolean {
    return !usesDirectBaseAccess(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
        generatedTargetType = generatedTargetType,
    ) || requiresDtoLoadedStateStorage(graph)
}

/** 判断 DTO 类型是否需要生成容纳运行时属性访问器的静态区域。 */
fun DtoType.hasDtoPropAccessorFields(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): Boolean {
    require(graph.typesById[id] == this) {
        "DTO type does not belong to this graph: ${id.value}"
    }
    return basePropsInDeclarationOrder(graph).any { prop ->
        prop.requiresDtoPropAccessor(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = targetLanguage,
            generatedTargetType = generatedTargetType,
        )
    } || foldPropsInDeclarationOrder(graph).any { prop -> prop.nullGuardProp(graph) != null }
}

/** 判断 DTO 属性访问器是否接受 null 作为可写入值。 */
fun DtoBaseProp.acceptsNullInAccessor(graph: DtoGraph): Boolean {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    return !(nullable && (
        !tailProp(graph).baseNullable ||
            DtoModifier.SPECIFICATION in ownerType.modifiers ||
            DtoModifier.FUZZY in ownerType.modifiers ||
            inputModifier == DtoModifier.FUZZY
    ))
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
    if (!requiresDtoLoadedStateStorage(graph)) {
        return null
    }
    return when (targetLanguage) {
        LsiLanguage.JAVA -> dtoIdentifier("_is", name, "Loaded")
        LsiLanguage.KOTLIN -> (this as DtoBaseProp).loadedAccessorName()
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "DTO target language must be Java or Kotlin",
        )
    }
}

/** 判断可见 DTO 属性是否需要独立的加载状态存储。 */
fun DtoProp.requiresDtoLoadedStateStorage(graph: DtoGraph): Boolean {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    return id in ownerType.propIds &&
        DtoModifier.INPUT in ownerType.modifiers &&
        nullable &&
        (this as? DtoBaseProp)?.inputModifier == DtoModifier.DYNAMIC
}

/** 判断 fuzzy 输入属性写回 Draft 时是否需要忽略 null 值。 */
fun DtoBaseProp.requiresNonNullDraftWriteGuard(graph: DtoGraph): Boolean {
    requireVisibleProp(graph)
    return inputModifier == DtoModifier.FUZZY
}

/** 判断 DTO 属性写回 Draft 的目标是否为实体关联列表。 */
fun DtoBaseProp.hasEntityAssociationListDraftTarget(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    requireVisibleProp(graph)
    val decisions = tailProp(graph).baseProps.map { binding ->
        val immutableProp = requireNotNull(immutableSchema.propsById[binding.propId]) {
            "No immutable base property '${binding.propId.value}' for DTO property '${id.value}'"
        }
        immutableProp.list && immutableSchema.isEntityAssociation(immutableProp)
    }.distinct()
    require(decisions.size == 1) {
        "DTO property '${id.value}' has inconsistent entity association list bindings"
    }
    return decisions.single()
}

/** 判断 dynamic DTO 属性写回 Draft 时是否需要用空列表表达 loaded-null。 */
fun DtoBaseProp.requiresEmptyAssociationListDraftFallback(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    val entityAssociationListTarget = hasEntityAssociationListDraftTarget(graph, immutableSchema)
    return nullable &&
        inputModifier == DtoModifier.DYNAMIC &&
        entityAssociationListTarget
}

/** 返回 Serializer 的加载状态访问器；非动态属性返回空。 */
fun DtoBaseProp.serializerLoadedAccessorNameOrNull(): String? {
    return if (inputModifier == DtoModifier.DYNAMIC) loadedAccessorName() else null
}

/** 返回目标语言访问 DTO 属性值时使用的成员名称。 */
fun DtoProp.dtoValueAccessorName(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): String {
    requireVisibleProp(graph)
    return when (targetLanguage) {
        LsiLanguage.JAVA -> javaValueAccessorName(hasPrimitiveBooleanValue(graph, immutableSchema))
        LsiLanguage.KOTLIN -> name
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "DTO value accessor requires Java or Kotlin target language",
        )
    }
}

/** 返回 Kotlin 从基础不可变对象直接读取 DTO 属性时使用的成员名。 */
fun DtoBaseProp.kotlinBaseValueAccessorName(graph: DtoGraph): String {
    requireVisibleProp(graph)
    return baseProps.first().name
}

/** 返回 Java 修改 DTO 属性值时使用的 setter 方法名。 */
fun DtoProp.javaValueSetterName(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): String {
    return javaValueSetterName(hasPrimitiveBooleanValue(graph, immutableSchema))
}

/** 判断 DTO 属性的最终值是否为非空原生 Boolean。 */
fun DtoProp.hasPrimitiveBooleanValue(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    requireVisibleProp(graph)
    return when (this) {
        is DtoBaseProp -> hasPrimitiveBooleanBaseValue(graph, immutableSchema)
        is DtoUserProp -> type.toLsiType(LsiLanguage.JAVA).isPrimitiveBooleanValue()
        is DtoFoldProp -> false
    }
}

/** 判断 Java DTO backing field 是否需要使用可空 boxed 类型。 */
fun DtoProp.hasNullableJavaBackingField(): Boolean {
    return this !is DtoBaseProp || (functionName != "null" && functionName != "notNull")
}

/** 返回 Hibernate Validator 查询属性 getter 时使用的真实 JVM 方法名。 */
fun DtoProp.hibernateValidatorGetterName(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): String {
    return when (targetLanguage) {
        LsiLanguage.JAVA -> javaValueAccessorName(
            hasPrimitiveBooleanValue(graph, immutableSchema),
        )
        LsiLanguage.KOTLIN -> {
            requireVisibleProp(graph)
            if (name.hasKotlinIsPrefix()) {
                name
            } else {
                dtoIdentifier("get", name)
            }
        }
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "Hibernate Validator getter name requires Java or Kotlin target language",
        )
    }
}

private fun DtoProp.requireVisibleProp(graph: DtoGraph) {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(id in ownerType.propIds) {
        "DTO property is not visible in its owner type: ${id.value}"
    }
}

private fun DtoBaseProp.hasPrimitiveBooleanBaseValue(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Boolean {
    if (nullable || enumType != null || targetTypeId != null || targetTypeReference != null) {
        return false
    }
    val tailProp = graph.propsById.getValue(tailPropId) as? DtoBaseProp
        ?: error("DTO tail property is not a base property: ${tailPropId.value}")
    if (
        tailProp.enumType != null ||
        tailProp.targetTypeId != null ||
        tailProp.targetTypeReference != null
    ) {
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

private fun DtoProp.javaValueAccessorName(primitiveBoolean: Boolean): String {
    return dtoIdentifier(
        if (primitiveBoolean) "is" else "get",
        javaValueAccessorSuffix(primitiveBoolean),
    )
}

private fun DtoProp.javaValueSetterName(primitiveBoolean: Boolean): String {
    return dtoIdentifier("set", javaValueAccessorSuffix(primitiveBoolean))
}

private fun DtoProp.javaValueAccessorSuffix(primitiveBoolean: Boolean): String {
    return if (primitiveBoolean && name.hasJavaIsPrefix()) name.substring(2) else name
}

private fun String.hasJavaIsPrefix(): Boolean {
    return startsWith("is") && length > 2 && this[2].isUpperCase()
}

private fun String.hasKotlinIsPrefix(): Boolean {
    return startsWith("is") && length > 2 && this[2] !in 'a'..'z'
}

private fun LsiTypeRef.isPrimitiveBooleanValue(): Boolean {
    return this is LsiPrimitiveType &&
        kind == LsiPrimitiveKind.BOOLEAN &&
        !boxed &&
        nullability == LsiNullability.NON_NULL
}

private fun ImmutableProp.hasPrimitiveBooleanValue(
    functionName: String?,
    immutableSchema: ImmutableSchema,
): Boolean {
    if (functionName == "null" || functionName == "notNull") {
        return true
    }
    if (functionName in COLLECTION_VALUE_FUNCTIONS || list || converter != null) {
        return false
    }
    val valueProp = when {
        functionName in ID_VALUE_FUNCTIONS -> requireNotNull(immutableSchema.targetIdPropOf(this)) {
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
    return valueType.isPrimitiveBooleanValue()
}

private val ID_VALUE_FUNCTIONS = setOf("id", "associatedIdEq", "associatedIdNe")

private val COLLECTION_VALUE_FUNCTIONS = setOf(
    "valueIn",
    "valueNotIn",
    "associatedIdIn",
    "associatedIdNotIn",
)

private val INPUT_CONTRACT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")

private val VIEW_CONTRACT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")

private val EMBEDDABLE_DTO_CONTRACT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.EmbeddableDto")

private val JAVA_SPECIFICATION_CONTRACT_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")

private val KOTLIN_SPECIFICATION_CONTRACT_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")

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
