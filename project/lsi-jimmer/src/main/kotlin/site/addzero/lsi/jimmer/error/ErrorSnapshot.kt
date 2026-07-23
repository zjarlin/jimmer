package site.addzero.lsi.jimmer.error

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

/** 生成与前端无关且顺序稳定的 Error 语义快照。 */
fun ErrorSchema.normalizedSnapshot(): String {
    return buildString {
        families.sortedBy(ErrorFamily::id).forEach { family ->
            appendRecord(
                "family",
                family.id.value,
                family.qualifiedName,
                family.packageName,
                family.family,
                family.exceptionTypeId.value,
                family.exceptionSimpleName,
                family.checkedException.toString(),
                family.documentation.orEmpty(),
            )
            family.declaredFields.forEach { field ->
                appendField("family-field", family.id.value, field)
            }
            family.codes.forEach { code ->
                appendRecord(
                    "code",
                    family.id.value,
                    code.id.value,
                    code.enumEntryName,
                    code.code,
                    code.creatorName,
                    code.exceptionTypeId.value,
                    code.exceptionSimpleName,
                    code.documentation.orEmpty(),
                )
                code.declaredFields.forEach { field ->
                    appendField("code-field", code.id.value, field)
                }
            }
        }
    }
}

/** 计算 Error 语义快照的 SHA-256 指纹。 */
fun ErrorSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalizedSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendField(
    kind: String,
    owner: String,
    field: ErrorField,
) {
    appendRecord(
        kind,
        owner,
        field.name,
        field.type.canonicalText(),
        field.list.toString(),
        field.nullable.toString(),
        field.documentation.orEmpty(),
        field.declaredBy.value,
    )
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
                else -> append(character)
            }
        }
    }
}

private fun LsiTypeRef.canonicalText(): String {
    return when (this) {
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
        is LsiFunctionType -> buildString {
            append(if (suspending) "suspend-function:" else "function:")
            receiverType?.let { receiver ->
                append(receiver.canonicalText())
                append('.')
            }
            append('(')
            append(parameterTypes.joinToString(",", transform = LsiTypeRef::canonicalText))
            append(")->")
            append(returnType.canonicalText())
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
}
