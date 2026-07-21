package org.babyfish.jimmer.compiler.render.apt

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class LsiJavaPoetTest {

    @Test
    fun `renders primitive representation without losing boxing`() {
        val rawType = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedType = rawType.copy(boxed = true)

        assertEquals("int", rawType.toJavaTypeName().toString())
        assertEquals("java.lang.Integer", boxedType.toJavaTypeName().toString())
        assertEquals(
            "kotlin.Unit",
            LsiPrimitiveType(LsiPrimitiveKind.UNIT, boxed = true).toJavaTypeName().toString(),
        )
        assertEquals(
            "java.lang.Void",
            LsiPrimitiveType(LsiPrimitiveKind.VOID, boxed = true).toJavaTypeName().toString(),
        )
    }

    @Test
    fun `renders unit class values as kotlin unit instead of void`() {
        val annotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.Marker"),
            arguments = mapOf(
                "unit" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiPrimitiveType(LsiPrimitiveKind.UNIT)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
                "rawVoid" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiPrimitiveType(LsiPrimitiveKind.VOID)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )

        val rendered = annotation.toJavaAnnotationSpec().toString()
        assertContains(rendered, "unit = kotlin.Unit.class")
        assertContains(rendered, "rawVoid = void.class")
    }
}
