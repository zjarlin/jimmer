package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.STRING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.poet.LsiPoetTypeName

class KotlinPoetTypesTest {

    @Test
    fun `preserves escaped nested declaration segments from stable type ids`() {
        val typeId = LsiSymbolId.type("demo.ModelDraft.\$.DraftImpl")
        val type = LsiDeclaredType(
            typeId,
        )

        assertEquals(
            ClassName("demo", "ModelDraft", "\$", "DraftImpl"),
            type.toKotlinTypeName(
                listOf(LsiPoetTypeName(typeId, "demo", listOf("ModelDraft", "\$", "DraftImpl")))
            ),
        )
    }

    @Test
    fun `uses exact names for uppercase packages lowercase declarations and nested types`() {
        val topLevelId = LsiSymbolId.type("Demo.API.order")
        val nestedId = LsiSymbolId.type("Demo.API.order.item.detail")
        val typeNames = listOf(
            LsiPoetTypeName(topLevelId, "Demo.API", listOf("order")),
            LsiPoetTypeName(nestedId, "Demo.API", listOf("order", "item", "detail")),
        )

        assertEquals(
            ClassName("Demo.API", "order"),
            LsiDeclaredType(topLevelId).toKotlinTypeName(typeNames),
        )
        assertEquals(
            ClassName("Demo.API", "order", "item", "detail"),
            LsiDeclaredType(nestedId).toKotlinTypeName(typeNames),
        )
        assertFailsWith<IllegalArgumentException> {
            LsiDeclaredType(nestedId).toKotlinTypeName(emptyList())
        }
    }

    @Test
    fun `only maps Kotlin builtins when the exact source boundary matches`() {
        val mapEntryId = LsiSymbolId.type("java.util.Map.Entry")

        assertEquals(
            ClassName("kotlin.collections", "Map", "Entry"),
            LsiDeclaredType(mapEntryId).toKotlinTypeName(
                listOf(LsiPoetTypeName(mapEntryId, "java.util", listOf("Map", "Entry"))),
            ),
        )
        assertEquals(
            ClassName("java.util.Map", "Entry"),
            LsiDeclaredType(mapEntryId).toKotlinTypeName(
                listOf(LsiPoetTypeName(mapEntryId, "java.util.Map", listOf("Entry"))),
            ),
        )
    }

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

        val typeNames = listOf(
            LsiPoetTypeName(LsiSymbolId.type("sample.Scope"), "sample", listOf("Scope")),
            LsiPoetTypeName(LsiSymbolId.type("sample.FunctionMarker"), "sample", listOf("FunctionMarker")),
            LsiPoetTypeName(LsiSymbolId.type("java.lang.String"), "java.lang", listOf("String")),
        )
        val typeName = assertIs<LambdaTypeName>(functionType.toKotlinTypeName(typeNames))

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
