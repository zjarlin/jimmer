package org.babyfish.jimmer.compiler.immutable

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

fun JimmerImmutableSchema.normalizedSnapshot(): String {
    return buildString {
        types.sortedBy(JimmerImmutableType::id).forEach { type ->
            appendRecord(
                "type",
                type.id.value,
                type.qualifiedName,
                type.kind.name,
                type.typeParameterIds.joinToString(",") { id -> id.value },
                type.superTypeIds.joinToString(",") { id -> id.value },
                type.primarySuperTypeId?.value.orEmpty(),
            )
            type.props.forEach { prop ->
                appendRecord(
                    "prop",
                    type.id.value,
                    prop.id.value,
                    prop.declarationId.value,
                    prop.declaringTypeId.value,
                    prop.name,
                    prop.type.normalizedTypeSignature(),
                    prop.annotations.canonicalText(),
                    prop.overrideChain.joinToString(",") { id -> id.value },
                    prop.inherited.toString(),
                    prop.overridden.toString(),
                    prop.nullable.toString(),
                    prop.list.toString(),
                    prop.association.toString(),
                    prop.embedded.toString(),
                    prop.targetTypeId?.value.orEmpty(),
                    prop.primaryMapping.name,
                    prop.primaryAnnotationTypeId?.value.orEmpty(),
                    prop.associationKind.name,
                    prop.formulaKind.name,
                    prop.viewKind.name,
                )
                prop.validations.sortedBy(JimmerValidation::annotationTypeId).forEach { validation ->
                    appendRecord(
                        "validation",
                        prop.id.value,
                        validation.annotationTypeId.value,
                        validation.validatorTypeIds.joinToString(",") { id -> id.value },
                        validation.message,
                    )
                }
                prop.converter?.let { converter ->
                    appendRecord(
                        "converter",
                        prop.id.value,
                        converter.converterTypeId.value,
                        converter.sourceType?.normalizedTypeSignature().orEmpty(),
                        converter.targetType?.normalizedTypeSignature().orEmpty(),
                        converter.sourceNullable.toString(),
                        converter.targetNullable.toString(),
                        converter.propertyNullable.toString(),
                    )
                }
            }
        }
    }
}

fun JimmerImmutableSchema.fingerprint(): String {
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

private fun List<LsiAnnotation>.canonicalText(): String {
    return map(LsiAnnotation::canonicalText).sorted().joinToString(";")
}

private fun LsiAnnotation.canonicalText(): String {
    return buildString {
        append(type.value)
        append('(')
        append(
            arguments.toSortedMap().entries.joinToString(",") { (name, argument) ->
                "$name=${argument.value.canonicalText()}"
            }
        )
        append(')')
    }
}

private fun LsiAnnotationValue.canonicalText(): String {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> "boolean:$value"
        is LsiAnnotationValue.ByteValue -> "byte:$value"
        is LsiAnnotationValue.ShortValue -> "short:$value"
        is LsiAnnotationValue.IntValue -> "int:$value"
        is LsiAnnotationValue.LongValue -> "long:$value"
        is LsiAnnotationValue.FloatValue -> "float:$value"
        is LsiAnnotationValue.DoubleValue -> "double:$value"
        is LsiAnnotationValue.CharValue -> "char:${value.code}"
        is LsiAnnotationValue.StringValue -> "string:${value.escapeSnapshotField()}"
        is LsiAnnotationValue.EnumValue -> "enum:${enumType.value}:$entryName"
        is LsiAnnotationValue.ClassValue -> "class:${type.canonicalTypeText()}"
        is LsiAnnotationValue.NestedAnnotationValue -> "annotation:${annotation.canonicalText()}"
        is LsiAnnotationValue.ArrayValue -> elements.joinToString(",", "array:[", "]") { element ->
            element.canonicalText()
        }
    }
}

private fun LsiTypeRef.canonicalTypeText(): String {
    return when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument ->
                    argument.type?.canonicalTypeText() ?: "*"
                })
                append('>')
            }
        }
        is LsiPrimitiveType -> "primitive:${kind.name.lowercase()}"
        is LsiArrayType -> "array:${elementType.canonicalTypeText()}"
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
}
