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
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerAssociationKind
import org.babyfish.jimmer.compiler.immutable.JimmerAssociationStorageKind
import org.babyfish.jimmer.compiler.immutable.JimmerFormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.JimmerInheritanceStrategy
import org.babyfish.jimmer.compiler.immutable.JimmerJoinedTableDissociateAction
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import org.babyfish.jimmer.dto.compiler.DtoModifier
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

class JimmerDtoRenderGraphTest {
    @Test
    fun `complex render graph is self contained frozen data`() {
        val graph = complexGraph()
        val forbiddenTypes = graph.reachableTypeNames().filter(::isForbiddenRenderStateType)

        assertTrue(forbiddenTypes.isEmpty(), "DTO render graph retains forbidden state: $forbiddenTypes")
        assertEquals(graph.types, graph.typesById.values.toList())
        assertEquals(graph.props, graph.propsById.values.toList())
        assertEquals(setOf(ROOT_TYPE_ID), graph.rootTypeIds.toSet())
    }

    @Test
    fun `complex render graph captures nested enum config polymorphism and recursion`() {
        val graph = complexGraph()
        val rootType = graph.typesById.getValue(ROOT_TYPE_ID)
        val statusProp = graph.propsById.getValue(STATUS_PROP_ID) as JimmerDtoBaseProp
        val storeProp = graph.propsById.getValue(STORE_PROP_ID) as JimmerDtoBaseProp
        val childrenProp = graph.propsById.getValue(CHILDREN_PROP_ID) as JimmerDtoBaseProp
        val polymorphism = requireNotNull(rootType.polymorphism)

        assertEquals(NESTED_TYPE_ID, storeProp.targetTypeId)
        assertEquals(listOf("DRAFT", "PUBLISHED"), statusProp.enumType?.mappings?.map { mapping -> mapping.constant })
        assertEquals(FILTER_TYPE_ID, storeProp.config?.filter?.typeId)
        assertEquals(RECURSION_TYPE_ID, storeProp.config?.recursion?.typeId)
        assertEquals(ROOT_TYPE_ID, childrenProp.targetTypeId)
        assertTrue(childrenProp.recursive)
        assertFalse(polymorphism.exhaustive)
        assertEquals(
            listOf(JimmerDtoPolymorphicBranchKind.DEFAULT, JimmerDtoPolymorphicBranchKind.TYPE),
            polymorphism.branches.map(JimmerDtoPolymorphicBranch::kind),
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
            defaultNullableInputModifier = DtoModifier.STATIC,
            rendererOptions = JimmerDtoRendererOptions(
                jacksonVersion = JimmerDtoJacksonVersion.JACKSON_2,
                hibernateValidatorEnhancement = false,
                aptFieldVisibility = JimmerDtoFieldVisibility.PRIVATE,
                kspMutable = false,
            ),
            effectiveKspMutableByRootTypeId = schema.documents
                .flatMap { document -> document.renderGraph.rootTypeIds }
                .sorted()
                .associateWith { false },
            immutableDependencyFingerprint = "immutable-fingerprint",
        )
        val forbiddenTypes = state.reachableTypeNames().filter(::isForbiddenRenderStateType)

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
    fun `complex graph fingerprint changes for every renderer semantic mutation`() {
        val graph = complexGraph()
        val baseline = schema(graph).fingerprint()
        val semanticMutations = listOf(
            graph.withRootType { type -> type.copy(documentation = "Changed render contract") },
            graph.withProp(STATUS_PROP_ID) { prop ->
                val baseProp = prop as JimmerDtoBaseProp
                baseProp.copy(
                    enumType = requireNotNull(baseProp.enumType).copy(
                        mappings = listOf(
                            JimmerDtoEnumMapping("DRAFT", "draft"),
                            JimmerDtoEnumMapping("PUBLISHED", "released"),
                        ),
                    ),
                )
            },
            graph.withProp(STORE_PROP_ID) { prop ->
                val baseProp = prop as JimmerDtoBaseProp
                baseProp.copy(config = requireNotNull(baseProp.config).copy(depth = 7))
            },
            graph.withRootType { type ->
                val polymorphism = requireNotNull(type.polymorphism)
                type.copy(
                    polymorphism = polymorphism.copy(
                        branches = polymorphism.branches.map { branch ->
                            if (branch.kind == JimmerDtoPolymorphicBranchKind.TYPE) {
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
                                        value = JimmerDtoAnnotationValue.LiteralValue("\"changed\""),
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
    fun `real dto precompiler freezes nested flat and polymorphic renderer graph`() {
        val fixture = realPrecompilerFixture()
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(CompilerInputDocumentReferenceFreezer().freeze(fixture.document)),
            immutableSchema = fixture.schema,
            immutableSemanticRootTypeIds = fixture.schema.types.mapTo(sortedSetOf(), JimmerImmutableType::id),
            workspace = fixture.workspace,
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )

        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString { failure -> failure.message })
        assertTrue(outcome.unresolvedDocuments.isEmpty())
        val document = outcome.schema.documents.single()
        val graph = document.renderGraph
        val rootType = graph.typesById.getValue(graph.rootTypeIds.single())
        val rootProps = rootType.propIds.map(graph.propsById::getValue)
        val nestedProp = rootProps.single { prop -> prop.name == "publisher" } as JimmerDtoBaseProp
        val nestedType = graph.typesById.getValue(assertNotNull(nestedProp.targetTypeId))
        val nestedPropNames = nestedType.propIds.map { propId -> graph.propsById.getValue(propId).name }
        val polymorphism = assertNotNull(rootType.polymorphism)

        assertEquals(listOf("id", "name"), nestedPropNames)
        assertEquals(
            "DTO client documentation\n@param name DTO name documentation\n",
            rootType.documentation,
        )
        assertEquals(
            "DTO name documentation",
            rootProps.single { prop -> prop.name == "name" }.documentation,
        )
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
        assertEquals(
            "Store name documentation\n",
            rootProps.single { prop -> prop.name == "storeName" }.documentation,
        )
        assertTrue(rootType.hiddenFlatPropIds.isNotEmpty())
        assertTrue(rootProps.any { prop -> prop.name == "storeId" })
        assertTrue(rootProps.any { prop -> prop.name == "storeName" })
        assertTrue(polymorphism.branches.any { branch -> branch.kind == JimmerDtoPolymorphicBranchKind.TYPE })
        polymorphism.branches.forEach { branch ->
            assertTrue(branch.bodyTypeId in graph.typesById)
            assertTrue(branch.mergedTypeId in graph.typesById)
        }
        assertTrue(graph.reachableTypeNames().none(::isForbiddenRenderStateType))
        assertTrue("taxCode" in outcome.schema.normalizedSnapshot())
    }

    private fun complexGraph(): JimmerDtoRenderGraph {
        val source = LsiSource.of(
            path = "demo-project/src/main/dto/demo/Book.dto",
            language = LsiLanguage.UNKNOWN,
        )
        val rootType = JimmerDtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = linkedSetOf(JimmerDtoModifier.INPUT, JimmerDtoModifier.FIXED),
            annotations = listOf(markerAnnotation(source)),
            superInterfaces = listOf(
                JimmerDtoTypeRef(
                    typeName = "demo.View",
                    arguments = listOf(
                        JimmerDtoTypeArgument(
                            variance = JimmerDtoVariance.OUT,
                            type = JimmerDtoTypeRef(
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
            documentation = "Book render contract",
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
            polymorphism = JimmerDtoPolymorphism(
                exhaustive = false,
                branches = listOf(
                    JimmerDtoPolymorphicBranch(
                        kind = JimmerDtoPolymorphicBranchKind.DEFAULT,
                        targetBaseTypeId = null,
                        declaredClassName = null,
                        className = "demo.dto.BookView.Default",
                        bodyTypeId = DEFAULT_BRANCH_TYPE_ID,
                        mergedTypeId = DEFAULT_MERGED_TYPE_ID,
                        implicit = true,
                        location = location(source, 21, 5),
                    ),
                    JimmerDtoPolymorphicBranch(
                        kind = JimmerDtoPolymorphicBranchKind.TYPE,
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
        val nestedType = JimmerDtoType(
            id = NESTED_TYPE_ID,
            baseTypeId = STORE_TYPE_ID,
            packageName = "demo.dto",
            name = null,
            modifiers = setOf(JimmerDtoModifier.DYNAMIC),
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
            JimmerDtoBaseProp(
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
                    JimmerDtoBasePropBinding("status", LsiSymbolId.property(BOOK_TYPE_ID, "status"))
                ),
                basePath = "status",
                nextPropId = null,
                tailPropId = STATUS_PROP_ID,
                baseNullable = false,
                inputModifier = JimmerDtoModifier.FIXED,
                functionName = null,
                targetTypeId = null,
                enumType = JimmerDtoEnumType(
                    numeric = false,
                    mappings = listOf(
                        JimmerDtoEnumMapping("DRAFT", "draft"),
                        JimmerDtoEnumMapping("PUBLISHED", "published"),
                    ),
                ),
                config = null,
                recursive = false,
                likeOptions = emptySet(),
            ),
            JimmerDtoBaseProp(
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
                    JimmerDtoBasePropBinding("store", LsiSymbolId.property(BOOK_TYPE_ID, "store"))
                ),
                basePath = "store",
                nextPropId = null,
                tailPropId = STORE_PROP_ID,
                baseNullable = true,
                inputModifier = JimmerDtoModifier.DYNAMIC,
                functionName = "flat",
                targetTypeId = NESTED_TYPE_ID,
                enumType = null,
                config = JimmerDtoPropConfig(
                    predicate = JimmerDtoPredicate.And(
                        listOf(
                            JimmerDtoPredicate.Comparison(
                                path = listOf(
                                    JimmerDtoPropPathNode(
                                        propId = LsiSymbolId.property(STORE_TYPE_ID, "name"),
                                        associatedId = false,
                                    )
                                ),
                                operator = "like",
                                value = JimmerDtoConfigValue.StringValue("MANNING"),
                            ),
                            JimmerDtoPredicate.Nullity(
                                path = listOf(
                                    JimmerDtoPropPathNode(
                                        propId = LsiSymbolId.property(STORE_TYPE_ID, "website"),
                                        associatedId = false,
                                    )
                                ),
                                negative = true,
                            ),
                        )
                    ),
                    orderItems = listOf(
                        JimmerDtoOrderItem(
                            path = listOf(
                                JimmerDtoPropPathNode(
                                    propId = LsiSymbolId.property(STORE_TYPE_ID, "name"),
                                    associatedId = false,
                                )
                            ),
                            descending = true,
                        )
                    ),
                    filter = JimmerDtoConfigTypeRef(FILTER_TYPE_ID, location(source, 10, 14)),
                    recursion = JimmerDtoConfigTypeRef(RECURSION_TYPE_ID, location(source, 10, 32)),
                    fetchType = JimmerDtoFetchType.JOIN_ALWAYS,
                    limit = 20,
                    offset = 5,
                    batch = 16,
                    depth = 3,
                ),
                recursive = false,
                likeOptions = linkedSetOf(
                    JimmerDtoLikeOption.INSENSITIVE,
                    JimmerDtoLikeOption.MATCH_START,
                ),
            ),
            JimmerDtoBaseProp(
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
                    JimmerDtoBasePropBinding("children", LsiSymbolId.property(BOOK_TYPE_ID, "children"))
                ),
                basePath = "children",
                nextPropId = null,
                tailPropId = CHILDREN_PROP_ID,
                baseNullable = false,
                inputModifier = JimmerDtoModifier.STATIC,
                functionName = null,
                targetTypeId = ROOT_TYPE_ID,
                enumType = null,
                config = JimmerDtoPropConfig(
                    predicate = null,
                    orderItems = emptyList(),
                    filter = null,
                    recursion = JimmerDtoConfigTypeRef(RECURSION_TYPE_ID, location(source, 12, 18)),
                    fetchType = JimmerDtoFetchType.AUTO,
                    limit = 0,
                    offset = 0,
                    batch = 8,
                    depth = 4,
                ),
                recursive = true,
                likeOptions = emptySet(),
            ),
            JimmerDtoUserProp(
                id = DISPLAY_NAME_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "displayName",
                alias = "label",
                nullable = true,
                annotations = emptyList(),
                documentation = "Computed label",
                aliasLocation = location(source, 14, 5),
                type = JimmerDtoTypeRef(
                    typeName = "java.lang.String",
                    arguments = emptyList(),
                    nullable = true,
                    location = location(source, 14, 24),
                ),
                defaultValueText = "\"unknown\"",
            ),
            JimmerDtoFoldProp(
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
        ).sortedBy(JimmerDtoProp::id)
        return JimmerDtoRenderGraph(
            source = source,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = (listOf(rootType, nestedType) + branchTypes).sortedBy(JimmerDtoType::id),
            props = props,
        )
    }

    private fun realPrecompilerFixture(): RealPrecompilerFixture {
        val idProp = immutableProp(
            ownerTypeId = CLIENT_TYPE_ID,
            name = "id",
            type = LONG_TYPE,
            primaryMapping = JimmerImmutablePrimaryMapping.ID,
        )
        val discriminatorProp = immutableProp(
            ownerTypeId = CLIENT_TYPE_ID,
            name = "type",
            type = STRING_TYPE,
            primaryMapping = JimmerImmutablePrimaryMapping.DISCRIMINATOR,
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
                primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
                association = true,
                targetTypeId = STORE_TYPE_ID,
                associationKind = JimmerAssociationKind.MANY_TO_ONE,
            ),
            immutableProp(
                ownerTypeId = CLIENT_TYPE_ID,
                name = "publisher",
                type = LsiDeclaredType(STORE_TYPE_ID),
                primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
                association = true,
                targetTypeId = STORE_TYPE_ID,
                associationKind = JimmerAssociationKind.MANY_TO_ONE,
            ),
        )
        val root = immutableType(
            id = CLIENT_TYPE_ID,
            props = rootProps,
            documentation = "Base client documentation\n@param name Base client name documentation",
            instantiable = false,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            inheritanceStrategy = JimmerInheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JimmerJoinedTableDissociateAction.DELETE,
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
                    primaryMapping = JimmerImmutablePrimaryMapping.ID,
                ),
                immutableProp(
                    ownerTypeId = STORE_TYPE_ID,
                    name = "name",
                    type = STRING_TYPE,
                    documentation = "Store name documentation",
                ),
            ),
        )
        val schema = JimmerImmutableSchema(listOf(root, organization, store))
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
            workspace = immutableHeaderWorkspace(schema.types.map(JimmerImmutableType::id)),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<JimmerImmutableProp>,
        documentation: String? = null,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: JimmerInheritanceStrategy? = null,
        joinedTableDissociateAction: JimmerJoinedTableDissociateAction? = null,
        instantiable: Boolean = true,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
    ): JimmerImmutableType {
        val completeProps = completeEntityProps(id, props)
        return JimmerImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = JimmerImmutableTypeKind.ENTITY,
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
                prop.primaryMapping == JimmerImmutablePrimaryMapping.ID
            }?.id,
            versionPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == JimmerImmutablePrimaryMapping.VERSION
            }?.id,
            logicalDeletedPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == JimmerImmutablePrimaryMapping.LOGICAL_DELETED
            }?.id,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        primaryMapping: JimmerImmutablePrimaryMapping = JimmerImmutablePrimaryMapping.SCALAR,
        association: Boolean = false,
        targetTypeId: LsiSymbolId? = (type as? LsiDeclaredType)?.declarationId,
        associationKind: JimmerAssociationKind = JimmerAssociationKind.NONE,
        documentation: String? = null,
    ): JimmerImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return JimmerImmutableProp(
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
            formulaKind = JimmerFormulaKind.NONE,
            mappedBy = null,
            associationStorage = when (associationKind) {
                JimmerAssociationKind.ONE_TO_ONE,
                JimmerAssociationKind.MANY_TO_ONE,
                -> JimmerAssociationStorageKind.COLUMN
                JimmerAssociationKind.MANY_TO_MANY -> JimmerAssociationStorageKind.MIDDLE_TABLE
                else -> JimmerAssociationStorageKind.NONE
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

    private fun JimmerImmutableProp.inheritedBy(ownerTypeId: LsiSymbolId): JimmerImmutableProp {
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

    private fun reorderedSetGraph(): JimmerDtoRenderGraph {
        val graph = complexGraph()
        val types = graph.types.map { type ->
            if (type.id == ROOT_TYPE_ID) {
                type.copy(modifiers = linkedSetOf(JimmerDtoModifier.FIXED, JimmerDtoModifier.INPUT))
            } else {
                type
            }
        }
        val props = graph.props.map { prop ->
            if (prop.id == STORE_PROP_ID) {
                (prop as JimmerDtoBaseProp).copy(
                    likeOptions = linkedSetOf(
                        JimmerDtoLikeOption.MATCH_START,
                        JimmerDtoLikeOption.INSENSITIVE,
                    ),
                )
            } else {
                prop
            }
        }
        return graph.copy(types = types, props = props)
    }

    private fun schema(graph: JimmerDtoRenderGraph): JimmerDtoPrecompiledSchema {
        val document = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = "demo/Book.dto",
            content = "frozen render graph fixture",
        )
        return JimmerDtoPrecompiledSchema(
            listOf(
                JimmerDtoPrecompiledDocument(
                    inputSnapshot = CompilerInputDocumentSnapshot(document, emptyList()),
                    targetTypeIds = listOf(BOOK_TYPE_ID),
                    renderGraph = graph,
                    annotationContract = JimmerDtoAnnotationContract(
                        declarations = emptyList(),
                        typePlans = graph.types.map { type ->
                            JimmerDtoTypeAnnotationPlan(type.id, emptyList())
                        },
                        propPlans = graph.props.map { prop ->
                            JimmerDtoPropAnnotationPlan(prop.id, emptyList(), emptyList())
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

    private fun JimmerDtoRenderGraph.withRootType(
        transform: (JimmerDtoType) -> JimmerDtoType,
    ): JimmerDtoRenderGraph {
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

    private fun JimmerDtoRenderGraph.withProp(
        propId: JimmerDtoPropId,
        transform: (JimmerDtoProp) -> JimmerDtoProp,
    ): JimmerDtoRenderGraph {
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
        id: JimmerDtoPropId,
        ownerTypeId: JimmerDtoTypeId,
        name: String,
        source: LsiSource,
        line: Int,
        basePropId: LsiSymbolId,
    ): JimmerDtoBaseProp {
        return JimmerDtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = location(source, line, 5),
            baseLocation = location(source, line, 5),
            baseProps = listOf(JimmerDtoBasePropBinding(name, basePropId)),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = false,
            inputModifier = JimmerDtoModifier.FIXED,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun branchType(
        id: JimmerDtoTypeId,
        baseTypeId: LsiSymbolId,
        source: LsiSource,
        line: Int,
    ): JimmerDtoType {
        return JimmerDtoType(
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

    private fun markerAnnotation(source: LsiSource): JimmerDtoAnnotation {
        return JimmerDtoAnnotation(
            typeId = MARKER_ANNOTATION_ID,
            arguments = listOf(
                JimmerDtoAnnotationArgument(
                    name = "modes",
                    value = JimmerDtoAnnotationValue.ArrayValue(
                        listOf(
                            JimmerDtoAnnotationValue.EnumValue(MODE_ENUM_ID, "READ"),
                            JimmerDtoAnnotationValue.EnumValue(MODE_ENUM_ID, "WRITE"),
                        )
                    ),
                ),
                JimmerDtoAnnotationArgument(
                    name = "payload",
                    value = JimmerDtoAnnotationValue.TypeValue(
                        JimmerDtoTypeRef(
                            typeName = "demo.Payload",
                            arguments = listOf(JimmerDtoTypeArgument(JimmerDtoVariance.STAR, null)),
                            nullable = false,
                            location = location(source, 1, 20),
                        )
                    ),
                ),
                JimmerDtoAnnotationArgument(
                    name = "nested",
                    value = JimmerDtoAnnotationValue.AnnotationValue(
                        JimmerDtoAnnotation(
                            typeId = NESTED_ANNOTATION_ID,
                            arguments = listOf(
                                JimmerDtoAnnotationArgument(
                                    name = "value",
                                    value = JimmerDtoAnnotationValue.LiteralValue("\"stable\""),
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

    private fun isForbiddenRenderStateType(typeName: String): Boolean {
        return typeName in FORBIDDEN_RENDER_STATE_TYPE_NAMES
    }

    private companion object {
        val ROOT_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView")
        val NESTED_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView#store")
        val DEFAULT_BRANCH_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView#types:default:body")
        val DEFAULT_MERGED_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView#types:default:merged")
        val SPECIAL_BRANCH_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView#types:special:body")
        val SPECIAL_MERGED_TYPE_ID = JimmerDtoTypeId("demo.dto.BookView#types:special:merged")

        val ID_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:00:id")
        val STATUS_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:01:status")
        val STORE_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:02:store")
        val CHILDREN_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:03:children")
        val DISPLAY_NAME_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:04:displayName")
        val SUMMARY_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:05:summary")
        val HIDDEN_STORE_ID_PROP_ID = JimmerDtoPropId("demo.dto.BookView#prop:06:storeId:hidden")
        val STORE_NAME_PROP_ID = JimmerDtoPropId("demo.dto.BookView#store#prop:00:name")

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
    val schema: JimmerImmutableSchema,
    val workspace: LsiWorkspace,
)
