package site.addzero.lsi.poet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class LsiPoetModelTest {

    @Test
    fun `builds language independent source artifact`() {
        val source = LsiSource.of("demo/Book.kt", LsiLanguage.KOTLIN)
        val bookTypeId = LsiSymbolId.type("demo.Book")
        val body = LsiPoetCodeBlock.build {
            text("return ")
            name("value")
            line()
        }
        val file = LsiPoetFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo.generated",
            fileName = "BookView",
            members = listOf(
                LsiPoetType(
                    name = "BookView",
                    kind = LsiPoetTypeKind.CLASS,
                    members = listOf(
                        LsiPoetProperty(
                            name = "id",
                            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                            mutable = false,
                        ),
                        LsiPoetFunction(
                            name = "book",
                            returnType = LsiDeclaredType(bookTypeId),
                            body = body,
                        ),
                    ),
                )
            ),
        )
        val artifact = LsiPoetArtifact(
            file = file,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(bookTypeId),
            originatingSources = setOf(source),
        )

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals("demo.generated.BookView", artifact.qualifiedFileName)
        val generated = artifact.generatedArtifact("package demo.generated\n")
        assertEquals("demo/generated/BookView.kt", generated.path)
        assertEquals(setOf(bookTypeId), generated.dependencySymbols)
    }

    @Test
    fun `rejects malformed code indentation and artifact origins`() {
        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodeBlock(listOf(LsiPoetCodePart.Unindent))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodeBlock(listOf(LsiPoetCodePart.EndControlFlow))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiPoetCodeBlock(
                listOf(
                    LsiPoetCodePart.NextControlFlow(
                        LsiPoetCodeBlock.build { text("else") }
                    )
                )
            )
        }
        val file = LsiPoetFile(
            language = LsiLanguage.JAVA,
            packageName = "demo",
            fileName = "Book",
            members = listOf(LsiPoetType("Book", LsiPoetTypeKind.CLASS)),
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            LsiPoetArtifact(
                file = file,
                aggregationMode = ArtifactAggregationMode.ISOLATING,
            )
        }
        assertTrue(exception.message.orEmpty().contains("originating symbol"))
    }

    @Test
    fun `builds balanced structural statements and control flow`() {
        val body = LsiPoetCodeBlock.build {
            beginControlFlow { text("if (ready)") }
            statement { text("run()") }
            nextControlFlow { text("else") }
            statement { text("stop()") }
            endControlFlow()
        }

        assertEquals(5, body.parts.size)
        assertTrue(body.parts.first() is LsiPoetCodePart.BeginControlFlow)
        assertTrue(body.parts.last() is LsiPoetCodePart.EndControlFlow)
    }

    @Test
    fun `rejects source extension and non trailing vararg`() {
        assertFailsWith<IllegalArgumentException> {
            LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo",
                fileName = "Book.java",
                members = listOf(LsiPoetType("Book", LsiPoetTypeKind.CLASS)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiPoetFunction(
                name = "consume",
                parameters = listOf(
                    LsiPoetParameter(
                        name = "values",
                        type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                        modifiers = setOf(LsiPoetModifier.VARARG),
                    ),
                    LsiPoetParameter(
                        name = "tail",
                        type = LsiPrimitiveType(LsiPrimitiveKind.INT),
                    ),
                ),
            )
        }
    }
}
