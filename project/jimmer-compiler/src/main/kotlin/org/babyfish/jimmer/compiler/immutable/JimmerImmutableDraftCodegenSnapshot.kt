package org.babyfish.jimmer.compiler.immutable

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.stableSignature

internal fun JimmerImmutableDraftCodegenSchema.normalizedSnapshot(): String {
    return snapshot(includePlatformSurface = false)
}

internal fun JimmerImmutableDraftCodegenSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(snapshot(includePlatformSurface = true).toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun JimmerImmutableDraftCodegenSchema.snapshot(includePlatformSurface: Boolean): String {
    return buildString {
        appendDraftRecord("draft-schema", jacksonFamily.name)
        types.sortedBy(JimmerImmutableDraftTypePlan::typeId).forEach { type ->
            appendDraftRecord(
                "draft-type",
                type.typeId.value,
                type.qualifiedName,
                type.kind.name,
                if (includePlatformSurface) type.visibility.name else "",
                type.typeParameters.joinToString(",", transform = LsiTypeParameter::snapshotText),
                type.selfType.typeText(includePlatformSurface),
                type.directSuperTypes.joinToString(",") { superType ->
                    superType.typeText(includePlatformSurface)
                },
                type.primarySuperTypeId?.value.orEmpty(),
                type.runtimeDeclaredPropIds.joinToString(",") { propId -> propId.value },
                type.runtimeRedefinedPropIds.joinToString(",") { propId -> propId.value },
                type.kotlinDraftPropIds.joinToString(",") { propId -> propId.value },
                type.idPropId?.value.orEmpty(),
                type.versionPropId?.value.orEmpty(),
                type.logicalDeletedPropId?.value.orEmpty(),
                type.requiresVisibilityState.toString(),
                type.customValidations.validationText(includePlatformSurface),
                type.artifactOriginatingSymbols.sorted().joinToString(",") { symbolId -> symbolId.value },
                if (includePlatformSurface) {
                    type.dependencySymbols.sorted().joinToString(",") { symbolId -> symbolId.value }
                } else {
                    ""
                },
                if (includePlatformSurface) type.sourceLanguage.name else "",
                if (includePlatformSurface) type.sourcePath.orEmpty() else "",
                if (includePlatformSurface) type.sourceBaseName.orEmpty() else "",
                if (includePlatformSurface) {
                    type.artifactOriginatingSources.joinToString(",") { source ->
                        "${source.path}:${source.language.name}:${source.kind.name}"
                    }
                } else {
                    ""
                },
                if (includePlatformSurface) {
                    type.dependencySources.joinToString(",") { source ->
                        "${source.path}:${source.language.name}:${source.kind.name}"
                    }
                } else {
                    ""
                },
            )
            type.propsBySlot.forEach { prop ->
                appendDraftRecord(
                    "draft-prop",
                    type.typeId.value,
                    prop.propId.value,
                    prop.declarationId.value,
                    prop.lineageRootId.value,
                    prop.sourceDeclaringTypeId.value,
                    prop.runtimeOwnerTypeId.value,
                    prop.slotIndex.toString(),
                    prop.metadataSlotIndex?.toString().orEmpty(),
                    prop.role.name,
                    prop.name,
                    prop.type.typeText(includePlatformSurface),
                    prop.elementType.typeText(includePlatformSurface),
                    prop.runtimeProp.kind.name,
                    prop.runtimeProp.valueCategory.name,
                    prop.runtimeProp.associationAnnotationTypeId?.value.orEmpty(),
                    prop.runtimeProp.metadataElementType.typeText(includePlatformSurface),
                    prop.targetTypeId?.value.orEmpty(),
                    prop.targetIdPropId?.value.orEmpty(),
                    prop.primitive.toString(),
                    prop.nullable.toString(),
                    prop.list.toString(),
                    prop.association.toString(),
                    prop.immutableReference.toString(),
                    prop.genericTarget.toString(),
                    prop.genericSourceTarget.toString(),
                    prop.languageFormula.toString(),
                    prop.valueState.name,
                    prop.visibilityControllable.toString(),
                    prop.writable.toString(),
                    prop.autoCreateSupported.toString(),
                    prop.referenceMutationSupported.toString(),
                    prop.idViewBasePropId?.value.orEmpty(),
                    prop.manyToManyBasePropId?.value.orEmpty(),
                    prop.manyToManyDeeperPropId?.value.orEmpty(),
                    prop.formulaDependencyPaths.joinToString(";") { path ->
                        path.joinToString(",") { propId -> propId.value }
                    },
                    if (includePlatformSurface) prop.associatedId?.name.orEmpty() else "",
                    prop.associatedId?.targetIdPropId?.value.orEmpty(),
                    prop.validationPlan.validationText(includePlatformSurface),
                    if (includePlatformSurface) {
                        prop.annotationPlan.builderMethodAnnotations.annotationText(true)
                    } else {
                        ""
                    },
                    if (includePlatformSurface) {
                        prop.annotationPlan.beanBridgeMethodAnnotations.annotationText(true)
                    } else {
                        ""
                    },
                    if (includePlatformSurface) prop.codegenName else "",
                    if (includePlatformSurface) prop.slotName else "",
                    if (includePlatformSurface) prop.javaApplierName else "",
                    if (includePlatformSurface) prop.javaAdderByName else "",
                    if (includePlatformSurface) prop.valueFieldName.orEmpty() else "",
                    if (includePlatformSurface) prop.loadedStateFieldName.orEmpty() else "",
                    if (includePlatformSurface) prop.javaDeeperPropIdName.orEmpty() else "",
                    if (includePlatformSurface) prop.kotlinDeeperPropIdName.orEmpty() else "",
                    if (includePlatformSurface) prop.sourceGetterName else "",
                    if (includePlatformSurface) prop.documentation.orEmpty() else "",
                    if (includePlatformSurface) prop.sourceDocumentation.orEmpty() else "",
                    if (includePlatformSurface) prop.accessorStyle.name else "",
                    if (includePlatformSurface) prop.javaSetterName else "",
                    if (includePlatformSurface) prop.javaBeanGetterName else "",
                )
            }
        }
    }
}

private fun LsiTypeParameter.snapshotText(): String {
    return buildString {
        append(id.value)
        append(':')
        append(name)
        append(':')
        append(variance.name)
        append(':')
        append(upperBounds.joinToString("&") { bound -> bound.jimmerTypeSignature() })
    }
}

private fun site.addzero.lsi.model.LsiTypeRef.typeText(includePlatformSurface: Boolean): String {
    return if (includePlatformSurface) stableSignature() else jimmerTypeSignature()
}

private fun List<ImmutableValidation>.validationText(includePlatformSurface: Boolean): String {
    return joinToString(";") { validation ->
        buildString {
            append(validation.annotationTypeId.value)
            if (includePlatformSurface) {
                append('@')
                append(validation.sourceAnnotationUseSiteTarget?.name.orEmpty())
            }
            append('(')
            append(validation.validatorTypeIds.joinToString(",") { typeId -> typeId.value })
            append(')')
            append('=')
            append(validation.message)
        }
    }
}

private fun JimmerImmutableDraftValidationPlan.validationText(includePlatformSurface: Boolean): String {
    return buildString {
        requiredNullCheck?.let { required ->
            append("required(")
            append(required.message)
            append(");")
        }
        steps.forEach { step ->
            when (step) {
                is JimmerImmutableDraftValidationStep.NotEmpty -> append("not-empty")
                is JimmerImmutableDraftValidationStep.NotBlank -> append("not-blank")
                is JimmerImmutableDraftValidationStep.Size -> {
                    append("size:")
                    append(step.measure.name)
                    append(':')
                    append(step.comparison.name)
                    append(':')
                    append(step.limit)
                }
                is JimmerImmutableDraftValidationStep.NumericBound -> {
                    append("numeric:")
                    append(step.target.name)
                    append(':')
                    append(step.comparison.name)
                    append(':')
                    append(step.bound)
                }
                is JimmerImmutableDraftValidationStep.Email -> append("email")
                is JimmerImmutableDraftValidationStep.Pattern -> {
                    append("pattern:")
                    append(step.index)
                    append(':')
                    append(step.regexp)
                    append(':')
                    append(step.flags.joinToString(",", transform = JimmerImmutableDraftPatternFlag::name))
                    append(':')
                    append(step.flagMask)
                }
                is JimmerImmutableDraftValidationStep.Assert -> {
                    append("assert:")
                    append(step.expected)
                }
                is JimmerImmutableDraftValidationStep.Digits -> {
                    append("digits:")
                    append(step.target.name)
                    append(':')
                    append(step.component.name)
                    append(':')
                    append(step.limit)
                }
                is JimmerImmutableDraftValidationStep.Temporal -> {
                    append("temporal:")
                    append(step.target.name)
                    append(':')
                    append(step.constraint.name)
                }
                is JimmerImmutableDraftValidationStep.CustomValidator -> {
                    append("custom:")
                    append(step.annotationTypeId.value)
                    if (includePlatformSurface) {
                        append('@')
                        append(step.sourceAnnotationUseSiteTarget?.name.orEmpty())
                    }
                    append('(')
                    append(step.validatorTypeIds.joinToString(",") { typeId -> typeId.value })
                    append(")=")
                    append(step.message)
                }
            }
            if (step is JimmerImmutableDraftValidationStep.BuiltIn) {
                append('@')
                append(step.sourceAnnotationTypeId.value)
                if (includePlatformSurface) {
                    append('@')
                    append(step.sourceAnnotationUseSiteTarget?.name.orEmpty())
                }
                append('(')
                append(step.failure.exceptionTypeId.value)
                append(':')
                append(step.failure.declaredMessage)
                append(':')
                append(step.failure.defaultMessage)
                append(':')
                append(step.failure.skipWhenNull)
                append(':')
                append(step.failure.usesDefaultMessage)
                append(')')
            }
            append(';')
        }
    }
}

private fun List<LsiAnnotation>.annotationText(includePlatformSurface: Boolean): String {
    return joinToString(";") { annotation -> annotation.annotationText(includePlatformSurface) }
}

private fun LsiAnnotation.annotationText(includePlatformSurface: Boolean): String {
    return buildString {
        append(type.value)
        if (includePlatformSurface) {
            append('@')
            append(useSiteTarget?.name.orEmpty())
        }
        append('(')
        append(arguments.toSortedMap().entries.joinToString(",") { (name, argument) ->
            buildString {
                append(name)
                if (includePlatformSurface) {
                    append('@')
                    append(argument.origin.name)
                }
                append('=')
                append(argument.value.annotationValueText(includePlatformSurface))
            }
        })
        append(')')
    }
}

private fun LsiAnnotationValue.annotationValueText(includePlatformSurface: Boolean): String {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> "boolean:$value"
        is LsiAnnotationValue.ByteValue -> "byte:$value"
        is LsiAnnotationValue.ShortValue -> "short:$value"
        is LsiAnnotationValue.IntValue -> "int:$value"
        is LsiAnnotationValue.LongValue -> "long:$value"
        is LsiAnnotationValue.FloatValue -> "float:$value"
        is LsiAnnotationValue.DoubleValue -> "double:$value"
        is LsiAnnotationValue.CharValue -> "char:${value.code}"
        is LsiAnnotationValue.StringValue -> "string:$value"
        is LsiAnnotationValue.EnumValue -> "enum:${enumType.value}:$entryName"
        is LsiAnnotationValue.ClassValue -> "class:${type.jimmerTypeSignature()}"
        is LsiAnnotationValue.NestedAnnotationValue -> {
            "annotation:${annotation.annotationText(includePlatformSurface)}"
        }
        is LsiAnnotationValue.ArrayValue -> elements.joinToString(",", "array:[", "]") { element ->
            element.annotationValueText(includePlatformSurface)
        }
    }
}

private fun StringBuilder.appendDraftRecord(
    kind: String,
    vararg fields: String,
) {
    append(kind)
    fields.forEach { field ->
        append('|')
        append(field.escapeDraftSnapshotField())
    }
    append('\n')
}

private fun String.escapeDraftSnapshotField(): String {
    return buildString {
        for (character in this@escapeDraftSnapshotField) {
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
