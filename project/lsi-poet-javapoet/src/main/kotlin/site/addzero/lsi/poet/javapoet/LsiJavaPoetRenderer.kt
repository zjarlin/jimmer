package site.addzero.lsi.poet.javapoet

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetBracedExpressionCompletion
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBlockIndentation
import site.addzero.lsi.poet.LsiPoetCodePart
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetDelegationTarget
import site.addzero.lsi.poet.LsiPoetEnumConstant
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetInitializerBlock
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle

/**
 * 在边界内使用 JavaPoet 渲染 Java 源码。
 */
class LsiJavaPoetRenderer : LsiPoetRenderer {

    /** 将单个 LSI 类型引用渲染为可嵌入现有 JavaPoet 声明的类型。 */
    fun renderTypeName(
        type: LsiTypeRef,
        typeNames: List<LsiPoetTypeName>,
    ): TypeName {
        return type.toJavaTypeName(typeNames)
    }

    /** 将任意 LSI Poet 代码块渲染为可嵌入现有 JavaPoet 声明的代码块。 */
    fun renderCodeBlock(
        codeBlock: LsiPoetCodeBlock,
        typeNames: List<LsiPoetTypeName>,
    ): CodeBlock {
        return codeBlock.toJavaCodeBlock(typeNames)
    }

    /** 将单个 LSI 类型渲染为可嵌入现有 JavaPoet 声明的结构。 */
    fun renderType(
        type: LsiPoetType,
        typeNames: List<LsiPoetTypeName>,
    ): TypeSpec {
        return type.toJavaTypeSpec(typeNames, currentPackageName = null)
    }

    /** 将单个 LSI 函数渲染为可嵌入现有 JavaPoet 类型的结构。 */
    fun renderFunction(
        function: LsiPoetFunction,
        typeNames: List<LsiPoetTypeName>,
    ): MethodSpec {
        return function.toJavaMethod(typeNames)
    }

    /** 将单个 LSI 字段渲染为可嵌入现有 JavaPoet 类型的结构。 */
    fun renderField(
        field: LsiPoetField,
        typeNames: List<LsiPoetTypeName>,
        currentPackageName: String? = null,
    ): FieldSpec {
        return field.toJavaField(typeNames, currentPackageName)
    }

    /** 将单个 LSI Poet 注解渲染为可嵌入现有 JavaPoet 声明的结构。 */
    fun renderAnnotation(
        annotation: LsiPoetAnnotation,
        typeNames: List<LsiPoetTypeName>,
    ): AnnotationSpec {
        return annotation.toJavaSourceAnnotationSpec(typeNames)
    }

    /** 按声明顺序将 LSI Poet 注解列表渲染为 JavaPoet 结构。 */
    fun renderAnnotations(
        annotations: List<LsiPoetAnnotation>,
        typeNames: List<LsiPoetTypeName>,
    ): List<AnnotationSpec> {
        return annotations.map { annotation -> renderAnnotation(annotation, typeNames) }
    }

    override fun render(artifact: LsiPoetArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.JAVA) {
            "JavaPoet renderer requires a Java LSI Poet file: ${artifact.qualifiedFileName}"
        }
        require(file.annotations.isEmpty()) {
            "JavaPoet renderer does not support file annotations: ${artifact.qualifiedFileName}"
        }
        require(file.imports.isEmpty()) {
            "JavaPoet renderer does not support explicit imports: ${artifact.qualifiedFileName}"
        }
        val type = file.members.singleOrNull() as? LsiPoetType
            ?: error("Java LSI Poet file must contain exactly one top-level type: ${artifact.qualifiedFileName}")
        require(type.name == file.fileName) {
            "Java LSI Poet file name must match its top-level type: ${artifact.qualifiedFileName}"
        }
        val javaFile = JavaFile.builder(
            file.packageName,
            type.toJavaTypeSpec(artifact.typeNames, file.packageName),
        )
            .indent("    ")
            .apply {
                file.headerComment?.let { comment -> addFileComment("\$L", comment) }
            }
            .build()
        return artifact.generatedArtifact(javaFile.toString())
    }
}

