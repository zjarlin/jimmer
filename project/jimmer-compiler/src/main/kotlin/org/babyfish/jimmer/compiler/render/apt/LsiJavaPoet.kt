package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance

internal fun LsiTypeRef.toJavaTypeName(): TypeName {
    return when (this) {
        is LsiPrimitiveType -> {
            val primitiveTypeName = kind.toJavaTypeName()
            when {
                !boxed -> primitiveTypeName
                kind == LsiPrimitiveKind.UNIT -> ClassName.get("kotlin", "Unit")
                else -> primitiveTypeName.box()
            }
        }
        is LsiDeclaredType -> {
            val rawType = ClassName.bestGuess(declarationId.requireTypeQualifiedName())
            if (arguments.isEmpty()) {
                rawType
            } else {
                ParameterizedTypeName.get(
                    rawType,
                    *arguments.map { argument ->
                        when (argument.variance) {
                            LsiVariance.STAR -> WildcardTypeName.subtypeOf(Any::class.java)
                            LsiVariance.INVARIANT -> requireNotNull(argument.type).toJavaTypeName().box()
                            LsiVariance.IN -> WildcardTypeName.supertypeOf(
                                requireNotNull(argument.type).toJavaTypeName().box()
                            )
                            LsiVariance.OUT -> WildcardTypeName.subtypeOf(
                                requireNotNull(argument.type).toJavaTypeName().box()
                            )
                        }
                    }.toTypedArray(),
                )
            }
        }
        is LsiArrayType -> ArrayTypeName.of(elementType.toJavaTypeName())
        is LsiTypeParameterRef -> TypeVariableName.get(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> ClassName.bestGuess(displayName.filterNot(Char::isWhitespace))
    }
}

internal fun LsiTypeRef.toJavaTypeName(annotations: Iterable<LsiAnnotation>): TypeName {
    val annotationSpecs = annotations.map(LsiAnnotation::toJavaAnnotationSpec).toList()
    return if (annotationSpecs.isEmpty()) {
        toJavaTypeName()
    } else {
        toJavaTypeName().annotated(*annotationSpecs.toTypedArray())
    }
}

internal fun LsiTypeParameter.toJavaTypeVariableName(): TypeVariableName {
    val bounds = upperBounds.map(LsiTypeRef::toJavaTypeName).toTypedArray()
    return if (bounds.isEmpty()) {
        TypeVariableName.get(name)
    } else {
        TypeVariableName.get(name, *bounds)
    }
}

internal fun LsiAnnotation.toJavaAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember(name, "\$L", argument.value.toJavaAnnotationValue())
                }
            }
        }
        .build()
}

private fun LsiPrimitiveKind.toJavaTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> TypeName.BOOLEAN
        LsiPrimitiveKind.BYTE -> TypeName.BYTE
        LsiPrimitiveKind.SHORT -> TypeName.SHORT
        LsiPrimitiveKind.INT -> TypeName.INT
        LsiPrimitiveKind.LONG -> TypeName.LONG
        LsiPrimitiveKind.CHAR -> TypeName.CHAR
        LsiPrimitiveKind.FLOAT -> TypeName.FLOAT
        LsiPrimitiveKind.DOUBLE -> TypeName.DOUBLE
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> TypeName.VOID
    }
}

private fun LsiAnnotationValue.toJavaAnnotationValue(): CodeBlock {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> CodeBlock.of("\$L", value)
        is LsiAnnotationValue.ByteValue -> CodeBlock.of("\$L", value)
        is LsiAnnotationValue.ShortValue -> CodeBlock.of("\$L", value)
        is LsiAnnotationValue.IntValue -> CodeBlock.of("\$L", value)
        is LsiAnnotationValue.LongValue -> CodeBlock.of("\$LL", value)
        is LsiAnnotationValue.FloatValue -> CodeBlock.of("\$Lf", value)
        is LsiAnnotationValue.DoubleValue -> CodeBlock.of("\$L", value)
        is LsiAnnotationValue.CharValue -> CodeBlock.of("\$L", value.toCharacterLiteral())
        is LsiAnnotationValue.StringValue -> CodeBlock.of("\$S", value)
        is LsiAnnotationValue.EnumValue -> CodeBlock.of(
            "\$T.\$L",
            ClassName.bestGuess(enumType.requireTypeQualifiedName()),
            entryName,
        )
        is LsiAnnotationValue.ClassValue -> CodeBlock.of("\$T.class", type.toJavaClassLiteralTypeName())
        is LsiAnnotationValue.NestedAnnotationValue -> CodeBlock.of(
            "\$L",
            annotation.toJavaAnnotationSpec(),
        )
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("{")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("\$L", element.toJavaAnnotationValue())
                }
            }
            .add("}")
            .build()
    }
}

private fun LsiTypeRef.toJavaClassLiteralTypeName(): TypeName {
    val primitive = this as? LsiPrimitiveType
    return if (primitive?.kind == LsiPrimitiveKind.UNIT) {
        ClassName.get("kotlin", "Unit")
    } else {
        toJavaTypeName()
    }
}

private fun Char.toCharacterLiteral(): String {
    val content = when (this) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\u000c' -> "\\f"
        '\r' -> "\\r"
        '\'' -> "\\'"
        '\\' -> "\\\\"
        else -> if (isISOControl()) "\\u${code.toString(16).padStart(4, '0')}" else toString()
    }
    return "'$content'"
}
