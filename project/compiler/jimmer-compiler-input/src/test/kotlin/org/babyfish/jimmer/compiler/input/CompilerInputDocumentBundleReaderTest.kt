package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.jimmer.input.*

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.compiler.CompilerSourceSet
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiSourceKind

class CompilerInputDocumentBundleReaderTest {

    @Test
    fun `directory and jar bundles freeze to identical stable documents`() {
        val root = fixture("root")
        val jar = createTempDirectory("dto-bundle-jar").resolve("model-dto.jar")
        createJar(root, jar)

        val directoryDocuments = read(root)
        val jarDocuments = read(jar)

        assertEquals(directoryDocuments, jarDocuments)
        assertEquals(
            listOf(
                "compiler-input-bundle/org.babyfish.jimmer.test.dto-root/src/main/dto/org/example/Author.dto",
                "compiler-input-bundle/org.babyfish.jimmer.test.dto-root/src/main/dto/org/example/Book.dto",
            ),
            directoryDocuments.map { document -> document.source.path },
        )
        assertEquals(listOf("AuthorView {\n    id\n}\n", "BookView {\n    id\n    name\n}\n"),
            directoryDocuments.map { document -> document.content })
        assertTrue(directoryDocuments.all { document -> document.kind == DTO_INPUT_DOCUMENT_KIND })
        assertTrue(directoryDocuments.all { document -> document.sourceSet == CompilerSourceSet.MAIN })
        assertTrue(directoryDocuments.all { document -> document.source.kind == LsiSourceKind.BINARY })
        assertTrue(directoryDocuments.all { document -> document.origin is CompilerInputDocumentOrigin.Bundle })
    }

    @Test
    fun `manifest lists exact resources instead of scanning classpath`() {
        val documents = read(fixture("custom-path"))

        assertEquals(listOf("org/example/Store.dto"), documents.map { document -> document.relativePath })
        assertTrue(documents.single().content.contains("StoreView"))
        assertTrue(documents.none { document -> document.content.contains("SkippedView") })
    }

    @Test
    fun `bundle ids isolate equal relative paths and classloader order is irrelevant`() {
        val temp = createTempDirectory("dto-bundle-identities")
        val first = temp.resolve("first")
        val second = temp.resolve("second")
        writeBundle(first, "org.example:first", "org/example/Book.dto", "FirstBookView {}\n")
        writeBundle(second, "org.example:second", "org/example/Book.dto", "SecondBookView {}\n")

        val forward = read(first, second)
        val reverse = read(second, first)

        assertEquals(forward, reverse)
        assertEquals(2, forward.size)
        assertEquals(2, forward.map { document -> document.source.path }.distinct().size)
    }

    @Test
    fun `identical repeated bundle is deduplicated but changed manifest with same id fails`() {
        val temp = createTempDirectory("dto-bundle-duplicates")
        val first = temp.resolve("first")
        val duplicate = temp.resolve("duplicate")
        val conflicting = temp.resolve("conflicting")
        writeBundle(first, "org.example:catalog", "org/example/Book.dto", "BookView {}\n")
        writeBundle(duplicate, "org.example:catalog", "org/example/Book.dto", "BookView {}\n")
        writeBundle(conflicting, "org.example:catalog", "org/example/Book.dto", "ChangedBookView {}\n")

        assertEquals(1, read(first, duplicate).size)
        val exception = assertFailsWith<IllegalArgumentException> {
            read(first, conflicting)
        }
        assertTrue(exception.message.orEmpty().contains("declared by different manifests"))
    }

    @Test
    fun `rejects checksum mismatch and unknown manifest fields`() {
        val temp = createTempDirectory("dto-bundle-invalid")
        val checksumRoot = temp.resolve("checksum")
        writeBundle(checksumRoot, "org.example:checksum", "Book.dto", "BookView {}\n")
        val checksumMarker = checksumRoot.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.writeString(
            checksumMarker,
            Files.readString(checksumMarker).replace(Regex("document\\.0\\.sha256=[0-9a-f]+"), "document.0.sha256=${"0".repeat(64)}"),
        )
        val checksumException = assertFailsWith<IllegalArgumentException> { read(checksumRoot) }
        assertTrue(checksumException.message.orEmpty().contains("checksum mismatch"))

        val fieldRoot = temp.resolve("field")
        writeBundle(fieldRoot, "org.example:field", "Book.dto", "BookView {}\n")
        val fieldMarker = fieldRoot.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.writeString(fieldMarker, Files.readString(fieldMarker) + "unsupported=true\n")
        val fieldException = assertFailsWith<IllegalArgumentException> { read(fieldRoot) }
        assertTrue(fieldException.message.orEmpty().contains("unsupported: unsupported"))

        val sourceSetRoot = temp.resolve("source-set")
        writeBundle(sourceSetRoot, "org.example:source-set", "Book.dto", "BookView {}\n")
        val sourceSetMarker = sourceSetRoot.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.writeString(
            sourceSetMarker,
            Files.readString(sourceSetMarker).replace("document.0.sourceRoot=src/main/dto", "document.0.sourceRoot=src/test/dto"),
        )
        val sourceSetException = assertFailsWith<IllegalArgumentException> { read(sourceSetRoot) }
        assertTrue(sourceSetException.message.orEmpty().contains("does not start with 'src/main/'"))
    }

