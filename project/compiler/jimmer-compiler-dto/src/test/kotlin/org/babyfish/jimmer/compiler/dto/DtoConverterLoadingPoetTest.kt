package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.immutable.toLsiGeneratedQueryPoetTypeNames
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
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoConverterLoadingPoetTest {

    @Test
    fun `renders inherited scalar converter from declaring Props for Java and Kotlin`() {
        val fixture = fixture()

        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter()",
            fixture.render(fixture.scalarProp, LsiLanguage.JAVA, forList = false),
        )
        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter()",
            fixture.render(fixture.scalarProp, LsiLanguage.JAVA, forList = true),
        )
        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter<kotlin.Any, kotlin.Any>()",
            fixture.render(fixture.scalarProp, LsiLanguage.KOTLIN, forList = false),
        )
        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter<kotlin.Any, kotlin.Any>()",
            fixture.render(fixture.scalarProp, LsiLanguage.KOTLIN, forList = true),
        )
    }

    @Test
    fun `renders association converter with list flag for Java and Kotlin`() {
        val fixture = fixture()

        assertEquals(
            "demo.BookProps.STORE.unwrap().getAssociatedIdConverter(false)",
            fixture.render(fixture.associationProp, LsiLanguage.JAVA, forList = false),
        )
        assertEquals(
            "demo.BookProps.STORE.unwrap().getAssociatedIdConverter(true)",
            fixture.render(fixture.associationProp, LsiLanguage.JAVA, forList = true),
        )
        assertEquals(
            "demo.BookProps.STORE.unwrap().getAssociatedIdConverter<kotlin.Any, kotlin.Any>(false)",
            fixture.render(fixture.associationProp, LsiLanguage.KOTLIN, forList = false),
        )
        assertEquals(
            "demo.BookProps.STORE.unwrap().getAssociatedIdConverter<kotlin.Any, kotlin.Any>(true)",
            fixture.render(fixture.associationProp, LsiLanguage.KOTLIN, forList = true),
        )
    }

    @Test
    fun `keeps explicit false list flag for to-many association`() {
        val fixture = fixture()

        assertEquals(
            "demo.BookProps.STORES.unwrap().getAssociatedIdConverter(false)",
            fixture.render(fixture.listAssociationProp, LsiLanguage.JAVA, forList = false),
        )
        assertEquals(
            "demo.BookProps.STORES.unwrap().getAssociatedIdConverter<kotlin.Any, kotlin.Any>(false)",
            fixture.render(fixture.listAssociationProp, LsiLanguage.KOTLIN, forList = false),
        )
    }

    @Test
    fun `loads flattened converter from tail binding and declaring Props`() {
        val fixture = fixture()

        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter()",
            fixture.render(fixture.flattenedProp, LsiLanguage.JAVA, forList = false),
        )
        assertEquals(
            "demo.BaseProps.CODE.unwrap().getConverter<kotlin.Any, kotlin.Any>()",
            fixture.render(fixture.flattenedProp, LsiLanguage.KOTLIN, forList = false),
        )
    }

    private fun fixture(): Fixture {
        val baseCode = immutableProp(
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = BASE_TYPE_ID,
            name = "code",
            type = LsiDeclaredType(STRING_TYPE_ID),
        )
        val inheritedBookCode = baseCode.copy(
            id = LsiSymbolId.property(BOOK_TYPE_ID, baseCode.name),
            declarationId = baseCode.id,
            ownerTypeId = BOOK_TYPE_ID,
            overrideChain = listOf(baseCode.id),
            inherited = true,
        )
        val bookId = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            declaringTypeId = BOOK_TYPE_ID,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
        )
        val storeId = immutableProp(
            ownerTypeId = STORE_TYPE_ID,
            declaringTypeId = STORE_TYPE_ID,
            name = "id",
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            primaryMapping = PrimaryMapping.ID,
        )
        val store = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            declaringTypeId = BOOK_TYPE_ID,
            name = "store",
            type = LsiDeclaredType(STORE_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
        )
        val stores = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            declaringTypeId = BOOK_TYPE_ID,
            name = "stores",
            type = LsiDeclaredType(
                declarationId = LIST_TYPE_ID,
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(STORE_TYPE_ID))),
            ),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = STORE_TYPE_ID,
            list = true,
            associationKind = AssociationKind.MANY_TO_MANY,
            associationStorage = AssociationStorageKind.MIDDLE_TABLE,
        )
        val inheritedStoreCode = baseCode.copy(
            id = LsiSymbolId.property(STORE_TYPE_ID, baseCode.name),
            declarationId = baseCode.id,
            ownerTypeId = STORE_TYPE_ID,
            overrideChain = listOf(baseCode.id),
            inherited = true,
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    id = BASE_TYPE_ID,
                    kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
                    props = listOf(baseCode),
                ),
                immutableType(
                    id = BOOK_TYPE_ID,
                    kind = ImmutableTypeKind.ENTITY,
                    props = listOf(bookId, inheritedBookCode, store, stores),
                    superTypeIds = listOf(BASE_TYPE_ID),
                    primarySuperTypeId = BASE_TYPE_ID,
                    idPropId = bookId.id,
                ),
                immutableType(
                    id = STORE_TYPE_ID,
                    kind = ImmutableTypeKind.ENTITY,
                    props = listOf(storeId, inheritedStoreCode),
                    superTypeIds = listOf(BASE_TYPE_ID),
                    primarySuperTypeId = BASE_TYPE_ID,
                    idPropId = storeId.id,
                ),
            ),
        )
        val scalarDtoProp = dtoProp("code", inheritedBookCode)
        val associationDtoProp = dtoProp("store", store)
        val listAssociationDtoProp = dtoProp("stores", stores)
        val flattenedTail = dtoProp(
            name = "storeCodeTail",
            immutableProp = inheritedStoreCode,
            id = DtoPropId("${DTO_TYPE_ID.value}#flat-tail:storeCode"),
        )
        val flattenedDtoProp = dtoProp(
            name = "storeCode",
            immutableProp = store,
            id = DtoPropId("${DTO_TYPE_ID.value}#flat-head:storeCode"),
            nextPropId = flattenedTail.id,
            tailPropId = flattenedTail.id,
        )
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(
                scalarDtoProp.id,
                associationDtoProp.id,
                listAssociationDtoProp.id,
                flattenedDtoProp.id,
            ),
            hiddenFlatPropIds = listOf(flattenedTail.id),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(dtoType.id),
            types = listOf(dtoType),
            props = listOf(
                scalarDtoProp,
                associationDtoProp,
                listAssociationDtoProp,
                flattenedDtoProp,
                flattenedTail,
            ).sortedBy(DtoProp::id),
        )
        return Fixture(
            graph = graph,
            schema = schema,
            scalarProp = scalarDtoProp,
            associationProp = associationDtoProp,
            listAssociationProp = listAssociationDtoProp,
            flattenedProp = flattenedDtoProp,
        )
    }

    private fun dtoProp(
        name: String,
        immutableProp: ImmutableProp,
        id: DtoPropId = DtoPropId("${DTO_TYPE_ID.value}#$name"),
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding(name, immutableProp.id)),
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp>,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        idPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = kind,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = superTypeIds,
            props = props,
            primarySuperTypeId = primarySuperTypeId,
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
        declaringTypeId: LsiSymbolId,
        name: String,
        type: LsiType,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        targetTypeId: LsiSymbolId? = null,
        list: Boolean = false,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = declaringTypeId,
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
            associationStorage = associationStorage,
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
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val scalarProp: DtoBaseProp,
        val associationProp: DtoBaseProp,
        val listAssociationProp: DtoBaseProp,
        val flattenedProp: DtoBaseProp,
    ) {
        fun render(prop: DtoBaseProp, language: LsiLanguage, forList: Boolean): String {
            val codeBlock = prop.toLsiConverterLoadingPoetCodeBlock(
                graph = graph,
                immutableSchema = schema,
                targetLanguage = language,
                forList = forList,
                typeArguments = if (language == LsiLanguage.KOTLIN) {
                    listOf(KOTLIN_ANY_TYPE, KOTLIN_ANY_TYPE)
                } else {
                    emptyList()
                },
            )
            val typeNames = DTO_COMMON_POET_TYPE_NAMES + schema.toLsiGeneratedQueryPoetTypeNames()
            return when (language) {
                LsiLanguage.JAVA -> LsiJavaPoetRenderer().renderCodeBlock(codeBlock, typeNames).toString()
                LsiLanguage.KOTLIN -> LsiKotlinPoetRenderer().renderCodeBlock(codeBlock, typeNames).toString()
                LsiLanguage.UNKNOWN -> error("测试只支持 Java 或 Kotlin")
            }
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Base")
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val LIST_TYPE_ID = LsiSymbolId.type("java.util.List")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookView")
        val KOTLIN_ANY_TYPE = LsiDeclaredType(LsiSymbolId.type("kotlin.Any"))
    }
}
