package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiThisExpression

class AssociatedIdGenerator(
    private val jacksonTypes: JacksonTypes,
    private val withImplementation: Boolean,
) {

    fun generate(prop: ImmutableAssociatedIdMetadata?): LsiPropertySpec? {
        if (prop == null) {
            return null
        }
        return LsiPropertySpec(
            name = prop.name,
            type = prop.associatedIdLsiTypeName,
            annotations = listOf(
                LsiAnnotationSpec(
                    type = jacksonTypes.jsonIgnore,
                    useSiteTarget = LsiAnnotationUseSiteTarget.GET
                )
            ),
            modifiers = setOf(
                LsiModifier.PUBLIC,
                if (withImplementation) {
                    LsiModifier.OVERRIDE
                } else {
                    LsiModifier.ABSTRACT
                }
            ),
            mutable = true,
            getterStatements = getterStatements(prop),
            setterStatements = setterStatements(prop),
        )
    }

    private fun getterStatements(
        prop: ImmutableAssociatedIdMetadata,
    ): List<LsiStatement> {
        if (!withImplementation) {
            return emptyList()
        }
        val ownerExpression = ownerExpression(prop)
        return buildList {
            if (prop.isNullable) {
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = ownerExpression,
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(LsiReturnStatement(LsiNullExpression)),
                    )
                )
            }
            add(
                LsiReturnStatement(
                    LsiPropertyGetExpression(
                        receiver = ownerExpression,
                        name = prop.targetIdPropName,
                        type = prop.associatedIdLsiTypeName,
                    )
                )
            )
        }
    }

    private fun setterStatements(
        prop: ImmutableAssociatedIdMetadata,
    ): List<LsiStatement> {
        if (!withImplementation) {
            return emptyList()
        }
        val ownerExpression = ownerExpression(prop)
        return buildList {
            if (prop.isNullable) {
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression("value"),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(
                            LsiPropertySetStatement(
                                receiver = LsiThisExpression,
                                name = prop.ownerPropName,
                                expression = LsiNullExpression,
                            ),
                            LsiReturnStatement(null),
                        ),
                    )
                )
            }
            add(
                LsiPropertySetStatement(
                    receiver = ownerExpression,
                    name = prop.targetIdPropName,
                    expression = LsiNameExpression("value"),
                )
            )
        }
    }

    private fun ownerExpression(prop: ImmutableAssociatedIdMetadata): LsiCallExpression =
        LsiCallExpression(name = prop.ownerPropName)
}
