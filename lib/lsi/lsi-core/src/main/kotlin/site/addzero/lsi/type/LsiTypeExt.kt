package site.addzero.lsi.type

import site.addzero.lsi.types.TypeRegistry

private val NUMBER_LIKE_TYPE_QUALIFIED_NAMES = setOf("java.lang.Number", "kotlin.Number")
private val LEGACY_DATE_LIKE_TYPE_QUALIFIED_NAMES = setOf("java.util.Date")
private val TEMPORAL_LIKE_TYPE_QUALIFIED_NAMES = setOf("java.time.temporal.Temporal")
private val COMPARABLE_LIKE_TYPE_QUALIFIED_NAMES = setOf("java.lang.Comparable", "kotlin.Comparable")
private val RUNTIME_EXCEPTION_LIKE_TYPE_QUALIFIED_NAMES = setOf("java.lang.RuntimeException", "kotlin.RuntimeException")

fun LsiType.isDateOrTime(): Boolean {
    val typeName = typeNameForTypeRegistry() ?: return false
    return TypeRegistry.isDateTime(typeName)
}

fun LsiType.isDate(): Boolean {
    val typeName = typeNameForTypeRegistry() ?: return false
    return TypeRegistry.isDate(typeName)
}

fun LsiType.isTime(): Boolean {
    val typeName = typeNameForTypeRegistry() ?: return false
    return TypeRegistry.isTime(typeName)
}

fun LsiType.isSubtypeOfNumberLike(): Boolean =
    isSubtypeOfAnyQualifiedName(NUMBER_LIKE_TYPE_QUALIFIED_NAMES)

fun LsiType.isSubtypeOfJavaUtilDateLike(): Boolean =
    isSubtypeOfAnyQualifiedName(LEGACY_DATE_LIKE_TYPE_QUALIFIED_NAMES)

fun LsiType.isSubtypeOfTemporalLike(): Boolean =
    isSubtypeOfAnyQualifiedName(TEMPORAL_LIKE_TYPE_QUALIFIED_NAMES)

fun LsiType.isSubtypeOfComparableLike(): Boolean =
    isSubtypeOfAnyQualifiedName(COMPARABLE_LIKE_TYPE_QUALIFIED_NAMES)

fun LsiType.isSubtypeOfRuntimeExceptionLike(): Boolean =
    isSubtypeOfAnyQualifiedName(RUNTIME_EXCEPTION_LIKE_TYPE_QUALIFIED_NAMES)

fun LsiType.isSubtypeOfAnyQualifiedName(
    qualifiedNames: Set<String>,
    visited: MutableSet<String> = linkedSetOf(),
): Boolean {
    val currentTypeName = normalizedSemanticTypeName() ?: return false
    if (!visited.add(currentTypeName)) {
        return false
    }
    if (currentTypeName in qualifiedNames) {
        return true
    }
    val currentClass = lsiClass ?: return false
    return currentClass.superTypes.any { superType ->
        superType.isSubtypeOfAnyQualifiedName(qualifiedNames, visited)
    }
}

private fun LsiType.typeNameForTypeRegistry(): String? {
    val raw = qualifiedName ?: presentableText ?: simpleName ?: return null
    return raw.normalizeTypeName()
}

private fun LsiType.normalizedSemanticTypeName(): String? {
    val raw = qualifiedName ?: presentableText ?: simpleName ?: return null
    return raw.normalizeTypeName()
}

private fun String.normalizeTypeName(): String {
    var normalized = trim()
    normalized = normalized.substringBefore('<')
    normalized = normalized.removeSuffix("?").removeSuffix("!").trim()
    while (normalized.endsWith("[]")) {
        normalized = normalized.removeSuffix("[]")
    }
    return normalized
}
