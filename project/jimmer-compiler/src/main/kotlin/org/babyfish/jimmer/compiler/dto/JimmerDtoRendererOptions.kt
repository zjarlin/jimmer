package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId

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

internal fun JimmerDtoRendererOptions.effectiveKspMutableByRootTypeId(
    platform: CompilerPlatform,
    schema: JimmerDtoPrecompiledSchema,
): Map<DtoTypeId, Boolean> {
    val result = sortedMapOf<DtoTypeId, Boolean>()
    schema.documents.forEach { document ->
        document.graph.rootTypeIds.forEach { rootTypeId ->
            val rootType = document.graph.typesById.getValue(rootTypeId)
            val mutable = if (platform == CompilerPlatform.KSP) {
                rootType.effectiveKspMutable(kspMutable)
            } else {
                false
            }
            val previous = result.put(rootTypeId, mutable)
            require(previous == null) {
                "DTO KSP renderer plan cannot contain duplicate root type ids: ${rootTypeId.value}"
            }
        }
    }
    return result.toMap()
}

private fun DtoType.effectiveKspMutable(defaultMutable: Boolean): Boolean {
    val annotations = annotations.filter { annotation ->
        annotation.typeId == KOTLIN_DTO_ANNOTATION_TYPE_ID
    }
    if (annotations.isEmpty()) {
        return defaultMutable
    }
    require(annotations.size == 1) {
        "DTO root type cannot declare KotlinDto more than once: ${id.value}"
    }
    val immutabilityArgument = annotations.single().arguments.singleOrNull { argument ->
        argument.name == KOTLIN_DTO_IMMUTABILITY_ARGUMENT
    } ?: error("DTO KotlinDto annotation requires immutability: ${id.value}")
    val immutability = immutabilityArgument.value as? DtoAnnotationValue.EnumValue
        ?: error("DTO KotlinDto immutability must be an enum value: ${id.value}")
    require(immutability.enumTypeId == KOTLIN_DTO_IMMUTABILITY_TYPE_ID) {
        "DTO KotlinDto immutability must use ${KOTLIN_DTO_IMMUTABILITY_TYPE_ID.value}: ${id.value}"
    }
    return when (immutability.constant) {
        "AUTO" -> defaultMutable
        "IMMUTABLE" -> false
        "MUTABLE" -> true
        else -> error(
            "Unsupported DTO KotlinDto immutability '${immutability.constant}': ${id.value}",
        )
    }
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
private const val KOTLIN_DTO_IMMUTABILITY_ARGUMENT = "immutability"
private val KOTLIN_DTO_ANNOTATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDto")
private val KOTLIN_DTO_IMMUTABILITY_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDtoImmutability")
