package site.addzero.lsi.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class ArtifactAggregationModeTest {

    @Test
    fun `classifies one same-source dependency set as isolating`() {
        val source = LsiSource.of("demo/Book.kt", LsiLanguage.KOTLIN)

        assertEquals(
            ArtifactAggregationMode.ISOLATING,
            classifyArtifactAggregationMode(
                originatingSymbols = setOf(LsiSymbolId.type("demo.Book")),
                originatingSources = setOf(source),
                dependencySources = setOf(source),
            ),
        )
    }

    @Test
    fun `classifies cross-source dependency set as aggregating`() {
        val source = LsiSource.of("demo/Book.kt", LsiLanguage.KOTLIN)
        val dependency = LsiSource.of("demo/Marker.kt", LsiLanguage.KOTLIN)

        assertEquals(
            ArtifactAggregationMode.AGGREGATING,
            classifyArtifactAggregationMode(
                originatingSymbols = setOf(LsiSymbolId.type("demo.Book")),
                originatingSources = setOf(source),
                dependencySources = setOf(source, dependency),
            ),
        )
    }

    @Test
    fun `classifies incomplete or multiple origins as aggregating`() {
        val source = LsiSource.of("demo/Book.kt", LsiLanguage.KOTLIN)

        assertEquals(
            ArtifactAggregationMode.AGGREGATING,
            classifyArtifactAggregationMode(
                originatingSymbols = emptySet(),
                originatingSources = emptySet(),
                dependencySources = emptySet(),
            ),
        )
        assertEquals(
            ArtifactAggregationMode.AGGREGATING,
            classifyArtifactAggregationMode(
                originatingSymbols = setOf(
                    LsiSymbolId.type("demo.Book"),
                    LsiSymbolId.type("demo.Author"),
                ),
                originatingSources = setOf(source),
                dependencySources = setOf(source),
            ),
        )
    }
}
