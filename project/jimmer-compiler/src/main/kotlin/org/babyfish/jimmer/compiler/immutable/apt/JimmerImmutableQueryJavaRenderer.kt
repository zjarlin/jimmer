package org.babyfish.jimmer.compiler.immutable.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.compiler.immutable.associatedIdPropName
import org.babyfish.jimmer.compiler.immutable.expressionKind
import org.babyfish.jimmer.compiler.immutable.fieldName
import org.babyfish.jimmer.compiler.immutable.isBranchDependent
import org.babyfish.jimmer.compiler.immutable.isDsl
import org.babyfish.jimmer.compiler.immutable.orderedProps
import org.babyfish.jimmer.compiler.immutable.propsMethodProps
import org.babyfish.jimmer.compiler.immutable.propsSuperTypes
import org.babyfish.jimmer.compiler.immutable.inheritanceArtifactAggregationMode
import org.babyfish.jimmer.compiler.immutable.inheritanceArtifactOriginatingSymbols
import org.babyfish.jimmer.compiler.immutable.typedPropKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePropExpressionKind
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypedPropKind
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.simpleName
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.primaryLineageOwner
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
import site.addzero.lsi.jimmer.targetIdPropOf
import site.addzero.lsi.jimmer.targetTypeOf
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeSystem

class JimmerImmutableQueryJavaRenderer {

    fun render(
        schema: ImmutableSchema,
        type: ImmutableType,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        require(type.kind in QUERY_TYPE_KINDS) {
            "Java immutable query renderer does not support type '${type.id.value}'"
        }
        require(type.typeParameterIds.isEmpty()) {
            "Java immutable query renderer does not support generic type '${type.id.value}'"
        }
        return QueryJavaRenderContext(schema, type, workspace).render()
    }
}

