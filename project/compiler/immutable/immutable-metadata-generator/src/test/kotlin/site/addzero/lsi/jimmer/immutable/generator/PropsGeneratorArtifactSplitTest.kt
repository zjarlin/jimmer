package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.renderKotlinSource
import site.addzero.lsi.poet.renderJavaSource

class PropsGeneratorArtifactSplitTest {

    @Test
    fun `split props kotlin dsl into separate artifact and keep java renderable core`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(2, artifacts.size)
        assertEquals("test.model.BookProps", artifacts[0].qualifiedName)
        assertTrue(artifacts[0].topLevelProperties.isEmpty())
        assertTrue(artifacts[0].topLevelCallables.isEmpty())
        assertEquals("test.model.BookPropsDsl", artifacts[1].qualifiedName)
        assertTrue(artifacts[1].types.isEmpty())
        assertTrue(artifacts[1].topLevelProperties.isNotEmpty())

        val javaSource = artifacts[0].renderJavaSource()
        assertTrue(
            javaSource.contains("TypedProp.scalar(ImmutableType.get(Book.class).getProp(\"id\"))"),
            javaSource,
        )
    }

    @Test
    fun `omit props kotlin dsl artifact for java-oriented assembly`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.JAVA_SHARED)

        assertEquals(1, artifacts.size)
        assertEquals("test.model.BookProps", artifacts.single().qualifiedName)
        assertTrue(artifacts.single().topLevelProperties.isEmpty())
        assertTrue(artifacts.single().topLevelCallables.isEmpty())
    }

    @Test
    fun `props dsl artifact stays outside java render boundary`() {
        val dslArtifact = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]
        val fetchByCallable = dslArtifact.topLevelCallables.first { it.name == "fetchBy" }
        val kotlinSource = dslArtifact.renderKotlinSource()

        assertTrue(dslArtifact.memberImports.isEmpty())
        assertTrue(dslArtifact.topLevelProperties.isNotEmpty())
        assertTrue(fetchByCallable.receiverType != null)
        assertTrue(fetchByCallable.parameters.any { it.type is LsiLambdaTypeName })
        assertTrue(
            "import org.babyfish.jimmer.sql.kt.fetcher.newFetcher" !in kotlinSource,
            kotlinSource,
        )
        assertTrue(
            "org.babyfish.jimmer.sql.kt.fetcher.newFetcher(Book::class).by(block)" in kotlinSource,
            kotlinSource,
        )

        assertThrows(IllegalArgumentException::class.java) {
            dslArtifact.renderJavaSource()
        }
    }

    private fun generator(): PropsGenerator =
        PropsGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookPropsMetadata(),
        )
}
