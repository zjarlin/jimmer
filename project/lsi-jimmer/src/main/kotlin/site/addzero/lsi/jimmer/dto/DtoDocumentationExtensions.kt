package site.addzero.lsi.jimmer.dto

/** 返回生成类型的 Description 注解值；空文档不生成注解。 */
fun DtoType.descriptionAnnotationValueOrNull(): String? {
    return documentation?.takeIf(String::isNotEmpty)
}

/** 返回可见生成属性的 Description 注解值；隐藏路径节点不能独立生成注解。 */
fun DtoProp.descriptionAnnotationValueOrNull(graph: DtoGraph): String? {
    require(graph.propsById[id] == this) {
        "DTO property does not belong to this graph: ${id.value}"
    }
    val ownerType = graph.typesById.getValue(ownerTypeId)
    require(id in ownerType.propIds) {
        "DTO property is not visible in its owner type: ${id.value}"
    }
    return documentation?.takeIf(String::isNotEmpty)
}