private class QueryJavaRenderContext(
    private val schema: ImmutableSchema,
    private val type: ImmutableType,
    private val workspace: LsiWorkspace,
) {

    private val typeSystem = LsiTypeSystem(workspace)

    private val modelClass = ClassName.bestGuess(type.qualifiedName)

    private val propsClass = ClassName.get(type.packageName, "${type.simpleName}Props")

    private val tableClass = ClassName.get(type.packageName, "${type.simpleName}Table")

    private val tableExClass = ClassName.get(type.packageName, "${type.simpleName}TableEx")

    fun render(): List<GeneratedArtifact> {
        val propsOriginatingSymbols = setOf(type.id)
        val artifacts = mutableListOf(
            sourceArtifact(
                qualifiedName = propsClass.canonicalName(),
                content = javaFile(propsType()),
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                originatingSymbols = propsOriginatingSymbols,
            )
        )
        if (type.kind != ImmutableTypeKind.ENTITY) {
            return artifacts
        }
        val tableOriginatingSymbols = schema.inheritanceArtifactOriginatingSymbols(type)
        artifacts += sourceArtifact(
            qualifiedName = tableClass.canonicalName(),
            content = javaFile(tableType(tableEx = false)),
            aggregationMode = schema.inheritanceArtifactAggregationMode(type),
            emissionMode = if (schema.isBranchDependent(type)) {
                ArtifactEmissionMode.STABLE
            } else {
                ArtifactEmissionMode.IMMEDIATE
            },
            originatingSymbols = tableOriginatingSymbols,
        )
        artifacts += sourceArtifact(
            qualifiedName = tableExClass.canonicalName(),
            content = javaFile(tableType(tableEx = true)),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = propsOriginatingSymbols,
        )
        return artifacts
    }

    private fun sourceArtifact(
        qualifiedName: String,
        content: String,
        aggregationMode: ArtifactAggregationMode,
        emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
        originatingSymbols: Set<LsiSymbolId>,
    ): GeneratedArtifact {
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = aggregationMode,
            emissionMode = emissionMode,
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }

    private fun javaFile(generatedType: TypeSpec): String {
        return JavaFile.builder(type.packageName, generatedType)
            .indent("    ")
            .build()
            .toString()
    }

    private fun propsType(): TypeSpec {
        return TypeSpec.interfaceBuilder(propsClass.simpleName())
            .addAnnotation(generatedAnnotation())
            .addModifiers(Modifier.PUBLIC)
            .apply {
                if (type.kind in SQL_QUERY_TYPE_KINDS) {
                    addAnnotation(
                        AnnotationSpec.builder(PROPS_FOR)
                            .addMember("value", "\$T.class", modelClass)
                            .build()
                    )
                }
                val superTypes = schema.propsSuperTypes(type)
                if (superTypes.isEmpty()) {
                    if (type.kind in SQL_QUERY_TYPE_KINDS) {
                        addSuperinterface(PROPS)
                    }
                } else {
                    superTypes.forEach { superType ->
                        addSuperinterface(superType.propsClassName())
                    }
                }
                if (type.kind == ImmutableTypeKind.ENTITY) {
                    addSuperinterface(ParameterizedTypeName.get(SELECTION, modelClass))
                }
                schema.orderedProps(type).forEach { prop -> addField(typedPropField(prop)) }
                if (type.kind in SQL_QUERY_TYPE_KINDS) {
                    schema.propsMethodProps(type).forEach { prop ->
                        if (schema.isDsl(prop, workspace, tableEx = false)) {
                            addMethod(
                                requireNotNull(
                                    propertyMethod(
                                        prop,
                                        tableEx = false,
                                        withJoinType = false,
                                        withImplementation = false,
                                    )
                                )
                            )
                            propertyMethod(
                                prop,
                                tableEx = false,
                                withJoinType = true,
                                withImplementation = false,
                            )?.let(::addMethod)
                        }
                        existsMethod(prop, withImplementation = false)?.let(::addMethod)
                        associatedIdMethod(prop, tableEx = false, withImplementation = false)?.let(::addMethod)
                    }
                }
            }
            .build()
    }

    private fun typedPropField(prop: ImmutableProp): FieldSpec {
        val kind = schema.typedPropKind(prop)
        return FieldSpec.builder(
            ParameterizedTypeName.get(
                kind.typedPropClassName(),
                modelClass,
                prop.elementTypeOrSelf().toJavaTypeName().box(),
            ),
            prop.fieldName(),
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
        )
            .initializer(
                "\n    \$T.\$L(\$T.get(\$T.class).getProp(\$S))",
                TYPED_PROP,
                kind.factoryName(),
                IMMUTABLE_TYPE,
                modelClass,
                prop.name,
            )
            .build()
    }

    private fun tableType(tableEx: Boolean): TypeSpec {
        val selfClass = if (tableEx) tableExClass else tableClass
        return TypeSpec.classBuilder(selfClass.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generatedAnnotation())
            .apply {
                if (tableEx) {
                    superclass(tableClass)
                    addSuperinterface(
                        ParameterizedTypeName.get(
                            TABLE_EX_PROXY,
                            modelClass,
                            tableClass,
                        )
                    )
                } else {
                    superclass(ParameterizedTypeName.get(ABSTRACT_TYPED_TABLE, modelClass))
                    addSuperinterface(propsClass)
                    if (schema.strictPrimarySubtypesOf(type).isNotEmpty()) {
                        addSuperinterface(ParameterizedTypeName.get(POLYMORPHIC_TABLE, modelClass))
                    }
                }
                addField(instanceField(tableEx))
                addMethod(defaultConstructor(tableEx))
                addMethod(delayedConstructor(tableEx))
                addMethod(wrapperConstructor())
                addMethod(disableJoinConstructor())
                addMethod(baseTableOwnerConstructor())
                schema.orderedProps(type).forEach { prop ->
                    if (schema.isDsl(prop, workspace, tableEx)) {
                        addMethod(
                            requireNotNull(
                                propertyMethod(
                                    prop,
                                    tableEx,
                                    withJoinType = false,
                                    withImplementation = true,
                                )
                            )
                        )
                        propertyMethod(
                            prop,
                            tableEx,
                            withJoinType = true,
                            withImplementation = true,
                        )?.let(::addMethod)
                    }
                    existsMethod(prop, withImplementation = true)?.let(::addMethod)
                    associatedIdMethod(prop, tableEx, withImplementation = true)?.let(::addMethod)
                }
                addMethod(asTableExMethod(tableEx))
                addMethod(disableJoinMethod(selfClass))
                addMethod(baseTableOwnerMethod(selfClass))
                if (!tableEx) {
                    addPolymorphicMethods()
                }
                addWeakJoinMethods(tableEx)
                if (!tableEx) {
                    addType(remoteType())
                }
            }
            .build()
    }

    private fun instanceField(tableEx: Boolean): FieldSpec {
        val selfClass = if (tableEx) tableExClass else tableClass
        return FieldSpec.builder(
            selfClass,
            "\$",
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
        )
            .apply {
                if (tableEx) {
                    initializer("new \$T(\$T.\$L, (String)null)", selfClass, tableClass, "\$")
                } else {
                    initializer("new \$T()", selfClass)
                }
            }
            .build()
    }

    private fun defaultConstructor(tableEx: Boolean): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .apply {
                if (tableEx) {
                    addStatement("super()")
                } else {
                    addStatement("super(\$T.class)", modelClass)
                }
            }
            .build()
    }

    private fun delayedConstructor(tableEx: Boolean): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(DELAYED_OPERATION, modelClass), "delayedOperation")
            .apply {
                if (tableEx) {
                    addStatement("super(delayedOperation)")
                } else {
                    addStatement("super(\$T.class, delayedOperation)", modelClass)
                }
            }
            .build()
    }

    private fun wrapperConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(TABLE_IMPLEMENTOR, modelClass), "table")
            .addStatement("super(table)")
            .build()
    }

    private fun disableJoinConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PROTECTED)
            .addParameter(tableClass, "base")
            .addParameter(STRING, "joinDisabledReason")
            .addStatement("super(base, joinDisabledReason)")
            .build()
    }

    private fun baseTableOwnerConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PROTECTED)
            .addParameter(tableClass, "base")
            .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
            .addStatement("super(base, baseTableOwner)")
            .build()
    }

    private fun propertyMethod(
        prop: ImmutableProp,
        tableEx: Boolean,
        withJoinType: Boolean,
        withImplementation: Boolean,
    ): MethodSpec? {
        val entityAssociation = schema.isEntityAssociation(prop)
        if (withJoinType && !entityAssociation) {
            return null
        }
        val returnType = propertyReturnType(prop, tableEx)
        return MethodSpec.methodBuilder(prop.name)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .apply {
                prop.documentation?.let(Doc::parse)?.value?.let { documentation ->
                    addJavadoc("\$L", documentation)
                }
                if (withImplementation) {
                    if (!tableEx) {
                        addAnnotation(OVERRIDE)
                    }
                } else {
                    addModifiers(Modifier.ABSTRACT)
                }
                if (withJoinType) {
                    addParameter(JOIN_TYPE, "joinType")
                }
                if (withImplementation) {
                    addPropertyImplementation(prop, returnType, withJoinType)
                }
            }
            .build()
    }

    private fun MethodSpec.Builder.addPropertyImplementation(
        prop: ImmutableProp,
        returnType: TypeName,
        withJoinType: Boolean,
    ) {
        val runtimePropsClass = schema.primaryLineageOwner(type, prop).propsClassName()
        if (schema.isEntityAssociation(prop)) {
            addStatement("__beforeJoin()")
            if (withJoinType) {
                beginControlFlow("if (raw != null)")
                    .addStatement(
                        "return new \$T(raw.joinImplementor(\$T.\$L.unwrap(), joinType))",
                        returnType,
                        runtimePropsClass,
                        prop.fieldName(),
                    )
                    .endControlFlow()
                    .addStatement(
                        "return new \$T(joinOperation(\$T.\$L.unwrap(), joinType))",
                        returnType,
                        runtimePropsClass,
                        prop.fieldName(),
                    )
            } else {
                beginControlFlow("if (raw != null)")
                    .addStatement(
                        "return new \$T(raw.joinImplementor(\$T.\$L.unwrap()))",
                        returnType,
                        runtimePropsClass,
                        prop.fieldName(),
                    )
                    .endControlFlow()
                    .addStatement(
                        "return new \$T(joinOperation(\$T.\$L.unwrap()))",
                        returnType,
                        runtimePropsClass,
                        prop.fieldName(),
                    )
            }
            return
        }
        if (schema.targetTypeOf(prop) != null) {
            addStatement(
                "return new \$T(__get(\$T.\$L.unwrap()))",
                returnType,
                runtimePropsClass,
                prop.fieldName(),
            )
            return
        }
        addStatement(
            "return __get(\$T.\$L.unwrap())",
            runtimePropsClass,
            prop.fieldName(),
        )
    }

    private fun propertyReturnType(
        prop: ImmutableProp,
        tableEx: Boolean,
    ): TypeName {
        val targetType = schema.targetTypeOf(prop)
        if (schema.isEntityAssociation(prop)) {
            val target = requireNotNull(targetType) {
                "Entity association '${prop.id.value}' must have a concrete target"
            }
            return when {
                prop.remote -> target.remoteTableClassName()
                tableEx -> target.tableExClassName()
                else -> target.tableClassName()
            }
        }
        if (targetType != null) {
            return targetType.propExpressionClassName()
        }
        return prop.expressionTypeName(typeSystem)
    }

    private fun existsMethod(
        prop: ImmutableProp,
        withImplementation: Boolean,
    ): MethodSpec? {
        if (!schema.isEntityAssociation(prop) || !prop.list) {
            return null
        }
        val targetType = requireNotNull(schema.targetTypeOf(prop)) {
            "List association '${prop.id.value}' must have a concrete target"
        }
        return MethodSpec.methodBuilder(prop.name)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(
                ParameterizedTypeName.get(
                    FUNCTION,
                    targetType.tableExClassName(),
                    PREDICATE,
                ),
                "block",
            )
            .returns(PREDICATE)
            .apply {
                if (withImplementation) {
                    addAnnotation(OVERRIDE)
                    val runtimePropsClass = schema.primaryLineageOwner(type, prop).propsClassName()
                    addStatement(
                        "return exists(\$T.\$L.unwrap(), block)",
                        runtimePropsClass,
                        prop.fieldName(),
                    )
                } else {
                    addModifiers(Modifier.ABSTRACT)
                }
            }
            .build()
    }

    private fun associatedIdMethod(
        prop: ImmutableProp,
        tableEx: Boolean,
        withImplementation: Boolean,
    ): MethodSpec? {
        val methodName = schema.associatedIdPropName(type, prop) ?: return null
        if (
            prop.primaryMapping == PrimaryMapping.TRANSIENT ||
            !schema.isEntityAssociation(prop) ||
            prop.list != tableEx
        ) {
            return null
        }
        val targetIdProp = requireNotNull(schema.targetIdPropOf(prop)) {
            "Association '${prop.id.value}' must target an entity with an id property"
        }
        return MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(targetIdProp.expressionTypeName(typeSystem))
            .apply {
                if (withImplementation) {
                    if (!tableEx) {
                        addAnnotation(OVERRIDE)
                    }
                    val runtimePropsClass = schema.primaryLineageOwner(type, prop).propsClassName()
                    addStatement(
                        "return __getAssociatedId(\$T.\$L.unwrap())",
                        runtimePropsClass,
                        prop.fieldName(),
                    )
                } else {
                    addModifiers(Modifier.ABSTRACT)
                }
            }
            .build()
    }

    private fun asTableExMethod(tableEx: Boolean): MethodSpec {
        return MethodSpec.methodBuilder("asTableEx")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .returns(tableExClass)
            .apply {
                if (tableEx) {
                    addStatement("return this")
                } else {
                    addStatement("return new \$T(this, (String)null)", tableExClass)
                }
            }
            .build()
    }

    private fun disableJoinMethod(selfClass: ClassName): MethodSpec {
        return MethodSpec.methodBuilder("__disableJoin")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .returns(selfClass)
            .addParameter(STRING, "reason")
            .addStatement("return new \$T(this, reason)", selfClass)
            .build()
    }

    private fun baseTableOwnerMethod(selfClass: ClassName): MethodSpec {
        return MethodSpec.methodBuilder("__baseTableOwner")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .returns(selfClass)
            .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
            .addStatement("return new \$T(this, baseTableOwner)", selfClass)
            .build()
    }

    private fun TypeSpec.Builder.addPolymorphicMethods() {
        if (schema.strictPrimarySubtypesOf(type).isEmpty()) {
            return
        }
        addMethod(treatAsMethod(optional = false))
        addMethod(treatAsMethod(optional = true))
        addMethod(instanceOfMethod())
        addMethod(exactTypeMethod())
    }

    private fun treatAsMethod(optional: Boolean): MethodSpec {
        val tableTypeVariable = TypeVariableName.get(
            "TT",
            ParameterizedTypeName.get(TABLE, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
        )
        return MethodSpec.methodBuilder(if (optional) "tryTreatAs" else "treatAs")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .addAnnotation(suppressAllAnnotation())
            .addTypeVariable(tableTypeVariable)
            .returns(tableTypeVariable)
            .addParameter(ParameterizedTypeName.get(CLASS, tableTypeVariable), "tableType")
            .addStatement("\$T treatedAs = \$T.tableType(tableType)", IMMUTABLE_TYPE, TABLE_PROXIES)
            .addStatement("__beforeJoin()")
            .beginControlFlow("if (raw != null)")
            .addStatement(
                "return (TT)\$T.wrap(raw.treatAsImplementor(treatedAs, \$T.\$L))",
                TABLE_PROXIES,
                JOIN_TYPE,
                if (optional) "LEFT" else "INNER",
            )
            .endControlFlow()
            .addStatement(
                "return (TT)\$T.fluent(treatAsOperation(treatedAs, \$T.\$L))",
                TABLE_PROXIES,
                JOIN_TYPE,
                if (optional) "LEFT" else "INNER",
            )
            .build()
    }

    private fun instanceOfMethod(): MethodSpec {
        return MethodSpec.methodBuilder("instanceOf")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .returns(PREDICATE)
            .addParameter(
                ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(modelClass)),
                "type",
            )
            .addStatement("return \$T.instanceOf(this, type)", TABLE_PROXIES)
            .build()
    }

    private fun exactTypeMethod(): MethodSpec {
        return MethodSpec.methodBuilder("exactType")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .returns(PREDICATE)
            .addParameter(
                ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(modelClass)),
                "type",
            )
            .addStatement("return \$T.exactType(this, type)", TABLE_PROXIES)
            .build()
    }

    private fun TypeSpec.Builder.addWeakJoinMethods(tableEx: Boolean) {
        if (!tableEx) {
            return
        }
        addMethod(weakJoinMethod(withJoinType = false))
        addMethod(weakJoinMethod(withJoinType = true))
        addMethod(lambdaWeakJoinMethod(withJoinType = false))
        addMethod(lambdaWeakJoinMethod(withJoinType = true))
        addMethod(baseTableWeakJoinMethod(withJoinType = false))
        addMethod(baseTableWeakJoinMethod(withJoinType = true))
    }

    private fun weakJoinMethod(withJoinType: Boolean): MethodSpec {
        val tableTypeVariable = TypeVariableName.get(
            "TT",
            ParameterizedTypeName.get(TABLE, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
        )
        val weakJoinTypeVariable = TypeVariableName.get(
            "WJ",
            ParameterizedTypeName.get(WEAK_JOIN, tableClass, tableTypeVariable),
        )
        return MethodSpec.methodBuilder("weakJoin")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(tableTypeVariable)
            .addTypeVariable(weakJoinTypeVariable)
            .returns(tableTypeVariable)
            .addParameter(ParameterizedTypeName.get(CLASS, weakJoinTypeVariable), "weakJoinType")
            .apply {
                if (withJoinType) {
                    addParameter(JOIN_TYPE, "joinType")
                    addAnnotation(suppressAllAnnotation())
                    addStatement("__beforeJoin()")
                    beginControlFlow("if (raw != null)")
                        .addStatement(
                            "return (TT)\$T.wrap(raw.weakJoinImplementor(weakJoinType, joinType))",
                            TABLE_PROXIES,
                        )
                        .endControlFlow()
                        .addStatement(
                            "return (TT)\$T.fluent(joinOperation(weakJoinType, joinType))",
                            TABLE_PROXIES,
                        )
                } else {
                    addStatement("return weakJoin(weakJoinType, JoinType.INNER)")
                }
            }
            .build()
    }

    private fun lambdaWeakJoinMethod(withJoinType: Boolean): MethodSpec {
        val tableTypeVariable = TypeVariableName.get(
            "TT",
            ParameterizedTypeName.get(TABLE, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
        )
        return MethodSpec.methodBuilder("weakJoin")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(tableTypeVariable)
            .returns(tableTypeVariable)
            .addParameter(ParameterizedTypeName.get(CLASS, tableTypeVariable), "targetTableType")
            .apply {
                if (withJoinType) {
                    addParameter(JOIN_TYPE, "joinType")
                    addAnnotation(suppressAllAnnotation())
                }
                addParameter(
                    ParameterizedTypeName.get(WEAK_JOIN, tableClass, tableTypeVariable),
                    "weakJoinLambda",
                )
                if (withJoinType) {
                    addStatement("__beforeJoin()")
                    beginControlFlow("if (raw != null)")
                        .addStatement(
                            "return (TT)\$T.wrap(raw.weakJoinImplementor(targetTableType, joinType, weakJoinLambda))",
                            TABLE_PROXIES,
                        )
                        .endControlFlow()
                        .addStatement(
                            "return (TT)\$T.fluent(joinOperation(targetTableType, joinType, weakJoinLambda))",
                            TABLE_PROXIES,
                        )
                } else {
                    addStatement("return weakJoin(targetTableType, JoinType.INNER, weakJoinLambda)")
                }
            }
            .build()
    }

    private fun baseTableWeakJoinMethod(withJoinType: Boolean): MethodSpec {
        val tableTypeVariable = TypeVariableName.get("TT", BASE_TABLE)
        return MethodSpec.methodBuilder("weakJoin")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(tableTypeVariable)
            .returns(tableTypeVariable)
            .addParameter(tableTypeVariable, "targetBaseTable")
            .apply {
                if (withJoinType) {
                    addParameter(JOIN_TYPE, "joinType")
                }
                addParameter(
                    ParameterizedTypeName.get(WEAK_JOIN, tableClass, tableTypeVariable),
                    "weakJoinLambda",
                )
                if (withJoinType) {
                    addCode(baseTableWeakJoinBody())
                } else {
                    addStatement("return weakJoin(targetBaseTable, \$T.INNER, weakJoinLambda)", JOIN_TYPE)
                }
            }
            .build()
    }

    private fun baseTableWeakJoinBody(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("\$T lambda = \$T.get(weakJoinLambda)", WEAK_JOIN_LAMBDA, J_WEAK_JOIN_LAMBDA_FACTORY)
            .add("\$T handle = \$T.of(\$>\n", WEAK_JOIN_HANDLE, WEAK_JOIN_HANDLE)
            .add("lambda,\n")
            .add("true,\n")
            .add("true,\n")
            .add(
                "(\$T)(\$T) weakJoinLambda\n\$<",
                ParameterizedTypeName.get(
                    WEAK_JOIN,
                    ParameterizedTypeName.get(TABLE_LIKE, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
                    ParameterizedTypeName.get(TABLE_LIKE, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
                ),
                ParameterizedTypeName.get(
                    WEAK_JOIN,
                    WildcardTypeName.subtypeOf(TypeName.OBJECT),
                    WildcardTypeName.subtypeOf(TypeName.OBJECT),
                ),
            )
            .addStatement(")")
            .addStatement(
                "return (\$T) \$T.of((\$T) targetBaseTable, this, handle, joinType)",
                TypeVariableName.get("TT"),
                BASE_TABLE_SYMBOLS,
                BASE_TABLE_SYMBOL,
            )
            .build()
    }

    private fun remoteType(): TypeSpec {
        val remoteClass = tableClass.nestedClass("Remote")
        val idProp = type.idPropId?.let(schema.propsById::get)
            ?: error("Entity immutable type '${type.id.value}' must declare an id property")
        return TypeSpec.classBuilder(remoteClass.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(generatedAnnotation())
            .superclass(ParameterizedTypeName.get(ABSTRACT_TYPED_TABLE, modelClass))
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(DELAYED_OPERATION, "delayedOperation")
                    .addStatement("super(\$T.class, delayedOperation)", modelClass)
                    .build()
            )
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ParameterizedTypeName.get(TABLE_IMPLEMENTOR, modelClass), "table")
                    .addStatement("super(table)")
                    .build()
            )
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(remoteClass, "base")
                    .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
                    .addStatement("super(base, baseTableOwner)")
                    .build()
            )
            .addMethod(remoteIdMethod(idProp))
            .addMethod(remoteAsTableExMethod())
            .addMethod(remoteDisableJoinMethod(remoteClass))
            .addMethod(remoteBaseTableOwnerMethod(remoteClass))
            .build()
    }

    private fun remoteIdMethod(idProp: ImmutableProp): MethodSpec {
        val returnType = propertyReturnType(idProp, tableEx = false)
        return MethodSpec.methodBuilder(idProp.name)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addStatement(
                "return (\$L)this.<\$T>get(\$T.\$L.unwrap())",
                returnType,
                idProp.type.toJavaTypeName().box(),
                propsClass,
                idProp.fieldName(),
            )
            .build()
    }

    private fun remoteAsTableExMethod(): MethodSpec {
        return MethodSpec.methodBuilder("asTableEx")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .addAnnotation(DEPRECATED)
            .returns(ParameterizedTypeName.get(TABLE_EX, modelClass))
            .addStatement("throw new UnsupportedOperationException()")
            .build()
    }

    private fun remoteDisableJoinMethod(remoteClass: ClassName): MethodSpec {
        return MethodSpec.methodBuilder("__disableJoin")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .addParameter(STRING, "reason")
            .returns(remoteClass)
            .addStatement("return this")
            .build()
    }

    private fun remoteBaseTableOwnerMethod(remoteClass: ClassName): MethodSpec {
        return MethodSpec.methodBuilder("__baseTableOwner")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OVERRIDE)
            .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
            .returns(remoteClass)
            .addStatement("return new Remote(this, baseTableOwner)")
            .build()
    }

    private fun generatedAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(GENERATED_BY)
            .addMember("type", "\$T.class", modelClass)
            .build()
    }
}

