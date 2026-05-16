package site.addzero.lsi.poet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.toLsiClassName
import site.addzero.lsi.clazz.toLsiNestedClassName
import site.addzero.lsi.type.LsiType

private val MAKE_ID_ONLY_MEMBER_NAME = MemberName("org.babyfish.jimmer.kt", "makeIdOnly")

internal fun LsiClassName.toKotlinPoet(): ClassName =
    ClassName(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray()).let { className ->
        if (nullable) {
            className.copy(nullable = true) as ClassName
        } else {
            className
        }
    }

fun ClassName.toLsiPoet(): LsiClassName =
    LsiClassName(
        packageName = packageName,
        simpleNames = simpleNames,
        nullable = isNullable
    )

internal fun LsiTypeName.toKotlinPoet(): TypeName =
    when (this) {
        is LsiClassName -> toKotlinPoetClassName()
        is LsiParameterizedTypeName -> {
            val rawTypeName = rawType.copyNullable(false).toKotlinPoet()
            rawTypeName
                .parameterizedBy(typeArguments.map { it.toKotlinPoet() })
                .let { if (nullable) it.copy(nullable = true) else it }
        }
        is LsiArrayTypeName -> {
            val arrayType = primitiveArrayTypeNameOrNull(componentType)
                ?: ARRAY.parameterizedBy(componentType.toKotlinPoet())
            if (nullable) {
                arrayType.copy(nullable = true)
            } else {
                arrayType
            }
        }
        is LsiLambdaTypeName ->
            LambdaTypeName.get(
                receiver = receiverType?.toKotlinPoet(),
                parameters = parameterTypes.map { ParameterSpec.unnamed(it.toKotlinPoet()) },
                returnType = returnType.toKotlinPoet()
            ).copy(nullable = nullable)
        is LsiTypeVariableName ->
            TypeVariableName(name).copy(nullable = nullable)
        is LsiWildcardTypeName -> wildcardTypeName().let {
            if (nullable) {
                it.copy(nullable = true)
            } else {
                it
            }
        }
        LsiStarTypeName -> STAR
    }

fun TypeName.toLsiPoet(): LsiTypeName =
    when {
        this === STAR || toString() == "*" -> LsiStarTypeName
        this is ClassName -> toLsiPoet()
        this is ParameterizedTypeName && rawType == ARRAY && typeArguments.size == 1 ->
            LsiArrayTypeName(componentType = typeArguments[0].toLsiPoet(), nullable = isNullable)
        this is LambdaTypeName ->
            LsiLambdaTypeName(
                receiverType = receiver?.toLsiPoet(),
                parameterTypes = parameters.map { it.type.toLsiPoet() },
                returnType = returnType.toLsiPoet(),
                nullable = isNullable
            )
        this is ParameterizedTypeName ->
            LsiParameterizedTypeName(
                rawType = rawType.toLsiPoet().copyNullable(false),
                typeArguments = typeArguments.map { it.toLsiPoet() },
                nullable = isNullable
            )
        this is TypeVariableName ->
            LsiTypeVariableName(
                name = name,
                nullable = isNullable,
                bounds = bounds.map { it.toLsiPoet() }
            )
        this is WildcardTypeName ->
            LsiWildcardTypeName(
                producerTypes = outTypes.map { it.toLsiPoet() },
                consumerTypes = inTypes.map { it.toLsiPoet() },
                nullable = isNullable
            )
        else -> primitiveTypeNameOrNull(this)
            ?: LsiClassName.bestGuess(toString().removeSuffix("?"), nullable = isNullable)
    }

internal fun LsiType.toKotlinPoet(nullableOverride: Boolean? = null): TypeName =
    toLsiPoet(nullableOverride).toKotlinPoet()

internal fun LsiAnnotationSpec.toKotlinPoet(): AnnotationSpec {
    val builder = AnnotationSpec.builder(type.copyNullable(false).toKotlinPoet())
    useSiteTarget?.toKotlinPoet()?.let(builder::useSiteTarget)
    for (argument in positionalArguments) {
        builder.addMember("%L", argument.toKotlinPoet())
    }
    for ((name, value) in members) {
        builder.addMember("%L = %L", name, value.toKotlinPoet())
    }
    return builder.build()
}

internal fun LsiAnnotation.toKotlinPoet(): AnnotationSpec =
    toLsiPoet().toKotlinPoet()

