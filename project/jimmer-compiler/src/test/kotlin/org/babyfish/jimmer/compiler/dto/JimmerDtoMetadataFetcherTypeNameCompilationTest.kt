package org.babyfish.jimmer.compiler.dto

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class JimmerDtoMetadataFetcherTypeNameCompilationTest {

    @Test
    fun `apt compiles metadata fetcher with allocated nested target name`() {
        val source = compileApt()

        assertEquals(APT_METADATA_FETCHER_IMPORTS, source.aptMetadataFetcherImports())
        assertEquals(
            APT_METADATA_INITIALIZER,
            source.requiredBlock(
                startMarker = "    DtoMetadata<Client, ClientView> METADATA = ",
                endMarker = "\n\n        long getId();",
            ),
        )
    }

    @Test
    fun `ksp compiles metadata fetcher with allocated nested target name`() {
        val source = compileKsp()

        assertEquals(KSP_METADATA_FETCHER_IMPORTS, source.kspMetadataFetcherImports())
        assertEquals(
            KSP_METADATA_INITIALIZER,
            source.requiredBlock(
                startMarker = "        @JvmField\n        public val METADATA: DtoMetadata<Client, ClientView> = ",
                endMarker = "\n    }\n\n    @GeneratedPolymorphicDtoBranch(",
            ),
        )
    }

    private fun compileApt(): String {
        val projectDir = fixtureProject("jimmer-dto-metadata-fetcher-apt")
        val sourceFiles = writeSources(projectDir, "src/main/java/demo", JAVA_SOURCES)
        writeDtoSource(projectDir)
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("DTO APT tests require a JDK compiler")
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
                    runtimeClasspathText(),
                    "-Ajimmer.jackson3=false",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertEquals(true, task.call(), diagnostics.toErrorMessage())
        }
        val generatedSources = generatedDir.generatedSources("java")
        val compileDiagnostics = DiagnosticCollector<JavaFileObject>()
        compiler.getStandardFileManager(compileDiagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                compileDiagnostics,
                listOf(
                    "-proc:none",
                    "-classpath",
                    runtimeClasspathText(),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles + generatedSources),
            )
            assertEquals(true, task.call(), compileDiagnostics.toErrorMessage())
        }
        return generatedDir.resolve("demo/dto/ClientView.java").readText()
    }

    private fun compileKsp(): String {
        val projectDir = fixtureProject("jimmer-dto-metadata-fetcher-ksp")
        val sourceFiles = writeSources(projectDir, "src/main/kotlin/demo", KOTLIN_SOURCES)
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-metadata-fetcher"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            processorOptions = mapOf(
                "jimmer.dto.mutable" to "true",
                "jimmer.jackson3" to "false",
            )
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
        compileWithK2(
            projectDir = projectDir,
            sourceFiles = sourceFiles + kotlinOutputDir.generatedSources("kt"),
        )
        return kotlinOutputDir.resolve("demo/dto/ClientView.kt").readText()
    }

    private fun compileWithK2(
        projectDir: File,
        sourceFiles: List<File>,
    ) {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val arguments = buildList {
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("17")
            add("-classpath")
            add(runtimeClasspathText())
            add("-d")
            add(classesDir.absolutePath)
            sourceFiles.mapTo(this) { file -> file.absolutePath }
        }
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(stream, *arguments.toTypedArray())
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun writeSources(
        projectDir: File,
        relativeDir: String,
        sources: Map<String, String>,
    ): List<File> {
        val sourceDir = projectDir.resolve(relativeDir)
        return sources.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/Client.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(DTO_SOURCE)
        }
    }

    private fun runtimeClasspath(): List<File> {
        return runtimeClasspathText()
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
    }

    private fun runtimeClasspathText(): String = System.getProperty("java.class.path")

    private fun File.generatedSources(extension: String): List<File> {
        return walkTopDown()
            .filter { file -> file.isFile && file.extension == extension }
            .toList()
    }

    private fun String.aptMetadataFetcherImports(): String = lineSequence()
        .filter { line -> line.endsWith("Fetcher;") }
        .joinToString("\n")

    private fun String.kspMetadataFetcherImports(): String = lineSequence()
        .filter { line -> line.endsWith(".`by`") || line.endsWith(".newFetcher") }
        .joinToString("\n")

    private fun String.requiredBlock(
        startMarker: String,
        endMarker: String,
    ): String {
        val startIndex = indexOf(startMarker)
        require(startIndex >= 0) {
            "Generated source does not contain start marker: $startMarker\n$this"
        }
        val endIndex = indexOf(endMarker, startIndex + startMarker.length)
        require(endIndex >= 0) {
            "Generated source does not contain end marker after start marker: $endMarker\n$this"
        }
        return substring(startIndex, endIndex)
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
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
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private companion object {
        val APT_METADATA_FETCHER_IMPORTS = """
            import demo.ClientFetcher;
            import demo.DepartmentFetcher;
            import demo.OrganizationFetcher;
        """.trimIndent()

        val APT_METADATA_INITIALIZER = """
            DtoMetadata<Client, ClientView> METADATA =${' '}
                new DtoMetadata<Client, ClientView>(
                    ClientView.class,
                    ClientFetcher.${'$'}
                        .forType(OrganizationFetcher.${'$'}
                            .department(TargetOf_department.TargetOf_department_2.METADATA.getFetcher())
                        ),
                        base -> {
                            Class<?> actualType = ((ImmutableSpi)base).__type().getJavaClass();
                            if (actualType == Organization.class) {
                                return new TargetOf_department((Organization)base);
                            }
                            return new Default(base);
                        }
                );
        """.trimIndent().prependIndent("    ")

        val KSP_METADATA_FETCHER_IMPORTS = """
            import demo.`by`
            import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
        """.trimIndent()

        val KSP_METADATA_INITIALIZER = """
            @JvmField
            public val METADATA: DtoMetadata<Client, ClientView> =${' '}
                DtoMetadata<Client, ClientView>(
                    ClientView::class.java,
                    newFetcher(Client::class).by {
                        forType(Organization::class) {
                            department(TargetOf_department.TargetOf_department_2.METADATA.fetcher)
                        }
                    },
                    { base ->
                        val actualType = (base as ImmutableSpi).__type().javaClass
                        when (actualType) {
                            Organization::class.java -> TargetOf_department(base as Organization)
                            else -> Default(base)
                        }
                    }
                )
        """.trimIndent().prependIndent("        ")

        val JAVA_SOURCES = linkedMapOf(
            "Department.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;

                @Entity
                public interface Department {
                    @Id
                    long id();

                    String name();
                }
            """.trimIndent(),
            "Client.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                public interface Client {
                    @Id
                    long id();

                    @Discriminator
                    String type();
                }
            """.trimIndent(),
            "Organization.java" to """
                package demo;

                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.ManyToOne;

                @Entity
                @DiscriminatorValue("ORG")
                public interface Organization extends Client {
                    @ManyToOne
                    Department department();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "Department.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Department {
                    @Id
                    val id: Long

                    val name: String
                }
            """.trimIndent(),
            "Client.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface Client {
                    @Id
                    val id: Long

                    @Discriminator
                    val type: String
                }
            """.trimIndent(),
            "Organization.kt" to """
                package demo

                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.ManyToOne

                @Entity
                @DiscriminatorValue("ORG")
                interface Organization : Client {
                    @ManyToOne
                    val department: Department
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            package demo.dto

            ClientView {
                id
                #types {
                    Organization class TargetOf_department {
                        department {
                            id
                            name
                        }
                    }
                }
            }
        """.trimIndent()
    }
}
