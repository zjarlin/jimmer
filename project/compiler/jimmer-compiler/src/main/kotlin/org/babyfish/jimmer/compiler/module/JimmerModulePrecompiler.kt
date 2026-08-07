package org.babyfish.jimmer.compiler.module

import org.babyfish.jimmer.compiler.CompilerPlatform
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind

data class JimmerModulePrecompileOptions(
    val platform: CompilerPlatform,
    val immutablesName: String = "Immutables",
    val tablesName: String = "Tables",
    val tableExesName: String = "TableExes",
    val fetchersName: String = "Fetchers",
    val moduleRequired: Boolean = false,
    val resourceGeneration: Boolean = true,
) {
    init {
        require(platform != CompilerPlatform.UNKNOWN) {
            "Jimmer module precompile options require an APT or KSP platform"
        }
        validateSimpleName(immutablesName, "immutables")
        validateSimpleName(tablesName, "tables")
        validateSimpleName(tableExesName, "table exes")
        validateSimpleName(fetchersName, "fetchers")
    }
}

data class JimmerModuleResourceState(
    val entityQualifiedTypeNames: List<String> = emptyList(),
    val immutableQualifiedTypeNames: List<String> = emptyList(),
) {
    init {
        validateQualifiedTypeNames(entityQualifiedTypeNames, "entity")
        validateQualifiedTypeNames(immutableQualifiedTypeNames, "immutable")
    }

    companion object {
        val EMPTY = JimmerModuleResourceState()
    }
}

data class JimmerModuleCompilationScope(
    val currentImmutableTypeIds: List<LsiSymbolId>,
    val compilationSourceTypeIds: List<LsiSymbolId>,
    val cumulativeImmutableTypeIds: List<LsiSymbolId> = currentImmutableTypeIds,
) {
    init {
        require(cumulativeImmutableTypeIds == cumulativeImmutableTypeIds.distinct().sorted()) {
            "Cumulative Jimmer immutable type ids must be distinct and sorted"
        }
        require(currentImmutableTypeIds == currentImmutableTypeIds.distinct().sorted()) {
            "Current Jimmer immutable type ids must be distinct and sorted"
        }
        require(compilationSourceTypeIds == compilationSourceTypeIds.distinct().sorted()) {
            "Jimmer compilation source type ids must be distinct and sorted"
        }
        require(compilationSourceTypeIds.containsAll(currentImmutableTypeIds)) {
            "Jimmer compilation source type ids must include current immutable types"
        }
        require(cumulativeImmutableTypeIds.containsAll(currentImmutableTypeIds)) {
            "Cumulative Jimmer immutable type ids must include current immutable types"
        }
    }
}

