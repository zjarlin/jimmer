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
import site.addzero.lsi.core.LsiSymbolId
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
import site.addzero.lsi.poet.LsiPoetClassLiteralStyle
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle

internal fun LsiTypeRef.toJavaTypeName(typeNames: List<LsiPoetTypeName>): TypeName {
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
            val rawType = typeNames.requireJavaClassName(declarationId)
            if (arguments.isEmpty()) {
                rawType
            } else {
                ParameterizedTypeName.get(
                    rawType,
                    *arguments.map { argument ->
                        when (argument.variance) {
                            LsiVariance.STAR -> WildcardTypeName.subtypeOf(Any::class.java)
                            LsiVariance.INVARIANT -> requireNotNull(argument.type)
                                .toJavaTypeName(typeNames)
                                .box()
                            LsiVariance.IN -> WildcardTypeName.supertypeOf(
                                requireNotNull(argument.type).toJavaTypeName(typeNames).box()
                            )
                            LsiVariance.OUT -> WildcardTypeName.subtypeOf(
                                requireNotNull(argument.type).toJavaTypeName(typeNames).box()
                            )
                        }
                    }.toTypedArray(),
                )
            }
        }
        is LsiArrayType -> ArrayTypeName.of(elementType.toJavaTypeName(typeNames))
        is LsiFunctionType -> error(
            "JavaPoet renderer cannot emit an LSI function type without an explicit JVM ABI",
        )
        is LsiTypeParameterRef -> TypeVariableName.get(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> error(
            "JavaPoet renderer cannot emit unresolved LSI type: $displayName"
        )
    }
    val annotationSpecs = annotations.map { annotation ->
        annotation.toJavaCoreAnnotationSpec(typeNames)
    }
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
    typeNames: List<LsiPoetTypeName>,
    referenceStyle: LsiPoetTypeReferenceStyle,
    currentPackageName: String?,
): TypeName {
    if (referenceStyle == LsiPoetTypeReferenceStyle.IMPORTED) {
        return toJavaTypeName(typeNames)
    }
    val declaredType = this as? LsiDeclaredType
        ?: error("Java declaration type reference style requires a declared type: $this")
    val exactTypeName = typeNames.requireTypeName(declaredType.declarationId)
    val rawType = when (referenceStyle) {
        LsiPoetTypeReferenceStyle.IMPORTED -> error("Imported Java type is handled before source qualification")
        LsiPoetTypeReferenceStyle.FULLY_QUALIFIED -> {
            val sourceSegments = exactTypeName.packageName
                .split('.')
                .filter(String::isNotEmpty) + exactTypeName.simpleNames
            ClassName.get("", sourceSegments.first(), *sourceSegments.drop(1).toTypedArray())
        }
        LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> {
            requireNotNull(currentPackageName) {
                "Same-package outer-qualified Java type requires file package context: ${declaredType.declarationId}"
            }
            require(exactTypeName.packageName == currentPackageName) {
                "Same-package outer-qualified Java type must belong to '$currentPackageName': " +
                    declaredType.declarationId
            }
            require(exactTypeName.simpleNames.size >= 2) {
                "Same-package outer-qualified Java type must be nested: ${declaredType.declarationId}"
            }
            ClassName.get(
                "",
                exactTypeName.simpleNames.first(),
                *exactTypeName.simpleNames.drop(1).toTypedArray(),
            )
        }
    }
    val sourceType = if (declaredType.arguments.isEmpty()) {
        rawType
    } else {
        ParameterizedTypeName.get(
            rawType,
            *declaredType.arguments.map { argument ->
                when (argument.variance) {
                    LsiVariance.STAR -> WildcardTypeName.subtypeOf(Any::class.java)
                    LsiVariance.INVARIANT -> requireNotNull(argument.type)
                        .toJavaTypeName(typeNames)
                        .box()
                    LsiVariance.IN -> WildcardTypeName.supertypeOf(
                        requireNotNull(argument.type).toJavaTypeName(typeNames).box()
                    )
                    LsiVariance.OUT -> WildcardTypeName.subtypeOf(
                        requireNotNull(argument.type).toJavaTypeName(typeNames).box()
                    )
                }
            }.toTypedArray(),
        )
    }
    val annotationSpecs = annotations.map { annotation ->
        annotation.toJavaCoreAnnotationSpec(typeNames)
    }
    return if (annotationSpecs.isEmpty()) {
        sourceType
    } else {
        sourceType.annotated(*annotationSpecs.toTypedArray())
    }
}

