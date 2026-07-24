package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.model.LsiAnnotation
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
import site.addzero.lsi.model.LsiVariance

/** InputBuilder 将属性写入 DTO 时采用的稳定策略。 */
enum class DtoInputBuilderBuildStrategy {
    JAVA_REQUIRE_NON_NULL_AND_SET,
    JAVA_REQUIRE_LOADED_AND_SET,
    JAVA_SET,
    JAVA_SET_WHEN_LOADED,
    JAVA_SET_WHEN_NON_NULL,
    KOTLIN_REQUIRE_NON_NULL_ARGUMENT,
    KOTLIN_REQUIRE_LOADED_ARGUMENT,
    KOTLIN_ARGUMENT,
    KOTLIN_ARGUMENT_WITH_LOADED_STATE,
}

/** 按 DTO 声明顺序返回 InputBuilder 必须声明的全部属性。 */
fun DtoType.inputBuilderPropsInDeclarationOrder(graph: DtoGraph): List<DtoProp> {
    require(requiresInputBuilder(graph)) {
        "DTO type does not require an input builder: ${id.value}"
    }
    return propsInDeclarationOrder(graph)
}

/** 返回 InputBuilder setter 的名称。 */
fun DtoProp.inputBuilderSetterName(): String = name

/**
 * 返回 Java InputBuilder 在 build 阶段调用的 DTO setter；Kotlin 通过构造参数赋值，因此返回空。
 */
fun DtoProp.inputBuilderBuiltDtoSetterNameOrNull(targetLanguage: LsiLanguage): String? {
    return when (targetLanguage.requireDtoTargetLanguage()) {
        LsiLanguage.JAVA -> {
            val suffix = if (
                this is DtoUserProp &&
                type.typeName == "Boolean" &&
                name.startsWith("is") &&
                name.length > 2 &&
                name[2].isUpperCase()
            ) {
                name.substring(2)
            } else {
                name
            }
            dtoIdentifier("set", suffix)
        }
        LsiLanguage.KOTLIN -> null
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
}

/** 返回 Builder 私有加载状态名称；无需独立加载状态时返回空。 */
fun DtoProp.inputBuilderLoadedStateNameOrNull(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
): String? {
    requireInputBuilderOwner(graph)
    if (!nullable) {
        return null
    }
    val inputModifier = (this as? DtoBaseProp)?.inputModifier ?: return null
    if (inputModifier != DtoModifier.FIXED && inputModifier != DtoModifier.DYNAMIC) {
        return null
    }
    return when (targetLanguage.requireDtoTargetLanguage()) {
        LsiLanguage.JAVA -> dtoIdentifier("_is", name, "Loaded")
        LsiLanguage.KOTLIN -> dtoIdentifier("is", name, "Loaded")
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
}

/** 返回 InputBuilder build 阶段对当前属性采用的语言相关策略。 */
fun DtoProp.inputBuilderBuildStrategy(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
): DtoInputBuilderBuildStrategy {
    requireInputBuilderOwner(graph)
    return when (targetLanguage.requireDtoTargetLanguage()) {
        LsiLanguage.JAVA -> javaInputBuilderBuildStrategy()
        LsiLanguage.KOTLIN -> kotlinInputBuilderBuildStrategy()
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
}

/**
 * 解析 InputBuilder setter 参数类型。
 *
 * 匿名嵌套 DTO 的最终生成名不属于 DTO 图本身，因此由调用方提供精确类型解析函数。
 */
fun DtoProp.inputBuilderParameterType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedDtoType: (DtoType) -> LsiDeclaredType,
): LsiTypeRef {
    requireInputBuilderOwner(graph)
    val language = targetLanguage.requireDtoTargetLanguage()
    val valueType = when (this) {
        is DtoBaseProp -> inputBuilderBaseValueType(graph, immutableSchema, language, generatedDtoType)
        is DtoFoldProp -> generatedDtoType(generatedTargetType(graph))
        is DtoUserProp -> type.toInputBuilderType(language)
    }
    return valueType
        .toInputBuilderTargetType(language)
        .withRootNullability(nullable)
        .toInputBuilderParameterRepresentation(language)
}

/** 返回普通 InputBuilder 属性的私有 backing storage 类型。 */
fun DtoProp.inputBuilderBackingType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedDtoType: (DtoType) -> LsiDeclaredType,
): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    val parameterType = inputBuilderParameterType(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = language,
        generatedDtoType = generatedDtoType,
    )
    if (this is DtoBaseProp && functionName in NON_NULL_BACKING_FUNCTIONS) {
        return parameterType.withRootNullability(false)
    }
    return parameterType.toNullableInputBuilderStorage(language)
}

