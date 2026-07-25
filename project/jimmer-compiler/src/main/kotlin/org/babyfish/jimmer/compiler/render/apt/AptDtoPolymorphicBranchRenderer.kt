package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.polymorphicDtoBranchPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toGeneratedPolymorphicDtoBranchPoetAnnotation
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享多态 DTO 分支标记渲染为 JavaPoet 结构。 */
internal object AptDtoPolymorphicBranchRenderer {

    @JvmStatic
    fun render(
        rootType: DtoType,
        branch: DtoPolymorphicBranch,
        generatedPackageName: String,
        generatedRootSimpleNames: List<String>,
    ): AnnotationSpec {
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            generatedPackageName,
            generatedRootSimpleNames,
        )
        return LsiJavaPoetRenderer().renderAnnotation(
            annotation = branch.toGeneratedPolymorphicDtoBranchPoetAnnotation(
                rootType = rootType,
                generatedRootTypeName = generatedRootTypeName,
            ),
            typeNames = polymorphicDtoBranchPoetTypeNames(generatedRootTypeName),
        )
    }
}
