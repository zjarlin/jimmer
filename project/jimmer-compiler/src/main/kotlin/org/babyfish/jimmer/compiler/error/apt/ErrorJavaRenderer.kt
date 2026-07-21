package org.babyfish.jimmer.compiler.error.apt

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
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.compiler.error.ErrorCodeModel
import org.babyfish.jimmer.compiler.error.ErrorFamilyModel
import org.babyfish.jimmer.compiler.error.ErrorFieldModel
import org.babyfish.jimmer.compiler.error.ErrorPrecompiledSchema
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class ErrorJavaRenderer {
    fun render(
        schema: ErrorPrecompiledSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        return schema.families.map { family -> render(family, workspace) }
    }

    private fun render(
        family: ErrorFamilyModel,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val enumClass = ClassName.bestGuess(family.qualifiedName)
        val exceptionClass = ClassName.get(family.packageName, family.exceptionSimpleName)
        val type = TypeSpec.classBuilder(family.exceptionSimpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .superclass(if (family.checkedException) CODE_BASED_EXCEPTION else CODE_BASED_RUNTIME_EXCEPTION)
            .addAnnotation(generatedBy(enumClass))
            .addAnnotation(clientException(family, exceptionClass))
            .apply {
                family.documentation?.let { documentation -> addJavadoc("$documentation\n") }
                addCommonMembers(family.declaredFields, family.declaredFields, null, exceptionClass)
                addMethod(
                    MethodSpec.methodBuilder("get${enumClass.simpleName()}")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotation(JSON_IGNORE)
                        .returns(enumClass)
                        .build()
                )
                family.codes.forEach { code ->
                    addCreatorMethods(code, exceptionClass)
                }
                family.codes.forEach { code ->
                    addType(codeType(family, code, enumClass, exceptionClass))
                }
            }
            .build()
        val content = JavaFile.builder(family.packageName, type)
            .indent("    ")
            .build()
            .toString()
        val qualifiedName = if (family.packageName.isEmpty()) {
            family.exceptionSimpleName
        } else {
            "${family.packageName}.${family.exceptionSimpleName}"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(family.id),
            originatingSources = workspace.originatingSources(setOf(family.id)),
        )
    }

    private fun TypeSpec.Builder.addCreatorMethods(
        code: ErrorCodeModel,
        exceptionClass: ClassName,
    ) {
        addMethod(creatorMethod(code, exceptionClass, withMessage = false, withCause = false))
        addMethod(creatorMethod(code, exceptionClass, withMessage = true, withCause = false))
        addMethod(creatorMethod(code, exceptionClass, withMessage = true, withCause = true))
    }

    private fun creatorMethod(
        code: ErrorCodeModel,
        exceptionClass: ClassName,
        withMessage: Boolean,
        withCause: Boolean,
    ): MethodSpec {
        val nestedClass = exceptionClass.nestedClass(code.exceptionSimpleName)
        return MethodSpec.methodBuilder(code.creatorName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(nestedClass)
            .apply {
                if (withMessage) {
                    addParameter(
                        ParameterSpec.builder(ClassName.get(String::class.java), "message")
                            .addAnnotation(NON_NULL)
                            .build()
                    )
                }
                if (withCause) {
                    addParameter(
                        ParameterSpec.builder(ClassName.get(Throwable::class.java), "cause")
                            .addAnnotation(NULLABLE)
                            .build()
                    )
                }
                code.fields.forEach { field -> addParameter(field.toJavaParameter()) }
                addCode(
                    CodeBlock.builder()
                        .add("return new \$T(\n", nestedClass)
                        .indent()
                        .add(if (withMessage) "message" else "null")
                        .add(",\n")
                        .add(if (withCause) "cause" else "null")
                        .apply {
                            code.fields.forEach { field -> add(",\n\$N", field.name) }
                        }
                        .unindent()
                        .add("\n);\n")
                        .build()
                )
            }
            .build()
    }

    private fun codeType(
        family: ErrorFamilyModel,
        code: ErrorCodeModel,
        enumClass: ClassName,
        exceptionClass: ClassName,
    ): TypeSpec {
        return TypeSpec.classBuilder(code.exceptionSimpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .superclass(exceptionClass)
            .addAnnotation(
                AnnotationSpec.builder(CLIENT_EXCEPTION)
                    .addMember("family", "\$S", family.family)
                    .addMember("code", "\$S", code.code)
                    .build()
            )
            .apply {
                code.documentation?.let { documentation -> addJavadoc("$documentation\n") }
                addCommonMembers(code.declaredFields, code.fields, family.declaredFields, exceptionClass)
                addMethod(
                    MethodSpec.methodBuilder("get${enumClass.simpleName()}")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(JSON_IGNORE)
                        .addAnnotation(Override::class.java)
                        .returns(enumClass)
                        .addStatement("return \$T.\$L", enumClass, code.enumEntryName)
                        .build()
                )
                addMethod(fieldsMethod(code.fields))
            }
            .build()
    }

    private fun TypeSpec.Builder.addCommonMembers(
        declaredFields: List<ErrorFieldModel>,
        allFields: List<ErrorFieldModel>,
        sharedFields: List<ErrorFieldModel>?,
        exceptionClass: ClassName,
    ) {
        declaredFields.forEach { field ->
            addField(
                FieldSpec.builder(field.javaType(), field.name)
                    .addModifiers(Modifier.FINAL)
                    .addAnnotation(field.nullabilityAnnotation())
                    .build()
            )
        }
        addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String::class.java, "message")
                .addParameter(Throwable::class.java, "cause")
                .apply {
                    allFields.forEach { field -> addParameter(field.toJavaParameter()) }
                    if (sharedFields == null) {
                        addStatement("super(message, cause)")
                    } else {
                        val arguments = CodeBlock.builder().add("message, cause")
                        sharedFields.forEach { field -> arguments.add(", \$N", field.name) }
                        addStatement("super(\$L)", arguments.build())
                    }
                    declaredFields.forEach { field ->
                        addStatement("this.\$N = \$N", field.name, field.name)
                    }
                }
                .build()
        )
        declaredFields.forEach { field ->
            addMethod(field.getter())
        }
    }

    private fun fieldsMethod(fields: List<ErrorFieldModel>): MethodSpec {
        val returnType = ParameterizedTypeName.get(MAP, STRING, OBJECT)
        return MethodSpec.methodBuilder("getFields")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(returnType)
            .apply {
                when (fields.size) {
                    0 -> addStatement("return \$T.emptyMap()", COLLECTIONS)
                    1 -> addStatement(
                        "return \$T.singletonMap(\$S, \$N)",
                        COLLECTIONS,
                        fields.single().name,
                        fields.single().name,
                    )
                    else -> {
                        addStatement("\$T<String, Object> fields = new \$T<>()", MAP, LINKED_HASH_MAP)
                        fields.forEach { field ->
                            addStatement("fields.put(\$S, \$N)", field.name, field.name)
                        }
                        addStatement("return fields")
                    }
                }
            }
            .build()
    }

    private fun clientException(
        family: ErrorFamilyModel,
        exceptionClass: ClassName,
    ): AnnotationSpec {
        val builder = AnnotationSpec.builder(CLIENT_EXCEPTION)
            .addMember("family", "\$S", family.family)
        if (family.codes.isNotEmpty()) {
            val format = family.codes.joinToString(", ") { "\$T.class" }
            val types = family.codes.map { code -> exceptionClass.nestedClass(code.exceptionSimpleName) }.toTypedArray()
            builder.addMember("subTypes", "{$format}", *types)
        }
        return builder.build()
    }

    private fun generatedBy(enumClass: ClassName): AnnotationSpec {
        return AnnotationSpec.builder(GENERATED_BY)
            .addMember("type", "\$T.class", enumClass)
            .build()
    }
}

private fun ErrorFieldModel.getter(): MethodSpec {
    val prefix = if (type is LsiPrimitiveType && type.kind == LsiPrimitiveKind.BOOLEAN && !list) {
        "is"
    } else {
        "get"
    }
    return MethodSpec.methodBuilder(prefix + name.replaceFirstChar(Char::uppercaseChar))
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(nullabilityAnnotation())
        .returns(javaType())
        .apply { documentation?.let { doc -> addJavadoc("$doc\n") } }
        .addStatement("return \$N", name)
        .build()
}

private fun ErrorFieldModel.toJavaParameter(): ParameterSpec {
    return ParameterSpec.builder(javaType(), name)
        .addAnnotation(nullabilityAnnotation())
        .build()
}

private fun ErrorFieldModel.javaType(): TypeName {
    val elementType = type.toJavaTypeName()
    return if (list) {
        ParameterizedTypeName.get(LIST, elementType.box())
    } else {
        elementType
    }
}

private fun ErrorFieldModel.nullabilityAnnotation(): ClassName {
    return if (nullable) NULLABLE else NON_NULL
}

private val CLIENT_EXCEPTION = ClassName.get("org.babyfish.jimmer", "ClientException")
private val GENERATED_BY = ClassName.get("org.babyfish.jimmer.internal", "GeneratedBy")
private val CODE_BASED_EXCEPTION = ClassName.get("org.babyfish.jimmer.error", "CodeBasedException")
private val CODE_BASED_RUNTIME_EXCEPTION = ClassName.get("org.babyfish.jimmer.error", "CodeBasedRuntimeException")
private val JSON_IGNORE = ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnore")
private val NON_NULL = ClassName.get("org.jspecify.annotations", "NonNull")
private val NULLABLE = ClassName.get("org.jspecify.annotations", "Nullable")
private val LIST = ClassName.get(List::class.java)
private val MAP = ClassName.get(Map::class.java)
private val STRING = ClassName.get(String::class.java)
private val OBJECT = ClassName.get(Any::class.java)
private val COLLECTIONS = ClassName.get(java.util.Collections::class.java)
private val LINKED_HASH_MAP = ClassName.get(LinkedHashMap::class.java)
