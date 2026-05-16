package site.addzero.lsi.poet

import site.addzero.lsi.types.PrimitiveType

sealed interface LsiTypeName {
    val nullable: Boolean

    fun copyNullable(nullable: Boolean): LsiTypeName
}

fun LsiTypeName.isSemanticallySameType(other: LsiTypeName): Boolean {
    if (nullable != other.nullable) {
        return false
    }
    return when (this) {
        is LsiClassName ->
            other is LsiClassName &&
                canonicalName.normalizedLsiSemanticClassName() == other.canonicalName.normalizedLsiSemanticClassName()
        is LsiParameterizedTypeName ->
            other is LsiParameterizedTypeName &&
                rawType.isSemanticallySameType(other.rawType) &&
                typeArguments.size == other.typeArguments.size &&
                typeArguments.zip(other.typeArguments).all { (left, right) ->
                    left.isSemanticallySameType(right)
                }
        is LsiArrayTypeName ->
            other is LsiArrayTypeName &&
                componentType.isSemanticallySameType(other.componentType)
        is LsiLambdaTypeName ->
            other is LsiLambdaTypeName &&
                receiverType.semanticNullableMatch(other.receiverType) &&
                parameterTypes.size == other.parameterTypes.size &&
                parameterTypes.zip(other.parameterTypes).all { (left, right) ->
                    left.isSemanticallySameType(right)
                } &&
                returnType.isSemanticallySameType(other.returnType)
        is LsiTypeVariableName ->
            other is LsiTypeVariableName &&
                name == other.name &&
                bounds.size == other.bounds.size &&
                bounds.zip(other.bounds).all { (left, right) ->
                    left.isSemanticallySameType(right)
                }
        is LsiWildcardTypeName ->
            other is LsiWildcardTypeName &&
                producerTypes.semanticListEquals(other.producerTypes) &&
                consumerTypes.semanticListEquals(other.consumerTypes)
        LsiStarTypeName -> other === LsiStarTypeName
    }
}

fun LsiTypeName.isBuiltInType(nullable: Boolean? = null): Boolean {
    if (nullable != null && this.nullable != nullable) {
        return false
    }
    val className = when (this) {
        is LsiClassName -> this
        is LsiParameterizedTypeName -> rawType
        else -> return false
    }
    if (className.packageName != "kotlin") {
        return false
    }
    return className.simpleName in setOf(
        "Boolean",
        "Char",
        "Byte",
        "Short",
        "Int",
        "Long",
        "Float",
        "Double"
    )
}

data class LsiParameterizedTypeName(
    val rawType: LsiClassName,
    val typeArguments: List<LsiTypeName>,
    override val nullable: Boolean = false,
) : LsiTypeName {

    init {
        require(typeArguments.isNotEmpty()) { "typeArguments must not be empty" }
    }

    override fun copyNullable(nullable: Boolean): LsiParameterizedTypeName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    override fun toString(): String =
        buildString {
            append(rawType.copyNullable(false).canonicalName)
            append("<")
            append(typeArguments.joinToString(", "))
            append(">")
            if (nullable) {
                append("?")
            }
        }
}

data class LsiArrayTypeName(
    val componentType: LsiTypeName,
    override val nullable: Boolean = false,
) : LsiTypeName {

    override fun copyNullable(nullable: Boolean): LsiArrayTypeName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    override fun toString(): String =
        buildString {
            append("kotlin.Array<")
            append(componentType)
            append(">")
            if (nullable) {
                append("?")
            }
        }
}

data class LsiLambdaTypeName(
    val receiverType: LsiTypeName? = null,
    val parameterTypes: List<LsiTypeName> = emptyList(),
    val returnType: LsiTypeName,
    override val nullable: Boolean = false,
) : LsiTypeName {

    override fun copyNullable(nullable: Boolean): LsiLambdaTypeName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    override fun toString(): String =
        buildString {
            receiverType?.let {
                append(it)
                append(".")
            }
            append("(")
            append(parameterTypes.joinToString(", "))
            append(") -> ")
            append(returnType)
            if (nullable) {
                append("?")
            }
        }
}