/** 返回已冻结且适用于 InputBuilder setter 的 Jackson 注解应用。 */
fun DtoProp.inputBuilderSetterJacksonAnnotationApplications(
    graph: DtoGraph,
    annotationContract: DtoAnnotationContract,
): List<DtoBuilderSetterAnnotationApplication> {
    requireInputBuilderOwner(graph)
    val propPlan = requireNotNull(annotationContract.propPlansByPropId[id]) {
        "DTO annotation contract has no property plan: ${id.value}"
    }
    return propPlan.builderSetterApplications
}

/**
 * 按当前 Jackson 坐标精确选择 DTO 自身声明的 JsonNaming 注解。
 *
 * 未显式声明 value 的 JsonNaming 不参与 Builder 生成。
 */
fun DtoType.inputBuilderJsonNamingAnnotationOrNull(
    graph: DtoGraph,
    annotationContract: DtoAnnotationContract,
    jsonNamingAnnotationTypeId: LsiSymbolId,
): LsiAnnotation? {
    require(requiresInputBuilder(graph)) {
        "DTO type does not require an input builder: ${id.value}"
    }
    jsonNamingAnnotationTypeId.requireTypeQualifiedName()
    val typePlan = requireNotNull(annotationContract.typePlansByTypeId[id]) {
        "DTO annotation contract has no type plan: ${id.value}"
    }
    val matches = typePlan.applications.filter { application ->
        application.origin == DtoAnnotationOrigin.DTO &&
            application.annotation.type == jsonNamingAnnotationTypeId &&
            application.annotation["value"]?.isExplicit == true
    }
    require(matches.size <= 1) {
        "DTO type cannot declare duplicate JsonNaming annotations: ${id.value}"
    }
    return matches.singleOrNull()?.annotation
}

private fun DtoProp.javaInputBuilderBuildStrategy(): DtoInputBuilderBuildStrategy {
    if (!nullable) {
        return DtoInputBuilderBuildStrategy.JAVA_REQUIRE_NON_NULL_AND_SET
    }
    return when ((this as? DtoBaseProp)?.inputModifier) {
        DtoModifier.FIXED -> DtoInputBuilderBuildStrategy.JAVA_REQUIRE_LOADED_AND_SET
        DtoModifier.DYNAMIC -> DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_LOADED
        DtoModifier.FUZZY -> DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_NON_NULL
        DtoModifier.STATIC,
        null,
        -> DtoInputBuilderBuildStrategy.JAVA_SET
        DtoModifier.INPUT,
        DtoModifier.SPECIFICATION,
        DtoModifier.SEALED,
        DtoModifier.UNSAFE,
        -> error("DTO input property has an illegal input modifier")
    }
}

private fun DtoProp.kotlinInputBuilderBuildStrategy(): DtoInputBuilderBuildStrategy {
    if (!nullable) {
        return DtoInputBuilderBuildStrategy.KOTLIN_REQUIRE_NON_NULL_ARGUMENT
    }
    return when ((this as? DtoBaseProp)?.inputModifier) {
        DtoModifier.FIXED -> DtoInputBuilderBuildStrategy.KOTLIN_REQUIRE_LOADED_ARGUMENT
        DtoModifier.DYNAMIC -> DtoInputBuilderBuildStrategy.KOTLIN_ARGUMENT_WITH_LOADED_STATE
        DtoModifier.STATIC,
        DtoModifier.FUZZY,
        null,
        -> DtoInputBuilderBuildStrategy.KOTLIN_ARGUMENT
        DtoModifier.INPUT,
        DtoModifier.SPECIFICATION,
        DtoModifier.SEALED,
        DtoModifier.UNSAFE,
        -> error("DTO input property has an illegal input modifier")
    }
}

