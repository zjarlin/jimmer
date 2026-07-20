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
            typeSelector = selector("demo.Tag"),
            kind = CompilerInputDocumentReferenceKind.ANNOTATION_TYPE,
            ownerTargetSelector = null,
            location = LsiLocation(document.source, LsiPosition(2, 1)),
        )
        val subject = CompilerInputDocumentReference(
            typeSelector = selector("demo.Book"),
            kind = CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
            ownerTargetSelector = selector("demo.Book"),
            location = LsiLocation(document.source, LsiPosition(1, 1)),
        )
        val usage = CompilerInputDocumentReference(
            typeSelector = selector("demo.Payload"),
            kind = CompilerInputDocumentReferenceKind.TYPE_USAGE,
            ownerTargetSelector = null,
            location = LsiLocation(document.source, LsiPosition(3, 1)),
        )
        val config = CompilerInputDocumentReference(
            typeSelector = selector("demo.Filter"),
            kind = CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION,
            ownerTargetSelector = null,
            location = LsiLocation(document.source, LsiPosition(4, 1)),
        )

        val snapshot = CompilerInputDocumentSnapshot(document, listOf(subject, annotation, usage, config))

        assertEquals(
            setOf(
                LsiSymbolId.type("demo.Book"),
                LsiSymbolId.type("demo.Tag"),
                LsiSymbolId.type("demo.Payload"),
                LsiSymbolId.type("demo.Filter"),
            ),
            snapshot.referencedTypeIds,
        )
        assertEquals(
            listOf(
                LsiTypeSeed(LsiSymbolId.type("demo.Book"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("demo.Filter"), LsiTypeSeedMode.FULL_DECLARATION),
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
                typeSelector = selector("demo.Book"),
                kind = CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
                ownerTargetSelector = selector("demo.Book"),
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

    @Test
    fun `uses full declaration seed for explicit dto target`() {
        val document = document("shared/Shared.dto", "BookView for demo.Book {}")
        val bookTypeId = LsiSymbolId.type("demo.Book")
        val snapshot = CompilerInputDocumentSnapshot(
            document,
            listOf(
                CompilerInputDocumentReference(
                    typeSelector = CompilerInputDocumentTypeSelector("demo.Book", bookTypeId),
                    kind = CompilerInputDocumentReferenceKind.TARGET_TYPE,
                    ownerTargetSelector = CompilerInputDocumentTypeSelector("demo.Book", bookTypeId),
                    location = LsiLocation(document.source, LsiPosition(1, 14)),
                )
            ),
        )

        assertEquals(
            listOf(LsiTypeSeed(bookTypeId, LsiTypeSeedMode.FULL_DECLARATION)),
            snapshot.typeSeeds,
        )
    }

    @Test
    fun `seeds every wildcard candidate and promotes owner targets`() {
        val document = document(
            "shared/Shared.dto",
            "BookView for Book { payload: Payload, store -> StoreView }",
        )
        val ownerSelector = selector("shared.Book", "demo.Book", "other.Book")
        assertEquals("Book", ownerSelector.sourceName)
        val usage = CompilerInputDocumentReference(
            typeSelector = selector("shared.Payload", "demo.Payload"),
            kind = CompilerInputDocumentReferenceKind.TYPE_USAGE,
            ownerTargetSelector = ownerSelector,
            location = LsiLocation(document.source, LsiPosition(1, 26)),
        )
        val reusableDto = CompilerInputDocumentReference(
            typeSelector = selector("shared.dto.StoreView", "demo.dto.StoreView"),
            kind = CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE,
            ownerTargetSelector = ownerSelector,
            location = LsiLocation(document.source, LsiPosition(1, 44)),
        )

        val snapshot = CompilerInputDocumentSnapshot(document, listOf(usage, reusableDto))

        assertEquals(
            setOf(
                LsiSymbolId.type("shared.Book"),
                LsiSymbolId.type("demo.Book"),
                LsiSymbolId.type("other.Book"),
                LsiSymbolId.type("shared.Payload"),
                LsiSymbolId.type("demo.Payload"),
                LsiSymbolId.type("shared.dto.StoreView"),
                LsiSymbolId.type("demo.dto.StoreView"),
            ),
            snapshot.referencedTypeIds,
        )
        assertEquals(
            listOf(
                LsiTypeSeed(LsiSymbolId.type("demo.Book"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("demo.Payload"), LsiTypeSeedMode.HEADER),
                LsiTypeSeed(LsiSymbolId.type("demo.dto.StoreView"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("other.Book"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("shared.Book"), LsiTypeSeedMode.FULL_DECLARATION),
                LsiTypeSeed(LsiSymbolId.type("shared.Payload"), LsiTypeSeedMode.HEADER),
                LsiTypeSeed(LsiSymbolId.type("shared.dto.StoreView"), LsiTypeSeedMode.FULL_DECLARATION),
            ),
            snapshot.typeSeeds,
        )
        assertEquals(
            LsiSymbolId.type("demo.Book"),
            ownerSelector.select { typeId -> typeId == LsiSymbolId.type("demo.Book") }.selectedTypeId,
        )
        assertEquals(
            listOf(LsiSymbolId.type("demo.Book"), LsiSymbolId.type("other.Book")),
            ownerSelector.select { typeId ->
                typeId != LsiSymbolId.type("shared.Book") && typeId.value.endsWith(".Book")
            }
                .conflictingTypeIds,
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

    private fun selector(
        fallbackQualifiedName: String,
        vararg wildcardQualifiedNames: String,
    ): CompilerInputDocumentTypeSelector {
        return CompilerInputDocumentTypeSelector(
            sourceName = fallbackQualifiedName.substringAfterLast('.'),
            fallbackTypeId = LsiSymbolId.type(fallbackQualifiedName),
            wildcardTypeIds = wildcardQualifiedNames.map(LsiSymbolId::type),
        )
    }
}
