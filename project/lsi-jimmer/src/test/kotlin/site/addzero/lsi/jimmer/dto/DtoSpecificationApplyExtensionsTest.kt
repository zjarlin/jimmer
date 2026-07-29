package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef

class DtoSpecificationApplyExtensionsTest {

    @Test
    fun `normalizes predicate operations and property argument shapes`() {
        val cases = listOf(
            Triple(null, "eq", true),
            Triple("id", "associatedIdEq", true),
            Triple("null", "isNull", true),
            Triple("notNull", "isNotNull", true),
            Triple("like", "like", true),
            Triple("notLike", "notLike", false),
            Triple("valueIn", "valueIn", true),
            Triple("valueNotIn", "valueNotIn", false),
            Triple("associatedIdIn", "associatedIdIn", true),
            Triple("associatedIdNotIn", "associatedIdNotIn", false),
            Triple("ge", "ge", false),
        )
        val props = cases.mapIndexed { index, (functionName, _, _) ->
            dtoProp(
                idSuffix = "operation-$index",
                name = "value$index",
                basePropIds = listOf(BOOK_NAME_PROP_ID),
                functionName = functionName,
            )
        }
        val graph = graph(props)

        cases.zip(props).forEach { (case, prop) ->
            val (_, expectedOperation, expectedArray) = case
            assertEquals(expectedOperation, prop.specificationOperationName(graph))
            assertEquals(expectedArray, prop.usesSpecificationPropArrayArgument(graph))
        }
    }

    @Test
    fun `resolves flat paths and multi binding arguments in declaration order`() {
        val fullNameProp = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "fullName",
            type = LsiDeclaredType(FULL_NAME_TYPE_ID),
            embedded = true,
            targetTypeId = FULL_NAME_TYPE_ID,
        )
        val firstNameProp = immutableProp(FULL_NAME_TYPE_ID, "firstName")
        val lastNameProp = immutableProp(FULL_NAME_TYPE_ID, "lastName")
        val schema = ImmutableSchema(
            listOf(
                entityType(BOOK_TYPE_ID, listOf(fullNameProp)),
                immutableType(
                    id = FULL_NAME_TYPE_ID,
                    kind = ImmutableTypeKind.EMBEDDABLE,
                    props = listOf(firstNameProp, lastNameProp),
                ),
            ),
        )
        val tail = dtoProp(
            idSuffix = "name-tail",
            name = "nameTail",
            basePropIds = listOf(firstNameProp.id, lastNameProp.id),
            functionName = "eq",
        )
        val root = dtoProp(
            idSuffix = "name-root",
            name = "name",
            basePropIds = listOf(fullNameProp.id),
            nextPropId = tail.id,
            tailPropId = tail.id,
        )
        val graph = graph(visibleProps = listOf(root), additionalProps = listOf(tail))

