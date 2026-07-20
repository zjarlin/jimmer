package org.babyfish.jimmer.compiler.ddl.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerDdlCompilerKspFeatureTest {

    @Test
    fun `ksp feature generates ddl file from kotlin jimmer entity`() {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-ksp-test").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/KspBook.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(KOTLIN_ENTITY_SOURCE)
        }
        val outputDir = projectDir.resolve("build/generated/jimmer-ddl/main/resources/db/migration")
        val kspOutputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-ddl-ksp-test"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            processorOptions = mapOf(
                "jimmerDdl.enabled" to "true",
                "jimmerDdl.databaseType" to "postgresql",
                "jimmerDdl.outputFormat" to "plain",
                "jimmerDdl.outputDir" to outputDir.absolutePath,
                "jimmerDdl.description" to "ksp_generated",
                "jimmerDdl.compareDatabase" to "false",
            )
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
        val sqlFile = outputDir.resolve("ksp_generated.sql")
        assertTrue(sqlFile.isFile, "KSP should generate ddl file: ${sqlFile.absolutePath}")
        val sql = sqlFile.readText()
        assertContains(sql, """CREATE TABLE IF NOT EXISTS "ksp_book"""")
        assertContains(sql, """"id" BIGINT NOT NULL""")
        assertContains(sql, """"title" VARCHAR(255) NOT NULL""")
        assertContains(sql, """"subtitle" VARCHAR(255) NOT NULL""")

        val snapshotFile = projectDir.resolve(
            "build/generated/jimmer-ddl/main/resources/.jimmer-ddl/entity-table-snapshot.properties",
        )
        assertTrue(snapshotFile.isFile, "KSP should generate snapshot file: ${snapshotFile.absolutePath}")
        assertContains(snapshotFile.readText(), "demo.KspBook=ksp_book")
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

    companion object {
        private val KOTLIN_ENTITY_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Column
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.Table

            @Entity
            @Table(name = "ksp_book")
            interface KspBook {
                @Id
                val id: Long

                @Column(name = "title")
                val title: String

                @Column(name = "subtitle")
                val subtitle: String
            }
        """.trimIndent()

        private fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
