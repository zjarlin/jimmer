@file:JvmSynthetic

package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue

internal fun LsiTypeRef.toKotlinTypeName(): TypeName {
    return toKotlinTypeName(referenceContext = false)
}

private fun LsiTypeRef.toKotlinTypeName(referenceContext: Boolean): TypeName {
    val typeName = when (this) {
        is LsiPrimitiveType -> toKotlinPrimitiveTypeName(referenceContext)
        is LsiDeclaredType -> {
            val qualifiedName = declarationId.requireTypeQualifiedName()
            val rawType = KOTLIN_TYPES[qualifiedName] ?: ClassName.bestGuess(qualifiedName)
            if (arguments.isEmpty()) {
                rawType
            } else {
                rawType.parameterizedBy(
                    arguments.map { argument ->
                        when (argument.variance) {
                            LsiVariance.STAR -> STAR
                            LsiVariance.INVARIANT -> requireNotNull(argument.type)
                                .toKotlinTypeName(referenceContext = true)
                            LsiVariance.IN -> WildcardTypeName.consumerOf(
                                requireNotNull(argument.type).toKotlinTypeName(referenceContext = true)
                            )
                            LsiVariance.OUT -> WildcardTypeName.producerOf(
                                requireNotNull(argument.type).toKotlinTypeName(referenceContext = true)
                            )
                        }
                    }
                )
            }
        }
        is LsiArrayType -> elementType.toKotlinArrayTypeName()
        is LsiFunctionType -> LambdaTypeName.get(
            receiver = receiverType?.toKotlinTypeName(referenceContext = true),
            parameters = parameterTypes.map { parameter ->
                ParameterSpec.unnamed(parameter.toKotlinTypeName(referenceContext = true))
            },
            returnType = returnType.toKotlinTypeName(referenceContext = false),
        ).copy(suspending = suspending)
        is LsiTypeParameterRef -> TypeVariableName(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> error(
            "KotlinPoet renderer cannot emit unresolved LSI type: $displayName"
        )
    }
    val nullableTypeName = typeName.copy(nullable = nullability == LsiNullability.NULLABLE)
    val annotationSpecs = annotations.map(LsiAnnotation::toKotlinCoreAnnotationSpec)
    return if (annotationSpecs.isEmpty()) {
        nullableTypeName
    } else {
        nullableTypeName.copy(annotations = nullableTypeName.annotations + annotationSpecs)
    }
}

internal fun LsiTypeParameter.toKotlinTypeVariableName(
    reified: Boolean = false,
): TypeVariableName {
    val varianceModifier = when (variance) {
        LsiVariance.INVARIANT -> null
        LsiVariance.IN -> KModifier.IN
        LsiVariance.OUT -> KModifier.OUT
        LsiVariance.STAR -> error("Kotlin type parameter declaration cannot use star variance: $name")
    }
    val bounds = upperBounds.map(LsiTypeRef::toKotlinTypeName).toTypedArray()
    return TypeVariableName(name, *bounds, variance = varianceModifier)
        .copy(reified = reified)
}

internal fun LsiAnnotation.toKotlinCoreAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            useSiteTarget?.toPoetUseSiteTarget()?.let(::useSiteTarget)
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember(
                        "%L = %L",
                        name,
                        argument.value.toKotlinCoreAnnotationValue(),
                    )
                }
            }
        }
        .build()
}

internal fun LsiPoetAnnotation.toKotlinSourceAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            useSiteTarget?.toPoetUseSiteTarget()?.let(::useSiteTarget)
            arguments.forEach { argument ->
                when (argument) {
                    is LsiPoetAnnotationArgument.Named -> addMember(
                        "%L = %L",
                        argument.name,
                        argument.value.toKotlinSourceAnnotationValue(),
                    )
                    is LsiPoetAnnotationArgument.Positional -> addMember(
                        "%L",
                        argument.value.toKotlinSourceAnnotationValue(),
                    )
                }
            }
        }
        .build()
}

