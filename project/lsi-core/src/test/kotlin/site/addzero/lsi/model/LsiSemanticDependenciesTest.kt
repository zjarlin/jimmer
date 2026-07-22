package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId

class LsiSemanticDependenciesTest {

    @Test
    fun `collects nested type and annotation dependencies`() {
        val parameterId = LsiSymbolId.typeParameter(LsiSymbolId.type("demo.Owner"), "T")
        val annotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.Marker"),
            arguments = mapOf(
                "nested" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.NestedAnnotationValue(
                        LsiAnnotation(
                            type = LsiSymbolId.type("demo.Nested"),
                            arguments = mapOf(
                                "type" to LsiAnnotationArgument(
                                    value = LsiAnnotationValue.ClassValue(
                                        LsiDeclaredType(LsiSymbolId.type("demo.Payload"))
                                    ),
                                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                                )
                            ),
                        )
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
        val type = LsiFunctionType(
            receiverType = LsiTypeParameterRef(parameterId),
            parameterTypes = listOf(
                LsiArrayType(
                    LsiDeclaredType(
                        declarationId = LsiSymbolId.type("demo.Container"),
                        arguments = listOf(
                            LsiTypeArgument.invariant(
                                LsiDeclaredType(LsiSymbolId.type("demo.Element"))
                            )
                        ),
                    )
                )
            ),
            returnType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("demo.Result"),
                annotations = listOf(annotation),
            ),
        )

        val dependencies = sortedSetOf<LsiSymbolId>().apply { collectTypeRefDependencies(type) }

        assertEquals(
            sortedSetOf(
                parameterId,
                LsiSymbolId.type("demo.Container"),
                LsiSymbolId.type("demo.Element"),
                LsiSymbolId.type("demo.Result"),
                LsiSymbolId.type("demo.Marker"),
                LsiSymbolId.type("demo.Nested"),
                LsiSymbolId.type("demo.Payload"),
            ),
            dependencies,
        )
    }
}
