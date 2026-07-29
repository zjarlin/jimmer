package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.boundImmutableProp
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.jimmer.generatedPropsConstantName
import site.addzero.lsi.jimmer.generatedPropsTypeOf
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetCodeBlock

/** 将 DTO 属性的 converter 获取语义降低为平台中立的代码块。 */
internal fun DtoBaseProp.toLsiConverterLoadingPoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    forList: Boolean,
): LsiPoetCodeBlock {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO converter loading requires Java or Kotlin target language"
    }
    val immutableProp = tailProp(graph).boundImmutableProp(graph, immutableSchema)
    val entityAssociation = immutableSchema.isEntityAssociation(immutableProp)
    return LsiPoetCodeBlock.build {
        type(immutableSchema.generatedPropsTypeOf(immutableProp))
        text(".")
        name(immutableProp.generatedPropsConstantName())
        text(".unwrap().")
        name(if (entityAssociation) "getAssociatedIdConverter" else "getConverter")
        if (targetLanguage == LsiLanguage.KOTLIN) {
            text("<")
            type(KOTLIN_ANY_TYPE)
            text(", ")
            type(KOTLIN_ANY_TYPE)
            text(">")
        }
        text("(")
        if (entityAssociation) {
            literal(forList.toString())
        }
        text(")")
    }
}

private val KOTLIN_ANY_TYPE = LsiDeclaredType(LsiSymbolId.type("kotlin.Any"))