private fun DtoBaseProp.inputBuilderBaseValueType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedDtoType: (DtoType) -> LsiDeclaredType,
): LsiTypeRef {
    enumType?.let { enumType ->
        return if (enumType.numeric) {
            LsiPrimitiveType(LsiPrimitiveKind.INT)
        } else {
            LsiDeclaredType(targetLanguage.stringTypeId())
        }
    }
    val tailProp = tailProp(graph)
    val immutableProp = tailProp.immutableProp(immutableSchema)
    if (tailProp.functionName !in NON_NULL_BACKING_FUNCTIONS) {
        immutableProp.converter?.targetType?.let { targetType -> return targetType }
    }
    val elementType = when {
        tailProp.targetTypeReference != null -> {
            LsiDeclaredType(LsiSymbolId.type(tailProp.targetTypeReference.qualifiedName))
        }
        tailProp.targetTypeId != null -> {
            val targetType = graph.typesById.getValue(tailProp.targetTypeId)
            val generatedType = if (tailProp.recursive && !targetType.focusedRecursion) {
                graph.typesById.getValue(ownerTypeId)
            } else {
                targetType
            }
            generatedDtoType(generatedType)
        }
        tailProp.functionName == "id" -> {
            val targetIdProp = requireNotNull(immutableSchema.targetIdPropOf(immutableProp)) {
                "DTO id function must reference an immutable association: ${tailProp.id.value}"
            }
            targetIdProp.inputBuilderClientElementType(immutableSchema)
        }
        tailProp.functionName in NON_NULL_BACKING_FUNCTIONS -> LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
        else -> immutableProp.inputBuilderClientElementType(immutableSchema)
    }
    if (!immutableProp.list || elementType.isListType()) {
        return elementType
    }
    return LsiDeclaredType(
        declarationId = targetLanguage.listTypeId(),
        arguments = listOf(LsiTypeArgument.invariant(elementType.toTypeArgument(targetLanguage))),
    )
}

private fun DtoBaseProp.immutableProp(immutableSchema: ImmutableSchema): ImmutableProp {
    val immutablePropIds = baseProps.map(DtoBasePropBinding::propId).distinct()
    val immutableProps = immutablePropIds.map { propId ->
        requireNotNull(immutableSchema.propsById[propId]) {
            "DTO base property references a missing immutable property: ${propId.value}"
        }
    }
    val clientTypeSignatures = immutableProps.map { prop ->
        (prop.converter?.targetType ?: prop.type).jimmerTypeSignature(ignoreRootNullability = true)
    }.distinct()
    require(clientTypeSignatures.size == 1) {
        "DTO base property bindings must expose one value type: ${id.value}"
    }
    return immutableProps.first()
}

private fun ImmutableProp.inputBuilderClientElementType(immutableSchema: ImmutableSchema): LsiTypeRef {
    converter?.targetType?.let { targetType -> return targetType }
    val idView = view as? ImmutableView.Id
    val targetIdProp = idView?.targetIdPropId?.let(immutableSchema.propsById::get)
    return targetIdProp?.converter?.targetType ?: targetIdProp?.elementTypeOrSelf() ?: elementTypeOrSelf()
}

private fun DtoTypeRef.toInputBuilderType(targetLanguage: LsiLanguage): LsiTypeRef {
    if (typeName == "Array") {
        val argument = arguments.singleOrNull()
        if (argument?.variance == DtoVariance.STAR && targetLanguage == LsiLanguage.KOTLIN) {
            return LsiDeclaredType(
                declarationId = KOTLIN_ARRAY_TYPE_ID,
                arguments = listOf(LsiTypeArgument.STAR),
                nullability = nullability(),
            )
        }
        val elementType = argument?.type?.toInputBuilderType(targetLanguage)
            ?: LsiDeclaredType(targetLanguage.anyTypeId())
        return LsiArrayType(
            elementType = elementType,
            nullability = nullability(),
        )
    }
    val primitiveKind = DTO_PRIMITIVE_KINDS[typeName]
    if (primitiveKind != null) {
        return LsiPrimitiveType(
            kind = primitiveKind,
            nullability = nullability(),
            boxed = targetLanguage == LsiLanguage.JAVA && nullable,
        )
    }
    val declarationId = targetLanguage.dtoDeclaredTypeId(typeName)
    val forceOutput = targetLanguage == LsiLanguage.JAVA && typeName in JAVA_FORCE_OUTPUT_TYPE_NAMES
    return LsiDeclaredType(
        declarationId = declarationId,
        arguments = arguments.map { argument ->
            argument.toInputBuilderTypeArgument(targetLanguage, forceOutput)
        },
        nullability = nullability(),
    )
}