internal fun LsiCodeBlock.toKotlinPoet(): CodeBlock =
    CodeBlock.of(format, *args.map { it.toKotlinPoetCodeArg() }.toTypedArray())

internal fun LsiFileSpec.toKotlinPoet(): FileSpec =
    FileSpec.builder(packageName, name).apply {
        indent("    ")
        this@toKotlinPoet.annotations.forEach { addAnnotation(it.toKotlinPoet()) }
        this@toKotlinPoet.memberImports.forEach { importSpec ->
            val alias = importSpec.alias
            if (alias == null) {
                addImport(importSpec.packageName, importSpec.name)
            } else {
                addAliasedImport(
                    MemberName(importSpec.packageName, importSpec.name),
                    alias
                )
            }
        }
        this@toKotlinPoet.topLevelProperties.forEach { addProperty(it.toKotlinPoet()) }
        this@toKotlinPoet.topLevelCallables.forEach { addFunction(it.toKotlinPoet()) }
        this@toKotlinPoet.types.forEach { addType(it.toKotlinPoet()) }
    }.build()

fun LsiFileSpec.renderKotlinSource(): String =
    toKotlinPoet().toString()

internal fun LsiParameterSpec.toKotlinPoet(): ParameterSpec =
    ParameterSpec.builder(name, type.toKotlinPoet()).apply {
        this@toKotlinPoet.annotations.forEach { addAnnotation(it.toKotlinPoet()) }
        this@toKotlinPoet.modifiers.mapNotNull { it.toKotlinPoet() }.forEach { addModifiers(it) }
        this@toKotlinPoet.defaultValue?.let { defaultValue(it.toKotlinPoet()) }
    }.build()

internal fun LsiPropertySpec.toKotlinPoet(): PropertySpec =
    PropertySpec.builder(name, type.toKotlinPoet()).apply {
        this@toKotlinPoet.receiverType?.let { receiver(it.toKotlinPoet()) }
        this@toKotlinPoet.annotations.forEach { addAnnotation(it.toKotlinPoet()) }
        this@toKotlinPoet.modifiers.mapNotNull { it.toKotlinPoet() }.forEach { addModifiers(it) }
        if (this@toKotlinPoet.mutable) {
            mutable()
        }
        this@toKotlinPoet.initializer?.let { initializer("%L", it.toKotlinPoet()) }
        if (this@toKotlinPoet.getterStatements.isNotEmpty()) {
            getter(
                FunSpec.getterBuilder()
                    .addCode(this@toKotlinPoet.getterStatements.toKotlinPoet())
                    .build()
            )
        }
        if (this@toKotlinPoet.setterStatements.isNotEmpty()) {
            setter(
                FunSpec.setterBuilder()
                    .addParameter("value", type.toKotlinPoet())
                    .addCode(this@toKotlinPoet.setterStatements.toKotlinPoet())
                    .build()
            )
        }
    }.build()

