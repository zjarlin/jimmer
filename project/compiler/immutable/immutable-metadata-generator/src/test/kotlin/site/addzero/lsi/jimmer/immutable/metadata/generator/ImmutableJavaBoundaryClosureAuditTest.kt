package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport

class ImmutableJavaBoundaryClosureAuditTest {

    @Test
    fun `apt shared immutable assembly is fully blocker free`() {
        val sharedShapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.JAVA_SHARED)

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookTable",
                "test.model.BookTableEx",
                "test.model.BookFetcher",
            ),
            sharedShapes.map { it.qualifiedName },
        )
        for (shape in sharedShapes) {
            assertEquals(ImmutableArtifactRole.JAVA_SHARED, shape.role, shape.describe())
            assertEquals(
                emptyList<String>(),
                shape.javaBoundaryBlockers(),
                "APT shared immutable artifact must be Java-clean:\n${shape.describe()}",
            )
        }
    }

    @Test
    fun `kotlin assembly keeps java blockers confined to dsl sidecars`() {
        val allShapes = ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)

        val blockedShapes = allShapes.filter { it.role == ImmutableArtifactRole.KOTLIN_SIDECAR }
        val cleanShapes = allShapes.filter { it.role == ImmutableArtifactRole.JAVA_SHARED }

        assertEquals(
            listOf(
                "test.model.BookDraftDsl",
                "test.model.BookPropsDsl",
                "test.model.BookFetcherDsl",
            ),
            blockedShapes.map { it.qualifiedName },
        )
        assertTrue(
            blockedShapes.all { it.javaBoundaryBlockers().isNotEmpty() },
            blockedShapes.joinToString("\n") { it.describe() },
        )
        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookFetcher",
            ),
            cleanShapes.map { it.qualifiedName },
        )
    }
}
