package org.babyfish.jimmer.compiler.exportdoc

import site.addzero.lsi.core.LsiSymbolId

data class ExportDocPrecompiledSchema(
    val effectiveConfigurationIds: List<LsiSymbolId>,
    val exportedTypeIds: List<LsiSymbolId>,
    val docs: List<ExportedDoc>,
) {
    init {
        require(effectiveConfigurationIds == effectiveConfigurationIds.distinct().sorted()) {
            "ExportDoc effective configuration ids must be distinct and sorted"
        }
        require(exportedTypeIds == exportedTypeIds.distinct().sorted()) {
            "ExportDoc exported type ids must be distinct and sorted"
        }
        require(docs == docs.sortedBy(ExportedDoc::key)) {
            "ExportDoc entries must use stable key order"
        }
        require(docs.map(ExportedDoc::key).distinct().size == docs.size) {
            "ExportDoc entries cannot contain duplicate keys"
        }
    }
}

data class ExportedDoc(
    val declarationId: LsiSymbolId,
    val key: String,
    val content: String,
) {
    init {
        require(key.isNotBlank()) { "ExportDoc key cannot be blank" }
        require(content.isNotBlank()) { "ExportDoc content cannot be blank" }
    }
}
