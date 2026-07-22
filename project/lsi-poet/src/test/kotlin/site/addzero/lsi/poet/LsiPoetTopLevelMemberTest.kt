package site.addzero.lsi.poet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LsiPoetTopLevelMemberTest {

    @Test
    fun `models a package member with explicit extension semantics`() {
        val block = LsiPoetCodeBlock.build {
            topLevelMember(
                packageName = "org.babyfish.jimmer.kt",
                simpleName = "by",
                extension = true,
            )
        }

        assertEquals(
            LsiPoetCodePart.TopLevelMember(
                packageName = "org.babyfish.jimmer.kt",
                simpleName = "by",
                extension = true,
            ),
            block.parts.single(),
        )
    }

    @Test
    fun `rejects malformed package and member names`() {
        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodePart.TopLevelMember("demo..extensions", "render", false)
        }
        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodePart.TopLevelMember("demo.extensions", "bad-name", false)
        }
    }

    @Test
    fun `exposes exactly one top-level member builder operation`() {
        val operations = LsiPoetCodeBuilder::class.java.declaredMethods
            .filter { method -> method.name == "topLevelMember" }

        assertEquals(1, operations.size)
        assertEquals(3, operations.single().parameterCount)
        assertTrue(operations.single().parameterTypes.last() == Boolean::class.javaPrimitiveType)
    }
}
