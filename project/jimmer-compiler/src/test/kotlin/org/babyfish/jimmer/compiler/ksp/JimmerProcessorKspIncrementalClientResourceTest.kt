package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JimmerProcessorKspIncrementalClientResourceTest {

    @Test
    fun `rebuilds aggregating client resource after service rename and deletion`() {
        val projectDir = createTempDirectory(prefix = "jimmer-ksp-client-incremental").toFile()
        val sourceDir = projectDir.resolve("src/main/kotlin/demo")
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val stableFile = sourceDir.writeService("StableService", "stable")
        val oldFile = sourceDir.writeService("OldService", "old")
        val removedFile = sourceDir.writeService("RemovedService", "removed")
        val firstSources = listOf(stableFile, oldFile, removedFile)

        val first = compile(
            projectDir = projectDir,
            outputDir = outputDir,
            sourceFiles = firstSources,
            modifiedSources = firstSources,
        )
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, first.exitCode, first.logger.text())
        val resourceFile = outputDir.resolve("resources/$CLIENT_RESOURCE_PATH")
        val firstContent = resourceFile.readText()
        assertTrue("demo.StableService" in firstContent, firstContent)
        assertTrue("demo.OldService" in firstContent, firstContent)
        assertTrue("demo.RemovedService" in firstContent, firstContent)

        assertTrue(oldFile.delete())
        assertTrue(removedFile.delete())
        val renamedFile = sourceDir.writeService("RenamedService", "renamed")
        val second = compile(
            projectDir = projectDir,
            outputDir = outputDir,
            sourceFiles = listOf(stableFile, renamedFile),
            modifiedSources = listOf(renamedFile),
            removedSources = listOf(oldFile, removedFile),
        )

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, second.exitCode, second.logger.text())
        val secondContent = resourceFile.readText()
        assertTrue("demo.StableService" in secondContent, secondContent)
        assertTrue("demo.RenamedService" in secondContent, secondContent)
        assertFalse("demo.OldService" in secondContent, secondContent)
        assertFalse("demo.RemovedService" in secondContent, secondContent)
    }

    private fun compile(
        projectDir: File,
        outputDir: File,
        sourceFiles: List<File>,
        modifiedSources: List<File>,
        removedSources: List<File> = emptyList(),
    ): CompilationResult {
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-ksp-client-incremental"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            incremental = true
            incrementalLog = true
            this.modifiedSources = modifiedSources
            this.removedSources = removedSources
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        return CompilationResult(exitCode, logger)
    }

    private fun File.writeService(
        simpleName: String,
        operationName: String,
    ): File {
        return resolve("$simpleName.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo

                    import org.babyfish.jimmer.client.meta.Api

                    @Api
                    interface $simpleName {
                        @Api
                        fun $operationName(): String
                    }
                """.trimIndent()
            )
        }
    }

    private class CapturingKspLogger : KSPLogger {
        private val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun exception(e: Throwable) {
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class CompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
    )

    private companion object {
        const val CLIENT_RESOURCE_PATH = "META-INF/jimmer/client"

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }
    }
}
