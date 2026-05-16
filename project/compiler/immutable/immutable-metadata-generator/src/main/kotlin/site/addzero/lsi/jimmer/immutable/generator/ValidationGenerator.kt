package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.*

import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.anno.get
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCollectionSizeExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLengthExpression
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.isBuiltInType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KClass

private val BIG_DECIMAL_LSI_CLASS_NAME = LsiClassName.bestGuess(BigDecimal::class.java.name)
private val BIG_INTEGER_LSI_CLASS_NAME = LsiClassName.bestGuess(BigInteger::class.java.name)
private val LOCAL_DATE_LSI_CLASS_NAME = LsiClassName.bestGuess(LocalDate::class.java.name)
private val LOCAL_DATE_TIME_LSI_CLASS_NAME = LsiClassName.bestGuess(LocalDateTime::class.java.name)
private val LOCAL_TIME_LSI_CLASS_NAME = LsiClassName.bestGuess(LocalTime::class.java.name)
private val INSTANT_LSI_CLASS_NAME = LsiClassName.bestGuess(java.time.Instant::class.java.name)
private val JAVAX_VALIDATION_EXCEPTION_LSI_CLASS_NAME =
  LsiClassName.bestGuess("javax.validation.ValidationException")
private val JAKARTA_VALIDATION_EXCEPTION_LSI_CLASS_NAME =
  LsiClassName.bestGuess("jakarta.validation.ValidationException")

