package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class ImmutableAptLegacyChainAuditTest {

    @Test
    fun `apt immutable generator directory keeps only non business helpers`() {
        val helperFiles =
            aptImmutableGeneratorDirectory()
                ?.let { directory ->
                    Files.list(directory).use { paths ->
                        paths
                            .filter { it.isRegularFile() && it.extension == "java" }
                            .map { it.fileName.toString() }
                            .sorted()
                            .toList()
                    }
                }
                ?: emptyList()

        assertEquals(
            emptyList<String>(),
            helperFiles,
        )
    }

    @Test
    fun `apt immutable processor delegates source generation to shared lsi assembly`() {
        val source = immutableProcessorFile().readText()

        assertTrue(source.contains("ImmutableProcessorSupport.generateAptOutput("), source)
        assertTrue(source.contains("Context.INSTANCE.guessGeneratedJimmerResourceFile(\"entities\")"), source)
        assertTrue(source.contains("generatedOutput.getSourceFileSpecs()"), source)
        assertTrue(source.contains("generatedOutput.getResourceArtifacts()"), source)

        val forbiddenSnippets = listOf(
            "ImmutableProcessorSupport.generateSharedArtifacts(",
            "ImmutableProcessorSupport.generateSharedOutput(",
            "ImmutableProcessorSupport.generateAptOnlyArtifacts(",
            "ImmutableProcessorSupport.generateAptOnlyFileSpecs(",
            "ImmutableProcessorSupport.generateAptEntityTableOutput(",
            "ImmutableAptGeneratedArtifactsKt.toAptEntityTableFileSpecs(",
            "GeneratedSourceFileArtifact",
            "new DraftGenerator(",
            "new ProducerGenerator(",
            "new BuilderGenerator(",
            "new ImplementorGenerator(",
            "new ImplGenerator(",
            "new DraftImplGenerator(",
            "new PropsGenerator(",
            "new FetcherGenerator(",
            "new ValidationGenerator(",
            "new CaseAppender(",
            "new TableGenerator(",
            "new EmbeddedPropExpressionGenerator(",
            "ImmutableGeneratedArtifactsKt.toGeneratedArtifacts(",
            "ImmutableAptGeneratedArtifactsKt.toAptOnlyGeneratedArtifacts(",
        )

        for (snippet in forbiddenSnippets) {
            assertFalse(source.contains(snippet), "ImmutableProcessor must not call legacy generator directly: $snippet\n$source")
        }
    }

    @Test
    fun `apt immutable package no longer keeps legacy business generator sources`() {
        val legacyGeneratorNames = setOf(
            "AssociatedIdGenerator.java",
            "BuilderGenerator.java",
            "CaseAppender.java",
            "DraftGenerator.java",
            "DraftImplGenerator.java",
            "FetcherGenerator.java",
            "ImplGenerator.java",
            "ImplementorGenerator.java",
            "ProducerGenerator.java",
            "PropExpressionGenerator.java",
            "PropsGenerator.java",
            "TableGenerator.java",
            "ValidationGenerator.java",
        )

        val presentLegacyFiles = Files.walk(aptImmutableDirectory()).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "java" && it.name in legacyGeneratorNames }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        assertTrue(
            presentLegacyFiles.isEmpty(),
            "APT immutable legacy generators must stay deleted, found: ${presentLegacyFiles.joinToString()}",
        )
    }

    private fun aptImmutableDirectory(): Path {
        val cwd = Path.of("").toAbsolutePath().normalize()
        val candidates = listOf(
            cwd.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable"),
            cwd.resolve("../../../jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable").normalize(),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate APT immutable directory from $cwd")
    }

    private fun aptImmutableGeneratorDirectory(): Path? =
        aptImmutableDirectory().resolve("generator")
            .takeIf(Files::exists)

    private fun immutableProcessorFile(): Path =
        aptImmutableDirectory().resolve("ImmutableProcessor.java")
}
