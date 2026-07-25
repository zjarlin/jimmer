package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoModelContractTest {

    @Test
    fun `rejects blank stable ids`() {
        assertFailsWith<IllegalArgumentException> { DtoTypeId(" ") }
        assertFailsWith<IllegalArgumentException> { DtoPropId("") }
    }

    @Test
    fun `rejects unstable and duplicate declarations`() {
        val firstType = type(DtoTypeId("demo#b"))
        val secondType = type(DtoTypeId("demo#a"))

        assertFailsWith<IllegalArgumentException> {
            DtoGraph(SOURCE, emptyList(), listOf(firstType, secondType), emptyList())
        }

        val ownerTypeId = DtoTypeId("demo#root")
        val propId = DtoPropId("demo#root/prop:value")
        val prop = userProp(propId, ownerTypeId)
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(ownerTypeId),
                types = listOf(type(ownerTypeId, listOf(propId))),
                props = listOf(prop, prop),
            )
        }
    }

    @Test
    fun `rejects dangling roots properties branches and targets`() {
        val rootTypeId = DtoTypeId("demo#root")
        val missingTypeId = DtoTypeId("demo#missing")
        val propId = DtoPropId("demo#root/prop:value")

        assertFailsWith<IllegalArgumentException> {
            DtoGraph(SOURCE, listOf(rootTypeId), emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type(rootTypeId, listOf(propId))),
                props = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(
                    type(
                        id = rootTypeId,
                        polymorphism = DtoPolymorphism(
                            exhaustive = true,
                            branches = listOf(
                                DtoPolymorphicBranch(
                                    kind = DtoPolymorphicBranchKind.DEFAULT,
                                    targetBaseTypeId = null,
                                    declaredClassName = null,
                                    className = "demo.MissingView",
                                    bodyTypeId = missingTypeId,
                                    mergedTypeId = missingTypeId,
                                    implicit = false,
                                    location = LOCATION,
                                )
                            ),
                        ),
                    )
                ),
                props = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type(rootTypeId, listOf(propId))),
                props = listOf(baseProp(propId, rootTypeId, targetTypeId = missingTypeId)),
            )
        }
    }

    @Test
    fun `rejects property owner mismatch`() {
        val rootTypeId = DtoTypeId("demo#root")
        val propId = DtoPropId("demo#root/prop:value")

        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type(rootTypeId, listOf(propId))),
                props = listOf(userProp(propId, DtoTypeId("demo#other"))),
            )
        }
    }

    @Test
    fun `rejects invalid conditional input strategies`() {
        val ownerTypeId = DtoTypeId("demo#root")

        assertFailsWith<IllegalArgumentException> {
            baseProp(
                id = DtoPropId("demo#root/prop:fuzzy"),
                ownerTypeId = ownerTypeId,
                inputModifier = DtoModifier.FUZZY,
            )
        }

        listOf(DtoModifier.DYNAMIC, DtoModifier.FUZZY).forEach { inputModifier ->
            val propId = DtoPropId("demo#root/prop:${inputModifier.name.lowercase()}")
            val prop = baseProp(
                id = propId,
                ownerTypeId = ownerTypeId,
                nullable = true,
                inputModifier = inputModifier,
            )
            assertFailsWith<IllegalArgumentException> {
                DtoGraph(
                    source = SOURCE,
                    rootTypeIds = listOf(ownerTypeId),
                    types = listOf(type(ownerTypeId, listOf(propId))),
                    props = listOf(prop),
                )
            }
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(ownerTypeId),
                types = listOf(
                    type(
                        id = ownerTypeId,
                        propIds = listOf(propId),
                        modifiers = setOf(DtoModifier.INPUT),
                    ),
                ),
                props = listOf(prop),
            )
        }
    }

    @Test
    fun `rejects non-base property chain references`() {
        val rootTypeId = DtoTypeId("demo#root")
        val basePropId = DtoPropId("demo#root/prop:base")
        val foldPropId = DtoPropId("demo#root/prop:fold")
        val userPropId = DtoPropId("demo#root/prop:user")
        val userProp = userProp(userPropId, rootTypeId)
        val type = type(rootTypeId, listOf(basePropId, foldPropId, userPropId))

        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type),
                props = listOf(
                    baseProp(basePropId, rootTypeId, tailPropId = userPropId),
                    foldProp(foldPropId, rootTypeId, basePropId),
                    userProp,
                ).sortedBy(DtoProp::id),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type),
                props = listOf(
                    baseProp(basePropId, rootTypeId, nextPropId = userPropId),
                    foldProp(foldPropId, rootTypeId, basePropId),
                    userProp,
                ).sortedBy(DtoProp::id),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootTypeId),
                types = listOf(type),
                props = listOf(
                    baseProp(basePropId, rootTypeId),
                    foldProp(foldPropId, rootTypeId, userPropId),
                    userProp,
                ).sortedBy(DtoProp::id),
            )
        }
    }

    private fun type(
        id: DtoTypeId,
        propIds: List<DtoPropId> = emptyList(),
        polymorphism: DtoPolymorphism? = null,
        modifiers: Set<DtoModifier> = emptySet(),
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = null,
            packageName = "demo",
            name = id.value.substringAfterLast('#'),
            modifiers = modifiers,
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = propIds,
            hiddenFlatPropIds = emptyList(),
            polymorphism = polymorphism,
        )
    }

    private fun userProp(id: DtoPropId, ownerTypeId: DtoTypeId): DtoUserProp {
        return DtoUserProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = "value",
            alias = "value",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("java.lang.String", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
    }

    private fun baseProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
        targetTypeId: DtoTypeId? = null,
        nullable: Boolean = false,
        inputModifier: DtoModifier = DtoModifier.STATIC,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = "base",
            alias = "base",
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = "base",
                    propId = LsiSymbolId.property(LsiSymbolId.type("demo.Entity"), "base"),
                )
            ),
            basePath = "base",
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = inputModifier,
            functionName = null,
            targetTypeId = targetTypeId,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun foldProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        nullGuardPropId: DtoPropId,
    ): DtoFoldProp {
        return DtoFoldProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = "fold",
            alias = "fold",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = nullGuardPropId,
            targetTypeId = ownerTypeId,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Model.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
    }
}
