package org.babyfish.jimmer.compiler.apt

import java.nio.charset.StandardCharsets
import javax.lang.model.SourceVersion
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerFailureTranslation
import org.babyfish.jimmer.compiler.CompilerFailureTranslator
import org.babyfish.jimmer.compiler.JimmerCompilerCollectContext
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider

class AptJimmerProcessorTest {

    @Test
    fun `processor metadata is the union of feature descriptors`() {
        val processor = AptJimmerProcessor(
            listOf(
                MetadataProvider("second", setOf("demo.Second"), setOf("demo.second.option")),
                MetadataProvider("first", setOf("demo.First"), setOf("demo.first.option")),
            )
        )

        assertEquals(setOf("demo.First", "demo.Second"), processor.supportedAnnotationTypes)
        assertEquals(setOf("demo.first.option", "demo.second.option"), processor.supportedOptions)
        assertEquals(SourceVersion.latest(), processor.supportedSourceVersion)
    }

    @Test
    fun `translated feature failure is reported on its annotation target`() {
        val diagnostics = compile(FailingProvider(annotationTypeName = TRIGGER_ANNOTATION))

        val matchingDiagnostics = diagnostics.diagnostics.filter { diagnostic ->
            diagnostic.kind == Diagnostic.Kind.ERROR && diagnostic.getMessage(null) == FAILURE_MESSAGE
        }
        assertEquals(1, matchingDiagnostics.size)
        val diagnostic = matchingDiagnostics.single()
        assertTrue(diagnostic.source?.name.orEmpty().endsWith("Sample.java"))
        assertTrue(diagnostic.lineNumber > 0)
    }

    @Test
    fun `translated feature failure is rethrown when its target is absent`() {
        val failure = kotlin.runCatching {
            compile(FailingProvider(annotationTypeName = "demo.Missing"))
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(generateSequence(failure, Throwable::cause).any { cause -> cause === FEATURE_FAILURE })
    }

    private fun compile(provider: JimmerCompilerFeatureProvider): DiagnosticCollector<JavaFileObject> {
        val projectDir = createTempDirectory(prefix = "apt-jimmer-processor").toFile()
        val sourceDir = projectDir.resolve("src").apply { mkdirs() }
        val classesDir = projectDir.resolve("classes").apply { mkdirs() }
        val generatedDir = projectDir.resolve("generated").apply { mkdirs() }
        val sourceFile = sourceDir.resolve("Sample.java").apply { writeText(SOURCE) }
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
                listOf("-proc:only", "-classpath", System.getProperty("java.class.path")),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(AptJimmerProcessor(listOf(provider))))
            task.call()
        }
        assertFalse(success)
        return diagnostics
    }

    private class MetadataProvider(
        id: String,
        annotationTypes: Set<String>,
        options: Set<String>,
    ) : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = id,
            aptAnnotationTypes = annotationTypes,
            supportedOptions = options,
        )
    }

    private class FailingProvider(
        private val annotationTypeName: String,
    ) : JimmerCompilerFeatureProvider, CompilerFailureTranslator {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = "failing",
            aptAnnotationTypes = setOf(TRIGGER_ANNOTATION),
        )

        override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
            if (!context.round.isFinal) {
                throw FEATURE_FAILURE
            }
            return JimmerCompilerFeatureCollection()
        }

        override fun translateFailure(failure: Throwable): CompilerFailureTranslation? {
            if (failure !== FEATURE_FAILURE) {
                return null
            }
            return CompilerFailureTranslation(
                message = FAILURE_MESSAGE,
                annotationTypeName = annotationTypeName,
            )
        }
    }

    private companion object {
        const val TRIGGER_ANNOTATION = "demo.Trigger"

        const val FAILURE_MESSAGE = "Feature compilation failed"

        val FEATURE_FAILURE = IllegalStateException(FAILURE_MESSAGE)

        val SOURCE = """
            package demo;

            @interface Trigger {}

            @Trigger
            class Sample {}
        """.trimIndent()
    }
}
