package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetCodeBlockIndentation
import site.addzero.lsi.poet.LsiPoetBracedExpressionCompletion
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodePart
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetDelegationTarget
import site.addzero.lsi.poet.LsiPoetEnumConstant
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetInitializerBlock
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle

/**
 * 在边界内使用 KotlinPoet 渲染 Kotlin 源码。
 */
class LsiKotlinPoetRenderer : LsiPoetRenderer {

    /** 将单个 LSI 类型引用渲染为可嵌入现有 KotlinPoet 声明的类型。 */
    fun renderTypeName(
        type: LsiTypeRef,
        typeNames: List<LsiPoetTypeName>,
    ): TypeName {
        return type.toKotlinTypeName(typeNames)
    }

    /** 将任意 LSI Poet 代码块渲染为可嵌入现有 KotlinPoet 声明的代码块。 */
    fun renderCodeBlock(
        codeBlock: LsiPoetCodeBlock,
        typeNames: List<LsiPoetTypeName>,
    ): CodeBlock {
        return codeBlock.toKotlinCodeBlock(typeNames)
    }

    /** 将 LSI 代码块直接追加到现有 KotlinPoet builder，保留外围语句和缩进状态。 */
    fun appendCodeBlock(
        builder: CodeBlock.Builder,
        codeBlock: LsiPoetCodeBlock,
        typeNames: List<LsiPoetTypeName>,
    ) {
        codeBlock.appendToKotlinCodeBlock(builder, typeNames)
    }

    /** 将单个 LSI 类型渲染为可嵌入现有 KotlinPoet 声明的结构。 */
    fun renderType(
        type: LsiPoetType,
        typeNames: List<LsiPoetTypeName>,
    ): TypeSpec {
        return type.toKotlinTypeSpec(typeNames)
    }

    /** 将单个 LSI 函数渲染为可嵌入现有 KotlinPoet 类型的结构。 */
    fun renderFunction(
        function: LsiPoetFunction,
        typeNames: List<LsiPoetTypeName>,
    ): FunSpec {
        return function.toKotlinFunction(typeNames)
    }

    /** 将单个 LSI 属性渲染为可嵌入现有 KotlinPoet 类型的结构。 */
    fun renderProperty(
        property: LsiPoetProperty,
        typeNames: List<LsiPoetTypeName>,
    ): PropertySpec {
        return property.toKotlinProperty(typeNames)
    }

    /** 将单个 LSI Poet 注解渲染为可嵌入现有 KotlinPoet 声明的结构。 */
    fun renderAnnotation(
        annotation: LsiPoetAnnotation,
        typeNames: List<LsiPoetTypeName>,
    ): AnnotationSpec {
        return annotation.toKotlinSourceAnnotationSpec(typeNames)
    }

    /** 按声明顺序将 LSI Poet 注解列表渲染为 KotlinPoet 结构。 */
    fun renderAnnotations(
        annotations: List<LsiPoetAnnotation>,
        typeNames: List<LsiPoetTypeName>,
    ): List<AnnotationSpec> {
        return annotations.map { annotation -> renderAnnotation(annotation, typeNames) }
    }

    override fun render(artifact: LsiPoetArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.KOTLIN) {
            "KotlinPoet renderer requires a Kotlin LSI Poet file: ${artifact.qualifiedFileName}"
        }
        val builder = FileSpec.builder(file.packageName, file.fileName)
            .indent("    ")
        file.headerComment?.let { comment -> builder.addFileComment("%L", comment) }
        file.annotations.forEach { annotation ->
            builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(artifact.typeNames))
        }
        file.imports.forEach { sourceImport ->
            builder.addImport(sourceImport.packageName, sourceImport.simpleName)
        }
        file.members.forEach { member -> builder.addKotlinTopLevelMember(member, artifact.typeNames) }
        return artifact.generatedArtifact(builder.build().toString())
    }
}

