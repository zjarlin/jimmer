package org.babyfish.jimmer.compiler.lsi.apt

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.net.URI
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.tools.FileObject
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class AptGeneratedArtifactWriterTest {

    @Test
    fun `writes java source and resource with current round elements`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val firstId = LsiSymbolId.type("demo.First")
        val secondId = LsiSymbolId.type("demo.Second")
        val firstElement = element("First")
        val secondElement = element("Second")
        val currentRoundElements = mapOf(
            firstId to firstElement,
            secondId to secondElement,
        )
        val currentRoundSources = mapOf(
            firstId to LsiSource.of("/workspace/First.java", LsiLanguage.JAVA),
            secondId to LsiSource.of("/workspace/Second.java", LsiLanguage.JAVA),
        )

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = "demo.BookDraft",
                content = "package demo; public interface BookDraft {}",
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                originatingSymbols = setOf(firstId),
                originatingSources = setOf(currentRoundSources.getValue(firstId)),
            ),
            currentRoundElements,
            currentRoundSources,
        )
        writer.write(
            GeneratedArtifact.create(
                kind = ArtifactKind.RESOURCE,
                path = "META-INF/jimmer/client",
                content = "schema",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(secondId, firstId),
            ),
            currentRoundElements,
            currentRoundSources,
        )

        val sourceCall = filer.sourceCalls.single()
        assertEquals("demo.BookDraft", sourceCall.qualifiedName)
        assertEquals("package demo; public interface BookDraft {}", sourceCall.output.content())
        assertEquals(1, sourceCall.originatingElements.size)
        assertSame(firstElement, sourceCall.originatingElements.single())
        val resourceCall = filer.resourceCalls.single()
        assertEquals(StandardLocation.CLASS_OUTPUT, resourceCall.location)
        assertEquals("", resourceCall.moduleAndPackage)
        assertEquals("META-INF/jimmer/client", resourceCall.path)
        assertEquals("schema", resourceCall.output.content())
        assertEquals(2, resourceCall.originatingElements.size)
        assertSame(firstElement, resourceCall.originatingElements[0])
        assertSame(secondElement, resourceCall.originatingElements[1])
    }

    @Test
    fun `rejects kotlin source and missing isolating element`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val sourceId = LsiSymbolId.type("demo.Book")

        assertFailsWith<IllegalArgumentException> {
            writer.write(
                GeneratedArtifact.source(
                    kind = ArtifactKind.KOTLIN_SOURCE,
                    qualifiedName = "demo.BookDraft",
                    content = "package demo",
                    aggregationMode = ArtifactAggregationMode.ISOLATING,
                    originatingSymbols = setOf(sourceId),
                ),
                emptyMap(),
                emptyMap(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            writer.write(
                GeneratedArtifact.source(
                    kind = ArtifactKind.JAVA_SOURCE,
                    qualifiedName = "demo.BookDraft",
                    content = "package demo;",
                    aggregationMode = ArtifactAggregationMode.ISOLATING,
                    originatingSymbols = setOf(sourceId),
                ),
                emptyMap(),
                emptyMap(),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            writer.write(
                GeneratedArtifact.source(
                    kind = ArtifactKind.JAVA_SOURCE,
                    qualifiedName = "demo.BookDraft",
                    content = "package demo;",
                    aggregationMode = ArtifactAggregationMode.ISOLATING,
                    originatingSymbols = setOf(sourceId),
                    originatingSources = setOf(LsiSource.of("catalog/src/main/dto/Book.dto")),
                ),
                mapOf(sourceId to element("Book")),
                mapOf(sourceId to LsiSource.of("/workspace/Book.java", LsiLanguage.JAVA)),
            )
        }

        assertTrue(filer.sourceCalls.isEmpty())
        assertTrue(filer.resourceCalls.isEmpty())
    }

    @Test
    fun `stable source requires all originating sources in current round`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val firstId = LsiSymbolId.type("demo.First")
        val secondId = LsiSymbolId.type("demo.Second")
        val firstSource = LsiSource.of("/workspace/First.java", LsiLanguage.JAVA)
        val secondSource = LsiSource.of("/workspace/Second.java", LsiLanguage.JAVA)
        val artifact = GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = "demo.RootTable",
            content = "package demo; public class RootTable {}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            emissionMode = ArtifactEmissionMode.STABLE,
            originatingSymbols = setOf(firstId, secondId),
            originatingSources = setOf(firstSource, secondSource),
        )

        assertFailsWith<IllegalArgumentException> {
            writer.write(
                artifact,
                mapOf(firstId to element("First")),
                mapOf(firstId to firstSource),
            )
        }

        assertTrue(filer.sourceCalls.isEmpty())
    }

    private fun element(label: String): Element {
        lateinit var instance: Any
        instance = Proxy.newProxyInstance(
            Element::class.java.classLoader,
            arrayOf(Element::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> label
                else -> null
            }
        }
        return instance as Element
    }

    private class CapturingFiler : Filer {

        val sourceCalls = mutableListOf<SourceCall>()

        val resourceCalls = mutableListOf<ResourceCall>()

        override fun createSourceFile(
            name: CharSequence,
            vararg originatingElements: Element,
        ): JavaFileObject {
            val output = MemoryFileObject("mem:///${name.toString().replace('.', '/')}.java", JavaFileObject.Kind.SOURCE)
            sourceCalls += SourceCall(name.toString(), originatingElements.toList(), output)
            return output
        }

        override fun createClassFile(
            name: CharSequence,
            vararg originatingElements: Element,
        ): JavaFileObject {
            error("Class file output is not supported by this test filer")
        }

        override fun createResource(
            location: JavaFileManager.Location,
            moduleAndPkg: CharSequence,
            relativeName: CharSequence,
            vararg originatingElements: Element,
        ): FileObject {
            val output = MemoryFileObject("mem:///${relativeName}", JavaFileObject.Kind.OTHER)
            resourceCalls += ResourceCall(
                location,
                moduleAndPkg.toString(),
                relativeName.toString(),
                originatingElements.toList(),
                output,
            )
            return output
        }

        override fun getResource(
            location: JavaFileManager.Location,
            moduleAndPkg: CharSequence,
            relativeName: CharSequence,
        ): FileObject {
            error("Resource lookup is not supported by this test filer")
        }
    }

    private data class SourceCall(
        val qualifiedName: String,
        val originatingElements: List<Element>,
        val output: MemoryFileObject,
    )

    private data class ResourceCall(
        val location: JavaFileManager.Location,
        val moduleAndPackage: String,
        val path: String,
        val originatingElements: List<Element>,
        val output: MemoryFileObject,
    )

    private class MemoryFileObject(
        uri: String,
        kind: JavaFileObject.Kind,
    ) : SimpleJavaFileObject(URI.create(uri), kind) {

        private val output = ByteArrayOutputStream()

        override fun openOutputStream(): OutputStream = output

        fun content(): String = String(output.toByteArray(), Charsets.UTF_8)
    }
}
