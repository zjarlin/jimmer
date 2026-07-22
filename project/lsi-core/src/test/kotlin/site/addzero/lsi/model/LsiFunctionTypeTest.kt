package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import site.addzero.lsi.core.LsiSymbolId

class LsiFunctionTypeTest {

    @Test
    fun `stable signature preserves function shape and ignores annotations`() {
        val marker = LsiAnnotation(LsiSymbolId.type("sample.FunctionMarker"))
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            receiverType = LsiDeclaredType(LsiSymbolId.type("sample.Scope")),
            parameterTypes = listOf(
                LsiPrimitiveType(LsiPrimitiveKind.INT),
                LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.String"),
                    nullability = LsiNullability.NULLABLE,
                ),
            ),
            suspending = true,
            nullability = LsiNullability.NULLABLE,
            annotations = listOf(marker),
        )

        assertEquals(
            "function:suspend:receiver:type:sample.Scope!non-null:" +
                "parameters:[primitive:int!non-null,type:java.lang.String?nullable]:" +
                "return:primitive:boolean!non-null?nullable",
            functionType.stableSignature(),
        )
        assertEquals(
            functionType.copy(annotations = emptyList()).stableSignature(),
            functionType.stableSignature(),
        )
        assertEquals(
            "function:regular:parameters:[]:return:primitive:unit!non-null!non-null",
            LsiFunctionType(LsiPrimitiveType(LsiPrimitiveKind.UNIT)).stableSignature(),
        )
    }

    @Test
    fun `annotation member normalization traverses the full function type`() {
        val functionType = LsiFunctionType(
            returnType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("sample.Result"),
                nullability = LsiNullability.NULLABLE,
            ),
            receiverType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("sample.Scope"),
                nullability = LsiNullability.PLATFORM,
            ),
            parameterTypes = listOf(
                LsiArrayType(
                    elementType = LsiDeclaredType(
                        declarationId = LsiSymbolId.type("sample.Input"),
                        nullability = LsiNullability.UNKNOWN,
                    ),
                    nullability = LsiNullability.NULLABLE,
                ),
            ),
            nullability = LsiNullability.NULLABLE,
        )

        val normalized = assertIs<LsiFunctionType>(functionType.toAnnotationMemberType())

        assertEquals(LsiNullability.NON_NULL, normalized.nullability)
        assertEquals(LsiNullability.NON_NULL, normalized.returnType.nullability)
        assertEquals(LsiNullability.NON_NULL, normalized.receiverType?.nullability)
        val parameter = assertIs<LsiArrayType>(normalized.parameterTypes.single())
        assertEquals(LsiNullability.NON_NULL, parameter.nullability)
        assertEquals(LsiNullability.NON_NULL, parameter.elementType.nullability)
    }
}
