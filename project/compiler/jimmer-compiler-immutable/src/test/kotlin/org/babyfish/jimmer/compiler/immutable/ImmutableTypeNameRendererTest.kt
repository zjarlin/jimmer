package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptImmutableTypeNameRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspImmutableTypeNameRenderer
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class ImmutableTypeNameRendererTest {

    @Test
    fun `保留嵌套不可变类型及其 Draft 的精确包边界`() {
        val outerId = LsiSymbolId.type("Demo.API.order")
        val typeId = LsiSymbolId.type("Demo.API.order.item")
        val workspace = LsiWorkspace(
            declarations = listOf(
                declaration(outerId, "order"),
                declaration(typeId, "item", outerId),
            ),
        )
        val type = immutableType(typeId)

        assertEquals(
            com.squareup.javapoet.ClassName.get("Demo.API", "order", "item"),
            AptImmutableTypeNameRenderer.renderSource(type, workspace),
        )
        assertEquals(
            com.squareup.javapoet.ClassName.get("Demo.API", "order", "itemDraft"),
            AptImmutableTypeNameRenderer.renderDraft(type, workspace),
        )
        assertEquals(
            com.squareup.kotlinpoet.ClassName("Demo.API", "order", "item"),
            KspImmutableTypeNameRenderer.renderSource(type, workspace),
        )
        assertEquals(
            com.squareup.kotlinpoet.ClassName("Demo.API", "order", "itemDraft"),
            KspImmutableTypeNameRenderer.renderDraft(type, workspace),
        )
    }

    private fun declaration(
        id: LsiSymbolId,
        name: String,
        enclosingTypeId: LsiSymbolId? = null,
    ): LsiClass {
        return LsiClass(
            id = id,
            name = name,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
            enclosingTypeId = enclosingTypeId,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
    }

    private fun immutableType(id: LsiSymbolId): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.IMMUTABLE,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = emptyList(),
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = false,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = null,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }
}
