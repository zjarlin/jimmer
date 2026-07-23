package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.client.meta.Doc
import site.addzero.lsi.model.parseLsiDocumentation

class LsiDocumentationParityTest {

    @Test
    fun `LSI 文档解析与现有 Jimmer 文档语义一致`() {
        val fixtures = listOf(
            null,
            "",
            "  \r\n\t ",
            "正文",
            """
                正文第一行
                 正文第二行
                @param id 参数第一行
                 参数第二行
                @property name 属性第一行
                 属性第二行
                @return 返回第一行
                 返回第二行
            """.trimIndent(),
            """
                可见正文
                @throws IllegalStateException 忽略内容
                 忽略的后续内容
                @param id
            """.trimIndent(),
            """
                @param A First
                @param
                @param B
                @param
            """.trimIndent(),
            """
                @param second 最初值
                @param first
                @param second 最终值
                @property name
                @return
            """.trimIndent(),
            "@returning 仍按现有规则识别为返回值",
            "正文\r\n@param id 标识\r\n@property name 名称\r\n",
        )

        fixtures.forEach { source ->
            val expected = Doc.parse(source)
            val actual = source.parseLsiDocumentation()

            assertEquals(expected?.value, actual?.value, source)
            assertEquals(expected?.parameterValueMap, actual?.parameterValues, source)
            assertEquals(expected?.propertyValueMap, actual?.propertyValues, source)
            assertEquals(expected?.returnValue, actual?.returnValue, source)
            assertEquals(expected?.toString(), actual?.canonicalText(), source)
        }
    }
}
