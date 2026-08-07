package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

internal data class JimmerImmutableDraftCodegenOptions(
    val jacksonFamily: JimmerImmutableJacksonFamily,
    val excludedUserAnnotationPrefixes: List<String>,
) {

    init {
        require(excludedUserAnnotationPrefixes.none(String::isBlank)) {
            "Excluded user annotation prefix cannot be blank"
        }
        require(excludedUserAnnotationPrefixes.distinct() == excludedUserAnnotationPrefixes) {
            "Excluded user annotation prefixes must be unique"
        }
    }

    companion object {

        val DEFAULT = JimmerImmutableDraftCodegenOptions(
            jacksonFamily = JimmerImmutableJacksonFamily.JACKSON_2,
            excludedUserAnnotationPrefixes = emptyList(),
        )

        fun from(
            compilerOptions: Map<String, String>,
            workspace: LsiWorkspace,
        ): JimmerImmutableDraftCodegenOptions {
            val jackson3 = compilerOptions["jimmer.jackson3"]?.trim()?.takeIf(String::isNotEmpty)
            val jacksonFamily = when (jackson3) {
                "true" -> JimmerImmutableJacksonFamily.JACKSON_3
                null -> if (workspace[TOOLS_JACKSON_OBJECT_MAPPER] != null) {
                    JimmerImmutableJacksonFamily.JACKSON_3
                } else {
                    JimmerImmutableJacksonFamily.JACKSON_2
                }
                else -> JimmerImmutableJacksonFamily.JACKSON_2
            }
            val excludedPrefixes = compilerOptions["jimmer.excludedUserAnnotationPrefixes"]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.split(ANNOTATION_PREFIX_SEPARATOR)
                ?.filter(String::isNotBlank)
                ?.distinct()
                .orEmpty()
            return JimmerImmutableDraftCodegenOptions(
                jacksonFamily = jacksonFamily,
                excludedUserAnnotationPrefixes = excludedPrefixes,
            )
        }
    }
}

internal enum class JimmerImmutableJacksonFamily {
    JACKSON_2,
    JACKSON_3,
}

private val TOOLS_JACKSON_OBJECT_MAPPER =
    LsiSymbolId.type("tools.jackson.databind.ObjectMapper")

private val ANNOTATION_PREFIX_SEPARATOR = Regex("\\s+|\\s*[,;]\\s*")
