package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType

class DtoJacksonPolymorphismExtensionsTest {

    @Test
    fun `creates complete root annotations from frozen polymorphic semantics`() {
        val fixture = fixture(selectDiscriminator = true)
        val contract = annotationContract(fixture.graph)

        assertEquals(
            listOf(
                expectedTypeInfoAnnotation(
                    includeEntryName = "EXISTING_PROPERTY",
                    propertyName = "kind",
                    visible = true,
                ),
                expectedSubTypesAnnotation(),
            ),
            fixture.root.generatedJacksonPolymorphicRootAnnotations(
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = contract,
                generatedRootTypeId = GENERATED_ROOT_TYPE_ID,
            ),
        )

        val inheritedDiscriminatorFixture = fixture(selectDiscriminator = false)
        assertEquals(
            listOf(
                expectedTypeInfoAnnotation(
                    includeEntryName = "PROPERTY",
                    propertyName = "type",
                    visible = false,
                ),
                expectedSubTypesAnnotation(),
            ),
            inheritedDiscriminatorFixture.root.generatedJacksonPolymorphicRootAnnotations(
                graph = inheritedDiscriminatorFixture.graph,
                immutableSchema = inheritedDiscriminatorFixture.schema,
                annotationContract = annotationContract(inheritedDiscriminatorFixture.graph),
                generatedRootTypeId = GENERATED_ROOT_TYPE_ID,
            ),
        )
    }

