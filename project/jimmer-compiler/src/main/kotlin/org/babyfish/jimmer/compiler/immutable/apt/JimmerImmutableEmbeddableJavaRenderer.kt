package org.babyfish.jimmer.compiler.immutable.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableEmbeddableMetadata
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePropExpressionKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypedPropKind
import org.babyfish.jimmer.compiler.immutable.packageName
import org.babyfish.jimmer.compiler.immutable.simpleName
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableEmbeddableJavaRenderer {

    fun render(
        schema: JimmerImmutableSchema,
        type: JimmerImmutableType,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        require(type.kind == JimmerImmutableTypeKind.EMBEDDABLE) {
            "Java immutable embeddable renderer only supports embeddable types: ${type.id.value}"
        }
        val metadata = JimmerImmutableEmbeddableMetadata(schema, workspace)
        val propsContent = JavaFile.builder(type.packageName, propsType(type, metadata))
            .indent("    ")
            .build()
            .toString()
        val expressionContent = JavaFile.builder(type.packageName, propExpressionType(type, metadata))
            .indent("    ")
            .build()
            .toString()
        val originatingSymbols = metadata.originatingSymbols(type)
        val originatingSources = workspace.originatingSources(originatingSymbols)
        return listOf(
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = type.propsClassName().canonicalName(),
                content = propsContent,
                aggregationMode = metadata.aggregationMode(),
                originatingSymbols = originatingSymbols,
                originatingSources = originatingSources,
            ),
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = type.propExpressionClassName().canonicalName(),
                content = expressionContent,
                aggregationMode = metadata.aggregationMode(),
                originatingSymbols = originatingSymbols,
                originatingSources = originatingSources,
            ),
        )
    }

    private fun propsType(
        type: JimmerImmutableType,
        metadata: JimmerImmutableEmbeddableMetadata,
    ): TypeSpec {
        return TypeSpec.interfaceBuilder(type.propsClassName().simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(type.generatedAnnotation())
            .apply {
                type.props.forEach { prop -> addField(typedPropField(type, prop, metadata)) }
            }
            .build()
    }

    private fun typedPropField(
        type: JimmerImmutableType,
        prop: JimmerImmutableProp,
        metadata: JimmerImmutableEmbeddableMetadata,
    ): FieldSpec {
        val kind = metadata.typedPropKind(prop)
        return FieldSpec.builder(
            ParameterizedTypeName.get(
                kind.typedPropClassName(),
                type.className(),
                metadata.typedPropElementType(prop).toJavaTypeName().box(),
            ),
            metadata.fieldName(prop),
            Modifier.PUBLIC,
            Modifier.STATIC,
            Modifier.FINAL,
        )
            .initializer(
                "\n    \$T.\$L(\$T.get(\$T.class).getProp(\$S))",
                TYPED_PROP,
                kind.factoryName(),
                IMMUTABLE_TYPE,
                type.className(),
                prop.name,
            )
            .build()
    }

    private fun propExpressionType(
        type: JimmerImmutableType,
        metadata: JimmerImmutableEmbeddableMetadata,
    ): TypeSpec {
        return TypeSpec.classBuilder(type.propExpressionClassName().simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(type.generatedAnnotation())
            .superclass(ParameterizedTypeName.get(ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION, type.className()))
            .apply {
                addMethod(rawConstructor(type))
                addMethod(baseTableConstructor(type))
                type.props.forEach { prop -> addMethod(propMethod(type, prop, metadata)) }
                addMethod(baseTableOwnerMethod(type))
            }
            .build()
    }

    private fun rawConstructor(type: JimmerImmutableType): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(EMBEDDED_PROP_EXPRESSION, type.className()), "raw")
            .addStatement("super(raw)")
            .build()
    }

    private fun baseTableConstructor(type: JimmerImmutableType): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(type.propExpressionClassName(), "base")
            .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
            .addStatement("super(base, baseTableOwner)")
            .build()
    }

    private fun propMethod(
        type: JimmerImmutableType,
        prop: JimmerImmutableProp,
        metadata: JimmerImmutableEmbeddableMetadata,
    ): MethodSpec {
        val targetType = metadata.targetType(prop)
        val returnType = targetType?.propExpressionClassName() ?: prop.expressionTypeName(metadata)
        return MethodSpec.methodBuilder(prop.name)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .apply {
                prop.documentation?.let(Doc::parse)?.value?.let { documentation ->
                    addJavadoc("\$L", documentation)
                }
                if (targetType != null) {
                    addStatement(
                        "return new \$T(__get(\$T.\$L.unwrap()))",
                        returnType,
                        type.propsClassName(),
                        metadata.fieldName(prop),
                    )
                } else {
                    addStatement(
                        "return __get(\$T.\$L.unwrap())",
                        type.propsClassName(),
                        metadata.fieldName(prop),
                    )
                }
            }
            .build()
    }

    private fun baseTableOwnerMethod(type: JimmerImmutableType): MethodSpec {
        return MethodSpec.methodBuilder("__baseTableOwner")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(BASE_TABLE_OWNER, "baseTableOwner")
            .returns(type.propExpressionClassName())
            .addStatement("return new \$T(this, baseTableOwner)", type.propExpressionClassName())
            .build()
    }
}

