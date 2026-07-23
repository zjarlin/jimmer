package org.babyfish.jimmer.compiler.dto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.jimmer.dto.fingerprint
import site.addzero.lsi.jimmer.dto.normalizedSnapshot

internal fun JimmerDtoPrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        documents.forEach { document ->
            val inputDocument = document.inputSnapshot.document
            appendRecord(
                "document",
                inputDocument.source.path,
                inputDocument.sourceSet.name,
                inputDocument.fingerprint,
                document.targetTypeIds.joinToString(",") { typeId -> typeId.value },
            )
            document.inputSnapshot.references.forEach { reference ->
                appendRecord(
                    "reference",
                    inputDocument.source.path,
                    reference.kind.name,
                    reference.typeSelector.canonicalText(),
                    reference.ownerTargetSelector?.canonicalText().orEmpty(),
                    reference.location.start.line.toString(),
                    reference.location.start.column.toString(),
                    reference.location.end.line.toString(),
                    reference.location.end.column.toString(),
                )
            }
            appendSemanticSnapshot(
                kind = "graph",
                documentPath = inputDocument.source.path,
                fingerprint = document.graph.fingerprint(),
                normalizedSnapshot = document.graph.normalizedSnapshot(),
            )
            appendSemanticSnapshot(
                kind = "annotation-contract",
                documentPath = inputDocument.source.path,
                fingerprint = document.annotationContract.fingerprint(),
                normalizedSnapshot = document.annotationContract.normalizedSnapshot(),
            )
            appendSemanticSnapshot(
                kind = "interface-contract",
                documentPath = inputDocument.source.path,
                fingerprint = document.interfaceContractResolution.fingerprint(),
                normalizedSnapshot = document.interfaceContractResolution.normalizedSnapshot(),
            )
            appendSemanticSnapshot(
                kind = "config-contract-resolution",
                documentPath = inputDocument.source.path,
                fingerprint = document.configContractResolution.fingerprint(),
                normalizedSnapshot = document.configContractResolution.normalizedSnapshot(),
            )
        }
    }
}

internal fun JimmerDtoPrecompiledSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalizedSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendSemanticSnapshot(
    kind: String,
    documentPath: String,
    fingerprint: String,
    normalizedSnapshot: String,
) {
    appendRecord(kind, documentPath, fingerprint)
    normalizedSnapshot
        .lineSequence()
        .filter(String::isNotEmpty)
        .forEachIndexed { index, record ->
            appendRecord(
                "$kind-record",
                documentPath,
                index.toString(),
                record,
            )
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
                else -> append(character)
            }
        }
    }
}
