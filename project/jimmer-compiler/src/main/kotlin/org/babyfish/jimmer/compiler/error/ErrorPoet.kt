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
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.toJvmReferenceType
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetDelegationCall
import site.addzero.lsi.poet.LsiPoetDelegationTarget
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

internal fun ErrorSchema.toLsiPoetArtifacts(
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    return families.map { family -> family.toLsiPoetArtifact(workspace) }
}

private fun ErrorFamily.toLsiPoetArtifact(workspace: LsiWorkspace): LsiPoetArtifact {
    val language = sourceLanguage(workspace)
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySymbols = dependencySymbols(language)
    val dependencySources = workspace.originatingSources(dependencySymbols)
    val file = LsiPoetFile(
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
    return LsiPoetArtifact(
        file = file,
        typeNames = workspace.toLsiPoetTypeNames(
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

private fun ErrorFamily.generatedTypeNames(): List<LsiPoetTypeName> {
    return buildList {
        addAll(BUILT_IN_TYPE_NAMES)
        add(LsiPoetTypeName(exceptionTypeId, packageName, listOf(exceptionSimpleName)))
        codes.forEach { code ->
            add(
                LsiPoetTypeName(
                    typeId = code.exceptionTypeId,
                    packageName = packageName,
                    simpleNames = listOf(exceptionSimpleName, code.exceptionSimpleName),
                )
            )
        }
    }
}

private fun ErrorFamily.toJavaPoetType(): LsiPoetType {
    val enumType = LsiDeclaredType(id)
    return LsiPoetType(
        name = exceptionSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        annotations = listOf(
            generatedByAnnotation(enumType),
            clientExceptionAnnotation(),
        ),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.ABSTRACT,
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

private fun ErrorFamily.toKotlinPoetType(): LsiPoetType {
    val enumType = LsiDeclaredType(id)
    return LsiPoetType(
        name = exceptionSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        annotations = listOf(
            generatedByAnnotation(enumType),
            clientExceptionAnnotation(),
        ),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.ABSTRACT,
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
): List<LsiPoetMember> {
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
): LsiPoetConstructor {
    val superArguments = buildList {
        add(codeName("message"))
        add(codeName("cause"))
        sharedFields?.forEach { field -> add(codeName(field.name)) }
    }
    return LsiPoetConstructor(
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        parameters = buildList {
            add(LsiPoetParameter("message", JAVA_STRING_TYPE))
            add(LsiPoetParameter("cause", JAVA_THROWABLE_TYPE))
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
        delegationCall = LsiPoetDelegationCall(
            target = LsiPoetDelegationTarget.SUPER,
            arguments = superArguments,
        ),
    )
}

private fun ErrorFamily.javaEnumFunction(enumType: LsiTypeRef): LsiPoetFunction {
    return LsiPoetFunction(
        name = "get${qualifiedName.substringAfterLast('.')}",
        annotations = listOf(JSON_IGNORE_ANNOTATION),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.ABSTRACT,
        ),
        returnType = enumType,
    )
}

private fun ErrorCode.javaCreatorFunctions(fields: List<ErrorField>): List<LsiPoetFunction> {
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
): LsiPoetFunction {
    val nestedType = LsiDeclaredType(exceptionTypeId)
    return LsiPoetFunction(
        name = creatorName,
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
        ),
        parameters = buildList {
            if (withMessage) {
                add(
                    LsiPoetParameter(
                        name = "message",
                        type = JAVA_STRING_TYPE,
                        annotations = listOf(NON_NULL_ANNOTATION),
                    )
                )
            }
            if (withCause) {
                add(
                    LsiPoetParameter(
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
    enumType: LsiTypeRef,
): LsiPoetType {
    val allFields = family.declaredFields + declaredFields
    return LsiPoetType(
        name = exceptionSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        annotations = listOf(clientExceptionAnnotation(family.family)),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
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

private fun ErrorCode.javaEnumFunction(enumType: LsiTypeRef): LsiPoetFunction {
    return LsiPoetFunction(
        name = "get${enumType.declarationSimpleName()}",
        annotations = listOf(
            JSON_IGNORE_ANNOTATION,
            JAVA_OVERRIDE_ANNOTATION,
        ),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.OVERRIDE,
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

private fun ErrorCode.javaFieldsFunction(fields: List<ErrorField>): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getFields",
        annotations = listOf(JAVA_OVERRIDE_ANNOTATION),
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.OVERRIDE,
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

private fun ErrorField.toJavaField(): LsiPoetField {
    return LsiPoetField(
        name = name,
        type = javaType(),
        annotations = listOf(nullabilityAnnotation()),
        modifiers = setOf(LsiPoetModifier.FINAL),
    )
}

private fun ErrorField.toJavaParameter(): LsiPoetParameter {
    return LsiPoetParameter(
        name = name,
        type = javaType(),
        annotations = listOf(nullabilityAnnotation()),
    )
}

private fun ErrorField.toJavaGetter(): LsiPoetFunction {
    val fieldType = type
    val prefix = if (fieldType is LsiPrimitiveType && fieldType.kind == LsiPrimitiveKind.BOOLEAN && !list) {
        "is"
    } else {
        "get"
    }
    return LsiPoetFunction(
        name = prefix + name.replaceFirstChar(Char::uppercaseChar),
        annotations = listOf(nullabilityAnnotation()),
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        documentation = documentation.withTrailingLineBreak(),
        returnType = javaType(),
        body = code { returnValue { name(this@toJavaGetter.name) } },
    )
}

private fun ErrorField.javaType(): LsiTypeRef {
    return if (list) {
        LsiDeclaredType(
            declarationId = LIST_ID,
            arguments = listOf(LsiTypeArgument.invariant(type.toJvmReferenceType())),
        )
    } else {
        type
    }
}

private fun ErrorField.nullabilityAnnotation(): LsiPoetAnnotation {
    return if (nullable) NULLABLE_ANNOTATION else NON_NULL_ANNOTATION
}

private fun ErrorFamily.kotlinPrimaryConstructor(fields: List<ErrorField>): LsiPoetConstructor {
    return LsiPoetConstructor(
        parameters = buildList {
            add(
                LsiPoetParameter(
                    name = "message",
                    type = KOTLIN_STRING_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            add(
                LsiPoetParameter(
                    name = "cause",
                    type = KOTLIN_THROWABLE_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            fields.forEach { field -> add(field.toKotlinParameter()) }
        },
    )
}

private fun ErrorField.toKotlinParameter(): LsiPoetParameter {
    return LsiPoetParameter(
        name = name,
        type = kotlinType(),
        defaultValue = if (nullable) codeLiteral("null") else null,
    )
}

private fun ErrorField.toKotlinProperty(): LsiPoetProperty {
    return LsiPoetProperty(
        name = name,
        type = kotlinType(),
        mutable = false,
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        documentation = documentation.withTrailingLineBreak(),
        initializer = codeName(name),
    )
}

private fun ErrorField.kotlinType(): LsiTypeRef {
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
    enumType: LsiTypeRef,
    code: ErrorCode?,
): LsiPoetProperty {
    return LsiPoetProperty(
        name = enumType.declarationSimpleName().replaceFirstChar(Char::lowercaseChar),
        type = enumType,
        mutable = false,
        annotations = listOf(JSON_IGNORE_GETTER_ANNOTATION),
        modifiers = buildSet {
            add(LsiPoetModifier.PUBLIC)
            add(if (code == null) LsiPoetModifier.ABSTRACT else LsiPoetModifier.OVERRIDE)
        },
        getter = code?.let { errorCode ->
            LsiPoetAccessor(
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

private fun ErrorFamily.kotlinFieldsProperty(fields: List<ErrorField>): LsiPoetProperty {
    return LsiPoetProperty(
        name = "fields",
        type = KOTLIN_FIELDS_MAP_TYPE,
        mutable = false,
        modifiers = setOf(LsiPoetModifier.OVERRIDE),
        getter = LsiPoetAccessor(
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

private fun ErrorFamily.kotlinCompanionType(): LsiPoetType {
    return LsiPoetType(
        name = "Companion",
        kind = LsiPoetTypeKind.OBJECT,
        modifiers = setOf(LsiPoetModifier.COMPANION),
        members = codes.map { code ->
            code.kotlinFactoryFunction(declaredFields + code.declaredFields)
        },
    )
}

private fun ErrorCode.kotlinFactoryFunction(fields: List<ErrorField>): LsiPoetFunction {
    val nestedType = LsiDeclaredType(exceptionTypeId)
    return LsiPoetFunction(
        name = creatorName,
        annotations = listOf(JVM_STATIC_ANNOTATION),
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        parameters = buildList {
            add(
                LsiPoetParameter(
                    name = "message",
                    type = KOTLIN_STRING_TYPE.copy(nullability = LsiNullability.NULLABLE),
                    defaultValue = codeLiteral("null"),
                )
            )
            add(
                LsiPoetParameter(
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
    enumType: LsiTypeRef,
): LsiPoetType {
    val allFields = family.declaredFields + declaredFields
    return LsiPoetType(
        name = exceptionSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        annotations = listOf(clientExceptionAnnotation(family.family)),
        modifiers = setOf(LsiPoetModifier.PUBLIC),
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

private fun ErrorFamily.generatedByAnnotation(enumType: LsiTypeRef): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = GENERATED_BY_ID,
        arguments = listOf(
            LsiPoetAnnotationArgument.Named(
                name = "type",
                value = LsiPoetAnnotationValue.ClassValue(enumType),
            )
        ),
    )
}

private fun ErrorFamily.clientExceptionAnnotation(): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = CLIENT_EXCEPTION_ID,
        arguments = buildList {
            add(
                LsiPoetAnnotationArgument.Named(
                    name = "family",
                    value = LsiPoetAnnotationValue.StringValue(family),
                )
            )
            if (codes.isNotEmpty()) {
                add(
                    LsiPoetAnnotationArgument.Named(
                        name = "subTypes",
                        value = LsiPoetAnnotationValue.ArrayValue(
                            codes.map { code ->
                                LsiPoetAnnotationValue.ClassValue(
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

private fun ErrorCode.clientExceptionAnnotation(family: String): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = CLIENT_EXCEPTION_ID,
        arguments = listOf(
            LsiPoetAnnotationArgument.Named(
                name = "family",
                value = LsiPoetAnnotationValue.StringValue(family),
            ),
            LsiPoetAnnotationArgument.Named(
                name = "code",
                value = LsiPoetAnnotationValue.StringValue(code),
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
    val declarationLanguage = (workspace[id] as? LsiTypeDeclaration)?.origin?.language
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

private fun MutableSet<LsiSymbolId>.addType(type: LsiTypeRef) {
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

private fun LsiTypeRef.withRootNullability(nullable: Boolean): LsiTypeRef {
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

private fun LsiTypeRef.declarationSimpleName(): String {
    return (this as LsiDeclaredType).declarationId.requireTypeQualifiedName().substringAfterLast('.')
}

private fun String?.withTrailingLineBreak(): String? {
    return this?.let { documentation -> "$documentation\n" }
}

private fun code(block: LsiPoetCodeBuilder.() -> Unit): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build(block)
}

private fun codeName(name: String): LsiPoetCodeBlock {
    return code { name(name) }
}

private fun codeLiteral(value: String): LsiPoetCodeBlock {
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
    LsiPoetTypeName(CLIENT_EXCEPTION_ID, "org.babyfish.jimmer", listOf("ClientException")),
    LsiPoetTypeName(GENERATED_BY_ID, "org.babyfish.jimmer.internal", listOf("GeneratedBy")),
    LsiPoetTypeName(CODE_BASED_EXCEPTION_ID, "org.babyfish.jimmer.error", listOf("CodeBasedException")),
    LsiPoetTypeName(
        CODE_BASED_RUNTIME_EXCEPTION_ID,
        "org.babyfish.jimmer.error",
        listOf("CodeBasedRuntimeException"),
    ),
    LsiPoetTypeName(JSON_IGNORE_ID, "com.fasterxml.jackson.annotation", listOf("JsonIgnore")),
    LsiPoetTypeName(NON_NULL_ID, "org.jspecify.annotations", listOf("NonNull")),
    LsiPoetTypeName(NULLABLE_ID, "org.jspecify.annotations", listOf("Nullable")),
    LsiPoetTypeName(JAVA_OVERRIDE_ID, "java.lang", listOf("Override")),
    LsiPoetTypeName(JVM_STATIC_ID, "kotlin.jvm", listOf("JvmStatic")),
    LsiPoetTypeName(JAVA_STRING_ID, "java.lang", listOf("String")),
    LsiPoetTypeName(JAVA_OBJECT_ID, "java.lang", listOf("Object")),
    LsiPoetTypeName(JAVA_THROWABLE_ID, "java.lang", listOf("Throwable")),
    LsiPoetTypeName(KOTLIN_STRING_ID, "kotlin", listOf("String")),
    LsiPoetTypeName(KOTLIN_ANY_ID, "kotlin", listOf("Any")),
    LsiPoetTypeName(KOTLIN_THROWABLE_ID, "kotlin", listOf("Throwable")),
    LsiPoetTypeName(LIST_ID, "java.util", listOf("List")),
    LsiPoetTypeName(MAP_ID, "java.util", listOf("Map")),
    LsiPoetTypeName(COLLECTIONS_ID, "java.util", listOf("Collections")),
    LsiPoetTypeName(LINKED_HASH_MAP_ID, "java.util", listOf("LinkedHashMap")),
    LsiPoetTypeName(
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

private val JSON_IGNORE_ANNOTATION = LsiPoetAnnotation(JSON_IGNORE_ID)
private val JSON_IGNORE_GETTER_ANNOTATION = LsiPoetAnnotation(
    type = JSON_IGNORE_ID,
    useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
)
private val NON_NULL_ANNOTATION = LsiPoetAnnotation(NON_NULL_ID)
private val NULLABLE_ANNOTATION = LsiPoetAnnotation(NULLABLE_ID)
private val JAVA_OVERRIDE_ANNOTATION = LsiPoetAnnotation(JAVA_OVERRIDE_ID)
private val JVM_STATIC_ANNOTATION = LsiPoetAnnotation(JVM_STATIC_ID)
