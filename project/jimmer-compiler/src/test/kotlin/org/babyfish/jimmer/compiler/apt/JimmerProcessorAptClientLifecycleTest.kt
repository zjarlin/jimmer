package org.babyfish.jimmer.compiler.apt

import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class JimmerProcessorAptClientLifecycleTest {

    @Test
    fun `client resource includes an api generated after the first jimmer round`() {
        val projectDir = createTempDirectory(prefix = "jimmer-apt-client-lifecycle").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply { mkdirs() }
        val generatedDir = projectDir.resolve("build/generated").apply { mkdirs() }
        val anchorFile = sourceDir.resolve("demo/Anchor.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(ANCHOR_SOURCE)
        }
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
                fileManager.getJavaFileObjects(anchorFile),
            )
            task.setProcessors(listOf(JimmerProcessor(), ApiGeneratingProcessor()))
            task.call()
        }

        assertTrue(success, diagnostics.errorMessage())
        assertTrue(
            diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            diagnostics.errorMessage(),
        )
        val clientResource = classesDir.resolve("META-INF/jimmer/client")
        assertTrue(clientResource.isFile, "Missing client resource: ${clientResource.absolutePath}")
        assertTrue("demo.BookService" in clientResource.readText(), clientResource.readText())
    }

    private class ApiGeneratingProcessor : AbstractProcessor() {
        private var generated = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("demo.GenerateApi")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (generated || roundEnvironment.processingOver()) {
                return false
            }
            processingEnv.filer.createSourceFile("demo.BookService").openWriter().use { writer ->
                writer.write(API_SOURCE)
            }
            generated = true
            return false
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
        val ANCHOR_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.EnableDtoGeneration;
            import org.babyfish.jimmer.sql.Discriminator;
            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.Inheritance;
            import org.babyfish.jimmer.sql.InheritanceType;

            @interface GenerateApi {}

            @Entity
            @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
            interface Root {
                @Id
                long id();

                @Discriminator
                String type();
            }

            @Entity
            @DiscriminatorValue("SPECIAL")
            interface SpecialRoot extends Root {}

            @EnableDtoGeneration
            @GenerateApi
            public class Anchor {}
        """.trimIndent()

        val API_SOURCE = """
            package demo;

            import org.babyfish.jimmer.client.meta.Api;

            @Api
            public interface BookService {
                @Api
                String findName();
            }
        """.trimIndent()
    }
}
