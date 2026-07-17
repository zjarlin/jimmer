package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 单个真实编译轮中已经冻结的完整 LSI 快照。
 */
class LsiWorkspace(
    sources: Collection<LsiSource> = emptyList(),
    declarations: Collection<LsiDeclaration> = emptyList(),
    typeHierarchy: Collection<LsiTypeHierarchyEntry> = emptyList(),
) {
    val sources: List<LsiSource> = sources.distinct().sorted()

    val declarations: List<LsiDeclaration>

    private val declarationMap: Map<LsiSymbolId, LsiDeclaration>

    val typeHierarchy: List<LsiTypeHierarchyEntry>

    private val typeHierarchyMap: Map<LsiSymbolId, LsiTypeHierarchyEntry>

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

        val duplicateHierarchyIds = typeHierarchy
            .groupingBy(LsiTypeHierarchyEntry::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateHierarchyIds.isEmpty()) {
            "Duplicate LSI type hierarchy ids: ${duplicateHierarchyIds.joinToString { id -> id.value }}"
        }
        val hierarchyById = typeHierarchy.associateByTo(linkedMapOf(), LsiTypeHierarchyEntry::id)
        this.declarations.filterIsInstance<LsiTypeDeclaration>().forEach { declaration ->
            hierarchyById[declaration.id] = LsiTypeHierarchyEntry.from(declaration)
        }
        this.typeHierarchy = hierarchyById.values.sortedBy(LsiTypeHierarchyEntry::id)
        typeHierarchyMap = this.typeHierarchy.associateBy(LsiTypeHierarchyEntry::id)
    }

    operator fun get(id: LsiSymbolId): LsiDeclaration? = declarationMap[id]

    inline fun <reified T : LsiDeclaration> declarationsOfType(): List<T> = declarations.filterIsInstance<T>()

    fun typeHierarchyEntry(id: LsiSymbolId): LsiTypeHierarchyEntry? = typeHierarchyMap[id]

    fun contains(id: LsiSymbolId): Boolean = id in declarationMap

    /**
     * 合并真实编译轮快照；同一符号以较新轮冻结结果为准。
     */
    fun merge(newer: LsiWorkspace): LsiWorkspace {
        if (newer.declarations.isEmpty() && newer.sources.isEmpty() && newer.typeHierarchy.isEmpty()) {
            return this
        }
        if (declarations.isEmpty() && sources.isEmpty() && typeHierarchy.isEmpty()) {
            return newer
        }
        val mergedDeclarations = declarations.associateByTo(linkedMapOf(), LsiDeclaration::id)
        newer.declarations.forEach { declaration ->
            mergedDeclarations[declaration.id] = declaration
        }
        val mergedTypeHierarchy = typeHierarchy.associateByTo(linkedMapOf(), LsiTypeHierarchyEntry::id)
        newer.typeHierarchy.forEach { entry ->
            mergedTypeHierarchy[entry.id] = entry
        }
        return LsiWorkspace(
            sources = sources + newer.sources,
            declarations = mergedDeclarations.values,
            typeHierarchy = mergedTypeHierarchy.values,
        )
    }

    fun originatingSources(symbolIds: Collection<LsiSymbolId>): Set<LsiSource> {
        val sources = sortedSetOf<LsiSource>()
        val pending = ArrayDeque(symbolIds.sorted())
        val visited = mutableSetOf<LsiSymbolId>()
        while (pending.isNotEmpty()) {
            val symbolId = pending.removeFirst()
            if (!visited.add(symbolId)) {
                continue
            }
            val declaration = declarationMap[symbolId] ?: continue
            declaration.origin.source?.let(sources::add)
            declaration.origin.originatingSymbols.sorted().forEach(pending::addLast)
        }
        return sources
    }

    companion object {
        val EMPTY: LsiWorkspace = LsiWorkspace()
    }
}
