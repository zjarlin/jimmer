package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport

class ImmutableKotlinOnlyShapeAuditTest {

    @Test
    fun `kotlin only type nodes stay outside immutable shared core artifacts`() {
        val shapes = generatedArtifacts().associateBy { it.qualifiedName }

        val draftCore = shapes.getValue("test.model.BookDraft")
        assertEquals(0, draftCore.topLevelCallableCount)
        assertEquals(0, draftCore.topLevelPropertyCount)
        assertTrue(draftCore.memberImportCount == 0)
        assertTrue(draftCore.lambdaTypePaths.isEmpty(), draftCore.describe())
        assertTrue(draftCore.unsupportedUseSiteTargetPaths.isEmpty(), draftCore.describe())
        assertTrue(draftCore.rawCodePaths.isEmpty(), draftCore.describe())
        assertTrue(draftCore.objectTypePaths.isEmpty(), draftCore.describe())

        val propsCore = shapes.getValue("test.model.BookProps")
        assertEquals(0, propsCore.topLevelCallableCount)
        assertEquals(0, propsCore.topLevelPropertyCount)
        assertTrue(propsCore.memberImportCount == 0)
        assertTrue(propsCore.lambdaTypePaths.isEmpty(), propsCore.describe())
        assertTrue(propsCore.unsupportedUseSiteTargetPaths.isEmpty(), propsCore.describe())
        assertTrue(propsCore.rawCodePaths.isEmpty(), propsCore.describe())
        assertTrue(propsCore.objectTypePaths.isEmpty(), propsCore.describe())

        val draftDsl = shapes.getValue("test.model.BookDraftDsl")
        assertEquals(1, draftDsl.topLevelCallableCount)
        assertTrue(draftDsl.memberImportCount == 0)
        assertTrue(draftDsl.unsupportedUseSiteTargetPaths.isEmpty(), draftDsl.describe())
        assertTrue(draftDsl.rawCodePaths.isEmpty(), draftDsl.describe())
        assertTrue(draftDsl.objectTypePaths.isEmpty(), draftDsl.describe())
        assertEquals(
            listOf(
                "file 'test.model.BookDraftDsl' -> top-level callable 'store' parameter 'block' type",
            ),
            draftDsl.lambdaTypePaths,
        )

        val propsDsl = shapes.getValue("test.model.BookPropsDsl")
        assertEquals(0, propsDsl.memberImportCount, propsDsl.describe())
        assertTrue(propsDsl.topLevelPropertyCount > 0, propsDsl.describe())
        assertTrue(propsDsl.topLevelCallableCount > 0, propsDsl.describe())
        assertTrue(propsDsl.unsupportedUseSiteTargetPaths.isEmpty(), propsDsl.describe())
        assertTrue(propsDsl.rawCodePaths.isEmpty(), propsDsl.describe())
        assertTrue(propsDsl.objectTypePaths.isEmpty(), propsDsl.describe())
        assertTrue(
            propsDsl.lambdaTypePaths.all { it.startsWith("file 'test.model.BookPropsDsl' -> top-level callable") },
            propsDsl.describe(),
        )

        val fetcherCore = shapes.getValue("test.model.BookFetcher")
        assertEquals(0, fetcherCore.memberImportCount, fetcherCore.describe())
        assertEquals(0, fetcherCore.topLevelPropertyCount)
        assertEquals(0, fetcherCore.topLevelCallableCount)
        assertTrue(fetcherCore.unsupportedUseSiteTargetPaths.isEmpty(), fetcherCore.describe())
        assertTrue(fetcherCore.rawCodePaths.isEmpty(), fetcherCore.describe())
        assertTrue(fetcherCore.objectTypePaths.isEmpty(), fetcherCore.describe())
        assertTrue(
            fetcherCore.lambdaTypePaths.isEmpty(),
            fetcherCore.describe(),
        )

        val fetcherDsl = shapes.getValue("test.model.BookFetcherDsl")
        assertEquals(0, fetcherDsl.memberImportCount, fetcherDsl.describe())
        assertEquals(0, fetcherDsl.topLevelPropertyCount)
        assertEquals(2, fetcherDsl.topLevelCallableCount)
        assertTrue(fetcherDsl.unsupportedUseSiteTargetPaths.isEmpty(), fetcherDsl.describe())
        assertTrue(fetcherDsl.rawCodePaths.isEmpty(), fetcherDsl.describe())
        assertTrue(fetcherDsl.objectTypePaths.isEmpty(), fetcherDsl.describe())
        assertTrue(
            fetcherDsl.lambdaTypePaths.any { it.contains("top-level callable 'by' parameter 'block'") },
            fetcherDsl.describe(),
        )
        assertTrue(
            fetcherDsl.lambdaTypePaths.any { it.contains("type 'BookFetcherDsl' callable") },
            fetcherDsl.describe(),
        )
    }

    private fun generatedArtifacts(): List<ImmutableArtifactShape> =
        ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
}
