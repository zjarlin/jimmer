package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.jimmer.input.*

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentKind
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerInputDocumentProvider
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.compiler.CompilerSourceSet

class CompilerInputDocumentScanner(
    requestedKinds: Set<CompilerInputDocumentKind>,
    options: Map<String, String>,
    bundleClassLoader: ClassLoader = CompilerInputDocumentScanner::class.java.classLoader,
) : CompilerInputDocumentProvider {

    private val referenceFreezer = CompilerInputDocumentReferenceFreezer()

    private val scansDto = DTO_INPUT_DOCUMENT_KIND in requestedKinds

    private val sourceRootsBySourceSet = if (scansDto) {
        CompilerSourceSet.entries.associateWith { sourceSet -> dtoSourceRoots(sourceSet, options) }
    } else {
        emptyMap()
    }

    private val bundleEnabled = scansDto && CompilerInputDocumentBundleReader.isEnabled(options)

    private val bundleSnapshots: List<CompilerInputDocumentSnapshot> by lazy {
        if (bundleEnabled) {
            try {
                CompilerInputDocumentBundleReader(bundleClassLoader)
                    .read()
                    .map(referenceFreezer::freeze)
            } catch (exception: Exception) {
                throw IllegalStateException("Cannot load compiler input DTO bundles: ${exception.message}", exception)
            }
        } else {
            emptyList()
        }
    }

    private var fileSystemSnapshots: List<CompilerInputDocumentSnapshot>? = null

    private var fileSystemSourceSet: CompilerSourceSet? = null

    override fun scan(
        startPaths: Collection<File>,
        sourceSet: CompilerSourceSet,
    ): List<CompilerInputDocumentSnapshot> {
        if (!scansDto) {
            return emptyList()
        }
        if (fileSystemSnapshots == null && startPaths.isNotEmpty()) {
            val sourceRoots = sourceRootsBySourceSet.getValue(sourceSet)
            fileSystemSourceSet = sourceSet
            fileSystemSnapshots = scanFileSystem(startPaths, sourceRoots, sourceSet)
        } else if (fileSystemSnapshots != null) {
            require(fileSystemSourceSet == sourceSet) {
                "Compiler input document source set changed within one session: " +
                    "$fileSystemSourceSet -> $sourceSet"
            }
        }
        return mergeSnapshots(
            bundleSnapshots.filter { snapshot -> snapshot.document.sourceSet == sourceSet } +
                fileSystemSnapshots.orEmpty()
        )
    }

    override fun isFileSystemDiscoveryComplete(sourceSet: CompilerSourceSet): Boolean {
        return !scansDto || sourceRootsBySourceSet.getValue(sourceSet).isEmpty() || fileSystemSnapshots != null
    }

    private fun scanFileSystem(
        startPaths: Collection<File>,
        sourceRoots: List<String>,
        sourceSet: CompilerSourceSet,
    ): List<CompilerInputDocumentSnapshot> {
        if (sourceRoots.isEmpty()) {
            return emptyList()
        }
        val projects = startPaths
            .mapNotNull { startPath -> findProject(startPath, sourceRoots) }
            .distinctBy { project -> project.root.canonicalPath }
            .sortedBy { project -> project.root.canonicalPath }
        return projects
            .flatMap { project -> project.documents(sourceSet) }
            .sorted()
            .map(referenceFreezer::freeze)
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
                        kind = DTO_INPUT_DOCUMENT_KIND,
                        sourceSet = sourceSet,
                        origin = CompilerInputDocumentOrigin.Project(
                            projectName = root.name,
                            sourceRoot = sourceRoot,
                        ),
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

private fun mergeSnapshots(
    snapshots: List<CompilerInputDocumentSnapshot>,
): List<CompilerInputDocumentSnapshot> {
    return snapshots
        .groupBy { snapshot -> snapshot.document.kind to snapshot.document.source.path }
        .toSortedMap(compareBy<Pair<CompilerInputDocumentKind, String>> { it.second }.thenBy { it.first })
        .map { (key, candidates) ->
            val distinctFingerprints = candidates.map { candidate -> candidate.document.fingerprint }.distinct()
            require(distinctFingerprints.size == 1) {
                "Compiler input documents conflict at '${key.second}'"
            }
            candidates.first()
        }
        .sorted()
}

private fun dtoSourceRoots(
    sourceSet: CompilerSourceSet,
    options: Map<String, String>,
): List<String> {
    val optionName = when (sourceSet) {
        CompilerSourceSet.MAIN -> "jimmer.dto.dirs"
        CompilerSourceSet.TEST -> "jimmer.dto.testDirs"
    }
    val requiredPrefix = sourceSet.dtoSourceRootPrefix
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

internal val CompilerSourceSet.dtoSourceRootPrefix: String
    get() = when (this) {
        CompilerSourceSet.MAIN -> "src/main/"
        CompilerSourceSet.TEST -> "src/test/"
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
