package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.client.meta.Doc
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.primaryLineageOwner
import site.addzero.lsi.jimmer.semanticDependencySymbols
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.jimmer.targetTypeOf
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
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
import site.addzero.lsi.poet.LsiPoetFileNameStyle
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/**
 * 将不可变类型查询语义降低为共享 LSI Poet 产物。
 */
internal fun ImmutableSchema.toQueryPoetArtifacts(
    types: List<ImmutableType>,
    language: LsiLanguage,
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
        "Immutable query Poet generation requires Java or Kotlin"
    }
    return types.flatMap { type ->
        require(type.typeParameterIds.isEmpty()) {
            "Immutable query Poet generation does not support generic type '${type.id.value}'"
        }
        when (language) {
            LsiLanguage.JAVA -> {
                require(type.kind in JAVA_QUERY_TYPE_KINDS) {
                    "Java immutable query Poet generation does not support type '${type.id.value}'"
                }
                JavaQueryPoetContext(this, type, workspace).artifacts()
            }
            LsiLanguage.KOTLIN -> {
                require(type.kind in KOTLIN_QUERY_TYPE_KINDS) {
                    "Kotlin immutable query Poet generation does not support type '${type.id.value}'"
                }
                listOf(KotlinQueryPoetContext(this, type, workspace).artifact())
            }
            LsiLanguage.UNKNOWN -> error("Unsupported immutable query Poet language")
        }
    }
}

