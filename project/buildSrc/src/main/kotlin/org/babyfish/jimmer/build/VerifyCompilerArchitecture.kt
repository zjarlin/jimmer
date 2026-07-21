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
    abstract val allowedPoetFileSuffixes: SetProperty<String>

    init {
        allowedPlatformPathSegments.convention(emptySet())
        allowedPoetPathSegments.convention(emptySet())
        allowedPoetFileSuffixes.convention(emptySet())
    }

    @TaskAction
    fun verify() {
        val baseDirectoryFile = baseDirectory.get().asFile
        val platformSegments = allowedPlatformPathSegments.get()
        val poetSegments = allowedPoetPathSegments.get()
        val poetFileSuffixes = allowedPoetFileSuffixes.get()
        val violations = sourceFiles.files
            .sortedBy { file -> file.invariantSeparatorsPath }
            .flatMap { file ->
                val relativePath = file.relativeTo(baseDirectoryFile).invariantSeparatorsPath
                val pathSegments = relativePath.split('/')
                val platformBoundary = pathSegments.any(platformSegments::contains)
                val rendererBoundary = pathSegments.any(poetSegments::contains) ||
                    poetFileSuffixes.any(file.name::endsWith)
                file.readLines().mapIndexedNotNull { index, line ->
                    val forbiddenNamespace = if (platformBoundary) {
                        null
                    } else {
                        PLATFORM_NAMESPACES.firstOrNull(line::contains)
                    } ?: if (rendererBoundary) {
                        null
                    } else {
                        POET_NAMESPACES.firstOrNull(line::contains)
                    }
                    forbiddenNamespace?.let { namespace ->
                        "$relativePath:${index + 1}: $namespace"
                    }
                }
            }
        check(violations.isEmpty()) {
            "Compiler architecture boundary violations:\n" + violations.joinToString("\n")
        }
    }

    private companion object {
        val PLATFORM_NAMESPACES = listOf(
            "javax.annotation.processing",
            "javax.lang.model",
            "com.google.devtools.ksp",
        )

        val POET_NAMESPACES = listOf(
            "com.squareup.javapoet",
            "com.squareup.kotlinpoet",
        )
    }
}
