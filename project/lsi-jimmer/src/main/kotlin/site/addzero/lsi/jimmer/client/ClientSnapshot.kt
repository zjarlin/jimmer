package site.addzero.lsi.jimmer.client

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiVariance

/** 生成可用于跨前端比较的稳定 Client 快照。 */
fun ClientSchema.normalizedSnapshot(): String {
    return buildString {
        services.sortedBy(ClientService::id).forEach { service ->
            appendRecord(
                "service",
                service.id.value,
                service.qualifiedName,
                service.groups.canonicalListText(),
                service.doc.orEmpty(),
            )
            service.operations.forEach { operation ->
                appendRecord(
                    "operation",
                    service.id.value,
                    operation.id.value,
                    operation.name,
                    operation.groups.canonicalListText(),
                    operation.doc.orEmpty(),
                    operation.returnType?.canonicalText().orEmpty(),
                    operation.declaredExceptionTypeIds.map { id -> id.value }.canonicalListText(),
                    operation.exceptionTypeIds.map { id -> id.value }.canonicalListText(),
                )
                operation.parameters.sortedBy(ClientParameter::originalIndex).forEach { parameter ->
                    appendRecord(
                        "parameter",
                        operation.id.value,
                        parameter.id.value,
                        parameter.originalIndex.toString(),
                        parameter.name,
                        parameter.type.canonicalText(),
                    )
                }
                operation.ignoredParameters.sortedBy(ClientIgnoredParameter::originalIndex).forEach { parameter ->
                    appendRecord(
                        "ignored-parameter",
                        operation.id.value,
                        parameter.id.value,
                        parameter.originalIndex.toString(),
                        parameter.name,
                    )
                }
                operation.exceptionMetadata.sortedBy(ClientExceptionMetadata::typeId).forEach { metadata ->
                    appendRecord(
                        "exception",
                        operation.id.value,
                        metadata.typeId.value,
                        metadata.errorFamilyId?.value.orEmpty(),
                        metadata.family,
                        metadata.code.orEmpty(),
                        metadata.checked.toString(),
                        metadata.abstract.toString(),
                        metadata.superTypeId?.value.orEmpty(),
                        metadata.subTypeIds.map { typeId -> typeId.value }.canonicalListText(),
                        metadata.documentation.orEmpty(),
                    )
                }
            }
        }
        definitions.sortedBy(ClientTypeDefinition::id).forEach { definition ->
            appendRecord(
                "definition",
                definition.id.value,
                definition.typeName.canonicalText(),
                definition.kind.name,
                definition.apiIgnore.toString(),
                definition.doc.orEmpty(),
                definition.error?.family.orEmpty(),
                definition.error?.code.orEmpty(),
            )
            definition.properties.forEach { property ->
                appendRecord(
                    "definition-property",
                    definition.id.value,
                    property.id.value,
                    property.name,
                    property.type.canonicalText(),
                    property.doc.orEmpty(),
                )
            }
            definition.superTypes
                .map(ClientTypeRef::canonicalText)
                .forEach { superType ->
                    appendRecord("definition-super", definition.id.value, superType)
                }
            definition.polymorphicBranches.forEach { branch ->
                appendRecord(
                    "definition-branch",
                    definition.id.value,
                    branch.canonicalText(),
                )
            }
            definition.enumConstants.forEach { constant ->
                appendRecord(
                    "definition-enum",
                    definition.id.value,
                    constant.id.value,
                    constant.name,
                    constant.doc.orEmpty(),
                )
            }
        }
    }
}

/** 计算完整 Client 语义快照的 SHA-256 指纹。 */
fun ClientSchema.fingerprint(): String {
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

private fun ClientTypeRef.canonicalText(): String {
    val typeText = when (this) {
        is ClientDeclaredTypeRef -> buildString {
            append(typeId.value)
            append('[')
            append(typeName.canonicalText())
            append(']')
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.map(ClientTypeArgument::canonicalText).canonicalListText())
                append('>')
            }
        }
        is ClientPrimitiveTypeRef -> "primitive:${kind.name.lowercase()}"
        is ClientArrayTypeRef -> "array:${elementType.canonicalText()}"
        is ClientTypeParameterRef -> buildString {
            append("parameter:")
            append(parameterId.value)
            append('[')
            append(ownerTypeName.canonicalText())
            append("::")
            append(name)
            append(']')
        }
        is ClientUnresolvedTypeRef -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    return buildString {
        append(typeText)
        append(if (nullable) "?" else "!")
        fetchBy?.let { fetchBy ->
            append("@fetchBy(")
            append(
                listOf(
                    fetchBy.value,
                    fetchBy.ownerTypeId.value,
                    fetchBy.ownerTypeName.canonicalText(),
                    fetchBy.targetEntityTypeId.value,
                    fetchBy.documentation.orEmpty(),
                ).canonicalListText()
            )
            append(')')
        }
    }
}

private fun ClientTypeName.canonicalText(): String {
    return listOf(
        packageName.orEmpty(),
        simpleNames.canonicalListText(),
    ).canonicalListText()
}

private fun ClientTypeArgument.canonicalText(): String {
    return when (variance) {
        LsiVariance.STAR -> "*"
        LsiVariance.INVARIANT -> requireNotNull(type).canonicalText()
        LsiVariance.IN -> "in:${requireNotNull(type).canonicalText()}"
        LsiVariance.OUT -> "out:${requireNotNull(type).canonicalText()}"
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
