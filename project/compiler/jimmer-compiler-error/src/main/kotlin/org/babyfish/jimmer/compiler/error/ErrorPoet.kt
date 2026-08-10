package org.babyfish.jimmer.compiler.error

import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.error.ErrorCode
import site.addzero.lsi.jimmer.error.ErrorFamily
import site.addzero.lsi.jimmer.error.ErrorField
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.toJvmReferenceType
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationCall
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.sourceLsiAnnotation
import site.addzero.lsi.clazz.toLsiClasses

internal fun ErrorSchema.toLsiSourceArtifacts(
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    return families.map { family -> family.toLsiSourceArtifact(workspace) }
}

private fun ErrorFamily.toLsiSourceArtifact(workspace: LsiWorkspace): LsiSourceArtifact {
    val language = sourceLanguage(workspace)
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySymbols = dependencySymbols(language)
    val dependencySources = workspace.originatingSources(dependencySymbols)
    val file = LsiFile(
        language = language,
        packageName = packageName,
        fileName = exceptionSimpleName,
        members = listOf(
            when (language) {
                LsiLanguage.JAVA -> toJavaPoetType()
                LsiLanguage.KOTLIN -> toKotlinPoetType()
                LsiLanguage.UNKNOWN -> error(
                    "Error family '${id.value}' has no Java or Kotlin source language"
                )
            }
        ),
    )
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiClasses(
            file.referencedTypeIds,
            additional = generatedTypeNames(),
        ),
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

private fun ErrorFamily.generatedTypeNames(): List<LsiClass> {
    return buildList {
        addAll(BUILT_IN_TYPE_NAMES)
        add(LsiClass(exceptionTypeId, packageName, listOf(exceptionSimpleName)))
        codes.forEach { code ->
            add(
                LsiClass(
                    typeId = code.exceptionTypeId,
                    packageName = packageName,
                    simpleNames = listOf(exceptionSimpleName, code.exceptionSimpleName),
                )
            )
        }
    }
}

private fun ErrorFamily.toJavaPoetType(): LsiClass {
    val enumType = LsiDeclaredType(id)
    return LsiClass(
        name = exceptionSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = listOf(
            generatedByAnnotation(enumType),
            clientExceptionAnnotation(),
        ),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.ABSTRACT,
        ),
        documentation = documentation.withTrailingLineBreak(),
        superClass = codeBasedExceptionType(),
        members = buildList {
            addAll(javaCommonMembers(declaredFields, declaredFields, sharedFields = null))
            add(javaEnumFunction(enumType))
            codes.forEach { code ->
                addAll(code.javaCreatorFunctions(declaredFields + code.declaredFields))
            }
            codes.forEach { code -> add(code.toJavaPoetType(this@toJavaPoetType, enumType)) }
        },
    )
}

private fun ErrorFamily.toKotlinPoetType(): LsiClass {
    val enumType = LsiDeclaredType(id)
    return LsiClass(
        name = exceptionSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = listOf(
            generatedByAnnotation(enumType),
            clientExceptionAnnotation(),
        ),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.ABSTRACT,
        ),
        documentation = documentation.withTrailingLineBreak(),
        superClass = codeBasedExceptionType(),
        superClassConstructorArguments = listOf(codeName("message"), codeName("cause")),
        primaryConstructor = kotlinPrimaryConstructor(declaredFields),
        members = buildList {
            declaredFields.forEach { field -> add(field.toKotlinProperty()) }
            add(kotlinEnumProperty(enumType, code = null))
            add(kotlinFieldsProperty(declaredFields))
            add(kotlinCompanionType())
            codes.forEach { code -> add(code.toKotlinPoetType(this@toKotlinPoetType, enumType)) }
        },
    )
}

private fun ErrorFamily.javaCommonMembers(
    declaredFields: List<ErrorField>,
    allFields: List<ErrorField>,
    sharedFields: List<ErrorField>?,
): List<LsiMember> {
    return buildList {
        declaredFields.forEach { field -> add(field.toJavaField()) }
        add(javaConstructor(declaredFields, allFields, sharedFields))
        declaredFields.forEach { field -> add(field.toJavaGetter()) }
    }
}

