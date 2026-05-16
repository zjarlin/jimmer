package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

class ImmutableLambdaBoundaryInventoryAuditTest {

    @Test
    fun `immutable kotlin sidecars keep lambda boundary inventory explicit`() {
        val shapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
            .associateBy(ImmutableArtifactShape::qualifiedName)

        assertEquals(
            listOf(
                "file 'test.model.BookDraftDsl' -> top-level callable 'store' parameter 'block' type",
            ),
            shapes.getValue("test.model.BookDraftDsl").lambdaTypePaths,
        )

        assertEquals(
            listOf(
                "file 'test.model.BookPropsDsl' -> top-level callable 'stores' parameter 'block' type",
                "file 'test.model.BookPropsDsl' -> top-level callable 'fetchBy' parameter 'block' type",
                "file 'test.model.BookPropsDsl' -> top-level callable 'fetchBy' parameter 'block' type",
            ),
            shapes.getValue("test.model.BookPropsDsl").lambdaTypePaths,
        )

        assertEquals(
            listOf(
                "file 'test.model.BookFetcherDsl' -> top-level callable 'by' parameter 'block' type",
                "file 'test.model.BookFetcherDsl' -> top-level callable 'by' parameter 'block' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'cfgBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'childBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'cfgBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'childBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'cfgBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'childBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'cfgBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'childBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store' parameter 'childBlock' type",
                "file 'test.model.BookFetcherDsl' -> type 'BookFetcherDsl' callable 'store*' parameter 'cfgBlock' type",
            ),
            shapes.getValue("test.model.BookFetcherDsl").lambdaTypePaths,
        )
    }
}
