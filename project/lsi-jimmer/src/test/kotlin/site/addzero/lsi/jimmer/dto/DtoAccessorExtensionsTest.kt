package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiLanguage
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
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef

class DtoAccessorExtensionsTest {

    @Test
    fun `keeps declaration order and excludes non-base and hidden properties`() {
        val graph = graph(visibleDynamic = true)
        val type = graph.types.single()

        assertEquals(
            listOf("dynamicValue", "userValue", "staticValue", "foldValue", "fuzzyValue"),
            type.propsInDeclarationOrder(graph).map(DtoProp::name),
        )
        assertEquals(
            listOf("dynamicValue", "staticValue", "fuzzyValue"),
            type.basePropsInDeclarationOrder(graph).map(DtoBaseProp::name),
        )
        assertEquals(
            listOf("dynamicValue", "staticValue", "fuzzyValue"),
            type.serializerPropsInDeclarationOrder(graph).map(DtoBaseProp::name),
        )
        assertEquals(
            listOf("isDynamicValueLoaded", null, null),
            type.serializerPropsInDeclarationOrder(graph)
                .map(DtoBaseProp::serializerLoadedAccessorNameOrNull),
        )
        assertTrue(type.requiresDynamicInputSerialization(graph))
        assertTrue(type.requiresInputBuilder(graph))
    }

    @Test
    fun `does not let hidden dynamic properties require serialization`() {
        val graph = graph(visibleDynamic = false)
        val type = graph.types.single()

        assertEquals(
            listOf("staticValue", "fuzzyValue"),
            type.basePropsInDeclarationOrder(graph).map(DtoBaseProp::name),
        )
        assertFalse(type.requiresDynamicInputSerialization(graph))
        assertFalse(type.requiresInputBuilder(graph))
    }

    @Test
    fun `does not require dynamic serialization for a non-input DTO`() {
        val graph = graph(visibleDynamic = true, input = false)
        val type = graph.types.single()

        assertFalse(type.requiresDynamicInputSerialization(graph))
        assertFalse(type.requiresInputBuilder(graph))
        assertFailsWith<IllegalArgumentException> {
            type.serializerPropsInDeclarationOrder(graph)
        }
    }

    @Test
    fun `derives Java accessors from final DTO value semantics`() {
        assertEquals("isActive", valueAccessorName("active"))
        assertEquals("isEnabled", valueAccessorName("isEnabled"))
        assertEquals("getEnabled", valueAccessorName("enabled", immutableType = STRING_TYPE))
        assertEquals("getIsEnabled", valueAccessorName("isEnabled", nullable = true))
        assertEquals("getURL", valueAccessorName("URL", immutableType = STRING_TYPE))
        assertEquals("get_1", valueAccessorName("_1", immutableType = STRING_TYPE))
        assertEquals(
            "getNullableType",
            valueAccessorName(
                name = "nullableType",
                immutableType = BOOLEAN_TYPE.copy(nullability = LsiNullability.NULLABLE),
            ),
        )
        assertEquals(
            "getConverted",
            valueAccessorName(
                name = "converted",
                converter = converter(STRING_TYPE),
            ),
        )
        assertEquals(
            "getConvertedBoolean",
            valueAccessorName(
                name = "convertedBoolean",
                converter = converter(BOOLEAN_TYPE),
            ),
        )
        assertEquals(
            "getNullableConverted",
            valueAccessorName(
                name = "nullableConverted",
                converter = converter(BOOLEAN_TYPE, targetNullable = true),
            ),
        )
        assertEquals(
            "getBooleanList",
            valueAccessorName(
                name = "booleanList",
                immutableList = true,
            ),
        )
    }

