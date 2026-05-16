package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.toLsiPoet

internal class InputBuilderGenerator(
    private val parentGenerator: DtoGenerator
) {

    private val dtoType: LsiDtoType = parentGenerator.dtoType

    fun generate(): LsiTypeSpec =
        LsiTypeSpec(
            name = "Builder",
            kind = LsiTypeSpecKind.CLASS,
            annotations = annotations(),
            properties = properties(),
            callables = callables(),
        )

    private fun annotations(): List<LsiAnnotationSpec> =
        buildList {
            add(generatedAnnotation())
            add(
                LsiAnnotationSpec(
                    type = parentGenerator.ctx.jacksonTypes.jsonPojoBuilder,
                    members = mapOf("withPrefix" to LsiStringAnnotationValue("")),
                )
            )
            for (annotation in dtoType.annotations) {
                if (annotation.qualifiedName == parentGenerator.ctx.jacksonTypes.jsonNaming.canonicalName) {
                    if (!annotation.valueMap.containsKey("value")) {
                        continue
                    }
                    add(DtoGenerator.lsiAnnotationOf(annotation))
                }
            }
        }

    private fun properties(): List<LsiPropertySpec> =
        buildList {
            for (prop in dtoType.dtoPropViews) {
                add(field(prop))
                stateField(prop)?.let(::add)
            }
            for (prop in dtoType.userPropViews) {
                add(field(prop))
                stateField(prop)?.let(::add)
            }
        }

    private fun callables(): List<LsiCallableSpec> =
        buildList {
            for (prop in dtoType.dtoPropViews) {
                add(setter(prop))
            }
            for (prop in dtoType.userPropViews) {
                add(setter(prop))
            }
            add(buildFun())
        }

    private fun field(prop: LsiDtoAbstractPropView): LsiPropertySpec {
        val isFieldNullable = isFieldNullable(prop)
        return LsiPropertySpec(
            name = prop.name,
            type = parentGenerator.propLsiTypeName(prop).copyNullable(isFieldNullable),
            modifiers = setOf(LsiModifier.PRIVATE),
            mutable = true,
            initializer = if (isFieldNullable) {
                LsiNullExpression
            } else {
                LsiLiteralExpression(false)
            },
        )
    }

    private fun stateField(prop: LsiDtoAbstractPropView): LsiPropertySpec? =
        parentGenerator.statePropName(prop, true)?.let {
            LsiPropertySpec(
                name = it,
                type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                modifiers = setOf(LsiModifier.PRIVATE),
                mutable = true,
                initializer = LsiLiteralExpression(false),
            )
        }

    private fun setter(prop: LsiDtoAbstractPropView): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = prop.name,
            annotations = jacksonAnnotations(prop),
            parameters = listOf(
                site.addzero.lsi.poet.LsiParameterSpec(
                    name = prop.name,
                    type = parentGenerator.propLsiTypeName(prop),
                )
            ),
            returnType = parentGenerator.getDtoLsiClassName("Builder"),
            statements = buildList {
                add(
                    LsiPropertySetStatement(
                        receiver = site.addzero.lsi.poet.LsiThisExpression,
                        name = prop.name,
                        expression = LsiNameExpression(prop.name),
                    )
                )
                parentGenerator.statePropName(prop, true)?.let {
                    add(
                        LsiPropertySetStatement(
                            receiver = site.addzero.lsi.poet.LsiThisExpression,
                            name = it,
                            expression = LsiLiteralExpression(true),
                        )
                    )
                }
                add(LsiReturnStatement(site.addzero.lsi.poet.LsiThisExpression))
            },
        )

    private fun buildFun(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "build",
            returnType = parentGenerator.getDtoLsiClassName(),
            statements = buildStatements(),
        )

    private fun buildStatements(): List<LsiStatement> =
        buildList {
            val statements = mutableListOf<LsiStatement>()
            for (prop in dtoType.dtoPropViews) {
                val builderStatePropName = parentGenerator.statePropName(prop, true)
                if (builderStatePropName != null &&
                    prop.inputModifier == DtoModifier.FIXED
                ) {
                    statements += LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression(builderStatePropName),
                            operator = LsiBinaryOperator.EQUALS,
                            right = LsiLiteralExpression(false),
                        ),
                        thenStatements = listOf(
                            LsiThrowStatement(unknownPropertyExpression("unknownNullableProperty", prop.name))
                        ),
                    )
                }
                if (!prop.isNullable && isFieldNullable(prop)) {
                    statements += missingNonNullPropertyStatement(prop.name)
                }
            }
            for (prop in dtoType.userPropViews) {
                if (!prop.isNullable && isFieldNullable(prop)) {
                    statements += missingNonNullPropertyStatement(prop.name)
                }
            }
            statements += LsiReturnStatement(
                LsiNewExpression(
                    type = parentGenerator.getDtoLsiClassName(),
                    arguments = buildConstructorArguments(),
                )
            )
            addAll(statements)
        }

    private fun buildConstructorArguments(): List<LsiExpression> =
        buildList {
            for (prop in dtoType.dtoPropViews) {
                add(LsiNameExpression(prop.name))
                val builderStatePropName = parentGenerator.statePropName(prop, true)
                if (builderStatePropName != null && parentGenerator.statePropName(prop, false) != null) {
                    add(LsiNameExpression(builderStatePropName))
                }
            }
            for (prop in dtoType.userPropViews) {
                add(LsiNameExpression(prop.name))
            }
        }

    private fun missingNonNullPropertyStatement(propName: String): LsiStatement =
        LsiIfStatement(
            condition = LsiBinaryExpression(
                left = LsiNameExpression(propName),
                operator = LsiBinaryOperator.EQUALS,
                right = LsiNullExpression,
            ),
            thenStatements = listOf(
                LsiThrowStatement(unknownPropertyExpression("unknownNonNullProperty", propName))
            ),
        )

    private fun unknownPropertyExpression(methodName: String, propName: String): LsiExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(INPUT_LSI_CLASS_NAME),
            name = methodName,
            arguments = listOf(
                LsiJavaClassExpression(parentGenerator.getDtoLsiClassName()),
                LsiLiteralExpression(propName),
            ),
        )

    private fun jacksonAnnotations(prop: LsiDtoAbstractPropView): List<LsiAnnotationSpec> {
        val typeNames = mutableSetOf<String>()
        return buildList {
            for (anno in prop.annotations) {
                if (JACKSON_ANNO_PREFIXIES.any { anno.qualifiedName.startsWith(it) } &&
                    typeNames.add(anno.qualifiedName)) {
                    add(DtoGenerator.lsiAnnotationOf(anno))
                }
            }
            if (prop is LsiDtoPropView) {
                for (anno in prop.tailFieldAnnotations) {
                    val annoQualifiedName = anno.qualifiedName
                    if (annoQualifiedName == null || JACKSON_ANNO_PREFIXIES.none { annoQualifiedName.startsWith(it) }) {
                        continue
                    }
                    if (typeNames.add(annoQualifiedName)) {
                        add(anno.toLsiPoet().copy(useSiteTarget = null))
                    }
                }
            }
        }
    }

    companion object {

        private const val INPUT_CLASS_FQ_NAME = "org.babyfish.jimmer.Input"
        private val INPUT_LSI_CLASS_NAME = LsiClassName.bestGuess(INPUT_CLASS_FQ_NAME)

        private val JACKSON_ANNO_PREFIXIES = arrayOf(
            "tools.jackson.databind.annotation.",
            "com.fasterxml.jackson.databind.annotation.",
            "com.fasterxml.jackson.annotation."
        )

        private fun isFieldNullable(prop: LsiDtoAbstractPropView): Boolean =
            prop !is LsiDtoPropView || (prop.funcName != "null" && prop.funcName != "notNull")
    }
}