private class KotlinQueryPoetContext(
    private val schema: ImmutableSchema,
    private val type: ImmutableType,
    private val workspace: LsiWorkspace,
) {
    private val modelType = LsiDeclaredType(type.id)
    private val propsType = type.generatedQueryType("${type.simpleName}$PROPS_SUFFIX")
    private val fetcherDslType = type.generatedQueryType("${type.simpleName}$FETCHER_DSL_SUFFIX")

    fun artifact(): LsiPoetArtifact {
        val sourceBaseName = workspace.immutableSourceBaseName(type)
        val branchDependent = schema.isBranchDependent(type)
        val dependencies = schema.queryDependencies(
            type = type,
            workspace = workspace,
            language = LsiLanguage.KOTLIN,
            branchDependent = branchDependent,
        )
        return dependencies.artifact(
            workspace = workspace,
            schema = schema,
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = type.packageName,
                fileName = "$sourceBaseName$PROPS_SUFFIX",
                fileNameStyle = LsiPoetFileNameStyle.KOTLIN_SOURCE_STEM,
                annotations = listOf(
                    KOTLIN_FILE_WARNING_SUPPRESSION,
                    generatedByAnnotation(modelType, fileTarget = true),
                ),
                members = buildList {
                    schema.orderedProps(type).forEach { prop -> addPropMembers(prop) }
                    if (type.kind == ImmutableTypeKind.ENTITY) {
                        add(remoteIdProperty(nullable = false))
                        add(remoteIdProperty(nullable = true))
                        add(fetchByFunction(nullable = false))
                        add(fetchByFunction(nullable = true))
                    }
                    addAll(polymorphicFunctions())
                    add(propsObject())
                },
            ),
            branchDependent = branchDependent,
        )
    }

    private fun MutableList<LsiPoetMember>.addPropMembers(prop: ImmutableProp) {
        addQueryProperty(prop, nonNullTable = true, outerJoin = false, tableEx = false)?.let(::add)
        addQueryProperty(prop, nonNullTable = false, outerJoin = false, tableEx = false)?.let(::add)
        addQueryProperty(prop, nonNullTable = true, outerJoin = true, tableEx = false)?.let(::add)
        addQueryProperty(prop, nonNullTable = false, outerJoin = true, tableEx = false)?.let(::add)
        addQueryProperty(prop, nonNullTable = true, outerJoin = false, tableEx = true)?.let(::add)
        addQueryProperty(prop, nonNullTable = false, outerJoin = false, tableEx = true)?.let(::add)
        addQueryProperty(prop, nonNullTable = true, outerJoin = true, tableEx = true)?.let(::add)
        addQueryProperty(prop, nonNullTable = false, outerJoin = true, tableEx = true)?.let(::add)
        associatedIdProperty(prop, nonNullTable = true, tableEx = false)?.let(::add)
        associatedIdProperty(prop, nonNullTable = false, tableEx = false)?.let(::add)
        associatedIdProperty(prop, nonNullTable = true, tableEx = true)?.let(::add)
        associatedIdProperty(prop, nonNullTable = false, tableEx = true)?.let(::add)
    }

    private fun addQueryProperty(
        prop: ImmutableProp,
        nonNullTable: Boolean,
        outerJoin: Boolean,
        tableEx: Boolean,
    ): LsiPoetMember? {
        val entityAssociation = schema.isEntityAssociation(prop)
        if (outerJoin && !entityAssociation) return null
        if (nonNullTable && (entityAssociation || prop.nullable)) return null
        if (tableEx && !entityAssociation) return null
        if (prop.list && entityAssociation && !tableEx) {
            return if (!outerJoin && schema.isDsl(prop, workspace, tableEx = true)) {
                existsFunction(prop)
            } else {
                null
            }
        }
        if (!schema.isDsl(prop, workspace, tableEx)) return null
        val receiverType = declaredType(
            when {
                tableEx -> K_TABLE_EX_ID
                entityAssociation || prop.nullable -> K_PROPS_ID
                nonNullTable -> K_NON_NULL_PROPS_ID
                else -> K_NULLABLE_PROPS_ID
            },
            modelType,
        )
        val propertyName = if (outerJoin) "${prop.name}?" else prop.name
        val operationName = when {
            outerJoin -> "outerJoin"
            entityAssociation -> "join"
            else -> "get"
        }
        val returnType = queryPropReturnType(prop, nonNullTable, outerJoin, tableEx)
        return LsiPoetProperty(
            name = propertyName,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = returnType,
            mutable = false,
            receiverType = receiverType,
            getter = LsiPoetAccessor(
                annotations = listOf(generatedByAnnotation(modelType)),
                body = code {
                    returnValue {
                        when {
                            prop.remote -> {
                                text("org.babyfish.jimmer.sql.kt.ast.table.KRemoteRef.protect(")
                                name(operationName)
                                text("(")
                                type(propsType)
                                text(".")
                                name(prop.fieldName())
                                text(".unwrap()))")
                            }
                            operationName == "get" -> {
                                text("get<")
                                type(propTargetType(prop))
                                text(">(")
                                type(propsType)
                                text(".")
                                name(prop.fieldName())
                                text(".unwrap()) as ")
                                type(returnType)
                            }
                            else -> {
                                name(operationName)
                                text("(")
                                type(propsType)
                                text(".")
                                name(prop.fieldName())
                                text(".unwrap())")
                            }
                        }
                    }
                },
            ),
        )
    }

    private fun queryPropReturnType(
        prop: ImmutableProp,
        nonNullTable: Boolean,
        outerJoin: Boolean,
        tableEx: Boolean,
    ): LsiTypeRef {
        val entityAssociation = schema.isEntityAssociation(prop)
        val rawReturnTypeId = when {
            prop.remote -> if (outerJoin) K_NULLABLE_REMOTE_REF_ID else K_NON_NULL_REMOTE_REF_ID
            entityAssociation && tableEx -> {
                if (outerJoin) K_NULLABLE_TABLE_EX_ID else K_NON_NULL_TABLE_EX_ID
            }
            !prop.list && entityAssociation -> {
                if (outerJoin) K_NULLABLE_TABLE_ID else K_NON_NULL_TABLE_ID
            }
            prop.embedded -> {
                if (nonNullTable) K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID else K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID
            }
            else -> {
                if (nonNullTable) K_NON_NULL_PROP_EXPRESSION_ID else K_NULLABLE_PROP_EXPRESSION_ID
            }
        }
        val elementType = propTargetType(prop)
        val targetType = if (prop.list && !entityAssociation) {
            declaredType(KOTLIN_LIST_ID, elementType)
        } else {
            elementType
        }
        return declaredType(rawReturnTypeId, targetType)
    }

    private fun existsFunction(prop: ImmutableProp): LsiPoetFunction {
        val targetType = schema.targetTypeOf(prop)?.let { target -> LsiDeclaredType(target.id) } ?: KOTLIN_ANY_TYPE
        return LsiPoetFunction(
            name = prop.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            receiverType = declaredType(K_PROPS_ID, modelType),
            parameters = listOf(
                LsiPoetParameter(
                    name = "block",
                    type = LsiFunctionType(
                        receiverType = declaredType(K_IMPLICIT_SUB_QUERY_TABLE_ID, targetType),
                        returnType = declaredType(
                            K_NON_NULL_EXPRESSION_ID,
                            BOOLEAN_TYPE,
                        ).withRootNullability(nullable = true),
                    ),
                )
            ),
            returnType = declaredType(K_NON_NULL_EXPRESSION_ID, BOOLEAN_TYPE)
                .withRootNullability(nullable = true),
            body = code {
                returnValue {
                    text("exists(")
                    type(propsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap(), block)")
                }
            },
        )
    }

    private fun associatedIdProperty(
        prop: ImmutableProp,
        nonNullTable: Boolean,
        tableEx: Boolean,
    ): LsiPoetProperty? {
        val propertyName = schema.associatedIdPropName(type, prop) ?: return null
        if (nonNullTable && prop.nullable) return null
        if (
            prop.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isEntityAssociation(prop) ||
            prop.list != tableEx
        ) {
            return null
        }
        val targetIdProp = schema.targetIdPropOf(prop) ?: return null
        val receiverType = declaredType(
            when {
                prop.nullable -> K_PROPS_ID
                tableEx && nonNullTable -> K_NON_NULL_TABLE_EX_ID
                tableEx -> K_NULLABLE_TABLE_EX_ID
                nonNullTable -> K_NON_NULL_TABLE_ID
                else -> K_NULLABLE_PROPS_ID
            },
            modelType,
        )
        val returnType = declaredType(
            when {
                targetIdProp.embedded && nonNullTable -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID
                targetIdProp.embedded -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID
                nonNullTable -> K_NON_NULL_PROP_EXPRESSION_ID
                else -> K_NULLABLE_PROP_EXPRESSION_ID
            },
            targetIdProp.type.toQueryKotlinType(),
        )
        return LsiPoetProperty(
            name = propertyName,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = returnType,
            mutable = false,
            receiverType = receiverType,
            getter = LsiPoetAccessor(
                annotations = listOf(generatedByAnnotation(modelType)),
                body = code {
                    returnValue {
                        text("getAssociatedId<")
                        type(targetIdProp.type.toQueryKotlinType().withRootNullability(nullable = false))
                        text(">(")
                        type(propsType)
                        text(".")
                        name(prop.fieldName())
                        text(".unwrap()) as ")
                        type(returnType)
                    }
                },
            ),
        )
    }

    private fun remoteIdProperty(nullable: Boolean): LsiPoetProperty {
        val idProp = type.idPropId?.let(schema.propsById::get)
            ?: error("Entity immutable type '${type.id.value}' must declare an id property")
        val idType = idProp.type.toQueryKotlinType()
        val returnType = declaredType(
            if (nullable) K_NULLABLE_PROP_EXPRESSION_ID else K_NON_NULL_PROP_EXPRESSION_ID,
            idType,
        )
        return LsiPoetProperty(
            name = idProp.name,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = returnType,
            mutable = false,
            receiverType = declaredType(
                if (nullable) K_NULLABLE_REMOTE_REF_ID else K_NON_NULL_REMOTE_REF_ID,
                modelType,
            ),
            getter = LsiPoetAccessor(
                annotations = listOf(generatedByAnnotation(modelType)),
                body = code {
                    returnValue {
                        text("(this as ")
                        type(declaredType(K_REMOTE_REF_IMPLEMENTOR_ID, LsiTypeArgument.STAR))
                        text(").id<")
                        type(idType.withRootNullability(nullable = false))
                        text(">() as ")
                        type(returnType)
                    }
                },
            ),
        )
    }

    private fun fetchByFunction(nullable: Boolean): LsiPoetFunction {
        return LsiPoetFunction(
            name = "fetchBy",
            annotations = listOf(generatedByAnnotation(modelType)),
            receiverType = declaredType(
                if (nullable) K_NULLABLE_TABLE_ID else K_NON_NULL_TABLE_ID,
                modelType,
            ),
            parameters = listOf(
                LsiPoetParameter(
                    name = "block",
                    type = LsiFunctionType(
                        receiverType = fetcherDslType,
                        returnType = UNIT_TYPE,
                    ),
                )
            ),
            returnType = declaredType(SELECTION_ID, modelType.withRootNullability(nullable)),
            body = code {
                returnValue {
                    text("fetch(")
                    topLevelMember(NEW_FETCHER_PACKAGE, "newFetcher", extension = false)
                    text("(")
                    type(modelType)
                    text("::class).")
                    topLevelMember(type.packageName, "by", extension = true)
                    text("(block))")
                }
            },
        )
    }

    private fun polymorphicFunctions(): List<LsiPoetFunction> {
        if (schema.strictPrimarySubtypesOf(type).isEmpty()) return emptyList()
        return listOf(
            treatAsFunction(receiverNullable = false, optional = false),
            treatAsFunction(receiverNullable = true, optional = false),
            treatAsFunction(receiverNullable = false, optional = true),
            treatAsFunction(receiverNullable = true, optional = true),
            reifiedTreatAsFunction(receiverNullable = false, optional = false),
            reifiedTreatAsFunction(receiverNullable = true, optional = false),
            reifiedTreatAsFunction(receiverNullable = false, optional = true),
            reifiedTreatAsFunction(receiverNullable = true, optional = true),
            instanceOfFunction(),
            reifiedInstanceOfFunction(),
            exactTypeFunction(),
            reifiedExactTypeFunction(),
        )
    }

    private fun treatAsFunction(receiverNullable: Boolean, optional: Boolean): LsiPoetFunction {
        val parameterId = LsiSymbolId.typeParameter(type.generatedQueryTypeId("query:$receiverNullable:$optional"), "S")
        val parameter = LsiTypeParameter(parameterId, "S", upperBounds = listOf(modelType))
        val parameterType = LsiTypeParameterRef(parameterId)
        val functionName = if (optional) "tryTreatAs" else "treatAs"
        return LsiPoetFunction(
            name = functionName,
            annotations = listOf(generatedByAnnotation(modelType)),
            typeParameters = listOf(parameter),
            receiverType = declaredType(
                if (receiverNullable) K_NULLABLE_TABLE_EX_ID else K_NON_NULL_TABLE_EX_ID,
                modelType,
            ),
            parameters = listOf(LsiPoetParameter("type", declaredType(K_CLASS_ID, parameterType))),
            returnType = declaredType(
                if (optional) K_NULLABLE_TABLE_EX_ID else K_NON_NULL_TABLE_EX_ID,
                parameterType,
            ),
            body = code {
                returnValue {
                    type(K_POLYMORPHIC_TABLES_TYPE)
                    text(".")
                    name(functionName)
                    text("(this, type)")
                }
            },
        )
    }

    private fun reifiedTreatAsFunction(receiverNullable: Boolean, optional: Boolean): LsiPoetFunction {
        val parameterId = LsiSymbolId.typeParameter(type.generatedQueryTypeId("reified-query:$receiverNullable:$optional"), "S")
        val parameter = LsiTypeParameter(parameterId, "S", upperBounds = listOf(modelType))
        val parameterType = LsiTypeParameterRef(parameterId)
        val functionName = if (optional) "tryTreatAs" else "treatAs"
        return LsiPoetFunction(
            name = functionName,
            annotations = listOf(generatedByAnnotation(modelType)),
            modifiers = setOf(LsiPoetModifier.INLINE),
            typeParameters = listOf(parameter),
            reifiedTypeParameterIds = setOf(parameterId),
            receiverType = declaredType(
                if (receiverNullable) K_NULLABLE_TABLE_EX_ID else K_NON_NULL_TABLE_EX_ID,
                modelType,
            ),
            returnType = declaredType(
                if (optional) K_NULLABLE_TABLE_EX_ID else K_NON_NULL_TABLE_EX_ID,
                parameterType,
            ),
            body = code {
                returnValue {
                    name(functionName)
                    text("(S::class)")
                }
            },
        )
    }

    private fun instanceOfFunction(): LsiPoetFunction = polymorphicPredicateFunction("instanceOf")

    private fun exactTypeFunction(): LsiPoetFunction = polymorphicPredicateFunction("exactType")

    private fun polymorphicPredicateFunction(name: String): LsiPoetFunction {
        return LsiPoetFunction(
            name = name,
            annotations = listOf(generatedByAnnotation(modelType)),
            receiverType = declaredType(K_TABLE_EX_ID, modelType),
            parameters = listOf(
                LsiPoetParameter(
                    "type",
                    declaredType(K_CLASS_ID, LsiTypeArgument.output(modelType)),
                )
            ),
            returnType = declaredType(K_NON_NULL_EXPRESSION_ID, BOOLEAN_TYPE),
            body = code {
                returnValue {
                    type(K_POLYMORPHIC_TABLES_TYPE)
                    text(".")
                    name(name)
                    text("(this, type)")
                }
            },
        )
    }

    private fun reifiedInstanceOfFunction(): LsiPoetFunction = reifiedPolymorphicPredicateFunction("instanceOf")

    private fun reifiedExactTypeFunction(): LsiPoetFunction = reifiedPolymorphicPredicateFunction("exactType")

    private fun reifiedPolymorphicPredicateFunction(name: String): LsiPoetFunction {
        val parameterId = LsiSymbolId.typeParameter(type.generatedQueryTypeId("reified-predicate:$name"), "S")
        val parameter = LsiTypeParameter(parameterId, "S", upperBounds = listOf(modelType))
        return LsiPoetFunction(
            name = name,
            annotations = listOf(generatedByAnnotation(modelType)),
            modifiers = setOf(LsiPoetModifier.INLINE),
            typeParameters = listOf(parameter),
            reifiedTypeParameterIds = setOf(parameterId),
            receiverType = declaredType(K_TABLE_EX_ID, modelType),
            returnType = declaredType(K_NON_NULL_EXPRESSION_ID, BOOLEAN_TYPE),
            body = code {
                returnValue {
                    name(name)
                    text("(S::class)")
                }
            },
        )
    }

    private fun propsObject(): LsiPoetType {
        return LsiPoetType(
            name = "${type.simpleName}$PROPS_SUFFIX",
            kind = LsiPoetTypeKind.OBJECT,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            annotations = listOf(generatedByAnnotation(modelType)),
            members = schema.orderedProps(type).map(::typedProp),
        )
    }

    private fun typedProp(prop: ImmutableProp): LsiPoetProperty {
        val kind = schema.typedPropKind(prop)
        return LsiPoetProperty(
            name = prop.fieldName(),
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
            type = declaredType(
                kind.typedPropTypeId,
                modelType,
                prop.elementTypeOrSelf().toQueryKotlinType(),
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

    private fun propTargetType(prop: ImmutableProp): LsiTypeRef {
        val typeRef = if (prop.list) prop.elementTypeOrSelf() else prop.type
        return typeRef.toQueryKotlinType().withRootNullability(nullable = false)
    }
}

// Java 产物在同一共享语义文件中构造，具体 Poet 仅存在于适配器边界。
private class JavaQueryPoetContext(
    private val schema: ImmutableSchema,
    private val type: ImmutableType,
    private val workspace: LsiWorkspace,
) {
    private val typeSystem = LsiTypeSystem(workspace)
    private val modelType = LsiDeclaredType(type.id)
    private val propsType = type.generatedQueryType("${type.simpleName}Props")
    private val tableType = type.generatedQueryType("${type.simpleName}Table")
    private val tableExType = type.generatedQueryType("${type.simpleName}TableEx")

    fun artifacts(): List<LsiPoetArtifact> {
        val branchDependent = schema.isBranchDependent(type)
        val dependencies = schema.queryDependencies(
            type = type,
            workspace = workspace,
            language = LsiLanguage.JAVA,
            branchDependent = branchDependent,
        )
        return buildList {
            add(dependencies.artifact(workspace, schema, javaFile(propsType, propsDeclaration()), branchDependent))
            if (type.kind == ImmutableTypeKind.ENTITY) {
                add(
                    dependencies.artifact(
                        workspace,
                        schema,
                        javaFile(tableType, tableDeclaration(tableEx = false)),
                        branchDependent,
                    )
                )
                add(
                    dependencies.artifact(
                        workspace,
                        schema,
                        javaFile(tableExType, tableDeclaration(tableEx = true)),
                        branchDependent,
                    )
                )
            }
        }
    }

    private fun javaFile(generatedType: LsiDeclaredType, declaration: LsiPoetType): LsiPoetFile {
        return LsiPoetFile(
            language = LsiLanguage.JAVA,
            packageName = type.packageName,
            fileName = generatedType.declarationId.value.substringAfterLast('.'),
            members = listOf(declaration),
        )
    }

    private fun propsDeclaration(): LsiPoetType {
        val superInterfaces = buildList {
            val superTypes = schema.propsSuperTypes(type)
            if (superTypes.isEmpty()) {
                if (type.kind in SQL_QUERY_TYPE_KINDS) add(PROPS_TYPE)
            } else {
                superTypes.forEach { superType -> add(superType.generatedQueryType("${superType.simpleName}Props")) }
            }
            if (type.kind == ImmutableTypeKind.ENTITY) add(declaredType(SELECTION_ID, modelType))
        }
        return LsiPoetType(
            name = "${type.simpleName}Props",
            kind = LsiPoetTypeKind.INTERFACE,
            annotations = buildList {
                add(generatedByAnnotation(modelType))
                if (type.kind in SQL_QUERY_TYPE_KINDS) {
                    add(
                        LsiPoetAnnotation(
                            type = PROPS_FOR_ID,
                            arguments = listOf(
                                LsiPoetAnnotationArgument.Positional(
                                    LsiPoetAnnotationValue.ClassValue(modelType)
                                )
                            ),
                        )
                    )
                }
            },
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            superInterfaces = superInterfaces,
            members = buildList {
                schema.orderedProps(type).forEach { prop -> add(javaTypedPropField(prop)) }
                if (type.kind in SQL_QUERY_TYPE_KINDS) {
                    schema.propsMethodProps(type).forEach { prop ->
                        if (schema.isDsl(prop, workspace, tableEx = false)) {
                            propertyFunction(
                                prop = prop,
                                tableEx = false,
                                withJoinType = false,
                                withImplementation = false,
                            )?.let(::add)
                            propertyFunction(
                                prop = prop,
                                tableEx = false,
                                withJoinType = true,
                                withImplementation = false,
                            )?.let(::add)
                        }
                        existsFunction(prop, withImplementation = false)?.let(::add)
                        associatedIdFunction(
                            prop = prop,
                            tableEx = false,
                            withImplementation = false,
                        )?.let(::add)
                    }
                }
            },
        )
    }

    private fun javaTypedPropField(prop: ImmutableProp): LsiPoetField {
        val kind = schema.typedPropKind(prop)
        return LsiPoetField(
            name = prop.fieldName(),
            type = declaredType(
                kind.typedPropTypeId,
                modelType,
                prop.elementTypeOrSelf().toQueryJavaType(),
            ),
            modifiers = setOf(
                LsiPoetModifier.PUBLIC,
                LsiPoetModifier.STATIC,
                LsiPoetModifier.FINAL,
            ),
            initializer = code {
                line()
                indent {
                    type(TYPED_PROP_TYPE)
                    text(".")
                    name(kind.factoryName)
                    text("(")
                    type(IMMUTABLE_TYPE_TYPE)
                    text(".get(")
                    type(modelType)
                    text(".class).getProp(")
                    string(prop.name)
                    text("))")
                }
            },
        )
    }

    private fun tableDeclaration(tableEx: Boolean): LsiPoetType {
        val selfType = if (tableEx) tableExType else tableType
        return LsiPoetType(
            name = selfType.declarationId.value.substringAfterLast('.'),
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(generatedByAnnotation(modelType)),
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            superClass = if (tableEx) tableType else declaredType(ABSTRACT_TYPED_TABLE_ID, modelType),
            superInterfaces = buildList {
                if (tableEx) {
                    add(declaredType(TABLE_EX_PROXY_ID, modelType, tableType))
                } else {
                    add(propsType)
                    if (schema.strictPrimarySubtypesOf(type).isNotEmpty()) {
                        add(declaredType(POLYMORPHIC_TABLE_ID, modelType))
                    }
                }
            },
            members = buildList {
                add(instanceField(tableEx))
                add(defaultConstructor(tableEx))
                add(delayedConstructor(tableEx))
                add(wrapperConstructor())
                add(disableJoinConstructor())
                add(baseTableOwnerConstructor())
                schema.orderedProps(type).forEach { prop ->
                    if (schema.isDsl(prop, workspace, tableEx)) {
                        propertyFunction(prop, tableEx, withJoinType = false, withImplementation = true)?.let(::add)
                        propertyFunction(prop, tableEx, withJoinType = true, withImplementation = true)?.let(::add)
                    }
                    existsFunction(prop, withImplementation = true)?.let(::add)
                    associatedIdFunction(prop, tableEx, withImplementation = true)?.let(::add)
                }
                add(asTableExFunction(tableEx))
                add(disableJoinFunction(selfType))
                add(baseTableOwnerFunction(selfType))
                if (!tableEx && schema.strictPrimarySubtypesOf(type).isNotEmpty()) {
                    add(treatAsFunction(optional = false))
                    add(treatAsFunction(optional = true))
                    add(instanceOfFunction())
                    add(exactTypeFunction())
                }
                if (tableEx) {
                    add(weakJoinFunction(withJoinType = false))
                    add(weakJoinFunction(withJoinType = true))
                    add(lambdaWeakJoinFunction(withJoinType = false))
                    add(lambdaWeakJoinFunction(withJoinType = true))
                    add(baseTableWeakJoinFunction(withJoinType = false))
                    add(baseTableWeakJoinFunction(withJoinType = true))
                } else {
                    add(remoteDeclaration())
                }
            },
        )
    }

    private fun instanceField(tableEx: Boolean): LsiPoetField {
        val selfType = if (tableEx) tableExType else tableType
        return LsiPoetField(
            name = "$",
            type = selfType,
            modifiers = setOf(
                LsiPoetModifier.PUBLIC,
                LsiPoetModifier.STATIC,
                LsiPoetModifier.FINAL,
            ),
            initializer = code {
                text("new ")
                type(selfType)
                if (tableEx) {
                    text("(")
                    type(tableType)
                    text(".$, (String)null)")
                } else {
                    text("()")
                }
            },
        )
    }

    private fun defaultConstructor(tableEx: Boolean): LsiPoetConstructor {
        return LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            delegationCall = LsiPoetDelegationCall(
                target = LsiPoetDelegationTarget.SUPER,
                arguments = if (tableEx) emptyList() else listOf(
                    code {
                        type(modelType)
                        text(".class")
                    }
                ),
            ),
        )
    }

    private fun delayedConstructor(tableEx: Boolean): LsiPoetConstructor {
        return LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            parameters = listOf(
                LsiPoetParameter("delayedOperation", declaredType(DELAYED_OPERATION_ID, modelType))
            ),
            delegationCall = LsiPoetDelegationCall(
                target = LsiPoetDelegationTarget.SUPER,
                arguments = buildList {
                    if (!tableEx) {
                        add(code {
                            type(modelType)
                            text(".class")
                        })
                    }
                    add(code { name("delayedOperation") })
                },
            ),
        )
    }

    private fun wrapperConstructor(): LsiPoetConstructor {
        return LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            parameters = listOf(
                LsiPoetParameter("table", declaredType(TABLE_IMPLEMENTOR_ID, modelType))
            ),
            delegationCall = LsiPoetDelegationCall(
                target = LsiPoetDelegationTarget.SUPER,
                arguments = listOf(code { name("table") }),
            ),
        )
    }

    private fun disableJoinConstructor(): LsiPoetConstructor {
        return LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PROTECTED),
            parameters = listOf(
                LsiPoetParameter("base", tableType),
                LsiPoetParameter("joinDisabledReason", STRING_TYPE),
            ),
            delegationCall = LsiPoetDelegationCall(
                target = LsiPoetDelegationTarget.SUPER,
                arguments = listOf(code { name("base") }, code { name("joinDisabledReason") }),
            ),
        )
    }

    private fun baseTableOwnerConstructor(): LsiPoetConstructor {
        return LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PROTECTED),
            parameters = listOf(
                LsiPoetParameter("base", tableType),
                LsiPoetParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE),
            ),
            delegationCall = LsiPoetDelegationCall(
                target = LsiPoetDelegationTarget.SUPER,
                arguments = listOf(code { name("base") }, code { name("baseTableOwner") }),
            ),
        )
    }

    private fun propertyFunction(
        prop: ImmutableProp,
        tableEx: Boolean,
        withJoinType: Boolean,
        withImplementation: Boolean,
    ): LsiPoetFunction? {
        if (withJoinType && !schema.isEntityAssociation(prop)) return null
        val returnType = propertyReturnType(prop, tableEx)
        return LsiPoetFunction(
            name = prop.name,
            documentation = prop.documentation?.let(Doc::parse)?.value,
            modifiers = buildSet {
                add(LsiPoetModifier.PUBLIC)
                if (withImplementation) {
                    if (!tableEx) add(LsiPoetModifier.OVERRIDE)
                } else {
                    add(LsiPoetModifier.ABSTRACT)
                }
            },
            parameters = if (withJoinType) listOf(LsiPoetParameter("joinType", JOIN_TYPE_TYPE)) else emptyList(),
            returnType = returnType,
            body = if (withImplementation) propertyImplementation(prop, returnType, withJoinType) else LsiPoetCodeBlock.EMPTY,
        )
    }

    private fun propertyImplementation(
        prop: ImmutableProp,
        returnType: LsiTypeRef,
        withJoinType: Boolean,
    ): LsiPoetCodeBlock {
        val runtimeOwner = schema.primaryLineageOwner(type, prop)
        val runtimePropsType = runtimeOwner.generatedQueryType("${runtimeOwner.simpleName}Props")
        return code {
            if (schema.isEntityAssociation(prop)) {
                statement { text("__beforeJoin()") }
                beginControlFlow { text("if (raw != null)") }
                returnValue {
                    text("new ")
                    type(returnType)
                    text("(raw.joinImplementor(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap()")
                    if (withJoinType) text(", joinType")
                    text("))")
                }
                endControlFlow()
                returnValue {
                    text("new ")
                    type(returnType)
                    text("(joinOperation(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap()")
                    if (withJoinType) text(", joinType")
                    text("))")
                }
            } else if (schema.targetTypeOf(prop) != null) {
                returnValue {
                    text("new ")
                    type(returnType)
                    text("(__get(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap()))")
                }
            } else {
                returnValue {
                    text("__get(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap())")
                }
            }
        }
    }

    private fun propertyReturnType(prop: ImmutableProp, tableEx: Boolean): LsiTypeRef {
        val targetType = schema.targetTypeOf(prop)
        if (schema.isEntityAssociation(prop)) {
            val target = requireNotNull(targetType) {
                "Entity association '${prop.id.value}' must have a concrete target"
            }
            return when {
                prop.remote -> target.generatedQueryType("${target.simpleName}Table.Remote")
                tableEx -> target.generatedQueryType("${target.simpleName}TableEx")
                else -> target.generatedQueryType("${target.simpleName}Table")
            }
        }
        if (targetType != null) return targetType.generatedQueryType("${targetType.simpleName}PropExpression")
        return prop.javaExpressionType(typeSystem)
    }

    private fun existsFunction(prop: ImmutableProp, withImplementation: Boolean): LsiPoetFunction? {
        if (!schema.isEntityAssociation(prop) || !prop.list) return null
        val targetType = requireNotNull(schema.targetTypeOf(prop)) {
            "List association '${prop.id.value}' must have a concrete target"
        }
        val runtimePropsType = schema.primaryLineageOwner(type, prop).let { owner ->
            owner.generatedQueryType("${owner.simpleName}Props")
        }
        return LsiPoetFunction(
            name = prop.name,
            modifiers = buildSet {
                add(LsiPoetModifier.PUBLIC)
                if (withImplementation) add(LsiPoetModifier.OVERRIDE) else add(LsiPoetModifier.ABSTRACT)
            },
            parameters = listOf(
                LsiPoetParameter(
                    "block",
                    declaredType(
                        JAVA_FUNCTION_ID,
                        targetType.generatedQueryType("${targetType.simpleName}TableEx"),
                        PREDICATE_TYPE,
                    ),
                )
            ),
            returnType = PREDICATE_TYPE,
            body = if (withImplementation) code {
                returnValue {
                    text("exists(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap(), block)")
                }
            } else LsiPoetCodeBlock.EMPTY,
        )
    }

    private fun associatedIdFunction(
        prop: ImmutableProp,
        tableEx: Boolean,
        withImplementation: Boolean,
    ): LsiPoetFunction? {
        val functionName = schema.associatedIdPropName(type, prop) ?: return null
        if (
            prop.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isEntityAssociation(prop) ||
            prop.list != tableEx
        ) return null
        val targetIdProp = requireNotNull(schema.targetIdPropOf(prop)) {
            "Association '${prop.id.value}' must target an entity with an id property"
        }
        val runtimePropsType = schema.primaryLineageOwner(type, prop).let { owner ->
            owner.generatedQueryType("${owner.simpleName}Props")
        }
        return LsiPoetFunction(
            name = functionName,
            modifiers = buildSet {
                add(LsiPoetModifier.PUBLIC)
                if (withImplementation) {
                    if (!tableEx) add(LsiPoetModifier.OVERRIDE)
                } else {
                    add(LsiPoetModifier.ABSTRACT)
                }
            },
            returnType = targetIdProp.javaExpressionType(typeSystem),
            body = if (withImplementation) code {
                returnValue {
                    text("__getAssociatedId(")
                    type(runtimePropsType)
                    text(".")
                    name(prop.fieldName())
                    text(".unwrap())")
                }
            } else LsiPoetCodeBlock.EMPTY,
        )
    }

    private fun asTableExFunction(tableEx: Boolean): LsiPoetFunction {
        return LsiPoetFunction(
            name = "asTableEx",
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
            returnType = tableExType,
            body = code {
                returnValue {
                    if (tableEx) {
                        text("this")
                    } else {
                        text("new ")
                        type(tableExType)
                        text("(this, (String)null)")
                    }
                }
            },
        )
    }

    private fun disableJoinFunction(selfType: LsiTypeRef): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__disableJoin",
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("reason", STRING_TYPE)),
            returnType = selfType,
            body = code {
                returnValue {
                    text("new ")
                    type(selfType)
                    text("(this, reason)")
                }
            },
        )
    }

    private fun baseTableOwnerFunction(selfType: LsiTypeRef): LsiPoetFunction {
        return LsiPoetFunction(
            name = "__baseTableOwner",
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
            parameters = listOf(LsiPoetParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE)),
            returnType = selfType,
            body = code {
                returnValue {
                    text("new ")
                    type(selfType)
                    text("(this, baseTableOwner)")
                }
            },
        )
    }

    private fun treatAsFunction(optional: Boolean): LsiPoetFunction {
        val parameterId = LsiSymbolId.typeParameter(type.generatedQueryTypeId("java-treat:$optional"), "TT")
        val parameter = LsiTypeParameter(
            id = parameterId,
            name = "TT",
            upperBounds = listOf(declaredType(TABLE_ID, LsiTypeArgument.STAR)),
        )
        val parameterType = LsiTypeParameterRef(parameterId)
        return LsiPoetFunction(
            name = if (optional) "tryTreatAs" else "treatAs",
            annotations = listOf(JAVA_OVERRIDE_ANNOTATION, SUPPRESS_ALL_ANNOTATION),
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            typeParameters = listOf(parameter),
            parameters = listOf(LsiPoetParameter("tableType", declaredType(JAVA_CLASS_ID, parameterType))),
            returnType = parameterType,
            body = code {
                statement {
                    type(IMMUTABLE_TYPE_TYPE)
                    text(" treatedAs = ")
                    type(TABLE_PROXIES_TYPE)
                    text(".tableType(tableType)")
                }
                statement { text("__beforeJoin()") }
                beginControlFlow { text("if (raw != null)") }
                returnValue {
                    text("(TT)")
                    type(TABLE_PROXIES_TYPE)
                    text(".wrap(raw.treatAsImplementor(treatedAs, ")
                    type(JOIN_TYPE_TYPE)
                    text(if (optional) ".LEFT))" else ".INNER))")
                }
                endControlFlow()
                returnValue {
                    text("(TT)")
                    type(TABLE_PROXIES_TYPE)
                    text(".fluent(treatAsOperation(treatedAs, ")
                    type(JOIN_TYPE_TYPE)
                    text(if (optional) ".LEFT))" else ".INNER))")
                }
            },
        )
    }

    private fun instanceOfFunction(): LsiPoetFunction = javaPolymorphicPredicateFunction("instanceOf")

    private fun exactTypeFunction(): LsiPoetFunction = javaPolymorphicPredicateFunction("exactType")

    private fun javaPolymorphicPredicateFunction(name: String): LsiPoetFunction {
        return LsiPoetFunction(
            name = name,
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
            parameters = listOf(
                LsiPoetParameter(
                    "type",
                    declaredType(JAVA_CLASS_ID, LsiTypeArgument.output(modelType)),
                )
            ),
            returnType = PREDICATE_TYPE,
            body = code {
                returnValue {
                    type(TABLE_PROXIES_TYPE)
                    text(".")
                    name(name)
                    text("(this, type)")
                }
            },
        )
    }

    private fun weakJoinFunction(withJoinType: Boolean): LsiPoetFunction {
        val ownerId = type.generatedQueryTypeId("java-weak-join:$withJoinType")
        val tableParameterId = LsiSymbolId.typeParameter(ownerId, "TT")
        val weakJoinParameterId = LsiSymbolId.typeParameter(ownerId, "WJ")
        val tableParameterType = LsiTypeParameterRef(tableParameterId)
        val weakJoinParameterType = LsiTypeParameterRef(weakJoinParameterId)
        return LsiPoetFunction(
            name = "weakJoin",
            annotations = if (withJoinType) listOf(SUPPRESS_ALL_ANNOTATION) else emptyList(),
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = tableParameterId,
                    name = "TT",
                    upperBounds = listOf(declaredType(TABLE_ID, LsiTypeArgument.STAR)),
                ),
                LsiTypeParameter(
                    id = weakJoinParameterId,
                    name = "WJ",
                    upperBounds = listOf(declaredType(WEAK_JOIN_ID, tableType, tableParameterType)),
                ),
            ),
            parameters = buildList {
                add(LsiPoetParameter("weakJoinType", declaredType(JAVA_CLASS_ID, weakJoinParameterType)))
                if (withJoinType) add(LsiPoetParameter("joinType", JOIN_TYPE_TYPE))
            },
            returnType = tableParameterType,
            body = code {
                if (withJoinType) {
                    statement { text("__beforeJoin()") }
                    beginControlFlow { text("if (raw != null)") }
                    returnValue {
                        text("(TT)")
                        type(TABLE_PROXIES_TYPE)
                        text(".wrap(raw.weakJoinImplementor(weakJoinType, joinType))")
                    }
                    endControlFlow()
                    returnValue {
                        text("(TT)")
                        type(TABLE_PROXIES_TYPE)
                        text(".fluent(joinOperation(weakJoinType, joinType))")
                    }
                } else {
                    returnValue {
                        text("weakJoin(weakJoinType, ")
                        type(JOIN_TYPE_TYPE)
                        text(".INNER)")
                    }
                }
            },
        )
    }

    private fun lambdaWeakJoinFunction(withJoinType: Boolean): LsiPoetFunction {
        val ownerId = type.generatedQueryTypeId("java-lambda-weak-join:$withJoinType")
        val tableParameterId = LsiSymbolId.typeParameter(ownerId, "TT")
        val tableParameterType = LsiTypeParameterRef(tableParameterId)
        return LsiPoetFunction(
            name = "weakJoin",
            annotations = if (withJoinType) listOf(SUPPRESS_ALL_ANNOTATION) else emptyList(),
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = tableParameterId,
                    name = "TT",
                    upperBounds = listOf(declaredType(TABLE_ID, LsiTypeArgument.STAR)),
                )
            ),
            parameters = buildList {
                add(LsiPoetParameter("targetTableType", declaredType(JAVA_CLASS_ID, tableParameterType)))
                if (withJoinType) add(LsiPoetParameter("joinType", JOIN_TYPE_TYPE))
                add(
                    LsiPoetParameter(
                        "weakJoinLambda",
                        declaredType(WEAK_JOIN_ID, tableType, tableParameterType),
                    )
                )
            },
            returnType = tableParameterType,
            body = code {
                if (withJoinType) {
                    statement { text("__beforeJoin()") }
                    beginControlFlow { text("if (raw != null)") }
                    returnValue {
                        text("(TT)")
                        type(TABLE_PROXIES_TYPE)
                        text(".wrap(raw.weakJoinImplementor(targetTableType, joinType, weakJoinLambda))")
                    }
                    endControlFlow()
                    returnValue {
                        text("(TT)")
                        type(TABLE_PROXIES_TYPE)
                        text(".fluent(joinOperation(targetTableType, joinType, weakJoinLambda))")
                    }
                } else {
                    returnValue {
                        text("weakJoin(targetTableType, ")
                        type(JOIN_TYPE_TYPE)
                        text(".INNER, weakJoinLambda)")
                    }
                }
            },
        )
    }

    private fun baseTableWeakJoinFunction(withJoinType: Boolean): LsiPoetFunction {
        val ownerId = type.generatedQueryTypeId("java-base-table-weak-join:$withJoinType")
        val tableParameterId = LsiSymbolId.typeParameter(ownerId, "TT")
        val tableParameterType = LsiTypeParameterRef(tableParameterId)
        return LsiPoetFunction(
            name = "weakJoin",
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = tableParameterId,
                    name = "TT",
                    upperBounds = listOf(BASE_TABLE_TYPE),
                )
            ),
            parameters = buildList {
                add(LsiPoetParameter("targetBaseTable", tableParameterType))
                if (withJoinType) add(LsiPoetParameter("joinType", JOIN_TYPE_TYPE))
                add(
                    LsiPoetParameter(
                        "weakJoinLambda",
                        declaredType(WEAK_JOIN_ID, tableType, tableParameterType),
                    )
                )
            },
            returnType = tableParameterType,
            body = if (withJoinType) baseTableWeakJoinBody() else code {
                returnValue {
                    text("weakJoin(targetBaseTable, ")
                    type(JOIN_TYPE_TYPE)
                    text(".INNER, weakJoinLambda)")
                }
            },
        )
    }

    private fun baseTableWeakJoinBody(): LsiPoetCodeBlock {
        val tableLikeWildcard = declaredType(TABLE_LIKE_ID, LsiTypeArgument.STAR)
        val broadWeakJoin = declaredType(WEAK_JOIN_ID, tableLikeWildcard, tableLikeWildcard)
        val starWeakJoin = declaredType(WEAK_JOIN_ID, LsiTypeArgument.STAR, LsiTypeArgument.STAR)
        return code {
            statement {
                type(WEAK_JOIN_LAMBDA_TYPE)
                text(" lambda = ")
                type(J_WEAK_JOIN_LAMBDA_FACTORY_TYPE)
                text(".get(weakJoinLambda)")
            }
            type(WEAK_JOIN_HANDLE_TYPE)
            text(" handle = ")
            type(WEAK_JOIN_HANDLE_TYPE)
            text(".of(")
            line()
            indent {
                text("lambda,")
                line()
                text("true,")
                line()
                text("true,")
                line()
                text("(")
                type(broadWeakJoin)
                text(")(")
                type(starWeakJoin)
                text(") weakJoinLambda")
                line()
            }
            text(");")
            line()
            returnValue {
                text("(TT) ")
                type(BASE_TABLE_SYMBOLS_TYPE)
                text(".of((")
                type(BASE_TABLE_SYMBOL_TYPE)
                text(") targetBaseTable, this, handle, joinType)")
            }
        }
    }

    private fun remoteDeclaration(): LsiPoetType {
        val remoteType = type.generatedQueryType("${type.simpleName}Table.Remote")
        val idProp = type.idPropId?.let(schema.propsById::get)
            ?: error("Entity immutable type '${type.id.value}' must declare an id property")
        return LsiPoetType(
            name = "Remote",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(generatedByAnnotation(modelType)),
            modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.STATIC),
            superClass = declaredType(ABSTRACT_TYPED_TABLE_ID, modelType),
            members = listOf(
                LsiPoetConstructor(
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(LsiPoetParameter("delayedOperation", DELAYED_OPERATION_TYPE)),
                    delegationCall = LsiPoetDelegationCall(
                        LsiPoetDelegationTarget.SUPER,
                        listOf(
                            code {
                                type(modelType)
                                text(".class")
                            },
                            code { name("delayedOperation") },
                        ),
                    ),
                ),
                LsiPoetConstructor(
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(
                        LsiPoetParameter("table", declaredType(TABLE_IMPLEMENTOR_ID, modelType))
                    ),
                    delegationCall = LsiPoetDelegationCall(
                        LsiPoetDelegationTarget.SUPER,
                        listOf(code { name("table") }),
                    ),
                ),
                LsiPoetConstructor(
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(
                        LsiPoetParameter("base", remoteType),
                        LsiPoetParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE),
                    ),
                    delegationCall = LsiPoetDelegationCall(
                        LsiPoetDelegationTarget.SUPER,
                        listOf(code { name("base") }, code { name("baseTableOwner") }),
                    ),
                ),
                remoteIdFunction(idProp),
                LsiPoetFunction(
                    name = "asTableEx",
                    annotations = listOf(JAVA_OVERRIDE_ANNOTATION, DEPRECATED_ANNOTATION),
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    returnType = declaredType(TABLE_EX_ID, modelType),
                    body = code { statement { text("throw new UnsupportedOperationException()") } },
                ),
                LsiPoetFunction(
                    name = "__disableJoin",
                    modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
                    parameters = listOf(LsiPoetParameter("reason", STRING_TYPE)),
                    returnType = remoteType,
                    body = code { returnValue { text("this") } },
                ),
                LsiPoetFunction(
                    name = "__baseTableOwner",
                    modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
                    parameters = listOf(LsiPoetParameter("baseTableOwner", BASE_TABLE_OWNER_TYPE)),
                    returnType = remoteType,
                    body = code { returnValue { text("new Remote(this, baseTableOwner)") } },
                ),
            ),
        )
    }

    private fun remoteIdFunction(idProp: ImmutableProp): LsiPoetFunction {
        val returnType = propertyReturnType(idProp, tableEx = false)
        val idType = idProp.type.toQueryJavaType()
        return LsiPoetFunction(
            name = idProp.name,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            returnType = returnType,
            body = code {
                returnValue {
                    text("(")
                    type(returnType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
                    text(")this.<")
                    type(idType)
                    text(">get(")
                    type(propsType)
                    text(".")
                    name(idProp.fieldName())
                    text(".unwrap())")
                }
            },
        )
    }
}

