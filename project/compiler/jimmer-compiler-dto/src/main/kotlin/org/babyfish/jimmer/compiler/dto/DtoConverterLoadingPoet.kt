package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.boundImmutableProp
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.jimmer.generatedPropsConstantName
import site.addzero.lsi.jimmer.generatedPropsTypeOf
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder

/** 将 DTO 属性的 converter 获取语义降低为平台中立的代码块。 */
internal fun DtoBaseProp.toLsiConverterLoadingPoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    forList: Boolean,
    typeArguments: List<LsiType>,
): LsiCodeBlock {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO converter loading requires Java or Kotlin target language"
    }
    require(typeArguments.isEmpty() || typeArguments.size == 2) {
        "DTO converter loading requires zero or two type arguments"
    }
    val tailProp = tailProp(graph)
    val immutableProp = tailProp.boundImmutableProp(graph, immutableSchema)
    val entityAssociation = immutableSchema.isEntityAssociation(immutableProp)
    return LsiCodeBlock.build {
        type(immutableSchema.generatedPropsTypeOf(immutableProp))
        text(".")
        name(immutableProp.generatedPropsConstantName())
        text(".unwrap().")
        if (targetLanguage == LsiLanguage.JAVA) {
            appendConverterTypeArguments(typeArguments)
        }
        name(if (entityAssociation) "getAssociatedIdConverter" else "getConverter")
        if (targetLanguage == LsiLanguage.KOTLIN) {
            appendConverterTypeArguments(typeArguments)
        }
        text("(")
        if (entityAssociation) {
            literal(forList.toString())
        } else if (
            forList &&
            (tailProp.functionName == "valueIn" || tailProp.functionName == "valueNotIn")
        ) {
            literal("true")
        }
        text(")")
    }
}

private fun LsiCodeBuilder.appendConverterTypeArguments(typeArguments: List<LsiType>) {
    if (typeArguments.isEmpty()) {
        return
    }
    text("<")
    typeArguments.forEachIndexed { index, typeArgument ->
        if (index != 0) {
            text(", ")
        }
        type(typeArgument)
    }
    text(">")
}
