package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableFetcherMetadata
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.simpleName
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableFetcherKotlinRenderer {

    fun render(
        schema: JimmerImmutableSchema,
        type: JimmerImmutableType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        require(
            type.kind == JimmerImmutableTypeKind.ENTITY ||
                type.kind == JimmerImmutableTypeKind.EMBEDDABLE
        ) {
            "Kotlin immutable fetcher renderer only supports entity and embeddable types: ${type.id.value}"
        }
        return FetcherRenderContext(schema, type, workspace).render()
    }
}

private class FetcherRenderContext(
    schema: JimmerImmutableSchema,
    private val type: JimmerImmutableType,
    private val workspace: LsiWorkspace,
) {

    private val metadata = JimmerImmutableFetcherMetadata(schema)

    private val modelClass = ClassName.bestGuess(type.qualifiedName)

    private val fetcherType = FETCHER.parameterizedBy(modelClass)

    private val fetcherDslClass = type.fetcherDslClassName()

    private val emptyFetcherName = "empty${type.simpleName}$FETCHER_SUFFIX"

    private val strictTypeBranches = metadata.strictTypeBranches(type)

    fun render(): GeneratedArtifact {
        val sourceBaseName = metadata.sourceBaseName(type, workspace)
        val fileName = "$sourceBaseName$FETCHER_SUFFIX"
        val fileSpec = FileSpec.builder(type.packageName, fileName)
            .indent("    ")
            .addAnnotation(suppressAllAnnotation())
            .addAnnotation(generatedByAnnotation(modelClass))
            .apply {
                addCrossPackageByImports()
                addFunction(createByFunction(withBase = false))
                addFunction(createByFunction(withBase = true))
                addType(fetcherDslType())
                addProperty(emptyFetcherProperty())
            }
            .build()
        val originatingSymbols = metadata.originatingSymbols(type)
        val qualifiedFileName = if (type.packageName.isEmpty()) {
            fileName
        } else {
            "${type.packageName}.$fileName"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = qualifiedFileName,
            content = fileSpec.toString(),
            aggregationMode = metadata.aggregationMode(type),
            emissionMode = if (metadata.branchDependent(type)) {
                ArtifactEmissionMode.STABLE
            } else {
                ArtifactEmissionMode.IMMEDIATE
            },
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }

    private fun FileSpec.Builder.addCrossPackageByImports() {
        type.props.asSequence()
            .filter(metadata::isEntityAssociation)
            .mapNotNull(metadata::targetType)
            .filter { targetType ->
                targetType.packageName.isNotEmpty() && targetType.packageName != type.packageName
            }
            .map(JimmerImmutableType::packageName)
            .distinct()
            .sorted()
            .forEach { packageName -> addImport(packageName, "by") }
    }

    private fun createByFunction(withBase: Boolean): FunSpec {
        return FunSpec.builder("by")
            .addAnnotation(generatedByAnnotation(modelClass))
            .receiver(FETCHER_CREATOR.parameterizedBy(modelClass))
            .apply {
                if (withBase) {
                    addParameter(
                        "base",
                        fetcherType.copy(nullable = true),
                    )
                }
            }
            .addParameter(
                "block",
                LambdaTypeName.get(fetcherDslClass, emptyList(), UNIT),
            )
            .returns(fetcherType)
            .addStatement(
                "val dsl = %T(%L$emptyFetcherName)",
                fetcherDslClass,
                if (withBase) "base ?: " else "",
            )
            .addStatement("dsl.block()")
            .addStatement("return dsl.internallyGetFetcher()")
            .build()
    }

    private fun fetcherDslType(): TypeSpec {
        return TypeSpec.classBuilder(fetcherDslClass)
            .addAnnotation(DSL_SCOPE)
            .addAnnotation(generatedByAnnotation(modelClass))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("fetcher", fetcherType)
                            .defaultValue(emptyFetcherName)
                            .build()
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("_fetcher", fetcherType, KModifier.PRIVATE)
                    .mutable()
                    .initializer("fetcher")
                    .build()
            )
            .addFunction(internallyGetFetcherFunction())
            .addFunction(deleteFunction("allScalarFields"))
            .addFunction(deleteFunction("allTableFields"))
            .apply {
                addInheritanceFunctions()
                type.props.forEach { prop ->
                    if (prop.primaryMapping != JimmerImmutablePrimaryMapping.ID) {
                        addFunction(simplePropFunction(prop))
                        idOnlyFetchTypeFunction(prop)?.let(::addFunction)
                        BOOLEAN_VALUES.forEach { enabled ->
                            BOOLEAN_VALUES.forEach { lambda ->
                                BOOLEAN_VALUES.forEach { config ->
                                    propFunction(prop, enabled, lambda, config)?.let(::addFunction)
                                }
                            }
                        }
                        referenceFetchTypeFunction(prop, lambda = false)?.let(::addFunction)
                        referenceFetchTypeFunction(prop, lambda = true)?.let(::addFunction)
                        recursiveFunction(prop, config = false)?.let(::addFunction)
                        recursiveFunction(prop, config = true)?.let(::addFunction)
                    }
                }
            }
            .build()
    }

    private fun internallyGetFetcherFunction(): FunSpec {
        return FunSpec.builder("internallyGetFetcher")
            .returns(fetcherType)
            .addStatement("return _fetcher")
            .build()
    }

    private fun deleteFunction(name: String): FunSpec {
        return FunSpec.builder(name)
            .addCode("_fetcher = _fetcher.%L()", name)
            .build()
    }

    private fun TypeSpec.Builder.addInheritanceFunctions() {
        if (strictTypeBranches.isEmpty()) {
            return
        }
        require(type.props.none { prop -> prop.name == "forType" }) {
            "Illegal property name \"forType\" conflicts with generated fetcher inheritance branches: ${type.id.value}"
        }
        val typeVariable = TypeVariableName("S", modelClass)
        addFunction(
            FunSpec.builder("forType")
                .addTypeVariable(typeVariable)
                .addParameter(
                    "typeBranchFetcher",
                    FETCHER.parameterizedBy(typeVariable),
                )
                .addStatement(
                    "_fetcher = (_fetcher as %T<%T>).__forType(typeBranchFetcher)",
                    FETCHER_IMPLEMENTOR,
                    modelClass,
                )
                .build()
        )
        strictTypeBranches.forEach { typeBranch ->
            addFunction(typeBranchFunction(typeBranch))
        }
    }

    private fun typeBranchFunction(typeBranch: JimmerImmutableType): FunSpec {
        val branchClass = ClassName.bestGuess(typeBranch.qualifiedName)
        val branchFetcherDslClass = typeBranch.fetcherDslClassName()
        return FunSpec.builder("forType")
            .addAnnotation(
                AnnotationSpec.builder(SUPPRESS)
                    .addMember("%S", "UNUSED_PARAMETER")
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(JVM_NAME)
                    .addMember("%S", "forType_${typeBranch.qualifiedName.replace('.', '_')}")
                    .build()
            )
            .addParameter(
                "type",
                K_CLASS.parameterizedBy(branchClass),
            )
            .addParameter(
                "block",
                LambdaTypeName.get(branchFetcherDslClass, emptyList(), UNIT),
            )
            .addStatement("val dsl = %T()", branchFetcherDslClass)
            .addStatement("dsl.block()")
            .addStatement(
                "_fetcher = (_fetcher as %T<%T>).__forType(dsl.internallyGetFetcher())",
                FETCHER_IMPLEMENTOR,
                modelClass,
            )
            .build()
    }

    private fun simplePropFunction(prop: JimmerImmutableProp): FunSpec {
        return FunSpec.builder(prop.name)
            .addParameter(
                ParameterSpec.builder("enabled", BOOLEAN)
                    .defaultValue("true")
                    .build()
            )
            .addCode(
                CodeBlock.builder()
                    .add("_fetcher = ")
                    .beginControlFlow("if (enabled)")
                    .addStatement("_fetcher.add(%S)", prop.name)
                    .nextControlFlow("else")
                    .addStatement("_fetcher.remove(%S)", prop.name)
                    .endControlFlow()
                    .build()
            )
            .build()
    }

    private fun idOnlyFetchTypeFunction(prop: JimmerImmutableProp): FunSpec? {
        val associationProp = metadata.idOnlyAssociationProp(prop)
        if (
            associationProp.primaryMapping == JimmerImmutablePrimaryMapping.TRANSIENT ||
            !metadata.isEntityAssociation(associationProp)
        ) {
            return null
        }
        if (
            prop.reverse ||
            associationProp.list ||
            metadata.hasAnnotation(associationProp, JOIN_TABLE_ANNOTATION)
        ) {
            return null
        }
        return FunSpec.builder(prop.name)
            .addParameter("idOnlyFetchType", ID_ONLY_FETCH_TYPE)
            .addCode("_fetcher = _fetcher.add(%S, idOnlyFetchType)", prop.name)
            .build()
    }

    private fun propFunction(
        prop: JimmerImmutableProp,
        enabled: Boolean,
        lambda: Boolean,
        config: Boolean,
    ): FunSpec? {
        val targetType = metadata.targetType(prop) ?: return null
        if (
            targetType.kind != JimmerImmutableTypeKind.ENTITY &&
            targetType.kind != JimmerImmutableTypeKind.EMBEDDABLE
        ) {
            return null
        }
        val configurable = !prop.remote && targetType.kind == JimmerImmutableTypeKind.ENTITY
        if (!configurable && config) {
            return null
        }
        val targetTypeName = prop.targetTypeName()
        val targetFetcherDslClass = targetType.fetcherDslClassName()
        val (configDslClass, configTransformName) = configDsl(prop)
        val configBlockParameter = ParameterSpec.builder(
            "cfgBlock",
            LambdaTypeName.get(
                configDslClass.parameterizedBy(targetTypeName),
                emptyList(),
                UNIT,
            ).copy(nullable = true),
        ).build()
        return FunSpec.builder(prop.name)
            .apply {
                if (enabled) {
                    addParameter("enabled", BOOLEAN)
                }
                if (lambda) {
                    if (config) {
                        addParameter(configBlockParameter)
                    }
                    addParameter(
                        "childBlock",
                        LambdaTypeName.get(targetFetcherDslClass, emptyList(), UNIT),
                    )
                } else {
                    addParameter(
                        "childFetcher",
                        FETCHER.parameterizedBy(targetTypeName),
                    )
                    if (config) {
                        addParameter(configBlockParameter)
                    }
                }
            }
            .addCode(
                if (enabled) {
                    enabledPropCode(prop, lambda, config)
                } else {
                    directPropCode(prop, targetFetcherDslClass, lambda, config, configTransformName)
                }
            )
            .build()
    }

    private fun enabledPropCode(
        prop: JimmerImmutableProp,
        lambda: Boolean,
        config: Boolean,
    ): CodeBlock {
        return CodeBlock.builder()
            .beginControlFlow("if (!enabled)")
            .addStatement("_fetcher = _fetcher.remove(%S)", prop.name)
            .nextControlFlow("else")
            .add("%N(", prop.name)
            .apply {
                if (lambda) {
                    if (config) {
                        add("cfgBlock, ")
                    }
                    add("childBlock)\n")
                } else {
                    add("childFetcher")
                    if (config) {
                        add(", cfgBlock")
                    }
                    add(")\n")
                }
            }
            .endControlFlow()
            .build()
    }

    private fun directPropCode(
        prop: JimmerImmutableProp,
        targetFetcherDslClass: ClassName,
        lambda: Boolean,
        config: Boolean,
        configTransformName: String,
    ): CodeBlock {
        return CodeBlock.builder()
            .add("_fetcher = _fetcher.add(\n")
            .indent()
            .add("%S,\n", prop.name)
            .apply {
                if (lambda) {
                    add(
                        "%T().apply { childBlock() }.internallyGetFetcher()",
                        targetFetcherDslClass,
                    )
                } else {
                    add("childFetcher")
                }
                if (config) {
                    add(",\n%T.%L(cfgBlock)", JAVA_FIELD_CONFIG_UTILS, configTransformName)
                }
            }
            .unindent()
            .add("\n)\n")
            .build()
    }

    private fun referenceFetchTypeFunction(
        prop: JimmerImmutableProp,
        lambda: Boolean,
    ): FunSpec? {
        if (prop.remote || prop.list || !metadata.isEntityAssociation(prop)) {
            return null
        }
        val targetType = requireNotNull(metadata.targetType(prop)) {
            "Entity association '${prop.id.value}' has no target type"
        }
        val targetTypeName = prop.targetTypeName()
        val targetFetcherDslClass = targetType.fetcherDslClassName()
        return FunSpec.builder(prop.name)
            .addParameter("fetchType", REFERENCE_FETCH_TYPE)
            .apply {
                if (lambda) {
                    addParameter(
                        "childBlock",
                        LambdaTypeName.get(targetFetcherDslClass, emptyList(), UNIT),
                    )
                } else {
                    addParameter(
                        "childFetcher",
                        FETCHER.parameterizedBy(targetTypeName),
                    )
                }
            }
            .addCode(
                CodeBlock.builder()
                    .add("_fetcher = _fetcher.add(\n")
                    .indent()
                    .add("%S,\n", prop.name)
                    .apply {
                        if (lambda) {
                            add(
                                "%T().apply { childBlock() }.internallyGetFetcher()",
                                targetFetcherDslClass,
                            )
                        } else {
                            add("childFetcher")
                        }
                    }
                    .add(
                        ",\n%T.reference<%T>(fetchType)",
                        JAVA_FIELD_CONFIG_UTILS,
                        ClassName.bestGuess(targetType.qualifiedName),
                    )
                    .unindent()
                    .add("\n)\n")
                    .build()
            )
            .build()
    }

    private fun recursiveFunction(
        prop: JimmerImmutableProp,
        config: Boolean,
    ): FunSpec? {
        if (!prop.recursive) {
            return null
        }
        val targetTypeName = prop.targetTypeName()
        val (configDslClass, configTransformName) = if (prop.list) {
            K_RECURSIVE_LIST_FIELD_DSL to "recursiveList"
        } else {
            K_RECURSIVE_REFERENCE_FIELD_DSL to "recursiveReference"
        }
        val configBlockParameter = ParameterSpec.builder(
            "cfgBlock",
            LambdaTypeName.get(
                configDslClass.parameterizedBy(targetTypeName),
                emptyList(),
                UNIT,
            ).copy(nullable = true),
        ).build()
        return FunSpec.builder(prop.name + '*')
            .apply {
                if (config) {
                    addParameter(configBlockParameter)
                }
            }
            .addCode(
                CodeBlock.builder()
                    .add("_fetcher = _fetcher.addRecursion(\n")
                    .indent()
                    .add("%S,\n", prop.name)
                    .apply {
                        if (config) {
                            add("%T.%N(cfgBlock)\n", JAVA_FIELD_CONFIG_UTILS, configTransformName)
                        } else {
                            add("null\n")
                        }
                    }
                    .unindent()
                    .add(")\n")
                    .build()
            )
            .build()
    }

    private fun configDsl(prop: JimmerImmutableProp): Pair<ClassName, String> {
        return when {
            prop.list -> K_LIST_FIELD_DSL to "list"
            metadata.isEntityAssociation(prop) -> K_REFERENCE_FIELD_DSL to "reference"
            else -> K_FIELD_DSL to "simple"
        }
    }

    private fun JimmerImmutableProp.targetTypeName(): TypeName {
        val targetTypeRef = if (list) {
            val declaredType = type as? LsiDeclaredType
                ?: error("List immutable property '${id.value}' must use a declared list type")
            val targetArgument = declaredType.arguments.singleOrNull()?.type
                ?: error("List immutable property '${id.value}' must declare one target type argument")
            targetArgument
        } else {
            type
        }
        return targetTypeRef.toKotlinTypeName().copy(nullable = false)
    }

    private fun emptyFetcherProperty(): PropertySpec {
        return PropertySpec.builder(emptyFetcherName, fetcherType, KModifier.PRIVATE)
            .initializer("%T(%T::class.java)", FETCHER_IMPL, modelClass)
            .build()
    }
}