private fun LsiPrimitiveKind.toKotlinTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> BOOLEAN
        LsiPrimitiveKind.BYTE -> BYTE
        LsiPrimitiveKind.SHORT -> SHORT
        LsiPrimitiveKind.INT -> INT
        LsiPrimitiveKind.LONG -> LONG
        LsiPrimitiveKind.CHAR -> CHAR
        LsiPrimitiveKind.FLOAT -> FLOAT
        LsiPrimitiveKind.DOUBLE -> DOUBLE
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> UNIT
    }
}

private fun LsiPrimitiveType.toKotlinPrimitiveTypeName(referenceContext: Boolean): TypeName {
    if (kind == LsiPrimitiveKind.VOID && (boxed || referenceContext)) {
        return JAVA_LANG_VOID
    }
    if (
        boxed &&
        !referenceContext &&
        nullability != LsiNullability.NULLABLE &&
        kind != LsiPrimitiveKind.UNIT
    ) {
        return kind.toKotlinBoxedTypeName()
    }
    return kind.toKotlinTypeName()
}

private fun LsiPrimitiveKind.toKotlinBoxedTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> ClassName("java.lang", "Boolean")
        LsiPrimitiveKind.BYTE -> ClassName("java.lang", "Byte")
        LsiPrimitiveKind.SHORT -> ClassName("java.lang", "Short")
        LsiPrimitiveKind.INT -> ClassName("java.lang", "Integer")
        LsiPrimitiveKind.LONG -> ClassName("java.lang", "Long")
        LsiPrimitiveKind.CHAR -> ClassName("java.lang", "Character")
        LsiPrimitiveKind.FLOAT -> ClassName("java.lang", "Float")
        LsiPrimitiveKind.DOUBLE -> ClassName("java.lang", "Double")
        LsiPrimitiveKind.UNIT -> UNIT
        LsiPrimitiveKind.VOID -> JAVA_LANG_VOID
    }
}

private fun LsiTypeRef.toKotlinArrayTypeName(): TypeName {
    val primitiveType = this as? LsiPrimitiveType
    if (
        primitiveType != null &&
        !primitiveType.boxed &&
        primitiveType.nullability == LsiNullability.NON_NULL
    ) {
        primitiveType.kind.toKotlinPrimitiveArrayTypeName()?.let { return it }
    }
    return ClassName("kotlin", "Array").parameterizedBy(
        toKotlinTypeName(referenceContext = true)
    )
}

private fun LsiPrimitiveKind.toKotlinPrimitiveArrayTypeName(): TypeName? {
    val simpleName = when (this) {
        LsiPrimitiveKind.BOOLEAN -> "BooleanArray"
        LsiPrimitiveKind.BYTE -> "ByteArray"
        LsiPrimitiveKind.SHORT -> "ShortArray"
        LsiPrimitiveKind.INT -> "IntArray"
        LsiPrimitiveKind.LONG -> "LongArray"
        LsiPrimitiveKind.CHAR -> "CharArray"
        LsiPrimitiveKind.FLOAT -> "FloatArray"
        LsiPrimitiveKind.DOUBLE -> "DoubleArray"
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> return null
    }
    return ClassName("kotlin", simpleName)
}

private fun LsiAnnotationValue.toKotlinCoreAnnotationValue(): CodeBlock {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ByteValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ShortValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.IntValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.LongValue -> CodeBlock.of("%LL", value)
        is LsiAnnotationValue.FloatValue -> CodeBlock.of("%LF", value)
        is LsiAnnotationValue.DoubleValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.CharValue -> CodeBlock.of("%L", value.toCharacterLiteral())
        is LsiAnnotationValue.StringValue -> CodeBlock.of("%S", value)
        is LsiAnnotationValue.EnumValue -> CodeBlock.of(
            "%T.%L",
            ClassName.bestGuess(enumType.requireTypeQualifiedName()),
            entryName,
        )
        is LsiAnnotationValue.ClassValue -> type.toKotlinClassLiteral()
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.toKotlinNestedCoreAnnotationValue()
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("[")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("%L", element.toKotlinCoreAnnotationValue())
                }
            }
            .add("]")
            .build()
    }
}

