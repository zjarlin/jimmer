package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.poet.renderJavaSource

class ImmutableSharedRenderBoundaryAuditTest {

    @Test
    fun `apt shared immutable artifacts stay java renderable`() {
        val sharedFileSpecs = ImmutableTestSupport.sharedGeneratedFileSpecs(mode = ImmutableGenerationMode.JAVA_SHARED)

        assertEquals(
            listOf(
                "test.model.BookDraft",
                "test.model.BookProps",
                "test.model.BookTable",
                "test.model.BookTableEx",
                "test.model.BookFetcher",
            ),
            sharedFileSpecs.map { it.qualifiedName },
        )

        for (fileSpec in sharedFileSpecs) {
            assertDoesNotThrow(
                { fileSpec.renderJavaSource() },
                "APT shared artifact must stay Java-renderable: ${fileSpec.qualifiedName}",
            )
        }
    }

    @Test
    fun `kotlin only immutable artifacts fail fast with explicit java boundary reason`() {
        val fileSpecs = ImmutableTestSupport.sharedGeneratedFileSpecs(mode = ImmutableGenerationMode.KOTLIN_FULL)

        val expectedBoundaryErrors = mapOf(
            "test.model.BookDraftDsl" to listOf("top-level callables", "LsiLambdaTypeName"),
            "test.model.BookPropsDsl" to listOf("top-level properties", "top-level callables", "LsiLambdaTypeName"),
            "test.model.BookFetcherDsl" to listOf("top-level callables", "LsiLambdaTypeName"),
        )

        for (fileSpec in fileSpecs) {
            val qualifiedName = fileSpec.qualifiedName
            val expectedErrors = expectedBoundaryErrors[qualifiedName]
            if (expectedErrors == null) {
                assertDoesNotThrow(
                    { fileSpec.renderJavaSource() },
                    "Shared core artifact must stay Java-renderable: $qualifiedName",
                )
                continue
            }
            val error = assertThrows(IllegalArgumentException::class.java) {
                fileSpec.renderJavaSource()
            }
            assertTrue(
                expectedErrors.any { error.message!!.contains(it) },
                "Unexpected Java boundary reason for $qualifiedName: ${error.message}",
            )
        }
    }

}
