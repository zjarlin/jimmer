package site.addzero.lsi.model

data class LsiSemanticSnapshotOptions(
    val platformNullability: LsiNullability = LsiNullability.NON_NULL,
    val normalizeUnitToVoid: Boolean = true,
    val includeAnnotationUseSiteTarget: Boolean = false,
) {

    init {
        require(platformNullability != LsiNullability.PLATFORM) {
            "Semantic snapshot platform nullability must resolve to a non-platform value"
        }
    }
}

/**
 * 生成与前端源码坐标无关的确定性语义快照，供 APT/KSP parity 和 golden 测试使用。
 */
fun LsiWorkspace.toSemanticSnapshot(
    options: LsiSemanticSnapshotOptions = LsiSemanticSnapshotOptions(),
): String {
    val lines = buildList {
        annotationScopes.mapTo(this) { annotationScope ->
            annotationScope.toSemanticSnapshotLine(options)
        }
        declarations.mapTo(this) { declaration ->
            declaration.toSemanticSnapshotLine(options)
        }
    }
    return lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n")
}

private fun LsiAnnotationScope.toSemanticSnapshotLine(options: LsiSemanticSnapshotOptions): String {
    val logicalPath = when (this) {
        is LsiPackageAnnotationScope -> ""
        is LsiFileAnnotationScope -> logicalPath.escapeSnapshotText()
    }
    return listOf(
        "annotation-scope",
        id.value,
        kind.name,
        packageName.escapeSnapshotText(),
        logicalPath,
        annotations.toSemanticSnapshot(options),
    ).joinToString("|")
}

private fun LsiDeclaration.toSemanticSnapshotLine(options: LsiSemanticSnapshotOptions): String {
    return when (this) {
        is LsiTypeDeclaration -> listOf(
            "type",
            id.value,
            name,
            qualifiedName,
            kind.name,
            enclosingTypeId?.value.orEmpty(),
            requiresEnclosingInstance.toString(),
            abstractDeclaration.toString(),
            dataClass.toString(),
            visibility.name,
            modality.name,
            typeParameters.joinToString(",") { parameter -> parameter.toSemanticSnapshot(options) },
            superTypes.joinToString(",") { type -> type.toSemanticSignature(options) },
            memberIds.joinToString(",") { memberId -> memberId.value },
            annotationMembers.joinToString(",") { member -> member.toSemanticSnapshot(options) },
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiProperty -> listOf(
            "property",
            id.value,
            name,
            ownerId.value,
            type.toSemanticSignature(options),
            mutable.toString(),
            static.toString(),
            modality.name,
            visibility.name,
            overrides.toSemanticSnapshot(),
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiField -> listOf(
            "field",
            id.value,
            name,
            ownerId.value,
            type.toSemanticSignature(options),
            mutable.toString(),
            static.toString(),
            visibility.name,
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiFunction -> listOf(
            "function",
            id.value,
            name,
            ownerId?.value.orEmpty(),
            returnType.toSemanticSignature(options),
            receiverType?.toSemanticSignature(options).orEmpty(),
            suspending.toString(),
            typeParameters.joinToString(",") { parameter -> parameter.toSemanticSnapshot(options) },
            parameters.joinToString(",") { parameter -> parameter.toSemanticSnapshot(options) },
            thrownTypes.joinToString(",") { type -> type.toSemanticSignature(options) },
            static.toString(),
            modality.name,
            visibility.name,
            overrides.toSemanticSnapshot(),
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiConstructor -> listOf(
            "constructor",
            id.value,
            ownerId.value,
            typeParameters.joinToString(",") { parameter -> parameter.toSemanticSnapshot(options) },
            parameters.joinToString(",") { parameter -> parameter.toSemanticSnapshot(options) },
            thrownTypes.joinToString(",") { type -> type.toSemanticSignature(options) },
            visibility.name,
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiParameter -> listOf(
            "parameter",
            id.value,
            name,
            callableId.value,
            index.toString(),
            type.toSemanticSignature(options),
            vararg.toString(),
            hasDefault.toString(),
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
        is LsiEnumEntry -> listOf(
            "enum-entry",
            id.value,
            name,
            ownerId.value,
            annotations.toSemanticSnapshot(options),
        ).joinToString("|")
    }
}

private fun LsiAnnotationMember.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return listOf(
        name,
        type.toSemanticSignature(options),
        vararg.toString(),
        hasDefault.toString(),
    ).joinToString(":")
}

private fun LsiParameter.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return listOf(
        id.value,
        name,
        index.toString(),
        type.toSemanticSignature(options),
        vararg.toString(),
        hasDefault.toString(),
        annotations.toSemanticSnapshot(options),
    ).joinToString(":")
}

private fun LsiTypeParameter.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return buildString {
        append(id.value)
        append(':')
        append(name)
        append(':')
        append(variance.name)
        if (upperBounds.isNotEmpty()) {
            append(':')
            append(upperBounds.joinToString("&") { bound -> bound.toSemanticSignature(options) })
        }
    }
}

private fun LsiTypeRef.toSemanticSignature(options: LsiSemanticSnapshotOptions): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.toSemanticSignature(options) })
                append('>')
            }
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiPrimitiveType -> {
            val normalizedKind = if (options.normalizeUnitToVoid && kind == LsiPrimitiveKind.UNIT) {
                LsiPrimitiveKind.VOID
            } else {
                kind
            }
            buildString {
                append("primitive:")
                append(normalizedKind.name.lowercase())
                if (boxed) {
                    append(":boxed")
                }
            }
        }
        is LsiArrayType -> "array:${elementType.toSemanticSignature(options)}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.toSemanticSignature(options))
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter ->
                parameter.toSemanticSignature(options)
            })
            append("]:return:")
            append(returnType.toSemanticSignature(options))
        }
        is LsiUnresolvedType -> "unresolved:$displayName"
    }
    val normalizedNullability = when (nullability) {
        LsiNullability.PLATFORM -> options.platformNullability
        else -> nullability
    }
    val annotationSnapshot = annotations.toSemanticSnapshot(options)
    return if (annotationSnapshot.isEmpty()) {
        "$base:${normalizedNullability.name.lowercase()}"
    } else {
        "$base:${normalizedNullability.name.lowercase()}@[$annotationSnapshot]"
    }
}

