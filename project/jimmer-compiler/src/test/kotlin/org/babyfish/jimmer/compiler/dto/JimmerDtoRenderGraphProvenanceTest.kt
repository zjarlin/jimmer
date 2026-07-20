package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource

class JimmerDtoRenderGraphProvenanceTest {

    @Test
    fun `accepts fragment property locations and collects every originating source`() {
        val documentSource = LsiSource.of("demo/src/main/dto/views/BookViews.dto")
        val fragmentSource = LsiSource.of("demo/src/main/dto/shared/BookFragments.dto")
        val typeId = JimmerDtoTypeId("${documentSource.path}#root")
        val propId = JimmerDtoPropId("${documentSource.path}#root/prop:payload")
        val type = JimmerDtoType(
            id = typeId,
            baseTypeId = null,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = location(documentSource, 3, 1),
            focusedRecursion = false,
            propIds = listOf(propId),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val prop = JimmerDtoUserProp(
            id = propId,
            ownerTypeId = typeId,
            name = "payload",
            alias = "payload",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = location(fragmentSource, 4, 5),
            type = JimmerDtoTypeRef(
                typeName = "java.lang.String",
                arguments = emptyList(),
                nullable = false,
                location = location(fragmentSource, 4, 14),
            ),
            defaultValueText = null,
        )

        val graph = JimmerDtoRenderGraph(
            source = documentSource,
            rootTypeIds = listOf(typeId),
            types = listOf(type),
            props = listOf(prop),
        )

        assertEquals(sortedSetOf(documentSource, fragmentSource), graph.originatingSources)
    }

    private fun location(source: LsiSource, line: Int, column: Int): LsiLocation {
        return LsiLocation(source, LsiPosition(line, column))
    }
}
