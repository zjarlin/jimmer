package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.input.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerSourceSet
import site.addzero.lsi.compiler.CompilerSessionSnapshot
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureStates
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import org.babyfish.jimmer.compiler.immutable.ImmutableFeatureState
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenOptions
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenPrecompiler
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoTypeKind

class JimmerDtoReusableIntegrationTest {

    @Test
    fun `links a same batch reusable dto declared in another document`() {
        val bookDocument = document(
            relativePath = "book/BookViews.dto",
            content = """
                package demo.dto

                BookView for demo.Book {
                    id
                    store -> StoreView
                }
            """.trimIndent(),
        )
        val storeDocument = document(
            relativePath = "store/StoreViews.dto",
            content = """
                package demo.dto

                StoreView for demo.Store {
                    id
                    name
                }
            """.trimIndent(),
        )

        val result = precompileDocuments(
            documents = listOf(bookDocument, storeDocument),
            workspace = workspace(),
            platform = CompilerPlatform.APT,
        )
        val state = result.dtoState()

        assertEquals(DtoFeatureStatus.RESOLVED, state.status)
        assertTrue(state.unresolvedDocuments.isEmpty())
        assertTrue(state.failures.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
        val bookGraph = state.graphs.single { graph -> graph.source == bookDocument.source }
        val bookView = bookGraph.typesById.getValue(bookGraph.rootTypeIds.single())
        val storeProp = bookView.propIds
            .map(bookGraph.propsById::getValue)
            .single { prop -> prop.name == "store" } as DtoBaseProp
        val storeView = bookGraph.typesById.getValue(assertNotNull(storeProp.targetTypeId))
        assertEquals("StoreView", storeView.name)
        assertEquals(STORE_TYPE_ID, storeView.baseTypeId)
        assertEquals(storeDocument.source, storeView.location.source)
        assertEquals("demo.dto.StoreView", storeProp.targetTypeReference?.qualifiedName)
        assertEquals(STORE_TYPE_ID, storeProp.targetTypeReference?.targetBaseTypeId)
        assertEquals(DtoTypeKind.VIEW, storeProp.targetTypeReference?.kind)
        assertEquals(bookDocument.source, storeProp.targetTypeReference?.location?.source)
    }

    @Test
    fun `stages a missing reusable dto by platform and reports its final location`() {
        val input = document(
            relativePath = "book/MissingStoreView.dto",
            content = """
                package demo.dto

                BookView for demo.Book {
                    store -> MissingStoreView
                }
            """.trimIndent(),
        )
        val snapshot = freeze(input)
        val reusableReference = snapshot.references.single { reference ->
            reference.kind == DTO_REUSABLE_TYPE_REFERENCE_KIND
        }
        val missingTypeId = reusableReference.typeSelector.fallbackTypeId
        val workspace = workspace()

        val aptResult = precompileSnapshots(
            snapshots = listOf(snapshot),
            workspace = workspace,
            platform = CompilerPlatform.APT,
        )
        assertEquals(DtoFeatureStatus.DEFERRED, aptResult.dtoState().status)
        assertEquals(setOf(missingTypeId), aptResult.unresolvedSymbols)
        assertTrue(aptResult.diagnostics.isEmpty())

        val kspResult = precompileSnapshots(
            snapshots = listOf(snapshot),
            workspace = workspace,
            platform = CompilerPlatform.KSP,
        )
        assertEquals(DtoFeatureStatus.PENDING, kspResult.dtoState().status)
        assertTrue(kspResult.unresolvedSymbols.isEmpty())
        assertTrue(kspResult.diagnostics.isEmpty())

        listOf(CompilerPlatform.APT, CompilerPlatform.KSP).forEach { platform ->
            val finalResult = precompileSnapshots(
                snapshots = listOf(snapshot),
                workspace = workspace,
                platform = platform,
                isFinal = true,
            )
            val finalState = finalResult.dtoState()
            val diagnostic = finalResult.diagnostics.single()

            assertEquals(DtoFeatureStatus.INVALID, finalState.status)
            assertEquals(listOf(missingTypeId), finalState.unresolvedDocuments.single().unresolvedTypeIds)
            assertTrue(finalResult.unresolvedSymbols.isEmpty())
            assertEquals("jimmer.dto.unresolved", diagnostic.code)
            assertEquals(LsiDiagnosticSeverity.ERROR, diagnostic.severity)
            assertEquals(missingTypeId, diagnostic.symbolId)
            assertEquals(reusableReference.location, diagnostic.location)
        }
    }

    @Test
    fun `resolves an external reusable dto through lsi inheritance`() {
        val externalDto = externalDto(
            qualifiedName = "contract.StoreView",
            entityTypeId = STORE_TYPE_ID,
        )
        val result = precompileDocuments(
            documents = listOf(
                document(
                    relativePath = "book/ExternalStoreView.dto",
                    content = """
                        package demo.dto

                        BookView for demo.Book {
                            store -> contract.StoreView
                        }
                    """.trimIndent(),
                )
            ),
            workspace = workspace(externalDeclarations = listOf(externalDto)),
            platform = CompilerPlatform.APT,
        )
        val state = result.dtoState()

        assertEquals(DtoFeatureStatus.RESOLVED, state.status)
        assertTrue(state.unresolvedDocuments.isEmpty())
        assertTrue(state.failures.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
        assertEquals(setOf(BOOK_TYPE_ID), result.processedSymbols)
        val graph = state.graphs.single()
        val rootType = graph.typesById.getValue(graph.rootTypeIds.single())
        val storeProp = rootType.propIds
            .map(graph.propsById::getValue)
            .single { prop -> prop.name == "store" } as DtoBaseProp
        assertNull(storeProp.targetTypeId)
        assertEquals("contract.StoreView", storeProp.targetTypeReference?.qualifiedName)
        assertEquals(STORE_TYPE_ID, storeProp.targetTypeReference?.targetBaseTypeId)
        assertEquals(DtoTypeKind.VIEW, storeProp.targetTypeReference?.kind)
    }

    @Test
    fun `routes external reusable specification by compiler target language`() {
        val input = document(
            relativePath = "book/ExternalStoreSpecification.dto",
            content = """
                package demo.dto

                specification BookSpecification for demo.Book {
                    store -> contract.StoreSpecification
                }
            """.trimIndent(),
        )

        listOf(CompilerPlatform.APT, CompilerPlatform.KSP).forEach { platform ->
            val externalSpecification = externalSpecification(
                qualifiedName = "contract.StoreSpecification",
                entityTypeId = STORE_TYPE_ID,
                platform = platform,
            )
            val result = precompileDocuments(
                documents = listOf(input),
                workspace = workspace(externalDeclarations = listOf(externalSpecification)),
                platform = platform,
            )
            val state = result.dtoState()
            val graph = state.graphs.single()
            val rootType = graph.typesById.getValue(graph.rootTypeIds.single())
            val storeProp = rootType.propIds
                .map(graph.propsById::getValue)
                .single { prop -> prop.name == "store" } as DtoBaseProp

            assertEquals(DtoFeatureStatus.RESOLVED, state.status)
            assertTrue(state.unresolvedDocuments.isEmpty())
            assertTrue(state.failures.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
            assertTrue(DtoModifier.SPECIFICATION in rootType.modifiers)
            assertNull(storeProp.targetTypeId)
            assertEquals("contract.StoreSpecification", storeProp.targetTypeReference?.qualifiedName)
            assertEquals(STORE_TYPE_ID, storeProp.targetTypeReference?.targetBaseTypeId)
            assertEquals(DtoTypeKind.SPECIFICATION, storeProp.targetTypeReference?.kind)
        }
    }

    @Test
    fun `classifies ambiguous and unresolved reusable references as invalid without retaining unresolved source`() {
        val input = document(
            relativePath = "book/MixedInvalidReusable.dto",
            content = """
                package demo.dto
                import first.*
                import second.*

                AmbiguousBookView for demo.Book {
                    store -> StoreView
                }

                MissingBookView for demo.Book {
                    store -> MissingStoreView
                }
            """.trimIndent(),
        )
        val snapshot = freeze(input)
        val reusableReferences = snapshot.references.filter { reference ->
            reference.kind == DTO_REUSABLE_TYPE_REFERENCE_KIND
        }
        val workspace = workspace(
            externalDeclarations = listOf(
                externalDto("first.StoreView", STORE_TYPE_ID),
                externalDto("second.StoreView", STORE_TYPE_ID),
            )
        )

        val result = precompileSnapshots(
            snapshots = listOf(snapshot),
            workspace = workspace,
            platform = CompilerPlatform.APT,
        )
        val state = result.dtoState()
        val failure = state.failures.single()

        assertEquals(2, reusableReferences.size)
        assertEquals(DtoFeatureStatus.INVALID, state.status)
        assertTrue(state.graphs.isEmpty())
        assertTrue(state.unresolvedDocuments.isEmpty())
        assertEquals("jimmer.dto.invalid", failure.code)
        assertTrue(failure.message.contains("Ambiguous type name \"StoreView\""), failure.message)
        assertEquals(listOf(failure.code), result.diagnostics.map { diagnostic -> diagnostic.code })
        assertFalse(BOOK_TYPE_ID in result.processedSymbols)
        assertTrue(result.unresolvedSymbols.isEmpty())
    }

    @Test
    fun `reports an invalid external reusable entity argument as dto invalid`() {
        val invalidEntity = typeDeclaration(
            id = NOT_IMMUTABLE_TYPE_ID,
            kind = LsiTypeDeclarationKind.CLASS,
            origin = BINARY_ORIGIN,
        )
        val invalidDto = externalDto(
            qualifiedName = "contract.InvalidStoreView",
            entityTypeId = NOT_IMMUTABLE_TYPE_ID,
        )
        val input = document(
            relativePath = "book/InvalidExternalStoreView.dto",
            content = """
                package demo.dto

                BookView for demo.Book {
                    store -> contract.InvalidStoreView
                }
            """.trimIndent(),
        )
        val reusableLocation = freeze(input).references.single { reference ->
            reference.kind == DTO_REUSABLE_TYPE_REFERENCE_KIND
        }.location

        val result = precompileDocuments(
            documents = listOf(input),
            workspace = workspace(externalDeclarations = listOf(invalidEntity, invalidDto)),
            platform = CompilerPlatform.APT,
        )
        val state = result.dtoState()
        val failure = state.failures.single()

        assertEquals(DtoFeatureStatus.INVALID, state.status)
        assertTrue(state.unresolvedDocuments.isEmpty())
        assertEquals("jimmer.dto.invalid", failure.code)
        assertEquals(LsiDiagnosticSeverity.ERROR, failure.severity)
        assertEquals(listOf(BOOK_TYPE_ID), failure.targetTypeIds)
        assertNull(failure.symbolId)
        assertEquals(reusableLocation, failure.location)
        assertTrue(
            failure.message.contains(
                "The entity type argument of reusable DTO type \"contract.InvalidStoreView\" " +
                    "is not an immutable type"
            ),
            failure.message,
        )
        assertEquals(listOf(failure.code), result.diagnostics.map { diagnostic -> diagnostic.code })
        assertFalse(BOOK_TYPE_ID in result.processedSymbols)
    }

    private fun precompileDocuments(
        documents: List<CompilerInputDocument>,
        workspace: LsiWorkspace,
        platform: CompilerPlatform,
        isFinal: Boolean = false,
    ): CompilerFeaturePrecompileResult<DtoFeatureState> {
        return precompileSnapshots(
            snapshots = documents.map(::freeze),
            workspace = workspace,
            platform = platform,
            isFinal = isFinal,
        )
    }

    private fun precompileSnapshots(
        snapshots: List<CompilerInputDocumentSnapshot>,
        workspace: LsiWorkspace,
        platform: CompilerPlatform,
        isFinal: Boolean = false,
    ): CompilerFeaturePrecompileResult<DtoFeatureState> {
        val currentTypeIds = if (isFinal) {
            emptySet()
        } else {
            setOf(BOOK_TYPE_ID, STORE_TYPE_ID)
        }
        val round = CompilerRound(
            number = 0,
            workspace = workspace,
            currentWorkspace = workspace,
            currentRootTypeIds = currentTypeIds,
            platform = platform,
            isFinal = isFinal,
            inputDocumentSnapshots = snapshots.sorted(),
        )
        val immutableState = ImmutableFeatureState(
            schema = IMMUTABLE_SCHEMA,
            draftCodegenSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
                schema = IMMUTABLE_SCHEMA,
                workspace = workspace,
                options = JimmerImmutableDraftCodegenOptions.DEFAULT,
            ),
            targetTypeIds = setOf(BOOK_TYPE_ID, STORE_TYPE_ID),
            semanticRootTypeIds = setOf(BOOK_TYPE_ID, STORE_TYPE_ID),
            currentTypeIds = currentTypeIds,
        )
        return DtoFeature().precompile(
            CompilerPrecompileContext(
                session = CompilerSessionSnapshot("dto-reusable-integration", emptyList()),
                round = round,
                collection = CompilerFeatureCollection(EmptyCompilerFeatureState),
                previousState = null,
                dependencyStates = CompilerFeatureStates(
                    mapOf(ImmutableFeature.Key to immutableState)
                ),
            )
        )
    }

    private fun workspace(
        externalDeclarations: List<LsiClass> = emptyList(),
    ): LsiWorkspace {
        val modelSource = LsiSource.of("demo/Models.kt", LsiLanguage.KOTLIN)
        val modelOrigin = LsiOrigin(LsiOriginKind.SOURCE, modelSource)
        val book = typeDeclaration(
            id = BOOK_TYPE_ID,
            kind = LsiTypeDeclarationKind.INTERFACE,
            origin = modelOrigin,
            annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION_TYPE_ID)),
            memberIds = IMMUTABLE_SCHEMA.typesById.getValue(BOOK_TYPE_ID)
                .props
                .map(ImmutableProp::id),
        )
        val store = typeDeclaration(
            id = STORE_TYPE_ID,
            kind = LsiTypeDeclarationKind.INTERFACE,
            origin = modelOrigin,
            annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION_TYPE_ID)),
            memberIds = IMMUTABLE_SCHEMA.typesById.getValue(STORE_TYPE_ID)
                .props
                .map(ImmutableProp::id),
        )
        val properties = IMMUTABLE_SCHEMA.types.flatMap { type ->
            type.props.map { prop ->
                site.addzero.lsi.field.LsiProperty(
                    id = prop.declarationId,
                    name = prop.name,
                    ownerId = type.id,
                    type = prop.type,
                    origin = modelOrigin,
                )
            }
        }
        return LsiWorkspace(
            sources = listOf(modelSource),
            declarations = listOf(book, store) + properties + externalDeclarations,
        )
    }

    private fun externalDto(
        qualifiedName: String,
        entityTypeId: LsiSymbolId,
    ): LsiClass {
        return typeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            kind = LsiTypeDeclarationKind.INTERFACE,
            origin = BINARY_ORIGIN,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = VIEW_TYPE_ID,
                    arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(entityTypeId))),
                )
            ),
        )
    }

    private fun externalSpecification(
        qualifiedName: String,
        entityTypeId: LsiSymbolId,
        platform: CompilerPlatform,
    ): LsiClass {
        val markerTypeId = when (platform) {
            CompilerPlatform.APT -> J_SPECIFICATION_TYPE_ID
            CompilerPlatform.KSP -> K_SPECIFICATION_TYPE_ID
            CompilerPlatform.UNKNOWN -> error("Unsupported DTO compiler platform")
        }
        val arguments = buildList {
            add(LsiTypeArgument.invariant(LsiDeclaredType(entityTypeId)))
            if (platform == CompilerPlatform.APT) {
                add(LsiTypeArgument.invariant(LsiDeclaredType(STORE_TABLE_TYPE_ID)))
            }
        }
        return typeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            kind = LsiTypeDeclarationKind.INTERFACE,
            origin = BINARY_ORIGIN,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = markerTypeId,
                    arguments = arguments,
                )
            ),
        )
    }

    private fun typeDeclaration(
        id: LsiSymbolId,
        kind: LsiTypeDeclarationKind,
        origin: LsiOrigin,
        annotations: List<LsiAnnotation> = emptyList(),
        superTypes: List<LsiType> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
    ): LsiClass {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiClass(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            modality = LsiModality.ABSTRACT,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun document(
        relativePath: String,
        content: String,
    ): CompilerInputDocument {
        return CompilerInputDocument(
            kind = DTO_INPUT_DOCUMENT_KIND,
            sourceSet = CompilerSourceSet.MAIN,
            origin = CompilerInputDocumentOrigin.Project("dto-reusable-integration", "src/main/dto"),
            relativePath = relativePath,
            content = content,
        )
    }

    private fun freeze(document: CompilerInputDocument): CompilerInputDocumentSnapshot {
        return REFERENCE_FREEZER.freeze(document)
    }

    private fun CompilerFeaturePrecompileResult<DtoFeatureState>.dtoState(): DtoFeatureState {
        return state
    }

    private companion object {
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val NOT_IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.NotImmutable")
        val ENTITY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
        val J_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")
        val K_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")
        val STORE_TABLE_TYPE_ID = LsiSymbolId.type("demo.StoreTable")
        val LONG_TYPE: LsiType = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE: LsiType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val BINARY_ORIGIN = LsiOrigin(LsiOriginKind.BINARY)
        val REFERENCE_FREEZER = CompilerInputDocumentReferenceFreezer()

        val IMMUTABLE_SCHEMA = ImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_TYPE_ID,
                    props = listOf(
                        prop(
                            ownerTypeId = BOOK_TYPE_ID,
                            name = "id",
                            type = LONG_TYPE,
                            primaryMapping = PrimaryMapping.ID,
                        ),
                        prop(
                            ownerTypeId = BOOK_TYPE_ID,
                            name = "store",
                            type = LsiDeclaredType(STORE_TYPE_ID),
                            primaryMapping = PrimaryMapping.ASSOCIATION,
                            associationKind = AssociationKind.MANY_TO_ONE,
                        ),
                    ),
                ),
                immutableType(
                    id = STORE_TYPE_ID,
                    props = listOf(
                        prop(
                            ownerTypeId = STORE_TYPE_ID,
                            name = "id",
                            type = LONG_TYPE,
                            primaryMapping = PrimaryMapping.ID,
                        ),
                        prop(
                            ownerTypeId = STORE_TYPE_ID,
                            name = "name",
                            type = STRING_TYPE,
                        ),
                    ),
                ),
            )
        )

        fun immutableType(
            id: LsiSymbolId,
            props: List<ImmutableProp>,
        ): ImmutableType {
            val completeProps = completeEntityProps(id, props)
            return ImmutableType(
                id = id,
                qualifiedName = id.requireTypeQualifiedName(),
                kind = ImmutableTypeKind.ENTITY,
                documentation = null,
                annotations = emptyList(),
                typeParameterIds = emptyList(),
                superTypeIds = emptyList(),
                props = completeProps,
                primarySuperTypeId = null,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = true,
                discriminatorValue = null,
                discriminatorPropId = null,
                idPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.ID
                }?.id,
                versionPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.VERSION
                }?.id,
                logicalDeletedPropId = completeProps.singleOrNull { prop ->
                    prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED
                }?.id,
                acrossMicroServices = false,
                microServiceName = "",
            )
        }

        fun prop(
            ownerTypeId: LsiSymbolId,
            name: String,
            type: LsiType,
            primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
            associationKind: AssociationKind = AssociationKind.NONE,
        ): ImmutableProp {
            val id = LsiSymbolId.property(ownerTypeId, name)
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
                association = associationKind != AssociationKind.NONE,
                embedded = false,
                targetTypeId = if (associationKind == AssociationKind.NONE) {
                    null
                } else {
                    (type as LsiDeclaredType).declarationId
                },
                primaryMapping = primaryMapping,
                primaryAnnotationTypeId = null,
                defaultContract = null,
                associationKind = associationKind,
                formulaKind = FormulaKind.NONE,
                mappedBy = null,
                associationStorage = when (associationKind) {
                    AssociationKind.ONE_TO_ONE,
                    AssociationKind.MANY_TO_ONE,
                    -> AssociationStorageKind.COLUMN
                    AssociationKind.MANY_TO_MANY -> AssociationStorageKind.MIDDLE_TABLE
                    else -> AssociationStorageKind.NONE
                },
                transientResolver = null,
                view = null,
                genericTarget = false,
                remote = false,
                recursive = false,
                validations = emptyList(),
                converter = null,
            )
        }
    }
}
