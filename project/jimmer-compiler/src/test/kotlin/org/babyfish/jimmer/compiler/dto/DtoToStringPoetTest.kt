package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoToStringPoetTest {

    @Test
    fun `renders conditional Java and Kotlin functions without local shadowing`() {
        val graph = graph(
            baseProp("builder"),
            baseProp("separator", DtoModifier.DYNAMIC),
            baseProp("_sp", DtoModifier.FUZZY),
            baseProp("when"),
        )
        val type = graph.types.single()

        val javaSource = LsiJavaPoetRenderer().renderFunction(
            type.toDtoToStringPoetFunction(graph, LsiLanguage.JAVA, "ShadowInput"),
            DTO_TO_STRING_POET_TYPE_NAMES,
        ).toString()
        val kotlinSource = LsiKotlinPoetRenderer().renderFunction(
            type.toDtoToStringPoetFunction(graph, LsiLanguage.KOTLIN, "ShadowInput"),
            DTO_TO_STRING_POET_TYPE_NAMES,
        ).toString()
        assertContains(javaSource, "StringBuilder builder_2 = new StringBuilder();")
        assertContains(javaSource, "String _sp_2 = \"\";")
        assertContains(javaSource, "if (_isSeparatorLoaded)")
        assertContains(javaSource, "if (_sp != null)")
        assertContains(javaSource, ".append(builder);")
        assertContains(javaSource, ".append(separator);")
        assertContains(javaSource, ".append(_sp);")
        assertContains(javaSource, ".append(when);")
        assertPropLabelsInOrder(javaSource)

        assertContains(kotlinSource, "val builder_2 = StringBuilder()")
        assertContains(kotlinSource, "var separator_2 = \"\"")
        assertContains(kotlinSource, "if (isSeparatorLoaded)")
        assertContains(kotlinSource, "if (_sp != null)")
        assertContains(kotlinSource, ".append(builder)")
        assertContains(kotlinSource, ".append(separator)")
        assertContains(kotlinSource, ".append(_sp)")
        assertContains(kotlinSource, ".append(`when`)")
        assertPropLabelsInOrder(kotlinSource)
    }

    @Test
    fun `preserves Kotlin expression function for unconditional properties`() {
        val graph = graph(baseProp("when"), baseProp("value"))
        val source = LsiKotlinPoetRenderer().renderFunction(
            graph.types.single().toDtoToStringPoetFunction(
                graph = graph,
                targetLanguage = LsiLanguage.KOTLIN,
                generatedSimpleNamePath = "PlainView",
            ),
            DTO_TO_STRING_POET_TYPE_NAMES,
        ).toString()

        assertContains(source, "public override fun toString(): kotlin.String = \"PlainView(\" +")
        assertContains(source, "\"when=\" + `when` +")
        assertContains(source, "\", value=\" + `value` +")
    }

    @Test
    fun `rejects unsupported target language`() {
        val graph = graph(baseProp("value"))

        assertFailsWith<IllegalArgumentException> {
            graph.types.single().toDtoToStringPoetFunction(
                graph = graph,
                targetLanguage = LsiLanguage.UNKNOWN,
                generatedSimpleNamePath = "InvalidView",
            )
        }
    }

    private fun assertPropLabelsInOrder(source: String) {
        val offsets = listOf("builder=", "separator=", "_sp=", "when=").map(source::indexOf)
        assertTrue(offsets.all { offset -> offset >= 0 }, source)
        assertTrue(offsets.zipWithNext().all { (left, right) -> left < right }, source)
    }

    private fun graph(vararg visibleProps: DtoBaseProp): DtoGraph {
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "ShadowInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = visibleProps.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = visibleProps.sortedBy(DtoProp::id),
        )
    }

    private fun baseProp(
        name: String,
        inputModifier: DtoModifier = DtoModifier.STATIC,
    ): DtoBaseProp {
        val propId = DtoPropId("dto#$name")
        return DtoBaseProp(
            id = propId,
            ownerTypeId = TYPE_ID,
            name = name,
            alias = name,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = name,
                    propId = LsiSymbolId.property(BASE_TYPE_ID, name),
                ),
            ),
            basePath = name,
            nextPropId = null,
            tailPropId = propId,
            baseNullable = true,
            inputModifier = inputModifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Shadow.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val TYPE_ID = DtoTypeId("demo.dto.ShadowInput")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Sample")
    }
}
