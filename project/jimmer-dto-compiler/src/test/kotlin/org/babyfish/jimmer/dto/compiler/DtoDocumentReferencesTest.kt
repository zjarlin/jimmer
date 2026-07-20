package org.babyfish.jimmer.dto.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DtoDocumentReferencesTest {

    @Test
    fun `does not report an unreliable subject for malformed export`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/Book.dto",
            "export",
            "workspace",
            "src/main/dto",
            emptyList(),
            "Book.dto",
        )

        assertEquals(emptyList<DtoDocumentReference>(), DtoDocumentReferences.parse(dtoFile))
    }

    @Test
    fun `does not report an empty subject from dto suffix only file name`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/.dto",
            "View implements java.lang.Runnable {}",
            "workspace",
            "src/main/dto",
            emptyList(),
            ".dto",
        )

        assertEquals(
            listOf("SUPER_TYPE:java.lang.Runnable"),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                "${reference.kind}:${reference.typeSelector.candidateQualifiedNames.joinToString("|")}"
            },
        )
    }

    @Test
    fun `leaves unqualified macro model names to immutable hierarchy resolution`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/model/Book.dto",
            """
                export demo.model.Book
                import demo.shared.ImportedBase

                BookView {
                    #allScalars(BaseEntity, ImportedBase, this)
                }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("model"),
            "Book.dto",
        )

        assertEquals(
            listOf(
                "SUBJECT_TYPE:demo.model.Book:demo.model.Book",
                "MODEL_TYPE:demo.shared.ImportedBase:demo.model.Book",
            ),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                reference.renderWithOwner()
            },
        )
    }

    @Test
    fun `freezes explicit dto and fragment targets without a synthetic subject`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/source/Shared.dto",
            """
                package demo.dto
                import demo.model.{Book, Author}

                BookView for Book { id }
                fragment AuthorProps for Author { id }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("source"),
            "Shared.dto",
        )

        assertEquals(
            listOf(
                "TARGET_TYPE:demo.model.Book:demo.model.Book",
                "TARGET_TYPE:demo.model.Author:demo.model.Author",
            ),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                reference.renderWithOwner()
            },
        )
    }

    @Test
    fun `keeps subject when a fragment uses its implicit target`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/model/Book.dto",
            """
                export demo.model.Book
                import demo.model.Author

                AuthorView for Author { id }
                fragment BookProps { id }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("model"),
            "Book.dto",
        )

        assertEquals(
            listOf(
                "SUBJECT_TYPE:demo.model.Book:demo.model.Book",
                "TARGET_TYPE:demo.model.Author:demo.model.Author",
            ),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                reference.renderWithOwner()
            },
        )
    }

    @Test
    fun `freezes declaration owners for partially invalid targets`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/shared/Shared.dto",
            """
                package demo.dto

                BookView for demo.Book {
                    payload: demo.MissingPayload
                }
                AuthorView for demo.Author { id }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("shared"),
            "Shared.dto",
        )

        assertEquals(
            listOf(
                "TARGET_TYPE:demo.Book:demo.Book",
                "TYPE_USAGE:demo.MissingPayload:demo.Book",
                "TARGET_TYPE:demo.Author:demo.Author",
            ),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                reference.renderWithOwner()
            },
        )
    }

    @Test
    fun `preserves ordered wildcard candidates for model and reusable dto types`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/source/Shared.dto",
            """
                package demo.dto
                import demo.model.*
                import demo.alt.*

                BookView for Book {
                    store -> StoreView
                }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("source"),
            "Shared.dto",
        )

        val references = DtoDocumentReferences.parse(dtoFile)

        assertEquals(
            listOf(
                "TARGET_TYPE:source.Book|demo.model.Book|demo.alt.Book:" +
                    "source.Book|demo.model.Book|demo.alt.Book",
                "REUSABLE_DTO_TYPE:demo.dto.StoreView|demo.model.StoreView|demo.alt.StoreView:" +
                    "source.Book|demo.model.Book|demo.alt.Book",
            ),
            references.map(DtoDocumentReference::renderWithOwner),
        )
        val targetSelector = references.first().typeSelector
        assertEquals("Book", targetSelector.sourceName)
        assertEquals("StoreView", references.last().typeSelector.sourceName)
        assertEquals(
            "source.Book",
            targetSelector.select { qualifiedName ->
                qualifiedName == "source.Book" || qualifiedName == "demo.model.Book"
            }.selectedQualifiedName,
        )
        assertEquals(
            "demo.alt.Book",
            targetSelector.select { qualifiedName -> qualifiedName == "demo.alt.Book" }
                .selectedQualifiedName,
        )
        assertEquals(
            listOf("demo.model.Book", "demo.alt.Book"),
            targetSelector.select { qualifiedName -> qualifiedName.startsWith("demo.") }
                .conflictingQualifiedNames,
        )
    }

    @Test
    fun `preserves source spelling when an imported alias prefixes a nested type`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/source/Shared.dto",
            """
                package demo.dto
                import demo.Root as Book

                NestedView for Book.Nested { id }
            """.trimIndent(),
            "workspace",
            "src/main/dto",
            listOf("source"),
            "Shared.dto",
        )

        val selector = DtoDocumentReferences.parse(dtoFile).single().typeSelector

        assertEquals("Book.Nested", selector.sourceName)
        assertEquals("demo.Root.Nested", selector.fallbackQualifiedName)
        assertEquals(listOf("demo.Root.Nested"), selector.candidateQualifiedNames)
    }

    @Test
    fun `freezes every dto-only type reference without compiler spi`() {
        val dtoFile = DtoFile(
            "/workspace/src/main/dto/catalog/Book.dto",
            """
                export demo.model.Book
                import demo.api.{Marker as ViewMarker}
                import demo.annotations.{Tag, Nested}
                import demo.types.{Payload, Kind}
                import demo.config.{Filter, Recursion}

                @Tag(meta = Nested(Payload::class), kind = Kind.ACTIVE)
                BookView implements ViewMarker, java.lang.Comparable<Payload> {
                    #allScalars(demo.model.BaseBook)
                    !filter(Filter)
                    !recursion(Recursion)
                    association
                    payload: List<Payload>
                    #types {
                        demo.model.SpecialBook { id }
                    }
                }
            """.trimIndent(),
            "catalog",
            "src/main/dto",
            listOf("catalog"),
            "Book.dto",
        )

        val references = DtoDocumentReferences.parse(dtoFile)

        assertEquals(
            listOf(
                "SUBJECT_TYPE:demo.model.Book",
                "ANNOTATION_TYPE:demo.annotations.Tag",
                "ANNOTATION_TYPE:demo.annotations.Nested",
                "TYPE_USAGE:demo.types.Payload",
                "TYPE_USAGE:demo.types.Kind",
                "SUPER_TYPE:demo.api.Marker",
                "SUPER_TYPE:java.lang.Comparable",
                "TYPE_USAGE:demo.types.Payload",
                "MODEL_TYPE:demo.model.BaseBook",
                "CONFIG_IMPLEMENTATION:demo.config.Filter",
                "CONFIG_IMPLEMENTATION:demo.config.Recursion",
                "TYPE_USAGE:demo.types.Payload",
                "MODEL_TYPE:demo.model.SpecialBook",
            ),
            references.map { reference ->
                "${reference.kind}:${reference.typeSelector.candidateQualifiedNames.joinToString("|")}"
            },
        )
        assertEquals(1, references.first().line)
        assertEquals(1, references.first().column)
        assertEquals(
            setOf("demo.model.Book"),
            references.map { reference ->
                reference.ownerTargetSelector?.candidateQualifiedNames?.single()
            }.toSet(),
        )
    }
}

private fun DtoDocumentReference.renderWithOwner(): String {
    val candidates = typeSelector.candidateQualifiedNames.joinToString("|")
    val ownerCandidates = ownerTargetSelector?.candidateQualifiedNames?.joinToString("|")
    return "$kind:$candidates:$ownerCandidates"
}
