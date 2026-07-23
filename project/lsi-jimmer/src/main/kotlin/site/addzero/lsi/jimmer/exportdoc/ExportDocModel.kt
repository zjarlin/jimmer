package site.addzero.lsi.jimmer.exportdoc

import site.addzero.lsi.core.LsiSymbolId

/** 描述当前工作区最终导出的文档语义。 */
data class ExportDocSchema(
    val effectiveConfigurationIds: List<LsiSymbolId>,
    val exportedTypeIds: List<LsiSymbolId>,
    val entries: List<ExportDocEntry>,
) {
    init {
        require(effectiveConfigurationIds == effectiveConfigurationIds.distinct().sorted()) {
            "ExportDoc effective configuration ids must be distinct and sorted"
        }
        require(exportedTypeIds == exportedTypeIds.distinct().sorted()) {
            "ExportDoc exported type ids must be distinct and sorted"
        }
        require(entries == entries.sortedBy(ExportDocEntry::key)) {
            "ExportDoc entries must use stable key order"
        }
        require(entries.map(ExportDocEntry::key).distinct().size == entries.size) {
            "ExportDoc entries cannot contain duplicate keys"
        }
    }
}

/** 描述一个稳定键对应的导出文档。 */
data class ExportDocEntry(
    val declarationId: LsiSymbolId,
    val key: String,
    val content: String,
) {
    init {
        require(key.isNotBlank()) { "ExportDoc key cannot be blank" }
        require(content.isNotBlank()) { "ExportDoc content cannot be blank" }
    }
}
