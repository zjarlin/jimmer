package org.babyfish.jimmer.compiler.dto

import javax.lang.model.element.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.babyfish.jimmer.compiler.render.apt.AptDtoLoadedStateRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoLoadedStateRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId

class DtoLoadedStatePoetTest {

    @Test
    fun `renders java loaded-state field visibility exactly`() {
        val fixture = fixture()

        assertEquals(
            "private boolean _isValueLoaded;\n",
            fixture.renderJava(Modifier.PRIVATE).toString(),
        )
        assertEquals(
            "protected boolean _isValueLoaded;\n",
            fixture.renderJava(Modifier.PROTECTED).toString(),
        )
        assertEquals(
            "public boolean _isValueLoaded;\n",
            fixture.renderJava(Modifier.PUBLIC).toString(),
        )
    }

    @Test
    fun `renders kotlin loaded-state property exactly`() {
        val fixture = fixture()

        assertEquals(
            """
                @org.babyfish.jimmer.client.ApiIgnore
                @get:com.fasterxml.jackson.`annotation`.JsonIgnore
                var isValueLoaded: kotlin.Boolean = isValueLoaded
            """.trimIndent(),
            fixture.renderKotlin(mutable = true).toString().trimEnd(),
        )
        assertEquals(
            """
                @org.babyfish.jimmer.client.ApiIgnore
                @get:com.fasterxml.jackson.`annotation`.JsonIgnore
                val isValueLoaded: kotlin.Boolean = isValueLoaded
            """.trimIndent(),
            fixture.renderKotlin(mutable = false).toString().trimEnd(),
        )
    }

    @Test
    fun `omits storage for property without loaded-state semantics`() {
        val fixture = fixture(inputModifier = DtoModifier.STATIC)

        assertNull(fixture.renderJava(Modifier.PRIVATE))
        assertNull(fixture.renderKotlin(mutable = true))
    }

    private fun fixture(
        inputModifier: DtoModifier = DtoModifier.DYNAMIC,
    ): Fixture {
        val prop = DtoBaseProp(
            id = PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "value",
            alias = null,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("value", IMMUTABLE_PROP_ID)),
            basePath = "value",
            nextPropId = null,
            tailPropId = PROP_ID,
            baseNullable = true,
            inputModifier = inputModifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(prop.id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return Fixture(
            prop = prop,
            graph = DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(type.id),
                types = listOf(type),
                props = listOf(prop),
            ),
        )
    }

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
    ) {
        fun renderJava(visibility: Modifier) =
            AptDtoLoadedStateRenderer.renderStorageField(prop, graph, visibility)

        fun renderKotlin(mutable: Boolean) =
            KspDtoLoadedStateRenderer.renderStorageProperty(prop, graph, mutable)
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Book.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val TYPE_ID = DtoTypeId("dto#root")
        val PROP_ID = DtoPropId("dto#root/prop:value")
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val IMMUTABLE_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "value")
    }
}
