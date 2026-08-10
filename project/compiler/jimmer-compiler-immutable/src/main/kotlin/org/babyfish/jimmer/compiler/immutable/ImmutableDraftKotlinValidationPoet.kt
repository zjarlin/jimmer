package org.babyfish.jimmer.compiler.immutable

import kotlin.math.abs
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableDraftComparison
import site.addzero.lsi.jimmer.ImmutableDraftDigitsComponent
import site.addzero.lsi.jimmer.ImmutableDraftDigitsTarget
import site.addzero.lsi.jimmer.ImmutableDraftNumericTarget
import site.addzero.lsi.jimmer.ImmutableDraftSizeMeasure
import site.addzero.lsi.jimmer.ImmutableDraftTemporalConstraint
import site.addzero.lsi.jimmer.ImmutableDraftTemporalTarget
import site.addzero.lsi.jimmer.ImmutableDraftValidationFailure
import site.addzero.lsi.jimmer.ImmutableDraftValidationStep
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind

/**
 * 将 Kotlin Draft 校验计划降低为纯 LSI Poet 成员和代码块。
 */
internal object ImmutableDraftKotlinValidationPoet {

    fun validationCode(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
    ): LsiCodeBlock {
        return draftCode {
            prop.validationPlan.steps.forEach { step ->
                when (step) {
                    is ImmutableDraftValidationStep.BuiltIn -> {
                        if (step.isKotlinRuntimeValidation()) {
                            addBuiltInValidation(typePlan, prop, valueName, step)
                        }
                    }
                    is ImmutableDraftValidationStep.CustomValidator -> {
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
    ): LsiCodeBlock {
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

    fun companion(typePlan: JimmerImmutableDraftTypePlan): LsiClass? {
        val props = typePlan.propsInKotlinDeclarationOrder()
        val email = props.any { prop ->
            prop.validationPlan.builtInSteps.any { step ->
                step is ImmutableDraftValidationStep.Email && step.isKotlinRuntimeValidation()
            }
        }
        val patterns = props.flatMap { prop ->
            prop.validationPlan.builtInSteps
                .filterIsInstance<ImmutableDraftValidationStep.Pattern>()
                .withIndex()
                .filter { indexedPattern -> indexedPattern.value.isKotlinRuntimeValidation() }
                .map { indexedPattern -> prop to indexedPattern }
        }
        val propValidators = props.flatMap { prop ->
            prop.validationPlan.customValidatorSteps
                .filter { step -> step.isKotlinRuntimeValidation() }
                .map { validation -> prop to validation }
        }
        if (!email && patterns.isEmpty() && typePlan.customValidations.isEmpty() && propValidators.isEmpty()) {
            return null
        }
        val members = buildList<LsiMember> {
            if (email) {
                add(
                    LsiProperty(
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
            patterns.forEach { (prop, indexedPattern) ->
                val pattern = indexedPattern.value
                add(
                    LsiProperty(
                        name = prop.kotlinPatternFieldName(indexedPattern.index),
                        nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                        type = PATTERN_TYPE,
                        mutable = false,
                        modifiers = PRIVATE,
                        initializer = draftCode {
                            type(PATTERN_TYPE)
                            text(".compile(")
                            string(pattern.regexp)
                            val flagMask = pattern.flags.toJvmPatternFlagMask()
                            if (flagMask != 0) {
                                text(", $flagMask")
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
        return LsiClass(
            name = "Companion",
            kind = LsiTypeDeclarationKind.OBJECT,
            modifiers = setOf(LsiModifier.COMPANION),
            members = members,
        )
    }

    private fun LsiCodeBuilder.addBuiltInValidation(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        step: ImmutableDraftValidationStep.BuiltIn,
    ) {
        val condition = when (step) {
            is ImmutableDraftValidationStep.NotEmpty -> draftCode {
                name(valueName)
                text(".isEmpty()")
            }
            is ImmutableDraftValidationStep.NotBlank -> draftCode {
                name(valueName)
                text(".trim().isEmpty()")
            }
            is ImmutableDraftValidationStep.Size -> draftCode {
                name(valueName)
                text(if (step.measure == ImmutableDraftSizeMeasure.LENGTH) ".length" else ".size")
                text(" ${step.comparison.operator} ${step.limit}")
            }
            is ImmutableDraftValidationStep.NumericBound -> numericBoundCondition(valueName, step)
            is ImmutableDraftValidationStep.Email -> draftCode {
                text("!")
                name(EMAIL_PATTERN_FIELD)
                text(".matcher(")
                name(valueName)
                text(").matches()")
            }
            is ImmutableDraftValidationStep.Pattern -> draftCode {
                text("!")
                name(prop.kotlinPatternFieldName(prop.validationPlan.patternIndexOf(step)))
                text(".matcher(")
                name(valueName)
                text(").matches()")
            }
            is ImmutableDraftValidationStep.Assert -> draftCode {
                name(valueName)
                text(" != ${step.expected}")
            }
            is ImmutableDraftValidationStep.Digits -> digitsCondition(valueName, step)
            is ImmutableDraftValidationStep.Temporal -> temporalCondition(valueName, step)
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
        step: ImmutableDraftValidationStep.NumericBound,
    ): LsiCodeBlock {
        return draftCode {
            when (step.target) {
                ImmutableDraftNumericTarget.PRIMITIVE -> {
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    name(valueName)
                    text(".toString()).compareTo(")
                    type(BIG_DECIMAL_TYPE)
                    text("(")
                    string(step.bound)
                    text(")) ${step.comparison.operator} 0")
                }
                ImmutableDraftNumericTarget.BIG_INTEGER -> {
                    name(valueName)
                    text(".compareTo(")
                    type(BIG_INTEGER_TYPE)
                    text("(")
                    string(step.bound.substringBefore('.'))
                    text(")) ${step.comparison.operator} 0")
                }
                ImmutableDraftNumericTarget.BIG_DECIMAL -> {
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
        step: ImmutableDraftValidationStep.Digits,
    ): LsiCodeBlock {
        return draftCode {
            name(valueName)
            when (step.target) {
                ImmutableDraftDigitsTarget.BIG_DECIMAL -> when (step.component) {
                    ImmutableDraftDigitsComponent.INTEGER -> {
                        text(".precision() - ")
                        name(valueName)
                        text(".scale() > ${step.limit}")
                    }
                    ImmutableDraftDigitsComponent.FRACTION -> text(".scale() > ${step.limit}")
                }
                ImmutableDraftDigitsTarget.BIG_INTEGER ->
                    text(".abs().toString().length > ${step.limit}")
                ImmutableDraftDigitsTarget.PRIMITIVE,
                ImmutableDraftDigitsTarget.CHAR_SEQUENCE,
                -> text(".toString().substringBefore('.').trimStart('-').length > ${step.limit}")
            }
        }
    }

    private fun temporalCondition(
        valueName: String,
        step: ImmutableDraftValidationStep.Temporal,
    ): LsiCodeBlock {
        val temporalType = when (step.target) {
            ImmutableDraftTemporalTarget.LOCAL_DATE -> LOCAL_DATE_TYPE
            ImmutableDraftTemporalTarget.LOCAL_DATE_TIME -> LOCAL_DATE_TIME_TYPE
            ImmutableDraftTemporalTarget.LOCAL_TIME -> LOCAL_TIME_TYPE
            ImmutableDraftTemporalTarget.INSTANT -> INSTANT_TYPE
        }
        return draftCode {
            when (step.constraint) {
                ImmutableDraftTemporalConstraint.PAST_OR_PRESENT -> {
                    name(valueName)
                    text(".isAfter(")
                }
                ImmutableDraftTemporalConstraint.PAST -> {
                    text("!")
                    name(valueName)
                    text(".isBefore(")
                }
                ImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT -> {
                    name(valueName)
                    text(".isBefore(")
                }
                ImmutableDraftTemporalConstraint.FUTURE -> {
                    text("!")
                    name(valueName)
                    text(".isAfter(")
                }
            }
            type(temporalType)
            text(".now())")
        }
    }

    private fun LsiCodeBuilder.addFailure(
        typePlan: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        failure: ImmutableDraftValidationFailure,
    ) {
        if (!failure.usesDefaultMessage) {
            return
        }
        statement {
            text("throw ")
            type(LsiDeclaredType(failure.exceptionTypeId))
            text("(")
            string("Illegal value'")
            text(" + ")
            name(valueName)
            text(" + ")
            string("'for property '${typePlan.qualifiedName}.${prop.name}', ${failure.defaultMessage}")
            text(")")
        }
    }

    private fun typeValidatorProperty(
        typePlan: JimmerImmutableDraftTypePlan,
        validation: ImmutableValidation,
    ): LsiProperty {
        return LsiProperty(
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
        validation: ImmutableDraftValidationStep.CustomValidator,
    ): LsiProperty {
        return LsiProperty(
            name = propValidatorFieldName(prop, validation.annotationTypeId),
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
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

    private fun ImmutableDraftValidationStep.BuiltIn.isKotlinRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
    }

    private fun ImmutableDraftValidationStep.CustomValidator.isKotlinRuntimeValidation(): Boolean {
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

private val ImmutableDraftComparison.operator: String
    get() = when (this) {
        ImmutableDraftComparison.LESS_THAN -> "<"
        ImmutableDraftComparison.GREATER_THAN -> ">"
    }

private val PRIVATE = setOf(LsiModifier.PRIVATE)

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
