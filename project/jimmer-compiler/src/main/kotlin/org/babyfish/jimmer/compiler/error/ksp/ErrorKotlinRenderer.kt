package org.babyfish.jimmer.compiler.error.ksp

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import org.babyfish.jimmer.compiler.error.ErrorCodeModel
import org.babyfish.jimmer.compiler.error.ErrorFamilyModel
import org.babyfish.jimmer.compiler.error.ErrorFieldModel
import org.babyfish.jimmer.compiler.error.ErrorPrecompiledSchema
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiWorkspace

class ErrorKotlinRenderer {
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
        val exceptionClass = ClassName(family.packageName, family.exceptionSimpleName)
        val content = FileSpec.builder(family.packageName, family.exceptionSimpleName)
            .indent("    ")
            .addType(familyType(family, enumClass, exceptionClass))
            .build()
            .toString()
        val qualifiedName = if (family.packageName.isEmpty()) {
            family.exceptionSimpleName
        } else {
            "${family.packageName}.${family.exceptionSimpleName}"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(family.id),
            originatingSources = workspace.originatingSources(setOf(family.id)),
        )
    }

    private fun familyType(
        family: ErrorFamilyModel,
        enumClass: ClassName,
        exceptionClass: ClassName,
    ): TypeSpec {
        return TypeSpec.classBuilder(family.exceptionSimpleName)
            .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
            .superclass(if (family.checkedException) CODE_BASED_EXCEPTION else CODE_BASED_RUNTIME_EXCEPTION)
            .addSuperclassConstructorParameter("message")
            .addSuperclassConstructorParameter("cause")
            .addAnnotation(generatedBy(enumClass))
            .addAnnotation(clientException(family, exceptionClass))
            .apply {
                family.documentation?.let { documentation -> addKdoc("$documentation\n") }
                addPrimaryConstructor(family.declaredFields, family.declaredFields, sharedFieldCount = 0)
                addProperty(enumProperty(enumClass, null))
                addProperty(fieldsProperty(family.declaredFields))
                addType(companionType(family, exceptionClass))
                family.codes.forEach { code ->
                    addType(codeType(family, code, enumClass, exceptionClass))
                }
            }
            .build()
    }

    private fun companionType(
        family: ErrorFamilyModel,
        exceptionClass: ClassName,
    ): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .apply {
                family.codes.forEach { code ->
                    addFunction(factoryFunction(code, exceptionClass))
                }
            }
            .build()
    }

    private fun factoryFunction(
        code: ErrorCodeModel,
        exceptionClass: ClassName,
    ): FunSpec {
        val nestedClass = exceptionClass.nestedClass(code.exceptionSimpleName)
        return FunSpec.builder(code.creatorName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(JVM_STATIC)
            .addParameter(
                ParameterSpec.builder("message", STRING.copy(nullable = true))
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("cause", THROWABLE.copy(nullable = true))
                    .defaultValue("null")
                    .build()
            )
            .apply { code.fields.forEach { field -> addParameter(field.toKotlinParameter()) } }
            .returns(nestedClass)
            .addCode(
                CodeBlock.builder()
                    .add("return %T(\n", nestedClass)
                    .indent()
                    .add("message,\n")
                    .add("cause")
                    .apply { code.fields.forEach { field -> add(",\n%N", field.name) } }
                    .unindent()
                    .add("\n)\n")
                    .build()
            )
            .build()
    }

    private fun codeType(
        family: ErrorFamilyModel,
        code: ErrorCodeModel,
        enumClass: ClassName,
        exceptionClass: ClassName,
    ): TypeSpec {
        return TypeSpec.classBuilder(code.exceptionSimpleName)
            .addModifiers(KModifier.PUBLIC)
            .superclass(exceptionClass)
            .apply {
                addSuperclassConstructorParameter("message")
                addSuperclassConstructorParameter("cause")
                family.declaredFields.forEach { field -> addSuperclassConstructorParameter("%N", field.name) }
            }
            .addAnnotation(
                AnnotationSpec.builder(CLIENT_EXCEPTION)
                    .addMember("family = %S", family.family)
                    .addMember("code = %S", code.code)
                    .build()
            )
            .apply {
                code.documentation?.let { documentation -> addKdoc("$documentation\n") }
                addPrimaryConstructor(code.declaredFields, code.fields, family.declaredFields.size)
                addProperty(enumProperty(enumClass, code))
                addProperty(fieldsProperty(code.fields))
            }
            .build()
    }

    private fun TypeSpec.Builder.addPrimaryConstructor(
        declaredFields: List<ErrorFieldModel>,
        allFields: List<ErrorFieldModel>,
        sharedFieldCount: Int,
    ) {
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    ParameterSpec.builder("message", STRING.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("cause", THROWABLE.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .apply { allFields.forEach { field -> addParameter(field.toKotlinParameter()) } }
                .build()
        )
        declaredFields.forEach { field ->
            val parameterIndex = allFields.indexOf(field)
            if (parameterIndex < sharedFieldCount) {
                return@forEach
            }
            addProperty(
                PropertySpec.builder(field.name, field.kotlinType())
                    .addModifiers(KModifier.PUBLIC)
                    .initializer(field.name)
                    .apply { field.documentation?.let { documentation -> addKdoc("$documentation\n") } }
                    .build()
            )
        }
    }

    private fun enumProperty(
        enumClass: ClassName,
        code: ErrorCodeModel?,
    ): PropertySpec {
        return PropertySpec.builder(enumClass.simpleName.replaceFirstChar(Char::lowercaseChar), enumClass)
            .addModifiers(KModifier.PUBLIC, if (code == null) KModifier.ABSTRACT else KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(JSON_IGNORE)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                    .build()
            )
            .apply {
                if (code != null) {
                    getter(
                        FunSpec.getterBuilder()
                            .addStatement("return %T.%L", enumClass, code.enumEntryName)
                            .build()
                    )
                }
            }
            .build()
    }

    private fun fieldsProperty(fields: List<ErrorFieldModel>): PropertySpec {
        return PropertySpec.builder(
            "fields",
            MAP.parameterizedBy(STRING, ANY.copy(nullable = true)),
        )
            .addModifiers(KModifier.OVERRIDE)
            .getter(
                FunSpec.getterBuilder()
                    .addCode(
                        if (fields.isEmpty()) {
                            CodeBlock.of("return emptyMap()\n")
                        } else {
                            CodeBlock.builder()
                                .add("return mapOf(\n")
                                .indent()
                                .apply {
                                    fields.forEachIndexed { index, field ->
                                        if (index > 0) add(",\n")
                                        add("%S to %N", field.name, field.name)
                                    }
                                }
                                .unindent()
                                .add("\n)\n")
                                .build()
                        }
                    )
                    .build()
            )
            .build()
    }

    private fun clientException(
        family: ErrorFamilyModel,
        exceptionClass: ClassName,
    ): AnnotationSpec {
        return AnnotationSpec.builder(CLIENT_EXCEPTION)
            .addMember("family = %S", family.family)
            .apply {
                if (family.codes.isNotEmpty()) {
                    val format = family.codes.joinToString(", ") { "%T::class" }
                    val types = family.codes.map { code ->
                        exceptionClass.nestedClass(code.exceptionSimpleName)
                    }.toTypedArray()
                    addMember("subTypes = [$format]", *types)
                }
            }
            .build()
    }

    private fun generatedBy(enumClass: ClassName): AnnotationSpec {
        return AnnotationSpec.builder(GENERATED_BY)
            .addMember("type = %T::class", enumClass)
            .build()
    }
}

private fun ErrorFieldModel.toKotlinParameter(): ParameterSpec {
    return ParameterSpec.builder(name, kotlinType())
        .apply { if (nullable) defaultValue("null") }
        .build()
}

private fun ErrorFieldModel.kotlinType(): TypeName {
    val elementType = type.toKotlinTypeName()
    val result = if (list) LIST.parameterizedBy(elementType) else elementType
    return result.copy(nullable = nullable)
}

private fun LsiTypeRef.toKotlinTypeName(): TypeName {
    return when (this) {
        is LsiPrimitiveType -> when (kind) {
            LsiPrimitiveKind.BOOLEAN -> BOOLEAN
            LsiPrimitiveKind.BYTE -> BYTE
            LsiPrimitiveKind.SHORT -> SHORT
            LsiPrimitiveKind.INT -> INT
            LsiPrimitiveKind.LONG -> LONG
            LsiPrimitiveKind.CHAR -> CHAR
            LsiPrimitiveKind.FLOAT -> FLOAT
            LsiPrimitiveKind.DOUBLE -> DOUBLE
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> UNIT
        }
        is LsiDeclaredType -> {
            val raw = KOTLIN_TYPES[declarationId.qualifiedTypeName()]
                ?: ClassName.bestGuess(declarationId.qualifiedTypeName())
            if (arguments.isEmpty()) {
                raw
            } else {
                raw.parameterizedBy(arguments.map { argument ->
                    when (argument.variance) {
                        LsiVariance.STAR -> STAR
                        LsiVariance.INVARIANT -> requireNotNull(argument.type).toKotlinTypeName()
                        LsiVariance.IN -> WildcardTypeName.consumerOf(requireNotNull(argument.type).toKotlinTypeName())
                        LsiVariance.OUT -> WildcardTypeName.producerOf(requireNotNull(argument.type).toKotlinTypeName())
                    }
                })
            }
        }
        is LsiArrayType -> ClassName("kotlin", "Array").parameterizedBy(elementType.toKotlinTypeName())
        is LsiTypeParameterRef -> TypeVariableName(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> ClassName.bestGuess(displayName.filterNot(Char::isWhitespace))
    }
}

private fun site.addzero.lsi.core.LsiSymbolId.qualifiedTypeName(): String {
    return requireTypeQualifiedName()
}

private val CLIENT_EXCEPTION = ClassName("org.babyfish.jimmer", "ClientException")
private val GENERATED_BY = ClassName("org.babyfish.jimmer.internal", "GeneratedBy")
private val CODE_BASED_EXCEPTION = ClassName("org.babyfish.jimmer.error", "CodeBasedException")
private val CODE_BASED_RUNTIME_EXCEPTION = ClassName("org.babyfish.jimmer.error", "CodeBasedRuntimeException")
private val JSON_IGNORE = ClassName("com.fasterxml.jackson.annotation", "JsonIgnore")
private val JVM_STATIC = ClassName("kotlin.jvm", "JvmStatic")
private val THROWABLE = ClassName("kotlin", "Throwable")

private val KOTLIN_TYPES = mapOf(
    "java.lang.Boolean" to BOOLEAN,
    "java.lang.Byte" to BYTE,
    "java.lang.Short" to SHORT,
    "java.lang.Integer" to INT,
    "java.lang.Long" to LONG,
    "java.lang.Character" to CHAR,
    "java.lang.Float" to FLOAT,
    "java.lang.Double" to DOUBLE,
    "java.lang.String" to STRING,
    "java.lang.Object" to ANY,
)
