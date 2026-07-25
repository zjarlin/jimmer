package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoDocumentationExtensionsTest {

    @Test
    fun `preserves exact description text and ignores empty values`() {
        val graph = graph(typeDocumentation = "Price is $5 at 100%.\nNext line.")
        val type = graph.types.single()
        val title = type.prop(graph, "title")
        val empty = type.prop(graph, "empty")

        assertEquals("Price is $5 at 100%.\nNext line.", type.descriptionAnnotationValueOrNull())
        assertEquals("Title costs $5 at 100%.", title.descriptionAnnotationValueOrNull(graph))
        assertNull(empty.descriptionAnnotationValueOrNull(graph))
        assertNull(type.copy(documentation = "").descriptionAnnotationValueOrNull())
    }

    @Test
    fun `keeps visible path descriptions and rejects hidden path properties`() {
        val graph = graph(typeDocumentation = null)
        val type = graph.types.single()
        val path = type.prop(graph, "storeName")
        val hiddenTail = graph.propsById.getValue(PATH_TAIL_PROP_ID)

        assertEquals(
            "Visible path documentation.",
            path.descriptionAnnotationValueOrNull(graph),
        )
        assertFailsWith<IllegalArgumentException> {
            hiddenTail.descriptionAnnotationValueOrNull(graph)
        }
    }

    private fun graph(typeDocumentation: String?): DtoGraph {
        val title = baseProp(
            id = TITLE_PROP_ID,
            name = "title",
            documentation = "Title costs $5 at 100%.",
        )
        val empty = baseProp(
            id = EMPTY_PROP_ID,
            name = "empty",
            documentation = "",
        )
        val pathTail = baseProp(
            id = PATH_TAIL_PROP_ID,
            name = "name",
            documentation = "Hidden path documentation.",
        )
        val path = baseProp(
            id = PATH_PROP_ID,
            name = "storeName",
            documentation = "Visible path documentation.",
            nextPropId = PATH_TAIL_PROP_ID,
            tailPropId = PATH_TAIL_PROP_ID,
        )
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = typeDocumentation,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(TITLE_PROP_ID, EMPTY_PROP_ID, PATH_PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = listOf(title, empty, pathTail, path).sortedBy(DtoProp::id),
        )
    }

    private fun baseProp(
        id: DtoPropId,
        name: String,
        documentation: String?,
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = TYPE_ID,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = documentation,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = name,
                    propId = LsiSymbolId.property(BASE_TYPE_ID, name),
                ),
            ),
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val TYPE_ID = DtoTypeId("demo/Book.dto#BookView")
        val TITLE_PROP_ID = DtoPropId("demo/Book.dto#BookView/prop:00000000:title")
        val EMPTY_PROP_ID = DtoPropId("demo/Book.dto#BookView/prop:00000001:empty")
        val PATH_PROP_ID = DtoPropId("demo/Book.dto#BookView/prop:00000002:storeName")
        val PATH_TAIL_PROP_ID = DtoPropId("demo/Book.dto#BookView/prop:00000003:name")
    }
}
