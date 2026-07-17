package org.babyfish.jimmer.compiler.transactional.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import org.babyfish.jimmer.compiler.render.ksp.toKotlinAnnotationSpec
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeName
import org.babyfish.jimmer.compiler.render.ksp.toKotlinTypeVariableName
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
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class TransactionalKotlinRenderer {

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
        val primaryConstructor = type.constructors.singleOrNull { constructor -> constructor.primary }
        val generatedType = TypeSpec.classBuilder(type.generatedSimpleName)
            .superclass(ClassName.bestGuess(type.qualifiedName))
            .apply {
                when (type.visibility) {
                    LsiVisibility.INTERNAL -> addModifiers(KModifier.INTERNAL)
                    LsiVisibility.PUBLIC -> addModifiers(KModifier.PUBLIC)
                    else -> Unit
                }
                if (type.modality == LsiModality.ABSTRACT) {
                    addModifiers(KModifier.ABSTRACT)
                }
                type.copiedAnnotations
                    .filter { annotation ->
                        annotation.useSiteTarget == null ||
                            annotation.useSiteTarget == LsiAnnotationUseSiteTarget.TYPE
                    }
                    .forEach { annotation -> addAnnotation(annotation.toKotlinAnnotationSpec()) }
                type.targetAnnotationTypeId?.let { annotationTypeId ->
                    addAnnotation(
                        AnnotationSpec.builder(
                            ClassName.bestGuess(annotationTypeId.requireTypeQualifiedName())
                        ).build()
                    )
                }
                if (primaryConstructor != null) {
                    primaryConstructor(primaryConstructor.toKotlinConstructor())
                    primaryConstructor.parameters.forEach { parameter ->
                        addSuperclassConstructorParameter(
                            "%L",
                            parameter.toKotlinArgument(),
                        )
                    }
                } else {
                    type.constructors.forEach { constructor ->
                        addFunction(
                            constructor.toKotlinConstructor().toBuilder()
                                .callSuperConstructor(
                                    constructor.parameters.map { parameter ->
                                        parameter.toKotlinArgument()
                                    }
                                )
                                .build()
                        )
                    }
                }
                type.methods.forEach { method -> addFunction(method.toKotlinFunction(type)) }
            }
            .build()
        val content = FileSpec.builder(type.packageName, type.generatedSimpleName)
            .indent("    ")
            .addAnnotation(
                AnnotationSpec.builder(SUPPRESS)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                    .addMember("%S", "warnings")
                    .build()
            )
            .addType(generatedType)
            .build()
            .toString()
        val qualifiedName = if (type.packageName.isEmpty()) {
            type.generatedSimpleName
        } else {
            "${type.packageName}.${type.generatedSimpleName}"
        }
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(type.id),
            originatingSources = workspace.originatingSources(setOf(type.id)),
        )
    }
}

private fun TransactionalConstructor.toKotlinConstructor(): FunSpec {
    return FunSpec.constructorBuilder()
        .apply {
            when (visibility) {
                LsiVisibility.PROTECTED -> addModifiers(KModifier.PROTECTED)
                LsiVisibility.INTERNAL -> addModifiers(KModifier.INTERNAL)
                LsiVisibility.PRIVATE -> addModifiers(KModifier.PRIVATE)
                LsiVisibility.PUBLIC -> Unit
                else -> Unit
            }
            copiedAnnotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.CONSTRUCTOR
                }
                .forEach { annotation -> addAnnotation(annotation.toKotlinAnnotationSpec()) }
            this@toKotlinConstructor.parameters.forEach { parameter ->
                addParameter(parameter.toKotlinParameter())
            }
            this@toKotlinConstructor.documentation?.let { documentation -> addKdoc("%L\n", documentation) }
        }
        .build()
}

private fun TransactionalMethod.toKotlinFunction(type: TransactionalType): FunSpec {
    return FunSpec.builder(name)
        .addModifiers(KModifier.OVERRIDE)
        .apply {
            when (visibility) {
                LsiVisibility.PROTECTED -> addModifiers(KModifier.PROTECTED)
                LsiVisibility.INTERNAL -> addModifiers(KModifier.INTERNAL)
                LsiVisibility.PUBLIC -> Unit
                else -> Unit
            }
            copiedAnnotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
                }
                .forEach { annotation -> addAnnotation(annotation.toKotlinAnnotationSpec()) }
            this@toKotlinFunction.typeParameters.forEach { parameter ->
                addTypeVariable(parameter.toKotlinTypeVariableName())
            }
            this@toKotlinFunction.parameters.forEach { parameter ->
                addParameter(parameter.toKotlinParameter())
            }
            returns(
                this@toKotlinFunction.returnType.toKotlinTypeName(
                    copiedAnnotations.filter { annotation ->
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
                    }
                )
            )
            this@toKotlinFunction.documentation?.let { documentation -> addKdoc("%L\n", documentation) }
            addCode(
                CodeBlock.builder()
                    .beginControlFlow(
                        "return this.%N.transaction(%T.%L)",
                        type.sqlClient.name,
                        PROPAGATION,
                        propagation,
                    )
                    .addStatement(
                        "super.%N(%L)",
                        name,
                        this@toKotlinFunction.parameters.joinToCodeBlock(
                            TransactionalParameter::toKotlinArgument
                        ),
                    )
                    .endControlFlow()
                    .build()
            )
        }
        .build()
}

private fun TransactionalParameter.toKotlinParameter(): ParameterSpec {
    return ParameterSpec.builder(name, type.toKotlinTypeName())
        .apply {
            if (this@toKotlinParameter.vararg) {
                addModifiers(KModifier.VARARG)
            }
            this@toKotlinParameter.annotations
                .filter { annotation ->
                    annotation.useSiteTarget == null ||
                        annotation.useSiteTarget == LsiAnnotationUseSiteTarget.PARAMETER
                }
                .forEach { annotation ->
                    addAnnotation(annotation.copy(useSiteTarget = null).toKotlinAnnotationSpec())
                }
        }
        .build()
}

private fun TransactionalParameter.toKotlinArgument(): CodeBlock {
    return if (vararg) {
        CodeBlock.of("*%N", name)
    } else {
        CodeBlock.of("%N", name)
    }
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
                add("%L", transform(value))
            }
        }
        .build()
}

private val PROPAGATION = ClassName("org.babyfish.jimmer.sql.transaction", "Propagation")
private val SUPPRESS = ClassName("kotlin", "Suppress")
