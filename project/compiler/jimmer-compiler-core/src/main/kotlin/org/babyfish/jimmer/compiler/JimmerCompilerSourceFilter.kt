package org.babyfish.jimmer.compiler

data class JimmerCompilerSourceFilter(
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
) {

    init {
        require(includes.none(String::isBlank)) { "Jimmer source include prefix cannot be blank" }
        require(excludes.none(String::isBlank)) { "Jimmer source exclude prefix cannot be blank" }
        require(includes == includes.distinct().sorted()) {
            "Jimmer source include prefixes must be distinct and sorted"
        }
        require(excludes == excludes.distinct().sorted()) {
            "Jimmer source exclude prefixes must be distinct and sorted"
        }
    }

    fun accepts(qualifiedName: String): Boolean {
        require(qualifiedName.isNotBlank()) { "Jimmer source qualified name cannot be blank" }
        if (includes.isNotEmpty() && includes.none(qualifiedName::startsWith)) {
            return false
        }
        return excludes.none(qualifiedName::startsWith)
    }

    companion object {
        fun from(options: Map<String, String>): JimmerCompilerSourceFilter {
            return JimmerCompilerSourceFilter(
                includes = options.parsePrefixes("jimmer.source.includes"),
                excludes = options.parsePrefixes("jimmer.source.excludes"),
            )
        }
    }
}

private fun Map<String, String>.parsePrefixes(optionName: String): List<String> {
    return get(optionName)
        ?.split(PREFIX_SEPARATOR)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.distinct()
        ?.sorted()
        .orEmpty()
}

private val PREFIX_SEPARATOR = Regex("[\\s,;]+")
