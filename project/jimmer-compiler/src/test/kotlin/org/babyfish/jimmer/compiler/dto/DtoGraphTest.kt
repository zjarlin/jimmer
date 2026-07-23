package org.babyfish.jimmer.compiler.dto

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.client.toClientDefinitionDocumentation
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import org.babyfish.jimmer.dto.compiler.DtoModifier as AstDtoModifier
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoConfigTypeRef
import site.addzero.lsi.jimmer.dto.DtoConfigValue
import site.addzero.lsi.jimmer.dto.DtoEnumMapping
import site.addzero.lsi.jimmer.dto.DtoEnumType
import site.addzero.lsi.jimmer.dto.DtoFetchType
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContract
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoLikeOption
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoOrderItem
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoPredicate
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoPropConfig
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoPropPathNode
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoTypeArgument
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.DtoVariance

class DtoGraphTest {
    @Test
    fun `complex DTO graph is self contained frozen data`() {
        val graph = complexGraph()
        val forbiddenTypes = graph.reachableTypeNames().filter(::isForbiddenCompilerStateType)

        assertTrue(forbiddenTypes.isEmpty(), "DTO graph retains forbidden state: $forbiddenTypes")
        assertEquals(graph.types, graph.typesById.values.toList())
        assertEquals(graph.props, graph.propsById.values.toList())
        assertEquals(setOf(ROOT_TYPE_ID), graph.rootTypeIds.toSet())
    }

    @Test
    fun `complex DTO graph captures nested enum config polymorphism and recursion`() {
        val graph = complexGraph()
        val rootType = graph.typesById.getValue(ROOT_TYPE_ID)
        val statusProp = graph.propsById.getValue(STATUS_PROP_ID) as DtoBaseProp
        val storeProp = graph.propsById.getValue(STORE_PROP_ID) as DtoBaseProp
        val childrenProp = graph.propsById.getValue(CHILDREN_PROP_ID) as DtoBaseProp
        val polymorphism = requireNotNull(rootType.polymorphism)

        assertEquals(NESTED_TYPE_ID, storeProp.targetTypeId)
        assertEquals(listOf("DRAFT", "PUBLISHED"), statusProp.enumType?.mappings?.map { mapping -> mapping.constant })
        assertEquals(FILTER_TYPE_ID, storeProp.config?.filter?.typeId)
        assertEquals(RECURSION_TYPE_ID, storeProp.config?.recursion?.typeId)
        assertEquals(ROOT_TYPE_ID, childrenProp.targetTypeId)
        assertTrue(childrenProp.recursive)
        assertFalse(polymorphism.exhaustive)
        assertEquals(
            listOf(DtoPolymorphicBranchKind.DEFAULT, DtoPolymorphicBranchKind.TYPE),
            polymorphism.branches.map(DtoPolymorphicBranch::kind),
        )
    }

    @Test
    fun `precompiled document and state retain only frozen dto graph`() {
        val schema = schema(complexGraph())
        val state = JimmerDtoCompilerFeatureState(
            status = JimmerDtoCompilerFeatureStatus.RESOLVED,
            dependencyStatus = JimmerDtoCompilerDependencyStatus.RESOLVED,
            schema = schema,
            unresolvedDocuments = emptyList(),
            failures = emptyList(),
            defaultNullableInputModifier = AstDtoModifier.STATIC,
            rendererOptions = JimmerDtoRendererOptions(
                jacksonVersion = JimmerDtoJacksonVersion.JACKSON_2,
                hibernateValidatorEnhancement = false,
                aptFieldVisibility = JimmerDtoFieldVisibility.PRIVATE,
                kspMutable = false,
            ),
            effectiveKspMutableByRootTypeId = schema.documents
                .flatMap { document -> document.graph.rootTypeIds }
                .sorted()
                .associateWith { false },
            immutableDependencyFingerprint = "immutable-fingerprint",
        )
        val forbiddenTypes = state.reachableTypeNames().filter(::isForbiddenCompilerStateType)

        assertTrue(forbiddenTypes.isEmpty(), "DTO feature state retains forbidden state: $forbiddenTypes")
        assertTrue(schema.fingerprint() in state.fingerprint)
        assertTrue(state.rendererOptions.fingerprint in state.fingerprint)
    }

