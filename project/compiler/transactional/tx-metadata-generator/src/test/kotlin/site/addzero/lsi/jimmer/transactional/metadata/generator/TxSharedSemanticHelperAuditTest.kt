package site.addzero.lsi.jimmer.transactional.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TxSharedSemanticHelperAuditTest {

    @Test
    fun `tx generator reuses shared carrier normalization`() {
        val source = Files.readString(
            locateRepoRoot().resolve(
                "project/compiler/transactional/tx-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/generator/TxMetadataGenerator.kt"
            )
        )

        assertTrue(
            source.contains("normalizedLsiCarrierQualifiedName"),
            "TxMetadataGenerator must reuse shared carrier normalization\n$source",
        )
        assertTrue(
            source.contains("isLsiVoidLikeQualifiedName"),
            "TxMetadataGenerator 必须复用共享 void-like 判定\n$source",
        )
        assertFalse(
            source.contains("primitiveTypeAliases = mapOf("),
            "TxMetadataGenerator must not keep local primitive alias table\n$source",
        )
        assertFalse(
            source.contains("normalizedQualifiedName() == \"kotlin.Unit\""),
            "TxMetadataGenerator 不得保留本地 Unit 判定\n$source",
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
