package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import kotlin.math.abs
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftComparison
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftDigitsComponent
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftDigitsTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftNumericTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftPropPlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftSizeMeasure
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTemporalConstraint
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTemporalTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftValidationFailure
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftValidationStep
import org.babyfish.jimmer.compiler.immutable.JimmerValidation
import org.babyfish.jimmer.compiler.immutable.legacyUpper
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget

internal class JimmerImmutableDraftKotlinValidationRenderer(
    private val context: JimmerImmutableDraftKotlinRenderContext,
) {

    private val type = context.type

    fun addValidation(
        builder: CodeBlock.Builder,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
    ) {
        prop.validationPlan.steps.forEach { step ->
            when (step) {
                is JimmerImmutableDraftValidationStep.BuiltIn -> if (step.isKotlinRuntimeValidation()) {
                    addBuiltInValidation(builder, prop, valueName, step)
                }
                is JimmerImmutableDraftValidationStep.CustomValidator -> {
                    if (!step.isKotlinRuntimeValidation()) {
                        return@forEach
                    }
                    require(step.validatorTypeIds.isNotEmpty()) {
                        "Immutable draft custom validation has no validators: ${step.annotationTypeId.value}"
                    }
                    builder.addStatement(
                        "%N.validate(%N)",
                        propValidatorFieldName(prop, step.annotationTypeId.requireTypeQualifiedName()),
                        valueName,
                    )
                }
            }
        }
    }

    fun addTypeValidationStatements(
        builder: FunSpec.Builder,
        valueName: String,
    ) {
        type.customValidations.forEach { validation ->
            require(validation.validatorTypeIds.isNotEmpty()) {
                "Immutable draft type validation has no validators: ${validation.annotationTypeId.value}"
            }
            builder.addStatement(
                "%N.validate(%N)",
                typeValidatorFieldName(validation.annotationTypeId.requireTypeQualifiedName()),
                valueName,
            )
        }
    }

    fun addCompanion(builder: TypeSpec.Builder) {
        val email = context.propsInDeclarationOrder.any { prop ->
            prop.validationPlan.builtInSteps.any { step ->
                step is JimmerImmutableDraftValidationStep.Email && step.isKotlinRuntimeValidation()
            }
        }
        val patterns = context.propsInDeclarationOrder.flatMap { prop ->
            prop.validationPlan.builtInSteps
                .filterIsInstance<JimmerImmutableDraftValidationStep.Pattern>()
                .filter { step -> step.isKotlinRuntimeValidation() }
                .map { pattern -> prop to pattern }
        }
        val propValidators = context.propsInDeclarationOrder.flatMap { prop ->
            prop.validationPlan.customValidatorSteps
                .filter { validation -> validation.isKotlinRuntimeValidation() }
                .map { validation -> prop to validation }
        }
        if (!email && patterns.isEmpty() && type.customValidations.isEmpty() && propValidators.isEmpty()) {
            return
        }
        builder.addType(
            TypeSpec.companionObjectBuilder()
                .apply {
                    if (email) {
                        addProperty(
                            PropertySpec.builder(EMAIL_PATTERN_FIELD, PATTERN, KModifier.PRIVATE)
                                .initializer("%T.compile(%S)", PATTERN, EMAIL_PATTERN)
                                .build()
                        )
                    }
                    patterns.forEach { (prop, pattern) ->
                        addProperty(
                            PropertySpec.builder(
                                prop.kotlinPatternFieldName(pattern.index),
                                PATTERN,
                                KModifier.PRIVATE,
                            )
                                .initializer(
                                    if (pattern.flagMask == 0) {
                                        CodeBlock.of("%T.compile(%S)", PATTERN, pattern.regexp)
                                    } else {
                                        CodeBlock.of(
                                            "%T.compile(%S, %L)",
                                            PATTERN,
                                            pattern.regexp,
                                            pattern.flagMask,
                                        )
                                    }
                                )
                                .build()
                        )
                    }
                    type.customValidations.forEach { validation ->
                        addProperty(typeValidatorProperty(validation))
                    }
                    propValidators.forEach { (prop, validation) ->
                        addProperty(propValidatorProperty(prop, validation))
                    }
                }
                .build()
        )
    }

    private fun addBuiltInValidation(
        builder: CodeBlock.Builder,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        step: JimmerImmutableDraftValidationStep.BuiltIn,
    ) {
        val condition = when (step) {
            is JimmerImmutableDraftValidationStep.NotEmpty ->
                CodeBlock.of("%N.isEmpty()", valueName)
            is JimmerImmutableDraftValidationStep.NotBlank ->
                CodeBlock.of("%N.trim().isEmpty()", valueName)
            is JimmerImmutableDraftValidationStep.Size -> CodeBlock.of(
                "%N.%L %L %L",
                valueName,
                if (step.measure == JimmerImmutableDraftSizeMeasure.LENGTH) "length" else "size",
                step.comparison.operator,
                step.limit,
            )
            is JimmerImmutableDraftValidationStep.NumericBound ->
                numericBoundCondition(valueName, step)
            is JimmerImmutableDraftValidationStep.Email ->
                CodeBlock.of("!%N.matcher(%N).matches()", EMAIL_PATTERN_FIELD, valueName)
            is JimmerImmutableDraftValidationStep.Pattern -> CodeBlock.of(
                "!%N.matcher(%N).matches()",
                prop.kotlinPatternFieldName(step.index),
                valueName,
            )
            is JimmerImmutableDraftValidationStep.Assert ->
                CodeBlock.of("%N != %L", valueName, step.expected)
            is JimmerImmutableDraftValidationStep.Digits ->
                digitsCondition(valueName, step)
            is JimmerImmutableDraftValidationStep.Temporal ->
                temporalCondition(valueName, step)
        }
        if (step.failure.skipWhenNull) {
            builder.beginControlFlow("if (%N != null && (%L))", valueName, condition)
        } else {
            builder.beginControlFlow("if (%L)", condition)
        }
        addFailure(builder, prop, valueName, step.failure)
        builder.endControlFlow()
    }

    private fun numericBoundCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.NumericBound,
    ): CodeBlock {
        val comparison = step.comparison.operator
        return when (step.target) {
            JimmerImmutableDraftNumericTarget.PRIMITIVE -> CodeBlock.of(
                "%T(%N.toString()).compareTo(%T(%S)) %L 0",
                BIG_DECIMAL,
                valueName,
                BIG_DECIMAL,
                step.bound,
                comparison,
            )
            JimmerImmutableDraftNumericTarget.BIG_INTEGER -> CodeBlock.of(
                "%N.compareTo(%T(%S)) %L 0",
                valueName,
                BIG_INTEGER,
                step.bound.substringBefore('.'),
                comparison,
            )
            JimmerImmutableDraftNumericTarget.BIG_DECIMAL -> CodeBlock.of(
                "%N.compareTo(%T(%S)) %L 0",
                valueName,
                BIG_DECIMAL,
                step.bound,
                comparison,
            )
        }
    }

    private fun digitsCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.Digits,
    ): CodeBlock {
        return when (step.target) {
            JimmerImmutableDraftDigitsTarget.BIG_DECIMAL -> when (step.component) {
                JimmerImmutableDraftDigitsComponent.INTEGER -> CodeBlock.of(
                    "%N.precision() - %N.scale() > %L",
                    valueName,
                    valueName,
                    step.limit,
                )
                JimmerImmutableDraftDigitsComponent.FRACTION -> CodeBlock.of(
                    "%N.scale() > %L",
                    valueName,
                    step.limit,
                )
            }
            JimmerImmutableDraftDigitsTarget.BIG_INTEGER -> CodeBlock.of(
                "%N.abs().toString().length > %L",
                valueName,
                step.limit,
            )
            JimmerImmutableDraftDigitsTarget.PRIMITIVE,
            JimmerImmutableDraftDigitsTarget.CHAR_SEQUENCE,
            -> CodeBlock.of(
                "%N.toString().substringBefore('.').trimStart('-').length > %L",
                valueName,
                step.limit,
            )
        }
    }

    private fun temporalCondition(
        valueName: String,
        step: JimmerImmutableDraftValidationStep.Temporal,
    ): CodeBlock {
        val temporalType = when (step.target) {
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE -> LOCAL_DATE
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE_TIME -> LOCAL_DATE_TIME
            JimmerImmutableDraftTemporalTarget.LOCAL_TIME -> LOCAL_TIME
            JimmerImmutableDraftTemporalTarget.INSTANT -> INSTANT
        }
        return when (step.constraint) {
            JimmerImmutableDraftTemporalConstraint.PAST_OR_PRESENT ->
                CodeBlock.of("%N.isAfter(%T.now())", valueName, temporalType)
            JimmerImmutableDraftTemporalConstraint.PAST ->
                CodeBlock.of("!%N.isBefore(%T.now())", valueName, temporalType)
            JimmerImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT ->
                CodeBlock.of("%N.isBefore(%T.now())", valueName, temporalType)
            JimmerImmutableDraftTemporalConstraint.FUTURE ->
                CodeBlock.of("!%N.isAfter(%T.now())", valueName, temporalType)
        }
    }

    private fun addFailure(
        builder: CodeBlock.Builder,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        failure: JimmerImmutableDraftValidationFailure,
    ) {
        val exceptionType = ClassName.bestGuess(failure.exceptionTypeId.requireTypeQualifiedName())
        if (failure.usesDefaultMessage) {
            builder.addStatement(
                "throw %T(%S + %N + %S)",
                exceptionType,
                "Illegal value'",
                valueName,
                "'for property '${type.qualifiedName}.${prop.name}', ${failure.defaultMessage}",
            )
        } else {
            builder.addStatement("throw %T(%S)", exceptionType, failure.declaredMessage)
        }
    }

    private fun typeValidatorProperty(validation: JimmerValidation): PropertySpec {
        val annotationName = validation.annotationTypeId.requireTypeQualifiedName()
        return PropertySpec.builder(
            typeValidatorFieldName(annotationName),
            VALIDATOR.parameterizedBy(context.modelClass),
            KModifier.PRIVATE,
        )
            .initializer(
                "%T(%T::class.java, %S, %T::class.java, null)",
                VALIDATOR,
                ClassName.bestGuess(annotationName),
                validation.message,
                context.modelClass,
            )
            .build()
    }

    private fun propValidatorProperty(
        prop: JimmerImmutableDraftPropPlan,
        validation: JimmerImmutableDraftValidationStep.CustomValidator,
    ): PropertySpec {
        val annotationName = validation.annotationTypeId.requireTypeQualifiedName()
        return PropertySpec.builder(
            propValidatorFieldName(prop, annotationName),
            VALIDATOR.parameterizedBy(context.propType(prop)),
            KModifier.PRIVATE,
        )
            .initializer(
                "%T(%T::class.java, %S, %T::class.java, %T.byIndex(%L))",
                VALIDATOR,
                ClassName.bestGuess(annotationName),
                validation.message,
                context.modelClass,
                PROP_ID,
                prop.slotName,
            )
            .build()
    }

    private fun JimmerImmutableDraftValidationStep.BuiltIn.isKotlinRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
    }

    private fun JimmerImmutableDraftValidationStep.CustomValidator.isKotlinRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.PROPERTY
    }

    private fun typeValidatorFieldName(annotationName: String): String {
        val annotationType = ClassName.bestGuess(annotationName)
        return "___VALIDATOR__${annotationType.simpleName.legacyUpper()}_${abs(annotationType.hashCode())}"
    }

    private fun propValidatorFieldName(
        prop: JimmerImmutableDraftPropPlan,
        annotationName: String,
    ): String {
        val annotationType = ClassName.bestGuess(annotationName)
        return "__${prop.name.legacyUpper()}_VALIDATOR__" +
            "${annotationType.simpleName.legacyUpper()}_${abs(annotationType.hashCode())}"
    }
}

private val JimmerImmutableDraftComparison.operator: String
    get() = when (this) {
        JimmerImmutableDraftComparison.LESS_THAN -> "<"
        JimmerImmutableDraftComparison.GREATER_THAN -> ">"
    }