private fun FileSpec.Builder.addKotlinTopLevelMember(
    member: LsiPoetMember,
    typeNames: List<LsiPoetTypeName>,
) {
    when (member) {
        is LsiPoetFunction -> addFunction(member.toKotlinFunction(typeNames))
        is LsiPoetProperty -> addProperty(member.toKotlinProperty(typeNames))
        is LsiPoetType -> addType(member.toKotlinTypeSpec(typeNames))
        is LsiPoetConstructor -> error("KotlinPoet renderer cannot emit a top-level constructor")
        is LsiPoetField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiPoetInitializerBlock -> error("KotlinPoet renderer cannot emit a top-level initializer block")
    }
}

private fun LsiPoetType.toKotlinTypeSpec(typeNames: List<LsiPoetTypeName>): TypeSpec {
    val builder = when (kind) {
        LsiPoetTypeKind.CLASS -> TypeSpec.classBuilder(name)
        LsiPoetTypeKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiPoetTypeKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiPoetTypeKind.OBJECT -> if (LsiPoetModifier.COMPANION in modifiers) {
            TypeSpec.companionObjectBuilder(name.takeUnless { candidate -> candidate == "Companion" })
        } else {
            TypeSpec.objectBuilder(name)
        }
        LsiPoetTypeKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
        LsiPoetTypeKind.RECORD -> error("KotlinPoet renderer cannot emit a Java record type: $name")
    }
    builder.addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.TYPE))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toKotlinTypeVariableName(typeNames)) }
    primaryConstructor?.let { constructor ->
        builder.primaryConstructor(constructor.toKotlinConstructor(typeNames, primary = true))
    }
    superClass?.let { type -> builder.superclass(type.toKotlinTypeName(typeNames)) }
    superClassConstructorArguments.forEach { argument ->
        builder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock(typeNames))
    }
    superInterfaces.forEach { type -> builder.addSuperinterface(type.toKotlinTypeName(typeNames)) }
    enumConstants.forEach { constant -> builder.addKotlinEnumConstant(constant, typeNames) }
    members.forEach { member -> builder.addKotlinMember(member, typeNames) }
    return builder.build()
}

private fun TypeSpec.Builder.addKotlinEnumConstant(
    constant: LsiPoetEnumConstant,
    typeNames: List<LsiPoetTypeName>,
) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val anonymousBuilder = TypeSpec.anonymousClassBuilder()
    constant.constructorArguments.forEach { argument ->
        anonymousBuilder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock(typeNames))
    }
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumConstants.isEmpty()) {
            "Kotlin enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames))
        }
        type.superInterfaces.forEach { superType ->
            anonymousBuilder.addSuperinterface(superType.toKotlinTypeName(typeNames))
        }
        type.members.forEach { member -> anonymousBuilder.addKotlinMember(member, typeNames) }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addKotlinMember(
    member: LsiPoetMember,
    typeNames: List<LsiPoetTypeName>,
) {
    when (member) {
        is LsiPoetConstructor -> addFunction(member.toKotlinConstructor(typeNames, primary = false))
        is LsiPoetField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiPoetFunction -> addFunction(member.toKotlinFunction(typeNames))
        is LsiPoetInitializerBlock -> addKotlinInitializer(member, typeNames)
        is LsiPoetProperty -> addProperty(member.toKotlinProperty(typeNames))
        is LsiPoetType -> addType(member.toKotlinTypeSpec(typeNames))
    }
}

private fun TypeSpec.Builder.addKotlinInitializer(
    initializer: LsiPoetInitializerBlock,
    typeNames: List<LsiPoetTypeName>,
) {
    require(!initializer.static) {
        "KotlinPoet renderer cannot emit a static initializer block"
    }
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Kotlin initializer block cannot declare annotations or documentation"
    }
    addInitializerBlock(initializer.body.toKotlinCodeBlock(typeNames))
}

