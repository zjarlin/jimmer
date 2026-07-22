package org.babyfish.jimmer.compiler.immutable

import kotlin.math.abs
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier

/**
 * 把已经冻结的校验计划降低为 Java Draft 使用的 LSI Poet 成员和语句。
 */
internal object ImmutableDraftJavaValidationPoet {

    fun staticMembers(type: JimmerImmutableDraftTypePlan): List<LsiPoetMember> {
        return buildList {
            type.propsBySlot.forEach { prop ->
                prop.validationPlan.builtInSteps
                    .filterIsInstance<JimmerImmutableDraftValidationStep.Pattern>()
                    .filter { pattern -> pattern.isJavaRuntimeValidation() }
                    .forEach { pattern ->
                        add(
                            LsiPoetField(
                                name = prop.javaPatternFieldName(pattern.index),
                                type = PATTERN_TYPE,
                                modifiers = PRIVATE_STATIC_FINAL,
                                initializer = draftCode {
                                    type(PATTERN_TYPE)
                                    text(".compile(")
                                    string(pattern.regexp)
                                    text(", ${pattern.flagMask})")
                                },
                            )
                        )
                    }
            }
            if (type.requiresJavaEmailPattern()) {
                add(
                    LsiPoetField(
                        name = EMAIL_PATTERN_FIELD,
                        type = PATTERN_TYPE,
                        modifiers = PRIVATE_STATIC_FINAL,
                        initializer = draftCode {
                            type(PATTERN_TYPE)
                            text(".compile(")
                            string(EMAIL_PATTERN)
                            text(")")
                        },
                    )
                )
            }
            type.customValidations.forEach { validation ->
                add(typeValidatorField(type, validation))
            }
            type.propsBySlot.forEach { prop ->
                prop.validationPlan.customValidatorSteps
                    .filter { validation -> validation.isJavaRuntimeValidation() }
                    .forEach { validation ->
                        add(propValidatorField(type, prop, validation))
                    }
            }
        }
    }

