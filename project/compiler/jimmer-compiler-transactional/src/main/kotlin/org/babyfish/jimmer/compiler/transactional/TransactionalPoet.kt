package org.babyfish.jimmer.compiler.transactional

import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.transactional.TransactionalConstructor
import site.addzero.lsi.jimmer.transactional.TransactionalMethod
import site.addzero.lsi.jimmer.transactional.TransactionalParameter
import site.addzero.lsi.jimmer.transactional.TransactionalSchema
import site.addzero.lsi.jimmer.transactional.TransactionalType
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationUseSiteTarget
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.anno.LsiSourceAnnotationArgument
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationCall
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.anno.sourceLsiAnnotation
import site.addzero.lsi.clazz.toLsiClasses
import site.addzero.lsi.anno.toSourceAnnotation

internal fun TransactionalSchema.toLsiSourceArtifacts(
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    return types.map { type -> type.toLsiSourceArtifact(workspace) }
}

private fun TransactionalType.toLsiSourceArtifact(workspace: LsiWorkspace): LsiSourceArtifact {
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySymbols = dependencySymbols()
    val dependencySources = workspace.originatingSources(dependencySymbols)
    val file = LsiFile(
        language = sqlClient.language,
        packageName = packageName,
        fileName = generatedSimpleName,
        annotations = if (sqlClient.language == LsiLanguage.KOTLIN) {
            listOf(FILE_WARNING_SUPPRESSION)
        } else {
            emptyList()
        },
        members = listOf(toLsiTypeDeclaration()),
    )
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiClasses(file.referencedTypeIds, BUILT_IN_TYPE_NAMES),
        aggregationMode = classifyArtifactAggregationMode(
            originatingSymbols = originatingSymbols,
            originatingSources = originatingSources,
            dependencySources = dependencySources,
        ),
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = dependencySymbols,
        dependencySources = dependencySources,
    )
}

private fun TransactionalType.toLsiTypeDeclaration(): LsiClass {
    return when (sqlClient.language) {
        LsiLanguage.JAVA -> toJavaPoetType()
        LsiLanguage.KOTLIN -> toKotlinPoetType()
        LsiLanguage.UNKNOWN -> error("Transactional SQL client language must be Java or Kotlin")
    }
}

private fun TransactionalType.toJavaPoetType(): LsiClass {
    return LsiClass(
        name = generatedSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = typeAnnotations(),
        modifiers = buildSet {
            if (visibility == LsiVisibility.PUBLIC) {
                add(LsiModifier.PUBLIC)
            }
            if (modality == LsiModality.ABSTRACT) {
                add(LsiModifier.ABSTRACT)
            }
        },
        superClass = LsiDeclaredType(id),
        members = buildList {
            constructors.forEach { constructor -> add(constructor.toJavaPoetConstructor()) }
            methods.forEach { method -> add(method.toJavaPoetFunction(this@toJavaPoetType)) }
        },
    )
}

private fun TransactionalType.toKotlinPoetType(): LsiClass {
    val primaryConstructor = constructors.singleOrNull(TransactionalConstructor::primary)
    return LsiClass(
        name = generatedSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = typeAnnotations(),
        modifiers = buildSet {
            when (visibility) {
                LsiVisibility.INTERNAL -> add(LsiModifier.INTERNAL)
                LsiVisibility.PUBLIC -> add(LsiModifier.PUBLIC)
                else -> Unit
            }
            if (modality == LsiModality.ABSTRACT) {
                add(LsiModifier.ABSTRACT)
            }
        },
        superClass = LsiDeclaredType(id),
        superClassConstructorArguments = primaryConstructor
            ?.parameters
            ?.map(TransactionalParameter::toKotlinArgument)
            .orEmpty(),
        primaryConstructor = primaryConstructor?.toKotlinPoetConstructor(),
        members = buildList {
            if (primaryConstructor == null) {
                constructors.forEach { constructor ->
                    add(constructor.toKotlinPoetConstructor(secondary = true))
                }
            }
            methods.forEach { method -> add(method.toKotlinPoetFunction(this@toKotlinPoetType)) }
        },
    )
}

