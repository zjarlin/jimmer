package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType

class DtoInterfaceContractModelTest {

    @Test
    fun `resolution requires stable unique DTO type ids`() {
        val first = contract(DtoTypeId("demo#b"))
        val second = contract(DtoTypeId("demo#a"))

        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceContractResolution(listOf(first, second), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceContractResolution(listOf(second, second), emptyList())
        }
    }

    @Test
    fun `contract requires unique super interfaces and stable property names`() {
        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceContract(
                typeId = DTO_TYPE_ID,
                superInterfaceTypeIds = listOf(DECLARING_TYPE_ID, DECLARING_TYPE_ID),
                props = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceContract(
                typeId = DTO_TYPE_ID,
                superInterfaceTypeIds = emptyList(),
                props = listOf(prop("z"), prop("a")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceContract(
                typeId = DTO_TYPE_ID,
                superInterfaceTypeIds = emptyList(),
                props = listOf(prop("value"), prop("value")),
            )
        }
    }

    @Test
    fun `property contract requires coherent accessors and mutability`() {
        assertFailsWith<IllegalArgumentException> {
            DtoInterfacePropContract(
                declaringTypeId = DECLARING_TYPE_ID,
                name = "value",
                type = STRING_TYPE,
                mutable = false,
                getter = null,
                setter = null,
                origin = ORIGIN,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoInterfacePropContract(
                declaringTypeId = DECLARING_TYPE_ID,
                name = "value",
                type = STRING_TYPE,
                mutable = true,
                getter = accessor("getValue"),
                setter = null,
                origin = ORIGIN,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoInterfaceAccessorContract(
                declarationId = LsiSymbolId.function(DECLARING_TYPE_ID, "getValue", emptyList()),
                name = " ",
                origin = ORIGIN,
            )
        }
    }

    private fun contract(typeId: DtoTypeId): DtoInterfaceContract {
        return DtoInterfaceContract(typeId, emptyList(), emptyList())
    }

    private fun prop(name: String): DtoInterfacePropContract {
        return DtoInterfacePropContract(
            declaringTypeId = DECLARING_TYPE_ID,
            name = name,
            type = STRING_TYPE,
            mutable = false,
            getter = accessor("get${name.replaceFirstChar(Char::uppercaseChar)}"),
            setter = null,
            origin = ORIGIN,
        )
    }

    private fun accessor(name: String): DtoInterfaceAccessorContract {
        return DtoInterfaceAccessorContract(
            declarationId = LsiSymbolId.function(DECLARING_TYPE_ID, name, emptyList()),
            name = name,
            origin = ORIGIN,
        )
    }

    private companion object {
        val DECLARING_TYPE_ID = LsiSymbolId.type("demo.Contract")
        val DTO_TYPE_ID = DtoTypeId("demo#root")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val ORIGIN = LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("demo/Contract.kt"),
        )
    }
}
