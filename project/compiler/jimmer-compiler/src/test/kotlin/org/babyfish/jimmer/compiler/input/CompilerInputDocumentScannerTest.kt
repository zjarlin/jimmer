package org.babyfish.jimmer.compiler.input

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentOrigin
import org.babyfish.jimmer.compiler.CompilerSourceSet

class CompilerInputDocumentScannerTest {

    @Test
    fun `scans requested source set in stable path order`() {
        val project = project()
        project.write("src/main/dto/store/Store.dto", "export Store")
        project.write("src/main/dto/Book.dto", "export Book")
        project.write("src/test/dto/TestBook.dto", "export TestBook")
        val start = project.resolve("build/classes/kotlin/main").apply(File::mkdirs)
        val scanner = scanner()

        val documents = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.MAIN,
        ).map { snapshot -> snapshot.document }

        assertEquals(listOf("Book.dto", "store/Store.dto"), documents.map { document -> document.relativePath })
        assertEquals(listOf("export Book", "export Store"), documents.map { document -> document.content })
        assertTrue(documents.all { document -> document.sourceSet == CompilerSourceSet.MAIN })
    }

    @Test
    fun `uses custom roots removes nested duplicates and freezes content`() {
        val project = project()
        val source = project.write("src/test/api-dto/nested/Book.dto", "first")
        val start = project.resolve("src/test/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }
        val options = mapOf(
            "jimmer.dto.testDirs" to "src/test/api-dto, src/test/api-dto/nested",
        )
        val scanner = scanner(options = options)

        val first = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.TEST,
        ).single().document
        source.writeText("second")
        val second = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.TEST,
        ).single().document
        val renamed = source.parentFile.resolve("Renamed.dto")
        assertTrue(source.renameTo(renamed))
        val third = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.TEST,
        ).single().document
        val refreshed = scanner(options = options).scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.TEST,
        ).single().document

        assertEquals("first", first.content)
        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(
            "src/test/api-dto",
            (second.origin as CompilerInputDocumentOrigin.Project).sourceRoot,
        )
        assertEquals("nested/Book.dto", second.relativePath)
        assertEquals("second", refreshed.content)
        assertEquals("nested/Renamed.dto", refreshed.relativePath)
    }

    @Test
    fun `returns no documents for missing roots or unrequested kind`() {
        val project = project()
        val start = project.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }

        assertTrue(
            scanner().scan(
                startPaths = listOf(start),
                sourceSet = CompilerSourceSet.MAIN,
            ).isEmpty()
        )
        assertTrue(
            scanner(requestedKinds = emptySet()).scan(
                startPaths = listOf(start),
                sourceSet = CompilerSourceSet.MAIN,
            ).isEmpty()
        )
    }

    @Test
    fun `keeps filesystem discovery pending until a source anchor appears`() {
        val project = project()
        project.write("src/main/dto/Book.dto", "export Book")
        val source = project.write("src/main/kotlin/demo/Model.kt", "interface Model")
        val scanner = scanner()

        assertTrue(scanner.scan(emptyList(), CompilerSourceSet.MAIN).isEmpty())
        assertTrue(!scanner.isFileSystemDiscoveryComplete(CompilerSourceSet.MAIN))

        val snapshots = scanner.scan(listOf(source), CompilerSourceSet.MAIN)

        assertEquals(listOf("Book.dto"), snapshots.map { snapshot -> snapshot.document.relativePath })
        assertTrue(scanner.isFileSystemDiscoveryComplete(CompilerSourceSet.MAIN))
    }

    @Test
    fun `explicit empty roots disable scanning and invalid options fail without source files`() {
        val project = project()
        project.write("src/main/dto/Book.dto", "export Book")
        val start = project.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }

        assertTrue(
            scanner(options = mapOf("jimmer.dto.dirs" to "/")).scan(
                startPaths = listOf(start),
                sourceSet = CompilerSourceSet.MAIN,
            ).isEmpty()
        )
        assertFailsWith<IllegalArgumentException> {
            scanner(
                options = mapOf("jimmer.dto.dirs" to "src/test/dto"),
            )
        }
    }

    @Test
    fun `freezes dto reference ids with each document`() {
        val project = project()
        project.write(
            "src/main/dto/demo/Book.dto",
            """
                export demo.Book
                import demo.api.Marker
                @demo.api.Tag
                BookView implements Marker {
                    payload: demo.api.Payload
                }
            """.trimIndent(),
        )
        val start = project.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }
        val scanner = scanner()

        val snapshot = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.MAIN,
        ).single()

        assertEquals(
            listOf("demo.Book", "demo.api.Tag", "demo.api.Marker", "demo.api.Payload"),
            snapshot.references.map { reference ->
                reference.typeSelector.fallbackTypeId.requireTypeQualifiedName()
            },
        )
        assertEquals(
            setOf("demo.Book", "demo.api.Tag", "demo.api.Marker", "demo.api.Payload"),
            snapshot.referencedTypeIds.mapTo(linkedSetOf()) { typeId -> typeId.requireTypeQualifiedName() },
        )
        assertTrue(snapshot.references.all { reference -> reference.location.source == snapshot.document.source })
    }

    @Test
    fun `keeps malformed dto for formal compiler diagnostics`() {
        val project = project()
        project.write("src/main/dto/demo/Broken.dto", "@broken(")
        val start = project.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }
        val scanner = scanner()

        val snapshot = scanner.scan(
            startPaths = listOf(start),
            sourceSet = CompilerSourceSet.MAIN,
        ).single()

        assertEquals("@broken(", snapshot.document.content)
        assertTrue(snapshot.referencedTypeIds.contains(site.addzero.lsi.core.LsiSymbolId.type("demo.Broken")))
    }

    private fun project(): File = createTempDirectory(prefix = "compiler-input-documents").toFile()

    private fun File.write(path: String, content: String): File {
        return resolve(path).also { file ->
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    private fun scanner(
        requestedKinds: Set<CompilerInputDocumentKind> = setOf(CompilerInputDocumentKind.DTO),
        options: Map<String, String> = emptyMap(),
        classLoader: ClassLoader = EMPTY_CLASS_LOADER,
    ): CompilerInputDocumentScanner = CompilerInputDocumentScanner(
        requestedKinds = requestedKinds,
        options = options,
        bundleClassLoader = classLoader,
    )

    private companion object {
        val EMPTY_CLASS_LOADER = object : ClassLoader(null) {}
    }
}
