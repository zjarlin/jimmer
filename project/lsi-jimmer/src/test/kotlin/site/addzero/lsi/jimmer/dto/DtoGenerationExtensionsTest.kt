package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import site.addzero.lsi.model.LsiDeclaredType

class DtoGenerationExtensionsTest {

    @Test
    fun `traverses generated targets in declaration order`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val baseProps = root.basePropsInDeclarationOrder(graph)
        val foldProps = root.foldPropsInDeclarationOrder(graph)

        assertEquals(listOf(ROOT_TYPE_ID), graph.rootTypesInDeclarationOrder().map(DtoType::id))
        assertEquals(ROOT_TYPE_ID, graph.rootType("demo.dto.RootInput").id)
        assertEquals("demo.dto.RootInput", root.qualifiedNameOrNull())
        assertEquals(listOf("userValue"), root.userPropsInDeclarationOrder(graph).map(DtoUserProp::name))
        assertEquals(
            listOf("hiddenTail"),
            root.hiddenFlatPropsInDeclarationOrder(graph).map(DtoBaseProp::name),
        )
        assertEquals(
            listOf("nested", "recursive", "focused", "sourceReference", "binaryReference", "scalar"),
            baseProps.map(DtoBaseProp::name),
        )
        assertEquals(NESTED_PROP_ID, root.prop(graph, "nested").id)
        assertEquals(NESTED_PROP_ID, root.baseProp(graph, "nested").id)
        assertEquals(FOLD_PROP_ID, root.foldProp(graph, "folded").id)
        assertEquals(HIDDEN_PROP_ID, baseProps[0].nextProp(graph)?.id)
        assertEquals(HIDDEN_PROP_ID, baseProps[0].tailProp(graph).id)
        assertEquals(NESTED_TYPE_ID, baseProps[0].generatedTargetType(graph)?.id)
        assertNull(baseProps[1].generatedTargetType(graph))
        assertEquals(FOCUSED_TYPE_ID, baseProps[2].generatedTargetType(graph)?.id)
        assertNull(baseProps[3].generatedTargetType(graph))
        assertEquals(REUSABLE_SOURCE_TYPE_ID, baseProps[3].targetTypeId)
        assertEquals("demo.dto.ReusableView", baseProps[3].targetTypeReference?.qualifiedName)
        assertEquals(DtoReusableTypeKind.VIEW, baseProps[3].targetTypeReference?.kind)
        assertNull(baseProps[4].generatedTargetType(graph))
        assertNull(baseProps[4].targetTypeId)
        assertEquals("contract.ExternalView", baseProps[4].targetTypeReference?.qualifiedName)
        assertNull(baseProps[5].generatedTargetType(graph))
        assertTrue(REFERENCE_SOURCE in graph.dependencySources())
        assertEquals(listOf("folded"), foldProps.map(DtoFoldProp::name))
        assertEquals(FOLD_TYPE_ID, foldProps.single().generatedTargetType(graph).id)
        assertEquals(NESTED_PROP_ID, foldProps.single().nullGuardProp(graph)?.id)
    }

    @Test
    fun `resolves polymorphic branch body and merged types`() {
        val graph = graph()
        val branches = graph.typesById.getValue(ROOT_TYPE_ID).polymorphism!!.branches

        assertEquals(
            listOf(BRANCH_BODY_TYPE_ID, SECOND_BRANCH_BODY_TYPE_ID),
            branches.map { branch -> branch.bodyType(graph).id },
        )
        assertEquals(
            listOf(BRANCH_MERGED_TYPE_ID, SECOND_BRANCH_MERGED_TYPE_ID),
            branches.map { branch -> branch.mergedType(graph).id },
        )
        assertEquals("Default", rootPolymorphism(graph).defaultBranch()?.className)
        assertEquals(
            listOf("Special"),
            rootPolymorphism(graph).typeBranchesInDeclarationOrder().map(DtoPolymorphicBranch::className),
        )
    }

    @Test
    fun `collects Kotlin by import packages from frozen DTO and immutable graphs`() {
        val sourceGraph = graph()
        val baseTypeIdsByDtoTypeId = mapOf(
            NESTED_TYPE_ID to NESTED_BASE_TYPE_ID,
            FOCUSED_TYPE_ID to FOCUSED_BASE_TYPE_ID,
            FOLD_TYPE_ID to FOLD_BASE_TYPE_ID,
            BRANCH_BODY_TYPE_ID to DEFAULT_BRANCH_BASE_TYPE_ID,
            SECOND_BRANCH_BODY_TYPE_ID to SPECIAL_BRANCH_BASE_TYPE_ID,
        )
        val graph = DtoGraph(
            source = sourceGraph.source,
            rootTypeIds = sourceGraph.rootTypeIds,
            types = sourceGraph.types.map { type ->
                type.copy(baseTypeId = baseTypeIdsByDtoTypeId[type.id] ?: type.baseTypeId)
            },
            props = sourceGraph.props,
        )

        assertEquals(
            listOf(
                "binary.model",
                "branch.default",
                "branch.special",
                "demo",
                "focused.model",
                "fold.model",
                "nested.model",
                "recursive.model",
                "source.model",
            ),
            graph.typesById
                .getValue(ROOT_TYPE_ID)
                .kotlinByImportPackages(graph, importSchema())
                .toList(),
        )
    }

    @Test
    fun `resolves generated polymorphic branches and validates their merged types`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val defaultBranch = root.generatedPolymorphicBranch(
            "Default",
            DtoPolymorphicBranchKind.DEFAULT,
        )
        val typeBranch = root.generatedPolymorphicBranch(
            "Special",
            DtoPolymorphicBranchKind.TYPE,
        )

        assertEquals(rootPolymorphism(graph).defaultBranch(), defaultBranch)
        assertEquals(rootPolymorphism(graph).typeBranchesInDeclarationOrder().single(), typeBranch)
        assertEquals(
            defaultBranch,
            defaultBranch.requireGeneratedMergedType(
                graph,
                graph.typesById.getValue(BRANCH_MERGED_TYPE_ID),
            ),
        )
        assertEquals(
            typeBranch,
            typeBranch.requireGeneratedMergedType(
                graph,
                graph.typesById.getValue(SECOND_BRANCH_MERGED_TYPE_ID),
            ),
        )
    }

    @Test
    fun `rejects missing or kind mismatched generated polymorphic branches`() {
        val root = graph().typesById.getValue(ROOT_TYPE_ID)

        assertFailsWith<IllegalArgumentException> {
            root.copy(polymorphism = null).generatedPolymorphicBranch(
                "Default",
                DtoPolymorphicBranchKind.DEFAULT,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            root.generatedPolymorphicBranch("Missing", DtoPolymorphicBranchKind.DEFAULT)
        }
        assertFailsWith<IllegalArgumentException> {
            root.generatedPolymorphicBranch("Default", DtoPolymorphicBranchKind.TYPE)
        }
    }

    @Test
    fun `rejects duplicate generated polymorphic branches`() {
        val graph = graph()
        val typeBranch = rootPolymorphism(graph).typeBranchesInDeclarationOrder().single()

        val ex = assertFailsWith<IllegalArgumentException> {
            DtoPolymorphism(
                exhaustive = true,
                branches = rootPolymorphism(graph).branches + typeBranch.copy(
                    bodyTypeId = DtoTypeId("dto#duplicate-branch-body"),
                    mergedTypeId = DtoTypeId("dto#duplicate-branch-merged"),
                ),
            )
        }
        assertTrue(ex.message.orEmpty().contains("duplicate generated branch class names"))
    }

    @Test
    fun `rejects generated polymorphic branch with mismatched merged type`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val defaultBranch = root.generatedPolymorphicBranch(
            "Default",
            DtoPolymorphicBranchKind.DEFAULT,
        )

        assertFailsWith<IllegalArgumentException> {
            defaultBranch.requireGeneratedMergedType(
                graph,
                graph.typesById.getValue(SECOND_BRANCH_MERGED_TYPE_ID),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            defaultBranch.requireGeneratedMergedType(
                graph,
                graph.typesById.getValue(BRANCH_MERGED_TYPE_ID).copy(name = "ForeignMergedType"),
            )
        }
    }

    @Test
    fun `promotes only root properties with shared generated targets`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val merged = rootPolymorphism(graph).defaultBranch()!!.mergedType(graph)

        assertEquals(
            root.prop(graph, "nested"),
            root.promotedPolymorphicRootPropOrNull(graph, merged.prop(graph, "nested")),
        )
        assertEquals(
            root.prop(graph, "focused"),
            root.promotedPolymorphicRootPropOrNull(graph, merged.prop(graph, "focused")),
        )
        assertEquals(
            root.prop(graph, "folded"),
            root.promotedPolymorphicRootPropOrNull(graph, merged.prop(graph, "folded")),
        )
        val secondMerged = rootPolymorphism(graph).typeBranchesInDeclarationOrder().single().mergedType(graph)
        assertEquals(
            root.prop(graph, "nested"),
            root.promotedPolymorphicRootPropOrNull(graph, secondMerged.prop(graph, "nested")),
        )
        assertEquals(
            root.prop(graph, "folded"),
            root.promotedPolymorphicRootPropOrNull(graph, secondMerged.prop(graph, "folded")),
        )
        listOf(
            "recursive",
            "sourceReference",
            "binaryReference",
            "scalar",
            "userValue",
            "branchOnly",
        ).forEach { name ->
            assertNull(
                root.promotedPolymorphicRootPropOrNull(graph, merged.prop(graph, name)),
                name,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            root.promotedPolymorphicRootPropOrNull(graph, root.prop(graph, "nested"))
        }
    }

    @Test
    fun `rejects properties copied from another graph`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val foreignProp = root.basePropsInDeclarationOrder(graph).first().copy(name = "foreign")

        assertFailsWith<IllegalArgumentException> {
            foreignProp.generatedTargetType(graph)
        }
        assertFailsWith<IllegalArgumentException> {
            root.userPropsInDeclarationOrder(graph)
                .single()
                .copy(name = "foreign")
                .generatedTargetTypeOrNull(graph)
        }
        assertFailsWith<IllegalArgumentException> {
            root.prop(graph, "missing")
        }
    }

    private fun graph(): DtoGraph {
        val hiddenTail = baseProp(
            id = HIDDEN_PROP_ID,
            name = "hiddenTail",
            targetTypeId = null,
        )
        val nested = baseProp(
            id = NESTED_PROP_ID,
            name = "nested",
            targetTypeId = NESTED_TYPE_ID,
        ).copy(nextPropId = hiddenTail.id, tailPropId = hiddenTail.id)
        val recursive = baseProp(
            id = RECURSIVE_PROP_ID,
            name = "recursive",
            targetTypeId = RECURSIVE_TYPE_ID,
            recursive = true,
        )
        val focused = baseProp(
            id = FOCUSED_PROP_ID,
            name = "focused",
            targetTypeId = FOCUSED_TYPE_ID,
            recursive = true,
        )
        val scalar = baseProp(
            id = SCALAR_PROP_ID,
            name = "scalar",
            targetTypeId = null,
        )
        val sourceReference = baseProp(
            id = SOURCE_REFERENCE_PROP_ID,
            name = "sourceReference",
            targetTypeId = REUSABLE_SOURCE_TYPE_ID,
            targetTypeReference = reusableReference("demo.dto.ReusableView"),
        )
        val binaryReference = baseProp(
            id = BINARY_REFERENCE_PROP_ID,
            name = "binaryReference",
            targetTypeId = null,
            targetTypeReference = reusableReference("contract.ExternalView"),
        )
        val folded = DtoFoldProp(
            id = FOLD_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "folded",
            alias = "folded",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = nested.id,
            targetTypeId = FOLD_TYPE_ID,
        )
        val userValue = DtoUserProp(
            id = USER_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "userValue",
            alias = "userValue",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("kotlin.String", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
        fun DtoBaseProp.copyToMerged(
            id: DtoPropId,
            ownerTypeId: DtoTypeId = BRANCH_MERGED_TYPE_ID,
        ): DtoBaseProp = copy(
            id = id,
            ownerTypeId = ownerTypeId,
            nextPropId = null,
            tailPropId = id,
        )
        val mergedNested = nested.copyToMerged(BRANCH_NESTED_PROP_ID)
        val mergedRecursive = recursive.copyToMerged(BRANCH_RECURSIVE_PROP_ID)
        val mergedFocused = focused
            .copyToMerged(BRANCH_FOCUSED_PROP_ID)
            .copy(targetTypeId = BRANCH_FOCUSED_TYPE_ID)
        val mergedSourceReference = sourceReference.copyToMerged(BRANCH_SOURCE_REFERENCE_PROP_ID)
        val mergedBinaryReference = binaryReference.copyToMerged(BRANCH_BINARY_REFERENCE_PROP_ID)
        val mergedScalar = scalar.copyToMerged(BRANCH_SCALAR_PROP_ID)
        val mergedUserValue = userValue.copy(
            id = BRANCH_USER_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
        )
        val mergedFolded = folded.copy(
            id = BRANCH_FOLD_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            nullGuardPropId = mergedNested.id,
        )
        val branchOnly = baseProp(
            id = BRANCH_ONLY_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            name = "branchOnly",
            targetTypeId = BRANCH_ONLY_TYPE_ID,
        )
        val secondMergedNested = nested.copyToMerged(
            id = SECOND_BRANCH_NESTED_PROP_ID,
            ownerTypeId = SECOND_BRANCH_MERGED_TYPE_ID,
        )
        val secondMergedFolded = folded.copy(
            id = SECOND_BRANCH_FOLD_PROP_ID,
            ownerTypeId = SECOND_BRANCH_MERGED_TYPE_ID,
            nullGuardPropId = secondMergedNested.id,
        )
        val mergedPropIds = listOf(
            mergedNested.id,
            mergedRecursive.id,
            mergedFocused.id,
            mergedSourceReference.id,
            mergedBinaryReference.id,
            mergedScalar.id,
            mergedUserValue.id,
            mergedFolded.id,
            branchOnly.id,
        )
        val branches = listOf(
            branch(
                kind = DtoPolymorphicBranchKind.DEFAULT,
                className = "Default",
                bodyTypeId = BRANCH_BODY_TYPE_ID,
                mergedTypeId = BRANCH_MERGED_TYPE_ID,
            ),
            branch(
                kind = DtoPolymorphicBranchKind.TYPE,
                className = "Special",
                bodyTypeId = SECOND_BRANCH_BODY_TYPE_ID,
                mergedTypeId = SECOND_BRANCH_MERGED_TYPE_ID,
            ),
        )
        val root = type(
            id = ROOT_TYPE_ID,
            name = "RootInput",
            propIds = listOf(
                nested.id,
                recursive.id,
                focused.id,
                sourceReference.id,
                binaryReference.id,
                scalar.id,
                userValue.id,
                folded.id,
            ),
            hiddenFlatPropIds = listOf(hiddenTail.id),
            polymorphism = DtoPolymorphism(exhaustive = true, branches = branches),
        )
        val types = listOf(
            root,
            type(NESTED_TYPE_ID, name = null),
            type(RECURSIVE_TYPE_ID, name = null),
            type(FOCUSED_TYPE_ID, name = null, focusedRecursion = true),
            type(BRANCH_FOCUSED_TYPE_ID, name = null, focusedRecursion = true),
            type(REUSABLE_SOURCE_TYPE_ID, name = "ReusableView"),
            type(FOLD_TYPE_ID, name = null),
            type(BRANCH_ONLY_TYPE_ID, name = null),
            type(BRANCH_BODY_TYPE_ID, name = null),
            type(BRANCH_MERGED_TYPE_ID, name = null, propIds = mergedPropIds),
            type(SECOND_BRANCH_BODY_TYPE_ID, name = null),
            type(
                SECOND_BRANCH_MERGED_TYPE_ID,
                name = null,
                propIds = listOf(secondMergedNested.id, secondMergedFolded.id),
            ),
        ).sortedBy(DtoType::id)
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = types,
            props = listOf(
                nested,
                recursive,
                focused,
                sourceReference,
                binaryReference,
                scalar,
                userValue,
                hiddenTail,
                folded,
                mergedNested,
                mergedRecursive,
                mergedFocused,
                mergedSourceReference,
                mergedBinaryReference,
                mergedScalar,
                mergedUserValue,
                mergedFolded,
                branchOnly,
                secondMergedNested,
                secondMergedFolded,
            ).sortedBy(DtoProp::id),
        )
    }

    private fun type(
        id: DtoTypeId,
        name: String?,
        propIds: List<DtoPropId> = emptyList(),
        hiddenFlatPropIds: List<DtoPropId> = emptyList(),
        focusedRecursion: Boolean = false,
        polymorphism: DtoPolymorphism? = null,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = name,
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = focusedRecursion,
            propIds = propIds,
            hiddenFlatPropIds = hiddenFlatPropIds,
            polymorphism = polymorphism,
        )
    }

    private fun baseProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId = ROOT_TYPE_ID,
        name: String,
        targetTypeId: DtoTypeId?,
        recursive: Boolean = false,
        targetTypeReference: DtoReusableTypeReference? = null,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = name,
            nullable = false,
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
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = null,
            config = null,
            recursive = recursive,
            likeOptions = emptySet(),
        )
    }

    private fun reusableReference(qualifiedName: String): DtoReusableTypeReference {
        return DtoReusableTypeReference(
            qualifiedName = qualifiedName,
            targetBaseTypeId = BASE_TYPE_ID,
            kind = DtoReusableTypeKind.VIEW,
            location = LsiLocation(
                source = if (qualifiedName.startsWith("contract.")) REFERENCE_SOURCE else SOURCE,
                start = LsiPosition(2, 1),
            ),
        )
    }

    private fun importSchema(): ImmutableSchema {
        val bookProps = listOf(
            immutableProp("nested", NESTED_BASE_TYPE_ID),
            immutableProp(
                "recursive",
                RECURSIVE_TARGET_TYPE_ID,
                association = false,
                embedded = true,
            ),
            immutableProp("focused", FOCUSED_BASE_TYPE_ID),
            immutableProp("sourceReference", SOURCE_TARGET_TYPE_ID, association = false),
            immutableProp("binaryReference", BINARY_TARGET_TYPE_ID),
            immutableProp("scalar", STRING_TYPE_ID, association = false),
        )
        return ImmutableSchema(
            listOf(
                immutableType(BASE_TYPE_ID, bookProps),
                immutableType(NESTED_BASE_TYPE_ID),
                immutableType(FOCUSED_BASE_TYPE_ID),
                immutableType(FOLD_BASE_TYPE_ID),
                immutableType(DEFAULT_BRANCH_BASE_TYPE_ID),
                immutableType(SPECIAL_BRANCH_BASE_TYPE_ID),
                immutableType(RECURSIVE_TARGET_TYPE_ID, kind = ImmutableTypeKind.EMBEDDABLE),
                immutableType(SOURCE_TARGET_TYPE_ID),
                immutableType(BINARY_TARGET_TYPE_ID),
            ).sortedBy(ImmutableType::id),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp> = emptyList(),
        kind: ImmutableTypeKind = ImmutableTypeKind.IMMUTABLE,
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
            instantiable = false,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = null,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        name: String,
        targetTypeId: LsiSymbolId?,
        association: Boolean = targetTypeId != null,
        embedded: Boolean = false,
    ): ImmutableProp {
        val id = LsiSymbolId.property(BASE_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = BASE_TYPE_ID,
            name = name,
            documentation = null,
            type = LsiDeclaredType(targetTypeId ?: STRING_TYPE_ID),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = association,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = if (association) PrimaryMapping.ASSOCIATION else PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = if (association) AssociationKind.MANY_TO_ONE else AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = if (association) {
                AssociationStorageKind.COLUMN
            } else {
                AssociationStorageKind.NONE
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

    private fun rootPolymorphism(graph: DtoGraph): DtoPolymorphism {
        return requireNotNull(graph.typesById.getValue(ROOT_TYPE_ID).polymorphism)
    }

    private fun branch(
        kind: DtoPolymorphicBranchKind,
        className: String,
        bodyTypeId: DtoTypeId,
        mergedTypeId: DtoTypeId,
    ): DtoPolymorphicBranch {
        return DtoPolymorphicBranch(
            kind = kind,
            targetBaseTypeId = BASE_TYPE_ID.takeIf { kind == DtoPolymorphicBranchKind.TYPE },
            declaredClassName = null,
            className = className,
            bodyTypeId = bodyTypeId,
            mergedTypeId = mergedTypeId,
            implicit = false,
            location = LOCATION,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Generation.dto")
        val REFERENCE_SOURCE = LsiSource.of("contract/src/main/dto/External.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val NESTED_BASE_TYPE_ID = LsiSymbolId.type("nested.model.Nested")
        val FOCUSED_BASE_TYPE_ID = LsiSymbolId.type("focused.model.Focused")
        val FOLD_BASE_TYPE_ID = LsiSymbolId.type("fold.model.Fold")
        val DEFAULT_BRANCH_BASE_TYPE_ID = LsiSymbolId.type("branch.default.Default")
        val SPECIAL_BRANCH_BASE_TYPE_ID = LsiSymbolId.type("branch.special.Special")
        val RECURSIVE_TARGET_TYPE_ID = LsiSymbolId.type("recursive.model.Node")
        val SOURCE_TARGET_TYPE_ID = LsiSymbolId.type("source.model.Source")
        val BINARY_TARGET_TYPE_ID = LsiSymbolId.type("binary.model.Binary")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val NESTED_TYPE_ID = DtoTypeId("dto#nested")
        val RECURSIVE_TYPE_ID = DtoTypeId("dto#recursive")
        val FOCUSED_TYPE_ID = DtoTypeId("dto#focused")
        val BRANCH_FOCUSED_TYPE_ID = DtoTypeId("dto#branch-focused")
        val REUSABLE_SOURCE_TYPE_ID = DtoTypeId("dto#reusable-source")
        val FOLD_TYPE_ID = DtoTypeId("dto#fold")
        val BRANCH_ONLY_TYPE_ID = DtoTypeId("dto#branch-only")
        val BRANCH_BODY_TYPE_ID = DtoTypeId("dto#branch-body")
        val BRANCH_MERGED_TYPE_ID = DtoTypeId("dto#branch-merged")
        val SECOND_BRANCH_BODY_TYPE_ID = DtoTypeId("dto#second-branch-body")
        val SECOND_BRANCH_MERGED_TYPE_ID = DtoTypeId("dto#second-branch-merged")
        val NESTED_PROP_ID = DtoPropId("dto#prop-nested")
        val RECURSIVE_PROP_ID = DtoPropId("dto#prop-recursive")
        val FOCUSED_PROP_ID = DtoPropId("dto#prop-focused")
        val SCALAR_PROP_ID = DtoPropId("dto#prop-scalar")
        val SOURCE_REFERENCE_PROP_ID = DtoPropId("dto#prop-source-reference")
        val BINARY_REFERENCE_PROP_ID = DtoPropId("dto#prop-binary-reference")
        val FOLD_PROP_ID = DtoPropId("dto#prop-fold")
        val HIDDEN_PROP_ID = DtoPropId("dto#prop-hidden")
        val USER_PROP_ID = DtoPropId("dto#prop-user")
        val BRANCH_NESTED_PROP_ID = DtoPropId("dto#branch-prop-nested")
        val BRANCH_RECURSIVE_PROP_ID = DtoPropId("dto#branch-prop-recursive")
        val BRANCH_FOCUSED_PROP_ID = DtoPropId("dto#branch-prop-focused")
        val BRANCH_SOURCE_REFERENCE_PROP_ID = DtoPropId("dto#branch-prop-source-reference")
        val BRANCH_BINARY_REFERENCE_PROP_ID = DtoPropId("dto#branch-prop-binary-reference")
        val BRANCH_SCALAR_PROP_ID = DtoPropId("dto#branch-prop-scalar")
        val BRANCH_USER_PROP_ID = DtoPropId("dto#branch-prop-user")
        val BRANCH_FOLD_PROP_ID = DtoPropId("dto#branch-prop-fold")
        val BRANCH_ONLY_PROP_ID = DtoPropId("dto#branch-prop-only")
        val SECOND_BRANCH_NESTED_PROP_ID = DtoPropId("dto#second-branch-prop-nested")
        val SECOND_BRANCH_FOLD_PROP_ID = DtoPropId("dto#second-branch-prop-fold")
    }
}
