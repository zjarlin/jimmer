package org.babyfish.jimmer.compiler.render.ksp

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class LsiKotlinPoetTest {

    @Test
    fun `renders boxed scalar and void types without primitive collapse`() {
        val boxedInt = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true)

        assertEquals("java.lang.Integer", boxedInt.toKotlinTypeName().toString())
        assertEquals(
            "kotlin.Int?",
            boxedInt.copy(nullability = LsiNullability.NULLABLE).toKotlinTypeName().toString(),
        )
        assertEquals(
            "java.lang.Void",
            LsiPrimitiveType(LsiPrimitiveKind.VOID, boxed = true).toKotlinTypeName().toString(),
        )
        assertEquals(
            "kotlin.Unit",
            LsiPrimitiveType(LsiPrimitiveKind.VOID).toKotlinTypeName().toString(),
        )
    }

    @Test
    fun `renders primitive class values with their frozen boxing`() {
        val annotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.Marker"),
            arguments = mapOf(
                "raw" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiPrimitiveType(LsiPrimitiveKind.INT)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
                "boxed" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )

        val rendered = annotation.toKotlinAnnotationSpec().toString()
        assertContains(rendered, "raw = kotlin.Int::class")
        assertContains(rendered, "boxed = java.lang.Integer::class")
    }

    @Test
    fun `rejects primitive void class values that kotlin annotations cannot express`() {
        val annotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.Marker"),
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiPrimitiveType(LsiPrimitiveKind.VOID)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            annotation.toKotlinAnnotationSpec()
        }
        assertContains(exception.message.orEmpty(), "primitive void class literal")
    }

    @Test
    fun `renders java string as kotlin string`() {
        assertEquals(
            "kotlin.String",
            LsiDeclaredType(site.addzero.lsi.core.LsiSymbolId.type("java.lang.String"))
                .toKotlinTypeName()
                .toString(),
        )
    }

    @Test
    fun `renders primitive and boxed arrays without collapsing their representation`() {
        val rawInt = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedInt = rawInt.copy(boxed = true)
        val nullableBoxedInt = boxedInt.copy(nullability = LsiNullability.NULLABLE)

        assertEquals("kotlin.IntArray", LsiArrayType(rawInt).toKotlinTypeName().toString())
        assertEquals(
            "kotlin.IntArray?",
            LsiArrayType(rawInt, nullability = LsiNullability.NULLABLE).toKotlinTypeName().toString(),
        )
        assertEquals(
            "kotlin.Array<kotlin.Int>",
            LsiArrayType(boxedInt).toKotlinTypeName().toString(),
        )
        assertEquals(
            "kotlin.Array<kotlin.Int?>",
            LsiArrayType(nullableBoxedInt).toKotlinTypeName().toString(),
        )
    }
}
