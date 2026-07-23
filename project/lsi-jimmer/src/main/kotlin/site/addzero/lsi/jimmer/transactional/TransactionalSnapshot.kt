package site.addzero.lsi.jimmer.transactional

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.stableSignature

/** 生成跨前端比较使用的 Transactional 规范化快照。 */
fun TransactionalSchema.normalizedSnapshot(): String {
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
                    constructor.parameters.map(TransactionalParameter::canonicalText).canonicalListText(),
                    constructor.typeParameters.map { parameter -> parameter.id.value }.canonicalListText(),
                    constructor.thrownTypes.map(LsiTypeRef::canonicalText).canonicalListText(),
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
                    method.parameters.map(TransactionalParameter::canonicalText).canonicalListText(),
                    method.typeParameters.map { parameter -> parameter.id.value }.canonicalListText(),
                    method.thrownTypes.map(LsiTypeRef::canonicalText).canonicalListText(),
                    method.propagation,
                    method.classLevel.toString(),
                )
            }
        }
    }
}

/** 计算完整 Transactional 生成语义的 SHA-256 指纹。 */
fun TransactionalSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(renderSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun TransactionalSchema.renderSnapshot(): String {
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
                    constructor.parameters.map(TransactionalParameter::renderSignature).canonicalListText(),
                    constructor.typeParameters.typeParameterSignatures(),
                    constructor.thrownTypes.map(LsiTypeRef::stableSignature).canonicalListText(),
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
                    method.parameters.map(TransactionalParameter::renderSignature).canonicalListText(),
                    method.typeParameters.typeParameterSignatures(),
                    method.thrownTypes.map(LsiTypeRef::stableSignature).canonicalListText(),
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
    ).canonicalListText()
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
        annotationProjectionTypeIds
            .map(LsiSymbolId::value)
            .sorted()
            .canonicalListText(),
    ).canonicalListText()
}

private fun Iterable<LsiAnnotation>.annotationSignatures(): String {
    return map(LsiAnnotation::stableSignature).canonicalListText()
}

private fun Iterable<LsiTypeParameter>.typeParameterSignatures(): String {
    return map(LsiTypeParameter::stableSignature).canonicalListText()
}

private fun LsiTypeRef.canonicalText(): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(
                    arguments
                        .map(LsiTypeArgument::canonicalText)
                        .canonicalListText()
                )
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
        is LsiFunctionType -> buildString {
            append(if (suspending) "suspend-function:" else "function:")
            receiverType?.let { receiver ->
                append(receiver.canonicalText())
                append('.')
            }
            append('(')
            append(parameterTypes.map(LsiTypeRef::canonicalText).canonicalListText())
            append(")->")
            append(returnType.canonicalText())
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    return base + if (nullability == site.addzero.lsi.model.LsiNullability.NULLABLE) "?" else "!"
}

private fun LsiTypeArgument.canonicalText(): String {
    return when (variance) {
        LsiVariance.STAR -> "*"
        LsiVariance.INVARIANT -> requireNotNull(type).canonicalText()
        LsiVariance.IN -> "in:${requireNotNull(type).canonicalText()}"
        LsiVariance.OUT -> "out:${requireNotNull(type).canonicalText()}"
    }
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

private fun Iterable<String>.canonicalListText(): String {
    return buildString {
        this@canonicalListText.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }
}