private fun DtoTypeArgument.toInputBuilderTypeArgument(
    targetLanguage: LsiLanguage,
    forceOutput: Boolean,
): LsiTypeArgument {
    return when (variance) {
        DtoVariance.STAR -> LsiTypeArgument.STAR
        DtoVariance.INVARIANT -> {
            val argumentType = requireNotNull(type)
                .toInputBuilderType(targetLanguage)
                .toTypeArgument(targetLanguage)
            if (forceOutput) {
                LsiTypeArgument.output(argumentType)
            } else {
                LsiTypeArgument.invariant(argumentType)
            }
        }
        DtoVariance.IN -> LsiTypeArgument.input(
            requireNotNull(type).toInputBuilderType(targetLanguage).toTypeArgument(targetLanguage),
        )
        DtoVariance.OUT -> LsiTypeArgument.output(
            requireNotNull(type).toInputBuilderType(targetLanguage).toTypeArgument(targetLanguage),
        )
    }
}

private fun DtoProp.requireInputBuilderOwner(graph: DtoGraph): DtoType {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(ownerType.requiresInputBuilder(graph)) {
        "DTO property owner does not require an input builder: ${id.value}"
    }
    return ownerType
}

private fun LsiLanguage.requireDtoTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO target language must be Java or Kotlin"
    }
    return this
}

private fun LsiLanguage.dtoDeclaredTypeId(typeName: String): LsiSymbolId {
    val canonicalName = when (this) {
        LsiLanguage.JAVA -> JAVA_DTO_DECLARED_TYPES[typeName]
        LsiLanguage.KOTLIN -> KOTLIN_DTO_DECLARED_TYPES[typeName]
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    } ?: typeName
    return LsiSymbolId.type(canonicalName)
}

private fun LsiLanguage.stringTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> LsiSymbolId.type("java.lang.String")
    LsiLanguage.KOTLIN -> LsiSymbolId.type("kotlin.String")
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun LsiLanguage.anyTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> LsiSymbolId.type("java.lang.Object")
    LsiLanguage.KOTLIN -> LsiSymbolId.type("kotlin.Any")
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun LsiLanguage.listTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> JAVA_LIST_TYPE_ID
    LsiLanguage.KOTLIN -> KOTLIN_LIST_TYPE_ID
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun LsiTypeRef.isListType(): Boolean {
    val type = this as? LsiDeclaredType ?: return false
    return type.declarationId == JAVA_LIST_TYPE_ID || type.declarationId == KOTLIN_LIST_TYPE_ID
}

