package org.babyfish.jimmer.compiler

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource

enum class CompilerInputDocumentKind {
    DTO,
}

enum class CompilerSourceSet {
    MAIN,
    TEST,
}

data class CompilerInputDocument(
    val kind: CompilerInputDocumentKind,
    val sourceSet: CompilerSourceSet,
    val projectName: String,
    val sourceRoot: String,
    val relativePath: String,
    val content: String,
) : Comparable<CompilerInputDocument> {

    val source: LsiSource = LsiSource.of(
        path = "$projectName/$sourceRoot/$relativePath",
        language = LsiLanguage.UNKNOWN,
    )

    val fingerprint: String = sha256(
        listOf(
            kind.name,
            sourceSet.name,
            projectName,
            sourceRoot,
            relativePath,
            content,
        ).joinToString(separator = "\u0000") { value -> "${value.length}:$value" },
    )

    init {
        require(projectName.isNotBlank()) { "Compiler input document project name cannot be blank" }
        require(projectName == projectName.trim()) {
            "Compiler input document project name cannot have surrounding whitespace: '$projectName'"
        }
        require('/' !in projectName && '\\' !in projectName) {
            "Compiler input document project name cannot contain path separators: '$projectName'"
        }
        requireCompilerResourcePath(sourceRoot)
        requireCompilerResourcePath(relativePath)
        if (kind == CompilerInputDocumentKind.DTO) {
            require(relativePath.endsWith(".dto")) {
                "DTO compiler input document must use the .dto extension: '$relativePath'"
            }
        }
    }

    override fun compareTo(other: CompilerInputDocument): Int {
        val sourceComparison = source.compareTo(other.source)
        if (sourceComparison != 0) {
            return sourceComparison
        }
        val kindComparison = kind.compareTo(other.kind)
        if (kindComparison != 0) {
            return kindComparison
        }
        return sourceSet.compareTo(other.sourceSet)
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
