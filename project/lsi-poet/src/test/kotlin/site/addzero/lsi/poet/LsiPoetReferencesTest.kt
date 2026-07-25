package site.addzero.lsi.poet

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef

class LsiPoetReferencesTest {

    @Test
    fun `collects standalone code block type references recursively`() {
        val conditionTypeId = LsiSymbolId.type("demo.Condition")
        val resultTypeId = LsiSymbolId.type("demo.Result")
        val codeBlock = LsiPoetCodeBlock.build {
            beginControlFlow {
                type(LsiDeclaredType(conditionTypeId))
            }
            returnBracedExpression(
                prefix = { text("result") },
                body = { type(LsiDeclaredType(resultTypeId)) },
            )
            endControlFlow()
        }

        val expected = sortedSetOf(conditionTypeId, resultTypeId)
        assertEquals(expected, codeBlock.referencedSymbolIds())
        assertEquals(expected, codeBlock.referencedTypeIds)
    }

    @Test
    fun `collects declarations annotations and code types recursively`() {
        val ownerId = LsiSymbolId.type("demo.Generated")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val nestedAnnotation = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.Nested"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "kind",
                    value = LsiPoetAnnotationValue.EnumValue(
                        enumType = LsiSymbolId.type("demo.Kind"),
                        entryName = "ONE",
                    ),
                )
            ),
        )
        val typeAnnotation = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.TypeMarker"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "nested",
                    value = LsiPoetAnnotationValue.NestedAnnotationValue(nestedAnnotation),
                )
            ),
        )
        val generatedType = LsiPoetType(
            name = "Generated",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(typeAnnotation),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = parameterId,
                    name = "T",
                    upperBounds = listOf(LsiDeclaredType(LsiSymbolId.type("demo.Bound"))),
                )
            ),
            superInterfaces = listOf(LsiDeclaredType(LsiSymbolId.type("demo.Contract"))),
            members = listOf(
                LsiPoetProperty(
                    name = "value",
                    type = LsiTypeParameterRef(parameterId),
                    mutable = true,
                    setter = LsiPoetAccessor(
                        parameterAnnotations = listOf(
                            LsiPoetAnnotation(LsiSymbolId.type("demo.ParameterMarker"))
                        ),
                        body = LsiPoetCodeBlock.build {
                            statement { type(LsiDeclaredType(LsiSymbolId.type("demo.SetterRuntime"))) }
                        },
                    ),
                ),
                LsiPoetFunction(
                    name = "render",
                    receiverType = LsiDeclaredType(LsiSymbolId.type("demo.Receiver")),
                    returnType = LsiDeclaredType(LsiSymbolId.type("demo.Result")),
                    body = LsiPoetCodeBlock.build {
                        returnBracedExpression(
                            prefix = { type(LsiDeclaredType(LsiSymbolId.type("demo.Factory"))) },
                            body = {
                                statement {
                                    type(LsiDeclaredType(LsiSymbolId.type("demo.Runtime")))
                                }
                            },
                        )
                    },
                ),
            ),
        )
        val file = LsiPoetFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo",
            fileName = "Generated",
            annotations = listOf(LsiPoetAnnotation(LsiSymbolId.type("demo.FileMarker"))),
            members = listOf(generatedType),
        )

        val memberSymbolIds = sortedSetOf(
            parameterId,
            LsiSymbolId.type("demo.TypeMarker"),
            LsiSymbolId.type("demo.Nested"),
            LsiSymbolId.type("demo.Kind"),
            LsiSymbolId.type("demo.Bound"),
            LsiSymbolId.type("demo.Contract"),
            LsiSymbolId.type("demo.ParameterMarker"),
            LsiSymbolId.type("demo.SetterRuntime"),
            LsiSymbolId.type("demo.Receiver"),
            LsiSymbolId.type("demo.Result"),
            LsiSymbolId.type("demo.Factory"),
            LsiSymbolId.type("demo.Runtime"),
        )
        assertEquals(
            sortedSetOf(
                LsiSymbolId.type("demo.TypeMarker"),
                LsiSymbolId.type("demo.Nested"),
                LsiSymbolId.type("demo.Kind"),
            ),
            typeAnnotation.referencedSymbolIds(),
        )
        assertEquals(typeAnnotation.referencedSymbolIds(), typeAnnotation.referencedTypeIds)
        assertEquals(memberSymbolIds, generatedType.referencedSymbolIds())
        assertEquals(
            memberSymbolIds.filterTo(sortedSetOf(), LsiSymbolId::isTypeId),
            generatedType.referencedTypeIds,
        )

        assertEquals(
            sortedSetOf(
                parameterId,
                LsiSymbolId.type("demo.FileMarker"),
                LsiSymbolId.type("demo.TypeMarker"),
                LsiSymbolId.type("demo.Nested"),
                LsiSymbolId.type("demo.Kind"),
                LsiSymbolId.type("demo.Bound"),
                LsiSymbolId.type("demo.Contract"),
                LsiSymbolId.type("demo.ParameterMarker"),
                LsiSymbolId.type("demo.SetterRuntime"),
                LsiSymbolId.type("demo.Receiver"),
                LsiSymbolId.type("demo.Result"),
                LsiSymbolId.type("demo.Factory"),
                LsiSymbolId.type("demo.Runtime"),
            ),
            file.referencedSymbolIds(),
        )
        assertEquals(
            file.referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId),
            file.referencedTypeIds,
        )
    }
}
