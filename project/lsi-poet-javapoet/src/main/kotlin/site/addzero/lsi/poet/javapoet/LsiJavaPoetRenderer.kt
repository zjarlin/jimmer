package site.addzero.lsi.poet.javapoet

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
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
 * 在边界内使用 JavaPoet 渲染 Java 源码。
 */
class LsiJavaPoetRenderer : LsiPoetRenderer {

    override fun render(artifact: LsiPoetArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.JAVA) {
            "JavaPoet renderer requires a Java LSI Poet file: ${artifact.qualifiedFileName}"
        }
        require(file.annotations.isEmpty()) {
            "JavaPoet renderer does not support file annotations: ${artifact.qualifiedFileName}"
        }
        val type = file.members.singleOrNull() as? LsiPoetType
            ?: error("Java LSI Poet file must contain exactly one top-level type: ${artifact.qualifiedFileName}")
        require(type.name == file.fileName) {
            "Java LSI Poet file name must match its top-level type: ${artifact.qualifiedFileName}"
        }
        val javaFile = JavaFile.builder(file.packageName, type.toJavaTypeSpec())
            .indent("    ")
            .apply {
                file.headerComment?.let { comment -> addFileComment("\$L", comment) }
            }
            .build()
        return artifact.generatedArtifact(javaFile.toString())
    }
}

private fun LsiPoetType.toJavaTypeSpec(): TypeSpec {
    val builder = when (kind) {
        LsiPoetTypeKind.CLASS -> TypeSpec.classBuilder(name)
        LsiPoetTypeKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiPoetTypeKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiPoetTypeKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
        LsiPoetTypeKind.OBJECT -> error("JavaPoet renderer cannot emit an object type: $name")
        LsiPoetTypeKind.RECORD -> error("JavaPoet 1.x renderer cannot emit a record type: $name")
    }
    builder.addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.TYPE))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toJavaSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toJavaTypeVariableName()) }
    superClass?.let { type -> builder.superclass(type.toJavaTypeName()) }
    superInterfaces.forEach { type -> builder.addSuperinterface(type.toJavaTypeName()) }
    require(superClassConstructorArguments.isEmpty()) {
        "Java superclass constructor arguments must be declared by a constructor delegation call: $name"
    }
    primaryConstructor?.let { constructor -> builder.addMethod(constructor.toJavaConstructor()) }
    enumConstants.forEach { constant -> builder.addJavaEnumConstant(constant) }
    members.forEach { member -> builder.addJavaMember(member) }
    return builder.build()
}

private fun TypeSpec.Builder.addJavaEnumConstant(constant: LsiPoetEnumConstant) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val arguments = constant.constructorArguments.toJavaArgumentList()
    val anonymousBuilder = TypeSpec.anonymousClassBuilder(arguments)
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumConstants.isEmpty()) {
            "Java enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toJavaSourceAnnotationSpec())
        }
        type.superInterfaces.forEach { superType -> anonymousBuilder.addSuperinterface(superType.toJavaTypeName()) }
        type.members.forEach { member -> anonymousBuilder.addJavaMember(member) }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addJavaMember(member: LsiPoetMember) {
    when (member) {
        is LsiPoetConstructor -> addMethod(member.toJavaConstructor())
        is LsiPoetField -> addField(member.toJavaField())
        is LsiPoetFunction -> addMethod(member.toJavaMethod())
        is LsiPoetInitializerBlock -> addJavaInitializer(member)
        is LsiPoetProperty -> error("JavaPoet renderer cannot emit a Kotlin property: ${member.name}")
        is LsiPoetType -> addType(member.toJavaTypeSpec())
    }
}

private fun TypeSpec.Builder.addJavaInitializer(initializer: LsiPoetInitializerBlock) {
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Java initializer block cannot declare annotations or documentation"
    }
    if (initializer.static) {
        addStaticBlock(initializer.body.toJavaCodeBlock())
    } else {
        addInitializerBlock(initializer.body.toJavaCodeBlock())
    }
}