data class LsiTypeVariableName(
    val name: String,
    override val nullable: Boolean = false,
    val bounds: List<LsiTypeName> = emptyList(),
) : LsiTypeName {

    override fun copyNullable(nullable: Boolean): LsiTypeVariableName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    override fun toString(): String =
        buildString {
            append(name)
            if (nullable) {
                append("?")
            }
        }
}

data class LsiWildcardTypeName(
    val producerTypes: List<LsiTypeName> = emptyList(),
    val consumerTypes: List<LsiTypeName> = emptyList(),
    override val nullable: Boolean = false,
) : LsiTypeName {

    override fun copyNullable(nullable: Boolean): LsiWildcardTypeName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    override fun toString(): String =
        buildString {
            when {
                producerTypes.isNotEmpty() -> append("out ${producerTypes.joinToString(" & ")}")
                consumerTypes.isNotEmpty() -> append("in ${consumerTypes.joinToString(" & ")}")
                else -> append("*")
            }
            if (nullable) {
                append("?")
            }
        }
}

object LsiStarTypeName : LsiTypeName {
    override val nullable: Boolean = false

    override fun copyNullable(nullable: Boolean): LsiTypeName = this

    override fun toString(): String = "*"
}

private fun List<LsiTypeName>.semanticListEquals(other: List<LsiTypeName>): Boolean =
    size == other.size && zip(other).all { (left, right) -> left.isSemanticallySameType(right) }

private fun LsiTypeName?.semanticNullableMatch(other: LsiTypeName?): Boolean =
    when {
        this == null || other == null -> this == other
        else -> isSemanticallySameType(other)
    }

fun String.normalizedLsiSemanticClassName(): String =
    when (this) {
        "java.lang.Iterable",
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableIterable",
        "Iterable",
        "MutableIterable" -> "lsi.collection.Iterable"

        "java.util.Collection",
        "kotlin.collections.Collection",
        "kotlin.collections.MutableCollection",
        "Collection",
        "MutableCollection" -> "lsi.collection.Collection"

        "java.util.List",
        "java.util.ArrayList",
        "java.util.LinkedList",
        "kotlin.collections.List",
        "kotlin.collections.MutableList",
        "kotlin.collections.ArrayList",
        "kotlin.collections.LinkedList",
        "ArrayList",
        "LinkedList",
        "List",
        "MutableList" -> "lsi.collection.List"

        "java.util.Set",
        "java.util.SortedSet",
        "java.util.NavigableSet",
        "java.util.HashSet",
        "java.util.LinkedHashSet",
        "java.util.TreeSet",
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet",
        "kotlin.collections.HashSet",
        "kotlin.collections.LinkedHashSet",
        "kotlin.collections.TreeSet",
        "SortedSet",
        "NavigableSet",
        "HashSet",
        "LinkedHashSet",
        "TreeSet",
        "Set",
        "MutableSet" -> "lsi.collection.Set"

        "java.util.Map",
        "java.util.SortedMap",
        "java.util.NavigableMap",
        "java.util.HashMap",
        "java.util.LinkedHashMap",
        "java.util.TreeMap",
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap",
        "kotlin.collections.HashMap",
        "kotlin.collections.LinkedHashMap",
        "kotlin.collections.TreeMap",
        "SortedMap",
        "NavigableMap",
        "HashMap",
        "LinkedHashMap",
        "TreeMap",
        "Map",
        "MutableMap" -> "lsi.collection.Map"

        else -> this
    }

fun String.normalizedLsiCollectionCarrierQualifiedName(): String =
    when (this) {
        "java.lang.Iterable",
        "kotlin.collections.Iterable",
        "kotlin.collections.MutableIterable",
        "Iterable",
        "MutableIterable" -> "kotlin.collections.Iterable"

        else -> when (normalizedLsiSemanticClassName()) {
            "lsi.collection.Collection" -> "kotlin.collections.Collection"
            "lsi.collection.List" -> "kotlin.collections.List"
            "lsi.collection.Set" -> "kotlin.collections.Set"
            "lsi.collection.Map" -> "kotlin.collections.Map"
            else -> this
        }
    }

