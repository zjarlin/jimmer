package site.addzero.lsi.poet

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.NameAllocator
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
import javax.lang.model.element.Modifier

private val JAVA_POET_PLACEHOLDER_PATTERN = Regex("%([TLSN])")
private val IMMUTABLE_OBJECTS_CLASS_NAME = ClassName.get("org.babyfish.jimmer", "ImmutableObjects")

internal fun LsiClassName.toJavaPoet(): ClassName =
    ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())

fun ClassName.toLsiPoet(): LsiClassName =
    LsiClassName(
        packageName = packageName(),
        simpleNames = simpleNames(),
        nullable = false
    )

internal fun LsiTypeName.toJavaPoet(): TypeName =
    toJavaPoet(boxPrimitives = false)

private fun LsiTypeName.toJavaPoet(boxPrimitives: Boolean): TypeName =
    when (this) {
        is LsiClassName -> specialJavaTypeNameOrNull(copyNullable(false).canonicalName)
            ?: primitiveTypeNameOrNull(copyNullable(false).canonicalName)?.let { primitiveType ->
                if (boxPrimitives) {
                    primitiveType.box()
                } else {
                    primitiveType
                }
            }
            ?: toJavaPoet()
        is LsiParameterizedTypeName ->
            ParameterizedTypeName.get(
                rawType.copyNullable(false).toJavaPoet(),
                *typeArguments.map { it.toJavaPoet(boxPrimitives = true) }.toTypedArray()
            )
        is LsiArrayTypeName ->
            primitiveArrayTypeNameOrNull(componentType)
                ?: ArrayTypeName.of(componentType.toJavaPoet(boxPrimitives = false))
        is LsiLambdaTypeName ->
            error("Kotlin-only LsiPoet node: LsiLambdaTypeName cannot be rendered by JavaPoet")
        is LsiTypeVariableName -> {
            val base = TypeVariableName.get(name)
            if (bounds.isEmpty()) {
                base
            } else {
                base.withBounds(bounds.map { it.toJavaPoet(boxPrimitives = true) })
            }
        }
        is LsiWildcardTypeName -> when {
            producerTypes.isNotEmpty() -> WildcardTypeName.subtypeOf(producerTypes.first().toJavaPoet(boxPrimitives = true))
            consumerTypes.isNotEmpty() -> WildcardTypeName.supertypeOf(consumerTypes.first().toJavaPoet(boxPrimitives = true))
            else -> WildcardTypeName.subtypeOf(TypeName.OBJECT)
        }
        LsiStarTypeName -> WildcardTypeName.subtypeOf(TypeName.OBJECT)
    }

fun TypeName.toLsiPoet(): LsiTypeName =
    when (this) {
        is ClassName -> toLsiPoet()
        is ParameterizedTypeName ->
            LsiParameterizedTypeName(
                rawType = rawType.toLsiPoet(),
                typeArguments = typeArguments.map { it.toLsiPoet() }
            )
        is ArrayTypeName ->
            LsiArrayTypeName(componentType = componentType.toLsiPoet())
        is TypeVariableName ->
            LsiTypeVariableName(name = name, bounds = bounds.map { it.toLsiPoet() })
        is WildcardTypeName ->
            LsiWildcardTypeName(
                producerTypes = upperBounds.filterNot { it == TypeName.OBJECT }.map { it.toLsiPoet() },
                consumerTypes = lowerBounds.map { it.toLsiPoet() }
            )
        else -> primitiveLsiTypeNameOrNull(this) ?: LsiClassName.bestGuess(toString())
    }

internal fun LsiAnnotationSpec.toJavaPoet(): AnnotationSpec {
    require(useSiteTarget == null) {
        "JavaPoet renderer does not support Kotlin use-site target: $useSiteTarget"
    }
    val builder = AnnotationSpec.builder(type.toJavaPoet())
    for (argument in positionalArguments) {
        builder.addMember("value", "\$L", argument.toJavaPoet())
    }
    for ((name, value) in members) {
        builder.addMember(name, "\$L", value.toJavaPoet())
    }
    return builder.build()
}

internal fun LsiCodeBlock.toJavaPoet(): CodeBlock =
    CodeBlock.of(toJavaPoetFormat(), *args.map { it.toJavaPoetCodeArg() }.toTypedArray())

internal fun LsiFileSpec.toJavaPoet(): JavaFile {
    validateJavaRenderable("file '$qualifiedName'")
    require(memberImports.isEmpty()) {
        "JavaPoet bridge does not support member imports"
    }
    require(topLevelProperties.isEmpty()) {
        "JavaPoet bridge does not support top-level properties"
    }
    require(topLevelCallables.isEmpty()) {
        "JavaPoet bridge does not support top-level callables"
    }
    require(types.size == 1) {
        "JavaPoet bridge currently expects exactly one top-level type"
    }
    return JavaFile.builder(packageName, types.single().toJavaPoet())
        .indent("    ")
        .build()
}

fun LsiFileSpec.renderJavaSource(): String =
    toJavaPoet().toString()

internal fun LsiParameterSpec.toJavaPoet(): ParameterSpec {
    validateJavaRenderable("parameter '$name'")
    return ParameterSpec.builder(type.toJavaPoet(), name).apply {
        this@toJavaPoet.annotations.forEach { addAnnotation(it.toJavaPoet()) }
        this@toJavaPoet.modifiers.toJavaPoetModifiers(defaultPublic = false).forEach { addModifiers(it) }
    }.build()
}

internal fun LsiTypeSpec.toJavaPoet(): TypeSpec {
    validateJavaRenderable("type '$name'")
    require(kind != LsiTypeSpecKind.OBJECT) {
        "Kotlin-only LsiPoet node: OBJECT cannot be rendered by JavaPoet"
    }
    val builder = when (kind) {
        LsiTypeSpecKind.CLASS -> TypeSpec.classBuilder(name)
        LsiTypeSpecKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiTypeSpecKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiTypeSpecKind.OBJECT -> error("Unreachable")
        LsiTypeSpecKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
    }
    this.annotations.forEach { builder.addAnnotation(it.toJavaPoet()) }
    this.modifiers.toJavaPoetModifiers(defaultPublic = true).forEach { builder.addModifiers(it) }
    this.typeVariables.forEach { builder.addTypeVariable(it.toJavaPoet() as TypeVariableName) }
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
            builder.addSuperinterface(it.toJavaPoet())
        }
    } else {
        effectiveSuperClass?.let { builder.superclass(it.toJavaPoet()) }
        effectiveSuperInterfaces.forEach { builder.addSuperinterface(it.toJavaPoet()) }
    }
    this.properties.forEach { property ->
        builder.addJavaMembers(property, inInterface = kind == LsiTypeSpecKind.INTERFACE)
    }
    callables.forEach { callable ->
        val constructorAssignedProperties = properties.filter { it.isAssignedFromConstructor(callable) }
        callable.toJavaPoetVariants(
            constructorAssignedProperties = constructorAssignedProperties,
            inInterface = kind == LsiTypeSpecKind.INTERFACE,
        ).forEach(builder::addMethod)
    }
    this.nestedTypes.forEach { builder.addType(it.toJavaPoet()) }
    return builder.build()
}

