package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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

    @Test
    fun `稳定签名与参数映射插入顺序无关`() {
        val type = LsiSymbolId.type("example.Constraint")
        val first = LsiAnnotation(
            type = type,
            arguments = linkedMapOf(
                "message" to LsiAnnotationArgument(
                    LsiAnnotationValue.StringValue("a:b,c"),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
                "groups" to LsiAnnotationArgument(
                    LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.ClassValue(
                                LsiDeclaredType(LsiSymbolId.type("example.Group"))
                            )
                        )
                    ),
                    LsiAnnotationArgumentOrigin.DEFAULT,
                ),
            ),
            useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
        )
        val reordered = first.copy(arguments = first.arguments.entries.reversed().associate { it.toPair() })

        assertEquals(first.stableSignature(), reordered.stableSignature())
        assertNotEquals(
            first.stableSignature(),
            first.copy(useSiteTarget = LsiAnnotationUseSiteTarget.FIELD).stableSignature(),
        )
    }
}
