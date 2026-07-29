package site.addzero.lsi.poet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LsiPoetCodeFragmentTest {

    @Test
    fun `models embeddable code and explicit imports together`() {
        val codeBlock = LsiPoetCodeBlock.build {
            text("source.by()")
        }
        val sourceImport = LsiPoetImport("demo.model", "by")

        val fragment = LsiPoetCodeFragment(
            codeBlock = codeBlock,
            imports = listOf(sourceImport),
        )

        assertEquals(codeBlock, fragment.codeBlock)
        assertEquals(listOf(sourceImport), fragment.imports)
    }

    @Test
    fun `rejects duplicate explicit imports`() {
        val sourceImport = LsiPoetImport("demo.model", "by")

        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodeFragment(
                codeBlock = LsiPoetCodeBlock.EMPTY,
                imports = listOf(sourceImport, sourceImport),
            )
        }
    }
}
