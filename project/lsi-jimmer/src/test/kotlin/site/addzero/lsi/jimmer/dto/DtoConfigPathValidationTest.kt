package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class DtoConfigPathValidationTest {

    @Test
    fun `accepts scalar embedded and reference associated id paths`() {
        val config = config(
            predicate = DtoPredicate.And(
                listOf(
                    comparison(AUTHOR_NAME_PROP_ID),
                    comparison(AUTHOR_ADDRESS_PROP_ID, ADDRESS_CITY_PROP_ID),
                    nullity(AUTHOR_PUBLISHER_PROP_ID, PUBLISHER_NAME_PROP_ID),
                    comparison(DtoPropPathNode(AUTHOR_PUBLISHER_PROP_ID, associatedId = true)),
                )
            ),
            orderItems = listOf(
                DtoOrderItem(
                    path = path(AUTHOR_ADDRESS_PROP_ID, ADDRESS_CITY_PROP_ID),
                    descending = false,
                )
            ),
        )

        assertEquals(emptyList(), dtoGraph(config).validateDtoConfigPaths(IMMUTABLE_SCHEMA))
    }

    @Test
    fun `reports every invalid path with stable property and path details`() {
        val invalidPredicates = listOf(
            nullity(MISSING_PREDICATE_PROP_ID),
            nullity(BOOK_TITLE_PROP_ID),
            nullity(AUTHOR_NAME_PROP_ID, AUTHOR_NAME_PROP_ID),
            nullity(AUTHOR_GENERIC_TARGET_PROP_ID, PUBLISHER_NAME_PROP_ID),
            nullity(
                DtoPropPathNode(AUTHOR_PUBLISHER_PROP_ID, associatedId = true),
                DtoPropPathNode(PUBLISHER_NAME_PROP_ID, associatedId = false),
            ),
            nullity(DtoPropPathNode(AUTHOR_NAME_PROP_ID, associatedId = true)),
            nullity(DtoPropPathNode(AUTHOR_IDLESS_TARGET_PROP_ID, associatedId = true)),
            nullity(AUTHOR_MISSING_TARGET_PROP_ID, MISSING_TARGET_NAME_PROP_ID),
            nullity(AUTHOR_PUBLISHER_PROP_ID),
            nullity(DtoPropPathNode(AUTHOR_BOOKS_PROP_ID, associatedId = true)),
            nullity(AUTHOR_BOOKS_PROP_ID, BOOK_TITLE_PROP_ID),
            nullity(AUTHOR_MISSING_EMBEDDED_PROP_ID, MISSING_TARGET_NAME_PROP_ID),
        )
        val graph = dtoGraph(
            config(
                predicate = DtoPredicate.And(invalidPredicates),
                orderItems = listOf(
                    DtoOrderItem(path(MISSING_ORDER_PROP_ID), descending = true)
                ),
            )
        )

        val diagnostics = graph.validateDtoConfigPaths(IMMUTABLE_SCHEMA)
        val resolution = LsiWorkspace.EMPTY.resolveDtoConfigContracts(
            graph = graph,
            immutableSchema = IMMUTABLE_SCHEMA,
            targetLanguage = LsiLanguage.JAVA,
        )

        assertEquals(13, diagnostics.size)
        assertEquals(diagnostics, resolution.diagnostics)
        assertEquals(diagnostics.map { diagnostic -> diagnostic.code }.sorted(), diagnostics.map { it.code })
        assertEquals(
            mapOf(
                "jimmer.dto.config.path-associated-id-list" to 1,
                "jimmer.dto.config.path-associated-id-non-association" to 1,
                "jimmer.dto.config.path-associated-id-non-terminal" to 1,
                "jimmer.dto.config.path-associated-id-target-id-unresolved" to 1,
                "jimmer.dto.config.path-non-terminal-category" to 2,
                "jimmer.dto.config.path-owner-mismatch" to 1,
                "jimmer.dto.config.path-prop-missing" to 2,
                "jimmer.dto.config.path-target-unresolved" to 3,
                "jimmer.dto.config.path-terminal-association" to 1,
            ),
            diagnostics.groupingBy { diagnostic -> diagnostic.code }.eachCount(),
        )
        diagnostics.forEach { diagnostic ->
            assertEquals(LsiDiagnosticSeverity.ERROR, diagnostic.severity)
            assertEquals(DTO_LOCATION, diagnostic.location)
            assertEquals(DTO_PROP_ID.value, diagnostic.details["dtoPropId"])
            assertEquals("authors", diagnostic.details["dtoPropName"])
            assertTrue(diagnostic.details.getValue("path").isNotEmpty())
            assertTrue(diagnostic.details.getValue("pathKind") in setOf("predicate", "order"))
            assertEquals(diagnostic.details.keys.sorted(), diagnostic.details.keys.toList())
        }
        val orderDiagnostic = diagnostics.single { diagnostic ->
            diagnostic.details["pathPropId"] == MISSING_ORDER_PROP_ID.value
        }
        assertEquals("order", orderDiagnostic.details["pathKind"])
        assertEquals("0", orderDiagnostic.details["pathOrdinal"])
        val associatedIdDiagnostic = diagnostics.single { diagnostic ->
            diagnostic.code == "jimmer.dto.config.path-associated-id-non-terminal"
        }
        assertEquals(
            "${AUTHOR_PUBLISHER_PROP_ID.value}[associated-id] -> ${PUBLISHER_NAME_PROP_ID.value}",
            associatedIdDiagnostic.details["path"],
        )
        assertEquals(diagnostics, graph.validateDtoConfigPaths(IMMUTABLE_SCHEMA))
    }

    @Test
    fun `reports an unresolved config root without dereferencing missing bindings`() {
        val graph = dtoGraph(
            config(predicate = nullity(AUTHOR_NAME_PROP_ID)),
            basePropId = MISSING_CONFIG_ROOT_PROP_ID,
        )

        val diagnostic = graph.validateDtoConfigPaths(IMMUTABLE_SCHEMA).single()

        assertEquals("jimmer.dto.config.path-root-target-unresolved", diagnostic.code)
        assertEquals("base-prop-missing", diagnostic.details["reason"])
        assertEquals(MISSING_CONFIG_ROOT_PROP_ID.value, diagnostic.details["missingBasePropIds"])
        assertEquals(AUTHOR_NAME_PROP_ID.value, diagnostic.details["path"])
        assertEquals(null, diagnostic.symbolId)
    }

    private fun dtoGraph(
        config: DtoPropConfig,
        basePropId: LsiSymbolId = BOOK_AUTHORS_PROP_ID,
    ): DtoGraph {
        val dtoProp = DtoBaseProp(
            id = DTO_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "authors",
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = DTO_LOCATION,
            baseLocation = DTO_LOCATION,
            baseProps = listOf(DtoBasePropBinding("authors", basePropId)),
            basePath = "authors",
            nextPropId = null,
            tailPropId = DTO_PROP_ID,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = config,
            recursive = false,
            likeOptions = emptySet(),
        )
        return DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(
                DtoType(
                    id = DTO_TYPE_ID,
                    baseTypeId = BOOK_TYPE_ID,
                    packageName = "demo.dto",
                    name = "BookView",
                    modifiers = emptySet(),
                    annotations = emptyList(),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = DTO_LOCATION,
                    focusedRecursion = false,
                    propIds = listOf(DTO_PROP_ID),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                )
            ),
            props = listOf(dtoProp),
        )
    }

    private fun config(
        predicate: DtoPredicate?,
        orderItems: List<DtoOrderItem> = emptyList(),
    ): DtoPropConfig {
        return DtoPropConfig(
            predicate = predicate,
            orderItems = orderItems,
            filter = null,
            recursion = null,
            fetchType = DtoFetchType.AUTO,
            limit = null,
            batch = null,
            depth = null,
        )
    }

    private fun comparison(propId: LsiSymbolId): DtoPredicate.Comparison {
        return comparison(DtoPropPathNode(propId, associatedId = false))
    }

    private fun comparison(
        firstPropId: LsiSymbolId,
        secondPropId: LsiSymbolId,
    ): DtoPredicate.Comparison {
        return comparison(
            DtoPropPathNode(firstPropId, associatedId = false),
            DtoPropPathNode(secondPropId, associatedId = false),
        )
    }

    private fun comparison(vararg nodes: DtoPropPathNode): DtoPredicate.Comparison {
        return DtoPredicate.Comparison(
            path = nodes.toList(),
            operator = DtoComparisonOperator.EQ,
            value = DtoConfigValue.StringValue("value"),
        )
    }

    private fun nullity(propId: LsiSymbolId): DtoPredicate.Nullity {
        return nullity(DtoPropPathNode(propId, associatedId = false))
    }

    private fun nullity(
        firstPropId: LsiSymbolId,
        secondPropId: LsiSymbolId,
    ): DtoPredicate.Nullity {
        return nullity(
            DtoPropPathNode(firstPropId, associatedId = false),
            DtoPropPathNode(secondPropId, associatedId = false),
        )
    }

    private fun nullity(vararg nodes: DtoPropPathNode): DtoPredicate.Nullity {
        return DtoPredicate.Nullity(path = nodes.toList(), negative = false)
    }

    private fun path(propId: LsiSymbolId): List<DtoPropPathNode> {
        return listOf(DtoPropPathNode(propId, associatedId = false))
    }

    private fun path(
        firstPropId: LsiSymbolId,
        secondPropId: LsiSymbolId,
    ): List<DtoPropPathNode> {
        return listOf(
            DtoPropPathNode(firstPropId, associatedId = false),
            DtoPropPathNode(secondPropId, associatedId = false),
        )
    }

    private companion object {
        val DTO_SOURCE = LsiSource.of("demo/Book.dto", LsiLanguage.UNKNOWN)
        val DTO_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(4, 5))
        val DTO_TYPE_ID = DtoTypeId("demo/Book.dto#root:BookView")
        val DTO_PROP_ID = DtoPropId("demo/Book.dto#root:BookView/prop:authors")

        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val AUTHOR_TYPE_ID = LsiSymbolId.type("demo.Author")
        val PUBLISHER_TYPE_ID = LsiSymbolId.type("demo.Publisher")
        val ADDRESS_TYPE_ID = LsiSymbolId.type("demo.Address")
        val IDLESS_TYPE_ID = LsiSymbolId.type("demo.IdlessTarget")
        val MISSING_TARGET_TYPE_ID = LsiSymbolId.type("demo.MissingTarget")

        val BOOK_AUTHORS_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "authors")
        val BOOK_TITLE_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "title")
        val AUTHOR_NAME_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "name")
        val AUTHOR_PUBLISHER_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "publisher")
        val AUTHOR_ADDRESS_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "address")
        val AUTHOR_GENERIC_TARGET_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "genericTarget")
        val AUTHOR_IDLESS_TARGET_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "idlessTarget")
        val AUTHOR_MISSING_TARGET_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "missingTarget")
        val AUTHOR_BOOKS_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "books")
        val AUTHOR_MISSING_EMBEDDED_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "missingEmbedded")
        val PUBLISHER_NAME_PROP_ID = LsiSymbolId.property(PUBLISHER_TYPE_ID, "name")
        val ADDRESS_CITY_PROP_ID = LsiSymbolId.property(ADDRESS_TYPE_ID, "city")
        val MISSING_TARGET_NAME_PROP_ID = LsiSymbolId.property(MISSING_TARGET_TYPE_ID, "name")
        val MISSING_PREDICATE_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "missingPredicate")
        val MISSING_ORDER_PROP_ID = LsiSymbolId.property(AUTHOR_TYPE_ID, "missingOrder")
        val MISSING_CONFIG_ROOT_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "missingAuthors")

        val IMMUTABLE_SCHEMA = ImmutableSchema(
            listOf(
                immutableType(
                    BOOK_TYPE_ID,
                    listOf(
                        associationProp(
                            BOOK_TYPE_ID,
                            "authors",
                            AUTHOR_TYPE_ID,
                            AssociationKind.MANY_TO_MANY,
                            list = true,
                        ),
                        scalarProp(BOOK_TYPE_ID, "title"),
                    ),
                ),
                immutableType(
                    AUTHOR_TYPE_ID,
                    listOf(
                        scalarProp(AUTHOR_TYPE_ID, "name"),
                        associationProp(AUTHOR_TYPE_ID, "publisher", PUBLISHER_TYPE_ID),
                        embeddedProp(AUTHOR_TYPE_ID, "address", ADDRESS_TYPE_ID),
                        associationProp(AUTHOR_TYPE_ID, "genericTarget", null, genericTarget = true),
                        associationProp(AUTHOR_TYPE_ID, "idlessTarget", IDLESS_TYPE_ID),
                        associationProp(AUTHOR_TYPE_ID, "missingTarget", MISSING_TARGET_TYPE_ID),
                        associationProp(
                            AUTHOR_TYPE_ID,
                            "books",
                            BOOK_TYPE_ID,
                            AssociationKind.MANY_TO_MANY,
                            list = true,
                        ),
                        embeddedProp(AUTHOR_TYPE_ID, "missingEmbedded", MISSING_TARGET_TYPE_ID),
                    ),
                ),
                immutableType(
                    PUBLISHER_TYPE_ID,
                    listOf(scalarProp(PUBLISHER_TYPE_ID, "name")),
                ),
                immutableType(
                    ADDRESS_TYPE_ID,
                    listOf(scalarProp(ADDRESS_TYPE_ID, "city")),
                    kind = ImmutableTypeKind.EMBEDDABLE,
                    withId = false,
                ),
                immutableType(
                    IDLESS_TYPE_ID,
                    listOf(scalarProp(IDLESS_TYPE_ID, "value")),
                    kind = ImmutableTypeKind.IMMUTABLE,
                    withId = false,
                ),
            )
        )

        private fun immutableType(
            typeId: LsiSymbolId,
            props: List<ImmutableProp>,
            kind: ImmutableTypeKind = ImmutableTypeKind.ENTITY,
            withId: Boolean = true,
        ): ImmutableType {
            val completeProps = if (withId) listOf(idProp(typeId)) + props else props
            return ImmutableType(
                id = typeId,
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = kind,
                documentation = null,
                annotations = emptyList(),
                typeParameterIds = emptyList(),
                superTypeIds = emptyList(),
                props = completeProps,
                primarySuperTypeId = null,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = kind == ImmutableTypeKind.ENTITY,
                discriminatorValue = null,
                discriminatorPropId = null,
                idPropId = completeProps.singleOrNull { prop -> prop.primaryMapping == PrimaryMapping.ID }?.id,
                versionPropId = null,
                logicalDeletedPropId = null,
                acrossMicroServices = false,
                microServiceName = "",
            )
        }

        private fun idProp(ownerTypeId: LsiSymbolId): ImmutableProp {
            return immutableProp(
                ownerTypeId = ownerTypeId,
                name = "id",
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                primaryMapping = PrimaryMapping.ID,
            )
        }

        private fun scalarProp(ownerTypeId: LsiSymbolId, name: String): ImmutableProp {
            return immutableProp(
                ownerTypeId = ownerTypeId,
                name = name,
                type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            )
        }

        private fun embeddedProp(
            ownerTypeId: LsiSymbolId,
            name: String,
            targetTypeId: LsiSymbolId,
        ): ImmutableProp {
            return immutableProp(
                ownerTypeId = ownerTypeId,
                name = name,
                type = LsiDeclaredType(targetTypeId),
                embedded = true,
                targetTypeId = targetTypeId,
            )
        }

        private fun associationProp(
            ownerTypeId: LsiSymbolId,
            name: String,
            targetTypeId: LsiSymbolId?,
            associationKind: AssociationKind = AssociationKind.MANY_TO_ONE,
            list: Boolean = false,
            genericTarget: Boolean = false,
        ): ImmutableProp {
            val targetType = LsiDeclaredType(targetTypeId ?: LsiSymbolId.type("java.lang.Object"))
            val type = if (list) {
                LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.util.List"),
                    arguments = listOf(LsiTypeArgument.invariant(targetType)),
                )
            } else {
                targetType
            }
            val storage = when (associationKind) {
                AssociationKind.ONE_TO_ONE,
                AssociationKind.MANY_TO_ONE,
                -> AssociationStorageKind.COLUMN
                AssociationKind.ONE_TO_MANY,
                AssociationKind.MANY_TO_MANY,
                AssociationKind.MANY_TO_MANY_VIEW,
                -> AssociationStorageKind.MIDDLE_TABLE
                AssociationKind.NONE,
                AssociationKind.IMPLICIT,
                -> AssociationStorageKind.NONE
            }
            return immutableProp(
                ownerTypeId = ownerTypeId,
                name = name,
                type = type,
                targetTypeId = targetTypeId,
                associationKind = associationKind,
                associationStorage = storage,
                list = list,
                genericTarget = genericTarget,
                primaryMapping = PrimaryMapping.ASSOCIATION,
            )
        }

        private fun immutableProp(
            ownerTypeId: LsiSymbolId,
            name: String,
            type: LsiTypeRef,
            targetTypeId: LsiSymbolId? = null,
            associationKind: AssociationKind = AssociationKind.NONE,
            associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
            primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
            embedded: Boolean = false,
            list: Boolean = false,
            genericTarget: Boolean = false,
        ): ImmutableProp {
            val propId = LsiSymbolId.property(ownerTypeId, name)
            return ImmutableProp(
                id = propId,
                declarationId = propId,
                ownerTypeId = ownerTypeId,
                declaringTypeId = ownerTypeId,
                name = name,
                documentation = null,
                type = type,
                annotations = emptyList(),
                overrideChain = listOf(propId),
                inherited = false,
                overridden = false,
                nullable = false,
                list = list,
                association = associationKind != AssociationKind.NONE,
                embedded = embedded,
                targetTypeId = targetTypeId,
                primaryMapping = primaryMapping,
                primaryAnnotationTypeId = null,
                defaultContract = null,
                associationKind = associationKind,
                formulaKind = FormulaKind.NONE,
                mappedBy = null,
                associationStorage = associationStorage,
                transientResolver = null,
                view = null,
                genericTarget = genericTarget,
                remote = false,
                recursive = false,
                validations = emptyList(),
                converter = null,
            )
        }
    }
}
