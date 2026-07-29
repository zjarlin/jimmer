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

class DtoGeneratedValueTypeExtensionsTest {

    @Test
    fun `resolves user fold scalar list converter and enum values`() {
        val fixture = fixture()

        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true, boxed = true),
            fixture.value(VIEW_TYPE_ID, "userCount", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true),
            fixture.value(VIEW_TYPE_ID, "userCount", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(GENERATED_SUMMARY_TYPE_ID),
            fixture.value(VIEW_TYPE_ID, "summary", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(GENERATED_SUMMARY_TYPE_ID),
            fixture.value(VIEW_TYPE_ID, "summary", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(GENERATED_SPEC_FOLD_TYPE_ID, nullable = true),
            fixture.value(SPEC_TYPE_ID, "folded", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(GENERATED_SPEC_FOLD_TYPE_ID, nullable = true),
            fixture.value(SPEC_TYPE_ID, "folded", LsiLanguage.KOTLIN),
        )

        assertEquals(
            primitive(LsiPrimitiveKind.INT),
            fixture.value(VIEW_TYPE_ID, "score", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true, boxed = true),
            fixture.value(VIEW_TYPE_ID, "optionalScore", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true),
            fixture.value(VIEW_TYPE_ID, "optionalScore", LsiLanguage.KOTLIN),
        )
        assertEquals(
            container(JAVA_LIST_TYPE_ID, declared(JAVA_STRING_TYPE_ID)),
            fixture.value(VIEW_TYPE_ID, "tags", LsiLanguage.JAVA),
        )
        assertEquals(
            container(KOTLIN_LIST_TYPE_ID, declared(KOTLIN_STRING_TYPE_ID)),
            fixture.value(VIEW_TYPE_ID, "tags", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG),
            fixture.value(VIEW_TYPE_ID, "rank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG, nullable = true),
            fixture.value(VIEW_TYPE_ID, "optionalRank", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true, boxed = true),
            fixture.value(VIEW_TYPE_ID, "status", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, nullable = true),
            fixture.value(VIEW_TYPE_ID, "status", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.DOUBLE),
            fixture.value(VIEW_TYPE_ID, "storeRank", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.DOUBLE),
            fixture.value(VIEW_TYPE_ID, "storeRank", LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `resolves every generated target through explicit occurrence callback`() {
        val fixture = fixture()
        assertEquals(
            declared(GENERATED_SUMMARY_TYPE_ID),
            fixture.value(VIEW_TYPE_ID, "summary", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(GENERATED_SPEC_FOLD_TYPE_ID, nullable = true),
            fixture.value(SPEC_TYPE_ID, "folded", LsiLanguage.KOTLIN),
        )
        val cases = listOf(
            "anonymous" to GENERATED_ANONYMOUS_TYPE_ID,
            "named" to GENERATED_NAMED_TYPE_ID,
            "reusable" to GENERATED_REUSABLE_TYPE_ID,
            "recursive" to GENERATED_RECURSIVE_TYPE_ID,
        )

        cases.forEach { (name, expectedTypeId) ->
            assertEquals(
                declared(expectedTypeId, nullable = name == "recursive"),
                fixture.value(VIEW_TYPE_ID, name, LsiLanguage.JAVA),
            )
            assertEquals(
                declared(expectedTypeId, nullable = name == "recursive"),
                fixture.value(VIEW_TYPE_ID, name, LsiLanguage.KOTLIN),
            )
        }
        assertEquals(
            container(JAVA_LIST_TYPE_ID, declared(GENERATED_STORES_TYPE_ID)),
            fixture.value(VIEW_TYPE_ID, "stores", LsiLanguage.JAVA),
        )
        assertEquals(
            container(KOTLIN_LIST_TYPE_ID, declared(GENERATED_STORES_TYPE_ID)),
            fixture.value(VIEW_TYPE_ID, "stores", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(GENERATED_SPEC_TARGET_TYPE_ID),
            fixture.value(SPEC_TYPE_ID, "storeFilter", LsiLanguage.JAVA),
        )

        assertEquals(
            setOf(
                "summary",
                "folded",
                "anonymous",
                "named",
                "reusable",
                "recursive",
                "stores",
                "storeFilter",
            ),
            fixture.requestedGeneratedProps,
        )
    }

    @Test
    fun `resolves accessor element types without property container nullability`() {
        val fixture = fixture()

        assertEquals(
            primitive(LsiPrimitiveKind.INT),
            fixture.element(VIEW_TYPE_ID, "score", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT, boxed = true),
            fixture.element(VIEW_TYPE_ID, "optionalScore", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.INT),
            fixture.element(VIEW_TYPE_ID, "optionalScore", LsiLanguage.KOTLIN),
        )
        assertEquals(
            container(JAVA_LIST_TYPE_ID, declared(JAVA_STRING_TYPE_ID)),
            fixture.element(VIEW_TYPE_ID, "tags", LsiLanguage.JAVA),
        )
        assertEquals(
            container(KOTLIN_LIST_TYPE_ID, declared(KOTLIN_STRING_TYPE_ID)),
            fixture.element(VIEW_TYPE_ID, "tags", LsiLanguage.KOTLIN),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG),
            fixture.element(VIEW_TYPE_ID, "rank", LsiLanguage.KOTLIN),
        )
        assertEquals(
            declared(GENERATED_ANONYMOUS_TYPE_ID),
            fixture.element(VIEW_TYPE_ID, "anonymous", LsiLanguage.JAVA),
        )
        assertEquals(
            declared(GENERATED_REUSABLE_TYPE_ID),
            fixture.element(VIEW_TYPE_ID, "reusable", LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `resolves all specification function value shapes`() {
        val fixture = fixture()

        listOf("nullScore", "notNullScore").forEach { name ->
            assertEquals(
                primitive(LsiPrimitiveKind.BOOLEAN),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.JAVA),
            )
            assertEquals(
                primitive(LsiPrimitiveKind.BOOLEAN),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.KOTLIN),
            )
        }
        listOf("scores", "excludedScores").forEach { name ->
            val nullable = name == "excludedScores"
            assertEquals(
                container(
                    JAVA_COLLECTION_TYPE_ID,
                    primitive(LsiPrimitiveKind.INT, boxed = true),
                    nullable,
                ),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.JAVA),
            )
            assertEquals(
                container(
                    KOTLIN_COLLECTION_TYPE_ID,
                    primitive(LsiPrimitiveKind.INT),
                    nullable,
                ),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.KOTLIN),
            )
        }
        listOf("storeId", "storeIdEq", "storeIdNe").forEach { name ->
            val nullable = name == "storeIdNe"
            assertEquals(
                primitive(LsiPrimitiveKind.INT, nullable, boxed = nullable),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.JAVA),
            )
            assertEquals(
                primitive(LsiPrimitiveKind.INT, nullable),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.KOTLIN),
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
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.JAVA),
            )
            assertEquals(
                container(
                    KOTLIN_COLLECTION_TYPE_ID,
                    primitive(LsiPrimitiveKind.INT),
                    nullable,
                ),
                fixture.value(SPEC_TYPE_ID, name, LsiLanguage.KOTLIN),
            )
        }
        assertEquals(
            primitive(LsiPrimitiveKind.INT),
            fixture.value(SPEC_TYPE_ID, "score", LsiLanguage.JAVA),
        )
        assertEquals(
            primitive(LsiPrimitiveKind.LONG),
            fixture.value(SPEC_TYPE_ID, "convertedRank", LsiLanguage.KOTLIN),
        )
    }

    private fun fixture(): Fixture {
        val bookId = immutableProp(BOOK_TYPE_ID, "id", primitive(LsiPrimitiveKind.LONG), PrimaryMapping.ID)
        val score = immutableProp(BOOK_TYPE_ID, "score", primitive(LsiPrimitiveKind.INT))
        val tags = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "tags",
            type = container(JAVA_LIST_TYPE_ID, declared(JAVA_STRING_TYPE_ID)),
            list = true,
        )
        val rank = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "rank",
            type = primitive(LsiPrimitiveKind.INT),
            converter = converter(primitive(LsiPrimitiveKind.INT), primitive(LsiPrimitiveKind.LONG)),
        )
        val status = immutableProp(BOOK_TYPE_ID, "status", declared(STATUS_TYPE_ID))
        val storeId = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "id",
            type = primitive(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
            converter = converter(primitive(LsiPrimitiveKind.LONG), primitive(LsiPrimitiveKind.INT)),
        )
        val storeRank = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "rank",
            type = primitive(LsiPrimitiveKind.BYTE),
            converter = converter(primitive(LsiPrimitiveKind.BYTE), primitive(LsiPrimitiveKind.DOUBLE)),
        )
        val store = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            type = declared(STORE_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val stores = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "stores",
            type = container(JAVA_LIST_TYPE_ID, declared(STORE_TYPE_ID)),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            list = true,
            associationKind = AssociationKind.MANY_TO_MANY,
        )
        val schema = ImmutableSchema(
            listOf(
                entityType(
                    BOOK_TYPE_ID,
                    listOf(bookId, score, tags, rank, status, store, stores),
                    bookId.id,
                ),
                entityType(STORE_TYPE_ID, listOf(storeId, storeRank), storeId.id),
            ),
        )

        val viewProps = mutableListOf<DtoProp>()
        val viewHiddenProps = mutableListOf<DtoBaseProp>()
        viewProps += userProp("userCount")
        viewProps += foldProp(VIEW_TYPE_ID, "summary", VIEW_SUMMARY_TARGET_TYPE_ID)
        viewProps += baseProp(VIEW_TYPE_ID, "score", score)
        viewProps += baseProp(VIEW_TYPE_ID, "optionalScore", score, nullable = true)
        viewProps += baseProp(VIEW_TYPE_ID, "tags", tags)
        viewProps += baseProp(VIEW_TYPE_ID, "rank", rank)
        viewProps += baseProp(VIEW_TYPE_ID, "optionalRank", rank, nullable = true)
        viewProps += baseProp(
            ownerTypeId = VIEW_TYPE_ID,
            name = "status",
            immutableProp = status,
            nullable = true,
            enumType = DtoEnumType(true, listOf(DtoEnumMapping("ACTIVE", "1"))),
        )
        viewProps += baseProp(
            VIEW_TYPE_ID,
            "anonymous",
            store,
            targetTypeId = ANONYMOUS_TARGET_TYPE_ID,
        )
        viewProps += baseProp(
            VIEW_TYPE_ID,
            "named",
            store,
            targetTypeId = NAMED_TARGET_TYPE_ID,
        )
        viewProps += baseProp(
            ownerTypeId = VIEW_TYPE_ID,
            name = "reusable",
            immutableProp = store,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = "contract.StoreView",
                targetBaseTypeId = STORE_TYPE_ID,
                kind = DtoReusableTypeKind.VIEW,
                location = LOCATION,
            ),
        )
        viewProps += baseProp(
            ownerTypeId = VIEW_TYPE_ID,
            name = "recursive",
            immutableProp = store,
            nullable = true,
            targetTypeId = VIEW_TYPE_ID,
            recursive = true,
        )
        viewProps += baseProp(
            VIEW_TYPE_ID,
            "stores",
            stores,
            targetTypeId = ANONYMOUS_TARGET_TYPE_ID,
        )
        val flatTail = baseProp(
            ownerTypeId = VIEW_TYPE_ID,
            name = "storeRankTail",
            immutableProp = storeRank,
            id = DtoPropId("${VIEW_TYPE_ID.value}#tail:storeRank"),
        )
        val flatHead = baseProp(
            ownerTypeId = VIEW_TYPE_ID,
            name = "storeRank",
            immutableProp = store,
            id = DtoPropId("${VIEW_TYPE_ID.value}#prop:storeRank"),
            nextPropId = flatTail.id,
            tailPropId = flatTail.id,
        )
        viewProps += flatHead
        viewHiddenProps += flatTail