private fun ImmutableSchema.queryDependencies(
    type: ImmutableType,
    workspace: LsiWorkspace,
    language: LsiLanguage,
    branchDependent: Boolean,
): QueryArtifactDependencies {
    val originatingSymbols = if (branchDependent) {
        inheritanceArtifactOriginatingSymbols(type)
    } else {
        setOf(type.id)
    }
    val dependencySymbols = sortedSetOf<LsiSymbolId>().apply {
        addAll(
            semanticDependencySymbols(
                rootTypeIds = originatingSymbols + type.id,
                rootProps = type.props,
                workspace = workspace,
            )
        )
        addAll(
            when (language) {
                LsiLanguage.JAVA -> JAVA_QUERY_RUNTIME_DEPENDENCIES
                LsiLanguage.KOTLIN -> KOTLIN_QUERY_RUNTIME_DEPENDENCIES
                LsiLanguage.UNKNOWN -> emptySet()
            }
        )
    }
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySources = buildSet {
        workspace.originatingSources(dependencySymbols)
            .filterTo(this) { source -> source.kind != LsiSourceKind.BINARY }
        addAll(originatingSources)
    }
    return QueryArtifactDependencies(
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = dependencySymbols,
        dependencySources = dependencySources,
    )
}

private fun QueryArtifactDependencies.artifact(
    workspace: LsiWorkspace,
    schema: ImmutableSchema,
    file: LsiPoetFile,
    branchDependent: Boolean,
): LsiPoetArtifact {
    return LsiPoetArtifact(
        file = file,
        typeNames = workspace.toLsiPoetTypeNames(
            file.referencedTypeIds,
            additional = schema.toLsiGeneratedQueryPoetTypeNames() + QUERY_RUNTIME_TYPE_NAMES,
        ),
        aggregationMode = if (branchDependent) {
            ArtifactAggregationMode.AGGREGATING
        } else {
            classifyArtifactAggregationMode(
                originatingSymbols = originatingSymbols,
                originatingSources = originatingSources,
                dependencySources = dependencySources,
            )
        },
        emissionMode = if (branchDependent) {
            ArtifactEmissionMode.STABLE
        } else {
            ArtifactEmissionMode.IMMEDIATE
        },
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = dependencySymbols,
        dependencySources = dependencySources,
    )
}

