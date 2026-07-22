package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId

class LsiJvmTypeSignatureTest {

    @Test
    fun `renders primitive wrappers arrays generics aliases and type parameters`() {
        val rawInt = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedInt = rawInt.copy(boxed = true)
        val mapTypeId = LsiSymbolId.type("kotlin.collections.MutableMap")
        val parameterId = LsiSymbolId.typeParameter(LsiSymbolId.type("demo.Box"), "T")
        val genericType = LsiDeclaredType(
            declarationId = mapTypeId,
            arguments = listOf(
                LsiTypeArgument.output(rawInt),
                LsiTypeArgument.input(LsiTypeParameterRef(parameterId)),
            ),
        )
        val context = LsiJvmTypeSignatureContext(
            canonicalDeclaredTypeIds = mapOf(mapTypeId to LsiSymbolId.type("java.util.Map")),
            typeParameters = mapOf(
                parameterId to LsiJvmTypeParameterDescriptor(
                    id = parameterId,
                    owner = LsiJvmTypeParameterOwner.Type(LsiSymbolId.type("demo.Box")),
                    index = 0,
                )
            ),
        )

        assertEquals("primitive:int", rawInt.toJvmTypeSignature())
        assertEquals("type:java.lang.Integer", boxedInt.toJvmTypeSignature())
        assertEquals("array:primitive:int", LsiArrayType(rawInt).toJvmTypeSignature())
        assertEquals("array:type:java.lang.Integer", LsiArrayType(boxedInt).toJvmTypeSignature())
        assertEquals(
            "type:java.util.Map<out:type:java.lang.Integer,in:parameter:type:demo.Box:0>",
            genericType.toJvmTypeSignature(context = context),
        )
        assertEquals(
            "type:java.util.Map",
            genericType.toJvmTypeSignature(eraseTypeArguments = true, context = context),
        )
    }

    @Test
    fun `normalizes unit and void in jvm reference positions`() {
        val unit = LsiPrimitiveType(LsiPrimitiveKind.UNIT)
        val void = LsiPrimitiveType(LsiPrimitiveKind.VOID)
        val listId = LsiSymbolId.type("java.util.List")

        assertEquals("type:kotlin.Unit", unit.toJvmCallableParameterType().toJvmTypeSignature())
        assertEquals("type:java.lang.Void", void.toJvmCallableParameterType().toJvmTypeSignature())
        assertEquals("array:type:kotlin.Unit", LsiArrayType(unit).toJvmTypeSignature())
        assertEquals("array:type:java.lang.Void", LsiArrayType(void).toJvmTypeSignature())
        assertEquals(
            "type:java.util.List<type:kotlin.Unit>",
            LsiDeclaredType(listId, listOf(LsiTypeArgument.invariant(unit))).toJvmTypeSignature(),
        )
        assertEquals(
            "type:java.util.List<type:java.lang.Void>",
            LsiDeclaredType(listId, listOf(LsiTypeArgument.invariant(void))).toJvmTypeSignature(),
        )
    }

    @Test
    fun `renders method type parameter erasure from structured descriptors`() {
        val ownerId = LsiSymbolId.function(LsiSymbolId.type("demo.Factory"), "convert")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val context = LsiJvmTypeSignatureContext(
            typeParameters = mapOf(
                parameterId to LsiJvmTypeParameterDescriptor(
                    id = parameterId,
                    owner = LsiJvmTypeParameterOwner.Method("convert"),
                    index = 0,
                    upperBounds = listOf(LsiPrimitiveType(LsiPrimitiveKind.INT)),
                )
            ),
        )

        assertEquals(
            "parameter:method:convert:0:type:java.lang.Integer",
            LsiTypeParameterRef(parameterId).toJvmTypeSignature(context = context),
        )
        assertEquals(
            "type:java.lang.Integer",
            LsiTypeParameterRef(parameterId).toJvmTypeSignature(
                eraseTypeArguments = true,
                context = context,
            ),
        )
    }

    @Test
    fun `fails for missing and recursive type parameter descriptors`() {
        val ownerId = LsiSymbolId.type("demo.Box")
        val firstId = LsiSymbolId.typeParameter(ownerId, "T")
        val secondId = LsiSymbolId.typeParameter(ownerId, "U")

        val missing = assertFailsWith<IllegalArgumentException> {
            LsiTypeParameterRef(firstId).toJvmTypeSignature()
        }
        assertTrue(requireNotNull(missing.message).contains(firstId.value))

        val recursiveContext = LsiJvmTypeSignatureContext(
            typeParameters = mapOf(
                firstId to LsiJvmTypeParameterDescriptor(
                    id = firstId,
                    owner = LsiJvmTypeParameterOwner.Type(ownerId),
                    index = 0,
                    upperBounds = listOf(LsiTypeParameterRef(secondId)),
                ),
                secondId to LsiJvmTypeParameterDescriptor(
                    id = secondId,
                    owner = LsiJvmTypeParameterOwner.Type(ownerId),
                    index = 1,
                    upperBounds = listOf(LsiTypeParameterRef(firstId)),
                ),
            ),
        )
        val recursive = assertFailsWith<IllegalStateException> {
            LsiTypeParameterRef(firstId).toJvmTypeSignature(
                eraseTypeArguments = true,
                context = recursiveContext,
            )
        }
        assertTrue(requireNotNull(recursive.message).contains("Recursive JVM type parameter erasure"))
    }

    @Test
    fun `rejects function types instead of guessing a JVM ABI`() {
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            parameterTypes = listOf(LsiPrimitiveType(LsiPrimitiveKind.INT)),
            suspending = true,
        )
        val containingTypes = listOf<LsiTypeRef>(
            functionType,
            LsiArrayType(functionType),
            LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(functionType)),
            ),
        )

        containingTypes.forEach { type ->
            val exception = assertFailsWith<IllegalArgumentException> {
                type.toJvmTypeSignature()
            }
            assertTrue(requireNotNull(exception.message).contains("cannot infer the ABI"))
        }
    }
}
