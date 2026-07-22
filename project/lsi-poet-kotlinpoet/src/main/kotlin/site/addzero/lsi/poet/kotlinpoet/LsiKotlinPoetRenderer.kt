package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetArtifact
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

/**
 * 在边界内使用 KotlinPoet 渲染 Kotlin 源码。
 */
class LsiKotlinPoetRenderer : LsiPoetRenderer {

    override fun render(artifact: LsiPoetArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.KOTLIN) {
            "KotlinPoet renderer requires a Kotlin LSI Poet file: ${artifact.qualifiedFileName}"
        }
        val builder = FileSpec.builder(file.packageName, file.fileName)
            .indent("    ")
        file.headerComment?.let { comment -> builder.addFileComment("%L", comment) }
        file.annotations.forEach { annotation ->
            builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec())
        }
        file.members.forEach { member -> builder.addKotlinTopLevelMember(member) }
        return artifact.generatedArtifact(builder.build().toString())
    }
}

private fun FileSpec.Builder.addKotlinTopLevelMember(member: LsiPoetMember) {
    when (member) {
        is LsiPoetFunction -> addFunction(member.toKotlinFunction())
        is LsiPoetProperty -> addProperty(member.toKotlinProperty())
        is LsiPoetType -> addType(member.toKotlinTypeSpec())
        is LsiPoetConstructor -> error("KotlinPoet renderer cannot emit a top-level constructor")
        is LsiPoetField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiPoetInitializerBlock -> error("KotlinPoet renderer cannot emit a top-level initializer block")
    }
}

private fun LsiPoetType.toKotlinTypeSpec(): TypeSpec {
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
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toKotlinTypeVariableName()) }
    primaryConstructor?.let { constructor -> builder.primaryConstructor(constructor.toKotlinConstructor(primary = true)) }
    superClass?.let { type -> builder.superclass(type.toKotlinTypeName()) }
    superClassConstructorArguments.forEach { argument ->
        builder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock())
    }
    superInterfaces.forEach { type -> builder.addSuperinterface(type.toKotlinTypeName()) }
    enumConstants.forEach { constant -> builder.addKotlinEnumConstant(constant) }
    members.forEach { member -> builder.addKotlinMember(member) }
    return builder.build()
}

private fun TypeSpec.Builder.addKotlinEnumConstant(constant: LsiPoetEnumConstant) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val anonymousBuilder = TypeSpec.anonymousClassBuilder()
    constant.constructorArguments.forEach { argument ->
        anonymousBuilder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock())
    }
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumConstants.isEmpty()) {
            "Kotlin enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toKotlinSourceAnnotationSpec())
        }
        type.superInterfaces.forEach { superType -> anonymousBuilder.addSuperinterface(superType.toKotlinTypeName()) }
        type.members.forEach { member -> anonymousBuilder.addKotlinMember(member) }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addKotlinMember(member: LsiPoetMember) {
    when (member) {
        is LsiPoetConstructor -> addFunction(member.toKotlinConstructor(primary = false))
        is LsiPoetField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiPoetFunction -> addFunction(member.toKotlinFunction())
        is LsiPoetInitializerBlock -> addKotlinInitializer(member)
        is LsiPoetProperty -> addProperty(member.toKotlinProperty())
        is LsiPoetType -> addType(member.toKotlinTypeSpec())
    }
}

private fun TypeSpec.Builder.addKotlinInitializer(initializer: LsiPoetInitializerBlock) {
    require(!initializer.static) {
        "KotlinPoet renderer cannot emit a static initializer block"
    }
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Kotlin initializer block cannot declare annotations or documentation"
    }
    addInitializerBlock(initializer.body.toKotlinCodeBlock())
}

private fun LsiPoetConstructor.toKotlinConstructor(primary: Boolean): FunSpec {
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
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter()) }
    builder.addThrownTypes(thrownTypes)
    delegationCall?.let { delegation ->
        val arguments = delegation.arguments.map(LsiPoetCodeBlock::toKotlinCodeBlock).toTypedArray()
        when (delegation.target) {
            LsiPoetDelegationTarget.THIS -> builder.callThisConstructor(*arguments)
            LsiPoetDelegationTarget.SUPER -> builder.callSuperConstructor(*arguments)
        }
    }
    builder.addCode(body.toKotlinCodeBlock())
    return builder.build()
}

