package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.model.sourceLsiAnnotation

import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.TransientResolver
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.hasAnnotation
import site.addzero.lsi.jimmer.idViewBasePropOrSelf
import site.addzero.lsi.jimmer.isConcreteEntityAssociation
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
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
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationCall
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFileNameStyle
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.clazz.generatedTopLevelClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

internal fun ImmutableSchema.toFetcherPoetArtifacts(
    types: List<ImmutableType>,
    language: LsiLanguage,
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
        "Immutable fetcher Poet generation requires Java or Kotlin"
    }
    return types.map { type ->
        require(type.kind == ImmutableTypeKind.ENTITY || type.kind == ImmutableTypeKind.EMBEDDABLE) {
            "Immutable fetcher Poet generation only supports entity and embeddable types: ${type.id.value}"
        }
        val dependencies = fetcherDependencies(type, workspace, language)
        FetcherPoetContext(this, type, workspace).artifact(language, dependencies)
    }
}

private class FetcherPoetContext(
    private val schema: ImmutableSchema,
    private val type: ImmutableType,
    private val workspace: LsiWorkspace,
) {
    private val modelType = LsiDeclaredType(type.id)
    private val fetcherType = declaredType(FETCHER_ID, modelType)
    private val fetcherClassType = LsiDeclaredType(type.generatedTypeId("${type.simpleName}$FETCHER_SUFFIX"))
    private val fetcherDslType = LsiDeclaredType(type.generatedTypeId("${type.simpleName}$FETCHER_DSL_SUFFIX"))
    private val emptyFetcherName = "empty${type.simpleName}$FETCHER_SUFFIX"
    private val strictTypeBranches = schema.strictPrimarySubtypesOf(type)

    fun artifact(
        language: LsiLanguage,
        dependencies: FetcherArtifactDependencies,
    ): LsiSourceArtifact {
        val file = when (language) {
            LsiLanguage.JAVA -> javaFile()
            LsiLanguage.KOTLIN -> kotlinFile()
            LsiLanguage.UNKNOWN -> error("Unsupported immutable fetcher Poet language")
        }
        val branchDependent = schema.isBranchDependent(type)
        val aggregationMode = if (branchDependent) {
            ArtifactAggregationMode.AGGREGATING
        } else {
            classifyArtifactAggregationMode(
                originatingSymbols = dependencies.originatingSymbols,
                originatingSources = dependencies.originatingSources,
                dependencySources = dependencies.dependencySources,
            )
        }
        return LsiSourceArtifact(
            file = file,
            typeNames = workspace.toLsiClasses(
                file.referencedTypeIds,
                additional = schema.generatedFetcherPoetTypeNames() + FETCHER_RUNTIME_TYPE_IDS.map(
                    LsiSymbolId::topLevelPoetTypeName
                ),
            ),
            aggregationMode = aggregationMode,
            emissionMode = if (branchDependent) {
                ArtifactEmissionMode.STABLE
            } else {
                ArtifactEmissionMode.IMMEDIATE
            },
            originatingSymbols = dependencies.originatingSymbols,
            originatingSources = dependencies.originatingSources,
            dependencySymbols = dependencies.dependencySymbols,
            dependencySources = dependencies.dependencySources,
        )
    }

    private fun ImmutableSchema.generatedFetcherPoetTypeNames(): List<LsiClass> {
        return types.flatMap { immutableType ->
            listOf(
                generatedTopLevelClass(
                    immutableType.packageName,
                    "${immutableType.simpleName}$FETCHER_SUFFIX",
                ),
                generatedTopLevelClass(
                    immutableType.packageName,
                    "${immutableType.simpleName}$FETCHER_DSL_SUFFIX",
                ),
                generatedTopLevelClass(
                    immutableType.packageName,
                    "${immutableType.simpleName}Table",
                ),
            )
        }.distinctBy { typeName -> typeName.id }
    }

    private fun javaFile(): LsiFile {
        return LsiFile(
            language = LsiLanguage.JAVA,
            packageName = type.packageName,
            fileName = "${type.simpleName}$FETCHER_SUFFIX",
            members = listOf(javaFetcherType()),
        )
    }

    private fun javaFetcherType(): LsiClass {
        return LsiClass(
            name = "${type.simpleName}$FETCHER_SUFFIX",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(generatedByAnnotation(modelType)),
            modifiers = setOf(LsiModifier.PUBLIC),
            superClass = declaredType(ABSTRACT_TYPED_FETCHER_ID, modelType, fetcherClassType),
            members = buildList {
                add(javaRootField())
                add(javaFromFunction())
                add(javaBaseConstructor())
                javaForTypeFunction()?.let(::add)
                type.props.forEach { prop -> addAll(javaPropFunctions(prop)) }
                add(javaNegativeConstructor())
                add(javaFieldConfigConstructor())
                add(javaTypeBranchConstructor())
                add(javaNegativeCreator())
                add(javaFieldConfigCreator())
                add(javaTypeBranchCreator())
            },
        )
    }

    private fun javaRootField(): LsiField {
        return LsiField(
            name = "$",
            type = fetcherClassType,
            modifiers = setOf(
                LsiModifier.PUBLIC,
                LsiModifier.STATIC,
                LsiModifier.FINAL,
            ),
            initializer = code {
                text("new ")
                type(fetcherClassType)
                text("(null)")
            },
        )
    }

    private fun javaFromFunction(): LsiFunction {
        return LsiFunction(
            name = "\$from",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
            parameters = listOf(LsiParameter("base", fetcherType)),
            returnType = fetcherClassType,
            body = code {
                text("return base instanceof ")
                type(fetcherClassType)
                text(" ? \n\t(")
                type(fetcherClassType)
                text(")base : \n\tnew ")
                type(fetcherClassType)
                text("((")
                type(declaredType(FETCHER_IMPL_ID, modelType))
                text(")base);\n")
            },
        )
    }

    private fun javaBaseConstructor(): LsiConstructor {
        return LsiConstructor(
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(
                LsiParameter("base", declaredType(FETCHER_IMPL_ID, modelType))
            ),
            delegationCall = LsiDelegationCall(
                target = LsiDelegationTarget.SUPER,
                arguments = listOf(
                    code {
                        type(modelType)
                        text(".class")
                    },
                    code { name("base") },
                ),
            ),
        )
    }

    private fun javaForTypeFunction(): LsiFunction? {
        if (strictTypeBranches.isEmpty()) {
            return null
        }
        val typeParameterId = LsiSymbolId.typeParameter(fetcherClassType.declarationId, "ST")
        val typeParameter = LsiTypeParameter(
            id = typeParameterId,
            name = "ST",
            upperBounds = listOf(modelType),
        )
        val typeParameterRef = LsiTypeParameterRef(typeParameterId)
        return LsiFunction(
            name = "forType",
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            typeParameters = listOf(typeParameter),
            parameters = listOf(
                LsiParameter("typeBranchFetcher", declaredType(FETCHER_ID, typeParameterRef))
            ),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("(")
                    type(fetcherClassType)
                    text(")__forType(typeBranchFetcher)")
                }
            },
        )
    }

    private fun javaPropFunctions(prop: ImmutableProp): List<LsiFunction> {
        if (prop.primaryMapping == PrimaryMapping.ID || !prop.fetchable) {
            return emptyList()
        }
        return buildList {
            add(javaSimpleProp(prop))
            add(javaEnabledProp(prop))
            val targetType = schema.targetTypeOf(prop)
            if (schema.isConcreteEntityAssociation(prop)) {
                add(javaChildProp(prop, targetType))
                javaIdOnlyProp(prop)?.let(::add)
                if (!prop.remote) {
                    add(javaFieldConfigProp(prop, targetType))
                    if (!prop.list) {
                        add(javaReferenceFetchTypeProp(prop, targetType))
                    }
                    javaRecursiveProp(prop, targetType, withConfig = false)?.let(::add)
                    javaRecursiveProp(prop, targetType, withConfig = true)?.let(::add)
                }
            } else if (targetType?.kind == ImmutableTypeKind.EMBEDDABLE) {
                add(javaChildProp(prop, targetType))
            }
        }
    }

    private fun javaSimpleProp(prop: ImmutableProp): LsiFunction {
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            documentation = prop.fetcherDocumentation(),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("add(")
                    string(prop.name)
                    text(")")
                }
            },
        )
    }

    private fun javaEnabledProp(prop: ImmutableProp): LsiFunction {
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            documentation = prop.fetcherDocumentation(),
            parameters = listOf(LsiParameter("enabled", BOOLEAN_TYPE)),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("enabled ? add(")
                    string(prop.name)
                    text(") : remove(")
                    string(prop.name)
                    text(")")
                }
            },
        )
    }

    private fun javaChildProp(
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ): LsiFunction {
        val targetModelType = targetType.requiredModelType(prop)
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameter("childFetcher", declaredType(FETCHER_ID, targetModelType))
            ),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("add(")
                    string(prop.name)
                    text(", childFetcher)")
                }
            },
        )
    }

    private fun javaIdOnlyProp(prop: ImmutableProp): LsiFunction? {
        val associationProp = schema.idViewBasePropOrSelf(prop)
        if (
            associationProp.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isConcreteEntityAssociation(associationProp) ||
            prop.reverse ||
            prop.list ||
            prop.hasAnnotation(JOIN_TABLE_ANNOTATION_ID)
        ) {
            return null
        }
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(LsiParameter("idOnlyFetchType", ID_ONLY_FETCH_TYPE)),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("add(")
                    string(prop.name)
                    text(", idOnlyFetchType)")
                }
            },
        )
    }

    private fun javaFieldConfigProp(
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ): LsiFunction {
        val targetModelType = targetType.requiredModelType(prop)
        val fieldConfigId = if (prop.list) LIST_FIELD_CONFIG_ID else REFERENCE_FIELD_CONFIG_ID
        val fieldConfigType = declaredType(
            fieldConfigId,
            targetModelType,
            targetType.requiredTableType(prop),
        )
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameter("childFetcher", declaredType(FETCHER_ID, targetModelType)),
                LsiParameter("fieldConfig", declaredType(CONSUMER_ID, fieldConfigType)),
            ),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("add(")
                    string(prop.name)
                    text(", childFetcher, fieldConfig)")
                }
            },
        )
    }

    private fun javaReferenceFetchTypeProp(
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ): LsiFunction {
        return LsiFunction(
            name = prop.name,
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(
                LsiParameter("fetchType", REFERENCE_FETCH_TYPE),
                LsiParameter(
                    "childFetcher",
                    declaredType(FETCHER_ID, targetType.requiredModelType(prop)),
                ),
            ),
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    name(prop.name)
                    text("(childFetcher, cfg -> cfg.fetchType(fetchType))")
                }
            },
        )
    }

    private fun javaRecursiveProp(
        prop: ImmutableProp,
        targetType: ImmutableType?,
        withConfig: Boolean,
    ): LsiFunction? {
        if (!prop.recursive) {
            return null
        }
        val parameters = if (withConfig) {
            val configId = if (prop.list) {
                RECURSIVE_LIST_FIELD_CONFIG_ID
            } else {
                RECURSIVE_REFERENCE_FIELD_CONFIG_ID
            }
            val configType = declaredType(
                configId,
                targetType.requiredModelType(prop),
                targetType.requiredTableType(prop),
            )
            listOf(LsiParameter("fieldConfig", declaredType(CONSUMER_ID, configType)))
        } else {
            emptyList()
        }
        return LsiFunction(
            name = StringUtil.identifier("recursive", prop.name),
            annotations = listOf(NEW_CHAIN_ANNOTATION),
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = parameters,
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("addRecursion(")
                    string(prop.name)
                    text(", ")
                    text(if (withConfig) "fieldConfig" else "null")
                    text(")")
                }
            },
        )
    }

    private fun javaNegativeConstructor(): LsiConstructor {
        return javaPrivateConstructor(
            parameters = listOf(
                LsiParameter("prev", fetcherClassType),
                LsiParameter("prop", IMMUTABLE_PROP_TYPE),
                LsiParameter("negative", BOOLEAN_TYPE),
                LsiParameter("idOnlyFetchType", ID_ONLY_FETCH_TYPE),
            )
        )
    }

    private fun javaFieldConfigConstructor(): LsiConstructor {
        return javaPrivateConstructor(
            parameters = listOf(
                LsiParameter("prev", fetcherClassType),
                LsiParameter("prop", IMMUTABLE_PROP_TYPE),
                LsiParameter("fieldConfig", fieldConfigWildcardType()),
            )
        )
    }

    private fun javaTypeBranchConstructor(): LsiConstructor {
        return javaPrivateConstructor(
            parameters = listOf(
                LsiParameter("prev", fetcherClassType),
                LsiParameter("typeBranchFetcher", declaredType(FETCHER_IMPL_ID, LsiTypeArgument.STAR)),
            )
        )
    }

    private fun javaPrivateConstructor(
        parameters: List<LsiParameter>,
    ): LsiConstructor {
        return LsiConstructor(
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = parameters,
            delegationCall = LsiDelegationCall(
                target = LsiDelegationTarget.SUPER,
                arguments = parameters.map { parameter -> code { name(parameter.name) } },
            ),
        )
    }

    private fun javaNegativeCreator(): LsiFunction {
        return javaCreator(
            parameters = listOf(
                LsiParameter("prop", IMMUTABLE_PROP_TYPE),
                LsiParameter("negative", BOOLEAN_TYPE),
                LsiParameter("idOnlyFetchType", ID_ONLY_FETCH_TYPE),
            )
        )
    }

    private fun javaFieldConfigCreator(): LsiFunction {
        return javaCreator(
            parameters = listOf(
                LsiParameter("prop", IMMUTABLE_PROP_TYPE),
                LsiParameter("fieldConfig", fieldConfigWildcardType()),
            )
        )
    }

    private fun javaTypeBranchCreator(): LsiFunction {
        return javaCreator(
            parameters = listOf(
                LsiParameter("typeBranchFetcher", declaredType(FETCHER_IMPL_ID, LsiTypeArgument.STAR))
            )
        )
    }

    private fun javaCreator(parameters: List<LsiParameter>): LsiFunction {
        return LsiFunction(
            name = "createFetcher",
            modifiers = setOf(LsiModifier.PROTECTED, LsiModifier.OVERRIDE),
            parameters = parameters,
            returnType = fetcherClassType,
            body = code {
                returnValue {
                    text("new ")
                    type(fetcherClassType)
                    text("(this")
                    parameters.forEach { parameter ->
                        text(", ")
                        name(parameter.name)
                    }
                    text(")")
                }
            },
        )
    }

    private fun fieldConfigWildcardType(): LsiDeclaredType {
        val tableWildcard = LsiDeclaredType(
            declarationId = TABLE_ID,
            arguments = listOf(LsiTypeArgument.STAR),
        )
        return LsiDeclaredType(
            declarationId = FIELD_CONFIG_ID,
            arguments = listOf(
                LsiTypeArgument.STAR,
                LsiTypeArgument.output(tableWildcard),
            ),
        )
    }

    private fun kotlinFile(): LsiFile {
        val sourceBaseName = workspace.immutableSourceBaseName(type)
        return LsiFile(
            language = LsiLanguage.KOTLIN,
            packageName = type.packageName,
            fileName = "$sourceBaseName$FETCHER_SUFFIX",
            fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
            annotations = listOf(
                FILE_WARNING_SUPPRESSION,
                generatedByAnnotation(modelType, fileTarget = true),
            ),
            imports = kotlinCrossPackageByImports(),
            members = listOf(
                kotlinByFunction(withBase = false),
                kotlinByFunction(withBase = true),
                kotlinFetcherDslType(),
                kotlinEmptyFetcherProperty(),
            ),
        )
    }

    private fun kotlinCrossPackageByImports(): List<LsiImport> {
        return type.props.asSequence()
            .filter(schema::isConcreteEntityAssociation)
            .mapNotNull(schema::targetTypeOf)
            .filter { targetType ->
                targetType.packageName.isNotEmpty() && targetType.packageName != type.packageName
            }
            .map(ImmutableType::packageName)
            .distinct()
            .sorted()
            .map { packageName -> LsiImport(packageName, "by") }
            .toList()
    }

    private fun kotlinByFunction(withBase: Boolean): LsiFunction {
        return LsiFunction(
            name = "by",
            annotations = listOf(generatedByAnnotation(modelType)),
            receiverType = declaredType(FETCHER_CREATOR_ID, modelType),
            parameters = buildList {
                if (withBase) {
                    add(LsiParameter("base", fetcherType.withRootNullability(nullable = true)))
                }
                add(LsiParameter("block", receiverFunctionType(fetcherDslType)))
            },
            returnType = fetcherType,
            body = code {
                statement {
                    text("val dsl = ")
                    type(fetcherDslType)
                    text("(")
                    if (withBase) {
                        text("base ?: ")
                    }
                    name(emptyFetcherName)
                    text(")")
                }
                statement { text("dsl.block()") }
                returnValue { text("dsl.internallyGetFetcher()") }
            },
        )
    }

    private fun kotlinFetcherDslType(): LsiClass {
        return LsiClass(
            name = "${type.simpleName}$FETCHER_DSL_SUFFIX",
            kind = LsiTypeDeclarationKind.CLASS,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            annotations = listOf(DSL_SCOPE_ANNOTATION, generatedByAnnotation(modelType)),
            primaryConstructor = LsiConstructor(
                parameters = listOf(
                    LsiParameter(
                        name = "fetcher",
                        type = fetcherType,
                        defaultValue = code { name(emptyFetcherName) },
                    )
                ),
            ),
            members = buildList {
                add(
                    LsiProperty(
                        name = "_fetcher",
                        type = fetcherType,
                        mutable = true,
                        modifiers = setOf(LsiModifier.PRIVATE),
                        initializer = code { name("fetcher") },
                    )
                )
                add(kotlinInternallyGetFetcherFunction())
                add(kotlinDeleteFunction("allScalarFields"))
                add(kotlinDeleteFunction("allTableFields"))
                addAll(kotlinInheritanceFunctions())
                type.props.forEach { prop ->
                    if (prop.primaryMapping != PrimaryMapping.ID) {
                        add(kotlinSimplePropFunction(prop))
                        kotlinIdOnlyFetchTypeFunction(prop)?.let(::add)
                        BOOLEAN_VALUES.forEach { enabled ->
                            BOOLEAN_VALUES.forEach { lambda ->
                                BOOLEAN_VALUES.forEach { config ->
                                    kotlinPropFunction(prop, enabled, lambda, config)?.let(::add)
                                }
                            }
                        }
                        kotlinReferenceFetchTypeFunction(prop, lambda = false)?.let(::add)
                        kotlinReferenceFetchTypeFunction(prop, lambda = true)?.let(::add)
                        kotlinRecursiveFunction(prop, config = false)?.let(::add)
                        kotlinRecursiveFunction(prop, config = true)?.let(::add)
                    }
                }
            },
        )
    }

    private fun kotlinInternallyGetFetcherFunction(): LsiFunction {
        return LsiFunction(
            name = "internallyGetFetcher",
            returnType = fetcherType,
            body = code { returnValue { name("_fetcher") } },
        )
    }

    private fun kotlinDeleteFunction(name: String): LsiFunction {
        return LsiFunction(
            name = name,
            body = code {
                text("_fetcher = _fetcher.")
                name(name)
                text("()")
            },
        )
    }

    private fun kotlinInheritanceFunctions(): List<LsiFunction> {
        if (strictTypeBranches.isEmpty()) {
            return emptyList()
        }
        val typeParameterId = LsiSymbolId.typeParameter(fetcherDslType.declarationId, "S")
        val typeParameter = LsiTypeParameter(
            id = typeParameterId,
            name = "S",
            upperBounds = listOf(modelType),
        )
        val genericFunction = LsiFunction(
            name = "forType",
            typeParameters = listOf(typeParameter),
            parameters = listOf(
                LsiParameter(
                    "typeBranchFetcher",
                    declaredType(FETCHER_ID, LsiTypeParameterRef(typeParameterId)),
                )
            ),
            body = code {
                statement {
                    text("_fetcher = (_fetcher as ")
                    type(declaredType(FETCHER_IMPLEMENTOR_ID, modelType))
                    text(").__forType(typeBranchFetcher)")
                }
            },
        )
        return listOf(genericFunction) + strictTypeBranches.map(::kotlinTypeBranchFunction)
    }

    private fun kotlinTypeBranchFunction(typeBranch: ImmutableType): LsiFunction {
        val branchType = LsiDeclaredType(typeBranch.id)
        val branchFetcherDslType = LsiDeclaredType(
            typeBranch.generatedTypeId("${typeBranch.simpleName}$FETCHER_DSL_SUFFIX")
        )
        return LsiFunction(
            name = "forType",
            annotations = listOf(
                sourceLsiAnnotation(
                    type = KOTLIN_SUPPRESS_ID,
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Positional(
                            LsiAnnotationValue.StringValue("UNUSED_PARAMETER")
                        )
                    ),
                ),
                sourceLsiAnnotation(
                    type = JVM_NAME_ID,
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Positional(
                            LsiAnnotationValue.StringValue(
                                "forType_${typeBranch.qualifiedName.replace('.', '_')}"
                            )
                        )
                    ),
                ),
            ),
            parameters = listOf(
                LsiParameter("type", declaredType(K_CLASS_ID, branchType)),
                LsiParameter("block", receiverFunctionType(branchFetcherDslType)),
            ),
            body = code {
                statement {
                    text("val dsl = ")
                    type(branchFetcherDslType)
                    text("()")
                }
                statement { text("dsl.block()") }
                statement {
                    text("_fetcher = (_fetcher as ")
                    type(declaredType(FETCHER_IMPLEMENTOR_ID, modelType))
                    text(").__forType(dsl.internallyGetFetcher())")
                }
            },
        )
    }

    private fun kotlinSimplePropFunction(prop: ImmutableProp): LsiFunction {
        return LsiFunction(
            name = prop.name,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            parameters = listOf(
                LsiParameter(
                    name = "enabled",
                    type = BOOLEAN_TYPE,
                    defaultValue = code { text("true") },
                )
            ),
            body = code {
                text("_fetcher = ")
                beginControlFlow { text("if (enabled)") }
                statement {
                    text("_fetcher.add(")
                    string(prop.name)
                    text(")")
                }
                nextControlFlow { text("else") }
                statement {
                    text("_fetcher.remove(")
                    string(prop.name)
                    text(")")
                }
                endControlFlow()
            },
        )
    }

    private fun kotlinIdOnlyFetchTypeFunction(prop: ImmutableProp): LsiFunction? {
        val associationProp = schema.idViewBasePropOrSelf(prop)
        if (
            associationProp.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isConcreteEntityAssociation(associationProp)
        ) {
            return null
        }
        if (
            prop.reverse ||
            associationProp.list ||
            associationProp.hasAnnotation(JOIN_TABLE_ANNOTATION_ID)
        ) {
            return null
        }
        return LsiFunction(
            name = prop.name,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            parameters = listOf(LsiParameter("idOnlyFetchType", ID_ONLY_FETCH_TYPE)),
            body = code {
                text("_fetcher = _fetcher.add(")
                string(prop.name)
                text(", idOnlyFetchType)")
            },
        )
    }

    private fun kotlinPropFunction(
        prop: ImmutableProp,
        enabled: Boolean,
        lambda: Boolean,
        config: Boolean,
    ): LsiFunction? {
        val targetType = schema.targetTypeOf(prop) ?: return null
        if (targetType.kind != ImmutableTypeKind.ENTITY && targetType.kind != ImmutableTypeKind.EMBEDDABLE) {
            return null
        }
        val configurable = !prop.remote && targetType.kind == ImmutableTypeKind.ENTITY
        if (!configurable && config) {
            return null
        }
        val targetTypeRef = prop.targetTypeRef()
        val targetFetcherDslType = LsiDeclaredType(
            targetType.generatedTypeId("${targetType.simpleName}$FETCHER_DSL_SUFFIX")
        )
        val (configDslId, configTransformName) = kotlinConfigDsl(prop)
        val configBlockParameter = LsiParameter(
            name = "cfgBlock",
            type = receiverFunctionType(
                declaredType(configDslId, targetTypeRef),
                nullable = true,
            ),
        )
        return LsiFunction(
            name = prop.name,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            parameters = buildList {
                if (enabled) {
                    add(LsiParameter("enabled", BOOLEAN_TYPE))
                }
                if (lambda) {
                    if (config) {
                        add(configBlockParameter)
                    }
                    add(LsiParameter("childBlock", receiverFunctionType(targetFetcherDslType)))
                } else {
                    add(LsiParameter("childFetcher", declaredType(FETCHER_ID, targetTypeRef)))
                    if (config) {
                        add(configBlockParameter)
                    }
                }
            },
            body = if (enabled) {
                kotlinEnabledPropCode(prop, lambda, config)
            } else {
                kotlinDirectPropCode(
                    prop = prop,
                    targetFetcherDslType = targetFetcherDslType,
                    lambda = lambda,
                    config = config,
                    configTransformName = configTransformName,
                )
            },
        )
    }

    private fun kotlinEnabledPropCode(
        prop: ImmutableProp,
        lambda: Boolean,
        config: Boolean,
    ): LsiCodeBlock {
        return code {
            beginControlFlow { text("if (!enabled)") }
            statement {
                text("_fetcher = _fetcher.remove(")
                string(prop.name)
                text(")")
            }
            nextControlFlow { text("else") }
            name(prop.name)
            text("(")
            if (lambda) {
                if (config) {
                    text("cfgBlock, ")
                }
                text("childBlock)")
            } else {
                text("childFetcher")
                if (config) {
                    text(", cfgBlock")
                }
                text(")")
            }
            line()
            endControlFlow()
        }
    }

    private fun kotlinDirectPropCode(
        prop: ImmutableProp,
        targetFetcherDslType: LsiDeclaredType,
        lambda: Boolean,
        config: Boolean,
        configTransformName: String,
    ): LsiCodeBlock {
        return code {
            text("_fetcher = _fetcher.add(")
            line()
            indent {
                string(prop.name)
                text(",")
                line()
                if (lambda) {
                    type(targetFetcherDslType)
                    text("().apply { childBlock() }.internallyGetFetcher()")
                } else {
                    text("childFetcher")
                }
                if (config) {
                    text(",")
                    line()
                    type(JAVA_FIELD_CONFIG_UTILS_TYPE)
                    text(".")
                    name(configTransformName)
                    text("(cfgBlock)")
                }
            }
            line()
            text(")")
            line()
        }
    }

    private fun kotlinReferenceFetchTypeFunction(
        prop: ImmutableProp,
        lambda: Boolean,
    ): LsiFunction? {
        if (prop.remote || prop.list || !schema.isConcreteEntityAssociation(prop)) {
            return null
        }
        val targetType = schema.targetTypeOf(prop)
            ?: error("Entity association '${prop.id.value}' has no target type")
        val targetTypeRef = prop.targetTypeRef()
        val targetFetcherDslType = LsiDeclaredType(
            targetType.generatedTypeId("${targetType.simpleName}$FETCHER_DSL_SUFFIX")
        )
        return LsiFunction(
            name = prop.name,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            parameters = buildList {
                add(LsiParameter("fetchType", REFERENCE_FETCH_TYPE))
                if (lambda) {
                    add(LsiParameter("childBlock", receiverFunctionType(targetFetcherDslType)))
                } else {
                    add(LsiParameter("childFetcher", declaredType(FETCHER_ID, targetTypeRef)))
                }
            },
            body = code {
                text("_fetcher = _fetcher.add(")
                line()
                indent {
                    string(prop.name)
                    text(",")
                    line()
                    if (lambda) {
                        type(targetFetcherDslType)
                        text("().apply { childBlock() }.internallyGetFetcher()")
                    } else {
                        text("childFetcher")
                    }
                    text(",")
                    line()
                    type(JAVA_FIELD_CONFIG_UTILS_TYPE)
                    text(".reference<")
                    type(LsiDeclaredType(targetType.id))
                    text(">(fetchType)")
                }
                line()
                text(")")
                line()
            },
        )
    }

    private fun kotlinRecursiveFunction(
        prop: ImmutableProp,
        config: Boolean,
    ): LsiFunction? {
        if (!prop.recursive) {
            return null
        }
        val targetTypeRef = prop.targetTypeRef()
        val (configDslId, configTransformName) = if (prop.list) {
            K_RECURSIVE_LIST_FIELD_DSL_ID to "recursiveList"
        } else {
            K_RECURSIVE_REFERENCE_FIELD_DSL_ID to "recursiveReference"
        }
        val parameters = if (config) {
            listOf(
                LsiParameter(
                    name = "cfgBlock",
                    type = receiverFunctionType(
                        declaredType(configDslId, targetTypeRef),
                        nullable = true,
                    ),
                )
            )
        } else {
            emptyList()
        }
        return LsiFunction(
            name = "${prop.name}*",
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            parameters = parameters,
            body = code {
                text("_fetcher = _fetcher.addRecursion(")
                line()
                indent {
                    string(prop.name)
                    text(",")
                    line()
                    if (config) {
                        type(JAVA_FIELD_CONFIG_UTILS_TYPE)
                        text(".")
                        name(configTransformName)
                        text("(cfgBlock)")
                    } else {
                        text("null")
                    }
                    line()
                }
                text(")")
                line()
            },
        )
    }

    private fun kotlinConfigDsl(prop: ImmutableProp): Pair<LsiSymbolId, String> {
        return when {
            prop.list -> K_LIST_FIELD_DSL_ID to "list"
            schema.isConcreteEntityAssociation(prop) -> K_REFERENCE_FIELD_DSL_ID to "reference"
            else -> K_FIELD_DSL_ID to "simple"
        }
    }

    private fun ImmutableProp.targetTypeRef(): LsiType {
        return elementTypeOrSelf()
            .withKotlinExpressionRoot()
            .withoutTypeAnnotations()
    }

    private fun kotlinEmptyFetcherProperty(): LsiProperty {
        return LsiProperty(
            name = emptyFetcherName,
            type = fetcherType,
            mutable = false,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            modifiers = setOf(LsiModifier.PRIVATE),
            initializer = code {
                type(LsiDeclaredType(FETCHER_IMPL_ID))
                text("(")
                type(modelType)
                text("::class.java)")
            },
        )
    }
}