private fun ErrorFamily.javaConstructor(
    declaredFields: List<ErrorField>,
    allFields: List<ErrorField>,
    sharedFields: List<ErrorField>?,
): LsiConstructor {
    val superArguments = buildList {
        add(codeName("message"))
        add(codeName("cause"))
        sharedFields?.forEach { field -> add(codeName(field.name)) }
    }
    return LsiConstructor(
        modifiers = setOf(LsiModifier.PUBLIC),
        parameters = buildList {
            add(LsiParameter("message", JAVA_STRING_TYPE))
            add(LsiParameter("cause", JAVA_THROWABLE_TYPE))
            allFields.forEach { field -> add(field.toJavaParameter()) }
        },
        body = code {
            declaredFields.forEach { field ->
                statement {
                    text("this.")
                    name(field.name)
                    text(" = ")
                    name(field.name)
                }
            }
        },
        delegationCall = LsiDelegationCall(
            target = LsiDelegationTarget.SUPER,
            arguments = superArguments,
        ),
    )
}

private fun ErrorFamily.javaEnumFunction(enumType: LsiType): LsiMethod {
    return LsiMethod(
        name = "get${qualifiedName.substringAfterLast('.')}",
        annotations = listOf(JSON_IGNORE_ANNOTATION),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.ABSTRACT,
        ),
        returnType = enumType,
    )
}

private fun ErrorCode.javaCreatorFunctions(fields: List<ErrorField>): List<LsiMethod> {
    return listOf(
        javaCreatorFunction(fields, withMessage = false, withCause = false),
        javaCreatorFunction(fields, withMessage = true, withCause = false),
        javaCreatorFunction(fields, withMessage = true, withCause = true),
    )
}

private fun ErrorCode.javaCreatorFunction(
    fields: List<ErrorField>,
    withMessage: Boolean,
    withCause: Boolean,
): LsiMethod {
    val nestedType = LsiDeclaredType(exceptionTypeId)
    return LsiMethod(
        name = creatorName,
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
        ),
        parameters = buildList {
            if (withMessage) {
                add(
                    LsiParameter(
                        name = "message",
                        type = JAVA_STRING_TYPE,
                        annotations = listOf(NON_NULL_ANNOTATION),
                    )
                )
            }
            if (withCause) {
                add(
                    LsiParameter(
                        name = "cause",
                        type = JAVA_THROWABLE_TYPE,
                        annotations = listOf(NULLABLE_ANNOTATION),
                    )
                )
            }
            fields.forEach { field -> add(field.toJavaParameter()) }
        },
        returnType = nestedType,
        body = code {
            text("return new ")
            type(nestedType)
            text("(")
            line()
            indent {
                val arguments = buildList {
                    add(if (withMessage) "message" else "null")
                    add(if (withCause) "cause" else "null")
                    fields.forEach { field -> add(field.name) }
                }
                arguments.forEachIndexed { index, argument ->
                    if (index != 0) {
                        text(",")
                        line()
                    }
                    if (argument == "null") {
                        literal(argument)
                    } else {
                        name(argument)
                    }
                }
            }
            line()
            text(");")
            line()
        },
    )
}

private fun ErrorCode.toJavaPoetType(
    family: ErrorFamily,
    enumType: LsiType,
): LsiClass {
    val allFields = family.declaredFields + declaredFields
    return LsiClass(
        name = exceptionSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = listOf(clientExceptionAnnotation(family.family)),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
        ),
        documentation = documentation.withTrailingLineBreak(),
        superClass = LsiDeclaredType(family.exceptionTypeId),
        members = buildList {
            addAll(family.javaCommonMembers(declaredFields, allFields, family.declaredFields))
            add(javaEnumFunction(enumType))
            add(javaFieldsFunction(allFields))
        },
    )
}

