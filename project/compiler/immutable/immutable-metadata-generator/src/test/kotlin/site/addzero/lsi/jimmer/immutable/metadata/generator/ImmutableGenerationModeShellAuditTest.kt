package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Files

class ImmutableGenerationModeShellAuditTest {

    @Test
    fun `ksp immutable processor uses kotlin full generation mode`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/ImmutableProcessor.kt"
            )
        )

        assertTrue(source.contains("ImmutableProcessorSupport.generateKspOutput("), source)
        assertTrue(source.contains("generatedOutput.sourceFileSpecs.forEach(::writeFileSpec)"), source)
        assertTrue(source.contains("generatedOutput.resourceArtifacts.forEach(::writeResourceArtifact)"), source)
        assertTrue(source.contains("private fun writeFileSpec(fileSpec: LsiFileSpec)"), source)
        assertFalse(source.contains("validateImmutableTopLevelAnnotatedTypes("), source)
        assertFalse(source.contains("GeneratedSourceFileArtifact"), source)
        assertFalse(source.contains("ImmutableGenerationMode.KOTLIN_FULL"), source)
    }

    @Test
    fun `apt immutable processor uses java shared generation mode`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/ImmutableProcessor.java"
            )
        )

        assertTrue(source.contains("ImmutableProcessorSupport.generateAptOutput("), source)
        assertFalse(source.contains("validateImmutableTopLevelAnnotatedTypes("), source)
        assertFalse(source.contains("ImmutableGenerationMode.JAVA_SHARED"), source)
    }

    @Test
    fun `immutable processor support exposes only explicit backend generation entrypoints`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/generator/ImmutableProcessorSupport.kt"
            )
        )

        assertTrue(source.contains("fun generateKspOutput("), source)
        assertTrue(source.contains("private fun generateJavaSharedOutput("), source)
        assertTrue(source.contains("fun generateAptOutput("), source)
        assertTrue(source.contains("private fun validateImmutableTopLevelAnnotatedTypes("), source)
        assertTrue(source.contains("validateImmutableTopLevelAnnotatedTypes(resolver)"), source)
        assertFalse(source.contains("generateAptEntityTableOutput("), source)
        assertFalse(source.contains("generateAptEntityTableFileSpecs("), source)
        assertFalse(source.contains("toAptEntityTableFileSpecs()"), source)
        assertFalse(source.contains("fun generateSharedOutput("), source)
        assertFalse(source.contains("fun generateSharedFileSpecs("), source)
        assertFalse(source.contains("fun generateSharedResourceArtifacts("), source)
    }

    @Test
    fun `immutable generated artifacts folds java table generation into shared java mode`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/generator/ImmutableGeneratedArtifacts.kt"
            )
        )

        assertTrue(source.contains("generationMode == ImmutableGenerationMode.JAVA_SHARED"), source)
        assertTrue(source.contains("propsTypeMetadata.isEntity"), source)
        assertTrue(source.contains("TableGenerator(propsTypeMetadata).generate()"), source)
    }

    @Test
    fun `apt extra immutable branch file stays deleted`() {
        val file = ImmutableTestSupport.repoRoot.resolve(
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/generator/ImmutableAptGeneratedArtifacts.kt"
        )

        assertFalse(Files.exists(file), file.toString())
    }

    @Test
    fun `immutable shared generator no longer exposes boolean compatibility entrypoints`() {
        val files = listOf(
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/generator/ImmutableGeneratedArtifacts.kt" to
                "includeKotlinDslArtifacts",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/DraftGenerator.kt" to
                "fun generate(includeKotlinDslArtifacts",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/PropsGenerator.kt" to
                "fun generate(includeKotlinDslArtifacts",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/FetcherGenerator.kt" to
                "fun generate(includeKotlinDslArtifacts",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/ImmutableGenerationMode.kt" to
                "includeKotlinDslArtifacts",
        )

        for ((relativePath, forbiddenSnippet) in files) {
            val source = Files.readString(ImmutableTestSupport.repoRoot.resolve(relativePath))
            assertFalse(source.contains(forbiddenSnippet), "$relativePath must not expose boolean compatibility entrypoints\n$source")
        }
    }

    @Test
    fun `immutable shared generator requires explicit generation mode`() {
        val files = listOf(
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/DraftGenerator.kt",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/PropsGenerator.kt",
            "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/FetcherGenerator.kt",
        )

        for (relativePath in files) {
            val source = Files.readString(ImmutableTestSupport.repoRoot.resolve(relativePath))
            assertFalse(
                source.contains("fun generate(mode: ImmutableGenerationMode ="),
                "$relativePath must require explicit generation mode\n$source",
            )
        }
    }
}
