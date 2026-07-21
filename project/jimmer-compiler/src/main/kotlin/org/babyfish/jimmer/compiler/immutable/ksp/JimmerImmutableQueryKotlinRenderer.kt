package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableQueryMetadata
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypedPropKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.simpleName
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableQueryKotlinRenderer {

    fun render(
        schema: JimmerImmutableSchema,
        type: JimmerImmutableType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        require(type.kind in QUERY_TYPE_KINDS) {
            "Kotlin immutable query renderer only supports entity and mapped-superclass types: ${type.id.value}"
        }
        require(type.typeParameterIds.isEmpty()) {
            "Kotlin immutable query renderer does not support generic types: ${type.id.value}"
        }
        return QueryRenderContext(schema, type, workspace).render()
    }
}

private class QueryRenderContext(
    private val schema: JimmerImmutableSchema,
    private val type: JimmerImmutableType,
    private val workspace: LsiWorkspace,
) {

    private val metadata = JimmerImmutableQueryMetadata(schema, workspace)

    private val modelClass = ClassName.bestGuess(type.qualifiedName)

    private val propsClass = ClassName(type.packageName, "${type.simpleName}$PROPS_SUFFIX")

    private val fetcherDslClass = ClassName(type.packageName, "${type.simpleName}$FETCHER_DSL_SUFFIX")

    fun render(): GeneratedArtifact {
        val sourceBaseName = metadata.sourceBaseName(type)
        val fileName = "$sourceBaseName$PROPS_SUFFIX"
        val fileSpec = FileSpec.builder(type.packageName, fileName)
            .indent("    ")
            .addAnnotation(suppressAllAnnotation())
            .addAnnotation(generatedByAnnotation(modelClass))
            .apply {
                metadata.orderedProps(type).forEach { prop ->
                    addQueryProp(prop, nonNullTable = true, outerJoin = false, tableEx = false)
                    addQueryProp(prop, nonNullTable = false, outerJoin = false, tableEx = false)
                    addQueryProp(prop, nonNullTable = true, outerJoin = true, tableEx = false)
                    addQueryProp(prop, nonNullTable = false, outerJoin = true, tableEx = false)
                    addQueryProp(prop, nonNullTable = true, outerJoin = false, tableEx = true)
                    addQueryProp(prop, nonNullTable = false, outerJoin = false, tableEx = true)
                    addQueryProp(prop, nonNullTable = true, outerJoin = true, tableEx = true)
                    addQueryProp(prop, nonNullTable = false, outerJoin = true, tableEx = true)
                    addAssociatedIdProp(prop, nonNullTable = true, tableEx = false)
                    addAssociatedIdProp(prop, nonNullTable = false, tableEx = false)
                    addAssociatedIdProp(prop, nonNullTable = true, tableEx = true)
                    addAssociatedIdProp(prop, nonNullTable = false, tableEx = true)
                }
                if (type.kind == JimmerImmutableTypeKind.ENTITY) {
                    addRemoteIdProp(nullable = false)
                    addRemoteIdProp(nullable = true)
                    addFunction(fetchByFunction(nullable = false))
                    addFunction(fetchByFunction(nullable = true))
                }
                addPolymorphicFunctions()
                addType(propsObject())
            }
            .build()
        val branchDependent = metadata.branchDependent(type)
        val aggregationMode = metadata.queryAggregationMode(type)
        val originatingSymbols = if (branchDependent) {
            metadata.queryOriginatingSymbols(type)
        } else {
            metadata.originatingSymbols(type)
        }
        val qualifiedFileName = if (type.packageName.isEmpty()) {
            fileName
        } else {
            "${type.packageName}.$fileName"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = qualifiedFileName,
            content = fileSpec.toString(),
            aggregationMode = aggregationMode,
            emissionMode = if (branchDependent) {
                ArtifactEmissionMode.STABLE
            } else {
                ArtifactEmissionMode.IMMEDIATE
            },
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }

    private fun FileSpec.Builder.addQueryProp(
        prop: JimmerImmutableProp,
        nonNullTable: Boolean,
        outerJoin: Boolean,
        tableEx: Boolean,
    ) {
        val entityAssociation = metadata.isEntityAssociation(prop)
        if (outerJoin && !entityAssociation) {
            return
        }
        if (nonNullTable && (entityAssociation || prop.nullable)) {
            return
        }
        if (tableEx && !entityAssociation) {
            return
        }
        if (prop.list && entityAssociation && !tableEx) {
            if (!outerJoin && metadata.isDsl(prop, tableEx = true)) {
                addFunction(existsFunction(prop))
            }
            return
        }
        if (!metadata.isDsl(prop, tableEx)) {
            return
        }
        val receiverClass = when {
            tableEx -> K_TABLE_EX
            entityAssociation || prop.nullable -> K_PROPS
            nonNullTable -> K_NON_NULL_PROPS
            else -> K_NULLABLE_PROPS
        }.parameterizedBy(modelClass)
        val propertyName = if (outerJoin) "${prop.name}?" else prop.name
        val operationName = when {
            outerJoin -> "outerJoin"
            entityAssociation -> "join"
            else -> "get"
        }
        val returnType = queryPropReturnType(prop, nonNullTable, outerJoin, tableEx)
        addProperty(
            PropertySpec.builder(propertyName, returnType)
                .receiver(receiverClass)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(generatedByAnnotation(modelClass))
                        .apply {
                            when {
                                prop.remote -> addCode(
                                    "return %L.protect(%L(%T.%L.unwrap()))",
                                    K_REMOTE_REF_QUALIFIED_NAME,
                                    operationName,
                                    propsClass,
                                    metadata.fieldName(prop),
                                )
                                operationName == "get" -> addCode(
                                    "return get<%T>(%T.%L.unwrap()) as %T",
                                    propTargetType(prop),
                                    propsClass,
                                    metadata.fieldName(prop),
                                    returnType,
                                )
                                else -> addCode(
                                    "return %L(%T.%L.unwrap())",
                                    operationName,
                                    propsClass,
                                    metadata.fieldName(prop),
                                )
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun queryPropReturnType(
        prop: JimmerImmutableProp,
        nonNullTable: Boolean,
        outerJoin: Boolean,
        tableEx: Boolean,
    ): TypeName {
        val entityAssociation = metadata.isEntityAssociation(prop)
        val rawReturnType = when {
            prop.remote -> if (outerJoin) K_NULLABLE_REMOTE_REF else K_NON_NULL_REMOTE_REF
            entityAssociation && tableEx -> {
                if (outerJoin) K_NULLABLE_TABLE_EX else K_NON_NULL_TABLE_EX
            }
            !prop.list && entityAssociation -> {
                if (outerJoin) K_NULLABLE_TABLE else K_NON_NULL_TABLE
            }
            prop.embedded -> {
                if (nonNullTable) K_NON_NULL_EMBEDDED_PROP_EXPRESSION else K_NULLABLE_EMBEDDED_PROP_EXPRESSION
            }
            else -> {
                if (nonNullTable) K_NON_NULL_PROP_EXPRESSION else K_NULLABLE_PROP_EXPRESSION
            }
        }
        val targetType = propTargetType(prop).let { elementType ->
            if (prop.list && !entityAssociation) {
                LIST.parameterizedBy(elementType)
            } else {
                elementType
            }
        }
        return rawReturnType.parameterizedBy(targetType)
    }

    private fun existsFunction(prop: JimmerImmutableProp): FunSpec {
        val targetType = metadata.targetType(prop)
            ?.let { target -> ClassName.bestGuess(target.qualifiedName) }
            ?: ANY
        return FunSpec.builder(prop.name)
            .receiver(K_PROPS.parameterizedBy(modelClass))
            .addParameter(
                "block",
                LambdaTypeName.get(
                    receiver = K_IMPLICIT_SUB_QUERY_TABLE.parameterizedBy(targetType),
                    returnType = K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN).copy(nullable = true),
                ),
            )
            .returns(K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN).copy(nullable = true))
            .addStatement(
                "return exists(%T.%L.unwrap(), block)",
                propsClass,
                metadata.fieldName(prop),
            )
            .build()
    }

    private fun FileSpec.Builder.addAssociatedIdProp(
        prop: JimmerImmutableProp,
        nonNullTable: Boolean,
        tableEx: Boolean,
    ) {
        val propertyName = metadata.associatedIdPropName(type, prop) ?: return
        if (nonNullTable && prop.nullable) {
            return
        }
        if (
            prop.primaryMapping == JimmerImmutablePrimaryMapping.TRANSIENT ||
            !metadata.isEntityAssociation(prop) ||
            prop.list != tableEx
        ) {
            return
        }
        val targetIdProp = metadata.targetIdProp(prop) ?: return
        val receiverClass = when {
            prop.nullable -> K_PROPS
            tableEx && nonNullTable -> K_NON_NULL_TABLE_EX
            tableEx -> K_NULLABLE_TABLE_EX
            nonNullTable -> K_NON_NULL_TABLE
            else -> K_NULLABLE_PROPS
        }.parameterizedBy(modelClass)
        val returnClass = when {
            targetIdProp.embedded && nonNullTable -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION
            targetIdProp.embedded -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION
            nonNullTable -> K_NON_NULL_PROP_EXPRESSION
            else -> K_NULLABLE_PROP_EXPRESSION
        }
        val targetIdType = targetIdProp.type.toQueryKotlinTypeName()
        val returnType = returnClass.parameterizedBy(targetIdType)
        addProperty(
            PropertySpec.builder(propertyName, returnType)
                .receiver(receiverClass)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(generatedByAnnotation(modelClass))
                        .addStatement(
                            "return getAssociatedId<%T>(%T.%L.unwrap()) as %T",
                            targetIdType.copy(nullable = false),
                            propsClass,
                            metadata.fieldName(prop),
                            returnType,
                        )
                        .build()
                )
                .build()
        )
    }

    private fun FileSpec.Builder.addRemoteIdProp(nullable: Boolean) {
        val idProp = type.idPropId?.let(schema.propsById::get)
            ?: error("Entity immutable type '${type.id.value}' must declare an id property")
        val idType = idProp.type.toQueryKotlinTypeName()
        val returnType = if (nullable) {
            K_NULLABLE_PROP_EXPRESSION
        } else {
            K_NON_NULL_PROP_EXPRESSION
        }.parameterizedBy(idType)
        val receiverType = if (nullable) {
            K_NULLABLE_REMOTE_REF
        } else {
            K_NON_NULL_REMOTE_REF
        }.parameterizedBy(modelClass)
        addProperty(
            PropertySpec.builder(idProp.name, returnType)
                .receiver(receiverType)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(generatedByAnnotation(modelClass))
                        .addCode(
                            "return (this as %T<*>).id<%T>() as %T",
                            K_REMOTE_REF_IMPLEMENTOR,
                            idType.copy(nullable = false),
                            returnType,
                        )
                        .build()
                )
                .build()
        )
    }

    private fun fetchByFunction(nullable: Boolean): FunSpec {
        val receiverType = if (nullable) {
            K_NULLABLE_TABLE
        } else {
            K_NON_NULL_TABLE
        }.parameterizedBy(modelClass)
        return FunSpec.builder("fetchBy")
            .addAnnotation(generatedByAnnotation(modelClass))
            .receiver(receiverType)
            .addParameter(
                "block",
                LambdaTypeName.get(fetcherDslClass, emptyList(), UNIT),
            )
            .returns(SELECTION.parameterizedBy(modelClass.copy(nullable = nullable)))
            .addCode(
                "return fetch(%T(%T::class).%M(block))",
                NEW_FETCHER,
                modelClass,
                MemberName(type.packageName, "by"),
            )
            .build()
    }

    private fun FileSpec.Builder.addPolymorphicFunctions() {
        if (metadata.strictTypeBranches(type).isEmpty()) {
            return
        }
        addFunction(treatAsFunction(receiverNullable = false, optional = false))
        addFunction(treatAsFunction(receiverNullable = true, optional = false))
        addFunction(treatAsFunction(receiverNullable = false, optional = true))
        addFunction(treatAsFunction(receiverNullable = true, optional = true))
        addFunction(reifiedTreatAsFunction(receiverNullable = false, optional = false))
        addFunction(reifiedTreatAsFunction(receiverNullable = true, optional = false))
        addFunction(reifiedTreatAsFunction(receiverNullable = false, optional = true))
        addFunction(reifiedTreatAsFunction(receiverNullable = true, optional = true))
        addFunction(instanceOfFunction())
        addFunction(reifiedInstanceOfFunction())
        addFunction(exactTypeFunction())
        addFunction(reifiedExactTypeFunction())
    }

    private fun treatAsFunction(
        receiverNullable: Boolean,
        optional: Boolean,
    ): FunSpec {
        val typeVariable = TypeVariableName("S", modelClass)
        val receiverType = if (receiverNullable) {
            K_NULLABLE_TABLE_EX
        } else {
            K_NON_NULL_TABLE_EX
        }.parameterizedBy(modelClass)
        val returnType = if (optional) {
            K_NULLABLE_TABLE_EX
        } else {
            K_NON_NULL_TABLE_EX
        }.parameterizedBy(typeVariable)
        val functionName = if (optional) "tryTreatAs" else "treatAs"
        return FunSpec.builder(functionName)
            .addAnnotation(generatedByAnnotation(modelClass))
            .addTypeVariable(typeVariable)
            .receiver(receiverType)
            .addParameter("type", K_CLASS.parameterizedBy(typeVariable))
            .returns(returnType)
            .addStatement(
                "return %T.%L(this, type)",
                K_POLYMORPHIC_TABLES,
                functionName,
            )
            .build()
    }

    private fun reifiedTreatAsFunction(
        receiverNullable: Boolean,
        optional: Boolean,
    ): FunSpec {
        val typeVariable = TypeVariableName("S", modelClass).copy(reified = true)
        val receiverType = if (receiverNullable) {
            K_NULLABLE_TABLE_EX
        } else {
            K_NON_NULL_TABLE_EX
        }.parameterizedBy(modelClass)
        val returnType = if (optional) {
            K_NULLABLE_TABLE_EX
        } else {
            K_NON_NULL_TABLE_EX
        }.parameterizedBy(typeVariable)
        val functionName = if (optional) "tryTreatAs" else "treatAs"
        return FunSpec.builder(functionName)
            .addAnnotation(generatedByAnnotation(modelClass))
            .addModifiers(KModifier.INLINE)
            .addTypeVariable(typeVariable)
            .receiver(receiverType)
            .returns(returnType)
            .addStatement("return %L(S::class)", functionName)
            .build()
    }

    private fun instanceOfFunction(): FunSpec {
        return FunSpec.builder("instanceOf")
            .addAnnotation(generatedByAnnotation(modelClass))
            .receiver(K_TABLE_EX.parameterizedBy(modelClass))
            .addParameter(
                "type",
                K_CLASS.parameterizedBy(WildcardTypeName.producerOf(modelClass)),
            )
            .returns(K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN))
            .addStatement("return %T.instanceOf(this, type)", K_POLYMORPHIC_TABLES)
            .build()
    }

    private fun reifiedInstanceOfFunction(): FunSpec {
        val typeVariable = TypeVariableName("S", modelClass).copy(reified = true)
        return FunSpec.builder("instanceOf")
            .addAnnotation(generatedByAnnotation(modelClass))
            .addModifiers(KModifier.INLINE)
            .addTypeVariable(typeVariable)
            .receiver(K_TABLE_EX.parameterizedBy(modelClass))
            .returns(K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN))
            .addStatement("return instanceOf(S::class)")
            .build()
    }

    private fun exactTypeFunction(): FunSpec {
        return FunSpec.builder("exactType")
            .addAnnotation(generatedByAnnotation(modelClass))
            .receiver(K_TABLE_EX.parameterizedBy(modelClass))
            .addParameter(
                "type",
                K_CLASS.parameterizedBy(WildcardTypeName.producerOf(modelClass)),
            )
            .returns(K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN))
            .addStatement("return %T.exactType(this, type)", K_POLYMORPHIC_TABLES)
            .build()
    }

    private fun reifiedExactTypeFunction(): FunSpec {
        val typeVariable = TypeVariableName("S", modelClass).copy(reified = true)
        return FunSpec.builder("exactType")
            .addAnnotation(generatedByAnnotation(modelClass))
            .addModifiers(KModifier.INLINE)
            .addTypeVariable(typeVariable)
            .receiver(K_TABLE_EX.parameterizedBy(modelClass))
            .returns(K_NON_NULL_EXPRESSION.parameterizedBy(BOOLEAN))
            .addStatement("return exactType(S::class)")
            .build()
    }

    private fun propsObject(): TypeSpec {
        return TypeSpec.objectBuilder(propsClass)
            .addAnnotation(generatedByAnnotation(modelClass))
            .apply {
                metadata.orderedProps(type).forEach { prop -> addProperty(typedProp(prop)) }
            }
            .build()
    }

    private fun typedProp(prop: JimmerImmutableProp): PropertySpec {
        val kind = metadata.typedPropKind(prop)
        val typedPropClass = when (kind) {
            JimmerImmutableTypedPropKind.SCALAR -> TYPED_PROP_SCALAR
            JimmerImmutableTypedPropKind.SCALAR_LIST -> TYPED_PROP_SCALAR_LIST
            JimmerImmutableTypedPropKind.REFERENCE -> TYPED_PROP_REFERENCE
            JimmerImmutableTypedPropKind.REFERENCE_LIST -> TYPED_PROP_REFERENCE_LIST
        }
        val factoryName = when (kind) {
            JimmerImmutableTypedPropKind.SCALAR -> "scalar"
            JimmerImmutableTypedPropKind.SCALAR_LIST -> "scalarList"
            JimmerImmutableTypedPropKind.REFERENCE -> "reference"
            JimmerImmutableTypedPropKind.REFERENCE_LIST -> "referenceList"
        }
        return PropertySpec.builder(
            metadata.fieldName(prop),
            typedPropClass.parameterizedBy(
                modelClass,
                metadata.typedPropElementType(prop).toQueryKotlinTypeName(),
            ),
        )
            .initializer(
                "%T.%L(%T::%N.%M())",
                TYPED_PROP,
                factoryName,
                modelClass,
                prop.name,
                TO_IMMUTABLE_PROP,
            )
            .build()
    }

    private fun propTargetType(prop: JimmerImmutableProp): TypeName {
        val typeRef = if (prop.list) {
            metadata.typedPropElementType(prop)
        } else {
            prop.type
        }
        return typeRef.toQueryKotlinTypeName().copy(nullable = false)
    }
}

