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
                "type%3Akotlin.collections.List%3Cout%3Atype%3Akotlin.String%21non-null%3E%3Fnullable)",
            aptFunction.value
        )
    }

    @Test
    fun `重载签名产生不同标识`() {
        val owner = LsiSymbolId.type("example.Book")
        val byString = LsiSymbolId.function(owner, "find", listOf("string"))
        val byLong = LsiSymbolId.function(owner, "find", listOf("long"))
        val emptyConstructor = LsiSymbolId.constructor(owner)
        val stringConstructor = LsiSymbolId.constructor(owner, listOf("string"))

        assertNotEquals(byString, byLong)
        assertNotEquals(emptyConstructor, stringConstructor)
        assertEquals("type:example.Book/field:version", LsiSymbolId.field(owner, "version").value)
        assertEquals("type:example.Book/constructor(string)", stringConstructor.value)
        assertEquals(
            "type:example.Book/enum-entry:DRAFT",
            LsiSymbolId.enumEntry(owner, "DRAFT").value
        )
    }

    @Test
    fun `反引号名称和签名分隔符被稳定转义`() {
        val owner = LsiSymbolId.type("example.Space Type")
        val oneParameter = LsiSymbolId.function(owner, "base object", listOf("a,b"))
        val twoParameters = LsiSymbolId.function(owner, "base object", listOf("a", "b"))

        assertEquals("type:example.Space%20Type", owner.value)
        assertEquals("example.Space Type", owner.requireTypeQualifiedName())
        assertEquals(
            "type:example.Space%20Type/function:base%20object(a%2Cb)",
            oneParameter.value
        )
        assertNotEquals(oneParameter, twoParameters)
    }

    @Test
    fun `类型参数名称可以无损恢复`() {
        val typeParameter = LsiSymbolId.typeParameter(
            LsiSymbolId.type("example.Container"),
            "元素 类型",
        )

        assertEquals("元素 类型", typeParameter.requireTypeParameterName())
    }

    @Test
    fun `源码路径在前端之间统一`() {
        val aptSource = LsiSource.of("src\\main\\java\\example\\Book.java", LsiLanguage.JAVA)
        val normalizedSource = LsiSource.of("./src/main/java/example/Book.java", LsiLanguage.JAVA)

        assertEquals(normalizedSource, aptSource)
        assertEquals("src/main/java/example/Book.java", aptSource.path)
    }
}
