package site.addzero.lsi.model

import java.util.Collections
import java.util.LinkedHashMap

/**
 * 语言无关的结构化文档。
 *
 * 参数与属性保持源码中的声明顺序，便于稳定生成文档和编译产物。
 */
class LsiDocumentation(
    val value: String?,
    parameterValues: Map<String, String>,
    val returnValue: String?,
    propertyValues: Map<String, String>,
) {

    val parameterValues: Map<String, String> = parameterValues.immutableOrderedCopy()

    val propertyValues: Map<String, String> = propertyValues.immutableOrderedCopy()

    /** 按稳定协议输出正文和已识别标签。 */
    fun canonicalText(): String {
        return buildString {
            value?.let { text -> append(text).append('\n') }
            parameterValues.forEach { (name, text) ->
                append("@param ").append(name).append(' ').append(text).append('\n')
            }
            propertyValues.forEach { (name, text) ->
                append("@property ").append(name).append(' ').append(text).append('\n')
            }
            returnValue?.let { text -> append("@return ").append(text).append('\n') }
        }
    }

    override fun toString(): String = canonicalText()

    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is LsiDocumentation &&
            value == other.value &&
            parameterValues.entries.toList() == other.parameterValues.entries.toList() &&
            returnValue == other.returnValue &&
            propertyValues.entries.toList() == other.propertyValues.entries.toList()
    }

    override fun hashCode(): Int {
        var result = value?.hashCode() ?: 0
        result = 31 * result + parameterValues.entries.toList().hashCode()
        result = 31 * result + (returnValue?.hashCode() ?: 0)
        result = 31 * result + propertyValues.entries.toList().hashCode()
        return result
    }
}

/**
 * 将 JavaDoc、KDoc 或 DTO 文档正文解析为语言无关的结构化文档。
 *
 * 仅保留正文、`@param`、`@property` 与 `@return`，其他标签及其后续文本会被忽略。
 */
fun String?.parseLsiDocumentation(): LsiDocumentation? {
    val documentation = this
        ?.trim { character -> character <= ' ' }
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val builder = LsiDocumentationBuilder()
    documentation.reader().buffered().useLines { lines ->
        lines.forEach(builder::appendLine)
    }
    return builder.build()
}

private class LsiDocumentationBuilder {

    private var value: String? = null

    private val parameterValues = linkedMapOf<String, String>()

    private var returnValue: String? = null

    private val propertyValues = linkedMapOf<String, String>()

    private var currentParameterName: String? = null

    private var currentPropertyName: String? = null

    private var currentReturn = false

    private var currentIgnored = false

    private var buffer = StringBuilder()

    fun appendLine(line: String) {
        val start = line.indexOfNonWhitespace()
        if (start == -1) {
            append(line)
            return
        }
        when {
            line.isNamedTag(PARAM_TAG, start) -> appendNamedTag(line, start, PARAM_TAG, ::switchToParameter)
            line.isNamedTag(PROPERTY_TAG, start) -> appendNamedTag(line, start, PROPERTY_TAG, ::switchToProperty)
            line.startsWith(RETURN_TAG, start) -> appendReturn(line, start)
            line.startsWith("@", start) -> switchToIgnored()
            line[0] <= ' ' -> append(line.substring(1))
            else -> append(line)
        }
    }

    fun build(): LsiDocumentation {
        commit()
        return LsiDocumentation(
            value = value?.takeIf(String::isNotEmpty),
            parameterValues = parameterValues.toMap(),
            returnValue = returnValue?.takeIf(String::isNotEmpty),
            propertyValues = propertyValues.toMap(),
        )
    }

    private fun appendNamedTag(
        line: String,
        start: Int,
        tag: String,
        switchSection: (String) -> Unit,
    ) {
        val nameStart = line.indexOfNonWhitespace(start + tag.length)
        if (nameStart == -1) {
            append(line.substring(start + tag.length))
            return
        }
        val nameEnd = line.indexOfWhitespace(nameStart + 1)
        if (nameEnd == -1) {
            switchSection(line.substring(nameStart))
            return
        }
        switchSection(line.substring(nameStart, nameEnd))
        val textStart = line.indexOfNonWhitespace(nameEnd)
        if (textStart != -1) {
            append(line.substring(textStart))
        } else {
            append(line.substring(nameEnd))
        }
    }

    private fun appendReturn(line: String, start: Int) {
        val textStart = line.indexOfNonWhitespace(start + RETURN_TAG.length)
        switchToReturn()
        if (textStart != -1) {
            append(line.substring(textStart))
        } else {
            append(line.substring(start + RETURN_TAG.length))
        }
    }

    private fun switchToParameter(name: String) {
        commit()
        currentParameterName = name
    }

    private fun switchToProperty(name: String) {
        commit()
        currentPropertyName = name
    }

    private fun switchToReturn() {
        commit()
        currentReturn = true
    }

    private fun switchToIgnored() {
        commit()
        currentIgnored = true
    }

    private fun append(text: String) {
        if (!currentIgnored) {
            buffer.append(text).append('\n')
        }
    }

    private fun commit() {
        if (buffer.lastOrNull() == '\n') {
            buffer.setLength(buffer.length - 1)
        }
        when {
            currentParameterName != null -> {
                parameterValues[requireNotNull(currentParameterName)] = buffer.toString()
                currentParameterName = null
            }
            currentPropertyName != null -> {
                propertyValues[requireNotNull(currentPropertyName)] = buffer.toString()
                currentPropertyName = null
            }
            currentReturn -> {
                returnValue = buffer.toString()
                currentReturn = false
            }
            !currentIgnored -> value = buffer.toString()
        }
        buffer = StringBuilder()
    }
}

private fun String.isNamedTag(tag: String, start: Int): Boolean {
    val boundary = start + tag.length
    return startsWith(tag, start) && length > boundary && Character.isWhitespace(this[boundary])
}

private fun String.indexOfNonWhitespace(start: Int = 0): Int {
    for (index in start until length) {
        if (this[index] > ' ') {
            return index
        }
    }
    return -1
}

private fun String.indexOfWhitespace(start: Int): Int {
    for (index in start until length) {
        if (this[index] <= ' ') {
            return index
        }
    }
    return -1
}

private fun Map<String, String>.immutableOrderedCopy(): Map<String, String> {
    return Collections.unmodifiableMap(LinkedHashMap(this))
}

private const val PARAM_TAG = "@param"

private const val PROPERTY_TAG = "@property"

private const val RETURN_TAG = "@return"