    @Test
    fun `precompiled document and state contracts exclude compiler and workspace types`() {
        val fieldTypeSignatures = reachableFieldTypeSignatures(
            JimmerDtoPrecompiledDocument::class.java,
            JimmerDtoCompilerFeatureState::class.java,
        )
        val forbiddenSignatures = fieldTypeSignatures.filter { signature ->
            FORBIDDEN_RENDER_STATE_TYPE_NAMES.any(signature::contains)
        }

        assertTrue(
            forbiddenSignatures.isEmpty(),
            "DTO precompiled contracts expose forbidden field types: $forbiddenSignatures",
        )
    }

    @Test
    fun `complex graph snapshot is stable across equivalent construction order`() {
        val first = schema(complexGraph())
        val reordered = schema(reorderedSetGraph())

        assertEquals(first.normalizedSnapshot(), reordered.normalizedSnapshot())
        assertEquals(first.fingerprint(), reordered.fingerprint())
        assertEquals(64, first.fingerprint().length)
        assertTrue("base-prop|" in first.normalizedSnapshot())
        assertTrue("user-prop|" in first.normalizedSnapshot())
        assertTrue("fold-prop|" in first.normalizedSnapshot())
        assertTrue("branch|" in first.normalizedSnapshot())
        assertTrue("BookRecursion" in first.normalizedSnapshot())
    }

    @Test
    fun `complex graph fingerprint changes for every DTO semantic mutation`() {
        val graph = complexGraph()
        val baseline = schema(graph).fingerprint()
        val semanticMutations = listOf(
            graph.withRootType { type -> type.copy(documentation = "Changed DTO contract") },
            graph.withProp(STATUS_PROP_ID) { prop ->
                val baseProp = prop as DtoBaseProp
                baseProp.copy(
                    enumType = requireNotNull(baseProp.enumType).copy(
                        mappings = listOf(
                            DtoEnumMapping("DRAFT", "draft"),
                            DtoEnumMapping("PUBLISHED", "released"),
                        ),
                    ),
                )
            },
            graph.withProp(STORE_PROP_ID) { prop ->
                val baseProp = prop as DtoBaseProp
                baseProp.copy(config = requireNotNull(baseProp.config).copy(depth = 7))
            },
            graph.withRootType { type ->
                val polymorphism = requireNotNull(type.polymorphism)
                type.copy(
                    polymorphism = polymorphism.copy(
                        branches = polymorphism.branches.map { branch ->
                            if (branch.kind == DtoPolymorphicBranchKind.TYPE) {
                                branch.copy(className = "demo.dto.ChangedSpecialBookView")
                            } else {
                                branch
                            }
                        },
                    ),
                )
            },
            graph.withRootType { type ->
                type.copy(
                    annotations = type.annotations.map { annotation ->
                        annotation.copy(
                            arguments = annotation.arguments.map { argument ->
                                if (argument.name == "nested") {
                                    argument.copy(
                                        value = DtoAnnotationValue.LiteralValue("\"changed\""),
                                    )
                                } else {
                                    argument
                                }
                            },
                        )
                    },
                )
            },
        )

        semanticMutations.forEach { mutation ->
            assertNotEquals(baseline, schema(mutation).fingerprint())
        }
        assertEquals(semanticMutations.size, semanticMutations.map { mutation -> schema(mutation).fingerprint() }.toSet().size)
    }

