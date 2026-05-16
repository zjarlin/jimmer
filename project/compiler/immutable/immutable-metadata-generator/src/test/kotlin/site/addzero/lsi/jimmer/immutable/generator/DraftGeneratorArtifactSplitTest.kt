package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.renderJavaSource

class DraftGeneratorArtifactSplitTest {

    @Test
    fun `split kotlin draft dsl into separate artifact`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        assertEquals(2, artifacts.size)
        assertEquals("test.model.BookDraft", artifacts[0].qualifiedName)
        assertTrue(artifacts[0].topLevelCallables.isEmpty())
        assertEquals("test.model.BookDraftDsl", artifacts[1].qualifiedName)
        assertTrue(artifacts[1].types.isEmpty())
        assertEquals(1, artifacts[1].topLevelCallables.size)
    }

    @Test
    fun `omit kotlin draft dsl artifact for java-oriented assembly`() {
        val artifacts = generator().generate(mode = ImmutableGenerationMode.JAVA_SHARED)

        assertEquals(1, artifacts.size)
        assertEquals("test.model.BookDraft", artifacts.single().qualifiedName)
        assertTrue(artifacts.single().topLevelCallables.isEmpty())
    }

    @Test
    fun `minimal draft core is java renderable`() {
        val artifact = generator().generate(mode = ImmutableGenerationMode.JAVA_SHARED).single()

        val source = artifact.renderJavaSource()

        assertTrue(source.contains("public interface BookDraft extends Book, Draft"), source)
        assertTrue(source.contains("class Producer {"), source)
        assertTrue(source.contains("private Producer()"), source)
        assertTrue(source.contains("public static final ImmutableType type"), source)
        assertTrue(!source.contains("BookDraft.$"), source)
    }

    @Test
    fun `draft core uses consumer while kotlin helper keeps lambda sugar`() {
        val artifacts = generatorWithRefBlock().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)

        val draftType = artifacts[0].types.single()
        val coreRefCallable = draftType.callables.single { it.name == "store" && it.parameters.size == 1 }
        val helperRefCallable = artifacts[1].topLevelCallables.single { it.name == "store" }

        val coreBlockType = assertInstanceOf(LsiParameterizedTypeName::class.java, coreRefCallable.parameters.single().type)
        assertEquals("org.babyfish.jimmer.DraftConsumer", coreBlockType.rawType.canonicalName)

        assertInstanceOf(LsiLambdaTypeName::class.java, helperRefCallable.parameters.single().type)
        assertEquals("test.model.BookDraft", helperRefCallable.receiverType.toString())
    }

    @Test
    fun `draft dsl artifact stays outside java render boundary`() {
        val dslArtifact = generatorWithRefBlock().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]
        val helperRefCallable = dslArtifact.topLevelCallables.single { it.name == "store" }

        assertTrue(dslArtifact.topLevelCallables.isNotEmpty())
        assertTrue(helperRefCallable.receiverType != null)
        assertInstanceOf(LsiLambdaTypeName::class.java, helperRefCallable.parameters.single().type)

        assertThrows(IllegalArgumentException::class.java) {
            dslArtifact.renderJavaSource()
        }
    }

    @Test
    fun `richer draft core with builder and associated id is java renderable`() {
        val source = richerGenerator().generate(mode = ImmutableGenerationMode.JAVA_SHARED).single().renderJavaSource()

        assertTrue(source.contains("public interface BookDraft extends Book, Draft"), source)
        assertTrue(source.contains("@JsonIgnore"), source)
        assertTrue(source.contains("Long getStoreId()"), source)
        assertTrue(source.contains("void setStoreId(Long value)"), source)
        assertTrue(source.contains("class Builder {"), source)
        assertTrue(source.contains("public Builder(Book base)"), source)
        assertTrue(source.contains("public Builder store(Store store)"), source)
        assertTrue(source.contains("return this;"), source)
        assertTrue(source.contains("return (Book) __draft.__unwrap();"), source)
        assertTrue(!source.contains(" as Book"), source)
    }

    private fun generator(): DraftGenerator =
        DraftGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            modelTypes = listOf(ImmutableGeneratorTestFixtures.minimalDraftTypeMetadata()),
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

    private fun generatorWithRefBlock(): DraftGenerator =
        DraftGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            modelTypes = listOf(ImmutableGeneratorTestFixtures.refDraftTypeMetadata()),
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

    private fun richerGenerator(): DraftGenerator =
        DraftGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            modelTypes = listOf(ImmutableGeneratorTestFixtures.draftTypeMetadataWithBuilderAndAssociatedId()),
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )
}
