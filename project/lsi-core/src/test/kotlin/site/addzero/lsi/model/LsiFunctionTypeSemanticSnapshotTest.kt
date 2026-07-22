package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertContains
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiFunctionTypeSemanticSnapshotTest {

    @Test
    fun `semantic snapshot preserves function semantics and nested annotations`() {
        val ownerId = LsiSymbolId.type("sample.Service")
        val propertyId = LsiSymbolId.property(ownerId, "handler")
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            receiverType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("sample.Scope"),
                nullability = LsiNullability.PLATFORM,
            ),
            parameterTypes = listOf(
                LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.String"),
                    annotations = listOf(LsiAnnotation(LsiSymbolId.type("sample.ParameterMarker"))),
                ),
            ),
            suspending = true,
            nullability = LsiNullability.NULLABLE,
            annotations = listOf(LsiAnnotation(LsiSymbolId.type("sample.FunctionMarker"))),
        )
        val origin = LsiOrigin(LsiOriginKind.SYNTHETIC)
        val workspace = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = ownerId,
                    name = "Service",
                    qualifiedName = "sample.Service",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    memberIds = listOf(propertyId),
                    origin = origin,
                ),
                LsiProperty(
                    id = propertyId,
                    name = "handler",
                    ownerId = ownerId,
                    type = functionType,
                    origin = origin,
                ),
            ),
        )

        assertContains(
            workspace.toSemanticSnapshot(),
            "function:suspend:receiver:type:sample.Scope:non_null:" +
                "parameters:[type:java.lang.String:non_null@[type:sample.ParameterMarker()]]:" +
                "return:primitive:void:non_null:nullable@[type:sample.FunctionMarker()]",
        )
    }
}
