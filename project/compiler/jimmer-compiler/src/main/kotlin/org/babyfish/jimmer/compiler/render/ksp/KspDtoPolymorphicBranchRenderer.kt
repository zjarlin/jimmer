package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.polymorphicDtoBranchPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toGeneratedPolymorphicDtoBranchPoetAnnotation
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享多态 DTO 分支标记渲染为 KotlinPoet 结构。 */
internal object KspDtoPolymorphicBranchRenderer {

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
        return LsiKotlinPoetRenderer().renderAnnotation(
            annotation = branch.toGeneratedPolymorphicDtoBranchPoetAnnotation(
                rootType = rootType,
                generatedRootTypeName = generatedRootTypeName,
            ),
            typeNames = polymorphicDtoBranchPoetTypeNames(generatedRootTypeName),
        )
    }
}
