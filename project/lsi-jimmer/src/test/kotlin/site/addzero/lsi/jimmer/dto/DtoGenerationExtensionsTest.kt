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
        assertTrue(REFERENCE_SOURCE in graph.originatingSources())
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
    fun `rejects properties copied from another graph`() {
        val graph = graph()
        val root = graph.typesById.getValue(ROOT_TYPE_ID)
        val foreignProp = root.basePropsInDeclarationOrder(graph).first().copy(name = "foreign")

        assertFailsWith<IllegalArgumentException> {
            foreignProp.generatedTargetType(graph)
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
            type(REUSABLE_SOURCE_TYPE_ID, name = "ReusableView"),
            type(FOLD_TYPE_ID, name = null),
            type(BRANCH_BODY_TYPE_ID, name = null),
            type(BRANCH_MERGED_TYPE_ID, name = null),
            type(SECOND_BRANCH_BODY_TYPE_ID, name = null),
            type(SECOND_BRANCH_MERGED_TYPE_ID, name = null),
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
        name: String,
        targetTypeId: DtoTypeId?,
        recursive: Boolean = false,
        targetTypeReference: DtoReusableTypeReference? = null,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ROOT_TYPE_ID,
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
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val NESTED_TYPE_ID = DtoTypeId("dto#nested")
        val RECURSIVE_TYPE_ID = DtoTypeId("dto#recursive")
        val FOCUSED_TYPE_ID = DtoTypeId("dto#focused")
        val REUSABLE_SOURCE_TYPE_ID = DtoTypeId("dto#reusable-source")
        val FOLD_TYPE_ID = DtoTypeId("dto#fold")
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
    }
}
