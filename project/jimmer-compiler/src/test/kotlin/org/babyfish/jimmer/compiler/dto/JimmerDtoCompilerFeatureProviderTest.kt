package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableCompilerFeatureProvider
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

class JimmerDtoCompilerFeatureProviderTest {
    @Test
    fun `registered dto feature consumes immutable state and dto documents`() {
        val providers = JimmerCompilerFeatureProviders.load()
        val featureIds = providers.map { provider -> provider.descriptor.id }
        val provider = providers.single { candidate -> candidate.descriptor.id == DTO_FEATURE_ID }

        assertEquals(setOf(IMMUTABLE_FEATURE_ID), provider.descriptor.dependsOn)
        assertEquals(setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID), provider.descriptor.classpathTypeIds)
        assertEquals(setOf(CompilerInputDocumentKind.DTO), provider.descriptor.inputDocumentKinds)
        assertTrue(featureIds.indexOf(IMMUTABLE_FEATURE_ID) < featureIds.indexOf(DTO_FEATURE_ID))
    }

    @Test
    fun `freezes jackson and shared renderer options`() {
        val detected = rendererOptions(
            platform = CompilerPlatform.UNKNOWN,
            availableTypeIds = setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID),
        )
        val explicitJackson2 = rendererOptions(
            platform = CompilerPlatform.KSP,
            options = mapOf(
                "jimmer.jackson3" to "false",
                "jimmer.dto.hibernateValidatorEnhancement" to "true",
                "jimmer.dto.mutable" to " true ",
                "jimmer.dto.fieldVisibility" to "invalid-but-ignored-by-ksp",
            ),
            availableTypeIds = setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID),
        )
        val explicitJackson3 = rendererOptions(
            platform = CompilerPlatform.APT,
            options = mapOf("jimmer.jackson3" to "true"),
        )

        assertEquals(JimmerDtoJacksonVersion.JACKSON_3, detected.jacksonVersion)
        assertEquals(JimmerDtoJacksonVersion.JACKSON_2, explicitJackson2.jacksonVersion)
        assertTrue(explicitJackson2.hibernateValidatorEnhancement)
        assertTrue(explicitJackson2.kspMutable)
        assertEquals(JimmerDtoFieldVisibility.PRIVATE, explicitJackson2.aptFieldVisibility)
        assertEquals(JimmerDtoJacksonVersion.JACKSON_3, explicitJackson3.jacksonVersion)
    }

    @Test
    fun `freezes apt field visibility and isolates platform options`() {
        val defaults = rendererOptions(CompilerPlatform.APT)
        val protectedOptions = rendererOptions(
            platform = CompilerPlatform.APT,
            options = mapOf(
                "jimmer.dto.fieldVisibility" to "protected",
                "jimmer.dto.mutable" to "true",
            ),
        )
        val publicOptions = rendererOptions(
            platform = CompilerPlatform.APT,
            options = mapOf("jimmer.dto.fieldVisibility" to "public"),
        )

        assertEquals(JimmerDtoFieldVisibility.PRIVATE, defaults.aptFieldVisibility)
        assertEquals(JimmerDtoFieldVisibility.PROTECTED, protectedOptions.aptFieldVisibility)
        assertFalse(protectedOptions.kspMutable)
        assertEquals(JimmerDtoFieldVisibility.PUBLIC, publicOptions.aptFieldVisibility)
        assertFailsWith<IllegalArgumentException> {
            rendererOptions(
                platform = CompilerPlatform.APT,
                options = mapOf("jimmer.dto.fieldVisibility" to "internal"),
            )
        }
    }

    @Test
    fun `renderer options participate in dto feature fingerprint`() {
        val workspace = immutableWorkspace(LsiLanguage.JAVA)
        val document = bookDocument("BookView { id name }")
        val jackson2 = session("dto-renderer-options-jackson2").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
                options = mapOf("jimmer.jackson3" to "false"),
            )
        ).dtoState()
        val jackson3 = session("dto-renderer-options-jackson3").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
                options = mapOf("jimmer.jackson3" to "true"),
            )
        ).dtoState()

        assertEquals(JimmerDtoJacksonVersion.JACKSON_2, jackson2.rendererOptions.jacksonVersion)
        assertEquals(JimmerDtoJacksonVersion.JACKSON_3, jackson3.rendererOptions.jacksonVersion)
        assertNotEquals(jackson2.fingerprint, jackson3.fingerprint)
    }

    @Test
    fun `ksp freezes kotlin dto root mutability overrides from dto source`() {
        val workspace = immutableWorkspaceWithClasses(
            listOf(
                LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDto"),
                LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDtoImmutability"),
            ),
        )
        val document = bookDocument(
            """
                @org.babyfish.jimmer.kt.dto.KotlinDto(
                    immutability = org.babyfish.jimmer.kt.dto.KotlinDtoImmutability.AUTO
                )
                AutoView { id }

                DefaultView { id }

                @org.babyfish.jimmer.kt.dto.KotlinDto(
                    immutability = org.babyfish.jimmer.kt.dto.KotlinDtoImmutability.IMMUTABLE
                )
                ImmutableView { id }

                @org.babyfish.jimmer.kt.dto.KotlinDto(
                    immutability = org.babyfish.jimmer.kt.dto.KotlinDtoImmutability.MUTABLE
                )
                MutableView { id }
            """.trimIndent(),
        )
        val immutableDefaultState = session("dto-ksp-root-mutability-immutable-default").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
            )
        ).dtoState()
        val mutableDefaultState = session("dto-ksp-root-mutability-mutable-default").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
                options = mapOf("jimmer.dto.mutable" to "true"),
            )
        ).dtoState()

        fun mutabilityByName(state: JimmerDtoCompilerFeatureState): Map<String, Boolean> {
            val graph = state.schema.documents.single().renderGraph
            return graph.rootTypeIds.associate { rootTypeId ->
                requireNotNull(graph.typesById.getValue(rootTypeId).name) to
                    state.effectiveKspMutableByRootTypeId.getValue(rootTypeId)
            }
        }

        assertEquals(
            mapOf(
                "AutoView" to false,
                "DefaultView" to false,
                "ImmutableView" to false,
                "MutableView" to true,
            ),
            mutabilityByName(immutableDefaultState),
        )
        assertEquals(
            mapOf(
                "AutoView" to true,
                "DefaultView" to true,
                "ImmutableView" to false,
                "MutableView" to true,
            ),
            mutabilityByName(mutableDefaultState),
        )
    }

    @Test
    fun `precompiles simple dto into platform independent semantic model`() {
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_ID,
                    props = listOf(
                        prop(BOOK_ID, "id", LONG_TYPE, PrimaryMapping.ID),
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
            platform = CompilerPlatform.APT,
        )

        assertTrue(outcome.unresolvedDocuments.isEmpty())
        assertTrue(outcome.failures.isEmpty())
        val document = outcome.schema.documents.single()
        assertEquals(inputSnapshot, document.inputSnapshot)
        val dtoType = document.renderGraph.typesById.getValue(document.renderGraph.rootTypeIds.single())
        assertEquals(listOf(BOOK_ID), document.targetTypeIds)
        assertEquals("demo.dto", dtoType.packageName)
        assertEquals("BookView", dtoType.name)
        assertEquals(
            listOf("id", "name"),
            dtoType.propIds.map { propId -> document.renderGraph.propsById.getValue(propId).name },
        )
        assertEquals(BOOK_ID, dtoType.baseTypeId)
        assertTrue(document.annotationContract.diagnostics.isEmpty())
        assertEquals(
            document.renderGraph.types.map(JimmerDtoType::id),
            document.annotationContract.typePlans.map(JimmerDtoTypeAnnotationPlan::typeId),
        )
        assertEquals(
            document.renderGraph.props.map(JimmerDtoProp::id),
            document.annotationContract.propPlans.map(JimmerDtoPropAnnotationPlan::propId),
        )
        assertTrue(document.interfaceContractResolution.successful)
        assertEquals(
            document.renderGraph.types.map(JimmerDtoType::id),
            document.interfaceContractResolution.contracts.map(DtoInterfaceContract::typeId),
        )
    }

    @Test
    fun `precompiles unbound multi target dto through shared lsi path`() {
        val schema = bookAndAuthorSchema()
        val inputSnapshot = REFERENCE_FREEZER.freeze(
            document(
                relativePath = "shared/Shared.dto",
                content = """
                    package demo.dto

                    BookView for demo.Book { id name }
                    AuthorView for demo.Author { id }
                """.trimIndent(),
            )
        )
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(inputSnapshot),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID, AUTHOR_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID, AUTHOR_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )

        assertTrue(outcome.unresolvedDocuments.isEmpty(), outcome.unresolvedDocuments.joinToString("\n"))
        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
        val document = outcome.schema.documents.single()
        assertEquals(listOf(AUTHOR_ID, BOOK_ID), document.targetTypeIds)
        assertEquals(
            listOf(
                "BookView" to BOOK_ID,
                "AuthorView" to AUTHOR_ID,
            ),
            document.renderGraph.rootTypeIds.map { rootTypeId ->
                val rootType = document.renderGraph.typesById.getValue(rootTypeId)
                rootType.name to rootType.baseTypeId
            },
        )
    }

    @Test
    fun `provider reports every multi target dto type as processed for apt and ksp`() {
        val inputDocument = document(
            relativePath = "shared/Shared.dto",
            content = """
                package demo.dto

                BookView for demo.Book { id name }
                AuthorView for demo.Author { id }
            """.trimIndent(),
        )

        listOf(CompilerPlatform.APT, CompilerPlatform.KSP).forEach { platform ->
            val language = if (platform == CompilerPlatform.APT) LsiLanguage.JAVA else LsiLanguage.KOTLIN
            val workspace = bookAndAuthorWorkspace(language)
            val result = session("dto-multi-target-${platform.name.lowercase()}").execute(
                round(
                    number = 0,
                    workspace = workspace,
                    currentWorkspace = workspace,
                    platform = platform,
                    inputDocuments = listOf(inputDocument),
                )
            )

            assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.dtoState().status)
            assertEquals(setOf(BOOK_ID, AUTHOR_ID), result.dtoResult().processedSymbols)
            assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
            assertTrue(result.dtoState().failures.isEmpty())
        }
    }

    @Test
    fun `precompiles cross file fragment in stable input order through shared lsi path`() {
        val schema = bookAndAuthorSchema()
        val fragmentDocument = document(
            relativePath = "shared/BookFragments.dto",
            content = """
                package demo.fragments

                fragment Identified for demo.Book { id }
            """.trimIndent(),
        )
        val viewDocument = document(
            relativePath = "views/BookViews.dto",
            content = """
                package demo.dto
                import demo.fragments.Identified

                BookView for demo.Book {
                    #include(Identified)
                    name
                }
            """.trimIndent(),
        )
        val precompiler = JimmerDtoPrecompiler()
        val first = precompiler.compile(
            inputDocumentSnapshots = listOf(fragmentDocument, viewDocument).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID, AUTHOR_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID, AUTHOR_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )
        val reversed = precompiler.compile(
            inputDocumentSnapshots = listOf(viewDocument, fragmentDocument).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID, AUTHOR_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID, AUTHOR_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )

        listOf(first, reversed).forEach { outcome ->
            assertTrue(outcome.unresolvedDocuments.isEmpty(), outcome.unresolvedDocuments.joinToString("\n"))
            assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
            assertEquals(
                listOf("shared/BookFragments.dto", "views/BookViews.dto"),
                outcome.schema.documents.map { document -> document.inputSnapshot.document.relativePath },
            )
            val fragment = outcome.schema.documents.single { document ->
                document.inputSnapshot.document.relativePath == fragmentDocument.relativePath
            }
            assertEquals(listOf(BOOK_ID), fragment.targetTypeIds)
            assertTrue(fragment.renderGraph.rootTypeIds.isEmpty())
        }
        val document = first.schema.documents.single { candidate ->
            candidate.renderGraph.rootTypeIds.isNotEmpty()
        }
        assertEquals(listOf(BOOK_ID), document.targetTypeIds)
        val bookView = document.renderGraph.typesById.getValue(document.renderGraph.rootTypeIds.single())
        assertEquals("BookView", bookView.name)
        assertEquals(BOOK_ID, bookView.baseTypeId)
        assertEquals(
            listOf("id", "name"),
            bookView.propIds.map { propId -> document.renderGraph.propsById.getValue(propId).name },
        )
        assertEquals(first.schema.normalizedSnapshot(), reversed.schema.normalizedSnapshot())
        assertEquals(first.schema.fingerprint(), reversed.schema.fingerprint())
    }

    @Test
    fun `precompiles dto annotation copy and target policy`() {
        val noTargetTypeId = LsiSymbolId.type("demo.NoTarget")
        val notNullTypeId = LsiSymbolId.type("jakarta.validation.constraints.NotNull")
        val javaTargetTypeId = LsiSymbolId.type("java.lang.annotation.Target")
        val javaElementTypeId = LsiSymbolId.type("java.lang.annotation.ElementType")
        val baseWorkspace = immutableWorkspace(LsiLanguage.UNKNOWN)
        val annotationSource = LsiSource.of("demo/Annotations.java", LsiLanguage.JAVA)
        val annotationOrigin = LsiOrigin(LsiOriginKind.SOURCE, annotationSource)
        val methodTarget = LsiAnnotation(
            type = javaTargetTypeId,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ArrayValue(
                        listOf(LsiAnnotationValue.EnumValue(javaElementTypeId, "METHOD"))
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
        val workspace = LsiWorkspace(
            sources = baseWorkspace.sources + annotationSource,
            declarations = baseWorkspace.declarations + listOf(
                LsiTypeDeclaration(
                    id = noTargetTypeId,
                    name = "NoTarget",
                    qualifiedName = "demo.NoTarget",
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                    origin = annotationOrigin,
                ),
                LsiTypeDeclaration(
                    id = notNullTypeId,
                    name = "NotNull",
                    qualifiedName = "jakarta.validation.constraints.NotNull",
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                    annotations = listOf(methodTarget),
                    origin = annotationOrigin,
                ),
            ),
        )
        val state = session("dto-annotation-copy-target-policy").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(
                    bookDocument(
                        """
                            @demo.NoTarget
                            BookView {
                                @demo.NoTarget
                                @jakarta.validation.constraints.NotNull
                                name
                            }
                        """.trimIndent(),
                    )
                ),
            )
        ).dtoState()
        val contract = state.schema.documents.single().annotationContract
        val rootTypeId = state.schema.documents.single().renderGraph.rootTypeIds.single()
        val namePropId = state.schema.documents.single().renderGraph.props.single().id
        val noTargetDeclaration = contract.declarationsByTypeId.getValue(noTargetTypeId)
        val typeApplications = contract.typePlansByTypeId.getValue(rootTypeId).applications
        val propApplications = contract.propPlansByPropId.getValue(namePropId).propertyApplications

        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, state.status)
        assertFalse(noTargetDeclaration.targetDeclared)
        assertEquals(
            listOf(JimmerDtoAnnotationPlacement.TYPE),
            typeApplications.single { application -> application.annotation.typeId == noTargetTypeId }.placements,
        )
        assertTrue(propApplications.none { application -> application.annotation.typeId == noTargetTypeId })
        assertEquals(
            JimmerDtoAnnotationOrigin.DTO,
            propApplications.single { application -> application.annotation.typeId == notNullTypeId }.origin,
        )
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
        assertTrue("annotation-contract-record|" in state.schema.normalizedSnapshot())
    }

    @Test
    fun `reports every contract diagnostic without losing structured fields`() {
        val badAnnotationId = LsiSymbolId.type("demo.BadAnnotation")
        val alphaId = LsiSymbolId.type("demo.AlphaClass")
        val zetaId = LsiSymbolId.type("demo.ZetaClass")
        val workspace = immutableWorkspaceWithClasses(listOf(badAnnotationId, alphaId, zetaId))
        val result = session("dto-contract-diagnostics").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(
                    bookDocument(
                        """
                            @demo.BadAnnotation
                            BookView implements demo.ZetaClass, demo.AlphaClass { id }
                        """.trimIndent(),
                    )
                ),
            )
        )

        val state = result.dtoState()
        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, state.status)
        val precompiledDocument = state.schema.documents.single()
        val contractDiagnostics =
            precompiledDocument.annotationContract.diagnostics +
                precompiledDocument.interfaceContractResolution.diagnostics
        assertEquals(3, contractDiagnostics.size)
        assertEquals(
            listOf(badAnnotationId, alphaId, zetaId),
            state.failures.map(JimmerDtoCompilerFailure::symbolId),
        )
        assertEquals(
            listOf(
                "jimmer.dto.annotation.declaration-kind",
                "jimmer.dto.interface.not-interface",
                "jimmer.dto.interface.not-interface",
            ),
            state.failures.map(JimmerDtoCompilerFailure::code),
        )
        assertEquals(
            state.failures.map { failure ->
                listOf(
                    failure.code,
                    failure.severity,
                    failure.symbolId,
                    failure.location,
                    failure.message,
                    failure.details,
                )
            },
            result.diagnostics.map { diagnostic ->
                listOf(
                    diagnostic.code,
                    diagnostic.severity,
                    diagnostic.symbolId,
                    diagnostic.location,
                    diagnostic.message,
                    diagnostic.details,
                )
            },
        )
        assertEquals(
            contractDiagnostics.map { diagnostic ->
                listOf(
                    diagnostic.code,
                    diagnostic.severity,
                    diagnostic.symbolId,
                    diagnostic.location,
                    diagnostic.message,
                    diagnostic.details,
                )
            }.toSet(),
            state.failures.map { failure ->
                listOf(
                    failure.code,
                    failure.severity,
                    failure.symbolId,
                    failure.location,
                    failure.message,
                    failure.details,
                )
            }.toSet(),
        )
    }

    @Test
    fun `annotation and interface contracts participate in schema and state fingerprints`() {
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_ID,
                    props = listOf(prop(BOOK_ID, "id", LONG_TYPE, PrimaryMapping.ID)),
                )
            )
        )
        val baseline = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument("BookView { id }"))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableWorkspace(LsiLanguage.UNKNOWN),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        ).schema
        val document = baseline.documents.single()
        val markerTypeId = LsiSymbolId.type("demo.RenderMarker")
        val rootTypeId = document.renderGraph.rootTypeIds.single()
        val annotationContract = document.annotationContract.copy(
            declarations = listOf(
                JimmerDtoAnnotationDeclaration(
                    typeId = markerTypeId,
                    kind = JimmerDtoAnnotationDeclarationKind.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(JimmerDtoAnnotationPlacement.TYPE),
                    argumentNames = emptyList(),
                    kotlinValueVararg = false,
                )
            ),
            typePlans = document.annotationContract.typePlans.map { plan ->
                if (plan.typeId != rootTypeId) {
                    plan
                } else {
                    plan.copy(
                        applications = listOf(
                            JimmerDtoAnnotationApplication(
                                annotation = JimmerDtoAppliedAnnotation(markerTypeId, emptyList()),
                                origin = JimmerDtoAnnotationOrigin.DTO,
                                sourceSymbolId = null,
                                placements = listOf(JimmerDtoAnnotationPlacement.TYPE),
                            )
                        ),
                    )
                }
            },
        )
        val interfaceResolution = document.interfaceContractResolution.copy(
            contracts = document.interfaceContractResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(superInterfaceTypeIds = listOf(LsiSymbolId.type("demo.RenderView")))
                }
            },
        )
        val annotationChanged = JimmerDtoPrecompiledSchema(
            listOf(document.copy(annotationContract = annotationContract)),
        )
        val interfaceChanged = JimmerDtoPrecompiledSchema(
            listOf(document.copy(interfaceContractResolution = interfaceResolution)),
        )

        assertTrue("annotation-contract|" in baseline.normalizedSnapshot())
        assertTrue("interface-contract|" in baseline.normalizedSnapshot())
        assertNotEquals(baseline.normalizedSnapshot(), annotationChanged.normalizedSnapshot())
        assertNotEquals(baseline.normalizedSnapshot(), interfaceChanged.normalizedSnapshot())
        assertNotEquals(baseline.fingerprint(), annotationChanged.fingerprint())
        assertNotEquals(baseline.fingerprint(), interfaceChanged.fingerprint())
        assertNotEquals(dtoState(baseline).fingerprint, dtoState(annotationChanged).fingerprint)
        assertNotEquals(dtoState(baseline).fingerprint, dtoState(interfaceChanged).fingerprint)
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
            PrimaryMapping.ID,
        )
        val storeProp = prop(
            ownerTypeId = BOOK_ID,
            name = "store",
            type = LsiDeclaredType(storeId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            association = true,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val storeIdViewProp = prop(
            ownerTypeId = BOOK_ID,
            name = "storeId",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            targetTypeId = null,
            view = ImmutableView.Id(storeProp.id, storeIdProp.id),
        )
        val linksProp = prop(
            ownerTypeId = BOOK_ID,
            name = "links",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(linkId))),
            ),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            list = true,
            association = true,
            targetTypeId = linkId,
            associationKind = AssociationKind.ONE_TO_MANY,
        )
        val deeperProp = prop(
            ownerTypeId = linkId,
            name = "author",
            type = LsiDeclaredType(authorId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            association = true,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val authorsViewProp = prop(
            ownerTypeId = BOOK_ID,
            name = "authors",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(authorId))),
            ),
            primaryMapping = PrimaryMapping.VIEW,
            list = true,
            association = true,
            targetTypeId = authorId,
            associationKind = AssociationKind.MANY_TO_MANY_VIEW,
            view = ImmutableView.ManyToMany(linksProp.id, deeperProp.id),
        )
        val schema = ImmutableSchema(
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
        val idProp = prop(CLIENT_ID, "id", LONG_TYPE, PrimaryMapping.ID)
        val discriminatorProp = prop(
            ownerTypeId = CLIENT_ID,
            name = "type",
            type = STRING_TYPE,
            primaryMapping = PrimaryMapping.DISCRIMINATOR,
        )
        val nameProp = prop(CLIENT_ID, "name", STRING_TYPE)
        val root = immutableType(
            id = CLIENT_ID,
            props = listOf(idProp, discriminatorProp, nameProp),
            instantiable = false,
            inheritanceRootTypeId = CLIENT_ID,
            inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE,
            joinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
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
        val schema = ImmutableSchema(listOf(root, organization))
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
            platform = CompilerPlatform.APT,
        )

        assertTrue(outcome.failures.isEmpty())
        val document = outcome.schema.documents.single()
        val dtoType = document.renderGraph.typesById.getValue(document.renderGraph.rootTypeIds.single())
        val polymorphism = assertNotNull(dtoType.polymorphism)
        assertFalse(polymorphism.exhaustive)
        assertTrue(polymorphism.branches.single { branch ->
            branch.kind == JimmerDtoPolymorphicBranchKind.DEFAULT
        }.implicit)
        val branch = polymorphism.branches.single { value ->
            value.kind == JimmerDtoPolymorphicBranchKind.TYPE
        }
        assertEquals(ORGANIZATION_ID, branch.targetBaseTypeId)
        assertEquals(CLIENT_ID, schema.typesById.getValue(ORGANIZATION_ID).primarySuperTypeId)
        assertEquals(CLIENT_ID, schema.typesById.getValue(ORGANIZATION_ID).inheritanceRootTypeId)
        val branchType = document.renderGraph.typesById.getValue(branch.bodyTypeId)
        assertEquals(
            listOf("taxCode"),
            branchType.propIds.map { propId -> document.renderGraph.propsById.getValue(propId).name },
        )
        assertTrue("branch|" in outcome.schema.normalizedSnapshot())
    }

    @Test
    fun `sorts multiple documents and includes frozen content in fingerprint`() {
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    id = AUTHOR_ID,
                    props = listOf(prop(AUTHOR_ID, "id", LONG_TYPE, PrimaryMapping.ID)),
                ),
                immutableType(
                    id = BOOK_ID,
                    props = listOf(
                        prop(BOOK_ID, "id", LONG_TYPE, PrimaryMapping.ID),
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
            platform = CompilerPlatform.APT,
        ).schema
        val reversed = precompiler.compile(
            inputDocumentSnapshots = listOf(author, book).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(AUTHOR_ID, BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(AUTHOR_ID, BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
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
            platform = CompilerPlatform.APT,
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
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    id = BOOK_ID,
                    props = listOf(prop(BOOK_ID, "id", LONG_TYPE, PrimaryMapping.ID)),
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
            platform = CompilerPlatform.APT,
        ).schema
        val same = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument(""))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        ).schema
        val whitespaceChanged = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument("\n"))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        ).schema

        assertTrue(empty.documents.single().renderGraph.rootTypeIds.isEmpty())
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
        val secondDocument = secondState.schema.documents.single()
        assertEquals(
            listOf("BookView"),
            secondDocument.renderGraph.rootTypeIds.map { typeId ->
                secondDocument.renderGraph.typesById.getValue(typeId).name
            },
        )
        assertEquals(setOf(BOOK_ID), second.dtoResult().processedSymbols)
        assertTrue(second.dtoResult().artifacts.isEmpty())
    }

    @Test
    fun `apt defers unresolved config contract then resolves or reports it at final round`() {
        val document = bookDocument(
            """
                BookView {
                    !filter(demo.AuthorFilter)
                    authors { id }
                }
            """.trimIndent(),
        )
        val unresolvedWorkspace = configWorkspace(resolved = false)
        val session = session("dto-apt-config-rounds")
        val first = session.execute(
            round(
                number = 0,
                workspace = unresolvedWorkspace,
                currentWorkspace = unresolvedWorkspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
            ),
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.DEFERRED, first.dtoState().status)
        assertEquals(listOf(FILTER_ID), first.dtoState().unresolvedDocuments.single().unresolvedTypeIds)
        assertEquals(setOf(FILTER_ID), first.unresolvedSymbols)
        assertTrue(first.diagnostics.isEmpty())

        val resolvedWorkspace = configWorkspace(resolved = true)
        val second = session.execute(
            round(
                number = 1,
                workspace = resolvedWorkspace,
                currentWorkspace = resolvedWorkspace,
                platform = CompilerPlatform.APT,
                inputDocuments = listOf(document),
            ),
        )
        assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, second.dtoState().status)
        val contract = second.dtoState().schema.documents.single().configContractResolution.contracts.single()
        assertEquals(FILTER_ID, contract.implementationTypeId)
        assertEquals(AUTHOR_ID, contract.targetEntityTypeId)
        assertEquals(AUTHOR_TABLE_ID, contract.contractArgumentTypeId)
        assertTrue(second.diagnostics.isEmpty())

        val final = session("dto-apt-config-final").execute(
            round(
                number = 0,
                workspace = unresolvedWorkspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = emptySet(),
                platform = CompilerPlatform.APT,
                isFinal = true,
                inputDocuments = listOf(document),
            ),
        )
        val configReference = REFERENCE_FREEZER.freeze(document).references.single { reference ->
            reference.kind == CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION
        }
        assertEquals(JimmerDtoCompilerFeatureStatus.INVALID, final.dtoState().status)
        assertEquals("jimmer.dto.unresolved", final.diagnostics.single().code)
        assertEquals(FILTER_ID, final.diagnostics.single().symbolId)
        assertEquals(configReference.location, final.diagnostics.single().location)
        assertTrue(final.unresolvedSymbols.isEmpty())
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
        assertEquals(listOf(listOf(BOOK_ID)), first.dtoState().unresolvedDocuments.map { it.targetTypeIds })
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
        val secondDocument = second.dtoState().schema.documents.single()
        assertEquals(
            listOf("BookView"),
            secondDocument.renderGraph.rootTypeIds.map { typeId ->
                secondDocument.renderGraph.typesById.getValue(typeId).name
            },
        )
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
                    .map { reference -> reference.typeSelector.fallbackTypeId },
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
                    snapshot.references.single { reference ->
                        fixture.missingTypeId in reference.typeSelector.candidateTypeIds
                    }.location,
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
    fun `skips dto targets unavailable to the active compiler platform`() {
        val cases = listOf(
            "ksp-java-source" to (
                CompilerPlatform.KSP to bookWorkspace(
                    language = LsiLanguage.JAVA,
                    annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                )
            ),
            "ksp-java" to (
                CompilerPlatform.KSP to bookWorkspace(
                    language = LsiLanguage.JAVA,
                    annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                    originKind = LsiOriginKind.BINARY,
                )
            ),
            "apt-kotlin-metadata" to (
                CompilerPlatform.APT to bookWorkspace(
                    language = LsiLanguage.KOTLIN,
                    annotations = listOf(
                        LsiAnnotation(ENTITY_ANNOTATION),
                        LsiAnnotation(KOTLIN_METADATA_ANNOTATION),
                    ),
                    originKind = LsiOriginKind.BINARY,
                )
            ),
        )

        cases.forEach { (name, platformAndWorkspace) ->
            val (platform, workspace) = platformAndWorkspace
            val result = session("dto-platform-target-$name").execute(
                round(
                    number = 0,
                    workspace = workspace,
                    currentWorkspace = workspace,
                    platform = platform,
                    inputDocuments = listOf(bookDocument("BookView { id name }")),
                )
            )

            assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().schema.documents.isEmpty())
            assertTrue(result.dtoState().unresolvedDocuments.isEmpty())
            assertTrue(result.dtoState().failures.isEmpty())
            assertTrue(result.dtoResult().processedSymbols.isEmpty())
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
        assertEquals(listOf(BOOK_ID), result.dtoState().failures.single().targetTypeIds)
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
    fun `source filter ignores missing references owned by an excluded dto target`() {
        val inputDocument = document(
            relativePath = "shared/Shared.dto",
            content = """
                package demo.dto

                BookView for demo.Book {
                    payload: demo.MissingPayload
                }
                AuthorView for demo.Author { id }
            """.trimIndent(),
        )

        listOf(CompilerPlatform.APT, CompilerPlatform.KSP).forEach { platform ->
            val language = if (platform == CompilerPlatform.APT) LsiLanguage.JAVA else LsiLanguage.KOTLIN
            val workspace = bookAndAuthorWorkspace(language)
            val result = session("dto-partial-filter-${platform.name.lowercase()}").execute(
                round(
                    number = 0,
                    workspace = workspace,
                    currentWorkspace = workspace,
                    platform = platform,
                    inputDocuments = listOf(inputDocument),
                    options = mapOf("jimmer.source.excludes" to "demo.Book"),
                )
            )

            val state = result.dtoState()
            assertEquals(JimmerDtoCompilerFeatureStatus.RESOLVED, state.status)
            assertTrue(state.unresolvedDocuments.isEmpty())
            assertTrue(state.failures.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
            val document = state.schema.documents.single()
            assertEquals(listOf(AUTHOR_ID), document.targetTypeIds)
            assertEquals(
                listOf("AuthorView" to AUTHOR_ID),
                document.renderGraph.rootTypeIds.map { rootTypeId ->
                    val rootType = document.renderGraph.typesById.getValue(rootTypeId)
                    rootType.name to rootType.baseTypeId
                },
            )
        }
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

    private fun rendererOptions(
        platform: CompilerPlatform,
        options: Map<String, String> = emptyMap(),
        availableTypeIds: Set<LsiSymbolId> = emptySet(),
    ): JimmerDtoRendererOptions {
        return CompilerRound(
            number = 0,
            workspace = LsiWorkspace.EMPTY,
            currentWorkspace = LsiWorkspace.EMPTY,
            currentRootTypeIds = emptySet(),
            platform = platform,
            options = options,
            availableTypeIds = availableTypeIds,
            inputDocumentSnapshots = emptyList(),
        ).toJimmerDtoRendererOptions()
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

    private fun configWorkspace(resolved: Boolean): LsiWorkspace {
        val source = LsiSource.of("demo/ConfigModels.java", LsiLanguage.JAVA)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val bookIdPropId = LsiSymbolId.property(BOOK_ID, "id")
        val bookAuthorsPropId = LsiSymbolId.property(BOOK_ID, "authors")
        val authorIdPropId = LsiSymbolId.property(AUTHOR_ID, "id")
        val constructorId = LsiSymbolId.constructor(FILTER_ID, emptyList())
        val filterArgument = if (resolved) {
            LsiDeclaredType(AUTHOR_TABLE_ID)
        } else {
            LsiUnresolvedType("demo.AuthorTable")
        }
        val declarations = listOf(
            LsiTypeDeclaration(
                id = BOOK_ID,
                name = "Book",
                qualifiedName = "demo.Book",
                kind = LsiTypeDeclarationKind.INTERFACE,
                modality = LsiModality.ABSTRACT,
                memberIds = listOf(bookIdPropId, bookAuthorsPropId),
                annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                origin = origin,
            ),
            LsiProperty(
                id = bookIdPropId,
                name = "id",
                ownerId = BOOK_ID,
                type = LONG_TYPE,
                modality = LsiModality.ABSTRACT,
                annotations = listOf(LsiAnnotation(ID_ANNOTATION)),
                origin = origin,
            ),
            LsiProperty(
                id = bookAuthorsPropId,
                name = "authors",
                ownerId = BOOK_ID,
                type = LsiDeclaredType(
                    declarationId = LIST_TYPE_ID,
                    arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(AUTHOR_ID))),
                ),
                modality = LsiModality.ABSTRACT,
                annotations = listOf(LsiAnnotation(MANY_TO_MANY_ANNOTATION)),
                origin = origin,
            ),
            LsiTypeDeclaration(
                id = AUTHOR_ID,
                name = "Author",
                qualifiedName = "demo.Author",
                kind = LsiTypeDeclarationKind.INTERFACE,
                modality = LsiModality.ABSTRACT,
                memberIds = listOf(authorIdPropId),
                annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                origin = origin,
            ),
            LsiProperty(
                id = authorIdPropId,
                name = "id",
                ownerId = AUTHOR_ID,
                type = LONG_TYPE,
                modality = LsiModality.ABSTRACT,
                annotations = listOf(LsiAnnotation(ID_ANNOTATION)),
                origin = origin,
            ),
            LsiTypeDeclaration(
                id = FILTER_ID,
                name = "AuthorFilter",
                qualifiedName = "demo.AuthorFilter",
                kind = LsiTypeDeclarationKind.CLASS,
                superTypes = listOf(
                    LsiDeclaredType(
                        declarationId = FIELD_FILTER_ID,
                        arguments = listOf(LsiTypeArgument.invariant(filterArgument)),
                    ),
                ),
                memberIds = listOf(constructorId),
                origin = origin,
            ),
            LsiConstructor(
                id = constructorId,
                ownerId = FILTER_ID,
                origin = origin,
            ),
        )
        return LsiWorkspace(
            sources = listOf(source),
            declarations = declarations,
            typeHierarchy = if (resolved) {
                listOf(
                    LsiTypeHierarchyEntry(
                        id = AUTHOR_TABLE_ID,
                        qualifiedName = "demo.AuthorTable",
                        kind = LsiTypeDeclarationKind.INTERFACE,
                        directSuperTypes = listOf(
                            LsiDeclaredType(
                                declarationId = TABLE_ID,
                                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(AUTHOR_ID))),
                            ),
                        ),
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun nonImmutableWorkspace(language: LsiLanguage): LsiWorkspace {
        return bookWorkspace(language, emptyList())
    }

    private fun immutableWorkspaceWithClasses(
        classTypeIds: Collection<LsiSymbolId>,
    ): LsiWorkspace {
        val base = immutableWorkspace(LsiLanguage.UNKNOWN)
        val source = LsiSource.of("demo/ContractClasses.kt", LsiLanguage.UNKNOWN)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        return LsiWorkspace(
            sources = (base.sources + source).distinct().sorted(),
            declarations = base.declarations + classTypeIds.sorted().map { typeId ->
                val qualifiedName = typeId.requireTypeQualifiedName()
                LsiTypeDeclaration(
                    id = typeId,
                    name = qualifiedName.substringAfterLast('.'),
                    qualifiedName = qualifiedName,
                    kind = LsiTypeDeclarationKind.CLASS,
                    origin = origin,
                )
            },
        )
    }

    private fun dtoState(schema: JimmerDtoPrecompiledSchema): JimmerDtoCompilerFeatureState {
        return JimmerDtoCompilerFeatureState(
            status = JimmerDtoCompilerFeatureStatus.RESOLVED,
            dependencyStatus = JimmerDtoCompilerDependencyStatus.RESOLVED,
            schema = schema,
            unresolvedDocuments = emptyList(),
            failures = emptyList(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            rendererOptions = rendererOptions(CompilerPlatform.UNKNOWN),
            effectiveKspMutableByRootTypeId = schema.documents
                .flatMap { document -> document.renderGraph.rootTypeIds }
                .sorted()
                .associateWith { false },
            immutableDependencyFingerprint = "immutable-fingerprint",
        )
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

    private fun bookAndAuthorSchema(): ImmutableSchema {
        return ImmutableSchema(
            listOf(
                immutableType(
                    id = AUTHOR_ID,
                    props = listOf(prop(AUTHOR_ID, "id", LONG_TYPE, PrimaryMapping.ID)),
                ),
                immutableType(
                    id = BOOK_ID,
                    props = listOf(
                        prop(BOOK_ID, "id", LONG_TYPE, PrimaryMapping.ID),
                        prop(BOOK_ID, "name", STRING_TYPE),
                    ),
                ),
            )
        )
    }

    private fun bookAndAuthorWorkspace(language: LsiLanguage): LsiWorkspace {
        val workspace = immutableWorkspace(language)
        val bookSource = workspace.sources.single()
        val authorSource = LsiSource.of(
            path = "demo/Author.${bookSource.path.substringAfterLast('.')}",
            language = bookSource.language,
            kind = bookSource.kind,
        )
        val origin = LsiOrigin(LsiOriginKind.SOURCE, authorSource)
        val authorIdPropId = LsiSymbolId.property(AUTHOR_ID, "id")
        return LsiWorkspace(
            sources = workspace.sources + authorSource,
            declarations = workspace.declarations + listOf(
                LsiTypeDeclaration(
                    id = AUTHOR_ID,
                    name = "Author",
                    qualifiedName = "demo.Author",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    memberIds = listOf(authorIdPropId),
                    annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                    origin = origin,
                ),
                LsiProperty(
                    id = authorIdPropId,
                    name = "id",
                    ownerId = AUTHOR_ID,
                    type = LONG_TYPE,
                    modality = LsiModality.ABSTRACT,
                    annotations = listOf(LsiAnnotation(ID_ANNOTATION)),
                    origin = origin,
                ),
            ),
        )
    }

    private fun bookWorkspace(
        language: LsiLanguage,
        annotations: List<LsiAnnotation>,
        originKind: LsiOriginKind = LsiOriginKind.SOURCE,
    ): LsiWorkspace {
        val sourceKind = when (originKind) {
            LsiOriginKind.SOURCE -> LsiSourceKind.SOURCE
            LsiOriginKind.BINARY -> LsiSourceKind.BINARY
            else -> error("Unsupported book workspace origin kind: $originKind")
        }
        val sourceExtension = when {
            originKind == LsiOriginKind.BINARY -> "class"
            language == LsiLanguage.JAVA -> "java"
            else -> "kt"
        }
        val source = LsiSource.of(
            path = "demo/Book.$sourceExtension",
            language = language,
            kind = sourceKind,
        )
        val origin = LsiOrigin(originKind, source)
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
        props: List<ImmutableProp>,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        inheritanceRootTypeId: LsiSymbolId? = null,
        inheritanceStrategy: InheritanceStrategy? = null,
        joinedTableDissociateAction: JoinedTableDissociateAction? = null,
        instantiable: Boolean = true,
        discriminatorValue: String? = null,
        discriminatorPropId: LsiSymbolId? = null,
        acrossMicroServices: Boolean = false,
        microServiceName: String = "",
    ): ImmutableType {
        val qualifiedName = id.requireTypeQualifiedName()
        val completeProps = completeEntityProps(id, props)
        return ImmutableType(
            id = id,
            qualifiedName = qualifiedName,
            kind = ImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = superTypeIds,
            props = completeProps,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = inheritanceRootTypeId,
            inheritanceStrategy = inheritanceStrategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue,
            discriminatorPropId = discriminatorPropId,
            idPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.ID
            }?.id,
            versionPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.VERSION
            }?.id,
            logicalDeletedPropId = completeProps.singleOrNull { prop ->
                prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED
            }?.id,
            acrossMicroServices = acrossMicroServices,
            microServiceName = microServiceName,
        )
    }

    private fun prop(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        list: Boolean = false,
        association: Boolean = false,
        targetTypeId: LsiSymbolId? = (type as? LsiDeclaredType)?.declarationId,
        associationKind: AssociationKind = AssociationKind.NONE,
        genericTarget: Boolean = false,
        remote: Boolean = false,
        recursive: Boolean = false,
        view: ImmutableView? = null,
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
            list = list,
            association = association,
            embedded = false,
            targetTypeId = targetTypeId,
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
            view = view,
            genericTarget = genericTarget,
            remote = remote,
            recursive = recursive,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun ImmutableProp.inheritedBy(ownerTypeId: LsiSymbolId): ImmutableProp {
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
                kind = CompilerInputDocumentReferenceKind.TARGET_TYPE,
                missingTypeId = MISSING_BOOK_ID,
                document = document(
                    relativePath = "shared/Shared.dto",
                    content = "MissingBookView for demo.MissingBook {}",
                ),
                workspace = LsiWorkspace.EMPTY,
            ),
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
        val AUTHOR_TABLE_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorTable")
        val FILTER_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorFilter")
        val CLIENT_ID: LsiSymbolId = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_ID: LsiSymbolId = LsiSymbolId.type("demo.Organization")
        val MISSING_BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingBook")
        val MISSING_MODEL_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingModel")
        val MISSING_PAYLOAD_ID: LsiSymbolId = LsiSymbolId.type("demo.MissingPayload")
        val ENTITY_ANNOTATION: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val KOTLIN_METADATA_ANNOTATION: LsiSymbolId = LsiSymbolId.type("kotlin.Metadata")
        val ID_ANNOTATION: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val MANY_TO_MANY_ANNOTATION: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")
        val FIELD_FILTER_ID: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.FieldFilter")
        val TABLE_ID: LsiSymbolId = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Table")
        val LIST_TYPE_ID: LsiSymbolId = LsiSymbolId.type("java.util.List")
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
