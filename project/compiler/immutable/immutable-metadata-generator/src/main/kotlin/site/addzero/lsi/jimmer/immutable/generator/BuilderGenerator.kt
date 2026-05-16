package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderSetterMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

class BuilderGenerator(
    private val type: ImmutableBuilderTypeMetadata,
) {
    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = "Builder",
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            properties = listOf(
                LsiPropertySpec(
                    name = "__draft",
                    type = type.draftImplClassName,
                    modifiers = setOf(LsiModifier.PRIVATE)
                )
            ),
            callables = listOf(primaryConstructor(), defaultConstructor()) +
                type.setters.map(::setter) +
                buildCallable()
        )

    private fun primaryConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = true,
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = type.className.copyNullable(true)
                )
            ),
            statements = buildList {
                add(
                    LsiAssignmentStatement(
                        target = LsiNameExpression("__draft"),
                        expression = LsiNewExpression(
                            type = type.draftImplClassName,
                            arguments = listOf(LsiNullExpression, LsiNameExpression("base"))
                        )
                    )
                )
                for (slotName in type.visibleSlotNames) {
                    add(showSlotStatement(slotName = slotName, visible = false))
                }
            }
        )

    private fun defaultConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.THIS,
                arguments = listOf(LsiNullExpression)
            )
        )

    private fun setter(
        prop: ImmutableBuilderSetterMetadata,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = prop.lsiAnnotations,
            parameters = listOf(
                LsiParameterSpec(
                    name = prop.name,
                    type = prop.parameterLsiTypeName
                )
            ),
            returnType = prop.returnTypeName,
            statements = setterStatements(prop)
        )

    private fun setterStatements(
        prop: ImmutableBuilderSetterMetadata,
    ): List<LsiStatement> =
        buildList {
            if (prop.isNullable) {
                add(
                    LsiPropertySetStatement(
                        receiver = LsiNameExpression("__draft"),
                        name = prop.name,
                        expression = LsiNameExpression(prop.name)
                    )
                )
                add(showSlotStatement(prop.slotName))
            } else {
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(prop.name),
                            operator = LsiBinaryOperator.NOT_EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(
                            LsiPropertySetStatement(
                                receiver = LsiNameExpression("__draft"),
                                name = prop.name,
                                expression = LsiNameExpression(prop.name)
                            ),
                            showSlotStatement(prop.slotName),
                        )
                    )
                )
            }
            add(LsiReturnStatement(LsiNameExpression("this")))
        }

    private fun showSlotStatement(
        slotName: String,
        visible: Boolean = true,
    ): LsiExpressionStatement =
        LsiExpressionStatement(
            LsiCallExpression(
                receiver = LsiNameExpression("__draft"),
                name = "__show",
                arguments = listOf(
                    LsiCallExpression(
                        receiver = LsiTypeExpression(PROP_ID_LSI_CLASS_NAME),
                        name = "byIndex",
                        arguments = listOf(
                            LsiPropertyAccessExpression(
                                receiver = LsiTypeExpression(type.producerClassName),
                                name = slotName
                            )
                        )
                    ),
                    LsiLiteralExpression(visible)
                )
            )
        )

    private fun buildCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "build",
            returnType = type.className,
            statements = listOf(
                LsiReturnStatement(
                    LsiCastExpression(
                        type.className,
                        LsiCallExpression(
                            receiver = LsiNameExpression("__draft"),
                            name = "__unwrap"
                        )
                    )
                )
            )
        )
}
