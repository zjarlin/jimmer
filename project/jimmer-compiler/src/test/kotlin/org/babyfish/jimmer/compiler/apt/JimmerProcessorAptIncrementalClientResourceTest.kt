package org.babyfish.jimmer.compiler.apt

import java.io.File
import java.nio.charset.StandardCharsets
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.client.meta.impl.Schemas

class JimmerProcessorAptIncrementalClientResourceTest {

    @Test
    fun `declares the umbrella apt processor as aggregating`() {
        val classLoader = JimmerProcessor::class.java.classLoader
        val resource = requireNotNull(
            classLoader.getResource("META-INF/gradle/incremental.annotation.processors")
        )

        assertEquals(
            "org.babyfish.jimmer.compiler.apt.JimmerProcessor,aggregating",
            resource.readText().trim(),
        )
    }

    @Test
    fun `runs client aggregation when enable implicit api is the only trigger`() {
        val projectDir = createTempDirectory(prefix = "jimmer-compiler-client-implicit-trigger").toFile()
        val sourceDir = projectDir.resolve("src/main/java/demo")
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val markerFile = sourceDir.resolve("Application.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo;

                    import org.babyfish.jimmer.client.EnableImplicitApi;

                    @EnableImplicitApi
                    public class Application {}
                """.trimIndent()
            )
        }

        compile(
            sourceFiles = listOf(markerFile),
            classesDir = classesDir,
            generatedDir = generatedDir,
        )

        val resourceFile = classesDir.resolve(CLIENT_RESOURCE_PATH)
        assertTrue(resourceFile.isFile, "Missing client resource: ${resourceFile.absolutePath}")
        val schema = resourceFile.reader().use(Schemas::readFrom)
        assertTrue(schema.apiServiceMap.isEmpty())
    }

    @Test
    fun `rebuilds client resource after service rename and deletion`() {
        val projectDir = createTempDirectory(prefix = "jimmer-compiler-client-incremental").toFile()
        val sourceDir = projectDir.resolve("src/main/java/demo")
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val stableFile = sourceDir.writeService("StableService", "stable")
        val oldFile = sourceDir.writeService("OldService", "old")
        val removedFile = sourceDir.writeService("RemovedService", "removed")

        compile(
            sourceFiles = listOf(stableFile, oldFile, removedFile),
            classesDir = classesDir,
            generatedDir = generatedDir,
        )
        val resourceFile = classesDir.resolve(CLIENT_RESOURCE_PATH)
        val firstContent = resourceFile.readText()
        assertTrue("demo.StableService" in firstContent, firstContent)
        assertTrue("demo.OldService" in firstContent, firstContent)
        assertTrue("demo.RemovedService" in firstContent, firstContent)

        assertTrue(oldFile.delete())
        assertTrue(removedFile.delete())
        val renamedFile = sourceDir.writeService("RenamedService", "renamed")
        compile(
            sourceFiles = listOf(stableFile, renamedFile),
            classesDir = classesDir,
            generatedDir = generatedDir,
        )

        val secondContent = resourceFile.readText()
        assertTrue("demo.StableService" in secondContent, secondContent)
        assertTrue("demo.RenamedService" in secondContent, secondContent)
        assertFalse("demo.OldService" in secondContent, secondContent)
        assertFalse("demo.RemovedService" in secondContent, secondContent)
    }

    private fun compile(
        sourceFiles: List<File>,
        classesDir: File,
        generatedDir: File,
    ) {
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(success, diagnostics.errorMessage())
        assertTrue(
            diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            diagnostics.errorMessage(),
        )
    }

    private fun File.writeService(
        simpleName: String,
        operationName: String,
    ): File {
        return resolve("$simpleName.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo;

                    import org.babyfish.jimmer.client.meta.Api;

                    @Api
                    public interface $simpleName {
                        @Api
                        String $operationName();
                    }
                """.trimIndent()
            )
        }
    }

    private fun DiagnosticCollector<JavaFileObject>.errorMessage(): String {
        return diagnostics.joinToString(separator = "\n") { diagnostic ->
            val source = diagnostic.source?.name.orEmpty()
            val position = if (diagnostic.lineNumber > 0) {
                "${diagnostic.lineNumber}:${diagnostic.columnNumber}"
            } else {
                "?:?"
            }
            "${diagnostic.kind} $source:$position ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        const val CLIENT_RESOURCE_PATH = "META-INF/jimmer/client"
    }
}
