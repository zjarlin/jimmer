package org.babyfish.jimmer.compiler

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind

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
    val origin: CompilerInputDocumentOrigin,
    val relativePath: String,
    val content: String,
) : Comparable<CompilerInputDocument> {

    val source: LsiSource = LsiSource.of(
        path = "${origin.sourcePathPrefix}/$relativePath",
        language = LsiLanguage.UNKNOWN,
        kind = origin.sourceKind,
    )

    val fingerprint: String = sha256(
        listOf(
            kind.name,
            sourceSet.name,
            origin.fingerprintValue,
            relativePath,
            content,
        ).joinToString(separator = "\u0000") { value -> "${value.length}:$value" },
    )

    init {
        requireCompilerResourcePath(relativePath)
        val sourceRoot = when (val documentOrigin = origin) {
            is CompilerInputDocumentOrigin.Project -> documentOrigin.sourceRoot
            is CompilerInputDocumentOrigin.Bundle -> documentOrigin.sourceRoot
        }
        val requiredSourceRootPrefix = when (sourceSet) {
            CompilerSourceSet.MAIN -> "src/main/"
            CompilerSourceSet.TEST -> "src/test/"
        }
        require(sourceRoot.startsWith(requiredSourceRootPrefix)) {
            "Compiler input document source root '$sourceRoot' does not match $sourceSet"
        }
        if (origin is CompilerInputDocumentOrigin.Bundle) {
            require(origin.contentSha256 == sha256(content)) {
                "Compiler input document content does not match bundle checksum: '${source.path}'"
            }
        }
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

sealed interface CompilerInputDocumentOrigin {

    val sourcePathPrefix: String

    val sourceKind: LsiSourceKind

    val fingerprintValue: String

    data class Project(
        val projectName: String,
        val sourceRoot: String,
    ) : CompilerInputDocumentOrigin {

        override val sourcePathPrefix: String = "$projectName/$sourceRoot"

        override val sourceKind: LsiSourceKind = LsiSourceKind.SOURCE

        override val fingerprintValue: String = "project\u0000$projectName\u0000$sourceRoot"

        init {
            require(projectName.isNotBlank()) { "Compiler input document project name cannot be blank" }
            require(projectName == projectName.trim()) {
                "Compiler input document project name cannot have surrounding whitespace: '$projectName'"
            }
            require('/' !in projectName && '\\' !in projectName) {
                "Compiler input document project name cannot contain path separators: '$projectName'"
            }
            requireCompilerResourcePath(sourceRoot)
        }
    }

    data class Bundle(
        val bundleId: String,
        val sourceRoot: String,
        val resourcePath: String,
        val contentSha256: String,
    ) : CompilerInputDocumentOrigin {

        override val sourcePathPrefix: String = "dto-bundle/$bundleId/$sourceRoot"

        override val sourceKind: LsiSourceKind = LsiSourceKind.BINARY

        override val fingerprintValue: String =
            "bundle\u0000$bundleId\u0000$sourceRoot"

        init {
            require(BUNDLE_ID_REGEX.matches(bundleId)) {
                "Compiler input document bundle id is invalid: '$bundleId'"
            }
            requireCompilerResourcePath(sourceRoot)
            requireCompilerResourcePath(resourcePath)
            require(SHA_256_REGEX.matches(contentSha256)) {
                "Compiler input document bundle checksum must be lowercase SHA-256: '$contentSha256'"
            }
        }
    }
}

private val BUNDLE_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*")

private val SHA_256_REGEX = Regex("[0-9a-f]{64}")

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
