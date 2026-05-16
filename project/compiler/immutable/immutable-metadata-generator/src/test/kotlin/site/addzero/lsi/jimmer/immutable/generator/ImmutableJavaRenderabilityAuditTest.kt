package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderSetterMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorDeepPropIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorPropCaseMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.renderJavaSource

class ImmutableJavaRenderabilityAuditTest {

    @Test
    fun `builder generator renders non-null setter through shared lsi statements`() {
        val builder = BuilderGenerator(
            ImmutableBuilderTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                producerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                draftImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.DraftImpl"),
                visibleSlotNames = listOf("STORE"),
                setters = listOf(
                    ImmutableBuilderSetterMetadata(
                        name = "store",
                        parameterLsiTypeName = ImmutableGeneratorTestFixtures.className("test.model.Store"),
                        returnTypeName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Builder"),
                        ownerProducerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                        slotName = "STORE",
                        isNullable = false,
                        lsiAnnotations = emptyList(),
                    )
                ),
            )
        ).generate()

        val source = LsiFileSpec(
            packageName = "test.model",
            name = "BookDraft",
            types = listOf(
                LsiTypeSpec(
                    name = "BookDraft",
                    kind = LsiTypeSpecKind.INTERFACE,
                    nestedTypes = listOf(builder),
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("if ((store != null))") || source.contains("if (store != null)"),
            source,
        )
        assertTrue(source.contains("__draft.setStore(store);"), source)
        assertTrue(source.contains("__draft.__show(PropId.byIndex(Producer.STORE), true);"), source)
    }

    @Test
    fun `implementor generator renders switch dispatch through shared lsi statements`() {
        val implementor = ImplementorGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplementorTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                producerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                typeDescription = "test.model.Book",
                propertyOrderNames = listOf("id", "name"),
                getCases = listOf(
                    ImmutableImplementorPropCaseMetadata(name = "id", slotName = "ID"),
                    ImmutableImplementorPropCaseMetadata(name = "name", slotName = "NAME"),
                ),
                deeperPropIds = listOf(
                    ImmutableImplementorDeepPropIdMetadata(
                        constantName = "STORE_ID",
                        propName = "store"
                    )
                ),
            )
        ).generate()

        val source = LsiFileSpec(
            packageName = "test.model",
            name = "BookDraft",
            types = listOf(
                LsiTypeSpec(
                    name = "Producer",
                    kind = LsiTypeSpecKind.CLASS,
                    nestedTypes = listOf(implementor),
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("switch (prop.asIndex())"), source)
        assertTrue(source.contains("case -1:"), source)
        assertTrue(source.contains("return __get(prop.asName());"), source)
        assertTrue(source.contains("case ID:"), source)
        assertTrue(source.contains("return id;"), source)
        assertTrue(source.contains("default:"), source)
        assertTrue(source.contains("throw new IllegalArgumentException("), source)
        assertTrue(
            source.contains("public static final PropId STORE_ID") ||
                source.contains("PropId STORE_ID ="),
            source
        )
    }

    @Test
    fun `impl generator renders standard getter and loaded checks without kotlin null operators`() {
        val impl = ImplGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                implementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                propsSize = 1,
                typeDescription = "test.model.Book",
                fieldProps = listOf(
                    ImmutableImplFieldMetadata(
                        valueFieldName = "__nameValue",
                        valueFieldTypeName = nullableClassName("kotlin.String"),
                        valueFieldDefaultValueKind = ImmutableImplDefaultValueKind.NULL,
                        loadedFieldName = "__nameLoaded",
                    )
                ),
                getterProps = listOf(
                    ImmutableImplGetterPropMetadata(
                        name = "name",
                        typeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
                        description = null,
                        kind = ImmutableImplGetterPropKind.STANDARD,
                        isNullable = false,
                        valueFieldName = "__nameValue",
                        loadedFieldName = "__nameLoaded",
                        declaringTypeClassName = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                    )
                ),
                stateProps = listOf(
                    ImmutableImplStatePropMetadata(
                        name = "name",
                        typeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
                        slotName = "NAME",
                        valueFieldName = "__nameValue",
                        loadedFieldName = "__nameLoaded",
                        isNullable = false,
                        isAssociation = false,
                        isId = false,
                        loadKind = ImmutableImplLoadKind.STANDARD,
                    )
                ),
                hiddenSlotNames = emptyList(),
            )
        ).generate()

        val source = renderImplSource(impl)

        assertTrue(source.contains("if ((this.__nameLoaded == false))"), source)
        assertTrue(source.contains("return this.__nameLoaded;"), source)
        assertTrue(!source.contains("!=="), source)
        assertTrue(!source.contains("==="), source)
    }

    @Test
    fun `impl generator renders nullable id-view getter through semantic property access`() {
        val impl = ImplGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                implementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                propsSize = 1,
                typeDescription = "test.model.Book",
                fieldProps = listOf(
                    ImmutableImplFieldMetadata(
                        valueFieldName = "__storeValue",
                        valueFieldTypeName = nullableClassName("test.model.Store"),
                        valueFieldDefaultValueKind = ImmutableImplDefaultValueKind.NULL,
                        loadedFieldName = "__storeLoaded",
                    )
                ),
                getterProps = listOf(
                    ImmutableImplGetterPropMetadata(
                        name = "store",
                        typeName = nullableClassName("test.model.Store"),
                        description = null,
                        kind = ImmutableImplGetterPropKind.STANDARD,
                        isNullable = true,
                        valueFieldName = "__storeValue",
                        loadedFieldName = "__storeLoaded",
                        declaringTypeClassName = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                    ),
                    ImmutableImplGetterPropMetadata(
                        name = "storeId",
                        typeName = nullableClassName("java.lang.Long"),
                        description = null,
                        kind = ImmutableImplGetterPropKind.ID_VIEW_SCALAR,
                        isNullable = true,
                        valueFieldName = null,
                        loadedFieldName = null,
                        declaringTypeClassName = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                        idViewBaseName = "store",
                        idViewBaseTypeName = nullableClassName("test.model.Store"),
                        idViewTargetIdPropName = "id",
                    )
                ),
                stateProps = listOf(
                    ImmutableImplStatePropMetadata(
                        name = "store",
                        typeName = nullableClassName("test.model.Store"),
                        slotName = "STORE",
                        valueFieldName = "__storeValue",
                        loadedFieldName = "__storeLoaded",
                        isNullable = true,
                        isAssociation = true,
                        isId = false,
                        loadKind = ImmutableImplLoadKind.STANDARD,
                    )
                ),
                hiddenSlotNames = emptyList(),
            )
        ).generate()

        val source = renderImplSource(impl)

        assertTrue(source.contains("Store __base = this.getStore();"), source)
        assertTrue(source.contains("return __base.getId();"), source)
        assertTrue(!source.contains("?."), source)
    }

    private fun nullableClassName(qualifiedName: String): LsiTypeName =
        ImmutableGeneratorTestFixtures.className(qualifiedName).copyNullable(true)

    private fun renderImplSource(impl: LsiTypeSpec): String =
        LsiFileSpec(
            packageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            name = "BookDraft",
            types = listOf(
                LsiTypeSpec(
                    name = "Producer",
                    kind = LsiTypeSpecKind.CLASS,
                    nestedTypes = listOf(impl),
                )
            )
        ).renderJavaSource()
}
