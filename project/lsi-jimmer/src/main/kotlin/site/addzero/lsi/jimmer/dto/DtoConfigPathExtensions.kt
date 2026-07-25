package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.targetIdPropOf

/** 返回配置路径节点引用的冻结不可变属性。 */
fun DtoPropPathNode.immutableProp(
    immutableSchema: ImmutableSchema,
): ImmutableProp = requireNotNull(immutableSchema.propsById[propId]) {
    "DTO config path references missing immutable property: ${propId.value}"
}

/** 返回配置路径实际参与比较的终值属性，关联主键路径解析为目标主键。 */
fun List<DtoPropPathNode>.terminalValueProp(
    immutableSchema: ImmutableSchema,
): ImmutableProp {
    val node = last()
    val prop = node.immutableProp(immutableSchema)
    if (!node.associatedId) {
        return prop
    }
    require(prop.association) {
        "DTO associated-id path must reference an association property: ${prop.id.value}"
    }
    return requireNotNull(immutableSchema.targetIdPropOf(prop)) {
        "DTO associated-id path target must declare an id property: ${prop.id.value}"
    }
}
