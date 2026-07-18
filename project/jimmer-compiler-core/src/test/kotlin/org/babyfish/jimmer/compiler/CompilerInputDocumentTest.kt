package org.babyfish.jimmer.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode

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

    @Test
    fun `binds stable type references to frozen document source`() {
        val document = document("book/Book.dto", "export Book")
        val annotation = CompilerInputDocumentReference(
            typeId = LsiSymbolId.type("demo.Tag"),
            kind = CompilerInputDocumentReferenceKind.ANNOTATION_TYPE,
            location = LsiLocation(document.source, LsiPosition(2, 1)),
        )
        val subject = CompilerInputDocumentReference(
            typeId = LsiSymbolId.type("demo.Book"),
            kind = CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
            location = LsiLocation(document.source, LsiPosition(1, 1)),
        )
        val usage = CompilerInputDocumentReference(
            typeId = LsiSymbolId.type("demo.Payload"),
            kind = CompilerInputDocumentReferenceKind.TYPE_USAGE,
            location = LsiLocation(document.source, LsiPosition(3, 1)),
        )

        val snapshot = CompilerInputDocumentSnapshot(document, listOf(subject, annotation, usage))

        assertEquals(
            setOf(
                LsiSymbolId.type("demo.Book"),
                LsiSymbolId.type("demo.Tag"),
                LsiSymbolId.type("demo.Payload"),
            ),
            snapshot.referencedTypeIds,
        )
        assertEquals(
            listOf(
                LsiTypeSeed(LsiSymbolId.type("demo.Book"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("demo.Payload"), LsiTypeSeedMode.HEADER),
                LsiTypeSeed(LsiSymbolId.type("demo.Tag"), LsiTypeSeedMode.FULL_DECLARATION),
            ),
            snapshot.typeSeeds,
        )
        assertFailsWith<IllegalArgumentException> {
            CompilerInputDocumentSnapshot(document, listOf(annotation, subject))
        }
        assertFailsWith<IllegalArgumentException> {
            CompilerInputDocumentSnapshot(
                document,
                listOf(
                    subject.copy(
                        location = LsiLocation(
                            LsiSource.of("other.dto"),
                            LsiPosition(1, 1),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `defensively freezes document references`() {
        val document = document("book/Book.dto", "export Book")
        val mutableReferences = mutableListOf(
            CompilerInputDocumentReference(
                typeId = LsiSymbolId.type("demo.Book"),
                kind = CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
                location = LsiLocation(document.source, LsiPosition(1, 1)),
            )
        )
        val snapshot = CompilerInputDocumentSnapshot(document, mutableReferences)

        mutableReferences.clear()

        assertEquals(1, snapshot.references.size)
        assertEquals(setOf(LsiSymbolId.type("demo.Book")), snapshot.referencedTypeIds)
        assertEquals(
            listOf(LsiTypeSeed(LsiSymbolId.type("demo.Book"), LsiTypeSeedMode.FULL_DECLARATION)),
            snapshot.typeSeeds,
        )
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
