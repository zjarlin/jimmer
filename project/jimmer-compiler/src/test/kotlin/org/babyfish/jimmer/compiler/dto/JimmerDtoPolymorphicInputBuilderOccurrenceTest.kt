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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class JimmerDtoPolymorphicInputBuilderOccurrenceTest {

    @Test
    fun `apt shares promoted inline input builder type between polymorphic branches`() {
        val source = compileApt()
        val personBody = source.classBody("final class Person implements ClientInput")
        val organizationBody = source.classBody("final class Organization implements ClientInput")
        val addressBody = source.classBody("class TargetOf_address implements EmbeddableDto<Address>")

        assertContains(source, "@Description(\"Client base documentation.\\n\")")
        assertContains(source, "@Description(\"Address reference documentation.\\n\")")
        assertContains(source, "@Description(\"Address embeddable documentation.\\n\")")
        source.assertRepeatedTypeAnnotations()
        assertEquals(1, source.countOccurrences("@JsonTypeInfo("))
        assertEquals(1, source.countOccurrences("@JsonSubTypes("))
        assertContains(source, "property = \"type\"")
        assertEquals(3, source.countOccurrences("@JsonDeserialize("))
        assertEquals(3, source.countOccurrences("public static class Builder"))
        assertEquals(1, source.countOccurrences("class TargetOf_address implements EmbeddableDto<Address>"))
        assertContains(
            source,
            "public interface ClientInput extends Input<Client>, HibernateValidatorEnhancedBean",
        )
        assertEquals(1, source.countOccurrences("Input<Client>"))
        assertContains(source, "TargetOf_address getAddress();")
        assertContains(addressBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(addressBody, "\$\$_hibernateValidator_getGetterValue")
        assertFalse("class TargetOf_address" in personBody)
        assertContains(personBody, "private TargetOf_address address;")
        assertContains(personBody, "Builder address(TargetOf_address address)")
        assertContains(personBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(personBody, "\$\$_hibernateValidator_getGetterValue")

        assertFalse("class TargetOf_address" in organizationBody)
        assertContains(organizationBody, "private TargetOf_address address;")
        assertContains(organizationBody, "Builder address(TargetOf_address address)")
        assertContains(organizationBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(organizationBody, "\$\$_hibernateValidator_getGetterValue")
    }

    @Test
    fun `ksp shares promoted inline input builder type between polymorphic branches`() {
        val source = compileKsp()
        val personBody = source.classBody("public class Person(")
        val organizationBody = source.classBody("public class Organization(")
        val addressBody = source.classBody("public open class TargetOf_address(")

        assertContains(source, "@Description(value = \"Client base documentation.\\n\")")
        assertContains(source, "@Description(value = \"Address reference documentation.\\n\")")
        assertContains(source, "@Description(value = \"Address embeddable documentation.\\n\")")
        source.assertRepeatedTypeAnnotations()
        assertEquals(1, source.countOccurrences("@JsonTypeInfo("))
        assertEquals(1, source.countOccurrences("@JsonSubTypes("))
        assertContains(source, "property = \"type\"")
        assertEquals(3, source.countOccurrences("@JsonDeserialize("))
        assertEquals(3, source.countOccurrences("public class Builder"))
        assertEquals(1, source.countOccurrences("public open class TargetOf_address("))
        assertContains(
            source,
            "public interface ClientInput : Input<Client>, HibernateValidatorEnhancedBean",
        )
        assertEquals(1, source.countOccurrences("Input<Client>"))
        assertContains(source, "public val address: TargetOf_address")
        assertContains(addressBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(addressBody, "\$\$_hibernateValidator_getGetterValue")
        assertFalse("class TargetOf_address" in personBody)
        assertContains(personBody, "private var address: TargetOf_address? = null")
        assertContains(personBody, "fun address(address: TargetOf_address): Builder")
        assertContains(personBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(personBody, "\$\$_hibernateValidator_getGetterValue")

        assertFalse("class TargetOf_address" in organizationBody)
        assertContains(organizationBody, "private var address: TargetOf_address? = null")
        assertContains(organizationBody, "fun address(address: TargetOf_address): Builder")
        assertContains(organizationBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(organizationBody, "\$\$_hibernateValidator_getGetterValue")
    }

    private fun compileApt(): String {
        val projectDir = fixtureProject("jimmer-dto-polymorphic-builder-apt")
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
                    System.getProperty("java.class.path"),
                    "-Ajimmer.dto.hibernateValidatorEnhancement=true",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            assertEquals(true, task.call(), diagnostics.toErrorMessage())
        }
        return generatedDir.resolve("demo/dto/ClientInput.java").readText()
    }

    private fun compileKsp(): String {
        val projectDir = fixtureProject("jimmer-dto-polymorphic-builder-ksp")
        val sourceFiles = writeSources(projectDir, "src/main/kotlin/demo", KOTLIN_SOURCES)
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-polymorphic-builder"
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
                "jimmer.dto.hibernateValidatorEnhancement" to "true",
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
            sourceFiles = sourceFiles + kotlinOutputDir
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .toList(),
        )
        return kotlinOutputDir.resolve("demo/dto/ClientInput.kt").readText()
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
            add(System.getProperty("java.class.path"))
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
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
    }

    private fun String.classBody(declaration: String): String {
        val declarationStart = indexOf(declaration)
        check(declarationStart >= 0) { "Missing generated declaration: $declaration" }
        val bodyStart = indexOf('{', declarationStart)
        check(bodyStart >= 0) { "Missing generated body: $declaration" }
        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return substring(bodyStart + 1, index)
                    }
                }
            }
        }
        error("Unclosed generated body: $declaration")
    }

    private fun String.countOccurrences(value: String): Int = split(value).size - 1

    private fun String.assertRepeatedTypeAnnotations() {
        assertEquals(2, countOccurrences("@Marker("))
        assertFalse("\"base\"" in this)
        val firstOrder = indexOf("order = 1")
        val firstValue = firstIndexOf("value = \"first\"", "`value` = \"first\"")
        val secondOrder = indexOf("order = 2")
        val secondValue = firstIndexOf("value = \"second\"", "`value` = \"second\"")
        assertTrue(firstOrder >= 0)
        assertTrue(firstOrder < firstValue)
        assertTrue(firstValue < secondOrder)
        assertTrue(secondOrder < secondValue)
    }

    private fun String.firstIndexOf(vararg candidates: String): Int {
        return candidates.map(::indexOf).filter { index -> index >= 0 }.minOrNull() ?: -1
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
        val JAVA_SOURCES = linkedMapOf(
            "Marker.java" to """
                package demo;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Repeatable;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @Repeatable(Markers.class)
                public @interface Marker {
                    String value();
                    int order();
                }
            """.trimIndent(),
            "Markers.java" to """
                package demo;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Markers {
                    Marker[] value();
                }
            """.trimIndent(),
            "Address.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Embeddable;

                /**
                 * Address embeddable documentation.
                 */
                @Embeddable
                public interface Address {
                    /** City documentation. */
                    String city();
                }
            """.trimIndent(),
            "Client.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;

                /**
                 * Client base documentation.
                 */
                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                @Marker(value = "base", order = 0)
                public interface Client {
                    @Id
                    long id();

                    @Discriminator
                    String type();

                    /** Address reference documentation. */
                    Address address();
                }
            """.trimIndent(),
            "Person.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;

                @Entity
                public interface Person extends Client {
                    String firstName();
                }
            """.trimIndent(),
            "Organization.java" to """
                package demo;

                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;

                @Entity
                @DiscriminatorValue("ORG")
                public interface Organization extends Client {
                    String taxCode();
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = linkedMapOf(
            "Marker.kt" to """
                package demo

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                @Repeatable
                annotation class Marker(
                    val value: String,
                    val order: Int,
                )
            """.trimIndent(),
            "Address.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Embeddable

                /**
                 * Address embeddable documentation.
                 */
                @Embeddable
                interface Address {
                    /** City documentation. */
                    val city: String
                }
            """.trimIndent(),
            "Client.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Discriminator
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.Id
                import org.babyfish.jimmer.sql.Inheritance
                import org.babyfish.jimmer.sql.InheritanceType

                /**
                 * Client base documentation.
                 */
                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                @Marker(value = "base", order = 0)
                interface Client {
                    @Id
                    val id: Long

                    @Discriminator
                    val type: String

                    /** Address reference documentation. */
                    val address: Address
                }
            """.trimIndent(),
            "Person.kt" to """
                package demo

                import org.babyfish.jimmer.sql.Entity

                @Entity
                interface Person : Client {
                    val firstName: String
                }
            """.trimIndent(),
            "Organization.kt" to """
                package demo

                import org.babyfish.jimmer.sql.DiscriminatorValue
                import org.babyfish.jimmer.sql.Entity

                @Entity
                @DiscriminatorValue("ORG")
                interface Organization : Client {
                    val taxCode: String
                }
            """.trimIndent(),
        )

        val DTO_SOURCE = """
            package demo.dto

            import demo.Marker

            @Marker(order = 1, value = "first")
            @Marker(order = 2, value = "second")
            dynamic input ClientInput {
                id?
                address {
                    dynamic city?
                }
                #types {
                    #exhaustive
                    Person {
                        firstName
                    }
                    Organization {
                        taxCode
                    }
                }
            }
        """.trimIndent()
    }
}
