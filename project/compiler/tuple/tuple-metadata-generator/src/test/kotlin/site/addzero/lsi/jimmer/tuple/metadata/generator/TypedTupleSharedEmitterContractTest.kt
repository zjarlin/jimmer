package site.addzero.lsi.jimmer.tuple.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TypedTupleSharedEmitterContractTest {

    @Test
    fun `typed tuple processor support only assembles file specs through TypedTupleMetadataGenerator`() {
        val source = readSource(
            "project/compiler/tuple/tuple-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/generator/TypedTupleProcessorSupport.kt"
        )

        assertTrue(source.contains("private val generator = TypedTupleMetadataGenerator()"), source)
        assertTrue(source.contains("types.map(generator::generate)"), source)
        assertFalse(source.contains("LsiFileSpec("), source)
    }

    @Test
    fun `typed tuple shared generator tree keeps file emission constrained to TypedTupleMetadataGenerator`() {
        val fileEmitterFiles = generatorSourceFiles()
            .filter { (_, text) -> text.contains("LsiFileSpec(") }
            .map { it.first }
            .sorted()

        assertEquals(listOf("TypedTupleMetadataGenerator.kt"), fileEmitterFiles)
    }

    private fun generatorSourceFiles(): List<Pair<String, String>> {
        val root = repoRoot.resolve(
            "project/compiler/tuple/tuple-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/generator"
        )
        return Files.list(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .map { it.fileName.toString() to Files.readString(it) }
                .sorted(compareBy { it.first })
                .toList()
        }
    }

    private fun readSource(relativePath: String): String =
        Files.readString(repoRoot.resolve(relativePath))

    private val repoRoot: Path by lazy {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return@lazy current
            }
            current = current.parent
        }
        error("Cannot locate repository root from ${Path.of("").toAbsolutePath()}")
    }
}
