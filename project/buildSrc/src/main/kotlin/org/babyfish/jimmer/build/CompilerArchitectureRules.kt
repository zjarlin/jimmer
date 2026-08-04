package org.babyfish.jimmer.build

internal data class CompilerArchitectureSource(
    val relativePath: String,
    val content: String,
)

internal data class CompilerArchitectureRules(
    val allowedPlatformPathSegments: Set<String> = emptySet(),
    val allowedPoetPathSegments: Set<String> = emptySet(),
    val allowedPoetRelativePathPrefixes: Set<String> = emptySet(),
    val allowedPoetFileSuffixes: Set<String> = emptySet(),
    val forbiddenRelativePaths: Set<String> = emptySet(),
    val allowedImportPrefixes: Set<String> = emptySet(),
    val additionalForbiddenNamespaces: Set<String> = emptySet(),
    val directDependencyIds: Set<String> = emptySet(),
    val allowedDirectDependencyIds: Set<String> = emptySet(),
    val resolvedProjectDependencyIds: Set<String> = emptySet(),
    val allowedResolvedProjectDependencyIds: Set<String> = emptySet(),
    val resolvedModuleDependencyIds: Set<String> = emptySet(),
    val forbiddenModuleDependencyPrefixes: Set<String> = emptySet(),
    val allowedResolvedModuleDependencyIds: Set<String> = emptySet(),
)

internal fun findCompilerArchitectureViolations(
    sources: Collection<CompilerArchitectureSource>,
    rules: CompilerArchitectureRules,
): List<String> {
    val violations = mutableListOf<String>()
    sources.sortedBy(CompilerArchitectureSource::relativePath).forEach { source ->
        if (source.relativePath in rules.forbiddenRelativePaths) {
            violations += "${source.relativePath}: forbidden compiler service entry"
            return@forEach
        }
        val pathSegments = source.relativePath.split('/')
        val platformBoundary = pathSegments.any(rules.allowedPlatformPathSegments::contains)
        val rendererBoundary = rules.allowedPoetRelativePathPrefixes.any(
            source.relativePath::matchesRelativePathPrefix,
        ) || pathSegments.any(rules.allowedPoetPathSegments::contains) ||
            rules.allowedPoetFileSuffixes.any(source.relativePath::endsWith)
        val forbiddenNamespaces = buildList {
            if (!platformBoundary) {
                addAll(PLATFORM_NAMESPACES)
            }
            if (!rendererBoundary) {
                addAll(POET_NAMESPACES)
            }
            addAll(rules.additionalForbiddenNamespaces)
        }.distinct()
        val kotlinSource = source.relativePath.endsWith(".kt") ||
            source.relativePath.endsWith(".kts")
        val code = stripCommentsAndLiterals(
            source = source.content,
            supportsTemplates = kotlinSource,
            supportsNestedBlockComments = kotlinSource,
        )
        code.importTargets(source.relativePath).forEach { sourceImport ->
            if (
                rules.allowedImportPrefixes.isNotEmpty() &&
                rules.allowedImportPrefixes.none(sourceImport.target::matchesPrefix)
            ) {
                violations +=
                    "${source.relativePath}:${sourceImport.line}: forbidden import ${sourceImport.target}"
            }
        }
        forbiddenNamespaces.forEach { namespace ->
            namespace.toCodePattern().findAll(code).forEach { match ->
                violations +=
                    "${source.relativePath}:${code.lineNumberAt(match.range.first)}: ${namespace.removeSuffix(".")}"
            }
        }
    }
    (rules.directDependencyIds - rules.allowedDirectDependencyIds)
        .forEach { dependencyId -> violations += "dependency: forbidden direct dependency $dependencyId" }
    (rules.resolvedProjectDependencyIds - rules.allowedResolvedProjectDependencyIds)
        .forEach { dependencyId -> violations += "dependency: forbidden resolved project dependency $dependencyId" }
    rules.resolvedModuleDependencyIds
        .filterNot(rules.allowedResolvedModuleDependencyIds::contains)
        .filter { dependencyId ->
            rules.forbiddenModuleDependencyPrefixes.any(dependencyId::startsWith)
        }
        .forEach { dependencyId -> violations += "dependency: forbidden resolved module dependency $dependencyId" }
    return violations.distinct().sorted()
}

