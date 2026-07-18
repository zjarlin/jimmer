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
                "${reference.kind}:${reference.qualifiedName}"
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
                "SUBJECT_TYPE:demo.model.Book",
                "MODEL_TYPE:demo.shared.ImportedBase",
            ),
            DtoDocumentReferences.parse(dtoFile).map { reference ->
                "${reference.kind}:${reference.qualifiedName}"
            },
        )
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
                "TYPE_USAGE:demo.config.Filter",
                "TYPE_USAGE:demo.config.Recursion",
                "TYPE_USAGE:demo.types.Payload",
                "MODEL_TYPE:demo.model.SpecialBook",
            ),
            references.map { reference -> "${reference.kind}:${reference.qualifiedName}" },
        )
        assertEquals(1, references.first().line)
        assertEquals(1, references.first().column)
    }
}
