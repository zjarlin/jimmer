package org.babyfish.jimmer.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyCompilerEntrypoints : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @get:Input
    abstract val expectedRelativePaths: SetProperty<String>

    @get:Input
    abstract val maxNonBlankLineCount: Property<Int>

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile
        val actual = sourceFiles.files
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        check(actual == expectedRelativePaths.get()) {
            "jimmer-compiler must contain exactly its processor entrypoints: $actual"
        }
        sourceFiles.files.forEach { file ->
            val source = file.readText()
            check("import " !in source) {
                "jimmer-compiler entrypoint must delegate without imports: ${file.relativeTo(root)}"
            }
            val nonBlankLineCount = source.lineSequence().count(String::isNotBlank)
            check(nonBlankLineCount <= maxNonBlankLineCount.get()) {
                "jimmer-compiler entrypoint must stay thin: ${file.relativeTo(root)} has " +
                    "$nonBlankLineCount non-blank lines"
            }
        }
    }
}