class ValidationGenerator(
  private val prop: ImmutableValidationPropMetadata,
) {
  private val annoMultiMap: Map<String, List<LsiAnnotation>> =
    prop.validationAnnotationMirrorMultiMap
  private val statements = mutableListOf<LsiStatement>()

  fun generate(): List<LsiStatement> {
    generateNotEmpty()
    generateNotBlank()
    generateSize()
    generateBound()
    generateEmail()
    generatePattern()
    generateConstraints()
    generateAssert()
    generateDigits()
    generateTime()
    return statements.toList()
  }

  private fun generateNotEmpty() {
    val notEmpty = annoMultiMap["NotEmpty"]?.get(0) ?: return
    if (!isSimpleType(String::class) && !isSimpleType(List::class)) {
      // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../generator/ValidationGenerator 各类校验异常锚点
      // 迁移说明：ValidationGenerator 的异常锚点由 KSPropertyDeclaration 统一切换为 LSI field
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            notEmpty.fullName +
            " but its type is neither string nor list"
      )
    }
    validate(
      call(propExpression(), "isEmpty"),
      notEmpty["message"]
    ) { "it cannot be empty" }
  }

  private fun generateNotBlank() {
    val notBlank = annoMultiMap["NotBlank"]?.get(0) ?: return
    if (!isSimpleType(String::class)) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            notBlank.fullName +
            " but its type is not string"
      )
    }
    validate(
      call(
        call(propExpression(), "trim"),
        "isEmpty",
      ),
      notBlank["message"]
    ) { "it cannot be empty" }
  }

  private fun generateSize() {
    val sizes = annoMultiMap["Size"] ?: emptyList()
    if (sizes.isEmpty()) {
      return
    }
    if (!isSimpleType(String::class) && !isSimpleType(List::class)) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            sizes[0].fullName +
            " but its type is neither string nor list"
      )
    }
    var min = 0
    var max = Int.MAX_VALUE
    var minMessage: String? = null
    var maxMessage: String? = null
    for (size in sizes) {
      val sizeMin: Int = size["min"]!!
      if (sizeMin > min) {
        min = sizeMin
        minMessage = size["message"]
      }
      val sizeMax: Int = size["max"]!!
      if (sizeMax < max) {
        max = sizeMax
        maxMessage = size["message"]
      }
    }
    if (min > max) {
      throw MetaException(
        prop.lsiField,
        "its size validation rules is illegal " +
            "so that there is not valid length"
      )
    }
    if (min == 0 && max == Int.MAX_VALUE) {
      return
    }
    val sizeExpression =
      if (isSimpleType(String::class)) {
        LsiLengthExpression(propExpression())
      } else {
        LsiCollectionSizeExpression(propExpression())
      }
    if (min > 0) {
      val finalValue = min
      validate(
        compare(
          sizeExpression,
          LsiBinaryOperator.LESS_THAN,
          LsiLiteralExpression(finalValue),
        ),
        minMessage
      ) { "it cannot be less than $finalValue" }
    }
    if (max < Int.MAX_VALUE) {
      val finalValue = max
      validate(
        compare(
          sizeExpression,
          LsiBinaryOperator.GREATER_THAN,
          LsiLiteralExpression(finalValue),
        ),
        maxMessage
      ) { "it cannot be greater than $finalValue" }
    }
  }

  private fun generateBound() {
    val minList = annoMultiMap["Min"] ?: emptyList()
    val maxList = annoMultiMap["Max"] ?: emptyList()
    val positives = annoMultiMap["Positive"] ?: emptyList()
    val positiveOrZeros = annoMultiMap["PositiveOrZero"] ?: emptyList()
    val negatives = annoMultiMap["Negative"] ?: emptyList()
    val negativeOrZeros = annoMultiMap["NegativeOrZero"] ?: emptyList()
    val decimalMinList = annoMultiMap["DecimalMin"] ?: emptyList()
    val decimalMaxList = annoMultiMap["DecimalMax"] ?: emptyList()
    val annotations = listOf(
      minList, maxList,
      positives, positiveOrZeros,
      negatives, negativeOrZeros,
      decimalMinList, decimalMaxList
    ).flatten()
    if (annotations.isEmpty()) {
      return
    }
    if (!isSimpleType(Byte::class) &&
      !isSimpleType(Short::class) &&
      !isSimpleType(Int::class) &&
      !isSimpleType(Long::class) &&
      !isSimpleType(Float::class) &&
      !isSimpleType(Double::class) &&
      !isSimpleType(BigInteger::class) &&
      !isSimpleType(BigDecimal::class)
    ) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            annotations[0].fullName +
            " but its type is not numeric"
      )
    }
    var minValue: BigDecimal? = null
    var maxValue: BigDecimal? = null
    var message: String? = null
    for (min in minList) {
      val annoValue: Long = min["value"]!!
      if (minValue == null || BigDecimal(annoValue) > minValue) {
        minValue = BigDecimal(annoValue)
        message = min["message"]
      }
    }
    for (decimalMin in decimalMinList) {
      val annoValue: String = decimalMin["value"]!!
      val value = BigDecimal(annoValue)
      if (minValue == null || value > minValue) {
        minValue = value
        message = decimalMin["message"]
      }
    }
    for (positive in positives) {
      if (minValue == null || BigDecimal.ONE > minValue) {
        minValue = BigDecimal.ONE
        message = positive["message"]
      }
    }
    for (positiveOrZero in positiveOrZeros) {
      if (minValue == null || BigDecimal.ZERO > minValue) {
        minValue = BigDecimal.ZERO
        message = positiveOrZero["message"]
      }
    }
    for (max in maxList) {
      val annoValue: Long = max["value"]!!
      if (maxValue == null || BigDecimal(annoValue) < maxValue) {
        maxValue = BigDecimal(annoValue)
        message = max["message"]
      }
    }
    for (decimalMax in decimalMaxList) {
      val annoValue: String = decimalMax["value"]!!
      val value = BigDecimal(annoValue)
      if (maxValue == null || value < maxValue) {
        maxValue = value
        message = decimalMax["message"]
      }
    }
    for (negative in negatives) {
      if (maxValue == null || BigDecimal.ONE.negate() < maxValue) {
        maxValue = BigDecimal.ONE.negate()
        message = negative["message"]
      }
    }
    for (negativeOrZero in negativeOrZeros) {
      if (maxValue == null || BigDecimal.ZERO < maxValue) {
        maxValue = BigDecimal.ZERO
        message = negativeOrZero["message"]
      }
    }
    if ((minValue != null) && (maxValue != null) && (minValue > maxValue)) {
      throw MetaException(
        prop.lsiField,
        "its numeric range validation rules is illegal " +
            "so that there is not valid number"
      )
    }
    if (minValue != null) {
      validateBound(minValue, "<", message)
    }
    if (maxValue != null) {
      validateBound(maxValue, ">", message)
    }
  }

  private fun generateEmail() {
    val email = annoMultiMap["Email"]?.get(0) ?: return
    if (!isSimpleType(String::class)) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            email.fullName +
            " but its type is not string"
      )
    }
    validate(
      isFalse(
        call(
          call(
            LsiNameExpression(DRAFT_FIELD_EMAIL_PATTERN),
            "matcher",
            propExpression(),
          ),
          "matches",
        ),
      ),
      email["message"]
    ) { "it is not email address" }
  }

  private fun generatePattern() {
    val patterns = annoMultiMap["Pattern"] ?: return
    if (!isSimpleType(String::class)) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            patterns[0].fullName +
            " but its type is not string"
      )
    }
    for (i in patterns.indices) {
      validate(
        isFalse(
          call(
            call(
              LsiNameExpression(regexpPatternFieldName(prop.name, i)),
              "matcher",
              propExpression(),
            ),
            "matches",
          ),
        ),
        patterns[i]["message"],
      ) {
        ("it does not match the regexp '" +
            patterns[i].get<String>("regexp")!!.replace("\\", "\\\\") +
            "'")
      }
    }
  }

  private fun generateConstraints() {
    for (e in prop.validationMessages) {
      statements +=
        LsiExpressionStatement(
          LsiCallExpression(
            receiver = LsiNameExpression(validatorFieldName(prop.name, e.key)),
            name = "validate",
            arguments = listOf(LsiNameExpression(prop.name)),
          )
        )
    }
  }

  private fun validationExceptionClassName(): LsiClassName =
    annoMultiMap.values.flatten().first().let {
      if (it.fullName.startsWith("javax.validation")) {
        JAVAX_VALIDATION_EXCEPTION_LSI_CLASS_NAME
      } else {
        JAKARTA_VALIDATION_EXCEPTION_LSI_CLASS_NAME
      }
    }

  private fun validationMessageExpression(
    defaultMessage: String,
  ): LsiExpression =
    stringConcat(
      LsiLiteralExpression("Illegal value'"),
      LsiNameExpression(prop.name),
      LsiLiteralExpression("'for property '${prop}', "),
      LsiLiteralExpression(defaultMessage),
    )

  private fun stringConcat(vararg expressions: LsiExpression): LsiExpression =
    expressions.reduce { left, right ->
      LsiBinaryExpression(
        left = left,
        operator = LsiBinaryOperator.PLUS,
        right = right,
      )
    }

  private fun propExpression(): LsiNameExpression =
    LsiNameExpression(prop.name)

  private fun call(
    receiver: LsiExpression,
    name: String,
    vararg arguments: LsiExpression,
  ): LsiCallExpression =
    LsiCallExpression(
      receiver = receiver,
      name = name,
      arguments = arguments.toList(),
    )

  private fun nowExpression(type: KClass<*>): LsiExpression =
    call(
      receiver = LsiTypeExpression(type.toLsiClassName()),
      name = "now",
    )

  private fun compare(
    left: LsiExpression,
    operator: LsiBinaryOperator,
    right: LsiExpression,
  ): LsiBinaryExpression =
    LsiBinaryExpression(
      left = left,
      operator = operator,
      right = right,
    )

  private fun or(
    left: LsiExpression,
    right: LsiExpression,
  ): LsiExpression =
    compare(left, LsiBinaryOperator.OR, right)

  private fun isFalse(expression: LsiExpression): LsiExpression =
    compare(
      expression,
      LsiBinaryOperator.EQUALS,
      LsiLiteralExpression(false),
    )

  private fun generateAssert() {
    val assertFalseList = annoMultiMap["AssertFalse"] ?: emptyList()
    val assertTrueList = annoMultiMap["AssertTrue"] ?: emptyList()

    val annotations = listOf(assertFalseList, assertTrueList).flatten()

    if (annotations.isEmpty()) {
      return
    }

    if (!isSimpleType(Boolean::class)) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            annotations[0].fullName +
            " but its type is not boolean"
      )
    }

    for (assertFalse in assertFalseList) {
      validate(
        compare(
          propExpression(),
          LsiBinaryOperator.NOT_EQUALS,
          LsiLiteralExpression(false),
        ),
        assertFalse["message"],
      ) { "it is not false" }
    }

    for (assertTrue in assertTrueList) {
      validate(
        compare(
          propExpression(),
          LsiBinaryOperator.NOT_EQUALS,
          LsiLiteralExpression(true),
        ),
        assertTrue["message"],
      ) { "it is not true" }
    }
  }

  private fun generateDigits() {
    val digits = annoMultiMap["Digits"]?.get(0) ?: return

    if (!prop.lsiTypeName().isBuiltInType()
      && !isSimpleType(BigDecimal::class)
      && !isSimpleType(BigInteger::class)
      && !isSimpleType(CharSequence::class)
    ) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            digits.fullName +
            " but its type is not BigDecimal"
      )
    }

    val integer = digits["integer"] ?: 0
    val fraction = digits["fraction"] ?: 0

    if (integer < 0 || fraction < 0) {
      throw MetaException(
        prop.lsiField,
        "its numeric range validation rules is illegal " +
            "so that there is not valid number"
      )
    }

    if (integer == 0 && fraction == 0) {
      throw MetaException(
        prop.lsiField,
        "its numeric range validation rules is illegal " +
            "so that there is not valid number"
      )
    }

    if (prop.lsiTypeName(overrideNullable = false) == BIG_DECIMAL_LSI_CLASS_NAME) {
      if (integer > 0) {
        validate(
          compare(
            call(propExpression(), "precision"),
            LsiBinaryOperator.GREATER_THAN,
            LsiLiteralExpression(integer),
          ),
          digits["message"],
        ) { "it's precision is less than $integer" }
      }
      if (fraction > 0) {
        validate(
          compare(
            call(propExpression(), "scale"),
            LsiBinaryOperator.GREATER_THAN,
            LsiLiteralExpression(fraction),
          ),
          digits["message"],
        ) { "it's scale is less than $fraction" }
      }
    } else if (prop.lsiTypeName(overrideNullable = false) == BIG_INTEGER_LSI_CLASS_NAME) {
      validate(
        compare(
          call(propExpression(), "precision"),
          LsiBinaryOperator.GREATER_THAN,
          LsiLiteralExpression(integer),
        ),
        digits["message"],
      ) { "it's precision is less than $integer" }
    } else {
      validate(
        compare(
          LsiLengthExpression(call(propExpression(), "toString")),
          LsiBinaryOperator.GREATER_THAN,
          LsiLiteralExpression(integer + fraction),
        ),
        digits["message"],
      ) { "it's length is less than ${integer + fraction}" }
    }
  }

  private fun generateTime() {
    val pastOrPresents = annoMultiMap["PastOrPresent"] ?: emptyList()
    val pasts = annoMultiMap["Past"] ?: emptyList()
    val futureOrPresents = annoMultiMap["FutureOrPresent"] ?: emptyList()
    val futures = annoMultiMap["Future"] ?: emptyList()

    val annotations = listOf(pastOrPresents, pasts, futureOrPresents, futures).flatten()

    if (annotations.isEmpty()) {
      return
    }

    if (!isSimpleType(LocalDate::class)
      && !isSimpleType(LocalDateTime::class)
      && !isSimpleType(LocalTime::class)
      && !isSimpleType(java.time.Instant::class)
    ) {
      throw MetaException(
        prop.lsiField,
        "it's decorated by the annotation @" +
            annotations[0].fullName +
            " but its type is not date or time"
      )
    }

    for (pastOrPresent in pastOrPresents) {
      if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isAfter",
            nowExpression(LocalDate::class),
          ),
          pastOrPresent["message"],
        ) { "it is not before or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_TIME_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isAfter",
            nowExpression(LocalDateTime::class),
          ),
          pastOrPresent["message"],
        ) { "it is not before or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_TIME_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isAfter",
            nowExpression(LocalTime::class),
          ),
          pastOrPresent["message"],
        ) { "it is not before or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == INSTANT_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isAfter",
            nowExpression(java.time.Instant::class),
          ),
          pastOrPresent["message"],
        ) { "it is not before or equal to now" }
      }
    }

    for (past in pasts) {
      if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isAfter",
              nowExpression(LocalDate::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalDate::class),
            ),
          ),
          past["message"],
        ) { "it is not before now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_TIME_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isAfter",
              nowExpression(LocalDateTime::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalDateTime::class),
            ),
          ),
          past["message"],
        ) { "it is not before now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_TIME_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isAfter",
              nowExpression(LocalTime::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalTime::class),
            ),
          ),
          past["message"],
        ) { "it is not before now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == INSTANT_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isAfter",
            nowExpression(java.time.Instant::class),
          ),
          past["message"],
        ) { "it is not before now" }
      }
    }

    for (futureOrPresent in futureOrPresents) {
      if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isBefore",
            nowExpression(LocalDate::class),
          ),
          futureOrPresent["message"],
        ) { "it is not after or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_TIME_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isBefore",
            nowExpression(LocalDateTime::class),
          ),
          futureOrPresent["message"],
        ) { "it is not after or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_TIME_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isBefore",
            nowExpression(LocalTime::class),
          ),
          futureOrPresent["message"],
        ) { "it is not after or equal to now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == INSTANT_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isBefore",
            nowExpression(java.time.Instant::class),
          ),
          futureOrPresent["message"],
        ) { "it is not after or equal to now" }
      }
    }

    for (future in futures) {
      if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isBefore",
              nowExpression(LocalDate::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalDate::class),
            ),
          ),
          future["message"],
        ) { "it is not after now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_DATE_TIME_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isBefore",
              nowExpression(LocalDateTime::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalDateTime::class),
            ),
          ),
          future["message"],
        ) { "it is not after now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == LOCAL_TIME_LSI_CLASS_NAME) {
        validate(
          or(
            call(
              propExpression(),
              "isBefore",
              nowExpression(LocalTime::class),
            ),
            call(
              propExpression(),
              "isEqual",
              nowExpression(LocalTime::class),
            ),
          ),
          future["message"],
        ) { "it is not after now" }
      } else if (prop.lsiTypeName(overrideNullable = false) == INSTANT_LSI_CLASS_NAME) {
        validate(
          call(
            propExpression(),
            "isBefore",
            nowExpression(java.time.Instant::class),
          ),
          future["message"],
        ) { "it is not after now" }
      }
    }
  }

  private fun validate(
    condition: LsiExpression,
    errorMessage: String?,
    defaultMessageSupplier: () -> String,
  ) {
    if (!(errorMessage.isNullOrEmpty() ||
        errorMessage.startsWith("{javax.validation.constraints.") ||
        errorMessage.startsWith("{jakarta.validation.constraints."))
    ) {
      return
    }

    val conditionExpression =
      if (!prop.isNullable || prop.lsiTypeName().isBuiltInType(false)) {
        condition
      } else {
        LsiBinaryExpression(
          left = LsiBinaryExpression(
            left = LsiNameExpression(prop.name),
            operator = LsiBinaryOperator.NOT_EQUALS,
            right = LsiNullExpression,
          ),
          operator = LsiBinaryOperator.AND,
          right = condition,
        )
      }

    statements +=
      LsiIfStatement(
        condition = conditionExpression,
        thenStatements = listOf(
          LsiThrowStatement(
            LsiNewExpression(
              type = validationExceptionClassName(),
              arguments = listOf(
                validationMessageExpression(defaultMessageSupplier())
              ),
            )
          )
        ),
      )
  }

  private fun isSimpleType(type: KClass<*>): Boolean {
    // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../generator/ValidationGenerator.isSimpleType
    // 迁移说明：简单类型判定改为 TypeName 反射名匹配，移除对 ImmutableProp.realDeclaration(KS) 依赖
    val className = when (val typeName = prop.lsiTypeName()) {
      is LsiClassName -> typeName.copyNullable(false).canonicalName
      is LsiParameterizedTypeName -> typeName.rawType.canonicalName
      else -> return false
    }
    return className == type.qualifiedName || className == type.java.name
  }

  private fun validateBound(bound: BigDecimal, cmp: String, message: String?) {
    val operator = when (cmp) {
      "<" -> LsiBinaryOperator.LESS_THAN
      ">" -> LsiBinaryOperator.GREATER_THAN
      else -> error("Unsupported validation comparison '$cmp'")
    }
    val bigNumLiteral = bigNumberBoundExpression(bound)
    validate(
      if (bigNumLiteral != null) {
        compare(
          call(
            propExpression(),
            "compareTo",
            bigNumLiteral,
          ),
          operator,
          LsiLiteralExpression(0),
        )
      } else {
        compare(
          propExpression(),
          operator,
          LsiLiteralExpression(bound),
        )
      },
      message
    ) {
      ("it cannot be " +
          (if ((cmp == "<")) "less than" else "greater than") +
          " " +
          bound)
    }
  }

  private fun bigNumberBoundExpression(bound: BigDecimal): LsiExpression? =
    when {
      prop.lsiTypeName(overrideNullable = false) == BIG_DECIMAL_LSI_CLASS_NAME ->
        when (bound) {
          BigDecimal.ZERO -> staticProperty(BigDecimal::class, "ZERO")
          BigDecimal.ONE -> staticProperty(BigDecimal::class, "ONE")
          BigDecimal.TEN -> staticProperty(BigDecimal::class, "TEN")
          else -> staticCall(BigDecimal::class, "valueOf", LsiLiteralExpression(bound))
        }

      prop.lsiTypeName(overrideNullable = false) == BIG_INTEGER_LSI_CLASS_NAME ->
        when (bound) {
          BigDecimal.ONE.negate() -> staticProperty(BigInteger::class, "NEGATIVE_ONE")
          BigDecimal.ZERO -> staticProperty(BigInteger::class, "ZERO")
          BigDecimal.ONE -> staticProperty(BigInteger::class, "ONE")
          BigDecimal.TEN -> staticProperty(BigInteger::class, "TEN")
          else -> staticCall(BigInteger::class, "valueOf", LsiLiteralExpression(bound))
        }

      else ->
        null
    }

  private fun staticCall(
    type: KClass<*>,
    name: String,
    vararg arguments: LsiExpression,
  ): LsiExpression =
    call(
      receiver = LsiTypeExpression(type.toLsiClassName()),
      name = name,
      *arguments,
    )

  private fun staticProperty(
    type: KClass<*>,
    name: String,
  ): LsiExpression =
    LsiPropertyAccessExpression(
      receiver = LsiTypeExpression(type.toLsiClassName()),
      name = name,
    )
}