private fun LsiPoetConstructor.toKotlinConstructor(
    typeNames: List<LsiPoetTypeName>,
    primary: Boolean,
): FunSpec {
    if (primary) {
        require(body.isEmpty && delegationCall == null) {
            "Kotlin primary constructor cannot declare a body or delegation call"
        }
    }
    require(typeParameters.isEmpty()) {
        "KotlinPoet renderer cannot emit constructor type parameters"
    }
    val builder = FunSpec.constructorBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.CONSTRUCTOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter(typeNames)) }
    builder.addThrownTypes(thrownTypes, typeNames)
    delegationCall?.let { delegation ->
        val arguments = delegation.arguments.map { argument -> argument.toKotlinCodeBlock(typeNames) }.toTypedArray()
        when (delegation.target) {
            LsiPoetDelegationTarget.THIS -> builder.callThisConstructor(*arguments)
            LsiPoetDelegationTarget.SUPER -> builder.callSuperConstructor(*arguments)
        }
    }
    builder.addCode(body.toKotlinCodeBlock(typeNames))
    return builder.build()
}

private fun LsiPoetFunction.toKotlinFunction(typeNames: List<LsiPoetTypeName>): FunSpec {
    val builder = FunSpec.builder(name)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.FUNCTION))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(
            parameter.toKotlinTypeVariableName(
                typeNames = typeNames,
                reified = parameter.id in reifiedTypeParameterIds,
            )
        )
    }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName(typeNames)) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter(typeNames)) }
    returnType?.let { type -> builder.returns(type.toKotlinTypeName(typeNames)) }
    builder.addThrownTypes(thrownTypes, typeNames)
    when (bodyStyle) {
        LsiPoetBodyStyle.BLOCK -> builder.addCode(body.toKotlinCodeBlock(typeNames))
        LsiPoetBodyStyle.EXPRESSION -> builder.addCode("return %L", body.toKotlinCodeBlock(typeNames))
    }
    return builder.build()
}

private fun LsiPoetParameter.toKotlinParameter(typeNames: List<LsiPoetTypeName>): ParameterSpec {
    val builder = ParameterSpec.builder(name, type.toKotlinTypeName(typeNames))
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PARAMETER))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    defaultValue?.let { value -> builder.defaultValue(value.toKotlinCodeBlock(typeNames)) }
    return builder.build()
}

private fun LsiPoetProperty.toKotlinProperty(typeNames: List<LsiPoetTypeName>): PropertySpec {
    val builder = PropertySpec.builder(name, type.toKotlinTypeName(typeNames))
        .mutable(mutable)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PROPERTY))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName(typeNames)) }
    initializer?.let { value -> builder.initializer(value.toKotlinCodeBlock(typeNames)) }
    getter?.let { accessor -> builder.getter(accessor.toKotlinGetter(typeNames)) }
    setter?.let { accessor -> builder.setter(accessor.toKotlinSetter(type, typeNames)) }
    return builder.build()
}

private fun LsiPoetAccessor.toKotlinGetter(typeNames: List<LsiPoetTypeName>): FunSpec {
    require(parameterAnnotations.isEmpty()) {
        "Kotlin getter cannot declare setter parameter annotations"
    }
    val builder = FunSpec.getterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    when (bodyStyle) {
        LsiPoetBodyStyle.BLOCK -> builder.addCode(body.toKotlinCodeBlock(typeNames))
        LsiPoetBodyStyle.EXPRESSION -> builder.addCode("return %L", body.toKotlinCodeBlock(typeNames))
    }
    return builder.build()
}

private fun LsiPoetAccessor.toKotlinSetter(
    type: LsiTypeRef,
    typeNames: List<LsiPoetTypeName>,
): FunSpec {
    require(bodyStyle == LsiPoetBodyStyle.BLOCK) {
        "KotlinPoet renderer cannot emit an expression setter body"
    }
    val parameter = ParameterSpec.builder(setterParameterName, type.toKotlinTypeName(typeNames))
        .apply {
            parameterAnnotations.forEach { annotation ->
                addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames))
            }
        }
        .build()
    val builder = FunSpec.setterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
        .addParameter(parameter)
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    builder.addCode(body.toKotlinCodeBlock(typeNames))
    return builder.build()
}

private fun LsiPoetCodeBlock.toKotlinCodeBlock(typeNames: List<LsiPoetTypeName>): CodeBlock {
    val builder = CodeBlock.builder()
    appendToKotlinCodeBlock(builder, typeNames)
    return builder.build()
}

