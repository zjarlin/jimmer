package org.babyfish.jimmer.compiler.render.ksp

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
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance

internal fun LsiTypeRef.toKotlinTypeName(): TypeName {
    val typeName = when (this) {
        is LsiPrimitiveType -> kind.toKotlinTypeName()
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
                            LsiVariance.INVARIANT -> requireNotNull(argument.type).toKotlinTypeName()
                            LsiVariance.IN -> WildcardTypeName.consumerOf(
                                requireNotNull(argument.type).toKotlinTypeName()
                            )
                            LsiVariance.OUT -> WildcardTypeName.producerOf(
                                requireNotNull(argument.type).toKotlinTypeName()
                            )
                        }
                    }
                )
            }
        }
        is LsiArrayType -> ClassName("kotlin", "Array").parameterizedBy(elementType.toKotlinTypeName())
        is LsiTypeParameterRef -> TypeVariableName(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> ClassName.bestGuess(displayName.filterNot(Char::isWhitespace))
    }
    return typeName.copy(nullable = nullability == LsiNullability.NULLABLE)
}

internal fun LsiTypeRef.toKotlinTypeName(annotations: Iterable<LsiAnnotation>): TypeName {
    val typeName = toKotlinTypeName()
    val annotationSpecs = annotations.map(LsiAnnotation::toKotlinAnnotationSpec)
    return typeName.copy(annotations = typeName.annotations + annotationSpecs)
}

internal fun LsiTypeParameter.toKotlinTypeVariableName(): TypeVariableName {
    val bounds = upperBounds.map(LsiTypeRef::toKotlinTypeName).toTypedArray()
    return TypeVariableName(name, *bounds)
}

internal fun LsiAnnotation.toKotlinAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            useSiteTarget?.toPoetUseSiteTarget()?.let(::useSiteTarget)
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember("%L = %L", name, argument.value.toKotlinAnnotationValue())
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

private fun LsiAnnotationValue.toKotlinAnnotationValue(): CodeBlock {
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
        is LsiAnnotationValue.ClassValue -> CodeBlock.of("%T::class", type.toKotlinTypeName().copy(nullable = false))
        is LsiAnnotationValue.NestedAnnotationValue -> CodeBlock.of(
            "%L",
            annotation.toKotlinAnnotationSpec(),
        )
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("[")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("%L", element.toKotlinAnnotationValue())
                }
            }
            .add("]")
            .build()
    }
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
        LsiAnnotationUseSiteTarget.TYPE,
        LsiAnnotationUseSiteTarget.CONSTRUCTOR,
        LsiAnnotationUseSiteTarget.METHOD,
        LsiAnnotationUseSiteTarget.RETURN_TYPE,
        -> null
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
    "java.lang.Object" to ANY,
    "kotlin.Boolean" to BOOLEAN,
    "kotlin.Byte" to BYTE,
    "kotlin.Short" to SHORT,
    "kotlin.Int" to INT,
    "kotlin.Long" to LONG,
    "kotlin.Char" to CHAR,
    "kotlin.Float" to FLOAT,
    "kotlin.Double" to DOUBLE,
    "kotlin.Any" to ANY,
    "kotlin.Unit" to UNIT,
)
