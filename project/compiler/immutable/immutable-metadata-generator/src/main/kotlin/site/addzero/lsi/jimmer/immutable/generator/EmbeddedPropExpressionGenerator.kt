package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.BASE_TABLE_OWNER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind

class EmbeddedPropExpressionGenerator(
    private val type: ImmutablePropsTypeMetadata,
) {
    init {
        require(type.isEmbeddable) {
            "EmbeddedPropExpressionGenerator requires embeddable metadata: ${type.className.canonicalName}"
        }
    }

    fun generate(): LsiFileSpec =
        LsiFileSpec(
            packageName = type.propExpressionClassName.packageName,
            name = type.propExpressionClassName.simpleName,
            types = listOf(propExpressionType()),
        )

    private fun propExpressionType(): LsiTypeSpec =
        LsiTypeSpec(
            name = type.propExpressionClassName.simpleName,
            kind = LsiTypeSpecKind.CLASS,
            annotations = listOf(generatedAnnotation(type.className)),
            modifiers = setOf(LsiModifier.PUBLIC),
            superClass = ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(type.className),
            callables = listOf(
                rawConstructor(),
                baseTableConstructor(),
            ) + type.properties.map(::propertyFun) + listOf(baseTableOwnerFun()),
            originatingClassName = type.className,
        )

    private fun rawConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "raw",
                    type = EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(type.className),
                )
            ),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(LsiNameExpression("raw")),
            ),
        )

    private fun baseTableConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "base",
                    type = type.propExpressionClassName,
                ),
                LsiParameterSpec(
                    name = "baseTableOwner",
                    type = BASE_TABLE_OWNER_LSI_CLASS_NAME,
                ),
            ),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.SUPER,
                arguments = listOf(
                    LsiNameExpression("base"),
                    LsiNameExpression("baseTableOwner"),
                ),
            ),
        )

    private fun propertyFun(prop: ImmutablePropsPropMetadata): LsiCallableSpec {
        require(!prop.isAssociation && !prop.isRemote) {
            "Embedded prop expression does not support association property '${type.className.canonicalName}.${prop.name}'"
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            modifiers = setOf(LsiModifier.PUBLIC),
            returnType = propertyReturnType(prop),
            statements = listOf(
                LsiReturnStatement(
                    if (prop.isEmbedded) {
                        LsiNewExpression(
                            type = prop.targetType.toPropExpressionClassName(),
                            arguments = listOf(
                                propertyGetExpression(prop)
                            ),
                        )
                    } else {
                        propertyGetExpression(prop)
                    }
                )
            ),
        )
    }

    private fun baseTableOwnerFun(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "__baseTableOwner",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "baseTableOwner",
                    type = BASE_TABLE_OWNER_LSI_CLASS_NAME,
                )
            ),
            returnType = type.propExpressionClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = type.propExpressionClassName,
                        arguments = listOf(
                            LsiThisExpression,
                            LsiNameExpression("baseTableOwner"),
                        ),
                    )
                )
            ),
        )

    private fun propertyReturnType(prop: ImmutablePropsPropMetadata): LsiTypeName =
        when {
            prop.isEmbedded -> prop.targetType.toPropExpressionClassName()
            else -> prop.type.toPropExpressionTypeName()
        }

    private fun propertyGetExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            name = "__get",
            arguments = listOf(propUnwrapExpression(prop)),
        )

    private fun propUnwrapExpression(prop: ImmutablePropsPropMetadata): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(type.propsClassName),
                name = prop.constantName,
            ),
            name = "unwrap",
        )
}
