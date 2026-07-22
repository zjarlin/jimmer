package org.babyfish.jimmer.compiler.immutable

import kotlin.math.abs
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind

/**
 * 将 Kotlin Draft 校验计划降低为纯 LSI Poet 成员和代码块。
 */
internal object ImmutableDraftKotlinValidationPoet {

    fun validationCode(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            prop.validationPlan.steps.forEach { step ->
                when (step) {
                    is JimmerImmutableDraftValidationStep.BuiltIn -> {
                        if (step.isKotlinRuntimeValidation()) {
                            addBuiltInValidation(typePlan, prop, valueName, step)
                        }
                    }
                    is JimmerImmutableDraftValidationStep.CustomValidator -> {
                        if (!step.isKotlinRuntimeValidation()) {
                            return@forEach
                        }
                        require(step.validatorTypeIds.isNotEmpty()) {
                            "Immutable draft custom validation has no validators: ${step.annotationTypeId.value}"
                        }
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

    fun typeValidationCode(
        typePlan: JimmerImmutableDraftTypePlan,
        valueName: String,
    ): LsiPoetCodeBlock {
        return draftCode {
            typePlan.customValidations.forEach { validation ->
                require(validation.validatorTypeIds.isNotEmpty()) {
                    "Immutable draft type validation has no validators: ${validation.annotationTypeId.value}"
                }
                statement {
                    name(typeValidatorFieldName(validation.annotationTypeId))
                    text(".validate(")
                    name(valueName)
                    text(")")
                }
            }
        }
    }

    fun companion(typePlan: JimmerImmutableDraftTypePlan): LsiPoetType? {
        val props = typePlan.propsInKotlinDeclarationOrder()
        val email = props.any { prop ->
            prop.validationPlan.builtInSteps.any { step ->
                step is JimmerImmutableDraftValidationStep.Email && step.isKotlinRuntimeValidation()
            }
        }
        val patterns = props.flatMap { prop ->
            prop.validationPlan.builtInSteps
                .filterIsInstance<JimmerImmutableDraftValidationStep.Pattern>()
                .filter { step -> step.isKotlinRuntimeValidation() }
                .map { pattern -> prop to pattern }
        }
        val propValidators = props.flatMap { prop ->
            prop.validationPlan.customValidatorSteps
                .filter { step -> step.isKotlinRuntimeValidation() }
                .map { validation -> prop to validation }
        }
        if (!email && patterns.isEmpty() && typePlan.customValidations.isEmpty() && propValidators.isEmpty()) {
            return null
        }
        val members = buildList<LsiPoetMember> {
            if (email) {
                add(
                    LsiPoetProperty(
                        name = EMAIL_PATTERN_FIELD,
                        type = PATTERN_TYPE,
                        mutable = false,
                        modifiers = PRIVATE,
                        initializer = draftCode {
                            type(PATTERN_TYPE)
                            text(".compile(")
                            string(EMAIL_PATTERN)
                            text(")")
                        },
                    )
                )
            }
            patterns.forEach { (prop, pattern) ->
                add(
                    LsiPoetProperty(
                        name = prop.kotlinPatternFieldName(pattern.index),
                        nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                        type = PATTERN_TYPE,
                        mutable = false,
                        modifiers = PRIVATE,
                        initializer = draftCode {
                            type(PATTERN_TYPE)
                            text(".compile(")
                            string(pattern.regexp)
                            if (pattern.flagMask != 0) {
                                text(", ${pattern.flagMask}")
                            }
                            text(")")
                        },
                    )
                )
            }
            typePlan.customValidations.forEach { validation -> add(typeValidatorProperty(typePlan, validation)) }
            propValidators.forEach { (prop, validation) ->
                add(propValidatorProperty(typePlan, prop, validation))
            }
        }
        return LsiPoetType(
            name = "Companion",
            kind = LsiPoetTypeKind.OBJECT,
            modifiers = setOf(LsiPoetModifier.COMPANION),
            members = members,
        )
    }

    private fun LsiPoetCodeBuilder.addBuiltInValidation(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        step: JimmerImmutableDraftValidationStep.BuiltIn,
    ) {
        val condition = when (step) {
            is JimmerImmutableDraftValidationStep.NotEmpty -> draftCode {
                name(valueName)
                text(".isEmpty()")
            }
            is JimmerImmutableDraftValidationStep.NotBlank -> draftCode {
                name(valueName)
                text(".trim().isEmpty()")
            }
            is JimmerImmutableDraftValidationStep.Size -> draftCode {
                name(valueName)
                text(if (step.measure == JimmerImmutableDraftSizeMeasure.LENGTH) ".length" else ".size")
                text(" ${step.comparison.operator} ${step.limit}")
            }
            is JimmerImmutableDraftValidationStep.NumericBound -> numericBoundCondition(valueName, step)
            is JimmerImmutableDraftValidationStep.Email -> draftCode {
                text("!")
                name(EMAIL_PATTERN_FIELD)
                text(".matcher(")
                name(valueName)
                text(").matches()")
            }
            is JimmerImmutableDraftValidationStep.Pattern -> draftCode {
                text("!")
                name(prop.kotlinPatternFieldName(step.index))
                text(".matcher(")
                name(valueName)
                text(").matches()")
            }
            is JimmerImmutableDraftValidationStep.Assert -> draftCode {
                name(valueName)
                text(" != ${step.expected}")
            }
            is JimmerImmutableDraftValidationStep.Digits -> digitsCondition(valueName, step)
            is JimmerImmutableDraftValidationStep.Temporal -> temporalCondition(valueName, step)
        }
        beginControlFlow {
            text("if (")
            if (step.failure.skipWhenNull) {
                name(valueName)
                text(" != null && (")
                add(condition)
                text(")")
            } else {
                add(condition)
            }
            text(")")
        }
        addFailure(typePlan, prop, valueName, step.failure)
        endControlFlow()
    }

    private fun numericBoundCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.NumericBound,
    ): LsiPoetCodeBlock {
        return draftCode {
            when (step.target) {
                JimmerImmutableDraftNumericTarget.PRIMITIVE -> {
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    name(valueName)
                    text(".toString()).compareTo(")
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    string(step.bound)
                    text(")) ${step.comparison.operator} 0")
                }
                JimmerImmutableDraftNumericTarget.BIG_INTEGER -> {
                    name(valueName)
                    text(".compareTo(")
                    type(BIG_INTEGER_TYPE)
                    text("(")
                    string(step.bound.substringBefore('.'))
                    text(")) ${step.comparison.operator} 0")
                }
                JimmerImmutableDraftNumericTarget.BIG_DECIMAL -> {
                    name(valueName)
                    text(".compareTo(")
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    string(step.bound)
                    text(")) ${step.comparison.operator} 0")
                }
            }
        }
    }

    private fun digitsCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.Digits,
    ): LsiPoetCodeBlock {
        return draftCode {
            name(valueName)
            when (step.target) {
                JimmerImmutableDraftDigitsTarget.BIG_DECIMAL -> when (step.component) {
                    JimmerImmutableDraftDigitsComponent.INTEGER -> {
                        text(".precision() - ")
                        name(valueName)
                        text(".scale() > ${step.limit}")
                    }
                    JimmerImmutableDraftDigitsComponent.FRACTION -> text(".scale() > ${step.limit}")
                }
                JimmerImmutableDraftDigitsTarget.BIG_INTEGER ->
                    text(".abs().toString().length > ${step.limit}")
                JimmerImmutableDraftDigitsTarget.PRIMITIVE,
                JimmerImmutableDraftDigitsTarget.CHAR_SEQUENCE,
                -> text(".toString().substringBefore('.').trimStart('-').length > ${step.limit}")
            }
        }
    }

    private fun temporalCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.Temporal,
    ): LsiPoetCodeBlock {
        val temporalType = when (step.target) {
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE -> LOCAL_DATE_TYPE
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE_TIME -> LOCAL_DATE_TIME_TYPE
            JimmerImmutableDraftTemporalTarget.LOCAL_TIME -> LOCAL_TIME_TYPE
            JimmerImmutableDraftTemporalTarget.INSTANT -> INSTANT_TYPE
        }
        return draftCode {
            when (step.constraint) {
                JimmerImmutableDraftTemporalConstraint.PAST_OR_PRESENT -> {
                    name(valueName)
                    text(".isAfter(")
                }
                JimmerImmutableDraftTemporalConstraint.PAST -> {
                    text("!")
                    name(valueName)
                    text(".isBefore(")
                }
                JimmerImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT -> {
                    name(valueName)
                    text(".isBefore(")
                }
                JimmerImmutableDraftTemporalConstraint.FUTURE -> {
                    text("!")
                    name(valueName)
                    text(".isAfter(")
                }
            }
            type(temporalType)
            text(".now())")
        }
    }

    private fun LsiPoetCodeBuilder.addFailure(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        failure: JimmerImmutableDraftValidationFailure,
    ) {
        statement {
            text("throw ")
            type(LsiDeclaredType(failure.exceptionTypeId))
            text("(")
            if (failure.usesDefaultMessage) {
                string("Illegal value'")
                text(" + ")
                name(valueName)
                text(" + ")
                string("'for property '${typePlan.qualifiedName}.${prop.name}', ${failure.defaultMessage}")
            } else {
                string(failure.declaredMessage)
            }
            text(")")
        }
    }

    private fun typeValidatorProperty(
        typePlan: JimmerImmutableDraftTypePlan,
        validation: ImmutableValidation,
    ): LsiPoetProperty {
        return LsiPoetProperty(
            name = typeValidatorFieldName(validation.annotationTypeId),
            type = draftDeclaredType(VALIDATOR_TYPE_ID, LsiDeclaredType(typePlan.typeId)),
            mutable = false,
            modifiers = PRIVATE,
            initializer = draftCode {
                type(VALIDATOR_TYPE)
                text("(")
                type(LsiDeclaredType(validation.annotationTypeId))
                text("::class.java, ")
                string(validation.message)
                text(", ")
                type(LsiDeclaredType(typePlan.typeId))
                text("::class.java, null)")
            },
        )
    }

    private fun propValidatorProperty(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        validation: JimmerImmutableDraftValidationStep.CustomValidator,
    ): LsiPoetProperty {
        return LsiPoetProperty(
            name = propValidatorFieldName(prop, validation.annotationTypeId),
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = draftDeclaredType(VALIDATOR_TYPE_ID, prop.type),
            mutable = false,
            modifiers = PRIVATE,
            initializer = draftCode {
                type(VALIDATOR_TYPE)
                text("(")
                type(LsiDeclaredType(validation.annotationTypeId))
                text("::class.java, ")
                string(validation.message)
                text(", ")
                type(LsiDeclaredType(typePlan.typeId))
                text("::class.java, ")
                type(PROP_ID_TYPE)
                text(".byIndex(${prop.slotName}))")
            },
        )
    }

    private fun JimmerImmutableDraftTypePlan.propsInKotlinDeclarationOrder(): List<JimmerImmutableDraftPropPlan> {
        return buildList {
            val added = hashSetOf<LsiSymbolId>()
            runtimeDeclaredPropIds.forEach { propId ->
                add(propsById.getValue(propId))
                added += propId
            }
            propsBySlot.forEach { prop -> if (added.add(prop.propId)) add(prop) }
        }
    }

    private fun JimmerImmutableDraftValidationStep.BuiltIn.isKotlinRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
    }