private fun JimmerImmutableProp.expressionTypeName(
    metadata: JimmerImmutableEmbeddableMetadata,
): TypeName {
    val boxedType = type.toJavaTypeName().box()
    return when (metadata.expressionKind(this)) {
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

private fun JimmerImmutableType.className(): ClassName = ClassName.bestGuess(qualifiedName)

private fun JimmerImmutableType.propsClassName(): ClassName = ClassName.get(packageName, "${simpleName}Props")

private fun JimmerImmutableType.propExpressionClassName(): ClassName =
    ClassName.get(packageName, "${simpleName}PropExpression")

private fun JimmerImmutableType.generatedAnnotation(): AnnotationSpec {
    return AnnotationSpec.builder(GENERATED_BY)
        .addMember("type", "\$T.class", className())
        .build()
}

private val GENERATED_BY = ClassName.get("org.babyfish.jimmer.internal", "GeneratedBy")

private val IMMUTABLE_TYPE = ClassName.get("org.babyfish.jimmer.meta", "ImmutableType")

private val TYPED_PROP = ClassName.get("org.babyfish.jimmer.meta", "TypedProp")

private val TYPED_PROP_SCALAR = TYPED_PROP.nestedClass("Scalar")

private val TYPED_PROP_SCALAR_LIST = TYPED_PROP.nestedClass("ScalarList")

private val TYPED_PROP_REFERENCE = TYPED_PROP.nestedClass("Reference")

private val TYPED_PROP_REFERENCE_LIST = TYPED_PROP.nestedClass("ReferenceList")

private val PROP_EXPRESSION = ClassName.get("org.babyfish.jimmer.sql.ast", "PropExpression")

private val EMBEDDED_PROP_EXPRESSION = PROP_EXPRESSION.nestedClass("Embedded")

private val PROP_NUMERIC_EXPRESSION = PROP_EXPRESSION.nestedClass("Num")

private val PROP_STRING_EXPRESSION = PROP_EXPRESSION.nestedClass("Str")

private val PROP_DATE_EXPRESSION = PROP_EXPRESSION.nestedClass("Dt")

private val PROP_TEMPORAL_EXPRESSION = PROP_EXPRESSION.nestedClass("Tp")

private val PROP_COMPARABLE_EXPRESSION = PROP_EXPRESSION.nestedClass("Cmp")

private val ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION = ClassName.get(
    "org.babyfish.jimmer.sql.ast.embedded",
    "AbstractTypedEmbeddedPropExpression",
)

private val BASE_TABLE_OWNER = ClassName.get("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableOwner")
