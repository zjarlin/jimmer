package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.model.sourceLsiAnnotation

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.descriptionAnnotationValueOrNull
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentNameStyle
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiTypeName

private val DESCRIPTION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.client.Description")

/** 将冻结的 DTO 类型文档降低为 Description 注解。 */
internal fun DtoType.toDescriptionPoetAnnotationOrNull(): LsiAnnotation? {
    return descriptionAnnotationValueOrNull()?.toDescriptionPoetAnnotation()
}

/** 将冻结的 DTO 属性文档降低为 Description 注解。 */
internal fun DtoProp.toDescriptionPoetAnnotationOrNull(
    graph: DtoGraph,
): LsiAnnotation? {
    return descriptionAnnotationValueOrNull(graph)?.toDescriptionPoetAnnotation()
}

private fun String.toDescriptionPoetAnnotation(): LsiAnnotation {
    return sourceLsiAnnotation(
        type = DESCRIPTION_TYPE_ID,
        arguments = listOf(
            LsiSourceAnnotationArgument.Named(
                name = "value",
                value = LsiAnnotationValue.StringValue(this),
                nameStyle = LsiAnnotationArgumentNameStyle.VERBATIM,
            ),
        ),
    )
}

internal val DTO_DESCRIPTION_POET_TYPE_NAME = LsiTypeName(
    typeId = DESCRIPTION_TYPE_ID,
    packageName = "org.babyfish.jimmer.client",
    simpleNames = listOf("Description"),
)
