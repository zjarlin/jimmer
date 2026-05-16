package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.renderKotlinSource
import site.addzero.lsi.poet.renderJavaSource

class FetcherGeneratorArtifactSplitTest {

    @Test
    fun `split fetcher artifacts for kotlin oriented assembly`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(
            listOf("test.model.BookFetcher", "test.model.BookFetcherDsl"),
            artifacts.map { it.qualifiedName },
        )

        val coreType = artifacts[0].types.single()
        val rootFetcher = coreType.properties.single { it.name == "\$" }
        val initializer = assertInstanceOf(LsiNewExpression::class.java, rootFetcher.initializer)
        assertEquals(listOf(LsiNullExpression), initializer.arguments)

        val dslSource = artifacts[1].renderKotlinSource()
        assertTrue(dslSource.contains("BookFetcher.`\$`"), dslSource)
    }

    @Test
    fun `keep shared fetcher core for java oriented assembly`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.JAVA_SHARED)

        assertEquals(
            listOf("test.model.BookFetcher"),
            artifacts.map { it.qualifiedName },
        )
    }

    @Test
    fun `only fetcher dsl artifact stays outside java render boundary`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertDoesNotThrow {
            artifacts[0].renderJavaSource()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            artifacts[1].renderJavaSource()
        }
        assertTrue(
            error.message!!.contains("top-level callables") ||
                error.message!!.contains("LsiLambdaTypeName"),
            error.message,
        )
    }

    private fun generator(): FetcherGenerator =
        FetcherGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookFetcherListMetadata(),
        )
}
