package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
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
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 Jackson 多态注解渲染为 JavaPoet 结构。 */
internal object AptDtoJacksonPolymorphismRenderer {

    @JvmStatic
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
        return LsiJavaPoetRenderer().renderAnnotations(
            dtoType.toJacksonPolymorphicRootPoetAnnotations(
                graph = graph,
                immutableSchema = immutableSchema,
                annotationContract = annotationContract,
                generatedRootTypeName = generatedRootTypeName,
                targetLanguage = LsiLanguage.JAVA,
            ),
            dtoType.jacksonPolymorphismPoetTypeNames(generatedRootTypeName),
        )
    }

    @JvmStatic
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
        return LsiJavaPoetRenderer().renderAnnotation(
            annotation,
            rootType.jacksonPolymorphismPoetTypeNames(generatedRootTypeName),
        )
    }
}
