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

    companion object {

        fun type(qualifiedName: String): LsiSymbolId {
            requireIdentifier(qualifiedName, "qualified type name")
            return LsiSymbolId("type:$qualifiedName")
        }

        fun property(owner: LsiSymbolId, name: String): LsiSymbolId {
            requireIdentifier(name, "property name")
            return LsiSymbolId("${owner.value}/property:$name")
        }

        fun enumEntry(owner: LsiSymbolId, name: String): LsiSymbolId {
            requireIdentifier(name, "enum entry name")
            return LsiSymbolId("${owner.value}/enum-entry:$name")
        }

        fun function(
            owner: LsiSymbolId,
            name: String,
            parameterTypeSignatures: List<String> = emptyList()
        ): LsiSymbolId {
            requireIdentifier(name, "function name")
            parameterTypeSignatures.forEach { signature ->
                requireIdentifier(signature, "parameter type signature")
            }
            val signature = parameterTypeSignatures.joinToString(",")
            return LsiSymbolId("${owner.value}/function:$name($signature)")
        }

        fun parameter(callable: LsiSymbolId, index: Int, name: String): LsiSymbolId {
            require(index >= 0) { "Parameter index cannot be negative: $index" }
            requireIdentifier(name, "parameter name")
            return LsiSymbolId("${callable.value}/parameter:$index:$name")
        }

        fun typeParameter(owner: LsiSymbolId, name: String): LsiSymbolId {
            requireIdentifier(name, "type parameter name")
            return LsiSymbolId("${owner.value}/type-parameter:$name")
        }

        private fun requireIdentifier(value: String, role: String) {
            require(value.isNotBlank()) { "$role cannot be blank" }
            require(value == value.trim()) { "$role cannot have surrounding whitespace: '$value'" }
            require(value.none(Char::isWhitespace)) { "$role cannot contain whitespace: '$value'" }
        }
    }
}
