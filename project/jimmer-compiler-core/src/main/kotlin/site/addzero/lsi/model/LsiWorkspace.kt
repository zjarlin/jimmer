package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 单个真实编译轮中已经冻结的完整 LSI 快照。
 */
class LsiWorkspace(
    sources: Collection<LsiSource> = emptyList(),
    declarations: Collection<LsiDeclaration> = emptyList()
) {
    val sources: List<LsiSource> = sources.distinct().sorted()

    val declarations: List<LsiDeclaration>

    private val declarationMap: Map<LsiSymbolId, LsiDeclaration>

    init {
        val duplicates = declarations
            .groupingBy(LsiDeclaration::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicates.isEmpty()) {
            "Duplicate LSI declaration ids: ${duplicates.joinToString { id -> id.value }}"
        }
        this.declarations = declarations.sortedBy { declaration -> declaration.id }
        declarationMap = this.declarations.associateBy(LsiDeclaration::id)
    }

    operator fun get(id: LsiSymbolId): LsiDeclaration? = declarationMap[id]

    inline fun <reified T : LsiDeclaration> declarationsOfType(): List<T> = declarations.filterIsInstance<T>()

    fun contains(id: LsiSymbolId): Boolean = id in declarationMap

    companion object {
        val EMPTY: LsiWorkspace = LsiWorkspace()
    }
}
