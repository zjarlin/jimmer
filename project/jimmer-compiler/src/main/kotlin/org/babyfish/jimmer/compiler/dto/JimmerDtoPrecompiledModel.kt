package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType

internal data class JimmerDtoPrecompiledSchema(
    val documents: List<JimmerDtoPrecompiledDocument>,
) {
    init {
        require(documents == documents.sortedBy(JimmerDtoPrecompiledDocument::inputSnapshot)) {
            "DTO precompiled documents must use stable input order"
        }
        require(
            documents.map { document -> document.inputSnapshot.document.source.path }.distinct().size == documents.size
        ) {
            "DTO precompiled schema cannot contain duplicate input documents"
        }
    }
}

internal data class JimmerDtoPrecompiledDocument(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val targetTypeIds: List<LsiSymbolId>,
    val graph: DtoGraph,
    val annotationContract: JimmerDtoAnnotationContract,
    val interfaceContractResolution: DtoInterfaceContractResolution,
    val configContractResolution: DtoConfigContractResolution,
) {
    init {
        targetTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(targetTypeIds == targetTypeIds.distinct().sorted()) {
            "DTO target type ids must be distinct and sorted"
        }
        require(graph.source == inputSnapshot.document.source) {
            "DTO graph source must match its input document"
        }
        require(graph.rootTypeIds.all { typeId ->
            graph.typesById.getValue(typeId).baseTypeId in targetTypeIds
        }) {
            "DTO types must reference a document target type: ${inputSnapshot.document.source.path}"
        }
        require(annotationContract.typePlans.map(JimmerDtoTypeAnnotationPlan::typeId) == graph.types.map(DtoType::id)) {
            "DTO annotation contract must cover every frozen DTO type: ${inputSnapshot.document.source.path}"
        }
        require(annotationContract.propPlans.map(JimmerDtoPropAnnotationPlan::propId) == graph.props.map(DtoProp::id)) {
            "DTO annotation contract must cover every frozen DTO property: ${inputSnapshot.document.source.path}"
        }
        require(interfaceContractResolution.contracts.all { contract -> contract.typeId in graph.typesById }) {
            "DTO interface contracts must reference frozen DTO types: ${inputSnapshot.document.source.path}"
        }
        require(configContractResolution.contracts.all { contract -> contract.propId in graph.propsById }) {
            "DTO config contracts must reference frozen DTO properties: ${inputSnapshot.document.source.path}"
        }
    }
}

internal data class JimmerDtoUnresolvedDocument(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val targetTypeIds: List<LsiSymbolId>,
    val unresolvedTypeIds: List<LsiSymbolId>,
    val message: String,
) {
    init {
        targetTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(targetTypeIds == targetTypeIds.distinct().sorted()) {
            "Unresolved DTO target type ids must be distinct and sorted"
        }
        unresolvedTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(unresolvedTypeIds.isNotEmpty()) {
            "Unresolved DTO document must contain unresolved type ids"
        }
        require(unresolvedTypeIds == unresolvedTypeIds.distinct().sorted()) {
            "Unresolved DTO type ids must be distinct and sorted"
        }
        require(message.isNotBlank()) { "Unresolved DTO document message cannot be blank" }
    }
}

internal data class JimmerDtoCompilerFailure(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val targetTypeIds: List<LsiSymbolId>,
    val code: String,
    val severity: LsiDiagnosticSeverity,
    val symbolId: LsiSymbolId?,
    val location: LsiLocation?,
    val message: String,
    val details: Map<String, String>,
) {
    init {
        targetTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(targetTypeIds == targetTypeIds.distinct().sorted()) {
            "Failed DTO target type ids must be distinct and sorted"
        }
        require(code.isNotBlank()) { "DTO compiler failure code cannot be blank" }
        require(code.none(Char::isWhitespace)) {
            "DTO compiler failure code cannot contain whitespace: '$code'"
        }
        require(message.isNotBlank()) { "DTO compiler failure message cannot be blank" }
    }
}

internal val JIMMER_DTO_COMPILER_FAILURE_COMPARATOR: Comparator<JimmerDtoCompilerFailure> =
    compareBy<JimmerDtoCompilerFailure>(
        JimmerDtoCompilerFailure::inputSnapshot,
        JimmerDtoCompilerFailure::code,
        JimmerDtoCompilerFailure::severity,
        { failure -> failure.symbolId?.value.orEmpty() },
        { failure -> failure.location?.source?.path.orEmpty() },
        { failure -> failure.location?.source?.language?.name.orEmpty() },
        { failure -> failure.location?.source?.kind?.name.orEmpty() },
        { failure -> failure.location?.start?.line ?: 0 },
        { failure -> failure.location?.start?.column ?: 0 },
        { failure -> failure.location?.end?.line ?: 0 },
        { failure -> failure.location?.end?.column ?: 0 },
        JimmerDtoCompilerFailure::message,
        { failure ->
            failure.details.toSortedMap().entries.joinToString("\u0000") { (name, value) ->
                "${name.length}:$name${value.length}:$value"
            }
        },
        { failure -> failure.targetTypeIds.joinToString(",") { typeId -> typeId.value } },
    )

internal data class JimmerDtoPrecompileOutcome(
    val schema: JimmerDtoPrecompiledSchema,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
)
