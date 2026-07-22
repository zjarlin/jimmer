package site.addzero.lsi.codegen

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.core.LsiSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedArtifactSetTest {

    private val sourceId = LsiSymbolId.type("example.Book")

    @Test
    fun `完全相同的产物只登记一次`() {
        val artifact = GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = "example.BookDraft",
            content = "package example\nclass BookDraft",
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(sourceId)
        )
        val artifacts = GeneratedArtifactSet()

        assertEquals(ArtifactRegistration.ADDED, artifacts.register(artifact))
        assertEquals(ArtifactRegistration.DUPLICATE, artifacts.register(artifact.copy()))
        assertEquals(listOf(artifact), artifacts.snapshot())
    }

    @Test
    fun `同一路径的不同内容直接冲突`() {
        val first = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "first",
            aggregationMode = ArtifactAggregationMode.AGGREGATING
        )
        val second = first.copy(content = "second")
        val artifacts = GeneratedArtifactSet()
        artifacts.register(first)

        val exception = assertFailsWith<GeneratedArtifactConflictException> {
            artifacts.register(second)
        }

        assertEquals(first, exception.existing)
        assertEquals(second, exception.incoming)
    }

    @Test
    fun `批量登记冲突时不保留部分产物`() {
        val first = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "first",
            aggregationMode = ArtifactAggregationMode.AGGREGATING
        )
        val conflict = first.copy(content = "second")
        val artifacts = GeneratedArtifactSet()

        assertFailsWith<GeneratedArtifactConflictException> {
            artifacts.registerAll(listOf(first, conflict))
        }

        assertEquals(emptyList(), artifacts.snapshot())
    }

    @Test
    fun `隔离产物必须有且只有一个来源`() {
        assertFailsWith<IllegalArgumentException> {
            GeneratedArtifact.create(
                kind = ArtifactKind.JAVA_SOURCE,
                path = "example/BookDraft.java",
                content = "package example; class BookDraft {}",
                aggregationMode = ArtifactAggregationMode.ISOLATING
            )
        }
    }

    @Test
    fun `产物分别保留确定排序的来源和依赖`() {
        val dependencyId = LsiSymbolId.type("example.BaseBook")
        val source = LsiSource.of("src/main/java/example/Book.java")
        val dependencySources = linkedSetOf(
            LsiSource.of("src/main/java/example/Z.java"),
            source,
            LsiSource.of("src/main/java/example/A.java"),
        )
        val artifact = GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = "example.BookDraft",
            content = "package example; class BookDraft {}",
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(sourceId),
            originatingSources = setOf(source),
            dependencySymbols = linkedSetOf(sourceId, dependencyId),
            dependencySources = dependencySources,
        )

        assertEquals(listOf(sourceId), artifact.originatingSymbols.toList())
        assertEquals(listOf(source.path), artifact.originatingSources.map(LsiSource::path))
        assertEquals(listOf(dependencyId, sourceId), artifact.dependencySymbols.toList())
        assertEquals(
            listOf(
                "src/main/java/example/A.java",
                "src/main/java/example/Book.java",
                "src/main/java/example/Z.java",
            ),
            artifact.dependencySources.map(LsiSource::path),
        )
    }

    @Test
    fun `产物依赖必须覆盖所有来源`() {
        assertFailsWith<IllegalArgumentException> {
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = "example.BookDraft",
                content = "package example; class BookDraft {}",
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                originatingSymbols = setOf(sourceId),
                dependencySymbols = emptySet(),
            )
        }
    }
}
