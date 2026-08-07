package org.babyfish.jimmer.compiler.ddl.apt

import java.io.File
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor

class JimmerDdlCompilerAptFeatureTest {

    @Test
    fun `apt feature generates ddl file from java jimmer entity`() {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-apt-test").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        val outputDir = projectDir.resolve("build/generated/jimmer-ddl/main/resources/db/migration")
        writeJavaSource(
            sourceDir = sourceDir,
            path = "demo/AptBook.java",
            content = """
                package demo;

                import org.babyfish.jimmer.sql.Column;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Table;

                @Entity
                @Table(name = "apt_book")
                public interface AptBook {
                    @Id
                    long id();

                    @Column(name = "title")
                    String title();

                    @Column(name = "subtitle")
                    String subtitle();
                }
            """.trimIndent(),
        )

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run APT integration tests")
        val sourceFiles = sourceDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .toList()
        classesDir.mkdirs()
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-AjimmerDdl.enabled=true",
                    "-AjimmerDdl.databaseType=postgresql",
                    "-AjimmerDdl.outputFormat=plain",
                    "-AjimmerDdl.outputDir=${outputDir.absolutePath}",
                    "-AjimmerDdl.description=apt_generated",
                    "-AjimmerDdl.compareDatabase=false",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            val compiled = task.call()
            assertTrue(compiled, diagnostics.toErrorMessage())
        }

        val sqlFile = outputDir.resolve("apt_generated.sql")
        assertTrue(sqlFile.isFile, "APT should generate ddl file: ${sqlFile.absolutePath}")
        val sql = sqlFile.readText()
        assertContains(sql, """CREATE TABLE IF NOT EXISTS "apt_book"""")
        assertContains(sql, """"id" BIGINT NOT NULL""")
        assertContains(sql, """"title" VARCHAR(255) NOT NULL""")
        assertContains(sql, """"subtitle" VARCHAR(255) NOT NULL""")

        val snapshotFile = projectDir.resolve(
            "build/generated/jimmer-ddl/main/resources/.jimmer-ddl/entity-table-snapshot/apt_book.properties",
        )
        assertTrue(snapshotFile.isFile, "APT should generate snapshot file: ${snapshotFile.absolutePath}")
        assertContains(snapshotFile.readText(), "entity.demo.AptBook=apt_book")
    }

    private fun writeJavaSource(
        sourceDir: File,
        path: String,
        content: String,
    ) {
        val file = sourceDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
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
}
