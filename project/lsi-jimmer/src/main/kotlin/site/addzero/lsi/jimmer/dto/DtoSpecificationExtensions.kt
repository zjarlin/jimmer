package site.addzero.lsi.jimmer.dto

/**
 * 返回 Specification 的 like/notLike 谓词需要追加的匹配参数。
 *
 * 参数顺序固定为忽略大小写、匹配开头、匹配结尾；其余谓词返回空。
 */
fun DtoBaseProp.specificationLikeOptionArguments(graph: DtoGraph): List<Boolean>? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(DtoModifier.SPECIFICATION in ownerType.modifiers) {
        "DTO property does not belong to a specification: ${id.value}"
    }
    val tailProp = tailProp(graph)
    if (tailProp.functionName != "like" && tailProp.functionName != "notLike") {
        return null
    }
    return listOf(
        DtoLikeOption.INSENSITIVE in tailProp.likeOptions,
        DtoLikeOption.MATCH_START in tailProp.likeOptions,
        DtoLikeOption.MATCH_END in tailProp.likeOptions,
    )
}
