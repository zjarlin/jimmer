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
    annotationScopes: Collection<LsiAnnotationScope> = emptyList(),
) {
    val sources: List<LsiSource> = sources.distinct().sorted()

    val declarations: List<LsiDeclaration>

    private val declarationMap: Map<LsiSymbolId, LsiDeclaration>

    val annotationScopes: List<LsiAnnotationScope>

    private val annotationScopeMap: Map<LsiSymbolId, LsiAnnotationScope>

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

        val duplicateAnnotationScopeIds = annotationScopes
            .groupingBy(LsiAnnotationScope::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateAnnotationScopeIds.isEmpty()) {
            "Duplicate LSI annotation scope ids: ${duplicateAnnotationScopeIds.joinToString { id -> id.value }}"
        }
        this.annotationScopes = annotationScopes.sortedBy(LsiAnnotationScope::id)
        annotationScopeMap = this.annotationScopes.associateBy(LsiAnnotationScope::id)

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

    fun annotationScope(id: LsiSymbolId): LsiAnnotationScope? = annotationScopeMap[id]

    inline fun <reified T : LsiDeclaration> declarationsOfType(): List<T> = declarations.filterIsInstance<T>()

    fun typeHierarchyEntry(id: LsiSymbolId): LsiTypeHierarchyEntry? = typeHierarchyMap[id]

    fun contains(id: LsiSymbolId): Boolean = id in declarationMap || id in annotationScopeMap

    /**
     * 合并真实编译轮快照；被当前轮完整重冻的类型先淘汰旧声明子树。
     */
    fun merge(
        newer: LsiWorkspace,
        refreshedTypeIds: Set<LsiSymbolId> = emptySet(),
    ): LsiWorkspace {
        refreshedTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        if (
            newer.declarations.isEmpty() &&
            newer.sources.isEmpty() &&
            newer.typeHierarchy.isEmpty() &&
            newer.annotationScopes.isEmpty() &&
            refreshedTypeIds.isEmpty()
        ) {
            return this
        }
        if (
            declarations.isEmpty() &&
            sources.isEmpty() &&
            typeHierarchy.isEmpty() &&
            annotationScopes.isEmpty()
        ) {
            return newer
        }
        val refreshedDeclarationTypeIds = declarations.expandedRefreshedTypeIds(refreshedTypeIds)
        val refreshedSourcePaths = buildSet {
            declarations.asSequence()
                .filter { declaration -> declaration.belongsToAny(refreshedDeclarationTypeIds) }
                .mapNotNull { declaration -> declaration.origin.source?.path }
                .toCollection(this)
            newer.declarations.asSequence()
                .filter { declaration -> declaration.belongsToAny(refreshedTypeIds) }
                .mapNotNull { declaration -> declaration.origin.source?.path }
                .toCollection(this)
        }
        val mergedDeclarations = declarations
            .asSequence()
            .filterNot { declaration -> declaration.belongsToAny(refreshedDeclarationTypeIds) }
            .associateByTo(linkedMapOf(), LsiDeclaration::id)
        newer.declarations.forEach { declaration ->
            mergedDeclarations[declaration.id] = declaration
        }
        val mergedAnnotationScopes = annotationScopes
            .asSequence()
            .filterNot { scope -> scope.origin.source?.path in refreshedSourcePaths }
            .associateByTo(linkedMapOf(), LsiAnnotationScope::id)
        newer.annotationScopes.forEach { annotationScope ->
            mergedAnnotationScopes[annotationScope.id] = annotationScope
        }
        val mergedTypeHierarchy = typeHierarchy
            .asSequence()
            .filterNot { entry -> entry.id in refreshedDeclarationTypeIds }
            .associateByTo(linkedMapOf(), LsiTypeHierarchyEntry::id)
        newer.typeHierarchy.forEach { entry ->
            mergedTypeHierarchy[entry.id] = entry
        }
        return LsiWorkspace(
            sources = sources + newer.sources,
            declarations = mergedDeclarations.values,
            typeHierarchy = mergedTypeHierarchy.values,
            annotationScopes = mergedAnnotationScopes.values,
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
            val origin = declarationMap[symbolId]?.origin ?: annotationScopeMap[symbolId]?.origin ?: continue
            origin.source?.let(sources::add)
            origin.originatingSymbols.sorted().forEach(pending::addLast)
        }
        return sources
    }

    companion object {
        val EMPTY: LsiWorkspace = LsiWorkspace()
    }
}

private fun LsiDeclaration.belongsToAny(typeIds: Set<LsiSymbolId>): Boolean {
    return typeIds.any { typeId ->
        id == typeId || id.value.startsWith("${typeId.value}/")
    }
}

private fun Collection<LsiDeclaration>.expandedRefreshedTypeIds(
    refreshedTypeIds: Set<LsiSymbolId>,
): Set<LsiSymbolId> {
    val expandedTypeIds = refreshedTypeIds.toMutableSet()
    val nestedTypes = filterIsInstance<LsiTypeDeclaration>()
    var changed: Boolean
    do {
        changed = false
        nestedTypes.forEach { type ->
            if (type.enclosingTypeId in expandedTypeIds && expandedTypeIds.add(type.id)) {
                changed = true
            }
        }
    } while (changed)
    return expandedTypeIds
}
