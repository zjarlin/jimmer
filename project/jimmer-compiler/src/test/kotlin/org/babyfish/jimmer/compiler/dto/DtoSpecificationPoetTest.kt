package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoSpecificationRenderer
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
import site.addzero.lsi.jimmer.dto.DtoLikeOption
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoSpecificationPoetTest {

    @Test
    fun `renders shared like option arguments for Java and Kotlin`() {
        val graph = specificationGraph()
        val props = graph.props.associateBy(DtoProp::name)
        val like = props.getValue("name") as DtoBaseProp
        val notLike = props.getValue("excludedName") as DtoBaseProp
        val eq = props.getValue("id") as DtoBaseProp

        assertEquals(
            ", true, false, true",
            AptDtoSpecificationRenderer.renderLikeOptionArguments(like, graph).toString(),
        )
        assertEquals(
            ", false, false, false",
            KspDtoSpecificationRenderer.renderLikeOptionArguments(notLike, graph).toString(),
        )
        assertNull(AptDtoSpecificationRenderer.renderLikeOptionArguments(eq, graph))
        assertNull(KspDtoSpecificationRenderer.renderLikeOptionArguments(eq, graph))
    }

    @Test
    fun `renders root entity type contract for Java and Kotlin`() {
        val baseType = entityType()
        val schema = ImmutableSchema(listOf(baseType))
        val dtoType = specification(baseType.id)
        val workspace = workspace(baseType.id)

        val javaMethod = AptDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()
        val kotlinMethod = KspDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()

        assertEquals(
            """
                @java.lang.Override
                public java.lang.Class<demo.Book> entityType() {
                  return demo.Book.class;
                }
            """.trimIndent(),
            javaMethod,
        )
        assertEquals(
            "override fun entityType(): java.lang.Class<demo.Book> = demo.Book::class.java",
            kotlinMethod,
        )
    }

    @Test
    fun `omits override for nested specification fragment`() {
        val baseType = immutableType(
            id = FRAGMENT_TYPE_ID,
            kind = ImmutableTypeKind.EMBEDDABLE,
            props = emptyList(),
        )
        val schema = ImmutableSchema(listOf(baseType))
        val dtoType = specification(baseType.id)
        val workspace = workspace(baseType.id)

        val javaMethod = AptDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()
        val kotlinMethod = KspDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()

        assertEquals(
            """
                public java.lang.Class<demo.Location> entityType() {
                  return demo.Location.class;
                }
            """.trimIndent(),
            javaMethod,
        )
        assertEquals(
            "public fun entityType(): java.lang.Class<demo.Location> = demo.Location::class.java",
            kotlinMethod,
        )
    }

    private fun specification(baseTypeId: LsiSymbolId): DtoType {
        return DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = "SampleSpecification",
            modifiers = setOf(DtoModifier.SPECIFICATION),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun specificationGraph(): DtoGraph {
        val props = listOf(
            specificationProp("id", "eq"),
            specificationProp(
                name = "name",
                functionName = "like",
                likeOptions = setOf(DtoLikeOption.INSENSITIVE, DtoLikeOption.MATCH_END),
            ),
            specificationProp("excludedName", "notLike"),
        )
        val type = specification(ENTITY_TYPE_ID).copy(propIds = props.map(DtoProp::id))
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(type.id),
            types = listOf(type),
            props = props.sortedBy(DtoProp::id),
        )
    }

    private fun specificationProp(
        name: String,
        functionName: String,
        likeOptions: Set<DtoLikeOption> = emptySet(),
    ): DtoBaseProp {
        val id = DtoPropId("${DTO_TYPE_ID.value}#$name")
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = null,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(name, LsiSymbolId.property(ENTITY_TYPE_ID, name)),
            ),
            basePath = name,
            nextPropId = null,
            tailPropId = id,
            baseNullable = true,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = likeOptions,
        )
    }

    private fun entityType(): ImmutableType {
        val idProp = immutableProp(ENTITY_TYPE_ID, "id", PrimaryMapping.ID)
        return immutableType(
            id = ENTITY_TYPE_ID,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(idProp),
            idPropId = idProp.id,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp>,
        idPropId: LsiSymbolId? = null,
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
            instantiable = kind == ImmutableTypeKind.ENTITY,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = idPropId,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        primaryMapping: PrimaryMapping,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            documentation = null,
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
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
            formulaKind = FormulaKind.NONE,
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

    private fun workspace(baseTypeId: LsiSymbolId): LsiWorkspace {
        val simpleName = baseTypeId.requireTypeQualifiedName().substringAfterLast('.')
        return LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = baseTypeId,
                    name = simpleName,
                    qualifiedName = baseTypeId.requireTypeQualifiedName(),
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
                ),
            ),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Sample.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("demo.dto.SampleSpecification")
        val ENTITY_TYPE_ID = LsiSymbolId.type("demo.Book")
        val FRAGMENT_TYPE_ID = LsiSymbolId.type("demo.Location")
    }
}