        assertEquals(listOf(fullNameProp), root.specificationPath(graph, schema))
        assertEquals(
            listOf(firstNameProp, lastNameProp),
            root.specificationArgumentProps(graph, schema),
        )
    }

    @Test
    fun `classifies entity and embeddable specification targets`() {
        val storeProp = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            type = LsiDeclaredType(STORE_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            targetTypeId = STORE_TYPE_ID,
        )
        val locationProp = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "location",
            type = LsiDeclaredType(LOCATION_TYPE_ID),
            embedded = true,
            targetTypeId = LOCATION_TYPE_ID,
        )
        val schema = ImmutableSchema(
            listOf(
                entityType(BOOK_TYPE_ID, listOf(storeProp, locationProp)),
                entityType(STORE_TYPE_ID),
                immutableType(LOCATION_TYPE_ID, ImmutableTypeKind.EMBEDDABLE),
            ),
        )
        val store = dtoProp(
            idSuffix = "store",
            name = "store",
            basePropIds = listOf(storeProp.id),
            targetTypeReference = reusableSpecification("contract.StoreSpecification", STORE_TYPE_ID),
        )
        val location = dtoProp(
            idSuffix = "location",
            name = "location",
            basePropIds = listOf(locationProp.id),
            targetTypeReference = reusableSpecification("contract.LocationSpecification", LOCATION_TYPE_ID),
        )
        val graph = graph(listOf(store, location))

        assertTrue(store.hasSpecificationTarget(graph))
        assertTrue(store.specificationTargetIsEntityAssociation(graph, schema))
        assertEquals(listOf(storeProp), store.specificationPath(graph, schema))
        assertFalse(location.specificationTargetIsEntityAssociation(graph, schema))
        assertEquals(listOf(locationProp), location.specificationPath(graph, schema))
        assertFailsWith<IllegalArgumentException> {
            store.specificationOperationName(graph)
        }
    }

    @Test
    fun `resolves converter requirements names and value accessors`() {
        val plainImmutableProp = immutableProp(BOOK_TYPE_ID, "plain")
        val convertedImmutableProp = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "converted",
            converter = ImmutableConverter(
                converterTypeId = LsiSymbolId.type("demo.StringConverter"),
                sourceType = STRING_TYPE,
                targetType = LsiDeclaredType(LsiSymbolId.type("demo.ConvertedValue")),
                sourceNullable = false,
                targetNullable = false,
                propertyNullable = false,
            ),
        )
        val activeImmutableProp = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "active",
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
        )
        val schema = ImmutableSchema(
            listOf(
                entityType(
                    BOOK_TYPE_ID,
                    listOf(plainImmutableProp, convertedImmutableProp, activeImmutableProp),
                ),
            ),
        )
        val plain = dtoProp("plain", "plain", listOf(plainImmutableProp.id))
        val converted = dtoProp("converted", "converted", listOf(convertedImmutableProp.id))
        val enumProp = dtoProp("enum", "enumValue", listOf(plainImmutableProp.id)).copy(
            enumType = DtoEnumType(
                numeric = false,
                mappings = listOf(DtoEnumMapping("ACTIVE", "A")),
            ),
        )
        val nullCheck = dtoProp(
            idSuffix = "active-null",
            name = "isActiveMissing",
            basePropIds = listOf(activeImmutableProp.id),
            functionName = "null",
        ).copy(nullable = false)
        val graph = graph(listOf(plain, converted, enumProp, nullCheck))

        assertFalse(plain.requiresSpecificationConverter(graph, schema))
        assertTrue(converted.requiresSpecificationConverter(graph, schema))
        assertTrue(enumProp.requiresSpecificationConverter(graph, schema))
        assertEquals("__convertConverted", converted.specificationConverterName(LsiLanguage.JAVA, graph))
        assertEquals("_convertConverted", converted.specificationConverterName(LsiLanguage.KOTLIN, graph))
        assertEquals(
            "isActiveMissing",
            nullCheck.dtoValueAccessorName(LsiLanguage.JAVA, graph, schema),
        )
        assertEquals(
            "isActiveMissing",
            nullCheck.dtoValueAccessorName(LsiLanguage.KOTLIN, graph, schema),
        )
        assertFailsWith<IllegalArgumentException> {
            converted.specificationConverterName(LsiLanguage.UNKNOWN, graph)
        }
    }

    private fun graph(
        visibleProps: List<DtoBaseProp>,
        additionalProps: List<DtoBaseProp> = emptyList(),
    ): DtoGraph {
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookSpecification",
            modifiers = setOf(DtoModifier.SPECIFICATION),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = DTO_LOCATION,
            focusedRecursion = false,
            propIds = visibleProps.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(type.id),
            types = listOf(type),
            props = (visibleProps + additionalProps).sortedBy(DtoProp::id),
        )
    }

    private fun dtoProp(
        idSuffix: String,
        name: String,
        basePropIds: List<LsiSymbolId>,
        functionName: String? = null,
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId? = null,
        targetTypeReference: DtoReusableTypeReference? = null,
    ): DtoBaseProp {
        val id = DtoPropId("${DTO_TYPE_ID.value}#$idSuffix")
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = name,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = DTO_LOCATION,
            baseLocation = DTO_LOCATION,
            baseProps = basePropIds.map { propId ->
                DtoBasePropBinding(propId.value.substringAfterLast(':'), propId)
            },
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId ?: id,
            baseNullable = true,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = null,
            targetTypeReference = targetTypeReference,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun reusableSpecification(
        qualifiedName: String,
        targetTypeId: LsiSymbolId,
    ): DtoReusableTypeReference {
        return DtoReusableTypeReference(
            qualifiedName = qualifiedName,
            targetBaseTypeId = targetTypeId,
            kind = DtoReusableTypeKind.SPECIFICATION,
            location = DTO_LOCATION,
        )
    }

    private fun entityType(
        id: LsiSymbolId,
        additionalProps: List<ImmutableProp> = emptyList(),
    ): ImmutableType {
        val idProp = immutableProp(
            ownerTypeId = id,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
        )
        return immutableType(
            id = id,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(idProp) + additionalProps,
            idPropId = idProp.id,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp> = emptyList(),
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
        type: LsiTypeRef = STRING_TYPE,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        associationKind: AssociationKind = AssociationKind.NONE,
        embedded: Boolean = false,
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
            list = false,
            association = associationKind != AssociationKind.NONE,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = when (associationKind) {
                AssociationKind.ONE_TO_ONE,
                AssociationKind.MANY_TO_ONE,
                -> AssociationStorageKind.COLUMN
                else -> AssociationStorageKind.NONE
            },
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private companion object {
        val DTO_SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.UNKNOWN)
        val DTO_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#root")
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val FULL_NAME_TYPE_ID = LsiSymbolId.type("demo.FullName")
        val LOCATION_TYPE_ID = LsiSymbolId.type("demo.Location")
        val BOOK_NAME_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "name")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
    }
}