private fun LsiPoetCodeBlock.appendToKotlinCodeBlock(
    builder: CodeBlock.Builder,
    typeNames: List<LsiPoetTypeName>,
) {
    if (indentation == LsiPoetCodeBlockIndentation.EXPLICIT) {
        // 空语句标记只抑制外围声明的双倍续行缩进，不产生任何源码字符。
        builder.add("«»")
    }
    parts.forEach { part ->
        when (part) {
            is LsiPoetCodePart.BeginControlFlow -> builder.beginControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(typeNames),
            )
            is LsiPoetCodePart.BracedExpression -> builder.addKotlinBracedExpression(part, typeNames)
            is LsiPoetCodePart.CharacterLiteral -> builder.add("%L", part.value.kotlinCharacterLiteral())
            LsiPoetCodePart.EndControlFlow -> builder.endControlFlow()
            LsiPoetCodePart.Indent -> builder.indent()
            is LsiPoetCodePart.Literal -> builder.add("%L", part.value)
            is LsiPoetCodePart.Name -> builder.add("%N", part.value)
            LsiPoetCodePart.NewLine -> builder.add("\n")
            is LsiPoetCodePart.NextControlFlow -> builder.nextControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(typeNames),
            )
            is LsiPoetCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return %L", value.toKotlinCodeBlock(typeNames))
            } ?: builder.addStatement("return")
            is LsiPoetCodePart.Statement -> builder.addStatement("%L", part.value.toKotlinCodeBlock(typeNames))
            is LsiPoetCodePart.StringLiteral -> builder.add("%S", part.value)
            is LsiPoetCodePart.Text -> builder.add("%L", part.value)
            is LsiPoetCodePart.TopLevelMember -> builder.add(
                "%M",
                MemberName(part.packageName, part.simpleName, part.extension),
            )
            is LsiPoetCodePart.Type -> when (part.referenceStyle) {
                LsiPoetTypeReferenceStyle.IMPORTED -> builder.add("%T", part.value.toKotlinTypeName(typeNames))
                LsiPoetTypeReferenceStyle.FULLY_QUALIFIED -> builder.add(
                    "%L",
                    part.value.toKotlinTypeName(typeNames),
                )
                LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> error(
                    "Same-package outer-qualified type references are only valid for Java declarations"
                )
            }
            LsiPoetCodePart.Unindent -> builder.unindent()
        }
    }
}

