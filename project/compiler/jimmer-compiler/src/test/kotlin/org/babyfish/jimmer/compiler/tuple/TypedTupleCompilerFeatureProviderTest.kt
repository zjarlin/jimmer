package org.babyfish.jimmer.compiler.tuple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

class TypedTupleCompilerFeatureProviderTest {

    @Test
    fun `apt defers recoverable tuple by stable type id`() {
        val result = provider.precompile(context(CompilerPlatform.APT))

        assertEquals(setOf(TYPE_ID), result.unresolvedSymbols)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `apt refreshes all tuple roots after fail fast unresolved result`() {
        val secondTypeId = LsiSymbolId.type("demo.SecondTuple")
        val secondFieldId = LsiSymbolId.field(secondTypeId, "value")
        val multipleTupleWorkspace = LsiWorkspace(
            sources = workspace.sources,
            declarations = workspace.declarations + listOf(
                LsiTypeDeclaration(
                    id = secondTypeId,
                    name = "SecondTuple",
                    qualifiedName = "demo.SecondTuple",
                    kind = LsiTypeDeclarationKind.CLASS,
                    superTypes = listOf(LsiDeclaredType(LsiSymbolId.type("java.lang.Object"))),
                    memberIds = listOf(secondFieldId),
                    annotations = listOf(
                        LsiAnnotation(LsiSymbolId.type("org.babyfish.jimmer.sql.TypedTuple"))
                    ),
                    origin = origin,
                ),
                LsiField(
                    id = secondFieldId,
                    name = "value",
                    ownerId = secondTypeId,
                    type = LsiUnresolvedType("demo.SecondGeneratedDto"),
                    origin = origin,
                ),
            ),
        )

        val result = provider.precompile(
            context(CompilerPlatform.APT, roundWorkspace = multipleTupleWorkspace)
        )

        assertEquals(setOf(TYPE_ID, secondTypeId), result.unresolvedSymbols)
    }

    @Test
    fun `ksp does not defer a valid declaration to force another round`() {
        val result = provider.precompile(context(CompilerPlatform.KSP))

        assertTrue(result.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.tuple.unresolved", result.diagnostics.single().code)
    }

    @Test
    fun `final apt round reports unresolved tuple`() {
        val result = provider.precompile(context(CompilerPlatform.APT, isFinal = true))

        assertTrue(result.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.tuple.unresolved", result.diagnostics.single().code)
    }

    @Test
    fun `tuple feature declares immutable and dto dependencies`() {
        val descriptor = JimmerCompilerFeatureProviders.load()
            .single { candidate -> candidate.descriptor.id == "tuple" }
            .descriptor

        assertEquals(setOf("immutable", "dto"), descriptor.dependsOn)
    }

    private fun context(
        platform: CompilerPlatform,
        isFinal: Boolean = false,
        roundWorkspace: LsiWorkspace = workspace,
    ): JimmerCompilerPrecompileContext {
        return JimmerCompilerPrecompileContext(
            session = CompilerSessionSnapshot("tuple-feature-test", emptyList()),
            round = CompilerRound(
                number = 0,
                workspace = roundWorkspace,
                currentWorkspace = if (isFinal) LsiWorkspace.EMPTY else roundWorkspace,
                currentRootTypeIds = if (isFinal) emptySet() else setOf(TYPE_ID),
                platform = platform,
                isFinal = isFinal,
                inputDocumentSnapshots = emptyList(),
            ),
            collection = JimmerCompilerFeatureCollection(),
            previousState = null,
            dependencyStates = emptyMap(),
        )
    }

    private companion object {
        val TYPE_ID = LsiSymbolId.type("demo.Tuple")
        val FIELD_ID = LsiSymbolId.field(TYPE_ID, "value")
        val source = LsiSource.of("demo/Tuple.java", LsiLanguage.JAVA)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = TYPE_ID,
                    name = "Tuple",
                    qualifiedName = "demo.Tuple",
                    kind = LsiTypeDeclarationKind.CLASS,
                    superTypes = listOf(LsiDeclaredType(LsiSymbolId.type("java.lang.Object"))),
                    memberIds = listOf(FIELD_ID),
                    annotations = listOf(
                        LsiAnnotation(LsiSymbolId.type("org.babyfish.jimmer.sql.TypedTuple"))
                    ),
                    origin = origin,
                ),
                LsiField(
                    id = FIELD_ID,
                    name = "value",
                    ownerId = TYPE_ID,
                    type = LsiUnresolvedType("demo.GeneratedDto"),
                    origin = origin,
                ),
            ),
        )
        val provider = TypedTupleCompilerFeatureProvider()
    }
}
