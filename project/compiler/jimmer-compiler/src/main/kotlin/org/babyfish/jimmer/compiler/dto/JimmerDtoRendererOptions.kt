package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import site.addzero.lsi.core.LsiSymbolId

internal data class JimmerDtoRendererOptions(
    val jacksonVersion: JimmerDtoJacksonVersion,
    val hibernateValidatorEnhancement: Boolean,
    val aptFieldVisibility: JimmerDtoFieldVisibility,
    val kspMutable: Boolean,
) {
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

internal enum class JimmerDtoJacksonVersion {
    JACKSON_2,
    JACKSON_3,
}

internal enum class JimmerDtoFieldVisibility {
    PRIVATE,
    PROTECTED,
    PUBLIC,
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
        JimmerDtoFieldVisibility.PRIVATE
    }
    val mutable = platform == CompilerPlatform.KSP &&
        options[KSP_DTO_MUTABLE_OPTION]?.trim() == "true"
    return JimmerDtoRendererOptions(
        jacksonVersion = if (jackson3) {
            JimmerDtoJacksonVersion.JACKSON_3
        } else {
            JimmerDtoJacksonVersion.JACKSON_2
        },
        hibernateValidatorEnhancement = options[HIBERNATE_VALIDATOR_ENHANCEMENT_OPTION] == "true",
        aptFieldVisibility = fieldVisibility,
        kspMutable = mutable,
    )
}

private fun Map<String, String>.aptDtoFieldVisibility(): JimmerDtoFieldVisibility {
    return when (val visibility = this[APT_DTO_FIELD_VISIBILITY_OPTION]) {
        null, "private" -> JimmerDtoFieldVisibility.PRIVATE
        "protected" -> JimmerDtoFieldVisibility.PROTECTED
        "public" -> JimmerDtoFieldVisibility.PUBLIC
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
