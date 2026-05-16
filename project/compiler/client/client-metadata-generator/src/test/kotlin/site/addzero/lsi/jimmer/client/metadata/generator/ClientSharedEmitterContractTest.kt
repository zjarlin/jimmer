package site.addzero.lsi.jimmer.client.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ClientSharedEmitterContractTest {

    @Test
    fun `client processor support delegates resource emission to the dedicated generators`() {
        val source = Files.readString(
            repoRoot.resolve(
                "project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator/ClientProcessorSupport.kt"
            )
        )

        assertTrue(source.contains("ClientSchemaMetadataGenerator().generate(metadata)"), source)
        assertTrue(source.contains("ExportDocResourceGenerator().generate(declarations)"), source)
        assertFalse(source.contains("GeneratedResourceArtifact("), source)
        assertFalse(source.contains("LsiFileSpec("), source)
    }

    @Test
    fun `client shared generator tree keeps resource emitters explicit`() {
        val generatedResourceFiles = repoRoot
            .resolve("project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator")
            .let { root ->
                Files.list(root).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".kt") }
                        .filter { Files.readString(it).contains("GeneratedResourceArtifact(") }
                        .map { it.fileName.toString() }
                        .sorted()
                        .toList()
                }
            }

        assertEquals(
            listOf("ClientSchemaMetadataGenerator.kt", "ExportDocResourceGenerator.kt"),
            generatedResourceFiles,
        )
    }

    @Test
    fun `client shared generator tree does not emit source file specs`() {
        val sourceEmitterFiles = repoRoot
            .resolve("project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator")
            .let { root ->
                Files.list(root).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".kt") }
                        .filter { Files.readString(it).contains("LsiFileSpec(") }
                        .map { it.fileName.toString() }
                        .sorted()
                        .toList()
                }
            }

        assertTrue(sourceEmitterFiles.isEmpty(), sourceEmitterFiles.toString())
    }

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
