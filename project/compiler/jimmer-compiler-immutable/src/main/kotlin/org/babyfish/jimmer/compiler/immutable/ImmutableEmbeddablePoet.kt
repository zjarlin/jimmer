package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.model.sourceLsiAnnotation
import site.addzero.lsi.clazz.classDeclaration
import site.addzero.lsi.clazz.directSuperTypes

import org.babyfish.jimmer.client.meta.Doc
import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutablePropValueCategory
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.jimmer.targetTypeOf
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
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationCall
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFileNameStyle
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.generatedTopLevelTypeName
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.toLsiTypeNames

internal fun ImmutableSchema.toEmbeddablePoetArtifacts(
    types: List<ImmutableType>,
    language: LsiLanguage,
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
        "Immutable embeddable Poet generation requires Java or Kotlin"
    }
    val typeSystem = LsiTypeSystem(workspace)
    return types.flatMap { type ->
        require(type.kind == ImmutableTypeKind.EMBEDDABLE) {
            "Immutable embeddable Poet generation only supports embeddable types: ${type.id.value}"
        }
        when (language) {
            LsiLanguage.JAVA -> type.toJavaPoetArtifacts(
                schema = this,
                typeSystem = typeSystem,
                workspace = workspace,
                propsDependencies = type.embeddableDependencies(
                    workspace = workspace,
                    runtimeDependencies = JAVA_PROPS_RUNTIME_DEPENDENCIES,
                    includeExpressionHierarchy = false,
                ),
                expressionDependencies = type.embeddableDependencies(
                    workspace = workspace,
                    runtimeDependencies = JAVA_EXPRESSION_RUNTIME_DEPENDENCIES,
                    includeExpressionHierarchy = true,
                ),
            )
            LsiLanguage.KOTLIN -> listOf(
                type.toKotlinPoetArtifact(
                    schema = this,
                    workspace = workspace,
                    dependencies = type.embeddableDependencies(
                        workspace = workspace,
                        runtimeDependencies = KOTLIN_RUNTIME_DEPENDENCIES,
                        includeExpressionHierarchy = false,
                    ),
                )
            )
            LsiLanguage.UNKNOWN -> error("Unsupported immutable embeddable Poet language")
        }
    }
}

private fun ImmutableType.embeddableDependencies(
    workspace: LsiWorkspace,
    runtimeDependencies: Set<LsiSymbolId>,
    includeExpressionHierarchy: Boolean,
): EmbeddableArtifactDependencies {
    val symbols = buildSet {
        add(id)
        add(GENERATED_BY_ID)
        add(TYPED_PROP_ID)
        annotations.forEach(::addAnnotationDependencies)
        props.forEach { prop ->
            add(prop.id)
            add(prop.declarationId)
            add(prop.declaringTypeId)
            addAll(prop.overrideChain)
            addTypeDependencies(prop.type)
            prop.annotations.forEach(::addAnnotationDependencies)
            prop.targetTypeId?.let(::add)
        }
        addAll(runtimeDependencies)
    }.toMutableSet()
    val hierarchySources = sortedSetOf<LsiSource>()
    if (includeExpressionHierarchy) {
        val hierarchyRoots = buildSet {
            props.forEach { prop -> prop.type.collectDeclaredTypeIds(this) }
        }
        symbols.addHierarchyDependencies(hierarchyRoots, workspace, hierarchySources)
    }
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySources = buildSet {
        workspace.originatingSources(symbols)
            .filterTo(this) { source -> source.kind != LsiSourceKind.BINARY }
        hierarchySources.filterTo(this) { source -> source.kind != LsiSourceKind.BINARY }
        addAll(originatingSources)
    }
    return EmbeddableArtifactDependencies(
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = symbols,
        dependencySources = dependencySources,
    )
}

private fun MutableSet<LsiSymbolId>.addHierarchyDependencies(
    rootTypeIds: Set<LsiSymbolId>,
    workspace: LsiWorkspace,
    sources: MutableSet<LsiSource>,
) {
    val pending = ArrayDeque(rootTypeIds.sorted())
    val visited = mutableSetOf<LsiSymbolId>()
    while (pending.isNotEmpty()) {
        val typeId = pending.removeFirst()
        if (!visited.add(typeId)) {
            continue
        }
        val declaration = workspace.classDeclaration(typeId) ?: continue
        declaration.origin.source?.let(sources::add)
        declaration.typeParameters.forEach { parameter ->
            parameter.upperBounds.forEach { bound ->
                addTypeDependencies(bound)
                bound.collectDeclaredTypeIds(pending)
            }
        }
        declaration.directSuperTypes.forEach { superType ->
            addTypeDependencies(superType)
            superType.collectDeclaredTypeIds(pending)
        }
    }
}

