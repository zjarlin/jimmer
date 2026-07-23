package site.addzero.lsi.jimmer.exportdoc

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 返回与输入顺序无关的 ExportDoc 规范快照。 */
fun ExportDocSchema.normalizedSnapshot(): String {
    return buildString {
        effectiveConfigurationIds.forEach { configurationId ->
            appendRecord("configuration", configurationId.value)
        }
        exportedTypeIds.forEach { typeId ->
            appendRecord("type", typeId.value)
        }
        entries.forEach { entry ->
            appendRecord(
                "doc",
                entry.declarationId.value,
                entry.key,
                entry.content,
            )
        }
    }
}

/** 返回 ExportDoc 规范快照的 SHA-256 指纹。 */
fun ExportDocSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalizedSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
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
                else -> append(character)
            }
        }
    }
}