private fun JimmerImmutableType.fetcherDslClassName(): ClassName {
    return ClassName(packageName, "$simpleName$FETCHER_DSL_SUFFIX")
}

private fun generatedByAnnotation(type: ClassName): AnnotationSpec {
    return AnnotationSpec.builder(GENERATED_BY)
        .addMember("type = %T::class", type)
        .build()
}

private fun suppressAllAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(SUPPRESS)
        .addMember("%S", "warnings")
        .build()
}

private const val FETCHER_SUFFIX = "Fetcher"

private const val FETCHER_DSL_SUFFIX = "FetcherDsl"

private val BOOLEAN_VALUES = booleanArrayOf(false, true)

private val JOIN_TABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinTable")

private val SUPPRESS = ClassName("kotlin", "Suppress")

private val JVM_NAME = ClassName("kotlin.jvm", "JvmName")

private val K_CLASS = ClassName("kotlin.reflect", "KClass")

private val GENERATED_BY = ClassName("org.babyfish.jimmer.internal", "GeneratedBy")

private val DSL_SCOPE = ClassName("org.babyfish.jimmer.kt", "DslScope")

private val FETCHER = ClassName("org.babyfish.jimmer.sql.fetcher", "Fetcher")

private val FETCHER_IMPL = ClassName("org.babyfish.jimmer.sql.fetcher.impl", "FetcherImpl")

private val FETCHER_IMPLEMENTOR = ClassName("org.babyfish.jimmer.sql.fetcher.impl", "FetcherImplementor")

private val FETCHER_CREATOR = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "FetcherCreator")

private val ID_ONLY_FETCH_TYPE = ClassName("org.babyfish.jimmer.sql.fetcher", "IdOnlyFetchType")

private val REFERENCE_FETCH_TYPE = ClassName("org.babyfish.jimmer.sql.fetcher", "ReferenceFetchType")

private val JAVA_FIELD_CONFIG_UTILS = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher.impl",
    "JavaFieldConfigUtils",
)

private val K_FIELD_DSL = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "KFieldDsl")

private val K_REFERENCE_FIELD_DSL = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "KReferenceFieldDsl")

private val K_LIST_FIELD_DSL = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "KListFieldDsl")

private val K_RECURSIVE_REFERENCE_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KRecursiveReferenceFieldDsl",
)

private val K_RECURSIVE_LIST_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KRecursiveListFieldDsl",
)
