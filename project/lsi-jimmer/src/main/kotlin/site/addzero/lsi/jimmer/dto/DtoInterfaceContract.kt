package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiTypeRef

data class DtoInterfaceContractResolution(
    val contracts: List<DtoInterfaceContract>,
    val diagnostics: List<LsiDiagnostic>,
) {
    val contractsByTypeId: Map<DtoTypeId, DtoInterfaceContract> =
        contracts.associateBy(DtoInterfaceContract::typeId)

    val successful: Boolean = diagnostics.isEmpty()

    init {
        require(contracts == contracts.sortedBy(DtoInterfaceContract::typeId)) {
            "DTO interface contracts must use stable type id order"
        }
        require(contractsByTypeId.size == contracts.size) {
            "DTO interface contracts cannot contain duplicate DTO type ids"
        }
    }
}

data class DtoInterfaceContract(
    val typeId: DtoTypeId,
    val superInterfaceTypeIds: List<LsiSymbolId>,
    val props: List<DtoInterfacePropContract>,
) {
    val propsByName: Map<String, DtoInterfacePropContract> =
        props.associateBy(DtoInterfacePropContract::name)

    init {
        require(superInterfaceTypeIds == superInterfaceTypeIds.distinct()) {
            "DTO interface contract cannot contain duplicate super interface ids: ${typeId.value}"
        }
        superInterfaceTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(props == props.sortedBy(DtoInterfacePropContract::name)) {
            "DTO interface contract properties must use stable name order: ${typeId.value}"
        }
        require(propsByName.size == props.size) {
            "DTO interface contract cannot contain duplicate property names: ${typeId.value}"
        }
    }
}

data class DtoInterfacePropContract(
    val declaringTypeId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val mutable: Boolean,
    val getter: DtoInterfaceAccessorContract?,
    val setter: DtoInterfaceAccessorContract?,
    val origin: LsiOrigin,
) {
    init {
        declaringTypeId.requireTypeQualifiedName()
        require(name.isNotBlank()) { "DTO interface contract property name cannot be blank" }
        require(getter != null || setter != null) {
            "DTO interface contract property requires at least one accessor: $name"
        }
        require(mutable == (setter != null)) {
            "DTO interface contract property mutability must match its setter: $name"
        }
    }
}

data class DtoInterfaceAccessorContract(
    val declarationId: LsiSymbolId,
    val name: String,
    val origin: LsiOrigin,
) {
    init {
        require(name.isNotBlank()) { "DTO interface accessor name cannot be blank" }
    }
}
