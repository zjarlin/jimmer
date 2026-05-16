package site.addzero.lsi.poet

data class LsiCodeBlock(
    val format: String,
    val args: List<Any?> = emptyList(),
) {

    operator fun plus(other: LsiCodeBlock): LsiCodeBlock =
        LsiCodeBlock(
            format = format + other.format,
            args = args + other.args
        )

    companion object {
        fun of(format: String, vararg args: Any?): LsiCodeBlock =
            LsiCodeBlock(format = format, args = args.toList())
    }
}

data class LsiKotlinOnlyRawCodeMatch(
    val description: String,
    val match: String?,
    val snippet: String,
)

private val KOTLIN_ONLY_RAW_CODE_PATTERNS = listOf(
    "safe call operator" to Regex("\\?\\."),
    "elvis operator" to Regex("\\?:"),
    "reference equality operator" to Regex("===|!=="),
    "non-null assertion" to Regex("!!"),
    "Kotlin class literal" to Regex("::class"),
    "Kotlin local declaration" to Regex("(?m)(^|\\s)(val|var)\\s+"),
    "Kotlin when expression" to Regex("\\bwhen\\s*\\("),
    "Kotlin until range" to Regex("\\buntil\\b"),
    "Kotlin labeled this" to Regex("this@"),
    "Kotlin error() call" to Regex("\\berror\\s*\\("),
    "Kotlin cast operator" to Regex("\\bas\\??\\s+%T"),
)

fun LsiCodeBlock.findKotlinOnlyRawCode(): LsiKotlinOnlyRawCodeMatch? {
    val matchedPattern = KOTLIN_ONLY_RAW_CODE_PATTERNS.firstOrNull { (_, pattern) ->
        pattern.containsMatchIn(format)
    } ?: return null
    val (description, pattern) = matchedPattern
    return LsiKotlinOnlyRawCodeMatch(
        description = description,
        match = pattern.find(format)?.value?.trim()?.takeIf { it.isNotEmpty() },
        snippet = format.trim().lineSequence().firstOrNull().orEmpty().ifBlank { format.trim() }
    )
}
