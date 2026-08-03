package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
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

/** 将冻结的 DTO 类型引用解析为目标源码语言的 LSI 类型。 */
fun DtoTypeRef.toLsiType(targetLanguage: LsiLanguage): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    DTO_RENDERABLE_BUILTIN_TYPE_ARITIES[typeName]?.let { expectedArity ->
        require(arguments.size == expectedArity) {
            "DTO builtin type '$typeName' requires $expectedArity type arguments"
        }
    }
    if (typeName == "Array") {
        val argument = arguments.single()
        if (argument.variance == DtoVariance.STAR && language == LsiLanguage.KOTLIN) {
            return LsiDeclaredType(
                declarationId = KOTLIN_ARRAY_TYPE_ID,
                arguments = listOf(LsiTypeArgument.STAR),
                nullability = nullability(),
            )
        }
        val elementType = argument.type?.toLsiType(language)
            ?: LsiDeclaredType(language.anyTypeId())
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
            boxed = language == LsiLanguage.JAVA && nullable,
        )
    }
    val declarationId = language.dtoDeclaredTypeId(typeName)
    val forceOutput = language == LsiLanguage.JAVA && typeName in JAVA_FORCE_OUTPUT_TYPE_NAMES
    return LsiDeclaredType(
        declarationId = declarationId,
        arguments = arguments.map { argument ->
            argument.toLsiTypeArgument(language, forceOutput)
        },
        nullability = nullability(),
    )
}

/** 将冻结的可复用 DTO 引用解析为稳定的 LSI 声明类型。 */
fun DtoReusableTypeReference.toLsiType(): LsiDeclaredType {
    return LsiDeclaredType(declarationId = LsiSymbolId.type(qualifiedName))
}

internal fun LsiLanguage.requireDtoTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO target language must be Java or Kotlin"
    }
    return this
}

internal fun LsiTypeRef.boxedForTypeArgument(targetLanguage: LsiLanguage): LsiTypeRef {
    return if (targetLanguage == LsiLanguage.JAVA && this is LsiPrimitiveType) {
        copy(boxed = true)
    } else {
        this
    }
}

