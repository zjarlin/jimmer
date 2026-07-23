package site.addzero.lsi.jimmer

import java.math.BigDecimal
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

/** 不可变属性在 Draft 写入阶段执行的语言无关校验计划。 */
data class ImmutableDraftValidationPlan(
    val propId: LsiSymbolId,
    val requiredNullCheck: ImmutableDraftRequiredNullCheck?,
    val steps: List<ImmutableDraftValidationStep>,
) {

    val builtInSteps: List<ImmutableDraftValidationStep.BuiltIn> =
        steps.filterIsInstance<ImmutableDraftValidationStep.BuiltIn>()

    val customValidatorSteps: List<ImmutableDraftValidationStep.CustomValidator> =
        steps.filterIsInstance<ImmutableDraftValidationStep.CustomValidator>()
}

/** 非空属性在 Draft 写入阶段执行的必填校验。 */
data class ImmutableDraftRequiredNullCheck(
    val message: String,
)

/** Draft 校验计划中的一个规范化校验步骤。 */
sealed interface ImmutableDraftValidationStep {

    sealed interface BuiltIn : ImmutableDraftValidationStep {
        val sourceAnnotationTypeId: LsiSymbolId
        val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?
        val failure: ImmutableDraftValidationFailure
    }

    data class NotEmpty(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class NotBlank(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Size(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val measure: ImmutableDraftSizeMeasure,
        val comparison: ImmutableDraftComparison,
        val limit: Int,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class NumericBound(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val target: ImmutableDraftNumericTarget,
        val comparison: ImmutableDraftComparison,
        val bound: String,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Email(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Pattern(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val regexp: String,
        val flags: List<ImmutableDraftPatternFlag>,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Assert(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val expected: Boolean,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Digits(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val target: ImmutableDraftDigitsTarget,
        val component: ImmutableDraftDigitsComponent,
        val limit: Int,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class Temporal(
        override val sourceAnnotationTypeId: LsiSymbolId,
        override val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
        val target: ImmutableDraftTemporalTarget,
        val constraint: ImmutableDraftTemporalConstraint,
        override val failure: ImmutableDraftValidationFailure,
    ) : BuiltIn

    data class CustomValidator(
        val annotationTypeId: LsiSymbolId,
        val validatorTypeIds: List<LsiSymbolId>,
        val message: String,
        val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
    ) : ImmutableDraftValidationStep
}

/** 校验失败时使用的异常族与消息语义。 */
data class ImmutableDraftValidationFailure(
    val exceptionTypeId: LsiSymbolId,
    val declaredMessage: String,
    val defaultMessage: String,
    val skipWhenNull: Boolean,
) {

    val usesDefaultMessage: Boolean = declaredMessage.isEmpty() ||
        declaredMessage.startsWith("{javax.validation.constraints.") ||
        declaredMessage.startsWith("{jakarta.validation.constraints.")
}

/** 尺寸校验读取字符串长度或集合大小。 */
enum class ImmutableDraftSizeMeasure {
    LENGTH,
    SIZE,
}

/** 数值和尺寸校验的失败比较方向。 */
enum class ImmutableDraftComparison {
    LESS_THAN,
    GREATER_THAN,
}

/** 数值边界校验的运行时值类别。 */
enum class ImmutableDraftNumericTarget {
    PRIMITIVE,
    BIG_INTEGER,
    BIG_DECIMAL,
}

/** 数字位数校验的运行时值类别。 */
enum class ImmutableDraftDigitsTarget {
    PRIMITIVE,
    BIG_INTEGER,
    BIG_DECIMAL,
    CHAR_SEQUENCE,
}

/** 数字位数校验的整数或小数部分。 */
enum class ImmutableDraftDigitsComponent {
    INTEGER,
    FRACTION,
}

/** 时间校验的运行时值类别。 */
enum class ImmutableDraftTemporalTarget {
    LOCAL_DATE,
    LOCAL_DATE_TIME,
    LOCAL_TIME,
    INSTANT,
}

/** 相对于当前时间的校验约束。 */
enum class ImmutableDraftTemporalConstraint {
    PAST_OR_PRESENT,
    PAST,
    FUTURE_OR_PRESENT,
    FUTURE,
}

/** 正则校验声明的语言无关标志。 */
enum class ImmutableDraftPatternFlag {
    UNIX_LINES,
    CASE_INSENSITIVE,
    COMMENTS,
    MULTILINE,
    LITERAL,
    DOTALL,
    UNICODE_CASE,
    CANON_EQ,
    UNICODE_CHARACTER_CLASS,
}

/** 将不可变属性冻结为 Draft 写入阶段的校验计划。 */
fun ImmutableProp.toDraftValidationPlan(
    workspace: LsiWorkspace,
): ImmutableDraftValidationPlan {
    return DraftValidationPlanCompiler(this, workspace).compile()
}

private class DraftValidationPlanCompiler(
    private val prop: ImmutableProp,
    private val workspace: LsiWorkspace,
) {

    fun compile(): ImmutableDraftValidationPlan {
        val annotations = prop.annotations
            .filter(LsiAnnotation::isBuiltInValidationAnnotation)
            .distinctBy { annotation -> annotation.type to annotation.arguments }
        annotations.forEach { annotation ->
            validateAnnotationDeclaration(prop, annotation, workspace)
            annotation.arguments["message"]?.value?.let { message ->
                if (message !is LsiAnnotationValue.StringValue) {
                    throw invalidArgument(prop, annotation, "message", "a string value")
                }
            }
        }
        val annotationsByName = annotations.groupBy(LsiAnnotation::builtInValidationName)
        val skipWhenNull = prop.nullable && !prop.isUnboxedPrimitive()
        val steps = buildList {
            addNotEmpty(prop, annotationsByName, skipWhenNull)
            addNotBlank(prop, annotationsByName, skipWhenNull)
            addSize(prop, annotationsByName, skipWhenNull)
            addBounds(prop, annotationsByName, skipWhenNull)
            addEmail(prop, annotationsByName, skipWhenNull)
            addPatterns(prop, annotationsByName, skipWhenNull)
            addCustomValidators(prop, workspace)
            addAsserts(prop, annotationsByName, skipWhenNull)
            addDigits(prop, annotationsByName, skipWhenNull)
            addTemporal(prop, annotationsByName, skipWhenNull)
        }
        return ImmutableDraftValidationPlan(
            propId = prop.id,
            requiredNullCheck = if (!prop.nullable && !prop.isUnboxedPrimitive()) {
                ImmutableDraftRequiredNullCheck(
                    message = "'${prop.name}' cannot be null, please specify non-null value or use nullable " +
                        "annotation to decorate this property",
                )
            } else {
                null
            },
            steps = steps,
        )
    }

    private fun MutableList<ImmutableDraftValidationStep>.addNotEmpty(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotation = annotationsByName["NotEmpty"]?.firstOrNull() ?: return
        if (!prop.isString() && !prop.list) {
            throw invalidType(prop, annotation, "its type is neither string nor list")
        }
        this += ImmutableDraftValidationStep.NotEmpty(
            sourceAnnotationTypeId = annotation.type,
            sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
            failure = annotation.failure("it cannot be empty", skipWhenNull),
        )
    }

    private fun MutableList<ImmutableDraftValidationStep>.addNotBlank(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotation = annotationsByName["NotBlank"]?.firstOrNull() ?: return
        if (!prop.isString()) {
            throw invalidType(prop, annotation, "its type is not string")
        }
        this += ImmutableDraftValidationStep.NotBlank(
            sourceAnnotationTypeId = annotation.type,
            sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
            failure = annotation.failure("it cannot be empty", skipWhenNull),
        )
    }

    private fun MutableList<ImmutableDraftValidationStep>.addSize(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotations = annotationsByName["Size"].orEmpty()
        if (annotations.isEmpty()) {
            return
        }
        if (!prop.isString() && !prop.list) {
            throw invalidType(prop, annotations.first(), "its type is neither string nor list")
        }
        var minimum: IntCandidate? = null
        var maximum: IntCandidate? = null
        annotations.forEach { annotation ->
            val min = annotation.intArgument(prop, "min", 0)
            val max = annotation.intArgument(prop, "max", Int.MAX_VALUE)
            if (minimum == null || min > requireNotNull(minimum).value) {
                minimum = IntCandidate(min, annotation)
            }
            if (maximum == null || max < requireNotNull(maximum).value) {
                maximum = IntCandidate(max, annotation)
            }
        }
        val min = requireNotNull(minimum)
        val max = requireNotNull(maximum)
        if (min.value > max.value) {
            throw invalid(prop, "its size validation rules are illegal because there is no valid length")
        }
        val measure = if (prop.isString()) {
            ImmutableDraftSizeMeasure.LENGTH
        } else {
            ImmutableDraftSizeMeasure.SIZE
        }
        if (min.value > 0) {
            this += ImmutableDraftValidationStep.Size(
                sourceAnnotationTypeId = min.annotation.type,
                sourceAnnotationUseSiteTarget = min.annotation.useSiteTarget,
                measure = measure,
                comparison = ImmutableDraftComparison.LESS_THAN,
                limit = min.value,
                failure = min.annotation.failure(
                    defaultMessage = "it cannot be less than ${min.value}",
                    skipWhenNull = skipWhenNull,
                ),
            )
        }
        if (max.value < Int.MAX_VALUE) {
            this += ImmutableDraftValidationStep.Size(
                sourceAnnotationTypeId = max.annotation.type,
                sourceAnnotationUseSiteTarget = max.annotation.useSiteTarget,
                measure = measure,
                comparison = ImmutableDraftComparison.GREATER_THAN,
                limit = max.value,
                failure = max.annotation.failure(
                    defaultMessage = "it cannot be greater than ${max.value}",
                    skipWhenNull = skipWhenNull,
                ),
            )
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addBounds(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val boundAnnotations = BOUND_ANNOTATION_NAMES.flatMap { name -> annotationsByName[name].orEmpty() }
        if (boundAnnotations.isEmpty()) {
            return
        }
        val target = prop.numericTarget()
            ?: throw invalidType(prop, boundAnnotations.first(), "its type is not numeric")
        var minimum: DecimalCandidate? = null
        var maximum: DecimalCandidate? = null

        fun acceptMinimum(value: BigDecimal, annotation: LsiAnnotation) {
            if (minimum == null || value > requireNotNull(minimum).value) {
                minimum = DecimalCandidate(value, annotation)
            }
        }

        fun acceptMaximum(value: BigDecimal, annotation: LsiAnnotation) {
            if (maximum == null || value < requireNotNull(maximum).value) {
                maximum = DecimalCandidate(value, annotation)
            }
        }

        annotationsByName["Min"].orEmpty().forEach { annotation ->
            acceptMinimum(BigDecimal.valueOf(annotation.longArgument(prop, "value", 0L)), annotation)
        }
        annotationsByName["DecimalMin"].orEmpty().forEach { annotation ->
            annotation.booleanArgument(prop, "inclusive", true)
            acceptMinimum(annotation.decimalArgument(prop, "value", "0"), annotation)
        }
        annotationsByName["Positive"].orEmpty().forEach { annotation ->
            acceptMinimum(BigDecimal.ONE, annotation)
        }
        annotationsByName["PositiveOrZero"].orEmpty().forEach { annotation ->
            acceptMinimum(BigDecimal.ZERO, annotation)
        }
        annotationsByName["Max"].orEmpty().forEach { annotation ->
            acceptMaximum(BigDecimal.valueOf(annotation.longArgument(prop, "value", 0L)), annotation)
        }
        annotationsByName["DecimalMax"].orEmpty().forEach { annotation ->
            annotation.booleanArgument(prop, "inclusive", true)
            acceptMaximum(annotation.decimalArgument(prop, "value", "0"), annotation)
        }
        annotationsByName["Negative"].orEmpty().forEach { annotation ->
            acceptMaximum(BigDecimal.ONE.negate(), annotation)
        }
        annotationsByName["NegativeOrZero"].orEmpty().forEach { annotation ->
            acceptMaximum(BigDecimal.ZERO, annotation)
        }
        val min = minimum
        val max = maximum
        if (min != null && max != null && min.value > max.value) {
            throw invalid(prop, "its numeric range validation rules are illegal because there is no valid number")
        }
        if (min != null) {
            val bound = min.value.toString()
            this += ImmutableDraftValidationStep.NumericBound(
                sourceAnnotationTypeId = min.annotation.type,
                sourceAnnotationUseSiteTarget = min.annotation.useSiteTarget,
                target = target,
                comparison = ImmutableDraftComparison.LESS_THAN,
                bound = bound,
                failure = min.annotation.failure(
                    defaultMessage = "it cannot be less than $bound",
                    skipWhenNull = skipWhenNull,
                ),
            )
        }
        if (max != null) {
            val bound = max.value.toString()
            this += ImmutableDraftValidationStep.NumericBound(
                sourceAnnotationTypeId = max.annotation.type,
                sourceAnnotationUseSiteTarget = max.annotation.useSiteTarget,
                target = target,
                comparison = ImmutableDraftComparison.GREATER_THAN,
                bound = bound,
                failure = max.annotation.failure(
                    defaultMessage = "it cannot be greater than $bound",
                    skipWhenNull = skipWhenNull,
                ),
            )
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addEmail(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotation = annotationsByName["Email"]?.firstOrNull() ?: return
        if (!prop.isString()) {
            throw invalidType(prop, annotation, "its type is not string")
        }
        this += ImmutableDraftValidationStep.Email(
            sourceAnnotationTypeId = annotation.type,
            sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
            failure = annotation.failure("it is not email address", skipWhenNull),
        )
    }

    private fun MutableList<ImmutableDraftValidationStep>.addPatterns(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotations = annotationsByName["Pattern"].orEmpty()
        if (annotations.isEmpty()) {
            return
        }
        if (!prop.isString()) {
            throw invalidType(prop, annotations.first(), "its type is not string")
        }
        annotations.forEach { annotation ->
            val regexp = annotation.stringArgument(prop, "regexp", null)
                ?: throw invalidArgument(prop, annotation, "regexp", "a string value")
            val flags = annotation.patternFlags(prop)
            this += ImmutableDraftValidationStep.Pattern(
                sourceAnnotationTypeId = annotation.type,
                sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                regexp = regexp,
                flags = flags,
                failure = annotation.failure(
                    defaultMessage = "it does not match the regexp '${regexp.replace("\\", "\\\\")}'",
                    skipWhenNull = skipWhenNull,
                ),
            )
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addCustomValidators(
        prop: ImmutableProp,
        workspace: LsiWorkspace,
    ) {
        val validationKeys = mutableSetOf<Pair<LsiSymbolId, LsiAnnotationUseSiteTarget?>>()
        prop.validations.forEach { validation ->
            val validationKey = validation.annotationTypeId to validation.sourceAnnotationUseSiteTarget
            if (!validationKeys.add(validationKey)) {
                throw invalid(prop, "duplicated validation annotation '${validation.annotationTypeId.value}'")
            }
            validateTypeSymbol(prop, validation.annotationTypeId, "validation annotation", workspace)
            if (validation.validatorTypeIds.isEmpty()) {
                throw invalid(
                    prop,
                    "validation annotation '${validation.annotationTypeId.value}' has no validator type",
                )
            }
            validation.validatorTypeIds.forEach { validatorTypeId ->
                validateTypeSymbol(prop, validatorTypeId, "validator", workspace)
            }
            this += ImmutableDraftValidationStep.CustomValidator(
                annotationTypeId = validation.annotationTypeId,
                validatorTypeIds = validation.validatorTypeIds,
                message = validation.message,
                sourceAnnotationUseSiteTarget = validation.sourceAnnotationUseSiteTarget,
            )
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addAsserts(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val falseAnnotations = annotationsByName["AssertFalse"].orEmpty()
        val trueAnnotations = annotationsByName["AssertTrue"].orEmpty()
        val firstAnnotation = falseAnnotations.firstOrNull() ?: trueAnnotations.firstOrNull() ?: return
        if (!prop.isBoolean()) {
            throw invalidType(prop, firstAnnotation, "its type is not boolean")
        }
        falseAnnotations.forEach { annotation ->
            this += ImmutableDraftValidationStep.Assert(
                sourceAnnotationTypeId = annotation.type,
                sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                expected = false,
                failure = annotation.failure("it is not false", skipWhenNull),
            )
        }
        trueAnnotations.forEach { annotation ->
            this += ImmutableDraftValidationStep.Assert(
                sourceAnnotationTypeId = annotation.type,
                sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                expected = true,
                failure = annotation.failure("it is not true", skipWhenNull),
            )
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addDigits(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotations = annotationsByName["Digits"].orEmpty()
        if (annotations.isEmpty()) {
            return
        }
        val target = prop.digitsTarget()
            ?: throw invalidType(
                prop,
                annotations.first(),
                "its type is not primitive, boxed primitive, BigDecimal, BigInteger or CharSequence",
            )
        annotations.forEach { annotation ->
            val integer = annotation.intArgument(prop, "integer", 0)
            val fraction = annotation.intArgument(prop, "fraction", 0)
            if (integer < 0 || fraction < 0 || integer == 0 && fraction == 0) {
                throw invalid(prop, "its digits validation rules are illegal because there is no valid number")
            }
            this += ImmutableDraftValidationStep.Digits(
                sourceAnnotationTypeId = annotation.type,
                sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                target = target,
                component = ImmutableDraftDigitsComponent.INTEGER,
                limit = integer,
                failure = annotation.failure(
                    defaultMessage = "its integer digits is greater than $integer",
                    skipWhenNull = skipWhenNull,
                ),
            )
            if (target == ImmutableDraftDigitsTarget.BIG_DECIMAL) {
                this += ImmutableDraftValidationStep.Digits(
                    sourceAnnotationTypeId = annotation.type,
                    sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                    target = target,
                    component = ImmutableDraftDigitsComponent.FRACTION,
                    limit = fraction,
                    failure = annotation.failure(
                        defaultMessage = "its fraction digits is greater than $fraction",
                        skipWhenNull = skipWhenNull,
                    ),
                )
            }
        }
    }

    private fun MutableList<ImmutableDraftValidationStep>.addTemporal(
        prop: ImmutableProp,
        annotationsByName: Map<String, List<LsiAnnotation>>,
        skipWhenNull: Boolean,
    ) {
        val annotations = TEMPORAL_ANNOTATIONS.flatMap { (name, _) -> annotationsByName[name].orEmpty() }
        if (annotations.isEmpty()) {
            return
        }
        val target = prop.temporalTarget()
            ?: throw invalidType(
                prop,
                annotations.first(),
                "its type is not LocalDate, LocalDateTime, LocalTime or Instant",
            )
        TEMPORAL_ANNOTATIONS.forEach { (name, contract) ->
            annotationsByName[name].orEmpty().forEach { annotation ->
                this += ImmutableDraftValidationStep.Temporal(
                    sourceAnnotationTypeId = annotation.type,
                    sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
                    target = target,
                    constraint = contract.constraint,
                    failure = annotation.failure(contract.defaultMessage, skipWhenNull),
                )
            }
        }
    }

    private fun validateAnnotationDeclaration(
        prop: ImmutableProp,
        annotation: LsiAnnotation,
        workspace: LsiWorkspace,
    ) {
        val declaration = workspace[annotation.type] ?: return
        if (declaration !is LsiTypeDeclaration || declaration.kind != LsiTypeDeclarationKind.ANNOTATION) {
            throw invalid(prop, "'${annotation.type.requireTypeQualifiedName()}' is not an annotation type")
        }
    }

    private fun validateTypeSymbol(
        prop: ImmutableProp,
        typeId: LsiSymbolId,
        role: String,
        workspace: LsiWorkspace,
    ) {
        val declaration = workspace[typeId] ?: return
        if (declaration !is LsiTypeDeclaration) {
            throw invalid(prop, "$role '${typeId.value}' is not a type declaration")
        }
    }

    private fun invalidType(
        prop: ImmutableProp,
        annotation: LsiAnnotation,
        message: String,
    ): ImmutablePrecompileException {
        return invalid(
            prop,
            "it is decorated by @${annotation.type.requireTypeQualifiedName()} but $message",
        )
    }

    private fun invalidArgument(
        prop: ImmutableProp,
        annotation: LsiAnnotation,
        name: String,
        expected: String,
    ): ImmutablePrecompileException {
        return invalid(
            prop,
            "annotation @${annotation.type.requireTypeQualifiedName()} argument '$name' must be $expected",
        )
    }

    private fun invalid(
        prop: ImmutableProp,
        message: String,
    ): ImmutablePrecompileException {
        return ImmutablePrecompileException(
            declarationId = prop.declarationId,
            message = "Immutable property '${prop.id.value}' $message",
        )
    }

    private fun LsiAnnotation.failure(
        defaultMessage: String,
        skipWhenNull: Boolean,
    ): ImmutableDraftValidationFailure {
        return ImmutableDraftValidationFailure(
            exceptionTypeId = if (type.requireTypeQualifiedName().startsWith(JAVAX_VALIDATION_PREFIX)) {
                JAVAX_VALIDATION_EXCEPTION
            } else {
                JAKARTA_VALIDATION_EXCEPTION
            },
            declaredMessage = stringArgumentOrEmpty("message"),
            defaultMessage = defaultMessage,
            skipWhenNull = skipWhenNull,
        )
    }

    private fun LsiAnnotation.patternFlags(
        prop: ImmutableProp,
    ): List<ImmutableDraftPatternFlag> {
        val value = arguments["flags"]?.value ?: return emptyList()
        val elements = (value as? LsiAnnotationValue.ArrayValue)?.elements
            ?: throw invalidArgument(prop, this, "flags", "an enum array")
        return elements.map { element ->
            val enumValue = element as? LsiAnnotationValue.EnumValue
                ?: throw invalidArgument(prop, this, "flags", "an enum array")
            ImmutableDraftPatternFlag.entries.firstOrNull { flag -> flag.name == enumValue.entryName }
                ?: throw invalid(
                    prop,
                    "annotation @${type.requireTypeQualifiedName()} argument 'flags' contains unsupported value " +
                        "'${enumValue.entryName}'",
                )
        }
    }

    private fun LsiAnnotation.stringArgument(
        prop: ImmutableProp,
        name: String,
        defaultValue: String?,
    ): String? {
        val value = arguments[name]?.value ?: return defaultValue
        return (value as? LsiAnnotationValue.StringValue)?.value
            ?: throw invalidArgument(prop, this, name, "a string value")
    }

    private fun LsiAnnotation.stringArgumentOrEmpty(name: String): String {
        return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value.orEmpty()
    }

    private fun LsiAnnotation.intArgument(
        prop: ImmutableProp,
        name: String,
        defaultValue: Int,
    ): Int {
        val value = arguments[name]?.value ?: return defaultValue
        return when (value) {
            is LsiAnnotationValue.ByteValue -> value.value.toInt()
            is LsiAnnotationValue.ShortValue -> value.value.toInt()
            is LsiAnnotationValue.IntValue -> value.value
            is LsiAnnotationValue.LongValue -> value.value.toInt().takeIf { converted ->
                converted.toLong() == value.value
            } ?: throw invalidArgument(prop, this, name, "an integer value")
            else -> throw invalidArgument(prop, this, name, "an integer value")
        }
    }

    private fun LsiAnnotation.longArgument(
        prop: ImmutableProp,
        name: String,
        defaultValue: Long,
    ): Long {
        val value = arguments[name]?.value ?: return defaultValue
        return when (value) {
            is LsiAnnotationValue.ByteValue -> value.value.toLong()
            is LsiAnnotationValue.ShortValue -> value.value.toLong()
            is LsiAnnotationValue.IntValue -> value.value.toLong()
            is LsiAnnotationValue.LongValue -> value.value
            else -> throw invalidArgument(prop, this, name, "an integer value")
        }
    }

    private fun LsiAnnotation.booleanArgument(
        prop: ImmutableProp,
        name: String,
        defaultValue: Boolean,
    ): Boolean {
        val value = arguments[name]?.value ?: return defaultValue
        return (value as? LsiAnnotationValue.BooleanValue)?.value
            ?: throw invalidArgument(prop, this, name, "a boolean value")
    }

    private fun LsiAnnotation.decimalArgument(
        prop: ImmutableProp,
        name: String,
        defaultValue: String,
    ): BigDecimal {
        val text = stringArgument(prop, name, defaultValue) ?: defaultValue
        return try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            throw invalidArgument(prop, this, name, "a decimal string")
        }
    }
}

private data class IntCandidate(
    val value: Int,
    val annotation: LsiAnnotation,
)

private data class DecimalCandidate(
    val value: BigDecimal,
    val annotation: LsiAnnotation,
)

private data class TemporalContract(
    val constraint: ImmutableDraftTemporalConstraint,
    val defaultMessage: String,
)

private fun LsiAnnotation.isBuiltInValidationAnnotation(): Boolean {
    return builtInValidationName() in BUILT_IN_VALIDATION_NAMES
}

private fun LsiAnnotation.builtInValidationName(): String {
    val qualifiedName = type.requireTypeQualifiedName()
    return when {
        qualifiedName.startsWith(JAVAX_CONSTRAINT_PREFIX) -> {
            qualifiedName.removePrefix(JAVAX_CONSTRAINT_PREFIX)
        }
        qualifiedName.startsWith(JAKARTA_CONSTRAINT_PREFIX) -> {
            qualifiedName.removePrefix(JAKARTA_CONSTRAINT_PREFIX)
        }
        else -> ""
    }
}

private fun ImmutableProp.isUnboxedPrimitive(): Boolean {
    val propType = type
    return propType is LsiPrimitiveType && !propType.boxed
}

private fun ImmutableProp.isString(): Boolean {
    return type.declarationIdOrNull() == STRING_TYPE
}

private fun ImmutableProp.isBoolean(): Boolean {
    return (type as? LsiPrimitiveType)?.kind == LsiPrimitiveKind.BOOLEAN
}

private fun ImmutableProp.numericTarget(): ImmutableDraftNumericTarget? {
    val primitive = type as? LsiPrimitiveType
    if (primitive != null && primitive.kind in NUMERIC_PRIMITIVE_KINDS) {
        return ImmutableDraftNumericTarget.PRIMITIVE
    }
    return when (type.declarationIdOrNull()) {
        BIG_INTEGER_TYPE -> ImmutableDraftNumericTarget.BIG_INTEGER
        BIG_DECIMAL_TYPE -> ImmutableDraftNumericTarget.BIG_DECIMAL
        else -> null
    }
}

private fun ImmutableProp.digitsTarget(): ImmutableDraftDigitsTarget? {
    val propType = type
    if (propType is LsiPrimitiveType && propType.kind !in NON_VALUE_PRIMITIVE_KINDS) {
        return ImmutableDraftDigitsTarget.PRIMITIVE
    }
    return when (type.declarationIdOrNull()) {
        BIG_INTEGER_TYPE -> ImmutableDraftDigitsTarget.BIG_INTEGER
        BIG_DECIMAL_TYPE -> ImmutableDraftDigitsTarget.BIG_DECIMAL
        CHAR_SEQUENCE_TYPE -> ImmutableDraftDigitsTarget.CHAR_SEQUENCE
        else -> null
    }
}

private fun ImmutableProp.temporalTarget(): ImmutableDraftTemporalTarget? {
    return when (type.declarationIdOrNull()) {
        LOCAL_DATE_TYPE -> ImmutableDraftTemporalTarget.LOCAL_DATE
        LOCAL_DATE_TIME_TYPE -> ImmutableDraftTemporalTarget.LOCAL_DATE_TIME
        LOCAL_TIME_TYPE -> ImmutableDraftTemporalTarget.LOCAL_TIME
        INSTANT_TYPE -> ImmutableDraftTemporalTarget.INSTANT
        else -> null
    }
}

private fun site.addzero.lsi.model.LsiTypeRef.declarationIdOrNull(): LsiSymbolId? {
    return (this as? LsiDeclaredType)?.declarationId
}

private const val JAVAX_VALIDATION_PREFIX = "javax.validation."

private const val JAVAX_CONSTRAINT_PREFIX = "javax.validation.constraints."

private const val JAKARTA_CONSTRAINT_PREFIX = "jakarta.validation.constraints."

private val JAVAX_VALIDATION_EXCEPTION = LsiSymbolId.type("javax.validation.ValidationException")

private val JAKARTA_VALIDATION_EXCEPTION = LsiSymbolId.type("jakarta.validation.ValidationException")

private val STRING_TYPE = LsiSymbolId.type("java.lang.String")

private val CHAR_SEQUENCE_TYPE = LsiSymbolId.type("java.lang.CharSequence")

private val BIG_INTEGER_TYPE = LsiSymbolId.type("java.math.BigInteger")

private val BIG_DECIMAL_TYPE = LsiSymbolId.type("java.math.BigDecimal")

private val LOCAL_DATE_TYPE = LsiSymbolId.type("java.time.LocalDate")

private val LOCAL_DATE_TIME_TYPE = LsiSymbolId.type("java.time.LocalDateTime")

private val LOCAL_TIME_TYPE = LsiSymbolId.type("java.time.LocalTime")

private val INSTANT_TYPE = LsiSymbolId.type("java.time.Instant")

private val NUMERIC_PRIMITIVE_KINDS = setOf(
    LsiPrimitiveKind.BYTE,
    LsiPrimitiveKind.SHORT,
    LsiPrimitiveKind.INT,
    LsiPrimitiveKind.LONG,
    LsiPrimitiveKind.FLOAT,
    LsiPrimitiveKind.DOUBLE,
)

private val NON_VALUE_PRIMITIVE_KINDS = setOf(
    LsiPrimitiveKind.UNIT,
    LsiPrimitiveKind.VOID,
)

private val BOUND_ANNOTATION_NAMES = listOf(
    "Min",
    "Max",
    "Positive",
    "PositiveOrZero",
    "Negative",
    "NegativeOrZero",
    "DecimalMin",
    "DecimalMax",
)

private val TEMPORAL_ANNOTATIONS = listOf(
    "PastOrPresent" to TemporalContract(
        ImmutableDraftTemporalConstraint.PAST_OR_PRESENT,
        "it is not before or equal to now",
    ),
    "Past" to TemporalContract(
        ImmutableDraftTemporalConstraint.PAST,
        "it is not before now",
    ),
    "FutureOrPresent" to TemporalContract(
        ImmutableDraftTemporalConstraint.FUTURE_OR_PRESENT,
        "it is not after or equal to now",
    ),
    "Future" to TemporalContract(
        ImmutableDraftTemporalConstraint.FUTURE,
        "it is not after now",
    ),
)

private val BUILT_IN_VALIDATION_NAMES = setOf(
    "NotEmpty",
    "NotBlank",
    "Size",
    "Min",
    "Max",
    "Positive",
    "PositiveOrZero",
    "Negative",
    "NegativeOrZero",
    "DecimalMin",
    "DecimalMax",
    "Email",
    "Pattern",
    "AssertFalse",
    "AssertTrue",
    "Digits",
    "PastOrPresent",
    "Past",
    "FutureOrPresent",
    "Future",
)
