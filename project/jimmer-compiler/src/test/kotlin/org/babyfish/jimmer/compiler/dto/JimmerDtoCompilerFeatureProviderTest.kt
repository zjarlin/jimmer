package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerRoundResult
import org.babyfish.jimmer.compiler.CompilerSession
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import org.babyfish.jimmer.compiler.immutable.JimmerAssociationKind
import org.babyfish.jimmer.compiler.immutable.JimmerFormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureProvider
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableView
import org.babyfish.jimmer.compiler.immutable.JimmerInheritanceStrategy
import org.babyfish.jimmer.compiler.immutable.JimmerJoinedTableDissociateAction
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class JimmerDtoCompilerFeatureProviderTest {
    @Test
    fun `registered dto feature consumes immutable state and dto documents`() {
        val providers = JimmerCompilerFeatureProviders.load()
        val featureIds = providers.map { provider -> provider.descriptor.id }
        val provider = providers.single { candidate -> candidate.descriptor.id == DTO_FEATURE_ID }

        assertEquals(setOf(IMMUTABLE_FEATURE_ID), provider.descriptor.dependsOn)
        assertEquals(setOf(CompilerInputDocumentKind.DTO), provider.descriptor.inputDocumentKinds)
        assertTrue(featureIds.indexOf(IMMUTABLE_FEATURE_ID) < featureIds.indexOf(DTO_FEATURE_ID))
    }

    @Test
    fun `precompiles simple dto into platform independent semantic model`() {
        val schema = JimmerImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_ID,
                    props = listOf(
                        prop(BOOK_ID, "id", LONG_TYPE, JimmerImmutablePrimaryMapping.ID),
                        prop(BOOK_ID, "name", STRING_TYPE),
                    ),
                )
            )
        )
        val inputSnapshot = REFERENCE_FREEZER.freeze(bookDocument("BookView { id name }"))
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(inputSnapshot),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableWorkspace(LsiLanguage.UNKNOWN),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        )

        assertTrue(outcome.unresolvedDocuments.isEmpty())
        assertTrue(outcome.failures.isEmpty())
        val document = outcome.schema.documents.single()
        assertEquals(inputSnapshot, document.inputSnapshot)
        val dtoType = document.dtoTypes.single()
        assertEquals(BOOK_ID, document.baseTypeId)
        assertEquals("demo.Book", document.sourceTypeName)
        assertEquals("demo.dto", dtoType.packageName)
        assertEquals("BookView", dtoType.name)
        assertEquals(listOf("id", "name"), dtoType.props.map { prop -> prop.name })
        assertEquals(BOOK_ID, dtoType.baseType.id)
    }

    @Test
    fun `dto base props consume typed immutable view links`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val linkId = LsiSymbolId.type("demo.BookAuthor")
        val authorId = LsiSymbolId.type("demo.Author")
        val storeIdProp = prop(
            storeId,
            "id",
            LONG_TYPE,
            JimmerImmutablePrimaryMapping.ID,
        )
        val storeProp = prop(
            ownerTypeId = BOOK_ID,
            name = "store",
            type = LsiDeclaredType(storeId),
            primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
            association = true,
            associationKind = JimmerAssociationKind.MANY_TO_ONE,
        )
        val storeIdViewProp = prop(
            ownerTypeId = BOOK_ID,
            name = "storeId",
            type = LONG_TYPE,
            primaryMapping = JimmerImmutablePrimaryMapping.VIEW,
            targetTypeId = null,
            view = JimmerImmutableView.Id(storeProp.id, storeIdProp.id),
        )
        val linksProp = prop(
            ownerTypeId = BOOK_ID,
            name = "links",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(linkId))),
            ),
            primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
            list = true,
            association = true,
            targetTypeId = linkId,
            associationKind = JimmerAssociationKind.ONE_TO_MANY,
        )
        val deeperProp = prop(
            ownerTypeId = linkId,
            name = "author",
            type = LsiDeclaredType(authorId),
            primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
            association = true,
            associationKind = JimmerAssociationKind.MANY_TO_ONE,
        )
        val authorsViewProp = prop(
            ownerTypeId = BOOK_ID,
            name = "authors",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(authorId))),
            ),
            primaryMapping = JimmerImmutablePrimaryMapping.VIEW,
            list = true,
            association = true,
            targetTypeId = authorId,
            associationKind = JimmerAssociationKind.MANY_TO_MANY_VIEW,
            view = JimmerImmutableView.ManyToMany(linksProp.id, deeperProp.id),
        )
        val schema = JimmerImmutableSchema(
            listOf(
                immutableType(storeId, props = listOf(storeIdProp)),
                immutableType(authorId, props = emptyList()),
                immutableType(linkId, props = listOf(deeperProp)),
                immutableType(
                    BOOK_ID,
                    props = listOf(storeProp, storeIdViewProp, linksProp, authorsViewProp),
                ),
            )
        )
        val registry = LsiDtoTypeRegistry(schema, LsiWorkspace.EMPTY)
        val book = requireNotNull(registry[BOOK_ID])

        assertEquals(storeProp.id, book.props.getValue("storeId").idViewBaseProp?.id)
        assertEquals(linksProp.id, book.props.getValue("authors").manyToManyViewBaseProp?.id)
    }

    @Test
    fun `uses immutable inheritance metadata for polymorphic dto branches`() {
        val idProp = prop(CLIENT_ID, "id", LONG_TYPE, JimmerImmutablePrimaryMapping.ID)
        val discriminatorProp = prop(
            ownerTypeId = CLIENT_ID,
            name = "type",
            type = STRING_TYPE,
            primaryMapping = JimmerImmutablePrimaryMapping.DISCRIMINATOR,
        )
        val nameProp = prop(CLIENT_ID, "name", STRING_TYPE)
        val root = immutableType(
            id = CLIENT_ID,
            props = listOf(idProp, discriminatorProp, nameProp),
            instantiable = false,
            inheritanceRootTypeId = CLIENT_ID,
            inheritanceStrategy = JimmerInheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JimmerJoinedTableDissociateAction.DELETE,
            discriminatorPropId = discriminatorProp.id,
        )
        val inheritedId = idProp.inheritedBy(ORGANIZATION_ID)
        val inheritedDiscriminator = discriminatorProp.inheritedBy(ORGANIZATION_ID)
        val inheritedName = nameProp.inheritedBy(ORGANIZATION_ID)
        val organization = immutableType(
            id = ORGANIZATION_ID,
            props = listOf(
                inheritedId,
                inheritedDiscriminator,
                inheritedName,
                prop(ORGANIZATION_ID, "taxCode", STRING_TYPE),
            ),
            superTypeIds = listOf(CLIENT_ID),
            primarySuperTypeId = CLIENT_ID,
            inheritanceRootTypeId = CLIENT_ID,
            instantiable = true,
            discriminatorValue = "ORGANIZATION",
            discriminatorPropId = inheritedDiscriminator.id,
        )
        val schema = JimmerImmutableSchema(listOf(root, organization))
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(
                REFERENCE_FREEZER.freeze(
                    document(
                        relativePath = "demo/Client.dto",
                        content = """
                            ClientView {
                                id
                                #types {
                                    Organization {
                                        taxCode
                                    }
                                }
                            }
                        """.trimIndent(),
                    )
                ),
            ),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(CLIENT_ID, ORGANIZATION_ID),
            workspace = immutableHeaderWorkspace(listOf(CLIENT_ID, ORGANIZATION_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        )

        assertTrue(outcome.failures.isEmpty())
        val dtoType = outcome.schema.documents.single().dtoTypes.single()
        val polymorphism = assertNotNull(dtoType.polymorphism)
        assertFalse(polymorphism.isExhaustive())
        assertTrue(assertNotNull(polymorphism.defaultBranch).isImplicit())
        val branch = polymorphism.typeBranches.single()
        assertEquals(ORGANIZATION_ID, branch.targetType?.id)
        assertEquals(CLIENT_ID, branch.targetType?.immutableType?.primarySuperTypeId)
        assertEquals(CLIENT_ID, branch.targetType?.immutableType?.inheritanceRootTypeId)
        assertEquals(listOf("taxCode"), branch.dtoType.props.map { prop -> prop.name })
        assertTrue("#types" in outcome.schema.normalizedSnapshot())
    }

    @Test
    fun `sorts multiple documents and includes frozen content in fingerprint`() {
        val schema = JimmerImmutableSchema(
            listOf(
                immutableType(
                    id = AUTHOR_ID,
                    props = listOf(prop(AUTHOR_ID, "id", LONG_TYPE, JimmerImmutablePrimaryMapping.ID)),
                ),
                immutableType(
                    id = BOOK_ID,
                    props = listOf(
                        prop(BOOK_ID, "id", LONG_TYPE, JimmerImmutablePrimaryMapping.ID),
                        prop(BOOK_ID, "name", STRING_TYPE),
                    ),
                ),
            )
        )
        val author = document("demo/Author.dto", "AuthorView { id }")
        val book = bookDocument("BookView { id name }")
        val precompiler = JimmerDtoPrecompiler()

        val first = precompiler.compile(
            inputDocumentSnapshots = listOf(book, author).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(AUTHOR_ID, BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(AUTHOR_ID, BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema
        val reversed = precompiler.compile(
            inputDocumentSnapshots = listOf(author, book).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(AUTHOR_ID, BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(AUTHOR_ID, BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema
        val whitespaceChanged = precompiler.compile(
            inputDocumentSnapshots = listOf(
                author,
                bookDocument("BookView { id name }\n"),
            ).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(AUTHOR_ID, BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(AUTHOR_ID, BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema

        assertEquals(listOf("Author.dto", "Book.dto"), first.documents.map {
            precompiled -> precompiled.inputSnapshot.document.relativePath.substringAfterLast('/')
        })
        assertEquals(first.normalizedSnapshot(), reversed.normalizedSnapshot())
        assertEquals(first.fingerprint(), reversed.fingerprint())
        assertNotEquals(first.fingerprint(), whitespaceChanged.fingerprint())
    }

    @Test
    fun `keeps empty dto document in stable snapshot`() {
        val schema = JimmerImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_ID,
                    props = listOf(prop(BOOK_ID, "id", LONG_TYPE, JimmerImmutablePrimaryMapping.ID)),
                )
            )
        )
        val precompiler = JimmerDtoPrecompiler()
        val empty = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument(""))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema
        val same = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument(""))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema
        val whitespaceChanged = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument("\n"))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
        ).schema

        assertTrue(empty.documents.single().dtoTypes.isEmpty())
        assertTrue("document|" in empty.normalizedSnapshot())
        assertEquals(empty.normalizedSnapshot(), same.normalizedSnapshot())
        assertEquals(empty.fingerprint(), same.fingerprint())
        assertNotEquals(empty.fingerprint(), whitespaceChanged.fingerprint())
    }

    @Test
    fun `apt defers missing base type and resolves it in a later real round`() {
        val session = session("dto-apt-rounds")
        val document = bookDocument("BookView { id name }")

        val first = session.execute(
            round(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
            )
        )
        val firstState = first.dtoState()
        assertEquals(JimmerDtoCompilerFeatureStatus.DEFERRED, firstState.status)
        assertEquals(setOf(BOOK_ID), first.unresolvedSymbols)
        assertTrue(first.diagnostics.isEmpty())

        val workspace = immutableWorkspace(LsiLanguage.JAVA)
        val second = session.execute(
            round(
                number = 1,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
            )
        )
        val secondState = second.dtoState()
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, secondState.status)
        assertEquals(listOf("BookView"), secondState.schema.documents.single().dtoTypes.map { type -> type.name })
        assertEquals(setOf(BOOK_ID), second.dtoResult().processedSymbols)
        assertTrue(second.dtoResult().artifacts.isEmpty())
    }

    @Test
    fun `ksp keeps missing base type pending without forcing another round and resolves when one occurs`() {
        val document = bookDocument("BookView { id }")
        val session = session("dto-ksp-rounds")
        val first = session.execute(
            round(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
            )
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.PENDING, first.dtoState().status)
        assertEquals(listOf(BOOK_ID), first.dtoState().unresolvedDocuments.map { it.baseTypeId })
        assertTrue(first.diagnostics.isEmpty())
        assertTrue(first.unresolvedSymbols.isEmpty())

        val workspace = immutableWorkspace(LsiLanguage.KOTLIN)
        val second = session.execute(
            round(
                number = 1,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
            )
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, second.dtoState().status)
        assertEquals(listOf("BookView"), second.dtoState().schema.documents.single().dtoTypes.map { it.name })
        assertEquals(setOf(BOOK_ID), second.dtoResult().processedSymbols)
    }

    @Test
    fun `ksp and apt final round report missing dto base type as invalid`() {
        val document = bookDocument("BookView { id }")
        val kspFinal = session("dto-ksp-final-missing").execute(
            round(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.KSP,
                isFinal = true,
                inputDocuments = listOf(document),
            )
        )
        val aptFinal = session("dto-apt-final-missing").execute(
            round(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.APT,
                isFinal = true,
                inputDocuments = listOf(document),
            )
        )

        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, kspFinal.dtoState().status)
        assertEquals("jimmer.dto.unresolved", kspFinal.diagnostics.single().code)
        assertTrue(kspFinal.unresolvedSymbols.isEmpty())
        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, aptFinal.dtoState().status)
        assertEquals("jimmer.dto.unresolved", aptFinal.diagnostics.single().code)
        assertTrue(aptFinal.unresolvedSymbols.isEmpty())
    }

    @Test
    fun `frozen missing dto references follow apt ksp and final round lifecycle`() {
        missingReferenceFixtures().forEach { fixture ->
            val snapshot = REFERENCE_FREEZER.freeze(fixture.document)
            assertEquals(
                listOf(fixture.missingTypeId),
                snapshot.references
                    .filter { reference -> reference.kind == fixture.kind }
                    .map { reference -> reference.typeId },
            )

            val apt = session("dto-missing-${fixture.kind.name.lowercase()}-apt").execute(
                round(
                    number = 0,
                    workspace = fixture.workspace,
                    currentWorkspace = fixture.workspace,
                    platform = CompilerPlatform.APT,
                    inputDocuments = listOf(fixture.document),
                )
            )
            assertEquals(JimmerDtoCompilerFeatureStatus.DEFERRED, apt.dtoState().status)
            assertEquals(
                listOf(fixture.missingTypeId),
                apt.dtoState().unresolvedDocuments.single().unresolvedTypeIds,
            )
            assertEquals(setOf(fixture.missingTypeId), apt.unresolvedSymbols)
            assertTrue(apt.diagnostics.isEmpty())

            val ksp = session("dto-missing-${fixture.kind.name.lowercase()}-ksp").execute(
                round(
                    number = 0,
                    workspace = fixture.workspace,
                    currentWorkspace = fixture.workspace,
                    platform = CompilerPlatform.KSP,
                    inputDocuments = listOf(fixture.document),
                )
            )
            assertEquals(JimmerDtoCompilerFeatureStatus.PENDING, ksp.dtoState().status)
            assertEquals(1, ksp.dtoState().unresolvedDocuments.size)
            assertEquals(
                listOf(fixture.missingTypeId),
                ksp.dtoState().unresolvedDocuments.single().unresolvedTypeIds,
            )
            assertTrue(fixture.missingTypeId.value in ksp.dtoState().unresolvedDocuments.single().message)
            assertTrue(ksp.unresolvedSymbols.isEmpty())
            assertTrue(ksp.diagnostics.isEmpty())

            listOf(CompilerPlatform.APT, CompilerPlatform.KSP).forEach { platform ->
                val final = session(
                    "dto-missing-${fixture.kind.name.lowercase()}-${platform.name.lowercase()}-final"
                ).execute(
                    round(
                        number = 0,
                        workspace = fixture.workspace,
                        currentWorkspace = LsiWorkspace.EMPTY,
                        currentRootTypeIds = emptySet(),
                        platform = platform,
                        isFinal = true,
                        inputDocuments = listOf(fixture.document),
                    )
                )
                assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, final.dtoState().status)
                assertEquals("jimmer.dto.unresolved", final.diagnostics.single().code)
                assertEquals(fixture.missingTypeId, final.diagnostics.single().symbolId)
                assertEquals(
                    snapshot.references.single { reference -> reference.typeId == fixture.missingTypeId }.location,
                    final.diagnostics.single().location,
                )
                assertTrue(final.unresolvedSymbols.isEmpty())
            }
        }
    }

    @Test
    fun `skips dto documents whose existing immutable base is removed by source filters`() {
        val workspace = immutableWorkspace(LsiLanguage.KOTLIN)
        val optionSets = listOf(
            mapOf("jimmer.source.includes" to "demo.api"),
            mapOf("jimmer.source.excludes" to "demo.Book"),
        )

        optionSets.forEachIndexed { index, options ->
            val result = session("dto-filtered-$index").execute(
                round(
                    number = 0,
                    workspace = workspace,
                    currentWorkspace = workspace,
                    platform = CompilerPlatform.KSP,
                    inputDocuments = listOf(bookDocument("BookView { id name }")),
                    options = options,
                )
            )

            assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().schema.documents.isEmpty())
            assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
            assertTrue(result.dtoState().failures.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
            assertTrue(result.unresolvedSymbols.isEmpty())
        }
    }

    @Test
    fun `source filter skips dto by frozen subject before it enters workspace`() {
        val optionSets = listOf(
            mapOf("jimmer.source.includes" to "demo.api"),
            mapOf("jimmer.source.excludes" to "demo.Book"),
        )

        optionSets.forEachIndexed { index, options ->
            val result = session("dto-filtered-missing-subject-$index").execute(
                round(
                    number = 0,
                    workspace = LsiWorkspace.EMPTY,
                    currentWorkspace = LsiWorkspace.EMPTY,
                    platform = CompilerPlatform.KSP,
                    inputDocuments = listOf(bookDocument("BookView { id }")),
                    options = options,
                )
            )

            assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().schema.documents.isEmpty())
            assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
            assertTrue(result.dtoState().failures.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
            assertTrue(result.unresolvedSymbols.isEmpty())
        }
    }

    @Test
    fun `reports an existing non immutable dto base as invalid`() {
        val workspace = nonImmutableWorkspace(LsiLanguage.KOTLIN)
        val result = session("dto-non-immutable").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(bookDocument("BookView { id name }")),
            )
        )

        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, result.dtoState().status)
        assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
        assertEquals(BOOK_ID, result.dtoState().failures.single().baseTypeId)
        assertEquals("jimmer.dto.invalid", result.diagnostics.single().code)
        assertTrue(result.diagnostics.single().message.contains("is not an immutable type"))
        assertTrue(result.unresolvedSymbols.isEmpty())
    }

    @Test
    fun `skips an existing non immutable dto base before marker validation when excluded`() {
        val workspace = nonImmutableWorkspace(LsiLanguage.KOTLIN)
        val result = session("dto-excluded-non-immutable").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(bookDocument("BookView { id name }")),
                options = mapOf("jimmer.source.excludes" to "demo.Book"),
            )
        )

        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.dtoState().status)
        assertTrue(result.dtoState().schema.documents.isEmpty())
        assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
        assertTrue(result.dtoState().failures.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.unresolvedSymbols.isEmpty())
    }

    @Test
    fun `same frozen lsi produces identical apt and ksp dto snapshots`() {
        val workspace = immutableWorkspace(LsiLanguage.UNKNOWN)
        val inputDocuments = listOf(bookDocument("input BookInput { id name }"))
        val apt = session("dto-parity-apt").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
                inputDocuments = inputDocuments,
            )
        ).dtoState()
        val ksp = session("dto-parity-ksp").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = inputDocuments,
            )
        ).dtoState()

        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, apt.status)
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, ksp.status)
        assertEquals(apt.schema.normalizedSnapshot(), ksp.schema.normalizedSnapshot())
        assertEquals(apt.schema.fingerprint(), ksp.schema.fingerprint())
        assertEquals(apt.fingerprint, ksp.fingerprint)
    }

    private fun session(id: String): CompilerSession {
        return CompilerSession(
            id = id,
            providers = listOf(
                JimmerImmutableCompilerFeatureProvider(),
                JimmerDtoCompilerFeatureProvider(),
            ),
        )
    }

    private fun round(
        number: Int,
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace,
        platform: CompilerPlatform,
        inputDocuments: List<CompilerInputDocument>,
        currentRootTypeIds: Set<LsiSymbolId> = currentWorkspace.declarations
            .filterIsInstance<LsiTypeDeclaration>()
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
        isFinal: Boolean = false,
        options: Map<String, String> = emptyMap(),
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = workspace,
            currentWorkspace = currentWorkspace,
            currentRootTypeIds = currentRootTypeIds,
            platform = platform,
            isFinal = isFinal,
            options = options,
            inputDocumentSnapshots = inputDocuments.map(REFERENCE_FREEZER::freeze),
        )
    }

    private fun CompilerRoundResult.dtoResult() = requireNotNull(featureResults[DTO_FEATURE_ID])

    private fun CompilerRoundResult.dtoState(): JimmerDtoCompilerFeatureState {
        return dtoResult().state as JimmerDtoCompilerFeatureState
    }

    private fun immutableWorkspace(language: LsiLanguage): LsiWorkspace {
        return bookWorkspace(language, listOf(LsiAnnotation(ENTITY_ANNOTATION)))
    }

    private fun nonImmutableWorkspace(language: LsiLanguage): LsiWorkspace {
        return bookWorkspace(language, emptyList())
    }

    private fun immutableHeaderWorkspace(typeIds: Collection<LsiSymbolId>): LsiWorkspace {
        val source = LsiSource.of("demo/Models.kt", LsiLanguage.UNKNOWN)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        return LsiWorkspace(
            sources = listOf(source),
            declarations = typeIds.sorted().map { typeId ->
                val qualifiedName = typeId.requireTypeQualifiedName()
                LsiTypeDeclaration(
                    id = typeId,
                    name = qualifiedName.substringAfterLast('.'),
                    qualifiedName = qualifiedName,
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                    origin = origin,
                )
            },
        )
    }

    private fun bookWorkspace(
        language: LsiLanguage,
        annotations: List<LsiAnnotation>,
    ): LsiWorkspace {
        val source = LsiSource.of("demo/Book.${if (language == LsiLanguage.JAVA) "java" else "kt"}", language)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val idPropertyId = LsiSymbolId.property(BOOK_ID, "id")
        val namePropertyId = LsiSymbolId.property(BOOK_ID, "name")
        return LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = BOOK_ID,
                    name = "Book",
                    qualifiedName = "demo.Book",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    memberIds = listOf(idPropertyId, namePropertyId),
                    annotations = annotations,
                    origin = origin,
                ),
                LsiProperty(
                    id = idPropertyId,
                    name = "id",
                    ownerId = BOOK_ID,
                    type = LONG_TYPE,
                    modality = LsiModality.ABSTRACT,
                    annotations = listOf(LsiAnnotation(ID_ANNOTATION)),
                    origin = origin,
                ),
                LsiProperty(
                    id = namePropertyId,
                    name = "name",
                    ownerId = BOOK_ID,
                    type = STRING_TYPE,
                    modality = LsiModality.ABSTRACT,
                    origin = origin,
                ),
            ),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<JimmerImmutableProp>,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: JimmerInheritanceStrategy? = null,
        joinedTableDissociateAction: JimmerJoinedTableDissociateAction? = null,
        instantiable: Boolean = true,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
        acrossMicroServices: Boolean = false,
        microServiceName: String = "",
    ): JimmerImmutableType {
        val qualifiedName = id.requireTypeQualifiedName()
        return JimmerImmutableType(
            id = id,
            qualifiedName = qualifiedName,
            kind = JimmerImmutableTypeKind.ENTITY,
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
            acrossMicroServices = acrossMicroServices,
            microServiceName = microServiceName,
        )
    }

    private fun prop(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        primaryMapping: JimmerImmutablePrimaryMapping = JimmerImmutablePrimaryMapping.SCALAR,
        list: Boolean = false,
        association: Boolean = false,
        targetTypeId: LsiSymbolId? = (type as? LsiDeclaredType)?.declarationId,
        associationKind: JimmerAssociationKind = JimmerAssociationKind.NONE,
        genericTarget: Boolean = false,
        remote: Boolean = false,
        recursive: Boolean = false,
        view: JimmerImmutableView? = null,
    ): JimmerImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return JimmerImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(id),
            inherited = false,
            overridden = false,
            nullable = false,
            list = list,
            association = association,
            embedded = false,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            associationKind = associationKind,
            formulaKind = JimmerFormulaKind.NONE,
            view = view,
            genericTarget = genericTarget,
            remote = remote,
            recursive = recursive,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun JimmerImmutableProp.inheritedBy(ownerTypeId: LsiSymbolId): JimmerImmutableProp {
        return copy(
            id = LsiSymbolId.property(ownerTypeId, name),
            ownerTypeId = ownerTypeId,
            inherited = true,
        )
    }

    private fun bookDocument(content: String): CompilerInputDocument {
        return document("demo/Book.dto", content)
    }

    private fun missingReferenceFixtures(): List<MissingReferenceFixture> {
        val bookWorkspace = immutableWorkspace(LsiLanguage.UNKNOWN)
        return listOf(
            MissingReferenceFixture(
                kind = CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
                missingTypeId = MISSING_BOOK_ID,
                document = document(
                    relativePath = "demo/MissingBook.dto",
                    content = "export demo.MissingBook\nMissingBookView {}",
                ),
                workspace = LsiWorkspace.EMPTY,
            ),
            MissingReferenceFixture(
                kind = CompilerInputDocumentReferenceKind.MODEL_TYPE,
                missingTypeId = MISSING_MODEL_ID,
                document = bookDocument(
                    """
                        BookView {
                            #allScalars(demo.MissingModel)
                        }
                    """.trimIndent(),
                ),
                workspace = bookWorkspace,
            ),
            MissingReferenceFixture(
                kind = CompilerInputDocumentReferenceKind.TYPE_USAGE,
                missingTypeId = MISSING_PAYLOAD_ID,
                document = bookDocument(
                    """
                        BookView {
                            payload: demo.MissingPayload
                        }
                    """.trimIndent(),
                ),
                workspace = bookWorkspace,
            ),
        )
    }

    private fun document(
        relativePath: String,
        content: String,
    ): CompilerInputDocument {
        return CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = relativePath,
            content = content,
        )
    }

    private companion object {
        const val DTO_FEATURE_ID = "dto"
        const val IMMUTABLE_FEATURE_ID = "immutable"

        val BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.Book")
        val AUTHOR_ID: LsiSymbolId = LsiSymbolId.type("demo.Author")
        val CLIENT_ID: LsiSymbolId = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_ID: LsiSymbolId = LsiSymbolId.type("demo.Organization")
        val MISSING_BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingBook")
        val MISSING_MODEL_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingModel")
        val MISSING_PAYLOAD_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingPayload")
        val ENTITY_ANNOTATION: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val ID_ANNOTATION: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val LONG_TYPE: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE: LsiTypeRef = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val REFERENCE_FREEZER = CompilerInputDocumentReferenceFreezer()
    }
}

private data class MissingReferenceFixture(
    val kind: CompilerInputDocumentReferenceKind,
    val missingTypeId: LsiSymbolId,
    val document: CompilerInputDocument,
    val workspace: LsiWorkspace,
)
