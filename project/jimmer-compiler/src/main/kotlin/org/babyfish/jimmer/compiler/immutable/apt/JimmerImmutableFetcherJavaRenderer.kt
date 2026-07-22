package org.babyfish.jimmer.compiler.immutable.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
import java.util.function.Consumer
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.compiler.immutable.isBranchDependent
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.inheritanceArtifactAggregationMode
import org.babyfish.jimmer.compiler.immutable.inheritanceArtifactOriginatingSymbols
import org.babyfish.jimmer.compiler.immutable.simpleName
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.hasAnnotation
import site.addzero.lsi.jimmer.idViewBasePropOrSelf
import site.addzero.lsi.jimmer.isConcreteEntityAssociation
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
import site.addzero.lsi.jimmer.targetTypeOf
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableFetcherJavaRenderer {

    fun render(
        schema: ImmutableSchema,
        type: ImmutableType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val content = JavaFile.builder(type.packageName, fetcherType(schema, type))
            .indent("    ")
            .build()
            .toString()
        val originatingSymbols = schema.inheritanceArtifactOriginatingSymbols(type)
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = type.fetcherClassName().canonicalName(),
            content = content,
            aggregationMode = schema.inheritanceArtifactAggregationMode(type),
            emissionMode = if (schema.isBranchDependent(type)) {
                ArtifactEmissionMode.STABLE
            } else {
                ArtifactEmissionMode.IMMEDIATE
            },
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }

    private fun fetcherType(
        schema: ImmutableSchema,
        type: ImmutableType,
    ): TypeSpec {
        val fetcherClass = type.fetcherClassName()
        return TypeSpec.classBuilder(fetcherClass.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(type.generatedAnnotation())
            .superclass(
                ParameterizedTypeName.get(
                    ABSTRACT_TYPED_FETCHER,
                    type.className(),
                    fetcherClass,
                )
            )
            .apply {
                addRootField(type)
                addFromMethod(type)
                addBaseConstructor(type)
                addForType(schema, type)
                type.props.forEach { prop -> addPropMethods(schema, type, prop) }
                addNegativeConstructor(type)
                addFieldConfigConstructor(type)
                addTypeBranchConstructor(type)
                addNegativeCreator(type)
                addFieldConfigCreator(type)
                addTypeBranchCreator(type)
            }
            .build()
    }

    private fun TypeSpec.Builder.addRootField(type: ImmutableType) {
        val fetcherClass = type.fetcherClassName()
        addField(
            FieldSpec.builder(fetcherClass, "\$")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new \$T(null)", fetcherClass)
                .build()
        )
    }

    private fun TypeSpec.Builder.addFromMethod(type: ImmutableType) {
        val fetcherClass = type.fetcherClassName()
        val fetcherType = ParameterizedTypeName.get(FETCHER, type.className())
        val fetcherImplType = ParameterizedTypeName.get(FETCHER_IMPL, type.className())
        addMethod(
            MethodSpec.methodBuilder("\$from")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(fetcherType, "base")
                .returns(fetcherClass)
                .addCode("return base instanceof \$T ? \n", fetcherClass)
                .addCode("\t(\$T)base : \n", fetcherClass)
                .addCode("\tnew \$T((\$T)base);\n", fetcherClass, fetcherImplType)
                .build()
        )
    }

    private fun TypeSpec.Builder.addBaseConstructor(type: ImmutableType) {
        addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ParameterizedTypeName.get(FETCHER_IMPL, type.className()), "base")
                .addStatement("super(\$T.class, base)", type.className())
                .build()
        )
    }

    private fun TypeSpec.Builder.addForType(
        schema: ImmutableSchema,
        type: ImmutableType,
    ) {
        if (schema.strictPrimarySubtypesOf(type).isEmpty()) {
            return
        }
        val typeVariable = TypeVariableName.get("ST", type.className())
        addMethod(
            MethodSpec.methodBuilder("forType")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addTypeVariable(typeVariable)
                .addParameter(ParameterizedTypeName.get(FETCHER, typeVariable), "typeBranchFetcher")
                .returns(type.fetcherClassName())
                .addStatement("return (\$T)__forType(typeBranchFetcher)", type.fetcherClassName())
                .build()
        )
    }

    private fun TypeSpec.Builder.addPropMethods(
        schema: ImmutableSchema,
        type: ImmutableType,
        prop: ImmutableProp,
    ) {
        if (prop.primaryMapping == PrimaryMapping.ID || !prop.fetchable) {
            return
        }
        addSimpleProp(type, prop)
        addEnabledProp(type, prop)
        val targetType = schema.targetTypeOf(prop)
        if (schema.isConcreteEntityAssociation(prop)) {
            addChildProp(type, prop, targetType)
            if (!prop.list) {
                addIdOnlyProp(schema, type, prop)
            }
            if (!prop.remote) {
                addFieldConfigProp(type, prop, targetType)
                if (!prop.list) {
                    addReferenceFetchTypeProp(type, prop, targetType)
                }
                addRecursiveProp(type, prop, targetType, withConfig = false)
                addRecursiveProp(type, prop, targetType, withConfig = true)
            }
        } else if (targetType?.kind == ImmutableTypeKind.EMBEDDABLE) {
            addChildProp(type, prop, targetType)
        }
    }

    private fun TypeSpec.Builder.addSimpleProp(
        type: ImmutableType,
        prop: ImmutableProp,
    ) {
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .returns(type.fetcherClassName())
                .addStatement("return add(\$S)", prop.name)
                .apply { prop.fetcherDocumentation()?.let { documentation -> addJavadoc("\$L", documentation) } }
                .build()
        )
    }

    private fun TypeSpec.Builder.addEnabledProp(
        type: ImmutableType,
        prop: ImmutableProp,
    ) {
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addParameter(Boolean::class.javaPrimitiveType, "enabled")
                .returns(type.fetcherClassName())
                .addStatement("return enabled ? add(\$S) : remove(\$S)", prop.name, prop.name)
                .apply { prop.fetcherDocumentation()?.let { documentation -> addJavadoc("\$L", documentation) } }
                .build()
        )
    }

    private fun TypeSpec.Builder.addChildProp(
        type: ImmutableType,
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ) {
        val targetClass = targetType.requiredClassName(prop)
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addParameter(ParameterizedTypeName.get(FETCHER, targetClass), "childFetcher")
                .returns(type.fetcherClassName())
                .addStatement("return add(\$S, childFetcher)", prop.name)
                .build()
        )
    }

    private fun TypeSpec.Builder.addIdOnlyProp(
        schema: ImmutableSchema,
        type: ImmutableType,
        prop: ImmutableProp,
    ) {
        val associationProp = schema.idViewBasePropOrSelf(prop)
        if (
            associationProp.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isConcreteEntityAssociation(associationProp) ||
            prop.reverse ||
            prop.list ||
            prop.hasAnnotation(JOIN_TABLE_ANNOTATION)
        ) {
            return
        }
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addParameter(ID_ONLY_FETCH_TYPE, "idOnlyFetchType")
                .returns(type.fetcherClassName())
                .addStatement("return add(\$S, idOnlyFetchType)", prop.name)
                .build()
        )
    }

    private fun TypeSpec.Builder.addFieldConfigProp(
        type: ImmutableType,
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ) {
        val targetClass = targetType.requiredClassName(prop)
        val fieldConfigClass = if (prop.list) LIST_FIELD_CONFIG else REFERENCE_FIELD_CONFIG
        val fieldConfigType = ParameterizedTypeName.get(
            fieldConfigClass,
            targetClass,
            targetType.requiredTableClassName(prop),
        )
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addParameter(ParameterizedTypeName.get(FETCHER, targetClass), "childFetcher")
                .addParameter(ParameterizedTypeName.get(CONSUMER, fieldConfigType), "fieldConfig")
                .returns(type.fetcherClassName())
                .addStatement("return add(\$S, childFetcher, fieldConfig)", prop.name)
                .build()
        )
    }

    private fun TypeSpec.Builder.addReferenceFetchTypeProp(
        type: ImmutableType,
        prop: ImmutableProp,
        targetType: ImmutableType?,
    ) {
        addMethod(
            MethodSpec.methodBuilder(prop.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(NEW_CHAIN)
                .addParameter(REFERENCE_FETCH_TYPE, "fetchType")
                .addParameter(
                    ParameterizedTypeName.get(FETCHER, targetType.requiredClassName(prop)),
                    "childFetcher",
                )
                .returns(type.fetcherClassName())
                .addStatement("return \$L(childFetcher, cfg -> cfg.fetchType(fetchType))", prop.name)
                .build()
        )
    }

    private fun TypeSpec.Builder.addRecursiveProp(
        type: ImmutableType,
        prop: ImmutableProp,
        targetType: ImmutableType?,
        withConfig: Boolean,
    ) {
        if (!prop.recursive) {
            return
        }
        val configClass = if (prop.list) RECURSIVE_LIST_FIELD_CONFIG else RECURSIVE_REFERENCE_FIELD_CONFIG
        val builder = MethodSpec.methodBuilder(StringUtil.identifier("recursive", prop.name))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(NEW_CHAIN)
        if (withConfig) {
            val configType = ParameterizedTypeName.get(
                configClass,
                targetType.requiredClassName(prop),
                targetType.requiredTableClassName(prop),
            )
            builder.addParameter(ParameterizedTypeName.get(CONSUMER, configType), "fieldConfig")
        }
        addMethod(
            builder
                .returns(type.fetcherClassName())
                .addStatement(
                    "return addRecursion(\$S, \$L)",
                    prop.name,
                    if (withConfig) "fieldConfig" else "null",
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addNegativeConstructor(type: ImmutableType) {
        addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(type.fetcherClassName(), "prev")
                .addParameter(IMMUTABLE_PROP, "prop")
                .addParameter(Boolean::class.javaPrimitiveType, "negative")
                .addParameter(ID_ONLY_FETCH_TYPE, "idOnlyFetchType")
                .addStatement("super(prev, prop, negative, idOnlyFetchType)")
                .build()
        )
    }

    private fun TypeSpec.Builder.addFieldConfigConstructor(type: ImmutableType) {
        addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(type.fetcherClassName(), "prev")
                .addParameter(IMMUTABLE_PROP, "prop")
                .addParameter(fieldConfigWildcardType(), "fieldConfig")
                .addStatement("super(prev, prop, fieldConfig)")
                .build()
        )
    }

    private fun TypeSpec.Builder.addTypeBranchConstructor(type: ImmutableType) {
        addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(type.fetcherClassName(), "prev")
                .addParameter(ParameterizedTypeName.get(FETCHER_IMPL, WildcardTypeName.subtypeOf(Any::class.java)), "typeBranchFetcher")
                .addStatement("super(prev, typeBranchFetcher)")
                .build()
        )
    }

    private fun TypeSpec.Builder.addNegativeCreator(type: ImmutableType) {
        addMethod(
            MethodSpec.methodBuilder("createFetcher")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(IMMUTABLE_PROP, "prop")
                .addParameter(Boolean::class.javaPrimitiveType, "negative")
                .addParameter(ID_ONLY_FETCH_TYPE, "idOnlyFetchType")
                .returns(type.fetcherClassName())
                .addAnnotation(Override::class.java)
                .addStatement("return new \$T(this, prop, negative, idOnlyFetchType)", type.fetcherClassName())
                .build()
        )
    }

    private fun TypeSpec.Builder.addFieldConfigCreator(type: ImmutableType) {
        addMethod(
            MethodSpec.methodBuilder("createFetcher")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(IMMUTABLE_PROP, "prop")
                .addParameter(fieldConfigWildcardType(), "fieldConfig")
                .returns(type.fetcherClassName())
                .addAnnotation(Override::class.java)
                .addStatement("return new \$T(this, prop, fieldConfig)", type.fetcherClassName())
                .build()
        )
    }

    private fun TypeSpec.Builder.addTypeBranchCreator(type: ImmutableType) {
        addMethod(
            MethodSpec.methodBuilder("createFetcher")
                .addModifiers(Modifier.PROTECTED)
                .addParameter(ParameterizedTypeName.get(FETCHER_IMPL, WildcardTypeName.subtypeOf(Any::class.java)), "typeBranchFetcher")
                .returns(type.fetcherClassName())
                .addAnnotation(Override::class.java)
                .addStatement("return new \$T(this, typeBranchFetcher)", type.fetcherClassName())
                .build()
        )
    }

    private fun fieldConfigWildcardType(): ParameterizedTypeName {
        val tableType = ParameterizedTypeName.get(TABLE, WildcardTypeName.subtypeOf(Any::class.java))
        return ParameterizedTypeName.get(
            FIELD_CONFIG,
            WildcardTypeName.subtypeOf(Any::class.java),
            WildcardTypeName.subtypeOf(tableType),
        )
    }
}