private fun TransactionalType.typeAnnotations(): List<LsiAnnotation> {
    return buildList {
        copiedAnnotations
            .filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.TYPE
            }
            .mapTo(this, LsiAnnotation::toSourceAnnotation)
        targetAnnotationTypeId?.let { annotationTypeId ->
            add(sourceLsiAnnotation(annotationTypeId))
        }
    }
}

private fun TransactionalConstructor.toJavaPoetConstructor(): LsiConstructor {
    return LsiConstructor(
        annotations = copiedAnnotations
            .filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.CONSTRUCTOR
            }
            .map(LsiAnnotation::toSourceAnnotation),
        documentation = documentation.withTrailingLineBreak(),
        typeParameters = typeParameters,
        parameters = parameters.map(TransactionalParameter::toJavaPoetParameter),
        thrownTypes = thrownTypes,
        delegationCall = LsiDelegationCall(
            target = LsiDelegationTarget.SUPER,
            arguments = parameters.map(TransactionalParameter::toJavaArgument),
        ),
    )
}

private fun TransactionalConstructor.toKotlinPoetConstructor(
    secondary: Boolean = false,
): LsiConstructor {
    return LsiConstructor(
        annotations = copiedAnnotations
            .filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.CONSTRUCTOR
            }
            .map(LsiAnnotation::toSourceAnnotation),
        modifiers = buildSet {
            when (visibility) {
                LsiVisibility.PROTECTED -> add(LsiModifier.PROTECTED)
                LsiVisibility.INTERNAL -> add(LsiModifier.INTERNAL)
                LsiVisibility.PRIVATE -> add(LsiModifier.PRIVATE)
                else -> Unit
            }
        },
        documentation = documentation.withTrailingLineBreak(),
        parameters = parameters.map(TransactionalParameter::toKotlinPoetParameter),
        delegationCall = if (secondary) {
            LsiDelegationCall(
                target = LsiDelegationTarget.SUPER,
                arguments = parameters.map(TransactionalParameter::toKotlinArgument),
            )
        } else {
            null
        },
    )
}

private fun TransactionalMethod.toJavaPoetFunction(type: TransactionalType): LsiMethod {
    return LsiMethod(
        name = name,
        annotations = buildList {
            // JavaPoet 必须先写 @Override，保持它位于复制的方法注解之前。
            add(JAVA_OVERRIDE_ANNOTATION)
            copiedAnnotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
                }
                .mapTo(this, LsiAnnotation::toSourceAnnotation)
        },
        modifiers = buildSet {
            add(LsiModifier.OVERRIDE)
            when (visibility) {
                LsiVisibility.PUBLIC -> add(LsiModifier.PUBLIC)
                LsiVisibility.PROTECTED -> add(LsiModifier.PROTECTED)
                else -> Unit
            }
        },
        documentation = documentation.withTrailingLineBreak(),
        typeParameters = typeParameters,
        parameters = parameters.map(TransactionalParameter::toJavaPoetParameter),
        returnType = returnType.withReturnAnnotations(copiedAnnotations),
        thrownTypes = thrownTypes,
        body = javaTransactionBody(type),
    )
}

private fun TransactionalMethod.toKotlinPoetFunction(
    type: TransactionalType,
): LsiMethod {
    return LsiMethod(
        name = name,
        annotations = copiedAnnotations
            .filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
            }
            .map(LsiAnnotation::toSourceAnnotation),
        modifiers = buildSet {
            add(LsiModifier.OVERRIDE)
            when (visibility) {
                LsiVisibility.PROTECTED -> add(LsiModifier.PROTECTED)
                LsiVisibility.INTERNAL -> add(LsiModifier.INTERNAL)
                else -> Unit
            }
        },
        documentation = documentation.withTrailingLineBreak(),
        typeParameters = typeParameters,
        parameters = parameters.map(TransactionalParameter::toKotlinPoetParameter),
        returnType = returnType.withReturnAnnotations(copiedAnnotations),
        body = kotlinTransactionBody(type),
    )
}

