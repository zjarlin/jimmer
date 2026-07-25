package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.descriptionAnnotationValueOrNull
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentNameStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetTypeName

private val DESCRIPTION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.client.Description")

/** 将冻结的 DTO 类型文档降低为 Description 注解。 */
internal fun DtoType.toDescriptionPoetAnnotationOrNull(): LsiPoetAnnotation? {
    return descriptionAnnotationValueOrNull()?.toDescriptionPoetAnnotation()
}

/** 将冻结的 DTO 属性文档降低为 Description 注解。 */
internal fun DtoProp.toDescriptionPoetAnnotationOrNull(
    graph: DtoGraph,
): LsiPoetAnnotation? {
    return descriptionAnnotationValueOrNull(graph)?.toDescriptionPoetAnnotation()
}

private fun String.toDescriptionPoetAnnotation(): LsiPoetAnnotation {
    return LsiPoetAnnotation(
        type = DESCRIPTION_TYPE_ID,
        arguments = listOf(
            LsiPoetAnnotationArgument.Named(
                name = "value",
                value = LsiPoetAnnotationValue.StringValue(this),
                nameStyle = LsiPoetAnnotationArgumentNameStyle.VERBATIM,
            ),
        ),
    )
}

internal val DTO_DESCRIPTION_POET_TYPE_NAME = LsiPoetTypeName(
    typeId = DESCRIPTION_TYPE_ID,
    packageName = "org.babyfish.jimmer.client",
    simpleNames = listOf("Description"),
)