private fun LsiTypeArgument.toSemanticSignature(options: LsiSemanticSnapshotOptions): String {
    return when (variance) {
        LsiVariance.STAR -> "*"
        LsiVariance.INVARIANT -> requireNotNull(type).toSemanticSignature(options)
        LsiVariance.IN -> "in:${requireNotNull(type).toSemanticSignature(options)}"
        LsiVariance.OUT -> "out:${requireNotNull(type).toSemanticSignature(options)}"
    }
}

private fun List<LsiOverride>.toSemanticSnapshot(): String {
    return sortedWith(compareBy(LsiOverride::distance, LsiOverride::declarationId))
        .joinToString(",") { override -> "${override.declarationId.value}@${override.distance}" }
}

private fun List<LsiAnnotation>.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return map { annotation -> annotation.toSemanticSnapshot(options) }
        .sorted()
        .joinToString(",")
}

private fun LsiAnnotation.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return buildString {
        append(type.value)
        if (options.includeAnnotationUseSiteTarget && useSiteTarget != null) {
            append('@')
            append(useSiteTarget.name)
        }
        append('(')
        append(
            arguments.toSortedMap().entries.joinToString(";") { (name, argument) ->
                "$name=${argument.origin.name}:${argument.value.toSemanticSnapshot(options)}"
            },
        )
        append(')')
    }
}

private fun LsiAnnotationValue.toSemanticSnapshot(options: LsiSemanticSnapshotOptions): String {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> "boolean:$value"
        is LsiAnnotationValue.ByteValue -> "byte:$value"
        is LsiAnnotationValue.ShortValue -> "short:$value"
        is LsiAnnotationValue.IntValue -> "int:$value"
        is LsiAnnotationValue.LongValue -> "long:$value"
        is LsiAnnotationValue.FloatValue -> "float:${value.toRawBits()}"
        is LsiAnnotationValue.DoubleValue -> "double:${value.toRawBits()}"
        is LsiAnnotationValue.CharValue -> "char:${value.code}"
        is LsiAnnotationValue.StringValue -> "string:${value.escapeSnapshotText()}"
        is LsiAnnotationValue.EnumValue -> "enum:${enumType.value}#$entryName"
        is LsiAnnotationValue.ClassValue -> "class:${type.toSemanticSignature(options)}"
        is LsiAnnotationValue.NestedAnnotationValue -> "annotation:${annotation.toSemanticSnapshot(options)}"
        is LsiAnnotationValue.ArrayValue -> elements.joinToString(prefix = "array:[", postfix = "]") { element ->
            element.toSemanticSnapshot(options)
        }
    }
}

private fun String.escapeSnapshotText(): String {
    return buildString(length) {
        for (character in this@escapeSnapshotText) {
            when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                ',', ';', '(', ')', '[', ']', '|', '=' -> {
                    append('\\')
                    append(character)
                }
                else -> append(character)
            }
        }
    }
}
