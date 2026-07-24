package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.dtoAnnotationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.propertyAnnotationPoetAnnotations
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.propertyAnnotationApplications
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 属性注解按 Java 字段或 getter 落点渲染。 */
internal object AptDtoPropAnnotationRenderer {

    @JvmStatic
    fun renderField(
        dtoProp: DtoProp,
        annotationContract: DtoAnnotationContract,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        excludedAnnotationQualifiedName: String?,
    ): List<AnnotationSpec> {
        return render(
            dtoProp = dtoProp,
            annotationContract = annotationContract,
            immutableSchema = immutableSchema,
            workspace = workspace,
            placement = DtoAnnotationPlacement.FIELD,
            excludedAnnotationQualifiedName = excludedAnnotationQualifiedName,
        )
    }

    @JvmStatic
    fun renderGetter(
        dtoProp: DtoProp,
        annotationContract: DtoAnnotationContract,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        excludedAnnotationQualifiedName: String?,
    ): List<AnnotationSpec> {
        return render(
            dtoProp = dtoProp,
            annotationContract = annotationContract,
            immutableSchema = immutableSchema,
            workspace = workspace,
            placement = DtoAnnotationPlacement.GETTER,
            excludedAnnotationQualifiedName = excludedAnnotationQualifiedName,
        )
    }

    private fun render(
        dtoProp: DtoProp,
        annotationContract: DtoAnnotationContract,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        placement: DtoAnnotationPlacement,
        excludedAnnotationQualifiedName: String?,
    ): List<AnnotationSpec> {
        val applications = dtoProp.propertyAnnotationApplications(annotationContract)
        val annotations = dtoProp.propertyAnnotationPoetAnnotations(
            annotationContract = annotationContract,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
        )
        require(applications.size == annotations.size) {
            "Frozen DTO property annotation applications and source annotations differ: ${dtoProp.id.value}"
        }
        val excludedTypeId = excludedAnnotationQualifiedName?.let(LsiSymbolId::type)
        val selectedAnnotations = applications.zip(annotations).mapNotNull { (application, annotation) ->
            annotation.takeIf {
                placement in application.placements && application.annotation.type != excludedTypeId
            }
        }
        return LsiJavaPoetRenderer().renderAnnotations(
            annotations = selectedAnnotations,
            typeNames = workspace.dtoAnnotationPoetTypeNames(selectedAnnotations),
        )
    }
}
