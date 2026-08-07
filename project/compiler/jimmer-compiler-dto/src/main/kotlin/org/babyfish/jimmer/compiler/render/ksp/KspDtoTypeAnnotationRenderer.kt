package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.dtoAnnotationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.typeAnnotationPoetAnnotations
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 类型注解渲染为 KotlinPoet 结构。 */
internal object KspDtoTypeAnnotationRenderer {

    fun render(
        dtoType: DtoType,
        annotationContract: DtoAnnotationContract,
        workspace: LsiWorkspace,
    ): List<AnnotationSpec> {
        val annotations = dtoType.typeAnnotationPoetAnnotations(
            annotationContract = annotationContract,
            targetLanguage = LsiLanguage.KOTLIN,
        )
        return LsiKotlinPoetRenderer().renderAnnotations(
            annotations = annotations,
            typeNames = workspace.dtoAnnotationPoetTypeNames(annotations),
        )
    }
}
