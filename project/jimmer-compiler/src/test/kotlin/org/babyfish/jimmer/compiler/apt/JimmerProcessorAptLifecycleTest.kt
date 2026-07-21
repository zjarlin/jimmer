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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JimmerProcessorAptLifecycleTest {

    @Test
    fun `main processor generates immutable artifacts for an entity created after the first round`() {
        val result = compile()

        assertTrue(result.success, result.diagnostics.errorMessage())
        assertTrue(
            result.diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            result.diagnostics.errorMessage(),
        )
        listOf(
            "demo/BookDraft.java",
            "demo/BookProps.java",
            "demo/BookTable.java",
            "demo/BookTableEx.java",
            "demo/BookFetcher.java",
        ).forEach { path ->
            assertTrue(result.generatedDir.resolve(path).isFile, "Missing generated source: $path")
        }
    }

    @Test
    fun `main processor waits for the next real round before generating dto source`() {
        val result = compile(DTO_SOURCE)

        assertTrue(result.success, result.diagnostics.errorMessage())
        assertTrue(
            result.roundCapture.roundOf("demo.BookDraft") < result.roundCapture.roundOf("demo.dto.BookView"),
            result.roundCapture.toString(),
        )
    }

    @Test
    fun `main processor generates embeddable query artifacts once after the first round`() {
        val result = compile(
            generatedTypeName = "demo.Location",
            generatedSource = LOCATION_SOURCE,
        )

        assertTrue(result.success, result.diagnostics.errorMessage())
        assertTrue(
            result.diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            result.diagnostics.errorMessage(),
        )
        assertTrue(
            result.roundCapture.roundOf("demo.Anchor") < result.roundCapture.roundOf("demo.Location"),
            result.roundCapture.toString(),
        )
        listOf(
            "LocationProps.java",
            "LocationPropExpression.java",
        ).forEach { name ->
            assertTrue(result.generatedDir.resolve("demo/$name").isFile, "Missing generated source: demo/$name")
            assertEquals(1, result.generatedSourcesNamed(name).size, "Generated source must be unique: $name")
        }
    }

    private fun compile(
        dtoSource: String? = null,
        generatedTypeName: String = "demo.Book",
        generatedSource: String = BOOK_SOURCE,
    ): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-compiler-lifecycle").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply { mkdirs() }
        val generatedDir = projectDir.resolve("build/generated").apply { mkdirs() }
        val anchorFile = sourceDir.resolve("demo/Anchor.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(ANCHOR_SOURCE)
        }
        dtoSource?.let { content ->
            projectDir.resolve("src/main/dto/demo/Book.dto").also { file ->
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val roundCapture = RoundCapture()
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
            task.setProcessors(
                listOf(
                    SourceGeneratingProcessor(roundCapture, generatedTypeName, generatedSource),
                    JimmerProcessor(),
                ),
            )
            task.call()
        }
        return AptCompilationResult(success, diagnostics, generatedDir, roundCapture)
    }

    private class SourceGeneratingProcessor(
        private val roundCapture: RoundCapture,
        private val generatedTypeName: String,
        private val generatedSource: String,
    ) : AbstractProcessor() {
        private var generated = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            roundCapture.record(roundEnvironment)
            if (generated || roundEnvironment.processingOver()) {
                return false
            }
            processingEnv.filer.createSourceFile(generatedTypeName).openWriter().use { writer ->
                writer.write(generatedSource)
            }
            generated = true
            return false
        }
    }

    private class RoundCapture {
        private val rootTypeNamesByRound = mutableListOf<Set<String>>()

        fun record(roundEnvironment: RoundEnvironment) {
            rootTypeNamesByRound += roundEnvironment.rootElements
                .filterIsInstance<TypeElement>()
                .mapTo(sortedSetOf()) { type -> type.qualifiedName.toString() }
        }

        fun roundOf(qualifiedName: String): Int {
            return rootTypeNamesByRound.indexOfFirst { rootTypeNames -> qualifiedName in rootTypeNames }
                .takeIf { round -> round >= 0 }
                ?: error("Type '$qualifiedName' did not become a round root: $this")
        }

        override fun toString(): String = rootTypeNamesByRound.withIndex().joinToString { (round, roots) ->
            "$round=${roots.joinToString(prefix = "[", postfix = "]")}"
        }
    }

    private data class AptCompilationResult(
        val success: Boolean,
        val diagnostics: DiagnosticCollector<JavaFileObject>,
        val generatedDir: java.io.File,
        val roundCapture: RoundCapture,
    ) {
        fun generatedSourcesNamed(name: String): List<java.io.File> {
            return generatedDir.walkTopDown()
                .filter { file -> file.isFile && file.name == name }
                .toList()
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

            @EnableDtoGeneration
            public class Anchor {}
        """.trimIndent()

        val BOOK_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Entity
            public interface Book {
                @Id
                long id();

                String name();
            }
        """.trimIndent()

        val LOCATION_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Embeddable;
            import org.jspecify.annotations.Nullable;

            @Embeddable
            public interface Location {
                String city();

                @Nullable
                Integer zipCode();
            }
        """.trimIndent()

        val DTO_SOURCE = """
            BookView {
                id
                name
            }
        """.trimIndent()
    }
}