private data class QueryArtifactDependencies(
    val originatingSymbols: Set<LsiSymbolId>,
    val originatingSources: Set<LsiSource>,
    val dependencySymbols: Set<LsiSymbolId>,
    val dependencySources: Set<LsiSource>,
)

private fun generatedByAnnotation(
    type: LsiTypeRef,
    fileTarget: Boolean = false,
): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = GENERATED_BY_ID,
        arguments = listOf(
            LsiPoetAnnotationArgument.Named(
                name = "type",
                value = LsiPoetAnnotationValue.ClassValue(type),
            )
        ),
        useSiteTarget = if (fileTarget) LsiAnnotationUseSiteTarget.FILE else null,
    )
}

private fun declaredType(
    id: LsiSymbolId,
    vararg arguments: LsiTypeRef,
): LsiDeclaredType = LsiDeclaredType(
    declarationId = id,
    arguments = arguments.map(LsiTypeArgument::invariant),
)

private fun declaredType(
    id: LsiSymbolId,
    vararg arguments: LsiTypeArgument,
): LsiDeclaredType = LsiDeclaredType(
    declarationId = id,
    arguments = arguments.toList(),
)

private fun LsiTypeRef.toQueryKotlinType(): LsiTypeRef {
    return when (this) {
        is LsiArrayType -> copy(
            elementType = elementType.toQueryKotlinType(),
            annotations = emptyList(),
        )
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toQueryKotlinType())
            },
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toQueryKotlinType(),
            receiverType = receiverType?.toQueryKotlinType(),
            parameterTypes = parameterTypes.map(LsiTypeRef::toQueryKotlinType),
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(annotations = emptyList())
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

