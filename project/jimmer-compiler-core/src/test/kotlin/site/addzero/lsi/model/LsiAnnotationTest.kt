package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LsiAnnotationTest {

    @Test
    fun `注解值保留类型和参数来源`() {
        val schemaType = LsiSymbolId.type("io.swagger.Schema")
        val nested = LsiAnnotation(
            type = LsiSymbolId.type("example.Constraint"),
            arguments = mapOf(
                "message" to LsiAnnotationArgument(
                    LsiAnnotationValue.StringValue("required"),
                    LsiAnnotationArgumentOrigin.EXPLICIT
                )
            )
        )
        val annotation = LsiAnnotation(
            type = schemaType,
            arguments = mapOf(
                "mode" to LsiAnnotationArgument(
                    LsiAnnotationValue.EnumValue(
                        enumType = LsiSymbolId.type("io.swagger.RequiredMode"),
                        entryName = "REQUIRED"
                    ),
                    LsiAnnotationArgumentOrigin.EXPLICIT
                ),
                "implementation" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(
                        LsiDeclaredType(LsiSymbolId.type("example.Book"))
                    ),
                    LsiAnnotationArgumentOrigin.DEFAULT
                ),
                "constraints" to LsiAnnotationArgument(
                    LsiAnnotationValue.ArrayValue(
                        listOf(LsiAnnotationValue.NestedAnnotationValue(nested))
                    ),
                    LsiAnnotationArgumentOrigin.EXPLICIT
                )
            ),
            useSiteTarget = LsiAnnotationUseSiteTarget.GETTER
        )

        assertTrue(requireNotNull(annotation["mode"]).isExplicit)
        assertFalse(requireNotNull(annotation["implementation"]).isExplicit)
        assertIs<LsiAnnotationValue.EnumValue>(requireNotNull(annotation["mode"]).value)
        val array = assertIs<LsiAnnotationValue.ArrayValue>(requireNotNull(annotation["constraints"]).value)
        val nestedValue = assertIs<LsiAnnotationValue.NestedAnnotationValue>(array.elements.single())
        assertEquals(nested, nestedValue.annotation)
        assertEquals(LsiAnnotationUseSiteTarget.GETTER, annotation.useSiteTarget)
    }
}