    @Test
    fun `directory bundle rejects resources escaping through symbolic links`() {
        val temp = createTempDirectory("dto-bundle-symlink")
        val root = temp.resolve("bundle")
        val content = "BookView {}\n"
        writeBundle(root, "org.example:symlink", "Book.dto", content)
        val resource = root.resolve("Book.dto")
        val outside = temp.resolve("Outside.dto")
        Files.writeString(outside, content, StandardCharsets.UTF_8)
        Files.delete(resource)
        Files.createSymbolicLink(resource, outside)

        val exception = assertFailsWith<IllegalArgumentException> { read(root) }

        assertTrue(exception.message.orEmpty().contains("escapes classpath root"))
    }

    @Test
    fun `jar bundle rejects directory resource paths`() {
        val root = createTempDirectory("dto-bundle-directory-resource")
        writeBundle(root, "org.example:directory", "Book.dto", "BookView {}\n")
        val marker = root.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.writeString(
            marker,
            Files.readString(marker).replace("document.0.resource=Book.dto", "document.0.resource=dto/"),
            StandardCharsets.UTF_8,
        )
        val jar = createTempDirectory("dto-bundle-directory-jar").resolve("model-dto.jar")
        createJar(root, jar)

        val exception = assertFailsWith<IllegalArgumentException> { read(jar) }

        assertTrue(exception.message.orEmpty().contains("non-normalized path 'dto/'"))
    }

    @Test
    fun `bundle option is strict and disabled scanner never opens marker`() {
        assertTrue(CompilerInputDocumentBundleReader.isEnabled(emptyMap()))
        assertTrue(CompilerInputDocumentBundleReader.isEnabled(mapOf(
            CompilerInputDocumentBundleReader.ENABLED_OPTION to " TrUe ",
        )))
        assertTrue(!CompilerInputDocumentBundleReader.isEnabled(mapOf(
            CompilerInputDocumentBundleReader.ENABLED_OPTION to " FALSE ",
        )))
        assertFailsWith<IllegalArgumentException> {
            CompilerInputDocumentBundleReader.isEnabled(mapOf(
                CompilerInputDocumentBundleReader.ENABLED_OPTION to "invalid",
            ))
        }

        val root = createTempDirectory("dto-bundle-disabled")
        val marker = root.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.createDirectories(marker.parent)
        Files.writeString(marker, "invalid")
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { classLoader ->
            val snapshots = CompilerInputDocumentScanner(
                requestedKinds = setOf(DTO_INPUT_DOCUMENT_KIND),
                options = mapOf(CompilerInputDocumentBundleReader.ENABLED_OPTION to "false"),
                bundleClassLoader = classLoader,
            ).scan(emptyList(), CompilerSourceSet.MAIN)
            assertTrue(snapshots.isEmpty())
        }
    }

    @Test
    fun `bundle only scanner freezes references without filesystem anchor`() {
        URLClassLoader(arrayOf(fixture("root").toUri().toURL()), null).use { classLoader ->
            val snapshots = CompilerInputDocumentScanner(
                requestedKinds = setOf(DTO_INPUT_DOCUMENT_KIND),
                options = mapOf("jimmer.dto.dirs" to "/"),
                bundleClassLoader = classLoader,
            ).scan(emptyList(), CompilerSourceSet.MAIN)

            assertEquals(2, snapshots.size)
            assertEquals(
                listOf("org.example.Author", "org.example.Book"),
                snapshots.flatMap { snapshot -> snapshot.typeSeeds }
                    .map { seed -> seed.typeId.requireTypeQualifiedName() },
            )
            assertIs<CompilerInputDocumentOrigin.Bundle>(snapshots.first().document.origin)
            assertEquals(CompilerSourceSet.MAIN, snapshots.first().document.sourceSet)
        }
    }

