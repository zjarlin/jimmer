package org.babyfish.jimmer.compiler.transactional

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.stableSignature

fun TransactionalPrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        types.sortedBy(TransactionalType::id).forEach { type ->
            appendRecord(
                "type",
                type.id.value,
                type.qualifiedName,
                type.packageName,
                type.simpleName,
                type.generatedSimpleName,
                type.visibility.name,
                type.modality.name,
                type.targetAnnotationTypeId?.value.orEmpty(),
            )
            appendRecord(
                "sql-client",
                type.id.value,
                type.sqlClient.logicalId.value,
                type.sqlClient.name,
            )
            type.constructors.sortedBy(TransactionalConstructor::id).forEach { constructor ->
                appendRecord(
                    "constructor",
                    type.id.value,
                    constructor.id.value,
                    constructor.visibility.name,
                    constructor.parameters.joinToString(",") { parameter -> parameter.canonicalText() },
                    constructor.typeParameters.joinToString(",") { parameter -> parameter.id.value },
                    constructor.thrownTypes.joinToString(",") { thrownType -> thrownType.canonicalText() },
                )
            }
            type.methods.sortedBy(TransactionalMethod::id).forEach { method ->
                appendRecord(
                    "method",
                    type.id.value,
                    method.id.value,
                    method.name,
                    method.sourceKind.name,
                    method.visibility.name,
                    method.modality.name,
                    method.returnType.canonicalText(),
                    method.parameters.joinToString(",") { parameter -> parameter.canonicalText() },
                    method.typeParameters.joinToString(",") { parameter -> parameter.id.value },
                    method.thrownTypes.joinToString(",") { thrownType -> thrownType.canonicalText() },
                    method.propagation,
                    method.classLevel.toString(),
                )
            }
        }
    }
}

fun TransactionalPrecompiledSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(renderSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun TransactionalPrecompiledSchema.renderSnapshot(): String {
    return buildString {
        types.forEach { type ->
            appendRecord(
                "render-type",
                type.id.value,
                type.qualifiedName,
                type.packageName,
                type.simpleName,
                type.generatedSimpleName,
                type.visibility.name,
                type.modality.name,
                type.copiedAnnotations.annotationSignatures(),
                type.targetAnnotationTypeId?.value.orEmpty(),
            )
            appendRecord(
                "render-sql-client",
                type.id.value,
                type.sqlClient.logicalId.value,
                type.sqlClient.declarationId.value,
                type.sqlClient.name,
                type.sqlClient.type.stableSignature(),
                type.sqlClient.platform.name,
            )
            type.constructors.forEach { constructor ->
                appendRecord(
                    "render-constructor",
                    type.id.value,
                    constructor.id.value,
                    constructor.primary.toString(),
                    constructor.visibility.name,
                    constructor.parameters.joinToString(",") { parameter -> parameter.renderSignature() },
                    constructor.typeParameters.typeParameterSignatures(),
                    constructor.thrownTypes.joinToString(",") { thrownType -> thrownType.stableSignature() },
                    constructor.documentation.orEmpty(),
                    constructor.copiedAnnotations.annotationSignatures(),
                )
            }
            type.methods.forEach { method ->
                appendRecord(
                    "render-method",
                    type.id.value,
                    method.id.value,
                    method.name,
                    method.sourceKind.name,
                    method.visibility.name,
                    method.modality.name,
                    method.returnType.stableSignature(),
                    method.parameters.joinToString(",") { parameter -> parameter.renderSignature() },
                    method.typeParameters.typeParameterSignatures(),
                    method.thrownTypes.joinToString(",") { thrownType -> thrownType.stableSignature() },
                    method.documentation.orEmpty(),
                    method.copiedAnnotations.annotationSignatures(),
                    method.propagation,
                    method.classLevel.toString(),
                )
            }
        }
    }
}

private fun TransactionalParameter.canonicalText(): String {
    return listOf(
        index.toString(),
        name,
        type.canonicalText(),
        vararg.toString(),
        hasDefault.toString(),
    ).joinToString(":")
}

private fun TransactionalParameter.renderSignature(): String {
    return listOf(
        id.value,
        index.toString(),
        name,
        type.stableSignature(),
        vararg.toString(),
        hasDefault.toString(),
        annotations.annotationSignatures(),
    ).joinToString(":")
}

private fun Iterable<LsiAnnotation>.annotationSignatures(): String {
    return joinToString(",") { annotation -> annotation.stableSignature() }
}

private fun Iterable<LsiTypeParameter>.typeParameterSignatures(): String {
    return joinToString(",") { parameter -> parameter.stableSignature() }
}

private fun LsiTypeRef.canonicalText(): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.type?.canonicalText() ?: "*" })
                append('>')
            }
        }
        is LsiPrimitiveType -> buildString {
            append("primitive:${kind.name.lowercase()}")
            if (boxed) {
                append(":boxed")
            }
        }
        is LsiArrayType -> "array:${elementType.canonicalText()}"
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    return base + if (nullability == site.addzero.lsi.model.LsiNullability.NULLABLE) "?" else "!"
}

private fun StringBuilder.appendRecord(
    kind: String,
    vararg fields: String,
) {
    append(kind)
    fields.forEach { field ->
        append('|')
        append(field.escapeSnapshotField())
    }
    append('\n')
}

private fun String.escapeSnapshotField(): String {
    return buildString {
        for (character in this@escapeSnapshotField) {
            when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '|' -> append("\\|")
                ',' -> append("\\,")
                ':' -> append("\\:")
                else -> append(character)
            }
        }
    }
}