/** 将冻结类型递归归一化为 DTO 目标语言中的声明坐标。 */
internal fun LsiTypeRef.toDtoTargetType(targetLanguage: LsiLanguage): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    return when (this) {
        is LsiDeclaredType -> copy(
            declarationId = declarationId.toDtoTargetTypeId(language),
            arguments = arguments.map { argument ->
                argument.copy(
                    type = argument.type
                        ?.toDtoTargetType(language)
                        ?.boxedForTypeArgument(language),
                )
            },
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(
            boxed = language == LsiLanguage.JAVA &&
                (boxed || nullability == LsiNullability.NULLABLE),
            annotations = emptyList(),
        )
        is LsiArrayType -> copy(
            elementType = elementType.toDtoTargetType(language),
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toDtoTargetType(language),
            receiverType = receiverType?.toDtoTargetType(language),
            parameterTypes = parameterTypes.map { type -> type.toDtoTargetType(language) },
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

/** 判断已生成类型与原生属性类型在目标源码语言中是否具有相同表示。 */
internal fun LsiTypeRef.hasSameDtoSourceType(
    other: LsiTypeRef,
    targetLanguage: LsiLanguage,
): Boolean {
    val language = targetLanguage.requireDtoTargetLanguage()
    val left = toDtoTargetType(language)
    return if (language == LsiLanguage.JAVA) {
        left.toJavaDtoSourceType() == other.toJavaDtoSourceType()
    } else {
        left.toKotlinDtoSourceType() == other.toKotlinDtoSourceType()
    }
}

private fun LsiTypeRef.toJavaDtoSourceType(boxPrimitive: Boolean = false): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                if (argument.variance == LsiVariance.STAR) {
                    LsiTypeArgument.output(LsiDeclaredType(LsiSymbolId.type("java.lang.Object")))
                } else {
                    argument.copy(type = argument.type?.toJavaDtoSourceType(boxPrimitive = true))
                }
            },
            nullability = LsiNullability.UNKNOWN,
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> when {
            boxed || boxPrimitive -> LsiDeclaredType(
                declarationId = kind.javaDtoBoxedTypeId(),
                nullability = LsiNullability.UNKNOWN,
            )
            kind == LsiPrimitiveKind.UNIT -> copy(
                kind = LsiPrimitiveKind.VOID,
                nullability = LsiNullability.UNKNOWN,
                annotations = emptyList(),
            )
            else -> copy(
                nullability = LsiNullability.UNKNOWN,
                annotations = emptyList(),
            )
        }
        is LsiArrayType -> copy(
            elementType = elementType.toJavaDtoSourceType(),
            nullability = LsiNullability.UNKNOWN,
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toJavaDtoSourceType(),
            receiverType = receiverType?.toJavaDtoSourceType(),
            parameterTypes = parameterTypes.map { type -> type.toJavaDtoSourceType() },
            nullability = LsiNullability.UNKNOWN,
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(
            nullability = LsiNullability.UNKNOWN,
            annotations = emptyList(),
        )
        is LsiUnresolvedType -> copy(
            nullability = LsiNullability.UNKNOWN,
            annotations = emptyList(),
        )
    }
}

private fun LsiTypeRef.toKotlinDtoSourceType(referenceContext: Boolean = false): LsiTypeRef {
    val sourceNullability = kotlinDtoSourceNullability()
    return when (this) {
        is LsiDeclaredType -> {
            val qualifiedName = declarationId.requireTypeQualifiedName()
            val primitiveKind = KOTLIN_DTO_SOURCE_PRIMITIVE_KINDS[qualifiedName]
            if (primitiveKind != null) {
                LsiPrimitiveType(
                    kind = primitiveKind,
                    nullability = sourceNullability,
                )
            } else {
                copy(
                    declarationId = LsiSymbolId.type(
                        KOTLIN_DTO_SOURCE_DECLARED_TYPES[qualifiedName] ?: qualifiedName,
                    ),
                    arguments = arguments.map { argument ->
                        argument.copy(
                            type = argument.type?.toKotlinDtoSourceType(referenceContext = true),
                        )
                    },
                    nullability = sourceNullability,
                    annotations = emptyList(),
                )
            }
        }
        is LsiPrimitiveType -> when {
            kind == LsiPrimitiveKind.VOID && (boxed || referenceContext) -> LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.lang.Void"),
                nullability = sourceNullability,
            )
            kind == LsiPrimitiveKind.VOID -> LsiPrimitiveType(
                kind = LsiPrimitiveKind.UNIT,
                nullability = sourceNullability,
            )
            boxed && !referenceContext && nullability != LsiNullability.NULLABLE &&
                kind != LsiPrimitiveKind.UNIT -> LsiDeclaredType(
                    declarationId = kind.javaDtoBoxedTypeId(),
                    nullability = sourceNullability,
                )
            else -> copy(
                nullability = sourceNullability,
                annotations = emptyList(),
                boxed = false,
            )
        }
        is LsiArrayType -> {
            val primitiveType = elementType as? LsiPrimitiveType
            val primitiveArrayTypeId = primitiveType
                ?.takeIf { type ->
                    !type.boxed && type.nullability == LsiNullability.NON_NULL
                }
                ?.kind
                ?.kotlinDtoPrimitiveArrayTypeIdOrNull()
            if (primitiveArrayTypeId != null) {
                LsiDeclaredType(
                    declarationId = primitiveArrayTypeId,
                    nullability = sourceNullability,
                )
            } else {
                LsiDeclaredType(
                    declarationId = KOTLIN_ARRAY_TYPE_ID,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            elementType.toKotlinDtoSourceType(referenceContext = true),
                        ),
                    ),
                    nullability = sourceNullability,
                )
            }
        }
        is LsiFunctionType -> copy(
            returnType = returnType.toKotlinDtoSourceType(),
            receiverType = receiverType?.toKotlinDtoSourceType(referenceContext = true),
            parameterTypes = parameterTypes.map { type ->
                type.toKotlinDtoSourceType(referenceContext = true)
            },
            nullability = sourceNullability,
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> copy(
            nullability = sourceNullability,
            annotations = emptyList(),
        )
        is LsiUnresolvedType -> copy(
            nullability = sourceNullability,
            annotations = emptyList(),
        )
    }
}

private fun LsiTypeRef.kotlinDtoSourceNullability(): LsiNullability {
    return if (nullability == LsiNullability.NULLABLE) {
        LsiNullability.NULLABLE
    } else {
        LsiNullability.NON_NULL
    }
}

