package org.babyfish.jimmer.compiler.lsi

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind

class LsiReferencedTypesTest {

    @Test
    fun `collects enclosing declaration as referenced type`() {
        val outerId = LsiSymbolId.type("sample.Outer")
        val nested = LsiTypeDeclaration(
            id = LsiSymbolId.type("sample.Outer.Nested"),
            name = "Nested",
            qualifiedName = "sample.Outer.Nested",
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = outerId,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        assertEquals(setOf(outerId), listOf(nested).referencedTypeIds())
    }
}