internal fun LsiPropertySpec.toJavaPoet(): FieldSpec {
    validateJavaRenderable("property '$name'")
    val fieldModifiers = backingFieldModifiers.ifEmpty { modifiers }
    return FieldSpec.builder(type.toJavaPoet(), name).apply {
        require(this@toJavaPoet.receiverType == null) {
            "JavaPoet bridge does not support extension properties"
        }
        require(this@toJavaPoet.setterStatements.isEmpty()) {
            "JavaPoet renderer does not support property setterStatements"
        }
        this@toJavaPoet.fieldAnnotations(inAccessor = false).forEach { addAnnotation(it.toJavaPoet()) }
        fieldModifiers.toJavaPoetModifiers(defaultPublic = true).forEach { addModifiers(it) }
        if (!this@toJavaPoet.mutable && !fieldModifiers.contains(LsiModifier.ABSTRACT)) {
            addModifiers(Modifier.FINAL)
        }
        this@toJavaPoet.initializer?.let { initializer("\$L", it.toJavaPoet()) }
    }.build()
}

internal fun LsiCallableSpec.toJavaPoet(
    constructorAssignedProperties: List<LsiPropertySpec> = emptyList(),
    inInterface: Boolean = false,
): MethodSpec {
    validateJavaRenderable("callable '${name ?: "<init>"}'")
    require(receiverType == null) {
        "JavaPoet bridge does not support extension functions"
    }
    val builder = when (kind) {
        LsiCallableSpecKind.CONSTRUCTOR -> MethodSpec.constructorBuilder()
        LsiCallableSpecKind.FUNCTION -> MethodSpec.methodBuilder(name!!)
    }
    annotations.forEach { builder.addAnnotation(it.toJavaPoet()) }
    if (modifiers.contains(LsiModifier.OVERRIDE)) {
        builder.addAnnotation(Override::class.java)
    }
    modifiers
        .filterNot { it == LsiModifier.OVERRIDE }
        .toJavaPoetModifiers(defaultPublic = true)
        .forEach { builder.addModifiers(it) }
    builder.applyJavaInterfaceDefaultModifier(
        inInterface = inInterface,
        hasBody = statements.isNotEmpty(),
        modifiers = modifiers,
    )
    typeVariables.forEach { builder.addTypeVariable(it.toJavaPoet() as TypeVariableName) }
    parameters.forEach { builder.addParameter(it.toJavaPoet()) }
    if (kind == LsiCallableSpecKind.FUNCTION) {
        returnType?.let { builder.returns(it.toJavaPoet()) }
        thrownTypes.forEach { builder.addException(it.toJavaPoet()) }
    }
    if (kind == LsiCallableSpecKind.CONSTRUCTOR) {
        builder.applyConstructorDelegate(delegateCall)
        constructorAssignedProperties.forEach { property ->
            builder.addStatement("this.\$L = \$L", property.name, property.name)
        }
    }
    if (statements.isNotEmpty()) {
        builder.addCode(statements.toJavaPoet())
    }
    return builder.build()
}

private fun LsiCallableSpec.toJavaPoetVariants(
    constructorAssignedProperties: List<LsiPropertySpec> = emptyList(),
    inInterface: Boolean = false,
): List<MethodSpec> {
    val methods = mutableListOf(
        toJavaPoet(
            constructorAssignedProperties = constructorAssignedProperties,
            inInterface = inInterface,
        )
    )
    methods += javaDefaultArgumentOverloads(inInterface = inInterface)
    return methods
}

private fun LsiCallableSpec.javaDefaultArgumentOverloads(inInterface: Boolean): List<MethodSpec> {
    val defaultParameterIndexes = parameters.mapIndexedNotNull { index, parameter ->
        index.takeIf { parameter.defaultValue != null }
    }
    if (defaultParameterIndexes.isEmpty()) {
        return emptyList()
    }
    val seenSignatures = linkedSetOf<String>()
    return buildList {
        for (omitCount in 1..defaultParameterIndexes.size) {
            val omittedIndexes = defaultParameterIndexes.takeLast(omitCount).toSet()
            val remainingParameters = parameters.filterIndexed { index, _ -> index !in omittedIndexes }
            if (remainingParameters.size == parameters.size) {
                continue
            }
            val signature = remainingParameters.joinToString(
                prefix = "${kind}:${name ?: "<init>"}(",
                separator = ",",
                postfix = ")"
            ) { it.type.toJavaPoet().toString() }
            if (!seenSignatures.add(signature)) {
                continue
            }
            add(javaDefaultArgumentOverload(remainingParameters, omittedIndexes, inInterface = inInterface))
        }
    }
}

private fun LsiCallableSpec.javaDefaultArgumentOverload(
    remainingParameters: List<LsiParameterSpec>,
    omittedIndexes: Set<Int>,
    inInterface: Boolean,
): MethodSpec {
    val invocationArguments = parameters.mapIndexed { index, parameter ->
        if (index in omittedIndexes) {
            LsiCodeExpression(
                parameter.defaultValue
                    ?: error("Internal bug: omitted Java overload parameter '${parameter.name}' must define defaultValue")
            )
        } else {
            LsiNameExpression(parameter.name)
        }
    }
    val modifiersWithoutOverride = modifiers - LsiModifier.OVERRIDE
    return when (kind) {
        LsiCallableSpecKind.CONSTRUCTOR ->
            MethodSpec.constructorBuilder().apply {
                this@javaDefaultArgumentOverload.annotations.forEach { addAnnotation(it.toJavaPoet()) }
                modifiersWithoutOverride.mapNotNull { it.toJavaPoet() }.forEach { addModifiers(it) }
                this@javaDefaultArgumentOverload.typeVariables.forEach {
                    addTypeVariable(it.toJavaPoet() as TypeVariableName)
                }
                remainingParameters.forEach { addParameter(it.toJavaPoet()) }
                addStatement("this(\$L)", invocationArguments.joinToCallArguments())
            }.build()

        LsiCallableSpecKind.FUNCTION ->
            MethodSpec.methodBuilder(name!!).apply {
                this@javaDefaultArgumentOverload.annotations.forEach { addAnnotation(it.toJavaPoet()) }
                modifiersWithoutOverride.mapNotNull { it.toJavaPoet() }.forEach { addModifiers(it) }
                applyJavaInterfaceDefaultModifier(
                    inInterface = inInterface,
                    hasBody = true,
                    modifiers = this@javaDefaultArgumentOverload.modifiers,
                )
                this@javaDefaultArgumentOverload.typeVariables.forEach {
                    addTypeVariable(it.toJavaPoet() as TypeVariableName)
                }
                remainingParameters.forEach { addParameter(it.toJavaPoet()) }
                this@javaDefaultArgumentOverload.returnType?.let { returns(it.toJavaPoet()) }
                this@javaDefaultArgumentOverload.thrownTypes.forEach { addException(it.toJavaPoet()) }
                val invocation = CodeBlock.builder().apply {
                    if (!this@javaDefaultArgumentOverload.modifiers.contains(LsiModifier.STATIC)) {
                        add("this.")
                    }
                    add("\$L(\$L)", this@javaDefaultArgumentOverload.name, invocationArguments.joinToCallArguments())
                }.build()
                val renderedReturnType = this@javaDefaultArgumentOverload.returnType?.toJavaPoet()
                if (renderedReturnType == null || renderedReturnType == TypeName.VOID) {
                    addStatement("\$L", invocation)
                } else {
                    addStatement("return \$L", invocation)
                }
            }.build()
    }
}

