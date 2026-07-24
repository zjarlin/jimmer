package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetTypeName

class LsiKotlinPoetAnnotationRendererTest {

    @Test
    fun `renders one annotation and an ordered annotation list`() {
        val firstId = LsiSymbolId.type("sample.First")
        val secondId = LsiSymbolId.type("sample.Second")
        val typeNames = listOf(
            LsiPoetTypeName(firstId, "sample", listOf("First")),
            LsiPoetTypeName(secondId, "sample", listOf("Second")),
        )
        val first = LsiPoetAnnotation(
            type = firstId,
            arguments = listOf(
                LsiPoetAnnotationArgument.Positional(
                    LsiPoetAnnotationValue.StringValue("first"),
                )
            ),
            argumentLayout = LsiPoetAnnotationArgumentLayout.SINGLE_LINE,
        )
        val second = LsiPoetAnnotation(
            type = secondId,
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "count",
                    value = LsiPoetAnnotationValue.IntValue(2),
                )
            ),
        )
        val renderer = LsiKotlinPoetRenderer()

        val renderedFirst = renderer.renderAnnotation(first, typeNames)
        val renderedAll = renderer.renderAnnotations(listOf(first, second), typeNames)

        assertEquals(ClassName("sample", "First"), renderedFirst.typeName)
        assertEquals("@sample.First(\"first\")", renderedFirst.toString())
        assertEquals(
            listOf("@sample.First(\"first\")", "@sample.Second(count = 2)"),
            renderedAll.map(Any::toString),
        )
    }
}
