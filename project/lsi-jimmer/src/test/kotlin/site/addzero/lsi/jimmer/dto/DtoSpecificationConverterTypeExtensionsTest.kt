package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
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
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef

class DtoSpecificationConverterTypeExtensionsTest {

    @Test
    fun `resolves converter and enum types with platform nullability`() {
        val fixture = fixture()

        assertEquals(
            primitive(LsiPrimitiveKind.LONG),
            fixture.input("rank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, boxed = true),
            fixture.output("rank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG),
            fixture.input("rank", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT),
            fixture.output("rank", LsiLanguage.KOTLIN),
        )

        assertEquals(
            primitive(LsiPrimitiveKind.LONG, nullable = true, boxed = true),
            fixture.input("optionalRank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true, boxed = true),
            fixture.output("optionalRank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG, nullable = true),
            fixture.input("optionalRank", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true),
            fixture.output("optionalRank", LsiLanguage.KOTLIN),
        )

        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true, boxed = true),
            fixture.input("numericStatus", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(STATUS_TYPE_ID, nullable = true),
            fixture.output("numericStatus", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true),
            fixture.input("numericStatus", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(STATUS_TYPE_ID, nullable = true),
            fixture.output("numericStatus", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(JAVA_STRING_TYPE_ID),
            fixture.input("textStatus", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(KOTLIN_STRING_TYPE_ID),
            fixture.input("textStatus", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(STATUS_TYPE_ID),
            fixture.output("textStatus", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(STATUS_TYPE_ID),
            fixture.output("textStatus", LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `resolves value collection converter types without losing element boxing`() {
        val fixture = fixture()

        assertEquals(
            container(
                JAVA_COLLECTION_TYPE_ID,
                primitive(LsiPrimitiveKind.LONG, boxed = true),
            ),
            fixture.input("ranks", LsiLanguage.JAVA),
        )
        assertEquals(
            container(
                JAVA_LIST_TYPE_ID,
                primitive(LsiPrimitiveKind.INT, boxed = true),
            ),
            fixture.output("ranks", LsiLanguage.JAVA),
        )
        assertEquals(
            container(
                KOTLIN_COLLECTION_TYPE_ID,
                primitive(LsiPrimitiveKind.LONG),
            ),
            fixture.input("ranks", LsiLanguage.KOTLIN),
        )
        assertEquals(
            container(
                KOTLIN_LIST_TYPE_ID,
                primitive(LsiPrimitiveKind.INT),
            ),
            fixture.output("ranks", LsiLanguage.KOTLIN),
        )

        assertEquals(
            container(
                JAVA_COLLECTION_TYPE_ID,
                primitive(LsiPrimitiveKind.LONG, boxed = true),
                nullable = true,
            ),
            fixture.input("excludedRanks", LsiLanguage.JAVA),
        )
        assertEquals(
            container(
                JAVA_LIST_TYPE_ID,
                primitive(LsiPrimitiveKind.INT, boxed = true),
                nullable = true,
            ),
            fixture.output("excludedRanks", LsiLanguage.JAVA),
        )
        assertEquals(
            container(
                KOTLIN_COLLECTION_TYPE_ID,
                primitive(LsiPrimitiveKind.LONG),
                nullable = true,
            ),
            fixture.input("excludedRanks", LsiLanguage.KOTLIN),
        )
        assertEquals(
            container(
                KOTLIN_LIST_TYPE_ID,
                primitive(LsiPrimitiveKind.INT),
                nullable = true,
            ),
            fixture.output("excludedRanks", LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `resolves associated id converter types for scalar and collection predicates`() {
        val fixture = fixture()

        listOf("storeId", "excludedStoreId").forEach { name ->
            val nullable = name == "excludedStoreId"
            assertEquals(
                primitive(LsiPrimitiveKind.INT, nullable, boxed = nullable),
                fixture.input(name, LsiLanguage.JAVA),
            )
            assertEquals(
                primitive(LsiPrimitiveKind.LONG, nullable, boxed = true),
                fixture.output(name, LsiLanguage.JAVA),
            )
            assertEquals(
                primitive(LsiPrimitiveKind.INT, nullable),
                fixture.input(name, LsiLanguage.KOTLIN),
            )
            assertEquals(
                primitive(LsiPrimitiveKind.LONG, nullable),
                fixture.output(name, LsiLanguage.KOTLIN),
            )
        }

        listOf("storeIds", "excludedStoreIds").forEach { name ->
            val nullable = name == "excludedStoreIds"
            assertEquals(
                container(
                    JAVA_COLLECTION_TYPE_ID,
                    primitive(LsiPrimitiveKind.INT, boxed = true),
                    nullable,
                ),
                fixture.input(name, LsiLanguage.JAVA),
            )
            assertEquals(
                container(
                    JAVA_LIST_TYPE_ID,
                    primitive(LsiPrimitiveKind.LONG, boxed = true),
                    nullable,
                ),
                fixture.output(name, LsiLanguage.JAVA),
            )
            assertEquals(
                container(
                    KOTLIN_COLLECTION_TYPE_ID,
                    primitive(LsiPrimitiveKind.INT),
                    nullable,
                ),
                fixture.input(name, LsiLanguage.KOTLIN),
            )
            assertEquals(
                container(
                    KOTLIN_LIST_TYPE_ID,
                    primitive(LsiPrimitiveKind.LONG),
                    nullable,
                ),
                fixture.output(name, LsiLanguage.KOTLIN),
            )
        }
    }

    @Test
    fun `uses flattened tail converter types`() {
        val fixture = fixture()

        assertEquals(
            primitive(LsiPrimitiveKind.DOUBLE),
            fixture.input("storeRank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.BYTE, boxed = true),
            fixture.output("storeRank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.DOUBLE),
            fixture.input("storeRank", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.BYTE),
            fixture.output("storeRank", LsiLanguage.KOTLIN),
        )
    }

    private fun fixture(): Fixture {
        val bookId = immutableProp(BOOK_TYPE_ID, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), PrimaryMapping.ID)
        val rank = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "rank",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            converter = converter(
                sourceType = LsiPrimitiveType(LsiPrimitiveKind.INT),
                targetType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            ),
        )
        val status = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "status",
            type = LsiDeclaredType(STATUS_TYPE_ID),
        )
        val storeId = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
            converter = converter(
                sourceType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                targetType = LsiPrimitiveType(LsiPrimitiveKind.INT),
            ),
        )
        val storeRank = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "rank",
            type = LsiPrimitiveType(LsiPrimitiveKind.BYTE),
            converter = converter(
                sourceType = LsiPrimitiveType(LsiPrimitiveKind.BYTE),
                targetType = LsiPrimitiveType(LsiPrimitiveKind.DOUBLE),
            ),
        )
        val store = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            type = LsiDeclaredType(STORE_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val schema = ImmutableSchema(
            listOf(
                entityType(BOOK_TYPE_ID, listOf(bookId, rank, status, store), bookId.id),
                entityType(STORE_TYPE_ID, listOf(storeId, storeRank), storeId.id),
            ),
        )

        val visibleProps = mutableListOf<DtoBaseProp>()
        fun add(
            name: String,
            immutableProp: ImmutableProp,
            functionName: String? = null,
            nullable: Boolean = false,
            enumType: DtoEnumType? = null,
        ): DtoBaseProp {
            return dtoProp(name, immutableProp, functionName, nullable, enumType).also(visibleProps::add)
        }
        add("rank", rank)
        add("optionalRank", rank, nullable = true)
        add("ranks", rank, functionName = "valueIn")
        add("excludedRanks", rank, functionName = "valueNotIn", nullable = true)
        add(
            name = "numericStatus",
            immutableProp = status,
            nullable = true,
            enumType = DtoEnumType(true, listOf(DtoEnumMapping("ACTIVE", "1"))),
        )
        add(
            name = "textStatus",
            immutableProp = status,
            enumType = DtoEnumType(false, listOf(DtoEnumMapping("ACTIVE", "A"))),
        )
        add("storeId", store, functionName = "associatedIdEq")
        add("excludedStoreId", store, functionName = "associatedIdNe", nullable = true)
        add("storeIds", store, functionName = "associatedIdIn")
        add("excludedStoreIds", store, functionName = "associatedIdNotIn", nullable = true)

        val tail = dtoProp(
            name = "storeRankTail",
            immutableProp = storeRank,
            id = DtoPropId("${DTO_TYPE_ID.value}#tail:storeRank"),
        )
        val flattened = dtoProp(
            name = "storeRank",
            immutableProp = store,
            id = DtoPropId("${DTO_TYPE_ID.value}#prop:storeRank"),
            nextPropId = tail.id,
            tailPropId = tail.id,
        )
        visibleProps += flattened
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookSpecification",
            modifiers = setOf(DtoModifier.SPECIFICATION),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = visibleProps.map(DtoProp::id),
            hiddenFlatPropIds = listOf(tail.id),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(type.id),
            types = listOf(type),
            props = (visibleProps + tail).sortedBy(DtoProp::id),
        )
        return Fixture(graph, schema, visibleProps.associateBy(DtoProp::name))
    }

    private fun dtoProp(
        name: String,
        immutableProp: ImmutableProp,
        functionName: String? = null,
        nullable: Boolean = false,
        enumType: DtoEnumType? = null,
        id: DtoPropId = DtoPropId("${DTO_TYPE_ID.value}#prop:$name"),
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = null,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding(immutableProp.name, immutableProp.id)),
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = immutableProp.nullable,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = null,
            enumType = enumType,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun entityType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        idPropId: LsiSymbolId,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = props,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = true,
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
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        targetTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
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
            embedded = false,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = if (associationKind == AssociationKind.MANY_TO_ONE) {
                AssociationStorageKind.COLUMN
            } else {
                AssociationStorageKind.NONE
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

    private fun converter(sourceType: LsiTypeRef, targetType: LsiTypeRef): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = CONVERTER_TYPE_ID,
            sourceType = sourceType,
            targetType = targetType,
            sourceNullable = false,
            targetNullable = false,
            propertyNullable = false,
        )
    }

    private data class Fixture(
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val props: Map<String, DtoBaseProp>,
    ) {
        fun input(name: String, language: LsiLanguage): LsiTypeRef {
            return props.getValue(name).specificationConverterInputType(graph, schema, language)
        }

        fun output(name: String, language: LsiLanguage): LsiTypeRef {
            return props.getValue(name).specificationConverterOutputType(graph, schema, language)
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookSpecification")
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val STATUS_TYPE_ID = LsiSymbolId.type("demo.Status")
        val CONVERTER_TYPE_ID = LsiSymbolId.type("demo.ValueConverter")
        val JAVA_STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val KOTLIN_STRING_TYPE_ID = LsiSymbolId.type("kotlin.String")
        val JAVA_COLLECTION_TYPE_ID = LsiSymbolId.type("java.util.Collection")
        val KOTLIN_COLLECTION_TYPE_ID = LsiSymbolId.type("kotlin.collections.Collection")
        val JAVA_LIST_TYPE_ID = LsiSymbolId.type("java.util.List")
        val KOTLIN_LIST_TYPE_ID = LsiSymbolId.type("kotlin.collections.List")

        fun primitive(
            kind: LsiPrimitiveKind,
            nullable: Boolean = false,
            boxed: Boolean = false,
        ): LsiPrimitiveType {
            return LsiPrimitiveType(
                kind = kind,
                nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL,
                boxed = boxed,
            )
        }

        fun declared(typeId: LsiSymbolId, nullable: Boolean = false): LsiDeclaredType {
            return LsiDeclaredType(
                declarationId = typeId,
                nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL,
            )
        }

        fun container(
            typeId: LsiSymbolId,
            elementType: LsiTypeRef,
            nullable: Boolean = false,
        ): LsiDeclaredType {
            return LsiDeclaredType(
                declarationId = typeId,
                arguments = listOf(LsiTypeArgument.invariant(elementType)),
                nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL,
            )
        }
    }
}