class JimmerModulePrecompiler(
    private val options: JimmerModulePrecompileOptions,
) {
    fun compile(
        immutableSchema: ImmutableSchema,
        resourceState: JimmerModuleResourceState = JimmerModuleResourceState.EMPTY,
        compilationScope: JimmerModuleCompilationScope = immutableSchema.defaultCompilationScope(),
    ): JimmerModuleSchema {
        val allTypes = immutableSchema.types.sortedBy(ImmutableType::qualifiedName)
        val allTypesById = allTypes.associateBy(ImmutableType::id)
        val unknownCumulativeTypeIds = compilationScope.cumulativeImmutableTypeIds
            .filterNot(allTypesById::containsKey)
        require(unknownCumulativeTypeIds.isEmpty()) {
            "Cumulative Jimmer immutable type ids are missing from immutable schema: " +
                unknownCumulativeTypeIds.joinToString { typeId -> typeId.value }
        }
        val cumulativeTypeIdSet = compilationScope.cumulativeImmutableTypeIds.toSet()
        val cumulativeTypes = allTypes.filter { type -> type.id in cumulativeTypeIdSet }
        val cumulativeTypesById = cumulativeTypes.associateBy(ImmutableType::id)
        val currentTypeIdSet = compilationScope.currentImmutableTypeIds.toSet()
        val currentTypes = cumulativeTypes.filter { type -> type.id in currentTypeIdSet }
        val cumulativeEntities = cumulativeTypes
            .filter { type -> type.kind == ImmutableTypeKind.ENTITY }
            .map(ImmutableType::toAggregateType)
        val cumulativeImmutableIndexTypes = cumulativeTypes
            .filter { type -> type.kind in IMMUTABLE_RESOURCE_KINDS }
            .map(ImmutableType::toAggregateType)
        val currentEntities = currentTypes
            .filter { type -> type.kind == ImmutableTypeKind.ENTITY }
            .map(ImmutableType::toAggregateType)
        val currentImmutableIndexTypes = currentTypes
            .filter { type -> type.kind in IMMUTABLE_RESOURCE_KINDS }
            .map(ImmutableType::toAggregateType)
        val retainedEntities = resourceState.entityQualifiedTypeNames.toRetainedTypes(
            currentTypesById = cumulativeTypesById,
            acceptedCurrentKinds = setOf(ImmutableTypeKind.ENTITY),
        )
        val retainedImmutableTypes = resourceState.immutableQualifiedTypeNames.toRetainedTypes(
            currentTypesById = cumulativeTypesById,
            acceptedCurrentKinds = IMMUTABLE_RESOURCE_KINDS,
        )
        val aggregateEntities = (retainedEntities + cumulativeEntities).distinctTypes()
        val aggregateImmutableTypes = (retainedImmutableTypes + cumulativeImmutableIndexTypes).distinctTypes()
        return when (options.platform) {
            CompilerPlatform.APT -> compileApt(
                currentEntities = currentEntities,
                currentImmutableTypes = currentImmutableIndexTypes,
                aggregateEntities = aggregateEntities,
                aggregateImmutableTypes = aggregateImmutableTypes,
            )
            CompilerPlatform.KSP -> compileKsp(
                cumulativeEntities = cumulativeEntities,
                aggregateEntities = aggregateEntities,
                compilationSourceTypeIds = compilationScope.compilationSourceTypeIds,
            )
            CompilerPlatform.UNKNOWN -> error(
                "Jimmer module precompile options require an APT or KSP platform"
            )
        }
    }

    private fun compileApt(
        currentEntities: List<AggregateType>,
        currentImmutableTypes: List<AggregateType>,
        aggregateEntities: List<AggregateType>,
        aggregateImmutableTypes: List<AggregateType>,
    ): JimmerModuleSchema {
        val aggregateManagedTypes = (aggregateEntities + aggregateImmutableTypes).distinctTypes()
        val packageName = aggregateManagedTypes.commonPackageName()
        val summaries = buildList {
            if (aggregateManagedTypes.isNotEmpty()) {
                add(
                    summary(
                        kind = JimmerModuleSummaryKind.IMMUTABLES,
                        packageName = packageName,
                        simpleName = options.immutablesName,
                        types = aggregateManagedTypes,
                        originatingTypeIds = (currentEntities + currentImmutableTypes)
                            .map(AggregateType::id)
                            .distinct()
                            .sorted(),
                    )
                )
            }
            if (aggregateEntities.isNotEmpty()) {
                add(
                    summary(
                        kind = JimmerModuleSummaryKind.TABLES,
                        packageName = packageName,
                        simpleName = options.tablesName,
                        types = aggregateEntities,
                        originatingTypeIds = currentEntities.map(AggregateType::id).sorted(),
                    )
                )
                add(
                    summary(
                        kind = JimmerModuleSummaryKind.TABLE_EXES,
                        packageName = packageName,
                        simpleName = options.tableExesName,
                        types = aggregateEntities,
                        originatingTypeIds = currentEntities.map(AggregateType::id).sorted(),
                    )
                )
                add(
                    summary(
                        kind = JimmerModuleSummaryKind.FETCHERS,
                        packageName = packageName,
                        simpleName = options.fetchersName,
                        types = aggregateEntities,
                        originatingTypeIds = currentEntities.map(AggregateType::id).sorted(),
                    )
                )
            }
        }
        val resources = if (options.resourceGeneration) {
            listOf(
                resource(
                    kind = JimmerModuleResourceKind.ENTITIES,
                    path = ENTITIES_RESOURCE_PATH,
                    types = aggregateEntities,
                    scope = JimmerModuleDependencyScope.MANAGED_TYPES_AND_EXISTING_RESOURCES,
                    originatingTypeIds = currentEntities.map(AggregateType::id).sorted(),
                ),
                resource(
                    kind = JimmerModuleResourceKind.IMMUTABLES,
                    path = IMMUTABLES_RESOURCE_PATH,
                    types = aggregateImmutableTypes,
                    scope = JimmerModuleDependencyScope.MANAGED_TYPES_AND_EXISTING_RESOURCES,
                    originatingTypeIds = currentImmutableTypes.map(AggregateType::id).sorted(),
                ),
            )
        } else {
            emptyList()
        }
        return JimmerModuleSchema(
            platform = CompilerPlatform.APT,
            options = options,
            packageName = packageName,
            summaries = summaries,
            module = null,
            resources = resources,
        )
    }

    private fun compileKsp(
        cumulativeEntities: List<AggregateType>,
        aggregateEntities: List<AggregateType>,
        compilationSourceTypeIds: List<LsiSymbolId>,
    ): JimmerModuleSchema {
        val packageName = cumulativeEntities.commonPackageName()
        if (!options.resourceGeneration || cumulativeEntities.isEmpty()) {
            return JimmerModuleSchema(
                platform = CompilerPlatform.KSP,
                options = options,
                packageName = packageName,
                summaries = emptyList(),
                module = null,
                resources = emptyList(),
            )
        }
        val module = if (options.moduleRequired) {
            JimmerModuleSource(
                packageName = packageName,
                simpleName = JIMMER_MODULE_SIMPLE_NAME,
                entityTypeIds = cumulativeEntities.map(AggregateType::id).sorted(),
                entityNamePrefix = packageName.takeIf(String::isNotEmpty)?.plus('.'),
                dependencies = cumulativeEntities.dependencies(
                    scope = JimmerModuleDependencyScope.ALL_COMPILATION_SOURCES,
                    originatingTypeIds = compilationSourceTypeIds,
                ),
            )
        } else {
            null
        }
        return JimmerModuleSchema(
            platform = CompilerPlatform.KSP,
            options = options,
            packageName = packageName,
            summaries = emptyList(),
            module = module,
            resources = listOf(
                resource(
                    kind = JimmerModuleResourceKind.ENTITIES,
                    path = ENTITIES_RESOURCE_PATH,
                    types = aggregateEntities,
                    scope = JimmerModuleDependencyScope.ALL_COMPILATION_SOURCES,
                    originatingTypeIds = compilationSourceTypeIds,
                )
            ),
        )
    }

    private fun summary(
        kind: JimmerModuleSummaryKind,
        packageName: String,
        simpleName: String,
        types: List<AggregateType>,
        originatingTypeIds: List<LsiSymbolId>,
    ): JimmerModuleSummary {
        val nameCounts = mutableMapOf<String, Int>()
        val members = types.map { type ->
            val baseName = kind.memberBaseName(type.simpleName)
            val count = nameCounts.getOrDefault(baseName, 0) + 1
            nameCounts[baseName] = count
            JimmerModuleSummaryMember(
                typeId = type.id,
                qualifiedTypeName = type.qualifiedName,
                packageName = type.packageName,
                simpleTypeName = type.simpleName,
                generatedName = if (count == 1) baseName else "${baseName}_$count",
            )
        }
        return JimmerModuleSummary(
            kind = kind,
            packageName = packageName,
            simpleName = simpleName,
            members = members,
            dependencies = types.dependencies(
                scope = JimmerModuleDependencyScope.MANAGED_TYPES_AND_EXISTING_RESOURCES,
                originatingTypeIds = originatingTypeIds,
            ),
        )
    }

    private fun resource(
        kind: JimmerModuleResourceKind,
        path: String,
        types: List<AggregateType>,
        scope: JimmerModuleDependencyScope,
        originatingTypeIds: List<LsiSymbolId>,
    ): JimmerModuleResource {
        return JimmerModuleResource(
            kind = kind,
            path = path,
            qualifiedTypeNames = types.map(AggregateType::qualifiedName).distinct().sorted(),
            contentTypeIds = types.map(AggregateType::id).distinct().sorted(),
            mergeExistingContent = true,
            dependencies = types.dependencies(scope, originatingTypeIds),
        )
    }
}