private fun ImmutableProp.expressionTypeName(typeSystem: LsiTypeSystem): TypeName {
    val boxedType = type.toJavaTypeName().box()
    return when (expressionKind(typeSystem)) {
        JimmerImmutablePropExpressionKind.GENERIC -> ParameterizedTypeName.get(PROP_EXPRESSION, boxedType)
        JimmerImmutablePropExpressionKind.NUMERIC -> ParameterizedTypeName.get(PROP_NUMERIC_EXPRESSION, boxedType)
        JimmerImmutablePropExpressionKind.STRING -> PROP_STRING_EXPRESSION
        JimmerImmutablePropExpressionKind.DATE -> ParameterizedTypeName.get(PROP_DATE_EXPRESSION, boxedType)
        JimmerImmutablePropExpressionKind.TEMPORAL -> ParameterizedTypeName.get(PROP_TEMPORAL_EXPRESSION, boxedType)
        JimmerImmutablePropExpressionKind.COMPARABLE -> ParameterizedTypeName.get(
            PROP_COMPARABLE_EXPRESSION,
            boxedType,
        )
    }
}

private fun JimmerImmutableTypedPropKind.typedPropClassName(): ClassName {
    return when (this) {
        JimmerImmutableTypedPropKind.SCALAR -> TYPED_PROP_SCALAR
        JimmerImmutableTypedPropKind.SCALAR_LIST -> TYPED_PROP_SCALAR_LIST
        JimmerImmutableTypedPropKind.REFERENCE -> TYPED_PROP_REFERENCE
        JimmerImmutableTypedPropKind.REFERENCE_LIST -> TYPED_PROP_REFERENCE_LIST
    }
}

