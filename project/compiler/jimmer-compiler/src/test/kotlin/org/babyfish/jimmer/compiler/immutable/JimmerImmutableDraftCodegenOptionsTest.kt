package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableDraftCodegenOptionsTest {

    @Test
    fun `detects jackson family and freezes excluded annotation prefixes`() {
        val workspace = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = JACKSON_3_OBJECT_MAPPER,
                    name = "ObjectMapper",
                    qualifiedName = "tools.jackson.databind.ObjectMapper",
                    kind = LsiTypeDeclarationKind.CLASS,
                    origin = BINARY_ORIGIN,
                )
            )
        )

        assertEquals(
            JimmerImmutableDraftCodegenOptions(
                jacksonFamily = JacksonFamily.JACKSON_3,
                excludedUserAnnotationPrefixes = listOf("demo.internal", "demo.generated"),
            ),
            JimmerImmutableDraftCodegenOptions.from(
                compilerOptions = mapOf(
                    "jimmer.excludedUserAnnotationPrefixes" to
                        " demo.internal, demo.generated;demo.internal ",
                ),
                workspace = workspace,
            ),
        )
        assertEquals(
            JacksonFamily.JACKSON_2,
            JimmerImmutableDraftCodegenOptions.from(
                compilerOptions = mapOf("jimmer.jackson3" to "false"),
                workspace = workspace,
            ).jacksonFamily,
        )
    }

    private companion object {
        val JACKSON_3_OBJECT_MAPPER = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
        val BINARY_ORIGIN = LsiOrigin(LsiOriginKind.BINARY)
    }
}