        val specProps = mutableListOf<DtoProp>()
        specProps += baseProp(SPEC_TYPE_ID, "score", score)
        specProps += baseProp(SPEC_TYPE_ID, "nullScore", score, functionName = "null")
        specProps += baseProp(SPEC_TYPE_ID, "notNullScore", score, functionName = "notNull")
        specProps += baseProp(SPEC_TYPE_ID, "scores", score, functionName = "valueIn")
        specProps += baseProp(
            SPEC_TYPE_ID,
            "excludedScores",
            score,
            functionName = "valueNotIn",
            nullable = true,
        )
        specProps += baseProp(SPEC_TYPE_ID, "storeId", store, functionName = "id")
        specProps += baseProp(SPEC_TYPE_ID, "storeIdEq", store, functionName = "associatedIdEq")
        specProps += baseProp(
            SPEC_TYPE_ID,
            "storeIdNe",
            store,
            functionName = "associatedIdNe",
            nullable = true,
        )
        specProps += baseProp(SPEC_TYPE_ID, "storeIds", store, functionName = "associatedIdIn")
        specProps += baseProp(
            SPEC_TYPE_ID,
            "excludedStoreIds",
            store,
            functionName = "associatedIdNotIn",
            nullable = true,
        )
        specProps += baseProp(SPEC_TYPE_ID, "convertedRank", rank, functionName = "ge")
        specProps += baseProp(
            SPEC_TYPE_ID,
            "storeFilter",
            store,
            targetTypeId = SPEC_TARGET_TYPE_ID,
        )
        specProps += foldProp(SPEC_TYPE_ID, "folded", SPEC_FOLD_TARGET_TYPE_ID)

