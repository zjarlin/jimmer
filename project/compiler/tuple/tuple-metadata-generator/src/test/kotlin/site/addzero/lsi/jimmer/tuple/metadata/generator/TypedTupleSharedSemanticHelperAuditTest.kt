package site.addzero.lsi.jimmer.tuple.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TypedTupleSharedSemanticHelperAuditTest {

    @Test
    fun `typed tuple generator reuses shared carrier normalization`() {
        val source = Files.readString(
            locateRepoRoot().resolve(
                "project/compiler/tuple/tuple-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/generator/TypedTupleMetadataGenerator.kt"
            )
        )

        assertTrue(
            source.contains("normalizedLsiCarrierQualifiedName"),
            "TypedTupleMetadataGenerator must reuse shared carrier normalization\n$source",
        )
        assertFalse(
            source.contains("primitiveAliases = mapOf("),
            "TypedTupleMetadataGenerator must not keep local primitive alias table\n$source",
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