private data class SourceImport(
    val line: Int,
    val target: String,
)

private fun String.importTargets(relativePath: String): List<SourceImport> {
    if (relativePath.endsWith(".java")) {
        return JAVA_IMPORT_DECLARATION.findAll(this)
            .mapNotNull { match -> match.toSourceImport(this) }
            .toList()
    }
    return lineSequence()
        .flatMapIndexed { index, line ->
            KOTLIN_IMPORT_DECLARATION.findAll(line)
                .mapNotNull { match ->
                    match.normalizedImportTarget()?.let { target ->
                        SourceImport(index + 1, target)
                    }
                }
        }
        .toList()
}

private fun MatchResult.toSourceImport(source: String): SourceImport? {
    return normalizedImportTarget()?.let { target ->
        SourceImport(source.lineNumberAt(requireNotNull(groups[1]).range.first), target)
    }
}

private fun MatchResult.normalizedImportTarget(): String? {
    return groupValues[1]
        .trim()
        .replaceFirst(KOTLIN_ALIAS_SUFFIX, "")
        .filterNot(Char::isWhitespace)
        .takeIf(String::isNotEmpty)
}

private fun String.matchesPrefix(prefix: String): Boolean {
    return this == prefix.removeSuffix(".") || startsWith(prefix)
}

private fun String.matchesRelativePathPrefix(prefix: String): Boolean {
    val normalizedPrefix = prefix.trim('/')
    return normalizedPrefix.isNotEmpty() &&
        (this == normalizedPrefix || startsWith("$normalizedPrefix/"))
}

private fun String.toCodePattern(): Regex {
    val packagePrefix = endsWith('.')
    val qualifiedName = removeSuffix(".")
        .split('.')
        .joinToString("\\s*\\.\\s*") { segment -> Regex.escape(segment) }
    val suffix = if (packagePrefix) {
        "\\s*\\.\\s*(?=[A-Za-z_$])"
    } else {
        "(?=[^A-Za-z0-9_$]|$)"
    }
    return Regex("(?<![A-Za-z0-9_$])$qualifiedName$suffix")
}

private fun String.lineNumberAt(offset: Int): Int {
    var line = 1
    for (index in 0 until offset) {
        if (this[index] == '\n') {
            line++
        }
    }
    return line
}

