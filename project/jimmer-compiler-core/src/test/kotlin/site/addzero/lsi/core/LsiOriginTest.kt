package site.addzero.lsi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LsiOriginTest {

    @Test
    fun `derives source projection language`() {
        val source = LsiSource.of("demo/Model.kt", LsiLanguage.KOTLIN)

        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)

        assertEquals(LsiLanguage.KOTLIN, origin.language)
    }

    @Test
    fun `retains binary frontend projection language without source`() {
        val javaOrigin = LsiOrigin(
            kind = LsiOriginKind.BINARY,
            language = LsiLanguage.JAVA,
        )
        val kotlinOrigin = LsiOrigin(
            kind = LsiOriginKind.BINARY,
            language = LsiLanguage.KOTLIN,
        )

        assertEquals(LsiLanguage.JAVA, javaOrigin.language)
        assertEquals(LsiLanguage.KOTLIN, kotlinOrigin.language)
    }

    @Test
    fun `rejects language conflicting with source projection`() {
        val source = LsiSource.of("demo/Model.java", LsiLanguage.JAVA)

        assertFailsWith<IllegalArgumentException> {
            LsiOrigin(
                kind = LsiOriginKind.SOURCE,
                source = source,
                language = LsiLanguage.KOTLIN,
            )
        }
    }
}