private fun ErrorCode.javaEnumFunction(enumType: LsiType): LsiMethod {
    return LsiMethod(
        name = "get${enumType.declarationSimpleName()}",
        annotations = listOf(
            JSON_IGNORE_ANNOTATION,
            JAVA_OVERRIDE_ANNOTATION,
        ),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.OVERRIDE,
        ),
        returnType = enumType,
        body = code {
            returnValue {
                type(enumType)
                text(".")
                literal(enumEntryName)
            }
        },
    )
}

private fun ErrorCode.javaFieldsFunction(fields: List<ErrorField>): LsiMethod {
    return LsiMethod(
        name = "getFields",
        annotations = listOf(JAVA_OVERRIDE_ANNOTATION),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.OVERRIDE,
        ),
        returnType = MAP_OF_STRING_OBJECT_TYPE,
        body = code {
            when (fields.size) {
                0 -> returnValue {
                    type(COLLECTIONS_TYPE)
                    text(".emptyMap()")
                }
                1 -> returnValue {
                    type(COLLECTIONS_TYPE)
                    text(".singletonMap(")
                    string(fields.single().name)
                    text(", ")
                    name(fields.single().name)
                    text(")")
                }
                else -> {
                    statement {
                        type(MAP_OF_STRING_OBJECT_TYPE)
                        text(" fields = new ")
                        type(LINKED_HASH_MAP_TYPE)
                        text("<>()")
                    }
                    fields.forEach { field ->
                        statement {
                            name("fields")
                            text(".put(")
                            string(field.name)
                            text(", ")
                            name(field.name)
                            text(")")
                        }
                    }
                    returnValue { name("fields") }
                }
            }
        },
    )
}

private fun ErrorField.toJavaField(): LsiField {
    return LsiField(
        name = name,
        type = javaType(),
        annotations = listOf(nullabilityAnnotation()),
        modifiers = setOf(LsiModifier.FINAL),
    )
}

private fun ErrorField.toJavaParameter(): LsiParameter {
    return LsiParameter(
        name = name,
        type = javaType(),
        annotations = listOf(nullabilityAnnotation()),
    )
}

private fun ErrorField.toJavaGetter(): LsiMethod {
    val fieldType = type
    val prefix = if (fieldType is LsiPrimitiveType && fieldType.kind == LsiPrimitiveKind.BOOLEAN && !list) {
        "is"
    } else {
        "get"
    }
    return LsiMethod(
        name = prefix + name.replaceFirstChar(Char::uppercaseChar),
        annotations = listOf(nullabilityAnnotation()),
        modifiers = setOf(LsiModifier.PUBLIC),
        documentation = documentation.withTrailingLineBreak(),
        returnType = javaType(),
        body = code { returnValue { name(this@toJavaGetter.name) } },
    )
}

private fun ErrorField.javaType(): LsiType {
    return if (list) {
        LsiDeclaredType(
            declarationId = LIST_ID,
            arguments = listOf(LsiTypeArgument.invariant(type.toJvmReferenceType())),
        )
    } else {
        type
    }
}

private fun ErrorField.nullabilityAnnotation(): LsiAnnotation {
    return if (nullable) NULLABLE_ANNOTATION else NON_NULL_ANNOTATION
}

