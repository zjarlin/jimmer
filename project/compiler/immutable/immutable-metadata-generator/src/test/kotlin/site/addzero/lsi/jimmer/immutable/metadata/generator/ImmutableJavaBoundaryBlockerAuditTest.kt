package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport

class ImmutableJavaBoundaryBlockerAuditTest {

    @Test
    fun `immutable artifacts expose complete java boundary blocker set`() {
        val blockersByArtifact = generatedArtifactShapes().associate { shape ->
            shape.qualifiedName to shape.javaBoundaryBlockers()
        }

        assertEquals(
            emptyList<String>(),
            blockersByArtifact.getValue("test.model.BookDraft"),
        )
        assertEquals(
            listOf("top-level callables", "LsiLambdaTypeName", "extension members"),
            blockersByArtifact.getValue("test.model.BookDraftDsl"),
        )
        assertEquals(
            emptyList<String>(),
            blockersByArtifact.getValue("test.model.BookProps"),
        )
        assertEquals(
            listOf("top-level properties", "top-level callables", "LsiLambdaTypeName", "extension members"),
            blockersByArtifact.getValue("test.model.BookPropsDsl"),
        )
        assertEquals(
            emptyList<String>(),
            blockersByArtifact.getValue("test.model.BookFetcher"),
        )
        assertEquals(
            listOf("top-level callables", "LsiLambdaTypeName", "extension members"),
            blockersByArtifact.getValue("test.model.BookFetcherDsl"),
        )
    }

    private fun generatedArtifactShapes(): List<ImmutableArtifactShape> =
        ImmutableTestSupport.sharedArtifactShapes(mode = ImmutableGenerationMode.KOTLIN_FULL)
}