private fun LsiPoetConstructor.toJavaConstructor(): MethodSpec {
    val builder = MethodSpec.constructorBuilder()
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.CONSTRUCTOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toJavaSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toJavaTypeVariableName()) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter()) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiPoetModifier.VARARG) == true) {
        builder.varargs(true)
    }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName()) }
    delegationCall?.let { delegation ->
        val target = when (delegation.target) {
            LsiPoetDelegationTarget.THIS -> "this"
            LsiPoetDelegationTarget.SUPER -> "super"
        }
        builder.addStatement("\$L(\$L)", target, delegation.arguments.toJavaArgumentList())
    }
    builder.addCode(body.toJavaCodeBlock())
    return builder.build()
}

private fun LsiPoetFunction.toJavaMethod(): MethodSpec {
    require(receiverType == null) {
        "JavaPoet renderer cannot emit an extension receiver: $name"
    }
    val builder = MethodSpec.methodBuilder(name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.FUNCTION))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toJavaSourceAnnotationSpec()) }
    if (
        LsiPoetModifier.OVERRIDE in modifiers &&
        annotations.none { annotation -> annotation.type == JAVA_LANG_OVERRIDE }
    ) {
        builder.addAnnotation(Override::class.java)
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toJavaTypeVariableName()) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter()) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiPoetModifier.VARARG) == true) {
        builder.varargs(true)
    }
    returnType?.let { type -> builder.returns(type.toJavaTypeName()) }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName()) }
    builder.addCode(body.toJavaCodeBlock())
    return builder.build()
}

private fun LsiPoetParameter.toJavaParameter(): ParameterSpec {
    require(defaultValue == null) {
        "JavaPoet renderer cannot emit a default parameter value: $name"
    }
    val parameterType = type.toJavaTypeName().let { typeName ->
        if (LsiPoetModifier.VARARG in modifiers) ArrayTypeName.of(typeName) else typeName
    }
    val builder = ParameterSpec.builder(parameterType, name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.PARAMETER))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toJavaSourceAnnotationSpec()) }
    return builder.build()
}

private fun LsiPoetField.toJavaField(): FieldSpec {
    val javaModifiers = modifiers.toJavaModifiers(JavaModifierContext.FIELD).toMutableSet()
    if (LsiPoetModifier.CONST in modifiers) {
        javaModifiers += Modifier.STATIC
        javaModifiers += Modifier.FINAL
    }
    val builder = FieldSpec.builder(type.toJavaTypeName(), name, *javaModifiers.toTypedArray())
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toJavaSourceAnnotationSpec()) }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    initializer?.let { value -> builder.initializer(value.toJavaCodeBlock()) }
    return builder.build()
}

private fun LsiPoetCodeBlock.toJavaCodeBlock(): CodeBlock {
    val builder = CodeBlock.builder()
    parts.forEach { part ->
        when (part) {
            is LsiPoetCodePart.BeginControlFlow -> builder.beginControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(),
            )
            is LsiPoetCodePart.BracedExpression -> builder.addJavaBracedExpression(part)
            is LsiPoetCodePart.CharacterLiteral -> builder.add("\$L", part.value.javaCharacterLiteral())
            LsiPoetCodePart.EndControlFlow -> builder.endControlFlow()
            LsiPoetCodePart.Indent -> builder.indent()
            is LsiPoetCodePart.Literal -> builder.add("\$L", part.value)
            is LsiPoetCodePart.Name -> builder.add("\$N", part.value)
            LsiPoetCodePart.NewLine -> builder.add("\n")
            is LsiPoetCodePart.NextControlFlow -> builder.nextControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(),
            )
            is LsiPoetCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return \$L", value.toJavaCodeBlock())
            } ?: builder.addStatement("return")
            is LsiPoetCodePart.Statement -> builder.addStatement("\$L", part.value.toJavaCodeBlock())
            is LsiPoetCodePart.StringLiteral -> builder.add("\$S", part.value)
            is LsiPoetCodePart.Text -> builder.add("\$L", part.value)
            is LsiPoetCodePart.Type -> builder.add("\$T", part.value.toJavaTypeName())
            LsiPoetCodePart.Unindent -> builder.unindent()
        }
    }
    return builder.build()
}