private fun CodeBlock.Builder.addKotlinBracedExpression(
    expression: LsiPoetCodePart.BracedExpression,
    typeNames: List<LsiPoetTypeName>,
) {
    if (expression.completion == LsiPoetBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("%L", expression.prefix.toKotlinCodeBlock(typeNames))
    add(" {\n")
    indent()
    add("%L", expression.body.toKotlinCodeBlock(typeNames))
    unindent()
    add("}")
    add("%L", expression.suffix.toKotlinCodeBlock(typeNames))
    add("\n")
}

private fun FunSpec.Builder.addThrownTypes(
    thrownTypes: List<LsiTypeRef>,
    typeNames: List<LsiPoetTypeName>,
) {
    if (thrownTypes.isEmpty()) {
        return
    }
    addAnnotation(
        AnnotationSpec.builder(Throws::class)
            .addMember(
                thrownTypes.joinToString(", ") { "%T::class" },
                *thrownTypes.map { type -> type.toKotlinTypeName(typeNames) }.toTypedArray(),
            )
            .build()
    )
}

private enum class KotlinModifierContext {
    TYPE,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY,
    PARAMETER,
    ACCESSOR,
}

private fun Set<LsiPoetModifier>.toKotlinModifiers(
    context: KotlinModifierContext,
): Array<KModifier> {
    return sorted().mapNotNullTo(linkedSetOf()) { modifier ->
        modifier.toKotlinModifier(context)
    }.toTypedArray()
}

private fun LsiPoetModifier.toKotlinModifier(context: KotlinModifierContext): KModifier? {
    val modifier = when (this) {
        LsiPoetModifier.PUBLIC -> KModifier.PUBLIC
        LsiPoetModifier.PROTECTED -> KModifier.PROTECTED
        LsiPoetModifier.INTERNAL -> KModifier.INTERNAL
        LsiPoetModifier.PRIVATE -> KModifier.PRIVATE
        LsiPoetModifier.ABSTRACT -> KModifier.ABSTRACT
        LsiPoetModifier.OPEN -> KModifier.OPEN
        LsiPoetModifier.FINAL -> KModifier.FINAL
        LsiPoetModifier.SEALED -> KModifier.SEALED
        LsiPoetModifier.CONST -> KModifier.CONST
        LsiPoetModifier.OVERRIDE -> KModifier.OVERRIDE
        LsiPoetModifier.INLINE -> KModifier.INLINE
        LsiPoetModifier.NOINLINE -> KModifier.NOINLINE
        LsiPoetModifier.CROSSINLINE -> KModifier.CROSSINLINE
        LsiPoetModifier.TAILREC -> KModifier.TAILREC
        LsiPoetModifier.SUSPEND -> KModifier.SUSPEND
        LsiPoetModifier.OPERATOR -> KModifier.OPERATOR
        LsiPoetModifier.INFIX -> KModifier.INFIX
        LsiPoetModifier.EXTERNAL -> KModifier.EXTERNAL
        LsiPoetModifier.LATEINIT -> KModifier.LATEINIT
        LsiPoetModifier.DATA -> KModifier.DATA
        LsiPoetModifier.VALUE -> KModifier.VALUE
        LsiPoetModifier.INNER -> KModifier.INNER
        LsiPoetModifier.VARARG -> KModifier.VARARG
        LsiPoetModifier.COMPANION,
        LsiPoetModifier.DEFAULT,
        -> null
        LsiPoetModifier.STATIC,
        LsiPoetModifier.SYNCHRONIZED,
        LsiPoetModifier.NATIVE,
        LsiPoetModifier.TRANSIENT,
        LsiPoetModifier.VOLATILE,
        -> error("KotlinPoet renderer cannot emit modifier $this for $context")
    }
    require(isAllowedInKotlin(context)) {
        "KotlinPoet renderer cannot emit modifier $this for $context"
    }
    return modifier
}

private fun LsiPoetModifier.isAllowedInKotlin(context: KotlinModifierContext): Boolean {
    return when (this) {
        LsiPoetModifier.PUBLIC,
        LsiPoetModifier.PROTECTED,
        LsiPoetModifier.INTERNAL,
        LsiPoetModifier.PRIVATE,
        -> context != KotlinModifierContext.PARAMETER
        LsiPoetModifier.ABSTRACT,
        LsiPoetModifier.OPEN,
        LsiPoetModifier.FINAL,
        -> context == KotlinModifierContext.TYPE ||
            context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY
        LsiPoetModifier.SEALED,
        LsiPoetModifier.DATA,
        LsiPoetModifier.VALUE,
        LsiPoetModifier.INNER,
        LsiPoetModifier.COMPANION,
        -> context == KotlinModifierContext.TYPE
        LsiPoetModifier.CONST,
        LsiPoetModifier.LATEINIT,
        -> context == KotlinModifierContext.PROPERTY
        LsiPoetModifier.OVERRIDE -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY
        LsiPoetModifier.INLINE -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY ||
            context == KotlinModifierContext.ACCESSOR
        LsiPoetModifier.NOINLINE,
        LsiPoetModifier.CROSSINLINE,
        LsiPoetModifier.VARARG,
        -> context == KotlinModifierContext.PARAMETER
        LsiPoetModifier.TAILREC,
        LsiPoetModifier.SUSPEND,
        LsiPoetModifier.OPERATOR,
        LsiPoetModifier.INFIX,
        -> context == KotlinModifierContext.FUNCTION
        LsiPoetModifier.EXTERNAL -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY ||
            context == KotlinModifierContext.ACCESSOR
        LsiPoetModifier.DEFAULT -> context == KotlinModifierContext.FUNCTION
        else -> false
    }
}

private fun Char.kotlinCharacterLiteral(): String {
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
