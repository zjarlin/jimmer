package org.babyfish.jimmer.compiler.dto

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
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoEnumMapping
import site.addzero.lsi.jimmer.dto.DtoEnumType
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoAccessorPoetTest {

    @Test
    fun `renders flattened slot path without converters byte for byte`() {
        val fixture = fixture()

        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  true,
                  new int[] {
                    demo.BookDraft.Producer.SLOT_STORE,
                    demo.StoreDraft.Producer.SLOT_NAME
                  }
                )
            """.trimIndent(),
            fixture.render(fixture.flatStoreName, LsiLanguage.JAVA, withConverters = false),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  true,
                  intArrayOf(
                    demo.BookDraft.`$`.SLOT_STORE,
                    demo.StoreDraft.`$`.SLOT_NAME
                  )
                )
            """.trimIndent(),
            fixture.render(fixture.flatStoreName, LsiLanguage.KOTLIN, withConverters = false),
        )
    }

    @Test
    fun `renders associated id getter and setter byte for byte`() {
        val fixture = fixture()

        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  false,
                  new int[] { demo.BookDraft.Producer.SLOT_STORE },
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.idReferenceGetter(demo.Store.class, demo.BookProps.STORE.unwrap().getAssociatedIdConverter(false)),
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.idReferenceSetter(demo.Store.class, demo.BookProps.STORE.unwrap().getAssociatedIdConverter(false))
                )
            """.trimIndent(),
            fixture.render(fixture.storeId, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  false,
                  intArrayOf(demo.BookDraft.`$`.SLOT_STORE),
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.idReferenceGetter(demo.Store::class.java, demo.BookProps.STORE.unwrap().getAssociatedIdConverter<kotlin.Any, kotlin.Any>(false)),
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.idReferenceSetter(demo.Store::class.java, demo.BookProps.STORE.unwrap().getAssociatedIdConverter<kotlin.Any, kotlin.Any>(false))
                )
            """.trimIndent(),
            fixture.render(fixture.storeId, LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders object constructor and setter byte for byte`() {
        val fixture = fixture()

        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  true,
                  new int[] { demo.BookDraft.Producer.SLOT_STORE },
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.<demo.Store, demo.dto.BookView.TargetOf_store>objectReferenceGetter(demo.dto.BookView.TargetOf_store::new),
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.objectReferenceSetter(demo.dto.BookView.TargetOf_store::toEntity)
                )
            """.trimIndent(),
            fixture.render(fixture.store, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  true,
                  intArrayOf(demo.BookDraft.`$`.SLOT_STORE),
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.objectReferenceGetter<demo.Store, demo.dto.BookView.TargetOf_store> {
                    demo.dto.BookView.TargetOf_store(it)
                  },
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.objectReferenceSetter<demo.Store, demo.dto.BookView.TargetOf_store> {
                    it.toEntity()
                  }
                )
            """.trimIndent(),
            fixture.render(fixture.store, LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders reusable object metadata converter byte for byte`() {
        val fixture = fixture()

        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  true,
                  new int[] { demo.BookDraft.Producer.SLOT_STORE },
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.<demo.Store, demo.dto.ReusableStoreInput>objectReferenceGetter(demo.dto.ReusableStoreInput.METADATA.getConverter()),
                  org.babyfish.jimmer.impl.util.DtoPropAccessor.objectReferenceSetter(demo.dto.ReusableStoreInput::toImmutable)
                )
            """.trimIndent(),
            fixture.render(fixture.reusableStore, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  true,
                  intArrayOf(demo.BookDraft.`$`.SLOT_STORE),
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.objectReferenceGetter<demo.Store, demo.dto.ReusableStoreInput>(demo.dto.ReusableStoreInput.METADATA.converter),
                  org.babyfish.jimmer.`impl`.util.DtoPropAccessor.objectReferenceSetter<demo.Store, demo.dto.ReusableStoreInput> {
                    it.toImmutable()
                  }
                )
            """.trimIndent(),
            fixture.render(fixture.reusableStore, LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders scalar converter and specification enum semantics byte for byte`() {
        val fixture = fixture()

        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  true,
                  new int[] { demo.BookDraft.Producer.SLOT_CODE },
                  arg -> demo.BookProps.CODE.unwrap().getConverter().output(arg),
                  arg -> demo.BookProps.CODE.unwrap().getConverter().input(arg)
                )
            """.trimIndent(),
            fixture.render(fixture.code, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  true,
                  intArrayOf(demo.BookDraft.`$`.SLOT_CODE),
                  { demo.BookProps.CODE.unwrap().getConverter<kotlin.Any, kotlin.Any>().output(it) },
                  { demo.BookProps.CODE.unwrap().getConverter<kotlin.Any, kotlin.Any>().input(it) }
                )
            """.trimIndent(),
            fixture.render(fixture.code, LsiLanguage.KOTLIN),
        )

        val specification = fixture.asSpecification()
        assertEquals(
            """
                new org.babyfish.jimmer.impl.util.DtoPropAccessor(
                  true,
                  new int[] { demo.BookDraft.Producer.SLOT_GENDER },
                  null,
                  arg -> {
                    switch ((int)arg) {
                      case 1:
                        return demo.Gender.MALE;
                      case 2:
                        return demo.Gender.FEMALE;
                      default:
                        throw new IllegalArgumentException("Illegal value `\"" + arg + "\"`for enum type: \"demo.Gender\"");
                    }
                  }
                )
            """.trimIndent(),
            specification.render(specification.gender, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                org.babyfish.jimmer.`impl`.util.DtoPropAccessor(
                  true,
                  intArrayOf(demo.BookDraft.`$`.SLOT_GENDER),
                  null,
                  {
                    when (it as kotlin.Int) {
                      1 -> demo.Gender.MALE
                      2 -> demo.Gender.FEMALE
                      else -> throw IllegalArgumentException(
                        "Illegal value \"" + it + "\" for the enum type \"demo.Gender\""
                      )
                    }
                  }
                )
            """.trimIndent(),
            specification.render(specification.gender, LsiLanguage.KOTLIN),
        )
    }

    private fun fixture(): Fixture {
        val storeId = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
        )
        val storeName = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            name = "name",
            type = LsiDeclaredType(STRING_TYPE_ID),
        )
        val bookId = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
        )
        val store = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            type = LsiDeclaredType(STORE_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
        )
        val code = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "code",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            converter = ImmutableConverter(
                converterTypeId = CONVERTER_TYPE_ID,
                sourceType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                targetType = LsiDeclaredType(STRING_TYPE_ID),
                sourceNullable = false,
                targetNullable = false,
                propertyNullable = false,
            ),
        )
        val gender = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "gender",
            type = LsiDeclaredType(GENDER_TYPE_ID),
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(BOOK_TYPE_ID, listOf(bookId, store, code, gender), bookId.id),
                immutableType(STORE_TYPE_ID, listOf(storeId, storeName), storeId.id),
            )
        )
        val dtoCode = dtoProp("code", code)
        val dtoGender = dtoProp(
            name = "gender",
            immutableProp = gender,
            enumType = DtoEnumType(
                numeric = true,
                mappings = listOf(
                    DtoEnumMapping("MALE", "1"),
                    DtoEnumMapping("FEMALE", "2"),
                ),
            ),
        )
        val dtoStore = dtoProp("store", store, targetTypeId = TARGET_DTO_TYPE_ID)
        val reusableStore = dtoProp(
            name = "reusableStore",
            immutableProp = store,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = REUSABLE_DTO_TYPE_NAME.canonicalName,
                targetBaseTypeId = STORE_TYPE_ID,
                kind = DtoTypeKind.INPUT,
                location = LOCATION,
            ),
        )
        val dtoStoreId = dtoProp("storeId", store, functionName = "id")
        val flatTail = dtoProp(
            name = "flatStoreNameTail",
            immutableProp = storeName,
            id = DtoPropId("${ROOT_DTO_TYPE_ID.value}#hidden:flatStoreNameTail"),
        )
        val flatStoreName = dtoProp(
            name = "flatStoreName",
            immutableProp = store,
            nextPropId = flatTail.id,
            tailPropId = flatTail.id,
        )
        val rootType = dtoType(
            id = ROOT_DTO_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            name = "BookView",
            propIds = listOf(
                dtoCode.id,
                dtoGender.id,
                dtoStore.id,
                reusableStore.id,
                dtoStoreId.id,
                flatStoreName.id,
            ),
            hiddenFlatPropIds = listOf(flatTail.id),
        )
        val targetType = dtoType(
            id = TARGET_DTO_TYPE_ID,
            baseTypeId = STORE_TYPE_ID,
            name = null,
        )
        return Fixture(
            graph = DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(rootType.id),
                types = listOf(rootType, targetType).sortedBy(DtoType::id),
                props = listOf(
                    dtoCode,
                    dtoGender,
                    dtoStore,
                    reusableStore,
                    dtoStoreId,
                    flatStoreName,
                    flatTail,
                ).sortedBy(DtoProp::id),
            ),
            schema = schema,
            code = dtoCode,
            gender = dtoGender,
            store = dtoStore,
            reusableStore = reusableStore,
            storeId = dtoStoreId,
            flatStoreName = flatStoreName,
        )
    }

    private fun dtoProp(
        name: String,
        immutableProp: ImmutableProp,
        id: DtoPropId = DtoPropId("${ROOT_DTO_TYPE_ID.value}#prop:$name"),
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
        functionName: String? = null,
        targetTypeId: DtoTypeId? = null,
        targetTypeReference: DtoReusableTypeReference? = null,
        enumType: DtoEnumType? = null,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ROOT_DTO_TYPE_ID,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding(immutableProp.name, immutableProp.id)),
            basePath = immutableProp.name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = enumType,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun dtoType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String?,
        propIds: List<DtoPropId> = emptyList(),
        hiddenFlatPropIds: List<DtoPropId> = emptyList(),
        modifiers: Set<DtoModifier> = emptySet(),
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
            propIds = propIds,
            hiddenFlatPropIds = hiddenFlatPropIds,
            polymorphism = null,
        )
    }

    private fun immutableType(
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
        type: LsiType,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        targetTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
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
            associationStorage = associationStorage,
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
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val code: DtoBaseProp,
        val gender: DtoBaseProp,
        val store: DtoBaseProp,
        val reusableStore: DtoBaseProp,
        val storeId: DtoBaseProp,
        val flatStoreName: DtoBaseProp,
    ) {
        fun render(
            prop: DtoBaseProp,
            targetLanguage: LsiLanguage,
            withConverters: Boolean = true,
        ): String {
            val initializer = prop.toAccessorInitializerPoetCodeBlock(
                graph = graph,
                immutableSchema = schema,
                workspace = LsiWorkspace.EMPTY,
                targetLanguage = targetLanguage,
                acceptNull = prop != storeId,
                withConverters = withConverters,
                generatedTargetType = { targetProp ->
                    val typeName = if ((targetProp as? DtoBaseProp)?.targetTypeReference != null) {
                        REUSABLE_DTO_TYPE_NAME
                    } else {
                        TARGET_DTO_GENERATED_TYPE_NAME
                    }
                    LsiDeclaredType(typeName.typeId)
                },
            )
            val typeNames = LsiWorkspace.EMPTY.dtoAccessorPoetTypeNames(
                initializer = initializer,
                immutableSchema = schema,
                generatedTypeNames = EXPLICIT_TYPE_NAMES,
            )
            return when (targetLanguage) {
                LsiLanguage.JAVA -> LsiJavaPoetRenderer().renderCodeBlock(initializer, typeNames).toString()
                LsiLanguage.KOTLIN -> LsiKotlinPoetRenderer().renderCodeBlock(initializer, typeNames).toString()
                LsiLanguage.UNKNOWN -> error("测试只支持 Java 或 Kotlin")
            }
        }

        fun asSpecification(): Fixture {
            val rootType = graph.typesById.getValue(ROOT_DTO_TYPE_ID).copy(
                modifiers = setOf(DtoModifier.SPECIFICATION),
            )
            return copy(
                graph = DtoGraph(
                    source = graph.source,
                    rootTypeIds = graph.rootTypeIds,
                    types = graph.types.map { type ->
                        if (type.id == rootType.id) rootType else type
                    }.sortedBy(DtoType::id),
                    props = graph.props,
                )
            )
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val GENDER_TYPE_ID = LsiSymbolId.type("demo.Gender")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val CONVERTER_TYPE_ID = LsiSymbolId.type("demo.LongToStringConverter")
        val ROOT_DTO_TYPE_ID = DtoTypeId("demo.dto.BookView#root")
        val TARGET_DTO_TYPE_ID = DtoTypeId("demo.dto.BookView#target:store")
        val TARGET_DTO_GENERATED_TYPE_NAME = JimmerDtoPoetTypeNames.create(
            "demo.dto",
            listOf("BookView", "TargetOf_store"),
        )
        val REUSABLE_DTO_TYPE_NAME = JimmerDtoPoetTypeNames.create(
            "demo.dto",
            listOf("ReusableStoreInput"),
        )
        val EXPLICIT_TYPE_NAMES: List<LsiTypeName> = listOf(
            JimmerDtoPoetTypeNames.create("demo", listOf("Book")),
            JimmerDtoPoetTypeNames.create("demo", listOf("Store")),
            JimmerDtoPoetTypeNames.create("demo", listOf("Gender")),
            TARGET_DTO_GENERATED_TYPE_NAME,
            REUSABLE_DTO_TYPE_NAME,
        )
    }
}