private data class AggregateType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val simpleName: String,
)

private fun ImmutableType.toAggregateType(): AggregateType {
    return qualifiedName.toAggregateType(id)
}

private fun String.toAggregateType(id: LsiSymbolId = LsiSymbolId.type(this)): AggregateType {
    return AggregateType(
        id = id,
        qualifiedName = this,
        packageName = substringBeforeLast('.', ""),
        simpleName = substringAfterLast('.'),
    )
}

private fun List<String>.toRetainedTypes(
    currentTypesById: Map<LsiSymbolId, ImmutableType>,
    acceptedCurrentKinds: Set<ImmutableTypeKind>,
): List<AggregateType> {
    return mapNotNull { qualifiedName ->
        val id = LsiSymbolId.type(qualifiedName)
        val currentType = currentTypesById[id]
        when {
            currentType == null -> qualifiedName.toAggregateType(id)
            currentType.kind in acceptedCurrentKinds -> currentType.toAggregateType()
            else -> null
        }
    }.distinctTypes()
}

private fun List<AggregateType>.distinctTypes(): List<AggregateType> {
    return associateBy(AggregateType::id).values.sortedBy(AggregateType::qualifiedName)
}

private fun List<AggregateType>.dependencies(
    scope: JimmerModuleDependencyScope,
    originatingTypeIds: List<LsiSymbolId>,
): JimmerModuleArtifactDependencies {
    return JimmerModuleArtifactDependencies(
        typeIds = map(AggregateType::id).distinct().sorted(),
        originatingTypeIds = originatingTypeIds.distinct().sorted(),
        packageNames = map(AggregateType::packageName).distinct().sorted(),
        scope = scope,
    )
}

