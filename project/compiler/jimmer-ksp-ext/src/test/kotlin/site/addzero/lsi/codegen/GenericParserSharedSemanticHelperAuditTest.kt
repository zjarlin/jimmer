package site.addzero.lsi.codegen

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GenericParserSharedSemanticHelperAuditTest {

    @Test
    fun `generic parser reuses shared collection carrier normalization`() {
        val source = Files.readString(
            locateRepoRoot().resolve(
                "project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/GenericParser.kt"
            )
        )

        assertTrue(
            source.contains("normalizedLsiCollectionCarrierQualifiedName"),
            "GenericParser must reuse shared collection carrier normalization\n$source",
        )
        assertTrue(
            source.contains("isLsiCollectionLikeQualifiedName"),
            "GenericParser must reuse shared collection predicate\n$source",
        )
        assertFalse(
            source.contains("\"kotlin.collections.MutableList\" -> \"kotlin.collections.List\""),
            "GenericParser must not keep local mutable-list mapping\n$source",
        )
        assertFalse(
            source.contains("\"kotlin.collections.MutableMap\" -> \"kotlin.collections.Map\""),
            "GenericParser must not keep local mutable-map mapping\n$source",
        )
        assertFalse(
            source.contains("startsWith(\"kotlin.collections.\")") ||
                source.contains("startsWith(\"java.util.\")"),
            "GenericParser must not keep local collection-like prefix checks\n$source",
        )
    }

    private fun locateRepoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Cannot locate repository root from ${Path.of("").toAbsolutePath()}")
    }
}
