package site.addzero.lsi.jimmer.dto

/** 校验 DTO 图与解析后的注解、接口及配置契约是否互相完整引用。 */
fun DtoGraph.requireResolvedContracts(
    annotationContract: DtoAnnotationContract,
    interfaceContractResolution: DtoInterfaceContractResolution,
    configContractResolution: DtoConfigContractResolution,
) {
    require(annotationContract.typePlans.map(DtoTypeAnnotationPlan::typeId) == types.map(DtoType::id)) {
        "DTO annotation contract must cover every frozen DTO type: ${source.path}"
    }
    require(annotationContract.propPlans.map(DtoPropAnnotationPlan::propId) == props.map(DtoProp::id)) {
        "DTO annotation contract must cover every frozen DTO property: ${source.path}"
    }
    require(interfaceContractResolution.contracts.all { contract -> contract.typeId in typesById }) {
        "DTO interface contracts must reference frozen DTO types: ${source.path}"
    }
    require(configContractResolution.contracts.all { contract -> contract.propId in propsById }) {
        "DTO config contracts must reference frozen DTO properties: ${source.path}"
    }
}