private fun TransactionalMethod.javaTransactionBody(type: TransactionalType): LsiCodeBlock {
    val methodReturnType = returnType
    val returnsVoid = methodReturnType is LsiPrimitiveType &&
        methodReturnType.kind in setOf(LsiPrimitiveKind.UNIT, LsiPrimitiveKind.VOID)
    return code {
        val prefix: LsiCodeBuilder.() -> Unit = {
            name(type.sqlClient.name)
            text(".transaction(")
            type(PROPAGATION_TYPE)
            text(".")
            literal(propagation)
            text(", () -> ")
        }
        val body: LsiCodeBuilder.() -> Unit = {
            if (returnsVoid) {
                statement { javaSuperMethodCall(this@javaTransactionBody) }
                returnValue { literal("null") }
            } else {
                returnValue { javaSuperMethodCall(this@javaTransactionBody) }
            }
        }
        val suffix: LsiCodeBuilder.() -> Unit = { text(")") }
        if (returnsVoid) {
            statementBracedExpression(prefix, body, suffix)
        } else {
            returnBracedExpression(prefix, body, suffix)
        }
    }
}

private fun TransactionalMethod.kotlinTransactionBody(type: TransactionalType): LsiCodeBlock {
    val methodName = name
    val methodPropagation = propagation
    val methodParameters = parameters
    return code {
        returnBracedExpression(
            prefix = {
                text("this.")
                name(type.sqlClient.name)
                text(".transaction(")
                type(PROPAGATION_TYPE)
                text(".")
                literal(methodPropagation)
                text(")")
            },
            body = {
                statement {
                    text("super.")
                    name(methodName)
                    text("(")
                    methodParameters.forEachIndexed { index, parameter ->
                        if (index != 0) {
                            text(", ")
                        }
                        add(parameter.toKotlinArgument())
                    }
                    text(")")
                }
            },
        )
    }
}

private fun LsiCodeBuilder.javaSuperMethodCall(method: TransactionalMethod) {
    text("super.")
    name(method.name)
    text("(")
    method.parameters.forEachIndexed { index, parameter ->
        if (index != 0) {
            text(", ")
        }
        name(parameter.name)
    }
    text(")")
}

private fun TransactionalParameter.toJavaPoetParameter(): LsiParameter {
    return LsiParameter(
        name = name,
        type = type,
        annotations = annotations.map(LsiAnnotation::toSourceAnnotation),
        modifiers = if (vararg) setOf(LsiModifier.VARARG) else emptySet(),
    )
}

private fun TransactionalParameter.toKotlinPoetParameter(): LsiParameter {
    return LsiParameter(
        name = name,
        type = type,
        annotations = annotations.map(LsiAnnotation::toSourceAnnotation),
        modifiers = if (vararg) setOf(LsiModifier.VARARG) else emptySet(),
    )
}

private fun TransactionalParameter.toJavaArgument(): LsiCodeBlock {
    return code { name(this@toJavaArgument.name) }
}

private fun TransactionalParameter.toKotlinArgument(): LsiCodeBlock {
    return code {
        if (vararg) {
            text("*")
        }
        name(this@toKotlinArgument.name)
    }
}

private fun LsiType.withReturnAnnotations(annotations: List<LsiAnnotation>): LsiType {
    val returnAnnotations = annotations.filter { annotation ->
        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
    }
    if (returnAnnotations.isEmpty()) {
        return this
    }
    val mergedAnnotations = (this.annotations + returnAnnotations).distinct()
    return when (this) {
        is LsiArrayType -> copy(annotations = mergedAnnotations)
        is LsiDeclaredType -> copy(annotations = mergedAnnotations)
        is LsiFunctionType -> copy(annotations = mergedAnnotations)
        is LsiPrimitiveType -> copy(annotations = mergedAnnotations)
        is LsiTypeParameterRef -> copy(annotations = mergedAnnotations)
        is LsiUnresolvedType -> copy(annotations = mergedAnnotations)
    }
}

