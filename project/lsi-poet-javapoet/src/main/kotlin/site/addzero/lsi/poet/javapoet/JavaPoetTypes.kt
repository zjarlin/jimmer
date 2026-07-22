@file:JvmSynthetic

package site.addzero.lsi.poet.javapoet

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
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle

internal fun LsiTypeRef.toJavaTypeName(): TypeName {
    val typeName = when (this) {
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
        is LsiFunctionType -> error(
            "JavaPoet renderer cannot emit an LSI function type without an explicit JVM ABI",
        )
        is LsiTypeParameterRef -> TypeVariableName.get(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> error(
            "JavaPoet renderer cannot emit unresolved LSI type: $displayName"
        )
    }
    val annotationSpecs = annotations.map(LsiAnnotation::toJavaCoreAnnotationSpec)
    return if (annotationSpecs.isEmpty()) {
        typeName
    } else {
        typeName.annotated(*annotationSpecs.toTypedArray())
    }
}

/**
 * 将声明类型的源码限定方式留在 JavaPoet 边界处理，语义类型本身保持不变。
 */
internal fun LsiTypeRef.toJavaTypeName(
    referenceStyle: LsiPoetTypeReferenceStyle,
    currentPackageName: String,
): TypeName {
    if (referenceStyle == LsiPoetTypeReferenceStyle.IMPORTED) {
        return toJavaTypeName()
    }
    val declaredType = this as? LsiDeclaredType
        ?: error("Java declaration type reference style requires a declared type: $this")
    val qualifiedName = declaredType.declarationId.requireTypeQualifiedName()
    val sourceSegments = when (referenceStyle) {
        LsiPoetTypeReferenceStyle.IMPORTED -> error("Imported Java type is handled before source qualification")
        LsiPoetTypeReferenceStyle.FULLY_QUALIFIED -> qualifiedName.split('.')
        LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> {
            val packagePrefix = currentPackageName.takeIf(String::isNotEmpty)?.plus('.') ?: ""
            require(qualifiedName.startsWith(packagePrefix)) {
                "Same-package outer-qualified Java type must belong to '$currentPackageName': $qualifiedName"
            }
            qualifiedName.removePrefix(packagePrefix).split('.').also { simpleNames ->
                require(simpleNames.size >= 2) {
                    "Same-package outer-qualified Java type must be nested: $qualifiedName"
                }
            }
        }
    }
    val rawType = ClassName.get("", sourceSegments.first(), *sourceSegments.drop(1).toTypedArray())
    val sourceType = if (declaredType.arguments.isEmpty()) {
        rawType
    } else {
        ParameterizedTypeName.get(
            rawType,
            *declaredType.arguments.map { argument ->
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
    val annotationSpecs = annotations.map(LsiAnnotation::toJavaCoreAnnotationSpec)
    return if (annotationSpecs.isEmpty()) {
        sourceType
    } else {
        sourceType.annotated(*annotationSpecs.toTypedArray())
    }
}

internal fun LsiTypeParameter.toJavaTypeVariableName(): TypeVariableName {
    require(variance == LsiVariance.INVARIANT) {
        "JavaPoet renderer cannot emit declaration-site variance for type parameter: $name"
    }
    val bounds = upperBounds.map(LsiTypeRef::toJavaTypeName).toTypedArray()
    return if (bounds.isEmpty()) {
        TypeVariableName.get(name)
    } else {
        TypeVariableName.get(name, *bounds)
    }
}

internal fun LsiAnnotation.toJavaCoreAnnotationSpec(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember(name, "\$L", argument.value.toJavaCoreAnnotationValue())
                }
            }
        }
        .build()
}

internal fun LsiPoetAnnotation.toJavaSourceAnnotationSpec(): AnnotationSpec {
    require(argumentLayout == LsiPoetAnnotationArgumentLayout.PLATFORM_DEFAULT) {
        "JavaPoet renderer cannot honor a forced single-line annotation layout: $type"
    }
    val positionalArguments = arguments.filterIsInstance<LsiPoetAnnotationArgument.Positional>()
    require(positionalArguments.size <= 1) {
        "Java annotation cannot represent multiple positional arguments: $type"
    }
    require(positionalArguments.isEmpty() || arguments.size == 1) {
        "Java annotation cannot combine positional and named arguments: $type"
    }
    return AnnotationSpec.builder(ClassName.bestGuess(type.requireTypeQualifiedName()))
        .apply {
            arguments.forEach { argument ->
                when (argument) {
                    is LsiPoetAnnotationArgument.Named -> addMember(
                        argument.name,
                        "\$L",
                        argument.value.toJavaSourceAnnotationValue(),
                    )
                    is LsiPoetAnnotationArgument.Positional -> addMember(
                        "value",
                        "\$L",
                        argument.value.toJavaSourceAnnotationValue(),
                    )
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

private fun LsiAnnotationValue.toJavaCoreAnnotationValue(): CodeBlock {
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
            annotation.toJavaCoreAnnotationSpec(),
        )
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("{")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("\$L", element.toJavaCoreAnnotationValue())
                }
            }
            .add("}")
            .build()
    }
}

private fun LsiPoetAnnotationValue.toJavaSourceAnnotationValue(): CodeBlock {
    return when (this) {
        is LsiPoetAnnotationValue.BooleanValue -> CodeBlock.of("\$L", value)
        is LsiPoetAnnotationValue.ByteValue -> CodeBlock.of("\$L", value)
        is LsiPoetAnnotationValue.ShortValue -> CodeBlock.of("\$L", value)
        is LsiPoetAnnotationValue.IntValue -> CodeBlock.of("\$L", value)
        is LsiPoetAnnotationValue.LongValue -> CodeBlock.of("\$LL", value)
        is LsiPoetAnnotationValue.FloatValue -> CodeBlock.of("\$Lf", value)
        is LsiPoetAnnotationValue.DoubleValue -> CodeBlock.of("\$L", value)
        is LsiPoetAnnotationValue.CharValue -> CodeBlock.of("\$L", value.toCharacterLiteral())
        is LsiPoetAnnotationValue.StringValue -> CodeBlock.of("\$S", value)
        is LsiPoetAnnotationValue.EnumValue -> CodeBlock.of(
            "\$T.\$L",
            ClassName.bestGuess(enumType.requireTypeQualifiedName()),
            entryName,
        )
        is LsiPoetAnnotationValue.ClassValue -> CodeBlock.of(
            "\$T.class",
            type.toJavaClassLiteralTypeName(),
        )
        is LsiPoetAnnotationValue.NestedAnnotationValue -> CodeBlock.of(
            "\$L",
            annotation.toJavaSourceAnnotationSpec(),
        )
        is LsiPoetAnnotationValue.ArrayValue -> {
            require(sourceStyle == LsiPoetAnnotationArrayStyle.LITERAL) {
                "JavaPoet renderer cannot emit an annotation array factory call"
            }
            CodeBlock.builder()
                .add("{")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(", ")
                        }
                        add("\$L", element.toJavaSourceAnnotationValue())
                    }
                }
                .add("}")
                .build()
        }
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
