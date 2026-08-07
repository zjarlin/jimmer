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
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace

class DtoDraftWritePoetTest {

    @Test
    fun `renders direct scalar draft write for both target languages`() {
        val direct = fixture(DtoModifier.STATIC, nullable = false, list = false, direct = true)

        assertEquals("__draft.setName(this.name);\n", direct.renderApt())
        assertEquals("_draft.name = name\n", direct.renderKsp())
    }

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
        direct: Boolean = false,
    ): Fixture {
        val propName = if (direct) "name" else "authorIds"
        val immutablePropId = LsiSymbolId.property(IMMUTABLE_TYPE_ID, if (direct) "name" else "authors")
        val dtoProp = DtoBaseProp(
            id = DTO_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = propName,
            alias = propName,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding(if (direct) "name" else "authors", immutablePropId)),
            basePath = propName,
            nextPropId = null,
            tailPropId = DTO_PROP_ID,
            baseNullable = false,
            inputModifier = modifier,
            functionName = if (direct) null else "id",
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
            id = immutablePropId,
            declarationId = immutablePropId,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = if (direct) "name" else "authors",
            documentation = null,
            type = LsiDeclaredType(if (direct) LsiSymbolId.type("java.lang.String") else LsiSymbolId.type("demo.Author")),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = if (direct) false else list,
            association = !direct,
            embedded = false,
            targetTypeId = null,
            primaryMapping = if (direct) PrimaryMapping.SCALAR else PrimaryMapping.ASSOCIATION,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = if (direct) AssociationKind.NONE else if (list) AssociationKind.MANY_TO_MANY else AssociationKind.MANY_TO_ONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = if (direct) AssociationStorageKind.NONE else if (list) AssociationStorageKind.MIDDLE_TABLE else AssociationStorageKind.COLUMN,
            transientResolver = null,
            view = null,
            genericTarget = !direct,
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
            accessorName = if (prop.name == "name") "NAME_ACCESSOR" else "AUTHOR_IDS_ACCESSOR",
            draftName = "__draft",
            valueName = prop.name,
            baseValueWriterName = if (prop.name == "name") "setName" else "setAuthors",
            generatedTargetType = { LsiDeclaredType(LsiSymbolId.type("demo.AuthorDto")) },
        ).toString()
    }

    private fun Fixture.renderKsp(): String {
        return KspDtoDraftWriteRenderer.render(
            prop = prop,
            graph = graph,
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            accessorName = if (prop.name == "name") "NAME_ACCESSOR" else "AUTHOR_IDS_ACCESSOR",
            draftName = "_draft",
            valueName = prop.name,
            generatedTargetType = { LsiDeclaredType(LsiSymbolId.type("demo.AuthorDto")) },
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
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookInput#root")
        val DTO_PROP_ID = DtoPropId("demo.dto.BookInput#prop:authorIds")
    }
}