    fun validationCode(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            prop.validationPlan.requiredNullCheck?.let { required ->
                beginControlFlow {
                    text("if (")
                    name(valueName)
                    text(" == null)")
                }
                text("throw new IllegalArgumentException(\n")
                text("    ")
                string(required.message)
                text("\n);\n")
                endControlFlow()
            }
            prop.validationPlan.steps.forEach { step ->
                if (step is JimmerImmutableDraftValidationStep.BuiltIn && !step.isJavaRuntimeValidation()) {
                    return@forEach
                }
                when (step) {
                    is JimmerImmutableDraftValidationStep.NotEmpty -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            name(valueName)
                            text(".isEmpty()")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.NotBlank -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            name(valueName)
                            text(".trim().isEmpty()")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Size -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            name(valueName)
                            text(
                                if (step.measure == JimmerImmutableDraftSizeMeasure.LENGTH) {
                                    ".length()"
                                } else {
                                    ".size()"
                                }
                            )
                            text(" ${step.comparison.operator} ${step.limit}")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.NumericBound -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = numericCondition(step, valueName),
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Email -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            text("!")
                            name(EMAIL_PATTERN_FIELD)
                            text(".matcher(")
                            name(valueName)
                            text(").matches()")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Pattern -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            text("!")
                            name(prop.javaPatternFieldName(step.index))
                            text(".matcher(")
                            name(valueName)
                            text(").matches()")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Assert -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = draftCode {
                            name(valueName)
                            text(" != ${step.expected}")
                        },
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Digits -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = digitsCondition(step, valueName),
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.Temporal -> addFailure(
                        type = type,
                        prop = prop,
                        valueName = valueName,
                        condition = temporalCondition(step, valueName),
                        failure = step.failure,
                    )
                    is JimmerImmutableDraftValidationStep.CustomValidator -> {
                        if (step.isJavaRuntimeValidation()) {
                            statement {
                                name(propValidatorFieldName(prop, step.annotationTypeId))
                                text(".validate(")
                                name(valueName)
                                text(")")
                            }
                        }
                    }
                }
            }
        }
    }

    fun typeValidationCode(
        type: JimmerImmutableDraftTypePlan,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            type.customValidations.forEach { validation ->
                statement {
                    name(typeValidatorFieldName(validation.annotationTypeId))
                    text(".validate(")
                    name(valueName)
                    text(")")
                }
            }
        }
    }

    private fun LsiPoetCodeBuilder.addFailure(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        condition: LsiPoetCodeBlock,
        failure: JimmerImmutableDraftValidationFailure,
    ) {
        beginControlFlow {
            text("if (")
            if (failure.skipWhenNull) {
                name(valueName)
                text(" != null && ")
            }
            add(condition)
            text(")")
        }
        if (failure.usesDefaultMessage) {
            statement {
                text("throw new ")
                type(LsiDeclaredType(failure.exceptionTypeId))
                text("(")
                string("Illegal value '")
                text(" + ")
                name(valueName)
                text(" + ")
                string("' for property '${type.qualifiedName}.${prop.name}', ${failure.defaultMessage}")
                text(")")
            }
        }
        endControlFlow()
    }

    private fun typeValidatorField(
        type: JimmerImmutableDraftTypePlan,
        validation: ImmutableValidation,
    ): LsiPoetField {
        val originalType = LsiDeclaredType(type.typeId)
        return LsiPoetField(
            name = typeValidatorFieldName(validation.annotationTypeId),
            type = draftDeclaredType(VALIDATOR_TYPE_ID, originalType),
            modifiers = PRIVATE_STATIC_FINAL,
            initializer = draftCode {
                text("\n    new ")
                type(VALIDATOR_RAW_TYPE)
                text("<>(")
                type(LsiDeclaredType(validation.annotationTypeId))
                text(".class, ")
                string(validation.message)
                text(", ")
                type(originalType)
                text(".class, null)")
            },
        )
    }

    private fun propValidatorField(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        validation: JimmerImmutableDraftValidationStep.CustomValidator,
    ): LsiPoetField {
        val originalType = LsiDeclaredType(type.typeId)
        val validatorType = draftDeclaredType(VALIDATOR_TYPE_ID, prop.type.boxedForJavaDraft())
        return LsiPoetField(
            name = propValidatorFieldName(prop, validation.annotationTypeId),
            type = validatorType,
            modifiers = PRIVATE_STATIC_FINAL,
            initializer = draftCode {
                text("\n    new ")
                type(VALIDATOR_RAW_TYPE)
                text("<>(")
                type(LsiDeclaredType(validation.annotationTypeId))
                text(".class, ")
                string(validation.message)
                text(", ")
                type(originalType)
                text(".class, ")
                type(PROP_ID_TYPE)
                text(".byIndex(${prop.slotName}))")
            },
        )
    }

    private fun numericCondition(
        step: JimmerImmutableDraftValidationStep.NumericBound,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            name(valueName)
            when (step.target) {
                JimmerImmutableDraftNumericTarget.PRIMITIVE -> {
                    text(" ${step.comparison.operator} ${step.bound}")
                }
                JimmerImmutableDraftNumericTarget.BIG_INTEGER -> {
                    text(".compareTo(")
                    add(bigIntegerLiteral(step.bound))
                    text(") ${step.comparison.operator} 0")
                }
                JimmerImmutableDraftNumericTarget.BIG_DECIMAL -> {
                    text(".compareTo(")
                    add(bigDecimalLiteral(step.bound))
                    text(") ${step.comparison.operator} 0")
                }
            }
        }
    }

    private fun digitsCondition(
        step: JimmerImmutableDraftValidationStep.Digits,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            when (step.target) {
                JimmerImmutableDraftDigitsTarget.BIG_DECIMAL -> {
                    name(valueName)
                    text(
                        if (step.component == JimmerImmutableDraftDigitsComponent.INTEGER) {
                            ".precision() > ${step.limit}"
                        } else {
                            ".scale() > ${step.limit}"
                        }
                    )
                }
                JimmerImmutableDraftDigitsTarget.BIG_INTEGER -> {
                    name(valueName)
                    text(".bitLength() > ${step.limit}")
                }
                JimmerImmutableDraftDigitsTarget.CHAR_SEQUENCE -> {
                    name(valueName)
                    text(".length() > ${step.limit}")
                }
                JimmerImmutableDraftDigitsTarget.PRIMITIVE -> {
                    text("new ")
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    name(valueName)
                    text(").precision() > ${step.limit}")
                }
            }
        }
    }

    private fun temporalCondition(
        step: JimmerImmutableDraftValidationStep.Temporal,
        valueName: String,
    ): LsiPoetCodeBlock {
        val temporalType = when (step.target) {
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE -> LOCAL_DATE_TYPE
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE_TIME -> LOCAL_DATE_TIME_TYPE
            JimmerImmutableDraftTemporalTarget.LOCAL_TIME -> LOCAL_TIME_TYPE
            JimmerImmutableDraftTemporalTarget.INSTANT -> INSTANT_TYPE
        }
        return draftCode {
            name(valueName)
            when (step.constraint) {
                JimmerImmutableDraftTemporalConstraint.PAST_OR_PRESENT -> {
                    text(".isAfter(")
                    type(temporalType)
                    text(".now())")
                }
                JimmerImmutableDraftTemporalConstraint.PAST -> {
                    text(".isAfter(")
                    type(temporalType)
                    text(".now())")
                    if (step.target != JimmerImmutableDraftTemporalTarget.INSTANT) {
                        text(" || ")
                        name(valueName)
                        text(".isEqual(")
                        type(temporalType)
                        text(".now())")
                    }
                }
                JimmerImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT -> {
                    text(".isBefore(")
                    type(temporalType)
                    text(".now())")
                }
                JimmerImmutableDraftTemporalConstraint.FUTURE -> {
                    text(".isBefore(")
                    type(temporalType)
                    text(".now())")
                    if (step.target != JimmerImmutableDraftTemporalTarget.INSTANT) {
                        text(" || ")
                        name(valueName)
                        text(".isEqual(")
                        type(temporalType)
                        text(".now())")
                    }
                }
            }
        }
    }

    private fun bigIntegerLiteral(bound: String): LsiPoetCodeBlock {
        return draftCode {
            when (bound) {
                "-1" -> {
                    type(BIG_INTEGER_TYPE)
                    text(".NEGATIVE_ONE")
                }
                "0" -> {
                    type(BIG_INTEGER_TYPE)
                    text(".ZERO")
                }
                "1" -> {
                    type(BIG_INTEGER_TYPE)
                    text(".ONE")
                }
                "10" -> {
                    type(BIG_INTEGER_TYPE)
                    text(".TEN")
                }
                else -> {
                    text("new ")
                    type(BIG_INTEGER_TYPE)
                    text("(")
                    string(bound)
                    text(")")
                }
            }
        }
    }

    private fun bigDecimalLiteral(bound: String): LsiPoetCodeBlock {
        return draftCode {
            when (bound) {
                "0" -> {
                    type(BIG_DECIMAL_TYPE)
                    text(".ZERO")
                }
                "1" -> {
                    type(BIG_DECIMAL_TYPE)
                    text(".ONE")
                }
                "10" -> {
                    type(BIG_DECIMAL_TYPE)
                    text(".TEN")
                }
                else -> {
                    text("new ")
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    string(bound)
                    text(")")
                }
            }
        }
    }

    private fun typeValidatorFieldName(annotationTypeId: LsiSymbolId): String {
        val qualifiedName = annotationTypeId.requireTypeQualifiedName()
        val simpleName = qualifiedName.substringAfterLast('.')
        return "__${simpleName.legacyUpper()}_VALIDATOR_${abs(qualifiedName.hashCode())}"
    }

    private fun propValidatorFieldName(
        prop: JimmerImmutableDraftPropPlan,
        annotationTypeId: LsiSymbolId,
    ): String {
        val qualifiedName = annotationTypeId.requireTypeQualifiedName()
        val simpleName = qualifiedName.substringAfterLast('.')
        return "__${prop.codegenName.legacyUpper()}_${simpleName.legacyUpper()}_VALIDATOR_" +
            abs(qualifiedName.hashCode())
    }

    private fun JimmerImmutableDraftTypePlan.requiresJavaEmailPattern(): Boolean {
        return propsBySlot.any { prop ->
            prop.validationPlan.builtInSteps.any { step ->
                step is JimmerImmutableDraftValidationStep.Email && step.isJavaRuntimeValidation()
            }
        }
    }

    private val JimmerImmutableDraftComparison.operator: String
        get() = if (this == JimmerImmutableDraftComparison.LESS_THAN) "<" else ">"

    private fun JimmerImmutableDraftValidationStep.BuiltIn.isJavaRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.METHOD
    }

    private fun JimmerImmutableDraftValidationStep.CustomValidator.isJavaRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.METHOD
    }
}

private val PRIVATE_STATIC_FINAL = setOf(
    LsiPoetModifier.PRIVATE,
    LsiPoetModifier.STATIC,
    LsiPoetModifier.FINAL,
)

private val PATTERN_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.regex.Pattern"))

private val BIG_INTEGER_TYPE = LsiDeclaredType(LsiSymbolId.type("java.math.BigInteger"))

private val BIG_DECIMAL_TYPE = LsiDeclaredType(LsiSymbolId.type("java.math.BigDecimal"))

private val LOCAL_DATE_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDate"))

private val LOCAL_DATE_TIME_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDateTime"))

private val LOCAL_TIME_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalTime"))

private val INSTANT_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.Instant"))

private val PROP_ID_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.PropId"))

private val VALIDATOR_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.impl.validation.Validator")

private val VALIDATOR_RAW_TYPE = LsiDeclaredType(VALIDATOR_TYPE_ID)

private const val EMAIL_PATTERN = "^[^@]+@[^@]+$"

private const val EMAIL_PATTERN_FIELD = "__EMAIL_PATTERN__"
