package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.requireResolvedContracts

internal data class JimmerDtoResolvedInput(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val targetTypeIds: List<LsiSymbolId>,
) {
    init {
        targetTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(targetTypeIds == targetTypeIds.distinct().sorted()) {
            "DTO target type ids must be distinct and sorted"
        }
    }
}

internal data class JimmerDtoRoundResolution(
    val resolvedInputs: List<JimmerDtoResolvedInput>,
    val graphs: List<DtoGraph>,
    val annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    val interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    val configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
) {
    init {
        require(resolvedInputs == resolvedInputs.sortedBy(JimmerDtoResolvedInput::inputSnapshot)) {
            "Resolved DTO inputs must use stable input order"
        }
        val resolvedSources = resolvedInputs.map { input -> input.inputSnapshot.document.source }
        require(resolvedSources.distinct().size == resolvedSources.size) {
            "DTO round cannot contain duplicate resolved inputs"
        }
        require(graphs == graphs.sortedBy(DtoGraph::source)) {
            "DTO graphs must use stable source order"
        }
        val graphSources = graphs.map(DtoGraph::source)
        require(graphSources.distinct().size == graphSources.size) {
            "DTO round cannot contain duplicate graph sources"
        }
        require(graphSources == resolvedSources) {
            "DTO graphs must match resolved input sources"
        }
        requireDtoResolvedContracts(
            graphs = graphs,
            annotationContractsBySource = annotationContractsBySource,
            interfaceContractsBySource = interfaceContractsBySource,
            configContractsBySource = configContractsBySource,
        )
        resolvedInputs.zip(graphs).forEach { (input, graph) ->
            require(graph.rootTypeIds.all { typeId ->
                graph.typesById.getValue(typeId).baseTypeId in input.targetTypeIds
            }) {
                "DTO types must reference a document target type: ${graph.source.path}"
            }
        }
        require(unresolvedDocuments == unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot)) {
            "Unresolved DTO documents must use stable input order"
        }
        require(failures == failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR)) {
            "DTO compiler failures must use stable diagnostic order"
        }
        val unresolvedSources = unresolvedDocuments.mapTo(hashSetOf()) { document ->
            document.inputSnapshot.document.source
        }
        val failedSources = failures.mapTo(hashSetOf()) { failure ->
            failure.inputSnapshot.document.source
        }
        require(unresolvedSources.intersect(failedSources).isEmpty()) {
            "DTO documents cannot be both unresolved and invalid"
        }
    }
}

internal fun requireDtoResolvedContracts(
    graphs: List<DtoGraph>,
    annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
) {
    require(graphs == graphs.sortedBy(DtoGraph::source)) {
        "DTO graphs must use stable source order"
    }
    val graphSources = graphs.map(DtoGraph::source)
    require(graphSources.distinct().size == graphSources.size) {
        "DTO state cannot contain duplicate graph sources"
    }
    requireContractSources(
        contractName = "annotation",
        graphSources = graphSources,
        contractSources = annotationContractsBySource.keys.toList(),
    )
    requireContractSources(
        contractName = "interface",
        graphSources = graphSources,
        contractSources = interfaceContractsBySource.keys.toList(),
    )
    requireContractSources(
        contractName = "config",
        graphSources = graphSources,
        contractSources = configContractsBySource.keys.toList(),
    )
    graphs.forEach { graph ->
        graph.requireResolvedContracts(
            annotationContract = annotationContractsBySource.getValue(graph.source),
            interfaceContractResolution = interfaceContractsBySource.getValue(graph.source),
            configContractResolution = configContractsBySource.getValue(graph.source),
        )
    }
}

private fun requireContractSources(
    contractName: String,
    graphSources: List<LsiSource>,
    contractSources: List<LsiSource>,
) {
    require(contractSources == contractSources.sorted()) {
        "DTO $contractName contract map must use stable source order"
    }
    require(contractSources == graphSources) {
        "DTO $contractName contracts must cover every graph source"
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
