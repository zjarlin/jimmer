package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
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
            sourceDocumentation = "source-first",
            origin = LsiOrigin(LsiOriginKind.SOURCE, firstSource),
        )
        val second = first.copy(
            documentation = "second",
            sourceDocumentation = "source-second",
            origin = LsiOrigin(LsiOriginKind.SOURCE, secondSource),
        )

        val merged = LsiWorkspace(listOf(firstSource), listOf(first)).merge(
            LsiWorkspace(listOf(secondSource), listOf(second)),
            refreshedTypeIds = setOf(typeId),
        )

        assertEquals(listOf(firstSource, secondSource), merged.sources)
        assertEquals("second", (merged[typeId] as LsiTypeDeclaration).documentation)
        assertEquals("source-second", merged[typeId]?.sourceDocumentation)
    }

    @Test
    fun `refreshing a type removes obsolete callable signatures`() {
        val source = LsiSource.of(
            "demo/Model.java",
            LsiLanguage.JAVA,
            LsiSourceKind.GENERATED,
        )
        val origin = LsiOrigin(LsiOriginKind.GENERATED, source)
        val typeId = LsiSymbolId.type("demo.Model")
        val oldFunctionId = LsiSymbolId.function(typeId, "consume", listOf("unresolved:Value"))
        val newFunctionId = LsiSymbolId.function(typeId, "consume", listOf("type:demo.Value"))
        val oldFunction = LsiFunction(
            id = oldFunctionId,
            name = "consume",
            ownerId = typeId,
            returnType = LsiUnresolvedType("Value"),
            origin = origin,
        )
        val newFunction = oldFunction.copy(
            id = newFunctionId,
            returnType = LsiDeclaredType(LsiSymbolId.type("demo.Value")),
        )
        val oldType = LsiTypeDeclaration(
            id = typeId,
            name = "Model",
            qualifiedName = "demo.Model",
            kind = LsiTypeDeclarationKind.CLASS,
            memberIds = listOf(oldFunctionId),
            origin = origin,
        )
        val newType = oldType.copy(memberIds = listOf(newFunctionId))

        val merged = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(oldType, oldFunction),
        ).merge(
            LsiWorkspace(
                sources = listOf(source),
                declarations = listOf(newType, newFunction),
            ),
            refreshedTypeIds = setOf(typeId),
        )

        assertFalse(merged.contains(oldFunctionId))
        assertEquals(newFunction, merged[newFunctionId])
        assertEquals(listOf(newFunctionId), (merged[typeId] as LsiTypeDeclaration).memberIds)
    }

    @Test
    fun `refreshing an outer type removes deleted nested declarations and scopes`() {
        val source = LsiSource.of("demo/Outer.kt", LsiLanguage.KOTLIN)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val outerTypeId = LsiSymbolId.type("demo.Outer")
        val nestedTypeId = LsiSymbolId.type("demo.Outer.Inner")
        val nestedPropId = LsiSymbolId.property(nestedTypeId, "value")
        val oldOuter = LsiTypeDeclaration(
            id = outerTypeId,
            name = "Outer",
            qualifiedName = "demo.Outer",
            kind = LsiTypeDeclarationKind.CLASS,
            origin = origin,
        )
        val oldNested = LsiTypeDeclaration(
            id = nestedTypeId,
            name = "Inner",
            qualifiedName = "demo.Outer.Inner",
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = outerTypeId,
            memberIds = listOf(nestedPropId),
            origin = origin,
        )
        val oldNestedProp = LsiProperty(
            id = nestedPropId,
            name = "value",
            ownerId = nestedTypeId,
            getterName = "getValue",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            origin = origin,
        )
        val oldScope = LsiFileAnnotationScope(
            packageName = "demo",
            logicalPath = "demo/Outer.kt",
            annotations = listOf(annotation("demo.Old")),
            origin = origin,
        )
        val newOuter = oldOuter.copy(documentation = "refreshed")

        val merged = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(oldOuter, oldNested, oldNestedProp),
            annotationScopes = listOf(oldScope),
        ).merge(
            LsiWorkspace(
                sources = listOf(source),
                declarations = listOf(newOuter),
            ),
            refreshedTypeIds = setOf(outerTypeId),
        )

        assertEquals(newOuter, merged[outerTypeId])
        assertFalse(merged.contains(nestedTypeId))
        assertFalse(merged.contains(nestedPropId))
        assertFalse(merged.contains(oldScope.id))
        assertTrue(merged.typeHierarchyEntry(nestedTypeId) == null)
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

        val merged = first.merge(second, refreshedTypeIds = emptySet())

        assertEquals(
            listOf(LsiDeclaredType(newBaseId)),
            merged.typeHierarchyEntry(typeId)?.directSuperTypes,
        )
        assertTrue(requireNotNull(merged.typeHierarchyEntry(typeId)).isExternal)
    }

    @Test
    fun `merges annotation scopes and resolves their originating sources`() {
        val source = LsiSource.of("demo/package-info.java", LsiLanguage.JAVA)
        val packageScopeId = LsiSymbolId.packageScope("demo")
        val generatedFileScopeId = LsiSymbolId.fileScope("demo", "generated/Generated.kt")
        val oldPackageScope = LsiPackageAnnotationScope(
            packageName = "demo",
            annotations = listOf(annotation("demo.Old")),
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
        val newPackageScope = oldPackageScope.copy(annotations = listOf(annotation("demo.New")))
        val generatedFileScope = LsiFileAnnotationScope(
            packageName = "demo",
            logicalPath = "generated/Generated.kt",
            annotations = listOf(annotation("demo.FileMarker")),
            origin = LsiOrigin(
                kind = LsiOriginKind.GENERATED,
                originatingSymbols = setOf(packageScopeId),
            ),
        )

        val merged = LsiWorkspace(annotationScopes = listOf(oldPackageScope)).merge(
            LsiWorkspace(annotationScopes = listOf(generatedFileScope, newPackageScope)),
            refreshedTypeIds = emptySet(),
        )

        assertEquals(newPackageScope, merged.annotationScope(packageScopeId))
        assertEquals(generatedFileScope, merged.annotationScope(generatedFileScopeId))
        assertTrue(merged.contains(packageScopeId))
        assertTrue(merged.contains(generatedFileScopeId))
        assertEquals(setOf(source), merged.originatingSources(setOf(generatedFileScopeId)))
    }

    @Test
    fun `rejects duplicate annotation scope ids`() {
        val first = LsiPackageAnnotationScope(
            packageName = "demo",
            annotations = listOf(annotation("demo.First")),
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
        val second = first.copy(annotations = listOf(annotation("demo.Second")))

        assertFailsWith<IllegalArgumentException> {
            LsiWorkspace(annotationScopes = listOf(first, second))
        }
    }

    @Test
    fun `rejects blank file annotation scope logical paths`() {
        assertFailsWith<IllegalArgumentException> {
            LsiFileAnnotationScope(
                packageName = "demo",
                logicalPath = " ",
                origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFileAnnotationScope(
                packageName = "demo",
                logicalPath = "/workspace/Model.kt",
                origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFileAnnotationScope(
                packageName = "demo",
                logicalPath = "generated/../Model.kt",
                origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFileAnnotationScope(
                packageName = "demo",
                logicalPath = "C:/workspace/Model.kt",
                origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
            )
        }
    }

    @Test
    fun `same package file scopes can use distinct logical paths`() {
        val first = LsiFileAnnotationScope(
            packageName = "demo",
            logicalPath = "alpha/Model.kt",
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
        val second = LsiFileAnnotationScope(
            packageName = "demo",
            logicalPath = "beta/Model.kt",
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        val workspace = LsiWorkspace(annotationScopes = listOf(first, second))

        assertEquals(listOf(first.id, second.id).sorted(), workspace.annotationScopes.map(LsiAnnotationScope::id))
    }

    private fun annotation(qualifiedName: String): LsiAnnotation {
        return LsiAnnotation(LsiSymbolId.type(qualifiedName))
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
