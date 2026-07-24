package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.poet.LsiPoetTypeName

class JavaPoetTypesTest {

    @Test
    fun `rejects function types without guessing a JVM representation`() {
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            parameterTypes = listOf(LsiPrimitiveType(LsiPrimitiveKind.INT)),
            suspending = true,
        )

        val exception = assertFailsWith<IllegalStateException> {
            functionType.toJavaTypeName(emptyList())
        }

        assertTrue(requireNotNull(exception.message).contains("without an explicit JVM ABI"))
    }

    @Test
    fun `renders core annotation values from exact source names`() {
        val annotationId = LsiSymbolId.type("UPPER.meta.marker")
        val enumId = LsiSymbolId.type("UPPER.values.outer.mode")
        val targetId = LsiSymbolId.type("UPPER.pkg.lowercase")
        val annotation = LsiAnnotation(
            type = annotationId,
            arguments = mapOf(
                "kind" to LsiAnnotationArgument(
                    LsiAnnotationValue.EnumValue(enumId, "ON"),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
                "target" to LsiAnnotationArgument(
                    LsiAnnotationValue.ClassValue(LsiDeclaredType(targetId)),
                    LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )
        val typeNames = listOf(
            LsiPoetTypeName(annotationId, "UPPER.meta", listOf("marker")),
            LsiPoetTypeName(enumId, "UPPER.values", listOf("outer", "mode")),
            LsiPoetTypeName(targetId, "UPPER.pkg", listOf("lowercase")),
        )

        val rendered = annotation.toJavaCoreAnnotationSpec(typeNames).toString()

        assertEquals(
            "@UPPER.meta.marker(kind = UPPER.values.outer.mode.ON, target = UPPER.pkg.lowercase.class)",
            rendered,
        )
    }
}