private fun LsiPrimitiveKind.javaDtoBoxedTypeId(): LsiSymbolId {
    val qualifiedName = when (this) {
        LsiPrimitiveKind.BOOLEAN -> "java.lang.Boolean"
        LsiPrimitiveKind.BYTE -> "java.lang.Byte"
        LsiPrimitiveKind.SHORT -> "java.lang.Short"
        LsiPrimitiveKind.INT -> "java.lang.Integer"
        LsiPrimitiveKind.LONG -> "java.lang.Long"
        LsiPrimitiveKind.CHAR -> "java.lang.Character"
        LsiPrimitiveKind.FLOAT -> "java.lang.Float"
        LsiPrimitiveKind.DOUBLE -> "java.lang.Double"
        LsiPrimitiveKind.UNIT -> "kotlin.Unit"
        LsiPrimitiveKind.VOID -> "java.lang.Void"
    }
    return LsiSymbolId.type(qualifiedName)
}

private fun LsiPrimitiveKind.kotlinDtoPrimitiveArrayTypeIdOrNull(): LsiSymbolId? {
    val simpleName = when (this) {
        LsiPrimitiveKind.BOOLEAN -> "BooleanArray"
        LsiPrimitiveKind.BYTE -> "ByteArray"
        LsiPrimitiveKind.SHORT -> "ShortArray"
        LsiPrimitiveKind.INT -> "IntArray"
        LsiPrimitiveKind.LONG -> "LongArray"
        LsiPrimitiveKind.CHAR -> "CharArray"
        LsiPrimitiveKind.FLOAT -> "FloatArray"
        LsiPrimitiveKind.DOUBLE -> "DoubleArray"
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> return null
    }
    return LsiSymbolId.type("kotlin.$simpleName")
}

