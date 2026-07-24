package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType

class DtoInterfaceContractExtensionsTest {

    @Test
    fun `derives renderer names from the frozen interface contract`() {
        val contract = DtoInterfaceContract(
            typeId = TYPE_ID,
            superInterfaceTypeIds = listOf(CONTRACT_TYPE_ID),
            props = listOf(
                prop("active", getter = "isActive"),
                prop("value", getter = "getValue", setter = "setValue"),
            ),
        )
        val resolution = DtoInterfaceContractResolution(listOf(contract), emptyList())

        assertSame(contract, resolution.contractFor(dtoType(TYPE_ID)))
        assertEquals(linkedSetOf("active", "value"), contract.requiredPropNames())
        assertEquals(
            linkedSetOf("isActive", "getValue", "setValue"),
            contract.requiredAccessorNames(),
        )
    }

    @Test
    fun `rejects a DTO type outside the frozen interface resolution`() {
        val resolution = DtoInterfaceContractResolution(
            contracts = listOf(
                DtoInterfaceContract(TYPE_ID, emptyList(), emptyList()),
            ),
            diagnostics = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            resolution.contractFor(dtoType(DtoTypeId("demo/Book.dto#MissingView")))
        }
    }

    private fun prop(
        name: String,
        getter: String,
        setter: String? = null,
    ): DtoInterfacePropContract {
        return DtoInterfacePropContract(
            declaringTypeId = CONTRACT_TYPE_ID,
            name = name,
            type = STRING_TYPE,
            mutable = setter != null,
            getter = accessor(getter),
            setter = setter?.let(::accessor),
            origin = ORIGIN,
        )
    }

    private fun accessor(name: String): DtoInterfaceAccessorContract {
        return DtoInterfaceAccessorContract(
            declarationId = LsiSymbolId.function(CONTRACT_TYPE_ID, name, emptyList()),
            name = name,
            origin = ORIGIN,
        )
    }

    private fun dtoType(typeId: DtoTypeId): DtoType {
        return DtoType(
            id = typeId,
            baseTypeId = null,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private companion object {
        val TYPE_ID = DtoTypeId("demo/Book.dto#BookView")
        val CONTRACT_TYPE_ID = LsiSymbolId.type("demo.Contract")
        val SOURCE = LsiSource.of("demo/Contract.kt")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, source = SOURCE)
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
    }
}
