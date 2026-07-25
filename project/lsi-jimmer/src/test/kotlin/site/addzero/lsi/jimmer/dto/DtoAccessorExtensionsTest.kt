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
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
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
    fun `requires Hibernate Validator enhancement only for visible dynamic properties`() {
        val visibleGraph = graph(visibleDynamic = true)
        val visibleType = visibleGraph.types.single()
        val hiddenGraph = graph(visibleDynamic = false)
        val hiddenType = hiddenGraph.types.single()

        assertTrue(
            visibleType.requiresHibernateValidatorEnhancement(
                graph = visibleGraph,
                enhancementEnabled = true,
            ),
        )
        assertFalse(
            visibleType.requiresHibernateValidatorEnhancement(
                graph = visibleGraph,
                enhancementEnabled = false,
            ),
        )
        assertFalse(
            hiddenType.requiresHibernateValidatorEnhancement(
                graph = hiddenGraph,
                enhancementEnabled = true,
            ),
        )
    }

    @Test
    fun `requires builders for merged branches but not polymorphic roots`() {
        val baseGraph = graph(visibleDynamic = true)
        val root = baseGraph.types.single()
        val dynamicProp = baseGraph.props
            .filterIsInstance<DtoBaseProp>()
            .single { prop -> prop.name == "dynamicValue" }
        val branchPropId = DtoPropId("dto#branch-dynamic")
        val branchProp = dynamicProp.copy(
            id = branchPropId,
            ownerTypeId = MERGED_TYPE_ID,
            tailPropId = branchPropId,
        )
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            targetBaseTypeId = null,
            declaredClassName = null,
            className = "DefaultBookInput",
            bodyTypeId = BODY_TYPE_ID,
            mergedTypeId = MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val polymorphicRoot = root.copy(
            polymorphism = DtoPolymorphism(exhaustive = true, branches = listOf(branch)),
        )
        val body = root.copy(
            id = BODY_TYPE_ID,
            name = null,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
        )
        val merged = root.copy(
            id = MERGED_TYPE_ID,
            name = null,
            propIds = listOf(branchPropId),
            hiddenFlatPropIds = emptyList(),
        )
        val graph = DtoGraph(
            source = baseGraph.source,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(polymorphicRoot, body, merged).sortedBy(DtoType::id),
            props = (baseGraph.props + branchProp).sortedBy(DtoProp::id),
        )

        assertFalse(polymorphicRoot.requiresInputBuilder(graph))
        assertTrue(merged.requiresInputBuilder(graph))
        assertTrue(polymorphicRoot.requiresHibernateValidatorEnhancement(graph, true))
        assertTrue(merged.requiresHibernateValidatorEnhancement(graph, true))
    }

    @Test
    fun `identifies nested specification fragments from frozen DTO semantics`() {
        val dtoType = graph(visibleDynamic = false).types.single()
        val specification = dtoType.copy(modifiers = setOf(DtoModifier.SPECIFICATION))
        val idProp = immutableProp(
            name = "id",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.ID,
        )
        val entityType = immutableType(
            id = BASE_TYPE_ID,
            props = listOf(idProp),
            kind = ImmutableTypeKind.ENTITY,
            idPropId = idProp.id,
        )
        val entitySchema = ImmutableSchema(listOf(entityType))

        assertEquals(entityType, specification.specificationBaseType(entitySchema))
        assertFalse(
            specification.isNestedSpecificationFragment(entitySchema),
        )
        assertTrue(
            specification.isNestedSpecificationFragment(
                ImmutableSchema(
                    listOf(immutableType(BASE_TYPE_ID, emptyList(), ImmutableTypeKind.EMBEDDABLE)),
                ),
            ),
        )
        assertTrue(
            specification.isNestedSpecificationFragment(
                ImmutableSchema(
                    listOf(immutableType(BASE_TYPE_ID, emptyList())),
                ),
            ),
        )
        assertFalse(
            dtoType.isNestedSpecificationFragment(
                ImmutableSchema(
                    listOf(immutableType(BASE_TYPE_ID, emptyList(), ImmutableTypeKind.EMBEDDABLE)),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            dtoType.specificationBaseType(entitySchema)
        }
        assertFailsWith<IllegalArgumentException> {
            dtoType.isNestedSpecificationFragment(ImmutableSchema(emptyList()))
        }
    }

    @Test
    fun `classifies generated base contracts from frozen semantics`() {
        val baseGraph = graph(visibleDynamic = false)
        val dtoType = baseGraph.types.single()
        val idProp = immutableProp(
            name = "id",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.ID,
        )
        fun schema(kind: ImmutableTypeKind): ImmutableSchema {
            val entity = kind == ImmutableTypeKind.ENTITY
            return ImmutableSchema(
                listOf(
                    immutableType(
                        id = BASE_TYPE_ID,
                        props = if (entity) listOf(idProp) else emptyList(),
                        kind = kind,
                        idPropId = idProp.id.takeIf { entity },
                    ),
                ),
            )
        }

        val cases = listOf(
            Triple(
                ImmutableTypeKind.ENTITY,
                setOf(DtoModifier.INPUT),
                DtoGeneratedBaseContractKind.ENTITY_INPUT,
            ),
            Triple(
                ImmutableTypeKind.ENTITY,
                emptySet(),
                DtoGeneratedBaseContractKind.ENTITY_VIEW,
            ),
            Triple(
                ImmutableTypeKind.ENTITY,
                setOf(DtoModifier.SPECIFICATION),
                DtoGeneratedBaseContractKind.ENTITY_SPECIFICATION,
            ),
            Triple(
                ImmutableTypeKind.EMBEDDABLE,
                setOf(DtoModifier.INPUT),
                DtoGeneratedBaseContractKind.EMBEDDABLE,
            ),
            Triple(
                ImmutableTypeKind.EMBEDDABLE,
                emptySet(),
                DtoGeneratedBaseContractKind.EMBEDDABLE,
            ),
            Triple(ImmutableTypeKind.EMBEDDABLE, setOf(DtoModifier.SPECIFICATION), null),
            Triple(ImmutableTypeKind.IMMUTABLE, setOf(DtoModifier.INPUT), null),
            Triple(ImmutableTypeKind.IMMUTABLE, emptySet(), null),
            Triple(ImmutableTypeKind.IMMUTABLE, setOf(DtoModifier.SPECIFICATION), null),
            Triple(ImmutableTypeKind.MAPPED_SUPERCLASS, setOf(DtoModifier.INPUT), null),
            Triple(ImmutableTypeKind.MAPPED_SUPERCLASS, emptySet(), null),
            Triple(ImmutableTypeKind.MAPPED_SUPERCLASS, setOf(DtoModifier.SPECIFICATION), null),
        )
        cases.forEach { (kind, modifiers, expected) ->
            assertEquals(
                expected,
                dtoType.copy(modifiers = modifiers).generatedBaseContractKind(schema(kind)),
                "$kind with $modifiers",
            )
        }

        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            targetBaseTypeId = null,
            declaredClassName = null,
            className = "DefaultBook",
            bodyTypeId = BODY_TYPE_ID,
            mergedTypeId = MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val polymorphism = DtoPolymorphism(exhaustive = true, branches = listOf(branch))
        fun polymorphicGraph(modifiers: Set<DtoModifier>): DtoGraph {
            val sourceGraph = graph(
                visibleDynamic = false,
                input = DtoModifier.INPUT in modifiers,
            )
            val sourceType = sourceGraph.types.single()
            val root = sourceType.copy(modifiers = modifiers, polymorphism = polymorphism)
            val body = sourceType.copy(
                id = BODY_TYPE_ID,
                name = null,
                modifiers = modifiers,
                propIds = emptyList(),
                hiddenFlatPropIds = emptyList(),
            )
            val merged = sourceType.copy(
                id = MERGED_TYPE_ID,
                name = null,
                modifiers = modifiers,
                propIds = emptyList(),
                hiddenFlatPropIds = emptyList(),
            )
            return DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(TYPE_ID),
                types = listOf(root, body, merged).sortedBy(DtoType::id),
                props = sourceGraph.props,
            )
        }
        val inputGraph = polymorphicGraph(setOf(DtoModifier.INPUT))
        val inputRoot = inputGraph.typesById.getValue(TYPE_ID)
        assertEquals(
            DtoGeneratedBaseContractKind.ENTITY_INPUT,
            inputRoot.generatedBaseContractKind(schema(ImmutableTypeKind.ENTITY)),
        )
        assertEquals(
            DtoGeneratedBaseContractKind.ENTITY_INPUT,
            branch.mergedType(inputGraph).generatedBaseContractKind(schema(ImmutableTypeKind.ENTITY)),
        )
        val viewGraph = polymorphicGraph(emptySet())
        val viewRoot = viewGraph.typesById.getValue(TYPE_ID)
        assertEquals(
            DtoGeneratedBaseContractKind.ENTITY_VIEW,
            viewRoot.generatedBaseContractKind(schema(ImmutableTypeKind.ENTITY)),
        )
        assertEquals(
            DtoGeneratedBaseContractKind.ENTITY_VIEW,
            branch.mergedType(viewGraph).generatedBaseContractKind(schema(ImmutableTypeKind.ENTITY)),
        )

        val entitySchema = schema(ImmutableTypeKind.ENTITY)
        assertFailsWith<IllegalArgumentException> {
            dtoType.generatedBaseContractKind(ImmutableSchema(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            dtoType.copy(baseTypeId = null).generatedBaseContractKind(entitySchema)
        }
    }

    @Test
    fun `identifies polymorphic input roots from frozen DTO semantics`() {
        val dtoType = graph(visibleDynamic = false).types.single()
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            targetBaseTypeId = null,
            declaredClassName = null,
            className = "DefaultBookInput",
            bodyTypeId = BODY_TYPE_ID,
            mergedTypeId = MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val polymorphicInput = dtoType.copy(
            polymorphism = DtoPolymorphism(exhaustive = true, branches = listOf(branch)),
        )
        val idProp = immutableProp(
            name = "id",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.ID,
        )
        val entitySchema = ImmutableSchema(
            listOf(
                immutableType(
                    id = BASE_TYPE_ID,
                    props = listOf(idProp),
                    kind = ImmutableTypeKind.ENTITY,
                    idPropId = idProp.id,
                ),
            ),
        )

        assertTrue(polymorphicInput.isPolymorphicInputRoot(entitySchema))
        assertFalse(
            polymorphicInput
                .copy(modifiers = emptySet())
                .isPolymorphicInputRoot(entitySchema),
        )
        assertFalse(dtoType.isPolymorphicInputRoot(entitySchema))
        assertFalse(
            polymorphicInput.isPolymorphicInputRoot(
                ImmutableSchema(listOf(immutableType(BASE_TYPE_ID, emptyList()))),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            polymorphicInput.copy(baseTypeId = null).isPolymorphicInputRoot(entitySchema)
        }
        assertFailsWith<IllegalArgumentException> {
            polymorphicInput.isPolymorphicInputRoot(ImmutableSchema(emptyList()))
        }
    }

    @Test
    fun `resolves polymorphic discriminator semantics from frozen models`() {
        val rootTypeId = LsiSymbolId.type("demo.Publication")
        val rootIdProp = immutableProp(
            name = "id",
            type = STRING_TYPE,
            ownerTypeId = rootTypeId,
            primaryMapping = PrimaryMapping.ID,
        )
        val rootDiscriminator = immutableProp(
            name = "kind",
            type = STRING_TYPE,
            ownerTypeId = rootTypeId,
            primaryMapping = PrimaryMapping.DISCRIMINATOR,
        )
        val rootType = immutableType(
            id = rootTypeId,
            props = listOf(rootIdProp, rootDiscriminator),
            kind = ImmutableTypeKind.ENTITY,
            idPropId = rootIdProp.id,
            inheritanceRootTypeId = rootTypeId,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
            discriminatorPropId = rootDiscriminator.id,
        )
        val inheritedIdProp = rootIdProp.copy(
            id = LsiSymbolId.property(BASE_TYPE_ID, rootIdProp.name),
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = rootTypeId,
            overrideChain = listOf(rootIdProp.id),
            inherited = true,
        )
        val inheritedDiscriminator = rootDiscriminator.copy(
            id = LsiSymbolId.property(BASE_TYPE_ID, rootDiscriminator.name),
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = rootTypeId,
            overrideChain = listOf(rootDiscriminator.id),
            inherited = true,
        )
        val derivedType = immutableType(
            id = BASE_TYPE_ID,
            props = listOf(inheritedIdProp, inheritedDiscriminator),
            kind = ImmutableTypeKind.ENTITY,
            idPropId = inheritedIdProp.id,
            superTypeIds = listOf(rootTypeId),
            primarySuperTypeId = rootTypeId,
            inheritanceRootTypeId = rootTypeId,
            discriminatorValue = "BOOK",
            discriminatorPropId = inheritedDiscriminator.id,
        )
        val dtoType = graph(visibleDynamic = false).types.single()
        val schema = ImmutableSchema(listOf(rootType, derivedType))

        assertEquals("kind", dtoType.polymorphicRootDiscriminatorPropNameOrNull(schema))
        assertEquals(
            "kind",
            dtoType
                .copy(baseTypeId = rootTypeId)
                .polymorphicRootDiscriminatorPropNameOrNull(schema),
        )
        assertEquals(
            null,
            dtoType.polymorphicRootDiscriminatorPropNameOrNull(
                ImmutableSchema(listOf(immutableType(BASE_TYPE_ID, emptyList()))),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            dtoType.polymorphicRootDiscriminatorPropNameOrNull(ImmutableSchema(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            rootType.copy(discriminatorPropId = rootIdProp.id)
        }

        val selectedProp = baseProp(
            name = "category",
            idSuffix = "selected-discriminator",
            baseName = inheritedDiscriminator.name,
        ).copy(
            baseProps = listOf(
                DtoBasePropBinding(inheritedDiscriminator.name, inheritedDiscriminator.id),
                DtoBasePropBinding(inheritedIdProp.name, inheritedIdProp.id),
            ),
        )
        val selectedType = dtoType.copy(
            modifiers = setOf(DtoModifier.INPUT),
            propIds = listOf(selectedProp.id),
            hiddenFlatPropIds = emptyList(),
        )
        val selectedGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(selectedType),
            props = listOf(selectedProp),
        )

        assertEquals(
            selectedProp,
            selectedType.selectedPolymorphicInputDiscriminatorPropOrNull(selectedGraph, schema),
        )
        val scalarFirstPropId = DtoPropId("dto#scalar-first")
        val scalarFirstProp = selectedProp.copy(
            id = scalarFirstPropId,
            name = "ignored",
            alias = "ignored",
            tailPropId = scalarFirstPropId,
            baseProps = listOf(
                DtoBasePropBinding(inheritedIdProp.name, inheritedIdProp.id),
                DtoBasePropBinding(inheritedDiscriminator.name, inheritedDiscriminator.id),
            ),
        )
        val scalarFirstType = selectedType.copy(propIds = listOf(scalarFirstProp.id))
        val scalarFirstGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(scalarFirstType),
            props = listOf(scalarFirstProp),
        )
        assertEquals(
            null,
            scalarFirstType.selectedPolymorphicInputDiscriminatorPropOrNull(
                scalarFirstGraph,
                schema,
            ),
        )
        assertEquals(
            null,
            selectedType
                .copy(modifiers = emptySet())
                .selectedPolymorphicInputDiscriminatorPropOrNull(
                    selectedGraph.copy(
                        types = listOf(selectedType.copy(modifiers = emptySet())),
                    ),
                    schema,
                ),
        )
        val secondSelectedProp = selectedProp.copy(
            id = DtoPropId("dto#second-selected-discriminator"),
            name = "type",
            alias = "type",
            tailPropId = DtoPropId("dto#second-selected-discriminator"),
        )
        val duplicateType = selectedType.copy(
            propIds = listOf(selectedProp.id, secondSelectedProp.id),
        )
        val duplicateGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(duplicateType),
            props = listOf(selectedProp, secondSelectedProp).sortedBy(DtoProp::id),
        )
        val duplicateException = assertFailsWith<IllegalArgumentException> {
            duplicateType.selectedPolymorphicInputDiscriminatorPropOrNull(duplicateGraph, schema)
        }
        assertEquals(
            "Discriminator property cannot be selected by polymorphic input DTO " +
                "\"BookInput\" more than once",
            duplicateException.message,
        )
        val missingBindingId = LsiSymbolId.property(BASE_TYPE_ID, "missing")
        val missingBindingProp = selectedProp.copy(
            baseProps = listOf(DtoBasePropBinding("missing", missingBindingId)),
        )
        val missingBindingGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(selectedType),
            props = listOf(missingBindingProp),
        )
        val missingBindingException = assertFailsWith<IllegalArgumentException> {
            selectedType.selectedPolymorphicInputDiscriminatorPropOrNull(
                missingBindingGraph,
                schema,
            )
        }
        assertEquals(
            "No immutable base property '${missingBindingId.value}' for DTO property " +
                "'${selectedProp.id.value}'",
            missingBindingException.message,
        )
    }

    @Test
    fun `derives accessor null acceptance from frozen tail and input semantics`() {
        fun acceptsNull(
            nullable: Boolean,
            baseNullable: Boolean,
            ownerModifiers: Set<DtoModifier> = setOf(DtoModifier.INPUT),
            inputModifier: DtoModifier = DtoModifier.STATIC,
        ): Boolean {
            val prop = baseProp(
                name = "value",
                nullable = nullable,
                baseNullable = baseNullable,
                modifier = inputModifier,
            )
            val graph = singlePropGraph(prop)
            val ownerType = graph.types.single().copy(modifiers = ownerModifiers)
            val semanticGraph = DtoGraph(
                source = graph.source,
                rootTypeIds = graph.rootTypeIds,
                types = listOf(ownerType),
                props = graph.props,
            )
            return prop.acceptsNullInAccessor(semanticGraph)
        }

        assertTrue(acceptsNull(nullable = false, baseNullable = false))
        assertTrue(acceptsNull(nullable = true, baseNullable = true))
        assertFalse(acceptsNull(nullable = true, baseNullable = false))
        assertFalse(
            acceptsNull(
                nullable = true,
                baseNullable = true,
                ownerModifiers = setOf(DtoModifier.SPECIFICATION),
            ),
        )
        assertFalse(
            acceptsNull(
                nullable = true,
                baseNullable = true,
                ownerModifiers = setOf(DtoModifier.INPUT, DtoModifier.FUZZY),
            ),
        )
        assertFalse(
            acceptsNull(
                nullable = true,
                baseNullable = true,
                inputModifier = DtoModifier.FUZZY,
            ),
        )

        val tailProp = baseProp(
            name = "tail",
            idSuffix = "tail",
            baseNullable = false,
        )
        val pathProp = baseProp(
            name = "path",
            idSuffix = "path",
            nullable = true,
            baseNullable = true,
        ).copy(
            nextPropId = tailProp.id,
            tailPropId = tailProp.id,
        )
        val ownerType = singlePropGraph(
            pathProp.copy(nextPropId = null, tailPropId = pathProp.id),
        ).types.single()
        val pathGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(pathProp, tailProp).sortedBy(DtoProp::id),
        )

        assertFalse(pathProp.acceptsNullInAccessor(pathGraph))
        val otherPropId = DtoPropId("dto#other")
        val otherProp = pathProp.copy(
            id = otherPropId,
            nextPropId = null,
            tailPropId = otherPropId,
        )
        assertFailsWith<IllegalArgumentException> {
            pathProp.acceptsNullInAccessor(singlePropGraph(otherProp))
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
    fun `derives toString inclusion from the frozen DTO graph`() {
        val graph = graph(visibleDynamic = true)
        val type = graph.types.single()

        assertEquals(
            listOf(
                "dynamicValue" to DtoToStringInclusion.WHEN_LOADED,
                "userValue" to DtoToStringInclusion.ALWAYS,
                "staticValue" to DtoToStringInclusion.ALWAYS,
                "foldValue" to DtoToStringInclusion.ALWAYS,
                "fuzzyValue" to DtoToStringInclusion.WHEN_NON_NULL,
            ),
            type.propsInDeclarationOrder(graph).map { prop ->
                prop.name to prop.toStringInclusion(graph)
            },
        )

        val nonInputGraph = graph(visibleDynamic = true, input = false)
        assertTrue(
            nonInputGraph.types.single().propsInDeclarationOrder(nonInputGraph).all { prop ->
                prop.toStringInclusion(nonInputGraph) == DtoToStringInclusion.ALWAYS
            },
        )

        val nullableFixed = baseProp(
            name = "nullableFixed",
            modifier = DtoModifier.FIXED,
            nullable = true,
        )
        val nullableFixedGraph = singlePropGraph(nullableFixed)
        assertEquals(
            DtoToStringInclusion.ALWAYS,
            nullableFixed.toStringInclusion(nullableFixedGraph),
        )

        assertFailsWith<IllegalArgumentException> {
            graph.propsById.getValue(DtoPropId("dto#h-hidden"))
                .toStringInclusion(graph)
        }
        assertFailsWith<IllegalArgumentException> {
            type.baseProp(graph, "dynamicValue")
                .copy(name = "foreign")
                .toStringInclusion(graph)
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
                        modifier = if (input) DtoModifier.DYNAMIC else DtoModifier.STATIC,
                        idSuffix = "z-dynamic",
                        nullable = true,
                    ),
                )
            }
            add(userProp().copy(nullable = true))
            add(baseProp("staticValue", DtoModifier.STATIC, "a-static", nullable = true))
            add(foldProp().copy(nullable = true))
            add(
                baseProp(
                    name = "fuzzyValue",
                    modifier = if (input) DtoModifier.FUZZY else DtoModifier.STATIC,
                    idSuffix = "b-fuzzy",
                    nullable = true,
                ),
            )
        }
        val hiddenDynamic = baseProp(
            name = "hiddenDynamic",
            modifier = if (input) DtoModifier.DYNAMIC else DtoModifier.STATIC,
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
        baseNullable: Boolean = false,
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
            baseNullable = baseNullable,
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
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: InheritanceStrategy? = null,
        joinedTableDissociateAction: JoinedTableDissociateAction? = null,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
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
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = kind == ImmutableTypeKind.ENTITY,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
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
        val BODY_TYPE_ID = DtoTypeId("dto#branch-body")
        val MERGED_TYPE_ID = DtoTypeId("dto#branch-merged")
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
