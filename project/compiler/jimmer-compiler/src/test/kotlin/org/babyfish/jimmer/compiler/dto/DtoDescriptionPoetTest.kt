package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.babyfish.jimmer.compiler.render.apt.AptDtoDescriptionRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDescriptionRenderer
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp

class DtoDescriptionPoetTest {

    @Test
    fun `renders exact Java and Kotlin description values without placeholder escaping`() {
        val graph = graph("Price is $5 at 100%.\nNext line.")
        val type = graph.types.single()
        val prop = graph.props.single()

        assertEquals(
            "@org.babyfish.jimmer.client.Description(\"Price is \$5 at 100%.\\n\"\n" +
                "        + \"Next line.\")",
            AptDtoDescriptionRenderer.render(type).toString(),
        )
        assertEquals(
            "@org.babyfish.jimmer.client.Description(" +
                "value = \"Price is ${KOTLIN_ESCAPED_DOLLAR}5 at 100%.\\nNext line.\")",
            KspDtoDescriptionRenderer.render(type).toString(),
        )
        assertEquals(
            "@org.babyfish.jimmer.client.Description(\"Property is \$10 at 50%.\")",
            AptDtoDescriptionRenderer.render(prop, graph).toString(),
        )
        assertEquals(
            "@org.babyfish.jimmer.client.Description(" +
                "value = \"Property is ${KOTLIN_ESCAPED_DOLLAR}10 at 50%.\")",
            KspDtoDescriptionRenderer.render(prop, graph).toString(),
        )
    }

    @Test
    fun `omits empty descriptions on both renderers`() {
        val graph = graph("")
        val type = graph.types.single().copy(documentation = null)
        val emptyGraph = DtoGraph(
            source = graph.source,
            rootTypeIds = graph.rootTypeIds,
            types = listOf(type),
            props = graph.props.map { prop -> (prop as DtoUserProp).copy(documentation = "") },
        )

        assertNull(AptDtoDescriptionRenderer.render(type))
        assertNull(KspDtoDescriptionRenderer.render(type))
        assertNull(AptDtoDescriptionRenderer.render(emptyGraph.props.single(), emptyGraph))
        assertNull(KspDtoDescriptionRenderer.render(emptyGraph.props.single(), emptyGraph))
    }

    private fun graph(typeDocumentation: String): DtoGraph {
        val prop = DtoUserProp(
            id = PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "price",
            alias = "price",
            nullable = false,
            annotations = emptyList(),
            documentation = "Property is $10 at 50%.",
            aliasLocation = LOCATION,
            type = DtoTypeRef(
                typeName = "String",
                arguments = emptyList(),
                nullable = false,
                location = LOCATION,
            ),
            defaultValueText = null,
        )
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "PriceView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = typeDocumentation,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = listOf(prop).sortedBy(DtoProp::id),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Price.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Price")
        val TYPE_ID = DtoTypeId("demo/Price.dto#PriceView")
        val PROP_ID = DtoPropId("demo/Price.dto#PriceView/prop:00000000:price")
        val KOTLIN_ESCAPED_DOLLAR = "\$" + "{'\$'}"
    }
}
