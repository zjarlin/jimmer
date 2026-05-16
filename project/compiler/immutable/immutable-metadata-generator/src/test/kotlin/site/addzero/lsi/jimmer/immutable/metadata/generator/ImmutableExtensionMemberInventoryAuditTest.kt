package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

class ImmutableExtensionMemberInventoryAuditTest {

    @Test
    fun `immutable kotlin sidecars keep extension member inventory explicit`() {
        val shapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
            .associateBy(ImmutableArtifactShape::qualifiedName)

        assertEquals(
            listOf(
                "file 'test.model.BookDraftDsl' -> top-level callable 'store'",
            ),
            shapes.getValue("test.model.BookDraftDsl").extensionMemberPaths,
        )

        assertEquals(
            listOf(
                "file 'test.model.BookPropsDsl' -> top-level property 'id'",
                "file 'test.model.BookPropsDsl' -> top-level property 'id'",
                "file 'test.model.BookPropsDsl' -> top-level property 'stores?'",
                "file 'test.model.BookPropsDsl' -> top-level property 'stores'",
                "file 'test.model.BookPropsDsl' -> top-level property 'id'",
                "file 'test.model.BookPropsDsl' -> top-level property 'id'",
                "file 'test.model.BookPropsDsl' -> top-level callable 'stores'",
                "file 'test.model.BookPropsDsl' -> top-level callable 'fetchBy'",
                "file 'test.model.BookPropsDsl' -> top-level callable 'fetchBy'",
            ),
            shapes.getValue("test.model.BookPropsDsl").extensionMemberPaths,
        )

        assertEquals(
            listOf(
                "file 'test.model.BookFetcherDsl' -> top-level callable 'by'",
                "file 'test.model.BookFetcherDsl' -> top-level callable 'by'",
            ),
            shapes.getValue("test.model.BookFetcherDsl").extensionMemberPaths,
        )
    }
}