internal fun LsiTypeParameter.toJavaTypeVariableName(
    typeNames: List<LsiPoetTypeName>,
): TypeVariableName {
    require(variance == LsiVariance.INVARIANT) {
        "JavaPoet renderer cannot emit declaration-site variance for type parameter: $name"
    }
    val bounds = upperBounds.map { bound -> bound.toJavaTypeName(typeNames) }.toTypedArray()
    return if (bounds.isEmpty()) {
        TypeVariableName.get(name)
    } else {
        TypeVariableName.get(name, *bounds)
    }
}

internal fun LsiAnnotation.toJavaCoreAnnotationSpec(
    typeNames: List<LsiPoetTypeName>,
): AnnotationSpec {
    return AnnotationSpec.builder(typeNames.requireJavaClassName(type))
        .apply {
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember(name, "\$L", argument.value.toJavaCoreAnnotationValue(typeNames))
                }
            }
        }
        .build()
}

internal fun LsiPoetAnnotation.toJavaSourceAnnotationSpec(
    typeNames: List<LsiPoetTypeName>,
): AnnotationSpec {
    require(argumentLayout == LsiPoetAnnotationArgumentLayout.PLATFORM_DEFAULT) {
        "JavaPoet renderer cannot honor a forced annotation layout: $type"
    }
    val positionalArguments = arguments.filterIsInstance<LsiPoetAnnotationArgument.Positional>()
    require(positionalArguments.size <= 1) {
        "Java annotation cannot represent multiple positional arguments: $type"
    }
    require(positionalArguments.isEmpty() || arguments.size == 1) {
        "Java annotation cannot combine positional and named arguments: $type"
    }
    return AnnotationSpec.builder(typeNames.requireJavaClassName(type))
        .apply {
            arguments.forEach { argument ->
                when (argument) {
                    is LsiPoetAnnotationArgument.Named -> addMember(
                        argument.name,
                        "\$L",
                        argument.value.toJavaSourceAnnotationValue(typeNames),
                    )
                    is LsiPoetAnnotationArgument.Positional -> addMember(
                        "value",
                        "\$L",
                        argument.value.toJavaSourceAnnotationValue(typeNames),
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

private fun LsiAnnotationValue.toJavaCoreAnnotationValue(
    typeNames: List<LsiPoetTypeName>,
): CodeBlock {
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
            typeNames.requireJavaClassName(enumType),
            entryName,
        )
        is LsiAnnotationValue.ClassValue -> CodeBlock.of(
            "\$T.class",
            type.toJavaClassLiteralTypeName(typeNames),
        )
        is LsiAnnotationValue.NestedAnnotationValue -> CodeBlock.of(
            "\$L",
            annotation.toJavaCoreAnnotationSpec(typeNames),
        )
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("{")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("\$L", element.toJavaCoreAnnotationValue(typeNames))
                }
            }
            .add("}")
            .build()
    }
}

private fun LsiPoetAnnotationValue.toJavaSourceAnnotationValue(
    typeNames: List<LsiPoetTypeName>,
): CodeBlock {
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
            typeNames.requireJavaClassName(enumType),
            entryName,
        )
        is LsiPoetAnnotationValue.ClassValue -> when (sourceStyle) {
            LsiPoetClassLiteralStyle.PLATFORM_TYPE -> CodeBlock.of(
                "\$T.class",
                type.toJavaClassLiteralTypeName(typeNames),
            )
            LsiPoetClassLiteralStyle.JAVA_BOXED_PRIMITIVE_QUALIFIED -> CodeBlock.of(
                "\$L.class",
                type.toJavaBoxedQualifiedName(),
            )
        }
        is LsiPoetAnnotationValue.NestedAnnotationValue -> CodeBlock.of(
            "\$L",
            annotation.toJavaSourceAnnotationSpec(typeNames),
        )
        is LsiPoetAnnotationValue.ArrayValue -> when (sourceStyle) {
            LsiPoetAnnotationArrayStyle.LITERAL -> CodeBlock.builder()
                .add("{")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(", ")
                        }
                        add("\$L", element.toJavaSourceAnnotationValue(typeNames))
                    }
                }
                .add("}")
                .build()
            LsiPoetAnnotationArrayStyle.LINE_SEPARATED_LITERAL -> CodeBlock.builder()
                .add("{")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(",\n")
                        }
                        add("\$L", element.toJavaSourceAnnotationValue(typeNames))
                    }
                }
                .add("}")
                .build()
            LsiPoetAnnotationArrayStyle.MULTI_LINE_LITERAL -> CodeBlock.builder()
                .add("{\n\$>")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(", \n")
                        }
                        add("\$L", element.toJavaSourceAnnotationValue(typeNames))
                    }
                }
                .add("\$<\n}")
                .build()
            LsiPoetAnnotationArrayStyle.COMPACT_MULTI_LINE_LITERAL -> CodeBlock.builder()
                .add("{\n\$>")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(",\n")
                        }
                        add("\$L", element.toJavaSourceAnnotationValue(typeNames))
                    }
                }
                .add("\$<\n}")
                .build()
            LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF -> throw IllegalArgumentException(
                "JavaPoet renderer cannot emit an annotation array factory call"
            )
        }
    }
}