private fun CodeBlock.Builder.addJavaBracedExpression(
    expression: LsiPoetCodePart.BracedExpression,
) {
    if (expression.completion == LsiPoetBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("\$L", expression.prefix.toJavaCodeBlock())
    add(" {\n")
    indent()
    add("\$L", expression.body.toJavaCodeBlock())
    unindent()
    add("}")
    add("\$L", expression.suffix.toJavaCodeBlock())
    add(";\n")
}

private fun List<LsiPoetCodeBlock>.toJavaArgumentList(): CodeBlock {
    val builder = CodeBlock.builder()
    forEachIndexed { index, argument ->
        if (index != 0) {
            builder.add(", ")
        }
        builder.add("\$L", argument.toJavaCodeBlock())
    }
    return builder.build()
}

private enum class JavaModifierContext {
    TYPE,
    CONSTRUCTOR,
    FUNCTION,
    FIELD,
    PARAMETER,
}

private fun Set<LsiPoetModifier>.toJavaModifiers(
    context: JavaModifierContext,
): Array<Modifier> {
    return sorted().mapNotNullTo(linkedSetOf()) { modifier ->
        modifier.toJavaModifier(context)
    }.toTypedArray()
}

private fun LsiPoetModifier.toJavaModifier(context: JavaModifierContext): Modifier? {
    val modifier = when (this) {
        LsiPoetModifier.PUBLIC -> Modifier.PUBLIC
        LsiPoetModifier.PROTECTED -> Modifier.PROTECTED
        LsiPoetModifier.PRIVATE -> Modifier.PRIVATE
        LsiPoetModifier.ABSTRACT -> Modifier.ABSTRACT
        LsiPoetModifier.FINAL -> Modifier.FINAL
        LsiPoetModifier.STATIC -> Modifier.STATIC
        LsiPoetModifier.DEFAULT -> Modifier.DEFAULT
        LsiPoetModifier.SYNCHRONIZED -> Modifier.SYNCHRONIZED
        LsiPoetModifier.NATIVE -> Modifier.NATIVE
        LsiPoetModifier.TRANSIENT -> Modifier.TRANSIENT
        LsiPoetModifier.VOLATILE -> Modifier.VOLATILE
        LsiPoetModifier.CONST,
        LsiPoetModifier.OVERRIDE,
        LsiPoetModifier.VARARG,
        -> null
        LsiPoetModifier.INTERNAL,
        LsiPoetModifier.OPEN,
        LsiPoetModifier.SEALED,
        LsiPoetModifier.INLINE,
        LsiPoetModifier.NOINLINE,
        LsiPoetModifier.CROSSINLINE,
        LsiPoetModifier.REIFIED,
        LsiPoetModifier.TAILREC,
        LsiPoetModifier.SUSPEND,
        LsiPoetModifier.OPERATOR,
        LsiPoetModifier.INFIX,
        LsiPoetModifier.EXTERNAL,
        LsiPoetModifier.LATEINIT,
        LsiPoetModifier.DATA,
        LsiPoetModifier.VALUE,
        LsiPoetModifier.INNER,
        LsiPoetModifier.COMPANION,
        -> error("JavaPoet renderer cannot emit modifier $this for $context")
    }
    require(isAllowedInJava(context)) {
        "JavaPoet renderer cannot emit modifier $this for $context"
    }
    return modifier
}

private fun LsiPoetModifier.isAllowedInJava(context: JavaModifierContext): Boolean {
    return when (this) {
        LsiPoetModifier.PUBLIC,
        LsiPoetModifier.PROTECTED,
        LsiPoetModifier.PRIVATE,
        -> true
        LsiPoetModifier.ABSTRACT -> context == JavaModifierContext.TYPE || context == JavaModifierContext.FUNCTION
        LsiPoetModifier.FINAL -> context != JavaModifierContext.CONSTRUCTOR
        LsiPoetModifier.STATIC -> context == JavaModifierContext.TYPE ||
            context == JavaModifierContext.FUNCTION ||
            context == JavaModifierContext.FIELD
        LsiPoetModifier.DEFAULT,
        LsiPoetModifier.SYNCHRONIZED,
        LsiPoetModifier.NATIVE,
        LsiPoetModifier.OVERRIDE,
        -> context == JavaModifierContext.FUNCTION
        LsiPoetModifier.TRANSIENT,
        LsiPoetModifier.VOLATILE,
        LsiPoetModifier.CONST,
        -> context == JavaModifierContext.FIELD
        LsiPoetModifier.VARARG -> context == JavaModifierContext.PARAMETER
        else -> false
    }
}

private fun Char.javaCharacterLiteral(): String {
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

private val JAVA_LANG_OVERRIDE = LsiSymbolId.type("java.lang.Override")