internal fun LsiTypeSpec.toKotlinPoet(): TypeSpec {
    val builder = when (kind) {
        LsiTypeSpecKind.CLASS -> TypeSpec.classBuilder(name)
        LsiTypeSpecKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiTypeSpecKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiTypeSpecKind.OBJECT -> TypeSpec.objectBuilder(name)
        LsiTypeSpecKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
    }
    this.annotations.forEach { builder.addAnnotation(it.toKotlinPoet()) }
    this.modifiers.mapNotNull { it.toKotlinPoet() }.forEach { builder.addModifiers(it) }
    this.typeVariables.forEach { builder.addTypeVariable(it.toKotlinPoet() as TypeVariableName) }
    val legacySuperClass = if (superClass == null && superInterfaces.isEmpty()) {
        this.superTypes.firstOrNull()
    } else {
        null
    }
    val effectiveSuperClass = superClass ?: legacySuperClass
    val effectiveSuperInterfaces =
        if (superInterfaces.isNotEmpty()) {
            superInterfaces
        } else if (superClass != null) {
            this.superTypes
        } else {
            this.superTypes.drop(1)
        }
    if (kind == LsiTypeSpecKind.INTERFACE) {
        (effectiveSuperInterfaces + listOfNotNull(effectiveSuperClass)).forEach {
            builder.addSuperinterface(it.toKotlinPoet())
        }
    } else {
        effectiveSuperClass?.let { builder.superclass(it.toKotlinPoet()) }
        effectiveSuperInterfaces.forEach { builder.addSuperinterface(it.toKotlinPoet()) }
    }
    val companionProperties = properties.filter { it.modifiers.contains(LsiModifier.STATIC) }
    properties
        .filterNot { it.modifiers.contains(LsiModifier.STATIC) }
        .forEach { builder.addProperty(it.toKotlinPoet()) }
    val primaryConstructor = callables.firstOrNull { it.kind == LsiCallableSpecKind.CONSTRUCTOR && it.primary }
    primaryConstructor?.let { callable ->
        builder.primaryConstructor(callable.toKotlinPoet())
        callable.delegateCall
            ?.takeIf { it.kind == LsiConstructorDelegateKind.SUPER }
            ?.arguments
            ?.forEach { argument ->
                builder.addSuperclassConstructorParameter("%L", argument.toKotlinPoet())
            }
    }
    val companionCallables = callables.filter { it.modifiers.contains(LsiModifier.STATIC) }
    callables
        .filterNot { it.kind == LsiCallableSpecKind.CONSTRUCTOR && it.primary }
        .filterNot { it.modifiers.contains(LsiModifier.STATIC) }
        .forEach { builder.addFunction(it.toKotlinPoet()) }
    if (companionProperties.isNotEmpty() || companionCallables.isNotEmpty()) {
        builder.addType(
            TypeSpec.companionObjectBuilder()
                .addModifiers(KModifier.PUBLIC)
                .apply {
                    companionProperties.forEach { property ->
                        addProperty(property.withoutStaticModifier().toCompanionKotlinPoet())
                    }
                    companionCallables.forEach { callable ->
                        addFunction(callable.withoutStaticModifier().toCompanionKotlinPoet())
                    }
                }
                .build()
        )
    }
    this.nestedTypes.forEach { builder.addType(it.toKotlinPoet()) }
    return builder.build()
}

internal fun LsiCallableSpec.toKotlinPoet(): FunSpec {
    val builder = when (kind) {
        LsiCallableSpecKind.CONSTRUCTOR -> FunSpec.constructorBuilder()
        LsiCallableSpecKind.FUNCTION -> FunSpec.builder(name!!)
    }
    receiverType?.let { builder.receiver(it.toKotlinPoet()) }
    annotations.forEach { builder.addAnnotation(it.toKotlinPoet()) }
    modifiers.mapNotNull { it.toKotlinPoet() }.forEach { builder.addModifiers(it) }
    typeVariables.forEach { builder.addTypeVariable(it.toKotlinPoet() as TypeVariableName) }
    parameters.forEach { builder.addParameter(it.toKotlinPoet()) }
    if (kind == LsiCallableSpecKind.FUNCTION) {
        returnType?.let { builder.returns(it.toKotlinPoet()) }
        if (thrownTypes.isNotEmpty()) {
            builder.addAnnotation(
                AnnotationSpec.builder(Throws::class)
                    .addMember(
                        thrownTypes.joinToCodeBlock { thrownType ->
                            CodeBlock.of("%T::class", thrownType.toKotlinPoet().rawTypeName())
                        }
                    )
                    .build()
            )
        }
    }
    if (kind == LsiCallableSpecKind.CONSTRUCTOR && !primary) {
        builder.applyConstructorDelegate(delegateCall)
    }
    if (statements.isNotEmpty()) {
        builder.addCode(statements.toKotlinPoet())
    }
    return builder.build()
}

internal fun LsiNameAllocator.toKotlinPoet(): NameAllocator =
    NameAllocator().also { allocator ->
        snapshot().forEach { allocator.newName(it) }
    }

internal fun LsiClass.toKotlinPoet(nameTransformer: (String) -> String = { it }): ClassName =
    toLsiClassName(nameTransformer = nameTransformer).toKotlinPoet()

internal fun LsiClass.toNestedKotlinPoet(
    namesTransformer: (List<String>) -> List<String> = { it },
): ClassName =
    toLsiNestedClassName(namesTransformer = namesTransformer).toKotlinPoet()

fun TypeName.isBuiltInType(nullable: Boolean? = null): Boolean {
    if (this !is ClassName) {
        return false
    }
    if (nullable != null && isNullable != nullable) {
        return false
    }
    if (packageName != "kotlin") {
        return false
    }
    return simpleName in setOf("Boolean", "Char", "Byte", "Short", "Int", "Long", "Float", "Double")
}

private fun LsiClassName.toKotlinPoetClassName(): ClassName = toKotlinPoet()