private fun ErrorFamily.kotlinPrimaryConstructor(fields: List<ErrorField>): LsiConstructor {
    return LsiConstructor(
        parameters = buildList {
            add(
                LsiParameter(
                    name = "message",
                    type = KOTLIN_STRING_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            add(
                LsiParameter(
                    name = "cause",
                    type = KOTLIN_THROWABLE_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            fields.forEach { field -> add(field.toKotlinParameter()) }
        },
    )
}

private fun ErrorField.toKotlinParameter(): LsiParameter {
    return LsiParameter(
        name = name,
        type = kotlinType(),
        defaultValue = if (nullable) codeLiteral("null") else null,
    )
}

private fun ErrorField.toKotlinProperty(): LsiProperty {
    return LsiProperty(
        name = name,
        type = kotlinType(),
        mutable = false,
        modifiers = setOf(LsiModifier.PUBLIC),
        documentation = documentation.withTrailingLineBreak(),
        initializer = codeName(name),
    )
}

private fun ErrorField.kotlinType(): LsiType {
    val baseType = if (list) {
        LsiDeclaredType(
            declarationId = LIST_ID,
            arguments = listOf(LsiTypeArgument.invariant(type)),
        )
    } else {
        type
    }
    return baseType.withRootNullability(nullable)
}

private fun ErrorFamily.kotlinEnumProperty(
    enumType: LsiType,
    code: ErrorCode?,
): LsiProperty {
    return LsiProperty(
        name = enumType.declarationSimpleName().replaceFirstChar(Char::lowercaseChar),
        type = enumType,
        mutable = false,
        annotations = listOf(JSON_IGNORE_GETTER_ANNOTATION),
        modifiers = buildSet {
            add(LsiModifier.PUBLIC)
            add(if (code == null) LsiModifier.ABSTRACT else LsiModifier.OVERRIDE)
        },
        getter = code?.let { errorCode ->
            LsiAccessor(
                body = code {
                    returnValue {
                        type(enumType)
                        text(".")
                        literal(errorCode.enumEntryName)
                    }
                }
            )
        },
    )
}

private fun ErrorFamily.kotlinFieldsProperty(fields: List<ErrorField>): LsiProperty {
    return LsiProperty(
        name = "fields",
        type = KOTLIN_FIELDS_MAP_TYPE,
        mutable = false,
        modifiers = setOf(LsiModifier.OVERRIDE),
        getter = LsiAccessor(
            body = code {
                if (fields.isEmpty()) {
                    returnValue { text("emptyMap()") }
                } else {
                    returnValue {
                        text("mapOf(")
                        line()
                        indent {
                            fields.forEachIndexed { index, field ->
                                if (index != 0) {
                                    text(",")
                                    line()
                                }
                                string(field.name)
                                text(" to ")
                                name(field.name)
                            }
                        }
                        line()
                        text(")")
                    }
                }
            }
        ),
    )
}

private fun ErrorFamily.kotlinCompanionType(): LsiClass {
    return LsiClass(
        name = "Companion",
        kind = LsiTypeDeclarationKind.OBJECT,
        modifiers = setOf(LsiModifier.COMPANION),
        members = codes.map { code ->
            code.kotlinFactoryFunction(declaredFields + code.declaredFields)
        },
    )
}

private fun ErrorCode.kotlinFactoryFunction(fields: List<ErrorField>): LsiMethod {
    val nestedType = LsiDeclaredType(exceptionTypeId)
    return LsiMethod(
        name = creatorName,
        annotations = listOf(JVM_STATIC_ANNOTATION),
        modifiers = setOf(LsiModifier.PUBLIC),
        parameters = buildList {
            add(
                LsiParameter(
                    name = "message",
                    type = KOTLIN_STRING_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            add(
                LsiParameter(
                    name = "cause",
                    type = KOTLIN_THROWABLE_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            fields.forEach { field -> add(field.toKotlinParameter()) }
        },
        returnType = nestedType,
        body = code {
            returnValue {
                type(nestedType)
                text("(")
                line()
                indent {
                    val arguments = listOf("message", "cause") + fields.map(ErrorField::name)
                    arguments.forEachIndexed { index, argument ->
                        if (index != 0) {
                            text(",")
                            line()
                        }
                        name(argument)
                    }
                }
                line()
                text(")")
            }
        },
    )
}

private fun ErrorCode.toKotlinPoetType(
    family: ErrorFamily,
    enumType: LsiType,
): LsiClass {
    val allFields = family.declaredFields + declaredFields
    return LsiClass(
        name = exceptionSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = listOf(clientExceptionAnnotation(family.family)),
        modifiers = setOf(LsiModifier.PUBLIC),
        documentation = documentation.withTrailingLineBreak(),
        superClass = LsiDeclaredType(family.exceptionTypeId),
        superClassConstructorArguments = buildList {
            add(codeName("message"))
            add(codeName("cause"))
            family.declaredFields.forEach { field -> add(codeName(field.name)) }
        },
        primaryConstructor = family.kotlinPrimaryConstructor(allFields),
        members = buildList {
            declaredFields.forEach { field -> add(field.toKotlinProperty()) }
            add(family.kotlinEnumProperty(enumType, this@toKotlinPoetType))
            add(family.kotlinFieldsProperty(allFields))
        },
    )
}

private fun ErrorFamily.generatedByAnnotation(enumType: LsiType): LsiAnnotation {
    return sourceLsiAnnotation(
        type = GENERATED_BY_ID,
        arguments = listOf(
            LsiSourceAnnotationArgument.Named(
                name = "type",
                value = LsiAnnotationValue.ClassValue(enumType),
            )
        ),
    )
}

private fun ErrorFamily.clientExceptionAnnotation(): LsiAnnotation {
    return sourceLsiAnnotation(
        type = CLIENT_EXCEPTION_ID,
        arguments = buildList {
            add(
                LsiSourceAnnotationArgument.Named(
                    name = "family",
                    value = LsiAnnotationValue.StringValue(family),
                )
            )
            if (codes.isNotEmpty()) {
                add(
                    LsiSourceAnnotationArgument.Named(
                        name = "subTypes",
                        value = LsiAnnotationValue.ArrayValue(
                            codes.map { code ->
                                LsiAnnotationValue.ClassValue(
                                    LsiDeclaredType(code.exceptionTypeId)
                                )
                            }
                        ),
                    )
                )
            }
        },
    )
}

private fun ErrorCode.clientExceptionAnnotation(family: String): LsiAnnotation {
    return sourceLsiAnnotation(
        type = CLIENT_EXCEPTION_ID,
        arguments = listOf(
            LsiSourceAnnotationArgument.Named(
                name = "family",
                value = LsiAnnotationValue.StringValue(family),
            ),
            LsiSourceAnnotationArgument.Named(
                name = "code",
                value = LsiAnnotationValue.StringValue(code),
            ),
        ),
    )
}

private fun ErrorFamily.codeBasedExceptionType(): LsiDeclaredType {
    return LsiDeclaredType(
        if (checkedException) CODE_BASED_EXCEPTION_ID else CODE_BASED_RUNTIME_EXCEPTION_ID
    )
}

private fun ErrorFamily.sourceLanguage(workspace: LsiWorkspace): LsiLanguage {
    val declarationLanguage = (workspace[id] as? LsiClass)?.origin?.language
    if (declarationLanguage == LsiLanguage.JAVA || declarationLanguage == LsiLanguage.KOTLIN) {
        return declarationLanguage
    }
    val sourceLanguage = (originatingSources + workspace.originatingSources(setOf(id)))
        .map { source -> source.language }
        .firstOrNull { language -> language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN }
    return sourceLanguage ?: LsiLanguage.UNKNOWN
}

private fun ErrorFamily.dependencySymbols(language: LsiLanguage): Set<LsiSymbolId> {
    return buildSet {
        add(id)
        add(codeBasedExceptionType().declarationId)
        add(CLIENT_EXCEPTION_ID)
        add(GENERATED_BY_ID)
        add(JSON_IGNORE_ID)
        add(MAP_ID)
        copiedFieldDependencies(declaredFields)
        codes.forEach { code ->
            add(code.id)
            copiedFieldDependencies(code.declaredFields)
        }
        when (language) {
            LsiLanguage.JAVA -> {
                add(JAVA_STRING_ID)
                add(JAVA_OBJECT_ID)
                add(JAVA_THROWABLE_ID)
                add(JAVA_OVERRIDE_ID)
                add(NON_NULL_ID)
                add(NULLABLE_ID)
                add(COLLECTIONS_ID)
                add(LINKED_HASH_MAP_ID)
                if ((declaredFields + codes.flatMap(ErrorCode::declaredFields)).any(ErrorField::list)) {
                    add(LIST_ID)
                }
            }
            LsiLanguage.KOTLIN -> {
                add(KOTLIN_STRING_ID)
                add(KOTLIN_ANY_ID)
                add(KOTLIN_THROWABLE_ID)
                add(JVM_STATIC_ID)
            }
            LsiLanguage.UNKNOWN -> Unit
        }
    }
}

private fun MutableSet<LsiSymbolId>.copiedFieldDependencies(fields: List<ErrorField>) {
    fields.forEach { field ->
        add(field.declaredBy)
        addType(field.type)
        if (field.list) {
            add(LIST_ID)
        }
    }
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

private fun LsiType.withRootNullability(nullable: Boolean): LsiType {
    val nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
    return when (this) {
        is LsiArrayType -> copy(nullability = nullability)
        is LsiDeclaredType -> copy(nullability = nullability)
        is LsiFunctionType -> copy(nullability = nullability)
        is LsiPrimitiveType -> copy(nullability = nullability)
        is LsiTypeParameterRef -> copy(nullability = nullability)
        is LsiUnresolvedType -> copy(nullability = nullability)
    }
}

private fun LsiType.declarationSimpleName(): String {
    return (this as LsiDeclaredType).declarationId.requireTypeQualifiedName().substringAfterLast('.')
}

private fun String?.withTrailingLineBreak(): String? {
    return this?.let { documentation -> "$documentation\n" }
}

private fun code(block: LsiCodeBuilder.() -> Unit): LsiCodeBlock {
    return LsiCodeBlock.build(block)
}

private fun codeName(name: String): LsiCodeBlock {
    return code { name(name) }
}

private fun codeLiteral(value: String): LsiCodeBlock {
    return code { literal(value) }
}

private val CLIENT_EXCEPTION_ID = LsiSymbolId.type("org.babyfish.jimmer.ClientException")
private val GENERATED_BY_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val CODE_BASED_EXCEPTION_ID = LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedException")
private val CODE_BASED_RUNTIME_EXCEPTION_ID = LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedRuntimeException")
private val JSON_IGNORE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonIgnore")
private val NON_NULL_ID = LsiSymbolId.type("org.jspecify.annotations.NonNull")
private val NULLABLE_ID = LsiSymbolId.type("org.jspecify.annotations.Nullable")
private val JAVA_OVERRIDE_ID = LsiSymbolId.type("java.lang.Override")
private val JVM_STATIC_ID = LsiSymbolId.type("kotlin.jvm.JvmStatic")
private val JAVA_STRING_ID = LsiSymbolId.type("java.lang.String")
private val JAVA_OBJECT_ID = LsiSymbolId.type("java.lang.Object")
private val JAVA_THROWABLE_ID = LsiSymbolId.type("java.lang.Throwable")
private val KOTLIN_STRING_ID = LsiSymbolId.type("kotlin.String")
private val KOTLIN_ANY_ID = LsiSymbolId.type("kotlin.Any")
private val KOTLIN_THROWABLE_ID = LsiSymbolId.type("kotlin.Throwable")
private val LIST_ID = LsiSymbolId.type("java.util.List")
private val MAP_ID = LsiSymbolId.type("java.util.Map")
private val COLLECTIONS_ID = LsiSymbolId.type("java.util.Collections")
private val LINKED_HASH_MAP_ID = LsiSymbolId.type("java.util.LinkedHashMap")
private val BUILT_IN_TYPE_NAMES = listOf(
    LsiClass(CLIENT_EXCEPTION_ID, "org.babyfish.jimmer", listOf("ClientException")),
    LsiClass(GENERATED_BY_ID, "org.babyfish.jimmer.internal", listOf("GeneratedBy")),
    LsiClass(CODE_BASED_EXCEPTION_ID, "org.babyfish.jimmer.error", listOf("CodeBasedException")),
    LsiClass(
        CODE_BASED_RUNTIME_EXCEPTION_ID,
        "org.babyfish.jimmer.error",
        listOf("CodeBasedRuntimeException"),
    ),
    LsiClass(JSON_IGNORE_ID, "com.fasterxml.jackson.annotation", listOf("JsonIgnore")),
    LsiClass(NON_NULL_ID, "org.jspecify.annotations", listOf("NonNull")),
    LsiClass(NULLABLE_ID, "org.jspecify.annotations", listOf("Nullable")),
    LsiClass(JAVA_OVERRIDE_ID, "java.lang", listOf("Override")),
    LsiClass(JVM_STATIC_ID, "kotlin.jvm", listOf("JvmStatic")),
    LsiClass(JAVA_STRING_ID, "java.lang", listOf("String")),
    LsiClass(JAVA_OBJECT_ID, "java.lang", listOf("Object")),
    LsiClass(JAVA_THROWABLE_ID, "java.lang", listOf("Throwable")),
    LsiClass(KOTLIN_STRING_ID, "kotlin", listOf("String")),
    LsiClass(KOTLIN_ANY_ID, "kotlin", listOf("Any")),
    LsiClass(KOTLIN_THROWABLE_ID, "kotlin", listOf("Throwable")),
    LsiClass(LIST_ID, "java.util", listOf("List")),
    LsiClass(MAP_ID, "java.util", listOf("Map")),
    LsiClass(COLLECTIONS_ID, "java.util", listOf("Collections")),
    LsiClass(LINKED_HASH_MAP_ID, "java.util", listOf("LinkedHashMap")),
    LsiClass(
        LsiSymbolId.type("java.time.LocalDateTime"),
        "java.time",
        listOf("LocalDateTime"),
    ),
)

private val JAVA_STRING_TYPE = LsiDeclaredType(JAVA_STRING_ID)
private val KOTLIN_STRING_TYPE = LsiDeclaredType(KOTLIN_STRING_ID)
private val JAVA_THROWABLE_TYPE = LsiDeclaredType(JAVA_THROWABLE_ID)
private val KOTLIN_THROWABLE_TYPE = LsiDeclaredType(KOTLIN_THROWABLE_ID)
private val COLLECTIONS_TYPE = LsiDeclaredType(COLLECTIONS_ID)
private val LINKED_HASH_MAP_TYPE = LsiDeclaredType(LINKED_HASH_MAP_ID)
private val MAP_OF_STRING_OBJECT_TYPE = LsiDeclaredType(
    declarationId = MAP_ID,
    arguments = listOf(
        LsiTypeArgument.invariant(JAVA_STRING_TYPE),
        LsiTypeArgument.invariant(LsiDeclaredType(JAVA_OBJECT_ID)),
    ),
)
private val KOTLIN_FIELDS_MAP_TYPE = LsiDeclaredType(
    declarationId = MAP_ID,
    arguments = listOf(
        LsiTypeArgument.invariant(KOTLIN_STRING_TYPE),
        LsiTypeArgument.invariant(
            LsiDeclaredType(
                declarationId = KOTLIN_ANY_ID,
                nullability = LsiNullability.NULLABLE,
            )
        ),
    ),
)

private val JSON_IGNORE_ANNOTATION = sourceLsiAnnotation(JSON_IGNORE_ID)
private val JSON_IGNORE_GETTER_ANNOTATION = sourceLsiAnnotation(
    type = JSON_IGNORE_ID,
    useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
)
private val NON_NULL_ANNOTATION = sourceLsiAnnotation(NON_NULL_ID)
private val NULLABLE_ANNOTATION = sourceLsiAnnotation(NULLABLE_ID)
private val JAVA_OVERRIDE_ANNOTATION = sourceLsiAnnotation(JAVA_OVERRIDE_ID)
private val JVM_STATIC_ANNOTATION = sourceLsiAnnotation(JVM_STATIC_ID)