    @Test
    fun `applies root annotation overrides independently`() {
        val fixture = fixture(selectDiscriminator = true)
        val typeInfoContract = annotationContract(
            fixture.graph,
            mapOf(ROOT_TYPE_ID to setOf(JSON_TYPE_INFO_TYPE_ID)),
        )

        assertEquals(
            listOf(expectedSubTypesAnnotation()),
            fixture.root.generatedJacksonPolymorphicRootAnnotations(
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = typeInfoContract,
                generatedRootTypeId = GENERATED_ROOT_TYPE_ID,
            ),
        )

        val subTypesContract = annotationContract(
            fixture.graph,
            mapOf(ROOT_TYPE_ID to setOf(JSON_SUB_TYPES_TYPE_ID)),
        )
        assertEquals(
            listOf(
                expectedTypeInfoAnnotation(
                    includeEntryName = "EXISTING_PROPERTY",
                    propertyName = "kind",
                    visible = true,
                )
            ),
            fixture.root.generatedJacksonPolymorphicRootAnnotations(
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = subTypesContract,
                generatedRootTypeId = GENERATED_ROOT_TYPE_ID,
            ),
        )
        assertNull(
            fixture.organizationBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
                rootType = fixture.root,
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = subTypesContract,
            ),
        )
    }

    @Test
    fun `resolves type names and honors branch annotation override`() {
        val fixture = fixture(selectDiscriminator = true)
        val contract = annotationContract(fixture.graph)

        assertNull(
            fixture.defaultBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
                rootType = fixture.root,
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = contract,
            ),
        )
        assertEquals(
            expectedTypeNameAnnotation("ORG"),
            fixture.organizationBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
                rootType = fixture.root,
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = contract,
            ),
        )

        val overriddenContract = annotationContract(
            fixture.graph,
            mapOf(ORGANIZATION_MERGED_TYPE_ID to setOf(JSON_TYPE_NAME_TYPE_ID)),
        )
        assertNull(
            fixture.organizationBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
                rootType = fixture.root,
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = overriddenContract,
            ),
        )
        assertEquals(
            expectedTypeNameAnnotation("PERSON"),
            fixture.personBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
                rootType = fixture.root,
                graph = fixture.graph,
                immutableSchema = fixture.schema,
                annotationContract = overriddenContract,
            ),
        )
    }

    private fun expectedTypeInfoAnnotation(
        includeEntryName: String,
        propertyName: String,
        visible: Boolean,
    ): LsiAnnotation {
        val arguments = mutableListOf(
            "use" to LsiAnnotationValue.EnumValue(JSON_TYPE_INFO_ID_TYPE_ID, "NAME"),
            "include" to LsiAnnotationValue.EnumValue(JSON_TYPE_INFO_AS_TYPE_ID, includeEntryName),
            "property" to LsiAnnotationValue.StringValue(propertyName),
        )
        if (visible) {
            arguments += "visible" to LsiAnnotationValue.BooleanValue(true)
        }
        arguments += "defaultImpl" to LsiAnnotationValue.ClassValue(
            LsiDeclaredType(GENERATED_DEFAULT_TYPE_ID)
        )
        return expectedAnnotation(JSON_TYPE_INFO_TYPE_ID, arguments)
    }

    private fun expectedSubTypesAnnotation(): LsiAnnotation {
        val branches = listOf(GENERATED_ORGANIZATION_TYPE_ID, GENERATED_PERSON_TYPE_ID).map { typeId ->
            LsiAnnotationValue.NestedAnnotationValue(
                expectedAnnotation(
                    JSON_SUB_TYPES_TYPE_TYPE_ID,
                    listOf(
                        "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(typeId)),
                    ),
                )
            )
        }
        return expectedAnnotation(
            JSON_SUB_TYPES_TYPE_ID,
            listOf(
                "value" to LsiAnnotationValue.ArrayValue(branches),
            ),
        )
    }

    private fun expectedTypeNameAnnotation(value: String): LsiAnnotation {
        return expectedAnnotation(
            JSON_TYPE_NAME_TYPE_ID,
            listOf("value" to LsiAnnotationValue.StringValue(value)),
        )
    }

    private fun expectedAnnotation(
        type: LsiSymbolId,
        arguments: List<Pair<String, LsiAnnotationValue>>,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = arguments.associateTo(linkedMapOf()) { (name, value) ->
                name to LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
            explicitArgumentNamesInSourceOrder = arguments.map { (name, _) -> name },
        )
    }

    private fun fixture(selectDiscriminator: Boolean): Fixture {
        val defaultBranch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
            targetBaseTypeId = null,
            bodyTypeId = DEFAULT_BODY_TYPE_ID,
            mergedTypeId = DEFAULT_MERGED_TYPE_ID,
        )
        val organizationBranch = branch(
            kind = DtoPolymorphicBranchKind.TYPE,
            className = "Organization",
            targetBaseTypeId = ORGANIZATION_TYPE_ID,
            bodyTypeId = ORGANIZATION_BODY_TYPE_ID,
            mergedTypeId = ORGANIZATION_MERGED_TYPE_ID,
        )
        val personBranch = branch(
            kind = DtoPolymorphicBranchKind.TYPE,
            className = "Person",
            targetBaseTypeId = PERSON_TYPE_ID,
            bodyTypeId = PERSON_BODY_TYPE_ID,
            mergedTypeId = PERSON_MERGED_TYPE_ID,
        )
        val discriminatorProp = dtoDiscriminatorProp()
        val root = dtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = CLIENT_TYPE_ID,
            name = "ClientInput",
            propIds = if (selectDiscriminator) listOf(discriminatorProp.id) else emptyList(),
            polymorphism = DtoPolymorphism(
                exhaustive = false,
                branches = listOf(defaultBranch, organizationBranch, personBranch),
            ),
        )
        val types = listOf(
            root,
            dtoType(DEFAULT_BODY_TYPE_ID, CLIENT_TYPE_ID),
            dtoType(DEFAULT_MERGED_TYPE_ID, CLIENT_TYPE_ID),
            dtoType(ORGANIZATION_BODY_TYPE_ID, ORGANIZATION_TYPE_ID),
            dtoType(ORGANIZATION_MERGED_TYPE_ID, ORGANIZATION_TYPE_ID),
            dtoType(PERSON_BODY_TYPE_ID, PERSON_TYPE_ID),
            dtoType(PERSON_MERGED_TYPE_ID, PERSON_TYPE_ID),
        ).sortedBy(DtoType::id)
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_TYPE_ID),
            types = types,
            props = if (selectDiscriminator) listOf(discriminatorProp) else emptyList(),
        )
        return Fixture(
            graph = graph,
            schema = immutableSchema(),
            root = root,
            defaultBranch = defaultBranch,
            organizationBranch = organizationBranch,
            personBranch = personBranch,
        )
    }

    private fun immutableSchema(): ImmutableSchema {
        val rootId = immutableProp(CLIENT_TYPE_ID, "id", PrimaryMapping.ID)
        val rootDiscriminator = immutableProp(CLIENT_TYPE_ID, "type", PrimaryMapping.DISCRIMINATOR)
        val root = immutableType(
            id = CLIENT_TYPE_ID,
            props = listOf(rootId, rootDiscriminator),
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            idPropId = rootId.id,
            discriminatorPropId = rootDiscriminator.id,
        )
        val organization = subtype(
            ORGANIZATION_TYPE_ID,
            "ORG",
        )
        val person = subtype(
            PERSON_TYPE_ID,
            "PERSON",
        )
        return ImmutableSchema(listOf(root, organization, person).sortedBy(ImmutableType::id))
    }

    private fun subtype(typeId: LsiSymbolId, discriminatorValue: String): ImmutableType {
        val id = immutableProp(typeId, "id", PrimaryMapping.ID)
        val discriminator = immutableProp(typeId, "type", PrimaryMapping.DISCRIMINATOR)
        return immutableType(
            id = typeId,
            props = listOf(id, discriminator),
            superTypeIds = listOf(CLIENT_TYPE_ID),
            primarySuperTypeId = CLIENT_TYPE_ID,
            inheritanceRootTypeId = CLIENT_TYPE_ID,
            discriminatorValue = discriminatorValue,
            idPropId = id.id,
            discriminatorPropId = discriminator.id,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId,
        inheritanceStrategy: InheritanceStrategy? = null,
        discriminatorValue: String? = null,
        idPropId: LsiSymbolId,
        discriminatorPropId: LsiSymbolId,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = superTypeIds,
            props = props,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = inheritanceStrategy?.let {
                JoinedTableDissociateAction.DELETE
            },
            instantiable = true,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
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
            type = STRING_TYPE,
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

    private fun dtoDiscriminatorProp(): DtoBaseProp {
        return DtoBaseProp(
            id = DISCRIMINATOR_PROP_ID,
            ownerTypeId = ROOT_TYPE_ID,
            name = "kind",
            alias = "kind",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding("type", LsiSymbolId.property(CLIENT_TYPE_ID, "type")),
            ),
            basePath = "type",
            nextPropId = null,
            tailPropId = DISCRIMINATOR_PROP_ID,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            targetTypeReference = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun dtoType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String? = null,
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

    private fun branch(
        kind: DtoPolymorphicBranchKind,
        className: String,
        targetBaseTypeId: LsiSymbolId?,
        bodyTypeId: DtoTypeId,
        mergedTypeId: DtoTypeId,
    ): DtoPolymorphicBranch {
        return DtoPolymorphicBranch(
            kind = kind,
            targetBaseTypeId = targetBaseTypeId,
            declaredClassName = null,
            className = className,
            bodyTypeId = bodyTypeId,
            mergedTypeId = mergedTypeId,
            implicit = false,
            location = LOCATION,
        )
    }

    private fun annotationContract(
        graph: DtoGraph,
        annotationTypeIdsByDtoTypeId: Map<DtoTypeId, Set<LsiSymbolId>> = emptyMap(),
    ): DtoAnnotationContract {
        val annotationTypeIds = annotationTypeIdsByDtoTypeId.values.flatten().distinct().sorted()
        return DtoAnnotationContract(
            declarations = annotationTypeIds.map { typeId ->
                DtoAnnotationDeclaration(
                    typeId = typeId,
                    kind = DtoAnnotationDeclarationKind.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(DtoAnnotationPlacement.TYPE),
                    argumentTypes = emptyMap(),
                    kotlinValueVararg = false,
                )
            },
            typePlans = graph.types.map { type ->
                DtoTypeAnnotationPlan(
                    typeId = type.id,
                    applications = annotationTypeIdsByDtoTypeId[type.id]
                        .orEmpty()
                        .sorted()
                        .map { annotationTypeId ->
                            DtoAnnotationApplication(
                                annotation = LsiAnnotation(annotationTypeId),
                                origin = DtoAnnotationOrigin.DTO,
                                sourceSymbolId = null,
                                placements = listOf(DtoAnnotationPlacement.TYPE),
                            )
                        },
                )
            },
            propPlans = graph.props.map { prop ->
                DtoPropAnnotationPlan(prop.id, emptyList(), emptyList())
            },
            diagnostics = emptyList(),
        )
    }

    private data class Fixture(
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val root: DtoType,
        val defaultBranch: DtoPolymorphicBranch,
        val organizationBranch: DtoPolymorphicBranch,
        val personBranch: DtoPolymorphicBranch,
    )

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Client.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val CLIENT_TYPE_ID = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_TYPE_ID = LsiSymbolId.type("demo.Organization")
        val PERSON_TYPE_ID = LsiSymbolId.type("demo.Person")
        val JSON_TYPE_INFO_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonTypeInfo")
        val JSON_TYPE_INFO_ID_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonTypeInfo.Id")
        val JSON_TYPE_INFO_AS_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonTypeInfo.As")
        val JSON_SUB_TYPES_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonSubTypes")
        val JSON_SUB_TYPES_TYPE_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonSubTypes.Type")
        val JSON_TYPE_NAME_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonTypeName")
        val GENERATED_ROOT_TYPE_ID = LsiSymbolId.type("demo.dto.ClientInput")
        val GENERATED_DEFAULT_TYPE_ID = LsiSymbolId.type("demo.dto.ClientInput.Default")
        val GENERATED_ORGANIZATION_TYPE_ID = LsiSymbolId.type("demo.dto.ClientInput.Organization")
        val GENERATED_PERSON_TYPE_ID = LsiSymbolId.type("demo.dto.ClientInput.Person")
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val DEFAULT_BODY_TYPE_ID = DtoTypeId("dto#default-body")
        val DEFAULT_MERGED_TYPE_ID = DtoTypeId("dto#default-merged")
        val ORGANIZATION_BODY_TYPE_ID = DtoTypeId("dto#organization-body")
        val ORGANIZATION_MERGED_TYPE_ID = DtoTypeId("dto#organization-merged")
        val PERSON_BODY_TYPE_ID = DtoTypeId("dto#person-body")
        val PERSON_MERGED_TYPE_ID = DtoTypeId("dto#person-merged")
        val DISCRIMINATOR_PROP_ID = DtoPropId("dto#discriminator")
    }
}
