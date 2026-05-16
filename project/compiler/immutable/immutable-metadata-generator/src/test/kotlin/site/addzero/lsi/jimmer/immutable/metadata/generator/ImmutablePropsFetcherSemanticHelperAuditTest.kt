package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Files

class ImmutablePropsFetcherSemanticHelperAuditTest {

    @Test
    fun `props fetcher metadata projector reuses shared subtype helpers`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/immutable/immutable-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/extractor/ImmutablePropsFetcherMetadataProjector.kt"
            )
        )

        val requiredSnippets = listOf(
            "isSubtypeOfNumberLike",
            "isSubtypeOfJavaUtilDateLike",
            "isSubtypeOfTemporalLike",
            "isSubtypeOfComparableLike",
        )
        for (snippet in requiredSnippets) {
            assertTrue(source.contains(snippet), "ImmutablePropsFetcherMetadataProjector 必须复用共享 subtype helper `$snippet`\n$source")
        }

        val forbiddenSnippets = listOf(
            "NUMBER_TYPE_QUALIFIED_NAMES",
            "DATE_TYPE_QUALIFIED_NAMES",
            "TEMPORAL_TYPE_QUALIFIED_NAMES",
            "COMPARABLE_TYPE_QUALIFIED_NAMES",
            "private fun LsiType.isSubtypeOfAny(",
        )
        for (snippet in forbiddenSnippets) {
            assertFalse(source.contains(snippet), "ImmutablePropsFetcherMetadataProjector 不得保留本地 subtype truth table `$snippet`\n$source")
        }
    }
}
