package org.babyfish.jimmer.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyCompilerFeatureSpi : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val baseDirectoryFile = baseDirectory.get().asFile
        val sources = sourceFiles.files.map { file ->
            CompilerFeatureSpiSource(
                relativePath = file.relativeTo(baseDirectoryFile).invariantSeparatorsPath,
                content = file.readText(),
            )
        }
        val violations = findCompilerFeatureSpiViolations(sources)
        check(violations.isEmpty()) {
            "Compiler feature SPI violations:\n" + violations.joinToString("\n")
        }
    }
}

internal data class CompilerFeatureSpiSource(
    val relativePath: String,
    val content: String,
)

internal fun findCompilerFeatureSpiViolations(
    sources: Collection<CompilerFeatureSpiSource>,
): List<String> {
    val violations = mutableListOf<String>()
    sources.sortedBy(CompilerFeatureSpiSource::relativePath).forEach { source ->
        when {
            source.relativePath.endsWith(OLD_FEATURE_SERVICE_PATH) -> {
                violations += "${source.relativePath}: forbidden legacy feature service"
            }
            source.relativePath.endsWith(FEATURE_SERVICE_PATH) -> {
                violations += serviceViolations(source)
            }
            source.relativePath.endsWith(".kt") || source.relativePath.endsWith(".java") -> {
                violations += codeViolations(source)
            }
        }
    }
    return violations.distinct().sorted()
}

private fun serviceViolations(source: CompilerFeatureSpiSource): List<String> {
    val implementations = source.content
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    if (implementations.isEmpty()) {
        return listOf("${source.relativePath}: empty feature service")
    }
    return implementations.mapIndexedNotNull { index, implementation ->
        implementation.takeUnless(FEATURE_IMPLEMENTATION::matches)?.let {
            "${source.relativePath}:${index + 1}: invalid feature implementation $implementation"
        }
    }
}

private fun codeViolations(source: CompilerFeatureSpiSource): List<String> {
    val kotlinSource = source.relativePath.endsWith(".kt")
    val code = stripCommentsAndLiterals(
        source = source.content,
        supportsTemplates = kotlinSource,
        supportsNestedBlockComments = kotlinSource,
    )
    return buildList {
        FORBIDDEN_FEATURE_IDENTIFIERS.findAll(code).forEach { match ->
            add(
                "${source.relativePath}:${code.lineNumberAt(match.range.first)}: " +
                    "forbidden feature identifier ${match.value}",
            )
        }
        MANUAL_FEATURE_STATE_CAST.findAll(code).forEach { match ->
            add(
                "${source.relativePath}:${code.lineNumberAt(match.range.first)}: " +
                    "manual feature state cast ${match.value.trim()}",
            )
        }
    }
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

private const val FEATURE_SERVICE_PATH =
    "/META-INF/services/site.addzero.lsi.compiler.CompilerFeature"

private const val OLD_FEATURE_SERVICE_PATH =
    "/META-INF/services/site.addzero.lsi.compiler.CompilerFeatureProvider"

private val FEATURE_IMPLEMENTATION = Regex(
    "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*\\.[A-Z][A-Za-z0-9_$]*Feature",
)

private val FORBIDDEN_FEATURE_IDENTIFIERS = Regex(
    "\\b(?:CompilerFeatureProvider|CompilerFeatureDescriptor|CompilerFeatures|FEATURE_ID|" +
        "Jimmer[A-Za-z0-9_$]*Feature(?:State|Status)?|[A-Z][A-Za-z0-9_$]*FeatureProvider)\\b",
)

private val MANUAL_FEATURE_STATE_CAST = Regex(
    "\\bas\\s+[A-Za-z_$][A-Za-z0-9_$.]*(?:FeatureState|FeatureCollection|FeaturePrecompileResult)\\b",
)