private fun LsiPoetType.toJavaTypeSpec(
    typeNames: List<LsiPoetTypeName>,
    currentPackageName: String?,
): TypeSpec {
    require(nameStyle == LsiPoetNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin type name: $name"
    }
    val builder = when (kind) {
        LsiPoetTypeKind.CLASS -> TypeSpec.classBuilder(name)
        LsiPoetTypeKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiPoetTypeKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiPoetTypeKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
        LsiPoetTypeKind.OBJECT -> error("JavaPoet renderer cannot emit an object type: $name")
        LsiPoetTypeKind.RECORD -> error("JavaPoet 1.x renderer cannot emit a record type: $name")
    }
    builder.addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.TYPE))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    superClass?.let { type -> builder.superclass(type.toJavaTypeName(typeNames)) }
    superInterfaces.forEach { type ->
        builder.addSuperinterface(type.toJavaTypeName(typeNames))
    }
    require(superClassConstructorArguments.isEmpty()) {
        "Java superclass constructor arguments must be declared by a constructor delegation call: $name"
    }
    primaryConstructor?.let { constructor ->
        builder.addMethod(constructor.toJavaConstructor(typeNames))
    }
    enumConstants.forEach { constant ->
        builder.addJavaEnumConstant(constant, typeNames, currentPackageName)
    }
    members.forEach { member -> builder.addJavaMember(member, typeNames, currentPackageName) }
    return builder.build()
}

private fun TypeSpec.Builder.addJavaEnumConstant(
    constant: LsiPoetEnumConstant,
    typeNames: List<LsiPoetTypeName>,
    currentPackageName: String?,
) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val arguments = constant.constructorArguments.toJavaArgumentList(typeNames)
    val anonymousBuilder = TypeSpec.anonymousClassBuilder(arguments)
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumConstants.isEmpty()) {
            "Java enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
        }
        type.superInterfaces.forEach { superType ->
            anonymousBuilder.addSuperinterface(superType.toJavaTypeName(typeNames))
        }
        type.members.forEach { member ->
            anonymousBuilder.addJavaMember(member, typeNames, currentPackageName)
        }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addJavaMember(
    member: LsiPoetMember,
    typeNames: List<LsiPoetTypeName>,
    currentPackageName: String?,
) {
    when (member) {
        is LsiPoetConstructor -> addMethod(member.toJavaConstructor(typeNames))
        is LsiPoetField -> addField(member.toJavaField(typeNames, currentPackageName))
        is LsiPoetFunction -> addMethod(member.toJavaMethod(typeNames))
        is LsiPoetInitializerBlock -> addJavaInitializer(member, typeNames)
        is LsiPoetProperty -> error("JavaPoet renderer cannot emit a Kotlin property: ${member.name}")
        is LsiPoetType -> addType(member.toJavaTypeSpec(typeNames, currentPackageName))
    }
}

private fun TypeSpec.Builder.addJavaInitializer(
    initializer: LsiPoetInitializerBlock,
    typeNames: List<LsiPoetTypeName>,
) {
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Java initializer block cannot declare annotations or documentation"
    }
    if (initializer.static) {
        addStaticBlock(initializer.body.toJavaCodeBlock(typeNames))
    } else {
        addInitializerBlock(initializer.body.toJavaCodeBlock(typeNames))
    }
}

private fun LsiPoetConstructor.toJavaConstructor(
    typeNames: List<LsiPoetTypeName>,
): MethodSpec {
    val builder = MethodSpec.constructorBuilder()
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.CONSTRUCTOR))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter(typeNames)) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiPoetModifier.VARARG) == true) {
        builder.varargs(true)
    }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName(typeNames)) }
    delegationCall?.let { delegation ->
        val target = when (delegation.target) {
            LsiPoetDelegationTarget.THIS -> "this"
            LsiPoetDelegationTarget.SUPER -> "super"
        }
        builder.addStatement("\$L(\$L)", target, delegation.arguments.toJavaArgumentList(typeNames))
    }
    builder.addCode(body.toJavaCodeBlock(typeNames))
    return builder.build()
}

private fun LsiPoetFunction.toJavaMethod(
    typeNames: List<LsiPoetTypeName>,
): MethodSpec {
    require(nameStyle == LsiPoetNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin function name: $name"
    }
    require(receiverType == null) {
        "JavaPoet renderer cannot emit an extension receiver: $name"
    }
    require(reifiedTypeParameterIds.isEmpty()) {
        "JavaPoet renderer cannot emit reified type parameters: $name"
    }
    require(bodyStyle == LsiPoetBodyStyle.BLOCK) {
        "JavaPoet renderer cannot emit an expression function body: $name"
    }
    val builder = MethodSpec.methodBuilder(name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.FUNCTION))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    if (
        LsiPoetModifier.OVERRIDE in modifiers &&
        annotations.none { annotation -> annotation.type == JAVA_LANG_OVERRIDE }
    ) {
        builder.addAnnotation(Override::class.java)
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter(typeNames)) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiPoetModifier.VARARG) == true) {
        builder.varargs(true)
    }
    returnType?.let { type -> builder.returns(type.toJavaTypeName(typeNames)) }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName(typeNames)) }
    builder.addCode(body.toJavaCodeBlock(typeNames))
    return builder.build()
}

