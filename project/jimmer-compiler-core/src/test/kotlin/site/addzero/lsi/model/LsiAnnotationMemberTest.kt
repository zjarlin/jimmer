package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiAnnotationMemberTest {

    @Test
    fun `canonicalizes annotation member nullability recursively`() {
        val platformType = LsiArrayType(
            elementType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.lang.String"),
                nullability = LsiNullability.PLATFORM,
            ),
            nullability = LsiNullability.PLATFORM,
        )

        val canonicalType = platformType.toAnnotationMemberType()

        assertEquals(
            LsiArrayType(LsiDeclaredType(LsiSymbolId.type("java.lang.String"))),
            canonicalType,
        )
    }

    @Test
    fun `includes annotation member semantics in snapshot`() {
        val typeId = LsiSymbolId.type("demo.Tags")
        val stringType = LsiDeclaredType(LsiSymbolId.type("kotlin.String"))
        val declaration = LsiTypeDeclaration(
            id = typeId,
            name = "Tags",
            qualifiedName = "demo.Tags",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotationMembers = listOf(
                LsiAnnotationMember(
                    name = "value",
                    type = LsiArrayType(stringType),
                    vararg = true,
                )
            ),
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        val snapshot = LsiWorkspace(declarations = listOf(declaration)).toSemanticSnapshot()

        assertTrue(snapshot.contains("value:array:type:kotlin.String:non_null:non_null:true:false"))
    }

    @Test
    fun `rejects annotation members on ordinary types`() {
        assertFailsWith<IllegalArgumentException> {
            LsiTypeDeclaration(
                id = LsiSymbolId.type("demo.Model"),
                name = "Model",
                qualifiedName = "demo.Model",
                kind = LsiTypeDeclarationKind.CLASS,
                annotationMembers = listOf(
                    LsiAnnotationMember(
                        name = "value",
                        type = LsiDeclaredType(LsiSymbolId.type("kotlin.String")),
                    )
                ),
                origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
            )
        }
    }

    @Test
    fun `requires vararg annotation members to expose array type`() {
        assertFailsWith<IllegalArgumentException> {
            LsiAnnotationMember(
                name = "value",
                type = LsiDeclaredType(LsiSymbolId.type("kotlin.String")),
                vararg = true,
            )
        }
    }
}
