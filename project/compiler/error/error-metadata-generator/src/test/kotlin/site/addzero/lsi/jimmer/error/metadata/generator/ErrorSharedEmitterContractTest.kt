package site.addzero.lsi.jimmer.error.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ErrorSharedEmitterContractTest {

    @Test
    fun `error processor support only assembles file specs through ErrorMetadataGenerator`() {
        val source = readSource(
            "project/compiler/error/error-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/error/metadata/generator/ErrorProcessorSupport.kt"
        )

        assertTrue(source.contains("private val generator = ErrorMetadataGenerator()"), source)
        assertTrue(source.contains("types.map { metadata ->"), source)
        assertTrue(source.contains("generator.generate("), source)
        assertFalse(source.contains("LsiFileSpec("), source)
    }

    @Test
    fun `error shared generator tree keeps file emission constrained to ErrorMetadataGenerator`() {
        val fileEmitterFiles = generatorSourceFiles()
            .filter { (_, text) -> text.contains("LsiFileSpec(") }
            .map { it.first }
            .sorted()

        assertEquals(listOf("ErrorMetadataGenerator.kt"), fileEmitterFiles)
    }

    private fun generatorSourceFiles(): List<Pair<String, String>> {
        val root = repoRoot.resolve(
            "project/compiler/error/error-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/error/metadata/generator"
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