private fun stripCommentsAndLiterals(
    source: String,
    supportsTemplates: Boolean,
    supportsNestedBlockComments: Boolean,
): String {
    val result = StringBuilder(source.length)
    var state = LexicalState.CODE
    val returnStates = ArrayDeque<LexicalState>()
    val templateDepths = ArrayDeque<Int>()
    var blockCommentDepth = 0
    var index = 0
    while (index < source.length) {
        val character = source[index]
        val next = source.getOrNull(index + 1)
        val third = source.getOrNull(index + 2)
        when (state) {
            LexicalState.CODE,
            LexicalState.TEMPLATE_CODE,
            -> when {
                state == LexicalState.TEMPLATE_CODE && character == '{' -> {
                    val depth = templateDepths.removeLast()
                    templateDepths.addLast(depth + 1)
                    result.append(character)
                    index++
                }
                state == LexicalState.TEMPLATE_CODE && character == '}' -> {
                    val depth = templateDepths.removeLast()
                    if (depth == 1) {
                        result.append(' ')
                        state = returnStates.removeLastOrCode()
                    } else {
                        templateDepths.addLast(depth - 1)
                        result.append(character)
                    }
                    index++
                }
                character == '/' && next == '/' -> {
                    result.append("  ")
                    returnStates.addLast(state)
                    state = LexicalState.LINE_COMMENT
                    index += 2
                }
                character == '/' && next == '*' -> {
                    result.append("  ")
                    returnStates.addLast(state)
                    state = LexicalState.BLOCK_COMMENT
                    blockCommentDepth = 1
                    index += 2
                }
                character == '"' && next == '"' && third == '"' -> {
                    result.append("   ")
                    returnStates.addLast(state)
                    state = LexicalState.TRIPLE_QUOTED_LITERAL
                    index += 3
                }
                character == '"' -> {
                    result.append(' ')
                    returnStates.addLast(state)
                    state = LexicalState.QUOTED_LITERAL
                    index++
                }
                character == '\'' -> {
                    result.append(' ')
                    returnStates.addLast(state)
                    state = LexicalState.CHARACTER_LITERAL
                    index++
                }
                else -> {
                    result.append(character)
                    index++
                }
            }
            LexicalState.LINE_COMMENT -> {
                if (character == '\n') {
                    result.append('\n')
                    state = returnStates.removeLastOrCode()
                } else {
                    result.append(' ')
                }
                index++
            }
            LexicalState.BLOCK_COMMENT -> {
                if (supportsNestedBlockComments && character == '/' && next == '*') {
                    result.append("  ")
                    blockCommentDepth++
                    index += 2
                } else if (character == '*' && next == '/') {
                    result.append("  ")
                    blockCommentDepth--
                    if (blockCommentDepth == 0) {
                        state = returnStates.removeLastOrCode()
                    }
                    index += 2
                } else {
                    result.append(if (character == '\n') '\n' else ' ')
                    index++
                }
            }
            LexicalState.QUOTED_LITERAL,
            LexicalState.CHARACTER_LITERAL,
            -> {
                val terminator = if (state == LexicalState.QUOTED_LITERAL) '"' else '\''
                when {
                    character == '\\' && next != null -> {
                        result.append(' ')
                        result.append(if (next == '\n') '\n' else ' ')
                        index += 2
                    }
                    state == LexicalState.QUOTED_LITERAL &&
                        supportsTemplates &&
                        character == '$' &&
                        next == '{' -> {
                        result.append("  ")
                        returnStates.addLast(state)
                        templateDepths.addLast(1)
                        state = LexicalState.TEMPLATE_CODE
                        index += 2
                    }
                    character == terminator -> {
                        result.append(' ')
                        state = returnStates.removeLastOrCode()
                        index++
                    }
                    character == '\n' -> {
                        result.append('\n')
                        state = returnStates.removeLastOrCode()
                        index++
                    }
                    else -> {
                        result.append(' ')
                        index++
                    }
                }
            }
            LexicalState.TRIPLE_QUOTED_LITERAL -> {
                if (character == '"' && next == '"' && third == '"') {
                    result.append("   ")
                    state = returnStates.removeLastOrCode()
                    index += 3
                } else if (supportsTemplates && character == '$' && next == '{') {
                    result.append("  ")
                    returnStates.addLast(state)
                    templateDepths.addLast(1)
                    state = LexicalState.TEMPLATE_CODE
                    index += 2
                } else {
                    result.append(if (character == '\n') '\n' else ' ')
                    index++
                }
            }
        }
    }
    return result.toString()
}

private enum class LexicalState {
    CODE,
    TEMPLATE_CODE,
    LINE_COMMENT,
    BLOCK_COMMENT,
    QUOTED_LITERAL,
    TRIPLE_QUOTED_LITERAL,
    CHARACTER_LITERAL,
}

private fun ArrayDeque<LexicalState>.removeLastOrCode(): LexicalState {
    return if (isEmpty()) LexicalState.CODE else removeLast()
}

private val PLATFORM_NAMESPACES = listOf(
    "javax.annotation.processing",
    "javax.lang.model",
    "javax.tools",
    "com.google.devtools.ksp",
    "com.sun.source",
    "com.sun.tools.javac",
)

private val POET_NAMESPACES = listOf(
    "com.squareup.javapoet",
    "com.squareup.kotlinpoet",
)

private val KOTLIN_ALIAS_SUFFIX = Regex("\\s+as\\s+[^\\s;]+$")

private val KOTLIN_IMPORT_DECLARATION = Regex(
    pattern = "(?:^|(?<=;))\\s*import\\s+(?:static\\s+)?([^;]+?)(?=\\s*;|$)",
)

private val JAVA_IMPORT_DECLARATION = Regex(
    pattern = "(?:^|(?<=;))\\s*import\\s+(?:static\\s+)?([^;]+);",
)