private fun LsiTypeRef.toJavaClassLiteralTypeName(
    typeNames: List<LsiPoetTypeName>,
): TypeName {
    val primitive = this as? LsiPrimitiveType
    return if (primitive?.kind == LsiPrimitiveKind.UNIT) {
        ClassName.get("kotlin", "Unit")
    } else {
        toJavaTypeName(typeNames)
    }
}

private fun LsiTypeRef.toJavaBoxedQualifiedName(): String {
    val primitive = this as? LsiPrimitiveType
    require(primitive?.boxed == true) {
        "Qualified Java boxed class literal requires a boxed primitive type: $this"
    }
    return when (primitive.kind) {
        LsiPrimitiveKind.BOOLEAN -> "java.lang.Boolean"
        LsiPrimitiveKind.BYTE -> "java.lang.Byte"
        LsiPrimitiveKind.SHORT -> "java.lang.Short"
        LsiPrimitiveKind.INT -> "java.lang.Integer"
        LsiPrimitiveKind.LONG -> "java.lang.Long"
        LsiPrimitiveKind.CHAR -> "java.lang.Character"
        LsiPrimitiveKind.FLOAT -> "java.lang.Float"
        LsiPrimitiveKind.DOUBLE -> "java.lang.Double"
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> error("Java has no boxed primitive class literal for ${primitive.kind.name}")
    }
}

private fun List<LsiPoetTypeName>.requireJavaClassName(typeId: LsiSymbolId): ClassName {
    val typeName = requireTypeName(typeId)
    return ClassName.get(
        typeName.packageName,
        typeName.simpleNames.first(),
        *typeName.simpleNames.drop(1).toTypedArray(),
    )
}

private fun List<LsiPoetTypeName>.requireTypeName(
    typeId: LsiSymbolId,
): LsiPoetTypeName {
    val matches = filter { typeName -> typeName.typeId == typeId }
    require(matches.size == 1) {
        "JavaPoet renderer requires exactly one source type name for $typeId, found ${matches.size}"
    }
    return matches.single()
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