private fun LsiType.collectDeclaredTypeIds(target: MutableCollection<LsiSymbolId>) {
    when (this) {
        is LsiArrayType -> elementType.collectDeclaredTypeIds(target)
        is LsiDeclaredType -> {
            target += declarationId
            arguments.forEach { argument -> argument.type?.collectDeclaredTypeIds(target) }
        }
        is LsiFunctionType -> {
            receiverType?.collectDeclaredTypeIds(target)
            parameterTypes.forEach { parameter -> parameter.collectDeclaredTypeIds(target) }
            returnType.collectDeclaredTypeIds(target)
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> Unit
    }
}

private fun MutableSet<LsiSymbolId>.addTypeDependencies(type: LsiType) {
    type.annotations.forEach(::addAnnotationDependencies)
    when (type) {
        is LsiArrayType -> addTypeDependencies(type.elementType)
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.forEach { argument -> argument.type?.let(::addTypeDependencies) }
        }
        is LsiFunctionType -> {
            type.receiverType?.let(::addTypeDependencies)
            type.parameterTypes.forEach(::addTypeDependencies)
            addTypeDependencies(type.returnType)
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> Unit
    }
}

private fun MutableSet<LsiSymbolId>.addAnnotationDependencies(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument -> addAnnotationValueDependencies(argument.value) }
}

private fun MutableSet<LsiSymbolId>.addAnnotationValueDependencies(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::addAnnotationValueDependencies)
        is LsiAnnotationValue.ClassValue -> addTypeDependencies(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> addAnnotationDependencies(value.annotation)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> Unit
    }
}

private fun ImmutableType.toJavaPoetArtifacts(
    schema: ImmutableSchema,
    typeSystem: LsiTypeSystem,
    workspace: LsiWorkspace,
    propsDependencies: EmbeddableArtifactDependencies,
    expressionDependencies: EmbeddableArtifactDependencies,
): List<LsiSourceArtifact> {
    return listOf(
        propsDependencies.artifact(
            workspace,
            schema,
            LsiFile(
                language = LsiLanguage.JAVA,
                packageName = packageName,
                fileName = propsSimpleName,
                members = listOf(javaPropsType(schema)),
            )
        ),
        expressionDependencies.artifact(
            workspace,
            schema,
            LsiFile(
                language = LsiLanguage.JAVA,
                packageName = packageName,
                fileName = propExpressionSimpleName,
                members = listOf(javaPropExpressionType(schema, typeSystem)),
            )
        ),
    )
}

private fun ImmutableType.javaPropsType(schema: ImmutableSchema): LsiClass {
    return LsiClass(
        name = propsSimpleName,
        kind = LsiTypeDeclarationKind.INTERFACE,
        annotations = listOf(generatedByAnnotation(modelType)),
        modifiers = setOf(LsiModifier.PUBLIC),
        members = props.map { prop -> javaTypedPropField(schema, prop) },
    )
}

private fun ImmutableType.javaTypedPropField(
    schema: ImmutableSchema,
    prop: ImmutableProp,
): LsiField {
    val kind = schema.typedPropValueCategory(prop)
    return LsiField(
        name = prop.fieldName(),
        type = declaredType(
            kind.typedPropTypeId,
            modelType,
            prop.elementTypeOrSelf().withoutTypeAnnotations(),
        ),
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
            LsiModifier.FINAL,
        ),
        initializer = code {
            line()
            indent {
                type(TYPED_PROP_TYPE)
                text(".")
                name(kind.factoryName)
                text("(")
                type(IMMUTABLE_TYPE)
                text(".get(")
                type(modelType)
                text(".class).getProp(")
                string(prop.name)
                text("))")
            }
        },
    )
}

