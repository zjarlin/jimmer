package org.babyfish.jimmer.compiler.input

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerSourceSet

internal class FileSystemCompilerInputDocumentScanner {

    private val referenceFreezer = CompilerInputDocumentReferenceFreezer()

    fun scan(
        startPaths: Collection<File>,
        requestedKinds: Set<CompilerInputDocumentKind>,
        sourceSet: CompilerSourceSet,
        options: Map<String, String>,
    ): List<CompilerInputDocumentSnapshot> {
        if (CompilerInputDocumentKind.DTO !in requestedKinds) {
            return emptyList()
        }
        val sourceRoots = dtoSourceRoots(sourceSet, options)
        if (startPaths.isEmpty() || sourceRoots.isEmpty()) {
            return emptyList()
        }
        val projects = startPaths
            .mapNotNull { startPath -> findProject(startPath, sourceRoots) }
            .distinctBy { project -> project.root.canonicalPath }
            .sortedBy { project -> project.root.canonicalPath }
        val documents = projects.flatMap { project -> project.documents(sourceSet) }.sorted()
        require(documents.distinctBy { document -> document.kind to document.source.path }.size == documents.size) {
            "Compiler input document paths collide across projects: " +
                documents.groupingBy { document -> document.source.path }
                    .eachCount()
                    .filterValues { count -> count > 1 }
                    .keys
                    .sorted()
                    .joinToString()
        }
        return documents.map(referenceFreezer::freeze)
    }

    private fun findProject(
        startPath: File,
        sourceRoots: List<String>,
    ): InputProject? {
        var candidate = startPath.absoluteFile.let { file -> if (file.isDirectory) file else file.parentFile }
        while (candidate != null) {
            val existingRoots = sourceRoots.filter { sourceRoot -> candidate.resolve(sourceRoot).isDirectory }
            if (existingRoots.isNotEmpty()) {
                return InputProject(candidate.canonicalFile, existingRoots)
            }
            candidate = candidate.parentFile
        }
        return null
    }

    private fun InputProject.documents(sourceSet: CompilerSourceSet): List<CompilerInputDocument> {
        return sourceRoots.flatMap { sourceRoot ->
            val sourceRootDirectory = root.resolve(sourceRoot)
            sourceRootDirectory.walkTopDown()
                .filter(File::isFile)
                .filter { file -> file.name.endsWith(".dto") }
                .map { file ->
                    val relativePath = file.relativeTo(sourceRootDirectory).invariantSeparatorsPath
                    CompilerInputDocument(
                        kind = CompilerInputDocumentKind.DTO,
                        sourceSet = sourceSet,
                        projectName = root.name,
                        sourceRoot = sourceRoot,
                        relativePath = relativePath,
                        content = readUtf8(file),
                    )
                }
                .toList()
        }
    }

    private fun readUtf8(file: File): String {
        return try {
            Files.readString(file.toPath(), StandardCharsets.UTF_8)
        } catch (exception: IOException) {
            throw IllegalStateException("Cannot read compiler input document '${file.absolutePath}'", exception)
        }
    }

    private data class InputProject(
        val root: File,
        val sourceRoots: List<String>,
    )
}

private fun dtoSourceRoots(
    sourceSet: CompilerSourceSet,
    options: Map<String, String>,
): List<String> {
    val optionName = when (sourceSet) {
        CompilerSourceSet.MAIN -> "jimmer.dto.dirs"
        CompilerSourceSet.TEST -> "jimmer.dto.testDirs"
    }
    val requiredPrefix = when (sourceSet) {
        CompilerSourceSet.MAIN -> "src/main/"
        CompilerSourceSet.TEST -> "src/test/"
    }
    val defaultRoot = when (sourceSet) {
        CompilerSourceSet.MAIN -> "src/main/dto"
        CompilerSourceSet.TEST -> "src/test/dto"
    }
    val configuredValue = options[optionName]
    val configuredRoots = if (configuredValue.isNullOrBlank()) {
        listOf(defaultRoot)
    } else {
        configuredValue
            .split(Regex("\\s*[,:;]\\s*"))
            .mapNotNull { rawPath -> rawPath.normalizeDocumentRoot() }
    }
    configuredRoots.forEach { sourceRoot ->
        require(sourceRoot.startsWith(requiredPrefix)) {
            "Compiler option '$optionName' contains '$sourceRoot' which does not start with '$requiredPrefix'"
        }
    }
    val orderedRoots = configuredRoots.distinct().sortedWith(
        compareBy<String> { sourceRoot -> sourceRoot.length }.thenBy { sourceRoot -> sourceRoot }
    )
    return orderedRoots.filter { candidate ->
        orderedRoots.none { possibleParent ->
            possibleParent.length < candidate.length &&
                candidate.startsWith(possibleParent) &&
                candidate[possibleParent.length] == '/'
        }
    }
}

private fun String.normalizeDocumentRoot(): String? {
    val normalized = trim().replace('\\', '/').trim('/')
    if (normalized.isEmpty()) {
        return null
    }
    require(normalized.split('/').none { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
        "Compiler input document root is not normalized: '$this'"
    }
    return normalized
}
