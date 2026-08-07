package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicInputRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPolymorphicInputRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
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
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoPolymorphicInputPoetTest {

    @Test
    fun `renders typed discriminator validation exactly`() {
        val fixture = fixture(discriminatorValue = "ORG")

        assertEquals(
            """
                if (!java.util.Objects.equals(this.getKind(), org.babyfish.jimmer.meta.ImmutableType.get(demo.Client.class).getInheritanceInfo().discriminatorValue("ORG"))) {
                  throw new java.lang.IllegalArgumentException("Discriminator value \"" + this.getKind() + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Organization\" whose entity type is \"demo.Organization\"");
                }
            """.trimIndent(),
            fixture.renderJava().trimEnd(),
        )
        assertEquals(
            """
                if (kind != org.babyfish.jimmer.meta.ImmutableType.get(demo.Client::class.java).inheritanceInfo!!.discriminatorValue("ORG")) {
                  throw java.lang.IllegalArgumentException("Discriminator value \"" + kind + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Organization\" whose entity type is \"demo.Organization\"")
                }
            """.trimIndent(),
            fixture.renderKotlin().trimEnd(),
        )
    }

    @Test
    fun `omits typed discriminator validation without entity discriminator value`() {
        val fixture = fixture(discriminatorValue = null)

        assertEquals("", fixture.renderJava())
        assertEquals("", fixture.renderKotlin())
    }

    @Test
    fun `renders default branch entity dispatch exactly`() {
        val fixture = defaultFixture()

        assertEquals(
            """
                if (java.util.Objects.equals(this.getKind(), org.babyfish.jimmer.meta.ImmutableType.get(demo.Client.class).getInheritanceInfo().discriminatorValue("ORG"))) {
                  return demo.OrganizationDraft.$.produce(__draft -> {
                    this.__applyTo(__draft);
                    if (id != null) {
                      __draft.setId(id);
                    }
                  });
                }
                if (java.util.Objects.equals(this.getKind(), org.babyfish.jimmer.meta.ImmutableType.get(demo.Client.class).getInheritanceInfo().discriminatorValue("Person"))) {
                  return demo.PersonDraft.$.produce(__draft -> {
                    this.__applyTo(__draft);
                    if (id != null) {
                      __draft.setId(id);
                    }
                  });
                }
                throw new java.lang.IllegalArgumentException("Illegal discriminator value \"" + this.getKind() + "\" for polymorphic input DTO branch \"demo.dto.ClientInput.Default\"");
            """.trimIndent(),
            fixture.renderDefaultJava().trimEnd(),
        )
        assertEquals(
            """
                if (kind == org.babyfish.jimmer.meta.ImmutableType.get(demo.Client::class.java).inheritanceInfo!!.discriminatorValue("ORG")) {
                  return org.babyfish.jimmer.kt.new(demo.Organization::class).by {
                    toEntityImpl(this)
                    block(this)
                  }
                }
                if (kind == org.babyfish.jimmer.meta.ImmutableType.get(demo.Client::class.java).inheritanceInfo!!.discriminatorValue("Person")) {
                  return org.babyfish.jimmer.kt.new(demo.Person::class).by {
                    toEntityImpl(this)
                    block(this)
                  }
                }
                throw java.lang.IllegalArgumentException("Illegal discriminator value \"" + kind + "\" for polymorphic input DTO branch \"demo.dto.ClientInput.Default\"")
            """.trimIndent(),
            fixture.renderDefaultKotlin(blockParameterName = "block").trimEnd(),
        )
    }

    private fun fixture(discriminatorValue: String?): Fixture {
        val rootId = idProp(CLIENT_TYPE_ID)
        val rootDiscriminator = discriminatorProp(CLIENT_TYPE_ID)
        val branchId = rootId.copy(
            id = ORGANIZATION_ID_PROP_ID,
            ownerTypeId = ORGANIZATION_TYPE_ID,
            inherited = true,
        )
        val branchDiscriminator = rootDiscriminator.copy(
            id = ORGANIZATION_DISCRIMINATOR_PROP_ID,
            ownerTypeId = ORGANIZATION_TYPE_ID,
            inherited = true,
        )
        val rootImmutableType = immutableType(
            typeId = CLIENT_TYPE_ID,
            props = listOf(rootId, rootDiscriminator),
            instantiable = false,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
            discriminatorPropId = rootDiscriminator.id,
        )
        val branchImmutableType = immutableType(
            typeId = ORGANIZATION_TYPE_ID,
            props = listOf(branchId, branchDiscriminator),
            superTypeIds = listOf(CLIENT_TYPE_ID),
            primarySuperTypeId = CLIENT_TYPE_ID,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = branchDiscriminator.id,
        )
        val schema = ImmutableSchema(listOf(rootImmutableType, branchImmutableType))
        val dtoDiscriminator = DtoBaseProp(
            id = DTO_DISCRIMINATOR_PROP_ID,
            ownerTypeId = MERGED_TYPE_ID,
            name = "kind",
            alias = "kind",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("kind", branchDiscriminator.id)),
            basePath = "kind",
            nextPropId = null,
            tailPropId = DTO_DISCRIMINATOR_PROP_ID,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.TYPE,
            targetBaseTypeId = ORGANIZATION_TYPE_ID,
            declaredClassName = "Organization",
            className = "Organization",
            bodyTypeId = BODY_TYPE_ID,
            mergedTypeId = MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val rootDtoType = dtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = CLIENT_TYPE_ID,
            name = "ClientInput",
            polymorphism = DtoPolymorphism(false, listOf(branch)),
        )
        val bodyDtoType = dtoType(
            id = BODY_TYPE_ID,
            baseTypeId = ORGANIZATION_TYPE_ID,
            name = null,
        )
        val mergedDtoType = dtoType(
            id = MERGED_TYPE_ID,
            baseTypeId = ORGANIZATION_TYPE_ID,
            name = null,
            propIds = listOf(dtoDiscriminator.id),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = listOf(rootDtoType, bodyDtoType, mergedDtoType).sortedBy(DtoType::id),
            props = listOf(dtoDiscriminator),
        )
        return Fixture(
            dtoType = mergedDtoType,
            branch = branch,
            discriminatorProp = dtoDiscriminator,
            graph = graph,
            schema = schema,
            generatedSimpleNames = listOf("ClientInput", "Organization"),
        )
    }

    private fun defaultFixture(): Fixture {
        val rootId = idProp(CLIENT_TYPE_ID)
        val rootDiscriminator = discriminatorProp(CLIENT_TYPE_ID)
        val rootImmutableType = immutableType(
            typeId = CLIENT_TYPE_ID,
            props = listOf(rootId, rootDiscriminator),
            instantiable = false,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
            discriminatorPropId = rootDiscriminator.id,
        )
        val organizationType = concreteType(
            typeId = ORGANIZATION_TYPE_ID,
            rootId = rootId,
            rootDiscriminator = rootDiscriminator,
            discriminatorValue = "ORG",
        )
        val personType = concreteType(
            typeId = PERSON_TYPE_ID,
            rootId = rootId,
            rootDiscriminator = rootDiscriminator,
            discriminatorValue = "Person",
        )
        val schema = ImmutableSchema(listOf(rootImmutableType, organizationType, personType))
        val dtoDiscriminator = DtoBaseProp(
            id = DTO_DISCRIMINATOR_PROP_ID,
            ownerTypeId = MERGED_TYPE_ID,
            name = "kind",
            alias = "kind",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("kind", rootDiscriminator.id)),
            basePath = "kind",
            nextPropId = null,
            tailPropId = DTO_DISCRIMINATOR_PROP_ID,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            targetBaseTypeId = null,
            declaredClassName = null,
            className = "Default",
            bodyTypeId = BODY_TYPE_ID,
            mergedTypeId = MERGED_TYPE_ID,
            implicit = false,
            location = LOCATION,
        )
        val rootDtoType = dtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = CLIENT_TYPE_ID,
            name = "ClientInput",
            polymorphism = DtoPolymorphism(false, listOf(branch)),
        )
        val bodyDtoType = dtoType(
            id = BODY_TYPE_ID,
            baseTypeId = CLIENT_TYPE_ID,
            name = null,
        )
        val mergedDtoType = dtoType(
            id = MERGED_TYPE_ID,
            baseTypeId = CLIENT_TYPE_ID,
            name = null,
            propIds = listOf(dtoDiscriminator.id),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = listOf(rootDtoType, bodyDtoType, mergedDtoType).sortedBy(DtoType::id),
            props = listOf(dtoDiscriminator),
        )
        return Fixture(
            dtoType = mergedDtoType,
            branch = branch,
            discriminatorProp = dtoDiscriminator,
            graph = graph,
            schema = schema,
            generatedSimpleNames = listOf("ClientInput", "Default"),
        )
    }

    private fun concreteType(
        typeId: LsiSymbolId,
        rootId: ImmutableProp,
        rootDiscriminator: ImmutableProp,
        discriminatorValue: String,
    ): ImmutableType {
        val id = rootId.copy(
            id = LsiSymbolId.property(typeId, "id"),
            ownerTypeId = typeId,
            inherited = true,
        )
        val discriminator = rootDiscriminator.copy(
            id = LsiSymbolId.property(typeId, "kind"),
            ownerTypeId = typeId,
            inherited = true,
        )
        return immutableType(
            typeId = typeId,
            props = listOf(id, discriminator),
            superTypeIds = listOf(CLIENT_TYPE_ID),
            primarySuperTypeId = CLIENT_TYPE_ID,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminator.id,
        )
    }

    private fun immutableType(
        typeId: LsiSymbolId,
        props: List<ImmutableProp>,
        instantiable: Boolean = true,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: InheritanceStrategy? = null,
        joinedTableDissociateAction: JoinedTableDissociateAction? = null,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = typeId,
            qualifiedName = typeId.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = superTypeIds,
            props = props,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
            idPropId = props.single { prop -> prop.primaryMapping == PrimaryMapping.ID }.id,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun idProp(ownerTypeId: LsiSymbolId): ImmutableProp {
        val propId = LsiSymbolId.property(ownerTypeId, "id")
        return discriminatorProp(ownerTypeId).copy(
            id = propId,
            declarationId = propId,
            name = "id",
            type = LsiDeclaredType(LONG_TYPE_ID),
            overrideChain = listOf(propId),
            primaryMapping = PrimaryMapping.ID,
        )
    }

    private fun discriminatorProp(ownerTypeId: LsiSymbolId): ImmutableProp {
        val propId = LsiSymbolId.property(ownerTypeId, "kind")
        return ImmutableProp(
            id = propId,
            declarationId = propId,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = "kind",
            documentation = null,
            type = LsiDeclaredType(STRING_TYPE_ID),
            annotations = emptyList(),
            overrideChain = listOf(propId),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.DISCRIMINATOR,
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

    private fun dtoType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String?,
        propIds: List<DtoPropId> = emptyList(),
        polymorphism: DtoPolymorphism? = null,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = name,
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = propIds,
            hiddenFlatPropIds = emptyList(),
            polymorphism = polymorphism,
        )
    }

    private data class Fixture(
        val dtoType: DtoType,
        val branch: DtoPolymorphicBranch,
        val discriminatorProp: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val generatedSimpleNames: List<String>,
    ) {
        fun renderJava(): String {
            return AptDtoPolymorphicInputRenderer.renderTypedDiscriminatorValidation(
                dtoType = dtoType,
                branch = branch,
                discriminatorProp = discriminatorProp,
                graph = graph,
                immutableSchema = schema,
                workspace = WORKSPACE,
                generatedPackageName = "demo.dto",
                generatedSimpleNames = generatedSimpleNames,
            ).toString()
        }

        fun renderKotlin(): String {
            return KspDtoPolymorphicInputRenderer.renderTypedDiscriminatorValidation(
                dtoType = dtoType,
                branch = branch,
                discriminatorProp = discriminatorProp,
                graph = graph,
                immutableSchema = schema,
                workspace = WORKSPACE,
                generatedPackageName = "demo.dto",
                generatedSimpleNames = generatedSimpleNames,
            ).toString()
        }

        fun renderDefaultJava(): String {
            return AptDtoPolymorphicInputRenderer.renderDefaultBranchBody(
                dtoType = dtoType,
                branch = branch,
                discriminatorProp = discriminatorProp,
                graph = graph,
                immutableSchema = schema,
                workspace = WORKSPACE,
                generatedPackageName = "demo.dto",
                generatedSimpleNames = generatedSimpleNames,
                idParameterName = "id",
            ).toString()
        }

        fun renderDefaultKotlin(blockParameterName: String?): String {
            return KspDtoPolymorphicInputRenderer.renderDefaultBranchBody(
                dtoType = dtoType,
                branch = branch,
                discriminatorProp = discriminatorProp,
                graph = graph,
                immutableSchema = schema,
                workspace = WORKSPACE,
                generatedPackageName = "demo.dto",
                generatedSimpleNames = generatedSimpleNames,
                blockParameterName = blockParameterName,
            ).toString()
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Client.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val CLIENT_TYPE_ID = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_TYPE_ID = LsiSymbolId.type("demo.Organization")
        val PERSON_TYPE_ID = LsiSymbolId.type("demo.Person")
        val ORGANIZATION_ID_PROP_ID = LsiSymbolId.property(ORGANIZATION_TYPE_ID, "id")
        val ORGANIZATION_DISCRIMINATOR_PROP_ID = LsiSymbolId.property(ORGANIZATION_TYPE_ID, "kind")
        val LONG_TYPE_ID = LsiSymbolId.type("java.lang.Long")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val BODY_TYPE_ID = DtoTypeId("dto#body")
        val MERGED_TYPE_ID = DtoTypeId("dto#merged")
        val DTO_DISCRIMINATOR_PROP_ID = DtoPropId("dto#merged/prop:kind")
        val WORKSPACE = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = CLIENT_TYPE_ID,
                    name = "Client",
                    qualifiedName = "demo.Client",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, SOURCE),
                )
            ),
        )
    }
}
