package site.addzero.lsi.core

/**
 * 与编译器前端和源码位置无关的稳定符号标识。
 */
@JvmInline
value class LsiSymbolId(
    val value: String
) : Comparable<LsiSymbolId> {

    init {
        require(value.isNotBlank()) { "LSI symbol id cannot be blank" }
        require(value == value.trim()) { "LSI symbol id cannot have surrounding whitespace: '$value'" }
        require(value.none(Char::isWhitespace)) { "LSI symbol id cannot contain whitespace: '$value'" }
    }

    override fun compareTo(other: LsiSymbolId): Int = value.compareTo(other.value)

    override fun toString(): String = value

    fun isTypeId(): Boolean = value.startsWith(TYPE_PREFIX) && '/' !in value

    fun requireTypeQualifiedName(): String {
        require(isTypeId()) {
            "LSI symbol id is not a type id: '$value'"
        }
        return decodeComponent(value.removePrefix(TYPE_PREFIX))
    }

    fun requireTypeParameterName(): String {
        val markerIndex = value.lastIndexOf(TYPE_PARAMETER_MARKER)
        require(markerIndex >= 0) { "LSI symbol id is not a type parameter id: '$value'" }
        val encodedName = value.substring(markerIndex + TYPE_PARAMETER_MARKER.length)
        require('/' !in encodedName) { "Invalid LSI type parameter id: '$value'" }
        return decodeComponent(encodedName)
    }

    companion object {

        private const val TYPE_PREFIX = "type:"
        private const val PACKAGE_SCOPE_PREFIX = "package-scope:"
        private const val FILE_SCOPE_MARKER = "/file:"
        private const val DEFAULT_PACKAGE_COMPONENT = "default"
        private const val NAMED_PACKAGE_PREFIX = "named:"
        private const val TYPE_PARAMETER_MARKER = "/type-parameter:"
        private const val HEX_DIGITS = "0123456789ABCDEF"

        fun packageScope(packageName: String): LsiSymbolId {
            return LsiSymbolId(PACKAGE_SCOPE_PREFIX + encodePackageName(packageName))
        }

        fun fileScope(packageName: String, logicalPath: String): LsiSymbolId {
            val normalizedLogicalPath = LsiSource.of(logicalPath).path
            require(logicalPath == normalizedLogicalPath && !logicalPath.hasWindowsDrivePrefix()) {
                "File scope logical path must be normalized and relative: '$logicalPath'"
            }
            return LsiSymbolId(
                packageScope(packageName).value +
                    FILE_SCOPE_MARKER +
                    encodeComponent(logicalPath, "file scope logical path")
            )
        }

        fun type(qualifiedName: String): LsiSymbolId {
            return LsiSymbolId(TYPE_PREFIX + encodeComponent(qualifiedName, "qualified type name"))
        }

        fun property(owner: LsiSymbolId, name: String): LsiSymbolId {
            return LsiSymbolId("${owner.value}/property:${encodeComponent(name, "property name")}")
        }

        fun field(owner: LsiSymbolId, name: String): LsiSymbolId {
            return LsiSymbolId("${owner.value}/field:${encodeComponent(name, "field name")}")
        }

        fun enumEntry(owner: LsiSymbolId, name: String): LsiSymbolId {
            return LsiSymbolId("${owner.value}/enum-entry:${encodeComponent(name, "enum entry name")}")
        }

        fun function(
            owner: LsiSymbolId,
            name: String,
            parameterTypeSignatures: List<String> = emptyList()
        ): LsiSymbolId {
            val encodedName = encodeComponent(name, "function name")
            val signature = parameterTypeSignatures.joinToString(",") { parameterTypeSignature ->
                encodeComponent(parameterTypeSignature, "parameter type signature")
            }
            return LsiSymbolId("${owner.value}/function:$encodedName($signature)")
        }

        fun constructor(
            owner: LsiSymbolId,
            parameterTypeSignatures: List<String> = emptyList()
        ): LsiSymbolId {
            val signature = parameterTypeSignatures.joinToString(",") { parameterTypeSignature ->
                encodeComponent(parameterTypeSignature, "parameter type signature")
            }
            return LsiSymbolId("${owner.value}/constructor($signature)")
        }

        fun parameter(callable: LsiSymbolId, index: Int, name: String): LsiSymbolId {
            require(index >= 0) { "Parameter index cannot be negative: $index" }
            return LsiSymbolId("${callable.value}/parameter:$index:${encodeComponent(name, "parameter name")}")
        }

        fun typeParameter(owner: LsiSymbolId, name: String): LsiSymbolId {
            return LsiSymbolId("${owner.value}$TYPE_PARAMETER_MARKER${encodeComponent(name, "type parameter name")}")
        }

        private fun encodePackageName(packageName: String): String {
            require(packageName == packageName.trim()) {
                "Package name cannot have surrounding whitespace: '$packageName'"
            }
            if (packageName.isEmpty()) {
                return DEFAULT_PACKAGE_COMPONENT
            }
            return NAMED_PACKAGE_PREFIX + encodeComponent(packageName, "package name")
        }

        private fun encodeComponent(value: String, role: String): String {
            require(value.isNotBlank()) { "$role cannot be blank" }
            val bytes = value.toByteArray(Charsets.UTF_8)
            return buildString(bytes.size) {
                bytes.forEach { byte ->
                    val unsignedByte = byte.toInt() and 0xff
                    if (unsignedByte.isUnreserved()) {
                        append(unsignedByte.toChar())
                    } else {
                        append('%')
                        append(HEX_DIGITS[unsignedByte ushr 4])
                        append(HEX_DIGITS[unsignedByte and 0x0f])
                    }
                }
            }
        }

        private fun decodeComponent(value: String): String {
            val bytes = ByteArray(value.length)
            var sourceIndex = 0
            var targetIndex = 0
            while (sourceIndex < value.length) {
                val current = value[sourceIndex]
                if (current == '%') {
                    require(sourceIndex + 2 < value.length) { "Incomplete encoded LSI symbol component: '$value'" }
                    val high = value[sourceIndex + 1].hexValue()
                    val low = value[sourceIndex + 2].hexValue()
                    require(high >= 0 && low >= 0) { "Invalid encoded LSI symbol component: '$value'" }
                    bytes[targetIndex++] = ((high shl 4) or low).toByte()
                    sourceIndex += 3
                } else {
                    require(current.code.isUnreserved()) { "Invalid raw character in LSI symbol component: '$current'" }
                    bytes[targetIndex++] = current.code.toByte()
                    sourceIndex++
                }
            }
            return String(bytes, 0, targetIndex, Charsets.UTF_8)
        }

        private fun Int.isUnreserved(): Boolean {
            return this in 'a'.code..'z'.code ||
                this in 'A'.code..'Z'.code ||
                this in '0'.code..'9'.code ||
                this == '-'.code ||
                this == '.'.code ||
                this == '_'.code ||
                this == '~'.code
        }

        private fun Char.hexValue(): Int {
            return when (this) {
                in '0'..'9' -> code - '0'.code
                in 'a'..'f' -> code - 'a'.code + 10
                in 'A'..'F' -> code - 'A'.code + 10
                else -> -1
            }
        }

        private fun String.hasWindowsDrivePrefix(): Boolean {
            return length >= 3 && this[0].isLetter() && this[1] == ':' && this[2] == '/'
        }
    }
}