private fun LsiTypeRef.toQueryJavaType(): LsiTypeRef {
    return when (this) {
        is LsiArrayType -> copy(
            // Java 数组分量保留原始类型，只有泛型实参需要装箱。
            elementType = elementType.toQueryJavaArrayElementType(),
            annotations = emptyList(),
        )
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toQueryJavaType())
            },
            annotations = emptyList(),
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toQueryJavaType(),
            receiverType = receiverType?.toQueryJavaType(),
            parameterTypes = parameterTypes.map(LsiTypeRef::toQueryJavaType),
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(boxed = true, annotations = emptyList())
        is LsiTypeParameterRef -> copy(annotations = emptyList())
        is LsiUnresolvedType -> copy(annotations = emptyList())
    }
}

private fun LsiTypeRef.toQueryJavaArrayElementType(): LsiTypeRef {
    return when (this) {
        is LsiArrayType -> copy(
            elementType = elementType.toQueryJavaArrayElementType(),
            annotations = emptyList(),
        )
        is LsiPrimitiveType -> copy(annotations = emptyList())
        else -> toQueryJavaType()
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

private fun code(block: LsiPoetCodeBuilder.() -> Unit): LsiPoetCodeBlock =
    LsiPoetCodeBlock.build(block)

private fun ImmutableType.generatedQueryType(simpleName: String): LsiDeclaredType =
    LsiDeclaredType(generatedQueryTypeId(simpleName))

private fun ImmutableType.generatedQueryTypeId(simpleName: String): LsiSymbolId {
    val qualifiedName = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    return LsiSymbolId.type(qualifiedName)
}

private val JimmerImmutableTypedPropKind.typedPropTypeId: LsiSymbolId
    get() = when (this) {
        JimmerImmutableTypedPropKind.SCALAR -> TYPED_PROP_SCALAR_ID
        JimmerImmutableTypedPropKind.SCALAR_LIST -> TYPED_PROP_SCALAR_LIST_ID
        JimmerImmutableTypedPropKind.REFERENCE -> TYPED_PROP_REFERENCE_ID
        JimmerImmutableTypedPropKind.REFERENCE_LIST -> TYPED_PROP_REFERENCE_LIST_ID
    }

private val JimmerImmutableTypedPropKind.factoryName: String
    get() = when (this) {
        JimmerImmutableTypedPropKind.SCALAR -> "scalar"
        JimmerImmutableTypedPropKind.SCALAR_LIST -> "scalarList"
        JimmerImmutableTypedPropKind.REFERENCE -> "reference"
        JimmerImmutableTypedPropKind.REFERENCE_LIST -> "referenceList"
    }

private const val PROPS_SUFFIX = "Props"
private const val FETCHER_DSL_SUFFIX = "FetcherDsl"
private const val NEW_FETCHER_PACKAGE = "org.babyfish.jimmer.sql.kt.fetcher"
private const val TO_IMMUTABLE_PROP_PACKAGE = "org.babyfish.jimmer.kt"

private val JAVA_QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.IMMUTABLE,
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)
private val KOTLIN_QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)
private val SQL_QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val GENERATED_BY_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val TYPED_PROP_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp")
private val TYPED_PROP_SCALAR_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.Scalar")
private val TYPED_PROP_SCALAR_LIST_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.ScalarList")
private val TYPED_PROP_REFERENCE_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.Reference")
private val TYPED_PROP_REFERENCE_LIST_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.TypedProp.ReferenceList")
private val TYPED_PROP_TYPE = LsiDeclaredType(TYPED_PROP_ID)
private val SELECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Selection")
private val K_PROPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KProps")
private val K_NON_NULL_PROPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNonNullProps")
private val K_NULLABLE_PROPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNullableProps")
private val K_NON_NULL_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable")
private val K_NULLABLE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNullableTable")
private val K_NON_NULL_REMOTE_REF_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KRemoteRef.NonNull")
private val K_NULLABLE_REMOTE_REF_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KRemoteRef.Nullable")
private val K_REMOTE_REF_IMPLEMENTOR_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.table.impl.KRemoteRefImplementor"
)
private val K_NON_NULL_TABLE_EX_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNonNullTableEx")
private val K_NULLABLE_TABLE_EX_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNullableTableEx")
private val K_IMPLICIT_SUB_QUERY_TABLE_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.table.KImplicitSubQueryTable"
)
private val K_NON_NULL_EXPRESSION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression"
)
private val K_TABLE_EX_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KTableEx")
private val K_POLYMORPHIC_TABLES_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.ast.table.impl.KPolymorphicTables"
)
private val K_POLYMORPHIC_TABLES_TYPE = LsiDeclaredType(K_POLYMORPHIC_TABLES_ID)
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
private val K_CLASS_ID = LsiSymbolId.type("kotlin.reflect.KClass")
private val KOTLIN_ANY_ID = LsiSymbolId.type("kotlin.Any")
private val KOTLIN_ANY_TYPE = LsiDeclaredType(KOTLIN_ANY_ID)
private val KOTLIN_LIST_ID = LsiSymbolId.type("kotlin.collections.List")
private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
private val UNIT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.UNIT)