internal fun LsiNameAllocator.toJavaPoet(): NameAllocator =
    NameAllocator().also { allocator ->
        snapshot().forEach { allocator.newName(it) }
    }

private fun LsiFileSpec.validateJavaRenderable(path: String) {
    annotations.forEach { it.validateJavaRenderable("$path annotation") }
    types.forEach { type -> type.validateJavaRenderable("$path -> type '${type.name}'") }
}

private fun LsiTypeSpec.validateJavaRenderable(path: String) {
    require(kind != LsiTypeSpecKind.OBJECT) {
        "Kotlin-only LsiPoet node at $path: OBJECT cannot be rendered by JavaPoet"
    }
    annotations.forEach { it.validateJavaRenderable("$path annotation") }
    typeVariables.forEach { it.validateJavaRenderable("$path type-variable '${it.name}'") }
    superClass?.validateJavaRenderable("$path superClass")
    superInterfaces.forEachIndexed { index, typeName ->
        typeName.validateJavaRenderable("$path superInterface[$index]")
    }
    superTypes.forEachIndexed { index, typeName ->
        typeName.validateJavaRenderable("$path superType[$index]")
    }
    properties.forEach { property ->
        property.validateJavaRenderable("$path property '${property.name}'")
    }
    callables.forEach { callable ->
        callable.validateJavaRenderable("$path callable '${callable.name ?: "<init>"}'")
    }
    nestedTypes.forEach { nestedType ->
        nestedType.validateJavaRenderable("$path nested-type '${nestedType.name}'")
    }
}

private fun LsiPropertySpec.validateJavaRenderable(path: String) {
    require(receiverType == null) {
        "JavaPoet bridge does not support extension properties at $path"
    }
    type.validateJavaRenderable("$path type")
    annotations.forEach { annotation ->
        annotation.validateJavaRenderable(
            path = "$path annotation",
            allowedAccessorUseSiteTargets = if (shouldRenderAsAccessor()) {
                setOf(LsiAnnotationUseSiteTarget.GET, LsiAnnotationUseSiteTarget.SET, LsiAnnotationUseSiteTarget.FIELD)
            } else {
                setOf(LsiAnnotationUseSiteTarget.FIELD)
            }
        )
    }
    getterStatements.forEachIndexed { index, statement ->
        statement.validateJavaRenderable("$path getter[$index]")
    }
    setterStatements.forEachIndexed { index, statement ->
        statement.validateJavaRenderable("$path setter[$index]")
    }
}

private fun LsiCallableSpec.validateJavaRenderable(path: String) {
    require(receiverType == null) {
        "JavaPoet bridge does not support extension functions at $path"
    }
    annotations.forEach { it.validateJavaRenderable("$path annotation") }
    typeVariables.forEach { it.validateJavaRenderable("$path type-variable '${it.name}'") }
    parameters.forEach { parameter ->
        parameter.validateJavaRenderable("$path parameter '${parameter.name}'")
    }
    returnType?.validateJavaRenderable("$path returnType")
    thrownTypes.forEachIndexed { index, typeName ->
        typeName.validateJavaRenderable("$path throws[$index]")
    }
    delegateCall?.arguments?.forEachIndexed { index, expression ->
        expression.validateJavaRenderable("$path delegate-argument[$index]")
    }
    statements.forEachIndexed { index, statement ->
        statement.validateJavaRenderable("$path statement[$index]")
    }
}

private fun LsiParameterSpec.validateJavaRenderable(path: String) {
    annotations.forEach { it.validateJavaRenderable("$path annotation") }
    type.validateJavaRenderable("$path type")
    defaultValue?.validateJavaRenderable("$path defaultValue")
}

private fun LsiTypeVariableName.validateJavaRenderable(path: String) {
    bounds.forEachIndexed { index, bound ->
        bound.validateJavaRenderable("$path bound[$index]")
    }
}

private fun LsiAnnotationSpec.validateJavaRenderable(
    path: String,
    allowedAccessorUseSiteTargets: Set<LsiAnnotationUseSiteTarget> = emptySet(),
) {
    require(useSiteTarget == null || useSiteTarget in allowedAccessorUseSiteTargets) {
        if (allowedAccessorUseSiteTargets.isNotEmpty()) {
            "JavaPoet accessor renderer only supports null/${allowedAccessorUseSiteTargets.joinToString("/")} annotation target at $path: $useSiteTarget"
        } else {
            "JavaPoet renderer does not support Kotlin use-site target at $path: $useSiteTarget"
        }
    }
    type.validateJavaRenderable("$path type")
}

private fun LsiTypeName.validateJavaRenderable(path: String) {
    when (this) {
        is LsiArrayTypeName -> {
            componentType.validateJavaRenderable("$path[]")
        }
        is LsiClassName -> Unit
        is LsiLambdaTypeName -> require(false) {
            "Kotlin-only LsiPoet node at $path: LsiLambdaTypeName cannot be rendered by JavaPoet"
        }
        is LsiParameterizedTypeName -> {
            rawType.validateJavaRenderable("$path rawType")
            typeArguments.forEachIndexed { index, typeName ->
                typeName.validateJavaRenderable("$path arg[$index]")
            }
        }
        LsiStarTypeName -> Unit
        is LsiTypeVariableName -> validateJavaRenderable(path)
        is LsiWildcardTypeName -> {
            producerTypes.forEachIndexed { index, typeName ->
                typeName.validateJavaRenderable("$path out[$index]")
            }
            consumerTypes.forEachIndexed { index, typeName ->
                typeName.validateJavaRenderable("$path in[$index]")
            }
        }
    }
}

