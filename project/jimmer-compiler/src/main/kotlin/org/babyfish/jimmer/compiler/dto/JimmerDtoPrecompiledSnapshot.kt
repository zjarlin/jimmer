package org.babyfish.jimmer.compiler.dto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.babyfish.jimmer.dto.compiler.DtoModifier

internal fun JimmerDtoPrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        documents.forEach { document ->
            appendRecord(
                "document",
                document.inputDocument.source.path,
                document.inputDocument.sourceSet.name,
                document.inputDocument.fingerprint,
                document.baseTypeId.value,
                document.sourceTypeName,
                document.targetPackageName.orEmpty(),
            )
            document.dtoTypes.forEachIndexed { index, dtoType ->
                appendRecord(
                    "type",
                    document.inputDocument.source.path,
                    index.toString(),
                    dtoType.baseType.id.value,
                    dtoType.packageName,
                    dtoType.name.orEmpty(),
                    dtoType.modifiers
                        .sortedBy(DtoModifier::getOrder)
                        .joinToString(",", transform = DtoModifier::name),
                    dtoType.toString(),
                )
            }
        }
    }
}

internal fun JimmerDtoPrecompiledSchema.fingerprint(): String {
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
                ',' -> append("\\,")
                else -> append(character)
            }
        }
    }
}
