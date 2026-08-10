package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiFileNameStyle
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ImmutableQueryPoetTest {

    @Test
    fun `java query preserves primitive and boxed array elements`() {
        val source = source("demo/ArrayEntity.java")
        val typeId = typeId("demo.ArrayEntity")
        val id = scalarProp(typeId, "id", LONG_TYPE, PrimaryMapping.ID)
        val primitiveValues = scalarProp(
            typeId,
            "primitiveValues",
            LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.INT)),
        )
        val boxedValues = scalarProp(
            typeId,
            "boxedValues",
            LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true)),
        )
        val type = immutableType(typeId, listOf(id, primitiveValues, boxedValues), idPropId = id.id)
        val schema = ImmutableSchema(listOf(type))
        val workspace = workspace(
            types = listOf(type to source),
            props = listOf(id to source, primitiveValues to source, boxedValues to source),
        )

        val tableArtifact = schema.toQueryPoetArtifacts(
            types = listOf(type),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        ).single { artifact -> artifact.qualifiedFileName == "demo.ArrayEntityTable" }
        val sourceText = LsiJavaPoetRenderer().render(tableArtifact).content

        assertTrue("PropExpression<int[]> primitiveValues()" in sourceText)
        assertTrue("PropExpression<Integer[]> boxedValues()" in sourceText)
    }

    @Test
    fun `same source query artifacts are isolating and immediate`() {
        val source = source("demo/Book.kt")
        val bookId = typeId("demo.Book")
        val id = scalarProp(bookId, "id", LONG_TYPE, PrimaryMapping.ID)
        val name = scalarProp(bookId, "name", STRING_TYPE)
        val book = immutableType(bookId, listOf(id, name), idPropId = id.id)
        val schema = ImmutableSchema(listOf(book))
        val workspace = workspace(
            types = listOf(book to source),
            props = listOf(id to source, name to source),
        )

        val javaArtifacts = schema.toQueryPoetArtifacts(
            types = listOf(book),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        )
        val kotlinArtifacts = schema.toQueryPoetArtifacts(
            types = listOf(book),
            language = LsiLanguage.KOTLIN,
            workspace = workspace,
        )
        val artifacts = javaArtifacts + kotlinArtifacts

        assertEquals(
            setOf("demo.BookProps", "demo.BookTable", "demo.BookTableEx"),
            javaArtifacts.mapTo(linkedSetOf(), LsiSourceArtifact::qualifiedFileName),
        )
        assertEquals(listOf("demo.BookProps"), kotlinArtifacts.map(LsiSourceArtifact::qualifiedFileName))
        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact ->
            artifact.originatingSymbols == setOf(bookId) && artifact.originatingSources == setOf(source)
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(setOf(bookId, id.id, name.id))
        })
    }

    @Test
    fun `cross source semantic closure is aggregating and preserves Kotlin source basename`() {
        val bookSource = source("demo/order-item.part.kt")
        val baseSource = source("shared/BaseRecord.kt")
        val storeSource = source("catalog/Store.kt")
        val locationSource = source("shared/Location.kt")
        val annotationSource = source("support/QueryTypeMarker.kt")
        val payloadSource = source("support/QueryPayload.kt")
        val enumSource = source("support/QueryMode.kt")
        val nestedSource = source("support/QueryNested.kt")
        val bookId = typeId("demo.Book")
        val baseId = typeId("shared.BaseRecord")
        val storeId = typeId("catalog.Store")
        val locationId = typeId("shared.Location")
        val markerId = typeId("support.QueryTypeMarker")
        val payloadId = typeId("support.QueryPayload")
        val enumId = typeId("support.QueryMode")
        val nestedId = typeId("support.QueryNested")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val baseIdentity = scalarProp(baseId, "id", LONG_TYPE, PrimaryMapping.ID)
        val baseOwner = immutableProp(
            id = LsiSymbolId.property(baseId, "owner"),
            declarationId = LsiSymbolId.property(baseId, "owner"),
            ownerId = baseId,
            declaringTypeId = baseId,
            name = "owner",
            type = LsiTypeParameterRef(baseParameterId),
            annotations = emptyList(),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            association = true,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
            genericTarget = true,
        )
        val bookIdentity = inheritedProp(bookId, baseIdentity)
        val bookOwner = immutableProp(
            id = LsiSymbolId.property(bookId, "owner"),
            declarationId = baseOwner.declarationId,
            ownerId = bookId,
            declaringTypeId = baseId,
            name = "owner",
            type = LsiDeclaredType(storeId),
            annotations = emptyList(),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            inherited = true,
            overrideChain = listOf(baseOwner.declarationId),
            association = true,
            targetTypeId = storeId,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
        )
        val storeIdentity = scalarProp(storeId, "id", LONG_TYPE, PrimaryMapping.ID)
        val store = associationProp(bookId, "store", storeId)
        val storeIdView = scalarProp(
            ownerId = bookId,
            name = "storeId",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(store.id, storeIdentity.id),
        )
        val locationCity = scalarProp(locationId, "city", STRING_TYPE)
        val location = embeddedProp(bookId, "location", locationId)
        val marker = LsiAnnotation(
            type = markerId,
            arguments = mapOf(
                "values" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.ClassValue(LsiDeclaredType(payloadId)),
                            LsiAnnotationValue.EnumValue(enumId, "PRIMARY"),
                            LsiAnnotationValue.NestedAnnotationValue(LsiAnnotation(nestedId)),
                        )
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
        val annotatedName = scalarProp(
            ownerId = bookId,
            name = "name",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.lang.String"),
                annotations = listOf(marker),
            ),
        )
        val base = immutableType(
            id = baseId,
            props = listOf(baseIdentity, baseOwner),
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            typeParameterIds = listOf(baseParameterId),
            idPropId = baseIdentity.id,
        )
        val book = immutableType(
            id = bookId,
            props = listOf(bookIdentity, bookOwner, store, storeIdView, location, annotatedName),
            superTypeIds = listOf(baseId),
            primarySuperTypeId = baseId,
            idPropId = bookIdentity.id,
        )
        val storeType = immutableType(storeId, listOf(storeIdentity), idPropId = storeIdentity.id)
        val locationType = immutableType(
            id = locationId,
            props = listOf(locationCity),
            kind = ImmutableTypeKind.EMBEDDABLE,
        )
        val schema = ImmutableSchema(listOf(base, book, storeType, locationType))
        val workspace = workspace(
            types = listOf(
                base to baseSource,
                book to bookSource,
                storeType to storeSource,
                locationType to locationSource,
            ),
            props = listOf(
                baseIdentity to baseSource,
                baseOwner to baseSource,
                bookIdentity to bookSource,
                bookOwner to bookSource,
                store to bookSource,
                storeIdView to bookSource,
                location to bookSource,
                annotatedName to bookSource,
                storeIdentity to storeSource,
                locationCity to locationSource,
            ),
            additionalTypeSources = mapOf(
                markerId to annotationSource,
                payloadId to payloadSource,
                enumId to enumSource,
                nestedId to nestedSource,
            ),
            superTypeRefsByTypeId = mapOf(
                bookId to listOf(
                    LsiDeclaredType(
                        declarationId = baseId,
                        arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(storeId))),
                    )
                )
            ),
        )

        val javaArtifacts = schema.toQueryPoetArtifacts(
            listOf(book),
            LsiLanguage.JAVA,
            workspace,
        )
        val kotlinArtifact = schema.toQueryPoetArtifacts(
            listOf(book),
            LsiLanguage.KOTLIN,
            workspace,
        ).single()
        val artifacts = javaArtifacts + kotlinArtifact
        val expectedSymbols = setOf(
            bookId,
            baseId,
            baseParameterId,
            storeId,
            locationId,
            markerId,
            payloadId,
            enumId,
            nestedId,
            baseIdentity.id,
            baseOwner.id,
            bookIdentity.id,
            bookOwner.id,
            store.id,
            storeIdView.id,
            storeIdentity.id,
            location.id,
            annotatedName.id,
        )

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(expectedSymbols)
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySources.containsAll(
                setOf(
                    bookSource,
                    baseSource,
                    storeSource,
                    locationSource,
                    annotationSource,
                    payloadSource,
                    enumSource,
                    nestedSource,
                )
            )
        })
        assertEquals(LsiFileNameStyle.KOTLIN_SOURCE_STEM, kotlinArtifact.file.fileNameStyle)
        assertEquals("demo/order-item.partProps.kt", LsiKotlinPoetRenderer().render(kotlinArtifact).path)
    }

    @Test
    fun `inheritance branch query artifacts are aggregating stable and keep branch origins`() {
        val rootSource = source("demo/Root.kt")
        val childSource = source("demo/SpecialRoot.kt")
        val rootId = typeId("demo.Root")
        val childId = typeId("demo.SpecialRoot")
        val rootIdentity = scalarProp(rootId, "id", LONG_TYPE, PrimaryMapping.ID)
        val rootDiscriminator = scalarProp(
            rootId,
            "type",
            STRING_TYPE,
            PrimaryMapping.DISCRIMINATOR,
        )
        val childIdentity = inheritedProp(childId, rootIdentity)
        val childDiscriminator = inheritedProp(childId, rootDiscriminator)
        val specialName = scalarProp(childId, "specialName", STRING_TYPE)
        val root = immutableType(
            id = rootId,
            props = listOf(rootIdentity, rootDiscriminator),
            idPropId = rootIdentity.id,
            inheritanceRootTypeId = rootId,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
            discriminatorPropId = rootDiscriminator.id,
        )
        val child = immutableType(
            id = childId,
            props = listOf(childIdentity, childDiscriminator, specialName),
            superTypeIds = listOf(rootId),
            primarySuperTypeId = rootId,
            idPropId = childIdentity.id,
            inheritanceRootTypeId = rootId,
            instantiable = true,
            discriminatorValue = "SPECIAL",
            discriminatorPropId = childDiscriminator.id,
        )
        val schema = ImmutableSchema(listOf(root, child))
        val workspace = workspace(
            types = listOf(root to rootSource, child to childSource),
            props = listOf(
                rootIdentity to rootSource,
                rootDiscriminator to rootSource,
                childIdentity to childSource,
                childDiscriminator to childSource,
                specialName to childSource,
            ),
        )

        val javaArtifacts = schema.toQueryPoetArtifacts(
            listOf(root, child),
            LsiLanguage.JAVA,
            workspace,
        )
        val kotlinArtifacts = schema.toQueryPoetArtifacts(
            listOf(root, child),
            LsiLanguage.KOTLIN,
            workspace,
        )
        val artifacts = javaArtifacts + kotlinArtifacts
        val rootBranchArtifacts = artifacts.filter { artifact ->
            rootId in artifact.originatingSymbols
        }

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING &&
                artifact.emissionMode == ArtifactEmissionMode.STABLE
        })
        assertTrue(rootBranchArtifacts.all { artifact ->
            artifact.originatingSymbols == setOf(rootId, childId) &&
                artifact.originatingSources == setOf(rootSource, childSource)
        })
        assertTrue(rootBranchArtifacts.all { artifact -> childId in artifact.dependencySymbols })
    }

    @Test
    fun `binary query dependencies stay isolating and are excluded from dependency sources`() {
        val bookSource = source("demo/Book.kt")
        val binarySource = source("catalog/Store.class", LsiSourceKind.BINARY)
        val bookId = typeId("demo.Book")
        val storeId = typeId("catalog.Store")
        val bookIdentity = scalarProp(bookId, "id", LONG_TYPE, PrimaryMapping.ID)
        val storeIdentity = scalarProp(storeId, "id", LONG_TYPE, PrimaryMapping.ID)
        val store = associationProp(bookId, "store", storeId)
        val book = immutableType(bookId, listOf(bookIdentity, store), idPropId = bookIdentity.id)
        val storeType = immutableType(storeId, listOf(storeIdentity), idPropId = storeIdentity.id)
        val schema = ImmutableSchema(listOf(book, storeType))
        val workspace = workspace(
            types = listOf(book to bookSource, storeType to binarySource),
            props = listOf(
                bookIdentity to bookSource,
                store to bookSource,
                storeIdentity to binarySource,
            ),
        )

        val artifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).flatMap { language ->
            schema.toQueryPoetArtifacts(listOf(book), language, workspace)
        }

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(setOf(storeId, storeIdentity.id))
        })
        assertTrue(artifacts.all { artifact -> artifact.dependencySources == setOf(bookSource) })
    }

    private fun workspace(
        types: List<Pair<ImmutableType, LsiSource>>,
        props: List<Pair<ImmutableProp, LsiSource>>,
        additionalTypeSources: Map<LsiSymbolId, LsiSource> = emptyMap(),
        superTypeRefsByTypeId: Map<LsiSymbolId, List<LsiType>> = emptyMap(),
    ): LsiWorkspace {
        val propsByOwner = props.groupBy { (prop, _) -> prop.ownerTypeId }
        val typeDeclarations = types.map { (type, source) ->
            LsiTypeDeclaration(
                id = type.id,
                name = type.qualifiedName.substringAfterLast('.'),
                qualifiedName = type.qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                typeParameters = type.typeParameterIds.map { parameterId ->
                    LsiTypeParameter(parameterId, parameterId.requireTypeParameterName())
                },
                superTypes = superTypeRefsByTypeId[type.id] ?: type.superTypeIds.map(::LsiDeclaredType),
                memberIds = propsByOwner[type.id].orEmpty().map { (prop, _) -> prop.id },
                origin = LsiOrigin(source.originKind(), source),
            )
        }
        val propertyDeclarations = props.map { (prop, source) ->
            LsiProperty(
                id = prop.id,
                name = prop.name,
                ownerId = prop.ownerTypeId,
                type = prop.type,
                annotations = prop.annotations,
                origin = LsiOrigin(source.originKind(), source),
            )
        }
        val additionalTypeDeclarations = additionalTypeSources.map { (id, source) ->
            val qualifiedName = id.requireTypeQualifiedName()
            LsiTypeDeclaration(
                id = id,
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                origin = LsiOrigin(source.originKind(), source),
            )
        }
        return LsiWorkspace(
            sources = buildSet {
                types.mapTo(this) { (_, source) -> source }
                addAll(additionalTypeSources.values)
            },
            declarations = typeDeclarations + propertyDeclarations + additionalTypeDeclarations,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        kind: ImmutableTypeKind = ImmutableTypeKind.ENTITY,
        typeParameterIds: List<LsiSymbolId> = emptyList(),
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        idPropId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: InheritanceStrategy? = null,
        joinedTableDissociateAction: JoinedTableDissociateAction? = null,
        instantiable: Boolean = false,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = kind,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = typeParameterIds,
            superTypeIds = superTypeIds,
            props = props,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
            idPropId = idPropId,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun scalarProp(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiType,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        view: ImmutableView? = null,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerId, name)
        val primaryAnnotationId = if (primaryMapping == PrimaryMapping.ID) ID_ANNOTATION_ID else null
        return immutableProp(
            id = id,
            declarationId = id,
            ownerId = ownerId,
            declaringTypeId = ownerId,
            name = name,
            type = type,
            annotations = listOfNotNull(primaryAnnotationId?.let(::LsiAnnotation)),
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = primaryAnnotationId,
            view = view,
        )
    }

    private fun inheritedProp(
        ownerId: LsiSymbolId,
        declaredProp: ImmutableProp,
    ): ImmutableProp {
        return immutableProp(
            id = LsiSymbolId.property(ownerId, declaredProp.name),
            declarationId = declaredProp.declarationId,
            ownerId = ownerId,
            declaringTypeId = declaredProp.declaringTypeId,
            name = declaredProp.name,
            type = declaredProp.type,
            annotations = declaredProp.annotations,
            primaryMapping = declaredProp.primaryMapping,
            primaryAnnotationTypeId = declaredProp.primaryAnnotationTypeId,
            inherited = true,
            overrideChain = declaredProp.overrideChain,
        )
    }

    private fun associationProp(
        ownerId: LsiSymbolId,
        name: String,
        targetTypeId: LsiSymbolId,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerId, name)
        return immutableProp(
            id = id,
            declarationId = id,
            ownerId = ownerId,
            declaringTypeId = ownerId,
            name = name,
            type = LsiDeclaredType(targetTypeId),
            annotations = emptyList(),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            association = true,
            targetTypeId = targetTypeId,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
        )
    }

    private fun embeddedProp(
        ownerId: LsiSymbolId,
        name: String,
        targetTypeId: LsiSymbolId,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerId, name)
        return immutableProp(
            id = id,
            declarationId = id,
            ownerId = ownerId,
            declaringTypeId = ownerId,
            name = name,
            type = LsiDeclaredType(targetTypeId),
            annotations = emptyList(),
            primaryMapping = PrimaryMapping.SCALAR,
            embedded = true,
            targetTypeId = targetTypeId,
        )
    }

    private fun immutableProp(
        id: LsiSymbolId,
        declarationId: LsiSymbolId,
        ownerId: LsiSymbolId,
        declaringTypeId: LsiSymbolId,
        name: String,
        type: LsiType,
        annotations: List<LsiAnnotation>,
        primaryMapping: PrimaryMapping,
        primaryAnnotationTypeId: LsiSymbolId? = null,
        inherited: Boolean = false,
        overrideChain: List<LsiSymbolId> = listOf(declarationId),
        association: Boolean = false,
        embedded: Boolean = false,
        targetTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
        view: ImmutableView? = null,
        genericTarget: Boolean = false,
    ): ImmutableProp {
        return ImmutableProp(
            id = id,
            declarationId = declarationId,
            ownerTypeId = ownerId,
            declaringTypeId = declaringTypeId,
            name = name,
            documentation = null,
            type = type,
            annotations = annotations,
            overrideChain = overrideChain,
            inherited = inherited,
            overridden = false,
            nullable = type.nullability == LsiNullability.NULLABLE,
            list = false,
            association = association,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = primaryAnnotationTypeId,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = site.addzero.lsi.jimmer.FormulaKind.NONE,
            mappedBy = null,
            associationStorage = associationStorage,
            transientResolver = null,
            view = view,
            genericTarget = genericTarget,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
            formulaDependencies = emptyList(),
        )
    }

    private fun LsiSource.originKind(): LsiOriginKind {
        return if (kind == LsiSourceKind.BINARY) LsiOriginKind.BINARY else LsiOriginKind.SOURCE
    }

    private fun source(
        path: String,
        kind: LsiSourceKind = LsiSourceKind.SOURCE,
    ): LsiSource {
        return LsiSource.of(path, LsiLanguage.KOTLIN, kind)
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)

    private companion object {
        val ID_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
    }
}
