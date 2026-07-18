package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

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
    val baseTypeId: LsiSymbolId,
    val sourceTypeName: String,
    val targetPackageName: String?,
    val renderGraph: JimmerDtoRenderGraph,
    val annotationContract: JimmerDtoAnnotationContract,
    val interfaceContractResolution: DtoInterfaceContractResolution,
    val configContractResolution: DtoConfigContractResolution,
) {
    init {
        baseTypeId.requireTypeQualifiedName()
        require(sourceTypeName.isNotBlank()) { "DTO source type name cannot be blank" }
        require(renderGraph.source == inputSnapshot.document.source) {
            "DTO render graph source must match its input document"
        }
        require(renderGraph.rootTypeIds.all { typeId ->
            renderGraph.typesById.getValue(typeId).baseTypeId == baseTypeId
        }) {
            "DTO types must reference the document base type: ${inputSnapshot.document.source.path}"
        }
        require(annotationContract.typePlans.map(JimmerDtoTypeAnnotationPlan::typeId) == renderGraph.types.map(JimmerDtoType::id)) {
            "DTO annotation contract must cover every frozen DTO type: ${inputSnapshot.document.source.path}"
        }
        require(annotationContract.propPlans.map(JimmerDtoPropAnnotationPlan::propId) == renderGraph.props.map(JimmerDtoProp::id)) {
            "DTO annotation contract must cover every frozen DTO property: ${inputSnapshot.document.source.path}"
        }
        require(interfaceContractResolution.contracts.all { contract -> contract.typeId in renderGraph.typesById }) {
            "DTO interface contracts must reference frozen DTO types: ${inputSnapshot.document.source.path}"
        }
        require(configContractResolution.contracts.all { contract -> contract.propId in renderGraph.propsById }) {
            "DTO config contracts must reference frozen DTO properties: ${inputSnapshot.document.source.path}"
        }
    }
}

internal data class JimmerDtoUnresolvedDocument(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val baseTypeId: LsiSymbolId,
    val unresolvedTypeIds: List<LsiSymbolId>,
    val message: String,
) {
    init {
        baseTypeId.requireTypeQualifiedName()
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
    val baseTypeId: LsiSymbolId?,
    val code: String,
    val severity: LsiDiagnosticSeverity,
    val symbolId: LsiSymbolId?,
    val location: LsiLocation?,
    val message: String,
    val details: Map<String, String>,
) {
    init {
        baseTypeId?.requireTypeQualifiedName()
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
        { failure -> failure.baseTypeId?.value.orEmpty() },
    )

internal data class JimmerDtoPrecompileOutcome(
    val schema: JimmerDtoPrecompiledSchema,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
)