private fun ImmutableSchema.fetcherDependencies(
    type: ImmutableType,
    workspace: LsiWorkspace,
    language: LsiLanguage,
): FetcherArtifactDependencies {
    val originatingSymbols = inheritanceArtifactOriginatingSymbols(type)
    val dependencySymbols = sortedSetOf<LsiSymbolId>().apply {
        addAll(originatingSymbols)
        addImmutableTypeHierarchy(
            schema = this@fetcherDependencies,
            rootTypeIds = originatingSymbols + type.id,
        )
        addImmutablePropClosure(
            schema = this@fetcherDependencies,
            rootProps = type.props,
        )
        addAll(
            when (language) {
                LsiLanguage.JAVA -> JAVA_RUNTIME_DEPENDENCIES
                LsiLanguage.KOTLIN -> KOTLIN_RUNTIME_DEPENDENCIES
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
    return FetcherArtifactDependencies(
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySymbols = dependencySymbols,
        dependencySources = dependencySources,
    )
}

private fun MutableSet<LsiSymbolId>.addImmutableTypeHierarchy(
    schema: ImmutableSchema,
    rootTypeIds: Collection<LsiSymbolId>,
) {
    val pending = ArrayDeque(rootTypeIds.sorted())
    val visited = mutableSetOf<LsiSymbolId>()
    while (pending.isNotEmpty()) {
        val typeId = pending.removeFirst()
        if (!visited.add(typeId)) {
            continue
        }
        add(typeId)
        val immutableType = schema.typesById[typeId] ?: continue
        immutableType.annotations.forEach(::addAnnotationDependencies)
        addAll(immutableType.typeParameterIds)
        immutableType.discriminatorPropId?.let(::add)
        immutableType.idPropId?.let(::add)
        immutableType.versionPropId?.let(::add)
        immutableType.logicalDeletedPropId?.let(::add)
        val hierarchyIds = buildList {
            addAll(immutableType.superTypeIds)
            immutableType.primarySuperTypeId?.let(::add)
            immutableType.inheritanceRootTypeId?.let(::add)
        }
        addAll(hierarchyIds)
        hierarchyIds.sorted().forEach(pending::addLast)
    }
}

private fun MutableSet<LsiSymbolId>.addImmutablePropClosure(
    schema: ImmutableSchema,
    rootProps: Collection<ImmutableProp>,
) {
    val pending = ArrayDeque(rootProps.sortedBy(ImmutableProp::id))
    val visited = mutableSetOf<LsiSymbolId>()
    while (pending.isNotEmpty()) {
        val prop = pending.removeFirst()
        if (!visited.add(prop.id)) {
            continue
        }
        add(prop.id)
        add(prop.declarationId)
        add(prop.ownerTypeId)
        add(prop.declaringTypeId)
        addAll(prop.overrideChain)
        prop.primaryAnnotationTypeId?.let(::add)
        addTypeDependencies(prop.type)
        prop.annotations.forEach(::addAnnotationDependencies)
        addImmutableTypeHierarchy(
            schema = schema,
            rootTypeIds = listOfNotNull(
                prop.ownerTypeId,
                prop.declaringTypeId,
                prop.targetTypeId,
            ),
        )
        val dependencyPropIds = buildList {
            prop.mappedBy?.ownerPropId?.let(::add)
            prop.view?.dependencyPropIds?.let(::addAll)
            prop.formulaDependencies.forEach { dependency -> addAll(dependency.propIds) }
        }
        addAll(dependencyPropIds)
        dependencyPropIds
            .mapNotNull(schema.propsById::get)
            .sortedBy(ImmutableProp::id)
            .forEach(pending::addLast)
        when (val resolver = prop.transientResolver) {
            is TransientResolver.Type -> {
                add(resolver.typeId)
                addImmutableTypeHierarchy(schema, listOf(resolver.typeId))
            }
            is TransientResolver.Reference,
            null,
            -> Unit
        }
        prop.validations.forEach { validation ->
            add(validation.annotationTypeId)
            addAll(validation.validatorTypeIds)
        }
        prop.converter?.let { converter ->
            add(converter.converterTypeId)
            converter.sourceType?.let(::addTypeDependencies)
            converter.targetType?.let(::addTypeDependencies)
        }
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

private fun generatedByAnnotation(
    type: LsiType,
    fileTarget: Boolean = false,
): LsiAnnotation {
    return sourceLsiAnnotation(
        type = GENERATED_BY_ID,
        arguments = listOf(
            LsiSourceAnnotationArgument.Named(
                name = "type",
                value = LsiAnnotationValue.ClassValue(type),
            )
        ),
        useSiteTarget = if (fileTarget) {
            LsiAnnotationUseSiteTarget.FILE
        } else {
            null
        },
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

private fun declaredType(
    id: LsiSymbolId,
    argument: LsiTypeArgument,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = id,
        arguments = listOf(argument),
    )
}

private fun receiverFunctionType(
    receiverType: LsiType,
    nullable: Boolean = false,
): LsiFunctionType {
    return LsiFunctionType(
        receiverType = receiverType,
        returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
        nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL,
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

private fun ImmutableType.generatedTypeId(generatedSimpleName: String): LsiSymbolId {
    val qualifiedName = if (packageName.isEmpty()) {
        generatedSimpleName
    } else {
        "$packageName.$generatedSimpleName"
    }
    return LsiSymbolId.type(qualifiedName)
}

private fun ImmutableType?.requiredModelType(prop: ImmutableProp): LsiDeclaredType {
    return this?.let { type -> LsiDeclaredType(type.id) } ?: throw ImmutablePrecompileException(
        declarationId = prop.declarationId,
        recoverable = true,
        message = "Cannot resolve fetcher target type of immutable property '${prop.id.value}'",
    )
}

private fun ImmutableType?.requiredTableType(prop: ImmutableProp): LsiDeclaredType {
    return this?.let { type ->
        LsiDeclaredType(type.generatedTypeId("${type.simpleName}Table"))
    } ?: throw ImmutablePrecompileException(
        declarationId = prop.declarationId,
        recoverable = true,
        message = "Cannot resolve fetcher table type of immutable property '${prop.id.value}'",
    )
}

private fun ImmutableProp.fetcherDocumentation(): String? {
    return documentation?.let(Doc::parse)?.value
}

private data class FetcherArtifactDependencies(
    val originatingSymbols: Set<LsiSymbolId>,
    val originatingSources: Set<LsiSource>,
    val dependencySymbols: Set<LsiSymbolId>,
    val dependencySources: Set<LsiSource>,
)

private const val FETCHER_SUFFIX = "Fetcher"
private const val FETCHER_DSL_SUFFIX = "FetcherDsl"

private val BOOLEAN_VALUES = booleanArrayOf(false, true)

private val GENERATED_BY_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val NEW_CHAIN_ID = LsiSymbolId.type("org.babyfish.jimmer.lang.NewChain")
private val DSL_SCOPE_ID = LsiSymbolId.type("org.babyfish.jimmer.kt.DslScope")
private val KOTLIN_SUPPRESS_ID = LsiSymbolId.type("kotlin.Suppress")
private val JVM_NAME_ID = LsiSymbolId.type("kotlin.jvm.JvmName")
private val K_CLASS_ID = LsiSymbolId.type("kotlin.reflect.KClass")
private val ABSTRACT_TYPED_FETCHER_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.fetcher.spi.AbstractTypedFetcher"
)
private val FETCHER_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.Fetcher")
private val FETCHER_IMPL_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.impl.FetcherImpl")
private val FETCHER_IMPLEMENTOR_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.fetcher.impl.FetcherImplementor"
)
private val FETCHER_CREATOR_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.fetcher.FetcherCreator")
private val ID_ONLY_FETCH_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.IdOnlyFetchType")
private val REFERENCE_FETCH_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.ReferenceFetchType")
private val FIELD_CONFIG_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.FieldConfig")
private val REFERENCE_FIELD_CONFIG_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.fetcher.ReferenceFieldConfig"
)
private val LIST_FIELD_CONFIG_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.ListFieldConfig")
private val RECURSIVE_REFERENCE_FIELD_CONFIG_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.fetcher.RecursiveReferenceFieldConfig"
)
private val RECURSIVE_LIST_FIELD_CONFIG_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.fetcher.RecursiveListFieldConfig"
)
private val TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Table")
private val IMMUTABLE_PROP_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableProp")
private val CONSUMER_ID = LsiSymbolId.type("java.util.function.Consumer")
private val JAVA_OVERRIDE_ID = LsiSymbolId.type("java.lang.Override")
private val JAVA_FIELD_CONFIG_UTILS_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.fetcher.impl.JavaFieldConfigUtils"
)
private val K_FIELD_DSL_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.fetcher.KFieldDsl")
private val K_REFERENCE_FIELD_DSL_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.fetcher.KReferenceFieldDsl"
)
private val K_LIST_FIELD_DSL_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.fetcher.KListFieldDsl")
private val K_RECURSIVE_REFERENCE_FIELD_DSL_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.fetcher.KRecursiveReferenceFieldDsl"
)
private val K_RECURSIVE_LIST_FIELD_DSL_ID = LsiSymbolId.type(
    "org.babyfish.jimmer.sql.kt.fetcher.KRecursiveListFieldDsl"
)
private val JOIN_TABLE_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinTable")

