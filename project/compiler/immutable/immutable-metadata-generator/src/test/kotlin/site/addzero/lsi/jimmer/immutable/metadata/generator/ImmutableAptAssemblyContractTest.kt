package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGeneratorTestFixtures
import site.addzero.lsi.jimmer.immutable.metadata.generator.toGeneratedOutput

class ImmutableAptAssemblyContractTest {

    @Test
    fun `shared immutable assembly excludes kotlin dsl artifacts for apt mode`() {
        val fileSpecs = ImmutableTestSupport.sharedGeneratedFileSpecs(mode = ImmutableGenerationMode.JAVA_SHARED)

        val qualifiedNames = ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(fileSpecs)
        val roles = fileSpecs.associate { it.qualifiedName to it.immutableArtifactRole() }

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookTable",
                "test.model.BookTableEx",
                "test.model.BookFetcher",
            ),
            qualifiedNames,
        )
        assertFalse(roles.values.any { it == ImmutableArtifactRole.KOTLIN_SIDECAR }, roles.toString())
    }

    @Test
    fun `shared immutable assembly keeps kotlin dsl artifacts for ksp mode`() {
        val fileSpecs = ImmutableTestSupport.sharedGeneratedFileSpecs(mode = ImmutableGenerationMode.KOTLIN_FULL)

        val qualifiedNames = ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(fileSpecs)
        val roles = fileSpecs.associate { it.qualifiedName to it.immutableArtifactRole() }

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookDraftDsl",
                "test.model.BookProps",
                "test.model.BookPropsDsl",
                "test.model.BookFetcher",
                "test.model.BookFetcherDsl",
            ),
            qualifiedNames,
        )
        assertEquals(ImmutableArtifactRole.JAVA_SHARED, roles.getValue("test.model.BookDraft"))
        assertEquals(ImmutableArtifactRole.KOTLIN_SIDECAR, roles.getValue("test.model.BookDraftDsl"))
        assertEquals(ImmutableArtifactRole.JAVA_SHARED, roles.getValue("test.model.BookProps"))
        assertEquals(ImmutableArtifactRole.KOTLIN_SIDECAR, roles.getValue("test.model.BookPropsDsl"))
        assertEquals(ImmutableArtifactRole.JAVA_SHARED, roles.getValue("test.model.BookFetcher"))
        assertEquals(ImmutableArtifactRole.KOTLIN_SIDECAR, roles.getValue("test.model.BookFetcherDsl"))
    }

    @Test
    fun `shared immutable output partitions java shared files from kotlin sidecars`() {
        val output = ImmutableTestSupport.sharedGeneratedOutput(mode = ImmutableGenerationMode.KOTLIN_FULL)
        val partition = output.partitionSourceFileSpecs()

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookFetcher",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(partition.javaSharedFileSpecs),
        )
        assertEquals(
            listOf(
                "test.model.BookDraftDsl",
                "test.model.BookPropsDsl",
                "test.model.BookFetcherDsl",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(partition.kotlinSidecarFileSpecs),
        )
    }

    @Test
    fun `kotlin full java shared partition stays a strict subset of apt java shared output`() {
        val kotlinFullOutput = ImmutableTestSupport.sharedGeneratedOutput(mode = ImmutableGenerationMode.KOTLIN_FULL)
        val javaSharedOutput = ImmutableTestSupport.sharedGeneratedOutput(mode = ImmutableGenerationMode.JAVA_SHARED)

        val kotlinPartition = kotlinFullOutput.partitionSourceFileSpecs()

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookFetcher",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(kotlinPartition.javaSharedFileSpecs),
        )
        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookTable",
                "test.model.BookTableEx",
                "test.model.BookFetcher",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(javaSharedOutput.sourceFileSpecs),
        )
        assertEquals(
            javaSharedOutput.resourceArtifacts,
            kotlinFullOutput.javaSharedOutput().resourceArtifacts,
        )
    }

    @Test
    fun `java shared output keeps resources while kotlin sidecar output drops them`() {
        val output = listOf(
            ImmutableGeneratorTestFixtures.referenceSourceGenerationPlan()
        ).toGeneratedOutput(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            existingEntitiesResourceFile = null,
            isResourceGenerationIgnored = false,
            isModuleRequired = false,
            generationMode = ImmutableGenerationMode.KOTLIN_FULL,
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

        val javaSharedOutput = output.javaSharedOutput()
        val kotlinSidecarOutput = output.kotlinSidecarOutput()

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookFetcher",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(javaSharedOutput.sourceFileSpecs),
        )
        assertEquals(output.resourceArtifacts, javaSharedOutput.resourceArtifacts)
        assertEquals(
            listOf(
                "META-INF/jimmer/entities",
            ),
            javaSharedOutput.resourceArtifacts.map { it.path },
        )
        assertEquals(
            listOf(
                "test.model.BookDraftDsl",
                "test.model.BookPropsDsl",
                "test.model.BookFetcherDsl",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(kotlinSidecarOutput.sourceFileSpecs),
        )
        assertTrue(kotlinSidecarOutput.resourceArtifacts.isEmpty())
    }

    @Test
    fun `complete apt immutable assembly comes directly from java shared output`() {
        val output = listOf(
            ImmutableGeneratorTestFixtures.referenceSourceGenerationPlan()
        ).toGeneratedOutput(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            existingEntitiesResourceFile = null,
            isResourceGenerationIgnored = true,
            isModuleRequired = false,
            generationMode = ImmutableGenerationMode.JAVA_SHARED,
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookTable",
                "test.model.BookTableEx",
                "test.model.BookFetcher",
            ),
            ImmutableGeneratorTestFixtures.sourceQualifiedNamesOfFileSpecs(output.sourceFileSpecs),
        )
        assertTrue(output.resourceArtifacts.isEmpty())
    }
}
