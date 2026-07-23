package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureState
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
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.jimmer.dto.DtoBaseProp

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

        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, state.status)
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
            reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
        }
        val missingTypeId = reusableReference.typeSelector.fallbackTypeId
        val workspace = workspace()

        val aptResult = precompileSnapshots(
            snapshots = listOf(snapshot),
            workspace = workspace,
            platform = CompilerPlatform.APT,
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.DEFERRED, aptResult.dtoState().status)
        assertEquals(setOf(missingTypeId), aptResult.unresolvedSymbols)
        assertTrue(aptResult.diagnostics.isEmpty())

        val kspResult = precompileSnapshots(
            snapshots = listOf(snapshot),
            workspace = workspace,
            platform = CompilerPlatform.KSP,
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.PENDING, kspResult.dtoState().status)
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

            assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, finalState.status)
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

        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, state.status)
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
            reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
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
        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, state.status)
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
            reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
        }.location

        val result = precompileDocuments(
            documents = listOf(input),
            workspace = workspace(externalDeclarations = listOf(invalidEntity, invalidDto)),
            platform = CompilerPlatform.APT,
        )
        val state = result.dtoState()
        val failure = state.failures.single()

        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, state.status)
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
    ): JimmerCompilerFeaturePrecompileResult {
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
    ): JimmerCompilerFeaturePrecompileResult {
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
        val immutableState = JimmerImmutableCompilerFeatureState(
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
        return JimmerDtoCompilerFeatureProvider().precompile(
            JimmerCompilerPrecompileContext(
                session = CompilerSessionSnapshot("dto-reusable-integration", emptyList()),
                round = round,
                collection = JimmerCompilerFeatureCollection(),
                previousState = null,
                dependencyStates = mapOf("immutable" to immutableState),
            )
        )
    }

    private fun workspace(
        externalDeclarations: List<LsiTypeDeclaration> = emptyList(),
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
                site.addzero.lsi.model.LsiProperty(
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
    ): LsiTypeDeclaration {
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

    private fun typeDeclaration(
        id: LsiSymbolId,
        kind: LsiTypeDeclarationKind,
        origin: LsiOrigin,
        annotations: List<LsiAnnotation> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
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
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "dto-reusable-integration",
            sourceRoot = "src/main/dto",
            relativePath = relativePath,
            content = content,
        )
    }

    private fun freeze(document: CompilerInputDocument): CompilerInputDocumentSnapshot {
        return REFERENCE_FREEZER.freeze(document)
    }

    private fun JimmerCompilerFeaturePrecompileResult.dtoState(): JimmerDtoCompilerFeatureState {
        return state as JimmerDtoCompilerFeatureState
    }

    private companion object {
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val NOT_IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.NotImmutable")
        val ENTITY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
        val LONG_TYPE: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE: LsiTypeRef = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
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
            type: LsiTypeRef,
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
