package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoReusableTypeKind
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetTypeName

class JimmerDtoPoetTypeNamesTest {

    @Test
    fun `pre-registers the complete generated type tree for one root`() {
        val fixture = fixture()
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(fixture.graph))

        val generatedTypes = JimmerDtoPoetTypeNames.forRoot(
            graph = fixture.graph,
            rootType = fixture.rootType,
            batchRootTypeNames = batchRootTypeNames,
        )

        assertEquals(
            listOf(
                "demo.dto.RootView",
                "other.dto.OtherView",
                "demo.dto.RootView.TargetOf_shared",
                "demo.dto.RootView.TargetOf_detail",
                "demo.dto.RootView.TargetOf_summary",
                "demo.dto.RootView.TargetOf_children",
                "demo.dto.RootView.TargetOf_children.TargetOf_children_2",
            ),
            generatedTypes.keys.map { typeName -> typeName.canonicalName },
        )
        assertEquals(
            mapOf(
                "demo.dto.RootView" to ROOT_TYPE_ID,
                "other.dto.OtherView" to OTHER_ROOT_TYPE_ID,
                "demo.dto.RootView.TargetOf_shared" to SHARED_TARGET_TYPE_ID,
                "demo.dto.RootView.TargetOf_detail" to DETAIL_TARGET_TYPE_ID,
                "demo.dto.RootView.TargetOf_summary" to FOLD_TARGET_TYPE_ID,
                "demo.dto.RootView.TargetOf_children" to BRANCH_MERGED_TYPE_ID,
                "demo.dto.RootView.TargetOf_children.TargetOf_children_2" to
                    BRANCH_NESTED_TARGET_TYPE_ID,
            ),
            generatedTypes.mapKeys { (typeName, _) -> typeName.canonicalName },
        )
        assertFalse(BRANCH_BODY_TYPE_ID in generatedTypes.values)
        assertEquals(
            1,
            generatedTypes.keys.count { typeName ->
                typeName.canonicalName == "demo.dto.RootView.TargetOf_shared"
            },
        )
        assertFalse(
            generatedTypes.keys.any { typeName ->
                typeName.canonicalName ==
                    "demo.dto.RootView.TargetOf_children.TargetOf_shared"
            },
        )
        val rootOccurrence = batchRootTypeNames.getValue(ROOT_TYPE_ID)
        assertEquals(
            "TargetOf_detail",
            JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                ownerTypeName = rootOccurrence,
                targetType = fixture.graph.typesById.getValue(DETAIL_TARGET_TYPE_ID),
                typeIdsByTypeName = generatedTypes,
            ),
        )
        assertEquals(
            "TargetOf_summary",
            JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                ownerTypeName = rootOccurrence,
                targetType = fixture.graph.typesById.getValue(FOLD_TARGET_TYPE_ID),
                typeIdsByTypeName = generatedTypes,
            ),
        )
        val branchOccurrence = generatedTypes.entries
            .single { (_, typeId) -> typeId == BRANCH_MERGED_TYPE_ID }
            .key
        assertEquals(
            "TargetOf_children_2",
            JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                ownerTypeName = branchOccurrence,
                targetType = fixture.graph.typesById.getValue(BRANCH_NESTED_TARGET_TYPE_ID),
                typeIdsByTypeName = generatedTypes,
            ),
        )
    }

    @Test
    fun `rejects duplicate canonical names with different source structures`() {
        val fixture = fixture()
        val batchRootTypeNames = JimmerDtoPoetTypeNames
            .roots(listOf(fixture.graph))
            .toMutableMap()
        batchRootTypeNames[OTHER_ROOT_TYPE_ID] = JimmerDtoPoetTypeNames.create(
            packageName = "demo",
            simpleNames = listOf("dto", "RootView"),
        )

        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.forRoot(
                graph = fixture.graph,
                rootType = fixture.rootType,
                batchRootTypeNames = batchRootTypeNames,
            )
        }
    }

    @Test
    fun `finds only the direct child occurrence in the owner package`() {
        val ownerTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView"),
        )
        val directChildTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "TargetOf_detail"),
        )
        val crossPackageTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "other.dto",
            simpleNames = listOf("RootView", "TargetOf_detail"),
        )
        val grandchildTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "Container", "TargetOf_detail"),
        )
        val typeIdsByTypeName = linkedMapOf(
            crossPackageTypeName to DETAIL_TARGET_TYPE_ID,
            grandchildTypeName to DETAIL_TARGET_TYPE_ID,
            directChildTypeName to DETAIL_TARGET_TYPE_ID,
        )

        assertEquals(
            directChildTypeName,
            JimmerDtoPoetTypeNames.directChildOccurrenceOrNull(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            ),
        )
        assertEquals(
            directChildTypeName,
            JimmerDtoPoetTypeNames.requireDirectChildOccurrence(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            ),
        )
    }

    @Test
    fun `returns null for missing direct child and rejects it when required`() {
        val ownerTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView"),
        )
        val typeIdsByTypeName = emptyMap<LsiPoetTypeName, DtoTypeId>()

        assertNull(
            JimmerDtoPoetTypeNames.directChildOccurrenceOrNull(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.requireDirectChildOccurrence(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            )
        }
    }

    @Test
    fun `rejects duplicate direct child occurrences through both APIs`() {
        val ownerTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView"),
        )
        val firstDirectChildTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "TargetOf_detail"),
        )
        val secondDirectChildTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "TargetOf_detail_2"),
        )
        val typeIdsByTypeName = linkedMapOf(
            firstDirectChildTypeName to DETAIL_TARGET_TYPE_ID,
            secondDirectChildTypeName to DETAIL_TARGET_TYPE_ID,
        )

        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.directChildOccurrenceOrNull(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.requireDirectChildOccurrence(
                ownerTypeName = ownerTypeName,
                targetTypeId = DETAIL_TARGET_TYPE_ID,
                typeIdsByTypeName = typeIdsByTypeName,
            )
        }
    }

    @Test
    fun `resolves direct fold reusable and promoted targets from owner occurrence`() {
        val fixture = fixture()
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(fixture.graph))
        val generatedTypes = JimmerDtoPoetTypeNames.forRoot(
            graph = fixture.graph,
            rootType = fixture.rootType,
            batchRootTypeNames = batchRootTypeNames,
        )
        val rootOccurrence = batchRootTypeNames.getValue(ROOT_TYPE_ID)
        val branchOccurrence = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "TargetOf_children"),
        )
        assertEquals(BRANCH_MERGED_TYPE_ID, generatedTypes[branchOccurrence])

        fun resolve(propId: DtoPropId, ownerOccurrence: LsiPoetTypeName): LsiPoetTypeName? {
            return JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                graph = fixture.graph,
                prop = fixture.graph.propsById.getValue(propId),
                generatedOwnerTypeName = ownerOccurrence,
                generatedDtoTypeIdsByTypeName = generatedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            )
        }

        assertEquals("demo.dto.RootView.TargetOf_detail", resolve(ROOT_DETAIL_PROP_ID, rootOccurrence)?.canonicalName)
        assertEquals("demo.dto.RootView.TargetOf_summary", resolve(ROOT_FOLD_PROP_ID, rootOccurrence)?.canonicalName)
        assertEquals("other.dto.OtherView", resolve(ROOT_REUSABLE_PROP_ID, rootOccurrence)?.canonicalName)
        assertEquals("demo.dto.RootView.TargetOf_detail", resolve(MERGED_DETAIL_PROP_ID, branchOccurrence)?.canonicalName)
        assertEquals("demo.dto.RootView.TargetOf_summary", resolve(MERGED_FOLD_PROP_ID, branchOccurrence)?.canonicalName)
        assertEquals(
            "demo.dto.RootView.TargetOf_children.TargetOf_children_2",
            resolve(MERGED_CHILDREN_PROP_ID, branchOccurrence)?.canonicalName,
        )
        assertNull(resolve(ROOT_EXTERNAL_REUSABLE_PROP_ID, rootOccurrence))
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("contract.ExternalView")),
            JimmerDtoPoetTypeNames.toLsiGeneratedTargetType(
                graph = fixture.graph,
                prop = fixture.graph.propsById.getValue(ROOT_EXTERNAL_REUSABLE_PROP_ID),
                generatedOwnerTypeName = rootOccurrence,
                generatedDtoTypeIdsByTypeName = generatedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            ),
        )
    }

    @Test
    fun `rejects conflicting owner and duplicate target occurrences`() {
        val fixture = fixture()
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(fixture.graph))
        val generatedTypes = JimmerDtoPoetTypeNames.forRoot(
            graph = fixture.graph,
            rootType = fixture.rootType,
            batchRootTypeNames = batchRootTypeNames,
        )
        val rootOccurrence = batchRootTypeNames.getValue(ROOT_TYPE_ID)
        val branchOccurrence = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("RootView", "TargetOf_children"),
        )
        assertEquals(BRANCH_MERGED_TYPE_ID, generatedTypes[branchOccurrence])
        val rootDetailProp = fixture.graph.propsById.getValue(ROOT_DETAIL_PROP_ID)

        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                graph = fixture.graph,
                prop = rootDetailProp,
                generatedOwnerTypeName = branchOccurrence,
                generatedDtoTypeIdsByTypeName = generatedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            )
        }

        val conflictingGeneratedTypes = generatedTypes + (
            JimmerDtoPoetTypeNames.create(
                packageName = rootOccurrence.packageName,
                simpleNames = rootOccurrence.simpleNames + "TargetOf_detail_2",
            ) to DETAIL_TARGET_TYPE_ID
        )
        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                graph = fixture.graph,
                prop = rootDetailProp,
                generatedOwnerTypeName = rootOccurrence,
                generatedDtoTypeIdsByTypeName = conflictingGeneratedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            )
        }
    }

    @Test
    fun `resolves flattened targets from tail semantics and head occurrence name`() {
        val directTail = baseProp(
            id = FLAT_DIRECT_TAIL_PROP_ID,
            ownerTypeId = FLAT_ROOT_TYPE_ID,
            name = "directTail",
            targetTypeId = FLAT_TARGET_TYPE_ID,
        )
        val reusableTail = baseProp(
            id = FLAT_REUSABLE_TAIL_PROP_ID,
            ownerTypeId = FLAT_ROOT_TYPE_ID,
            name = "reusableTail",
            targetTypeId = FLAT_REUSABLE_ROOT_TYPE_ID,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = "other.dto.ReusableView",
                targetBaseTypeId = ENTITY_TYPE_ID,
                kind = DtoReusableTypeKind.VIEW,
                location = LOCATION,
            ),
        )
        val directHead = baseProp(
            id = FLAT_DIRECT_HEAD_PROP_ID,
            ownerTypeId = FLAT_ROOT_TYPE_ID,
            name = "direct",
            targetTypeId = directTail.targetTypeId,
            nextPropId = directTail.id,
            tailPropId = directTail.id,
        )
        val reusableHead = baseProp(
            id = FLAT_REUSABLE_HEAD_PROP_ID,
            ownerTypeId = FLAT_ROOT_TYPE_ID,
            name = "reusable",
            targetTypeId = reusableTail.targetTypeId,
            targetTypeReference = reusableTail.targetTypeReference,
            nextPropId = reusableTail.id,
            tailPropId = reusableTail.id,
        )
        val rootType = dtoType(
            id = FLAT_ROOT_TYPE_ID,
            name = "FlatView",
            propIds = listOf(directHead.id, reusableHead.id),
            hiddenFlatPropIds = listOf(directTail.id, reusableTail.id),
        )
        val reusableRootType = dtoType(
            id = FLAT_REUSABLE_ROOT_TYPE_ID,
            packageName = "other.dto",
            name = "ReusableView",
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(rootType.id, reusableRootType.id),
            types = listOf(
                rootType,
                reusableRootType,
                dtoType(id = FLAT_TARGET_TYPE_ID),
            ).sortedBy(DtoType::id),
            props = listOf(
                directHead,
                reusableHead,
                directTail,
                reusableTail,
            ).sortedBy(DtoProp::id),
        )
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(graph))
        val generatedTypes = JimmerDtoPoetTypeNames.forRoot(
            graph = graph,
            rootType = rootType,
            batchRootTypeNames = batchRootTypeNames,
        )
        val rootOccurrence = batchRootTypeNames.getValue(rootType.id)

        fun resolve(prop: DtoProp): LsiPoetTypeName? {
            return JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                graph = graph,
                prop = prop,
                generatedOwnerTypeName = rootOccurrence,
                generatedDtoTypeIdsByTypeName = generatedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            )
        }

        assertEquals("demo.dto.FlatView.TargetOf_direct", resolve(directHead)?.canonicalName)
        assertEquals("other.dto.ReusableView", resolve(reusableHead)?.canonicalName)
        val directOccurrence = requireNotNull(resolve(directHead))
        assertEquals(directHead.generatedTargetType(graph)?.id, directTail.generatedTargetType(graph)?.id)
        assertEquals(directHead.generatedTargetType(graph)?.id, generatedTypes[directOccurrence])
        assertFalse(
            generatedTypes.keys.any { typeName ->
                typeName.canonicalName.endsWith("TargetOf_directTail")
            },
        )
    }

    @Test
    fun `tracks repeated semantic target at focused recursion occurrences`() {
        val parentProp = baseProp(
            id = RECURSIVE_PARENT_PROP_ID,
            ownerTypeId = RECURSIVE_ROOT_TYPE_ID,
            name = "parent",
            targetTypeId = FOCUSED_PARENT_TYPE_ID,
            recursive = true,
        )
        val childrenProp = baseProp(
            id = RECURSIVE_CHILDREN_PROP_ID,
            ownerTypeId = RECURSIVE_ROOT_TYPE_ID,
            name = "children",
            targetTypeId = FOCUSED_CHILDREN_TYPE_ID,
            recursive = true,
        )
        val rootOwnerProp = baseProp(
            id = RECURSIVE_ROOT_OWNER_PROP_ID,
            ownerTypeId = RECURSIVE_ROOT_TYPE_ID,
            name = "owner",
            targetTypeId = RECURSIVE_OWNER_TARGET_TYPE_ID,
        )
        val parentOwnerProp = baseProp(
            id = FOCUSED_PARENT_OWNER_PROP_ID,
            ownerTypeId = FOCUSED_PARENT_TYPE_ID,
            name = "owner",
            targetTypeId = RECURSIVE_OWNER_TARGET_TYPE_ID,
        )
        val childrenOwnerProp = baseProp(
            id = FOCUSED_CHILDREN_OWNER_PROP_ID,
            ownerTypeId = FOCUSED_CHILDREN_TYPE_ID,
            name = "owner",
            targetTypeId = RECURSIVE_OWNER_TARGET_TYPE_ID,
        )
        val selfProp = baseProp(
            id = RECURSIVE_SELF_PROP_ID,
            ownerTypeId = RECURSIVE_ROOT_TYPE_ID,
            name = "self",
            targetTypeId = RECURSIVE_ROOT_TYPE_ID,
            recursive = true,
        )
        val rootType = dtoType(
            id = RECURSIVE_ROOT_TYPE_ID,
            name = "NodeView",
            propIds = listOf(rootOwnerProp.id, parentProp.id, childrenProp.id, selfProp.id),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(rootType.id),
            types = listOf(
                rootType,
                dtoType(
                    id = FOCUSED_PARENT_TYPE_ID,
                    focusedRecursion = true,
                    propIds = listOf(parentOwnerProp.id),
                ),
                dtoType(
                    id = FOCUSED_CHILDREN_TYPE_ID,
                    focusedRecursion = true,
                    propIds = listOf(childrenOwnerProp.id),
                ),
                dtoType(id = RECURSIVE_OWNER_TARGET_TYPE_ID),
            ).sortedBy(DtoType::id),
            props = listOf(
                parentProp,
                childrenProp,
                rootOwnerProp,
                parentOwnerProp,
                childrenOwnerProp,
                selfProp,
            ).sortedBy(DtoProp::id),
        )

        val generatedTypes = JimmerDtoPoetTypeNames.forRoot(
            graph = graph,
            rootType = rootType,
            batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(graph)),
        )

        assertEquals(
            listOf(
                "demo.dto.NodeView.TargetOf_owner",
                "demo.dto.NodeView.TargetOf_parent.TargetOf_owner",
                "demo.dto.NodeView.TargetOf_children.TargetOf_owner",
            ),
            generatedTypes
                .filterValues { typeId -> typeId == RECURSIVE_OWNER_TARGET_TYPE_ID }
                .keys
                .map { typeName -> typeName.canonicalName },
        )
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(graph))
        val rootOccurrence = batchRootTypeNames.getValue(rootType.id)
        val parentOccurrence = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("NodeView", "TargetOf_parent"),
        )
        val childrenOccurrence = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("NodeView", "TargetOf_children"),
        )
        assertEquals(FOCUSED_PARENT_TYPE_ID, generatedTypes[parentOccurrence])
        assertEquals(FOCUSED_CHILDREN_TYPE_ID, generatedTypes[childrenOccurrence])
        assertEquals(
            rootOccurrence,
            JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                graph = graph,
                prop = selfProp,
                generatedOwnerTypeName = rootOccurrence,
                generatedDtoTypeIdsByTypeName = generatedTypes,
                batchRootDtoTypeNames = batchRootTypeNames,
            ),
        )
        listOf(
            rootOwnerProp to rootOccurrence,
            parentOwnerProp to parentOccurrence,
            childrenOwnerProp to childrenOccurrence,
        ).forEach { (prop, ownerOccurrence) ->
            assertEquals(
                "${ownerOccurrence.canonicalName}.TargetOf_owner",
                JimmerDtoPoetTypeNames.generatedTargetTypeNameOrNull(
                    graph = graph,
                    prop = prop,
                    generatedOwnerTypeName = ownerOccurrence,
                    generatedDtoTypeIdsByTypeName = generatedTypes,
                    batchRootDtoTypeNames = batchRootTypeNames,
                )?.canonicalName,
            )
        }

        val scopedNames = mutableMapOf(
            RECURSIVE_OWNER_TARGET_TYPE_ID to JimmerDtoPoetTypeNames.create(
                "demo.dto",
                listOf("NodeView", "TargetOf_owner"),
            ),
        )
        val locallyRegisteredTypeIds = mutableSetOf<DtoTypeId>()
        val focusedOwnerTypeName = JimmerDtoPoetTypeNames.create(
            "demo.dto",
            listOf("NodeView", "TargetOf_parent", "TargetOf_owner"),
        )
        val ownerTargetType = graph.typesById.getValue(RECURSIVE_OWNER_TARGET_TYPE_ID)
        JimmerDtoPoetTypeNames.register(
            graph = graph,
            type = ownerTargetType,
            typeNamesByTypeId = scopedNames,
            locallyRegisteredTypeIds = locallyRegisteredTypeIds,
            typeName = focusedOwnerTypeName,
        )
        assertEquals(focusedOwnerTypeName, scopedNames.getValue(RECURSIVE_OWNER_TARGET_TYPE_ID))
        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.register(
                graph = graph,
                type = ownerTargetType,
                typeNamesByTypeId = scopedNames,
                locallyRegisteredTypeIds = locallyRegisteredTypeIds,
                typeName = JimmerDtoPoetTypeNames.create(
                    "demo.dto",
                    listOf("NodeView", "TargetOf_children", "TargetOf_owner"),
                ),
            )
        }
    }

    private fun fixture(): Fixture {
        val rootSharedProp = baseProp(
            id = ROOT_SHARED_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "shared",
            targetTypeId = SHARED_TARGET_TYPE_ID,
        )
        val rootDetailProp = baseProp(
            id = ROOT_DETAIL_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "detail",
            targetTypeId = DETAIL_TARGET_TYPE_ID,
        )
        val rootFoldProp = foldProp(
            id = ROOT_FOLD_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "summary",
            targetTypeId = FOLD_TARGET_TYPE_ID,
        )
        val rootReusableProp = baseProp(
            id = ROOT_REUSABLE_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "other",
            targetTypeId = OTHER_ROOT_TYPE_ID,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = "other.dto.OtherView",
                targetBaseTypeId = ENTITY_TYPE_ID,
                kind = DtoReusableTypeKind.VIEW,
                location = LOCATION,
            ),
        )
        val rootExternalReusableProp = baseProp(
            id = ROOT_EXTERNAL_REUSABLE_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "external",
            targetTypeId = null,
            targetTypeReference = DtoReusableTypeReference(
                qualifiedName = "contract.ExternalView",
                targetBaseTypeId = ENTITY_TYPE_ID,
                kind = DtoReusableTypeKind.VIEW,
                location = LOCATION,
            ),
        )
        val bodyChildrenProp = baseProp(
            id = BODY_CHILDREN_PROP_ID,
            ownerTypeId = BRANCH_BODY_TYPE_ID,
            name = "children",
            targetTypeId = BRANCH_NESTED_TARGET_TYPE_ID,
        )
        val mergedSharedProp = baseProp(
            id = MERGED_SHARED_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            name = "shared",
            targetTypeId = SHARED_TARGET_TYPE_ID,
        )
        val mergedDetailProp = baseProp(
            id = MERGED_DETAIL_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            name = "detail",
            targetTypeId = DETAIL_TARGET_TYPE_ID,
        )
        val mergedFoldProp = foldProp(
            id = MERGED_FOLD_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            name = "summary",
            targetTypeId = FOLD_TARGET_TYPE_ID,
        )
        val mergedChildrenProp = baseProp(
            id = MERGED_CHILDREN_PROP_ID,
            ownerTypeId = BRANCH_MERGED_TYPE_ID,
            name = "children",
            targetTypeId = BRANCH_NESTED_TARGET_TYPE_ID,
        )
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.TYPE,
            targetBaseTypeId = BRANCH_ENTITY_TYPE_ID,
            declaredClassName = "TargetOf_children",
            className = "TargetOf_children",
            bodyTypeId = BRANCH_BODY_TYPE_ID,
            mergedTypeId = BRANCH_MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val rootType = dtoType(
            id = ROOT_TYPE_ID,
            packageName = "demo.dto",
            name = "RootView",
            propIds = listOf(
                rootSharedProp.id,
                rootDetailProp.id,
                rootFoldProp.id,
                rootReusableProp.id,
                rootExternalReusableProp.id,
            ),
            polymorphism = DtoPolymorphism(
                exhaustive = true,
                branches = listOf(branch),
            ),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_TYPE_ID, OTHER_ROOT_TYPE_ID),
            types = listOf(
                rootType,
                dtoType(
                    id = OTHER_ROOT_TYPE_ID,
                    packageName = "other.dto",
                    name = "OtherView",
                ),
                dtoType(id = SHARED_TARGET_TYPE_ID),
                dtoType(id = DETAIL_TARGET_TYPE_ID),
                dtoType(id = FOLD_TARGET_TYPE_ID),
                dtoType(
                    id = BRANCH_BODY_TYPE_ID,
                    propIds = listOf(bodyChildrenProp.id),
                ),
                dtoType(
                    id = BRANCH_MERGED_TYPE_ID,
                    propIds = listOf(
                        mergedSharedProp.id,
                        mergedDetailProp.id,
                        mergedFoldProp.id,
                        mergedChildrenProp.id,
                    ),
                ),
                dtoType(id = BRANCH_NESTED_TARGET_TYPE_ID),
            ).sortedBy(DtoType::id),
            props = listOf(
                rootSharedProp,
                rootDetailProp,
                rootFoldProp,
                rootReusableProp,
                rootExternalReusableProp,
                bodyChildrenProp,
                mergedSharedProp,
                mergedDetailProp,
                mergedFoldProp,
                mergedChildrenProp,
            ).sortedBy(DtoProp::id),
        )
        return Fixture(graph, rootType)
    }

    private fun dtoType(
        id: DtoTypeId,
        packageName: String = "demo.dto",
        name: String? = null,
        focusedRecursion: Boolean = false,
        propIds: List<DtoPropId> = emptyList(),
        hiddenFlatPropIds: List<DtoPropId> = emptyList(),
        polymorphism: DtoPolymorphism? = null,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = ENTITY_TYPE_ID,
            packageName = packageName,
            name = name,
            modifiers = emptySet(),
            annotations = emptyList<DtoAnnotation>(),
            superInterfaces = emptyList<DtoTypeRef>(),
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
        ownerTypeId: DtoTypeId,
        name: String,
        targetTypeId: DtoTypeId?,
        recursive: Boolean = false,
        targetTypeReference: DtoReusableTypeReference? = null,
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(
                    name = name,
                    propId = LsiSymbolId.property(ENTITY_TYPE_ID, name),
                ),
            ),
            basePath = name,
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = false,
            inputModifier = DtoModifier.FIXED,
            functionName = null,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = null,
            config = null,
            recursive = recursive,
            likeOptions = emptySet(),
        )
    }

    private fun foldProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        name: String,
        targetTypeId: DtoTypeId,
    ): DtoFoldProp {
        return DtoFoldProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = name,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = targetTypeId,
        )
    }

    private data class Fixture(
        val graph: DtoGraph,
        val rootType: DtoType,
    )

    private companion object {
        val SOURCE = LsiSource.of("demo/Root.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))

        val ROOT_TYPE_ID = DtoTypeId("demo/Root.dto#root:00:RootView")
        val OTHER_ROOT_TYPE_ID = DtoTypeId("demo/Root.dto#root:01:OtherView")
        val SHARED_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:shared")
        val DETAIL_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:detail")
        val FOLD_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:fold")
        val BRANCH_BODY_TYPE_ID = DtoTypeId("demo/Root.dto#type:branch-body")
        val BRANCH_MERGED_TYPE_ID = DtoTypeId("demo/Root.dto#type:branch-merged")
        val BRANCH_NESTED_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:branch-nested")
        val RECURSIVE_ROOT_TYPE_ID = DtoTypeId("demo/Root.dto#type:recursive-root")
        val FOCUSED_PARENT_TYPE_ID = DtoTypeId("demo/Root.dto#type:focused-parent")
        val FOCUSED_CHILDREN_TYPE_ID = DtoTypeId("demo/Root.dto#type:focused-children")
        val RECURSIVE_OWNER_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:recursive-owner")
        val FLAT_ROOT_TYPE_ID = DtoTypeId("demo/Root.dto#type:flat-root")
        val FLAT_REUSABLE_ROOT_TYPE_ID = DtoTypeId("demo/Root.dto#type:flat-reusable-root")
        val FLAT_TARGET_TYPE_ID = DtoTypeId("demo/Root.dto#type:flat-target")

        val ROOT_SHARED_PROP_ID = DtoPropId("demo/Root.dto#prop:root-shared")
        val ROOT_DETAIL_PROP_ID = DtoPropId("demo/Root.dto#prop:root-detail")
        val ROOT_FOLD_PROP_ID = DtoPropId("demo/Root.dto#prop:root-fold")
        val ROOT_REUSABLE_PROP_ID = DtoPropId("demo/Root.dto#prop:root-reusable")
        val ROOT_EXTERNAL_REUSABLE_PROP_ID = DtoPropId("demo/Root.dto#prop:root-external-reusable")
        val BODY_CHILDREN_PROP_ID = DtoPropId("demo/Root.dto#prop:body-children")
        val MERGED_SHARED_PROP_ID = DtoPropId("demo/Root.dto#prop:merged-shared")
        val MERGED_DETAIL_PROP_ID = DtoPropId("demo/Root.dto#prop:merged-detail")
        val MERGED_FOLD_PROP_ID = DtoPropId("demo/Root.dto#prop:merged-fold")
        val MERGED_CHILDREN_PROP_ID = DtoPropId("demo/Root.dto#prop:merged-children")
        val RECURSIVE_PARENT_PROP_ID = DtoPropId("demo/Root.dto#prop:recursive-parent")
        val RECURSIVE_CHILDREN_PROP_ID = DtoPropId("demo/Root.dto#prop:recursive-children")
        val RECURSIVE_ROOT_OWNER_PROP_ID = DtoPropId("demo/Root.dto#prop:recursive-root-owner")
        val FOCUSED_PARENT_OWNER_PROP_ID = DtoPropId("demo/Root.dto#prop:focused-parent-owner")
        val FOCUSED_CHILDREN_OWNER_PROP_ID = DtoPropId("demo/Root.dto#prop:focused-children-owner")
        val RECURSIVE_SELF_PROP_ID = DtoPropId("demo/Root.dto#prop:recursive-self")
        val FLAT_DIRECT_HEAD_PROP_ID = DtoPropId("demo/Root.dto#prop:flat-direct-head")
        val FLAT_DIRECT_TAIL_PROP_ID = DtoPropId("demo/Root.dto#prop:flat-direct-tail")
        val FLAT_REUSABLE_HEAD_PROP_ID = DtoPropId("demo/Root.dto#prop:flat-reusable-head")
        val FLAT_REUSABLE_TAIL_PROP_ID = DtoPropId("demo/Root.dto#prop:flat-reusable-tail")

        val ENTITY_TYPE_ID = LsiSymbolId.type("demo.Entity")
        val BRANCH_ENTITY_TYPE_ID = LsiSymbolId.type("demo.BranchEntity")
    }
}
