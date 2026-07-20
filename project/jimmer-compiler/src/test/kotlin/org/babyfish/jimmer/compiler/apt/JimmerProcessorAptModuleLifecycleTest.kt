package org.babyfish.jimmer.compiler.apt

import java.nio.charset.StandardCharsets
import java.lang.reflect.Proxy
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Completion
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 让模块聚合契约独立于大型 Client fixture。
 *
 * 探针刻意作为第二个处理器运行，使断言描述 JimmerProcessor 实际观察到的 javac 轮次，
 * 而不是人工构造的 CompilerSession。
 */
class JimmerProcessorAptModuleLifecycleTest {

    @Test
    fun `apt aggregates Fetchers after immutable generated sources become visible`() {
        val projectDir = createTempDirectory(prefix = "jimmer-apt-module-lifecycle").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes").apply { mkdirs() }
        val generatedDir = projectDir.resolve("build/generated").apply { mkdirs() }
        val sourceFile = sourceDir.resolve("demo/Book.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(BOOK_SOURCE)
        }
        val probe = RoundProbe()
        val processor = WrappedJimmerProcessor()
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
                    "-Ajimmer.source.includes=demo.",
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(processor, probe))
            task.call()
        }
        assertTrue(success, diagnostics.errorMessage())
        assertTrue(
            diagnostics.diagnostics.none { diagnostic -> diagnostic.kind == Diagnostic.Kind.ERROR },
            diagnostics.errorMessage(),
        )
        assertTrue(probe.rounds.size >= 2, probe.rounds.toString())
        assertEquals(false, probe.rounds.first().processingOver)
        assertTrue(
            probe.rounds.drop(1).any { round -> round.rootNames.any { name -> name.endsWith("BookDraft") } },
            probe.rounds.toString(),
        )
        val fetchers = generatedDir.resolve("demo/Fetchers.java")
        assertTrue(fetchers.isFile, "Missing aggregated Fetchers.java: ${fetchers.absolutePath}\n${probe.rounds}")
        assertTrue(fetchers.readText().contains("BOOK_FETCHER"), fetchers.readText())
    }

    private class RoundProbe : AbstractProcessor() {

        val rounds = mutableListOf<RoundSnapshot>()

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            rounds += RoundSnapshot(
                processingOver = roundEnvironment.processingOver(),
                errorRaised = roundEnvironment.errorRaised(),
                rootNames = roundEnvironment.rootElements
                    .filterIsInstance<TypeElement>()
                    .map { element -> element.qualifiedName.toString() }
                    .sorted(),
            )
            return false
        }
    }

    private class WrappedJimmerProcessor : Processor {

        private val delegate = JimmerProcessor()

        override fun getSupportedOptions(): MutableSet<String> = delegate.supportedOptions

        override fun getSupportedAnnotationTypes(): MutableSet<String> = delegate.supportedAnnotationTypes

        override fun getSupportedSourceVersion(): SourceVersion = delegate.supportedSourceVersion

        override fun init(processingEnvironment: ProcessingEnvironment) {
            val wrappedElements = Proxy.newProxyInstance(
                Elements::class.java.classLoader,
                arrayOf(Elements::class.java),
            ) { _, method, arguments ->
                if (method.name == "getFileObjectOf") {
                    null
                } else {
                    method.invoke(processingEnvironment.elementUtils, *(arguments ?: emptyArray()))
                }
            } as Elements
            delegate.init(
                object : ProcessingEnvironment by processingEnvironment {
                    override fun getElementUtils(): Elements = wrappedElements
                }
            )
        }

        override fun process(
            annotations: MutableSet<out TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean = delegate.process(annotations, roundEnvironment)

        override fun getCompletions(
            element: Element,
            annotation: AnnotationMirror,
            member: ExecutableElement,
            userText: String,
        ): MutableIterable<Completion> = delegate.getCompletions(element, annotation, member, userText)

    }

    private data class RoundSnapshot(
        val processingOver: Boolean,
        val errorRaised: Boolean,
        val rootNames: List<String>,
    )

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
        val BOOK_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Entity
            public interface Book {
                @Id
                long id();
            }
        """.trimIndent()
    }
}