private val KOTLIN_FILE_WARNING_SUPPRESSION = LsiPoetAnnotation(
    type = LsiSymbolId.type("kotlin.Suppress"),
    arguments = listOf(
        LsiPoetAnnotationArgument.Positional(
            LsiPoetAnnotationValue.StringValue("warnings")
        )
    ),
    useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
)

private val KOTLIN_QUERY_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    TYPED_PROP_ID,
    TYPED_PROP_SCALAR_ID,
    TYPED_PROP_SCALAR_LIST_ID,
    TYPED_PROP_REFERENCE_ID,
    TYPED_PROP_REFERENCE_LIST_ID,
    SELECTION_ID,
    K_PROPS_ID,
    K_NON_NULL_PROPS_ID,
    K_NULLABLE_PROPS_ID,
    K_NON_NULL_TABLE_ID,
    K_NULLABLE_TABLE_ID,
    K_NON_NULL_REMOTE_REF_ID,
    K_NULLABLE_REMOTE_REF_ID,
    K_REMOTE_REF_IMPLEMENTOR_ID,
    K_NON_NULL_TABLE_EX_ID,
    K_NULLABLE_TABLE_EX_ID,
    K_IMPLICIT_SUB_QUERY_TABLE_ID,
    K_NON_NULL_EXPRESSION_ID,
    K_TABLE_EX_ID,
    K_POLYMORPHIC_TABLES_ID,
    K_NON_NULL_PROP_EXPRESSION_ID,
    K_NULLABLE_PROP_EXPRESSION_ID,
    K_NON_NULL_EMBEDDED_PROP_EXPRESSION_ID,
    K_NULLABLE_EMBEDDED_PROP_EXPRESSION_ID,
    K_CLASS_ID,
    KOTLIN_ANY_ID,
    KOTLIN_LIST_ID,
    LsiSymbolId.type("kotlin.Suppress"),
)