private fun LsiStatement.validateJavaRenderable(path: String) {
    when (this) {
        is LsiAssignmentStatement -> {
            target.validateJavaRenderable("$path target")
            expression.validateJavaRenderable("$path expression")
        }
        is LsiExpressionStatement -> expression.validateJavaRenderable("$path expression")
        is LsiIfStatement -> {
            condition.validateJavaRenderable("$path condition")
            thenStatements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path then[$index]")
            }
            elseStatements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path else[$index]")
            }
        }
        is LsiTryStatement -> {
            tryStatements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path try[$index]")
            }
            finallyStatements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path finally[$index]")
            }
        }
        is LsiForRangeStatement -> {
            from.validateJavaRenderable("$path from")
            until.validateJavaRenderable("$path until")
            statements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path body[$index]")
            }
        }
        is LsiPropertySetStatement -> {
            receiver.validateJavaRenderable("$path receiver")
            expression.validateJavaRenderable("$path expression")
        }
        is LsiReturnStatement -> expression?.validateJavaRenderable("$path return")
        is LsiThrowStatement -> expression.validateJavaRenderable("$path throw")
        is LsiVariableDeclarationStatement -> {
            type?.validateJavaRenderable("$path type")
            initializer.validateJavaRenderable("$path initializer")
        }
        is LsiWhenStatement -> {
            subject.validateJavaRenderable("$path subject")
            cases.forEachIndexed { caseIndex, case ->
                case.conditions.forEachIndexed { conditionIndex, condition ->
                    condition.validateJavaRenderable("$path case[$caseIndex].condition[$conditionIndex]")
                }
                case.statements.forEachIndexed { statementIndex, statement ->
                    statement.validateJavaRenderable("$path case[$caseIndex].statement[$statementIndex]")
                }
            }
            elseStatements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path else[$index]")
            }
        }
    }
}

private fun LsiExpression.validateJavaRenderable(path: String) {
    when (this) {
        is LsiArrayExpression -> {
            elementType.validateJavaRenderable("$path elementType")
            elements.forEachIndexed { index, expression ->
                expression.validateJavaRenderable("$path element[$index]")
            }
        }
        is LsiIntArrayExpression -> {
            elements.forEachIndexed { index, expression ->
                expression.validateJavaRenderable("$path element[$index]")
            }
        }
        is LsiArrayOfNullsExpression -> {
            elementType.validateJavaRenderable("$path elementType")
            size.validateJavaRenderable("$path size")
            castTo?.validateJavaRenderable("$path cast")
        }
        is LsiBinaryExpression -> {
            left.validateJavaRenderable("$path left")
            right.validateJavaRenderable("$path right")
        }
        is LsiCallableReferenceExpression -> {
            require(receiverLabel == null) {
                "JavaPoet renderer does not support labeled callable reference at $path: this@$receiverLabel::$name"
            }
            receiver.validateJavaRenderable("$path receiver")
        }
        is LsiCallExpression -> {
            receiver?.validateJavaRenderable("$path receiver")
            typeArguments.forEachIndexed { index, typeName ->
                typeName.validateJavaRenderable("$path typeArgument[$index]")
            }
            arguments.forEachIndexed { index, expression ->
                expression.validateJavaRenderable("$path argument[$index]")
            }
        }
        is LsiClassLiteralExpression -> type.validateJavaRenderable("$path type")
        is LsiCollectionElementExpression -> {
            receiver.validateJavaRenderable("$path receiver")
            index.validateJavaRenderable("$path index")
        }
        is LsiCollectionSizeExpression -> receiver.validateJavaRenderable("$path receiver")
        is LsiJavaClassExpression -> type.validateJavaRenderable("$path type")
        is LsiCastExpression -> {
            type.validateJavaRenderable("$path castType")
            expression.validateJavaRenderable("$path expression")
        }
        is LsiSafeCastExpression -> {
            type.validateJavaRenderable("$path castType")
            expression.validateJavaRenderable("$path expression")
        }
        is LsiCodeExpression -> code.validateJavaRenderable("$path code")
        is LsiEnumConstantExpression -> Unit
        is LsiIndexAccessExpression -> {
            receiver.validateJavaRenderable("$path receiver")
            index.validateJavaRenderable("$path index")
        }
        is LsiLengthExpression -> receiver.validateJavaRenderable("$path receiver")
        is LsiLambdaExpression -> {
            expression?.validateJavaRenderable("$path expression")
            statements.forEachIndexed { index, statement ->
                statement.validateJavaRenderable("$path lambda[$index]")
            }
        }
        is LsiListExpression -> {
            elements.forEachIndexed { index, expression ->
                expression.validateJavaRenderable("$path element[$index]")
            }
        }
        is LsiLiteralExpression -> Unit
        is LsiMakeIdOnlyExpression -> {
            targetType.validateJavaRenderable("$path targetType")
            idExpression.validateJavaRenderable("$path idExpression")
        }
        is LsiNameExpression -> Unit
        is LsiNewExpression -> {
            type.validateJavaRenderable("$path type")
            arguments.forEachIndexed { index, expression ->
                expression.validateJavaRenderable("$path argument[$index]")
            }
        }
        is LsiPropertyGetExpression -> {
            receiver.validateJavaRenderable("$path receiver")
            type.validateJavaRenderable("$path type")
        }
        is LsiPropertyAccessExpression -> receiver.validateJavaRenderable("$path receiver")
        is LsiTypeExpression -> type.validateJavaRenderable("$path type")
        is LsiVarargExpression -> expression.validateJavaRenderable("$path vararg")
        LsiNullExpression,
        LsiSuperExpression,
        LsiThisExpression -> Unit
    }
}

private fun LsiCodeBlock.validateJavaRenderable(path: String) {
    val matched = findKotlinOnlyRawCode()
    require(matched == null) {
        val match = matched!!.match.orEmpty()
        "JavaPoet renderer does not support Kotlin-only raw code (${matched.description}) at $path: " +
            if (match.isNotEmpty()) {
                "'$match' in '${matched.snippet}'"
            } else {
                "'${matched.snippet}'"
            }
    }
    args.forEachIndexed { index, arg ->
        when (arg) {
            is LsiAnnotationSpec -> {
                require(arg.useSiteTarget == null) {
                    "JavaPoet renderer does not support Kotlin use-site target at $path arg[$index]"
                }
            }
            is LsiClassName -> arg.validateJavaRenderable("$path arg[$index]")
            is LsiTypeName -> arg.validateJavaRenderable("$path arg[$index]")
            is LsiCodeBlock -> arg.validateJavaRenderable("$path arg[$index]")
            is LsiExpression -> arg.validateJavaRenderable("$path arg[$index]")
        }
    }
}

private fun LsiAnnotationValue.toJavaPoet(): CodeBlock =
    when (this) {
        LsiNullAnnotationValue -> CodeBlock.of("null")
        is LsiStringAnnotationValue -> CodeBlock.of("\$S", value)
        is LsiLiteralAnnotationValue -> CodeBlock.of("\$L", value)
        is LsiCharAnnotationValue -> CodeBlock.of("'\$L'", escapeChar(value))
        is LsiEnumAnnotationValue -> CodeBlock.of("\$T.\$L", enumType.toJavaPoet(), constantName)
        is LsiClassAnnotationValue -> CodeBlock.of("\$T.class", className.toJavaPoet())
        is LsiTypeAnnotationValue -> CodeBlock.of("\$T.class", typeName.toJavaPoet())
        is LsiNestedAnnotationValue -> CodeBlock.of("\$L", annotation.toJavaPoet())
        is LsiArrayAnnotationValue -> CodeBlock.of("{ \$L }", elements.joinToJavaCodeBlock())
        is LsiRawAnnotationValue -> CodeBlock.of("\$L", value)
    }

