package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CompilerUnifiedEmitterContractAuditTest {

    @Test
    fun `simple shared compiler chains keep a single canonical artifact emitter`() {
        assertEquals(
            listOf("DtoGenerator.kt"),
            kotlinFileEmitters(
                "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto",
            ),
        )
        assertEquals(
            listOf("ErrorMetadataGenerator.kt"),
            kotlinFileEmitters(
                "project/compiler/error/error-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/error/metadata/generator",
            ),
        )
        assertEquals(
            listOf("TxMetadataGenerator.kt"),
            kotlinFileEmitters(
                "project/compiler/transactional/tx-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/generator",
            ),
        )
        assertEquals(
            listOf("TypedTupleMetadataGenerator.kt"),
            kotlinFileEmitters(
                "project/compiler/tuple/tuple-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/generator",
            ),
        )
    }

    @Test
    fun `client shared chain keeps resource emission boundary explicit`() {
        assertEquals(
            listOf("ClientSchemaMetadataGenerator.kt", "ExportDocResourceGenerator.kt"),
            resourceArtifactEmitters(
                "project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator",
            ),
        )
        assertEquals(
            emptyList<String>(),
            kotlinFileEmitters(
                "project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator",
            ),
        )
    }

    @Test
    fun `shared processor supports stay on generator orchestration instead of direct emission`() {
        val dtoSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoProcessorSupport.kt"
        )
        assertTrue(dtoSupport.contains("val generator = DtoGenerator("), dtoSupport)
        assertTrue(dtoSupport.contains("generator.generate()?.let(fileSpecs::add)"), dtoSupport)
        assertFalse(dtoSupport.contains("LsiFileSpec("), dtoSupport)

        val errorSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/error/error-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/error/metadata/generator/ErrorProcessorSupport.kt"
        )
        assertTrue(errorSupport.contains("private val generator = ErrorMetadataGenerator()"), errorSupport)
        assertTrue(errorSupport.contains("generator.generate("), errorSupport)
        assertFalse(errorSupport.contains("LsiFileSpec("), errorSupport)

        val txSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/transactional/tx-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/transactional/metadata/generator/TxProcessorSupport.kt"
        )
        assertTrue(txSupport.contains("private val generator = TxMetadataGenerator()"), txSupport)
        assertTrue(txSupport.contains("types.map(generator::generate)"), txSupport)
        assertFalse(txSupport.contains("LsiFileSpec("), txSupport)

        val tupleSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/tuple/tuple-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/tuple/metadata/generator/TypedTupleProcessorSupport.kt"
        )
        assertTrue(tupleSupport.contains("private val generator = TypedTupleMetadataGenerator()"), tupleSupport)
        assertTrue(tupleSupport.contains("types.map(generator::generate)"), tupleSupport)
        assertFalse(tupleSupport.contains("LsiFileSpec("), tupleSupport)

        val clientSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client/metadata/generator/ClientProcessorSupport.kt"
        )
        assertTrue(clientSupport.contains("ClientSchemaMetadataGenerator().generate(metadata)"), clientSupport)
        assertTrue(clientSupport.contains("ExportDocResourceGenerator().generate(declarations)"), clientSupport)
        assertFalse(clientSupport.contains("GeneratedResourceArtifact("), clientSupport)
        assertFalse(clientSupport.contains("LsiFileSpec("), clientSupport)
    }

    @Test
    fun `immutable shared chain stays on assembled output contract`() {
        val immutableSupport = CompilerAuditTestSupport.sourceOf(
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/generator/ImmutableProcessorSupport.kt"
        )

        assertTrue(immutableSupport.contains("fun generateKspOutput("), immutableSupport)
        assertTrue(immutableSupport.contains("fun generateAptOutput("), immutableSupport)
        assertTrue(immutableSupport.contains("resolvedSources.toGeneratedOutput("), immutableSupport)
        assertFalse(immutableSupport.contains("generateAptEntityTable"), immutableSupport)
        assertFalse(immutableSupport.contains("renderKotlinSource("), immutableSupport)
        assertFalse(immutableSupport.contains("renderJavaSource("), immutableSupport)
        assertFalse(immutableSupport.contains("createSourceFile("), immutableSupport)
    }

    private fun kotlinFileEmitters(relativeDir: String): List<String> =
        listFiles(relativeDir)
            .filter { (_, text) -> text.contains("LsiFileSpec(") }
            .map { it.first }
            .sorted()

    private fun resourceArtifactEmitters(relativeDir: String): List<String> =
        listFiles(relativeDir)
            .filter { (_, text) -> text.contains("GeneratedResourceArtifact(") }
            .map { it.first }
            .sorted()

    private fun listFiles(relativeDir: String): List<Pair<String, String>> {
        val root = CompilerAuditTestSupport.repoRoot.resolve(relativeDir)
        return Files.list(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") || it.fileName.toString().endsWith(".java") }
                .map { it.fileName.toString() to Files.readString(it) }
                .sorted(compareBy { it.first })
                .toList()
        }
    }
}
