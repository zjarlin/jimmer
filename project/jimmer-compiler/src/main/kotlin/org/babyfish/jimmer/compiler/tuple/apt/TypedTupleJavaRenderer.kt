package org.babyfish.jimmer.compiler.tuple.apt

import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.WildcardTypeName
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import org.babyfish.jimmer.compiler.tuple.TypedTupleJavaPositionalPlan
import org.babyfish.jimmer.compiler.tuple.TypedTupleJavaSetterPlan
import org.babyfish.jimmer.compiler.tuple.TypedTuplePrecompiledSchema
import org.babyfish.jimmer.compiler.tuple.TypedTupleProperty
import org.babyfish.jimmer.compiler.tuple.TypedTupleType
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class TypedTupleJavaRenderer {

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
        val mapperClass = ClassName.get(tuple.packageName, tuple.mapperSimpleName)
        val tupleClass = ClassName.get(tuple.packageName, tuple.simpleName)
        val mapperType = TypeSpec.classBuilder(tuple.mapperSimpleName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(TUPLE_MAPPER, tupleClass))
            .addField(
                FieldSpec.builder(SELECTION_ARRAY, "selections")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build()
            )
            .addMethod(mapperConstructor())
            .addMethod(getSelectionsMethod())
            .addMethod(createTupleMethod(tuple, tupleClass))
            .addMethod(firstPropertyMethod(tuple, mapperClass))
            .apply {
                tuple.properties.drop(1).forEach { property ->
                    addType(builderType(tuple, property, mapperClass))
                }
            }
            .build()
        val content = JavaFile.builder(tuple.packageName, mapperType)
            .indent("    ")
            .build()
            .toString()
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = tuple.mapperQualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(tuple.id),
            originatingSources = workspace.originatingSources(setOf(tuple.id)),
        )
    }

    private fun mapperConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addParameter(SELECTION_ARRAY, "selections")
            .addStatement("this.selections = selections")
            .build()
    }

    private fun getSelectionsMethod(): MethodSpec {
        return MethodSpec.methodBuilder("getSelections")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(ParameterizedTypeName.get(LIST, SELECTION_OF_ANY))
            .addStatement("return \$T.unmodifiableList(\$T.asList(selections))", COLLECTIONS, ARRAYS)
            .build()
    }

    private fun createTupleMethod(
        tuple: TypedTupleType,
        tupleClass: ClassName,
    ): MethodSpec {
        return MethodSpec.methodBuilder("createTuple")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(ArrayTypeName.of(Any::class.java), "args")
            .returns(tupleClass)
            .addCode(tuple.createTupleCode(tupleClass))
            .build()
    }

    private fun firstPropertyMethod(
        tuple: TypedTupleType,
        mapperClass: ClassName,
    ): MethodSpec {
        val property = tuple.properties.first()
        val returnType = tuple.stepType(property, mapperClass)
        return MethodSpec.methodBuilder(property.name)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(selectionType(property), "selection")
            .returns(returnType)
            .addStatement("\$T<?>[] selections = new \$T<?>[\$L]", SELECTION, SELECTION, tuple.properties.size)
            .addStatement("selections[\$L] = selection", property.index)
            .addStatement("return new \$T(selections)", returnType)
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
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addField(
                FieldSpec.builder(SELECTION_ARRAY, "selections")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build()
            )
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addParameter(SELECTION_ARRAY, "selections")
                    .addStatement("this.selections = selections")
                    .build()
            )
            .addMethod(
                MethodSpec.methodBuilder(property.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(selectionType(property), "selection")
                    .returns(returnType)
                    .addStatement("selections[\$L] = selection", property.index)
                    .addStatement("return new \$T(selections)", returnType)
                    .build()
            )
            .build()
    }
}

private fun TypedTupleType.createTupleCode(tupleClass: ClassName): CodeBlock {
    return when (val plan = construction) {
        is TypedTupleJavaPositionalPlan -> CodeBlock.builder()
            .add("return new \$T(\n", tupleClass)
            .indent()
            .apply {
                plan.arguments.forEachIndexed { index, argument ->
                    if (index != 0) {
                        add(",\n")
                    }
                    val property = properties[argument.propertyIndex]
                    add("(\$T)args[\$L]", property.type.toJavaTypeName().box(), property.index)
                }
            }
            .unindent()
            .add("\n);\n")
            .build()
        is TypedTupleJavaSetterPlan -> CodeBlock.builder()
            .addStatement("\$T __tuple = new \$T()", tupleClass, tupleClass)
            .apply {
                plan.assignments.forEach { assignment ->
                    val property = properties[assignment.propertyIndex]
                    addStatement(
                        "__tuple.\$L((\$T)args[\$L])",
                        assignment.setterName,
                        property.type.toJavaTypeName().box(),
                        property.index,
                    )
                }
            }
            .addStatement("return __tuple")
            .build()
        else -> error("Java typed tuple '${id.value}' has unsupported construction plan '$plan'")
    }
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

private fun selectionType(property: TypedTupleProperty): ParameterizedTypeName {
    return ParameterizedTypeName.get(SELECTION, property.type.toJavaTypeName().box())
}

private val SELECTION = ClassName.get("org.babyfish.jimmer.sql.ast", "Selection")
private val TUPLE_MAPPER = ClassName.get("org.babyfish.jimmer.sql.runtime", "TupleMapper")
private val LIST = ClassName.get(List::class.java)
private val COLLECTIONS = ClassName.get(java.util.Collections::class.java)
private val ARRAYS = ClassName.get(java.util.Arrays::class.java)
private val SELECTION_OF_ANY = ParameterizedTypeName.get(
    SELECTION,
    WildcardTypeName.subtypeOf(Any::class.java),
)
private val SELECTION_ARRAY = ArrayTypeName.of(SELECTION_OF_ANY)