private fun LsiPoetFunction.toKotlinFunction(): FunSpec {
    val builder = FunSpec.builder(name)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.FUNCTION))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toKotlinTypeVariableName()) }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName()) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter()) }
    returnType?.let { type -> builder.returns(type.toKotlinTypeName()) }
    builder.addThrownTypes(thrownTypes)
    builder.addCode(body.toKotlinCodeBlock())
    return builder.build()
}

private fun LsiPoetParameter.toKotlinParameter(): ParameterSpec {
    val builder = ParameterSpec.builder(name, type.toKotlinTypeName())
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PARAMETER))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    defaultValue?.let { value -> builder.defaultValue(value.toKotlinCodeBlock()) }
    return builder.build()
}

private fun LsiPoetProperty.toKotlinProperty(): PropertySpec {
    val builder = PropertySpec.builder(name, type.toKotlinTypeName())
        .mutable(mutable)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PROPERTY))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName()) }
    initializer?.let { value -> builder.initializer(value.toKotlinCodeBlock()) }
    getter?.let { accessor -> builder.getter(accessor.toKotlinGetter()) }
    setter?.let { accessor -> builder.setter(accessor.toKotlinSetter(type)) }
    return builder.build()
}

private fun LsiPoetAccessor.toKotlinGetter(): FunSpec {
    require(parameterAnnotations.isEmpty()) {
        "Kotlin getter cannot declare setter parameter annotations"
    }
    val builder = FunSpec.getterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    builder.addCode(body.toKotlinCodeBlock())
    return builder.build()
}

private fun LsiPoetAccessor.toKotlinSetter(type: LsiTypeRef): FunSpec {
    val parameter = ParameterSpec.builder("value", type.toKotlinTypeName())
        .apply {
            parameterAnnotations.forEach { annotation ->
                addAnnotation(annotation.toKotlinSourceAnnotationSpec())
            }
        }
        .build()
    val builder = FunSpec.setterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
        .addParameter(parameter)
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec()) }
    builder.addCode(body.toKotlinCodeBlock())
    return builder.build()
}

private fun LsiPoetCodeBlock.toKotlinCodeBlock(): CodeBlock {
    val builder = CodeBlock.builder()
    parts.forEach { part ->
        when (part) {
            is LsiPoetCodePart.BeginControlFlow -> builder.beginControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(),
            )
            is LsiPoetCodePart.BracedExpression -> builder.addKotlinBracedExpression(part)
            is LsiPoetCodePart.CharacterLiteral -> builder.add("%L", part.value.kotlinCharacterLiteral())
            LsiPoetCodePart.EndControlFlow -> builder.endControlFlow()
            LsiPoetCodePart.Indent -> builder.indent()
            is LsiPoetCodePart.Literal -> builder.add("%L", part.value)
            is LsiPoetCodePart.Name -> builder.add("%N", part.value)
            LsiPoetCodePart.NewLine -> builder.add("\n")
            is LsiPoetCodePart.NextControlFlow -> builder.nextControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(),
            )
            is LsiPoetCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return %L", value.toKotlinCodeBlock())
            } ?: builder.addStatement("return")
            is LsiPoetCodePart.Statement -> builder.addStatement("%L", part.value.toKotlinCodeBlock())
            is LsiPoetCodePart.StringLiteral -> builder.add("%S", part.value)
            is LsiPoetCodePart.Text -> builder.add("%L", part.value)
            is LsiPoetCodePart.Type -> builder.add("%T", part.value.toKotlinTypeName())
            LsiPoetCodePart.Unindent -> builder.unindent()
        }
    }
    return builder.build()
}

private fun CodeBlock.Builder.addKotlinBracedExpression(
    expression: LsiPoetCodePart.BracedExpression,
) {
    if (expression.completion == LsiPoetBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("%L", expression.prefix.toKotlinCodeBlock())
    add(" {\n")
    indent()
    add("%L", expression.body.toKotlinCodeBlock())
    unindent()
    add("}")
    add("%L", expression.suffix.toKotlinCodeBlock())
    add("\n")
}

private fun FunSpec.Builder.addThrownTypes(thrownTypes: List<LsiTypeRef>) {
    if (thrownTypes.isEmpty()) {
        return
    }
    addAnnotation(
        AnnotationSpec.builder(Throws::class)
            .addMember(
                thrownTypes.joinToString(", ") { "%T::class" },
                *thrownTypes.map { type -> type.toKotlinTypeName() }.toTypedArray(),
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
        LsiPoetModifier.REIFIED,
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