private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
private val ID_ONLY_FETCH_TYPE = LsiDeclaredType(ID_ONLY_FETCH_TYPE_ID)
private val REFERENCE_FETCH_TYPE = LsiDeclaredType(REFERENCE_FETCH_TYPE_ID)
private val IMMUTABLE_PROP_TYPE = LsiDeclaredType(IMMUTABLE_PROP_ID)
private val JAVA_FIELD_CONFIG_UTILS_TYPE = LsiDeclaredType(JAVA_FIELD_CONFIG_UTILS_ID)

private val NEW_CHAIN_ANNOTATION = sourceLsiAnnotation(NEW_CHAIN_ID)
private val DSL_SCOPE_ANNOTATION = sourceLsiAnnotation(DSL_SCOPE_ID)
private val FILE_WARNING_SUPPRESSION = sourceLsiAnnotation(
    type = KOTLIN_SUPPRESS_ID,
    arguments = listOf(
        LsiSourceAnnotationArgument.Positional(
            LsiAnnotationValue.StringValue("warnings")
        )
    ),
    useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
)

private val FETCHER_RUNTIME_TYPE_IDS = listOf(
    "org.babyfish.jimmer.internal.GeneratedBy",
    "org.babyfish.jimmer.lang.NewChain",
    "org.babyfish.jimmer.kt.DslScope",
    "kotlin.Suppress",
    "kotlin.jvm.JvmName",
    "kotlin.reflect.KClass",
    "org.babyfish.jimmer.sql.fetcher.spi.AbstractTypedFetcher",
    "org.babyfish.jimmer.sql.fetcher.Fetcher",
    "org.babyfish.jimmer.sql.fetcher.impl.FetcherImpl",
    "org.babyfish.jimmer.sql.fetcher.impl.FetcherImplementor",
    "org.babyfish.jimmer.sql.kt.fetcher.FetcherCreator",
    "org.babyfish.jimmer.sql.fetcher.IdOnlyFetchType",
    "org.babyfish.jimmer.sql.fetcher.ReferenceFetchType",
    "org.babyfish.jimmer.sql.fetcher.FieldConfig",
    "org.babyfish.jimmer.sql.fetcher.ReferenceFieldConfig",
    "org.babyfish.jimmer.sql.fetcher.ListFieldConfig",
    "org.babyfish.jimmer.sql.fetcher.RecursiveReferenceFieldConfig",
    "org.babyfish.jimmer.sql.fetcher.RecursiveListFieldConfig",
    "org.babyfish.jimmer.sql.ast.table.Table",
    "org.babyfish.jimmer.meta.ImmutableProp",
    "java.util.function.Consumer",
    "java.lang.Override",
    "org.babyfish.jimmer.sql.kt.fetcher.impl.JavaFieldConfigUtils",
    "org.babyfish.jimmer.sql.kt.fetcher.KFieldDsl",
    "org.babyfish.jimmer.sql.kt.fetcher.KReferenceFieldDsl",
    "org.babyfish.jimmer.sql.kt.fetcher.KListFieldDsl",
    "org.babyfish.jimmer.sql.kt.fetcher.KRecursiveReferenceFieldDsl",
    "org.babyfish.jimmer.sql.kt.fetcher.KRecursiveListFieldDsl",
    "org.babyfish.jimmer.sql.JoinTable",
).map(LsiSymbolId::type)

