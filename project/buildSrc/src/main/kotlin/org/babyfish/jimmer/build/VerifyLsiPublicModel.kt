package org.babyfish.jimmer.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyLsiPublicModel : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @get:Input
    abstract val requiredInterfaces: SetProperty<String>

    @get:Input
    abstract val forbiddenLegacyNames: SetProperty<String>

    init {
        requiredInterfaces.convention(emptySet())
        forbiddenLegacyNames.convention(emptySet())
    }

    @TaskAction
    fun verify() {
        val baseDirectoryFile = baseDirectory.get().asFile
        val sources = sourceFiles.files.map { file ->
            LsiPublicModelSource(
                relativePath = file.relativeTo(baseDirectoryFile).invariantSeparatorsPath,
                content = file.readText(),
            )
        }
        val violations = findLsiPublicModelViolations(
            sources = sources,
            requiredInterfaces = requiredInterfaces.get(),
            forbiddenLegacyNames = forbiddenLegacyNames.get(),
        )
        check(violations.isEmpty()) {
            "LSI public model violations:\n" + violations.joinToString("\n")
        }
    }
}

internal data class LsiPublicModelSource(
    val relativePath: String,
    val content: String,
)

internal fun findLsiPublicModelViolations(
    sources: Collection<LsiPublicModelSource>,
    requiredInterfaces: Set<String>,
    forbiddenLegacyNames: Set<String>,
): List<String> {
    val interfaces = linkedSetOf<String>()
    val violations = mutableListOf<String>()
    val requiredSimpleNames = requiredInterfaces.mapTo(hashSetOf()) { qualifiedName ->
        qualifiedName.substringAfterLast('.')
    }

    sources.sortedBy(LsiPublicModelSource::relativePath).forEach { source ->
        val code = stripCommentsAndLiterals(
            source = source.content,
            supportsTemplates = true,
            supportsNestedBlockComments = true,
        )
        val packageName = PACKAGE_DECLARATION.find(code)?.groupValues?.get(1).orEmpty()
        INTERFACE_DECLARATION.findAll(code).forEach { match ->
            interfaces += qualifiedName(packageName, match.groupValues[1])
        }
        CONCRETE_DECLARATION.findAll(code).forEach { match ->
            val simpleName = match.groupValues[1]
            if (simpleName in requiredSimpleNames) {
                violations +=
                    "${source.relativePath}:${code.lineNumberAt(match.range.first)}: " +
                        "structural LSI declaration $simpleName must be an interface"
            }
        }
        ANY_TYPE_DECLARATION.findAll(code).forEach { match ->
            val simpleName = match.groupValues[1]
            if (simpleName in forbiddenLegacyNames) {
                violations +=
                    "${source.relativePath}:${code.lineNumberAt(match.range.first)}: " +
                        "forbidden legacy LSI declaration $simpleName"
            }
        }
    }
    (requiredInterfaces - interfaces).sorted().forEach { requiredInterface ->
        violations += "missing required LSI interface $requiredInterface"
    }
    return violations.distinct().sorted()
}

private fun qualifiedName(packageName: String, simpleName: String): String {
    return if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
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

private val PACKAGE_DECLARATION = Regex(
    "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)",
)

private val INTERFACE_DECLARATION = Regex(
    "\\b(?:sealed\\s+)?interface\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b",
)

private val CONCRETE_DECLARATION = Regex(
    "\\b(?:(?:data|value|enum|annotation)\\s+class|class|typealias)\\s+" +
        "([A-Za-z_$][A-Za-z0-9_$]*)\\b",
)

private val ANY_TYPE_DECLARATION = Regex(
    "\\b(?:(?:data|value|enum|annotation)\\s+class|class|interface|typealias)\\s+" +
        "([A-Za-z_$][A-Za-z0-9_$]*)\\b",
)
