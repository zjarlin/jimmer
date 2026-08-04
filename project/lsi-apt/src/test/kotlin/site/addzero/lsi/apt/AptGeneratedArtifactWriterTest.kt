package site.addzero.lsi.apt

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.net.URI
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
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
    fun `keeps same-source transitive dependencies isolating`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val childId = LsiSymbolId.type("demo.Child")
        val baseId = LsiSymbolId.type("demo.Base")
        val childElement = element("Child")
        val baseElement = element("Base")
        val sharedSource = LsiSource.of("/workspace/Models.java", LsiLanguage.JAVA)

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = "demo.ChildDraft",
                content = "package demo; public interface ChildDraft {}",
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                originatingSymbols = setOf(childId),
                originatingSources = setOf(sharedSource),
                dependencySymbols = setOf(childId, baseId),
                dependencySources = setOf(sharedSource),
            ),
            currentRoundElements = mapOf(childId to childElement, baseId to baseElement),
            currentRoundSources = mapOf(childId to sharedSource, baseId to sharedSource),
        )

        val call = filer.sourceCalls.single()
        assertEquals(1, call.originatingElements.size)
        assertSame(childElement, call.originatingElements.single())
    }

    @Test
    fun `writes transitive dependency elements separately from artifact origin`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val childId = LsiSymbolId.type("demo.Child")
        val baseId = LsiSymbolId.type("demo.Base")
        val childElement = element("Child")
        val baseElement = element("Base")
        val childSource = LsiSource.of("/workspace/Child.java", LsiLanguage.JAVA)
        val baseSource = LsiSource.of("/workspace/Base.java", LsiLanguage.JAVA)

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = "demo.ChildDraft",
                content = "package demo; public interface ChildDraft {}",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(childId),
                originatingSources = setOf(childSource),
                dependencySymbols = setOf(childId, baseId),
                dependencySources = setOf(childSource, baseSource),
            ),
            currentRoundElements = mapOf(childId to childElement, baseId to baseElement),
            currentRoundSources = mapOf(childId to childSource, baseId to baseSource),
        )

        val call = filer.sourceCalls.single()
        assertEquals(2, call.originatingElements.size)
        assertSame(baseElement, call.originatingElements[0])
        assertSame(childElement, call.originatingElements[1])
    }

    @Test
    fun `normalizes aggregating member and type parameter dependencies to top level types`() {
        val filer = CapturingFiler()
        val writer = AptGeneratedArtifactWriter(filer)
        val rootId = LsiSymbolId.type("demo.Root")
        val propertyId = LsiSymbolId.property(rootId, "value")
        val typeParameterId = LsiSymbolId.typeParameter(rootId, "T")
        val rootElement = element("demo.Root")
        val propertyElement = enclosedElement("value", rootElement)
        val typeParameterElement = enclosedTypeParameter("T", rootElement)
        val source = LsiSource.of("/workspace/Root.java", LsiLanguage.JAVA)

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.JAVA_SOURCE,
                qualifiedName = "demo.RootTable",
                content = "package demo; public class RootTable {}",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(rootId),
                originatingSources = setOf(source),
                dependencySymbols = setOf(typeParameterId, propertyId, rootId),
                dependencySources = setOf(source),
            ),
            currentRoundElements = mapOf(
                rootId to rootElement,
                propertyId to propertyElement,
                typeParameterId to typeParameterElement,
            ),
            currentRoundSources = mapOf(
                rootId to source,
                propertyId to source,
                typeParameterId to source,
            ),
        )

        val originatingElements = filer.sourceCalls.single().originatingElements
        assertEquals(1, originatingElements.size)
        assertSame(rootElement, originatingElements.single())
        assertTrue(originatingElements.single() is TypeElement)
    }

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
                qualifiedName = "demo.BookGenerated",
                content = "package demo; public interface BookGenerated {}",
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
                path = "META-INF/lsi-test/schema",
                content = "schema",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(secondId, firstId),
            ),
            currentRoundElements,
            currentRoundSources,
        )

        val sourceCall = filer.sourceCalls.single()
        assertEquals("demo.BookGenerated", sourceCall.qualifiedName)
        assertEquals("package demo; public interface BookGenerated {}", sourceCall.output.content())
        assertEquals(1, sourceCall.originatingElements.size)
        assertSame(firstElement, sourceCall.originatingElements.single())
        val resourceCall = filer.resourceCalls.single()
        assertEquals(StandardLocation.CLASS_OUTPUT, resourceCall.location)
        assertEquals("", resourceCall.moduleAndPackage)
        assertEquals("META-INF/lsi-test/schema", resourceCall.path)
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
                    qualifiedName = "demo.BookGenerated",
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
                    qualifiedName = "demo.BookGenerated",
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
                    qualifiedName = "demo.BookGenerated",
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

    private fun element(label: String): TypeElement {
        return proxyElement(TypeElement::class.java, label, enclosingElement = null)
    }

    private fun enclosedElement(
        label: String,
        enclosingElement: Element,
    ): Element {
        return proxyElement(Element::class.java, label, enclosingElement)
    }

    private fun enclosedTypeParameter(
        label: String,
        enclosingElement: Element,
    ): TypeParameterElement {
        return proxyElement(TypeParameterElement::class.java, label, enclosingElement)
    }

    private fun <T : Element> proxyElement(
        elementType: Class<T>,
        label: String,
        enclosingElement: Element?,
    ): T {
        lateinit var instance: Any
        instance = Proxy.newProxyInstance(
            elementType.classLoader,
            arrayOf(elementType),
        ) { _, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.firstOrNull()
                "getEnclosingElement" -> enclosingElement
                "getKind" -> when (elementType) {
                    TypeElement::class.java -> ElementKind.INTERFACE
                    TypeParameterElement::class.java -> ElementKind.TYPE_PARAMETER
                    else -> ElementKind.FIELD
                }
                "getQualifiedName",
                "getSimpleName",
                -> TestName(label)
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> label
                else -> null
            }
        }
        return elementType.cast(instance)
    }

    private data class TestName(
        private val value: String,
    ) : Name, CharSequence by value {
        override fun contentEquals(cs: CharSequence): Boolean = value.contentEquals(cs)

        override fun toString(): String = value
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
