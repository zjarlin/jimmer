package site.addzero.lsi.codegen

import site.addzero.lsi.core.LsiSymbolId

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
    val originatingSymbols: Set<LsiSymbolId> = emptySet()
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
    }

    companion object {

        fun create(
            kind: ArtifactKind,
            path: String,
            content: String,
            aggregationMode: ArtifactAggregationMode,
            originatingSymbols: Set<LsiSymbolId> = emptySet()
        ): GeneratedArtifact = GeneratedArtifact(
            kind = kind,
            path = normalizePath(path),
            content = content,
            aggregationMode = aggregationMode,
            originatingSymbols = originatingSymbols.toSortedSet()
        )

        fun source(
            kind: ArtifactKind,
            qualifiedName: String,
            content: String,
            aggregationMode: ArtifactAggregationMode,
            originatingSymbols: Set<LsiSymbolId>
        ): GeneratedArtifact {
            require(kind.isSource) { "Source artifact must use JAVA_SOURCE or KOTLIN_SOURCE" }
            require(qualifiedName.isNotBlank()) { "Generated source qualified name cannot be blank" }
            val extension = when (kind) {
                ArtifactKind.JAVA_SOURCE -> "java"
                ArtifactKind.KOTLIN_SOURCE -> "kt"
                ArtifactKind.RESOURCE -> error("Resource artifact cannot be created as source")
            }
            val path = qualifiedName.replace('.', '/') + ".$extension"
            return create(kind, path, content, aggregationMode, originatingSymbols)
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
