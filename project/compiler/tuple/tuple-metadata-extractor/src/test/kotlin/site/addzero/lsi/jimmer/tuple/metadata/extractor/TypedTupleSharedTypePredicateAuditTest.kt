package site.addzero.lsi.jimmer.tuple.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TypedTupleSharedTypePredicateAuditTest {

    @Test
    fun `typed tuple extractor reuses shared root object predicate`() {
        val source = locateRepoRoot().resolve(
            "project/compiler/tuple/tuple-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/extractor/TypedTupleMetadataExtractor.kt"
        ).readText()

        assertTrue(
            source.contains("isLsiObjectLikeQualifiedName"),
            "TypedTupleMetadataExtractor 必须复用共享 object-like 判定\n$source",
        )
        assertFalse(
            source.contains("ROOT_SUPER_TYPES = setOf("),
            "TypedTupleMetadataExtractor 不得保留本地 root super type truth table\n$source",
        )
    }

    private fun locateRepoRoot(): File {
        var current = File(".").absoluteFile
        while (current.parentFile != null) {
            if (current.resolve("settings.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile
        }
        error("Cannot locate repository root from ${File(".").absoluteFile}")
    }
}
