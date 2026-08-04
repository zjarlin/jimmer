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
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
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
        assertTrue(type.isInput())
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
        assertFalse(type.isInput())
        assertFailsWith<IllegalArgumentException> {
            type.serializerPropsInDeclarationOrder(graph)
        }
    }

    @Test
    fun `identifies fixed input fields from the frozen DTO graph`() {
        val fixedProp = baseProp("fixedValue", DtoModifier.FIXED)
        val fixedGraph = singlePropGraph(fixedProp)

        assertTrue(fixedProp.requiresFixedInputField(fixedGraph))
        assertFalse(
            fixedProp.requiresFixedInputField(
                singlePropGraph(fixedProp, input = false),
            ),
        )

        val staticProp = baseProp("staticValue", DtoModifier.STATIC)
        assertFalse(staticProp.requiresFixedInputField(singlePropGraph(staticProp)))

        val userProp = userProp()
        assertFalse(userProp.requiresFixedInputField(singlePropGraph(userProp)))

        val foldProp = foldProp()
        assertFalse(foldProp.requiresFixedInputField(singlePropGraph(foldProp)))

        assertFailsWith<IllegalArgumentException> {
            fixedProp.copy(name = "foreign").requiresFixedInputField(fixedGraph)
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
        assertTrue(polymorphicRoot.isPolymorphicRoot())
        assertFalse(body.isPolymorphicRoot())
        assertFalse(merged.isPolymorphicRoot())
    }

    @Test
    fun `identifies entity base from frozen immutable semantics`() {
        val dtoType = graph(visibleDynamic = false).types.single()

        ImmutableTypeKind.entries.forEach { kind ->
            val idProp = immutableProp(
                name = "id",
                type = STRING_TYPE,
                primaryMapping = PrimaryMapping.ID,
            )
            val entity = kind == ImmutableTypeKind.ENTITY
            val schema = ImmutableSchema(
                listOf(
                    immutableType(
                        id = BASE_TYPE_ID,
                        props = if (entity) listOf(idProp) else emptyList(),
                        kind = kind,
                        idPropId = idProp.id.takeIf { entity },
                    ),
                ),
            )

            assertEquals(entity, dtoType.hasEntityBase(schema), kind.name)
            assertEquals(
                entity,
                dtoType.copy(id = MERGED_TYPE_ID, name = null).hasEntityBase(schema),
                "merged ${kind.name}",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            dtoType.copy(baseTypeId = null).hasEntityBase(ImmutableSchema(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            dtoType.hasEntityBase(ImmutableSchema(emptyList()))
        }
    }

    @Test
    fun `identifies specification from frozen DTO modifiers`() {
        val dtoType = graph(visibleDynamic = false).types.single()

        assertFalse(dtoType.isSpecification())
        assertTrue(
            dtoType.copy(
                id = MERGED_TYPE_ID,
                name = null,
                modifiers = setOf(DtoModifier.SPECIFICATION),
            ).isSpecification(),
        )
    }

    @Test
    fun `identifies sealed polymorphic roots from frozen DTO modifiers`() {
        val dtoType = graph(visibleDynamic = false).types.single()

        assertFalse(dtoType.isSealed())
        assertTrue(
            dtoType.copy(
                modifiers = dtoType.modifiers + DtoModifier.SEALED,
            ).isSealed(),
        )
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

        fun contract(typeName: String, vararg arguments: LsiTypeRef): LsiDeclaredType {
            return LsiDeclaredType(
                declarationId = LsiSymbolId.type(typeName),
                arguments = arguments.map(LsiTypeArgument::invariant),
            )
        }
        val entitySchema = schema(ImmutableTypeKind.ENTITY)
        val baseTypeRef = LsiDeclaredType(BASE_TYPE_ID)
        assertEquals(
            contract("org.babyfish.jimmer.Input", baseTypeRef),
            dtoType.copy(modifiers = setOf(DtoModifier.INPUT))
                .generatedBaseContractType(entitySchema, LsiLanguage.JAVA),
        )
        assertEquals(
            contract("org.babyfish.jimmer.View", baseTypeRef),
            dtoType.copy(modifiers = emptySet())
                .generatedBaseContractType(entitySchema, LsiLanguage.KOTLIN),
        )
        val specification = dtoType.copy(modifiers = setOf(DtoModifier.SPECIFICATION))
        assertEquals(
            contract(
                "org.babyfish.jimmer.sql.ast.query.specification.JSpecification",
                baseTypeRef,
                LsiDeclaredType(
                    LsiSymbolId.type("${BASE_TYPE_ID.requireTypeQualifiedName()}Table"),
                ),
            ),
            specification.generatedBaseContractType(entitySchema, LsiLanguage.JAVA),
        )
        assertEquals(
            contract(
                "org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification",
                baseTypeRef,
            ),
            specification.generatedBaseContractType(entitySchema, LsiLanguage.KOTLIN),
        )
        assertEquals(
            contract("org.babyfish.jimmer.EmbeddableDto", baseTypeRef),
            dtoType.generatedBaseContractType(
                schema(ImmutableTypeKind.EMBEDDABLE),
                LsiLanguage.JAVA,
            ),
        )
        assertEquals(
            null,
            dtoType.generatedBaseContractType(
                schema(ImmutableTypeKind.IMMUTABLE),
                LsiLanguage.KOTLIN,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            dtoType.generatedBaseContractType(entitySchema, LsiLanguage.UNKNOWN)
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
    fun `derives Java setters from final DTO value semantics`() {
        assertEquals("setActive", valueSetterName("active"))
        assertEquals("setEnabled", valueSetterName("isEnabled"))
        assertEquals("setEnabled", valueSetterName("enabled", immutableType = STRING_TYPE))
        assertEquals("setIsEnabled", valueSetterName("isEnabled", nullable = true))
        assertEquals("setURL", valueSetterName("URL", immutableType = STRING_TYPE))
        assertEquals("set_1", valueSetterName("_1", immutableType = STRING_TYPE))
        assertEquals(
            "setNullableType",
            valueSetterName(
                name = "nullableType",
                immutableType = BOOLEAN_TYPE.copy(nullability = LsiNullability.NULLABLE),
            ),
        )
        assertEquals(
            "setConverted",
            valueSetterName(
                name = "converted",
                converter = converter(STRING_TYPE),
            ),
        )
        assertEquals(
            "setConvertedBoolean",
            valueSetterName(
                name = "convertedBoolean",
                converter = converter(BOOLEAN_TYPE),
            ),
        )
        assertEquals(
            "setNullableConverted",
            valueSetterName(
                name = "nullableConverted",
                converter = converter(BOOLEAN_TYPE, targetNullable = true),
            ),
        )
        assertEquals(
            "setBooleanList",
            valueSetterName(
                name = "booleanList",
                immutableList = true,
            ),
        )
        val nullCheck = baseProp(name = "isMissing", baseName = "name")
            .copy(functionName = "null")
        val nullCheckGraph = singlePropGraph(nullCheck)
        val nullCheckSchema = immutableSchema(immutableProp("name", STRING_TYPE))
        assertEquals("setMissing", nullCheck.javaValueSetterName(nullCheckGraph, nullCheckSchema))
    }

    @Test
    fun `classifies nullable Java backing fields from frozen DTO semantics`() {
        assertFalse(
            baseProp("isMissing", baseName = "name")
                .copy(functionName = "null")
                .hasNullableJavaBackingField(),
        )
        assertFalse(
            baseProp("isPresent", baseName = "name")
                .copy(functionName = "notNull")
                .hasNullableJavaBackingField(),
        )
        assertTrue(baseProp("name").hasNullableJavaBackingField())
        assertTrue(userProp().hasNullableJavaBackingField())
        assertTrue(foldProp().hasNullableJavaBackingField())
    }

    @Test
    fun `derives Hibernate Validator getter names from final target language semantics`() {
        fun assertBaseGetterNames(
            name: String,
            nullable: Boolean = false,
            expectedJava: String,
            expectedKotlin: String,
        ) {
            val prop = baseProp(name = name, nullable = nullable)
            val graph = singlePropGraph(prop)
            val schema = immutableSchema(immutableProp(name, BOOLEAN_TYPE))

            assertEquals(
                expectedJava,
                prop.hibernateValidatorGetterName(LsiLanguage.JAVA, graph, schema),
            )
            assertEquals(
                expectedKotlin,
                prop.hibernateValidatorGetterName(LsiLanguage.KOTLIN, graph, schema),
            )
        }

        assertBaseGetterNames(
            name = "active",
            expectedJava = "isActive",
            expectedKotlin = "getActive",
        )
        assertBaseGetterNames(
            name = "isEnabled",
            expectedJava = "isEnabled",
            expectedKotlin = "isEnabled",
        )
        assertBaseGetterNames(
            name = "isEnabled",
            nullable = true,
            expectedJava = "getIsEnabled",
            expectedKotlin = "isEnabled",
        )
        assertBaseGetterNames(
            name = "is1",
            expectedJava = "isIs1",
            expectedKotlin = "is1",
        )
        assertBaseGetterNames(
            name = "is_",
            expectedJava = "isIs_",
            expectedKotlin = "is_",
        )

        val nullCheck = baseProp(name = "isMissing", baseName = "name")
            .copy(functionName = "null")
        val nullCheckGraph = singlePropGraph(nullCheck)
        val nullCheckSchema = immutableSchema(immutableProp("name", STRING_TYPE))
        assertTrue(nullCheck.hasPrimitiveBooleanValue(nullCheckGraph, nullCheckSchema))
        assertEquals(
            "isMissing",
            nullCheck.hibernateValidatorGetterName(
                LsiLanguage.JAVA,
                nullCheckGraph,
                nullCheckSchema,
            ),
        )
        assertEquals(
            "isMissing",
            nullCheck.hibernateValidatorGetterName(
                LsiLanguage.KOTLIN,
                nullCheckGraph,
                nullCheckSchema,
            ),
        )

        val userBoolean = userProp().copy(
            name = "isEnabled",
            alias = "isEnabled",
            type = DtoTypeRef("Boolean", emptyList(), false, LOCATION),
        )
        val userGraph = singlePropGraph(userBoolean)
        val emptySchema = immutableSchema()
        assertTrue(userBoolean.hasPrimitiveBooleanValue(userGraph, emptySchema))
        assertEquals(
            "isEnabled",
            userBoolean.hibernateValidatorGetterName(LsiLanguage.JAVA, userGraph, emptySchema),
        )
        assertEquals(
            "isEnabled",
            userBoolean.hibernateValidatorGetterName(LsiLanguage.KOTLIN, userGraph, emptySchema),
        )

        val nullableUserBoolean = userBoolean.copy(
            nullable = true,
            type = userBoolean.type.copy(nullable = true),
        )
        val nullableUserGraph = singlePropGraph(nullableUserBoolean)
        assertFalse(nullableUserBoolean.hasPrimitiveBooleanValue(nullableUserGraph, emptySchema))
        assertEquals(
            "isEnabled",
            nullableUserBoolean.hibernateValidatorGetterName(
                LsiLanguage.KOTLIN,
                nullableUserGraph,
                emptySchema,
            ),
        )

        listOf(
            "isDisplayName" to "getIsDisplayName",
            "is1" to "getIs1",
            "is_" to "getIs_",
            "is\u00e4" to "getIs\u00e4",
            "isabc" to "getIsabc",
        ).forEach { (name, javaGetterName) ->
            val userString = userProp().copy(name = name, alias = name)
            val userStringGraph = singlePropGraph(userString)
            assertEquals(
                javaGetterName,
                userString.hibernateValidatorGetterName(
                    LsiLanguage.JAVA,
                    userStringGraph,
                    emptySchema,
                ),
            )
            assertEquals(
                if (name == "isabc") "getIsabc" else name,
                userString.hibernateValidatorGetterName(
                    LsiLanguage.KOTLIN,
                    userStringGraph,
                    emptySchema,
                ),
            )
        }

        val fold = foldProp()
        val foldGraph = singlePropGraph(fold)
        assertFalse(fold.hasPrimitiveBooleanValue(foldGraph, emptySchema))
        assertEquals(
            "getFoldValue",
            fold.hibernateValidatorGetterName(LsiLanguage.JAVA, foldGraph, emptySchema),
        )
        assertEquals(
            "getFoldValue",
            fold.hibernateValidatorGetterName(LsiLanguage.KOTLIN, foldGraph, emptySchema),
        )
        val isFold = fold.copy(name = "isFold", alias = "isFold")
        val isFoldGraph = singlePropGraph(isFold)
        assertEquals(
            "getIsFold",
            isFold.hibernateValidatorGetterName(LsiLanguage.JAVA, isFoldGraph, emptySchema),
        )
        assertEquals(
            "isFold",
            isFold.hibernateValidatorGetterName(LsiLanguage.KOTLIN, isFoldGraph, emptySchema),
        )
        assertFailsWith<IllegalArgumentException> {
            fold.hibernateValidatorGetterName(LsiLanguage.UNKNOWN, foldGraph, emptySchema)
        }
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
            idProp.dtoValueAccessorName(LsiLanguage.JAVA, idGraph, schema),
        )

        val listIdProp = baseProp("targetIds", baseName = "targets").copy(functionName = "id")
        val listIdGraph = singlePropGraph(listIdProp)
        assertEquals(
            "getTargetIds",
            listIdProp.dtoValueAccessorName(LsiLanguage.JAVA, listIdGraph, schema),
        )

        val idViewProp = baseProp("targetIdView", baseName = "targetId")
        val idViewGraph = singlePropGraph(idViewProp)
        assertEquals(
            "isTargetIdView",
            idViewProp.dtoValueAccessorName(LsiLanguage.JAVA, idViewGraph, schema),
        )

        val convertedIdViewProp = baseProp(
            "convertedTargetIdView",
            baseName = "convertedTargetId",
        )
        val convertedIdViewGraph = singlePropGraph(convertedIdViewProp)
        assertEquals(
            "getConvertedTargetIdView",
            convertedIdViewProp.dtoValueAccessorName(
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

        assertEquals("getWhen", aliasProp.dtoValueAccessorName(LsiLanguage.JAVA, graph, schema))
        assertEquals("when", aliasProp.dtoValueAccessorName(LsiLanguage.KOTLIN, graph, schema))
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
    fun `resolves Kotlin base value accessor from the frozen head binding`() {
        val prop = baseProp(name = "displayName", baseName = "name").copy(
            baseProps = listOf(
                DtoBasePropBinding("name", LsiSymbolId.property(BASE_TYPE_ID, "name")),
                DtoBasePropBinding("suffix", LsiSymbolId.property(BASE_TYPE_ID, "suffix")),
            ),
        )
        val graph = singlePropGraph(prop)

        assertEquals("name", prop.kotlinBaseValueAccessorName(graph))
        assertFailsWith<IllegalArgumentException> {
            prop.copy(name = "foreign").kotlinBaseValueAccessorName(graph)
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
    fun `derives non-null draft write guards from frozen input semantics`() {
        val graph = graph(visibleDynamic = true)
        val type = graph.types.single()

        assertFalse(type.baseProp(graph, "dynamicValue").requiresNonNullDraftWriteGuard(graph))
        assertFalse(type.baseProp(graph, "staticValue").requiresNonNullDraftWriteGuard(graph))
        assertTrue(type.baseProp(graph, "fuzzyValue").requiresNonNullDraftWriteGuard(graph))
        assertFailsWith<IllegalArgumentException> {
            (graph.propsById.getValue(DtoPropId("dto#h-hidden")) as DtoBaseProp)
                .requiresNonNullDraftWriteGuard(graph)
        }
    }

    @Test
    fun `derives empty association list draft fallback from frozen semantics`() {
        val toManyProp = baseProp(
            name = "childIds",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
            baseName = "children",
        )
        val toManyGraph = singlePropGraph(toManyProp)
        val children = immutableProp(
            name = "children",
            type = STRING_TYPE,
            list = true,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.ONE_TO_MANY,
            genericTarget = true,
        )
        val toManySchema = immutableSchema(children)

        assertTrue(toManyProp.hasEntityAssociationListDraftTarget(toManyGraph, toManySchema))
        assertTrue(
            toManyProp.requiresEmptyAssociationListDraftFallback(toManyGraph, toManySchema),
        )

        val nonNullableProp = baseProp(
            name = "childIds",
            modifier = DtoModifier.STATIC,
            nullable = false,
            baseName = "children",
        )
        val nonNullableGraph = singlePropGraph(nonNullableProp)
        assertTrue(
            nonNullableProp.hasEntityAssociationListDraftTarget(nonNullableGraph, toManySchema),
        )
        assertFalse(
            nonNullableProp.requiresEmptyAssociationListDraftFallback(
                nonNullableGraph,
                toManySchema,
            ),
        )

        listOf(DtoModifier.STATIC, DtoModifier.FUZZY).forEach { modifier ->
            val optionalProp = baseProp(
                name = "childIds",
                modifier = modifier,
                nullable = true,
                baseName = "children",
            )
            val optionalGraph = singlePropGraph(optionalProp)
            assertFalse(
                optionalProp.requiresEmptyAssociationListDraftFallback(
                    optionalGraph,
                    toManySchema,
                ),
            )
        }

        val toOneProp = baseProp(
            name = "parentId",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
            baseName = "parent",
        )
        val toOneGraph = singlePropGraph(toOneProp)
        val parent = immutableProp(
            name = "parent",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            genericTarget = true,
        )
        assertFalse(
            toOneProp.hasEntityAssociationListDraftTarget(
                toOneGraph,
                immutableSchema(parent),
            ),
        )

        val scalarListProp = baseProp(
            name = "labels",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
        )
        val scalarListGraph = singlePropGraph(scalarListProp)
        val labels = immutableProp(name = "labels", type = STRING_TYPE, list = true)
        assertFalse(
            scalarListProp.hasEntityAssociationListDraftTarget(
                scalarListGraph,
                immutableSchema(labels),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            toManyProp.requiresEmptyAssociationListDraftFallback(
                toManyGraph,
                immutableSchema(),
            )
        }

        val mixedProp = toManyProp.copy(
            baseProps = listOf(
                DtoBasePropBinding("children", children.id),
                DtoBasePropBinding("labels", labels.id),
            ),
        )
        val mixedGraph = singlePropGraph(mixedProp)
        assertFailsWith<IllegalArgumentException> {
            mixedProp.requiresEmptyAssociationListDraftFallback(
                mixedGraph,
                immutableSchema(children, labels),
            )
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
            prop.dtoValueAccessorName(LsiLanguage.JAVA, graph, schema)
        }
        assertFailsWith<IllegalArgumentException> {
            prop.javaValueSetterName(graph, schema)
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
            consistentProp.dtoValueAccessorName(
                LsiLanguage.JAVA,
                consistentGraph,
                consistentSchema,
            ),
        )
        assertEquals("setMixed", consistentProp.javaValueSetterName(consistentGraph, consistentSchema))
    }

    @Test
    fun `derives value accessors for any visible DTO property`() {
        val prop = baseProp("value")
        val graph = singlePropGraph(prop, input = false)
        val schema = immutableSchema(immutableProp("value", STRING_TYPE))

        assertEquals("getValue", prop.dtoValueAccessorName(LsiLanguage.JAVA, graph, schema))
        assertEquals("setValue", prop.javaValueSetterName(graph, schema))
        assertEquals("value", prop.dtoValueAccessorName(LsiLanguage.KOTLIN, graph, schema))
        assertFailsWith<IllegalArgumentException> {
            prop.dtoValueAccessorName(LsiLanguage.UNKNOWN, graph, schema)
        }
        assertFailsWith<IllegalArgumentException> {
            prop.copy(name = "foreign").javaValueSetterName(graph, schema)
        }
    }

    @Test
    fun `resolves frozen accessor paths including hidden next properties`() {
        val store = immutableProp(
            name = "store",
            type = LsiDeclaredType(TARGET_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            targetTypeId = TARGET_TYPE_ID,
        )
        val storeName = immutableProp(
            name = "name",
            type = STRING_TYPE,
            ownerTypeId = TARGET_TYPE_ID,
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(BASE_TYPE_ID, listOf(store)),
                immutableType(TARGET_TYPE_ID, listOf(storeName)),
            ),
        )
        val tailProp = baseProp(
            name = "name",
            idSuffix = "store-name-tail",
            baseName = "name",
        ).copy(
            baseProps = listOf(DtoBasePropBinding("name", storeName.id)),
        )
        val pathProp = baseProp(
            name = "storeName",
            idSuffix = "store-name",
            baseName = "store",
        ).copy(
            nextPropId = tailProp.id,
            tailPropId = tailProp.id,
        )
        val ownerType = singlePropType(pathProp)
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(pathProp, tailProp).sortedBy(DtoProp::id),
        )

        assertEquals(
            listOf(store.id, storeName.id),
            pathProp.accessorPath(graph, schema).map(ImmutableProp::id),
        )
        assertEquals(
            listOf(storeName.id),
            tailProp.accessorPath(graph, schema).map(ImmutableProp::id),
        )

        val cyclicTailProp = tailProp.copy(nextPropId = pathProp.id)
        val cyclicGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(pathProp, cyclicTailProp).sortedBy(DtoProp::id),
        )
        assertFailsWith<IllegalArgumentException> {
            pathProp.accessorPath(cyclicGraph, schema)
        }

        val wrongTailPathProp = pathProp.copy(tailPropId = pathProp.id)
        val wrongTailGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType.copy(propIds = listOf(wrongTailPathProp.id))),
            props = listOf(wrongTailPathProp, tailProp).sortedBy(DtoProp::id),
        )
        assertFailsWith<IllegalArgumentException> {
            wrongTailPathProp.accessorPath(wrongTailGraph, schema)
        }
    }

    @Test
    fun `requires every accessor binding to resolve the same Draft slot`() {
        val name = immutableProp("name", STRING_TYPE)
        val inheritedName = immutableProp(
            name = "name",
            type = STRING_TYPE,
            ownerTypeId = TARGET_TYPE_ID,
        ).copy(
            declarationId = name.declarationId,
            declaringTypeId = name.declaringTypeId,
            inherited = true,
            overrideChain = listOf(name.declarationId),
        )
        val label = immutableProp("label", STRING_TYPE)
        val schema = ImmutableSchema(
            listOf(
                immutableType(BASE_TYPE_ID, listOf(name, label)),
                immutableType(TARGET_TYPE_ID, listOf(inheritedName)),
            ),
        )
        val sameSlotProp = baseProp("displayName", baseName = "name").copy(
            baseProps = listOf(
                DtoBasePropBinding("name", name.id),
                DtoBasePropBinding("inheritedName", inheritedName.id),
            ),
        )
        val sameSlotGraph = singlePropGraph(sameSlotProp)
        assertEquals(
            listOf(name.id),
            sameSlotProp.accessorPath(sameSlotGraph, schema).map(ImmutableProp::id),
        )

        val differentSlotProp = sameSlotProp.copy(
            baseProps = listOf(
                DtoBasePropBinding("name", name.id),
                DtoBasePropBinding("label", label.id),
            ),
        )
        val differentSlotGraph = singlePropGraph(differentSlotProp)
        val exception = assertFailsWith<IllegalArgumentException> {
            differentSlotProp.accessorPath(differentSlotGraph, schema)
        }
        assertEquals(
            "DTO base property bindings must resolve one Draft slot: ${differentSlotProp.id.value}",
            exception.message,
        )

        val missingPropId = LsiSymbolId.property(BASE_TYPE_ID, "missing")
        val missingProp = baseProp("missing").copy(
            baseProps = listOf(DtoBasePropBinding("missing", missingPropId)),
        )
        assertFailsWith<IllegalArgumentException> {
            missingProp.accessorPath(singlePropGraph(missingProp), schema)
        }
    }

    @Test
    fun `classifies accessor conversion directly from frozen DTO semantics`() {
        val targetId = immutableProp(
            name = "id",
            type = STRING_TYPE,
            ownerTypeId = TARGET_TYPE_ID,
            primaryMapping = PrimaryMapping.ID,
        )
        val target = immutableProp(
            name = "target",
            type = LsiDeclaredType(TARGET_TYPE_ID),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            targetTypeId = TARGET_TYPE_ID,
        )
        val plain = immutableProp("plain", STRING_TYPE)
        val status = immutableProp("status", STRING_TYPE)
        val converted = immutableProp(
            name = "converted",
            type = BOOLEAN_TYPE,
            converter = converter(STRING_TYPE),
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(BASE_TYPE_ID, listOf(converted, plain, status, target)),
                immutableType(
                    id = TARGET_TYPE_ID,
                    props = listOf(targetId),
                    kind = ImmutableTypeKind.ENTITY,
                    idPropId = targetId.id,
                ),
            ),
        )

        fun kind(prop: DtoBaseProp, graph: DtoGraph = singlePropGraph(prop)) =
            prop.accessorConversionKind(graph, schema)

        val plainProp = baseProp("plain")
        assertEquals(DtoAccessorConversionKind.NONE, kind(plainProp))

        val idProp = baseProp("targetId", baseName = "target").copy(functionName = "id")
        assertEquals(DtoAccessorConversionKind.ASSOCIATED_ID, kind(idProp))

        val enumProp = baseProp("status").copy(
            enumType = DtoEnumType(
                numeric = false,
                mappings = listOf(DtoEnumMapping("ENABLED", "enabled")),
            ),
        )
        assertEquals(DtoAccessorConversionKind.ENUM, kind(enumProp))

        val converterProp = baseProp("converted")
        assertEquals(DtoAccessorConversionKind.CONVERTER, kind(converterProp))

        val constructorProp = baseProp("target").copy(targetTypeId = TARGET_DTO_TYPE_ID)
        val constructorGraph = graphWithTarget(constructorProp)
        assertEquals(
            DtoAccessorConversionKind.OBJECT_CONSTRUCTOR,
            kind(constructorProp, constructorGraph),
        )

        val reusableProp = baseProp("target").copy(
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = "demo.dto.TargetView",
                targetBaseTypeId = TARGET_TYPE_ID,
                kind = DtoReusableTypeKind.VIEW,
                location = LOCATION,
            ),
        )
        assertEquals(DtoAccessorConversionKind.OBJECT_METADATA, kind(reusableProp))

        val polymorphicProp = baseProp("target").copy(targetTypeId = TARGET_DTO_TYPE_ID)
        val polymorphicGraph = graphWithTarget(polymorphicProp, polymorphic = true)
        assertEquals(
            DtoAccessorConversionKind.OBJECT_METADATA,
            kind(polymorphicProp, polymorphicGraph),
        )
    }

    @Test
    fun `rejects inconsistent frozen conversion fields across accessor path`() {
        val name = immutableProp("name", STRING_TYPE)
        val schema = immutableSchema(name)
        val tailProp = baseProp(
            name = "name",
            idSuffix = "conversion-tail",
        )
        val headProp = baseProp(
            name = "displayName",
            idSuffix = "conversion-head",
            baseName = "name",
        ).copy(
            nextPropId = tailProp.id,
            tailPropId = tailProp.id,
            functionName = "id",
        )
        val ownerType = singlePropType(headProp)
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(headProp, tailProp).sortedBy(DtoProp::id),
        )

        assertFailsWith<IllegalArgumentException> {
            headProp.accessorConversionKind(graph, schema)
        }

        val enumType = DtoEnumType(
            numeric = false,
            mappings = listOf(DtoEnumMapping("A", "a")),
        )
        val enumHead = headProp.copy(functionName = null, enumType = enumType)
        val enumGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(enumHead, tailProp).sortedBy(DtoProp::id),
        )
        assertFailsWith<IllegalArgumentException> {
            enumHead.accessorConversionKind(enumGraph, schema)
        }
    }

    @Test
    fun `uses direct base access only when frozen source representations match`() {
        val directImmutableProp = immutableProp("name", STRING_TYPE)
        val directProp = baseProp("name")
        val directGraph = singlePropGraph(directProp)
        val directSchema = immutableSchema(directImmutableProp)
        for (language in listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN)) {
            assertTrue(
                directProp.usesDirectBaseAccess(
                    directGraph,
                    directSchema,
                    language,
                    ::generatedTargetType,
                ),
            )
        }

        val primitiveProp = baseProp("active")
        val primitiveGraph = singlePropGraph(primitiveProp)
        val primitiveSchema = immutableSchema(immutableProp("active", BOOLEAN_TYPE))
        for (language in listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN)) {
            assertTrue(
                primitiveProp.usesDirectBaseAccess(
                    primitiveGraph,
                    primitiveSchema,
                    language,
                    ::generatedTargetType,
                ),
            )
        }

        val nullableBoxedBooleanType = BOOLEAN_TYPE.copy(
            nullability = LsiNullability.NULLABLE,
            boxed = true,
        )
        val nullablePrimitiveProp = baseProp(
            name = "active",
            nullable = true,
            baseNullable = true,
        )
        val nullablePrimitiveGraph = singlePropGraph(nullablePrimitiveProp)
        val nullablePrimitiveSchema = immutableSchema(
            immutableProp(
                name = "active",
                type = nullableBoxedBooleanType,
                nullable = true,
            ),
        )
        for (language in listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN)) {
            assertTrue(
                nullablePrimitiveProp.usesDirectBaseAccess(
                    nullablePrimitiveGraph,
                    nullablePrimitiveSchema,
                    language,
                    ::generatedTargetType,
                ),
            )
        }

        val flatTailProp = baseProp(
            name = "name",
            idSuffix = "direct-access-tail",
        )
        val flatProp = baseProp(
            name = "displayName",
            idSuffix = "direct-access-head",
            baseName = "name",
        ).copy(
            nextPropId = flatTailProp.id,
            tailPropId = flatTailProp.id,
        )
        val flatGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(singlePropType(flatProp)),
            props = listOf(flatProp, flatTailProp).sortedBy(DtoProp::id),
        )
        assertFalse(
            flatProp.usesDirectBaseAccess(
                flatGraph,
                directSchema,
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )

        val discriminatorImmutableProp = immutableProp(
            name = "kind",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.DISCRIMINATOR,
        )
        val discriminatorProp = baseProp("kind")
        assertFalse(
            discriminatorProp.usesDirectBaseAccess(
                singlePropGraph(discriminatorProp),
                immutableSchema(discriminatorImmutableProp),
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )

        val enumImmutableProp = immutableProp(
            name = "status",
            type = LsiDeclaredType(LsiSymbolId.type("demo.Status")),
        )
        val enumProp = baseProp("status").copy(
            enumType = DtoEnumType(
                numeric = false,
                mappings = listOf(DtoEnumMapping("ENABLED", "enabled")),
            ),
        )
        assertFalse(
            enumProp.usesDirectBaseAccess(
                singlePropGraph(enumProp),
                immutableSchema(enumImmutableProp),
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )

        val boxedBooleanType = BOOLEAN_TYPE.copy(boxed = true)
        val primitiveConvertedImmutableProp = immutableProp(
            name = "active",
            type = BOOLEAN_TYPE,
            converter = converter(
                targetType = boxedBooleanType,
                sourceType = boxedBooleanType,
            ),
        )
        val convertedProp = baseProp("active")
        assertFalse(
            convertedProp.usesDirectBaseAccess(
                singlePropGraph(convertedProp),
                immutableSchema(primitiveConvertedImmutableProp),
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )

        val nullableConvertedType = boxedBooleanType.copy(
            nullability = LsiNullability.NULLABLE,
        )
        val nullableConvertedImmutableProp = immutableProp(
            name = "active",
            type = nullableConvertedType,
            nullable = true,
            converter = converter(
                targetType = boxedBooleanType,
                sourceType = boxedBooleanType,
            ),
        )
        val nullableConvertedProp = baseProp(
            name = "active",
            nullable = true,
            baseNullable = true,
        )
        val nullableConvertedSchema = immutableSchema(nullableConvertedImmutableProp)
        assertTrue(
            nullableConvertedProp.usesDirectBaseAccess(
                singlePropGraph(nullableConvertedProp),
                nullableConvertedSchema,
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )
        assertFalse(
            nullableConvertedProp.usesDirectBaseAccess(
                singlePropGraph(nullableConvertedProp, input = false),
                nullableConvertedSchema,
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )
    }

    @Test
    fun `preserves target language nullability rules for direct base access`() {
        val nullableStringType = STRING_TYPE.copy(nullability = LsiNullability.NULLABLE)
        val nullableImmutableProp = immutableProp(
            name = "name",
            type = nullableStringType,
            nullable = true,
        )
        val requiredProp = baseProp("name", baseNullable = true)
        val requiredGraph = singlePropGraph(requiredProp)
        val requiredSchema = immutableSchema(nullableImmutableProp)
        assertTrue(
            requiredProp.usesDirectBaseAccess(
                requiredGraph,
                requiredSchema,
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )
        assertFalse(
            requiredProp.usesDirectBaseAccess(
                requiredGraph,
                requiredSchema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )

        val widenedProp = baseProp("name", nullable = true, baseNullable = false)
        val widenedGraph = singlePropGraph(widenedProp)
        val nonNullSchema = immutableSchema(immutableProp("name", STRING_TYPE))
        for (language in listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN)) {
            assertFalse(
                widenedProp.usesDirectBaseAccess(
                    widenedGraph,
                    nonNullSchema,
                    language,
                    ::generatedTargetType,
                ),
            )
        }

        val nullableArgument = STRING_TYPE.copy(nullability = LsiNullability.NULLABLE)
        val listWithNullableArgument = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(nullableArgument)),
        )
        val listWithNonNullArgument = listWithNullableArgument.copy(
            arguments = listOf(LsiTypeArgument.invariant(STRING_TYPE)),
        )
        val listImmutableProp = immutableProp(
            name = "tags",
            type = listWithNullableArgument,
            list = true,
            converter = converter(
                targetType = listWithNonNullArgument,
                sourceType = listWithNullableArgument,
            ),
        )
        val listProp = baseProp("tags")
        val listGraph = singlePropGraph(listProp)
        val listSchema = immutableSchema(listImmutableProp)
        assertTrue(
            listProp.usesDirectBaseAccess(
                listGraph,
                listSchema,
                LsiLanguage.JAVA,
                ::generatedTargetType,
            ),
        )
        assertFalse(
            listProp.usesDirectBaseAccess(
                listGraph,
                listSchema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )
    }

    @Test
    fun `compares canonical target source representations without poet`() {
        val nonNullArgument = STRING_TYPE.copy(nullability = LsiNullability.NON_NULL)
        val nonNullList = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(nonNullArgument)),
        )
        for (nullability in listOf(LsiNullability.PLATFORM, LsiNullability.UNKNOWN)) {
            val platformList = nonNullList.copy(
                arguments = listOf(
                    LsiTypeArgument.invariant(
                        nonNullArgument.copy(nullability = nullability),
                    ),
                ),
            )
            assertTrue(platformList.hasSameDtoSourceType(nonNullList, LsiLanguage.KOTLIN))
        }

        val nullableList = nonNullList.copy(
            arguments = listOf(
                LsiTypeArgument.invariant(
                    nonNullArgument.copy(nullability = LsiNullability.NULLABLE),
                ),
            ),
        )
        assertFalse(nullableList.hasSameDtoSourceType(nonNullList, LsiLanguage.KOTLIN))

        val javaIntegerType = LsiDeclaredType(LsiSymbolId.type("java.lang.Integer"))
        val intType = LsiPrimitiveType(LsiPrimitiveKind.INT)
        assertTrue(javaIntegerType.hasSameDtoSourceType(intType, LsiLanguage.KOTLIN))
        assertTrue(
            javaIntegerType.hasSameDtoSourceType(
                intType.copy(boxed = true),
                LsiLanguage.JAVA,
            ),
        )
        assertFalse(
            javaIntegerType.hasSameDtoSourceType(
                intType,
                LsiLanguage.JAVA,
            ),
        )

        val generatedPrimitiveArray = LsiArrayType(intType)
        val nativeBoxedArray = LsiArrayType(intType.copy(boxed = true))
        assertFalse(
            generatedPrimitiveArray.hasSameDtoSourceType(
                nativeBoxedArray,
                LsiLanguage.KOTLIN,
            ),
        )
        assertTrue(
            generatedPrimitiveArray.hasSameDtoSourceType(
                LsiDeclaredType(LsiSymbolId.type("kotlin.IntArray")),
                LsiLanguage.KOTLIN,
            ),
        )
    }

    @Test
    fun `requires accessors for loaded state and fold null guards`() {
        val nullableStringType = STRING_TYPE.copy(nullability = LsiNullability.NULLABLE)
        val immutableProp = immutableProp(
            name = "name",
            type = nullableStringType,
            nullable = true,
        )
        val dynamicProp = baseProp(
            name = "name",
            modifier = DtoModifier.DYNAMIC,
            nullable = true,
            baseNullable = true,
        )
        val dynamicGraph = singlePropGraph(dynamicProp)
        val schema = immutableSchema(immutableProp)
        assertTrue(
            dynamicProp.usesDirectBaseAccess(
                dynamicGraph,
                schema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )
        assertTrue(
            dynamicProp.requiresDtoPropAccessor(
                dynamicGraph,
                schema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )

        val staticProp = dynamicProp.copy(inputModifier = DtoModifier.STATIC)
        val staticGraph = singlePropGraph(staticProp)
        assertFalse(
            staticProp.requiresDtoPropAccessor(
                staticGraph,
                schema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )
        assertFalse(
            staticGraph.types.single().hasDtoPropAccessorFields(
                staticGraph,
                schema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )

        val nullGuardProp = staticProp.copy(id = DtoPropId("dto#hidden:null-guard"))
        val foldProp = foldProp().copy(nullGuardPropId = nullGuardProp.id)
        val ownerType = singlePropType(staticProp).copy(
            propIds = listOf(staticProp.id, foldProp.id),
            hiddenFlatPropIds = listOf(nullGuardProp.id),
        )
        val foldGraph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(ownerType),
            props = listOf(staticProp, nullGuardProp, foldProp).sortedBy(DtoProp::id),
        )
        assertTrue(
            ownerType.hasDtoPropAccessorFields(
                foldGraph,
                schema,
                LsiLanguage.KOTLIN,
                ::generatedTargetType,
            ),
        )
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
        return prop.dtoValueAccessorName(LsiLanguage.JAVA, graph, schema)
    }

    private fun valueSetterName(
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
        return prop.javaValueSetterName(graph, schema)
    }

    private fun singlePropGraph(
        prop: DtoProp,
        input: Boolean = true,
    ): DtoGraph {
        val type = singlePropType(prop, input)
        return DtoGraph(SOURCE, listOf(TYPE_ID), listOf(type), listOf(prop))
    }

    private fun singlePropType(
        prop: DtoProp,
        input: Boolean = true,
    ): DtoType {
        return DtoType(
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
    }

    private fun graphWithTarget(
        prop: DtoBaseProp,
        polymorphic: Boolean = false,
    ): DtoGraph {
        val ownerType = singlePropType(prop)
        val targetType = ownerType.copy(
            id = TARGET_DTO_TYPE_ID,
            baseTypeId = TARGET_TYPE_ID,
            name = "TargetView",
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = if (polymorphic) {
                DtoPolymorphism(
                    exhaustive = true,
                    branches = listOf(
                        DtoPolymorphicBranch(
                            kind = DtoPolymorphicBranchKind.DEFAULT,
                            targetBaseTypeId = null,
                            declaredClassName = null,
                            className = "DefaultTargetView",
                            bodyTypeId = TARGET_BODY_DTO_TYPE_ID,
                            mergedTypeId = TARGET_MERGED_DTO_TYPE_ID,
                            implicit = false,
                            location = LOCATION,
                        ),
                    ),
                )
            } else {
                null
            },
        )
        val branchTypes = if (polymorphic) {
            listOf(
                targetType.copy(
                    id = TARGET_BODY_DTO_TYPE_ID,
                    name = null,
                    polymorphism = null,
                ),
                targetType.copy(
                    id = TARGET_MERGED_DTO_TYPE_ID,
                    name = null,
                    polymorphism = null,
                ),
            )
        } else {
            emptyList()
        }
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = (listOf(ownerType, targetType) + branchTypes).sortedBy(DtoType::id),
            props = listOf(prop),
        )
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
        nullable: Boolean = false,
        list: Boolean = false,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        associationKind: AssociationKind = AssociationKind.NONE,
        targetTypeId: LsiSymbolId? = null,
        view: ImmutableView? = null,
        converter: ImmutableConverter? = null,
        genericTarget: Boolean = false,
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
            nullable = nullable,
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
            genericTarget = genericTarget,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private fun converter(
        targetType: LsiTypeRef,
        targetNullable: Boolean = false,
        sourceType: LsiTypeRef = BOOLEAN_TYPE,
    ): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = LsiSymbolId.type("demo.Converter"),
            sourceType = sourceType,
            targetType = targetType,
            sourceNullable = false,
            targetNullable = targetNullable,
            propertyNullable = false,
        )
    }

    private fun generatedTargetType(@Suppress("UNUSED_PARAMETER") prop: DtoProp): LsiDeclaredType {
        return LsiDeclaredType(LsiSymbolId.type("demo.dto.GeneratedTarget"))
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
        val TARGET_DTO_TYPE_ID = DtoTypeId("dto#target-view")
        val TARGET_BODY_DTO_TYPE_ID = DtoTypeId("dto#target-view-body")
        val TARGET_MERGED_DTO_TYPE_ID = DtoTypeId("dto#target-view-merged")
        val USER_PROP_ID = DtoPropId("dto#c-user")
        val FOLD_PROP_ID = DtoPropId("dto#d-fold")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val TARGET_TYPE_ID = LsiSymbolId.type("demo.Target")
        val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
    }
}