private fun ImmutableType.javaPropExpressionType(
    schema: ImmutableSchema,
    typeSystem: LsiTypeSystem,
): LsiClass {
    return LsiClass(
        name = propExpressionSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = listOf(generatedByAnnotation(modelType)),
        modifiers = setOf(LsiModifier.PUBLIC),
        superClass = declaredType(ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_ID, modelType),
        members = buildList {
            add(
                LsiConstructor(
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(
                        LsiParameter(
                            name = "raw",
                            type = declaredType(EMBEDDED_PROP_EXPRESSION_ID, modelType),
                        )
                    ),
                    delegationCall = LsiDelegationCall(
                        target = LsiDelegationTarget.SUPER,
                        arguments = listOf(code { name("raw") }),
                    ),
                )
            )
            add(
                LsiConstructor(
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(
                        LsiParameter("base", propExpressionType),
                        LsiParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE),
                    ),
                    delegationCall = LsiDelegationCall(
                        target = LsiDelegationTarget.SUPER,
                        arguments = listOf(
                            code { name("base") },
                            code { name("baseTableOwner") },
                        ),
                    ),
                )
            )
            props.forEach { prop -> add(javaPropFunction(schema, prop, typeSystem)) }
            add(javaBaseTableOwnerFunction())
        },
    )
}

private fun ImmutableType.javaPropFunction(
    schema: ImmutableSchema,
    prop: ImmutableProp,
    typeSystem: LsiTypeSystem,
): LsiFunction {
    val targetType = schema.targetTypeOf(prop)
    val returnType = targetType?.propExpressionType ?: prop.javaExpressionType(typeSystem)
    return LsiFunction(
        name = prop.name,
        modifiers = setOf(LsiModifier.PUBLIC),
        documentation = prop.documentation?.let(Doc::parse)?.value,
        returnType = returnType,
        body = code {
            returnValue {
                if (targetType != null) {
                    text("new ")
                    type(returnType)
                    text("(")
                }
                text("__get(")
                type(propsType)
                text(".")
                name(prop.fieldName())
                text(".unwrap())")
                if (targetType != null) {
                    text(")")
                }
            }
        },
    )
}

private fun ImmutableType.javaBaseTableOwnerFunction(): LsiFunction {
    return LsiFunction(
        name = "__baseTableOwner",
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.OVERRIDE,
        ),
        parameters = listOf(LsiParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE)),
        returnType = propExpressionType,
        body = code {
            returnValue {
                text("new ")
                type(propExpressionType)
                text("(this, ")
                name("baseTableOwner")
                text(")")
            }
        },
    )
}

private fun ImmutableProp.javaExpressionType(typeSystem: LsiTypeSystem): LsiType {
    val renderedType = type.withoutTypeAnnotations()
    return when (expressionKind(typeSystem)) {
        JimmerImmutablePropExpressionKind.GENERIC -> declaredType(PROP_EXPRESSION_ID, renderedType)
        JimmerImmutablePropExpressionKind.NUMERIC -> declaredType(PROP_NUMERIC_EXPRESSION_ID, renderedType)
        JimmerImmutablePropExpressionKind.STRING -> PROP_STRING_EXPRESSION_TYPE
        JimmerImmutablePropExpressionKind.DATE -> declaredType(PROP_DATE_EXPRESSION_ID, renderedType)
        JimmerImmutablePropExpressionKind.TEMPORAL -> declaredType(PROP_TEMPORAL_EXPRESSION_ID, renderedType)
        JimmerImmutablePropExpressionKind.COMPARABLE -> declaredType(
            PROP_COMPARABLE_EXPRESSION_ID,
            renderedType,
        )
    }
}

private fun ImmutableType.toKotlinPoetArtifact(
    schema: ImmutableSchema,
    workspace: LsiWorkspace,
    dependencies: EmbeddableArtifactDependencies,
): LsiSourceArtifact {
    val sourceBaseName = workspace.immutableSourceBaseName(this)
    return dependencies.artifact(
        workspace,
        schema,
        LsiFile(
            language = LsiLanguage.KOTLIN,
            packageName = packageName,
            fileName = "${sourceBaseName}Props",
            fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
            annotations = listOf(
                FILE_WARNING_SUPPRESSION,
                generatedByAnnotation(modelType, LsiAnnotationUseSiteTarget.FILE),
            ),
            members = buildList {
                props.forEach { prop ->
                    kotlinEmbeddedProp(prop, nullable = false)?.let(::add)
                    kotlinEmbeddedProp(prop, nullable = true)?.let(::add)
                }
                add(kotlinFetchByFunction(nullable = false))
                add(kotlinFetchByFunction(nullable = true))
                add(kotlinPropsObject(schema))
            },
        )
    )
}

