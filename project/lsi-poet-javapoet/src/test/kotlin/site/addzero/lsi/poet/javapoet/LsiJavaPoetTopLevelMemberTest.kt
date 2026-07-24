package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind

class LsiJavaPoetTopLevelMemberTest {

    @Test
    fun `rejects Kotlin top-level member references`() {
        val type = LsiPoetType(
            name = "References",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "render",
                    body = LsiPoetCodeBlock.build {
                        statement {
                            topLevelMember(
                                packageName = "org.babyfish.jimmer.kt",
                                simpleName = "by",
                                extension = false,
                            )
                            text("()")
                        }
                    },
                )
            ),
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "References",
                members = listOf(type),
            ),
            typeNames = emptyList(),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val exception = assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(artifact)
        }

        assertContains(
            exception.message.orEmpty(),
            "Kotlin top-level member reference: org.babyfish.jimmer.kt.by",
        )
    }
}
