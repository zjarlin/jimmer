package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoDraftWriteRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDraftWriteRenderer
import site.addzero.lsi.core.LsiLanguage
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
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace

class DtoDraftWritePoetTest {

    @Test
    fun `renders platform-specific entity list fallback boundaries`() {
        val dynamicList = fixture(DtoModifier.DYNAMIC, nullable = true, list = true)
        assertEquals(
            "AUTHOR_IDS_ACCESSOR.set(__draft, this.authorIds != null ? this.authorIds : java.util.Collections.emptyList());\n",
            dynamicList.renderApt(),
        )
        assertEquals(
            "AUTHOR_IDS_ACCESSOR.set(_draft, authorIds.kotlin.collections.orEmpty())\n",
            dynamicList.renderKsp(),
        )

        listOf(DtoModifier.STATIC, DtoModifier.FUZZY).forEach { modifier ->
            val optionalList = fixture(modifier, nullable = true, list = true)
            assertEquals(
                "AUTHOR_IDS_ACCESSOR.set(__draft, this.authorIds != null ? this.authorIds : java.util.Collections.emptyList());\n",
                optionalList.renderApt(),
            )
            assertEquals(
                "AUTHOR_IDS_ACCESSOR.set(_draft, authorIds)\n",
                optionalList.renderKsp(),
            )
        }

        val dynamicToOne = fixture(DtoModifier.DYNAMIC, nullable = true, list = false)
        assertEquals(
            "AUTHOR_IDS_ACCESSOR.set(__draft, this.authorIds);\n",
            dynamicToOne.renderApt(),
        )
        assertEquals(
            "AUTHOR_IDS_ACCESSOR.set(_draft, authorIds)\n",
            dynamicToOne.renderKsp(),
        )
    }

    private fun fixture(
        modifier: DtoModifier,
        nullable: Boolean,
        list: Boolean,
    ): Fixture {
        val dtoProp = DtoBaseProp(
            id = DTO_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "authorIds",
            alias = "authorIds",
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("authors", IMMUTABLE_PROP_ID)),
            basePath = "authors",
            nextPropId = null,
            tailPropId = DTO_PROP_ID,
            baseNullable = false,
            inputModifier = modifier,
            functionName = "id",
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(DTO_PROP_ID),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(dtoType),
            props = listOf(dtoProp),
        )
        val immutableProp = ImmutableProp(
            id = IMMUTABLE_PROP_ID,
            declarationId = IMMUTABLE_PROP_ID,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = "authors",
            documentation = null,
            type = LsiDeclaredType(LsiSymbolId.type("demo.Author")),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = list,
            association = true,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = if (list) AssociationKind.MANY_TO_MANY else AssociationKind.MANY_TO_ONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = if (list) AssociationStorageKind.MIDDLE_TABLE else AssociationStorageKind.COLUMN,
            transientResolver = null,
            view = null,
            genericTarget = true,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
        val immutableType = ImmutableType(
            id = IMMUTABLE_TYPE_ID,
            qualifiedName = IMMUTABLE_TYPE_ID.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.IMMUTABLE,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = listOf(immutableProp),
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
        return Fixture(dtoProp, graph, ImmutableSchema(listOf(immutableType)))
    }

    private fun Fixture.renderApt(): String {
        return AptDtoDraftWriteRenderer.render(
            prop = prop,
            graph = graph,
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            accessorName = "AUTHOR_IDS_ACCESSOR",
            draftName = "__draft",
            valueName = "authorIds",
        ).toString()
    }

    private fun Fixture.renderKsp(): String {
        return KspDtoDraftWriteRenderer.render(
            prop = prop,
            graph = graph,
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            accessorName = "AUTHOR_IDS_ACCESSOR",
            draftName = "_draft",
            valueName = "authorIds",
        ).toString()
    }

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    )

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val IMMUTABLE_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "authors")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookInput#root")
        val DTO_PROP_ID = DtoPropId("demo.dto.BookInput#prop:authorIds")
    }
}