private fun LsiWildcardTypeName.wildcardTypeName(): TypeName =
    when {
        producerTypes.isNotEmpty() -> WildcardTypeName.producerOf(producerTypes.first().toKotlinPoet())
        consumerTypes.isNotEmpty() -> WildcardTypeName.consumerOf(consumerTypes.first().toKotlinPoet())
        else -> STAR
    }

private fun LsiAnnotationValue.toKotlinPoet(): CodeBlock =
    when (this) {
        LsiNullAnnotationValue -> CodeBlock.of("null")
        is LsiStringAnnotationValue -> CodeBlock.of("%S", value)
        is LsiLiteralAnnotationValue -> CodeBlock.of("%L", value)
        is LsiCharAnnotationValue -> CodeBlock.of("'%L'", escapeChar(value))
        is LsiEnumAnnotationValue -> CodeBlock.of("%T.%L", enumType.toKotlinPoet(), constantName)
        is LsiClassAnnotationValue -> CodeBlock.of("%T::class", className.copyNullable(false).toKotlinPoet())
        is LsiTypeAnnotationValue -> CodeBlock.of("%T::class", typeName.toKotlinPoet().rawTypeName())
        is LsiNestedAnnotationValue -> CodeBlock.of("%L", annotation.toKotlinPoet())
        is LsiArrayAnnotationValue -> CodeBlock.of("[%L]", elements.joinToCodeBlock())
        is LsiRawAnnotationValue -> CodeBlock.of("%L", value)
    }

private fun List<LsiAnnotationValue>.joinToCodeBlock(): CodeBlock =
    CodeBlock.builder().apply {
        forEachIndexed { index, value ->
            if (index > 0) {
                add(", ")
            }
            add("%L", value.toKotlinPoet())
        }
    }.build()

private fun Any?.toKotlinPoetCodeArg(): Any? =
    when (this) {
        is LsiClassName -> toKotlinPoet()
        is LsiTypeName -> toKotlinPoet()
        is LsiAnnotationSpec -> toKotlinPoet()
        is LsiCodeBlock -> toKotlinPoet()
        is LsiExpression -> toKotlinPoet()
        else -> this
    }

private fun List<LsiTypeName>.joinToCodeBlock(
    transform: (LsiTypeName) -> CodeBlock,
): CodeBlock =
    CodeBlock.builder().apply {
        forEachIndexed { index, value ->
            if (index > 0) {
                add(", ")
            }
            add("%L", transform(value))
        }
    }.build()

private fun FunSpec.Builder.applyConstructorDelegate(
    delegateCall: LsiConstructorDelegateCall?,
) {
    val arguments = delegateCall?.arguments?.map { it.toKotlinPoet() } ?: return
    when (delegateCall.kind) {
        LsiConstructorDelegateKind.THIS -> callThisConstructor(arguments)
        LsiConstructorDelegateKind.SUPER -> callSuperConstructor(arguments)
    }
}

private fun List<LsiStatement>.toKotlinPoet(): CodeBlock =
    CodeBlock.builder().apply {
        for (statement in this@toKotlinPoet) {
            when (statement) {
                is LsiAssignmentStatement ->
                    add("%L = %L\n", statement.target.toKotlinPoet(), statement.expression.toKotlinPoet())
                is LsiExpressionStatement -> add("%L\n", statement.expression.toKotlinPoet())
                is LsiIfStatement -> {
                    add("if (%L) {\n", statement.condition.toKotlinPoet())
                    indent()
                    add(statement.thenStatements.toKotlinPoet())
                    unindent()
                    if (statement.elseStatements.isEmpty()) {
                        add("}\n")
                    } else {
                        add("} else {\n")
                        indent()
                        add(statement.elseStatements.toKotlinPoet())
                        unindent()
                        add("}\n")
                    }
                }
                is LsiTryStatement -> {
                    add("try {\n")
                    indent()
                    add(statement.tryStatements.toKotlinPoet())
                    unindent()
                    if (statement.finallyStatements.isEmpty()) {
                        add("}\n")
                    } else {
                        add("} finally {\n")
                        indent()
                        add(statement.finallyStatements.toKotlinPoet())
                        unindent()
                        add("}\n")
                    }
                }
                is LsiForRangeStatement -> {
                    add(
                        "for (%L in %L until %L) {\n",
                        statement.variableName,
                        statement.from.toKotlinPoet(),
                        statement.until.toKotlinPoet(),
                    )
                    indent()
                    add(statement.statements.toKotlinPoet())
                    unindent()
                    add("}\n")
                }
                is LsiPropertySetStatement ->
                    add("%L.%L = %L\n", statement.receiver.toKotlinPoet(), statement.name, statement.expression.toKotlinPoet())
                is LsiThrowStatement ->
                    add("throw %L\n", statement.expression.toKotlinPoet())
                is LsiVariableDeclarationStatement -> {
                    add(if (statement.mutable) "var %L" else "val %L", statement.name)
                    statement.type?.let { add(": %T", it.toKotlinPoet()) }
                    add(" = %L\n", statement.initializer.toKotlinPoet())
                }
                is LsiReturnStatement -> {
                    val expression = statement.expression
                    if (expression == null) {
                        add("return\n")
                    } else {
                        add("return %L\n", expression.toKotlinPoet())
                    }
                }
                is LsiWhenStatement -> {
                    add("when (%L) {\n", statement.subject.toKotlinPoet())
                    indent()
                    statement.cases.forEach { case ->
                        case.conditions.forEachIndexed { index, condition ->
                            if (index > 0) {
                                add(", ")
                            }
                            add("%L", condition.toKotlinPoet())
                        }
                        add(" -> {\n")
                        indent()
                        add(case.statements.toKotlinPoet())
                        unindent()
                        add("}\n")
                    }
                    add("else -> {\n")
                    indent()
                    add(statement.elseStatements.toKotlinPoet())
                    unindent()
                    add("}\n")
                    unindent()
                    add("}\n")
                }
            }
        }
    }.build()

