package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

enum class LsiAnnotationTarget {
    PACKAGE,
    TYPE,
    ANNOTATION_TYPE,
    CONSTRUCTOR,
    FIELD,
    METHOD,
    PARAMETER,
    TYPE_USE,
    TYPE_PARAMETER,
    LOCAL_VARIABLE,
    MODULE,
    RECORD_COMPONENT,
    PROPERTY,
    GETTER,
    SETTER,
    EXPRESSION,
    FILE,
    TYPE_ALIAS
}

data class LsiAnnotationTargetPolicy(
    val declared: Boolean,
    val targets: Set<LsiAnnotationTarget>,
) {
    fun allows(target: LsiAnnotationTarget): Boolean {
        return !declared || target in targets
    }
}

fun LsiTypeDeclaration.annotationTargetPolicy(): LsiAnnotationTargetPolicy {
    val javaTarget = annotations.firstOrNull { annotation -> annotation.type == JAVA_TARGET }
    val kotlinTarget = annotations.firstOrNull { annotation -> annotation.type == KOTLIN_TARGET }
    if (javaTarget == null && kotlinTarget == null) {
        return LsiAnnotationTargetPolicy(declared = false, targets = emptySet())
    }
    val targets = buildSet {
        javaTarget?.enumEntryNames("value")?.mapNotNullTo(this, JAVA_TARGETS::get)
        kotlinTarget?.enumEntryNames("allowedTargets")?.mapNotNullTo(this, KOTLIN_TARGETS::get)
    }
    return LsiAnnotationTargetPolicy(declared = true, targets = targets)
}

private fun LsiAnnotation.enumEntryNames(argumentName: String): List<String> {
    return arguments[argumentName]?.value?.enumEntryNames().orEmpty()
}

private fun LsiAnnotationValue.enumEntryNames(): List<String> {
    return when (this) {
        is LsiAnnotationValue.EnumValue -> listOf(entryName)
        is LsiAnnotationValue.ArrayValue -> elements.flatMap(LsiAnnotationValue::enumEntryNames)
        else -> emptyList()
    }
}

private val JAVA_TARGET = LsiSymbolId.type("java.lang.annotation.Target")

private val KOTLIN_TARGET = LsiSymbolId.type("kotlin.annotation.Target")

private val JAVA_TARGETS = mapOf(
    "PACKAGE" to LsiAnnotationTarget.PACKAGE,
    "TYPE" to LsiAnnotationTarget.TYPE,
    "ANNOTATION_TYPE" to LsiAnnotationTarget.ANNOTATION_TYPE,
    "CONSTRUCTOR" to LsiAnnotationTarget.CONSTRUCTOR,
    "FIELD" to LsiAnnotationTarget.FIELD,
    "METHOD" to LsiAnnotationTarget.METHOD,
    "PARAMETER" to LsiAnnotationTarget.PARAMETER,
    "TYPE_USE" to LsiAnnotationTarget.TYPE_USE,
    "TYPE_PARAMETER" to LsiAnnotationTarget.TYPE_PARAMETER,
    "LOCAL_VARIABLE" to LsiAnnotationTarget.LOCAL_VARIABLE,
    "MODULE" to LsiAnnotationTarget.MODULE,
    "RECORD_COMPONENT" to LsiAnnotationTarget.RECORD_COMPONENT,
)

private val KOTLIN_TARGETS = mapOf(
    "CLASS" to LsiAnnotationTarget.TYPE,
    "ANNOTATION_CLASS" to LsiAnnotationTarget.ANNOTATION_TYPE,
    "CONSTRUCTOR" to LsiAnnotationTarget.CONSTRUCTOR,
    "FIELD" to LsiAnnotationTarget.FIELD,
    "FUNCTION" to LsiAnnotationTarget.METHOD,
    "PROPERTY_GETTER" to LsiAnnotationTarget.GETTER,
    "PROPERTY_SETTER" to LsiAnnotationTarget.SETTER,
    "PROPERTY" to LsiAnnotationTarget.PROPERTY,
    "VALUE_PARAMETER" to LsiAnnotationTarget.PARAMETER,
    "TYPE" to LsiAnnotationTarget.TYPE_USE,
    "TYPE_PARAMETER" to LsiAnnotationTarget.TYPE_PARAMETER,
    "LOCAL_VARIABLE" to LsiAnnotationTarget.LOCAL_VARIABLE,
    "EXPRESSION" to LsiAnnotationTarget.EXPRESSION,
    "FILE" to LsiAnnotationTarget.FILE,
    "TYPEALIAS" to LsiAnnotationTarget.TYPE_ALIAS,
)
