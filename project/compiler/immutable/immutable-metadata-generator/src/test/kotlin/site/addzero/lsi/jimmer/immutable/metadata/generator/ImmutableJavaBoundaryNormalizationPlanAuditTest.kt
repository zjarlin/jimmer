package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

class ImmutableJavaBoundaryNormalizationPlanAuditTest {

    @Test
    fun `java shared immutable artifacts have no pending normalization blockers`() {
        val shapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
            .filter { it.role == ImmutableArtifactRole.JAVA_SHARED }

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookFetcher",
            ),
            shapes.map(ImmutableArtifactShape::qualifiedName),
        )
        for (shape in shapes) {
            assertEquals(emptyList<String>(), shape.pendingJavaNormalizationBlockers(), shape.describe())
            assertEquals(emptyList<String>(), shape.kotlinSidecarRetainedBlockers(), shape.describe())
        }
    }

    @Test
    fun `kotlin sidecar immutable artifacts retain only approved blocker families`() {
        val shapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
            .filter { it.role == ImmutableArtifactRole.KOTLIN_SIDECAR }
            .associateBy(ImmutableArtifactShape::qualifiedName)

        assertEquals(
            listOf(
                "LsiLambdaTypeName",
                "extension members",
                "top-level callables",
            ),
            shapes.getValue("test.model.BookDraftDsl").kotlinSidecarRetainedBlockers().sorted(),
        )
        assertEquals(
            emptyList<String>(),
            shapes.getValue("test.model.BookDraftDsl").pendingJavaNormalizationBlockers(),
        )

        assertEquals(
            listOf(
                "LsiLambdaTypeName",
                "extension members",
                "top-level callables",
                "top-level properties",
            ),
            shapes.getValue("test.model.BookPropsDsl").kotlinSidecarRetainedBlockers().sorted(),
        )
        assertEquals(
            emptyList<String>(),
            shapes.getValue("test.model.BookPropsDsl").pendingJavaNormalizationBlockers(),
        )

        assertEquals(
            listOf(
                "LsiLambdaTypeName",
                "extension members",
                "top-level callables",
            ),
            shapes.getValue("test.model.BookFetcherDsl").kotlinSidecarRetainedBlockers().sorted(),
        )
        assertEquals(
            emptyList<String>(),
            shapes.getValue("test.model.BookFetcherDsl").pendingJavaNormalizationBlockers(),
        )
    }
}
