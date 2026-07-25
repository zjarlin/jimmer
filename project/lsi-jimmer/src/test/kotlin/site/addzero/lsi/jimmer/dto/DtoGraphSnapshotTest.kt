package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoGraphSnapshotTest {

    @Test
    fun `normalizes equivalent collection insertion order`() {
        val graph = graph()
        val rootType = graph.typesById.getValue(ROOT_TYPE_ID)
        val baseProp = graph.propsById.getValue(BASE_PROP_ID) as DtoBaseProp
        val reordered = graph.copy(
            types = graph.types.map { type ->
                if (type.id == ROOT_TYPE_ID) {
                    type.copy(modifiers = linkedSetOf(DtoModifier.FIXED, DtoModifier.INPUT))
                } else {
                    type
                }
            },
            props = graph.props.map { prop ->
                if (prop.id == BASE_PROP_ID) {
                    baseProp.copy(
                        likeOptions = linkedSetOf(
                            DtoLikeOption.MATCH_START,
                            DtoLikeOption.INSENSITIVE,
                        ),
                    )
                } else {
                    prop
                }
            },
        )

        assertEquals(graph.normalizedSnapshot(), reordered.normalizedSnapshot())
        assertEquals(graph.fingerprint(), reordered.fingerprint())
        assertEquals(64, graph.fingerprint().length)
        assertContains(graph.normalizedSnapshot(), "graph|")
        assertContains(graph.normalizedSnapshot(), "root|")
        assertContains(graph.normalizedSnapshot(), "type|")
        assertContains(graph.normalizedSnapshot(), "branch|")
        assertContains(graph.normalizedSnapshot(), "base-prop|")
        assertContains(graph.normalizedSnapshot(), "user-prop|")
        assertContains(graph.normalizedSnapshot(), "fold-prop|")
        assertEquals(setOf(DtoModifier.INPUT, DtoModifier.FIXED), rootType.modifiers)
    }

    @Test
    fun `changes fingerprint for DTO semantic mutations`() {
        val graph = graph()
        val baseline = graph.fingerprint()
        val mutations = listOf(
            graph.copy(rootTypeIds = graph.rootTypeIds.reversed()),
            graph.withRootType { type -> type.copy(documentation = "changed type documentation") },
            graph.withBaseProp { prop -> prop.copy(dtoDocumentation = "changed DTO documentation") },
            graph.withBaseProp { prop ->
                prop.copy(
                    targetTypeReference = DtoReusableTypeReference(
                        qualifiedName = "demo.dto.StoreView",
                        targetBaseTypeId = STORE_TYPE_ID,
                        kind = DtoReusableTypeKind.VIEW,
                        location = prop.aliasLocation,
                    ),
                )
            },
            graph.withUserProp { prop -> prop.copy(defaultValueText = "\"changed\"") },
            graph.withRootType { type ->
                val polymorphism = requireNotNull(type.polymorphism)
                type.copy(
                    polymorphism = polymorphism.copy(
                        branches = polymorphism.branches.map { branch ->
                            branch.copy(className = "demo.dto.ChangedBranch")
                        },
                    ),
                )
            },
        )

        mutations.forEach { mutation -> assertNotEquals(baseline, mutation.fingerprint()) }
        assertEquals(mutations.size, mutations.map(DtoGraph::fingerprint).toSet().size)
    }

    @Test
    fun `requires annotation interface and config contracts to reference the graph`() {
        val graph = graph()
        val annotationContract = annotationContract(graph)
        val interfaceResolution = DtoInterfaceContractResolution(
            contracts = listOf(DtoInterfaceContract(ROOT_TYPE_ID, emptyList(), emptyList())),
            diagnostics = emptyList(),
        )
        val configResolution = DtoConfigContractResolution(
            contracts = listOf(configContract(BASE_PROP_ID)),
            diagnostics = emptyList(),
        )

        graph.requireResolvedContracts(
            annotationContract,
            interfaceResolution,
            configResolution,
        )

        val missingType = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract.copy(typePlans = annotationContract.typePlans.dropLast(1)),
                interfaceResolution,
                configResolution,
            )
        }
        assertContains(requireNotNull(missingType.message), "every frozen DTO type")

        val missingProp = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract.copy(propPlans = annotationContract.propPlans.dropLast(1)),
                interfaceResolution,
                configResolution,
            )
        }
        assertContains(requireNotNull(missingProp.message), "every frozen DTO property")

        val unknownInterfaceType = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract,
                DtoInterfaceContractResolution(
                    contracts = listOf(
                        DtoInterfaceContract(
                            DtoTypeId("demo.dto.MissingView#root"),
                            emptyList(),
                            emptyList(),
                        )
                    ),
                    diagnostics = emptyList(),
                ),
                configResolution,
            )
        }
        assertContains(requireNotNull(unknownInterfaceType.message), "frozen DTO types")

        val unknownConfigProp = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract,
                interfaceResolution,
                DtoConfigContractResolution(
                    contracts = listOf(configContract(DtoPropId("demo.dto.BookView#prop:missing"))),
                    diagnostics = emptyList(),
                ),
            )
        }
        assertContains(requireNotNull(unknownConfigProp.message), "frozen DTO properties")

        graph.requireResolvedContracts(
            annotationContract,
            interfaceResolution,
            DtoConfigContractResolution(
                contracts = emptyList(),
                diagnostics = emptyList(),
                unresolvedTypeIds = listOf(FILTER_TYPE_ID),
            ),
        )

        val missingConfig = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract,
                interfaceResolution,
                DtoConfigContractResolution(emptyList(), emptyList()),
            )
        }
        assertContains(requireNotNull(missingConfig.message), "cover every frozen property config")

        val wrongConfig = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract,
                interfaceResolution,
                DtoConfigContractResolution(
                    contracts = listOf(
                        configContract(BASE_PROP_ID).copy(kind = DtoConfigContractKind.RECURSION)
                    ),
                    diagnostics = emptyList(),
                ),
            )
        }
        assertContains(requireNotNull(wrongConfig.message), "exactly match frozen property configs")

        val wrongImplementationTypeId = LsiSymbolId.type("demo.WrongBookFilter")
        val wrongImplementation = assertFailsWith<IllegalArgumentException> {
            graph.requireResolvedContracts(
                annotationContract,
                interfaceResolution,
                DtoConfigContractResolution(
                    contracts = listOf(
                        configContract(BASE_PROP_ID).copy(
                            implementationTypeId = wrongImplementationTypeId,
                            dependencyTypeIds = listOf(BOOK_TYPE_ID, wrongImplementationTypeId).sorted(),
                        )
                    ),
                    diagnostics = emptyList(),
                ),
            )
        }
        assertContains(requireNotNull(wrongImplementation.message), "exactly match frozen property configs")
    }

    @Test
    fun `rejects config states that cannot be expressed by the DTO grammar`() {
        val location = location(LsiSource.of("demo/config.dto", LsiLanguage.UNKNOWN), 1)
        val predicate = DtoPredicate.Nullity(
            path = listOf(DtoPropPathNode(LsiSymbolId.property(STORE_TYPE_ID, "name"), false)),
            negative = false,
        )

        assertFailsWith<IllegalArgumentException> {
            defaultConfig().copy(
                predicate = predicate,
                filter = DtoConfigTypeRef(FILTER_TYPE_ID, location),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            defaultConfig().copy(
                recursion = DtoConfigTypeRef(FILTER_TYPE_ID, location),
                depth = 2,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DtoLimit(value = 1, offset = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            DtoLimit(value = 0, offset = 0)
        }
    }

    @Test
    fun `normalizes only the supported comparison operators`() {
        assertEquals(
            listOf("=", "<>", "<", "<=", ">", ">=", "like", "ilike"),
            DtoComparisonOperator.entries.map(DtoComparisonOperator::token),
        )
        assertFailsWith<IllegalArgumentException> {
            DtoComparisonOperator.fromToken("!=")
        }
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

    private fun graph(): DtoGraph {
        val source = LsiSource.of(
            "demo/src/main/dto/Book.dto",
            LsiLanguage.UNKNOWN,
        )
        val rootType = DtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = linkedSetOf(DtoModifier.INPUT, DtoModifier.FIXED),
            annotations = listOf(markerAnnotation()),
            superInterfaces = listOf(typeRef(source, "demo.View")),
            documentation = "book DTO",
            location = location(source, 1),
            focusedRecursion = false,
            propIds = listOf(BASE_PROP_ID, USER_PROP_ID, FOLD_PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = DtoPolymorphism(
                exhaustive = true,
                branches = listOf(
                    DtoPolymorphicBranch(
                        kind = DtoPolymorphicBranchKind.DEFAULT,
                        targetBaseTypeId = null,
                        declaredClassName = null,
                        className = "demo.dto.DefaultBookView",
                        bodyTypeId = BODY_TYPE_ID,
                        mergedTypeId = MERGED_TYPE_ID,
                        implicit = false,
                        location = location(source, 2),
                    )
                ),
            ),
        )
        val types = listOf(
            rootType,
            simpleType(NESTED_TYPE_ID, STORE_TYPE_ID, source, 3),
            simpleType(BODY_TYPE_ID, BOOK_TYPE_ID, source, 4),
            simpleType(MERGED_TYPE_ID, BOOK_TYPE_ID, source, 5),
        ).sortedBy(DtoType::id)
        val props = listOf(
            baseProp(source),
            DtoUserProp(
                id = USER_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "label",
                alias = "label",
                nullable = true,
                annotations = emptyList(),
                documentation = "label documentation",
                aliasLocation = location(source, 7),
                type = typeRef(source, "java.lang.String", nullable = true),
                defaultValueText = "\"unknown\"",
            ),
            DtoFoldProp(
                id = FOLD_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "summary",
                alias = "summary",
                nullable = true,
                annotations = emptyList(),
                documentation = null,
                aliasLocation = location(source, 8),
                nullGuardPropId = BASE_PROP_ID,
                targetTypeId = NESTED_TYPE_ID,
            ),
        ).sortedBy(DtoProp::id)
        return DtoGraph(
            source = source,
            rootTypeIds = listOf(ROOT_TYPE_ID, NESTED_TYPE_ID),
            types = types,
            props = props,
        )
    }

    private fun baseProp(source: LsiSource): DtoBaseProp {
        return DtoBaseProp(
            id = BASE_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "store",
            alias = "store",
            nullable = false,
            annotations = listOf(markerAnnotation()),
            documentation = "store documentation",
            aliasLocation = location(source, 6),
            baseLocation = location(source, 6),
            baseProps = listOf(
                DtoBasePropBinding("store", LsiSymbolId.property(BOOK_TYPE_ID, "store"))
            ),
            basePath = "store",
            nextPropId = null,
            tailPropId = BASE_PROP_ID,
            baseNullable = false,
            inputModifier = DtoModifier.FIXED,
            functionName = null,
            targetTypeId = NESTED_TYPE_ID,
            enumType = null,
            config = DtoPropConfig(
                predicate = null,
                orderItems = emptyList(),
                filter = DtoConfigTypeRef(FILTER_TYPE_ID, location(source, 6)),
                recursion = null,
                fetchType = DtoFetchType.AUTO,
                limit = DtoLimit(10, 0),
                batch = 4,
                depth = 1,
            ),
            recursive = false,
            likeOptions = linkedSetOf(
                DtoLikeOption.INSENSITIVE,
                DtoLikeOption.MATCH_START,
            ),
            dtoDocumentation = "store DTO documentation",
        )
    }

    private fun simpleType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        source: LsiSource,
        line: Int,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = null,
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = location(source, line),
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun annotationContract(graph: DtoGraph): DtoAnnotationContract {
        return DtoAnnotationContract(
            declarations = emptyList(),
            typePlans = graph.types.map { type -> DtoTypeAnnotationPlan(type.id, emptyList()) },
            propPlans = graph.props.map { prop ->
                DtoPropAnnotationPlan(prop.id, emptyList(), emptyList())
            },
            diagnostics = emptyList(),
        )
    }

    private fun configContract(propId: DtoPropId): DtoConfigContract {
        val dependencyTypeIds = listOf(BOOK_TYPE_ID, FILTER_TYPE_ID).sorted()
        return DtoConfigContract(
            propId = propId,
            kind = DtoConfigContractKind.FILTER,
            implementationTypeId = FILTER_TYPE_ID,
            targetEntityTypeId = BOOK_TYPE_ID,
            construction = DtoConfigConstructionKind.ZERO_ARGUMENT_CONSTRUCTOR,
            dependencyTypeIds = dependencyTypeIds,
        )
    }

    private fun markerAnnotation(): DtoAnnotation {
        return DtoAnnotation(
            typeId = MARKER_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "value",
                    value = DtoAnnotationValue.ArrayValue(
                        listOf(
                            DtoAnnotationValue.EnumValue(MODE_TYPE_ID, "READ"),
                            DtoAnnotationValue.LiteralValue("\"stable\""),
                        )
                    ),
                )
            ),
        )
    }

    private fun typeRef(
        source: LsiSource,
        typeName: String,
        nullable: Boolean = false,
    ): DtoTypeRef {
        return DtoTypeRef(
            typeName = typeName,
            arguments = emptyList(),
            nullable = nullable,
            location = location(source, 1),
        )
    }

    private fun location(source: LsiSource, line: Int): LsiLocation {
        return LsiLocation(source, LsiPosition(line, 1))
    }

    private fun DtoGraph.withRootType(transform: (DtoType) -> DtoType): DtoGraph {
        return copy(
            types = types.map { type ->
                if (type.id == ROOT_TYPE_ID) {
                    transform(type)
                } else {
                    type
                }
            },
        )
    }

    private fun DtoGraph.withBaseProp(transform: (DtoBaseProp) -> DtoBaseProp): DtoGraph {
        return copy(
            props = props.map { prop ->
                if (prop.id == BASE_PROP_ID) {
                    transform(prop as DtoBaseProp)
                } else {
                    prop
                }
            },
        )
    }

    private fun DtoGraph.withUserProp(transform: (DtoUserProp) -> DtoUserProp): DtoGraph {
        return copy(
            props = props.map { prop ->
                if (prop.id == USER_PROP_ID) {
                    transform(prop as DtoUserProp)
                } else {
                    prop
                }
            },
        )
    }

    private companion object {
        val ROOT_TYPE_ID = DtoTypeId("demo.dto.BookView#root")
        val NESTED_TYPE_ID = DtoTypeId("demo.dto.BookView#nested")
        val BODY_TYPE_ID = DtoTypeId("demo.dto.BookView#branch:body")
        val MERGED_TYPE_ID = DtoTypeId("demo.dto.BookView#branch:merged")
        val BASE_PROP_ID = DtoPropId("demo.dto.BookView#prop:0:store")
        val USER_PROP_ID = DtoPropId("demo.dto.BookView#prop:1:label")
        val FOLD_PROP_ID = DtoPropId("demo.dto.BookView#prop:2:summary")
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val FILTER_TYPE_ID = LsiSymbolId.type("demo.BookFilter")
        val MARKER_TYPE_ID = LsiSymbolId.type("demo.Marker")
        val MODE_TYPE_ID = LsiSymbolId.type("demo.Mode")
    }
}