private fun LsiExpression.toKotlinPoet(): CodeBlock =
    when (this) {
        is LsiArrayExpression -> toKotlinPoetArray()
        is LsiIntArrayExpression -> toKotlinPoetIntArray()
        is LsiArrayOfNullsExpression -> toKotlinPoetArrayOfNulls()
        is LsiBinaryExpression -> CodeBlock.of(
            "(%L %L %L)",
            left.toKotlinPoet(),
            operator.toKotlinPoet(),
            right.toKotlinPoet()
        )
        is LsiCastExpression -> CodeBlock.of("%L as %T", expression.toKotlinPoet(), type.toKotlinPoet())
        is LsiSafeCastExpression -> CodeBlock.of("%L as? %T", expression.toKotlinPoet(), type.toKotlinPoet())
        is LsiCallableReferenceExpression -> toKotlinPoetCallableReference()
        is LsiClassLiteralExpression -> CodeBlock.of("%T::class", type.toKotlinPoet().rawTypeName())
        is LsiCollectionElementExpression -> CodeBlock.of("%L[%L]", receiver.toKotlinPoet(), index.toKotlinPoet())
        is LsiCollectionSizeExpression -> CodeBlock.of("%L.size", receiver.toKotlinPoetMemberAccessReceiver())
        is LsiJavaClassExpression -> CodeBlock.of("%T::class.java", type.toKotlinPoet().rawTypeName())
        is LsiCodeExpression -> code.toKotlinPoet()
        is LsiCallExpression -> toKotlinPoetCall()
        is LsiEnumConstantExpression -> CodeBlock.of("%T.%L", type.toKotlinPoet(), constantName)
        is LsiIndexAccessExpression -> CodeBlock.of("%L[%L]", receiver.toKotlinPoet(), index.toKotlinPoet())
        is LsiLengthExpression -> CodeBlock.of("%L.length", receiver.toKotlinPoetMemberAccessReceiver())
        is LsiLambdaExpression -> toKotlinPoetLambda()
        is LsiListExpression -> toKotlinPoetList()
        is LsiLiteralExpression -> value.toKotlinPoetLiteral()
        is LsiMakeIdOnlyExpression -> CodeBlock.of(
            "%M(%T::class, %L)",
            MAKE_ID_ONLY_MEMBER_NAME,
            targetType.toKotlinPoet().rawTypeName(),
            idExpression.toKotlinPoet()
        )
        is LsiNameExpression -> CodeBlock.of("%L", name)
        is LsiNewExpression -> CodeBlock.of("%T(%L)", type.toKotlinPoet(), arguments.joinToCallArguments())
        is LsiPropertyGetExpression -> CodeBlock.of("%L.%L", receiver.toKotlinPoetMemberAccessReceiver(), name.toKotlinMemberName())
        is LsiPropertyAccessExpression -> CodeBlock.of("%L.%L", receiver.toKotlinPoetMemberAccessReceiver(), name.toKotlinMemberName())
        is LsiTypeExpression -> CodeBlock.of("%T", type.toKotlinPoet())
        is LsiVarargExpression -> CodeBlock.of("*%L", expression.toKotlinPoet())
        LsiNullExpression -> CodeBlock.of("null")
        LsiSuperExpression -> CodeBlock.of("super")
        LsiThisExpression -> CodeBlock.of("this")
    }