private fun TransactionalType.dependencySymbols(): Set<LsiSymbolId> {
    return buildSet {
        add(id)
        add(sqlClient.logicalId)
        add(sqlClient.declarationId)
        add(PROPAGATION_TYPE.declarationId)
        when (sqlClient.language) {
            LsiLanguage.JAVA -> if (methods.isNotEmpty()) {
                add(JAVA_OVERRIDE_ID)
            }
            LsiLanguage.KOTLIN -> add(KOTLIN_SUPPRESS_ID)
            LsiLanguage.UNKNOWN -> error("Transactional SQL client language must be Java or Kotlin")
        }
        addType(sqlClient.type)
        copiedAnnotations.forEach(::addAnnotation)
        targetAnnotationTypeId?.let(::add)
        constructors.forEach { constructor ->
            add(constructor.id)
            constructor.typeParameters.forEach(::addTypeParameter)
            constructor.thrownTypes.forEach(::addType)
            constructor.copiedAnnotations.forEach(::addAnnotation)
            constructor.parameters.forEach(::addParameter)
        }
        methods.forEach { method ->
            add(method.id)
            addType(method.returnType)
            method.typeParameters.forEach(::addTypeParameter)
            method.thrownTypes.forEach(::addType)
            method.copiedAnnotations.forEach(::addAnnotation)
            method.parameters.forEach(::addParameter)
        }
    }
}

private fun MutableSet<LsiSymbolId>.addParameter(parameter: TransactionalParameter) {
    add(parameter.id)
    addType(parameter.type)
    parameter.annotations.forEach(::addAnnotation)
    addAll(parameter.annotationProjectionTypeIds)
}

private fun MutableSet<LsiSymbolId>.addTypeParameter(parameter: LsiTypeParameter) {
    add(parameter.id)
    parameter.upperBounds.forEach(::addType)
}

private fun MutableSet<LsiSymbolId>.addType(type: LsiType) {
    when (type) {
        is LsiArrayType -> addType(type.elementType)
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.forEach { argument -> argument.type?.let(::addType) }
        }
        is LsiFunctionType -> {
            type.receiverType?.let(::addType)
            type.parameterTypes.forEach(::addType)
            addType(type.returnType)
        }
        is LsiPrimitiveType -> Unit
        is LsiTypeParameterRef -> add(type.parameterId)
        is LsiUnresolvedType -> Unit
    }
    type.annotations.forEach(::addAnnotation)
}

private fun MutableSet<LsiSymbolId>.addAnnotation(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument -> addAnnotationValue(argument.value) }
}

private fun MutableSet<LsiSymbolId>.addAnnotationValue(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::addAnnotationValue)
        is LsiAnnotationValue.ClassValue -> addType(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> addAnnotation(value.annotation)
        else -> Unit
    }
}

private fun String?.withTrailingLineBreak(): String? {
    return this?.let { documentation -> "$documentation\n" }
}

private fun code(block: LsiCodeBuilder.() -> Unit): LsiCodeBlock {
    return LsiCodeBlock.build(block)
}

private val PROPAGATION_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.Propagation")
)
private val JAVA_OVERRIDE_ID = LsiSymbolId.type("java.lang.Override")
private val KOTLIN_SUPPRESS_ID = LsiSymbolId.type("kotlin.Suppress")
private val BUILT_IN_TYPE_NAMES = listOf(
    LsiClass(
        PROPAGATION_TYPE.declarationId,
        "org.babyfish.jimmer.sql.transaction",
        listOf("Propagation"),
    ),
    LsiClass(JAVA_OVERRIDE_ID, "java.lang", listOf("Override")),
    LsiClass(KOTLIN_SUPPRESS_ID, "kotlin", listOf("Suppress")),
    LsiClass(LsiSymbolId.type("java.lang.String"), "java.lang", listOf("String")),
    LsiClass(LsiSymbolId.type("java.io.IOException"), "java.io", listOf("IOException")),
    LsiClass(
        LsiSymbolId.type("org.babyfish.jimmer.sql.JSqlClient"),
        "org.babyfish.jimmer.sql",
        listOf("JSqlClient"),
    ),
    LsiClass(
        LsiSymbolId.type("org.babyfish.jimmer.sql.kt.KSqlClient"),
        "org.babyfish.jimmer.sql.kt",
        listOf("KSqlClient"),
    ),
)
private val JAVA_OVERRIDE_ANNOTATION = sourceLsiAnnotation(
    JAVA_OVERRIDE_ID
)
private val FILE_WARNING_SUPPRESSION = sourceLsiAnnotation(
    type = KOTLIN_SUPPRESS_ID,
    arguments = listOf(
        LsiSourceAnnotationArgument.Positional(
            LsiAnnotationValue.StringValue("warnings")
        )
    ),
    useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
)
