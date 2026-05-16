package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import kotlin.io.path.name
import kotlin.io.path.readText

class ImmutableSharedConstantReuseAuditTest {

    @Test
    fun `immutable generators reuse shared constant carriers for common runtime types`() {
        val forbiddenSnippets = listOf(
            "LsiClassName.bestGuess(\"kotlin.Any\")",
            "LsiClassName.bestGuess(\"kotlin.Boolean\")",
            "LsiClassName.bestGuess(\"kotlin.Int\")",
            "LsiClassName.bestGuess(\"kotlin.String\")",
            "LsiClassName.bestGuess(\"java.util.ArrayList\")",
            "LsiClassName.bestGuess(\"java.lang.IllegalArgumentException\")",
            "LsiClassName.bestGuess(\"java.lang.IllegalStateException\")",
            "LsiClassName.bestGuess(\"org.babyfish.jimmer.jackson.ImmutableModuleRequiredException\")",
            "LsiClassName.bestGuess(\"org.babyfish.jimmer.UnloadedException\")",
            "LsiClassName.bestGuess(\"java.lang.System\")",
            "LsiClassName.bestGuess(\"java.io.Serializable\")",
            "LsiClassName.bestGuess(\"kotlin.Cloneable\")",
        )

        for (file in ImmutableTestSupport.generatorFiles()) {
            val source = file.readText()
            for (snippet in forbiddenSnippets) {
                assertFalse(
                    source.contains(snippet),
                    "immutable generator source must reuse shared LSI constants instead of local duplicated carriers: ${file.name} contains `$snippet`\n$source",
                )
            }
        }
    }
}