        val types = listOf(
            dtoType(
                id = VIEW_TYPE_ID,
                name = "BookView",
                modifiers = emptySet(),
                props = viewProps,
                hiddenProps = viewHiddenProps,
            ),
            dtoType(SPEC_TYPE_ID, "BookSpecification", setOf(DtoModifier.SPECIFICATION), specProps),
            dtoType(VIEW_SUMMARY_TARGET_TYPE_ID, null, emptySet(), emptyList()),
            dtoType(ANONYMOUS_TARGET_TYPE_ID, null, emptySet(), emptyList(), baseTypeId = STORE_TYPE_ID),
            dtoType(NAMED_TARGET_TYPE_ID, "NamedStoreView", emptySet(), emptyList(), baseTypeId = STORE_TYPE_ID),
            dtoType(SPEC_TARGET_TYPE_ID, null, setOf(DtoModifier.SPECIFICATION), emptyList(), baseTypeId = STORE_TYPE_ID),
            dtoType(SPEC_FOLD_TARGET_TYPE_ID, null, setOf(DtoModifier.SPECIFICATION), emptyList()),
        ).sortedBy(DtoType::id)
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(VIEW_TYPE_ID, SPEC_TYPE_ID),
            types = types,
            props = (viewProps + viewHiddenProps + specProps).sortedBy(DtoProp::id),
        )
        return Fixture(
            graph = graph,
            schema = schema,
            generatedTypeIds = mapOf(
                propId(VIEW_TYPE_ID, "summary") to GENERATED_SUMMARY_TYPE_ID,
                propId(SPEC_TYPE_ID, "folded") to GENERATED_SPEC_FOLD_TYPE_ID,
                propId(VIEW_TYPE_ID, "anonymous") to GENERATED_ANONYMOUS_TYPE_ID,
                propId(VIEW_TYPE_ID, "named") to GENERATED_NAMED_TYPE_ID,
                propId(VIEW_TYPE_ID, "reusable") to GENERATED_REUSABLE_TYPE_ID,
                propId(VIEW_TYPE_ID, "recursive") to GENERATED_RECURSIVE_TYPE_ID,
                propId(VIEW_TYPE_ID, "stores") to GENERATED_STORES_TYPE_ID,
                propId(SPEC_TYPE_ID, "storeFilter") to GENERATED_SPEC_TARGET_TYPE_ID,
            ),
        )
    }

    private fun dtoType(
        id: DtoTypeId,
        name: String?,
        modifiers: Set<DtoModifier>,
        props: List<DtoProp>,
        hiddenProps: List<DtoProp> = emptyList(),
        baseTypeId: LsiSymbolId = BOOK_TYPE_ID,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = name,
            modifiers = modifiers,
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = props.map(DtoProp::id),
            hiddenFlatPropIds = hiddenProps.map(DtoProp::id),
            polymorphism = null,
        )
    }

    private fun baseProp(
        ownerTypeId: DtoTypeId,
        name: String,
        immutableProp: ImmutableProp,
        functionName: String? = null,
        nullable: Boolean = false,
        enumType: DtoEnumType? = null,
        targetTypeId: DtoTypeId? = null,
        targetTypeReference: DtoReusableTypeReference? = null,
        recursive: Boolean = false,
        id: DtoPropId = propId(ownerTypeId, name),
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
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
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = enumType,
            config = null,
            recursive = recursive,
            likeOptions = emptySet(),
        )
    }

    private fun userProp(name: String): DtoUserProp {
        return DtoUserProp(
            id = propId(VIEW_TYPE_ID, name),
            ownerTypeId = VIEW_TYPE_ID,
            name = name,
            alias = name,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("Int", emptyList(), nullable = true, location = LOCATION),
            defaultValueText = null,
        )
    }

    private fun foldProp(ownerTypeId: DtoTypeId, name: String, targetTypeId: DtoTypeId): DtoFoldProp {
        return DtoFoldProp(
            id = propId(ownerTypeId, name),
            ownerTypeId = ownerTypeId,
            name = name,
            alias = name,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = targetTypeId,
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
        list: Boolean = false,
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
            associationStorage = when (associationKind) {
                AssociationKind.MANY_TO_ONE -> AssociationStorageKind.COLUMN
                AssociationKind.MANY_TO_MANY -> AssociationStorageKind.MIDDLE_TABLE
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
        val generatedTypeIds: Map<DtoPropId, LsiSymbolId>,
        val requestedGeneratedProps: MutableSet<String> = linkedSetOf(),
    ) {
        fun value(ownerTypeId: DtoTypeId, name: String, language: LsiLanguage): LsiTypeRef {
            val prop = graph.propsById.getValue(propId(ownerTypeId, name))
            return prop.generatedValueType(graph, schema, language) { generatedProp ->
                requestedGeneratedProps += generatedProp.name
                LsiDeclaredType(generatedTypeIds.getValue(generatedProp.id))
            }
        }

        fun element(ownerTypeId: DtoTypeId, name: String, language: LsiLanguage): LsiTypeRef {
            val prop = graph.propsById.getValue(propId(ownerTypeId, name)) as DtoBaseProp
            return prop.generatedElementValueType(graph, schema, language) { generatedProp ->
                requestedGeneratedProps += generatedProp.name
                LsiDeclaredType(generatedTypeIds.getValue(generatedProp.id))
            }
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val STATUS_TYPE_ID = LsiSymbolId.type("demo.Status")
        val CONVERTER_TYPE_ID = LsiSymbolId.type("demo.ValueConverter")
        val VIEW_TYPE_ID = DtoTypeId("demo.dto.BookView")
        val SPEC_TYPE_ID = DtoTypeId("demo.dto.BookSpecification")
        val VIEW_SUMMARY_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookView#target:summary")
        val ANONYMOUS_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookView#target:anonymous")
        val NAMED_TARGET_TYPE_ID = DtoTypeId("demo.dto.NamedStoreView")
        val SPEC_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#target:store")
        val SPEC_FOLD_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#target:folded")
        val GENERATED_SUMMARY_TYPE_ID = LsiSymbolId.type("demo.dto.BookView.TargetOf_summary")
        val GENERATED_SPEC_FOLD_TYPE_ID = LsiSymbolId.type("demo.dto.BookSpecification.TargetOf_folded")
        val GENERATED_ANONYMOUS_TYPE_ID = LsiSymbolId.type("demo.dto.BookView.TargetOf_anonymous")
        val GENERATED_NAMED_TYPE_ID = LsiSymbolId.type("demo.dto.NamedStoreView")
        val GENERATED_REUSABLE_TYPE_ID = LsiSymbolId.type("contract.StoreView")
        val GENERATED_RECURSIVE_TYPE_ID = LsiSymbolId.type("demo.dto.BookView")
        val GENERATED_STORES_TYPE_ID = LsiSymbolId.type("demo.dto.BookView.TargetOf_stores")
        val GENERATED_SPEC_TARGET_TYPE_ID = LsiSymbolId.type("demo.dto.BookSpecification.TargetOf_storeFilter")
        val JAVA_STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val KOTLIN_STRING_TYPE_ID = LsiSymbolId.type("kotlin.String")

        fun propId(ownerTypeId: DtoTypeId, name: String): DtoPropId {
            return DtoPropId("${ownerTypeId.value}#prop:$name")
        }

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