private fun ImmutableType.className(): ClassName = ClassName.bestGuess(qualifiedName)

private fun ImmutableType.fetcherClassName(): ClassName = ClassName.get(packageName, "${simpleName}Fetcher")

private fun ImmutableType.tableClassName(): ClassName = ClassName.get(packageName, "${simpleName}Table")

private fun ImmutableType.generatedAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(GENERATED_BY)
        .addMember("type", "\$T.class", className())
        .build()
}

private fun ImmutableProp.fetcherDocumentation(): String? {
    return documentation?.let(Doc::parse)?.value
}

private fun ImmutableType?.requiredClassName(prop: ImmutableProp): ClassName {
    return this?.className() ?: throw ImmutablePrecompileException(
        declarationId = prop.declarationId,
        recoverable = true,
        message = "Cannot resolve fetcher target type of immutable property '${prop.id.value}'",
    )
}

private fun ImmutableType?.requiredTableClassName(prop: ImmutableProp): ClassName {
    return this?.tableClassName() ?: throw ImmutablePrecompileException(
        declarationId = prop.declarationId,
        recoverable = true,
        message = "Cannot resolve fetcher table type of immutable property '${prop.id.value}'",
    )
}

private val GENERATED_BY = ClassName.get("org.babyfish.jimmer.internal", "GeneratedBy")
private val NEW_CHAIN = ClassName.get("org.babyfish.jimmer.lang", "NewChain")
private val ABSTRACT_TYPED_FETCHER = ClassName.get("org.babyfish.jimmer.sql.fetcher.spi", "AbstractTypedFetcher")
private val FETCHER = ClassName.get("org.babyfish.jimmer.sql.fetcher", "Fetcher")
private val FETCHER_IMPL = ClassName.get("org.babyfish.jimmer.sql.fetcher.impl", "FetcherImpl")
private val ID_ONLY_FETCH_TYPE = ClassName.get("org.babyfish.jimmer.sql.fetcher", "IdOnlyFetchType")
private val REFERENCE_FETCH_TYPE = ClassName.get("org.babyfish.jimmer.sql.fetcher", "ReferenceFetchType")
private val FIELD_CONFIG = ClassName.get("org.babyfish.jimmer.sql.fetcher", "FieldConfig")
private val REFERENCE_FIELD_CONFIG = ClassName.get("org.babyfish.jimmer.sql.fetcher", "ReferenceFieldConfig")
private val LIST_FIELD_CONFIG = ClassName.get("org.babyfish.jimmer.sql.fetcher", "ListFieldConfig")
private val RECURSIVE_REFERENCE_FIELD_CONFIG =
    ClassName.get("org.babyfish.jimmer.sql.fetcher", "RecursiveReferenceFieldConfig")
private val RECURSIVE_LIST_FIELD_CONFIG =
    ClassName.get("org.babyfish.jimmer.sql.fetcher", "RecursiveListFieldConfig")
private val TABLE = ClassName.get("org.babyfish.jimmer.sql.ast.table", "Table")
private val IMMUTABLE_PROP = ClassName.get("org.babyfish.jimmer.meta", "ImmutableProp")
private val CONSUMER = ClassName.get(Consumer::class.java)
private val JOIN_TABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinTable")
