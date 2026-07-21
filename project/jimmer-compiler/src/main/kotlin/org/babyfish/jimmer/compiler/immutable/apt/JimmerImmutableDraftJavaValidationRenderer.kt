package org.babyfish.jimmer.compiler.immutable.apt

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.regex.Pattern
import javax.lang.model.element.Modifier
import kotlin.math.abs
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftComparison
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftDigitsComponent
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftDigitsTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftNumericTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftPropPlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftSizeMeasure
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTemporalConstraint
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTemporalTarget
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTypePlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftValidationFailure
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftValidationStep
import org.babyfish.jimmer.compiler.immutable.JimmerValidation
import org.babyfish.jimmer.compiler.immutable.legacyUpper
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import org.babyfish.jimmer.meta.PropId
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget

internal object JimmerImmutableDraftJavaValidationRenderer {

    fun addStaticFields(
        type: JimmerImmutableDraftTypePlan,
        typeBuilder: TypeSpec.Builder,
    ) {
        type.propsBySlot.forEach { prop ->
            prop.validationPlan.builtInSteps
                .filterIsInstance<JimmerImmutableDraftValidationStep.Pattern>()
                .filter { pattern -> pattern.isJavaRuntimeValidation() }
                .forEach { pattern ->
                    typeBuilder.addField(
                        FieldSpec.builder(
                            Pattern::class.java,
                            prop.javaPatternFieldName(pattern.index),
                            Modifier.PRIVATE,
                            Modifier.STATIC,
                            Modifier.FINAL,
                        )
                            .initializer(
                                "\$T.compile(\$S, \$L)",
                                Pattern::class.java,
                                pattern.regexp,
                                pattern.flagMask,
                            )
                            .build()
                    )
                }
        }
        if (type.propsBySlot.any { prop ->
                prop.validationPlan.builtInSteps.any { step ->
                    step is JimmerImmutableDraftValidationStep.Email && step.isJavaRuntimeValidation()
                }
            }
        ) {
            typeBuilder.addField(
                FieldSpec.builder(
                    Pattern::class.java,
                    EMAIL_PATTERN_FIELD,
                    Modifier.PRIVATE,
                    Modifier.STATIC,
                    Modifier.FINAL,
                )
                    .initializer("\$T.compile(\$S)", Pattern::class.java, "^[^@]+@[^@]+$")
                    .build()
            )
        }
        type.customValidations.forEach { validation ->
            typeBuilder.addField(typeValidatorField(type, validation))
        }
        type.propsBySlot.forEach { prop ->
            prop.validationPlan.customValidatorSteps
                .filter { validation -> validation.isJavaRuntimeValidation() }
                .forEach { validation ->
                    typeBuilder.addField(propValidatorField(type, prop, validation))
                }
        }
    }

