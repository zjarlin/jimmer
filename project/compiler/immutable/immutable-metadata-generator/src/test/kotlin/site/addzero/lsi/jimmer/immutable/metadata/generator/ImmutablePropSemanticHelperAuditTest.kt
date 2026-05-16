package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Files

class ImmutablePropSemanticHelperAuditTest {

    @Test
    fun `immutable prop reuses shared collection semantic helpers`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/jimmer/meta/ImmutableProp.kt"
            )
        )

        val requiredSnippets = listOf(
            "preferredLsiCollectionQualifiedName",
            "isLsiImmutableListQualifiedName",
            "isLsiMapQualifiedName",
        )
        for (snippet in requiredSnippets) {
            assertTrue(source.contains(snippet), "ImmutableProp must reuse shared semantic helper `$snippet`\n$source")
        }

        val forbiddenSnippets = listOf(
            "FORBIDDEN_TYPE_NAMES =",
            "LIST_TYPE_NAMES =",
            "MAP_TYPE_NAMES =",
            "BOOLEAN_TYPE_NAMES =",
        )
        for (snippet in forbiddenSnippets) {
            assertFalse(source.contains(snippet), "ImmutableProp must not keep local collection truth table `$snippet`\n$source")
        }
    }
}
