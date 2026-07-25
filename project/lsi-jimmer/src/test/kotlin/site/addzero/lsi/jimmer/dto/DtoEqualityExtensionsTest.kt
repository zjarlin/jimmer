package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertContains
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
import site.addzero.lsi.jimmer.ImmutableConverter
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef

class DtoEqualityExtensionsTest {

    @Test
    fun `resolves associated array ids from the final DTO root type`() {
        assertEquals(
            DtoValueEqualityKind.ARRAY_CONTENT,
            fixture(DtoModifier.INPUT, associationList = false, functionName = "id").equalityKind(),
        )
        assertEquals(
            DtoValueEqualityKind.VALUE,
            fixture(DtoModifier.INPUT, associationList = true, functionName = "id").equalityKind(),
        )
        assertEquals(
            DtoValueEqualityKind.ARRAY_CONTENT,
            fixture(DtoModifier.SPECIFICATION, associationList = true, functionName = "id").equalityKind(),
        )
        assertEquals(
            DtoValueEqualityKind.ARRAY_CONTENT,
            fixture(
                DtoModifier.SPECIFICATION,
                associationList = false,
                functionName = "associatedIdNe",
            ).equalityKind(),
        )
        assertEquals(
            DtoValueEqualityKind.VALUE,
            fixture(
                DtoModifier.SPECIFICATION,
                associationList = false,
                functionName = "associatedIdIn",
            ).equalityKind(),
        )
    }

    @Test
    fun `recognizes star array converter targets`() {
        val starArrayType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("kotlin.Array"),
            arguments = listOf(LsiTypeArgument.STAR),
        )

        assertEquals(
            DtoValueEqualityKind.ARRAY_CONTENT,
            fixture(
                dtoModifier = DtoModifier.INPUT,
                associationList = false,
                functionName = "id",
                idStorageType = STRING_TYPE,
                idConverterTargetType = starArrayType,
            ).equalityKind(),
        )
    }

    @Test
    fun `validates all associated id bindings`() {
        val matchingFixture = multiBindingFixture(
            dtoModifier = DtoModifier.INPUT,
            functionName = "id",
            bindings = listOf(
                AssociatedIdBinding(list = false, idStorageType = ARRAY_ID_TYPE),
                AssociatedIdBinding(list = false, idStorageType = ARRAY_ID_TYPE),
            ),
        )
        assertEquals(DtoValueEqualityKind.ARRAY_CONTENT, matchingFixture.equalityKind())

        val typeFailure = assertFailsWith<IllegalArgumentException> {
            multiBindingFixture(
                dtoModifier = DtoModifier.INPUT,
                functionName = "id",
                bindings = listOf(
                    AssociatedIdBinding(list = false, idStorageType = ARRAY_ID_TYPE),
                    AssociatedIdBinding(list = false, idStorageType = STRING_TYPE),
                ),
            ).equalityKind()
        }
        assertContains(typeFailure.message.orEmpty(), "must expose one client type")

        val cardinalityFailure = assertFailsWith<IllegalArgumentException> {
            multiBindingFixture(
                dtoModifier = DtoModifier.INPUT,
                functionName = "id",
                bindings = listOf(
                    AssociatedIdBinding(list = false, idStorageType = ARRAY_ID_TYPE),
                    AssociatedIdBinding(list = true, idStorageType = ARRAY_ID_TYPE),
                ),
            ).equalityKind()
        }
        assertContains(cardinalityFailure.message.orEmpty(), "must use one association cardinality")
    }

    private fun fixture(
        dtoModifier: DtoModifier,
        associationList: Boolean,
        functionName: String,
        idStorageType: LsiTypeRef = ARRAY_ID_TYPE,
        idConverterTargetType: LsiTypeRef? = null,
    ): Fixture {
        return multiBindingFixture(
            dtoModifier = dtoModifier,
            functionName = functionName,
            bindings = listOf(
                AssociatedIdBinding(
                    list = associationList,
                    idStorageType = idStorageType,
                    idConverterTargetType = idConverterTargetType,
                ),
            ),
        )
    }

    private fun multiBindingFixture(
        dtoModifier: DtoModifier,
        functionName: String,
        bindings: List<AssociatedIdBinding>,
    ): Fixture {
        val targetTypes = mutableListOf<ImmutableType>()
        val associationProps = bindings.mapIndexed { index, binding ->
            val suffix = index + 1
            val targetTypeId = LsiSymbolId.type("demo.Target$suffix")
            val idProp = immutableProp(
                ownerTypeId = targetTypeId,
                name = "id",
                type = binding.idStorageType,
                primaryMapping = PrimaryMapping.ID,
                converter = binding.idConverterTargetType?.let { targetType ->
                    ImmutableConverter(
                        converterTypeId = LsiSymbolId.type("demo.ArrayIdConverter$suffix"),
                        sourceType = binding.idStorageType,
                        targetType = targetType,
                        sourceNullable = false,
                        targetNullable = false,
                        propertyNullable = false,
                    )
                },
            )
            targetTypes += immutableType(
                targetTypeId,
                ImmutableTypeKind.ENTITY,
                listOf(idProp),
                idPropId = idProp.id,
            )
            immutableProp(
                ownerTypeId = OWNER_TYPE_ID,
                name = "targets$suffix",
                type = if (binding.list) {
                    LsiDeclaredType(
                        declarationId = LsiSymbolId.type("java.util.List"),
                        arguments = listOf(
                            LsiTypeArgument.invariant(LsiDeclaredType(targetTypeId)),
                        ),
                    )
                } else {
                    LsiDeclaredType(targetTypeId)
                },
                list = binding.list,
                primaryMapping = PrimaryMapping.ASSOCIATION,
                associationKind = AssociationKind.IMPLICIT,
                targetTypeId = targetTypeId,
            )
        }
        val schema = ImmutableSchema(
            listOf(immutableType(OWNER_TYPE_ID, ImmutableTypeKind.IMMUTABLE, associationProps)) +
                targetTypes,
        )
        val dtoPropId = DtoPropId("demo.dto.TargetIds#targets")
        val dtoProp = DtoBaseProp(
            id = dtoPropId,
            ownerTypeId = DTO_TYPE_ID,
            name = "targetIds",
            alias = "targetIds",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = associationProps.map { associationProp ->
                DtoBasePropBinding(associationProp.name, associationProp.id)
            },
            basePath = "targets",
            nextPropId = null,
            tailPropId = dtoPropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = OWNER_TYPE_ID,
            packageName = "demo.dto",
            name = "TargetIds",
            modifiers = setOf(dtoModifier),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(dtoPropId),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(dtoType),
            props = listOf(dtoProp),
        )
        return Fixture(dtoProp, graph, schema)
    }

    private fun immutableType(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp>,
        idPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = kind,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = props,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = kind == ImmutableTypeKind.ENTITY,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = idPropId,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        list: Boolean = false,
        primaryMapping: PrimaryMapping,
        associationKind: AssociationKind = AssociationKind.NONE,
        targetTypeId: LsiSymbolId? = null,
        converter: ImmutableConverter? = null,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = list,
            association = associationKind != AssociationKind.NONE,
            embedded = false,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    ) {
        fun equalityKind(): DtoValueEqualityKind = prop.valueEqualityKind(graph, schema)
    }

    private data class AssociatedIdBinding(
        val list: Boolean,
        val idStorageType: LsiTypeRef,
        val idConverterTargetType: LsiTypeRef? = null,
    )

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/TargetIds.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val OWNER_TYPE_ID = LsiSymbolId.type("demo.Owner")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.TargetIds")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val ARRAY_ID_TYPE = LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.BYTE))
    }
}