private fun LsiCallExpression.toKotlinPoetCall(): CodeBlock {
    val trailingLambda = arguments.lastOrNull() as? LsiLambdaExpression
    val normalArguments = if (trailingLambda != null) {
        arguments.dropLast(1)
    } else {
        arguments
    }
    return CodeBlock.builder().apply {
        receiver?.let { add("%L.", it.toKotlinPoetMemberAccessReceiver()) }
        add("%L", name.toKotlinMemberName())
        if (typeArguments.isNotEmpty()) {
            add("<%L>", typeArguments.joinToCodeBlock { CodeBlock.of("%T", it.toKotlinPoet()) })
        }
        if (normalArguments.isNotEmpty() || trailingLambda == null) {
            add("(")
            normalArguments.forEachIndexed { index, argument ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", argument.toKotlinPoet())
            }
            add(")")
        }
        trailingLambda?.let { add(" %L", it.toKotlinPoet()) }
    }.build()
}

private fun LsiCallableReferenceExpression.toKotlinPoetCallableReference(): CodeBlock =
    if (receiverLabel != null) {
        CodeBlock.of("this@%L::%L", receiverLabel, name.toKotlinMemberName())
    } else {
        CodeBlock.of("%L::%L", receiver.toKotlinPoet(), name.toKotlinMemberName())
    }

private fun LsiExpression.toKotlinPoetMemberAccessReceiver(): CodeBlock =
    when (this) {
        is LsiCastExpression, is LsiSafeCastExpression -> CodeBlock.of("(%L)", toKotlinPoet())
        else -> toKotlinPoet()
    }

private fun String.toKotlinMemberName(): String =
    if (KOTLIN_PLAIN_IDENTIFIER_REGEX.matches(this) && this !in KOTLIN_KEYWORDS) {
        this
    } else {
        "`$this`"
    }

private val KOTLIN_PLAIN_IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

private val KOTLIN_KEYWORDS = setOf(
    "as",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "interface",
    "is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "val",
    "var",
    "when",
    "while",
)

private fun LsiArrayOfNullsExpression.toKotlinPoetArrayOfNulls(): CodeBlock {
    val base = CodeBlock.of("arrayOfNulls<%T>(%L)", elementType.toKotlinPoet(), size.toKotlinPoet())
    return castTo?.let { CodeBlock.of("%L as %T", base, it.toKotlinPoet()) } ?: base
}

private fun LsiArrayExpression.toKotlinPoetArray(): CodeBlock =
    if (elements.isEmpty()) {
        CodeBlock.of("arrayOf<%T>()", elementType.toKotlinPoet())
    } else {
        CodeBlock.builder().apply {
            add("arrayOf(")
            elements.forEachIndexed { index, element ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", element.toKotlinPoet())
            }
            add(")")
        }.build()
    }

private fun LsiIntArrayExpression.toKotlinPoetIntArray(): CodeBlock =
    if (elements.isEmpty()) {
        CodeBlock.of("intArrayOf()")
    } else {
        CodeBlock.builder().apply {
            add("intArrayOf(")
            elements.forEachIndexed { index, element ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", element.toKotlinPoet())
            }
            add(")")
        }.build()
    }

private fun LsiListExpression.toKotlinPoetList(): CodeBlock =
    if (elements.isEmpty()) {
        CodeBlock.of("emptyList()")
    } else {
        CodeBlock.builder().apply {
            add("listOf(")
            elements.forEachIndexed { index, element ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", element.toKotlinPoet())
            }
            add(")")
        }.build()
    }

private fun List<LsiExpression>.joinToCallArguments(): CodeBlock =
    CodeBlock.builder().apply {
        forEachIndexed { index, argument ->
            if (index > 0) {
                add(", ")
            }
            add("%L", argument.toKotlinPoet())
        }
    }.build()