    @Test
    fun `derives Java accessors for id functions and id views`() {
        val targetId = immutableProp(
            name = "id",
            type = BOOLEAN_TYPE,
            ownerTypeId = TARGET_TYPE_ID,
            primaryMapping = PrimaryMapping.ID,
        )
        val targetType = immutableType(
            id = TARGET_TYPE_ID,
            props = listOf(targetId),
            kind = ImmutableTypeKind.ENTITY,
            idPropId = targetId.id,
        )
        val target = immutableProp(
            name = "target",
            type = LsiDeclaredType(TARGET_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            targetTypeId = TARGET_TYPE_ID,
        )
        val targets = immutableProp(
            name = "targets",
            type = LsiDeclaredType(TARGET_TYPE_ID),
            list = true,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.ONE_TO_MANY,
            targetTypeId = TARGET_TYPE_ID,
        )
        val targetIdView = immutableProp(
            name = "targetId",
            type = BOOLEAN_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(target.id, targetId.id),
        )
        val convertedTargetIdView = immutableProp(
            name = "convertedTargetId",
            type = BOOLEAN_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(target.id, targetId.id),
            converter = converter(STRING_TYPE),
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    BASE_TYPE_ID,
                    listOf(target, targets, targetIdView, convertedTargetIdView),
                ),
                targetType,
            ),
        )

        val idProp = baseProp("targetId", baseName = "target").copy(functionName = "id")
        val idGraph = singlePropGraph(idProp)
        assertEquals(
            "isTargetId",
            idProp.serializerValueAccessorName(LsiLanguage.JAVA, idGraph, schema),
        )

        val listIdProp = baseProp("targetIds", baseName = "targets").copy(functionName = "id")
        val listIdGraph = singlePropGraph(listIdProp)
        assertEquals(
            "getTargetIds",
            listIdProp.serializerValueAccessorName(LsiLanguage.JAVA, listIdGraph, schema),
        )

        val idViewProp = baseProp("targetIdView", baseName = "targetId")
        val idViewGraph = singlePropGraph(idViewProp)
        assertEquals(
            "isTargetIdView",
            idViewProp.serializerValueAccessorName(LsiLanguage.JAVA, idViewGraph, schema),
        )

