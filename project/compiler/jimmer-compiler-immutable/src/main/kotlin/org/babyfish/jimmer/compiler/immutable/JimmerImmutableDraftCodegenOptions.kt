package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

data class JimmerImmutableDraftCodegenOptions(
    val jacksonFamily: JacksonFamily,
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
            jacksonFamily = JacksonFamily.JACKSON_2,
            excludedUserAnnotationPrefixes = emptyList(),
        )

        fun from(
            compilerOptions: Map<String, String>,
            workspace: LsiWorkspace,
        ): JimmerImmutableDraftCodegenOptions {
            val jackson3 = compilerOptions["jimmer.jackson3"]?.trim()?.takeIf(String::isNotEmpty)
            val jacksonFamily = when (jackson3) {
                "true" -> JacksonFamily.JACKSON_3
                null -> if (workspace[TOOLS_JACKSON_OBJECT_MAPPER] != null) {
                    JacksonFamily.JACKSON_3
                } else {
                    JacksonFamily.JACKSON_2
                }
                else -> JacksonFamily.JACKSON_2
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

private val TOOLS_JACKSON_OBJECT_MAPPER =
    LsiSymbolId.type("tools.jackson.databind.ObjectMapper")

private val ANNOTATION_PREFIX_SEPARATOR = Regex("\\s+|\\s*[,;]\\s*")
