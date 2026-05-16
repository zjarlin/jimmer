package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoModifier

enum class LsiDtoModifier {
    INPUT,
    SPECIFICATION,
    UNSAFE,
    FIXED,
    STATIC,
    DYNAMIC,
    FUZZY,
    ;

    internal fun toRawDtoModifier(): DtoModifier = DtoModifier.valueOf(name)

    companion object {
        @JvmStatic
        fun fromNullableInputOption(option: String): LsiDtoModifier =
            when (option.lowercase()) {
                "fixed" -> FIXED
                "static" -> STATIC
                "dynamic" -> DYNAMIC
                "fuzzy" -> FUZZY
                else -> throw IllegalArgumentException(
                    "The DTO option `defaultNullableInputModifier` can only be \"fixed\", \"static\", \"dynamic\" or \"fuzzy\""
                )
            }
    }
}
