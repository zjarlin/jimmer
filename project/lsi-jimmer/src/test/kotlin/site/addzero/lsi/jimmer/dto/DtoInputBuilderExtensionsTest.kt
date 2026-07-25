package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiVariance

class DtoInputBuilderExtensionsTest {

    @Test
    fun `resolves builder eligibility declaration order names and build strategies`() {
        val fixture = fixture()
        val props = fixture.type.inputBuilderPropsInDeclarationOrder(fixture.graph)

        assertEquals(
            listOf(
                "dynamicName",
                "isEnabled",
                "tags",
                "mutableTags",
                "values",
                "count",
                "fixedCode",
                "fuzzyText",
            ),
            props.map(DtoProp::name),
        )
        assertEquals("dynamicName", props[0].inputBuilderSetterName())
        assertEquals("setEnabled", props[1].inputBuilderBuiltDtoSetterNameOrNull(LsiLanguage.JAVA))
        assertNull(props[1].inputBuilderBuiltDtoSetterNameOrNull(LsiLanguage.KOTLIN))
        assertEquals("_isDynamicNameLoaded", props[0].inputBuilderLoadedStateNameOrNull(fixture.graph, LsiLanguage.JAVA))
        assertEquals("isDynamicNameLoaded", props[0].inputBuilderLoadedStateNameOrNull(fixture.graph, LsiLanguage.KOTLIN))
        assertNull(props[5].inputBuilderLoadedStateNameOrNull(fixture.graph, LsiLanguage.JAVA))
        assertEquals(
            DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_LOADED,
            props[0].inputBuilderBuildStrategy(fixture.graph, LsiLanguage.JAVA),
        )
        assertEquals(
            DtoInputBuilderBuildStrategy.KOTLIN_ARGUMENT_WITH_LOADED_STATE,
            props[0].inputBuilderBuildStrategy(fixture.graph, LsiLanguage.KOTLIN),
        )
        assertEquals(
            DtoInputBuilderBuildStrategy.JAVA_REQUIRE_NON_NULL_AND_SET,
            props[5].inputBuilderBuildStrategy(fixture.graph, LsiLanguage.JAVA),
        )
        assertEquals(
            DtoInputBuilderBuildStrategy.JAVA_REQUIRE_LOADED_AND_SET,
            props[6].inputBuilderBuildStrategy(fixture.graph, LsiLanguage.JAVA),
        )
        assertEquals(
            DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_NON_NULL,
            props[7].inputBuilderBuildStrategy(fixture.graph, LsiLanguage.JAVA),
        )
    }

