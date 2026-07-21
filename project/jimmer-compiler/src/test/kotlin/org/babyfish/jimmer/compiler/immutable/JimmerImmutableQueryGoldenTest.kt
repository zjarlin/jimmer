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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider

class JimmerImmutableQueryGoldenTest {

    @Test
    fun `apt entity query sources match migration golden`() {
        assertGoldens("apt", compileApt())
    }

    @Test
    fun `ksp entity query source matches migration golden`() {
        assertGoldens("ksp", compileKsp())
    }

    private fun compileApt(): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-query-apt-golden").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Book.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(JAVA_SOURCE)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run immutable query APT golden tests")
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
            "BookProps.java",
            "BookTable.java",
            "BookTableEx.java",
        ).associateWith { name ->
            val generatedFile = generatedDir.resolve("demo/$name")
            assertTrue(generatedFile.isFile, "APT query output is missing: ${generatedFile.absolutePath}")
            generatedFile.readBytes()
        }
    }

    private fun compileKsp(): Map<String, ByteArray> {
        val projectDir = createTempDirectory(prefix = "jimmer-query-ksp-golden").toFile()
        val sourceDir = projectDir.resolve("src/main/kotlin/demo")
        val sourceFiles = KOTLIN_SOURCES.map { (name, content) ->
            sourceDir.resolve(name).also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-query-ksp-golden"
            sourceRoots = sourceFiles
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
        assertTrue(propsFile.isFile, "KSP query output is missing: ${propsFile.absolutePath}")
        return mapOf("SourceProps.kt" to propsFile.readBytes())
    }

    private fun assertGoldens(platform: String, actualFiles: Map<String, ByteArray>) {
        val missing = mutableListOf<String>()
        for ((name, actual) in actualFiles) {
            val resourcePath = "/immutable/query/$platform/$name"
            val expected = javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            if (expected == null) {
                val outputFile = File(
                    System.getProperty("java.io.tmpdir"),
                    "jimmer-immutable-query-golden/$platform/$name",
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

            import java.util.List;
            import org.babyfish.jimmer.sql.Discriminator;
            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Embeddable;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.Inheritance;
            import org.babyfish.jimmer.sql.InheritanceType;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.OneToMany;
            import org.jspecify.annotations.Nullable;

            @MappedSuperclass
            interface BaseNode<T extends BaseNode<T>> {
                @ManyToOne
                @Nullable
                T parent();

                @IdView("parent")
                @Nullable
                Long parentId();

                @OneToMany(mappedBy = "parent")
                List<T> children();
            }

            @Embeddable
            interface Location {
                String city();

                @Nullable
                Integer zipCode();
            }

            @Entity
            interface Store {
                @Id
                long id();

                String name();
            }

            @Entity
            @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
            public interface Book extends BaseNode<Book> {
                @Id
                long id();

                @Discriminator
                String kind();

                String name();

                @ManyToOne
                @Nullable
                Store store();

                @IdView("store")
                @Nullable
                Long storeId();

                Location location();
            }

            @Entity
            @DiscriminatorValue("SPECIAL")
            interface SpecialBook extends Book {
                String specialCode();
            }
        """.trimIndent()

        val KOTLIN_SOURCES = linkedMapOf(
            "BaseNode.kt" to """
                package demo

                import org.babyfish.jimmer.sql.IdView
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.MappedSuperclass
                import org.babyfish.jimmer.sql.OneToMany

                @MappedSuperclass
                interface BaseNode<T : BaseNode<T>> {
                    @ManyToOne
                    val parent: T?

                    @IdView("parent")
                    val parentId: Long?

                    @OneToMany(mappedBy = "parent")
                    val children: List<T>
                }
            """.trimIndent(),
            "Location.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                @Embeddable
                interface Location {
                    val city: String

                    val zipCode: Int?
                }
            """.trimIndent(),
            "Store.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id

                @Entity
                interface Store {
                    @Id
                    val id: Long

                    val name: String
                }
            """.trimIndent(),
            "Source.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.IdView
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType
                import org.babyfish.jimmer.sql.ManyToOne

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface Book : BaseNode<Book> {
                    @Id
                    val id: Long

                    @Discriminator
                    val kind: String

                    val name: String

                    @ManyToOne
                    val store: Store?

                    @IdView("store")
                    val storeId: Long?

                    val location: Location
                }
            """.trimIndent(),
            "SpecialBook.kt" to """
                package demo

                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity

                @Entity
                @DiscriminatorValue("SPECIAL")
                interface SpecialBook : Book {
                    val specialCode: String
                }
            """.trimIndent(),
        )

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }
    }
}
