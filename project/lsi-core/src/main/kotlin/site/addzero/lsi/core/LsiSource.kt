package site.addzero.lsi.core

enum class LsiLanguage {
    JAVA,
    KOTLIN,
    UNKNOWN
}

enum class LsiSourceKind {
    SOURCE,
    GENERATED,
    BINARY
}

/**
 * 使用统一斜杠表示的源码身份。
 */
data class LsiSource(
    val path: String,
    val language: LsiLanguage = LsiLanguage.UNKNOWN,
    val kind: LsiSourceKind = LsiSourceKind.SOURCE
) : Comparable<LsiSource> {

    init {
        require(path.isNotBlank()) { "LSI source path cannot be blank" }
        require(path == normalizePath(path)) {
            "LSI source path must be normalized, use LsiSource.of(...): '$path'"
        }
    }

    override fun compareTo(other: LsiSource): Int {
        val pathComparison = path.compareTo(other.path)
        if (pathComparison != 0) {
            return pathComparison
        }
        val languageComparison = language.compareTo(other.language)
        if (languageComparison != 0) {
            return languageComparison
        }
        return kind.compareTo(other.kind)
    }

    companion object {

        fun of(
            path: String,
            language: LsiLanguage = LsiLanguage.UNKNOWN,
            kind: LsiSourceKind = LsiSourceKind.SOURCE
        ): LsiSource = LsiSource(normalizePath(path), language, kind)

        private fun normalizePath(path: String): String {
            val slashNormalized = path.trim().replace('\\', '/')
            val segments = slashNormalized.split('/')
            val normalizedSegments = ArrayDeque<String>()
            for (segment in segments) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> {
                        require(normalizedSegments.isNotEmpty()) {
                            "LSI source path cannot escape its root: '$path'"
                        }
                        normalizedSegments.removeLast()
                    }
                    else -> normalizedSegments.addLast(segment)
                }
            }
            require(normalizedSegments.isNotEmpty()) { "LSI source path cannot be blank" }
            return normalizedSegments.joinToString("/")
        }
    }
}

/**
 * 一基行列坐标。
 */
data class LsiPosition(
    val line: Int,
    val column: Int
) : Comparable<LsiPosition> {

    init {
        require(line >= 1) { "LSI position line must be positive: $line" }
        require(column >= 1) { "LSI position column must be positive: $column" }
    }

    override fun compareTo(other: LsiPosition): Int {
        val lineComparison = line.compareTo(other.line)
        if (lineComparison != 0) {
            return lineComparison
        }
        return column.compareTo(other.column)
    }
}

data class LsiLocation(
    val source: LsiSource,
    val start: LsiPosition,
    val end: LsiPosition = start
) {

    init {
        require(end >= start) { "LSI location end cannot precede start: $start -> $end" }
    }
}

enum class LsiOriginKind {
    SOURCE,
    GENERATED,
    BINARY,
    SYNTHETIC
}

/**
 * 声明的来源以及生成链上的直接起点；语言表示前端冻结时观察到的投影视图。
 */
data class LsiOrigin(
    val kind: LsiOriginKind,
    val source: LsiSource? = null,
    val language: LsiLanguage = source?.language ?: LsiLanguage.UNKNOWN,
    val originatingSymbols: Set<LsiSymbolId> = emptySet()
) {

    init {
        if (kind == LsiOriginKind.SOURCE) {
            requireNotNull(source) { "Source declaration origin requires an LSI source" }
        }
        require(source == null || language == source.language) {
            "LSI origin language must match its source language"
        }
    }
}
