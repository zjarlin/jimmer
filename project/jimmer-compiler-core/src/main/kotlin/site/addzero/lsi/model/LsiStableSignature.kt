package site.addzero.lsi.model

fun LsiTypeParameter.stableSignature(): String {
    return buildString {
        append("type-parameter(")
        appendToken(id.value)
        appendToken(name)
        appendToken(variance.name)
        upperBounds.forEach { bound -> appendToken(bound.stableSignature()) }
        append(')')
    }
}

fun LsiAnnotation.stableSignature(): String {
    return buildString {
        append("annotation(")
        appendToken(type.value)
        appendToken(useSiteTarget?.name.orEmpty())
        arguments.toSortedMap().forEach { (name, argument) ->
            appendToken(name)
            appendToken(argument.origin.name)
            appendToken(argument.value.stableSignature())
        }
        append(')')
    }
}

fun LsiAnnotationValue.stableSignature(): String {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> "boolean:$value"
        is LsiAnnotationValue.ByteValue -> "byte:$value"
        is LsiAnnotationValue.ShortValue -> "short:$value"
        is LsiAnnotationValue.IntValue -> "int:$value"
        is LsiAnnotationValue.LongValue -> "long:$value"
        is LsiAnnotationValue.FloatValue -> "float:${value.toRawBits()}"
        is LsiAnnotationValue.DoubleValue -> "double:${value.toRawBits()}"
        is LsiAnnotationValue.CharValue -> "char:${value.code}"
        is LsiAnnotationValue.StringValue -> buildString {
            append("string(")
            appendToken(value)
            append(')')
        }
        is LsiAnnotationValue.EnumValue -> buildString {
            append("enum(")
            appendToken(enumType.value)
            appendToken(entryName)
            append(')')
        }
        is LsiAnnotationValue.ClassValue -> buildString {
            append("class(")
            appendToken(type.stableSignature())
            append(')')
        }
        is LsiAnnotationValue.NestedAnnotationValue -> buildString {
            append("nested(")
            appendToken(annotation.stableSignature())
            append(')')
        }
        is LsiAnnotationValue.ArrayValue -> buildString {
            append("array(")
            elements.forEach { element -> appendToken(element.stableSignature()) }
            append(')')
        }
    }
}

private fun StringBuilder.appendToken(value: String) {
    append(value.length)
    append(':')
    append(value)
}
