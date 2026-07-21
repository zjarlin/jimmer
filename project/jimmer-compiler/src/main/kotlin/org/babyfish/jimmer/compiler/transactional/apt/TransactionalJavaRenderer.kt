package org.babyfish.jimmer.compiler.transactional.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.compiler.render.apt.toJavaAnnotationSpec
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeVariableName
import org.babyfish.jimmer.compiler.transactional.TransactionalConstructor
import org.babyfish.jimmer.compiler.transactional.TransactionalMethod
import org.babyfish.jimmer.compiler.transactional.TransactionalParameter
import org.babyfish.jimmer.compiler.transactional.TransactionalPrecompiledSchema
import org.babyfish.jimmer.compiler.transactional.TransactionalType
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class TransactionalJavaRenderer {

    fun render(
        schema: TransactionalPrecompiledSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        return schema.types.map { type -> render(type, workspace) }
    }

    private fun render(
        type: TransactionalType,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val generatedType = TypeSpec.classBuilder(type.generatedSimpleName)
            .superclass(ClassName.bestGuess(type.qualifiedName))
            .apply {
                if (type.visibility == LsiVisibility.PUBLIC) {
                    addModifiers(Modifier.PUBLIC)
                }
                if (type.modality == LsiModality.ABSTRACT) {
                    addModifiers(Modifier.ABSTRACT)
                }
                type.copiedAnnotations
                    .filter { annotation ->
                        annotation.useSiteTarget == null ||
                            annotation.useSiteTarget == LsiAnnotationUseSiteTarget.TYPE
                    }
                    .forEach { annotation -> addAnnotation(annotation.toJavaAnnotationSpec()) }
                type.targetAnnotationTypeId?.let { annotationTypeId ->
                    addAnnotation(
                        AnnotationSpec.builder(
                            ClassName.bestGuess(annotationTypeId.requireTypeQualifiedName())
                        ).build()
                    )
                }
                type.constructors.forEach { constructor -> addMethod(constructor.toJavaConstructor()) }
                type.methods.forEach { method -> addMethod(method.toJavaMethod(type)) }
            }
            .build()
        val content = JavaFile.builder(type.packageName, generatedType)
            .indent("    ")
            .build()
            .toString()
        val qualifiedName = if (type.packageName.isEmpty()) {
            type.generatedSimpleName
        } else {
            "${type.packageName}.${type.generatedSimpleName}"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(type.id),
            originatingSources = workspace.originatingSources(setOf(type.id)),
        )
    }
}

private fun TransactionalConstructor.toJavaConstructor(): MethodSpec {
    return MethodSpec.constructorBuilder()
        .apply {
            this@toJavaConstructor.typeParameters.forEach { parameter ->
                addTypeVariable(parameter.toJavaTypeVariableName())
            }
            copiedAnnotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.CONSTRUCTOR
                }
                .forEach { annotation -> addAnnotation(annotation.toJavaAnnotationSpec()) }
            this@toJavaConstructor.parameters.forEach { parameter ->
                addParameter(parameter.toJavaParameter())
            }
            if (this@toJavaConstructor.parameters.lastOrNull()?.vararg == true) {
                varargs(true)
            }
            this@toJavaConstructor.thrownTypes.forEach { thrownType ->
                addException(thrownType.toJavaTypeName())
            }
            this@toJavaConstructor.documentation?.let { documentation -> addJavadoc("\$L\n", documentation) }
            addStatement(
                "super(\$L)",
                this@toJavaConstructor.parameters.joinToCodeBlock { parameter ->
                    CodeBlock.of("\$N", parameter.name)
                },
            )
        }
        .build()
}

private fun TransactionalMethod.toJavaMethod(type: TransactionalType): MethodSpec {
    val returnsVoid = returnType is LsiPrimitiveType &&
        returnType.kind in setOf(LsiPrimitiveKind.UNIT, LsiPrimitiveKind.VOID)
    return MethodSpec.methodBuilder(name)
        .addAnnotation(Override::class.java)
        .apply {
            when (visibility) {
                LsiVisibility.PUBLIC -> addModifiers(Modifier.PUBLIC)
                LsiVisibility.PROTECTED -> addModifiers(Modifier.PROTECTED)
                else -> Unit
            }
            copiedAnnotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
                }
                .forEach { annotation -> addAnnotation(annotation.toJavaAnnotationSpec()) }
            this@toJavaMethod.typeParameters.forEach { parameter ->
                addTypeVariable(parameter.toJavaTypeVariableName())
            }
            returns(
                this@toJavaMethod.returnType.toJavaTypeName(
                    copiedAnnotations.filter { annotation ->
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
                    }
                )
            )
            this@toJavaMethod.parameters.forEach { parameter ->
                addParameter(parameter.toJavaParameter())
            }
            if (this@toJavaMethod.parameters.lastOrNull()?.vararg == true) {
                varargs(true)
            }
            this@toJavaMethod.thrownTypes.forEach { thrownType -> addException(thrownType.toJavaTypeName()) }
            this@toJavaMethod.documentation?.let { documentation -> addJavadoc("\$L\n", documentation) }
            addCode(transactionCode(type, returnsVoid))
        }
        .build()
}

private fun TransactionalMethod.transactionCode(
    type: TransactionalType,
    returnsVoid: Boolean,
): CodeBlock {
    val arguments = parameters.joinToCodeBlock { parameter -> CodeBlock.of("\$N", parameter.name) }
    return CodeBlock.builder()
        .apply {
            if (!returnsVoid) {
                add("return ")
            }
            add(
                "\$N.transaction(\$T.\$L, () ->  {\n",
                type.sqlClient.name,
                PROPAGATION,
                propagation,
            )
            indent()
            if (!returnsVoid) {
                add("return ")
            }
            add("super.\$N(\$L);\n", name, arguments)
            if (returnsVoid) {
                add("return null;\n")
            }
            unindent()
            add("});\n")
        }
        .build()
}

private fun TransactionalParameter.toJavaParameter(): ParameterSpec {
    val javaType = type.toJavaTypeName().let { elementType ->
        if (vararg) ArrayTypeName.of(elementType) else elementType
    }
    return ParameterSpec.builder(javaType, name)
        .apply {
            this@toJavaParameter.annotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.PARAMETER
                }
                .forEach { annotation -> addAnnotation(annotation.toJavaAnnotationSpec()) }
        }
        .build()
}

private inline fun <T> Iterable<T>.joinToCodeBlock(
    transform: (T) -> CodeBlock,
): CodeBlock {
    return CodeBlock.builder()
        .apply {
            this@joinToCodeBlock.forEachIndexed { index, value ->
                if (index != 0) {
                    add(", ")
                }
                add("\$L", transform(value))
            }
        }
        .build()
}

private val PROPAGATION = ClassName.get("org.babyfish.jimmer.sql.transaction", "Propagation")