private fun JimmerImmutableTypedPropKind.factoryName(): String {
    return when (this) {
        JimmerImmutableTypedPropKind.SCALAR -> "scalar"
        JimmerImmutableTypedPropKind.SCALAR_LIST -> "scalarList"
        JimmerImmutableTypedPropKind.REFERENCE -> "reference"
        JimmerImmutableTypedPropKind.REFERENCE_LIST -> "referenceList"
    }
}

private fun ImmutableType.propsClassName(): ClassName = ClassName.get(packageName, "${simpleName}Props")

private fun ImmutableType.tableClassName(): ClassName = ClassName.get(packageName, "${simpleName}Table")

private fun ImmutableType.tableExClassName(): ClassName = ClassName.get(packageName, "${simpleName}TableEx")

private fun ImmutableType.remoteTableClassName(): ClassName = tableClassName().nestedClass("Remote")

private fun ImmutableType.propExpressionClassName(): ClassName =
    ClassName.get(packageName, "${simpleName}PropExpression")

private fun suppressAllAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(SUPPRESS_WARNINGS)
        .addMember("value", "\$S", "all")
        .build()
}

private val QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.IMMUTABLE,
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val SQL_QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val GENERATED_BY = ClassName.get("org.babyfish.jimmer.internal", "GeneratedBy")