private fun List<LsiAnnotationValue>.joinToJavaCodeBlock(): CodeBlock =
    CodeBlock.builder().apply {
        forEachIndexed { index, value ->
            if (index > 0) {
                add(", ")
            }
            add("\$L", value.toJavaPoet())
        }
    }.build()

private fun Any?.toJavaPoetCodeArg(): Any? =
    when (this) {
        is LsiClassName -> toJavaPoet()
        is LsiTypeName -> toJavaPoet()
        is LsiAnnotationSpec -> toJavaPoet()
        is LsiCodeBlock -> toJavaPoet()
        is LsiExpression -> toJavaPoet()
        else -> this
    }

private fun LsiCodeBlock.toJavaPoetFormat(): String =
    JAVA_POET_PLACEHOLDER_PATTERN.replace(format) { matchResult ->
        "$" + matchResult.groupValues[1]
    }

private fun MethodSpec.Builder.applyConstructorDelegate(
    delegateCall: LsiConstructorDelegateCall?,
) {
    val delegate = delegateCall ?: return
    val keyword = when (delegate.kind) {
        LsiConstructorDelegateKind.THIS -> "this"
        LsiConstructorDelegateKind.SUPER -> "super"
    }
    addStatement(
        "$keyword(${delegate.arguments.joinToString(", ") { "\$L" }})",
        *delegate.arguments.map { it.toJavaPoet() }.toTypedArray()
    )
}

private fun List<LsiStatement>.toJavaPoet(): CodeBlock =
    CodeBlock.builder().apply {
        for (statement in this@toJavaPoet) {
            when (statement) {
                is LsiAssignmentStatement ->
                    add("\$L = \$L;\n", statement.target.toJavaPoet(), statement.expression.toJavaPoet())
                is LsiExpressionStatement -> add("\$L;\n", statement.expression.toJavaPoet())
                is LsiIfStatement -> {
                    beginControlFlow("if (\$L)", statement.condition.toJavaPoet())
                    add(statement.thenStatements.toJavaPoet())
                    if (statement.elseStatements.isEmpty()) {
                        endControlFlow()
                    } else {
                        nextControlFlow("else")
                        add(statement.elseStatements.toJavaPoet())
                        endControlFlow()
                    }
                }
                is LsiTryStatement -> {
                    beginControlFlow("try")
                    add(statement.tryStatements.toJavaPoet())
                    if (statement.finallyStatements.isEmpty()) {
                        endControlFlow()
                    } else {
                        nextControlFlow("finally")
                        add(statement.finallyStatements.toJavaPoet())
                        endControlFlow()
                    }
                }
                is LsiForRangeStatement -> {
                    beginControlFlow(
                        "for (int \$L = \$L; \$L < \$L; \$L++)",
                        statement.variableName,
                        statement.from.toJavaPoet(),
                        statement.variableName,
                        statement.until.toJavaPoet(),
                        statement.variableName,
                    )
                    add(statement.statements.toJavaPoet())
                    endControlFlow()
                }
                is LsiPropertySetStatement ->
                    add(
                        "\$L.\$L(\$L);\n",
                        statement.receiver.toJavaPoetMemberAccessReceiver(),
                        statement.name.setterMethodName(),
                        statement.expression.toJavaPoet()
                    )
                is LsiThrowStatement ->
                    add("throw \$L;\n", statement.expression.toJavaPoet())
                is LsiVariableDeclarationStatement -> {
                    val type = statement.type
                        ?: error("JavaPoet bridge requires explicit type for local variable '${statement.name}'")
                    add(
                        "\$T \$L = \$L;\n",
                        type.toJavaPoet(),
                        statement.name,
                        statement.initializer.toJavaPoet()
                    )
                }
                is LsiReturnStatement -> {
                    val expression = statement.expression
                    if (expression == null) {
                        add("return;\n")
                    } else {
                        add("return \$L;\n", expression.toJavaPoet())
                    }
                }
                is LsiWhenStatement -> {
                    beginControlFlow("switch (\$L)", statement.subject.toJavaPoet())
                    statement.cases.forEach { case ->
                        case.conditions.forEach { condition ->
                            add("case \$L:\n", condition.toJavaPoet())
                        }
                        indent()
                        add(case.statements.toJavaPoet())
                        if (!case.statements.isTerminalForSwitch()) {
                            addStatement("break")
                        }
                        unindent()
                    }
                    add("default:\n")
                    indent()
                    add(statement.elseStatements.toJavaPoet())
                    if (!statement.elseStatements.isTerminalForSwitch()) {
                        addStatement("break")
                    }
                    unindent()
                    endControlFlow()
                }
            }
        }
    }.build()

private fun List<LsiStatement>.isTerminalForSwitch(): Boolean =
    lastOrNull()?.isTerminalForSwitch() == true

private fun LsiStatement.isTerminalForSwitch(): Boolean =
    when (this) {
        is LsiReturnStatement,
        is LsiThrowStatement -> true
        is LsiIfStatement ->
            thenStatements.isTerminalForSwitch() &&
                elseStatements.isNotEmpty() &&
                elseStatements.isTerminalForSwitch()
        else -> false
    }

