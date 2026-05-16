package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.generator.DraftGenerator
import site.addzero.lsi.jimmer.immutable.generator.FetcherGenerator
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGeneratorTestFixtures
import site.addzero.lsi.jimmer.immutable.generator.PropsGenerator

class ImmutableKotlinSidecarEmitterContractTest {

    @Test
    fun `BookDraftDsl is emitted only by DraftGenerator`() {
        val artifacts = draftGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(
            listOf("test.model.BookDraft", "test.model.BookDraftDsl"),
            artifacts.map { it.qualifiedName },
        )

        val sidecar = artifacts[1]
        assertTrue(sidecar.types.isEmpty(), sidecar.toString())
        assertTrue(sidecar.topLevelProperties.isEmpty(), sidecar.toString())
        assertEquals(listOf("store"), sidecar.topLevelCallables.map { it.name })
    }

    @Test
    fun `BookPropsDsl is emitted only by PropsGenerator`() {
        val artifacts = propsGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(
            listOf("test.model.BookProps", "test.model.BookPropsDsl"),
            artifacts.map { it.qualifiedName },
        )

        val sidecar = artifacts[1]
        assertTrue(sidecar.types.isEmpty(), sidecar.toString())
        assertTrue(sidecar.topLevelProperties.isNotEmpty(), sidecar.toString())
        assertEquals(
            listOf("stores", "fetchBy", "fetchBy"),
            sidecar.topLevelCallables.map { it.name },
        )
    }

    @Test
    fun `BookFetcherDsl is emitted only by FetcherGenerator`() {
        val artifacts = fetcherGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(
            listOf("test.model.BookFetcher", "test.model.BookFetcherDsl"),
            artifacts.map { it.qualifiedName },
        )

        val sidecar = artifacts[1]
        assertTrue(sidecar.topLevelProperties.isEmpty(), sidecar.toString())
        assertEquals(listOf("by", "by"), sidecar.topLevelCallables.map { it.name })
        assertEquals(listOf("BookFetcherDsl"), sidecar.types.map { it.name })
    }

    private fun draftGenerator(): DraftGenerator =
        DraftGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            modelTypes = listOf(ImmutableGeneratorTestFixtures.refDraftTypeMetadata()),
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

    private fun propsGenerator(): PropsGenerator =
        PropsGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookPropsMetadata(),
        )

    private fun fetcherGenerator(): FetcherGenerator =
        FetcherGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookFetcherReferenceMetadata(),
        )
}
