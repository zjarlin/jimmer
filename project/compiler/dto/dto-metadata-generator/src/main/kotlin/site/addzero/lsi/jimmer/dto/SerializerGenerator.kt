package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

internal class SerializerGenerator(
    private val parentGenerator: DtoGenerator
) {
    private val dtoType: LsiDtoType =
        parentGenerator.dtoType

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = "Serializer",
            kind = LsiTypeSpecKind.CLASS,
            superClass = LsiParameterizedTypeName(
                rawType = parentGenerator.ctx.jacksonTypes.jsonSerializer,
                typeArguments = listOf(parentGenerator.getDtoLsiClassName()),
            ),
            callables = listOf(newSerialize()),
        )

    private fun newSerialize(): LsiCallableSpec {
        val serializeFieldMethodName = if (parentGenerator.ctx.jackson3) "defaultSerializeProperty" else "defaultSerializeField"
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "serialize",
            modifiers = setOf(LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec("input", parentGenerator.getDtoLsiClassName()),
                LsiParameterSpec("gen", parentGenerator.ctx.jacksonTypes.jsonGenerator),
                LsiParameterSpec("provider", parentGenerator.ctx.jacksonTypes.serializeProvider),
            ),
            statements = serializeStatements(serializeFieldMethodName),
        )
    }

    private fun serializeStatements(serializeFieldMethodName: String): List<LsiStatement> =
        buildList {
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("gen"),
                        name = "writeStartObject",
                    )
                )
            )
            for (prop in dtoType.dtoPropViews) {
                val serializeStatement = LsiExpressionStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("provider"),
                        name = serializeFieldMethodName,
                        arguments = listOf(
                            LsiLiteralExpression(prop.name),
                            inputPropertyExpression(prop),
                            LsiNameExpression("gen"),
                        ),
                    )
                )
                if (prop.inputModifier == DtoModifier.DYNAMIC) {
                    add(
                        LsiIfStatement(
                            condition = LsiPropertyGetExpression(
                                receiver = LsiNameExpression("input"),
                                name = StringUtil.identifier("is", prop.name, "Loaded"),
                                type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                            ),
                            thenStatements = listOf(serializeStatement),
                        )
                    )
                } else {
                    add(serializeStatement)
                }
            }
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("gen"),
                        name = "writeEndObject",
                    )
                )
            )
        }

    private fun inputPropertyExpression(prop: LsiDtoPropView) =
        LsiPropertyGetExpression(
            receiver = LsiNameExpression("input"),
            name = prop.name,
            type = parentGenerator.propLsiTypeName(prop),
        )
}