private fun LsiPoetAnnotationValue.toKotlinSourceAnnotationValue(): CodeBlock {
    return when (this) {
        is LsiPoetAnnotationValue.BooleanValue -> CodeBlock.of("%L", value)
        is LsiPoetAnnotationValue.ByteValue -> CodeBlock.of("%L", value)
        is LsiPoetAnnotationValue.ShortValue -> CodeBlock.of("%L", value)
        is LsiPoetAnnotationValue.IntValue -> CodeBlock.of("%L", value)
        is LsiPoetAnnotationValue.LongValue -> CodeBlock.of("%LL", value)
        is LsiPoetAnnotationValue.FloatValue -> CodeBlock.of("%LF", value)
        is LsiPoetAnnotationValue.DoubleValue -> CodeBlock.of("%L", value)
        is LsiPoetAnnotationValue.CharValue -> CodeBlock.of("%L", value.toCharacterLiteral())
        is LsiPoetAnnotationValue.StringValue -> CodeBlock.of("%S", value)
        is LsiPoetAnnotationValue.EnumValue -> CodeBlock.of(
            "%T.%L",
            ClassName.bestGuess(enumType.requireTypeQualifiedName()),
            entryName,
        )
        is LsiPoetAnnotationValue.ClassValue -> type.toKotlinClassLiteral()
        is LsiPoetAnnotationValue.NestedAnnotationValue -> annotation.toKotlinNestedSourceAnnotationValue()
        is LsiPoetAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add(
                when (sourceStyle) {
                    LsiPoetAnnotationArrayStyle.LITERAL -> "["
                    LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF -> "arrayOf("
                }
            )
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("%L", element.toKotlinSourceAnnotationValue())
                }
            }
            .add(
                when (sourceStyle) {
                    LsiPoetAnnotationArrayStyle.LITERAL -> "]"
                    LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF -> ")"
                }
            )
            .build()
    }
}

private fun LsiAnnotation.toKotlinNestedCoreAnnotationValue(): CodeBlock {
    require(useSiteTarget == null) {
        "Nested Kotlin annotation value cannot declare a use-site target: $type"
    }
    return CodeBlock.builder()
        .add("%T(", ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            arguments
                .asSequence()
                .filter { (_, argument) -> argument.isExplicit }
                .sortedBy { (name, _) -> name }
                .forEachIndexed { index, (name, argument) ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("%L = %L", name, argument.value.toKotlinCoreAnnotationValue())
                }
        }
        .add(")")
        .build()
}

private fun LsiPoetAnnotation.toKotlinNestedSourceAnnotationValue(): CodeBlock {
    require(useSiteTarget == null) {
        "Nested Kotlin annotation value cannot declare a use-site target: $type"
    }
    return CodeBlock.builder()
        .add("%T(", ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            arguments.forEachIndexed { index, argument ->
                if (index != 0) {
                    add(", ")
                }
                when (argument) {
                    is LsiPoetAnnotationArgument.Named -> add(
                        "%L = %L",
                        argument.name,
                        argument.value.toKotlinSourceAnnotationValue(),
                    )
                    is LsiPoetAnnotationArgument.Positional -> add(
                        "%L",
                        argument.value.toKotlinSourceAnnotationValue(),
                    )
                }
            }
        }
        .add(")")
        .build()
}

private fun LsiTypeRef.toKotlinClassLiteral(): CodeBlock {
    val primitive = this as? LsiPrimitiveType
    if (primitive?.kind == LsiPrimitiveKind.VOID && !primitive.boxed) {
        error("Kotlin annotation source cannot represent the primitive void class literal")
    }
    val typeName = if (primitive?.boxed == true) {
        primitive.kind.toKotlinBoxedTypeName()
    } else {
        toKotlinTypeName()
    }
    return CodeBlock.of("%T::class", typeName.copy(nullable = false))
}

