package org.babyfish.jimmer.compiler.tuple.ksp

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import org.babyfish.jimmer.compiler.tuple.TypedTupleKotlinNamedPlan
import org.babyfish.jimmer.compiler.tuple.TypedTuplePrecompiledSchema
import org.babyfish.jimmer.compiler.tuple.TypedTupleProperty
import org.babyfish.jimmer.compiler.tuple.TypedTupleType
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class TypedTupleKotlinRenderer {

    fun render(
        schema: TypedTuplePrecompiledSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        return schema.tuples.map { tuple -> render(tuple, workspace) }
    }

    private fun render(
        tuple: TypedTupleType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val mapperClass = ClassName(tuple.packageName, tuple.mapperSimpleName)
        val tupleClass = ClassName(tuple.packageName, tuple.simpleName)
        val mapperType = TypeSpec.classBuilder(tuple.mapperSimpleName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("selections", SELECTION_ARRAY)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("selections", SELECTION_ARRAY)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("selections")
                    .build()
            )
            .addSuperinterface(TUPLE_MAPPER.parameterizedBy(tupleClass))
            .addFunction(getSelectionsFunction())
            .addFunction(createTupleFunction(tuple, tupleClass))
            .apply {
                tuple.properties.drop(1).forEach { property ->
                    addType(builderType(tuple, property, mapperClass))
                }
                addType(companionType(tuple, mapperClass))
            }
            .build()
        val content = FileSpec.builder(tuple.packageName, tuple.mapperSimpleName)
            .indent("    ")
            .addType(mapperType)
            .build()
            .toString()
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = tuple.mapperQualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(tuple.id),
            originatingSources = workspace.originatingSources(setOf(tuple.id)),
        )
    }

    private fun getSelectionsFunction(): FunSpec {
        return FunSpec.builder("getSelections")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(SUPPRESS)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build()
            )
            .returns(LIST.parameterizedBy(SELECTION.parameterizedBy(STAR)))
            .addStatement(
                "return %T.unmodifiableList(listOf(*selections as Array<%T>))",
                COLLECTIONS,
                SELECTION.parameterizedBy(STAR),
            )
            .build()
    }

    private fun createTupleFunction(
        tuple: TypedTupleType,
        tupleClass: ClassName,
    ): FunSpec {
        return FunSpec.builder("createTuple")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("args", ARRAY.parameterizedBy(ANY.copy(nullable = true)))
            .returns(tupleClass)
            .addCode(tuple.createTupleCode(tupleClass))
            .build()
    }

    private fun builderType(
        tuple: TypedTupleType,
        property: TypedTupleProperty,
        mapperClass: ClassName,
    ): TypeSpec {
        val builderSimpleName = requireNotNull(property.builderSimpleName)
        val returnType = tuple.stepType(property, mapperClass)
        return TypeSpec.classBuilder(builderSimpleName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("selections", SELECTION_ARRAY)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("selections", SELECTION_ARRAY)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("selections")
                    .build()
            )
            .addFunction(
                FunSpec.builder(property.name)
                    .addParameter("selection", selectionType(property))
                    .returns(returnType)
                    .addStatement("selections[%L] = selection", property.index)
                    .addStatement("return %T(selections)", returnType)
                    .build()
            )
            .build()
    }

    private fun companionType(
        tuple: TypedTupleType,
        mapperClass: ClassName,
    ): TypeSpec {
        val property = tuple.properties.first()
        val returnType = tuple.stepType(property, mapperClass)
        return TypeSpec.companionObjectBuilder()
            .addFunction(
                FunSpec.builder(property.name)
                    .addParameter("selection", selectionType(property))
                    .returns(returnType)
                    .addStatement(
                        "val selections = arrayOfNulls<%T>(%L)",
                        SELECTION.parameterizedBy(STAR),
                        tuple.properties.size,
                    )
                    .addStatement("selections[%L] = selection", property.index)
                    .addStatement("return %T(selections)", returnType)
                    .build()
            )
            .build()
    }
}

private fun TypedTupleType.createTupleCode(tupleClass: ClassName): CodeBlock {
    val plan = construction as? TypedTupleKotlinNamedPlan
        ?: error("Kotlin typed tuple '${id.value}' has unsupported construction plan '$construction'")
    return CodeBlock.builder()
        .add("return %T(\n", tupleClass)
        .indent()
        .apply {
            plan.arguments.forEachIndexed { index, argument ->
                if (index != 0) {
                    add(",\n")
                }
                val property = properties[argument.propertyIndex]
                add(
                    "%N = args[%L] as %T",
                    argument.parameterName,
                    property.index,
                    property.type.toKotlinTypeName(),
                )
            }
        }
        .unindent()
        .add("\n)\n")
        .build()
}

private fun TypedTupleType.stepType(
    property: TypedTupleProperty,
    mapperClass: ClassName,
): ClassName {
    return if (property.nextStepTypeName == mapperSimpleName) {
        mapperClass
    } else {
        mapperClass.nestedClass(property.nextStepTypeName)
    }
}

private fun selectionType(property: TypedTupleProperty) =
    SELECTION.parameterizedBy(property.type.toKotlinTypeName())

private val SELECTION = ClassName("org.babyfish.jimmer.sql.ast", "Selection")
private val TUPLE_MAPPER = ClassName("org.babyfish.jimmer.sql.runtime", "TupleMapper")
private val COLLECTIONS = ClassName("java.util", "Collections")
private val SUPPRESS = ClassName("kotlin", "Suppress")
private val SELECTION_ARRAY = ARRAY.parameterizedBy(
    SELECTION.parameterizedBy(STAR).copy(nullable = true)
)