private fun Any?.toKotlinPoetLiteral(): CodeBlock =
    when (this) {
        null -> CodeBlock.of("null")
        is String -> CodeBlock.of("%S", this)
        is Char -> CodeBlock.of("'%L'", escapeChar(this))
        is Long -> CodeBlock.of("%L", "${this}L")
        is Float -> toKotlinPoetFloatLiteral()
        else -> CodeBlock.of("%L", this)
    }

private fun Float.toKotlinPoetFloatLiteral(): CodeBlock =
    when {
        isNaN() -> CodeBlock.of("%T.NaN", FLOAT)
        this == Float.POSITIVE_INFINITY -> CodeBlock.of("%T.POSITIVE_INFINITY", FLOAT)
        this == Float.NEGATIVE_INFINITY -> CodeBlock.of("%T.NEGATIVE_INFINITY", FLOAT)
        else -> CodeBlock.of("%L", "${this}F")
    }

private fun LsiCallableSpec.withoutStaticModifier(): LsiCallableSpec =
    copy(modifiers = modifiers - LsiModifier.STATIC)

private fun LsiPropertySpec.withoutStaticModifier(): LsiPropertySpec =
    copy(modifiers = modifiers - LsiModifier.STATIC)

private fun LsiCallableSpec.toCompanionKotlinPoet(): FunSpec =
    toKotlinPoet().toBuilder().apply {
        addAnnotation(JvmStatic::class)
    }.build()

private fun LsiPropertySpec.toCompanionKotlinPoet(): PropertySpec =
    toKotlinPoet().toBuilder().apply {
        if (canRenderAsJvmField()) {
            addAnnotation(JvmField::class)
        }
    }.build()

private fun LsiPropertySpec.canRenderAsJvmField(): Boolean =
    !mutable &&
        initializer != null &&
        getterStatements.isEmpty() &&
        setterStatements.isEmpty()

private fun LsiLambdaExpression.toKotlinPoetLambda(): CodeBlock =
    when (mode) {
        LsiLambdaMode.EXPRESSION -> {
            val prefix = lambdaParameterPrefix()
            if (prefix == null) {
                CodeBlock.of("{ %L }", expression!!.toKotlinPoet())
            } else {
                CodeBlock.of("{ %L %L }", prefix, expression!!.toKotlinPoet())
            }
        }
        LsiLambdaMode.UNIT -> {
            if (statements.size == 1 && statements[0] is LsiExpressionStatement) {
                val body = (statements[0] as LsiExpressionStatement).expression.toKotlinPoet()
                val prefix = lambdaParameterPrefix()
                if (prefix == null) {
                    CodeBlock.of("{ %L }", body)
                } else {
                    CodeBlock.of("{ %L %L }", prefix, body)
                }
            } else {
                CodeBlock.builder()
                    .add("{")
                    .apply {
                        val prefix = lambdaParameterPrefix()
                        if (prefix == null) {
                            add("\n")
                        } else {
                            add(" %L\n", prefix)
                        }
                    }
                    .indent()
                    .add(statements.toKotlinPoet())
                    .unindent()
                    .add("}")
                    .build()
            }
        }
        LsiLambdaMode.BLOCK ->
            CodeBlock.builder()
                .add("{")
                .apply {
                    val prefix = lambdaParameterPrefix()
                    if (prefix == null) {
                        add("\n")
                    } else {
                        add(" %L\n", prefix)
                    }
                }
                .indent()
                .add(statements.toKotlinPoet())
                .unindent()
                .add("}")
                .build()
    }

private fun LsiLambdaExpression.lambdaParameterPrefix(): String? =
    parameterNames
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ", postfix = " ->")

private fun LsiAnnotationUseSiteTarget.toKotlinPoet(): AnnotationSpec.UseSiteTarget? =
    runCatching { AnnotationSpec.UseSiteTarget.valueOf(name) }.getOrNull()

private fun TypeName.rawTypeName(): TypeName =
    when (this) {
        is ParameterizedTypeName -> rawType.copy(nullable = false)
        else -> copy(nullable = false)
    }