private val IMMUTABLE_TYPE = ClassName.get("org.babyfish.jimmer.meta", "ImmutableType")

private val TYPED_PROP = ClassName.get("org.babyfish.jimmer.meta", "TypedProp")

private val TYPED_PROP_SCALAR = TYPED_PROP.nestedClass("Scalar")

private val TYPED_PROP_SCALAR_LIST = TYPED_PROP.nestedClass("ScalarList")

private val TYPED_PROP_REFERENCE = TYPED_PROP.nestedClass("Reference")

private val TYPED_PROP_REFERENCE_LIST = TYPED_PROP.nestedClass("ReferenceList")

private val PROPS = ClassName.get("org.babyfish.jimmer.sql.ast.table", "Props")

private val PROPS_FOR = ClassName.get("org.babyfish.jimmer.sql.ast.table", "PropsFor")

private val SELECTION = ClassName.get("org.babyfish.jimmer.sql.ast", "Selection")

private val JOIN_TYPE = ClassName.get("org.babyfish.jimmer.sql", "JoinType")

private val PREDICATE = ClassName.get("org.babyfish.jimmer.sql.ast", "Predicate")

private val FUNCTION = ClassName.get("java.util.function", "Function")

private val PROP_EXPRESSION = ClassName.get("org.babyfish.jimmer.sql.ast", "PropExpression")

