package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef

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
        val rootType = dtoType(
            id = RECURSIVE_ROOT_TYPE_ID,
            name = "NodeView",
            propIds = listOf(rootOwnerProp.id, parentProp.id, childrenProp.id),
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
            propIds = listOf(rootSharedProp.id, rootDetailProp.id, rootFoldProp.id),
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
            hiddenFlatPropIds = emptyList(),
            polymorphism = polymorphism,
        )
    }

    private fun baseProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        name: String,
        targetTypeId: DtoTypeId,
        recursive: Boolean = false,
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
            nextPropId = null,
            tailPropId = id,
            baseNullable = false,
            inputModifier = DtoModifier.FIXED,
            functionName = null,
            targetTypeId = targetTypeId,
            targetTypeReference = null,
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

        val ROOT_SHARED_PROP_ID = DtoPropId("demo/Root.dto#prop:root-shared")
        val ROOT_DETAIL_PROP_ID = DtoPropId("demo/Root.dto#prop:root-detail")
        val ROOT_FOLD_PROP_ID = DtoPropId("demo/Root.dto#prop:root-fold")
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

        val ENTITY_TYPE_ID = LsiSymbolId.type("demo.Entity")
        val BRANCH_ENTITY_TYPE_ID = LsiSymbolId.type("demo.BranchEntity")
    }
}