    fun addValidation(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        methodBuilder: MethodSpec.Builder,
    ) {
        prop.validationPlan.requiredNullCheck?.let { requiredNullCheck ->
            methodBuilder.beginControlFlow("if (\$L == null)", valueName)
            methodBuilder.addCode("throw new IllegalArgumentException(\n")
            methodBuilder.addCode("    \$S\n", requiredNullCheck.message)
            methodBuilder.addCode(");\n")
            methodBuilder.endControlFlow()
        }
        prop.validationPlan.steps.forEach { step ->
            if (step is JimmerImmutableDraftValidationStep.BuiltIn && !step.isJavaRuntimeValidation()) {
                return@forEach
            }
            when (step) {
                is JimmerImmutableDraftValidationStep.NotEmpty -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of("\$L.isEmpty()", valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.NotBlank -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of("\$L.trim().isEmpty()", valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Size -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of(
                        "\$L.\$L() \$L \$L",
                        valueName,
                        if (step.measure == JimmerImmutableDraftSizeMeasure.LENGTH) "length" else "size",
                        step.comparison.operator,
                        step.limit,
                    ),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.NumericBound -> addFailure(
                    type,
                    prop,
                    valueName,
                    numericCondition(step, valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Email -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of("!\$L.matcher(\$L).matches()", EMAIL_PATTERN_FIELD, valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Pattern -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of(
                        "!\$L.matcher(\$L).matches()",
                        prop.javaPatternFieldName(step.index),
                        valueName,
                    ),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Assert -> addFailure(
                    type,
                    prop,
                    valueName,
                    CodeBlock.of("\$L != \$L", valueName, step.expected),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Digits -> addFailure(
                    type,
                    prop,
                    valueName,
                    digitsCondition(step, valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.Temporal -> addFailure(
                    type,
                    prop,
                    valueName,
                    temporalCondition(step, valueName),
                    step.failure,
                    methodBuilder,
                )
                is JimmerImmutableDraftValidationStep.CustomValidator -> if (step.isJavaRuntimeValidation()) {
                    methodBuilder.addStatement(
                        "\$L.validate(\$L)",
                        propValidatorFieldName(prop, step.annotationTypeId),
                        valueName,
                    )
                }
            }
        }
    }

    fun addTypeValidation(
        type: JimmerImmutableDraftTypePlan,
        valueName: String,
        methodBuilder: MethodSpec.Builder,
    ) {
        type.customValidations.forEach { validation ->
            methodBuilder.addStatement(
                "\$L.validate(\$L)",
                typeValidatorFieldName(validation.annotationTypeId),
                valueName,
            )
        }
    }

    private fun typeValidatorField(
        type: JimmerImmutableDraftTypePlan,
        validation: JimmerValidation,
    ): FieldSpec {
        val originalType = type.originalClassName()
        return FieldSpec.builder(
            ParameterizedTypeName.get(VALIDATOR, originalType),
            typeValidatorFieldName(validation.annotationTypeId),
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL,
        )
            .initializer(
                "\n    new \$T<>(\$T.class, \$S, \$T.class, null)",
                VALIDATOR,
                validation.annotationTypeId.className(),
                validation.message,
                originalType,
            )
            .build()
    }

    private fun propValidatorField(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        validation: JimmerImmutableDraftValidationStep.CustomValidator,
    ): FieldSpec {
        val originalType = type.originalClassName()
        return FieldSpec.builder(
            ParameterizedTypeName.get(VALIDATOR, prop.type.toJavaTypeName().box()),
            propValidatorFieldName(prop, validation.annotationTypeId),
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL,
        )
            .initializer(
                "\n    new \$T<>(\$T.class, \$S, \$T.class, \$T.byIndex(\$L))",
                VALIDATOR,
                validation.annotationTypeId.className(),
                validation.message,
                originalType,
                PropId::class.java,
                prop.slotName,
            )
            .build()
    }

    private fun addFailure(
        type: JimmerImmutableDraftTypePlan,
        prop: JimmerImmutableDraftPropPlan,
        valueName: String,
        condition: CodeBlock,
        failure: JimmerImmutableDraftValidationFailure,
        methodBuilder: MethodSpec.Builder,
    ) {
        if (failure.skipWhenNull) {
            methodBuilder.beginControlFlow("if (\$L != null && \$L)", valueName, condition)
        } else {
            methodBuilder.beginControlFlow("if (\$L)", condition)
        }
        if (failure.usesDefaultMessage) {
            methodBuilder.addStatement(
                "throw new \$T(\$S + \$L + \$S)",
                failure.exceptionTypeId.className(),
                "Illegal value '",
                valueName,
                "' for property '${type.qualifiedName}.${prop.name}', ${failure.defaultMessage}",
            )
        }
        methodBuilder.endControlFlow()
    }

    private fun numericCondition(
        step: JimmerImmutableDraftValidationStep.NumericBound,
        valueName: String,
    ): CodeBlock {
        val operator = step.comparison.operator
        return when (step.target) {
            JimmerImmutableDraftNumericTarget.PRIMITIVE -> CodeBlock.of(
                "\$L \$L \$L",
                valueName,
                operator,
                step.bound,
            )
            JimmerImmutableDraftNumericTarget.BIG_INTEGER -> CodeBlock.of(
                "\$L.compareTo(\$L) \$L 0",
                valueName,
                bigIntegerLiteral(step.bound),
                operator,
            )
            JimmerImmutableDraftNumericTarget.BIG_DECIMAL -> CodeBlock.of(
                "\$L.compareTo(\$L) \$L 0",
                valueName,
                bigDecimalLiteral(step.bound),
                operator,
            )
        }
    }

    private fun digitsCondition(
        step: JimmerImmutableDraftValidationStep.Digits,
        valueName: String,
    ): CodeBlock {
        return when (step.target) {
            JimmerImmutableDraftDigitsTarget.BIG_DECIMAL -> if (
                step.component == JimmerImmutableDraftDigitsComponent.INTEGER
            ) {
                CodeBlock.of("\$L.precision() > \$L", valueName, step.limit)
            } else {
                CodeBlock.of("\$L.scale() > \$L", valueName, step.limit)
            }
            JimmerImmutableDraftDigitsTarget.BIG_INTEGER -> CodeBlock.of(
                "\$L.bitLength() > \$L",
                valueName,
                step.limit,
            )
            JimmerImmutableDraftDigitsTarget.CHAR_SEQUENCE -> CodeBlock.of(
                "\$L.length() > \$L",
                valueName,
                step.limit,
            )
            JimmerImmutableDraftDigitsTarget.PRIMITIVE -> CodeBlock.of(
                "new \$T(\$L).precision() > \$L",
                BigDecimal::class.java,
                valueName,
                step.limit,
            )
        }
    }

    private fun temporalCondition(
        step: JimmerImmutableDraftValidationStep.Temporal,
        valueName: String,
    ): CodeBlock {
        val temporalType = when (step.target) {
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE -> LocalDate::class.java
            JimmerImmutableDraftTemporalTarget.LOCAL_DATE_TIME -> LocalDateTime::class.java
            JimmerImmutableDraftTemporalTarget.LOCAL_TIME -> LocalTime::class.java
            JimmerImmutableDraftTemporalTarget.INSTANT -> Instant::class.java
        }
        return when (step.constraint) {
            JimmerImmutableDraftTemporalConstraint.PAST_OR_PRESENT -> CodeBlock.of(
                "\$L.isAfter(\$T.now())",
                valueName,
                temporalType,
            )
            JimmerImmutableDraftTemporalConstraint.PAST -> if (
                step.target == JimmerImmutableDraftTemporalTarget.INSTANT
            ) {
                CodeBlock.of("\$L.isAfter(\$T.now())", valueName, temporalType)
            } else {
                CodeBlock.of(
                    "\$L.isAfter(\$T.now()) || \$L.isEqual(\$T.now())",
                    valueName,
                    temporalType,
                    valueName,
                    temporalType,
                )
            }
            JimmerImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT -> CodeBlock.of(
                "\$L.isBefore(\$T.now())",
                valueName,
                temporalType,
            )
            JimmerImmutableDraftTemporalConstraint.FUTURE -> if (
                step.target == JimmerImmutableDraftTemporalTarget.INSTANT
            ) {
                CodeBlock.of("\$L.isBefore(\$T.now())", valueName, temporalType)
            } else {
                CodeBlock.of(
                    "\$L.isBefore(\$T.now()) || \$L.isEqual(\$T.now())",
                    valueName,
                    temporalType,
                    valueName,
                    temporalType,
                )
            }
        }
    }

    private fun bigIntegerLiteral(bound: String): CodeBlock {
        return when (bound) {
            "-1" -> CodeBlock.of("\$T.NEGATIVE_ONE", BigInteger::class.java)
            "0" -> CodeBlock.of("\$T.ZERO", BigInteger::class.java)
            "1" -> CodeBlock.of("\$T.ONE", BigInteger::class.java)
            "10" -> CodeBlock.of("\$T.TEN", BigInteger::class.java)
            else -> CodeBlock.of("new \$T(\$S)", BigInteger::class.java, bound)
        }
    }

    private fun bigDecimalLiteral(bound: String): CodeBlock {
        return when (bound) {
            "0" -> CodeBlock.of("\$T.ZERO", BigDecimal::class.java)
            "1" -> CodeBlock.of("\$T.ONE", BigDecimal::class.java)
            "10" -> CodeBlock.of("\$T.TEN", BigDecimal::class.java)
            else -> CodeBlock.of("new \$T(\$S)", BigDecimal::class.java, bound)
        }
    }

    private fun typeValidatorFieldName(annotationTypeId: LsiSymbolId): String {
        val annotationType = annotationTypeId.className()
        return "__${annotationType.simpleName().legacyUpper()}_VALIDATOR_${abs(annotationType.hashCode())}"
    }

    private fun propValidatorFieldName(
        prop: JimmerImmutableDraftPropPlan,
        annotationTypeId: LsiSymbolId,
    ): String {
        val annotationType = annotationTypeId.className()
        return "__${prop.codegenName.legacyUpper()}_${annotationType.simpleName().legacyUpper()}_VALIDATOR_" +
            abs(annotationType.hashCode())
    }

    private val JimmerImmutableDraftComparison.operator: String
        get() = if (this == JimmerImmutableDraftComparison.LESS_THAN) "<" else ">"

    private fun JimmerImmutableDraftValidationStep.BuiltIn.isJavaRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.METHOD
    }

    private fun JimmerImmutableDraftValidationStep.CustomValidator.isJavaRuntimeValidation(): Boolean {
        return sourceAnnotationUseSiteTarget == LsiAnnotationUseSiteTarget.METHOD
    }

    private fun JimmerImmutableDraftTypePlan.originalClassName(): ClassName =
        ClassName.bestGuess(qualifiedName)

    private fun LsiSymbolId.className(): ClassName = ClassName.bestGuess(requireTypeQualifiedName())

    private val VALIDATOR = ClassName.get("org.babyfish.jimmer.impl.validation", "Validator")

    private const val EMAIL_PATTERN_FIELD = "__EMAIL_PATTERN__"
}
