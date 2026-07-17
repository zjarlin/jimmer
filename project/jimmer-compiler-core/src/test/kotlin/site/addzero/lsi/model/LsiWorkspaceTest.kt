package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class LsiWorkspaceTest {

    @Test
    fun `merges rounds and replaces newer declarations`() {
        val firstSource = LsiSource.of("demo/First.kt", LsiLanguage.KOTLIN)
        val secondSource = LsiSource.of("demo/Second.kt", LsiLanguage.KOTLIN)
        val typeId = LsiSymbolId.type("demo.Model")
        val first = LsiTypeDeclaration(
            id = typeId,
            name = "Model",
            qualifiedName = "demo.Model",
            kind = LsiTypeDeclarationKind.CLASS,
            documentation = "first",
            origin = LsiOrigin(LsiOriginKind.SOURCE, firstSource),
        )
        val second = first.copy(
            documentation = "second",
            origin = LsiOrigin(LsiOriginKind.SOURCE, secondSource),
        )

        val merged = LsiWorkspace(listOf(firstSource), listOf(first)).merge(
            LsiWorkspace(listOf(secondSource), listOf(second))
        )

        assertEquals(listOf(firstSource, secondSource), merged.sources)
        assertEquals("second", (merged[typeId] as LsiTypeDeclaration).documentation)
    }

    @Test
    fun `projects full declarations over external hierarchy entries`() {
        val externalSource = LsiSource.of(
            "libs/model.jar",
            kind = site.addzero.lsi.core.LsiSourceKind.BINARY,
        )
        val source = LsiSource.of("demo/Model.kt", LsiLanguage.KOTLIN)
        val typeId = LsiSymbolId.type("demo.Model")
        val baseId = LsiSymbolId.type("demo.Base")
        val externalEntry = LsiTypeHierarchyEntry(
            id = typeId,
            qualifiedName = "demo.Model",
            kind = LsiTypeDeclarationKind.CLASS,
            directSuperTypes = listOf(LsiDeclaredType(LsiSymbolId.type("demo.StaleBase"))),
            source = externalSource,
        )
        val declaration = LsiTypeDeclaration(
            id = typeId,
            name = "Model",
            qualifiedName = "demo.Model",
            kind = LsiTypeDeclarationKind.INTERFACE,
            superTypes = listOf(LsiDeclaredType(baseId)),
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )

        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(declaration),
            typeHierarchy = listOf(externalEntry),
        )

        val projected = workspace.typeHierarchyEntry(typeId)
        assertEquals(LsiTypeDeclarationKind.INTERFACE, projected?.kind)
        assertEquals(listOf(LsiDeclaredType(baseId)), projected?.directSuperTypes)
        assertEquals(source, projected?.source)
        assertFalse(requireNotNull(projected).isExternal)
        assertTrue(workspace.typeHierarchyEntry(baseId) == null)
    }

    @Test
    fun `merges external hierarchy entries with newer round precedence`() {
        val typeId = LsiSymbolId.type("demo.External")
        val oldBaseId = LsiSymbolId.type("demo.OldBase")
        val newBaseId = LsiSymbolId.type("demo.NewBase")
        val first = LsiWorkspace(
            typeHierarchy = listOf(
                externalHierarchy(typeId, oldBaseId),
            ),
        )
        val second = LsiWorkspace(
            typeHierarchy = listOf(
                externalHierarchy(typeId, newBaseId),
            ),
        )

        val merged = first.merge(second)

        assertEquals(
            listOf(LsiDeclaredType(newBaseId)),
            merged.typeHierarchyEntry(typeId)?.directSuperTypes,
        )
        assertTrue(requireNotNull(merged.typeHierarchyEntry(typeId)).isExternal)
    }

    private fun externalHierarchy(
        id: LsiSymbolId,
        superTypeId: LsiSymbolId,
    ): LsiTypeHierarchyEntry {
        return LsiTypeHierarchyEntry(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            directSuperTypes = listOf(LsiDeclaredType(superTypeId)),
        )
    }
}