private val PROPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Props")
private val PROPS_TYPE = LsiDeclaredType(PROPS_ID)
private val PROPS_FOR_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.PropsFor")
private val IMMUTABLE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableType")
private val IMMUTABLE_TYPE_TYPE = LsiDeclaredType(IMMUTABLE_TYPE_ID)
private val JOIN_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinType")
private val JOIN_TYPE_TYPE = LsiDeclaredType(JOIN_TYPE_ID)
private val PREDICATE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Predicate")
private val PREDICATE_TYPE = LsiDeclaredType(PREDICATE_ID)
private val JAVA_FUNCTION_ID = LsiSymbolId.type("java.util.function.Function")
private val PROP_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression")
private val PROP_NUMERIC_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Num")
private val PROP_STRING_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Str")
private val PROP_DATE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Dt")
private val PROP_TEMPORAL_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Tp")
private val PROP_COMPARABLE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.PropExpression.Cmp")
private val TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Table")
private val TABLE_EX_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.TableEx")
private val POLYMORPHIC_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.PolymorphicTable")
private val ABSTRACT_TYPED_TABLE_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable"
)
private val DELAYED_OPERATION_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable.DelayedOperation"
)
private val DELAYED_OPERATION_TYPE = LsiDeclaredType(DELAYED_OPERATION_ID)
private val TABLE_IMPLEMENTOR_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.impl.table.TableImplementor"
)
private val TABLE_EX_PROXY_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.table.spi.TableExProxy"
)
private val TABLE_PROXIES_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.table.TableProxies")
private val TABLE_PROXIES_TYPE = LsiDeclaredType(TABLE_PROXIES_ID)
private val BASE_TABLE_OWNER_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner")
private val BASE_TABLE_OWNER_TYPE = LsiDeclaredType(BASE_TABLE_OWNER_ID)
private val BASE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.BaseTable")
private val BASE_TABLE_TYPE = LsiDeclaredType(BASE_TABLE_ID)
private val BASE_TABLE_SYMBOL_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbol")
private val BASE_TABLE_SYMBOL_TYPE = LsiDeclaredType(BASE_TABLE_SYMBOL_ID)
private val BASE_TABLE_SYMBOLS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbols")
private val BASE_TABLE_SYMBOLS_TYPE = LsiDeclaredType(BASE_TABLE_SYMBOLS_ID)
private val WEAK_JOIN_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.WeakJoin")
private val WEAK_JOIN_HANDLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.table.WeakJoinHandle")
private val WEAK_JOIN_HANDLE_TYPE = LsiDeclaredType(WEAK_JOIN_HANDLE_ID)
private val WEAK_JOIN_LAMBDA_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.impl.table.WeakJoinLambda")
private val WEAK_JOIN_LAMBDA_TYPE = LsiDeclaredType(WEAK_JOIN_LAMBDA_ID)
private val J_WEAK_JOIN_LAMBDA_FACTORY_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.ast.impl.table.JWeakJoinLambdaFactory"
)
private val J_WEAK_JOIN_LAMBDA_FACTORY_TYPE = LsiDeclaredType(J_WEAK_JOIN_LAMBDA_FACTORY_ID)
private val TABLE_LIKE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.spi.TableLike")
private val JAVA_CLASS_ID = LsiSymbolId.type("java.lang.Class")
private val STRING_ID = LsiSymbolId.type("java.lang.String")
private val STRING_TYPE = LsiDeclaredType(STRING_ID)
private val SUPPRESS_WARNINGS_ID = LsiSymbolId.type("java.lang.SuppressWarnings")
private val DEPRECATED_ID = LsiSymbolId.type("java.lang.Deprecated")
private val JAVA_OVERRIDE_ID = LsiSymbolId.type("java.lang.Override")
private val UNSUPPORTED_OPERATION_EXCEPTION_ID = LsiSymbolId.type("java.lang.UnsupportedOperationException")