private fun LsiExpression.toJavaPoet(): CodeBlock =
    when (this) {
        is LsiArrayExpression -> toJavaPoetArray()
        is LsiIntArrayExpression -> toJavaPoetIntArray()
        is LsiArrayOfNullsExpression -> toJavaPoetArrayOfNulls()
        is LsiBinaryExpression -> CodeBlock.of(
            "(\$L \$L \$L)",
            left.toJavaPoet(),
            operator.toJavaPoet(),
            right.toJavaPoet()
        )
        is LsiCallableReferenceExpression -> toJavaPoetCallableReference()
        is LsiClassLiteralExpression -> CodeBlock.of("\$T.class", type.toJavaPoet(boxPrimitives = false).rawJavaTypeName())
        is LsiJavaClassExpression -> CodeBlock.of("\$T.class", type.toJavaPoet(boxPrimitives = false).rawJavaTypeName())
        is LsiCastExpression -> CodeBlock.of("(\$T) \$L", type.toJavaPoet(boxPrimitives = true), expression.toJavaPoet())
        is LsiSafeCastExpression -> {
            val javaType = type.toJavaPoet(boxPrimitives = true)
            val rawType = javaType.rawJavaTypeName()
            CodeBlock.of(
                "(\$L instanceof \$T ? (\$T) \$L : null)",
                expression.toJavaPoet(),
                rawType,
                javaType,
                expression.toJavaPoet()
            )
        }
        is LsiCodeExpression -> code.toJavaPoet()
        is LsiCallExpression -> toJavaPoetCall()
        is LsiEnumConstantExpression -> CodeBlock.of("\$T.\$L", type.toJavaPoet(), constantName)
        is LsiCollectionElementExpression -> CodeBlock.of(
            "\$L.get(\$L)",
            receiver.toJavaPoet(),
            index.toJavaPoet(),
        )
        is LsiCollectionSizeExpression -> CodeBlock.of(
            "\$L.size()",
            receiver.toJavaPoetMemberAccessReceiver(),
        )
        is LsiIndexAccessExpression -> CodeBlock.of("\$L[\$L]", receiver.toJavaPoet(), index.toJavaPoet())
        is LsiLengthExpression -> CodeBlock.of(
            "\$L.length()",
            receiver.toJavaPoetMemberAccessReceiver(),
        )
        is LsiLambdaExpression -> toJavaPoetLambda()
        is LsiListExpression -> toJavaPoetList()
        is LsiLiteralExpression -> value.toJavaPoetLiteral()
        is LsiMakeIdOnlyExpression -> CodeBlock.of(
            "\$T.makeIdOnly(\$T.class, \$L)",
            IMMUTABLE_OBJECTS_CLASS_NAME,
            targetType.toJavaPoet(boxPrimitives = false).rawJavaTypeName(),
            idExpression.toJavaPoet(),
        )
        is LsiNameExpression -> CodeBlock.of("\$L", name)
        is LsiNewExpression -> CodeBlock.of("new \$T(\$L)", type.toJavaPoet(), arguments.joinToCallArguments())
        is LsiPropertyGetExpression -> CodeBlock.of(
            "\$L.\$L()",
            receiver.toJavaPoetMemberAccessReceiver(),
            name.javaGetterName(type),
        )
        is LsiPropertyAccessExpression -> CodeBlock.of("\$L.\$L", receiver.toJavaPoetMemberAccessReceiver(), name)
        is LsiTypeExpression -> CodeBlock.of("\$T", type.toJavaPoet())
        is LsiVarargExpression -> expression.toJavaPoet()
        LsiNullExpression -> CodeBlock.of("null")
        LsiSuperExpression -> CodeBlock.of("super")
        LsiThisExpression -> CodeBlock.of("this")
    }

private fun LsiCallExpression.toJavaPoetCall(): CodeBlock =
    CodeBlock.builder().apply {
        receiver?.let { add("\$L.", it.toJavaPoetMemberAccessReceiver()) }
        if (typeArguments.isNotEmpty()) {
            add("<")
            typeArguments.forEachIndexed { index, typeName ->
                if (index > 0) {
                    add(", ")
                }
                add("\$T", typeName.toJavaPoet())
            }
            add(">")
        }
        add("\$L(", name)
        arguments.forEachIndexed { index, argument ->
            if (index > 0) {
                add(", ")
            }
            add("\$L", argument.toJavaPoet())
        }
        add(")")
    }.build()

private fun LsiCallableReferenceExpression.toJavaPoetCallableReference(): CodeBlock {
    require(receiverLabel == null) {
        "JavaPoet renderer does not support labeled callable reference: this@$receiverLabel::$name"
    }
    return CodeBlock.of("\$L::\$L", receiver.toJavaPoetCallableReferenceReceiver(), name)
}

private fun LsiExpression.toJavaPoetMemberAccessReceiver(): CodeBlock =
    when (this) {
        is LsiCastExpression, is LsiSafeCastExpression -> CodeBlock.of("(\$L)", toJavaPoet())
        else -> toJavaPoet()
    }

private fun LsiExpression.toJavaPoetCallableReferenceReceiver(): CodeBlock =
    when (this) {
        is LsiCastExpression, is LsiSafeCastExpression -> CodeBlock.of("(\$L)", toJavaPoet())
        else -> toJavaPoet()
    }

private fun LsiBinaryOperator.toJavaPoet(): String =
    when (this) {
        LsiBinaryOperator.PLUS -> "+"
        LsiBinaryOperator.TIMES -> "*"
        LsiBinaryOperator.LESS_THAN -> "<"
        LsiBinaryOperator.LESS_THAN_OR_EQUALS -> "<="
        LsiBinaryOperator.GREATER_THAN -> ">"
        LsiBinaryOperator.GREATER_THAN_OR_EQUALS -> ">="
        LsiBinaryOperator.EQUALS,
        LsiBinaryOperator.IDENTITY_EQUALS -> "=="
        LsiBinaryOperator.NOT_EQUALS,
        LsiBinaryOperator.IDENTITY_NOT_EQUALS -> "!="
        LsiBinaryOperator.AND -> "&&"
        LsiBinaryOperator.OR -> "||"
    }

private fun LsiArrayOfNullsExpression.toJavaPoetArrayOfNulls(): CodeBlock {
    val arrayCode = CodeBlock.of("new \$T[\$L]", elementType.toJavaPoet(), size.toJavaPoet())
    return castTo?.let { CodeBlock.of("(\$T) \$L", it.toJavaPoet(), arrayCode) } ?: arrayCode
}

private fun LsiArrayExpression.toJavaPoetArray(): CodeBlock =
    CodeBlock.builder().apply {
        add("new \$T[] {", elementType.toJavaPoet(boxPrimitives = false))
        elements.forEachIndexed { index, element ->
            if (index > 0) {
                add(", ")
            }
            add("\$L", element.toJavaPoet())
        }
        add("}")
    }.build()

private fun LsiIntArrayExpression.toJavaPoetIntArray(): CodeBlock =
    CodeBlock.builder().apply {
        add("new int[] {")
        elements.forEachIndexed { index, element ->
            if (index > 0) {
                add(", ")
            }
            add("\$L", element.toJavaPoet())
        }
        add("}")
    }.build()

private fun LsiListExpression.toJavaPoetList(): CodeBlock =
    when (elements.size) {
        0 -> CodeBlock.of("\$T.emptyList()", ClassName.get("java.util", "Collections"))
        1 -> CodeBlock.of(
            "\$T.singletonList(\$L)",
            ClassName.get("java.util", "Collections"),
            elements.single().toJavaPoet(),
        )
        else -> CodeBlock.of(
            "\$T.asList(\$L)",
            ClassName.get("java.util", "Arrays"),
            elements.joinToCallArguments(),
        )
    }

private fun List<LsiExpression>.joinToCallArguments(): CodeBlock =
    CodeBlock.builder().apply {
        forEachIndexed { index, argument ->
            if (index > 0) {
                add(", ")
            }
            add("\$L", argument.toJavaPoet())
        }
    }.build()