/** 覆盖 DTO 生成值类型的根可空性。 */
internal fun LsiTypeRef.withDtoRootNullability(nullable: Boolean): LsiTypeRef {
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

/** 按 Java 源码表示规则装箱 DTO 根 primitive 类型。 */
internal fun LsiTypeRef.withDtoJavaBoxing(
    targetLanguage: LsiLanguage,
    force: Boolean,
): LsiTypeRef {
    if (targetLanguage != LsiLanguage.JAVA || this !is LsiPrimitiveType) {
        return this
    }
    return copy(boxed = boxed || force || nullability == LsiNullability.NULLABLE)
}

/** 将当前类型包装为目标语言的 DTO Collection。 */
internal fun LsiTypeRef.toDtoCollectionType(targetLanguage: LsiLanguage): LsiDeclaredType {
    return toDtoContainerType(targetLanguage, targetLanguage.dtoCollectionTypeId())
}

/** 将当前类型包装为目标语言的 DTO List。 */
internal fun LsiTypeRef.toDtoListType(targetLanguage: LsiLanguage): LsiDeclaredType {
    return toDtoContainerType(targetLanguage, targetLanguage.dtoListTypeId())
}

/** 判断当前声明是否已是 Java 或 Kotlin DTO List。 */
internal fun LsiTypeRef.isDtoListType(): Boolean {
    return this is LsiDeclaredType &&
        (declarationId == JAVA_LIST_TYPE_ID || declarationId == KOTLIN_LIST_TYPE_ID)
}

private fun LsiTypeRef.toDtoContainerType(
    targetLanguage: LsiLanguage,
    containerTypeId: LsiSymbolId,
): LsiDeclaredType {
    val language = targetLanguage.requireDtoTargetLanguage()
    return LsiDeclaredType(
        declarationId = containerTypeId,
        arguments = listOf(
            LsiTypeArgument.invariant(
                toDtoTargetType(language).boxedForTypeArgument(language),
            ),
        ),
    )
}

private fun LsiSymbolId.toDtoTargetTypeId(targetLanguage: LsiLanguage): LsiSymbolId {
    val qualifiedName = requireTypeQualifiedName()
    val targetName = when (targetLanguage) {
        LsiLanguage.JAVA -> JAVA_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.KOTLIN -> KOTLIN_DTO_DECLARED_TYPES[qualifiedName]
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
    return targetName?.let(LsiSymbolId::type) ?: this
}

private fun LsiLanguage.dtoCollectionTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> JAVA_COLLECTION_TYPE_ID
    LsiLanguage.KOTLIN -> KOTLIN_COLLECTION_TYPE_ID
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun LsiLanguage.dtoListTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> JAVA_LIST_TYPE_ID
    LsiLanguage.KOTLIN -> KOTLIN_LIST_TYPE_ID
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun DtoTypeArgument.toLsiTypeArgument(
    targetLanguage: LsiLanguage,
    forceOutput: Boolean,
): LsiTypeArgument {
    return when (variance) {
        DtoVariance.STAR -> LsiTypeArgument.STAR
        DtoVariance.INVARIANT -> {
            val argumentType = requireNotNull(type)
                .toLsiType(targetLanguage)
                .boxedForTypeArgument(targetLanguage)
            if (forceOutput) {
                LsiTypeArgument.output(argumentType)
            } else {
                LsiTypeArgument.invariant(argumentType)
            }
        }
        DtoVariance.IN -> LsiTypeArgument.input(
            requireNotNull(type).toLsiType(targetLanguage).boxedForTypeArgument(targetLanguage),
        )
        DtoVariance.OUT -> LsiTypeArgument.output(
            requireNotNull(type).toLsiType(targetLanguage).boxedForTypeArgument(targetLanguage),
        )
    }
}

/** 返回 Kotlin DTO 用户属性的隐式或显式默认值源码；没有默认值时返回空。 */
fun DtoUserProp.kotlinDefaultValueTextOrNull(): String? {
    defaultValueText?.let { return it }
    if (type.nullable) {
        return "null"
    }
    return when (type.typeName) {
        "Boolean" -> "false"
        "Char" -> "'\\0'"
        "Byte", "Short", "Int" -> "0"
        "Long" -> "0L"
        "Float" -> "0F"
        "Double" -> "0.0"
        "String", "kotlin.String" -> "\"\""
        "Array" -> {
            val componentType = type.arguments.single().type
            when {
                componentType == null -> "emptyArray<Any?>()"
                componentType.nullable -> "emptyArray()"
                else -> when (componentType.typeName) {
                    "Boolean" -> "booleanArrayOf()"
                    "Char" -> "charArrayOf()"
                    "Byte" -> "byteArrayOf()"
                    "Short" -> "shortArrayOf()"
                    "Int" -> "intArrayOf()"
                    "Long" -> "longArrayOf()"
                    "Float" -> "floatArrayOf()"
                    "Double" -> "doubleArrayOf()"
                    else -> "emptyArray()"
                }
            }
        }
        "Iterable",
        "kotlin.collections.Iterable",
        "java.lang.Iterable",
        "Collection",
        "kotlin.collections.Collection",
        "java.util.Collection",
        "List",
        "kotlin.collections.List",
        "java.util.List",
        -> if (type.arguments.single().type == null) "emptyList<Any?>()" else "emptyList()"
        "MutableIterable",
        "kotlin.collections.MutableIterable",
        "MutableCollection",
        "kotlin.collections.MutableCollection",
        "MutableList",
        "kotlin.collections.MutableList",
        -> if (type.arguments.single().type == null) "mutableListOf<Any?>()" else "mutableListOf()"
        "Set",
        "kotlin.collections.Set",
        "java.util.Set",
        -> "emptySet()"
        "MutableSet",
        "kotlin.collections.MutableSet",
        -> "mutableSetOf()"
        "Map",
        "kotlin.collections.Map",
        "java.util.Map",
        -> "emptyMap()"
        "MutableMap",
        "kotlin.collections.MutableMap",
        -> "mutableMapOf()"
        else -> null
    }
}

private fun LsiLanguage.dtoDeclaredTypeId(typeName: String): LsiSymbolId {
    val canonicalName = when (this) {
        LsiLanguage.JAVA -> JAVA_DTO_DECLARED_TYPES[typeName]
        LsiLanguage.KOTLIN -> KOTLIN_DTO_DECLARED_TYPES[typeName]
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    } ?: typeName
    return LsiSymbolId.type(canonicalName)
}

private fun LsiLanguage.anyTypeId(): LsiSymbolId = when (this) {
    LsiLanguage.JAVA -> LsiSymbolId.type("java.lang.Object")
    LsiLanguage.KOTLIN -> LsiSymbolId.type("kotlin.Any")
    LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
}

private fun DtoTypeRef.nullability(): LsiNullability {
    return if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
}

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

private val KOTLIN_DTO_SOURCE_PRIMITIVE_KINDS = mapOf(
    "java.lang.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "java.lang.Byte" to LsiPrimitiveKind.BYTE,
    "java.lang.Short" to LsiPrimitiveKind.SHORT,
    "java.lang.Integer" to LsiPrimitiveKind.INT,
    "java.lang.Long" to LsiPrimitiveKind.LONG,
    "java.lang.Character" to LsiPrimitiveKind.CHAR,
    "java.lang.Float" to LsiPrimitiveKind.FLOAT,
    "java.lang.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "kotlin.Byte" to LsiPrimitiveKind.BYTE,
    "kotlin.Short" to LsiPrimitiveKind.SHORT,
    "kotlin.Int" to LsiPrimitiveKind.INT,
    "kotlin.Long" to LsiPrimitiveKind.LONG,
    "kotlin.Char" to LsiPrimitiveKind.CHAR,
    "kotlin.Float" to LsiPrimitiveKind.FLOAT,
    "kotlin.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Unit" to LsiPrimitiveKind.UNIT,
)

private val KOTLIN_DTO_SOURCE_DECLARED_TYPES = mapOf(
    "java.lang.String" to "kotlin.String",
    "java.lang.Object" to "kotlin.Any",
    "java.lang.Iterable" to "kotlin.collections.Iterable",
    "java.util.Collection" to "kotlin.collections.Collection",
    "java.util.Iterator" to "kotlin.collections.Iterator",
    "java.util.List" to "kotlin.collections.List",
    "java.util.ListIterator" to "kotlin.collections.ListIterator",
    "java.util.Map" to "kotlin.collections.Map",
    "java.util.Map.Entry" to "kotlin.collections.Map.Entry",
    "java.util.Set" to "kotlin.collections.Set",
)

private val DTO_RENDERABLE_BUILTIN_TYPE_ARITIES = buildMap {
    listOf(
        "Boolean",
        "Char",
        "Byte",
        "Short",
        "Int",
        "Long",
        "Float",
        "Double",
        "Any",
        "kotlin.Any",
        "java.lang.Object",
        "String",
        "kotlin.String",
        "java.lang.String",
    ).forEach { typeName -> put(typeName, 0) }
    listOf(
        "Array",
        "Iterable",
        "kotlin.collections.Iterable",
        "java.lang.Iterable",
        "MutableIterable",
        "kotlin.collections.MutableIterable",
        "Collection",
        "kotlin.collections.Collection",
        "java.util.Collection",
        "MutableCollection",
        "kotlin.collections.MutableCollection",
        "List",
        "kotlin.collections.List",
        "java.util.List",
        "MutableList",
        "kotlin.collections.MutableList",
        "Set",
        "kotlin.collections.Set",
        "java.util.Set",
        "MutableSet",
        "kotlin.collections.MutableSet",
    ).forEach { typeName -> put(typeName, 1) }
    listOf(
        "Map",
        "kotlin.collections.Map",
        "java.util.Map",
        "MutableMap",
        "kotlin.collections.MutableMap",
    ).forEach { typeName -> put(typeName, 2) }
}

private val JAVA_FORCE_OUTPUT_TYPE_NAMES = setOf(
    "Iterable",
    "kotlin.collections.Iterable",
    "java.lang.Iterable",
    "Collection",
    "kotlin.collections.Collection",
    "java.util.Collection",
    "List",
    "kotlin.collections.List",
    "java.util.List",
    "Set",
    "kotlin.collections.Set",
    "java.util.Set",
    "Map",
    "kotlin.collections.Map",
    "java.util.Map",
)

internal val JAVA_DTO_DECLARED_TYPES = mapOf(
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

internal val KOTLIN_DTO_DECLARED_TYPES = mapOf(
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

internal val JAVA_LIST_TYPE_ID = LsiSymbolId.type("java.util.List")
internal val KOTLIN_LIST_TYPE_ID = LsiSymbolId.type("kotlin.collections.List")
internal val JAVA_COLLECTION_TYPE_ID = LsiSymbolId.type("java.util.Collection")
internal val KOTLIN_COLLECTION_TYPE_ID = LsiSymbolId.type("kotlin.collections.Collection")
private val KOTLIN_ARRAY_TYPE_ID = LsiSymbolId.type("kotlin.Array")
