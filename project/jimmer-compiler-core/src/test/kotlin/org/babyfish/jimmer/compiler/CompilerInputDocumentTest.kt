package org.babyfish.jimmer.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CompilerInputDocumentTest {

    @Test
    fun `derives stable source and content fingerprint`() {
        val document = document("book/Book.dto", "export Book")

        assertEquals("catalog/src/main/dto/book/Book.dto", document.source.path)
        assertEquals(
            "8649417ec959322d191b59b848c534401474f78f80db0b18af3d98da2c734740",
            document.fingerprint,
        )
        assertEquals(document.fingerprint, document("book/Book.dto", "export Book").fingerprint)
        assertNotEquals(document.fingerprint, document("book/Book.dto", "export Store").fingerprint)
        assertNotEquals(document.fingerprint, document("book/Store.dto", "export Book").fingerprint)
        assertNotEquals(document.fingerprint, document.copy(projectName = "catalog-api").fingerprint)
        assertNotEquals(document.fingerprint, document.copy(sourceRoot = "src/main/api-dto").fingerprint)
        assertNotEquals(document.fingerprint, document.copy(sourceSet = CompilerSourceSet.TEST).fingerprint)
    }

    @Test
    fun `rejects invalid dto identities`() {
        assertFailsWith<IllegalArgumentException> {
            document("../Book.dto", "export Book")
        }
        assertFailsWith<IllegalArgumentException> {
            document("Book.txt", "export Book")
        }
        assertFailsWith<IllegalArgumentException> {
            CompilerInputDocument(
                kind = CompilerInputDocumentKind.DTO,
                sourceSet = CompilerSourceSet.MAIN,
                projectName = "bad/project",
                sourceRoot = "src/main/dto",
                relativePath = "Book.dto",
                content = "export Book",
            )
        }
    }

    private fun document(relativePath: String, content: String): CompilerInputDocument {
        return CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "catalog",
            sourceRoot = "src/main/dto",
            relativePath = relativePath,
            content = content,
        )
    }
}
