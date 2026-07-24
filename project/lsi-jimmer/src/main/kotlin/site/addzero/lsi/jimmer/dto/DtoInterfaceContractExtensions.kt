package site.addzero.lsi.jimmer.dto

/** 返回指定冻结 DTO 类型的接口契约。 */
fun DtoInterfaceContractResolution.contractFor(type: DtoType): DtoInterfaceContract =
    requireNotNull(contractsByTypeId[type.id]) {
        "No DTO interface contract for frozen type '${type.id.value}'"
    }

/** 返回生成 DTO 必须实现的属性名。 */
fun DtoInterfaceContract.requiredPropNames(): Set<String> =
    props.mapTo(linkedSetOf(), DtoInterfacePropContract::name)

/** 返回生成 Java DTO 必须实现的访问器名。 */
fun DtoInterfaceContract.requiredAccessorNames(): Set<String> = buildSet {
    props.forEach { prop ->
        prop.getter?.let { accessor -> add(accessor.name) }
        prop.setter?.let { accessor -> add(accessor.name) }
    }
}
