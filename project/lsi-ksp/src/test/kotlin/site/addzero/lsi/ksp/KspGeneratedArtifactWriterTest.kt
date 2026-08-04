package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class KspGeneratedArtifactWriterTest {

    @Test
    fun `writes transitive dependency files separately from artifact origin`() {
        val codeGenerator = CapturingCodeGenerator()
        val writer = KspGeneratedArtifactWriter(codeGenerator)
        val childId = LsiSymbolId.type("demo.Child")
        val baseId = LsiSymbolId.type("demo.Base")
        val childFile = file("/workspace/Child.kt")
        val baseFile = file("/workspace/Base.kt")

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.KOTLIN_SOURCE,
                qualifiedName = "demo.ChildDraft",
                content = "package demo\ninterface ChildDraft",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(childId),
                originatingSources = setOf(LsiSource.of(childFile.filePath, LsiLanguage.KOTLIN)),
                dependencySymbols = setOf(childId, baseId),
                dependencySources = setOf(
                    LsiSource.of(childFile.filePath, LsiLanguage.KOTLIN),
                    LsiSource.of(baseFile.filePath, LsiLanguage.KOTLIN),
                ),
            ),
            currentRoundFiles = mapOf(childId to childFile, baseId to baseFile),
            currentRoundSourceFiles = listOf(childFile, baseFile),
        )

        val dependencies = codeGenerator.calls.single().dependencies
        assertTrue(dependencies.aggregating)
        assertEquals(2, dependencies.originatingFiles.size)
        assertSame(baseFile, dependencies.originatingFiles[0])
        assertSame(childFile, dependencies.originatingFiles[1])
    }

    @Test
    fun `writes kotlin source and resource with incremental dependencies`() {
        val codeGenerator = CapturingCodeGenerator()
        val writer = KspGeneratedArtifactWriter(codeGenerator)
        val firstId = LsiSymbolId.type("demo.First")
        val secondId = LsiSymbolId.type("demo.Second")
        val firstFile = file("/workspace/First.kt")
        val secondFile = file("/workspace/Second.kt")
        val thirdFile = file("/workspace/Third.kt")
        val currentRoundFiles = mapOf(
            firstId to firstFile,
            secondId to secondFile,
        )

        writer.write(
            GeneratedArtifact.source(
                kind = ArtifactKind.KOTLIN_SOURCE,
                qualifiedName = "demo.BookGenerated",
                content = "package demo\ninterface BookGenerated",
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                originatingSymbols = setOf(firstId),
                originatingSources = setOf(LsiSource.of("/workspace/First.kt", LsiLanguage.KOTLIN)),
            ),
            currentRoundFiles,
            listOf(firstFile, secondFile),
        )
        writer.write(
            GeneratedArtifact.create(
                kind = ArtifactKind.RESOURCE,
                path = "META-INF/lsi-test/schema",
                content = "schema",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(secondId, firstId),
                originatingSources = setOf(LsiSource.of("catalog/src/main/dto/Book.dto")),
            ),
            currentRoundFiles,
            listOf(firstFile, secondFile, thirdFile),
        )

        val sourceCall = codeGenerator.calls[0]
        assertEquals("demo/BookGenerated", sourceCall.path)
        assertEquals("kt", sourceCall.extension)
        assertEquals("package demo\ninterface BookGenerated", sourceCall.content())
        assertTrue(!sourceCall.dependencies.aggregating)
        assertEquals(1, sourceCall.dependencies.originatingFiles.size)
        assertSame(firstFile, sourceCall.dependencies.originatingFiles.single())
        val resourceCall = codeGenerator.calls[1]
        assertEquals("META-INF/lsi-test/schema", resourceCall.path)
        assertEquals("", resourceCall.extension)
        assertEquals("schema", resourceCall.content())
        assertTrue(resourceCall.dependencies.aggregating)
        assertFalse(resourceCall.dependencies.isAllSources)
        assertEquals(3, resourceCall.dependencies.originatingFiles.size)
        assertSame(firstFile, resourceCall.dependencies.originatingFiles[0])
        assertSame(secondFile, resourceCall.dependencies.originatingFiles[1])
        assertSame(thirdFile, resourceCall.dependencies.originatingFiles[2])
    }

    @Test
    fun `writes final aggregating resource with all files dependency`() {
        val codeGenerator = CapturingCodeGenerator()
        val writer = KspGeneratedArtifactWriter(codeGenerator)

        writer.write(
            GeneratedArtifact.create(
                kind = ArtifactKind.RESOURCE,
                path = "META-INF/lsi-test/doc.properties",
                content = "demo.Book=Book",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSymbols = setOf(LsiSymbolId.type("demo.Book")),
                originatingSources = setOf(LsiSource.of("/workspace/Book.kt", LsiLanguage.KOTLIN)),
            ),
            emptyMap(),
            emptyList(),
        )

        val resourceCall = codeGenerator.calls.single()
        assertTrue(resourceCall.dependencies.isAllSources)
        assertTrue(resourceCall.dependencies.aggregating)
        assertTrue(resourceCall.dependencies.originatingFiles.isEmpty())
    }

    @Test
    fun `writes dependency free aggregating artifact with all files dependency`() {
        val codeGenerator = CapturingCodeGenerator()
        val writer = KspGeneratedArtifactWriter(codeGenerator)
        val currentFile = file("/workspace/Book.kt")

        writer.write(
            GeneratedArtifact.create(
                kind = ArtifactKind.RESOURCE,
                path = "META-INF/lsi-test/module",
                content = "module",
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
            ),
            currentRoundFiles = emptyMap(),
            currentRoundSourceFiles = listOf(currentFile),
        )

        val dependencies = codeGenerator.calls.single().dependencies
        assertTrue(dependencies.isAllSources)
        assertTrue(dependencies.aggregating)
        assertTrue(dependencies.originatingFiles.isEmpty())
    }

    @Test
    fun `rejects java source and missing isolating file`() {
        val codeGenerator = CapturingCodeGenerator()
        val writer = KspGeneratedArtifactWriter(codeGenerator)
        val sourceId = LsiSymbolId.type("demo.Book")

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
                emptyList(),
            )
        }
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
                emptyList(),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            writer.write(
                GeneratedArtifact.source(
                    kind = ArtifactKind.KOTLIN_SOURCE,
                    qualifiedName = "demo.BookGenerated",
                    content = "package demo",
                    aggregationMode = ArtifactAggregationMode.ISOLATING,
                    originatingSymbols = setOf(sourceId),
                    originatingSources = setOf(LsiSource.of("catalog/src/main/dto/Book.dto")),
                ),
                mapOf(sourceId to file("/workspace/Book.kt")),
                listOf(file("/workspace/Book.kt")),
            )
        }

        assertTrue(codeGenerator.calls.isEmpty())
    }

    private fun file(path: String): KSFile {
        lateinit var instance: Any
        instance = Proxy.newProxyInstance(
            KSFile::class.java.classLoader,
            arrayOf(KSFile::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "getFilePath" -> path
                "toString" -> path
                else -> null
            }
        }
        return instance as KSFile
    }

    private class CapturingCodeGenerator : CodeGenerator {

        val calls = mutableListOf<WriteCall>()

        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): OutputStream {
            error("Package-based output is not supported by this test generator")
        }

        override fun createNewFileByPath(
            dependencies: Dependencies,
            path: String,
            extensionName: String,
        ): OutputStream {
            val output = ByteArrayOutputStream()
            calls += WriteCall(dependencies, path, extensionName, output)
            return output
        }

        override fun associate(
            sources: List<KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override fun associateByPath(
            sources: List<KSFile>,
            path: String,
            extensionName: String,
        ) = Unit

        override fun associateWithClasses(
            classes: List<KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override val generatedFile: Collection<File> = emptyList()
    }

    private data class WriteCall(
        val dependencies: Dependencies,
        val path: String,
        val extension: String,
        val output: ByteArrayOutputStream,
    ) {
        fun content(): String = String(output.toByteArray(), Charsets.UTF_8)
    }
}
