package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef

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
private val KOTLIN_ARRAY_TYPE_ID = LsiSymbolId.type("kotlin.Array")
