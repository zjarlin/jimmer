package org.babyfish.jimmer.compiler.ddl

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerDdlCompilerFrontendParityTest {

    @Test
    fun `apt and ksp generate identical ddl and snapshots from equivalent inheritance`() {
        val apt = compileApt()
        val ksp = compileKsp()

        assertContains(apt.sqlText, "\"status\" INTEGER NOT NULL DEFAULT 1")
        assertMigrationGolden("apt", apt)
        assertMigrationGolden("ksp", ksp)
        assertContentEquals(apt.sqlBytes, ksp.sqlBytes)
        assertEquals(apt.snapshotBytes.keys, ksp.snapshotBytes.keys)
        apt.snapshotBytes.forEach { (name, bytes) ->
            assertContentEquals(bytes, ksp.snapshotBytes.getValue(name), name)
        }
    }

    private fun compileApt(): DdlOutput {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Book.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(JAVA_SOURCE)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val outputDir = projectDir.ddlOutputDir()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run DDL APT parity tests")
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-classpath",
                    System.getProperty("java.class.path"),
                ) + ddlProcessorOptions(outputDir).map { (name, value) -> "-A$name=$value" },
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertTrue(task.call(), diagnostics.toErrorMessage())
        }
        return outputDir.readDdlOutput(projectDir)
    }

    private fun compileKsp(): DdlOutput {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-ksp-parity").toFile()
        val sourceFiles = listOf(
            projectDir.resolve("src/main/kotlin/demo/BookBase.kt").also { file ->
                file.parentFile.mkdirs()
                file.writeText(KOTLIN_BASE_SOURCE)
            },
            projectDir.resolve("src/main/kotlin/demo/Book.kt").also { file ->
                file.parentFile.mkdirs()
                file.writeText(KOTLIN_ENTITY_SOURCE)
            },
        )
        val outputDir = projectDir.ddlOutputDir()
        val kspOutputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-ddl-parity"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            processorOptions = ddlProcessorOptions(outputDir)
            projectBaseDir = projectDir
            outputBaseDir = kspOutputDir
            cachesDir = kspOutputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = kspOutputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = kspOutputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = kspOutputDir.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = kspOutputDir.resolve("resources").apply(File::mkdirs)
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
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.text())
        return outputDir.readDdlOutput(projectDir)
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
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class DdlOutput(
        val sqlBytes: ByteArray,
        val snapshotBytes: Map<String, ByteArray>,
    ) {
        val sqlText: String
            get() = String(sqlBytes, StandardCharsets.UTF_8)
    }

    private fun assertMigrationGolden(platform: String, output: DdlOutput) {
        assertContentEquals(
            migrationGolden("/ddl/$platform/generated.sql"),
            output.sqlBytes,
            "DDL $platform SQL differs from migration golden",
        )
        output.snapshotBytes.forEach { (name, bytes) ->
            assertContentEquals(
                migrationGolden("/ddl/$platform/$name"),
                bytes,
                "DDL $platform snapshot '$name' differs from migration golden",
            )
        }
    }

    private fun migrationGolden(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream(path)) {
            "Missing DDL migration golden: $path"
        }.use { stream -> stream.readBytes() }
    }

    private companion object {
        val JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Column;
            import org.babyfish.jimmer.sql.Default;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.Table;

            @MappedSuperclass
            interface BookBase {
                @Id
                long id();

                @Default("0")
                int status();
            }

            @Entity
            @Table(name = "book")
            public interface Book extends BookBase {
                @Override
                @Default("1")
                int status();

                @Column(name = "title")
                String title();
            }
        """.trimIndent()

        val KOTLIN_BASE_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Default
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.MappedSuperclass

            @MappedSuperclass
            interface BookBase {
                @Id
                val id: Long

                @Default("0")
                val status: Int
            }
        """.trimIndent()

        val KOTLIN_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Column
            import org.babyfish.jimmer.sql.Default
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Table

            @Entity
            @Table(name = "book")
            interface Book : BookBase {
                @Default("1")
                override val status: Int

                @Column(name = "title")
                val title: String
            }
        """.trimIndent()

        fun ddlProcessorOptions(outputDir: File): Map<String, String> = linkedMapOf(
            "jimmerDdl.enabled" to "true",
            "jimmerDdl.databaseType" to "postgresql",
            "jimmerDdl.outputFormat" to "plain",
            "jimmerDdl.outputDir" to outputDir.absolutePath,
            "jimmerDdl.description" to "generated",
            "jimmerDdl.compareDatabase" to "false",
        )

        fun File.ddlOutputDir(): File {
            return resolve("build/generated/jimmer-ddl/main/resources/db/migration")
        }

        fun File.readDdlOutput(projectDir: File): DdlOutput {
            val sqlFile = resolve("generated.sql")
            val snapshotDirectory = projectDir.resolve(
                "build/generated/jimmer-ddl/main/resources/.jimmer-ddl/entity-table-snapshot",
            )
            assertTrue(sqlFile.isFile, "DDL output is missing: ${sqlFile.absolutePath}")
            assertTrue(snapshotDirectory.isDirectory, "DDL snapshot is missing: ${snapshotDirectory.absolutePath}")
            val snapshot = snapshotDirectory.listFiles { file -> file.isFile && file.extension == "properties" }
                .orEmpty()
                .sortedBy(File::getName)
                .associate { file -> file.name to file.readBytes() }
            return DdlOutput(sqlFile.readBytes(), snapshot)
        }

        fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
            return diagnostics.joinToString("\n") { diagnostic ->
                "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                    "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
            }
        }

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
