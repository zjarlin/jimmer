package site.addzero.lsi.apt.clazz

import site.addzero.lsi.apt.context.AptLsiContext
import site.addzero.lsi.apt.diagnostic.AptLsiDiagnostics
import javax.lang.model.element.TypeElement

private const val KOTLIN_METADATA_ANNOTATION = "kotlin.Metadata"
private const val IMMUTABLE_ANNOTATION = "org.babyfish.jimmer.Immutable"
private val SQL_TYPE_ANNOTATIONS =
    listOf(
        "org.babyfish.jimmer.sql.Entity",
        "org.babyfish.jimmer.sql.MappedSuperclass",
        "org.babyfish.jimmer.sql.Embeddable",
    )

fun TypeElement.immutableAnnotationQualifiedNameOrNull(): String? {
    val annotationQualifiedNames = annotationQualifiedNames()
    val immutableAnnotation = annotationQualifiedNames.firstOrNull { it == IMMUTABLE_ANNOTATION }
    var sqlAnnotationQualifiedName: String? = null
    for (qualifiedName in SQL_TYPE_ANNOTATIONS) {
        if (qualifiedName !in annotationQualifiedNames) {
            continue
        }
        if (sqlAnnotationQualifiedName != null) {
            throw AptLsiDiagnostics.metaException(
                this,
                "Type '$qualifiedName' cannot be decorated by both @$sqlAnnotationQualifiedName and @$qualifiedName"
            )
        }
        sqlAnnotationQualifiedName = qualifiedName
    }
    return sqlAnnotationQualifiedName ?: immutableAnnotation
}

fun TypeElement.isImmutableType(): Boolean =
    immutableAnnotationQualifiedNameOrNull() != null

fun TypeElement.matchesConfiguredSourceFilters(): Boolean {
    val annotationQualifiedNames = annotationQualifiedNames()
    if (annotationQualifiedNames.contains(KOTLIN_METADATA_ANNOTATION)) {
        return false
    }
    val qualifiedName = qualifiedName.toString()
    val includes = AptLsiContext.options["jimmer.source.includes"].toFilterPrefixes()
    val excludes = AptLsiContext.options["jimmer.source.excludes"].toFilterPrefixes()
    if (includes.isNotEmpty() && includes.none { qualifiedName.startsWith(it) }) {
        return false
    }
    if (excludes.isNotEmpty() && excludes.any { qualifiedName.startsWith(it) }) {
        return false
    }
    return true
}

private fun TypeElement.annotationQualifiedNames(): Set<String> =
    annotationMirrors.mapNotNullTo(linkedSetOf()) { annotationMirror ->
        annotationMirror.annotationType.asElement().toString().takeIf { it.isNotEmpty() }
    }

private fun String?.toFilterPrefixes(): List<String> =
    this
        ?.takeIf { it.isNotBlank() }
        ?.split(Regex("\\s*,\\s*"))
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