    @Test
    fun `renderer creates canonical aggregating artifacts that round trip through reader`() {
        val main = projectDocument(
            sourceSet = CompilerSourceSet.MAIN,
            sourceRoot = "src/main/dto",
            relativePath = "demo/Book.dto",
            content = "BookView {}\n",
        )
        val test = projectDocument(
            sourceSet = CompilerSourceSet.TEST,
            sourceRoot = "src/test/dto",
            relativePath = "demo/TestBook.dto",
            content = "TestBookView {}\n",
        )
        val renderer = CompilerInputDocumentBundleRenderer()
        val artifacts = renderer.render(
            bundleId = "org.example:catalog-model",
            snapshots = listOf(
                CompilerInputDocumentSnapshot(test, emptyList()),
                CompilerInputDocumentSnapshot(main, emptyList()),
            ),
        )

        assertEquals(
            artifacts,
            renderer.render(
                bundleId = "org.example:catalog-model",
                snapshots = listOf(
                    CompilerInputDocumentSnapshot(main, emptyList()),
                    CompilerInputDocumentSnapshot(test, emptyList()),
                ),
            ),
        )
        assertEquals(3, artifacts.size)
        assertTrue(artifacts.all { artifact -> artifact.kind == ArtifactKind.RESOURCE })
        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        val root = createTempDirectory("rendered-dto-bundle")
        artifacts.forEach { artifact ->
            val file = root.resolve(artifact.path)
            Files.createDirectories(file.parent)
            Files.writeString(file, artifact.content, StandardCharsets.UTF_8)
        }

        val documents = read(root)

        assertEquals(
            listOf(
                Triple(CompilerSourceSet.MAIN, "demo/Book.dto", "BookView {}\n"),
                Triple(CompilerSourceSet.TEST, "demo/TestBook.dto", "TestBookView {}\n"),
            ),
            documents.map { document -> Triple(document.sourceSet, document.relativePath, document.content) },
        )
        assertTrue(documents.all { document -> document.source.kind == LsiSourceKind.BINARY })
    }

    @Test
    fun `scanner selects only bundle documents for the current source set`() {
        val temp = createTempDirectory("dto-bundle-source-sets")
        val main = temp.resolve("main")
        val test = temp.resolve("test")
        writeBundle(
            root = main,
            bundleId = "org.example:main",
            relativePath = "demo/Book.dto",
            content = "BookView {}\n",
            sourceSet = CompilerSourceSet.MAIN,
        )
        writeBundle(
            root = test,
            bundleId = "org.example:test",
            relativePath = "demo/TestBook.dto",
            content = "TestBookView {}\n",
            sourceSet = CompilerSourceSet.TEST,
        )
        URLClassLoader(arrayOf(main.toUri().toURL(), test.toUri().toURL()), null).use { classLoader ->
            val scanner = CompilerInputDocumentScanner(
                requestedKinds = setOf(DTO_INPUT_DOCUMENT_KIND),
                options = emptyMap(),
                bundleClassLoader = classLoader,
            )

            assertEquals(
                listOf("demo/Book.dto"),
                scanner.scan(emptyList(), CompilerSourceSet.MAIN)
                    .map { snapshot -> snapshot.document.relativePath },
            )
            assertEquals(
                listOf("demo/TestBook.dto"),
                scanner.scan(emptyList(), CompilerSourceSet.TEST)
                    .map { snapshot -> snapshot.document.relativePath },
            )
        }
    }

    private fun fixture(name: String): Path {
        val url = requireNotNull(javaClass.classLoader.getResource("dto-bundle-fixtures/$name"))
        return Paths.get(url.toURI())
    }

    private fun read(vararg roots: Path) = URLClassLoader(
        roots.map { root -> root.toUri().toURL() }.toTypedArray(),
        null,
    ).use { classLoader -> CompilerInputDocumentBundleReader(classLoader).read() }

    private fun writeBundle(
        root: Path,
        bundleId: String,
        relativePath: String,
        content: String,
        sourceSet: CompilerSourceSet = CompilerSourceSet.MAIN,
    ) {
        val resource = root.resolve(relativePath)
        Files.createDirectories(resource.parent)
        Files.writeString(resource, content, StandardCharsets.UTF_8)
        val marker = root.resolve(CompilerInputDocumentBundleReader.MARKER_PATH)
        Files.createDirectories(marker.parent)
        Files.writeString(
            marker,
            """
                format=2
                bundleId=$bundleId
                document.count=1
                document.0.sourceSet=${sourceSet.name}
                document.0.sourceRoot=${if (sourceSet == CompilerSourceSet.MAIN) "src/main/dto" else "src/test/dto"}
                document.0.relativePath=$relativePath
                document.0.resource=$relativePath
                document.0.sha256=${sha256(content.toByteArray(StandardCharsets.UTF_8))}
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
    }

    private fun createJar(source: Path, target: Path) {
        JarOutputStream(Files.newOutputStream(target)).use { output ->
            Files.walk(source).use { files ->
                val sourceUri = source.toUri()
                files.filter(Files::isRegularFile).sorted().forEach { file ->
                    val name = sourceUri.relativize(file.toUri()).path
                    output.putNextEntry(JarEntry(name))
                    Files.copy(file, output)
                    output.closeEntry()
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun projectDocument(
        sourceSet: CompilerSourceSet,
        sourceRoot: String,
        relativePath: String,
        content: String,
    ): CompilerInputDocument = CompilerInputDocument(
        kind = DTO_INPUT_DOCUMENT_KIND,
        sourceSet = sourceSet,
        origin = CompilerInputDocumentOrigin.Project("catalog", sourceRoot),
        relativePath = relativePath,
        content = content,
    )
}