    @Test
    fun `real dto precompiler freezes nested flat and polymorphic DTO graph`() {
        val fixture = realPrecompilerFixture()
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(CompilerInputDocumentReferenceFreezer().freeze(fixture.document)),
            immutableSchema = fixture.schema,
            immutableSemanticRootTypeIds = fixture.schema.types.mapTo(sortedSetOf(), ImmutableType::id),
            workspace = fixture.workspace,
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = AstDtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )

        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString { failure -> failure.message })
        assertTrue(outcome.unresolvedDocuments.isEmpty())
        val document = outcome.schema.documents.single()
        val graph = document.graph
        val rootType = graph.typesById.getValue(graph.rootTypeIds.single())
        val rootProps = rootType.propIds.map(graph.propsById::getValue)
        val nestedProp = rootProps.single { prop -> prop.name == "publisher" } as DtoBaseProp
        val nestedType = graph.typesById.getValue(assertNotNull(nestedProp.targetTypeId))
        val nestedPropNames = nestedType.propIds.map { propId -> graph.propsById.getValue(propId).name }
        val polymorphism = assertNotNull(rootType.polymorphism)

        assertEquals(listOf("id", "name"), nestedPropNames)
        assertEquals(
            "DTO client documentation\n@param name DTO name documentation\n",
            rootType.documentation,
        )
        val nameProp = rootProps.single { prop -> prop.name == "name" } as DtoBaseProp
        assertEquals("DTO name documentation", nameProp.documentation)
        assertEquals("DTO name documentation", nameProp.dtoDocumentation)
        assertEquals(
            "Store documentation\n@param name Store type name documentation\n",
            nestedType.documentation,
        )
        assertEquals(
            "Store name documentation\n",
            nestedType.propIds
                .map(graph.propsById::getValue)
                .single { prop -> prop.name == "name" }
                .documentation,
        )
        val storeNameProp = rootProps.single { prop -> prop.name == "storeName" } as DtoBaseProp
        assertEquals("Store name documentation\n", storeNameProp.documentation)
        assertNull(storeNameProp.dtoDocumentation)
        val rootTypeName = assertNotNull(rootType.name)
        val rootTypeId = LsiSymbolId.type(
            if (rootType.packageName.isEmpty()) rootTypeName else "${rootType.packageName}.$rootTypeName"
        )
        val clientDocumentation = outcome.schema.toClientDefinitionDocumentation(fixture.schema)
            .getValue(rootTypeId)
        assertEquals("DTO name documentation", clientDocumentation.properties.getValue("name"))
        assertEquals("Store name documentation", clientDocumentation.properties.getValue("storeName"))
        assertTrue(rootType.hiddenFlatPropIds.isNotEmpty())
        assertTrue(rootProps.any { prop -> prop.name == "storeId" })
        assertTrue(rootProps.any { prop -> prop.name == "storeName" })
        assertTrue(polymorphism.branches.any { branch -> branch.kind == DtoPolymorphicBranchKind.TYPE })
        polymorphism.branches.forEach { branch ->
            assertTrue(branch.bodyTypeId in graph.typesById)
            assertTrue(branch.mergedTypeId in graph.typesById)
        }
        assertTrue(graph.reachableTypeNames().none(::isForbiddenCompilerStateType))
        assertTrue("taxCode" in outcome.schema.normalizedSnapshot())
    }

    private fun complexGraph(): DtoGraph {
        val source = LsiSource.of(
            path = "demo-project/src/main/dto/demo/Book.dto",
            language = LsiLanguage.UNKNOWN,
        )
        val rootType = DtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = linkedSetOf(DtoModifier.INPUT, DtoModifier.FIXED),
            annotations = listOf(markerAnnotation(source)),
            superInterfaces = listOf(
                DtoTypeRef(
                    typeName = "demo.View",
                    arguments = listOf(
                        DtoTypeArgument(
                            variance = DtoVariance.OUT,
                            type = DtoTypeRef(
                                typeName = "java.lang.String",
                                arguments = emptyList(),
                                nullable = true,
                                location = location(source, 2, 31),
                            ),
                        )
                    ),
                    nullable = false,
                    location = location(source, 2, 21),
                )
            ),
            documentation = "Book DTO contract",
            location = location(source, 2, 1),
            focusedRecursion = true,
            propIds = listOf(
                ID_PROP_ID,
                STATUS_PROP_ID,
                STORE_PROP_ID,
                CHILDREN_PROP_ID,
                DISPLAY_NAME_PROP_ID,
                SUMMARY_PROP_ID,
            ),
            hiddenFlatPropIds = listOf(HIDDEN_STORE_ID_PROP_ID),
            polymorphism = DtoPolymorphism(
                exhaustive = false,
                branches = listOf(
                    DtoPolymorphicBranch(
                        kind = DtoPolymorphicBranchKind.DEFAULT,
                        targetBaseTypeId = null,
                        declaredClassName = null,
                        className = "demo.dto.BookView.Default",
                        bodyTypeId = DEFAULT_BRANCH_TYPE_ID,
                        mergedTypeId = DEFAULT_MERGED_TYPE_ID,
                        implicit = true,
                        location = location(source, 21, 5),
                    ),
                    DtoPolymorphicBranch(
                        kind = DtoPolymorphicBranchKind.TYPE,
                        targetBaseTypeId = SPECIAL_BOOK_TYPE_ID,
                        declaredClassName = "Special",
                        className = "demo.dto.SpecialBookView",
                        bodyTypeId = SPECIAL_BRANCH_TYPE_ID,
                        mergedTypeId = SPECIAL_MERGED_TYPE_ID,
                        implicit = false,
                        location = location(source, 22, 5),
                    ),
                ),
            ),
        )
        val nestedType = DtoType(
            id = NESTED_TYPE_ID,
            baseTypeId = STORE_TYPE_ID,
            packageName = "demo.dto",
            name = null,
            modifiers = setOf(DtoModifier.DYNAMIC),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = "Nested store",
            location = location(source, 8, 12),
            focusedRecursion = false,
            propIds = listOf(STORE_NAME_PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val branchTypes = listOf(
            branchType(DEFAULT_BRANCH_TYPE_ID, BOOK_TYPE_ID, source, 21),
            branchType(DEFAULT_MERGED_TYPE_ID, BOOK_TYPE_ID, source, 21),
            branchType(SPECIAL_BRANCH_TYPE_ID, SPECIAL_BOOK_TYPE_ID, source, 22),
            branchType(SPECIAL_MERGED_TYPE_ID, SPECIAL_BOOK_TYPE_ID, source, 22),
        )
        val props = listOf(
            baseProp(
                id = ID_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "id",
                source = source,
                line = 4,
                basePropId = LsiSymbolId.property(BOOK_TYPE_ID, "id"),
            ),
            DtoBaseProp(
                id = STATUS_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "status",
                alias = null,
                nullable = false,
                annotations = emptyList(),
                documentation = "Publishing status",
                aliasLocation = location(source, 5, 5),
                baseLocation = location(source, 5, 5),
                baseProps = listOf(
                    DtoBasePropBinding("status", LsiSymbolId.property(BOOK_TYPE_ID, "status"))
                ),
                basePath = "status",
                nextPropId = null,
                tailPropId = STATUS_PROP_ID,
                baseNullable = false,
                inputModifier = DtoModifier.FIXED,
                functionName = null,
                targetTypeId = null,
                enumType = DtoEnumType(
                    numeric = false,
                    mappings = listOf(
                        DtoEnumMapping("DRAFT", "draft"),
                        DtoEnumMapping("PUBLISHED", "published"),
                    ),
                ),
                config = null,
                recursive = false,
                likeOptions = emptySet(),
            ),
            DtoBaseProp(
                id = STORE_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "store",
                alias = "storeDetail",
                nullable = true,
                annotations = listOf(markerAnnotation(source)),
                documentation = "Configured nested store",
                aliasLocation = location(source, 7, 14),
                baseLocation = location(source, 7, 5),
                baseProps = listOf(
                    DtoBasePropBinding("store", LsiSymbolId.property(BOOK_TYPE_ID, "store"))
                ),
                basePath = "store",
                nextPropId = null,
                tailPropId = STORE_PROP_ID,
                baseNullable = true,
                inputModifier = DtoModifier.DYNAMIC,
                functionName = "flat",
                targetTypeId = NESTED_TYPE_ID,
                enumType = null,
                config = DtoPropConfig(
                    predicate = DtoPredicate.And(
                        listOf(
                            DtoPredicate.Comparison(
                                path = listOf(
                                    DtoPropPathNode(
                                        propId = LsiSymbolId.property(STORE_TYPE_ID, "name"),
                                        associatedId = false,
                                    )
                                ),
                                operator = "like",
                                value = DtoConfigValue.StringValue("MANNING"),
                            ),
                            DtoPredicate.Nullity(
                                path = listOf(
                                    DtoPropPathNode(
                                        propId = LsiSymbolId.property(STORE_TYPE_ID, "website"),
                                        associatedId = false,
                                    )
                                ),
                                negative = true,
                            ),
                        )
                    ),
                    orderItems = listOf(
                        DtoOrderItem(
                            path = listOf(
                                DtoPropPathNode(
                                    propId = LsiSymbolId.property(STORE_TYPE_ID, "name"),
                                    associatedId = false,
                                )
                            ),
                            descending = true,
                        )
                    ),
                    filter = DtoConfigTypeRef(FILTER_TYPE_ID, location(source, 10, 14)),
                    recursion = DtoConfigTypeRef(RECURSION_TYPE_ID, location(source, 10, 32)),
                    fetchType = DtoFetchType.JOIN_ALWAYS,
                    limit = 20,
                    offset = 5,
                    batch = 16,
                    depth = 3,
                ),
                recursive = false,
                likeOptions = linkedSetOf(
                    DtoLikeOption.INSENSITIVE,
                    DtoLikeOption.MATCH_START,
                ),
            ),
            DtoBaseProp(
                id = CHILDREN_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "children",
                alias = null,
                nullable = false,
                annotations = emptyList(),
                documentation = "Recursive children",
                aliasLocation = location(source, 12, 5),
                baseLocation = location(source, 12, 5),
                baseProps = listOf(
                    DtoBasePropBinding("children", LsiSymbolId.property(BOOK_TYPE_ID, "children"))
                ),
                basePath = "children",
                nextPropId = null,
                tailPropId = CHILDREN_PROP_ID,
                baseNullable = false,
                inputModifier = DtoModifier.STATIC,
                functionName = null,
                targetTypeId = ROOT_TYPE_ID,
                enumType = null,
                config = DtoPropConfig(
                    predicate = null,
                    orderItems = emptyList(),
                    filter = null,
                    recursion = DtoConfigTypeRef(RECURSION_TYPE_ID, location(source, 12, 18)),
                    fetchType = DtoFetchType.AUTO,
                    limit = 0,
                    offset = 0,
                    batch = 8,
                    depth = 4,
                ),
                recursive = true,
                likeOptions = emptySet(),
            ),
            DtoUserProp(
                id = DISPLAY_NAME_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "displayName",
                alias = "label",
                nullable = true,
                annotations = emptyList(),
                documentation = "Computed label",
                aliasLocation = location(source, 14, 5),
                type = DtoTypeRef(
                    typeName = "java.lang.String",
                    arguments = emptyList(),
                    nullable = true,
                    location = location(source, 14, 24),
                ),
                defaultValueText = "\"unknown\"",
            ),
            DtoFoldProp(
                id = SUMMARY_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "summary",
                alias = "storeSummary",
                nullable = true,
                annotations = emptyList(),
                documentation = "Folded store summary",
                aliasLocation = location(source, 16, 5),
                nullGuardPropId = STORE_PROP_ID,
                targetTypeId = NESTED_TYPE_ID,
            ),
            baseProp(
                id = HIDDEN_STORE_ID_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "storeId",
                source = source,
                line = 18,
                basePropId = LsiSymbolId.property(BOOK_TYPE_ID, "storeId"),
            ),
            baseProp(
                id = STORE_NAME_PROP_ID,
                ownerTypeId = NESTED_TYPE_ID,
                name = "name",
                source = source,
                line = 9,
                basePropId = LsiSymbolId.property(STORE_TYPE_ID, "name"),
            ),
        ).sortedBy(DtoProp::id)
        return DtoGraph(
            source = source,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = (listOf(rootType, nestedType) + branchTypes).sortedBy(DtoType::id),
            props = props,
        )
    }

    private fun realPrecompilerFixture(): RealPrecompilerFixture {
        val idProp = immutableProp(
            ownerTypeId = CLIENT_TYPE_ID,
            name = "id",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.ID,
        )
        val discriminatorProp = immutableProp(
            ownerTypeId = CLIENT_TYPE_ID,
            name = "type",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.DISCRIMINATOR,
        )
        val rootProps = listOf(
            idProp,
            discriminatorProp,
            immutableProp(
                ownerTypeId = CLIENT_TYPE_ID,
                name = "name",
                type = STRING_TYPE,
                documentation = "Immutable client name documentation",
            ),
            immutableProp(
                ownerTypeId = CLIENT_TYPE_ID,
                name = "store",
                type = LsiDeclaredType(STORE_TYPE_ID),
                primaryMapping = PrimaryMapping.ASSOCIATION,
                association = true,
                targetTypeId = STORE_TYPE_ID,
                associationKind = AssociationKind.MANY_TO_ONE,
            ),
            immutableProp(
                ownerTypeId = CLIENT_TYPE_ID,
                name = "publisher",
                type = LsiDeclaredType(STORE_TYPE_ID),
                primaryMapping = PrimaryMapping.ASSOCIATION,
                association = true,
                targetTypeId = STORE_TYPE_ID,
                associationKind = AssociationKind.MANY_TO_ONE,
            ),
        )
        val root = immutableType(
            id = CLIENT_TYPE_ID,
            props = rootProps,
            documentation = "Base client documentation\n@param name Base client name documentation",
            instantiable = false,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
            discriminatorPropId = discriminatorProp.id,
        )
        val inheritedProps = rootProps.map { prop -> prop.inheritedBy(ORGANIZATION_TYPE_ID) }
        val inheritedDiscriminator = inheritedProps.single { prop -> prop.name == "type" }
        val organization = immutableType(
            id = ORGANIZATION_TYPE_ID,
            props = inheritedProps + immutableProp(ORGANIZATION_TYPE_ID, "taxCode", STRING_TYPE),
            superTypeIds = listOf(CLIENT_TYPE_ID),
            primarySuperTypeId = CLIENT_TYPE_ID,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            instantiable = true,
            discriminatorValue = "ORGANIZATION",
            discriminatorPropId = inheritedDiscriminator.id,
        )
        val store = immutableType(
            id = STORE_TYPE_ID,
            documentation = "Store documentation\n@param name Store type name documentation",
            props = listOf(
                immutableProp(
                    ownerTypeId = STORE_TYPE_ID,
                    name = "id",
                    type = LONG_TYPE,
                    primaryMapping = PrimaryMapping.ID,
                ),
                immutableProp(
                    ownerTypeId = STORE_TYPE_ID,
                    name = "name",
                    type = STRING_TYPE,
                    documentation = "Store name documentation",
                ),
            ),
        )
        val schema = ImmutableSchema(listOf(root, organization, store))
        val document = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = "demo/Client.dto",
            content = """
                /**
                 * DTO client documentation
                 * @param name DTO name documentation
                 */
                ClientView {
                    id
                    name
                    publisher {
                        id
                        name
                    }
                    flat(store) {
                        id as storeId
                        name as storeName
                    }
                    #types {
                        Organization {
                            taxCode
                        }
                    }
                }
            """.trimIndent(),
        )
        return RealPrecompilerFixture(
            document = document,
            schema = schema,
            workspace = immutableHeaderWorkspace(schema.types.map(ImmutableType::id)),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        documentation: String? = null,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: InheritanceStrategy? = null,
        joinedTableDissociateAction: JoinedTableDissociateAction? = null,
        instantiable: Boolean = true,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
    ): ImmutableType {
        val completeProps = completeEntityProps(id, props)
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = documentation,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = superTypeIds,
            props = completeProps,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
            idPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.ID
            }?.id,
            versionPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.VERSION
            }?.id,
            logicalDeletedPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED
            }?.id,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        association: Boolean = false,
        targetTypeId: LsiSymbolId? = (type as? LsiDeclaredType)?.declarationId,
        associationKind: AssociationKind = AssociationKind.NONE,
        documentation: String? = null,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            documentation = documentation,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(id),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = association,
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
                AssociationKind.MANY_TO_MANY -> AssociationStorageKind.MIDDLE_TABLE
                else -> AssociationStorageKind.NONE
            },
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun ImmutableProp.inheritedBy(ownerTypeId: LsiSymbolId): ImmutableProp {
        return copy(
            id = LsiSymbolId.property(ownerTypeId, name),
            ownerTypeId = ownerTypeId,
            inherited = true,
        )
    }

    private fun immutableHeaderWorkspace(typeIds: Collection<LsiSymbolId>): LsiWorkspace {
        val source = LsiSource.of("demo/Models.kt", LsiLanguage.UNKNOWN)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        return LsiWorkspace(
            sources = listOf(source),
            declarations = typeIds.sorted().map { typeId ->
                val qualifiedName = typeId.requireTypeQualifiedName()
                LsiTypeDeclaration(
                    id = typeId,
                    name = qualifiedName.substringAfterLast('.'),
                    qualifiedName = qualifiedName,
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION_ID)),
                    origin = origin,
                )
            },
        )
    }

    private fun reorderedSetGraph(): DtoGraph {
        val graph = complexGraph()
        val types = graph.types.map { type ->
            if (type.id == ROOT_TYPE_ID) {
                type.copy(modifiers = linkedSetOf(DtoModifier.FIXED, DtoModifier.INPUT))
            } else {
                type
            }
        }
        val props = graph.props.map { prop ->
            if (prop.id == STORE_PROP_ID) {
                (prop as DtoBaseProp).copy(
                    likeOptions = linkedSetOf(
                        DtoLikeOption.MATCH_START,
                        DtoLikeOption.INSENSITIVE,
                    ),
                )
            } else {
                prop
            }
        }
        return graph.copy(types = types, props = props)
    }

    private fun schema(graph: DtoGraph): JimmerDtoPrecompiledSchema {
        val document = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = "demo/Book.dto",
            content = "frozen DTO graph fixture",
        )
        return JimmerDtoPrecompiledSchema(
            listOf(
                JimmerDtoPrecompiledDocument(
                    inputSnapshot = CompilerInputDocumentSnapshot(document, emptyList()),
                    targetTypeIds = listOf(BOOK_TYPE_ID),
                    graph = graph,
                    annotationContract = DtoAnnotationContract(
                        declarations = emptyList(),
                        typePlans = graph.types.map { type ->
                            DtoTypeAnnotationPlan(type.id, emptyList())
                        },
                        propPlans = graph.props.map { prop ->
                            DtoPropAnnotationPlan(prop.id, emptyList(), emptyList())
                        },
                        diagnostics = emptyList(),
                    ),
                    interfaceContractResolution = DtoInterfaceContractResolution(
                        contracts = graph.types.map { type ->
                            DtoInterfaceContract(type.id, emptyList(), emptyList())
                        },
                        diagnostics = emptyList(),
                    ),
                    configContractResolution = DtoConfigContractResolution(
                        contracts = emptyList(),
                        diagnostics = emptyList(),
                    ),
                )
            )
        )
    }

    private fun DtoGraph.withRootType(
        transform: (DtoType) -> DtoType,
    ): DtoGraph {
        return copy(
            types = types.map { type ->
                if (type.id == ROOT_TYPE_ID) {
                    transform(type)
                } else {
                    type
                }
            },
        )
    }

    private fun DtoGraph.withProp(
        propId: DtoPropId,
        transform: (DtoProp) -> DtoProp,
    ): DtoGraph {
        return copy(
            props = props.map { prop ->
                if (prop.id == propId) {
                    transform(prop)
                } else {
                    prop
                }
            },
        )
    }

    private fun baseProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        name: String,
        source: LsiSource,
        line: Int,
        basePropId: LsiSymbolId,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = location(source, line, 5),
            baseLocation = location(source, line, 5),
            baseProps = listOf(DtoBasePropBinding(name, basePropId)),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = false,
            inputModifier = DtoModifier.FIXED,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun branchType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        source: LsiSource,
        line: Int,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = null,
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = location(source, line, 5),
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun markerAnnotation(source: LsiSource): DtoAnnotation {
        return DtoAnnotation(
            typeId = MARKER_ANNOTATION_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "modes",
                    value = DtoAnnotationValue.ArrayValue(
                        listOf(
                            DtoAnnotationValue.EnumValue(MODE_ENUM_ID, "READ"),
                            DtoAnnotationValue.EnumValue(MODE_ENUM_ID, "WRITE"),
                        )
                    ),
                ),
                DtoAnnotationArgument(
                    name = "payload",
                    value = DtoAnnotationValue.TypeValue(
                        DtoTypeRef(
                            typeName = "demo.Payload",
                            arguments = listOf(DtoTypeArgument(DtoVariance.STAR, null)),
                            nullable = false,
                            location = location(source, 1, 20),
                        )
                    ),
                ),
                DtoAnnotationArgument(
                    name = "nested",
                    value = DtoAnnotationValue.AnnotationValue(
                        DtoAnnotation(
                            typeId = NESTED_ANNOTATION_ID,
                            arguments = listOf(
                                DtoAnnotationArgument(
                                    name = "value",
                                    value = DtoAnnotationValue.LiteralValue("\"stable\""),
                                )
                            ),
                        )
                    ),
                ),
            ),
        )
    }

    private fun location(source: LsiSource, line: Int, column: Int): LsiLocation {
        return LsiLocation(source, LsiPosition(line, column))
    }

    private fun Any.reachableTypeNames(): Set<String> {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val typeNames = sortedSetOf<String>()
        val pending = ArrayDeque<Any>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val value = pending.removeFirst()
            if (!visited.add(value)) {
                continue
            }
            val valueType = value.javaClass
            typeNames += valueType.name
            when {
                value is Iterable<*> -> value.filterNotNullTo(pending)
                value is Map<*, *> -> value.entries.forEach { entry ->
                    entry.key?.let(pending::add)
                    entry.value?.let(pending::add)
                }
                valueType.isArray -> (value as Array<*>).filterNotNullTo(pending)
                valueType.isPrimitive || valueType.isEnum || value is String || value is Number || value is Boolean -> Unit
                valueType.name.startsWith("org.babyfish.jimmer.compiler.dto.") ||
                    valueType.name.startsWith("site.addzero.lsi.") -> valueType.declaredFields
                    .filterNot { field -> Modifier.isStatic(field.modifiers) }
                    .forEach { field ->
                        field.trySetAccessible()
                        field.get(value)?.let(pending::add)
                    }
            }
        }
        return typeNames
    }

    private fun reachableFieldTypeSignatures(vararg roots: Class<*>): Set<String> {
        val visited = mutableSetOf<Class<*>>()
        val signatures = sortedSetOf<String>()
        val pending = ArrayDeque<Class<*>>()
        roots.forEach(pending::add)
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (!visited.add(type)) {
                continue
            }
            type.declaredFields
                .filterNot { field -> Modifier.isStatic(field.modifiers) }
                .forEach { field ->
                    signatures += field.genericType.typeName
                    field.genericType.referencedClasses()
                        .filter { referencedType -> referencedType.name.startsWith("org.babyfish.jimmer.compiler.dto.") }
                        .forEach(pending::add)
                }
        }
        return signatures
    }

    private fun Type.referencedClasses(): Sequence<Class<*>> = when (this) {
        is Class<*> -> sequenceOf(this)
        is ParameterizedType -> sequence {
            yieldAll(rawType.referencedClasses())
            actualTypeArguments.forEach { argument -> yieldAll(argument.referencedClasses()) }
        }
        is WildcardType -> sequence {
            lowerBounds.forEach { bound -> yieldAll(bound.referencedClasses()) }
            upperBounds.forEach { bound -> yieldAll(bound.referencedClasses()) }
        }
        is GenericArrayType -> genericComponentType.referencedClasses()
        else -> emptySequence()
    }

    private fun isForbiddenCompilerStateType(typeName: String): Boolean {
        return typeName in FORBIDDEN_RENDER_STATE_TYPE_NAMES
    }

    private companion object {
        val ROOT_TYPE_ID = DtoTypeId("demo.dto.BookView")
        val NESTED_TYPE_ID = DtoTypeId("demo.dto.BookView#store")
        val DEFAULT_BRANCH_TYPE_ID = DtoTypeId("demo.dto.BookView#types:default:body")
        val DEFAULT_MERGED_TYPE_ID = DtoTypeId("demo.dto.BookView#types:default:merged")
        val SPECIAL_BRANCH_TYPE_ID = DtoTypeId("demo.dto.BookView#types:special:body")
        val SPECIAL_MERGED_TYPE_ID = DtoTypeId("demo.dto.BookView#types:special:merged")

        val ID_PROP_ID = DtoPropId("demo.dto.BookView#prop:00:id")
        val STATUS_PROP_ID = DtoPropId("demo.dto.BookView#prop:01:status")
        val STORE_PROP_ID = DtoPropId("demo.dto.BookView#prop:02:store")
        val CHILDREN_PROP_ID = DtoPropId("demo.dto.BookView#prop:03:children")
        val DISPLAY_NAME_PROP_ID = DtoPropId("demo.dto.BookView#prop:04:displayName")
        val SUMMARY_PROP_ID = DtoPropId("demo.dto.BookView#prop:05:summary")
        val HIDDEN_STORE_ID_PROP_ID = DtoPropId("demo.dto.BookView#prop:06:storeId:hidden")
        val STORE_NAME_PROP_ID = DtoPropId("demo.dto.BookView#store#prop:00:name")

        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val CLIENT_TYPE_ID = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_TYPE_ID = LsiSymbolId.type("demo.Organization")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val SPECIAL_BOOK_TYPE_ID = LsiSymbolId.type("demo.SpecialBook")
        val FILTER_TYPE_ID = LsiSymbolId.type("demo.BookFilter")
        val RECURSION_TYPE_ID = LsiSymbolId.type("demo.BookRecursion")
        val MARKER_ANNOTATION_ID = LsiSymbolId.type("demo.DtoMarker")
        val NESTED_ANNOTATION_ID = LsiSymbolId.type("demo.Nested")
        val MODE_ENUM_ID = LsiSymbolId.type("demo.Mode")
        val ENTITY_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val LONG_TYPE: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE: LsiTypeRef = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

        val FORBIDDEN_RENDER_STATE_TYPE_NAMES = setOf(
            "org.babyfish.jimmer.dto.compiler.DtoType",
            "org.babyfish.jimmer.compiler.dto.LsiDtoBaseType",
            "org.babyfish.jimmer.compiler.dto.LsiDtoBaseProp",
            "site.addzero.lsi.model.LsiWorkspace",
        )
    }
}

private data class RealPrecompilerFixture(
    val document: CompilerInputDocument,
    val schema: ImmutableSchema,
    val workspace: LsiWorkspace,
)
