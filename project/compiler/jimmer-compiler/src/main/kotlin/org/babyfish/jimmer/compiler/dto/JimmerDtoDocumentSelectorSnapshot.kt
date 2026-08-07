package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelector

internal fun CompilerInputDocumentTypeSelector.canonicalText(): String {
    return buildString {
        appendPart(sourceName)
        append(':')
        append(checksFallbackExistence)
        appendPart(fallbackTypeId.value)
        append(':')
        append(wildcardTypeIds.size)
        wildcardTypeIds.forEach { typeId -> appendPart(typeId.value) }
    }
}

private fun StringBuilder.appendPart(value: String) {
    append(':')
    append(value.length)
    append(':')
    append(value)
}
