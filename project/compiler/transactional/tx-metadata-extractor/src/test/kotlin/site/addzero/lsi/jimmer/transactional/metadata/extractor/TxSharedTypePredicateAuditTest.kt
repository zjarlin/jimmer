package site.addzero.lsi.jimmer.transactional.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TxSharedTypePredicateAuditTest {

    @Test
    fun `tx extractor reuses shared root object predicate`() {
        val source = locateRepoRoot().resolve(
            "project/compiler/transactional/tx-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/extractor/TxMetadataExtractor.kt"
        ).readText()

        assertTrue(
            source.contains("isLsiObjectLikeQualifiedName"),
            "TxMetadataExtractor 必须复用共享 object-like 判定\n$source",
        )
        assertTrue(
            source.contains("isSubtypeOfRuntimeExceptionLike"),
            "TxMetadataExtractor 必须复用共享 RuntimeException subtype helper\n$source",
        )
        assertFalse(
            source.contains("setOf(\"kotlin.Any\", \"java.lang.Object\")"),
            "TxMetadataExtractor 不得保留本地 root object 字符串集合\n$source",
        )
        assertFalse(
            source.contains("\"kotlin.RuntimeException\" || typeName == \"java.lang.RuntimeException\""),
            "TxMetadataExtractor 不得保留本地 RuntimeException 字符串判断\n$source",
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
