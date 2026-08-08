package org.babyfish.jimmer.compiler.module

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSymbolId

data class JimmerModuleSchema(
    val platform: CompilerPlatform,
    val options: JimmerModulePrecompileOptions,
    val packageName: String,
    val summaries: List<JimmerModuleSummary>,
    val module: JimmerModuleSource?,
    val resources: List<JimmerModuleResource>,
) {
    init {
        require(platform != CompilerPlatform.UNKNOWN) {
            "Jimmer module schema requires an APT or KSP platform"
        }
        require(options.platform == platform) {
            "Jimmer module options must match schema platform"
        }
        when (platform) {
            CompilerPlatform.APT -> require(module == null) {
                "APT Jimmer module schema cannot contain a Kotlin module source"
            }
            CompilerPlatform.KSP -> {
                require(summaries.isEmpty()) {
                    "KSP Jimmer module schema cannot contain APT summaries"
                }
                require(resources.none { resource -> resource.kind == JimmerModuleResourceKind.IMMUTABLES }) {
                    "KSP Jimmer module schema cannot contain an immutable index"
                }
            }
            CompilerPlatform.UNKNOWN -> error(
                "Jimmer module schema requires an APT or KSP platform"
            )
        }
    }
}

data class JimmerModuleSummary(
    val kind: JimmerModuleSummaryKind,
    val packageName: String,
    val simpleName: String,
    val members: List<JimmerModuleSummaryMember>,
    val dependencies: JimmerModuleArtifactDependencies,
) {
    init {
        require(members.map(JimmerModuleSummaryMember::typeId).distinct().size == members.size) {
            "Jimmer summary cannot reference the same type more than once: $kind"
        }
        require(members.map(JimmerModuleSummaryMember::generatedName).distinct().size == members.size) {
            "Jimmer summary member names must be distinct: $kind"
        }
        require(dependencies.typeIds == members.map(JimmerModuleSummaryMember::typeId).distinct().sorted()) {
            "Jimmer summary dependencies must match its members: $kind"
        }
    }
}

data class JimmerModuleSummaryMember(
    val typeId: LsiSymbolId,
    val qualifiedTypeName: String,
    val packageName: String,
    val simpleTypeName: String,
    val generatedName: String,
)

data class JimmerModuleSource(
    val packageName: String,
    val simpleName: String,
    val entityTypeIds: List<LsiSymbolId>,
    val entityNamePrefix: String?,
    val dependencies: JimmerModuleArtifactDependencies,
) {
    init {
        require(entityTypeIds == entityTypeIds.distinct().sorted()) {
            "Jimmer module entity type ids must be distinct and sorted"
        }
        require(dependencies.typeIds == entityTypeIds) {
            "Jimmer module dependencies must match its entity types"
        }
        require(entityNamePrefix == packageName.takeIf(String::isNotEmpty)?.plus('.')) {
            "Jimmer module entity name prefix must match its package"
        }
    }
}

data class JimmerModuleResource(
    val kind: JimmerModuleResourceKind,
    val path: String,
    val qualifiedTypeNames: List<String>,
    val contentTypeIds: List<LsiSymbolId>,
    val mergeExistingContent: Boolean,
    val dependencies: JimmerModuleArtifactDependencies,
) {
    init {
        require(qualifiedTypeNames == qualifiedTypeNames.distinct().sorted()) {
            "Jimmer module resource type names must be distinct and sorted: $path"
        }
        require(contentTypeIds == contentTypeIds.distinct().sorted()) {
            "Jimmer module resource type ids must be distinct and sorted: $path"
        }
        require(qualifiedTypeNames.size == contentTypeIds.size) {
            "Jimmer module resource names and ids must have the same size: $path"
        }
        require(dependencies.typeIds == contentTypeIds) {
            "Jimmer module resource dependencies must match its content: $path"
        }
        require(dependencies.aggregationMode == ArtifactAggregationMode.AGGREGATING) {
            "Jimmer module resources must be aggregating: $path"
        }
    }
}

data class JimmerModuleArtifactDependencies(
    val typeIds: List<LsiSymbolId>,
    val originatingTypeIds: List<LsiSymbolId>,
    val packageNames: List<String>,
    val scope: JimmerModuleDependencyScope,
    val aggregationMode: ArtifactAggregationMode = ArtifactAggregationMode.AGGREGATING,
) {
    init {
        require(typeIds == typeIds.distinct().sorted()) {
            "Jimmer module type dependencies must be distinct and sorted"
        }
        require(originatingTypeIds == originatingTypeIds.distinct().sorted()) {
            "Jimmer module originating type ids must be distinct and sorted"
        }
        require(packageNames == packageNames.distinct().sorted()) {
            "Jimmer module package dependencies must be distinct and sorted"
        }
        require(aggregationMode == ArtifactAggregationMode.AGGREGATING) {
            "Jimmer module artifacts must be aggregating"
        }
    }
}

enum class JimmerModuleSummaryKind {
    IMMUTABLES,
    TABLES,
    TABLE_EXES,
    FETCHERS,
}

enum class JimmerModuleResourceKind {
    ENTITIES,
    IMMUTABLES,
}

enum class JimmerModuleDependencyScope {
    MANAGED_TYPES_AND_EXISTING_RESOURCES,
    ALL_COMPILATION_SOURCES,
}
