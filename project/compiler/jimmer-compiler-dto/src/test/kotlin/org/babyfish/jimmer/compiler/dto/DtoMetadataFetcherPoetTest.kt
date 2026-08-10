package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
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
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoFetchType
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropConfig
import site.addzero.lsi.jimmer.dto.DtoPropId
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeFragment
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoMetadataFetcherPoetTest {

    @Test
    fun `lowers complete apt metadata fetcher byte for byte`() {
        assertEquals(JAVA_EXPECTED, fixture().render(LsiLanguage.JAVA))
    }

    @Test
    fun `lowers complete ksp metadata fetcher byte for byte`() {
        assertEquals(KOTLIN_EXPECTED, fixture().render(LsiLanguage.KOTLIN))
    }

    @Test
    fun `declares by import only for kotlin metadata fetcher`() {
        val fixture = fixture()

        assertEquals(emptyList(), fixture.fragment(LsiLanguage.JAVA).imports)
        assertEquals(
            listOf(LsiImport("demo", "by")),
            fixture.fragment(LsiLanguage.KOTLIN).imports,
        )
    }

    private fun fixture(): Fixture {
        val rootId = DtoTypeId("demo/Book.dto#root:BookView")
        val storeTargetId = DtoTypeId("demo/Book.dto#target:store")
        val editorTargetId = DtoTypeId("demo/Book.dto#target:editor")
        val hiddenPublisherTargetId = DtoTypeId("demo/Book.dto#hidden:publisher")
        val firstFoldTargetId = DtoTypeId("demo/Book.dto#fold:first")
        val firstFoldStoreTargetId = DtoTypeId("demo/Book.dto#fold:first:target:store")
        val secondFoldTargetId = DtoTypeId("demo/Book.dto#fold:second")
        val secondFoldStoreTargetId = DtoTypeId("demo/Book.dto#fold:second:target:store")
        val secondFoldHiddenPublisherTargetId = DtoTypeId("demo/Book.dto#fold:second:hidden:publisher")
        val branchBodyId = DtoTypeId("demo/Book.dto#branch:special:body")
        val branchMergedId = DtoTypeId("demo/Book.dto#branch:special:merged")
        val branchStoreTargetId = DtoTypeId("demo/Book.dto#branch:special:target:store")

        val idProp = dtoBaseProp(
            id = dtoPropId(rootId, "00:id"),
            ownerTypeId = rootId,
            name = "id",
            baseName = "id",
            immutablePropId = BOOK_ID_PROP_ID,
        )
        val displayNameProp = dtoBaseProp(
            id = dtoPropId(rootId, "01:displayName"),
            ownerTypeId = rootId,
            name = "displayName",
            baseName = "name",
            immutablePropId = BOOK_NAME_PROP_ID,
            alias = "displayName",
        )
        val editionProp = dtoBaseProp(
            id = dtoPropId(rootId, "02:edition"),
            ownerTypeId = rootId,
            name = "edition",
            baseName = "edition",
            immutablePropId = BOOK_EDITION_PROP_ID,
        )
        val storeProp = dtoBaseProp(
            id = dtoPropId(rootId, "03:store"),
            ownerTypeId = rootId,
            name = "store",
            baseName = "store",
            immutablePropId = BOOK_STORE_PROP_ID,
            targetTypeId = storeTargetId,
        )
        val parentProp = dtoBaseProp(
            id = dtoPropId(rootId, "04:parent"),
            ownerTypeId = rootId,
            name = "parent",
            baseName = "parent",
            immutablePropId = BOOK_PARENT_PROP_ID,
            targetTypeId = rootId,
            recursive = true,
        )
        val editorProp = dtoBaseProp(
            id = dtoPropId(rootId, "05:editor"),
            ownerTypeId = rootId,
            name = "editor",
            baseName = "editor",
            immutablePropId = BOOK_EDITOR_PROP_ID,
            targetTypeId = editorTargetId,
            config = DtoPropConfig(
                predicate = null,
                orderItems = emptyList(),
                filter = null,
                recursion = null,
                fetchType = DtoFetchType.AUTO,
                limit = null,
                batch = 5,
                depth = null,
            ),
        )
        val reusableStoreProp = dtoBaseProp(
            id = dtoPropId(rootId, "06:reusableStore"),
            ownerTypeId = rootId,
            name = "reusableStore",
            baseName = "reusableStore",
            immutablePropId = BOOK_REUSABLE_STORE_PROP_ID,
            targetTypeReference = reusableTypeReference(
                qualifiedName = "demo.dto.StoreView",
                targetBaseTypeId = STORE_TYPE_ID,
            ),
        )
        val externalStoreProp = dtoBaseProp(
            id = dtoPropId(rootId, "07:externalStore"),
            ownerTypeId = rootId,
            name = "externalStore",
            baseName = "externalStore",
            immutablePropId = BOOK_EXTERNAL_STORE_PROP_ID,
            targetTypeReference = reusableTypeReference(
                qualifiedName = "contract.ExternalStoreView",
                targetBaseTypeId = STORE_TYPE_ID,
            ),
        )
        val hiddenPublisherProp = dtoBaseProp(
            id = dtoPropId(rootId, "hidden:publisher"),
            ownerTypeId = rootId,
            name = "publisher",
            baseName = "publisher",
            immutablePropId = BOOK_PUBLISHER_PROP_ID,
            targetTypeId = hiddenPublisherTargetId,
            functionName = "flat",
        )
        val firstFoldProp = dtoFoldProp(
            id = dtoPropId(rootId, "06:firstSummary"),
            ownerTypeId = rootId,
            name = "firstSummary",
            targetTypeId = firstFoldTargetId,
        )
        val secondFoldProp = dtoFoldProp(
            id = dtoPropId(rootId, "07:secondSummary"),
            ownerTypeId = rootId,
            name = "secondSummary",
            targetTypeId = secondFoldTargetId,
        )

        val hiddenPublisherNameProp = dtoBaseProp(
            id = dtoPropId(hiddenPublisherTargetId, "00:name"),
            ownerTypeId = hiddenPublisherTargetId,
            name = "name",
            baseName = "name",
            immutablePropId = PUBLISHER_NAME_PROP_ID,
        )
        val hiddenPublisherStoreProp = dtoBaseProp(
            id = dtoPropId(hiddenPublisherTargetId, "01:store"),
            ownerTypeId = hiddenPublisherTargetId,
            name = "store",
            baseName = "store",
            immutablePropId = PUBLISHER_STORE_PROP_ID,
            targetTypeId = storeTargetId,
        )
        val firstFoldNameProp = dtoBaseProp(
            id = dtoPropId(firstFoldTargetId, "00:name"),
            ownerTypeId = firstFoldTargetId,
            name = "firstFoldName",
            baseName = "name",
            immutablePropId = BOOK_NAME_PROP_ID,
            alias = "firstFoldName",
        )
        val firstFoldStoreProp = dtoBaseProp(
            id = dtoPropId(firstFoldTargetId, "01:firstFoldStore"),
            ownerTypeId = firstFoldTargetId,
            name = "firstFoldStore",
            baseName = "store",
            immutablePropId = BOOK_STORE_PROP_ID,
            alias = "firstFoldStore",
            targetTypeId = firstFoldStoreTargetId,
        )
        val secondFoldNameProp = dtoBaseProp(
            id = dtoPropId(secondFoldTargetId, "00:name"),
            ownerTypeId = secondFoldTargetId,
            name = "secondFoldName",
            baseName = "name",
            immutablePropId = BOOK_NAME_PROP_ID,
            alias = "secondFoldName",
        )
        val secondFoldStoreProp = dtoBaseProp(
            id = dtoPropId(secondFoldTargetId, "01:secondFoldStore"),
            ownerTypeId = secondFoldTargetId,
            name = "secondFoldStore",
            baseName = "store",
            immutablePropId = BOOK_STORE_PROP_ID,
            alias = "secondFoldStore",
            targetTypeId = secondFoldStoreTargetId,
        )
        val secondFoldHiddenPublisherProp = dtoBaseProp(
            id = dtoPropId(secondFoldTargetId, "hidden:publisher"),
            ownerTypeId = secondFoldTargetId,
            name = "publisher",
            baseName = "publisher",
            immutablePropId = BOOK_PUBLISHER_PROP_ID,
            targetTypeId = secondFoldHiddenPublisherTargetId,
            functionName = "flat",
        )
        val secondFoldHiddenPublisherStoreProp = dtoBaseProp(
            id = dtoPropId(secondFoldHiddenPublisherTargetId, "00:store"),
            ownerTypeId = secondFoldHiddenPublisherTargetId,
            name = "store",
            baseName = "store",
            immutablePropId = PUBLISHER_STORE_PROP_ID,
            targetTypeId = secondFoldStoreTargetId,
        )

        val branchStoreProp = dtoBaseProp(
            id = dtoPropId(branchBodyId, "00:specialStore"),
            ownerTypeId = branchBodyId,
            name = "specialStore",
            baseName = "specialStore",
            immutablePropId = SPECIAL_BOOK_STORE_PROP_ID,
            targetTypeId = branchStoreTargetId,
        )
        val mergedBranchStoreProp = dtoBaseProp(
            id = dtoPropId(branchMergedId, "00:specialStore"),
            ownerTypeId = branchMergedId,
            name = "specialStore",
            baseName = "specialStore",
            immutablePropId = SPECIAL_BOOK_STORE_PROP_ID,
            targetTypeId = branchStoreTargetId,
        )
        val branch = DtoPolymorphicBranch(
            kind = DtoPolymorphicBranchKind.TYPE,
            targetBaseTypeId = SPECIAL_BOOK_TYPE_ID,
            declaredClassName = "TargetOf_specialStore",
            className = "TargetOf_specialStore",
            bodyTypeId = branchBodyId,
            mergedTypeId = branchMergedId,
            implicit = false,
            location = LOCATION,
        )
        val rootType = dtoType(
            id = rootId,
            baseTypeId = BOOK_TYPE_ID,
            name = "BookView",
            propIds = listOf(
                idProp.id,
                displayNameProp.id,
                editionProp.id,
                storeProp.id,
                parentProp.id,
                editorProp.id,
                reusableStoreProp.id,
                externalStoreProp.id,
                firstFoldProp.id,
                secondFoldProp.id,
            ),
            hiddenFlatPropIds = listOf(hiddenPublisherProp.id),
            polymorphism = DtoPolymorphism(
                exhaustive = true,
                branches = listOf(branch),
            ),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(rootId),
            types = listOf(
                rootType,
                dtoType(storeTargetId, STORE_TYPE_ID),
                dtoType(editorTargetId, STORE_TYPE_ID),
                dtoType(
                    id = hiddenPublisherTargetId,
                    baseTypeId = PUBLISHER_TYPE_ID,
                    propIds = listOf(hiddenPublisherNameProp.id, hiddenPublisherStoreProp.id),
                ),
                dtoType(
                    id = firstFoldTargetId,
                    baseTypeId = BOOK_TYPE_ID,
                    propIds = listOf(firstFoldNameProp.id, firstFoldStoreProp.id),
                ),
                dtoType(firstFoldStoreTargetId, STORE_TYPE_ID),
                dtoType(
                    id = secondFoldTargetId,
                    baseTypeId = BOOK_TYPE_ID,
                    propIds = listOf(secondFoldNameProp.id, secondFoldStoreProp.id),
                    hiddenFlatPropIds = listOf(secondFoldHiddenPublisherProp.id),
                ),
                dtoType(secondFoldStoreTargetId, STORE_TYPE_ID),
                dtoType(
                    id = secondFoldHiddenPublisherTargetId,
                    baseTypeId = PUBLISHER_TYPE_ID,
                    propIds = listOf(secondFoldHiddenPublisherStoreProp.id),
                ),
                dtoType(
                    id = branchBodyId,
                    baseTypeId = SPECIAL_BOOK_TYPE_ID,
                    propIds = listOf(branchStoreProp.id),
                ),
                dtoType(
                    id = branchMergedId,
                    baseTypeId = SPECIAL_BOOK_TYPE_ID,
                    propIds = listOf(mergedBranchStoreProp.id),
                ),
                dtoType(branchStoreTargetId, STORE_TYPE_ID),
            ).sortedBy(DtoType::id),
            props = listOf(
                idProp,
                displayNameProp,
                editionProp,
                storeProp,
                parentProp,
                editorProp,
                reusableStoreProp,
                externalStoreProp,
                hiddenPublisherProp,
                firstFoldProp,
                secondFoldProp,
                hiddenPublisherNameProp,
                hiddenPublisherStoreProp,
                firstFoldNameProp,
                firstFoldStoreProp,
                secondFoldNameProp,
                secondFoldStoreProp,
                secondFoldHiddenPublisherProp,
                secondFoldHiddenPublisherStoreProp,
                branchStoreProp,
                mergedBranchStoreProp,
            ).sortedBy(DtoProp::id),
        )
        val batchRootTypeNames = JimmerDtoPoetTypeNames.roots(listOf(graph)) + mapOf(
            REUSABLE_STORE_VIEW_DTO_TYPE_ID to LsiTypeName(
                typeId = REUSABLE_STORE_VIEW_TYPE_ID,
                packageName = "demo.dto",
                simpleNames = listOf("StoreView"),
            ),
        )
        val generatedDtoTypeName = batchRootTypeNames.getValue(rootId)
        val generatedDtoTypeIdsByTypeName = JimmerDtoPoetTypeNames.forRoot(
            graph = graph,
            rootType = rootType,
            batchRootTypeNames = batchRootTypeNames,
        )
        return Fixture(
            rootType = rootType,
            graph = graph,
            immutableSchema = immutableSchema(),
            workspace = workspace(),
            generatedDtoTypeName = generatedDtoTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames = batchRootTypeNames,
        )
    }

    private fun immutableSchema(): ImmutableSchema {
        val bookProps = listOf(
            immutableProp(BOOK_TYPE_ID, "id", PrimaryMapping.ID),
            immutableProp(BOOK_TYPE_ID, "name", PrimaryMapping.SCALAR),
            immutableProp(BOOK_TYPE_ID, "edition", PrimaryMapping.SCALAR),
            immutableProp(BOOK_TYPE_ID, "store", targetTypeId = STORE_TYPE_ID),
            immutableProp(BOOK_TYPE_ID, "parent", targetTypeId = BOOK_TYPE_ID, recursive = true),
            immutableProp(BOOK_TYPE_ID, "editor", targetTypeId = STORE_TYPE_ID),
            immutableProp(BOOK_TYPE_ID, "reusableStore", targetTypeId = STORE_TYPE_ID),
            immutableProp(BOOK_TYPE_ID, "externalStore", targetTypeId = STORE_TYPE_ID),
            immutableProp(BOOK_TYPE_ID, "publisher", targetTypeId = PUBLISHER_TYPE_ID),
        )
        val specialBookProps = listOf(
            immutableProp(SPECIAL_BOOK_TYPE_ID, "id", PrimaryMapping.ID),
            immutableProp(SPECIAL_BOOK_TYPE_ID, "specialStore", targetTypeId = STORE_TYPE_ID),
        )
        val storeProps = listOf(
            immutableProp(STORE_TYPE_ID, "id", PrimaryMapping.ID),
            immutableProp(STORE_TYPE_ID, "name", PrimaryMapping.SCALAR),
        )
        val publisherProps = listOf(
            immutableProp(PUBLISHER_TYPE_ID, "id", PrimaryMapping.ID),
            immutableProp(PUBLISHER_TYPE_ID, "name", PrimaryMapping.SCALAR),
            immutableProp(PUBLISHER_TYPE_ID, "store", targetTypeId = STORE_TYPE_ID),
        )
        return ImmutableSchema(
            listOf(
                immutableType(BOOK_TYPE_ID, bookProps),
                immutableType(
                    id = SPECIAL_BOOK_TYPE_ID,
                    props = specialBookProps,
                    superTypeIds = listOf(BOOK_TYPE_ID),
                ),
                immutableType(STORE_TYPE_ID, storeProps),
                immutableType(PUBLISHER_TYPE_ID, publisherProps),
            )
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        superTypeIds: List<LsiSymbolId> = emptyList(),
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
            primarySuperTypeId = superTypeIds.singleOrNull(),
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = true,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = props.single { prop -> prop.primaryMapping == PrimaryMapping.ID }.id,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        primaryMapping: PrimaryMapping = PrimaryMapping.ASSOCIATION,
        targetTypeId: LsiSymbolId? = null,
        recursive: Boolean = false,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        val association = targetTypeId != null
        val type: LsiType = when {
            targetTypeId != null -> LsiDeclaredType(targetTypeId)
            primaryMapping == PrimaryMapping.ID -> LsiPrimitiveType(LsiPrimitiveKind.LONG)
            else -> LsiDeclaredType(STRING_TYPE_ID)
        }
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(id),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = association,
            embedded = false,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
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
            recursive = recursive,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun dtoType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String? = null,
        propIds: List<DtoPropId> = emptyList(),
        hiddenFlatPropIds: List<DtoPropId> = emptyList(),
        polymorphism: DtoPolymorphism? = null,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = name,
            modifiers = emptySet(),
            annotations = emptyList<DtoAnnotation>(),
            superInterfaces = emptyList<DtoTypeRef>(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = propIds,
            hiddenFlatPropIds = hiddenFlatPropIds,
            polymorphism = polymorphism,
        )
    }

    private fun dtoBaseProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        name: String,
        baseName: String,
        immutablePropId: LsiSymbolId,
        alias: String? = null,
        targetTypeId: DtoTypeId? = null,
        functionName: String? = null,
        config: DtoPropConfig? = null,
        recursive: Boolean = false,
        targetTypeReference: DtoReusableTypeReference? = null,
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = alias,
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding(baseName, immutablePropId)),
            basePath = baseName,
            nextPropId = null,
            tailPropId = id,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = targetTypeId,
            targetTypeReference = targetTypeReference,
            enumType = null,
            config = config,
            recursive = recursive,
            likeOptions = emptySet(),
        )
    }

    private fun reusableTypeReference(
        qualifiedName: String,
        targetBaseTypeId: LsiSymbolId,
    ): DtoReusableTypeReference {
        return DtoReusableTypeReference(
            qualifiedName = qualifiedName,
            targetBaseTypeId = targetBaseTypeId,
            kind = DtoTypeKind.VIEW,
            location = LOCATION,
        )
    }

    private fun dtoFoldProp(
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

    private fun dtoPropId(ownerTypeId: DtoTypeId, suffix: String): DtoPropId {
        return DtoPropId("${ownerTypeId.value}#prop:$suffix")
    }

    private fun workspace(): LsiWorkspace {
        return LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                typeDeclaration(BOOK_TYPE_ID),
                typeDeclaration(SPECIAL_BOOK_TYPE_ID),
                typeDeclaration(BOOK_FETCHER_TYPE_ID),
                typeDeclaration(SPECIAL_BOOK_FETCHER_TYPE_ID),
                typeDeclaration(PUBLISHER_FETCHER_TYPE_ID),
                typeDeclaration(EXTERNAL_STORE_VIEW_TYPE_ID),
            ),
        )
    }

    private fun typeDeclaration(id: LsiSymbolId): LsiClass {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiClass(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            abstractDeclaration = true,
            modality = LsiModality.ABSTRACT,
            origin = LsiOrigin(LsiOriginKind.SOURCE, SOURCE),
        )
    }

    private data class Fixture(
        val rootType: DtoType,
        val graph: DtoGraph,
        val immutableSchema: ImmutableSchema,
        val workspace: LsiWorkspace,
        val generatedDtoTypeName: LsiTypeName,
        val generatedDtoTypeIdsByTypeName: Map<LsiTypeName, DtoTypeId>,
        val batchRootDtoTypeNames: Map<DtoTypeId, LsiTypeName>,
    ) {
        fun fragment(targetLanguage: LsiLanguage): LsiCodeFragment {
            return rootType.toLsiMetadataFetcherPoetFragment(
                targetLanguage = targetLanguage,
                graph = graph,
                immutableSchema = immutableSchema,
                workspace = workspace,
                configContractResolution = DtoConfigContractResolution(emptyList(), emptyList()),
                generatedDtoTypeName = generatedDtoTypeName,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                batchRootDtoTypeNames = batchRootDtoTypeNames,
            )
        }

        fun render(targetLanguage: LsiLanguage): String {
            val fragment = fragment(targetLanguage)
            val typeNames = workspace.dtoMetadataFetcherPoetTypeNames(
                fragment = fragment,
                immutableSchema = immutableSchema,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            )
            return when (targetLanguage) {
                LsiLanguage.JAVA ->
                    LsiJavaPoetRenderer().renderCodeBlock(fragment.codeBlock, typeNames).toString()
                LsiLanguage.KOTLIN ->
                    LsiKotlinPoetRenderer().renderCodeBlock(fragment.codeBlock, typeNames).toString()
                else -> error("Unsupported metadata fetcher test language: $targetLanguage")
            }
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Book.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))

        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val SPECIAL_BOOK_TYPE_ID = LsiSymbolId.type("demo.SpecialBook")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val PUBLISHER_TYPE_ID = LsiSymbolId.type("demo.Publisher")
        val REUSABLE_STORE_VIEW_TYPE_ID = LsiSymbolId.type("demo.dto.StoreView")
        val EXTERNAL_STORE_VIEW_TYPE_ID = LsiSymbolId.type("contract.ExternalStoreView")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")

        val REUSABLE_STORE_VIEW_DTO_TYPE_ID = DtoTypeId("demo/Store.dto#root:StoreView")

        val BOOK_ID_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "id")
        val BOOK_NAME_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "name")
        val BOOK_EDITION_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "edition")
        val BOOK_STORE_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "store")
        val BOOK_PARENT_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "parent")
        val BOOK_EDITOR_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "editor")
        val BOOK_REUSABLE_STORE_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "reusableStore")
        val BOOK_EXTERNAL_STORE_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "externalStore")
        val BOOK_PUBLISHER_PROP_ID = LsiSymbolId.property(BOOK_TYPE_ID, "publisher")
        val SPECIAL_BOOK_STORE_PROP_ID = LsiSymbolId.property(SPECIAL_BOOK_TYPE_ID, "specialStore")
        val PUBLISHER_NAME_PROP_ID = LsiSymbolId.property(PUBLISHER_TYPE_ID, "name")
        val PUBLISHER_STORE_PROP_ID = LsiSymbolId.property(PUBLISHER_TYPE_ID, "store")

        val BOOK_FETCHER_TYPE_ID = LsiSymbolId.type("demo.BookFetcher")
        val SPECIAL_BOOK_FETCHER_TYPE_ID = LsiSymbolId.type("demo.SpecialBookFetcher")
        val PUBLISHER_FETCHER_TYPE_ID = LsiSymbolId.type("demo.PublisherFetcher")

        val JAVA_EXPECTED = listOf(
            "demo.BookFetcher.${'$'}",
            "  .name()",
            "  .edition()",
            "  .store(demo.dto.BookView.TargetOf_store.METADATA.getFetcher())",
            "  .recursiveParent()",
            "  .editor(",
            "    demo.dto.BookView.TargetOf_editor.METADATA.getFetcher(), ",
            "    cfg -> cfg",
            "      .batch(5)",
            "  )",
            "  .reusableStore(demo.dto.StoreView.METADATA.getFetcher())",
            "  .externalStore(contract.ExternalStoreView.METADATA.getFetcher())",
            "  .publisher(demo.PublisherFetcher.${'$'}",
            "      .name()",
            "      .store(demo.dto.BookView.TargetOf_store.METADATA.getFetcher())",
            "  )",
            "  .name()",
            "  .store(" +
                "demo.dto.BookView.TargetOf_firstSummary.TargetOf_firstFoldStore.METADATA.getFetcher())",
            "  .name()",
            "  .store(" +
                "demo.dto.BookView.TargetOf_secondSummary.TargetOf_secondFoldStore.METADATA.getFetcher())",
            "  .publisher(demo.PublisherFetcher.${'$'}",
            "      .store(" +
                "demo.dto.BookView.TargetOf_secondSummary.TargetOf_secondFoldStore.METADATA.getFetcher())",
            "  )",
            "  .forType(demo.SpecialBookFetcher.${'$'}",
            "    .specialStore(" +
                "demo.dto.BookView.TargetOf_specialStore.TargetOf_specialStore_2.METADATA.getFetcher())",
            "  )",
        ).joinToString("\n")

        val KOTLIN_EXPECTED = """
            org.babyfish.jimmer.sql.kt.fetcher.newFetcher(demo.Book::class).by {
              name()
              edition()
              store(demo.dto.BookView.TargetOf_store.METADATA.fetcher)
              `parent*`()
              editor(demo.dto.BookView.TargetOf_editor.METADATA.fetcher) {
                batch(5)
              }
              reusableStore(demo.dto.StoreView.METADATA.fetcher)
              externalStore(contract.ExternalStoreView.METADATA.fetcher)
              publisher {
                name()
                store(demo.dto.BookView.TargetOf_store.METADATA.fetcher)

              }
              name()
              store(demo.dto.BookView.TargetOf_firstSummary.TargetOf_firstFoldStore.METADATA.fetcher)
              name()
              store(demo.dto.BookView.TargetOf_secondSummary.TargetOf_secondFoldStore.METADATA.fetcher)
              publisher {
                store(demo.dto.BookView.TargetOf_secondSummary.TargetOf_secondFoldStore.METADATA.fetcher)

              }
              forType(demo.SpecialBook::class) {
                specialStore(demo.dto.BookView.TargetOf_specialStore.TargetOf_specialStore_2.METADATA.fetcher)
              }
            }
        """.trimIndent()
    }
}
