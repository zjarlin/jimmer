package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.jimmer.dto.DtoPropId

internal data class DtoConfigContractResolution(
    val contracts: List<DtoConfigContract>,
    val diagnostics: List<LsiDiagnostic>,
    val unresolvedTypeIds: List<LsiSymbolId> = emptyList(),
) {
    val contractsByPropId: Map<DtoPropId, List<DtoConfigContract>> =
        contracts.groupBy(DtoConfigContract::propId)

    val successful: Boolean = diagnostics.isEmpty() && unresolvedTypeIds.isEmpty()

    init {
        require(contracts == contracts.sortedWith(DTO_CONFIG_CONTRACT_COMPARATOR)) {
            "DTO config contracts must use stable property and kind order"
        }
        require(contracts.distinctBy { contract -> contract.propId to contract.kind }.size == contracts.size) {
            "DTO config contracts cannot contain duplicate property kinds"
        }
        unresolvedTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(unresolvedTypeIds == unresolvedTypeIds.distinct().sorted()) {
            "Unresolved DTO config type ids must be distinct and sorted"
        }
    }
}

internal data class DtoConfigContract(
    val propId: DtoPropId,
    val kind: DtoConfigContractKind,
    val implementationTypeId: LsiSymbolId,
    val targetEntityTypeId: LsiSymbolId,
    val contractArgumentTypeId: LsiSymbolId,
    val construction: DtoConfigConstructionKind,
    val dependencyTypeIds: List<LsiSymbolId>,
) {
    init {
        implementationTypeId.requireTypeQualifiedName()
        targetEntityTypeId.requireTypeQualifiedName()
        contractArgumentTypeId.requireTypeQualifiedName()
        dependencyTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(dependencyTypeIds == dependencyTypeIds.distinct().sorted()) {
            "DTO config dependency type ids must be distinct and sorted: ${propId.value}"
        }
        require(implementationTypeId in dependencyTypeIds) {
            "DTO config dependencies must include the implementation type: ${propId.value}"
        }
        require(targetEntityTypeId in dependencyTypeIds) {
            "DTO config dependencies must include the target entity type: ${propId.value}"
        }
    }
}

internal enum class DtoConfigContractKind {
    FILTER,
    RECURSION,
}

internal enum class DtoConfigConstructionKind {
    ZERO_ARGUMENT_CONSTRUCTOR,
}

private val DTO_CONFIG_CONTRACT_COMPARATOR: Comparator<DtoConfigContract> =
    compareBy(DtoConfigContract::propId, DtoConfigContract::kind)