private val PROP_NUMERIC_EXPRESSION = PROP_EXPRESSION.nestedClass("Num")

private val PROP_STRING_EXPRESSION = PROP_EXPRESSION.nestedClass("Str")

private val PROP_DATE_EXPRESSION = PROP_EXPRESSION.nestedClass("Dt")

private val PROP_TEMPORAL_EXPRESSION = PROP_EXPRESSION.nestedClass("Tp")

private val PROP_COMPARABLE_EXPRESSION = PROP_EXPRESSION.nestedClass("Cmp")

private val TABLE = ClassName.get("org.babyfish.jimmer.sql.ast.table", "Table")

private val TABLE_EX = ClassName.get("org.babyfish.jimmer.sql.ast.table", "TableEx")

private val POLYMORPHIC_TABLE = ClassName.get("org.babyfish.jimmer.sql.ast.table", "PolymorphicTable")

private val ABSTRACT_TYPED_TABLE = ClassName.get("org.babyfish.jimmer.sql.ast.table.spi", "AbstractTypedTable")

private val DELAYED_OPERATION = ABSTRACT_TYPED_TABLE.nestedClass("DelayedOperation")

private val TABLE_IMPLEMENTOR = ClassName.get("org.babyfish.jimmer.sql.ast.impl.table", "TableImplementor")