private fun ImmutableType.kotlinEmbeddedProp(
    prop: ImmutableProp,
    nullable: Boolean,
): LsiProperty? {
    if (!nullable && prop.nullable) {
        return null
    }
    val receiverTypeId = when {
        prop.nullable -> K_EMBEDDED_PROP_EXPRESSION_ID
        nullable -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID
        else -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID
    }
    val receiverType = declaredType(receiverTypeId, modelType)
    val propType = prop.type
        .withoutTypeAnnotations()
        .withKotlinExpressionRoot()
    val returnTypeId = when {
        prop.embedded && nullable -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID
        prop.embedded -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID
        nullable -> K_NULLABLE_PROP_EXPRESSION_ID
        else -> K_NON_NULL_PROP_EXPRESSION_ID
    }
    val returnType = declaredType(returnTypeId, propType)
    return LsiProperty(
        name = prop.name,
        type = returnType,
        mutable = false,
        receiverType = receiverType,
        getter = LsiAccessor(
            annotations = listOf(generatedByAnnotation(modelType)),
            body = code {
                returnValue {
                    text("get")
                    if (prop.embedded || !nullable || prop.nullable) {
                        text("<")
                        type(propType)
                        text(">")
                    }
                    text("(")
                    type(propsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap())")
                    if (prop.embedded || !nullable || prop.nullable) {
                        text(" as ")
                        type(returnType)
                    }
                }
            },
        ),
    )
}

private fun ImmutableType.kotlinFetchByFunction(nullable: Boolean): LsiFunction {
    val receiverTypeId = if (nullable) {
        K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID
    } else {
        K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID
    }
    return LsiFunction(
        name = "fetchBy",
        annotations = listOf(generatedByAnnotation(modelType)),
        receiverType = declaredType(receiverTypeId, modelType),
        parameters = listOf(
            LsiParameter(
                name = "block",
                type = LsiFunctionType(
                    receiverType = fetcherDslType,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                ),
            )
        ),
        returnType = declaredType(
            SELECTION_ID,
            modelType.withRootNullability(nullable),
        ),
        body = code {
            returnValue {
                text("fetch(")
                topLevelMember(NEW_FETCHER_PACKAGE, "newFetcher", extension = false)
                text("(")
                type(modelType)
                text("::class).")
                topLevelMember(packageName, "by", extension = true)
                text("(")
                name("block")
                text("))")
            }
        },
    )
}

private fun ImmutableType.kotlinPropsObject(schema: ImmutableSchema): LsiClass {
    return LsiClass(
        name = propsSimpleName,
        kind = LsiTypeDeclarationKind.OBJECT,
        annotations = listOf(generatedByAnnotation(modelType)),
        members = props.map { prop -> kotlinTypedProp(schema, prop) },
    )
}

private fun ImmutableType.kotlinTypedProp(
    schema: ImmutableSchema,
    prop: ImmutableProp,
): LsiProperty {
    val kind = schema.typedPropValueCategory(prop)
    return LsiProperty(
        name = prop.fieldName(),
        type = declaredType(
            kind.typedPropTypeId,
            modelType,
            prop.elementTypeOrSelf().withoutTypeAnnotations(),
        ),
        mutable = false,
        initializer = code {
            type(TYPED_PROP_TYPE)
            text(".")
            name(kind.factoryName)
            text("(")
            type(modelType)
            text("::")
            name(prop.name)
            text(".")
            topLevelMember(TO_IMMUTABLE_PROP_PACKAGE, "toImmutableProp", extension = true)
            text("())")
        },
    )
}

private fun generatedByAnnotation(
    type: LsiType,
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
): LsiAnnotation {
    return sourceLsiAnnotation(
        type = GENERATED_BY_ID,
        arguments = listOf(
            LsiSourceAnnotationArgument.Named(
                name = "type",
                value = LsiAnnotationValue.ClassValue(type),
            )
        ),
        useSiteTarget = useSiteTarget,
    )
}

private fun declaredType(
    id: LsiSymbolId,
    vararg arguments: LsiType,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = id,
        arguments = arguments.map(LsiTypeArgument::invariant),
    )
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