private fun LsiTypeRef.toQueryKotlinTypeName(): TypeName {
    return if (this is LsiPrimitiveType) {
        copy(boxed = false).toKotlinTypeName()
    } else {
        toKotlinTypeName()
    }
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

private const val PROPS_SUFFIX = "Props"

private const val FETCHER_DSL_SUFFIX = "FetcherDsl"

private val QUERY_TYPE_KINDS = setOf(
    JimmerImmutableTypeKind.ENTITY,
    JimmerImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val SUPPRESS = ClassName("kotlin", "Suppress")

private val GENERATED_BY = ClassName("org.babyfish.jimmer.internal", "GeneratedBy")

private val TYPED_PROP = ClassName("org.babyfish.jimmer.meta", "TypedProp")

private val TYPED_PROP_SCALAR = ClassName("org.babyfish.jimmer.meta", "TypedProp", "Scalar")

private val TYPED_PROP_SCALAR_LIST = ClassName("org.babyfish.jimmer.meta", "TypedProp", "ScalarList")

private val TYPED_PROP_REFERENCE = ClassName("org.babyfish.jimmer.meta", "TypedProp", "Reference")

private val TYPED_PROP_REFERENCE_LIST = ClassName("org.babyfish.jimmer.meta", "TypedProp", "ReferenceList")

private val K_PROPS = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KProps")

private val K_NON_NULL_PROPS = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullProps")

private val K_NULLABLE_PROPS = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableProps")

private val K_NON_NULL_TABLE = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullTable")

private val K_NULLABLE_TABLE = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableTable")

private val K_NON_NULL_REMOTE_REF = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef", "NonNull")

private val K_NULLABLE_REMOTE_REF = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef", "Nullable")

private val K_REMOTE_REF = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef")

private const val K_REMOTE_REF_QUALIFIED_NAME = "org.babyfish.jimmer.sql.kt.ast.table.KRemoteRef"

private val K_REMOTE_REF_IMPLEMENTOR = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table.impl",
    "KRemoteRefImplementor",
)

private val K_NON_NULL_TABLE_EX = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullTableEx")

private val K_NULLABLE_TABLE_EX = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableTableEx")

private val K_IMPLICIT_SUB_QUERY_TABLE = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KImplicitSubQueryTable",
)

private val K_NON_NULL_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullExpression",
)

private val K_TABLE_EX = ClassName("org.babyfish.jimmer.sql.kt.ast.table", "KTableEx")

private val K_POLYMORPHIC_TABLES = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table.impl",
    "KPolymorphicTables",
)

private val K_NON_NULL_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullPropExpression",
)

private val K_NULLABLE_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNullablePropExpression",
)

private val K_NON_NULL_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullEmbeddedPropExpression",
)

private val K_NULLABLE_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNullableEmbeddedPropExpression",
)

private val K_CLASS = ClassName("kotlin.reflect", "KClass")

private val SELECTION = ClassName("org.babyfish.jimmer.sql.ast", "Selection")

private val NEW_FETCHER = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "newFetcher")

private val TO_IMMUTABLE_PROP = MemberName("org.babyfish.jimmer.kt", "toImmutableProp")