private val QUERY_RUNTIME_TYPE_NAMES = listOf(
    "org.babyfish.jimmer.internal.GeneratedBy",
    "org.babyfish.jimmer.meta.TypedProp",
    "org.babyfish.jimmer.sql.ast.Selection",
    "org.babyfish.jimmer.sql.kt.ast.table.KProps",
    "org.babyfish.jimmer.sql.kt.ast.table.KNonNullProps",
    "org.babyfish.jimmer.sql.kt.ast.table.KNullableProps",
    "org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable",
    "org.babyfish.jimmer.sql.kt.ast.table.KNullableTable",
    "org.babyfish.jimmer.sql.kt.ast.table.KNonNullTableEx",
    "org.babyfish.jimmer.sql.kt.ast.table.KNullableTableEx",
    "org.babyfish.jimmer.sql.kt.ast.table.KTableEx",
    "org.babyfish.jimmer.sql.kt.ast.table.KImplicitSubQueryTable",
    "org.babyfish.jimmer.sql.kt.ast.table.impl.KRemoteRefImplementor",
    "org.babyfish.jimmer.sql.kt.ast.table.impl.KPolymorphicTables",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullablePropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNonNullEmbeddedPropExpression",
    "org.babyfish.jimmer.sql.kt.ast.expression.KNullableEmbeddedPropExpression",
    "kotlin.reflect.KClass",
    "kotlin.Any",
    "kotlin.collections.List",
    "kotlin.Suppress",
    "org.babyfish.jimmer.sql.ast.table.Props",
    "org.babyfish.jimmer.sql.ast.table.PropsFor",
    "org.babyfish.jimmer.meta.ImmutableType",
    "org.babyfish.jimmer.sql.JoinType",
    "org.babyfish.jimmer.sql.ast.Predicate",
    "org.babyfish.jimmer.sql.ast.PropExpression",
    "java.util.function.Function",
    "org.babyfish.jimmer.sql.ast.table.Table",
    "org.babyfish.jimmer.sql.ast.table.TableEx",
    "org.babyfish.jimmer.sql.ast.table.PolymorphicTable",
    "org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable",
    "org.babyfish.jimmer.sql.ast.table.spi.TableExProxy",
    "org.babyfish.jimmer.sql.ast.impl.table.TableProxies",
    "org.babyfish.jimmer.sql.ast.impl.table.TableImplementor",
    "org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner",
    "org.babyfish.jimmer.sql.ast.table.BaseTable",
    "org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbol",
    "org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbols",
    "org.babyfish.jimmer.sql.ast.table.WeakJoin",
    "org.babyfish.jimmer.sql.ast.impl.table.WeakJoinHandle",
    "org.babyfish.jimmer.sql.ast.impl.table.WeakJoinLambda",
    "org.babyfish.jimmer.sql.ast.impl.table.JWeakJoinLambdaFactory",
    "org.babyfish.jimmer.sql.ast.table.spi.TableLike",
    "java.lang.Class",
    "java.lang.String",
    "java.lang.SuppressWarnings",
    "java.lang.Deprecated",
    "java.lang.Override",
    "java.lang.UnsupportedOperationException",
).map(LsiSymbolId::type).map(LsiSymbolId::topLevelPoetTypeName) + listOf(
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "Scalar")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "ScalarList")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "Reference")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.meta", listOf("TypedProp", "ReferenceList")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.kt.ast.table", listOf("KRemoteRef", "NonNull")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.kt.ast.table", listOf("KRemoteRef", "Nullable")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Num")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Str")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Dt")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Tp")),
    generatedNestedPoetTypeName("org.babyfish.jimmer.sql.ast", listOf("PropExpression", "Cmp")),
    generatedNestedPoetTypeName(
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("AbstractTypedTable", "DelayedOperation"),
    ),
)

private val SUPPRESS_ALL_ANNOTATION = LsiPoetAnnotation(
    type = SUPPRESS_WARNINGS_ID,
    arguments = listOf(
        LsiPoetAnnotationArgument.Positional(
            LsiPoetAnnotationValue.StringValue("all")
        )
    ),
)
private val DEPRECATED_ANNOTATION = LsiPoetAnnotation(DEPRECATED_ID)
private val JAVA_OVERRIDE_ANNOTATION = LsiPoetAnnotation(JAVA_OVERRIDE_ID)

private val JAVA_QUERY_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    TYPED_PROP_ID,
    TYPED_PROP_SCALAR_ID,
    TYPED_PROP_SCALAR_LIST_ID,
    TYPED_PROP_REFERENCE_ID,
    TYPED_PROP_REFERENCE_LIST_ID,
    PROPS_ID,
    PROPS_FOR_ID,
    IMMUTABLE_TYPE_ID,
    SELECTION_ID,
    JOIN_TYPE_ID,
    PREDICATE_ID,
    JAVA_FUNCTION_ID,
    PROP_EXPRESSION_ID,
    PROP_NUMERIC_EXPRESSION_ID,
    PROP_STRING_EXPRESSION_ID,
    PROP_DATE_EXPRESSION_ID,
    PROP_TEMPORAL_EXPRESSION_ID,
    PROP_COMPARABLE_EXPRESSION_ID,
    TABLE_ID,
    TABLE_EX_ID,
    POLYMORPHIC_TABLE_ID,
    ABSTRACT_TYPED_TABLE_ID,
    DELAYED_OPERATION_ID,
    TABLE_IMPLEMENTOR_ID,
    TABLE_EX_PROXY_ID,
    TABLE_PROXIES_ID,
    BASE_TABLE_OWNER_ID,
    BASE_TABLE_ID,
    BASE_TABLE_SYMBOL_ID,
    BASE_TABLE_SYMBOLS_ID,
    WEAK_JOIN_ID,
    WEAK_JOIN_HANDLE_ID,
    WEAK_JOIN_LAMBDA_ID,
    J_WEAK_JOIN_LAMBDA_FACTORY_ID,
    TABLE_LIKE_ID,
    JAVA_CLASS_ID,
    STRING_ID,
    SUPPRESS_WARNINGS_ID,
    DEPRECATED_ID,
    JAVA_OVERRIDE_ID,
    UNSUPPORTED_OPERATION_EXCEPTION_ID,
)

private fun ImmutableProp.javaExpressionType(typeSystem: LsiTypeSystem): LsiTypeRef {
    val boxedType = type.toQueryJavaType()
    return when (expressionKind(typeSystem)) {
        JimmerImmutablePropExpressionKind.GENERIC -> declaredType(PROP_EXPRESSION_ID, boxedType)
        JimmerImmutablePropExpressionKind.NUMERIC -> declaredType(PROP_NUMERIC_EXPRESSION_ID, boxedType)
        JimmerImmutablePropExpressionKind.STRING -> LsiDeclaredType(PROP_STRING_EXPRESSION_ID)
        JimmerImmutablePropExpressionKind.DATE -> declaredType(PROP_DATE_EXPRESSION_ID, boxedType)
        JimmerImmutablePropExpressionKind.TEMPORAL -> declaredType(PROP_TEMPORAL_EXPRESSION_ID, boxedType)
        JimmerImmutablePropExpressionKind.COMPARABLE -> declaredType(PROP_COMPARABLE_EXPRESSION_ID, boxedType)
    }
}
