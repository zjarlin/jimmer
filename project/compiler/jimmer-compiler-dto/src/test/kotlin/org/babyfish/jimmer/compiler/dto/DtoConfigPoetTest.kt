package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.render.apt.AptDtoConfigRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoConfigRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoConfigConstructionKind
import site.addzero.lsi.jimmer.dto.DtoConfigContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractKind
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoConfigTypeRef
import site.addzero.lsi.jimmer.dto.DtoFetchType
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoLimit
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropConfig
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoConfigPoetTest {

    @Test
    fun `keeps platform ordering for recursion and fetch type`() {
        val fixture = fixture(
            config = defaultConfig().copy(
                recursion = DtoConfigTypeRef(RECURSION_TYPE_ID, LOCATION),
                fetchType = DtoFetchType.JOIN_ALWAYS,
            ),
            contracts = listOf(
                DtoConfigContract(
                    propId = PROP_ID,
                    kind = DtoConfigContractKind.RECURSION,
                    implementationTypeId = RECURSION_TYPE_ID,
                    targetEntityTypeId = NODE_TYPE_ID,
                    construction = DtoConfigConstructionKind.ZERO_ARGUMENT_CONSTRUCTOR,
                    dependencyTypeIds = listOf(NODE_TYPE_ID, RECURSION_TYPE_ID).sorted(),
                )
            ),
        )

        val java = fixture.renderJava()
        val kotlin = fixture.renderKotlin()

        assertTrue(java.indexOf("fetchType") < java.indexOf("recursive"), java)
        assertTrue(kotlin.indexOf("recursive") < kotlin.indexOf("fetchType"), kotlin)
        assertEquals(1, Regex("fetchType").findAll(java).count())
        assertEquals(1, Regex("fetchType").findAll(kotlin).count())
    }

    @Test
    fun `renders explicit depth without a recursion contract`() {
        val fixture = fixture(
            config = defaultConfig().copy(depth = 2),
            contracts = emptyList(),
        )

        assertTrue(".depth(2)" in fixture.renderJava())
        assertTrue("depth(2)" in fixture.renderKotlin())
    }

    @Test
    fun `preserves explicit maximum numeric config values`() {
        val fixture = fixture(
            config = defaultConfig().copy(
                limit = DtoLimit(Int.MAX_VALUE, 1),
                batch = Int.MAX_VALUE,
                depth = Int.MAX_VALUE,
            ),
            contracts = emptyList(),
        )

        val java = fixture.renderJava()
        val kotlin = fixture.renderKotlin()
        assertTrue(".limit(2147483647, 1)" in java, java)
        assertTrue(".batch(2147483647)" in java, java)
        assertTrue(".depth(2147483647)" in java, java)
        assertTrue("limit(2147483647, 1)" in kotlin, kotlin)
        assertTrue("batch(2147483647)" in kotlin, kotlin)
        assertTrue("depth(2147483647)" in kotlin, kotlin)
    }

    private fun fixture(
        config: DtoPropConfig,
        contracts: List<DtoConfigContract>,
    ): Fixture {
        val prop = DtoBaseProp(
            id = PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "parent",
            alias = null,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("parent", BASE_PROP_ID)),
            basePath = "parent",
            nextPropId = null,
            tailPropId = PROP_ID,
            baseNullable = true,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = DTO_TYPE_ID,
            enumType = null,
            config = config,
            recursive = true,
            likeOptions = emptySet(),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(
                DtoType(
                    id = DTO_TYPE_ID,
                    baseTypeId = NODE_TYPE_ID,
                    packageName = "demo.dto",
                    name = "NodeView",
                    modifiers = emptySet(),
                    annotations = emptyList(),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = LOCATION,
                    focusedRecursion = true,
                    propIds = listOf(PROP_ID),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                )
            ),
            props = listOf(prop),
        )
        return Fixture(
            prop = prop,
            graph = graph,
            resolution = DtoConfigContractResolution(contracts.sortedBy(DtoConfigContract::kind), emptyList()),
        )
    }

    private fun defaultConfig(): DtoPropConfig = DtoPropConfig(
        predicate = null,
        orderItems = emptyList(),
        filter = null,
        recursion = null,
        fetchType = DtoFetchType.AUTO,
        limit = null,
        batch = null,
        depth = null,
    )

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
        val resolution: DtoConfigContractResolution,
    ) {
        fun renderJava(): String = AptDtoConfigRenderer.render(
            prop = prop,
            graph = graph,
            immutableSchema = ImmutableSchema(emptyList()),
            workspace = WORKSPACE,
            configContractResolution = resolution,
        ).toString()

        fun renderKotlin(): String = KspDtoConfigRenderer.render(
            prop = prop,
            graph = graph,
            immutableSchema = ImmutableSchema(emptyList()),
            workspace = WORKSPACE,
            configContractResolution = resolution,
        ).toString()
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Node.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val NODE_TYPE_ID = LsiSymbolId.type("demo.Node")
        val RECURSION_TYPE_ID = LsiSymbolId.type("demo.NodeRecursion")
        val BASE_PROP_ID = LsiSymbolId.property(NODE_TYPE_ID, "parent")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.NodeView#root")
        val PROP_ID = DtoPropId("demo.dto.NodeView#prop:parent")
        val WORKSPACE = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = RECURSION_TYPE_ID,
                    name = "NodeRecursion",
                    qualifiedName = "demo.NodeRecursion",
                    kind = LsiTypeDeclarationKind.CLASS,
                    modality = LsiModality.FINAL,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, SOURCE),
                )
            ),
        )
    }
}
