package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.DTO_DESCRIPTION_POET_TYPE_NAME
import org.babyfish.jimmer.compiler.dto.toDescriptionPoetAnnotationOrNull
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将 DTO Description 注解渲染为 JavaPoet 结构。 */
internal object AptDtoDescriptionRenderer {

    @JvmStatic
    fun render(dtoType: DtoType): AnnotationSpec? {
        return dtoType.toDescriptionPoetAnnotationOrNull()?.render()
    }

    @JvmStatic
    fun render(dtoProp: DtoProp, graph: DtoGraph): AnnotationSpec? {
        return dtoProp.toDescriptionPoetAnnotationOrNull(graph)?.render()
    }

    private fun LsiAnnotation.render(): AnnotationSpec {
        return LsiJavaPoetRenderer().renderAnnotation(
            annotation = this,
            typeNames = listOf(DTO_DESCRIPTION_POET_TYPE_NAME),
        )
    }
}
