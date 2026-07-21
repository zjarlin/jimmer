package org.babyfish.jimmer.compiler.immutable

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
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerImmutableEmbeddableGoldenTest {

    @Test
    fun `apt embeddable sources match migration golden`() {
        assertGoldens("apt", compileApt())
    }

    @Test
    fun `ksp embeddable source matches migration golden`() {
        assertGoldens("ksp", compileKsp())
    }

    @Test
    fun `apt classifies embeddable prop expressions through lsi`() {
        val expression = compileApt(
            source = JAVA_EXPRESSION_SOURCE,
            typeName = "ExpressionKinds",
        ).getValue("ExpressionKindsPropExpression.java").toString(StandardCharsets.UTF_8)

        assertContains(expression, "PropExpression<Boolean> active()")
        assertContains(expression, "PropExpression.Num<Character> code()")
        assertContains(expression, "PropExpression.Cmp<Boolean> boxedActive()")
        assertContains(expression, "PropExpression.Num<BigDecimal> amount()")
        assertContains(expression, "PropExpression.Dt<Date> legacyDate()")
        assertContains(expression, "PropExpression.Tp<LocalDate> date()")
        assertContains(expression, "PropExpression.Cmp<UUID> uuid()")
        assertContains(expression, "PropExpression<Map<String, String>> attributes()")
    }

    private fun compileApt(
        source: String = JAVA_SOURCE,
        typeName: String = "Location",
    ): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-embeddable-apt-golden").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/$typeName.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable embeddable APT golden tests")
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
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
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertTrue(task.call(), diagnostics.toErrorMessage())
        }
        return listOf(
            "${typeName}Props.java",
            "${typeName}PropExpression.java",
        ).associateWith { name ->
            val generatedFile = generatedDir.resolve("demo/$name")
            assertTrue(generatedFile.isFile, "APT embeddable output is missing: ${generatedFile.absolutePath}")
            generatedFile.readBytes()
        }
    }

    private fun compileKsp(): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-embeddable-ksp-golden").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Source.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(KOTLIN_SOURCE)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-embeddable-ksp-golden"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
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
        val propsFile = kotlinOutputDir.resolve("demo/SourceProps.kt")
        assertTrue(propsFile.isFile, "KSP embeddable output is missing: ${propsFile.absolutePath}")
        return mapOf("SourceProps.kt" to propsFile.readBytes())
    }

    private fun assertGoldens(platform: String, actualFiles: Map<String, ByteArray>) {
        val missing = mutableListOf<String>()
        for ((name, actual) in actualFiles) {
            val resourcePath = "/immutable/embeddable/$platform/$name"
            val expected = javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            if (expected == null) {
                val outputFile = File(
                    System.getProperty("java.io.tmpdir"),
                    "jimmer-immutable-embeddable-golden/$platform/$name",
                )
                outputFile.parentFile.mkdirs()
                outputFile.writeBytes(actual)
                missing += "$resourcePath -> ${outputFile.absolutePath}"
            } else {
                assertContentEquals(expected, actual, resourcePath)
            }
        }
        assertTrue(missing.isEmpty(), "Missing golden resources:\n${missing.joinToString("\n")}")
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

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Embeddable;
            import org.jspecify.annotations.Nullable;

            /** 配送地址。 */
            @Embeddable
            public interface Location {
                /** 城市名称。 */
                String city();

                /** 邮政编码。 */
                @Nullable
                Integer zipCode();
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Embeddable

            /** 配送地址。 */
            @Embeddable
            interface Location {
                /** 城市名称。 */
                val city: String

                /** 邮政编码。 */
                val zipCode: Int?
            }
        """.trimIndent()

        val JAVA_EXPRESSION_SOURCE = """
            package demo;

            import java.math.BigDecimal;
            import java.time.LocalDate;
            import java.util.Map;
            import java.util.UUID;
            import org.babyfish.jimmer.sql.Embeddable;

            @Embeddable
            public interface ExpressionKinds {
                boolean active();

                char code();

                Boolean boxedActive();

                BigDecimal amount();

                java.util.Date legacyDate();

                LocalDate date();

                UUID uuid();

                Map<String, String> attributes();
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
