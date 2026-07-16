package site.addzero.lsi.core

import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.stableSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LsiSymbolIdTest {

    @Test
    fun `相同语义产生稳定标识`() {
        val ownerFromApt = LsiSymbolId.type("example.Book")
        val ownerFromKsp = LsiSymbolId.type("example.Book")
        val stringType = LsiDeclaredType(LsiSymbolId.type("kotlin.String"))
        val listType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("kotlin.collections.List"),
            arguments = listOf(LsiTypeArgument.output(stringType)),
            nullability = LsiNullability.NULLABLE
        )

        val aptFunction = LsiSymbolId.function(
            ownerFromApt,
            "find",
            listOf(listType.stableSignature())
        )
        val kspFunction = LsiSymbolId.function(
            ownerFromKsp,
            "find",
            listOf(listType.stableSignature())
        )

        assertEquals(ownerFromApt, ownerFromKsp)
        assertEquals(aptFunction, kspFunction)
        assertEquals(
            "type:example.Book/function:find(" +
                "type:kotlin.collections.List<out:type:kotlin.String!non-null>?nullable)",
            aptFunction.value
        )
    }

    @Test
    fun `重载签名产生不同标识`() {
        val owner = LsiSymbolId.type("example.Book")
        val byString = LsiSymbolId.function(owner, "find", listOf("string"))
        val byLong = LsiSymbolId.function(owner, "find", listOf("long"))

        assertNotEquals(byString, byLong)
        assertEquals(
            "type:example.Book/enum-entry:DRAFT",
            LsiSymbolId.enumEntry(owner, "DRAFT").value
        )
    }

    @Test
    fun `源码路径在前端之间统一`() {
        val aptSource = LsiSource.of("src\\main\\java\\example\\Book.java", LsiLanguage.JAVA)
        val normalizedSource = LsiSource.of("./src/main/java/example/Book.java", LsiLanguage.JAVA)

        assertEquals(normalizedSource, aptSource)
        assertEquals("src/main/java/example/Book.java", aptSource.path)
    }
}