        val convertedIdViewProp = baseProp(
            "convertedTargetIdView",
            baseName = "convertedTargetId",
        )
        val convertedIdViewGraph = singlePropGraph(convertedIdViewProp)
        assertEquals(
            "getConvertedTargetIdView",
            convertedIdViewProp.serializerValueAccessorName(
                LsiLanguage.JAVA,
                convertedIdViewGraph,
                schema,
            ),
        )
    }

    @Test
    fun `uses effective DTO names for value and loaded accessors`() {
        val aliasProp = baseProp(
            name = "when",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
            baseName = "active",
        )
        val graph = singlePropGraph(aliasProp)
        val schema = immutableSchema(immutableProp("active", STRING_TYPE))

        assertEquals("getWhen", aliasProp.serializerValueAccessorName(LsiLanguage.JAVA, graph, schema))
        assertEquals("when", aliasProp.serializerValueAccessorName(LsiLanguage.KOTLIN, graph, schema))
        assertEquals("isWhenLoaded", aliasProp.loadedAccessorName())
        assertEquals(
            "isURLloaded",
            baseProp("URL", DtoModifier.DYNAMIC, nullable = true).loadedAccessorName(),
        )
        assertEquals(
            "isIsEnabledLoaded",
            baseProp("isEnabled", DtoModifier.DYNAMIC, nullable = true).loadedAccessorName(),
        )

        assertFailsWith<IllegalArgumentException> {
            baseProp("staticValue", DtoModifier.STATIC).loadedAccessorName()
        }
        assertFailsWith<IllegalArgumentException> {
            baseProp("invalidDynamic", DtoModifier.DYNAMIC, nullable = false)
        }
    }

    @Test
    fun `derives generated loaded state storage from the frozen DTO graph`() {
        val graph = graph(visibleDynamic = true)
        val type = graph.types.single()
        val dynamicProp = type.baseProp(graph, "dynamicValue")

        assertEquals(
            "_isDynamicValueLoaded",
            dynamicProp.dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.JAVA),
        )
        assertEquals(
            "isDynamicValueLoaded",
            dynamicProp.dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.KOTLIN),
        )
        assertEquals(
            listOf(null, null, null, null),
            listOf("userValue", "staticValue", "foldValue", "fuzzyValue").map { name ->
                type.prop(graph, name)
                    .dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.JAVA)
            },
        )
        assertEquals(
            null,
            graph.propsById.getValue(DtoPropId("dto#h-hidden"))
                .dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.JAVA),
        )

        val nonInputGraph = graph(visibleDynamic = true, input = false)
        assertEquals(
            null,
            nonInputGraph.types.single().prop(nonInputGraph, "dynamicValue")
                .dtoLoadedStateStorageNameOrNull(nonInputGraph, LsiLanguage.KOTLIN),
        )

        val fixedProp = baseProp("fixedValue", DtoModifier.FIXED, nullable = true)
        val fixedGraph = singlePropGraph(fixedProp)
        assertEquals(
            null,
            fixedProp.dtoLoadedStateStorageNameOrNull(fixedGraph, LsiLanguage.JAVA),
        )
        assertEquals(
            "_isFixedValueLoaded",
            fixedProp.inputBuilderLoadedStateNameOrNull(fixedGraph, LsiLanguage.JAVA),
        )

        val acronymProp = baseProp("URL", DtoModifier.DYNAMIC, nullable = true)
        val acronymGraph = singlePropGraph(acronymProp)
        assertEquals(
            "_isURLloaded",
            acronymProp.dtoLoadedStateStorageNameOrNull(acronymGraph, LsiLanguage.JAVA),
        )
        assertEquals(
            "isURLloaded",
            acronymProp.dtoLoadedStateStorageNameOrNull(acronymGraph, LsiLanguage.KOTLIN),
        )

        assertFailsWith<IllegalArgumentException> {
            dynamicProp.dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.UNKNOWN)
        }
        assertFailsWith<IllegalArgumentException> {
            dynamicProp.copy(name = "foreign")
                .dtoLoadedStateStorageNameOrNull(graph, LsiLanguage.JAVA)
        }
    }

    @Test
    fun `rejects inconsistent Java boolean semantics across base bindings`() {
        val prop = baseProp("mixed", baseName = "active").copy(
            baseProps = listOf(
                DtoBasePropBinding("active", LsiSymbolId.property(BASE_TYPE_ID, "active")),
                DtoBasePropBinding("label", LsiSymbolId.property(BASE_TYPE_ID, "label")),
            ),
        )
        val graph = singlePropGraph(prop)
        val schema = immutableSchema(
            immutableProp("active", BOOLEAN_TYPE),
            immutableProp("label", STRING_TYPE),
        )

        assertFailsWith<IllegalArgumentException> {
            prop.serializerValueAccessorName(LsiLanguage.JAVA, graph, schema)
        }

        val consistentProp = prop.copy(
            baseProps = listOf(
                DtoBasePropBinding("active", LsiSymbolId.property(BASE_TYPE_ID, "active")),
                DtoBasePropBinding("enabled", LsiSymbolId.property(BASE_TYPE_ID, "enabled")),
            ),
        )
        val consistentGraph = singlePropGraph(consistentProp)
        val consistentSchema = immutableSchema(
            immutableProp("active", BOOLEAN_TYPE),
            immutableProp("enabled", BOOLEAN_TYPE),
        )
        assertEquals(
            "isMixed",
            consistentProp.serializerValueAccessorName(
                LsiLanguage.JAVA,
                consistentGraph,
                consistentSchema,
            ),
        )
    }

    @Test
    fun `rejects serializer accessors for a non-input DTO`() {
        val prop = baseProp("value")
        val graph = singlePropGraph(prop, input = false)

        assertFailsWith<IllegalArgumentException> {
            prop.serializerValueAccessorName(
                LsiLanguage.JAVA,
                graph,
                immutableSchema(immutableProp("value", STRING_TYPE)),
            )
        }
    }

    @Test
    fun `rejects a DTO type from another graph`() {
        val graph = graph(visibleDynamic = true)
        val foreignType = graph.types.single().copy(name = "ForeignInput")

        assertFailsWith<IllegalArgumentException> {
            foreignType.basePropsInDeclarationOrder(graph)
        }
    }

    private fun graph(
        visibleDynamic: Boolean,
        input: Boolean = true,
    ): DtoGraph {
        val visibleProps = buildList {
            if (visibleDynamic) {
                add(
                    baseProp(
                        name = "dynamicValue",
                        modifier = DtoModifier.DYNAMIC,
                        idSuffix = "z-dynamic",
                        nullable = true,
                    ),
                )
            }
            add(userProp().copy(nullable = true))
            add(baseProp("staticValue", DtoModifier.STATIC, "a-static", nullable = true))
            add(foldProp().copy(nullable = true))
            add(baseProp("fuzzyValue", DtoModifier.FUZZY, "b-fuzzy", nullable = true))
        }
        val hiddenDynamic = baseProp(
            name = "hiddenDynamic",
            modifier = DtoModifier.DYNAMIC,
            idSuffix = "h-hidden",
            nullable = true,
        )
        val props = (visibleProps + hiddenDynamic).sortedBy(DtoProp::id)
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = if (input) setOf(DtoModifier.INPUT) else emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = visibleProps.map(DtoProp::id),
            hiddenFlatPropIds = listOf(hiddenDynamic.id),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = props,
        )
    }

    private fun baseProp(
        name: String,
        modifier: DtoModifier = DtoModifier.STATIC,
        idSuffix: String = name,
        nullable: Boolean = false,
        baseName: String = name,
    ): DtoBaseProp {
        val propId = DtoPropId("dto#$idSuffix")
        return DtoBaseProp(
            id = propId,
            ownerTypeId = TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = baseName,
                    propId = LsiSymbolId.property(BASE_TYPE_ID, baseName),
                ),
            ),
            basePath = baseName,
            nextPropId = null,
            tailPropId = propId,
            baseNullable = false,
            inputModifier = modifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun valueAccessorName(
        name: String,
        nullable: Boolean = false,
        immutableType: LsiTypeRef = BOOLEAN_TYPE,
        immutableList: Boolean = false,
        converter: ImmutableConverter? = null,
    ): String {
        val prop = baseProp(name = name, nullable = nullable)
        val graph = singlePropGraph(prop)
        val schema = immutableSchema(
            immutableProp(
                name = name,
                type = immutableType,
                list = immutableList,
                converter = converter,
            ),
        )
        return prop.serializerValueAccessorName(LsiLanguage.JAVA, graph, schema)
    }

    private fun singlePropGraph(
        prop: DtoBaseProp,
        input: Boolean = true,
    ): DtoGraph {
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = if (input) setOf(DtoModifier.INPUT) else emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(prop.id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(SOURCE, listOf(TYPE_ID), listOf(type), listOf(prop))
    }

    private fun immutableSchema(vararg props: ImmutableProp): ImmutableSchema {
        return ImmutableSchema(listOf(immutableType(BASE_TYPE_ID, props.toList())))
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        kind: ImmutableTypeKind = ImmutableTypeKind.IMMUTABLE,
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
        name: String,
        type: LsiTypeRef,
        ownerTypeId: LsiSymbolId = BASE_TYPE_ID,
        list: Boolean = false,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        associationKind: AssociationKind = AssociationKind.NONE,
        targetTypeId: LsiSymbolId? = null,
        view: ImmutableView? = null,
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
                AssociationKind.ONE_TO_ONE,
                AssociationKind.MANY_TO_ONE,
                -> AssociationStorageKind.COLUMN
                else -> AssociationStorageKind.NONE
            },
            transientResolver = null,
            view = view,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private fun converter(
        targetType: LsiTypeRef,
        targetNullable: Boolean = false,
    ): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = LsiSymbolId.type("demo.Converter"),
            sourceType = BOOLEAN_TYPE,
            targetType = targetType,
            sourceNullable = false,
            targetNullable = targetNullable,
            propertyNullable = false,
        )
    }

    private fun userProp(): DtoUserProp {
        return DtoUserProp(
            id = USER_PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "userValue",
            alias = "userValue",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("kotlin.String", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
    }

    private fun foldProp(): DtoFoldProp {
        return DtoFoldProp(
            id = FOLD_PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "foldValue",
            alias = "foldValue",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = TYPE_ID,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val TYPE_ID = DtoTypeId("dto#book-input")
        val USER_PROP_ID = DtoPropId("dto#c-user")
        val FOLD_PROP_ID = DtoPropId("dto#d-fold")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val TARGET_TYPE_ID = LsiSymbolId.type("demo.Target")
        val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
    }
}
