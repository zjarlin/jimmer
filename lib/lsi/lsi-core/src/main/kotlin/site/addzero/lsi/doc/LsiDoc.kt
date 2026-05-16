package site.addzero.lsi.doc

import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.util.LinkedHashMap

// 覆盖来源：project/jimmer-core/src/main/java/org/babyfish/jimmer/client/meta/Doc.java
// 迁移说明：将文档注释的 parse/toString 语义下沉到 LSI 文档对象，
// 使 compiler 侧先面向 LSI 文档 IR，再在最终 runtime 装配处显式转换为 jimmer `Doc`
class LsiDoc(
    val value: String?,
    parameterValues: Map<String, String> = emptyMap(),
    val returnValue: String? = null,
    propertyValues: Map<String, String> = emptyMap(),
) {

    val parameterValueMap: Map<String, String> =
        if (parameterValues.isEmpty()) {
            emptyMap()
        } else {
            LinkedHashMap(parameterValues)
        }

    val propertyValueMap: Map<String, String> =
        if (propertyValues.isEmpty()) {
            emptyMap()
        } else {
            LinkedHashMap(propertyValues)
        }

    override fun toString(): String =
        buildString {
            value?.let {
                append(it).append('\n')
            }
            for ((name, doc) in parameterValueMap) {
                append("@param ").append(name).append(' ').append(doc).append('\n')
            }
            for ((name, doc) in propertyValueMap) {
                append("@property ").append(name).append(' ').append(doc).append('\n')
            }
            returnValue?.let {
                append("@return ").append(it).append('\n')
            }
        }

    companion object {

        fun parse(doc: String?): LsiDoc? {
            val normalizedDoc = doc?.trim()
            if (normalizedDoc.isNullOrEmpty()) {
                return null
            }
            val builder = Builder()
            try {
                BufferedReader(StringReader(normalizedDoc)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val start = indexOfNonWhitespace(line, 0)
                        when {
                            start == -1 -> {
                                builder.append(line)
                            }

                            line.startsWith("@param", start) &&
                                line.length > start + 6 &&
                                line[start + 6].isWhitespace() -> {
                                val begin = indexOfNonWhitespace(line, start + 6)
                                if (begin != -1) {
                                    val end = indexOfWhitespace(line, begin + 1)
                                    if (end == -1) {
                                        builder.switchToParam(line.substring(begin))
                                    } else {
                                        builder.switchToParam(line.substring(begin, end))
                                        val rest = indexOfNonWhitespace(line, end)
                                        if (rest != -1) {
                                            builder.append(line.substring(rest))
                                        } else {
                                            builder.append(line.substring(end))
                                        }
                                    }
                                } else {
                                    val rest = indexOfNonWhitespace(line, start + 6)
                                    if (rest != -1) {
                                        builder.append(line.substring(rest))
                                    } else {
                                        builder.append(line.substring(start + 6))
                                    }
                                }
                            }

                            line.startsWith("@property", start) &&
                                line.length > start + 9 &&
                                line[start + 9].isWhitespace() -> {
                                val begin = indexOfNonWhitespace(line, start + 9)
                                if (begin != -1) {
                                    val end = indexOfWhitespace(line, begin + 1)
                                    if (end == -1) {
                                        builder.switchToProperty(line.substring(begin))
                                    } else {
                                        builder.switchToProperty(line.substring(begin, end))
                                        val rest = indexOfNonWhitespace(line, end)
                                        if (rest != -1) {
                                            builder.append(line.substring(rest))
                                        } else {
                                            builder.append(line.substring(end))
                                        }
                                    }
                                } else {
                                    builder.append(line.substring(start + 9))
                                }
                            }

                            line.startsWith("@return", start) -> {
                                val begin = indexOfNonWhitespace(line, start + 7)
                                builder.switchToReturn()
                                if (begin != -1) {
                                    builder.append(line.substring(begin))
                                } else {
                                    builder.append(line.substring(start + 7))
                                }
                            }

                            line.startsWith("@", start) -> {
                                builder.switchToIgnored()
                            }

                            line[0] <= ' ' -> {
                                builder.append(line.substring(1))
                            }

                            else -> {
                                builder.append(line)
                            }
                        }
                        line = reader.readLine()
                    }
                }
            } catch (ex: IOException) {
                throw AssertionError("Cannot parse documentation comment", ex)
            }
            return builder.build()
        }

        private fun indexOfNonWhitespace(line: String, start: Int): Int {
            for (index in start until line.length) {
                if (line[index] > ' ') {
                    return index
                }
            }
            return -1
        }

        private fun indexOfWhitespace(line: String, start: Int): Int {
            for (index in start until line.length) {
                if (line[index] <= ' ') {
                    return index
                }
            }
            return -1
        }
    }

    private class Builder {

        private var value: String? = null
        private val parameterValueMap = linkedMapOf<String, String>()
        private val propertyValueMap = linkedMapOf<String, String>()
        private var returnValue: String? = null

        private var currentParamName: String? = null
        private var currentPropertyName: String? = null
        private var currentReturn = false
        private var currentIgnored = false
        private var builder = StringBuilder()

        fun switchToParam(name: String?) {
            commit()
            currentParamName = name ?: "<unknown>"
        }

        fun switchToProperty(name: String?) {
            commit()
            currentPropertyName = name ?: "<unknown>"
        }

        fun switchToReturn() {
            commit()
            currentReturn = true
        }

        fun switchToIgnored() {
            commit()
            currentIgnored = true
        }

        fun append(text: String) {
            if (!currentIgnored) {
                builder.append(text).append('\n')
            }
        }

        fun build(): LsiDoc {
            commit()
            return LsiDoc(
                value = value?.takeIf { it.isNotEmpty() },
                parameterValues = parameterValueMap,
                returnValue = returnValue?.takeIf { it.isNotEmpty() },
                propertyValues = propertyValueMap,
            )
        }

        private fun commit() {
            if (builder.isNotEmpty() && builder.last() == '\n') {
                builder.setLength(builder.length - 1)
            }
            when {
                currentParamName != null -> {
                    parameterValueMap[currentParamName!!] = builder.toString()
                    currentParamName = null
                }

                currentPropertyName != null -> {
                    propertyValueMap[currentPropertyName!!] = builder.toString()
                    currentPropertyName = null
                }

                currentReturn -> {
                    returnValue = builder.toString()
                    currentReturn = false
                }

                !currentIgnored -> {
                    value = builder.toString()
                }
            }
            builder = StringBuilder()
        }
    }
}