private val TABLE_EX_PROXY = ClassName.get("org.babyfish.jimmer.sql.ast.table.spi", "TableExProxy")

private val TABLE_PROXIES = ClassName.get("org.babyfish.jimmer.sql.ast.impl.table", "TableProxies")

private val BASE_TABLE_OWNER = ClassName.get("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableOwner")

private val BASE_TABLE = ClassName.get("org.babyfish.jimmer.sql.ast.table", "BaseTable")

private val BASE_TABLE_SYMBOL = ClassName.get("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableSymbol")

private val BASE_TABLE_SYMBOLS = ClassName.get("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableSymbols")

private val WEAK_JOIN = ClassName.get("org.babyfish.jimmer.sql.ast.table", "WeakJoin")

private val WEAK_JOIN_HANDLE = ClassName.get("org.babyfish.jimmer.sql.ast.impl.table", "WeakJoinHandle")

private val WEAK_JOIN_LAMBDA = ClassName.get("org.babyfish.jimmer.sql.ast.impl.table", "WeakJoinLambda")

private val J_WEAK_JOIN_LAMBDA_FACTORY =
    ClassName.get("org.babyfish.jimmer.sql.ast.impl.table", "JWeakJoinLambdaFactory")

private val TABLE_LIKE = ClassName.get("org.babyfish.jimmer.sql.ast.table.spi", "TableLike")

private val STRING = ClassName.get("java.lang", "String")

private val CLASS = ClassName.get("java.lang", "Class")

private val OVERRIDE = ClassName.get("java.lang", "Override")

private val DEPRECATED = ClassName.get("java.lang", "Deprecated")

private val SUPPRESS_WARNINGS = ClassName.get("java.lang", "SuppressWarnings")
