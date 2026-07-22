package org.babyfish.jimmer.compiler.immutable.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import site.addzero.lsi.jimmer.ImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableQueryMetadata
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypedPropKind
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.simpleName
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableEmbeddableKotlinRenderer {

    fun render(
        schema: ImmutableSchema,
        type: ImmutableType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        require(type.kind == ImmutableTypeKind.EMBEDDABLE) {
            "Kotlin immutable embeddable renderer only supports embeddable types: ${type.id.value}"
        }
        return EmbeddableRenderContext(schema, type, workspace).render()
    }
}

private class EmbeddableRenderContext(
    schema: ImmutableSchema,
    private val type: ImmutableType,
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
                type.props.forEach { prop ->
                    addEmbeddedProp(prop, nullable = false)
                    addEmbeddedProp(prop, nullable = true)
                }
                addFunction(fetchByFunction(nullable = false))
                addFunction(fetchByFunction(nullable = true))
                addType(propsObject())
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
            aggregationMode = metadata.aggregationMode(),
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }

    private fun FileSpec.Builder.addEmbeddedProp(
        prop: ImmutableProp,
        nullable: Boolean,
    ) {
        if (!nullable && prop.nullable) {
            return
        }
        val receiverType = when {
            prop.nullable -> K_EMBEDDED_PROP_EXPRESSION
            nullable -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION
            else -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION
        }.parameterizedBy(modelClass)
        val propType = prop.type.toKotlinTypeName().copy(nullable = false)
        val returnType = when {
            prop.embedded && nullable -> K_NULLABLE_EMBEDDED_PROP_EXPRESSION
            prop.embedded -> K_NON_NULL_EMBEDDED_PROP_EXPRESSION
            nullable -> K_NULLABLE_PROP_EXPRESSION
            else -> K_NON_NULL_PROP_EXPRESSION
        }.parameterizedBy(propType)
        addProperty(
            PropertySpec.builder(prop.name, returnType)
                .receiver(receiverType)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(generatedByAnnotation(modelClass))
                        .apply {
                            if (prop.embedded || !nullable || prop.nullable) {
                                addStatement(
                                    "return get<%T>(%T.%L.unwrap()) as %T",
                                    propType,
                                    propsClass,
                                    metadata.fieldName(prop),
                                    returnType,
                                )
                            } else {
                                addStatement(
                                    "return get(%T.%L.unwrap())",
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

    private fun fetchByFunction(nullable: Boolean): FunSpec {
        val receiverType = if (nullable) {
            K_NULLABLE_EMBEDDED_PROP_EXPRESSION
        } else {
            K_NON_NULL_EMBEDDED_PROP_EXPRESSION
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

    private fun propsObject(): TypeSpec {
        return TypeSpec.objectBuilder(propsClass)
            .addAnnotation(generatedByAnnotation(modelClass))
            .apply {
                type.props.forEach { prop -> addProperty(typedProp(prop)) }
            }
            .build()
    }

    private fun typedProp(prop: ImmutableProp): PropertySpec {
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
                metadata.typedPropElementType(prop).toKotlinTypeName(),
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

private val SUPPRESS = ClassName("kotlin", "Suppress")

private val GENERATED_BY = ClassName("org.babyfish.jimmer.internal", "GeneratedBy")

private val TYPED_PROP = ClassName("org.babyfish.jimmer.meta", "TypedProp")

private val TYPED_PROP_SCALAR = ClassName("org.babyfish.jimmer.meta", "TypedProp", "Scalar")

private val TYPED_PROP_SCALAR_LIST = ClassName("org.babyfish.jimmer.meta", "TypedProp", "ScalarList")

private val TYPED_PROP_REFERENCE = ClassName("org.babyfish.jimmer.meta", "TypedProp", "Reference")

private val TYPED_PROP_REFERENCE_LIST = ClassName("org.babyfish.jimmer.meta", "TypedProp", "ReferenceList")

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

private val K_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KEmbeddedPropExpression",
)

private val SELECTION = ClassName("org.babyfish.jimmer.sql.ast", "Selection")

private val NEW_FETCHER = ClassName("org.babyfish.jimmer.sql.kt.fetcher", "newFetcher")

private val TO_IMMUTABLE_PROP = MemberName("org.babyfish.jimmer.kt", "toImmutableProp")