private fun Any?.toJavaPoetLiteral(): CodeBlock =
    when (this) {
        null -> CodeBlock.of("null")
        is String -> CodeBlock.of("\$S", this)
        is Char -> CodeBlock.of("'\$L'", escapeChar(this))
        is Long -> CodeBlock.of("\$L", "${this}L")
        is Float -> toJavaPoetFloatLiteral()
        else -> CodeBlock.of("\$L", this)
    }

private fun Float.toJavaPoetFloatLiteral(): CodeBlock =
    when {
        isNaN() -> CodeBlock.of("\$T.NaN", Float::class.javaObjectType)
        this == Float.POSITIVE_INFINITY -> CodeBlock.of("\$T.POSITIVE_INFINITY", Float::class.javaObjectType)
        this == Float.NEGATIVE_INFINITY -> CodeBlock.of("\$T.NEGATIVE_INFINITY", Float::class.javaObjectType)
        else -> CodeBlock.of("\$L", "${this}F")
    }

private fun String.setterMethodName(): String =
    "set" + replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase()
        } else {
            char.toString()
        }
    }

private fun TypeName.rawJavaTypeName(): TypeName =
    when (this) {
        is ParameterizedTypeName -> rawType
        else -> this
    }

private fun LsiLambdaExpression.toJavaPoetLambda(): CodeBlock =
    when (mode) {
        LsiLambdaMode.EXPRESSION ->
            CodeBlock.builder()
                .add("\$L -> {\n", javaLambdaParameterPrefix())
                .indent()
                .addStatement("return \$L", expression!!.toJavaPoet())
                .unindent()
                .add("}")
                .build()
        LsiLambdaMode.UNIT ->
            CodeBlock.builder()
                .add("\$L -> {\n", javaLambdaParameterPrefix())
                .indent()
                .add(statements.toJavaPoet())
                .addStatement("return null")
                .unindent()
                .add("}")
                .build()
        LsiLambdaMode.BLOCK ->
            CodeBlock.builder()
                .add("\$L -> {\n", javaLambdaParameterPrefix())
                .indent()
                .add(statements.toJavaPoet())
                .unindent()
                .add("}")
                .build()
    }

private fun LsiLambdaExpression.javaLambdaParameterPrefix(): String =
    when (parameterNames.size) {
        0 -> "()"
        1 -> parameterNames.single()
        else -> parameterNames.joinToString(", ", prefix = "(", postfix = ")")
    }

private fun LsiModifier.toJavaPoet(): Modifier? =
    when (this) {
        LsiModifier.PUBLIC -> Modifier.PUBLIC
        LsiModifier.PROTECTED -> Modifier.PROTECTED
        LsiModifier.PRIVATE -> Modifier.PRIVATE
        LsiModifier.CONST -> Modifier.FINAL
        LsiModifier.FINAL -> Modifier.FINAL
        LsiModifier.ABSTRACT -> Modifier.ABSTRACT
        LsiModifier.STATIC -> Modifier.STATIC
        else -> null
    }

private fun TypeSpec.Builder.addJavaMembers(property: LsiPropertySpec, inInterface: Boolean) {
    if (property.shouldRenderAsAccessor()) {
        property.toJavaBackingField()?.let(::addField)
        addMethod(property.toJavaGetter(inInterface = inInterface))
        property.toJavaSetter(inInterface = inInterface)?.let(::addMethod)
    } else {
        addField(property.toJavaPoet())
    }
}

private fun LsiPropertySpec.shouldRenderAsAccessor(): Boolean =
    !modifiers.contains(LsiModifier.STATIC) && (
        getterStatements.isNotEmpty() ||
        setterStatements.isNotEmpty() ||
        modifiers.contains(LsiModifier.ABSTRACT) ||
        modifiers.contains(LsiModifier.OVERRIDE) ||
        !modifiers.contains(LsiModifier.PRIVATE)
    )

private fun LsiPropertySpec.toJavaBackingField(): FieldSpec? {
    if (modifiers.contains(LsiModifier.ABSTRACT)) {
        return null
    }
    if ((getterStatements.isNotEmpty() || setterStatements.isNotEmpty()) && backingFieldModifiers.isEmpty()) {
        return null
    }
    return FieldSpec.builder(type.toJavaPoet(), name).apply {
        val fieldModifiers = backingFieldModifiers.ifEmpty { setOf(LsiModifier.PRIVATE) }
        fieldModifiers.toJavaPoetModifiers(defaultPublic = false).forEach { addModifiers(it) }
        fieldAnnotations(inAccessor = true).forEach { addAnnotation(it.toJavaPoet()) }
        if (!mutable) {
            addModifiers(Modifier.FINAL)
        }
        initializer.toJavaBackingInitializerOrNull()?.let { initializer("\$L", it) }
    }.build()
}

private fun LsiPropertySpec.toJavaGetter(inInterface: Boolean = false): MethodSpec {
    val builder = MethodSpec.methodBuilder(javaGetterName()).apply {
        getterAnnotations().forEach { addAnnotation(it.toJavaPoet()) }
        if (this@toJavaGetter.modifiers.contains(LsiModifier.OVERRIDE)) {
            addAnnotation(Override::class.java)
        }
        this@toJavaGetter.modifiers
            .filterNot { it == LsiModifier.OVERRIDE }
            .toJavaAccessorModifiers(defaultPublic = true)
            .forEach { addModifiers(it) }
        applyJavaInterfaceDefaultModifier(
            inInterface = inInterface,
            hasBody = !this@toJavaGetter.modifiers.contains(LsiModifier.ABSTRACT),
            modifiers = this@toJavaGetter.modifiers,
        )
        returns(this@toJavaGetter.type.toJavaPoet())
    }
    if (!modifiers.contains(LsiModifier.ABSTRACT)) {
        if (getterStatements.isNotEmpty()) {
            builder.addCode(getterStatements.toJavaPoet())
        } else {
            builder.addStatement("return \$L", name)
        }
    }
    return builder.build()
}

private fun LsiPropertySpec.toJavaSetter(inInterface: Boolean = false): MethodSpec? {
    if (!mutable) {
        return null
    }
    val builder = MethodSpec.methodBuilder(javaSetterName()).apply {
        setterAnnotations().forEach { addAnnotation(it.toJavaPoet()) }
        if (this@toJavaSetter.modifiers.contains(LsiModifier.OVERRIDE)) {
            addAnnotation(Override::class.java)
        }
        this@toJavaSetter.modifiers
            .filterNot { it == LsiModifier.OVERRIDE }
            .toJavaAccessorModifiers(defaultPublic = true)
            .forEach { addModifiers(it) }
        applyJavaInterfaceDefaultModifier(
            inInterface = inInterface,
            hasBody = !this@toJavaSetter.modifiers.contains(LsiModifier.ABSTRACT),
            modifiers = this@toJavaSetter.modifiers,
        )
        addParameter(this@toJavaSetter.type.toJavaPoet(), "value")
    }
    if (!modifiers.contains(LsiModifier.ABSTRACT)) {
        if (setterStatements.isNotEmpty()) {
            builder.addCode(setterStatements.toJavaPoet())
        } else {
            builder.addStatement("this.\$L = value", name)
        }
    }
    return builder.build()
}