private fun LsiPoetParameter.toJavaParameter(
    typeNames: List<LsiPoetTypeName>,
): ParameterSpec {
    require(nameStyle == LsiPoetNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin parameter name: $name"
    }
    require(defaultValue == null) {
        "JavaPoet renderer cannot emit a default parameter value: $name"
    }
    val parameterType = type.toJavaTypeName(typeNames).let { typeName ->
        if (LsiPoetModifier.VARARG in modifiers) ArrayTypeName.of(typeName) else typeName
    }
    val builder = ParameterSpec.builder(parameterType, name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.PARAMETER))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    return builder.build()
}

private fun LsiPoetField.toJavaField(
    typeNames: List<LsiPoetTypeName>,
    currentPackageName: String?,
): FieldSpec {
    val javaModifiers = modifiers.toJavaModifiers(JavaModifierContext.FIELD).toMutableSet()
    if (LsiPoetModifier.CONST in modifiers) {
        javaModifiers += Modifier.STATIC
        javaModifiers += Modifier.FINAL
    }
    val builder = FieldSpec.builder(
        type.toJavaTypeName(typeNames, typeReferenceStyle, currentPackageName),
        name,
        *javaModifiers.toTypedArray(),
    )
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    initializer?.let { value -> builder.initializer(value.toJavaCodeBlock(typeNames)) }
    return builder.build()
}

private fun LsiPoetCodeBlock.toJavaCodeBlock(
    typeNames: List<LsiPoetTypeName>,
): CodeBlock {
    require(indentation == LsiPoetCodeBlockIndentation.PLATFORM_DEFAULT) {
        "JavaPoet renderer cannot honor explicit Kotlin code indentation"
    }
    val builder = CodeBlock.builder()
    parts.forEach { part ->
        when (part) {
            is LsiPoetCodePart.BeginControlFlow -> builder.beginControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(typeNames),
            )
            is LsiPoetCodePart.BracedExpression -> builder.addJavaBracedExpression(part, typeNames)
            is LsiPoetCodePart.CharacterLiteral -> builder.add("\$L", part.value.javaCharacterLiteral())
            LsiPoetCodePart.EndControlFlow -> builder.endControlFlow()
            LsiPoetCodePart.Indent -> builder.indent()
            is LsiPoetCodePart.Literal -> builder.add("\$L", part.value)
            is LsiPoetCodePart.Name -> builder.add("\$N", part.value)
            LsiPoetCodePart.NewLine -> builder.add("\n")
            is LsiPoetCodePart.NextControlFlow -> builder.nextControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(typeNames),
            )
            is LsiPoetCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return \$L", value.toJavaCodeBlock(typeNames))
            } ?: builder.addStatement("return")
            is LsiPoetCodePart.Statement -> builder.addStatement(
                "\$L",
                part.value.toJavaCodeBlock(typeNames),
            )
            is LsiPoetCodePart.StringLiteral -> builder.add("\$S", part.value)
            is LsiPoetCodePart.Text -> builder.add("\$L", part.value)
            is LsiPoetCodePart.TopLevelMember -> error(
                "JavaPoet renderer cannot emit a Kotlin top-level member reference: " +
                    "${part.packageName}.${part.simpleName}"
            )
            is LsiPoetCodePart.Type -> when (part.referenceStyle) {
                LsiPoetTypeReferenceStyle.IMPORTED -> builder.add(
                    "\$T",
                    part.value.toJavaTypeName(typeNames),
                )
                LsiPoetTypeReferenceStyle.FULLY_QUALIFIED -> builder.add(
                    "\$L",
                    part.value.toJavaTypeName(typeNames),
                )
                LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> error(
                    "Same-package outer-qualified type references require a declaration type position"
                )
            }
            LsiPoetCodePart.Unindent -> builder.unindent()
        }
    }
    return builder.build()
}

private fun CodeBlock.Builder.addJavaBracedExpression(
    expression: LsiPoetCodePart.BracedExpression,
    typeNames: List<LsiPoetTypeName>,
) {
    if (expression.completion == LsiPoetBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("\$L", expression.prefix.toJavaCodeBlock(typeNames))
    add(" {\n")
    indent()
    add("\$L", expression.body.toJavaCodeBlock(typeNames))
    unindent()
    add("}")
    add("\$L", expression.suffix.toJavaCodeBlock(typeNames))
    add(";\n")
}

private fun List<LsiPoetCodeBlock>.toJavaArgumentList(
    typeNames: List<LsiPoetTypeName>,
): CodeBlock {
    val builder = CodeBlock.builder()
    forEachIndexed { index, argument ->
        if (index != 0) {
            builder.add(", ")
        }
        builder.add("\$L", argument.toJavaCodeBlock(typeNames))
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