private fun LsiTypeRef.toInputBuilderTargetType(targetLanguage: LsiLanguage): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            declarationId = declarationId.toInputBuilderTargetTypeId(targetLanguage),
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toInputBuilderTargetType(targetLanguage)?.toTypeArgument(targetLanguage))
            },
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(
            annotations = emptyList(),
            boxed = targetLanguage == LsiLanguage.JAVA && (boxed || nullability == LsiNullability.NULLABLE),
        )
        is LsiArrayType -> copy(
            elementType = elementType.toInputBuilderTargetType(targetLanguage),
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toInputBuilderTargetType(targetLanguage),
            receiverType = receiverType?.toInputBuilderTargetType(targetLanguage),
            parameterTypes = parameterTypes.map { type -> type.toInputBuilderTargetType(targetLanguage) },
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

private fun LsiSymbolId.toInputBuilderTargetTypeId(targetLanguage: LsiLanguage): LsiSymbolId {
    val qualifiedName = requireTypeQualifiedName()
    val targetName = when (targetLanguage) {
        LsiLanguage.JAVA -> JAVA_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.KOTLIN -> KOTLIN_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
    return targetName?.let { name -> LsiSymbolId.type(name) } ?: this
}

private fun LsiTypeRef.toTypeArgument(targetLanguage: LsiLanguage): LsiTypeRef {
    return if (targetLanguage == LsiLanguage.JAVA && this is LsiPrimitiveType) {
        copy(boxed = true)
    } else {
        this
    }
}

private fun LsiTypeRef.toNullableInputBuilderStorage(targetLanguage: LsiLanguage): LsiTypeRef {
    val nullableType = withRootNullability(true)
    return if (targetLanguage == LsiLanguage.JAVA && nullableType is LsiPrimitiveType) {
        nullableType.copy(boxed = true)
    } else {
        nullableType
    }
}

private fun LsiTypeRef.toInputBuilderParameterRepresentation(targetLanguage: LsiLanguage): LsiTypeRef {
    return if (
        targetLanguage == LsiLanguage.JAVA &&
        this is LsiPrimitiveType &&
        nullability == LsiNullability.NULLABLE
    ) {
        copy(boxed = true)
    } else {
        this
    }
}

private fun LsiTypeRef.withRootNullability(nullable: Boolean): LsiTypeRef {
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

private fun DtoTypeRef.nullability(): LsiNullability {
    return if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
}

private val NON_NULL_BACKING_FUNCTIONS = setOf("null", "notNull")

private val DTO_PRIMITIVE_KINDS = mapOf(
    "Boolean" to LsiPrimitiveKind.BOOLEAN,
    "Char" to LsiPrimitiveKind.CHAR,
    "Byte" to LsiPrimitiveKind.BYTE,
    "Short" to LsiPrimitiveKind.SHORT,
    "Int" to LsiPrimitiveKind.INT,
    "Long" to LsiPrimitiveKind.LONG,
    "Float" to LsiPrimitiveKind.FLOAT,
    "Double" to LsiPrimitiveKind.DOUBLE,
)

private val JAVA_FORCE_OUTPUT_TYPE_NAMES = setOf(
    "Iterable",
    "Collection",
    "List",
    "Set",
    "Map",
)

private val JAVA_DTO_DECLARED_TYPES = mapOf(
    "Any" to "java.lang.Object",
    "kotlin.Any" to "java.lang.Object",
    "String" to "java.lang.String",
    "kotlin.String" to "java.lang.String",
    "Iterable" to "java.lang.Iterable",
    "kotlin.collections.Iterable" to "java.lang.Iterable",
    "MutableIterable" to "java.lang.Iterable",
    "kotlin.collections.MutableIterable" to "java.lang.Iterable",
    "Collection" to "java.util.Collection",
    "kotlin.collections.Collection" to "java.util.Collection",
    "MutableCollection" to "java.util.Collection",
    "kotlin.collections.MutableCollection" to "java.util.Collection",
    "List" to "java.util.List",
    "kotlin.collections.List" to "java.util.List",
    "MutableList" to "java.util.List",
    "kotlin.collections.MutableList" to "java.util.List",
    "Set" to "java.util.Set",
    "kotlin.collections.Set" to "java.util.Set",
    "MutableSet" to "java.util.Set",
    "kotlin.collections.MutableSet" to "java.util.Set",
    "Map" to "java.util.Map",
    "kotlin.collections.Map" to "java.util.Map",
    "MutableMap" to "java.util.Map",
    "kotlin.collections.MutableMap" to "java.util.Map",
)

private val KOTLIN_DTO_DECLARED_TYPES = mapOf(
    "Any" to "kotlin.Any",
    "java.lang.Object" to "kotlin.Any",
    "String" to "kotlin.String",
    "java.lang.String" to "kotlin.String",
    "Iterable" to "kotlin.collections.Iterable",
    "java.lang.Iterable" to "kotlin.collections.Iterable",
    "MutableIterable" to "kotlin.collections.MutableIterable",
    "Collection" to "kotlin.collections.Collection",
    "java.util.Collection" to "kotlin.collections.Collection",
    "MutableCollection" to "kotlin.collections.MutableCollection",
    "List" to "kotlin.collections.List",
    "java.util.List" to "kotlin.collections.List",
    "MutableList" to "kotlin.collections.MutableList",
    "Set" to "kotlin.collections.Set",
    "java.util.Set" to "kotlin.collections.Set",
    "MutableSet" to "kotlin.collections.MutableSet",
    "Map" to "kotlin.collections.Map",
    "java.util.Map" to "kotlin.collections.Map",
    "MutableMap" to "kotlin.collections.MutableMap",
)

private val JAVA_LIST_TYPE_ID = LsiSymbolId.type("java.util.List")
private val KOTLIN_LIST_TYPE_ID = LsiSymbolId.type("kotlin.collections.List")
private val KOTLIN_ARRAY_TYPE_ID = LsiSymbolId.type("kotlin.Array")
