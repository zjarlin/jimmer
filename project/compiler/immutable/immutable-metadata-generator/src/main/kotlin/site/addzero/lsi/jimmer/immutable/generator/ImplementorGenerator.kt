package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.IMPLEMENTOR
import site.addzero.lsi.codegen.IMMUTABLE_MODULE_REQUIRED_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_SPI_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_TYPE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_ANY_LSI_CLASS_NAME as ANY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_INT_LSI_CLASS_NAME as INT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorDeepPropIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiWhenCase
import site.addzero.lsi.poet.LsiWhenStatement
import site.addzero.lsi.poet.LsiTypeSpecKind

internal class ImplementorGenerator(
    private val jacksonTypes: JacksonTypes,
    private val type: ImmutableImplementorTypeMetadata,
) {

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = IMPLEMENTOR,
            kind = LsiTypeSpecKind.INTERFACE,
            annotations = listOf(
                generatedAnnotation(type.className),
                propertyOrderAnnotation()
            ),
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.ABSTRACT),
            superInterfaces = listOf(type.className, IMMUTABLE_SPI_LSI_CLASS_NAME),
            properties = listOf(dummyPropForNoImmutableModuleError()) + deeperPropIdProperties(),
            callables = listOf(
                getCallable(PropertyDispatchArgKind.PROP_ID),
                getCallable(PropertyDispatchArgKind.PROP_NAME),
                typeCallable()
            )
        )

    private fun propertyOrderAnnotation(): LsiAnnotationSpec =
        LsiAnnotationSpec(
            type = jacksonTypes.jsonPropertyOrder,
            positionalArguments = listOf(LsiStringAnnotationValue("dummyPropForJacksonError__")) +
                type.propertyOrderNames.map(::LsiStringAnnotationValue)
        )

    private fun getCallable(argKind: PropertyDispatchArgKind): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__get",
            parameters = listOf(
                LsiParameterSpec(
                    name = "prop",
                    type = argKind.typeName
                )
            ),
            returnType = ANY_LSI_CLASS_NAME.copyNullable(true),
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(getWhenStatement(argKind))
        )

    private fun getWhenStatement(argKind: PropertyDispatchArgKind): LsiWhenStatement =
        LsiWhenStatement(
            subject = whenSubject(argKind),
            cases = buildWhenCases(argKind),
            elseStatements = listOf(illegalPropThrow(argKind))
        )

    private fun whenSubject(argKind: PropertyDispatchArgKind): site.addzero.lsi.poet.LsiExpression =
        if (argKind.usesIndexedSubject) {
            LsiCallExpression(receiver = LsiNameExpression("prop"), name = "asIndex")
        } else {
            LsiNameExpression("prop")
        }

    private fun buildWhenCases(argKind: PropertyDispatchArgKind): List<LsiWhenCase> =
        buildList {
            if (argKind.usesIndexedSubject) {
                add(
                    LsiWhenCase(
                        conditions = listOf(LsiLiteralExpression(-1)),
                        statements = listOf(
                            LsiReturnStatement(
                                LsiCallExpression(
                                    name = "__get",
                                    arguments = listOf(
                                        LsiCallExpression(
                                            receiver = LsiNameExpression("prop"),
                                            name = "asName"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            }
            type.getCases.forEach { prop ->
                add(
                    LsiWhenCase(
                        conditions = listOf(
                            if (argKind.usesIndexedSubject) {
                                LsiNameExpression(prop.slotName)
                            } else {
                                LsiLiteralExpression(prop.name)
                            }
                        ),
                        statements = listOf(LsiReturnStatement(LsiNameExpression(prop.name)))
                    )
                )
            }
        }

    private fun illegalPropThrow(argKind: PropertyDispatchArgKind): LsiThrowStatement =
        LsiThrowStatement(
            LsiNewExpression(
                type = JAVA_ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                arguments = listOf(
                    LsiBinaryExpression(
                        left = LsiBinaryExpression(
                            left = LsiLiteralExpression("Illegal property ${argKind.illegalKindLabel} "),
                            operator = LsiBinaryOperator.PLUS,
                            right = LsiLiteralExpression(" for \"${type.typeDescription}\": "),
                        ),
                        operator = LsiBinaryOperator.PLUS,
                        right = LsiNameExpression("prop"),
                    )
                )
            )
        )

    private fun typeCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__type",
            returnType = IMMUTABLE_TYPE_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.OVERRIDE),
            statements = listOf(
                LsiReturnStatement(
                    LsiPropertyAccessExpression(
                        receiver = LsiTypeExpression(type.producerClassName),
                        name = "type",
                    )
                )
            )
        )

    private fun dummyPropForNoImmutableModuleError(): LsiPropertySpec =
        LsiPropertySpec(
            name = "dummyPropForJacksonError__",
            type = INT_LSI_CLASS_NAME,
            getterStatements = listOf(
                LsiThrowStatement(
                    LsiNewExpression(
                        type = IMMUTABLE_MODULE_REQUIRED_EXCEPTION_LSI_CLASS_NAME,
                    )
                )
            )
        )

    private fun deeperPropIdProperties(): List<LsiPropertySpec> =
        type.deeperPropIds.map(::deeperPropIdProperty)

    private fun deeperPropIdProperty(
        prop: ImmutableImplementorDeepPropIdMetadata,
    ): LsiPropertySpec =
        LsiPropertySpec(
            name = prop.constantName,
            type = PROP_ID_LSI_CLASS_NAME,
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            initializer = LsiCallExpression(
                receiver = LsiCallExpression(
                    receiver = LsiCallExpression(
                        receiver = LsiPropertyAccessExpression(
                            receiver = LsiTypeExpression(type.producerClassName),
                            name = "type",
                        ),
                        name = "getProp",
                        arguments = listOf(LsiLiteralExpression(prop.propName)),
                    ),
                    name = "getManyToManyViewBaseDeeperProp",
                ),
                name = "getId",
            )
        )

}
