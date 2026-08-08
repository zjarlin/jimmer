package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.input.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerInputDocumentReferenceKind
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerRoundResult
import site.addzero.lsi.compiler.CompilerSession
import site.addzero.lsi.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.compiler.CompilerFeatureLoader
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentBundleRenderer
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
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
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoInterfaceAccessorContract
import site.addzero.lsi.jimmer.dto.DtoInterfaceContract
import site.addzero.lsi.jimmer.dto.DtoInterfacePropContract
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.fingerprint
import site.addzero.lsi.jimmer.dto.normalizedSnapshot
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind

class DtoFeatureTest {
    @Test
    fun `registered dto feature consumes immutable state and dto documents`() {
        val features = CompilerFeatureLoader.load()
        val featureKeys = features.map { feature -> feature.key }
        val feature = features.single { candidate -> candidate.key == DtoFeature.Key }

        assertEquals(setOf(ImmutableFeature.Key), feature.dependencies)
        assertEquals(setOf(JACKSON_3_OBJECT_MAPPER_TYPE_ID), feature.metadata.classpathTypeIds)
        assertEquals(setOf(DTO_INPUT_DOCUMENT_KIND), feature.metadata.inputDocumentKinds)
        assertTrue(feature.metadata.requiresSourceQuiescence)
        assertTrue(featureKeys.indexOf(ImmutableFeature.Key) < featureKeys.indexOf(DtoFeature.Key))
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

        assertEquals(JacksonFamily.JACKSON_3, detected.jacksonVersion)
        assertEquals(JacksonFamily.JACKSON_2, explicitJackson2.jacksonVersion)
        assertTrue(explicitJackson2.hibernateValidatorEnhancement)
        assertTrue(explicitJackson2.kspMutable)
        assertEquals(LsiVisibility.PRIVATE, explicitJackson2.aptFieldVisibility)
        assertEquals(JacksonFamily.JACKSON_3, explicitJackson3.jacksonVersion)
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

        assertEquals(LsiVisibility.PRIVATE, defaults.aptFieldVisibility)
        assertEquals(LsiVisibility.PROTECTED, protectedOptions.aptFieldVisibility)
        assertFalse(protectedOptions.kspMutable)
        assertEquals(LsiVisibility.PUBLIC, publicOptions.aptFieldVisibility)
        assertFailsWith<IllegalArgumentException> {
            rendererOptions(
                platform = CompilerPlatform.APT,
                options = mapOf("jimmer.dto.fieldVisibility" to "internal"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            JimmerDtoRendererOptions(
                jacksonVersion = JacksonFamily.JACKSON_2,
                hibernateValidatorEnhancement = false,
                aptFieldVisibility = LsiVisibility.INTERNAL,
                kspMutable = false,
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

        assertEquals(JacksonFamily.JACKSON_2, jackson2.rendererOptions.jacksonVersion)
        assertEquals(JacksonFamily.JACKSON_3, jackson3.rendererOptions.jacksonVersion)
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

        fun mutabilityByName(state: DtoFeatureState): Map<String, Boolean> {
            val graph = state.graphs.single()
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
        val resolvedInput = outcome.resolvedInputs.single()
        val graph = outcome.graphs.single()
        val annotationContract = outcome.annotationContractsBySource.getValue(graph.source)
        val interfaceContract = outcome.interfaceContractsBySource.getValue(graph.source)
        assertEquals(inputSnapshot, resolvedInput.inputSnapshot)
        val dtoType = graph.typesById.getValue(graph.rootTypeIds.single())
        assertEquals(listOf(BOOK_ID), resolvedInput.targetTypeIds)
        assertEquals("demo.dto", dtoType.packageName)
        assertEquals("BookView", dtoType.name)
        assertEquals(
            listOf("id", "name"),
            dtoType.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
        assertEquals(BOOK_ID, dtoType.baseTypeId)
        assertTrue(annotationContract.diagnostics.isEmpty())
        assertEquals(
            graph.types.map(DtoType::id),
            annotationContract.typePlans.map(DtoTypeAnnotationPlan::typeId),
        )
        assertEquals(
            graph.props.map(DtoProp::id),
            annotationContract.propPlans.map(DtoPropAnnotationPlan::propId),
        )
        assertTrue(interfaceContract.successful)
        assertEquals(
            graph.types.map(DtoType::id),
            interfaceContract.contracts.map(DtoInterfaceContract::typeId),
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
        val resolvedInput = outcome.resolvedInputs.single()
        val graph = outcome.graphs.single()
        assertEquals(listOf(AUTHOR_ID, BOOK_ID), resolvedInput.targetTypeIds)
        assertEquals(
            listOf(
                "BookView" to BOOK_ID,
                "AuthorView" to AUTHOR_ID,
            ),
            graph.rootTypeIds.map { rootTypeId ->
                val rootType = graph.typesById.getValue(rootTypeId)
                rootType.name to rootType.baseTypeId
            },
        )
    }

    @Test
    fun `feature reports every multi target dto type as processed for apt and ksp`() {
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

            assertEquals(DtoFeatureStatus.RESOLVED, result.dtoState().status)
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
                outcome.resolvedInputs.map { input -> input.inputSnapshot.document.relativePath },
            )
            val fragmentInput = outcome.resolvedInputs.single { input ->
                input.inputSnapshot.document.relativePath == fragmentDocument.relativePath
            }
            val fragmentGraph = outcome.graphs.single { graph ->
                graph.source == fragmentInput.inputSnapshot.document.source
            }
            assertEquals(listOf(BOOK_ID), fragmentInput.targetTypeIds)
            assertTrue(fragmentGraph.rootTypeIds.isEmpty())
        }
        val graph = first.graphs.single { candidate ->
            candidate.rootTypeIds.isNotEmpty()
        }
        val resolvedInput = first.resolvedInputs.single { input ->
            input.inputSnapshot.document.source == graph.source
        }
        assertEquals(listOf(BOOK_ID), resolvedInput.targetTypeIds)
        val bookView = graph.typesById.getValue(graph.rootTypeIds.single())
        assertEquals("BookView", bookView.name)
        assertEquals(BOOK_ID, bookView.baseTypeId)
        assertEquals(
            listOf("id", "name"),
            bookView.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
        assertEquals(
            first.resolvedInputs.resolvedInputFingerprint(),
            reversed.resolvedInputs.resolvedInputFingerprint(),
        )
        assertEquals(
            dtoSemanticFingerprint(
                first.graphs,
                first.annotationContractsBySource,
                first.interfaceContractsBySource,
                first.configContractsBySource,
            ),
            dtoSemanticFingerprint(
                reversed.graphs,
                reversed.annotationContractsBySource,
                reversed.interfaceContractsBySource,
                reversed.configContractsBySource,
            ),
        )
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
        val graph = state.graphs.single()
        val contract = state.annotationContractsBySource.getValue(graph.source)
        val rootTypeId = graph.rootTypeIds.single()
        val namePropId = graph.props.single().id
        val noTargetDeclaration = contract.declarationsByTypeId.getValue(noTargetTypeId)
        val typeApplications = contract.typePlansByTypeId.getValue(rootTypeId).applications
        val propApplications = contract.propPlansByPropId.getValue(namePropId).propertyApplications

        assertEquals(DtoFeatureStatus.RESOLVED, state.status)
        assertFalse(noTargetDeclaration.targetDeclared)
        assertEquals(
            listOf(DtoAnnotationPlacement.TYPE),
            typeApplications.single { application -> application.annotation.type == noTargetTypeId }.placements,
        )
        assertTrue(propApplications.none { application -> application.annotation.type == noTargetTypeId })
        assertEquals(
            DtoAnnotationOrigin.DTO,
            propApplications.single { application -> application.annotation.type == notNullTypeId }.origin,
        )
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
        assertTrue(contract.normalizedSnapshot().isNotEmpty())
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
        assertEquals(DtoFeatureStatus.INVALID, state.status)
        val graph = state.graphs.single()
        val contractDiagnostics =
            state.annotationContractsBySource.getValue(graph.source).diagnostics +
                state.interfaceContractsBySource.getValue(graph.source).diagnostics
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
    fun `annotation and interface contracts participate in semantic and state fingerprints`() {
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
        )
        val graph = baseline.graphs.single()
        val source = graph.source
        val baselineAnnotationContract = baseline.annotationContractsBySource.getValue(source)
        val baselineInterfaceResolution = baseline.interfaceContractsBySource.getValue(source)
        val markerTypeId = LsiSymbolId.type("demo.RenderMarker")
        val rootTypeId = graph.rootTypeIds.single()
        val annotationContract = baselineAnnotationContract.copy(
            declarations = listOf(
                DtoAnnotationDeclaration(
                    typeId = markerTypeId,
                    language = LsiLanguage.JAVA,
                    targetDeclared = true,
                    allowedPlacements = listOf(DtoAnnotationPlacement.TYPE),
                    argumentTypes = emptyMap(),
                    kotlinValueVararg = false,
                )
            ),
            typePlans = baselineAnnotationContract.typePlans.map { plan ->
                if (plan.typeId != rootTypeId) {
                    plan
                } else {
                    plan.copy(
                        applications = listOf(
                            DtoAnnotationApplication(
                                annotation = LsiAnnotation(markerTypeId),
                                origin = DtoAnnotationOrigin.DTO,
                                sourceSymbolId = null,
                                placements = listOf(DtoAnnotationPlacement.TYPE),
                            )
                        ),
                    )
                }
            },
        )
        val interfaceResolution = baselineInterfaceResolution.copy(
            contracts = baselineInterfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    val interfaceTypeId = LsiSymbolId.type("demo.RenderView")
                    val propertyId = LsiSymbolId.property(interfaceTypeId, "label")
                    val origin = LsiOrigin(LsiOriginKind.SYNTHETIC)
                    contract.copy(
                        superInterfaceTypeIds = listOf(interfaceTypeId),
                        props = listOf(
                            DtoInterfacePropContract(
                                declaringTypeId = interfaceTypeId,
                                name = "label",
                                type = STRING_TYPE,
                                mutable = false,
                                getter = DtoInterfaceAccessorContract(
                                    declarationId = propertyId,
                                    name = "label",
                                    origin = origin,
                                ),
                                setter = null,
                                origin = origin,
                            ),
                        ),
                    )
                }
            },
        )
        val annotationChanged = baseline.copy(
            annotationContractsBySource = sortedMapOf(source to annotationContract),
        )
        val interfaceChanged = baseline.copy(
            interfaceContractsBySource = sortedMapOf(source to interfaceResolution),
        )
        val interfaceProp = interfaceResolution.contracts.single { contract ->
            contract.typeId == rootTypeId
        }.props.single()
        val interfaceSuperTypesChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(
                        superInterfaceTypeIds = contract.superInterfaceTypeIds +
                            LsiSymbolId.type("demo.ExtraRenderView"),
                    )
                }
            },
        )
        val interfacePropDeclaringTypeChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(
                        props = listOf(
                            interfaceProp.copy(
                                declaringTypeId = LsiSymbolId.type("demo.OtherRenderView"),
                            ),
                        ),
                    )
                }
            },
        )
        val interfacePropTypeChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(
                        props = listOf(interfaceProp.copy(type = LONG_TYPE)),
                    )
                }
            },
        )
        val interfacePropMutabilityChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    val setterId = LsiSymbolId.function(
                        interfaceProp.declaringTypeId,
                        "setLabel",
                        listOf(interfaceProp.type.stableSignature()),
                    )
                    contract.copy(
                        props = listOf(
                            interfaceProp.copy(
                                mutable = true,
                                setter = DtoInterfaceAccessorContract(
                                    declarationId = setterId,
                                    name = "setLabel",
                                    origin = interfaceProp.origin,
                                ),
                            ),
                        ),
                    )
                }
            },
        )
        val interfacePropAccessorChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(
                        props = listOf(
                            interfaceProp.copy(
                                getter = requireNotNull(interfaceProp.getter).copy(name = "getLabel"),
                            ),
                        ),
                    )
                }
            },
        )
        val interfacePropAccessorDeclarationChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    contract.copy(
                        props = listOf(
                            interfaceProp.copy(
                                getter = requireNotNull(interfaceProp.getter).copy(
                                    declarationId = LsiSymbolId.property(
                                        interfaceProp.declaringTypeId,
                                        "alternateLabel",
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            },
        )
        val interfacePropOriginChanged = interfaceResolution.copy(
            contracts = interfaceResolution.contracts.map { contract ->
                if (contract.typeId != rootTypeId) {
                    contract
                } else {
                    val origin = LsiOrigin(
                        kind = LsiOriginKind.GENERATED,
                        originatingSymbols = setOf(interfaceProp.declaringTypeId),
                    )
                    contract.copy(
                        props = listOf(
                            interfaceProp.copy(
                                getter = requireNotNull(interfaceProp.getter).copy(origin = origin),
                                origin = origin,
                            ),
                        ),
                    )
                }
            },
        )

        val interfaceMutations = listOf(
            interfaceSuperTypesChanged,
            interfacePropDeclaringTypeChanged,
            interfacePropTypeChanged,
            interfacePropMutabilityChanged,
            interfacePropAccessorChanged,
            interfacePropAccessorDeclarationChanged,
            interfacePropOriginChanged,
        )
        val semanticFingerprint: (JimmerDtoRoundResolution) -> String = { resolution ->
            dtoSemanticFingerprint(
                resolution.graphs,
                resolution.annotationContractsBySource,
                resolution.interfaceContractsBySource,
                resolution.configContractsBySource,
            )
        }

        assertTrue(baselineAnnotationContract.normalizedSnapshot().isNotEmpty())
        assertTrue(baselineInterfaceResolution.normalizedSnapshot().isNotEmpty())
        assertNotEquals(baselineAnnotationContract.fingerprint(), annotationContract.fingerprint())
        assertNotEquals(baselineInterfaceResolution.fingerprint(), interfaceResolution.fingerprint())
        interfaceMutations.forEach { mutation ->
            assertNotEquals(interfaceResolution.normalizedSnapshot(), mutation.normalizedSnapshot())
            assertNotEquals(interfaceResolution.fingerprint(), mutation.fingerprint())
        }
        assertNotEquals(semanticFingerprint(baseline), semanticFingerprint(annotationChanged))
        assertNotEquals(semanticFingerprint(baseline), semanticFingerprint(interfaceChanged))
        assertNotEquals(dtoState(baseline).fingerprint, dtoState(annotationChanged).fingerprint)
        assertNotEquals(dtoState(baseline).fingerprint, dtoState(interfaceChanged).fingerprint)
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
        val graph = outcome.graphs.single()
        val dtoType = graph.typesById.getValue(graph.rootTypeIds.single())
        val polymorphism = assertNotNull(dtoType.polymorphism)
        assertFalse(polymorphism.exhaustive)
        assertTrue(polymorphism.branches.single { branch ->
            branch.kind == DtoPolymorphicBranchKind.DEFAULT
        }.implicit)
        val branch = polymorphism.branches.single { value ->
            value.kind == DtoPolymorphicBranchKind.TYPE
        }
        assertEquals(ORGANIZATION_ID, branch.targetBaseTypeId)
        assertEquals(CLIENT_ID, schema.typesById.getValue(ORGANIZATION_ID).primarySuperTypeId)
        assertEquals(CLIENT_ID, schema.typesById.getValue(ORGANIZATION_ID).inheritanceRootTypeId)
        val branchType = graph.typesById.getValue(branch.bodyTypeId)
        assertEquals(
            listOf("taxCode"),
            branchType.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
        assertTrue("branch|" in graph.normalizedSnapshot())
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
        )
        val reversed = precompiler.compile(
            inputDocumentSnapshots = listOf(author, book).map(REFERENCE_FREEZER::freeze),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(AUTHOR_ID, BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(AUTHOR_ID, BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )
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
        )

        assertEquals(listOf("Author.dto", "Book.dto"), first.resolvedInputs.map { input ->
            input.inputSnapshot.document.relativePath.substringAfterLast('/')
        })
        assertEquals(
            first.resolvedInputs.resolvedInputFingerprint(),
            reversed.resolvedInputs.resolvedInputFingerprint(),
        )
        assertEquals(
            dtoSemanticFingerprint(
                first.graphs,
                first.annotationContractsBySource,
                first.interfaceContractsBySource,
                first.configContractsBySource,
            ),
            dtoSemanticFingerprint(
                reversed.graphs,
                reversed.annotationContractsBySource,
                reversed.interfaceContractsBySource,
                reversed.configContractsBySource,
            ),
        )
        assertNotEquals(
            first.resolvedInputs.resolvedInputFingerprint(),
            whitespaceChanged.resolvedInputs.resolvedInputFingerprint(),
        )
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
        )
        val same = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument(""))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )
        val whitespaceChanged = precompiler.compile(
            inputDocumentSnapshots = listOf(REFERENCE_FREEZER.freeze(bookDocument("\n"))),
            immutableSchema = schema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = immutableHeaderWorkspace(listOf(BOOK_ID)),
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )

        assertTrue(empty.graphs.single().rootTypeIds.isEmpty())
        assertEquals(1, empty.resolvedInputs.size)
        assertEquals(
            empty.resolvedInputs.resolvedInputFingerprint(),
            same.resolvedInputs.resolvedInputFingerprint(),
        )
        assertNotEquals(
            empty.resolvedInputs.resolvedInputFingerprint(),
            whitespaceChanged.resolvedInputs.resolvedInputFingerprint(),
        )
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
        assertEquals(DtoFeatureStatus.DEFERRED, firstState.status)
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
        assertEquals(DtoFeatureStatus.RESOLVED, secondState.status)
        val secondGraph = secondState.graphs.single()
        assertEquals(
            listOf("BookView"),
            secondGraph.rootTypeIds.map { typeId ->
                secondGraph.typesById.getValue(typeId).name
            },
        )
        assertEquals(setOf(BOOK_ID), second.dtoResult().processedSymbols)
        val artifact = second.dtoResult().artifacts.single()
        assertEquals(ArtifactKind.JAVA_SOURCE, artifact.kind)
        assertEquals("demo/dto/BookView.java", artifact.path)
        assertEquals(ArtifactAggregationMode.AGGREGATING, artifact.aggregationMode)
        assertEquals(ArtifactEmissionMode.IMMEDIATE, artifact.emissionMode)
        assertTrue(secondGraph.source in artifact.originatingSources)
        assertTrue(secondGraph.source in artifact.dependencySources)
        assertTrue(BOOK_ID in artifact.dependencySymbols)
        assertTrue("public class BookView" in artifact.content)
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
        assertEquals(DtoFeatureStatus.DEFERRED, first.dtoState().status)
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
        assertEquals(DtoFeatureStatus.RESOLVED, second.dtoState().status)
        val contract = second.dtoState().configContractsBySource.values.single().contracts.single()
        assertEquals(FILTER_ID, contract.implementationTypeId)
        assertEquals(AUTHOR_ID, contract.targetEntityTypeId)
        assertEquals(listOf(AUTHOR_ID, FILTER_ID), contract.dependencyTypeIds)
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
            reference.kind == DTO_CONFIG_IMPLEMENTATION_REFERENCE_KIND
        }
        assertEquals(DtoFeatureStatus.INVALID, final.dtoState().status)
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
        assertEquals(DtoFeatureStatus.PENDING, first.dtoState().status)
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
        assertEquals(DtoFeatureStatus.RESOLVED, second.dtoState().status)
        val secondGraph = second.dtoState().graphs.single()
        assertEquals(
            listOf("BookView"),
            secondGraph.rootTypeIds.map { typeId ->
                secondGraph.typesById.getValue(typeId).name
            },
        )
        assertEquals(setOf(BOOK_ID), second.dtoResult().processedSymbols)
        val artifact = second.dtoResult().artifacts.single()
        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals("demo/dto/BookView.kt", artifact.path)
        assertEquals(ArtifactAggregationMode.AGGREGATING, artifact.aggregationMode)
        assertEquals(ArtifactEmissionMode.IMMEDIATE, artifact.emissionMode)
        assertTrue(secondGraph.source in artifact.originatingSources)
        assertTrue(secondGraph.source in artifact.dependencySources)
        assertTrue(BOOK_ID in artifact.dependencySymbols)
        assertTrue("class BookView" in artifact.content)
    }

    @Test
    fun `ksp waits for filesystem discovery before dto generation`() {
        val workspace = immutableWorkspace(LsiLanguage.KOTLIN)
        val document = bookDocument("BookView { id }")
        val active = session("dto-ksp-input-discovery").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
                inputDocumentDiscoveryComplete = false,
            )
        )

        assertEquals(DtoFeatureStatus.INPUT_PENDING, active.dtoState().status)
        assertTrue(active.dtoResult().processedSymbols.isEmpty())
        assertTrue(active.diagnostics.isEmpty())
        assertTrue(active.dtoResult().artifacts.isEmpty())

        val final = session("dto-ksp-input-discovery-final").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = emptySet(),
                platform = CompilerPlatform.KSP,
                inputDocuments = listOf(document),
                inputDocumentDiscoveryComplete = false,
                isFinal = true,
                options = mapOf(
                    CompilerInputDocumentBundleRenderer.BUNDLE_ID_OPTION to "org.example:incomplete",
                ),
            )
        )
        assertEquals(DtoFeatureStatus.INVALID, final.dtoState().status)
        assertEquals(listOf("jimmer.dto.input-discovery"), final.diagnostics.map { it.code })
        assertTrue(final.newArtifacts.isEmpty())
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

        assertEquals(DtoFeatureStatus.INVALID, kspFinal.dtoState().status)
        assertEquals("jimmer.dto.unresolved", kspFinal.diagnostics.single().code)
        assertTrue(kspFinal.unresolvedSymbols.isEmpty())
        assertEquals(DtoFeatureStatus.INVALID, aptFinal.dtoState().status)
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

            val apt = session("dto-missing-${fixture.kind.id}-apt").execute(
                round(
                    number = 0,
                    workspace = fixture.workspace,
                    currentWorkspace = fixture.workspace,
                    platform = CompilerPlatform.APT,
                    inputDocuments = listOf(fixture.document),
                )
            )
            assertEquals(DtoFeatureStatus.DEFERRED, apt.dtoState().status)
            assertEquals(
                listOf(fixture.missingTypeId),
                apt.dtoState().unresolvedDocuments.single().unresolvedTypeIds,
            )
            assertEquals(setOf(fixture.missingTypeId), apt.unresolvedSymbols)
            assertTrue(apt.diagnostics.isEmpty())

            val ksp = session("dto-missing-${fixture.kind.id}-ksp").execute(
                round(
                    number = 0,
                    workspace = fixture.workspace,
                    currentWorkspace = fixture.workspace,
                    platform = CompilerPlatform.KSP,
                    inputDocuments = listOf(fixture.document),
                )
            )
            assertEquals(DtoFeatureStatus.PENDING, ksp.dtoState().status)
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
                    "dto-missing-${fixture.kind.id}-${platform.name.lowercase()}-final"
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
                assertEquals(DtoFeatureStatus.INVALID, final.dtoState().status)
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

            assertEquals(DtoFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().graphs.isEmpty())
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

            assertEquals(DtoFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().graphs.isEmpty())
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

            assertEquals(DtoFeatureStatus.RESOLVED, result.dtoState().status)
            assertTrue(result.dtoState().graphs.isEmpty())
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

        assertEquals(DtoFeatureStatus.INVALID, result.dtoState().status)
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

        assertEquals(DtoFeatureStatus.RESOLVED, result.dtoState().status)
        assertTrue(result.dtoState().graphs.isEmpty())
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
            assertEquals(DtoFeatureStatus.RESOLVED, state.status)
            assertTrue(state.unresolvedDocuments.isEmpty())
            assertTrue(state.failures.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
            val graph = state.graphs.single()
            assertEquals(setOf(AUTHOR_ID), result.dtoResult().processedSymbols)
            assertEquals(
                listOf("AuthorView" to AUTHOR_ID),
                graph.rootTypeIds.map { rootTypeId ->
                    val rootType = graph.typesById.getValue(rootTypeId)
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

        assertEquals(DtoFeatureStatus.RESOLVED, apt.status)
        assertEquals(DtoFeatureStatus.RESOLVED, ksp.status)
        assertEquals(apt.resolvedInputFingerprint, ksp.resolvedInputFingerprint)
        assertEquals(
            apt.graphs.map { graph -> graph.normalizedSnapshot() },
            ksp.graphs.map { graph -> graph.normalizedSnapshot() },
        )
        assertEquals(
            dtoSemanticFingerprint(
                apt.graphs,
                apt.annotationContractsBySource,
                apt.interfaceContractsBySource,
                apt.configContractsBySource,
            ),
            dtoSemanticFingerprint(
                ksp.graphs,
                ksp.annotationContractsBySource,
                ksp.interfaceContractsBySource,
                ksp.configContractsBySource,
            ),
        )
        assertEquals(apt.fingerprint, ksp.fingerprint)
    }

    private fun session(id: String): CompilerSession {
        return CompilerSession(
            id = id,
            features = listOf(
                ImmutableFeature(),
                DtoFeature(),
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
        inputDocumentDiscoveryComplete: Boolean = true,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = workspace,
            currentWorkspace = currentWorkspace,
            currentRootTypeIds = currentRootTypeIds,
            platform = platform,
            isFinal = isFinal,
            options = options,
            inputDocumentDiscoveryComplete = inputDocumentDiscoveryComplete,
            inputDocumentSnapshots = inputDocuments.map(REFERENCE_FREEZER::freeze),
        )
    }

    private fun CompilerRoundResult.dtoResult() = featureResults.getValue(DtoFeature.Key)

    private fun CompilerRoundResult.dtoState(): DtoFeatureState {
        return dtoResult().state
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

    private fun dtoState(resolution: JimmerDtoRoundResolution): DtoFeatureState {
        return DtoFeatureState(
            status = DtoFeatureStatus.RESOLVED,
            dependencyStatus = CompilerResolutionStatus.RESOLVED,
            graphs = resolution.graphs,
            annotationContractsBySource = resolution.annotationContractsBySource,
            interfaceContractsBySource = resolution.interfaceContractsBySource,
            configContractsBySource = resolution.configContractsBySource,
            resolvedInputFingerprint = resolution.resolvedInputs.resolvedInputFingerprint(),
            unresolvedDocuments = emptyList(),
            failures = emptyList(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            rendererOptions = rendererOptions(CompilerPlatform.UNKNOWN),
            effectiveKspMutableByRootTypeId = resolution.graphs
                .flatMap(DtoGraph::rootTypeIds)
                .sorted()
                .associateWith { false },
            inputDocumentDiscoveryComplete = true,
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
                kind = DTO_TARGET_TYPE_REFERENCE_KIND,
                missingTypeId = MISSING_BOOK_ID,
                document = document(
                    relativePath = "shared/Shared.dto",
                    content = "MissingBookView for demo.MissingBook {}",
                ),
                workspace = LsiWorkspace.EMPTY,
            ),
            MissingReferenceFixture(
                kind = DTO_SUBJECT_TYPE_REFERENCE_KIND,
                missingTypeId = MISSING_BOOK_ID,
                document = document(
                    relativePath = "demo/MissingBook.dto",
                    content = "export demo.MissingBook\nMissingBookView {}",
                ),
                workspace = LsiWorkspace.EMPTY,
            ),
            MissingReferenceFixture(
                kind = DTO_MODEL_TYPE_REFERENCE_KIND,
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
                kind = DTO_TYPE_USAGE_REFERENCE_KIND,
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
            kind = DTO_INPUT_DOCUMENT_KIND,
            sourceSet = CompilerSourceSet.MAIN,
            origin = CompilerInputDocumentOrigin.Project("demo-project", "src/main/dto"),
            relativePath = relativePath,
            content = content,
        )
    }

    private companion object {

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
