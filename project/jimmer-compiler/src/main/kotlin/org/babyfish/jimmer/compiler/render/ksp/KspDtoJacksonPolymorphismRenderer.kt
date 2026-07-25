package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.jacksonPolymorphismPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toJacksonPolymorphicRootPoetAnnotations
import org.babyfish.jimmer.compiler.dto.toJacksonPolymorphicTypeNamePoetAnnotationOrNull
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 Jackson 多态注解渲染为 KotlinPoet 结构。 */
internal object KspDtoJacksonPolymorphismRenderer {

    fun renderRootAnnotations(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        annotationContract: DtoAnnotationContract,
        generatedPackageName: String,
        generatedSimpleNames: List<String>,
    ): List<AnnotationSpec> {
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            generatedPackageName,
            generatedSimpleNames,
        )
        return LsiKotlinPoetRenderer().renderAnnotations(
            dtoType.toJacksonPolymorphicRootPoetAnnotations(
                graph = graph,
                immutableSchema = immutableSchema,
                annotationContract = annotationContract,
                generatedRootTypeName = generatedRootTypeName,
                targetLanguage = LsiLanguage.KOTLIN,
            ),
            dtoType.jacksonPolymorphismPoetTypeNames(generatedRootTypeName),
        )
    }

    fun renderBranchTypeName(
        rootType: DtoType,
        branch: DtoPolymorphicBranch,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        annotationContract: DtoAnnotationContract,
        generatedPackageName: String,
        generatedRootSimpleNames: List<String>,
    ): AnnotationSpec? {
        val annotation = branch.toJacksonPolymorphicTypeNamePoetAnnotationOrNull(
            rootType = rootType,
            graph = graph,
            immutableSchema = immutableSchema,
            annotationContract = annotationContract,
        ) ?: return null
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            generatedPackageName,
            generatedRootSimpleNames,
        )
        return LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            rootType.jacksonPolymorphismPoetTypeNames(generatedRootTypeName),
        )
    }
}
