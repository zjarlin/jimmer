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
import site.addzero.lsi.jimmer.FormulaDependency
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableConverter
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.MappedBy
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.TransientResolver
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.file.LsiFileNameStyle
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ImmutableFetcherPoetTest {

    @Test
    fun `same source fetchers are isolating and immediate`() {
        val source = source("demo/Book.kt")
        val bookId = typeId("demo.Book")
        val id = scalarProp(bookId, "id", LONG_TYPE, PrimaryMapping.ID)
        val title = scalarProp(bookId, "title", STRING_TYPE)
        val book = immutableType(
            id = bookId,
            props = listOf(id, title),
            idPropId = id.id,
        )
        val workspace = workspace(
            typeSources = mapOf(bookId to source),
            props = listOf(id to source, title to source),
        )
        val schema = ImmutableSchema(listOf(book))

        val artifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).map { language ->
            schema.toFetcherPoetArtifacts(listOf(book), language, workspace).single()
        }

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact ->
            artifact.originatingSymbols == setOf(bookId) && artifact.originatingSources == setOf(source)
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(setOf(bookId, id.id, title.id))
        })
    }

    @Test
    fun `cross source semantic closure is aggregating and preserves raw source basename`() {
        val bookSource = source("demo/order-item.part.kt")
        val baseSource = source("shared/BaseRecord.kt")
        val authorSource = source("shared/Author.kt")
        val commentSource = source("shared/Comment.kt")
        val supportSource = source("support/FetcherSupport.kt")
        val bookId = typeId("demo.Book")
        val baseId = typeId("shared.BaseRecord")
        val authorId = typeId("shared.Author")
        val commentId = typeId("shared.Comment")
        val resolverId = typeId("support.RankResolver")
        val validationId = typeId("support.ValidRank")
        val validatorId = typeId("support.RankValidator")
        val converterId = typeId("support.RankConverter")
        val converterSourceId = typeId("support.RankSource")
        val converterTargetId = typeId("support.RankTarget")
        val baseBookId = scalarProp(baseId, "id", LONG_TYPE, PrimaryMapping.ID)
        val baseTitle = scalarProp(baseId, "title", STRING_TYPE)
        val effectiveBookId = inheritedProp(bookId, baseBookId)
        val effectiveTitle = inheritedProp(bookId, baseTitle)
        val authorEntityId = scalarProp(authorId, "id", LONG_TYPE, PrimaryMapping.ID)
        val commentEntityId = scalarProp(commentId, "id", LONG_TYPE, PrimaryMapping.ID)
        val author = associationProp(
            ownerId = bookId,
            name = "author",
            targetId = authorId,
            kind = AssociationKind.MANY_TO_ONE,
            storage = AssociationStorageKind.COLUMN,
        )
        val authorIdView = scalarProp(
            ownerId = bookId,
            name = "authorId",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(author.id, authorEntityId.id),
        )
        val commentBook = associationProp(
            ownerId = commentId,
            name = "book",
            targetId = bookId,
            kind = AssociationKind.MANY_TO_ONE,
            storage = AssociationStorageKind.COLUMN,
        )
        val comments = associationProp(
            ownerId = bookId,
            name = "comments",
            targetId = commentId,
            kind = AssociationKind.ONE_TO_MANY,
            storage = AssociationStorageKind.NONE,
            mappedBy = MappedBy("book", commentBook.id),
        )
        val display = scalarProp(
            ownerId = bookId,
            name = "display",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.FORMULA,
            formulaKind = FormulaKind.LANGUAGE,
            formulaDependencies = listOf(FormulaDependency(listOf(effectiveTitle.id))),
        )
        val rank = scalarProp(
            ownerId = bookId,
            name = "rank",
            type = INT_TYPE,
            primaryMapping = PrimaryMapping.TRANSIENT,
            transientResolver = TransientResolver.Type(resolverId),
            validations = listOf(
                ImmutableValidation(validationId, listOf(validatorId), "invalid", null)
            ),
            converter = ImmutableConverter(
                converterTypeId = converterId,
                sourceType = LsiDeclaredType(converterSourceId),
                targetType = LsiDeclaredType(converterTargetId),
                sourceNullable = false,
                targetNullable = false,
                propertyNullable = false,
            ),
        )
        val base = immutableType(
            id = baseId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = listOf(baseBookId, baseTitle),
            idPropId = baseBookId.id,
        )
        val book = immutableType(
            id = bookId,
            props = listOf(
                effectiveBookId,
                effectiveTitle,
                author,
                authorIdView,
                comments,
                display,
                rank,
            ),
            superTypeIds = listOf(baseId),
            primarySuperTypeId = baseId,
            idPropId = effectiveBookId.id,
        )
        val authorType = immutableType(
            id = authorId,
            props = listOf(authorEntityId),
            idPropId = authorEntityId.id,
        )
        val commentType = immutableType(
            id = commentId,
            props = listOf(commentEntityId, commentBook),
            idPropId = commentEntityId.id,
        )
        val supportIds = setOf(
            resolverId,
            validationId,
            validatorId,
            converterId,
            converterSourceId,
            converterTargetId,
        )
        val workspace = workspace(
            typeSources = buildMap {
                put(bookId, bookSource)
                put(baseId, baseSource)
                put(authorId, authorSource)
                put(commentId, commentSource)
                supportIds.forEach { supportId -> put(supportId, supportSource) }
            },
            props = listOf(
                baseBookId to baseSource,
                baseTitle to baseSource,
                effectiveBookId to bookSource,
                effectiveTitle to bookSource,
                author to bookSource,
                authorIdView to bookSource,
                comments to bookSource,
                display to bookSource,
                rank to bookSource,
                authorEntityId to authorSource,
                commentEntityId to commentSource,
                commentBook to commentSource,
            ),
        )
        val schema = ImmutableSchema(listOf(base, book, authorType, commentType))

        val javaArtifact = schema.toFetcherPoetArtifacts(
            listOf(book),
            LsiLanguage.JAVA,
            workspace,
        ).single()
        val kotlinArtifact = schema.toFetcherPoetArtifacts(
            listOf(book),
            LsiLanguage.KOTLIN,
            workspace,
        ).single()
        val generated = LsiKotlinPoetRenderer().render(kotlinArtifact)
        val expectedDependencyIds = setOf(
            baseId,
            baseBookId.id,
            baseTitle.id,
            authorId,
            authorEntityId.id,
            commentId,
            commentBook.id,
            effectiveTitle.id,
        ) + supportIds

        assertTrue(listOf(javaArtifact, kotlinArtifact).all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        assertTrue(listOf(javaArtifact, kotlinArtifact).all { artifact ->
            artifact.dependencySymbols.containsAll(expectedDependencyIds)
        })
        assertTrue(listOf(javaArtifact, kotlinArtifact).all { artifact ->
            artifact.dependencySources.containsAll(
                setOf(bookSource, baseSource, authorSource, commentSource, supportSource)
            )
        })
        assertEquals(
            listOf("shared.by"),
            kotlinArtifact.file.imports.map { sourceImport ->
                "${sourceImport.packageName}.${sourceImport.simpleName}"
            },
        )
        assertEquals(LsiFileNameStyle.KOTLIN_SOURCE_STEM, kotlinArtifact.file.fileNameStyle)
        assertEquals("demo/order-item.partFetcher.kt", generated.path)
    }

    @Test
    fun `inheritance root and child fetchers are aggregating and stable`() {
        val rootSource = source("demo/Root.kt")
        val childSource = source("demo/Child.kt")
        val rootId = typeId("demo.Root")
        val childId = typeId("demo.Child")
        val rootIdentity = scalarProp(rootId, "id", LONG_TYPE, PrimaryMapping.ID)
        val rootDiscriminator = scalarProp(
            rootId,
            "type",
            STRING_TYPE,
            PrimaryMapping.DISCRIMINATOR,
        )
        val childIdentity = inheritedProp(childId, rootIdentity)
        val childDiscriminator = inheritedProp(childId, rootDiscriminator)
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
            props = listOf(childIdentity, childDiscriminator),
            superTypeIds = listOf(rootId),
            primarySuperTypeId = rootId,
            idPropId = childIdentity.id,
            inheritanceRootTypeId = rootId,
            instantiable = true,
            discriminatorValue = "CHILD",
            discriminatorPropId = childDiscriminator.id,
        )
        val workspace = workspace(
            typeSources = mapOf(rootId to rootSource, childId to childSource),
            props = listOf(
                rootIdentity to rootSource,
                rootDiscriminator to rootSource,
                childIdentity to childSource,
                childDiscriminator to childSource,
            ),
        )
        val schema = ImmutableSchema(listOf(root, child))

        val artifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).flatMap { language ->
            schema.toFetcherPoetArtifacts(listOf(root, child), language, workspace)
        }
        val rootArtifacts = artifacts.filter { artifact -> rootId in artifact.originatingSymbols }
        val childArtifacts = artifacts.filter { artifact -> artifact.originatingSymbols == setOf(childId) }

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING &&
                artifact.emissionMode == ArtifactEmissionMode.STABLE
        })
        assertTrue(rootArtifacts.all { artifact ->
            artifact.originatingSymbols == setOf(rootId, childId)
        })
        assertEquals(2, childArtifacts.size)
    }

    @Test
    fun `binary targets stay isolating while structured annotation values aggregate`() {
        val bookSource = source("demo/Book.kt")
        val binarySource = source("binary/shared/Author.class", LsiSourceKind.BINARY)
        val bookId = typeId("demo.Book")
        val authorId = typeId("shared.Author")
        val bookIdentity = scalarProp(bookId, "id", LONG_TYPE, PrimaryMapping.ID)
        val authorIdentity = scalarProp(authorId, "id", LONG_TYPE, PrimaryMapping.ID)
        val author = associationProp(
            ownerId = bookId,
            name = "author",
            targetId = authorId,
            kind = AssociationKind.MANY_TO_ONE,
            storage = AssociationStorageKind.COLUMN,
        )
        val book = immutableType(bookId, listOf(bookIdentity, author), idPropId = bookIdentity.id)
        val authorType = immutableType(authorId, listOf(authorIdentity), idPropId = authorIdentity.id)
        val binaryWorkspace = workspace(
            typeSources = mapOf(bookId to bookSource, authorId to binarySource),
            props = listOf(bookIdentity to bookSource, author to bookSource, authorIdentity to binarySource),
        )
        val binarySchema = ImmutableSchema(listOf(book, authorType))

        val binaryArtifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).map { language ->
            binarySchema.toFetcherPoetArtifacts(listOf(book), language, binaryWorkspace).single()
        }

        assertTrue(binaryArtifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING
        })
        assertTrue(binaryArtifacts.all { artifact -> authorId in artifact.dependencySymbols })
        assertTrue(binaryArtifacts.all { artifact -> artifact.dependencySources == setOf(bookSource) })

        val markerSource = source("support/TypeMarker.kt")
        val payloadSource = source("support/Payload.kt")
        val enumSource = source("support/Mode.kt")
        val nestedSource = source("support/Nested.kt")
        val markerId = typeId("support.TypeMarker")
        val payloadId = typeId("support.Payload")
        val enumId = typeId("support.Mode")
        val nestedId = typeId("support.Nested")
        val annotatedBookId = typeId("demo.AnnotatedBook")
        val annotatedIdentity = scalarProp(annotatedBookId, "id", LONG_TYPE, PrimaryMapping.ID)
        val nestedAnnotation = LsiAnnotation(nestedId)
        val marker = LsiAnnotation(
            type = markerId,
            arguments = mapOf(
                "values" to explicit(
                    LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.ClassValue(LsiDeclaredType(payloadId)),
                            LsiAnnotationValue.EnumValue(enumId, "PRIMARY"),
                            LsiAnnotationValue.NestedAnnotationValue(nestedAnnotation),
                        )
                    )
                )
            ),
        )
        val annotatedTitle = scalarProp(
            ownerId = annotatedBookId,
            name = "title",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.lang.String"),
                annotations = listOf(marker),
            ),
        )
        val annotatedBook = immutableType(
            annotatedBookId,
            listOf(annotatedIdentity, annotatedTitle),
            idPropId = annotatedIdentity.id,
        )
        val annotationWorkspace = workspace(
            typeSources = mapOf(
                annotatedBookId to bookSource,
                markerId to markerSource,
                payloadId to payloadSource,
                enumId to enumSource,
                nestedId to nestedSource,
            ),
            props = listOf(annotatedIdentity to bookSource, annotatedTitle to bookSource),
        )
        val annotationSchema = ImmutableSchema(listOf(annotatedBook))

        val annotationArtifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).map { language ->
            annotationSchema.toFetcherPoetArtifacts(
                listOf(annotatedBook),
                language,
                annotationWorkspace,
            ).single()
        }
        val annotationDependencyIds = setOf(markerId, payloadId, enumId, nestedId)
        val annotationDependencySources = setOf(markerSource, payloadSource, enumSource, nestedSource)

        assertTrue(annotationArtifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        assertTrue(annotationArtifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(annotationDependencyIds)
        })
        assertTrue(annotationArtifacts.all { artifact ->
            artifact.dependencySources.containsAll(annotationDependencySources)
        })
    }

    private fun workspace(
        typeSources: Map<LsiSymbolId, LsiSource>,
        props: List<Pair<ImmutableProp, LsiSource>>,
    ): LsiWorkspace {
        val propsByOwner = props.groupBy { (prop, _) -> prop.ownerTypeId }
        val typeDeclarations = typeSources.map { (id, source) ->
            val qualifiedName = id.requireTypeQualifiedName()
            LsiClass(
                id = id,
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                memberIds = propsByOwner[id].orEmpty().map { (prop, _) -> prop.id },
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
        return LsiWorkspace(
            sources = typeSources.values.toSet(),
            declarations = typeDeclarations + propertyDeclarations,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        kind: ImmutableTypeKind = ImmutableTypeKind.ENTITY,
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
        formulaKind: FormulaKind = FormulaKind.NONE,
        formulaDependencies: List<FormulaDependency> = emptyList(),
        transientResolver: TransientResolver? = null,
        validations: List<ImmutableValidation> = emptyList(),
        converter: ImmutableConverter? = null,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerId, name)
        val primaryAnnotation = if (primaryMapping == PrimaryMapping.ID) ID_ANNOTATION_ID else null
        return immutableProp(
            id = id,
            declarationId = id,
            ownerId = ownerId,
            declaringTypeId = ownerId,
            name = name,
            type = type,
            annotations = listOfNotNull(primaryAnnotation?.let(::LsiAnnotation)),
            inherited = false,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = primaryAnnotation,
            view = view,
            formulaKind = formulaKind,
            formulaDependencies = formulaDependencies,
            transientResolver = transientResolver,
            validations = validations,
            converter = converter,
        )
    }

    private fun inheritedProp(
        ownerId: LsiSymbolId,
        declaredProp: ImmutableProp,
    ): ImmutableProp {
        return immutableProp(
            id = LsiSymbolId.property(ownerId, declaredProp.name),
            declarationId = declaredProp.id,
            ownerId = ownerId,
            declaringTypeId = declaredProp.ownerTypeId,
            name = declaredProp.name,
            type = declaredProp.type,
            annotations = declaredProp.annotations,
            inherited = true,
            primaryMapping = declaredProp.primaryMapping,
            primaryAnnotationTypeId = declaredProp.primaryAnnotationTypeId,
        )
    }

    private fun associationProp(
        ownerId: LsiSymbolId,
        name: String,
        targetId: LsiSymbolId,
        kind: AssociationKind,
        storage: AssociationStorageKind,
        mappedBy: MappedBy? = null,
    ): ImmutableProp {
        val list = kind == AssociationKind.ONE_TO_MANY ||
            kind == AssociationKind.MANY_TO_MANY ||
            kind == AssociationKind.MANY_TO_MANY_VIEW
        val targetType = LsiDeclaredType(targetId)
        val propType = if (list) {
            LsiDeclaredType(
                declarationId = LIST_ID,
                arguments = listOf(LsiTypeArgument.invariant(targetType)),
            )
        } else {
            targetType
        }
        val id = LsiSymbolId.property(ownerId, name)
        return immutableProp(
            id = id,
            declarationId = id,
            ownerId = ownerId,
            declaringTypeId = ownerId,
            name = name,
            type = propType,
            annotations = emptyList(),
            inherited = false,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            association = true,
            list = list,
            targetTypeId = targetId,
            associationKind = kind,
            associationStorage = storage,
            mappedBy = mappedBy,
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
        inherited: Boolean,
        primaryMapping: PrimaryMapping,
        primaryAnnotationTypeId: LsiSymbolId? = null,
        association: Boolean = false,
        list: Boolean = false,
        targetTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
        mappedBy: MappedBy? = null,
        view: ImmutableView? = null,
        formulaKind: FormulaKind = FormulaKind.NONE,
        formulaDependencies: List<FormulaDependency> = emptyList(),
        transientResolver: TransientResolver? = null,
        validations: List<ImmutableValidation> = emptyList(),
        converter: ImmutableConverter? = null,
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
            overrideChain = listOf(declarationId),
            inherited = inherited,
            overridden = false,
            nullable = type.nullability == LsiNullability.NULLABLE,
            list = list,
            association = association,
            embedded = false,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = primaryAnnotationTypeId,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = formulaKind,
            mappedBy = mappedBy,
            associationStorage = associationStorage,
            transientResolver = transientResolver,
            view = view,
            genericTarget = false,
            remote = false,
            recursive = association && targetTypeId == ownerId,
            validations = validations,
            converter = converter,
            formulaDependencies = formulaDependencies,
        )
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
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

    private fun typeId(qualifiedName: String): LsiSymbolId {
        return LsiSymbolId.type(qualifiedName)
    }

    private companion object {
        val ID_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val LIST_ID = LsiSymbolId.type("java.util.List")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val INT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.INT)
    }
}
