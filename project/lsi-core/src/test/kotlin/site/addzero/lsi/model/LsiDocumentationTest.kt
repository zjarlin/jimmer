package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LsiDocumentationTest {

    @Test
    fun `空文档不产生结构化模型`() {
        assertNull(null.parseLsiDocumentation())
        assertNull("".parseLsiDocumentation())
        assertNull(" \r\n\t ".parseLsiDocumentation())
    }

    @Test
    fun `解析正文参数属性返回值与多行内容`() {
        val documentation = """
            类型正文第一行
            类型正文第二行
            @param id 标识第一行
             标识第二行
            @property name 名称第一行
             名称第二行
            @return 返回第一行
             返回第二行
        """.trimIndent().parseLsiDocumentation()

        assertEquals("类型正文第一行\n类型正文第二行", documentation?.value)
        assertEquals(mapOf("id" to "标识第一行\n标识第二行"), documentation?.parameterValues)
        assertEquals(mapOf("name" to "名称第一行\n名称第二行"), documentation?.propertyValues)
        assertEquals("返回第一行\n返回第二行", documentation?.returnValue)
    }

    @Test
    fun `忽略未知标签及其后续文本`() {
        val documentation = """
            可见正文
            @throws IllegalStateException 不应保留
             不应保留的多行说明
        """.trimIndent().parseLsiDocumentation()

        assertEquals("可见正文\n", documentation?.canonicalText())
    }

    @Test
    fun `保留标签声明顺序并稳定输出规范文本`() {
        val documentation = LsiDocumentation(
            value = "正文",
            parameterValues = linkedMapOf(
                "second" to "第二个参数",
                "first" to "第一个参数",
            ),
            returnValue = "返回值",
            propertyValues = linkedMapOf(
                "beta" to "第二个属性",
                "alpha" to "第一个属性",
            ),
        )
        val expected = """
            正文
            @param second 第二个参数
            @param first 第一个参数
            @property beta 第二个属性
            @property alpha 第一个属性
            @return 返回值

        """.trimIndent()

        assertEquals(expected, documentation.canonicalText())
        assertEquals(expected, documentation.toString())
        assertEquals(documentation, documentation.canonicalText().parseLsiDocumentation())
    }

    @Test
    fun `支持空标签值和重复标签覆盖`() {
        val documentation = """
            @param second 最初值
            @param first
            @param second 最终值
            @property name
            @return
        """.trimIndent().parseLsiDocumentation()

        assertEquals(
            linkedMapOf(
                "second" to "最终值",
                "first" to "",
            ),
            documentation?.parameterValues,
        )
        assertEquals(mapOf("name" to ""), documentation?.propertyValues)
        assertNull(documentation?.returnValue)
        assertEquals(
            "@param second 最终值\n@param first \n@property name \n",
            documentation?.canonicalText(),
        )
    }

    @Test
    fun `缺少参数名的标签结束当前文档段落`() {
        val documentation = """
            @param A First
            @param
            @param B
            @param
        """.trimIndent().parseLsiDocumentation()

        assertEquals(
            mapOf(
                "A" to "First",
                "B" to "",
            ),
            documentation?.parameterValues,
        )
    }

    @Test
    fun `统一换行与首尾空白`() {
        val documentation = " \r\n正文\r\n@param id 标识\r\n "
            .parseLsiDocumentation()

        assertEquals("正文\n@param id 标识\n", documentation?.canonicalText())
    }

    @Test
    fun `构造时冻结标签顺序与内容`() {
        val parameters = linkedMapOf("second" to "第二个", "first" to "第一个")
        val properties = linkedMapOf("name" to "名称")
        val documentation = LsiDocumentation(
            value = "正文",
            parameterValues = parameters,
            returnValue = null,
            propertyValues = properties,
        )

        parameters["second"] = "已修改"
        parameters["third"] = "第三个"
        properties.clear()

        assertEquals(
            "正文\n@param second 第二个\n@param first 第一个\n@property name 名称\n",
            documentation.canonicalText(),
        )
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (documentation.parameterValues as MutableMap<String, String>)["fourth"] = "第四个"
        }
    }

    @Test
    fun `标签顺序参与相等性与哈希计算`() {
        val first = LsiDocumentation(
            value = null,
            parameterValues = linkedMapOf("first" to "一", "second" to "二"),
            returnValue = null,
            propertyValues = emptyMap(),
        )
        val second = LsiDocumentation(
            value = null,
            parameterValues = linkedMapOf("second" to "二", "first" to "一"),
            returnValue = null,
            propertyValues = emptyMap(),
        )

        assertNotEquals(first, second)
        assertNotEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first.canonicalText(), second.canonicalText())
    }
}
