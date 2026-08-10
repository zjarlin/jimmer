package org.babyfish.jimmer.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LsiPublicModelRulesTest {

    @Test
    fun `accepts interfaces and internal frozen implementations`() {
        val violations = findLsiPublicModelViolations(
            sources = listOf(
                source(
                    "type/LsiType.kt",
                    """
                    package site.addzero.lsi.type
                    sealed interface LsiType
                    interface LsiDeclaredType : LsiType
                    internal data class FrozenLsiType(val name: String) : LsiType
                    internal data class FrozenLsiDeclaredType(val name: String) : LsiDeclaredType
                    """.trimIndent(),
                ),
            ),
            requiredInterfaces = setOf(
                "site.addzero.lsi.type.LsiType",
                "site.addzero.lsi.type.LsiDeclaredType",
            ),
            forbiddenLegacyNames = LEGACY_NAMES,
        )

        assertTrue(violations.isEmpty(), violations.toString())
    }

    @Test
    fun `rejects concrete structural models aliases and legacy declarations`() {
        val violations = findLsiPublicModelViolations(
            sources = listOf(
                source(
                    "model/Legacy.kt",
                    """
                    package site.addzero.lsi.model
                    data class LsiType(val name: String)
                    typealias LsiClass = LsiType
                    data class LsiOverride(val target: String)
                    interface LsiFunction
                    """.trimIndent(),
                ),
            ),
            requiredInterfaces = setOf(
                "site.addzero.lsi.type.LsiType",
                "site.addzero.lsi.clazz.LsiClass",
                "site.addzero.lsi.model.LsiOverride",
            ),
            forbiddenLegacyNames = LEGACY_NAMES,
        )

        assertEquals(
            listOf(
                "missing required LSI interface site.addzero.lsi.clazz.LsiClass",
                "missing required LSI interface site.addzero.lsi.model.LsiOverride",
                "missing required LSI interface site.addzero.lsi.type.LsiType",
                "model/Legacy.kt:2: structural LSI declaration LsiType must be an interface",
                "model/Legacy.kt:3: structural LSI declaration LsiClass must be an interface",
                "model/Legacy.kt:4: structural LSI declaration LsiOverride must be an interface",
                "model/Legacy.kt:5: forbidden legacy LSI declaration LsiFunction",
            ),
            violations,
        )
    }

    private fun source(path: String, content: String): LsiPublicModelSource {
        return LsiPublicModelSource(path, content)
    }

    private companion object {
        val LEGACY_NAMES = setOf("LsiFunction", "LsiTypeName", "LsiTypeHierarchyEntry")
    }
}
