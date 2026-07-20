package org.babyfish.jimmer.compiler.exportdoc

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun ExportDocPrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        effectiveConfigurationIds.forEach { configurationId ->
            appendRecord("configuration", configurationId.value)
        }
        exportedTypeIds.forEach { typeId ->
            appendRecord("type", typeId.value)
        }
        docs.forEach { doc ->
            appendRecord(
                "doc",
                doc.declarationId.value,
                doc.key,
                doc.content,
            )
        }
    }
}

fun ExportDocPrecompiledSchema.fingerprint(): String {
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
