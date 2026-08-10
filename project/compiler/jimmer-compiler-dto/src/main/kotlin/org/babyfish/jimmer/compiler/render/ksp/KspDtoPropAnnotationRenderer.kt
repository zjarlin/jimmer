package org.babyfish.jimmer.compiler.render.ksp

import site.addzero.lsi.anno.copy
import com.squareup.kotlinpoet.AnnotationSpec
import org.babyfish.jimmer.compiler.dto.dtoAnnotationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.propertyAnnotationPoetAnnotations
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoKotlinPropertyShape
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.kotlinPropertyPlacement
import site.addzero.lsi.jimmer.dto.propertyAnnotationApplications
import site.addzero.lsi.anno.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 属性注解按 Kotlin 属性形态渲染。 */
internal object KspDtoPropAnnotationRenderer {

    fun renderConcrete(
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
            shape = DtoKotlinPropertyShape.CONCRETE,
            excludedAnnotationQualifiedName = excludedAnnotationQualifiedName,
        )
    }

    fun renderAbstractAccessor(
        dtoProp: DtoProp,
        annotationContract: DtoAnnotationContract,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): List<AnnotationSpec> {
        return render(
            dtoProp = dtoProp,
            annotationContract = annotationContract,
            immutableSchema = immutableSchema,
            workspace = workspace,
            shape = DtoKotlinPropertyShape.ABSTRACT_ACCESSOR,
            excludedAnnotationQualifiedName = null,
        )
    }

    private fun render(
        dtoProp: DtoProp,
        annotationContract: DtoAnnotationContract,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        shape: DtoKotlinPropertyShape,
        excludedAnnotationQualifiedName: String?,
    ): List<AnnotationSpec> {
        val applications = dtoProp.propertyAnnotationApplications(annotationContract)
        val annotations = dtoProp.propertyAnnotationPoetAnnotations(
            annotationContract = annotationContract,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
        )
        require(applications.size == annotations.size) {
            "Frozen DTO property annotation applications and source annotations differ: ${dtoProp.id.value}"
        }
        val excludedTypeId = excludedAnnotationQualifiedName?.let(LsiSymbolId::type)
        val selectedAnnotations = applications.zip(annotations).mapNotNull { (application, annotation) ->
            if (application.annotation.type == excludedTypeId) {
                return@mapNotNull null
            }
            val placement = application.kotlinPropertyPlacement(shape) ?: return@mapNotNull null
            annotation.copy(useSiteTarget = placement.toLsiUseSiteTarget())
        }
        return LsiKotlinPoetRenderer().renderAnnotations(
            annotations = selectedAnnotations,
            typeNames = workspace.dtoAnnotationPoetTypeNames(selectedAnnotations),
        )
    }
}

private fun DtoAnnotationPlacement.toLsiUseSiteTarget(): LsiAnnotationUseSiteTarget {
    return when (this) {
        DtoAnnotationPlacement.FIELD -> LsiAnnotationUseSiteTarget.FIELD
        DtoAnnotationPlacement.GETTER -> LsiAnnotationUseSiteTarget.GETTER
        DtoAnnotationPlacement.SETTER -> LsiAnnotationUseSiteTarget.SETTER
        DtoAnnotationPlacement.PROPERTY -> LsiAnnotationUseSiteTarget.PROPERTY
        else -> error("Unsupported Kotlin DTO property annotation placement: $name")
    }
}
