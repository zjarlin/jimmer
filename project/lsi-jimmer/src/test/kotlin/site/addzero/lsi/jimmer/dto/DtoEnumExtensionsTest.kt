package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
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

class DtoEnumExtensionsTest {

    @Test
    fun `preserves declaration order in both mapping directions`() {
        val enumType = DtoEnumType(
            numeric = true,
            mappings = listOf(
                DtoEnumMapping("NEW", "10"),
                DtoEnumMapping("PROCESSING", "20"),
                DtoEnumMapping("DONE", "30"),
            ),
        )

        assertEquals(
            listOf("NEW" to "10", "PROCESSING" to "20", "DONE" to "30"),
            enumType.mappingsByConstant().entries.map { entry -> entry.key to entry.value },
        )
        assertEquals(
            listOf("10" to "NEW", "20" to "PROCESSING", "30" to "DONE"),
            enumType.mappingsByValue().entries.map { entry -> entry.key to entry.value },
        )
    }

    @Test
    fun `resolves numeric and string scalar types for each target language`() {
        val numericType = enumType(numeric = true)
        val stringType = enumType(numeric = false)

        assertEquals(
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            numericType.scalarType(LsiLanguage.JAVA),
        )
        assertEquals(
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            numericType.scalarType(LsiLanguage.KOTLIN),
        )
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            stringType.scalarType(LsiLanguage.JAVA),
        )
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("kotlin.String")),
            stringType.scalarType(LsiLanguage.KOTLIN),
        )
        assertFailsWith<IllegalArgumentException> {
            stringType.scalarType(LsiLanguage.UNKNOWN)
        }
    }

    @Test
    fun `resolves the enum declaration from the tail immutable property`() {
        val fixture = fixture(listOf(STATUS_PROP_ID to STATUS_ENUM_TYPE_ID))

        assertEquals(
            STATUS_ENUM_TYPE_ID,
            fixture.rootProp.enumTypeId(fixture.graph, fixture.schema),
        )
        assertEquals(
            LsiDeclaredType(STATUS_ENUM_TYPE_ID),
            fixture.rootProp.enumTypeRef(fixture.graph, fixture.schema),
        )
    }

    @Test
    fun `accepts multiple tail bindings only when their enum declarations match`() {
        val sharedFixture = fixture(
            listOf(
                STATUS_PROP_ID to STATUS_ENUM_TYPE_ID,
                FALLBACK_STATUS_PROP_ID to STATUS_ENUM_TYPE_ID,
            ),
        )
        assertEquals(
            STATUS_ENUM_TYPE_ID,
            sharedFixture.rootProp.enumTypeId(sharedFixture.graph, sharedFixture.schema),
        )

        val conflictingFixture = fixture(
            listOf(
                STATUS_PROP_ID to STATUS_ENUM_TYPE_ID,
                FALLBACK_STATUS_PROP_ID to OTHER_ENUM_TYPE_ID,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            conflictingFixture.rootProp.enumTypeId(
                conflictingFixture.graph,
                conflictingFixture.schema,
            )
        }
    }

    @Test
    fun `rejects missing non-declared and unmapped enum properties`() {
        val missingFixture = fixture(listOf(STATUS_PROP_ID to STATUS_ENUM_TYPE_ID))
        assertFailsWith<IllegalArgumentException> {
            missingFixture.rootProp.enumTypeId(
                missingFixture.graph,
                ImmutableSchema(emptyList()),
            )
        }

        val primitiveFixture = fixture(
            bindings = listOf(STATUS_PROP_ID to STATUS_ENUM_TYPE_ID),
            primitiveTail = true,
        )
        assertFailsWith<IllegalStateException> {
            primitiveFixture.rootProp.enumTypeId(primitiveFixture.graph, primitiveFixture.schema)
        }

        val unmappedProp = missingFixture.rootProp.copy(enumType = null)
        val unmappedGraph = missingFixture.graph.copy(
            props = missingFixture.graph.props.map { prop ->
                if (prop.id == unmappedProp.id) unmappedProp else prop
            },
        )
        assertFailsWith<IllegalArgumentException> {
            unmappedProp.enumTypeId(unmappedGraph, missingFixture.schema)
        }
    }

    private fun enumType(numeric: Boolean): DtoEnumType {
        val value = if (numeric) "1" else "\"active\""
        return DtoEnumType(numeric, listOf(DtoEnumMapping("ACTIVE", value)))
    }

    private fun fixture(
        bindings: List<Pair<LsiSymbolId, LsiSymbolId>>,
        primitiveTail: Boolean = false,
    ): Fixture {
        val rootProp = dtoBaseProp(
            id = ROOT_PROP_ID,
            name = "container",
            immutablePropIds = listOf(CONTAINER_PROP_ID),
            nextPropId = TAIL_PROP_ID,
            tailPropId = TAIL_PROP_ID,
            enumType = enumType(numeric = true),
        )
        val tailProp = dtoBaseProp(
            id = TAIL_PROP_ID,
            name = "status",
            immutablePropIds = bindings.map(Pair<LsiSymbolId, LsiSymbolId>::first),
            nextPropId = null,
            tailPropId = TAIL_PROP_ID,
            enumType = enumType(numeric = true),
        )
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo.dto",
            name = "ItemView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(ROOT_PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(dtoType),
            props = listOf(rootProp, tailProp).sortedBy(DtoProp::id),
        )
        val immutableProps = buildList {
            add(immutableProp(CONTAINER_PROP_ID, LsiDeclaredType(CONTAINER_TYPE_ID)))
            bindings.forEach { (propId, enumTypeId) ->
                val type = if (primitiveTail) {
                    LsiPrimitiveType(LsiPrimitiveKind.INT)
                } else {
                    LsiDeclaredType(enumTypeId)
                }
                add(immutableProp(propId, type))
            }
        }.distinctBy(ImmutableProp::id)
        val immutableType = ImmutableType(
            id = IMMUTABLE_TYPE_ID,
            qualifiedName = "demo.Item",
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = immutableProps,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = false,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = null,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
        return Fixture(rootProp, graph, ImmutableSchema(listOf(immutableType)))
    }

    private fun dtoBaseProp(
        id: DtoPropId,
        name: String,
        immutablePropIds: List<LsiSymbolId>,
        nextPropId: DtoPropId?,
        tailPropId: DtoPropId,
        enumType: DtoEnumType?,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = name,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = immutablePropIds.mapIndexed { index, propId ->
                DtoBasePropBinding("$name$index", propId)
            },
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = enumType,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun immutableProp(
        id: LsiSymbolId,
        type: site.addzero.lsi.model.LsiTypeRef,
    ): ImmutableProp {
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = when (id) {
                CONTAINER_PROP_ID -> "container"
                STATUS_PROP_ID -> "status"
                FALLBACK_STATUS_PROP_ID -> "fallbackStatus"
                else -> error("Unexpected immutable property id: ${id.value}")
            },
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(id),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private data class Fixture(
        val rootProp: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    )

    private companion object {
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Item")
        val CONTAINER_TYPE_ID = LsiSymbolId.type("demo.Container")
        val STATUS_ENUM_TYPE_ID = LsiSymbolId.type("demo.Status")
        val OTHER_ENUM_TYPE_ID = LsiSymbolId.type("demo.OtherStatus")
        val CONTAINER_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "container")
        val STATUS_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "status")
        val FALLBACK_STATUS_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "fallbackStatus")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.ItemView")
        val ROOT_PROP_ID = DtoPropId("demo.dto.ItemView#container")
        val TAIL_PROP_ID = DtoPropId("demo.dto.ItemView#container/status")
        val SOURCE = LsiSource.of("demo/Item.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
    }
}
