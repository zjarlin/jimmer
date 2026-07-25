package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.DTO_DESCRIPTION_POET_TYPE_NAME
import org.babyfish.jimmer.compiler.dto.toDescriptionPoetAnnotationOrNull
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将 DTO Description 注解渲染为 KotlinPoet 结构。 */
internal object KspDtoDescriptionRenderer {

    fun render(dtoType: DtoType): AnnotationSpec? {
        return dtoType.toDescriptionPoetAnnotationOrNull()?.render()
    }

    fun render(dtoProp: DtoProp, graph: DtoGraph): AnnotationSpec? {
        return dtoProp.toDescriptionPoetAnnotationOrNull(graph)?.render()
    }

    private fun site.addzero.lsi.poet.LsiPoetAnnotation.render(): AnnotationSpec {
        return LsiKotlinPoetRenderer().renderAnnotation(
            annotation = this,
            typeNames = listOf(DTO_DESCRIPTION_POET_TYPE_NAME),
        )
    }
}
