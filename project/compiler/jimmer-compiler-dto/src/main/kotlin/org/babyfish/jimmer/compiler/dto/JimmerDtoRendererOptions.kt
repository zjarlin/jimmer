package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerRound
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiVisibility

data class JimmerDtoRendererOptions(
    val jacksonVersion: JacksonFamily,
    val hibernateValidatorEnhancement: Boolean,
    val aptFieldVisibility: LsiVisibility,
    val kspMutable: Boolean,
) {
    init {
        require(
            aptFieldVisibility == LsiVisibility.PRIVATE ||
                aptFieldVisibility == LsiVisibility.PROTECTED ||
                aptFieldVisibility == LsiVisibility.PUBLIC
        ) {
            "APT DTO field visibility must be private, protected or public: $aptFieldVisibility"
        }
    }

    val fingerprint: String = buildString {
        append(jacksonVersion.name)
        append(':')
        append(hibernateValidatorEnhancement)
        append(':')
        append(aptFieldVisibility.name)
        append(':')
        append(kspMutable)
    }
}

internal fun CompilerRound.toJimmerDtoRendererOptions(): JimmerDtoRendererOptions {
    val jackson3Option = options[JACKSON_3_OPTION]
    val jackson3 = if (jackson3Option.isNullOrEmpty()) {
        JACKSON_3_OBJECT_MAPPER_TYPE_ID in availableTypeIds
    } else {
        jackson3Option == "true"
    }
    val fieldVisibility = if (platform == CompilerPlatform.APT) {
        options.aptDtoFieldVisibility()
    } else {
        LsiVisibility.PRIVATE
    }
    val mutable = platform == CompilerPlatform.KSP &&
        options[KSP_DTO_MUTABLE_OPTION]?.trim() == "true"
    return JimmerDtoRendererOptions(
        jacksonVersion = if (jackson3) {
            JacksonFamily.JACKSON_3
        } else {
            JacksonFamily.JACKSON_2
        },
        hibernateValidatorEnhancement = options[HIBERNATE_VALIDATOR_ENHANCEMENT_OPTION] == "true",
        aptFieldVisibility = fieldVisibility,
        kspMutable = mutable,
    )
}

private fun Map<String, String>.aptDtoFieldVisibility(): LsiVisibility {
    return when (val visibility = this[APT_DTO_FIELD_VISIBILITY_OPTION]) {
        null, "private" -> LsiVisibility.PRIVATE
        "protected" -> LsiVisibility.PROTECTED
        "public" -> LsiVisibility.PUBLIC
        else -> throw IllegalArgumentException(
            "The apt options `$APT_DTO_FIELD_VISIBILITY_OPTION` can only be " +
                "\"private\", \"protected\" or \"public\", but got \"$visibility\"",
        )
    }
}

private const val JACKSON_3_OPTION = "jimmer.jackson3"
private const val HIBERNATE_VALIDATOR_ENHANCEMENT_OPTION = "jimmer.dto.hibernateValidatorEnhancement"
private const val APT_DTO_FIELD_VISIBILITY_OPTION = "jimmer.dto.fieldVisibility"
private const val KSP_DTO_MUTABLE_OPTION = "jimmer.dto.mutable"
