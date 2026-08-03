package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.isLanguageFormula

/** 判断 DTO 基础属性是否不应写回 Draft。 */
fun DtoBaseProp.isDraftWriteSkipped(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): Boolean {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "Draft write skip check requires Java or Kotlin: $targetLanguage"
    }
    val formulaProp = if (targetLanguage == LsiLanguage.JAVA) this else tailProp(graph)
    if (formulaProp.boundImmutableProp(graph, immutableSchema).isLanguageFormula(targetLanguage)) {
        return true
    }
    return nextPropId == null &&
        tailProp(graph).boundImmutableProp(graph, immutableSchema).primaryMapping == PrimaryMapping.DISCRIMINATOR
}

/** 返回 Kotlin Draft 直接写回最终基础属性时使用的成员名。 */
fun DtoBaseProp.kotlinDraftValueWriterName(graph: DtoGraph): String {
    return tailProp(graph).baseProps.first().name
}