private fun primitiveTypeNameOrNull(typeName: TypeName): LsiTypeName? =
    when (typeName) {
        BOOLEAN -> LsiClassName.bestGuess("kotlin.Boolean", nullable = typeName.isNullable)
        BYTE -> LsiClassName.bestGuess("kotlin.Byte", nullable = typeName.isNullable)
        SHORT -> LsiClassName.bestGuess("kotlin.Short", nullable = typeName.isNullable)
        INT -> LsiClassName.bestGuess("kotlin.Int", nullable = typeName.isNullable)
        LONG -> LsiClassName.bestGuess("kotlin.Long", nullable = typeName.isNullable)
        FLOAT -> LsiClassName.bestGuess("kotlin.Float", nullable = typeName.isNullable)
        DOUBLE -> LsiClassName.bestGuess("kotlin.Double", nullable = typeName.isNullable)
        CHAR -> LsiClassName.bestGuess("kotlin.Char", nullable = typeName.isNullable)
        STRING -> LsiClassName.bestGuess("kotlin.String", nullable = typeName.isNullable)
        ANY -> LsiClassName.bestGuess("kotlin.Any", nullable = typeName.isNullable)
        BOOLEAN_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Boolean"), nullable = typeName.isNullable)
        BYTE_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Byte"), nullable = typeName.isNullable)
        SHORT_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Short"), nullable = typeName.isNullable)
        INT_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Int"), nullable = typeName.isNullable)
        LONG_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Long"), nullable = typeName.isNullable)
        FLOAT_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Float"), nullable = typeName.isNullable)
        DOUBLE_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Double"), nullable = typeName.isNullable)
        CHAR_ARRAY -> LsiArrayTypeName(LsiClassName.bestGuess("kotlin.Char"), nullable = typeName.isNullable)
        else -> null
    }

private fun primitiveArrayTypeNameOrNull(typeName: LsiTypeName): TypeName? =
    when (typeName) {
        is LsiClassName -> when (typeName.copyNullable(false).canonicalName) {
            "kotlin.Boolean", "java.lang.Boolean", "boolean" -> BOOLEAN_ARRAY
            "kotlin.Byte", "java.lang.Byte", "byte" -> BYTE_ARRAY
            "kotlin.Short", "java.lang.Short", "short" -> SHORT_ARRAY
            "kotlin.Int", "java.lang.Integer", "int" -> INT_ARRAY
            "kotlin.Long", "java.lang.Long", "long" -> LONG_ARRAY
            "kotlin.Float", "java.lang.Float", "float" -> FLOAT_ARRAY
            "kotlin.Double", "java.lang.Double", "double" -> DOUBLE_ARRAY
            "kotlin.Char", "java.lang.Character", "char" -> CHAR_ARRAY
            else -> null
        }
        else -> null
    }

private fun LsiModifier.toKotlinPoet(): KModifier? =
    when (this) {
        LsiModifier.PUBLIC -> KModifier.PUBLIC
        LsiModifier.INTERNAL -> KModifier.INTERNAL
        LsiModifier.PROTECTED -> KModifier.PROTECTED
        LsiModifier.PRIVATE -> KModifier.PRIVATE
        LsiModifier.FINAL -> KModifier.FINAL
        LsiModifier.ABSTRACT -> KModifier.ABSTRACT
        LsiModifier.OPEN -> KModifier.OPEN
        LsiModifier.OVERRIDE -> KModifier.OVERRIDE
        LsiModifier.CONST -> KModifier.CONST
        LsiModifier.LATEINIT -> KModifier.LATEINIT
        LsiModifier.DATA -> KModifier.DATA
        LsiModifier.SEALED -> KModifier.SEALED
        LsiModifier.VARARG -> KModifier.VARARG
        LsiModifier.INLINE -> KModifier.INLINE
        LsiModifier.SUSPEND -> KModifier.SUSPEND
        LsiModifier.STATIC -> null
    }

private fun LsiBinaryOperator.toKotlinPoet(): String =
    when (this) {
        LsiBinaryOperator.PLUS -> "+"
        LsiBinaryOperator.TIMES -> "*"
        LsiBinaryOperator.LESS_THAN -> "<"
        LsiBinaryOperator.LESS_THAN_OR_EQUALS -> "<="
        LsiBinaryOperator.GREATER_THAN -> ">"
        LsiBinaryOperator.GREATER_THAN_OR_EQUALS -> ">="
        LsiBinaryOperator.EQUALS -> "=="
        LsiBinaryOperator.NOT_EQUALS -> "!="
        LsiBinaryOperator.IDENTITY_EQUALS -> "==="
        LsiBinaryOperator.IDENTITY_NOT_EQUALS -> "!=="
        LsiBinaryOperator.AND -> "&&"
        LsiBinaryOperator.OR -> "||"
    }

private fun escapeChar(value: Char): String =
    when (value) {
        '\\' -> "\\\\"
        '\'' -> "\\'"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> value.toString()
    }