private fun LsiAnnotationUseSiteTarget.toPoetUseSiteTarget(): AnnotationSpec.UseSiteTarget? {
    return when (this) {
        LsiAnnotationUseSiteTarget.FILE -> AnnotationSpec.UseSiteTarget.FILE
        LsiAnnotationUseSiteTarget.PROPERTY -> AnnotationSpec.UseSiteTarget.PROPERTY
        LsiAnnotationUseSiteTarget.FIELD -> AnnotationSpec.UseSiteTarget.FIELD
        LsiAnnotationUseSiteTarget.GETTER -> AnnotationSpec.UseSiteTarget.GET
        LsiAnnotationUseSiteTarget.SETTER -> AnnotationSpec.UseSiteTarget.SET
        LsiAnnotationUseSiteTarget.RECEIVER -> AnnotationSpec.UseSiteTarget.RECEIVER
        LsiAnnotationUseSiteTarget.PARAMETER -> AnnotationSpec.UseSiteTarget.PARAM
        LsiAnnotationUseSiteTarget.SET_PARAMETER -> AnnotationSpec.UseSiteTarget.SETPARAM
        LsiAnnotationUseSiteTarget.DELEGATE -> AnnotationSpec.UseSiteTarget.DELEGATE
        LsiAnnotationUseSiteTarget.PACKAGE,
        LsiAnnotationUseSiteTarget.TYPE,
        LsiAnnotationUseSiteTarget.CONSTRUCTOR,
        LsiAnnotationUseSiteTarget.METHOD,
        LsiAnnotationUseSiteTarget.RETURN_TYPE,
        -> null
        LsiAnnotationUseSiteTarget.ALL -> error(
            "KotlinPoet renderer cannot emit the Kotlin ALL annotation use-site target"
        )
    }
}

private fun Char.toCharacterLiteral(): String {
    val content = when (this) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\u000c' -> "\\u000c"
        '\r' -> "\\r"
        '\'' -> "\\'"
        '\\' -> "\\\\"
        else -> if (isISOControl()) "\\u${code.toString(16).padStart(4, '0')}" else toString()
    }
    return "'$content'"
}

private val KOTLIN_TYPES = mapOf(
    "java.lang.Boolean" to BOOLEAN,
    "java.lang.Byte" to BYTE,
    "java.lang.Short" to SHORT,
    "java.lang.Integer" to INT,
    "java.lang.Long" to LONG,
    "java.lang.Character" to CHAR,
    "java.lang.Float" to FLOAT,
    "java.lang.Double" to DOUBLE,
    "java.lang.String" to STRING,
    "java.lang.Object" to ANY,
    "java.lang.Iterable" to ClassName("kotlin.collections", "Iterable"),
    "java.util.Collection" to ClassName("kotlin.collections", "Collection"),
    "java.util.Iterator" to ClassName("kotlin.collections", "Iterator"),
    "java.util.List" to ClassName("kotlin.collections", "List"),
    "java.util.ListIterator" to ClassName("kotlin.collections", "ListIterator"),
    "java.util.Map" to ClassName("kotlin.collections", "Map"),
    "java.util.Map.Entry" to ClassName("kotlin.collections", "Map", "Entry"),
    "java.util.Set" to ClassName("kotlin.collections", "Set"),
    "kotlin.Boolean" to BOOLEAN,
    "kotlin.Byte" to BYTE,
    "kotlin.Short" to SHORT,
    "kotlin.Int" to INT,
    "kotlin.Long" to LONG,
    "kotlin.Char" to CHAR,
    "kotlin.Float" to FLOAT,
    "kotlin.Double" to DOUBLE,
    "kotlin.String" to STRING,
    "kotlin.Any" to ANY,
    "kotlin.Unit" to UNIT,
)

private val JAVA_LANG_VOID = ClassName("java.lang", "Void")