private val JAVA_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    NEW_CHAIN_ID,
    ABSTRACT_TYPED_FETCHER_ID,
    FETCHER_ID,
    FETCHER_IMPL_ID,
    ID_ONLY_FETCH_TYPE_ID,
    REFERENCE_FETCH_TYPE_ID,
    FIELD_CONFIG_ID,
    REFERENCE_FIELD_CONFIG_ID,
    LIST_FIELD_CONFIG_ID,
    RECURSIVE_REFERENCE_FIELD_CONFIG_ID,
    RECURSIVE_LIST_FIELD_CONFIG_ID,
    TABLE_ID,
    IMMUTABLE_PROP_ID,
    CONSUMER_ID,
    JAVA_OVERRIDE_ID,
)

private val KOTLIN_RUNTIME_DEPENDENCIES = setOf(
    GENERATED_BY_ID,
    DSL_SCOPE_ID,
    KOTLIN_SUPPRESS_ID,
    JVM_NAME_ID,
    K_CLASS_ID,
    FETCHER_ID,
    FETCHER_IMPL_ID,
    FETCHER_IMPLEMENTOR_ID,
    FETCHER_CREATOR_ID,
    ID_ONLY_FETCH_TYPE_ID,
    REFERENCE_FETCH_TYPE_ID,
    JAVA_FIELD_CONFIG_UTILS_ID,
    K_FIELD_DSL_ID,
    K_REFERENCE_FIELD_DSL_ID,
    K_LIST_FIELD_DSL_ID,
    K_RECURSIVE_REFERENCE_FIELD_DSL_ID,
    K_RECURSIVE_LIST_FIELD_DSL_ID,
)