private fun LsiType.withKotlinExpressionRoot(): LsiType {
    return when (this) {
        is LsiPrimitiveType -> copy(
            nullability = LsiNullability.NON_NULL,
            boxed = boxed && nullability != LsiNullability.NULLABLE,
        )
        else -> withRootNullability(nullable = false)
    }
}

private fun LsiType.withoutTypeAnnotations(): LsiType {
    return when (this) {
        is LsiArrayType -> copy(
            elementType = elementType.withoutTypeAnnotations(),
            annotations = emptyList(),
        )
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.withoutTypeAnnotations())
            },
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.withoutTypeAnnotations(),
            receiverType = receiverType?.withoutTypeAnnotations(),
            parameterTypes = parameterTypes.map(LsiType::withoutTypeAnnotations),
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(annotations = emptyList())
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

private fun code(block: LsiCodeBuilder.() -> Unit): LsiCodeBlock {
    return LsiCodeBlock.build(block)
}

private val ImmutableType.modelType: LsiDeclaredType
    get() = LsiDeclaredType(id)

private val ImmutableType.propsSimpleName: String
    get() = "${simpleName}Props"

private val ImmutableType.propsType: LsiDeclaredType
    get() = LsiDeclaredType(generatedTypeId(propsSimpleName))

private val ImmutableType.propExpressionSimpleName: String
    get() = "${simpleName}PropExpression"

private val ImmutableType.propExpressionType: LsiDeclaredType
    get() = LsiDeclaredType(generatedTypeId(propExpressionSimpleName))

private val ImmutableType.fetcherDslType: LsiDeclaredType
    get() = LsiDeclaredType(generatedTypeId("${simpleName}FetcherDsl"))

private fun ImmutableType.generatedTypeId(generatedSimpleName: String): LsiSymbolId {
    val qualifiedName = if (packageName.isEmpty()) {
        generatedSimpleName
    } else {
        "$packageName.$generatedSimpleName"
    }
    return LsiSymbolId.type(qualifiedName)
}

private val ImmutablePropValueCategory.typedPropTypeId: LsiSymbolId
    get() = when (this) {
        ImmutablePropValueCategory.SCALAR -> TYPED_PROP_SCALAR_ID
        ImmutablePropValueCategory.SCALAR_LIST -> TYPED_PROP_SCALAR_LIST_ID
        ImmutablePropValueCategory.REFERENCE -> TYPED_PROP_REFERENCE_ID
        ImmutablePropValueCategory.REFERENCE_LIST -> TYPED_PROP_REFERENCE_LIST_ID
    }

private val ImmutablePropValueCategory.factoryName: String
    get() = when (this) {
        ImmutablePropValueCategory.SCALAR -> "scalar"
        ImmutablePropValueCategory.SCALAR_LIST -> "scalarList"
        ImmutablePropValueCategory.REFERENCE -> "reference"
        ImmutablePropValueCategory.REFERENCE_LIST -> "referenceList"
    }

private fun EmbeddableArtifactDependencies.artifact(
    workspace: LsiWorkspace,
    schema: ImmutableSchema,
    file: LsiFile,
): LsiSourceArtifact {
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiTypeNames(
            file.referencedTypeIds,
            additional = schema.generatedEmbeddablePoetTypeNames() + EMBEDDABLE_RUNTIME_TYPE_NAMES,
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

private fun ImmutableSchema.generatedEmbeddablePoetTypeNames(): List<LsiTypeName> {
    return types.flatMap { type ->
        listOf(
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}Props"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}PropExpression"),
            generatedTopLevelTypeName(type.packageName, "${type.simpleName}FetcherDsl"),
        )
    }.distinctBy { typeName -> typeName.typeId }
}

private data class EmbeddableArtifactDependencies(
    val originatingSymbols: Set<LsiSymbolId>,
    val originatingSources: Set<LsiSource>,
    val dependencySymbols: Set<LsiSymbolId>,
    val dependencySources: Set<LsiSource>,
)

private val GENERATED_BY_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val TYPED_PROP_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp")
private val TYPED_PROP_TYPE = LsiDeclaredType(TYPED_PROP_ID)
private val TYPED_PROP_SCALAR_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.Scalar")
private val TYPED_PROP_SCALAR_LIST_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.ScalarList")
private val TYPED_PROP_REFERENCE_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.Reference")
private val TYPED_PROP_REFERENCE_LIST_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.ReferenceList")

