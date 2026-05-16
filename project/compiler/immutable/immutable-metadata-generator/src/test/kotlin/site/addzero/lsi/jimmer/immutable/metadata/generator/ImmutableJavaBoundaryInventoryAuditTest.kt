package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import site.addzero.lsi.poet.LsiFileSpec

class ImmutableJavaBoundaryInventoryAuditTest {

    @Test
    fun `draft dsl blocker inventory stays minimal and explicit`() {
        val draftDsl = generatedFileSpecs().getValue("test.model.BookDraftDsl")

        assertEquals(emptyList<String>(), draftDsl.memberImports.map { "${it.packageName}.${it.name}" })
        assertEquals(emptyList<String>(), draftDsl.topLevelProperties.map { it.name })
        assertEquals(listOf("store"), draftDsl.topLevelCallables.mapNotNull { it.name })
        assertEquals(emptyList<String>(), draftDsl.types.map { it.name })
    }

    @Test
    fun `props dsl blocker inventory stays explicit`() {
        val propsDsl = generatedFileSpecs().getValue("test.model.BookPropsDsl")

        assertEquals(
            emptyList<String>(),
            propsDsl.memberImports.map { "${it.packageName}.${it.name}" },
        )
        assertEquals(
            listOf("fetchBy", "fetchBy", "stores"),
            propsDsl.topLevelCallables.mapNotNull { it.name }.sorted(),
        )
        assertEquals(
            listOf("id", "id", "id", "id", "stores", "stores?"),
            propsDsl.topLevelProperties.map { it.name }.sorted(),
        )
        assertEquals(emptyList<String>(), propsDsl.types.map { it.name })
    }

    @Test
    fun `fetcher dsl blocker inventory stays explicit`() {
        val fetcherDsl = generatedFileSpecs().getValue("test.model.BookFetcherDsl")

        assertEquals(
            emptyList<String>(),
            fetcherDsl.memberImports.map { "${it.packageName}.${it.name}" },
        )
        assertEquals(emptyList<String>(), fetcherDsl.topLevelProperties.map { it.name })
        assertEquals(listOf("by", "by"), fetcherDsl.topLevelCallables.mapNotNull { it.name }.sorted())
        assertEquals(listOf("BookFetcherDsl"), fetcherDsl.types.map { it.name })
    }

    private fun generatedFileSpecs() =
        ImmutableTestSupport.sharedGeneratedFileSpecs(mode = ImmutableGenerationMode.KOTLIN_FULL)
            .associateBy(LsiFileSpec::qualifiedName)
}