    @Test
    fun `resolves ordinary parameter and backing storage types without poet types`() {
        val fixture = fixture()
        val generatedType: (DtoType) -> LsiDeclaredType = { type ->
            LsiDeclaredType(LsiSymbolId.type("demo.dto.${type.name}"))
        }
        val count = fixture.graph.propsById.getValue(COUNT_PROP_ID)
        val enabled = fixture.graph.propsById.getValue(ENABLED_PROP_ID)
        val dynamicName = fixture.graph.propsById.getValue(DYNAMIC_PROP_ID)

        val javaParameter = count.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.JAVA,
            generatedType,
        ) as LsiPrimitiveType
        val javaBacking = count.inputBuilderBackingType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.JAVA,
            generatedType,
        ) as LsiPrimitiveType
        val kotlinBacking = count.inputBuilderBackingType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.KOTLIN,
            generatedType,
        ) as LsiPrimitiveType
        val enabledType = enabled.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.KOTLIN,
            generatedType,
        ) as LsiPrimitiveType
        val javaDynamicNameType = dynamicName.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.JAVA,
            generatedType,
        ) as LsiDeclaredType
        val kotlinDynamicNameType = dynamicName.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.KOTLIN,
            generatedType,
        ) as LsiDeclaredType

        assertEquals(LsiPrimitiveKind.INT, javaParameter.kind)
        assertEquals(LsiNullability.NON_NULL, javaParameter.nullability)
        assertFalse(javaParameter.boxed)
        assertEquals(LsiNullability.NULLABLE, javaBacking.nullability)
        assertTrue(javaBacking.boxed)
        assertEquals(LsiNullability.NULLABLE, kotlinBacking.nullability)
        assertFalse(kotlinBacking.boxed)
        assertEquals(LsiPrimitiveKind.BOOLEAN, enabledType.kind)
        assertEquals(LsiNullability.NON_NULL, enabledType.nullability)
        assertEquals(LsiSymbolId.type("java.lang.String"), javaDynamicNameType.declarationId)
        assertEquals(LsiSymbolId.type("kotlin.String"), kotlinDynamicNameType.declarationId)

        val tags = fixture.graph.propsById.getValue(TAGS_PROP_ID)
        val javaTags = tags.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.JAVA,
            generatedType,
        ) as LsiDeclaredType
        val kotlinTags = tags.inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.KOTLIN,
            generatedType,
        ) as LsiDeclaredType
        val javaMutableTags = fixture.graph.propsById.getValue(MUTABLE_TAGS_PROP_ID)
            .inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ) as LsiDeclaredType
        val javaValues = fixture.graph.propsById.getValue(VALUES_PROP_ID)
            .inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ) as LsiArrayType
        val kotlinValues = fixture.graph.propsById.getValue(VALUES_PROP_ID)
            .inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.KOTLIN,
                generatedType,
            ) as LsiDeclaredType

        assertEquals(LsiSymbolId.type("java.util.List"), javaTags.declarationId)
        assertEquals(LsiVariance.OUT, javaTags.arguments.single().variance)
        assertEquals(LsiSymbolId.type("kotlin.collections.List"), kotlinTags.declarationId)
        assertEquals(LsiVariance.INVARIANT, kotlinTags.arguments.single().variance)
        assertEquals(LsiVariance.INVARIANT, javaMutableTags.arguments.single().variance)
        assertEquals(LsiSymbolId.type("java.lang.Object"), (javaValues.elementType as LsiDeclaredType).declarationId)
        assertEquals(LsiSymbolId.type("kotlin.Array"), kotlinValues.declarationId)
        assertEquals(LsiVariance.STAR, kotlinValues.arguments.single().variance)
    }

    @Test
    fun `selects frozen setter jackson annotations and exact dto json naming`() {
        val fixture = fixture()
        val jsonAlias = LsiAnnotation(JSON_ALIAS_TYPE_ID)
        val naming = LsiAnnotation(
            type = JACKSON_2_JSON_NAMING_TYPE_ID,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ClassValue(LsiDeclaredType(NAMING_STRATEGY_TYPE_ID)),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )
        val contract = DtoAnnotationContract(
            declarations = listOf(
                annotationDeclaration(JSON_ALIAS_TYPE_ID),
                annotationDeclaration(JACKSON_2_JSON_NAMING_TYPE_ID),
            ).sortedBy(DtoAnnotationDeclaration::typeId),
            typePlans = listOf(
                DtoTypeAnnotationPlan(
                    typeId = TYPE_ID,
                    applications = listOf(
                        DtoAnnotationApplication(
                            annotation = naming,
                            origin = DtoAnnotationOrigin.DTO,
                            sourceSymbolId = null,
                            placements = listOf(DtoAnnotationPlacement.TYPE),
                        ),
                    ),
                ),
            ),
            propPlans = listOf(
                DtoPropAnnotationPlan(
                    propId = DYNAMIC_PROP_ID,
                    propertyApplications = emptyList(),
                    builderSetterApplications = listOf(
                        DtoBuilderSetterAnnotationApplication(
                            annotation = jsonAlias,
                            origin = DtoAnnotationOrigin.DTO,
                            sourceSymbolId = null,
                        ),
                    ),
                ),
            ),
            diagnostics = emptyList(),
        )

        assertEquals(
            listOf(jsonAlias),
            fixture.graph.propsById.getValue(DYNAMIC_PROP_ID)
                .inputBuilderSetterJacksonAnnotationApplications(fixture.graph, contract)
                .map(DtoBuilderSetterAnnotationApplication::annotation),
        )
        assertSame(
            naming,
            fixture.type.inputBuilderJsonNamingAnnotationOrNull(
                fixture.graph,
                contract,
                JACKSON_2_JSON_NAMING_TYPE_ID,
            ),
        )
        assertNull(
            fixture.type.inputBuilderJsonNamingAnnotationOrNull(
                fixture.graph,
                contract,
                JACKSON_3_JSON_NAMING_TYPE_ID,
            ),
        )
    }

    @Test
    fun `resolves nested recursive reusable fold converter id and id view types`() {
        val fixture = complexFixture()
        val resolvedTypeIds = mutableListOf<DtoTypeId>()
        val generatedType: (DtoType) -> LsiDeclaredType = { type ->
            resolvedTypeIds += type.id
            LsiDeclaredType(GENERATED_TYPE_IDS.getValue(type.id))
        }
        val props = fixture.type.inputBuilderPropsInDeclarationOrder(fixture.graph)
            .associateBy(DtoProp::name)

        assertEquals(
            GENERATED_TYPE_IDS.getValue(NESTED_TYPE_ID),
            props.getValue("nested").inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ).declaredTypeId(),
        )
        assertEquals(
            GENERATED_TYPE_IDS.getValue(COMPLEX_TYPE_ID),
            props.getValue("recursive").inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ).declaredTypeId(),
        )
        assertEquals(
            SHARED_INPUT_TYPE_ID,
            props.getValue("shared").inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ).declaredTypeId(),
        )
        assertEquals(
            GENERATED_TYPE_IDS.getValue(FOLD_TYPE_ID),
            props.getValue("folded").inputBuilderParameterType(
                fixture.graph,
                fixture.schema,
                LsiLanguage.JAVA,
                generatedType,
            ).declaredTypeId(),
        )
        val convertedValues = props.getValue("convertedValues").inputBuilderParameterType(
            fixture.graph,
            fixture.schema,
            LsiLanguage.JAVA,
            generatedType,
        ) as LsiDeclaredType
        assertEquals(LsiSymbolId.type("java.util.Set"), convertedValues.declarationId)
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            (convertedValues.arguments.single().type as LsiDeclaredType).declarationId,
        )
        listOf("targetIds", "targetIdViews").forEach { propName ->
            assertJavaStringList(
                props.getValue(propName).inputBuilderParameterType(
                    fixture.graph,
                    fixture.schema,
                    LsiLanguage.JAVA,
                    generatedType,
                ),
            )
        }
        assertTrue(COMPLEX_TYPE_ID in resolvedTypeIds)
        assertTrue(NESTED_TYPE_ID in resolvedTypeIds)
        assertTrue(FOLD_TYPE_ID in resolvedTypeIds)
        assertFalse(RECURSIVE_TYPE_ID in resolvedTypeIds)
        assertFalse(REFERENCE_SOURCE_TYPE_ID in resolvedTypeIds)
    }

    @Test
    fun `resolves frozen dto converter targets without platform metadata`() {
        val fixture = complexFixture()
        val props = fixture.type.inputBuilderPropsInDeclarationOrder(fixture.graph)
            .filterIsInstance<DtoBaseProp>()
            .associateBy(DtoBaseProp::name)

        val convertedValues = props.getValue("convertedValues")
            .dtoConverterTargetTypeOrNull(fixture.graph, fixture.schema) as LsiDeclaredType
        assertEquals(LsiSymbolId.type("java.util.Set"), convertedValues.declarationId)
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            (convertedValues.arguments.single().type as LsiDeclaredType).declarationId,
        )
        assertJavaStringList(
            requireNotNull(
                props.getValue("targetIds").dtoConverterTargetTypeOrNull(fixture.graph, fixture.schema),
            ),
        )
        assertJavaStringList(
            requireNotNull(
                props.getValue("targetIdViews").dtoConverterTargetTypeOrNull(fixture.graph, fixture.schema),
            ),
        )
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            (props.getValue("targetIds").dtoClientType(fixture.graph, fixture.schema) as LsiDeclaredType)
                .declarationId,
        )

        val targetIds = props.getValue("targetIds")
        listOf("associatedIdEq", "associatedIdNe").forEach { functionName ->
            val associatedProp = targetIds.copy(functionName = functionName)
            val associatedGraph = fixture.graph.replacingComplexRootProp(
                fixture.type,
                associatedProp,
                DtoModifier.SPECIFICATION,
            )
            assertEquals(
                LsiSymbolId.type("java.lang.String"),
                (associatedProp.dtoConverterTargetTypeOrNull(associatedGraph, fixture.schema) as LsiDeclaredType)
                    .declarationId,
            )
        }
        listOf("associatedIdIn", "associatedIdNotIn").forEach { functionName ->
            val associatedProp = targetIds.copy(functionName = functionName)
            val associatedGraph = fixture.graph.replacingComplexRootProp(
                fixture.type,
                associatedProp,
                DtoModifier.SPECIFICATION,
            )
            assertJavaStringList(
                requireNotNull(
                    associatedProp.dtoConverterTargetTypeOrNull(associatedGraph, fixture.schema),
                ),
            )
        }
    }

    @Test
    fun `compares frozen dto client types by stable immutable property id`() {
        val fixture = complexFixture()

        assertTrue(
            fixture.schema.haveSameDtoClientType(
                "demo.Complex",
                "convertedValues",
                "demo.Complex",
                "convertedValues",
            ),
        )
        assertFalse(
            fixture.schema.haveSameDtoClientType(
                "demo.Complex",
                "convertedValues",
                "demo.Complex",
                "targetIds",
            ),
        )
    }

    private fun fixture(): Fixture {
        val dynamic = baseProp(DYNAMIC_PROP_ID, "dynamicName", DtoModifier.DYNAMIC, nullable = true)
        val enabled = DtoUserProp(
            id = ENABLED_PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "isEnabled",
            alias = "isEnabled",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("Boolean", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
        val tags = userProp(
            id = TAGS_PROP_ID,
            name = "tags",
            type = DtoTypeRef(
                typeName = "List",
                arguments = listOf(
                    DtoTypeArgument(
                        variance = DtoVariance.INVARIANT,
                        type = DtoTypeRef("String", emptyList(), false, LOCATION),
                    ),
                ),
                nullable = false,
                location = LOCATION,
            ),
        )
        val mutableTags = userProp(
            id = MUTABLE_TAGS_PROP_ID,
            name = "mutableTags",
            type = DtoTypeRef(
                typeName = "MutableList",
                arguments = listOf(
                    DtoTypeArgument(
                        variance = DtoVariance.INVARIANT,
                        type = DtoTypeRef("String", emptyList(), false, LOCATION),
                    ),
                ),
                nullable = false,
                location = LOCATION,
            ),
        )
        val values = userProp(
            id = VALUES_PROP_ID,
            name = "values",
            type = DtoTypeRef(
                typeName = "Array",
                arguments = listOf(DtoTypeArgument(DtoVariance.STAR, null)),
                nullable = false,
                location = LOCATION,
            ),
        )
        val count = baseProp(COUNT_PROP_ID, "count", DtoModifier.STATIC, nullable = false)
        val fixed = baseProp(FIXED_PROP_ID, "fixedCode", DtoModifier.FIXED, nullable = true)
        val fuzzy = baseProp(FUZZY_PROP_ID, "fuzzyText", DtoModifier.FUZZY, nullable = true)
        val declarationProps = listOf(dynamic, enabled, tags, mutableTags, values, count, fixed, fuzzy)
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = declarationProps.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = declarationProps.sortedBy(DtoProp::id),
        )
        val immutableProps = declarationProps.filterIsInstance<DtoBaseProp>().map { prop ->
            immutableProp(
                name = prop.name,
                type = if (prop.name == "count") {
                    LsiPrimitiveType(LsiPrimitiveKind.INT)
                } else {
                    LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
                },
            )
        }
        val immutableType = ImmutableType(
            id = BASE_TYPE_ID,
            qualifiedName = BASE_TYPE_ID.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.IMMUTABLE,
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
        return Fixture(type, graph, ImmutableSchema(listOf(immutableType)))
    }

    private fun complexFixture(): Fixture {
        val nested = complexBaseProp(
            id = NESTED_PROP_ID,
            name = "nested",
            modifier = DtoModifier.FIXED,
            nullable = true,
            targetTypeId = NESTED_TYPE_ID,
        )
        val recursive = complexBaseProp(
            id = RECURSIVE_PROP_ID,
            name = "recursive",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
            targetTypeId = RECURSIVE_TYPE_ID,
            recursive = true,
        )
        val shared = complexBaseProp(
            id = SHARED_PROP_ID,
            name = "shared",
            modifier = DtoModifier.STATIC,
            nullable = true,
            targetTypeId = REFERENCE_SOURCE_TYPE_ID,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = SHARED_INPUT_TYPE_ID.requireTypeQualifiedName(),
                targetBaseTypeId = SHARED_BASE_TYPE_ID,
                kind = DtoReusableTypeKind.INPUT,
                location = LOCATION,
            ),
        )
        val convertedValues = complexBaseProp(
            id = CONVERTED_VALUES_PROP_ID,
            name = "convertedValues",
            modifier = DtoModifier.STATIC,
            nullable = false,
        )
        val targetIds = complexBaseProp(
            id = TARGET_IDS_PROP_ID,
            name = "targetIds",
            modifier = DtoModifier.STATIC,
            nullable = false,
            functionName = "id",
        )
        val targetIdViews = complexBaseProp(
            id = TARGET_ID_VIEWS_PROP_ID,
            name = "targetIdViews",
            modifier = DtoModifier.STATIC,
            nullable = false,
        )
        val folded = DtoFoldProp(
            id = FOLDED_PROP_ID,
            ownerTypeId = COMPLEX_TYPE_ID,
            name = "folded",
            alias = "folded",
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = FOLD_TYPE_ID,
        )
        val declarationProps = listOf(
            nested,
            recursive,
            shared,
            convertedValues,
            targetIds,
            targetIdViews,
            folded,
        )
        val rootType = dtoType(
            id = COMPLEX_TYPE_ID,
            baseTypeId = COMPLEX_BASE_TYPE_ID,
            name = "ComplexInput",
            propIds = declarationProps.map(DtoProp::id),
        )
        val nestedType = dtoType(NESTED_TYPE_ID, COMPLEX_BASE_TYPE_ID, null)
        val recursiveType = dtoType(RECURSIVE_TYPE_ID, COMPLEX_BASE_TYPE_ID, null)
        val referenceSourceType = dtoType(REFERENCE_SOURCE_TYPE_ID, SHARED_BASE_TYPE_ID, null)
        val foldType = dtoType(FOLD_TYPE_ID, COMPLEX_BASE_TYPE_ID, null)
        val types = listOf(rootType, nestedType, recursiveType, referenceSourceType, foldType).sortedBy(DtoType::id)
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(COMPLEX_TYPE_ID),
            types = types,
            props = declarationProps.sortedBy(DtoProp::id),
        )

        val targetId = complexImmutableProp(
            ownerTypeId = TARGET_BASE_TYPE_ID,
            name = "id",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.ID,
            converter = idConverter(),
        )
        val targetType = immutableType(
            id = TARGET_BASE_TYPE_ID,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(targetId),
            idPropId = targetId.id,
        )
        val targetIdsBase = complexImmutableProp(
            ownerTypeId = COMPLEX_BASE_TYPE_ID,
            name = "targetIds",
            type = listType(LsiDeclaredType(TARGET_BASE_TYPE_ID)),
            list = true,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_MANY,
            associationStorage = AssociationStorageKind.MIDDLE_TABLE,
            targetTypeId = TARGET_BASE_TYPE_ID,
        )
        val complexProps = listOf(
            complexImmutableProp(COMPLEX_BASE_TYPE_ID, "nested", STRING_TYPE),
            complexImmutableProp(COMPLEX_BASE_TYPE_ID, "recursive", STRING_TYPE),
            complexImmutableProp(COMPLEX_BASE_TYPE_ID, "shared", STRING_TYPE),
            complexImmutableProp(
                ownerTypeId = COMPLEX_BASE_TYPE_ID,
                name = "convertedValues",
                type = listType(LONG_TYPE),
                list = true,
                converter = listToSetConverter(),
            ),
            targetIdsBase,
            complexImmutableProp(
                ownerTypeId = COMPLEX_BASE_TYPE_ID,
                name = "targetIdViews",
                type = listType(LONG_TYPE),
                list = true,
                primaryMapping = PrimaryMapping.VIEW,
                view = ImmutableView.Id(targetIdsBase.id, targetId.id),
            ),
        )
        val complexType = immutableType(
            id = COMPLEX_BASE_TYPE_ID,
            kind = ImmutableTypeKind.IMMUTABLE,
            props = complexProps,
        )
        val sharedType = immutableType(
            id = SHARED_BASE_TYPE_ID,
            kind = ImmutableTypeKind.IMMUTABLE,
            props = emptyList(),
        )
        return Fixture(rootType, graph, ImmutableSchema(listOf(complexType, sharedType, targetType)))
    }

    private fun dtoType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String?,
        propIds: List<DtoPropId> = emptyList(),
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = name,
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = propIds,
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun complexBaseProp(
        id: DtoPropId,
        name: String,
        modifier: DtoModifier,
        nullable: Boolean,
        targetTypeId: DtoTypeId? = null,
        targetTypeReference: DtoReusableTypeReference? = null,
        recursive: Boolean = false,
        functionName: String? = null,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = COMPLEX_TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(name, LsiSymbolId.property(COMPLEX_BASE_TYPE_ID, name)),
            ),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = false,
            inputModifier = modifier,
            functionName = functionName,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = null,
            config = null,
            recursive = recursive,
            likeOptions = emptySet(),
        )
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

    private fun complexImmutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: site.addzero.lsi.model.LsiTypeRef,
        list: Boolean = false,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
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
            associationStorage = associationStorage,
            transientResolver = null,
            view = view,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private fun idConverter(): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = LsiSymbolId.type("demo.LongToStringConverter"),
            sourceType = LONG_TYPE,
            targetType = STRING_TYPE,
            sourceNullable = false,
            targetNullable = false,
            propertyNullable = false,
        )
    }

    private fun listToSetConverter(): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = LsiSymbolId.type("demo.LongListToStringSetConverter"),
            sourceType = listType(LONG_TYPE),
            targetType = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.Set"),
                arguments = listOf(LsiTypeArgument.invariant(STRING_TYPE)),
            ),
            sourceNullable = false,
            targetNullable = false,
            propertyNullable = false,
        )
    }

    private fun listType(elementType: site.addzero.lsi.model.LsiTypeRef): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(elementType)),
        )
    }

    private fun site.addzero.lsi.model.LsiTypeRef.declaredTypeId(): LsiSymbolId {
        return (this as LsiDeclaredType).declarationId
    }

    private fun assertJavaStringList(type: site.addzero.lsi.model.LsiTypeRef) {
        val listType = type as LsiDeclaredType
        assertEquals(LsiSymbolId.type("java.util.List"), listType.declarationId)
        val elementType = listType.arguments.single().type as LsiDeclaredType
        assertEquals(LsiSymbolId.type("java.lang.String"), elementType.declarationId)
    }

    private fun DtoGraph.replacingComplexRootProp(
        rootType: DtoType,
        newProp: DtoBaseProp,
        modifier: DtoModifier,
    ): DtoGraph {
        val newRootType = rootType.copy(
            modifiers = setOf(modifier),
            propIds = listOf(newProp.id),
            hiddenFlatPropIds = emptyList(),
        )
        return DtoGraph(
            source = source,
            rootTypeIds = rootTypeIds,
            types = listOf(newRootType),
            props = listOf(newProp),
        )
    }

    private fun baseProp(
        id: DtoPropId,
        name: String,
        modifier: DtoModifier,
        nullable: Boolean,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(name, LsiSymbolId.property(BASE_TYPE_ID, name)),
            ),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = nullable,
            inputModifier = modifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun userProp(
        id: DtoPropId,
        name: String,
        type: DtoTypeRef,
    ): DtoUserProp {
        return DtoUserProp(
            id = id,
            ownerTypeId = TYPE_ID,
            name = name,
            alias = name,
            nullable = type.nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = type,
            defaultValueText = null,
        )
    }

    private fun immutableProp(
        name: String,
        type: site.addzero.lsi.model.LsiTypeRef,
    ): ImmutableProp {
        val id = LsiSymbolId.property(BASE_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = BASE_TYPE_ID,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = emptyList(),
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

    private fun annotationDeclaration(typeId: LsiSymbolId): DtoAnnotationDeclaration {
        return DtoAnnotationDeclaration(
            typeId = typeId,
            kind = DtoAnnotationDeclarationKind.JAVA,
            targetDeclared = true,
            allowedPlacements = listOf(DtoAnnotationPlacement.TYPE),
            argumentTypes = emptyMap(),
            kotlinValueVararg = false,
        )
    }

    private data class Fixture(
        val type: DtoType,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    )

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val TYPE_ID = DtoTypeId("dto#book-input")
        val DYNAMIC_PROP_ID = DtoPropId("dto#a-dynamic")
        val ENABLED_PROP_ID = DtoPropId("dto#b-enabled")
        val TAGS_PROP_ID = DtoPropId("dto#c-tags")
        val MUTABLE_TAGS_PROP_ID = DtoPropId("dto#d-mutable-tags")
        val VALUES_PROP_ID = DtoPropId("dto#e-values")
        val COUNT_PROP_ID = DtoPropId("dto#f-count")
        val FIXED_PROP_ID = DtoPropId("dto#g-fixed")
        val FUZZY_PROP_ID = DtoPropId("dto#h-fuzzy")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val JSON_ALIAS_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonAlias")
        val JACKSON_2_JSON_NAMING_TYPE_ID =
            LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonNaming")
        val JACKSON_3_JSON_NAMING_TYPE_ID =
            LsiSymbolId.type("tools.jackson.databind.annotation.JsonNaming")
        val NAMING_STRATEGY_TYPE_ID = LsiSymbolId.type("demo.SnakeCaseStrategy")
        val COMPLEX_TYPE_ID = DtoTypeId("dto#complex-root")
        val NESTED_TYPE_ID = DtoTypeId("dto#complex-target-nested")
        val RECURSIVE_TYPE_ID = DtoTypeId("dto#complex-target-recursive")
        val REFERENCE_SOURCE_TYPE_ID = DtoTypeId("dto#complex-target-reference")
        val FOLD_TYPE_ID = DtoTypeId("dto#complex-target-fold")
        val NESTED_PROP_ID = DtoPropId("dto#complex-a-nested")
        val RECURSIVE_PROP_ID = DtoPropId("dto#complex-b-recursive")
        val SHARED_PROP_ID = DtoPropId("dto#complex-c-shared")
        val CONVERTED_VALUES_PROP_ID = DtoPropId("dto#complex-d-converted-values")
        val TARGET_IDS_PROP_ID = DtoPropId("dto#complex-e-target-ids")
        val TARGET_ID_VIEWS_PROP_ID = DtoPropId("dto#complex-f-target-id-views")
        val FOLDED_PROP_ID = DtoPropId("dto#complex-g-folded")
        val COMPLEX_BASE_TYPE_ID = LsiSymbolId.type("demo.Complex")
        val SHARED_BASE_TYPE_ID = LsiSymbolId.type("demo.Shared")
        val TARGET_BASE_TYPE_ID = LsiSymbolId.type("demo.Target")
        val SHARED_INPUT_TYPE_ID = LsiSymbolId.type("demo.SharedInput")
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val GENERATED_TYPE_IDS = mapOf(
            COMPLEX_TYPE_ID to LsiSymbolId.type("demo.dto.ComplexInput"),
            NESTED_TYPE_ID to LsiSymbolId.type("demo.dto.ComplexInput.TargetOf_nested"),
            RECURSIVE_TYPE_ID to LsiSymbolId.type("demo.dto.WrongRecursiveTarget"),
            REFERENCE_SOURCE_TYPE_ID to LsiSymbolId.type("demo.dto.WrongReferenceTarget"),
            FOLD_TYPE_ID to LsiSymbolId.type("demo.dto.ComplexInput.TargetOf_folded"),
        )
    }
}