    private fun JimmerImmutableDraftValidationStep.CustomValidator.isKotlinRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.PROPERTY
    }

    private fun typeValidatorFieldName(annotationTypeId: LsiSymbolId): String {
        val qualifiedName = annotationTypeId.requireTypeQualifiedName()
        val simpleName = qualifiedName.substringAfterLast('.')
        return "___VALIDATOR__${simpleName.legacyUpper()}_${abs(qualifiedName.legacyKotlinClassNameHash())}"
    }

    private fun propValidatorFieldName(
        prop: JimmerImmutableDraftPropPlan,
        annotationTypeId: LsiSymbolId,
    ): String {
        val qualifiedName = annotationTypeId.requireTypeQualifiedName()
        val simpleName = qualifiedName.substringAfterLast('.')
        return "__${prop.name.legacyUpper()}_VALIDATOR__" +
            "${simpleName.legacyUpper()}_${abs(qualifiedName.legacyKotlinClassNameHash())}"
    }
}

private fun String.legacyKotlinClassNameHash(): Int {
    var index = 0
    while (index < length && Character.isLowerCase(codePointAt(index))) {
        val separator = indexOf('.', index)
        require(separator >= 0) { "Cannot derive legacy Kotlin class name from '$this'" }
        index = separator + 1
    }
    val names = buildList {
        add(if (index == 0) "" else substring(0, index - 1))
        val classNames = substring(index).split('.')
        require(classNames.all { name -> name.isNotEmpty() && Character.isUpperCase(name.codePointAt(0)) }) {
            "Cannot derive legacy Kotlin class name from '$this'"
        }
        addAll(classNames)
    }
    var result = 31 * false.hashCode() + emptyList<Any>().hashCode()
    result = 31 * result + names.hashCode()
    return result
}

private val JimmerImmutableDraftComparison.operator: String
    get() = when (this) {
        JimmerImmutableDraftComparison.LESS_THAN -> "<"
        JimmerImmutableDraftComparison.GREATER_THAN -> ">"
    }

private val PRIVATE = setOf(LsiPoetModifier.PRIVATE)

private const val EMAIL_PATTERN = "^[^@]+@[^@]+$"
private const val EMAIL_PATTERN_FIELD = "__email_pattern"

private val PATTERN_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.regex.Pattern"))
private val BIG_INTEGER_TYPE = LsiDeclaredType(LsiSymbolId.type("java.math.BigInteger"))
private val BIG_DECIMAL_TYPE = LsiDeclaredType(LsiSymbolId.type("java.math.BigDecimal"))
private val LOCAL_DATE_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDate"))
private val LOCAL_DATE_TIME_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDateTime"))
private val LOCAL_TIME_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.LocalTime"))
private val INSTANT_TYPE = LsiDeclaredType(LsiSymbolId.type("java.time.Instant"))
private val VALIDATOR_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.impl.validation.Validator")
private val VALIDATOR_TYPE = LsiDeclaredType(VALIDATOR_TYPE_ID)
private val PROP_ID_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.PropId"))
