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
        val summaryBody = source.classBody("class TargetOf_summary implements Input<Client>")

        assertContains(source, "@Description(\"Client base documentation costs \$5 at 100%.\\n\")")
        assertContains(source, "@Description(\"Address reference documentation is 100%.\\n\")")
        assertContains(source, "@Description(\"Address embeddable documentation costs \$5 at 100%.\\n\")")
        source.assertDescriptionNear(
            declaration = "String getLocationCity();",
            annotation = "@Description(\"City documentation.\\n\")",
        )
        source.assertRepeatedTypeAnnotations()
        assertEquals(1, source.countOccurrences("@JsonTypeInfo("))
        assertEquals(1, source.countOccurrences("@JsonSubTypes("))
        assertContains(source, "property = \"type\"")
        assertEquals(4, source.countOccurrences("@JsonDeserialize("))
        assertEquals(4, source.countOccurrences("public static class Builder"))
        assertEquals(1, source.countOccurrences("class TargetOf_address implements EmbeddableDto<Address>"))
        assertContains(
            source,
            "public interface ClientInput extends Input<Client>, HibernateValidatorEnhancedBean",
        )
        assertEquals(1, source.countOccurrences("interface ClientInput extends Input<Client>"))
        assertEquals(1, source.countOccurrences("class TargetOf_summary implements Input<Client>"))
        assertContains(source, "TargetOf_address getAddress();")
        assertContains(source, "TargetOf_summary getSummary();")
        assertContains(addressBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(addressBody, "\$\$_hibernateValidator_getGetterValue")
        assertContains(addressBody, "case \"city\": return this.city;")
        assertContains(summaryBody, "String getName()")
        assertFalse("class TargetOf_address" in personBody)
        assertFalse("class TargetOf_summary" in personBody)
        assertContains(personBody, "private TargetOf_address address;")
        assertContains(personBody, "TargetOf_summary")
        assertContains(personBody, "Builder address(TargetOf_address address)")
        assertContains(personBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(personBody, "\$\$_hibernateValidator_getGetterValue")
        assertContains(personBody, "case \"address\": return this.address;")
        personBody.assertDescriptionNear(
            declaration = "String getLocationCity()",
            annotation = "@Description(\"City documentation.\\n\")",
        )

        assertFalse("class TargetOf_address" in organizationBody)
        assertFalse("class TargetOf_summary" in organizationBody)
        assertContains(organizationBody, "private TargetOf_address address;")
        assertContains(organizationBody, "TargetOf_summary")
        assertContains(organizationBody, "Builder address(TargetOf_address address)")
        assertContains(organizationBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(organizationBody, "\$\$_hibernateValidator_getGetterValue")
    }

    @Test
    fun `ksp shares promoted inline input builder type between polymorphic branches`() {
        val source = compileKsp()
        val personBody = source.classBody("public class Person(")
        val personHeader = source.classHeader("public class Person(")
        val organizationBody = source.classBody("public class Organization(")
        val addressBody = source.classBody("public open class TargetOf_address(")
        val summaryHeader = source.classHeader("public open class TargetOf_summary(")

        assertContains(
            source,
            "@Description(value = \"Client base documentation costs ${KOTLIN_ESCAPED_DOLLAR}5 at 100%.\\n\")",
        )
        assertContains(
            source,
            "@Description(value = \"Address reference documentation is 100%.\\n\")",
        )
        assertContains(
            source,
            "@Description(value = \"Address embeddable documentation costs ${KOTLIN_ESCAPED_DOLLAR}5 at 100%.\\n\")",
        )
        source.assertDescriptionNear(
            declaration = "public val locationCity: String",
            annotation = "@Description(value = \"City documentation.\\n\")",
        )
        personHeader.assertDescriptionNear(
            declaration = "override var locationCity: String",
            annotation = "@Description(value = \"City documentation.\\n\")",
        )
        source.assertRepeatedTypeAnnotations()
        assertEquals(1, source.countOccurrences("@JsonTypeInfo("))
        assertEquals(1, source.countOccurrences("@JsonSubTypes("))
        assertContains(source, "property = \"type\"")
        assertEquals(4, source.countOccurrences("@JsonDeserialize("))
        assertEquals(4, source.countOccurrences("public class Builder"))
        assertEquals(1, source.countOccurrences("public open class TargetOf_address("))
        assertContains(
            source,
            "public interface ClientInput : Input<Client>, HibernateValidatorEnhancedBean",
        )
        assertEquals(1, source.countOccurrences("interface ClientInput : Input<Client>"))
        assertEquals(1, source.countOccurrences("public open class TargetOf_summary("))
        assertContains(source, "public val address: TargetOf_address")
        assertContains(source, "public val summary: TargetOf_summary")
        assertContains(addressBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(addressBody, "\$\$_hibernateValidator_getGetterValue")
        assertContains(addressBody, "\"city\" -> this.city")
        assertContains(summaryHeader, "public var name: String")
        assertFalse("class TargetOf_address" in personBody)
        assertFalse("class TargetOf_summary" in personBody)
        assertContains(personBody, "private var address: TargetOf_address? = null")
        assertContains(personBody, "TargetOf_summary")
        assertContains(personBody, "fun address(address: TargetOf_address): Builder")
        assertContains(personBody, "\$\$_hibernateValidator_getFieldValue")
        assertContains(personBody, "\$\$_hibernateValidator_getGetterValue")
        assertContains(personBody, "\"address\" -> this.address")

        assertFalse("class TargetOf_address" in organizationBody)
        assertFalse("class TargetOf_summary" in organizationBody)
        assertContains(organizationBody, "private var address: TargetOf_address? = null")
        assertContains(organizationBody, "TargetOf_summary")
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
        val generatedSources = generatedDir
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .toList()
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
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles + generatedSources),
            )
            assertEquals(true, task.call(), compileDiagnostics.toErrorMessage())
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

    private fun String.classHeader(declaration: String): String {
        val declarationStart = indexOf(declaration)
        check(declarationStart >= 0) { "Missing generated declaration: $declaration" }
        val bodyStart = indexOf('{', declarationStart)
        check(bodyStart >= 0) { "Missing generated body: $declaration" }
        return substring(declarationStart, bodyStart)
    }

    private fun String.countOccurrences(value: String): Int = split(value).size - 1

    private fun String.assertDescriptionNear(declaration: String, annotation: String) {
        val declarationOffset = indexOf(declaration)
        assertTrue(declarationOffset >= 0, "Missing generated declaration: $declaration")
        val annotationWindow = substring((declarationOffset - 400).coerceAtLeast(0), declarationOffset)
        assertContains(annotationWindow, annotation)
    }

    private fun String.assertRepeatedTypeAnnotations() {
        val interfaceStart = firstIndexOf(
            "public interface ClientInput extends",
            "public interface ClientInput :",
        )
        assertTrue(interfaceStart >= 0)
        val annotationSection = substring(0, interfaceStart)
        assertEquals(2, annotationSection.countOccurrences("@Marker("))
        assertFalse("\"base\"" in annotationSection)
        val firstOrder = annotationSection.indexOf("order = 1")
        val firstValue = annotationSection.firstIndexOf("value = \"first\"", "`value` = \"first\"")
        val secondOrder = annotationSection.indexOf("order = 2")
        val secondValue = annotationSection.firstIndexOf("value = \"second\"", "`value` = \"second\"")
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
        val KOTLIN_ESCAPED_DOLLAR = "\$" + "{'\$'}"

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
                 * Address embeddable documentation costs ${'$'}5 at 100%.
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
                 * Client base documentation costs ${'$'}5 at 100%.
                 */
                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                @Marker(value = "base", order = 0)
                public interface Client {
                    @Id
                    long id();

                    @Discriminator
                    String type();

                    /** Address reference documentation is 100%. */
                    Address address();

                    Address location();

                    String name();
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
                 * Address embeddable documentation costs ${'$'}5 at 100%.
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
                 * Client base documentation costs ${'$'}5 at 100%.
                 */
                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                @Marker(value = "base", order = 0)
                interface Client {
                    @Id
                    val id: Long

                    @Discriminator
                    val type: String

                    /** Address reference documentation is 100%. */
                    val address: Address

                    val location: Address

                    val name: String
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
                fold(summary) {
                    name
                }
                flat(location) {
                    city as locationCity
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
