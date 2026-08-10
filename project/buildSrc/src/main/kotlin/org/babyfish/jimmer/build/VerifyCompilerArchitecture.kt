package org.babyfish.jimmer.build

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyCompilerArchitecture : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @get:Input
    abstract val allowedPlatformPathSegments: SetProperty<String>

    @get:Input
    abstract val allowedPoetPathSegments: SetProperty<String>

    @get:Input
    abstract val allowedPoetRelativePathPrefixes: SetProperty<String>

    @get:Input
    abstract val allowedPoetFileSuffixes: SetProperty<String>

    @get:Input
    abstract val forbiddenRelativePaths: SetProperty<String>

    @get:Input
    abstract val expectedRelativePaths: SetProperty<String>

    @get:Input
    abstract val allowedImportPrefixes: SetProperty<String>

    @get:Input
    abstract val additionalForbiddenNamespaces: SetProperty<String>

    @get:Input
    abstract val directDependencyIds: SetProperty<String>

    @get:Input
    abstract val allowedDirectDependencyIds: SetProperty<String>

    @get:Input
    abstract val resolvedProjectDependencyIds: SetProperty<String>

    @get:Input
    abstract val allowedResolvedProjectDependencyIds: SetProperty<String>

    @get:Input
    abstract val resolvedModuleDependencyIds: SetProperty<String>

    @get:Input
    abstract val forbiddenModuleDependencyPrefixes: SetProperty<String>

    @get:Input
    abstract val allowedResolvedModuleDependencyIds: SetProperty<String>

    init {
        allowedPlatformPathSegments.convention(emptySet())
        allowedPoetPathSegments.convention(emptySet())
        allowedPoetRelativePathPrefixes.convention(emptySet())
        allowedPoetFileSuffixes.convention(emptySet())
        forbiddenRelativePaths.convention(emptySet())
        expectedRelativePaths.convention(emptySet())
        allowedImportPrefixes.convention(emptySet())
        additionalForbiddenNamespaces.convention(emptySet())
        directDependencyIds.convention(emptySet())
        allowedDirectDependencyIds.convention(emptySet())
        resolvedProjectDependencyIds.convention(emptySet())
        allowedResolvedProjectDependencyIds.convention(emptySet())
        resolvedModuleDependencyIds.convention(emptySet())
        forbiddenModuleDependencyPrefixes.convention(emptySet())
        allowedResolvedModuleDependencyIds.convention(emptySet())
    }

    fun captureDependencies(
        configurations: ConfigurationContainer,
        allowedDirectIds: Set<String>,
        allowedResolvedProjectIds: Set<String>,
        forbiddenResolvedModulePrefixes: Set<String>,
        allowedResolvedModuleIds: Set<String> = emptySet(),
    ) {
        configurations.configureEach(
            Action<Configuration> {
                if (name in MAIN_DEPENDENCY_CONFIGURATION_NAMES) {
                    dependencies.all(
                        Action<Dependency> {
                            directDependencyIds.add(architectureId())
                        }
                    )
                }
            }
        )
        val currentProjectPath = project.path
        val compileClasspath = configurations.named("compileClasspath")
        val runtimeClasspath = configurations.named("runtimeClasspath")
        allowedDirectDependencyIds.set(allowedDirectIds)
        resolvedProjectDependencyIds.set(
            compileClasspath.zip(runtimeClasspath) { compile, runtime ->
                listOf(compile, runtime)
                    .flatMap { configuration ->
                        configuration.incoming.resolutionResult.allComponents
                    }
                    .mapNotNull { component ->
                        (component.id as? ProjectComponentIdentifier)
                            ?.projectPath
                            ?.takeUnless { projectPath -> projectPath == currentProjectPath }
                            ?.let(::projectDependencyId)
                    }
                    .toSet()
            }
        )
        allowedResolvedProjectDependencyIds.set(allowedResolvedProjectIds)
        resolvedModuleDependencyIds.set(
            compileClasspath.zip(runtimeClasspath) { compile, runtime ->
                listOf(compile, runtime)
                    .flatMap { configuration ->
                        configuration.incoming.resolutionResult.allComponents
                    }
                    .mapNotNull { component ->
                        (component.id as? ModuleComponentIdentifier)?.let { identifier ->
                            "module:${identifier.group}:${identifier.module}"
                        }
                    }
                    .toSet()
            }
        )
        forbiddenModuleDependencyPrefixes.set(forbiddenResolvedModulePrefixes)
        allowedResolvedModuleDependencyIds.set(allowedResolvedModuleIds)
    }

    @TaskAction
    fun verify() {
        val baseDirectoryFile = baseDirectory.get().asFile
        val rules = CompilerArchitectureRules(
            allowedPlatformPathSegments = allowedPlatformPathSegments.get(),
            allowedPoetPathSegments = allowedPoetPathSegments.get(),
            allowedPoetRelativePathPrefixes = allowedPoetRelativePathPrefixes.get(),
            allowedPoetFileSuffixes = allowedPoetFileSuffixes.get(),
            forbiddenRelativePaths = forbiddenRelativePaths.get(),
            allowedImportPrefixes = allowedImportPrefixes.get(),
            additionalForbiddenNamespaces = additionalForbiddenNamespaces.get(),
            directDependencyIds = directDependencyIds.get(),
            allowedDirectDependencyIds = allowedDirectDependencyIds.get(),
            resolvedProjectDependencyIds = resolvedProjectDependencyIds.get(),
            allowedResolvedProjectDependencyIds = allowedResolvedProjectDependencyIds.get(),
            resolvedModuleDependencyIds = resolvedModuleDependencyIds.get(),
            forbiddenModuleDependencyPrefixes = forbiddenModuleDependencyPrefixes.get(),
            allowedResolvedModuleDependencyIds = allowedResolvedModuleDependencyIds.get(),
        )
        val sources = sourceFiles.files.map { file ->
            CompilerArchitectureSource(
                relativePath = file.relativeTo(baseDirectoryFile).invariantSeparatorsPath,
                content = file.readText(),
            )
        }
        expectedRelativePaths.get().takeIf(Set<String>::isNotEmpty)?.let { expected ->
            val actual = sources.map(CompilerArchitectureSource::relativePath).toSet()
            check(actual == expected) {
                "Compiler architecture source set mismatch: expected $expected, actual $actual"
            }
        }
        val violations = findCompilerArchitectureViolations(sources, rules)
        check(violations.isEmpty()) {
            "Compiler architecture boundary violations:\n" + violations.joinToString("\n")
        }
    }

    private fun Dependency.architectureId(): String {
        return when (this) {
            is ProjectDependency -> projectDependencyId(path)
            else -> "module:${group ?: "<unspecified>"}:$name"
        }
    }

    private fun projectDependencyId(projectPath: String): String {
        return "project:${projectPath.removePrefix(":")}"
    }

    private companion object {
        val MAIN_DEPENDENCY_CONFIGURATION_NAMES = setOf(
            "api",
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "annotationProcessor",
            "ksp",
            "kapt",
        )
    }
}
