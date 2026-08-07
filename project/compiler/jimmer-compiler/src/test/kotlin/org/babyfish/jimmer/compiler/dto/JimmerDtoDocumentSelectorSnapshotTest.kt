package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelector
import site.addzero.lsi.core.LsiSymbolId

class JimmerDtoDocumentSelectorSnapshotTest {

    @Test
    fun `canonical selector text preserves every resolution input`() {
        val selector = selector(
            sourceName = "Book",
            fallback = "demo.Book",
            wildcards = listOf("first.Book", "second.Book"),
        )

        assertEquals(
            ":4:Book:true:14:type:demo.Book:2:15:type:first.Book:16:type:second.Book",
            selector.canonicalText(),
        )
        assertNotEquals(
            selector.canonicalText(),
            selector(sourceName = "Alias", fallback = "demo.Book", wildcards = selector.wildcards()).canonicalText(),
        )
        assertNotEquals(
            selector.canonicalText(),
            selector(sourceName = "Book", fallback = "other.Book", wildcards = selector.wildcards()).canonicalText(),
        )
        assertNotEquals(
            selector.canonicalText(),
            selector(
                sourceName = "Book",
                fallback = "demo.Book",
                wildcards = selector.wildcards().reversed(),
            ).canonicalText(),
        )
        assertNotEquals(
            selector.canonicalText(),
            CompilerInputDocumentTypeSelector(
                sourceName = "Book",
                fallbackTypeId = typeId("demo.Book"),
                wildcardTypeIds = selector.wildcardTypeIds,
                checksFallbackExistence = false,
            ).canonicalText(),
        )
    }

    private fun selector(
        sourceName: String,
        fallback: String,
        wildcards: List<String>,
    ): CompilerInputDocumentTypeSelector {
        return CompilerInputDocumentTypeSelector(
            sourceName = sourceName,
            fallbackTypeId = typeId(fallback),
            wildcardTypeIds = wildcards.map(::typeId),
        )
    }

    private fun CompilerInputDocumentTypeSelector.wildcards(): List<String> {
        return wildcardTypeIds.map(LsiSymbolId::requireTypeQualifiedName)
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)
}