private fun ImmutableSchema.defaultCompilationScope(): JimmerModuleCompilationScope {
    val typeIds = types.map(ImmutableType::id).distinct().sorted()
    return JimmerModuleCompilationScope(
        currentImmutableTypeIds = typeIds,
        compilationSourceTypeIds = typeIds,
        cumulativeImmutableTypeIds = typeIds,
    )
}

private fun List<AggregateType>.commonPackageName(): String {
    if (isEmpty()) {
        return ""
    }
    val packageParts = map { type ->
        type.packageName.split('.').filter(String::isNotEmpty)
    }
    val shared = packageParts.first().toMutableList()
    for (parts in packageParts.drop(1)) {
        val commonSize = shared.indices.firstOrNull { index ->
            index >= parts.size || shared[index] != parts[index]
        } ?: minOf(shared.size, parts.size)
        if (commonSize < shared.size) {
            shared.subList(commonSize, shared.size).clear()
        }
    }
    return shared.joinToString(".")
}

private fun JimmerModuleSummaryKind.memberBaseName(simpleTypeName: String): String {
    return when (this) {
        JimmerModuleSummaryKind.IMMUTABLES -> "create$simpleTypeName"
        JimmerModuleSummaryKind.TABLES -> snakeUpper(simpleTypeName + "Table")
        JimmerModuleSummaryKind.TABLE_EXES -> snakeUpper(simpleTypeName + "TableEx")
        JimmerModuleSummaryKind.FETCHERS -> snakeUpper(simpleTypeName + "Fetcher")
    }
}

private fun snakeUpper(value: String): String {
    return buildString(value.length) {
        var previousLowercaseOrDigit = false
        value.forEach { character ->
            val lowercaseOrDigit = character.isLowerCase() || character.isDigit()
            if (previousLowercaseOrDigit && !lowercaseOrDigit) {
                append('_')
            }
            append(character.uppercaseChar())
            previousLowercaseOrDigit = lowercaseOrDigit
        }
    }
}

private fun validateSimpleName(value: String, role: String) {
    require(SIMPLE_NAME.matches(value)) {
        "Jimmer $role entry name must be a simple identifier: '$value'"
    }
}

private fun validateQualifiedTypeNames(values: List<String>, role: String) {
    require(values == values.distinct().sorted()) {
        "Retained Jimmer $role type names must be distinct and sorted"
    }
    require(values.none { value -> value.isBlank() || value != value.trim() || value.any(Char::isWhitespace) }) {
        "Retained Jimmer $role type names must be non-blank qualified names"
    }
}

private val IMMUTABLE_RESOURCE_KINDS = setOf(
    ImmutableTypeKind.IMMUTABLE,
    ImmutableTypeKind.EMBEDDABLE,
)
private const val ENTITIES_RESOURCE_PATH = "META-INF/jimmer/entities"
private const val IMMUTABLES_RESOURCE_PATH = "META-INF/jimmer/immutables"
private const val JIMMER_MODULE_SIMPLE_NAME = "JimmerModule"
private val SIMPLE_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
