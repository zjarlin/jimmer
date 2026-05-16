package site.addzero.lsi.jimmer.transactional.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TxSharedEmitterContractTest {

    @Test
    fun `tx processor support only assembles file specs through TxMetadataGenerator`() {
        val source = readSource(
            "project/compiler/transactional/tx-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/generator/TxProcessorSupport.kt"
        )

        assertTrue(source.contains("private val generator = TxMetadataGenerator()"), source)
        assertTrue(source.contains("types.map(generator::generate)"), source)
        assertFalse(source.contains("LsiFileSpec("), source)
    }

    @Test
    fun `tx shared generator tree keeps file emission constrained to TxMetadataGenerator`() {
        val fileEmitterFiles = generatorSourceFiles()
            .filter { (_, text) -> text.contains("LsiFileSpec(") }
            .map { it.first }
            .sorted()

        assertEquals(listOf("TxMetadataGenerator.kt"), fileEmitterFiles)
    }

    private fun generatorSourceFiles(): List<Pair<String, String>> {
        val root = repoRoot.resolve(
            "project/compiler/transactional/tx-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/generator"
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