private val IMMUTABLE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableType")
private val IMMUTABLE_TYPE = LsiDeclaredType(IMMUTABLE_TYPE_ID)
private val PROP_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression")
private val EMBEDDED_PROP_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Embedded")
private val PROP_NUMERIC_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Num")
private val PROP_STRING_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Str")
private val PROP_STRING_EXPRESSION_TYPE = LsiDeclaredType(PROP_STRING_EXPRESSION_ID)
private val PROP_DATE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Dt")
private val PROP_TEMPORAL_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Tp")
private val PROP_COMPARABLE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Cmp")
private val ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.embedded.AbstractTypedEmbeddedPropExpression"
)
private val BASE_TABLE_OWNER_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner")
private val BASE_TABLE_OWNER_TYPE = LsiDeclaredType(BASE_TABLE_OWNER_ID)
private val JAVA_OVERRIDE_ID = LsiSymbolId.type("java.lang.Override")

private val KOTLIN_SUPPRESS_ID = LsiSymbolId.type("kotlin.Suppress")
private val K_NON_NULL_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression"
)
private val K_NULLABLE_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullablePropExpression"
)
private val K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullEmbeddedPropExpression"
)
private val K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullableEmbeddedPropExpression"
)
private val K_EMBEDDED_PROP_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KEmbeddedPropExpression"
)
private val SELECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Selection")
private const val NEW_FETCHER_PACKAGE = "org.babyfish.jimmer.sql.kt.fetcher"
private const val TO_IMMUTABLE_PROP_PACKAGE = "org.babyfish.jimmer.kt"

private val FILE_WARNING_SUPPRESSION = sourceLsiAnnotation(
    type = KOTLIN_SUPPRESS_ID,
    arguments = listOf(
        LsiSourceAnnotationArgument.Positional(
            LsiAnnotationValue.StringValue("warnings")
        )
    ),
    useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
)

private val EMBEDDABLE_RUNTIME_TYPE_NAMES = listOf(
    "org.babyfish.jimmer.internal.GeneratedBy",
    "org.babyfish.jimmer.meta.TypedProp",
    "org.babyfish.jimmer.meta.ImmutableType",
    "org.babyfish.jimmer.sql.ast.PropExpression",
    "org.babyfish.jimmer.sql.ast.embedded.AbstractTypedEmbeddedPropExpression",
    "org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner",
    "java.lang.Override",
    "java.lang.String",
    "kotlin.Suppress",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullablePropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullEmbeddedPropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullableEmbeddedPropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KEmbeddedPropExpression",
    "org.babyfish.jimmer.sql.ast.Selection",
).map(LsiSymbolId::type).map(LsiSymbolId::topLevelPoetTypeName) + listOf(
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "Scalar")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "ScalarList")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "Reference")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "ReferenceList")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Embedded")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Num")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Str")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Dt")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Tp")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Cmp")),
)

private val JAVA_PROPS_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    TYPED_PROP_ID,
    TYPED_PROP_SCALAR_ID,
    TYPED_PROP_SCALAR_LIST_ID,
    TYPED_PROP_REFERENCE_ID,
    TYPED_PROP_REFERENCE_LIST_ID,
    IMMUTABLE_TYPE_ID,
)

private val JAVA_EXPRESSION_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    PROP_EXPRESSION_ID,
    EMBEDDED_PROP_EXPRESSION_ID,
    PROP_NUMERIC_EXPRESSION_ID,
    PROP_STRING_EXPRESSION_ID,
    PROP_DATE_EXPRESSION_ID,
    PROP_TEMPORAL_EXPRESSION_ID,
    PROP_COMPARABLE_EXPRESSION_ID,
    ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_ID,
    BASE_TABLE_OWNER_ID,
    JAVA_OVERRIDE_ID,
)

private val KOTLIN_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    KOTLIN_SUPPRESS_ID,
    TYPED_PROP_ID,
    TYPED_PROP_SCALAR_ID,
    TYPED_PROP_SCALAR_LIST_ID,
    TYPED_PROP_REFERENCE_ID,
    TYPED_PROP_REFERENCE_LIST_ID,
    K_NON_NULL_PROP_EXPRESSION_ID,
    K_NULLABLE_PROP_EXPRESSION_ID,
    K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID,
    K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID,
    K_EMBEDDED_PROP_EXPRESSION_ID,
    SELECTION_ID,
)
