package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.dtoAnnotationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.typeAnnotationPoetAnnotations
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.hasTypeAnnotation
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 类型注解渲染为 JavaPoet 结构。 */
internal object AptDtoTypeAnnotationRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        annotationContract: DtoAnnotationContract,
        workspace: LsiWorkspace,
    ): List<AnnotationSpec> {
        val annotations = dtoType.typeAnnotationPoetAnnotations(
            annotationContract = annotationContract,
            targetLanguage = LsiLanguage.JAVA,
        )
        return LsiJavaPoetRenderer().renderAnnotations(
            annotations = annotations,
            typeNames = workspace.dtoAnnotationPoetTypeNames(annotations),
        )
    }

    /** 将 JavaPoet 类型名边界适配为冻结契约的稳定类型身份查询。 */
    @JvmStatic
    fun hasTypeAnnotation(
        dtoType: DtoType,
        annotationContract: DtoAnnotationContract,
        annotationQualifiedName: String,
    ): Boolean {
        return dtoType.hasTypeAnnotation(
            annotationContract = annotationContract,
            annotationTypeId = LsiSymbolId.type(annotationQualifiedName),
        )
    }
}
