package site.addzero.lsi.jimmer.error.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ErrorSharedPrimitivePredicateAuditTest {

    @Test
    fun `error metadata extractor reuses shared primitive predicate`() {
        val source = Files.readString(
            locateRepoRoot().resolve(
                "project/compiler/error/error-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/error/metadata/extractor/ErrorMetadataExtractor.kt"
            )
        )

        assertTrue(
            source.contains("isLsiPrimitiveLikeQualifiedName"),
            "ErrorMetadataExtractor 必须复用 shared primitive-like helper\n$source",
        )
        assertFalse(
            source.contains("typeName in setOf("),
            "ErrorMetadataExtractor 不得保留本地 primitive 字符串表\n$source",
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