fun String.normalizedLsiCarrierQualifiedName(): String =
    PrimitiveType.findByName(this)?.let { primitiveType ->
        "kotlin.${primitiveType.kotlinName}"
    } ?: when (this) {
        "java.lang.String",
        "String",
        "kotlin.String" -> "kotlin.String"

        "java.lang.Object",
        "Object",
        "kotlin.Any",
        "Any" -> "kotlin.Any"

        "java.lang.Void",
        "void",
        "kotlin.Unit",
        "Unit" -> "kotlin.Unit"

        "kotlin.Nothing",
        "Nothing" -> "kotlin.Nothing"

        else -> normalizedLsiCollectionCarrierQualifiedName()
    }

fun String?.normalizedLsiCarrierLookupName(): String? =
    this
        ?.substringBefore('<')
        ?.removeSuffix("?")
        ?.removeSuffix("!")
        ?.normalizedLsiCarrierQualifiedName()

fun String.preferredLsiCollectionQualifiedName(): String? {
    val preferredQualifiedName = normalizedLsiCollectionCarrierQualifiedName()
    if (preferredQualifiedName == this || preferredQualifiedName == "kotlin.collections.Iterable") {
        return null
    }
    return when (this) {
        "kotlin.collections.MutableCollection",
        "kotlin.collections.MutableList",
        "kotlin.collections.MutableSet",
        "kotlin.collections.MutableMap" -> null

        else -> preferredQualifiedName
    }
}

fun String.isLsiImmutableListQualifiedName(): Boolean =
    this == "kotlin.collections.List" || this == "java.util.List"

fun String.isLsiMapQualifiedName(): Boolean =
    normalizedLsiSemanticClassName() == "lsi.collection.Map"

fun String?.isLsiCollectionLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName() in setOf(
        "kotlin.collections.Iterable",
        "kotlin.collections.Collection",
        "kotlin.collections.List",
        "kotlin.collections.Set",
        "kotlin.collections.Map",
    )

fun String.toBuiltInLsiClassNameOrNull(): LsiClassName? {
    if (!isLsiBuiltInCarrierName()) {
        return null
    }
    return LsiClassName.bestGuess(normalizedLsiCarrierQualifiedName())
}

fun String.toBoxedPrimitiveLsiClassNameOrNull(): LsiClassName? =
    PrimitiveType.findByName(this)?.let { primitiveType ->
        LsiClassName.bestGuess(primitiveType.javaWrapperFqName)
    }

fun String?.isLsiBooleanLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName() == "kotlin.Boolean"

fun String?.isLsiPrimitiveLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName()
        ?.let { PrimitiveType.findByName(it) != null }
        ?: false

fun String?.isLsiVoidLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName() == "kotlin.Unit"

fun String?.isLsiObjectLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName() == "kotlin.Any"

fun String?.isLsiNoValueLikeQualifiedName(): Boolean =
    normalizedLsiCarrierLookupName() in setOf("kotlin.Unit", "kotlin.Nothing")

private fun String.isLsiBuiltInCarrierName(): Boolean =
    PrimitiveType.findByName(this) != null ||
        this in setOf(
            "java.lang.String",
            "String",
            "kotlin.String",
            "java.lang.Object",
            "Object",
            "kotlin.Any",
            "Any",
            "java.lang.Void",
            "void",
            "kotlin.Unit",
            "Unit",
            "java.lang.Iterable",
            "kotlin.collections.Iterable",
            "kotlin.collections.MutableIterable",
            "Iterable",
            "MutableIterable",
            "Collection",
            "MutableCollection",
            "List",
            "MutableList",
            "Set",
            "MutableSet",
            "Map",
            "MutableMap",
        ) ||
        normalizedLsiSemanticClassName() in setOf(
            "lsi.collection.Collection",
            "lsi.collection.List",
            "lsi.collection.Set",
            "lsi.collection.Map",
        )
