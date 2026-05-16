package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.KOTLIN_STRING_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression
import kotlin.reflect.KClass

internal enum class PropertyDispatchArgKind(
    val typeName: LsiClassName,
    val illegalKindLabel: String,
    val usesIndexedSubject: Boolean,
) {
    PROP_ID(
        typeName = PROP_ID_LSI_CLASS_NAME,
        illegalKindLabel = "id",
        usesIndexedSubject = true,
    ),
    PROP_NAME(
        typeName = KOTLIN_STRING_LSI_CLASS_NAME,
        illegalKindLabel = "name",
        usesIndexedSubject = false,
    ),
}

internal fun LsiClassName.parameterizedBy(vararg typeArguments: LsiTypeName): LsiParameterizedTypeName =
    LsiParameterizedTypeName(
        rawType = copyNullable(false),
        typeArguments = typeArguments.toList(),
        nullable = nullable,
    )

internal fun rawExpression(format: String, vararg args: Any?): LsiCodeExpression =
    LsiCodeExpression(LsiCodeBlock.of(format, *args))

internal fun rawExpression(code: LsiCodeBlock): LsiCodeExpression =
    LsiCodeExpression(code)

internal fun rawStatement(format: String, vararg args: Any?): LsiExpressionStatement =
    LsiExpressionStatement(rawExpression(format, *args))

internal fun rawStatement(code: String): LsiExpressionStatement =
    LsiExpressionStatement(LsiCodeExpression(LsiCodeBlock.of(code)))

internal fun rawStatement(code: LsiCodeBlock): LsiExpressionStatement =
    LsiExpressionStatement(rawExpression(code))

internal fun KClass<*>.toLsiClassName(): LsiClassName =
    LsiClassName.bestGuess(qualifiedName ?: java.name)

internal fun LsiTypeName.primitiveDefaultValueExpression(): LsiExpression =
    when ((this as? LsiClassName)?.copyNullable(false)?.canonicalName) {
        "kotlin.Boolean" -> LsiLiteralExpression(false)
        "kotlin.Char" -> LsiPropertyAccessExpression(
            receiver = LsiTypeExpression(LsiClassName.bestGuess("kotlin.Char")),
            name = "MIN_VALUE",
        )
        "kotlin.Float" -> LsiLiteralExpression(0F)
        "kotlin.Double" -> LsiLiteralExpression(0.0)
        "kotlin.Byte",
        "kotlin.Short",
        "kotlin.Int",
        "kotlin.Long" -> LsiLiteralExpression(0)
        else -> error("Internal bug: $this is not a supported primitive LSI type")
    }
