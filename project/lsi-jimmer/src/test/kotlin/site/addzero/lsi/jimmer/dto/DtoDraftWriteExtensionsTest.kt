package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
import site.addzero.lsi.model.LsiDeclaredType

class DtoDraftWriteExtensionsTest {

    @Test
    fun `keeps Java head and Kotlin tail formula semantics`() {
        val javaHeadFixture = fixture(headFormula = FormulaKind.ABSTRACT)
        val javaHead = javaHeadFixture.dtoProp
        assertTrue(
            javaHead.isDraftWriteSkipped(javaHeadFixture.graph, javaHeadFixture.schema, LsiLanguage.JAVA),
        )
        assertFalse(
            javaHead.isDraftWriteSkipped(javaHeadFixture.graph, javaHeadFixture.schema, LsiLanguage.KOTLIN),
        )

        val kotlinTailFixture = fixture(flat = true, tailFormula = FormulaKind.LANGUAGE)
        val kotlinTail = kotlinTailFixture.dtoProp
        assertFalse(
            kotlinTail.isDraftWriteSkipped(kotlinTailFixture.graph, kotlinTailFixture.schema, LsiLanguage.JAVA),
        )
        assertTrue(
            kotlinTail.isDraftWriteSkipped(kotlinTailFixture.graph, kotlinTailFixture.schema, LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `skips only direct discriminator writes`() {
        val direct = fixture(directMapping = PrimaryMapping.DISCRIMINATOR)
        assertTrue(direct.dtoProp.isDraftWriteSkipped(direct.graph, direct.schema, LsiLanguage.JAVA))
        assertTrue(direct.dtoProp.isDraftWriteSkipped(direct.graph, direct.schema, LsiLanguage.KOTLIN))

        val flat = fixture(flat = true, tailMapping = PrimaryMapping.DISCRIMINATOR)
        assertFalse(flat.dtoProp.isDraftWriteSkipped(flat.graph, flat.schema, LsiLanguage.JAVA))
        assertFalse(flat.dtoProp.isDraftWriteSkipped(flat.graph, flat.schema, LsiLanguage.KOTLIN))
    }

    @Test
    fun `resolves Kotlin Draft writer from the frozen tail binding`() {
        val direct = fixture()
        assertEquals("head", direct.dtoProp.kotlinDraftValueWriterName(direct.graph))

        val flat = fixture(flat = true)
        assertEquals("tail", flat.dtoProp.kotlinDraftValueWriterName(flat.graph))

        assertFailsWith<IllegalArgumentException> {
            direct.dtoProp.copy(name = "foreign").kotlinDraftValueWriterName(direct.graph)
        }
    }

    private fun fixture(
        flat: Boolean = false,
        headFormula: FormulaKind = FormulaKind.NONE,
        tailFormula: FormulaKind = FormulaKind.NONE,
        directMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        tailMapping: PrimaryMapping = PrimaryMapping.SCALAR,
    ): Fixture {
        val headImmutableProp = immutableProp("head", headFormula, if (flat) PrimaryMapping.SCALAR else directMapping)
        val tailImmutableProp = immutableProp("tail", tailFormula, if (flat) tailMapping else directMapping)
        val immutableType = ImmutableType(
            id = IMMUTABLE_TYPE_ID,
            qualifiedName = "demo.Book",
            kind = ImmutableTypeKind.IMMUTABLE,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = if (flat) listOf(headImmutableProp, tailImmutableProp) else listOf(headImmutableProp),
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
        val schema = ImmutableSchema(listOf(immutableType))
        val headId = DtoPropId("demo#BookView/prop:head")
        val tailId = DtoPropId("demo#BookView/prop:tail")
        val dtoProp = DtoBaseProp(
            id = headId,
            ownerTypeId = DTO_TYPE_ID,
            name = "head",
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("head", headImmutableProp.id)),
            basePath = "head",
            nextPropId = tailId.takeIf { flat },
            tailPropId = tailId.takeIf { flat } ?: headId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val tailProp = DtoBaseProp(
            id = tailId,
            ownerTypeId = DTO_TYPE_ID,
            name = "tail",
            alias = null,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("tail", tailImmutableProp.id)),
            basePath = "tail",
            nextPropId = null,
            tailPropId = tailId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(headId),
            hiddenFlatPropIds = listOf(tailId).takeIf { flat }.orEmpty(),
            polymorphism = null,
        )
        val props = if (flat) listOf<DtoProp>(dtoProp, tailProp) else listOf(dtoProp)
        return Fixture(
            graph = DtoGraph(SOURCE, listOf(DTO_TYPE_ID), listOf(type), props),
            schema = schema,
            dtoProp = dtoProp,
        )
    }

    private fun immutableProp(
        name: String,
        formulaKind: FormulaKind,
        primaryMapping: PrimaryMapping,
    ): ImmutableProp {
        val id = LsiSymbolId.property(IMMUTABLE_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = name,
            documentation = null,
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = formulaKind,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private data class Fixture(
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val dtoProp: DtoBaseProp,
    )

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Model.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val DTO_TYPE_ID = DtoTypeId("demo#BookView")
    }
}
