package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.STRING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class KotlinPoetTypesTest {

    @Test
    fun `converts the complete LSI function type to a Kotlin lambda type`() {
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            receiverType = LsiDeclaredType(LsiSymbolId.type("sample.Scope")),
            parameterTypes = listOf(
                LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                LsiPrimitiveType(
                    kind = LsiPrimitiveKind.INT,
                    nullability = LsiNullability.NULLABLE,
                ),
            ),
            suspending = true,
            nullability = LsiNullability.NULLABLE,
            annotations = listOf(LsiAnnotation(LsiSymbolId.type("sample.FunctionMarker"))),
        )

        val typeName = assertIs<LambdaTypeName>(functionType.toKotlinTypeName())

        assertEquals(ClassName("sample", "Scope"), typeName.receiver)
        assertEquals(listOf(STRING, INT.copy(nullable = true)), typeName.parameters.map { it.type })
        assertEquals(BOOLEAN, typeName.returnType)
        assertTrue(typeName.isSuspending)
        assertTrue(typeName.isNullable)
        assertEquals(
            ClassName("sample", "FunctionMarker"),
            typeName.annotations.single().typeName,
        )
    }
}
