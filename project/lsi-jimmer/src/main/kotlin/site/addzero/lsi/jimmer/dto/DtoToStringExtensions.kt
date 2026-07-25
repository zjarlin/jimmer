package site.addzero.lsi.jimmer.dto

/** DTO 属性写入 toString 结果的条件。 */
enum class DtoToStringInclusion {
    ALWAYS,
    WHEN_LOADED,
    WHEN_NON_NULL,
}

/** 返回可见 DTO 属性写入 toString 结果的条件。 */
fun DtoProp.toStringInclusion(graph: DtoGraph): DtoToStringInclusion {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(id in ownerType.propIds) {
        "DTO toString property must be visible in its owner type: ${id.value}"
    }
    if (requiresDtoLoadedStateStorage(graph)) {
        return DtoToStringInclusion.WHEN_LOADED
    }
    if (this !is DtoBaseProp || DtoModifier.INPUT !in ownerType.modifiers || !nullable) {
        return DtoToStringInclusion.ALWAYS
    }
    return when (inputModifier) {
        DtoModifier.FUZZY -> DtoToStringInclusion.WHEN_NON_NULL
        else -> DtoToStringInclusion.ALWAYS
    }
}
