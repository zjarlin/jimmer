package site.addzero.lsi.jimmer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

fun ImmutableSchema.normalizedSnapshot(): String {
    return buildString {
        types.sortedBy(ImmutableType::id).forEach { type ->
            appendRecord(
                "type",
                type.id.value,
                type.qualifiedName,
                type.kind.name,
                type.typeParameterIds.joinToString(",") { id -> id.value },
                type.superTypeIds.joinToString(",") { id -> id.value },
                type.primarySuperTypeId?.value.orEmpty(),
                type.documentation.orEmpty(),
                type.annotations.canonicalText(),
                type.instantiable.toString(),
                type.inheritanceRootTypeId?.value.orEmpty(),
                type.inheritanceStrategy?.name.orEmpty(),
                type.joinedTableDissociateAction?.name.orEmpty(),
                type.discriminatorValue.orEmpty(),
                type.discriminatorPropId?.value.orEmpty(),
                type.idPropId?.value.orEmpty(),
                type.versionPropId?.value.orEmpty(),
                type.logicalDeletedPropId?.value.orEmpty(),
                type.acrossMicroServices.toString(),
                type.microServiceName,
            )
            type.props.forEach { prop ->
                appendRecord(
                    "prop",
                    type.id.value,
                    prop.id.value,
                    prop.declarationId.value,
                    prop.declaringTypeId.value,
                    prop.name,
                    prop.documentation.orEmpty(),
                    prop.type.jimmerTypeSignature(),
                    prop.annotations.semanticCanonicalText(),
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
                    when (prop.defaultContract) {
                        null -> ""
                        is ImmutableDefault.Application -> "APPLICATION"
                        is ImmutableDefault.Database -> "DATABASE"
                    },
                    when (val default = prop.defaultContract) {
                        null -> ""
                        is ImmutableDefault.Application -> (default.annotationValue != null).toString()
                        is ImmutableDefault.Database -> "true"
                    },
                    when (val default = prop.defaultContract) {
                        null -> ""
                        is ImmutableDefault.Application -> default.annotationValue.orEmpty()
                        is ImmutableDefault.Database -> default.expression.orEmpty()
                    },
                    when (val default = prop.defaultContract) {
                        null -> ""
                        is ImmutableDefault.Application -> default.strategy?.name.orEmpty()
                        is ImmutableDefault.Database -> "DATABASE"
                    },
                    prop.associationKind.name,
                    prop.associationStorage.name,
                    prop.reverse.toString(),
                    prop.formulaKind.name,
                    prop.fetchable.toString(),
                    when (prop.view) {
                        null -> ""
                        is ImmutableView.Id -> "ID"
                        is ImmutableView.ManyToMany -> "MANY_TO_MANY"
                    },
                    prop.view?.dependencyPropIds?.joinToString(",") { propId -> propId.value }.orEmpty(),
                    prop.genericTarget.toString(),
                    prop.remote.toString(),
                    prop.recursive.toString(),
                )
                prop.validations.sortedBy(ImmutableValidation::annotationTypeId).forEach { validation ->
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
                        converter.sourceType?.jimmerTypeSignature().orEmpty(),
                        converter.targetType?.jimmerTypeSignature().orEmpty(),
                        converter.sourceNullable.toString(),
                        converter.targetNullable.toString(),
                        converter.propertyNullable.toString(),
                    )
                }
                prop.mappedBy?.let { mappedBy ->
                    appendRecord(
                        "mapped-by",
                        prop.id.value,
                        mappedBy.name,
                        mappedBy.ownerPropId?.value.orEmpty(),
                    )
                }
                prop.transientResolver?.let { resolver ->
                    appendRecord(
                        "transient-resolver",
                        prop.id.value,
                        when (resolver) {
                            is TransientResolver.Type -> "TYPE"
                            is TransientResolver.Reference -> "REFERENCE"
                        },
                        when (resolver) {
                            is TransientResolver.Type -> resolver.typeId.value
                            is TransientResolver.Reference -> resolver.beanName
                        },
                    )
                }
                prop.formulaDependencies.forEach { dependency ->
                    appendRecord(
                        "formula-dependency",
                        prop.id.value,
                        dependency.propIds.joinToString(",") { propId -> propId.value },
                    )
                }
            }
        }
    }
}

fun ImmutableSchema.fingerprint(): String {
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

private fun List<LsiAnnotation>.semanticCanonicalText(): String {
    return filterNot { annotation -> annotation.type.annotationNullability() != null }
        .canonicalText()
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
        is LsiPrimitiveType -> {
            val semanticKind = if (kind == LsiPrimitiveKind.UNIT) {
                LsiPrimitiveKind.VOID
            } else {
                kind
            }
            buildString {
                append("primitive:${semanticKind.name.lowercase()}")
                if (boxed) {
                    append(":boxed")
                }
            }
        }
        is LsiArrayType -> "array:${elementType.canonicalTypeText()}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.canonicalTypeText())
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter -> parameter.canonicalTypeText() })
            append("]:return:")
            append(returnType.canonicalTypeText())
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
}
