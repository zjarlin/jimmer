package site.addzero.lsi.codegen

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.core.LsiSource

enum class ArtifactKind {
    JAVA_SOURCE,
    KOTLIN_SOURCE,
    RESOURCE;

    val isSource: Boolean
        get() = this != RESOURCE
}

enum class ArtifactAggregationMode {
    ISOLATING,
    AGGREGATING
}

/**
 * 根据完整来源集合判定增量产物类型，跨源语义依赖不能声明为 isolating。
 */
fun classifyArtifactAggregationMode(
    originatingSymbols: Set<LsiSymbolId>,
    originatingSources: Set<LsiSource>,
    dependencySources: Set<LsiSource>,
): ArtifactAggregationMode {
    if (originatingSymbols.size != 1 || originatingSources.size != 1) {
        return ArtifactAggregationMode.AGGREGATING
    }
    val originatingPath = originatingSources.single().path
    return if (dependencySources.all { source -> source.path == originatingPath }) {
        ArtifactAggregationMode.ISOLATING
    } else {
        ArtifactAggregationMode.AGGREGATING
    }
}

/**
 * 控制源码首次提交给平台 filer 的时机。
 */
enum class ArtifactEmissionMode {
    IMMEDIATE,
    STABLE,
}

data class GeneratedArtifactKey(
    val kind: ArtifactKind,
    val path: String
) : Comparable<GeneratedArtifactKey> {

    override fun compareTo(other: GeneratedArtifactKey): Int {
        val kindComparison = kind.compareTo(other.kind)
        if (kindComparison != 0) {
            return kindComparison
        }
        return path.compareTo(other.path)
    }
}

/**
 * 语言渲染器交给平台 filer 的完整生成产物。
 */
data class GeneratedArtifact(
    val kind: ArtifactKind,
    val path: String,
    val content: String,
    val aggregationMode: ArtifactAggregationMode,
    val emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
    val originatingSymbols: Set<LsiSymbolId> = emptySet(),
    val originatingSources: Set<LsiSource> = emptySet(),
    val dependencySymbols: Set<LsiSymbolId> = originatingSymbols,
    val dependencySources: Set<LsiSource> = originatingSources,
) {
    val key: GeneratedArtifactKey
        get() = GeneratedArtifactKey(kind, path)

    init {
        require(path.isNotBlank()) { "Generated artifact path cannot be blank" }
        require(path == normalizePath(path)) {
            "Generated artifact path must be normalized, use GeneratedArtifact.create(...): '$path'"
        }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(originatingSymbols.size == 1) {
                "Isolating generated artifact requires exactly one originating symbol: $path"
            }
        }
        require(dependencySymbols.containsAll(originatingSymbols)) {
            "Generated artifact dependencies must contain all originating symbols: $path"
        }
        require(dependencySources.containsAll(originatingSources)) {
            "Generated artifact dependencies must contain all originating sources: $path"
        }
        require(emissionMode != ArtifactEmissionMode.STABLE || kind.isSource) {
            "Stable generated artifact must be a source artifact: $path"
        }
        require(
            emissionMode != ArtifactEmissionMode.STABLE ||
                aggregationMode == ArtifactAggregationMode.AGGREGATING
        ) {
            "Stable generated artifact must be aggregating: $path"
        }
    }

    companion object {

        fun create(
            kind: ArtifactKind,
            path: String,
            content: String,
            aggregationMode: ArtifactAggregationMode,
            emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
            originatingSymbols: Set<LsiSymbolId> = emptySet(),
            originatingSources: Set<LsiSource> = emptySet(),
            dependencySymbols: Set<LsiSymbolId> = originatingSymbols,
            dependencySources: Set<LsiSource> = originatingSources,
        ): GeneratedArtifact = GeneratedArtifact(
            kind = kind,
            path = normalizePath(path),
            content = content,
            aggregationMode = aggregationMode,
            emissionMode = emissionMode,
            originatingSymbols = originatingSymbols.toSortedSet(),
            originatingSources = originatingSources.toSortedSet(),
            dependencySymbols = dependencySymbols.toSortedSet(),
            dependencySources = dependencySources.toSortedSet(),
        )

        fun source(
            kind: ArtifactKind,
            qualifiedName: String,
            content: String,
            aggregationMode: ArtifactAggregationMode,
            emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
            originatingSymbols: Set<LsiSymbolId>,
            originatingSources: Set<LsiSource> = emptySet(),
            dependencySymbols: Set<LsiSymbolId> = originatingSymbols,
            dependencySources: Set<LsiSource> = originatingSources,
        ): GeneratedArtifact {
            require(kind.isSource) { "Source artifact must use JAVA_SOURCE or KOTLIN_SOURCE" }
            require(qualifiedName.isNotBlank()) { "Generated source qualified name cannot be blank" }
            val extension = when (kind) {
                ArtifactKind.JAVA_SOURCE -> "java"
                ArtifactKind.KOTLIN_SOURCE -> "kt"
                ArtifactKind.RESOURCE -> error("Resource artifact cannot be created as source")
            }
            val path = qualifiedName.replace('.', '/') + ".$extension"
            return create(
                kind = kind,
                path = path,
                content = content,
                aggregationMode = aggregationMode,
                emissionMode = emissionMode,
                originatingSymbols = originatingSymbols,
                originatingSources = originatingSources,
                dependencySymbols = dependencySymbols,
                dependencySources = dependencySources,
            )
        }

        private fun normalizePath(path: String): String {
            val slashNormalized = path.trim().replace('\\', '/')
            require(!slashNormalized.startsWith('/')) {
                "Generated artifact path must be relative: '$path'"
            }
            val segments = slashNormalized.split('/')
            require(segments.none { segment -> segment.isBlank() || segment == "." || segment == ".." }) {
                "Generated artifact path contains an invalid segment: '$path'"
            }
            return segments.joinToString("/")
        }
    }
}
