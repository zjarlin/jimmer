package org.babyfish.jimmer.compiler.client

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.Schemas
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class ClientClasspathDefinitionFrontendParityTest {

    @Test
    fun `apt and ksp render the complete reachable classpath pojo closure`() {
        val projectDir = createTempDirectory(prefix = "jimmer-client-classpath-parity").toFile()
        val dependencyClasses = compileDependency(projectDir)

        val aptContent = compileApt(projectDir, dependencyClasses, directPojoFixture())
        val kspContent = compileKsp(projectDir, dependencyClasses, directPojoFixture())

        assertEquals(aptContent, kspContent)
        val schema = Schemas.readFrom(StringReader(aptContent))
        val payload = schema.typeDefinitionMap.getValue(TypeName.parse("external.Payload"))
        val nested = schema.typeDefinitionMap.getValue(TypeName.parse("external.Nested"))
        assertEquals(
            setOf("nested", "rawCount", "boxedCount", "validatedCount"),
            payload.propMap.keys,
        )
        assertEquals(listOf("name"), nested.propMap.keys.toList())
        assertFalse(payload.propMap.getValue("rawCount").type.isNullable)
        assertTrue(payload.propMap.getValue("boxedCount").type.isNullable)
        assertTrue(payload.propMap.getValue("validatedCount").type.isNullable)
    }

    @Test
    fun `apt and ksp render classpath pojo reached through immutable converter target`() {
        val projectDir = createTempDirectory(prefix = "jimmer-client-converter-classpath-parity").toFile()
        val dependencyClasses = compileDependency(projectDir)
        val fixture = immutableConverterFixture()

        val aptContent = compileApt(projectDir, dependencyClasses, fixture)
        val kspContent = compileKsp(projectDir, dependencyClasses, fixture)

        assertEquals(aptContent, kspContent)
        val schema = Schemas.readFrom(StringReader(aptContent))
        val book = schema.typeDefinitionMap.getValue(TypeName.parse("demo.ConvertedBook"))
        val externalPojo = schema.typeDefinitionMap.getValue(TypeName.parse("external.ExternalPojo"))
        val nested = schema.typeDefinitionMap.getValue(TypeName.parse("external.Nested"))
        assertEquals(
            TypeName.parse("external.ExternalPojo"),
            book.propMap.getValue("payload").type.typeName,
        )
        assertEquals(listOf("nested"), externalPojo.propMap.keys.toList())
        assertEquals(listOf("name"), nested.propMap.keys.toList())
    }

    @Test
    fun `apt and ksp render classpath pojo reached through json value`() {
        val projectDir = createTempDirectory(prefix = "jimmer-client-json-value-classpath-parity").toFile()
        val dependencyClasses = compileDependency(projectDir)
        val fixture = jsonValueFixture()

        val aptContent = compileApt(projectDir, dependencyClasses, fixture)
        val kspContent = compileKsp(projectDir, dependencyClasses, fixture)

        assertEquals(aptContent, kspContent)
        val schema = Schemas.readFrom(StringReader(aptContent))
        val externalPojo = schema.typeDefinitionMap.getValue(TypeName.parse("external.ExternalPojo"))
        val nested = schema.typeDefinitionMap.getValue(TypeName.parse("external.Nested"))
        assertTrue(TypeName.parse("external.JsonValueEnvelope") !in schema.typeDefinitionMap)
        assertEquals(listOf("nested"), externalPojo.propMap.keys.toList())
        assertEquals(listOf("name"), nested.propMap.keys.toList())
    }

    private fun compileDependency(projectDir: File): File {
        val sourceDir = projectDir.resolve("dependency/src/main/java")
        val classesDir = projectDir.resolve("dependency/build/classes").apply(File::mkdirs)
        val notNullFile = sourceDir.resolve("jakarta/validation/constraints/NotNull.java").writeSource(
            VALIDATION_NOT_NULL_SOURCE,
        )
        val payloadFile = sourceDir.resolve("external/Payload.java").writeSource(
            """
                package external;

                import jakarta.validation.constraints.NotNull;

                public class Payload {
                    public Nested getNested() {
                        return null;
                    }

                    public int getRawCount() {
                        return 0;
                    }

                    public Integer getBoxedCount() {
                        return null;
                    }

                    @NotNull
                    public Integer getValidatedCount() {
                        return null;
                    }
                }
            """.trimIndent()
        )
        val nestedFile = sourceDir.resolve("external/Nested.java").writeSource(
            """
                package external;

                public class Nested {
                    public String getName() {
                        return null;
                    }
                }
            """.trimIndent()
        )
        val externalPojoFile = sourceDir.resolve("external/ExternalPojo.java").writeSource(
            """
                package external;

                public class ExternalPojo {
                    public Nested getNested() {
                        return null;
                    }
                }
            """.trimIndent()
        )
        val jsonValueEnvelopeFile = sourceDir.resolve("external/JsonValueEnvelope.java").writeSource(
            """
                package external;

                import com.fasterxml.jackson.annotation.JsonValue;

                public class JsonValueEnvelope {
                    @JsonValue
                    public ExternalPojo value() {
                        return null;
                    }
                }
            """.trimIndent()
        )
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Client classpath parity tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:none", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjects(
                    notNullFile,
                    payloadFile,
                    nestedFile,
                    externalPojoFile,
                    jsonValueEnvelopeFile,
                ),
            )
            task.call()
        }
        assertTrue(success, diagnostics.errorMessage())
        return classesDir
    }

    private fun compileApt(
        projectDir: File,
        dependencyClasses: File,
        fixture: FrontendFixture,
    ): String {
        val sourceFiles = fixture.aptSources.map { (path, content) ->
            projectDir.resolve("apt/src/main/java/$path").writeSource(content)
        }
        val classesDir = projectDir.resolve("apt/build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("apt/build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Client classpath parity tests require a JDK compiler")
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
                    classpath(dependencyClasses),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(success, diagnostics.errorMessage())
        val resource = classesDir.resolve(CLIENT_RESOURCE_PATH)
        assertTrue(resource.isFile, "Missing APT client resource: ${resource.absolutePath}")
        return resource.readText()
    }

    private fun compileKsp(
        projectDir: File,
        dependencyClasses: File,
        fixture: FrontendFixture,
    ): String {
        val sourceFiles = fixture.kspSources.map { (path, content) ->
            projectDir.resolve("ksp/src/main/kotlin/$path").writeSource(content)
        }
        val outputDir = projectDir.resolve("ksp/build").apply(File::mkdirs)
        val resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-client-classpath-parity"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath() + dependencyClasses
            projectBaseDir = projectDir.resolve("ksp")
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            this.resourceOutputDir = resourceOutputDir
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
        val resource = resourceOutputDir.resolve(CLIENT_RESOURCE_PATH)
        assertTrue(resource.isFile, "Missing KSP client resource: ${resource.absolutePath}")
        return resource.readText()
    }

    private fun File.writeSource(content: String): File {
        parentFile.mkdirs()
        writeText(content)
        return this
    }

    private fun classpath(dependencyClasses: File): String {
        return (runtimeClasspath() + dependencyClasses).joinToString(File.pathSeparator)
    }

    private fun runtimeClasspathText(): String = runtimeClasspath().joinToString(File.pathSeparator)

    private fun runtimeClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::exists)
    }

    private fun directPojoFixture(): FrontendFixture {
        return FrontendFixture(
            aptSources = mapOf(
                "demo/ExternalService.java" to
                    """
                        package demo;

                        import external.Payload;
                        import org.babyfish.jimmer.client.meta.Api;

                        @Api
                        public interface ExternalService {
                            @Api
                            Payload find();
                        }
                    """.trimIndent()
            ),
            kspSources = mapOf(
                "demo/ExternalService.kt" to
                    """
                        package demo

                        import external.Payload
                        import org.babyfish.jimmer.client.meta.Api

                        @Api
                        interface ExternalService {
                            @Api
                            fun find(): Payload
                        }
                    """.trimIndent()
            ),
        )
    }

    private fun immutableConverterFixture(): FrontendFixture {
        return FrontendFixture(
            aptSources = mapOf(
                "demo/ConvertedBook.java" to
                    """
                        package demo;

                        import org.babyfish.jimmer.Immutable;
                        import org.babyfish.jimmer.jackson.JsonConverter;

                        @Immutable
                        public interface ConvertedBook {
                            @JsonConverter(ExternalPojoConverter.class)
                            String payload();
                        }
                    """.trimIndent(),
                "demo/ExternalPojoConverter.java" to
                    """
                        package demo;

                        import external.ExternalPojo;
                        import org.babyfish.jimmer.jackson.Converter;

                        public class ExternalPojoConverter implements Converter<String, ExternalPojo> {
                            @Override
                            public ExternalPojo output(String value) {
                                return new ExternalPojo();
                            }
                        }
                    """.trimIndent(),
                "demo/ConverterService.java" to
                    """
                        package demo;

                        import org.babyfish.jimmer.client.meta.Api;

                        @Api
                        public interface ConverterService {
                            @Api
                            ConvertedBook find();
                        }
                    """.trimIndent(),
            ),
            kspSources = mapOf(
                "demo/ConverterService.kt" to
                    """
                        package demo

                        import external.ExternalPojo
                        import org.babyfish.jimmer.Immutable
                        import org.babyfish.jimmer.client.meta.Api
                        import org.babyfish.jimmer.jackson.Converter
                        import org.babyfish.jimmer.jackson.JsonConverter

                        @Immutable
                        interface ConvertedBook {
                            @JsonConverter(ExternalPojoConverter::class)
                            val payload: String
                        }

                        class ExternalPojoConverter : Converter<String, ExternalPojo> {
                            override fun output(value: String): ExternalPojo = ExternalPojo()
                        }

                        @Api
                        interface ConverterService {
                            @Api
                            fun find(): ConvertedBook
                        }
                    """.trimIndent()
            ),
        )
    }

    private fun jsonValueFixture(): FrontendFixture {
        return FrontendFixture(
            aptSources = mapOf(
                "demo/JsonValueService.java" to
                    """
                        package demo;

                        import external.JsonValueEnvelope;
                        import org.babyfish.jimmer.client.meta.Api;

                        @Api
                        public interface JsonValueService {
                            @Api
                            JsonValueEnvelope load();
                        }
                    """.trimIndent()
            ),
            kspSources = mapOf(
                "demo/JsonValueService.kt" to
                    """
                        package demo

                        import external.JsonValueEnvelope
                        import org.babyfish.jimmer.client.meta.Api

                        @Api
                        interface JsonValueService {
                            @Api
                            fun load(): JsonValueEnvelope
                        }
                    """.trimIndent()
            ),
        )
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

    private data class FrontendFixture(
        val aptSources: Map<String, String>,
        val kspSources: Map<String, String>,
    )

    private companion object {
        const val CLIENT_RESOURCE_PATH = "META-INF/jimmer/client"

        val VALIDATION_NOT_NULL_SOURCE = """
            package jakarta.validation.constraints;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({
                ElementType.METHOD,
                ElementType.FIELD,
                ElementType.ANNOTATION_TYPE,
                ElementType.CONSTRUCTOR,
                ElementType.PARAMETER,
                ElementType.TYPE_USE
            })
            public @interface NotNull {}
        """.trimIndent()
    }
}
