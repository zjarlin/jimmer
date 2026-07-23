package org.babyfish.jimmer.compiler.dto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.fingerprint

internal fun List<JimmerDtoResolvedInput>.resolvedInputFingerprint(): String {
    require(this == sortedBy(JimmerDtoResolvedInput::inputSnapshot)) {
        "Resolved DTO inputs must use stable input order"
    }
    return sha256(
        buildString {
            this@resolvedInputFingerprint.forEach { input ->
                val document = input.inputSnapshot.document
                appendRecord(
                    "document",
                    document.source.path,
                    document.sourceSet.name,
                    document.fingerprint,
                    input.targetTypeIds.joinToString(",") { typeId -> typeId.value },
                )
                input.inputSnapshot.references.forEach { reference ->
                    appendRecord(
                        "reference",
                        document.source.path,
                        reference.kind.name,
                        reference.typeSelector.canonicalText(),
                        reference.ownerTargetSelector?.canonicalText().orEmpty(),
                        reference.location.start.line.toString(),
                        reference.location.start.column.toString(),
                        reference.location.end.line.toString(),
                        reference.location.end.column.toString(),
                    )
                }
            }
        }
    )
}

internal fun dtoSemanticFingerprint(
    graphs: List<DtoGraph>,
    annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
): String {
    requireDtoResolvedContracts(
        graphs = graphs,
        annotationContractsBySource = annotationContractsBySource,
        interfaceContractsBySource = interfaceContractsBySource,
        configContractsBySource = configContractsBySource,
    )
    return sha256(
        buildString {
            graphs.forEach { graph ->
                val source = graph.source
                appendRecord(
                    "semantic",
                    source.path,
                    source.language.name,
                    source.kind.name,
                    graph.fingerprint(),
                    annotationContractsBySource.getValue(source).fingerprint(),
                    interfaceContractsBySource.getValue(source).fingerprint(),
                    configContractsBySource.getValue(source).fingerprint(),
                )
            }
        }
    )
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendRecord(
    kind: String,
    vararg fields: String,
) {
    append(kind)
    fields.forEach { field ->
        append('|')
        append(field.escapeFingerprintField())
    }
    append('\n')
}

private fun String.escapeFingerprintField(): String {
    return buildString {
        for (character in this@escapeFingerprintField) {
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
