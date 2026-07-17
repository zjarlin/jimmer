package org.babyfish.jimmer.compiler.input

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerSourceSet

class FileSystemCompilerInputDocumentScannerTest {

    @Test
    fun `scans requested source set in stable path order`() {
        val project = project()
        project.write("src/main/dto/store/Store.dto", "export Store")
        project.write("src/main/dto/Book.dto", "export Book")
        project.write("src/test/dto/TestBook.dto", "export TestBook")
        val start = project.resolve("build/classes/kotlin/main").apply(File::mkdirs)

        val documents = scanner.scan(
            startPaths = listOf(start),
            requestedKinds = setOf(CompilerInputDocumentKind.DTO),
            sourceSet = CompilerSourceSet.MAIN,
            options = emptyMap(),
        )

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

        val first = scanner.scan(
            startPaths = listOf(start),
            requestedKinds = setOf(CompilerInputDocumentKind.DTO),
            sourceSet = CompilerSourceSet.TEST,
            options = options,
        ).single()
        source.writeText("second")
        val second = scanner.scan(
            startPaths = listOf(start),
            requestedKinds = setOf(CompilerInputDocumentKind.DTO),
            sourceSet = CompilerSourceSet.TEST,
            options = options,
        ).single()
        val renamed = source.parentFile.resolve("Renamed.dto")
        assertTrue(source.renameTo(renamed))
        val third = scanner.scan(
            startPaths = listOf(start),
            requestedKinds = setOf(CompilerInputDocumentKind.DTO),
            sourceSet = CompilerSourceSet.TEST,
            options = options,
        ).single()

        assertEquals("first", first.content)
        assertEquals("second", second.content)
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals("src/test/api-dto", second.sourceRoot)
        assertEquals("nested/Book.dto", second.relativePath)
        assertEquals("nested/Renamed.dto", third.relativePath)
        assertNotEquals(second.fingerprint, third.fingerprint)
    }

    @Test
    fun `returns no documents for missing roots or unrequested kind`() {
        val project = project()
        val start = project.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }

        assertTrue(
            scanner.scan(
                startPaths = listOf(start),
                requestedKinds = setOf(CompilerInputDocumentKind.DTO),
                sourceSet = CompilerSourceSet.MAIN,
                options = emptyMap(),
            ).isEmpty()
        )
        assertTrue(
            scanner.scan(
                startPaths = listOf(start),
                requestedKinds = emptySet(),
                sourceSet = CompilerSourceSet.MAIN,
                options = emptyMap(),
            ).isEmpty()
        )
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
            scanner.scan(
                startPaths = listOf(start),
                requestedKinds = setOf(CompilerInputDocumentKind.DTO),
                sourceSet = CompilerSourceSet.MAIN,
                options = mapOf("jimmer.dto.dirs" to "/"),
            ).isEmpty()
        )
        assertFailsWith<IllegalArgumentException> {
            scanner.scan(
                startPaths = emptyList(),
                requestedKinds = setOf(CompilerInputDocumentKind.DTO),
                sourceSet = CompilerSourceSet.MAIN,
                options = mapOf("jimmer.dto.dirs" to "src/test/dto"),
            )
        }
    }

    private fun project(): File = createTempDirectory(prefix = "compiler-input-documents").toFile()

    private fun File.write(path: String, content: String): File {
        return resolve(path).also { file ->
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    private companion object {
        val scanner = FileSystemCompilerInputDocumentScanner()
    }
}