private fun MethodSpec.Builder.applyJavaInterfaceDefaultModifier(
    inInterface: Boolean,
    hasBody: Boolean,
    modifiers: Set<LsiModifier>,
) {
    if (!inInterface || !hasBody) {
        return
    }
    if (modifiers.contains(LsiModifier.ABSTRACT) ||
        modifiers.contains(LsiModifier.STATIC) ||
        modifiers.contains(LsiModifier.PRIVATE)
    ) {
        return
    }
    addModifiers(Modifier.DEFAULT)
}

private fun LsiPropertySpec.javaGetterName(): String =
    name.javaGetterName(type)

private fun LsiPropertySpec.javaSetterName(): String =
    "set" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun String.javaGetterName(type: LsiTypeName): String {
    val head = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return if (type.isPrimitiveBoolean()) {
        "is$head"
    } else {
        "get$head"
    }
}

private fun LsiPropertySpec.getterAnnotations(): List<LsiAnnotationSpec> =
    accessorAnnotations(LsiAnnotationUseSiteTarget.GET)

private fun LsiPropertySpec.setterAnnotations(): List<LsiAnnotationSpec> =
    accessorAnnotations(LsiAnnotationUseSiteTarget.SET)

private fun LsiPropertySpec.fieldAnnotations(inAccessor: Boolean): List<LsiAnnotationSpec> =
    annotations.mapNotNull { annotation ->
        when (annotation.useSiteTarget) {
            null -> if (inAccessor) null else annotation
            LsiAnnotationUseSiteTarget.FIELD -> annotation.copy(useSiteTarget = null)
            else -> null
        }
    }

private fun LsiPropertySpec.accessorAnnotations(
    target: LsiAnnotationUseSiteTarget,
): List<LsiAnnotationSpec> =
    annotations.mapNotNull { annotation ->
        when (annotation.useSiteTarget) {
            null -> if (target == LsiAnnotationUseSiteTarget.GET) annotation else null
            target -> annotation.copy(useSiteTarget = null)
            LsiAnnotationUseSiteTarget.GET,
            LsiAnnotationUseSiteTarget.SET,
            LsiAnnotationUseSiteTarget.FIELD -> null
            else -> error(
                "JavaPoet accessor renderer only supports null/GET/SET/FIELD annotation target for property '$name': ${annotation.useSiteTarget}"
            )
        }
    }

private fun LsiPropertySpec.isAssignedFromConstructor(callable: LsiCallableSpec): Boolean =
    (initializer as? LsiNameExpression)?.let { initializerExpression ->
        callable.kind == LsiCallableSpecKind.CONSTRUCTOR &&
            callable.parameters.any { it.name == initializerExpression.name } &&
            toJavaBackingField() != null
    } ?: false

private fun LsiExpression?.toJavaBackingInitializerOrNull(): CodeBlock? =
    when (this) {
        null -> null
        is LsiNameExpression -> null
        else -> toJavaPoet()
    }

private fun LsiModifier.toJavaAccessorModifier(): Modifier? =
    when (this) {
        LsiModifier.PUBLIC -> Modifier.PUBLIC
        LsiModifier.PROTECTED -> Modifier.PROTECTED
        LsiModifier.PRIVATE -> Modifier.PRIVATE
        LsiModifier.ABSTRACT -> Modifier.ABSTRACT
        LsiModifier.STATIC -> Modifier.STATIC
        else -> null
    }

private fun Iterable<LsiModifier>.toJavaPoetModifiers(defaultPublic: Boolean): List<Modifier> =
    toJavaModifiers(defaultPublic) { it.toJavaPoet() }

private fun Iterable<LsiModifier>.toJavaAccessorModifiers(defaultPublic: Boolean): List<Modifier> =
    toJavaModifiers(defaultPublic) { it.toJavaAccessorModifier() }

private fun Iterable<LsiModifier>.toJavaModifiers(
    defaultPublic: Boolean,
    mapper: (LsiModifier) -> Modifier?,
): List<Modifier> {
    val source = toList()
    val mapped = source.mapNotNull(mapper).toMutableList()
    if (defaultPublic && !source.hasExplicitVisibility()) {
        mapped += Modifier.PUBLIC
    }
    return mapped.distinct()
}

private fun Iterable<LsiModifier>.hasExplicitVisibility(): Boolean =
    any {
        it == LsiModifier.PUBLIC ||
            it == LsiModifier.INTERNAL ||
            it == LsiModifier.PROTECTED ||
            it == LsiModifier.PRIVATE
    }

private fun LsiTypeName.isPrimitiveBoolean(): Boolean =
    this is LsiClassName && copyNullable(false).canonicalName in setOf("kotlin.Boolean", "boolean")

private fun primitiveTypeNameOrNull(canonicalName: String): TypeName? =
    when (canonicalName) {
        "kotlin.Boolean", "boolean" -> TypeName.BOOLEAN
        "kotlin.Byte", "byte" -> TypeName.BYTE
        "kotlin.Short", "short" -> TypeName.SHORT
        "kotlin.Int", "int" -> TypeName.INT
        "kotlin.Long", "long" -> TypeName.LONG
        "kotlin.Float", "float" -> TypeName.FLOAT
        "kotlin.Double", "double" -> TypeName.DOUBLE
        "kotlin.Char", "char" -> TypeName.CHAR
        else -> null
    }

private fun specialJavaTypeNameOrNull(canonicalName: String): TypeName? =
    when (canonicalName) {
        "kotlin.Any" -> TypeName.OBJECT
        "kotlin.String" -> ClassName.get("java.lang", "String")
        "kotlin.Unit" -> TypeName.VOID
        else -> null
    }

private fun primitiveLsiTypeNameOrNull(typeName: TypeName): LsiTypeName? =
    when (typeName) {
        TypeName.BOOLEAN -> LsiClassName.bestGuess("kotlin.Boolean")
        TypeName.BYTE -> LsiClassName.bestGuess("kotlin.Byte")
        TypeName.SHORT -> LsiClassName.bestGuess("kotlin.Short")
        TypeName.INT -> LsiClassName.bestGuess("kotlin.Int")
        TypeName.LONG -> LsiClassName.bestGuess("kotlin.Long")
        TypeName.FLOAT -> LsiClassName.bestGuess("kotlin.Float")
        TypeName.DOUBLE -> LsiClassName.bestGuess("kotlin.Double")
        TypeName.CHAR -> LsiClassName.bestGuess("kotlin.Char")
        else -> null
    }

private fun primitiveArrayTypeNameOrNull(typeName: LsiTypeName): TypeName? =
    when (typeName) {
        is LsiClassName -> primitiveTypeNameOrNull(typeName.copyNullable(false).canonicalName)
        else -> null
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
