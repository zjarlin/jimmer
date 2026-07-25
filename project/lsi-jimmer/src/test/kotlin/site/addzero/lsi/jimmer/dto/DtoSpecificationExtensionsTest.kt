package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoSpecificationExtensionsTest {

    @Test
    fun `resolves like and notLike arguments in predicate order`() {
        val graph = graph(specification = true)
        val props = graph.props.associateBy(DtoProp::name)

        assertEquals(
            listOf(true, true, false),
            (props.getValue("name") as DtoBaseProp).specificationLikeOptionArguments(graph),
        )
        assertEquals(
            listOf(false, false, false),
            (props.getValue("excludedName") as DtoBaseProp).specificationLikeOptionArguments(graph),
        )
        assertNull(
            (props.getValue("id") as DtoBaseProp).specificationLikeOptionArguments(graph),
        )
    }

    @Test
    fun `rejects properties outside a specification`() {
        val graph = graph(specification = false)
        val prop = graph.props.single { prop -> prop.name == "name" } as DtoBaseProp

        assertFailsWith<IllegalArgumentException> {
            prop.specificationLikeOptionArguments(graph)
        }
    }

    private fun graph(specification: Boolean): DtoGraph {
        val props = listOf(
            prop("id", "eq"),
            prop(
                name = "name",
                functionName = "like",
                likeOptions = setOf(DtoLikeOption.INSENSITIVE, DtoLikeOption.MATCH_START),
            ),
            prop("excludedName", "notLike"),
        )
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookSpecification",
            modifiers = if (specification) setOf(DtoModifier.SPECIFICATION) else emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = props.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(type.id),
            types = listOf(type),
            props = props.sortedBy(DtoProp::id),
        )
    }

    private fun prop(
        name: String,
        functionName: String,
        likeOptions: Set<DtoLikeOption> = emptySet(),
    ): DtoBaseProp {
        val id = DtoPropId("${DTO_TYPE_ID.value}#$name")
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = null,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(name, LsiSymbolId.property(BASE_TYPE_ID, name)),
            ),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = true,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = likeOptions,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookSpecification")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
    }
}
