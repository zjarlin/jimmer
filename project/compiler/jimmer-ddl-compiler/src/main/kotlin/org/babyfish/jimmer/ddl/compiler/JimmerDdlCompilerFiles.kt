package org.babyfish.jimmer.ddl.compiler

import java.io.File

object JimmerDdlCompilerFiles {
    private const val SNAPSHOT_DIR_NAME = ".jimmer-ddl"
    private const val SNAPSHOT_DIRECTORY_NAME = "entity-table-snapshot"
    private const val SOURCE_FINGERPRINT_FILE_NAME = "source-fingerprint.properties"
    private const val FLYWAY_HISTORY_FILE_NAME = "applied-flyway-scripts.txt"

    fun resolveOutputFile(settings: JimmerDdlCompilerSettings): File {
        val outputDir = File(settings.outputDir)
        return File(outputDir, settings.outputFileName)
    }

    fun resolveSnapshotDirectory(settings: JimmerDdlCompilerSettings): File? {
        val projectDir = settings.outputDir.toProjectDir() ?: return null
        return projectDir.resolve(SNAPSHOT_DIR_NAME).resolve(SNAPSHOT_DIRECTORY_NAME)
    }

    fun resolveGeneratedSnapshotDirectory(settings: JimmerDdlCompilerSettings): File {
        val resourcesDir = settings.outputDir.toGeneratedResourcesDir()
        return resourcesDir.resolve(SNAPSHOT_DIR_NAME).resolve(SNAPSHOT_DIRECTORY_NAME)
    }

    fun resolveBuildSourceFingerprintFile(settings: JimmerDdlCompilerSettings): File? {
        val projectDir = settings.outputDir.toProjectDir() ?: return null
        return projectDir.resolve("build/jimmer-ddl").resolve(SOURCE_FINGERPRINT_FILE_NAME)
    }

    fun writeOutputFile(
        settings: JimmerDdlCompilerSettings,
        sql: String,
    ): File {
        val outputFile = resolveOutputFile(settings)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(sql)
        return outputFile
    }

    fun writeGeneratedFlywayHistory(
        settings: JimmerDdlCompilerSettings,
        history: JimmerDdlFlywayHistory,
    ): File {
        val historyFile = settings.outputDir.toGeneratedResourcesDir()
            .resolve(SNAPSHOT_DIR_NAME)
            .resolve(FLYWAY_HISTORY_FILE_NAME)
        val content = buildList {
            add("available=${history.available}")
            addAll(history.appliedScripts.sorted())
        }.joinToString(separator = "\n", postfix = "\n")
        historyFile.parentFile.mkdirs()
        historyFile.writeText(content)
        return historyFile
    }

    private fun String.toProjectDir(): File? {
        val absoluteOutputDir = File(this).absoluteFile.path
        return absoluteOutputDir
            .substringBefore("${File.separator}build${File.separator}", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let(::File)
    }

    private fun String.toGeneratedResourcesDir(): File {
        val outputDir = File(this).absoluteFile
        val parent = outputDir.parentFile
        if (outputDir.name == "migration" && parent?.name == "db") {
            return parent.parentFile ?: outputDir
        }
        return outputDir
    }
}
