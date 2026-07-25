package site.addzero.lsi.jimmer.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.jimmer.error.ErrorCode
import site.addzero.lsi.jimmer.error.ErrorFamily
import site.addzero.lsi.jimmer.error.ErrorField
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature

class ClientWorkspaceExtensionsTest {

    @Test
    fun `discovers targets and isolates unresolved service roots`() {
        val serviceId = LsiSymbolId.type("demo.PartialService")
        val missingOperationId = LsiSymbolId.function(serviceId, "find")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = serviceId.requireTypeQualifiedName(),
                    annotations = listOf(api()),
                    memberIds = listOf(missingOperationId),
                )
            )
        )

        val targets = workspace.clientTargets()
        val unresolvedTypeIds = workspace.unresolvedClientTargetTypeIds(targets)

        assertEquals(setOf(serviceId), targets.serviceTypeIds)
        assertEquals(setOf(serviceId), unresolvedTypeIds)
        assertTrue(
            workspace.toClientSchema(
                targets = targets.without(unresolvedTypeIds),
                dependencies = EMPTY_ERROR_SCHEMA.clientDependencies(),
            ).services.isEmpty()
        )
    }

    @Test
    fun `resolves service operation parameters exceptions and fetch by`() {
        val bookId = LsiSymbolId.type("demo.Book")
        val exceptionId = LsiSymbolId.type("demo.BookException")
        val serviceId = LsiSymbolId.type("demo.BookService")
        val fetcherOwnerId = LsiSymbolId.type("demo.BookFetchers")
        val detailFetcher = fetcher(fetcherOwnerId, "DETAIL_FETCHER", bookId)
        val operation = function(
            ownerId = serviceId,
            name = "findBook",
            parameters = listOf(
                ParameterSpec("id", LsiPrimitiveType(LsiPrimitiveKind.LONG)),
                ParameterSpec(
                    name = "principal",
                    type = LsiDeclaredType(LsiSymbolId.type("java.security.Principal")),
                    annotations = listOf(annotation(API_IGNORE)),
                ),
            ),
            returnType = LsiDeclaredType(bookId),
            annotations = listOf(
                api("public"),
                fetchBy("DETAIL_FETCHER", nullable = true),
            ),
            thrownTypes = listOf(LsiDeclaredType(exceptionId)),
            documentation = "查找图书。\r\n  返回完整视图。  ",
            origin = sourceOrigin(LsiLanguage.JAVA),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Book",
                    annotations = listOf(annotation(ENTITY)),
                ),
                type(qualifiedName = "demo.BookException"),
                type(
                    qualifiedName = "demo.BookFetchers",
                    memberIds = listOf(detailFetcher.id),
                ),
                detailFetcher,
                type(
                    qualifiedName = "demo.BookService",
                    memberIds = listOf(operation.id),
                    annotations = listOf(
                        api("public", "admin"),
                        annotation(
                            DEFAULT_FETCHER_OWNER,
                            mapOf(
                                "value" to LsiAnnotationValue.ClassValue(
                                    LsiDeclaredType(LsiSymbolId.type("demo.BookFetchers"))
                                )
                            ),
                        ),
                    ),
                    documentation = "图书服务。",
                ),
                operation,
            ),
        )

        val schema = workspace.toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())

        val service = schema.services.single()
        assertEquals(serviceId, service.id)
        assertEquals(listOf("public", "admin"), service.groups)
        assertEquals("图书服务。", service.doc)
        val compiledOperation = service.operations.single()
        assertEquals("findBook", compiledOperation.name)
        assertEquals(listOf("public"), compiledOperation.groups)
        assertEquals("查找图书。\n  返回完整视图。", compiledOperation.doc)
        assertEquals(exceptionId, compiledOperation.declaredExceptionTypeIds.single())
        assertTrue(compiledOperation.exceptionTypeIds.isEmpty())
        assertTrue(compiledOperation.exceptionMetadata.isEmpty())
        assertEquals("id", compiledOperation.parameters.single().name)
        assertEquals(0, compiledOperation.parameters.single().originalIndex)
        assertEquals("principal", compiledOperation.ignoredParameters.single().name)
        assertEquals(1, compiledOperation.ignoredParameters.single().originalIndex)
        val returnType = assertIs<ClientDeclaredTypeRef>(compiledOperation.returnType)
        assertEquals(bookId, returnType.typeId)
        assertTrue(returnType.nullable)
        val fetchBy = requireNotNull(returnType.fetchBy)
        assertEquals("DETAIL_FETCHER", fetchBy.value)
        assertEquals(LsiSymbolId.type("demo.BookFetchers"), fetchBy.ownerTypeId)
        assertEquals(bookId, fetchBy.targetEntityTypeId)
        assertEquals(64, schema.fingerprint().length)
    }

    @Test
    fun `resolves reachable immutable enum and polymorphic definitions`() {
        val bookId = LsiSymbolId.type("demo.Book")
        val titleProp = property(bookId, "title")
        val resultId = LsiSymbolId.type("demo.SearchResult")
        val branchId = LsiSymbolId.type("demo.SearchResult.BookResult")
        val laterBranchId = LsiSymbolId.type("demo.SearchResult.ArticleResult")
        val branchBookProp = property(branchId, "book", type = LsiDeclaredType(bookId))
        val categoryId = LsiSymbolId.type("demo.Category")
        val categoryProp = property(bookId, "category", type = LsiDeclaredType(categoryId))
        val serviceId = LsiSymbolId.type("demo.DefinitionService")
        val operation = function(
            ownerId = serviceId,
            name = "search",
            returnType = LsiDeclaredType(resultId),
            annotations = listOf(api()),
        )
        val categoryEntry = LsiEnumEntry(
            id = LsiSymbolId("${categoryId.value}#BOOK"),
            name = "BOOK",
            ownerId = categoryId,
            documentation = "Book category.",
            origin = SYNTHETIC_ORIGIN,
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Book",
                    annotations = listOf(annotation(IMMUTABLE)),
                    memberIds = listOf(titleProp.id, categoryProp.id),
                    documentation = "Book model.",
                ),
                titleProp,
                categoryProp,
                type(
                    qualifiedName = "demo.SearchResult",
                    documentation = "Search result.",
                ),
                type(
                    qualifiedName = "demo.SearchResult.ArticleResult",
                    kind = LsiTypeDeclarationKind.CLASS,
                    enclosingTypeId = resultId,
                    annotations = listOf(
                        annotation(
                            GENERATED_POLYMORPHIC_BRANCH,
                            mapOf(
                                "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(resultId)),
                                "order" to LsiAnnotationValue.IntValue(1),
                            ),
                        )
                    ),
                ),
                type(
                    qualifiedName = "demo.SearchResult.BookResult",
                    kind = LsiTypeDeclarationKind.CLASS,
                    enclosingTypeId = resultId,
                    memberIds = listOf(branchBookProp.id),
                    annotations = listOf(
                        annotation(
                            GENERATED_POLYMORPHIC_BRANCH,
                            mapOf(
                                "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(resultId)),
                                "order" to LsiAnnotationValue.IntValue(0),
                            ),
                        )
                    ),
                ),
                branchBookProp,
                type(
                    qualifiedName = "demo.Category",
                    kind = LsiTypeDeclarationKind.ENUM,
                    enumEntries = listOf(categoryEntry),
                ),
                categoryEntry,
                type(
                    qualifiedName = "demo.DefinitionService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
            ),
        )
        val immutableSchema = workspace.toImmutableSchema(setOf(bookId))
        val schema = workspace.toClientSchema(
            ClientSchemaDependencies(
                immutableSchema = immutableSchema,
                errorSchema = EMPTY_ERROR_SCHEMA,
                definitionDocumentationByTypeId = emptyMap(),
            ),
        )

        val definitions = schema.definitions.associateBy(ClientTypeDefinition::id)
        assertEquals(ClientDefinitionKind.IMMUTABLE, definitions.getValue(bookId).kind)
        assertEquals(listOf("title", "category"), definitions.getValue(bookId).properties.map { it.name })
        assertEquals(
            listOf(branchId, laterBranchId),
            definitions.getValue(resultId).polymorphicBranches.map { it.typeId },
        )
        assertEquals(listOf("SearchResult", "BookResult"), definitions.getValue(branchId).typeName.simpleNames)
        assertEquals(listOf("BOOK"), definitions.getValue(categoryId).enumConstants.map { it.name })
        assertTrue(schema.normalizedSnapshot().contains("definition|type:demo.Book"))
    }

    @Test
    fun `uses frozen dto documentation for generated definitions`() {
        val dtoId = LsiSymbolId.type("demo.BookInput")
        val nameProp = property(dtoId, "name")
        val editionProp = property(
            ownerId = dtoId,
            name = "edition",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
        )
        val serviceId = LsiSymbolId.type("demo.BookService")
        val operation = function(
            ownerId = serviceId,
            name = "save",
            returnType = LsiDeclaredType(dtoId),
            annotations = listOf(api()),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.BookInput",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(nameProp.id, editionProp.id),
                ),
                nameProp,
                editionProp,
                type(
                    qualifiedName = "demo.BookService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
            ),
        )
        val schema = workspace.toClientSchema(
            ClientSchemaDependencies(
                immutableSchema = ImmutableSchema(emptyList()),
                errorSchema = EMPTY_ERROR_SCHEMA,
                definitionDocumentationByTypeId = mapOf(
                    dtoId to ClientDefinitionDocumentation(
                        type = "Book input.",
                        properties = mapOf(
                            "name" to "Book name.",
                            "edition" to "Book edition.",
                        ),
                    )
                ),
            ),
        )

        val definition = schema.definitions.single { definition -> definition.id == dtoId }
        assertEquals("Book input.", definition.doc)
        assertEquals(
            mapOf("name" to "Book name.", "edition" to "Book edition."),
            definition.properties.associate { property -> property.name to property.doc },
        )
    }

    @Test
    fun `rejects missing and duplicate polymorphic branch orders`() {
        fun compile(vararg branchOrders: Int?): ClientValidationException {
            val resultId = LsiSymbolId.type("demo.OrderedResult")
            val serviceId = LsiSymbolId.type("demo.OrderedResultService")
            val operation = function(
                ownerId = serviceId,
                name = "find",
                returnType = LsiDeclaredType(resultId),
                annotations = listOf(api()),
            )
            val branches = branchOrders.mapIndexed { index, order ->
                val branchId = LsiSymbolId.type("demo.OrderedResult.Branch$index")
                type(
                    qualifiedName = branchId.requireTypeQualifiedName(),
                    kind = LsiTypeDeclarationKind.CLASS,
                    enclosingTypeId = resultId,
                    annotations = listOf(
                        annotation(
                            GENERATED_POLYMORPHIC_BRANCH,
                            buildMap {
                                put("value", LsiAnnotationValue.ClassValue(LsiDeclaredType(resultId)))
                                order?.let { value -> put("order", LsiAnnotationValue.IntValue(value)) }
                            },
                        )
                    ),
                )
            }
            val workspace = LsiWorkspace(
                declarations = listOf(
                    type(qualifiedName = resultId.requireTypeQualifiedName()),
                    type(
                        qualifiedName = serviceId.requireTypeQualifiedName(),
                        annotations = listOf(api()),
                        memberIds = listOf(operation.id),
                    ),
                    operation,
                ) + branches,
            )
            return assertFailsWith {
                workspace.toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
            }
        }

        val missingOrder = compile(null)
        assertTrue(missingOrder.message.orEmpty().contains("has no order"))

        val duplicateOrder = compile(0, 0)
        assertTrue(duplicateOrder.message.orEmpty().contains("duplicate branch order 0"))
    }

    @Test
    fun `requests full declarations for the reachable client definition closure`() {
        val serviceId = LsiSymbolId.type("demo.ExternalService")
        val externalId = LsiSymbolId.type("external.Envelope")
        val payloadId = LsiSymbolId.type("external.Payload")
        val operation = function(
            ownerId = serviceId,
            name = "load",
            returnType = LsiDeclaredType(externalId),
            annotations = listOf(api()),
        )
        val envelopePayload = property(externalId, "payload", type = LsiDeclaredType(payloadId))
        val initialWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.ExternalService",
                    memberIds = listOf(operation.id),
                    annotations = listOf(api()),
                ),
                operation,
                type(qualifiedName = "external.Envelope", kind = LsiTypeDeclarationKind.CLASS),
            ),
        )
        assertEquals(
            listOf(externalId),
            initialWorkspace.requestedClientTypeSeeds().map { seed -> seed.typeId },
        )

        val expandedWorkspace = LsiWorkspace(
            declarations = initialWorkspace.declarations.filterNot { declaration ->
                declaration.id == externalId
            } + listOf(
                type(
                    qualifiedName = "external.Envelope",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(envelopePayload.id),
                ),
                envelopePayload,
                type(qualifiedName = "external.Payload", kind = LsiTypeDeclarationKind.CLASS),
            ),
        )
        assertEquals(
            listOf(externalId, payloadId),
            expandedWorkspace.requestedClientTypeSeeds().map { seed -> seed.typeId },
        )
        assertTrue(
            expandedWorkspace.requestedClientTypeSeeds().all { seed ->
                seed.mode == LsiTypeSeedMode.FULL_DECLARATION
            }
        )
    }

    @Test
    fun `immutable converter target contributes a full definition seed and closure`() {
        val serviceId = LsiSymbolId.type("demo.ConverterService")
        val bookId = LsiSymbolId.type("demo.ConvertedBook")
        val converterId = LsiSymbolId.type("demo.ExternalPojoConverter")
        val externalPojoId = LsiSymbolId.type("external.ExternalPojo")
        val nestedId = LsiSymbolId.type("external.Nested")
        val convertedProp = property(
            ownerId = bookId,
            name = "payload",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.lang.String"),
                nullability = LsiNullability.NULLABLE,
            ),
            annotations = listOf(
                annotation(
                    JSON_CONVERTER,
                    mapOf(
                        "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(converterId))
                    ),
                )
            ),
        )
        val operation = function(
            ownerId = serviceId,
            name = "find",
            returnType = LsiDeclaredType(bookId),
            annotations = listOf(api()),
        )
        val externalNested = property(
            ownerId = externalPojoId,
            name = "nested",
            type = LsiDeclaredType(nestedId),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.ConvertedBook",
                    annotations = listOf(annotation(IMMUTABLE)),
                    memberIds = listOf(convertedProp.id),
                ),
                convertedProp,
                type(
                    qualifiedName = "demo.ExternalPojoConverter",
                    kind = LsiTypeDeclarationKind.CLASS,
                    superTypes = listOf(
                        LsiDeclaredType(
                            declarationId = CONVERTER,
                            arguments = listOf(
                                LsiTypeArgument.invariant(
                                    LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
                                ),
                                LsiTypeArgument.invariant(LsiDeclaredType(externalPojoId)),
                            ),
                        )
                    ),
                ),
                type(
                    qualifiedName = "demo.ConverterService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
                type(
                    qualifiedName = "external.ExternalPojo",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(externalNested.id),
                ),
                externalNested,
                type(qualifiedName = "external.Nested", kind = LsiTypeDeclarationKind.CLASS),
            ),
        )
        val dependencies = ClientSchemaDependencies(
            immutableSchema = workspace.toImmutableSchema(setOf(bookId)),
            errorSchema = EMPTY_ERROR_SCHEMA,
            definitionDocumentationByTypeId = emptyMap(),
        )

        val seeds = workspace.requestedClientTypeSeeds(dependencies)

        assertEquals(
            LsiTypeSeedMode.FULL_DECLARATION,
            seeds.single { seed -> seed.typeId == externalPojoId }.mode,
        )
        assertEquals(
            LsiTypeSeedMode.FULL_DECLARATION,
            seeds.single { seed -> seed.typeId == nestedId }.mode,
        )
        val definitions = workspace.toClientSchema(dependencies)
            .definitions
            .associateBy(ClientTypeDefinition::id)
        val convertedType = assertIs<ClientDeclaredTypeRef>(
            definitions.getValue(bookId).properties.single().type
        )
        assertEquals(externalPojoId, convertedType.typeId)
        assertFalse(convertedType.nullable)
        assertEquals(
            listOf("nested"),
            definitions.getValue(externalPojoId).properties.map(ClientDefinitionProperty::name),
        )
    }

    @Test
    fun `generated error field type contributes a full definition seed and closure`() {
        val serviceId = LsiSymbolId.type("demo.GeneratedErrorService")
        val familyId = LsiSymbolId.type("demo.GeneratedErrorCode")
        val familyExceptionId = LsiSymbolId.type("demo.GeneratedErrorException")
        val codeExceptionId = LsiSymbolId.type("demo.GeneratedErrorException.Invalid")
        val externalPojoId = LsiSymbolId.type("external.ExternalPojo")
        val externalName = property(externalPojoId, "name")
        val operation = function(
            ownerId = serviceId,
            name = "execute",
            annotations = listOf(api()),
            thrownTypes = listOf(LsiDeclaredType(codeExceptionId)),
        )
        val field = ErrorField(
            name = "detail",
            type = LsiDeclaredType(externalPojoId),
            list = false,
            nullable = false,
            documentation = "External error detail.",
            declaredBy = familyId,
        )
        val dependencies = ClientSchemaDependencies(
            immutableSchema = ImmutableSchema(emptyList()),
            errorSchema = ErrorSchema(
                families = listOf(
                    ErrorFamily(
                        id = familyId,
                        qualifiedName = "demo.GeneratedErrorCode",
                        packageName = "demo",
                        family = "GENERATED",
                        exceptionTypeId = familyExceptionId,
                        exceptionSimpleName = "GeneratedErrorException",
                        checkedException = true,
                        documentation = "Generated error family.",
                        declaredFields = listOf(field),
                        codes = listOf(
                            ErrorCode(
                                id = LsiSymbolId("${familyId.value}#INVALID"),
                                enumEntryName = "INVALID",
                                code = "INVALID",
                                creatorName = "invalid",
                                exceptionTypeId = codeExceptionId,
                                exceptionSimpleName = "Invalid",
                                documentation = "Invalid error.",
                                declaredFields = emptyList(),
                            )
                        ),
                    )
                ),
            ),
            definitionDocumentationByTypeId = emptyMap(),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.GeneratedErrorService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
                type(
                    qualifiedName = "external.ExternalPojo",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(externalName.id),
                ),
                externalName,
            ),
        )

        val seeds = workspace.requestedClientTypeSeeds(dependencies)

        assertEquals(
            LsiTypeSeedMode.FULL_DECLARATION,
            seeds.single { seed -> seed.typeId == externalPojoId }.mode,
        )
        val definitions = workspace.toClientSchema(dependencies)
            .definitions
            .associateBy(ClientTypeDefinition::id)
        val generatedError = definitions.getValue(codeExceptionId)
        assertEquals("detail", generatedError.properties.single().name)
        assertEquals(
            externalPojoId,
            assertIs<ClientDeclaredTypeRef>(generatedError.properties.single().type).typeId,
        )
        assertEquals(
            listOf("name"),
            definitions.getValue(externalPojoId).properties.map(ClientDefinitionProperty::name),
        )
    }

    @Test
    fun `json value return type contributes a full definition seed and closure`() {
        val serviceId = LsiSymbolId.type("demo.JsonValueService")
        val wrapperId = LsiSymbolId.type("external.JsonValueEnvelope")
        val externalPojoId = LsiSymbolId.type("external.ExternalPojo")
        val externalName = property(externalPojoId, "name")
        val jsonValue = function(
            ownerId = wrapperId,
            name = "value",
            returnType = LsiDeclaredType(externalPojoId),
            annotations = listOf(annotation(JSON_VALUE)),
        )
        val operation = function(
            ownerId = serviceId,
            name = "load",
            returnType = LsiDeclaredType(wrapperId),
            annotations = listOf(api()),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "external.JsonValueEnvelope",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(jsonValue.id),
                ),
                jsonValue,
                type(
                    qualifiedName = "external.ExternalPojo",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(externalName.id),
                ),
                externalName,
                type(
                    qualifiedName = "demo.JsonValueService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
            ),
        )

        val seeds = workspace.requestedClientTypeSeeds()

        assertEquals(
            LsiTypeSeedMode.FULL_DECLARATION,
            seeds.single { seed -> seed.typeId == wrapperId }.mode,
        )
        assertEquals(
            LsiTypeSeedMode.FULL_DECLARATION,
            seeds.single { seed -> seed.typeId == externalPojoId }.mode,
        )
        val schema = workspace.toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        assertEquals(
            externalPojoId,
            assertIs<ClientDeclaredTypeRef>(schema.services.single().operations.single().returnType).typeId,
        )
        val definitions = schema.definitions.associateBy(ClientTypeDefinition::id)
        assertTrue(wrapperId !in definitions)
        assertEquals(
            listOf("name"),
            definitions.getValue(externalPojoId).properties.map(ClientDefinitionProperty::name),
        )
    }

    @Test
    fun `java object definitions only expose bean getters and preserve boxed nullability`() {
        val javaOrigin = sourceOrigin(LsiLanguage.JAVA)
        val dtoId = LsiSymbolId.type("demo.JavaDto")
        val serviceId = LsiSymbolId.type("demo.JavaDtoService")
        val nameProp = property(
            ownerId = dtoId,
            name = "name",
            getterName = "getName",
            type = LsiPrimitiveType(
                kind = LsiPrimitiveKind.INT,
                nullability = LsiNullability.PLATFORM,
                boxed = true,
            ),
            origin = javaOrigin,
        )
        val calculateProp = property(
            ownerId = dtoId,
            name = "calculate",
            getterName = "calculate",
            origin = javaOrigin,
        )
        val toStringProp = property(
            ownerId = dtoId,
            name = "toString",
            getterName = "toString",
            origin = javaOrigin,
        )
        val operation = function(
            ownerId = serviceId,
            name = "load",
            returnType = LsiDeclaredType(dtoId),
            annotations = listOf(api()),
            origin = javaOrigin,
        )
        val schema = LsiWorkspace(
            declarations = listOf(
                    type(
                        qualifiedName = "demo.JavaDto",
                        kind = LsiTypeDeclarationKind.CLASS,
                        memberIds = listOf(nameProp.id, calculateProp.id, toStringProp.id),
                        origin = javaOrigin,
                    ),
                    nameProp,
                    calculateProp,
                    toStringProp,
                    type(
                        qualifiedName = "demo.JavaDtoService",
                        memberIds = listOf(operation.id),
                        annotations = listOf(api()),
                        origin = javaOrigin,
                    ),
                    operation,
            ),
        ).toClientSchema(
            EMPTY_ERROR_SCHEMA.clientDependencies(),
        )

        val property = schema.definitions.single().properties.single()
        assertEquals("name", property.name)
        val propertyType = assertIs<ClientPrimitiveTypeRef>(property.type)
        assertEquals(LsiPrimitiveKind.INT, propertyType.kind)
        assertTrue(propertyType.nullable)
    }

    @Test
    fun `java object definitions accept underscore bean getters`() {
        val javaOrigin = sourceOrigin(LsiLanguage.JAVA)
        val tupleId = LsiSymbolId.type("demo.Tuple2")
        val serviceId = LsiSymbolId.type("demo.TupleService")
        val firstProp = property(
            ownerId = tupleId,
            name = "_1",
            getterName = "get_1",
            origin = javaOrigin,
        )
        val secondProp = property(
            ownerId = tupleId,
            name = "_2",
            getterName = "get_2",
            origin = javaOrigin,
        )
        val operation = function(
            ownerId = serviceId,
            name = "load",
            returnType = LsiDeclaredType(tupleId),
            annotations = listOf(api()),
            origin = javaOrigin,
        )
        val schema = LsiWorkspace(
            declarations = listOf(
                    type(
                        qualifiedName = "demo.Tuple2",
                        kind = LsiTypeDeclarationKind.CLASS,
                        memberIds = listOf(firstProp.id, secondProp.id),
                        origin = javaOrigin,
                    ),
                    firstProp,
                    secondProp,
                    type(
                        qualifiedName = "demo.TupleService",
                        memberIds = listOf(operation.id),
                        annotations = listOf(api()),
                        origin = javaOrigin,
                    ),
                    operation,
            ),
        ).toClientSchema(
            EMPTY_ERROR_SCHEMA.clientDependencies(),
        )

        assertEquals(
            listOf("_1", "_2"),
            schema.definitions.single().properties.map(ClientDefinitionProperty::name),
        )
    }

    @Test
    fun `validates primitive nullability annotations without treating validation constraints as type nullability`() {
        val nullable = annotation(LsiSymbolId.type("demo.Nullable"))
        val nonNull = annotation(LsiSymbolId.type("demo.NonNull"))

        val nullablePrimitive = assertFailsWith<ClientValidationException> {
            compileSingleOperation(
                LsiPrimitiveType(LsiPrimitiveKind.INT, annotations = listOf(nullable)),
            )
        }
        assertTrue(nullablePrimitive.message.orEmpty().contains("cannot decorate primitive type"))

        val nonNullBoxed = assertFailsWith<ClientValidationException> {
            compileSingleOperation(
                LsiPrimitiveType(
                    LsiPrimitiveKind.INT,
                    nullability = LsiNullability.NON_NULL,
                    annotations = listOf(nonNull),
                    boxed = true,
                ),
            )
        }
        assertTrue(nonNullBoxed.message.orEmpty().contains("cannot decorate boxed primitive type"))

        val conflict = assertFailsWith<ClientValidationException> {
            compileSingleOperation(
                LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.String"),
                    annotations = listOf(nullable, nonNull),
                ),
            )
        }
        assertTrue(conflict.message.orEmpty().contains("conflicting nullability annotations"))

        val validationNotNull = annotation(
            LsiSymbolId.type("jakarta.validation.constraints.NotNull")
        )
        val schema = compileSingleOperation(
            LsiPrimitiveType(
                LsiPrimitiveKind.INT,
                nullability = LsiNullability.PLATFORM,
                annotations = listOf(validationNotNull),
                boxed = true,
            ),
        )
        assertTrue(assertIs<ClientPrimitiveTypeRef>(schema.services.single().operations.single().returnType).nullable)
    }

    @Test
    fun `validates fetch by nullability source language and non-null conflict`() {
        val nonNull = annotation(LsiSymbolId.type("demo.NonNull"))
        val conflict = assertFailsWith<ClientValidationException> {
            languageWorkspace(
                language = LsiLanguage.JAVA,
                javaGetter = false,
                fetchByNullable = true,
                typeAnnotations = listOf(nonNull),
            ).toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        }
        assertTrue(conflict.message.orEmpty().contains("conflicting nullability annotations"))

        val unknownLanguage = assertFailsWith<ClientValidationException> {
            languageWorkspace(
                language = LsiLanguage.UNKNOWN,
                javaGetter = false,
                fetchByNullable = true,
            ).toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        }
        assertTrue(unknownLanguage.message.orEmpty().contains("known source language"))
    }

    @Test
    fun `json value types are replaced before definitions are collected`() {
        val levelId = LsiSymbolId.type("demo.Level")
        val serviceId = LsiSymbolId.type("demo.LevelService")
        val jsonValue = function(
            ownerId = levelId,
            name = "value",
            returnType = LsiPrimitiveType(LsiPrimitiveKind.INT),
            annotations = listOf(annotation(JSON_VALUE)),
        )
        val operation = function(
            ownerId = serviceId,
            name = "level",
            returnType = LsiDeclaredType(levelId),
            annotations = listOf(api()),
        )
        val schema = LsiWorkspace(
            declarations = listOf(
                    type(
                        qualifiedName = "demo.Level",
                        kind = LsiTypeDeclarationKind.ENUM,
                        memberIds = listOf(jsonValue.id),
                    ),
                    jsonValue,
                    type(
                        qualifiedName = "demo.LevelService",
                        memberIds = listOf(operation.id),
                        annotations = listOf(api()),
                    ),
                    operation,
            ),
        ).toClientSchema(
            EMPTY_ERROR_SCHEMA.clientDependencies(),
        )

        assertIs<ClientPrimitiveTypeRef>(schema.services.single().operations.single().returnType)
        assertTrue(schema.definitions.isEmpty())
    }

    @Test
    fun `explicit mode discovers spring controller mappings`() {
        val serviceId = LsiSymbolId.type("demo.SpringBookController")
        val mapped = function(
            ownerId = serviceId,
            name = "findAll",
            annotations = listOf(annotation(GET_MAPPING)),
        )
        val ordinary = function(ownerId = serviceId, name = "helper")
        val ignored = function(
            ownerId = serviceId,
            name = "hidden",
            annotations = listOf(annotation(GET_MAPPING), annotation(API_IGNORE)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.SpringBookController",
                    memberIds = listOf(ordinary.id, ignored.id, mapped.id),
                    annotations = listOf(annotation(REST_CONTROLLER)),
                ),
                mapped,
                ordinary,
                ignored,
            ),
        )

        assertTrue(
            workspace.toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies()).services.isEmpty()
        )
        val schema = workspace.toClientSchema(
            dependencies = EMPTY_ERROR_SCHEMA.clientDependencies(),
            options = ClientSchemaOptions(explicitApi = true),
        )

        assertEquals(listOf("findAll"), schema.services.single().operations.map(ClientOperation::name))
    }

    @Test
    fun `resolves recursive error metadata with stable sorting and deduplication`() {
        val serviceId = LsiSymbolId.type("demo.ErrorService")
        val baseExceptionId = LsiSymbolId.type("demo.BookException")
        val notFoundExceptionId = LsiSymbolId.type("demo.BookException.NotFound")
        val forbiddenExceptionId = LsiSymbolId.type("demo.BookException.Forbidden")
        val operation = function(
            ownerId = serviceId,
            name = "execute",
            annotations = listOf(api()),
            thrownTypes = listOf(
                LsiDeclaredType(notFoundExceptionId),
                LsiDeclaredType(baseExceptionId),
                LsiDeclaredType(baseExceptionId),
            ),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.ErrorService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id),
                ),
                operation,
            ),
        )
        val schema = workspace.toClientSchema(errorSchema().clientDependencies())
        val compiledOperation = schema.services.single().operations.single()

        assertEquals(
            listOf(notFoundExceptionId, baseExceptionId),
            compiledOperation.declaredExceptionTypeIds,
        )
        assertEquals(
            listOf(notFoundExceptionId, forbiddenExceptionId),
            compiledOperation.exceptionTypeIds,
        )
        assertEquals(
            listOf(baseExceptionId, notFoundExceptionId, forbiddenExceptionId),
            compiledOperation.exceptionMetadata.map(ClientExceptionMetadata::typeId),
        )
        val baseMetadata = compiledOperation.exceptionMetadata.first()
        assertEquals("BOOK", baseMetadata.family)
        assertNull(baseMetadata.code)
        assertTrue(baseMetadata.checked)
        assertEquals(
            listOf(notFoundExceptionId, forbiddenExceptionId),
            baseMetadata.subTypeIds,
        )
        val notFoundMetadata = compiledOperation.exceptionMetadata.single { metadata ->
            metadata.typeId == notFoundExceptionId
        }
        assertEquals("NOT_FOUND", notFoundMetadata.code)
        assertEquals(baseExceptionId, notFoundMetadata.superTypeId)
        assertTrue(schema.normalizedSnapshot().contains("exception|"))
    }

    @Test
    fun `resolves multi-level exception metadata once and rejects cycles`() {
        val familyId = LsiSymbolId.type("demo.ErrorCode")
        val operationId = LsiSymbolId.function(SERVICE_ID, "execute")
        val rootId = LsiSymbolId.type("demo.RootException")
        val branchId = LsiSymbolId.type("demo.BranchException")
        val leafId = LsiSymbolId.type("demo.LeafException")
        val root = exceptionMetadata(rootId, familyId, subTypeIds = listOf(branchId))
        val branch = exceptionMetadata(
            branchId,
            familyId,
            superTypeId = rootId,
            subTypeIds = listOf(leafId),
        )
        val leaf = exceptionMetadata(leafId, familyId, code = "LEAF", superTypeId = branchId)
        val resolution = ClientExceptionMetadataResolver(listOf(leaf, root, branch, leaf))
            .resolve(listOf(leafId, rootId, rootId), operationId)

        assertEquals(listOf(leafId), resolution.typeIds)
        assertEquals(
            listOf(rootId, branchId, leafId),
            resolution.metadata.map(ClientExceptionMetadata::typeId),
        )

        val cyclicRoot = root.copy(superTypeId = branchId)
        val cyclicBranch = branch.copy(subTypeIds = listOf(rootId))
        val exception = assertFailsWith<ClientValidationException> {
            ClientExceptionMetadataResolver(listOf(cyclicRoot, cyclicBranch))
                .resolve(listOf(rootId), operationId)
        }
        assertEquals(operationId, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("cycle"))
    }

    @Test
    fun `operation without exceptions has empty exception semantics`() {
        val serviceId = LsiSymbolId.type("demo.PlainService")
        val operation = function(ownerId = serviceId, name = "execute", annotations = listOf(api()))
        val schema = LsiWorkspace(
            declarations = listOf(
                    type(
                        qualifiedName = "demo.PlainService",
                        annotations = listOf(api()),
                        memberIds = listOf(operation.id),
                    ),
                    operation,
            ),
        ).toClientSchema(
            errorSchema().clientDependencies(),
        )

        val compiledOperation = schema.services.single().operations.single()
        assertTrue(compiledOperation.declaredExceptionTypeIds.isEmpty())
        assertTrue(compiledOperation.exceptionTypeIds.isEmpty())
        assertTrue(compiledOperation.exceptionMetadata.isEmpty())
    }

    @Test
    fun `rejects nested and generic services`() {
        val outer = type(qualifiedName = "demo.Outer")
        val nested = type(
            qualifiedName = "demo.Outer.Service",
            annotations = listOf(api()),
        )
        val nestedException = assertFailsWith<ClientValidationException> {
            LsiWorkspace(declarations = listOf(outer, nested))
                .toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        }
        assertTrue(nestedException.message.orEmpty().contains("top-level"))

        val genericId = LsiSymbolId.type("demo.GenericService")
        val generic = type(
            qualifiedName = "demo.GenericService",
            annotations = listOf(api()),
            typeParameters = listOf(
                LsiTypeParameter(LsiSymbolId.typeParameter(genericId, "T"), "T")
            ),
        )
        val genericException = assertFailsWith<ClientValidationException> {
            LsiWorkspace(declarations = listOf(generic))
                .toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        }
        assertTrue(genericException.message.orEmpty().contains("type parameters"))
    }

    @Test
    fun `rejects non public static generic operations and foreign groups`() {
        assertOperationRejected(
            function(
                ownerId = SERVICE_ID,
                name = "privateCall",
                annotations = listOf(api()),
                visibility = LsiVisibility.PRIVATE,
            ),
            "must be public",
        )
        assertOperationRejected(
            function(
                ownerId = SERVICE_ID,
                name = "staticCall",
                annotations = listOf(api()),
                static = true,
            ),
            "cannot be static",
        )
        assertOperationRejected(
            function(
                ownerId = SERVICE_ID,
                name = "genericCall",
                annotations = listOf(api()),
                generic = true,
            ),
            "type parameters",
        )
        assertOperationRejected(
            function(
                ownerId = SERVICE_ID,
                name = "foreignGroup",
                annotations = listOf(api("internal")),
            ),
            "outside service",
            serviceGroups = listOf("public"),
        )
    }

    @Test
    fun `java getter and kotlin function produce equivalent snapshots`() {
        val javaSchema = languageWorkspace(LsiLanguage.JAVA, javaGetter = true)
            .toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        val kotlinSchema = languageWorkspace(LsiLanguage.KOTLIN, javaGetter = false)
            .toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())

        assertEquals(javaSchema.normalizedSnapshot(), kotlinSchema.normalizedSnapshot())
        assertEquals(javaSchema.fingerprint(), kotlinSchema.fingerprint())
        assertEquals(64, javaSchema.fingerprint().length)
    }

    private fun assertOperationRejected(
        operation: LsiFunction,
        messagePart: String,
        serviceGroups: List<String> = emptyList(),
    ) {
        val service = type(
            qualifiedName = "demo.Service",
            annotations = listOf(api(*serviceGroups.toTypedArray())),
            memberIds = listOf(operation.id),
        )
        val exception = assertFailsWith<ClientValidationException> {
            LsiWorkspace(declarations = listOf(service, operation))
                .toClientSchema(EMPTY_ERROR_SCHEMA.clientDependencies())
        }
        assertTrue(exception.message.orEmpty().contains(messagePart))
    }

    private fun languageWorkspace(
        language: LsiLanguage,
        javaGetter: Boolean,
        fetchByNullable: Boolean = false,
        typeAnnotations: List<LsiAnnotation> = emptyList(),
    ): LsiWorkspace {
        val origin = sourceOrigin(language)
        val bookId = LsiSymbolId.type("demo.Book")
        val serviceId = LsiSymbolId.type("demo.LanguageService")
        val annotations = listOf(api(), fetchBy("BOOK_FETCHER", nullable = fetchByNullable))
        val bookFetcher = fetcher(
            ownerId = serviceId,
            name = "BOOK_FETCHER",
            entityTypeId = bookId,
            origin = origin,
        )
        val operation = if (javaGetter) {
            property(
                ownerId = serviceId,
                name = "findBook",
                getterName = "findBook",
                type = LsiDeclaredType(
                    bookId,
                    nullability = LsiNullability.PLATFORM,
                    annotations = typeAnnotations,
                ),
                annotations = annotations,
                documentation = "查找图书。",
                origin = origin,
            )
        } else {
            function(
                ownerId = serviceId,
                name = "findBook",
                returnType = LsiDeclaredType(
                    bookId,
                    nullability = LsiNullability.NON_NULL,
                    annotations = typeAnnotations,
                ),
                annotations = annotations,
                documentation = "查找图书。",
                origin = origin,
            )
        }
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = listOf(
                type(
                    qualifiedName = "demo.Book",
                    annotations = listOf(annotation(ENTITY)),
                    origin = origin,
                ),
                type(
                    qualifiedName = "demo.LanguageService",
                    annotations = listOf(api()),
                    memberIds = listOf(operation.id, bookFetcher.id),
                    documentation = "语言无关服务。",
                    origin = origin,
                ),
                operation,
                bookFetcher,
            ),
        )
    }

    private fun type(
        qualifiedName: String,
        annotations: List<LsiAnnotation> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
        kind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.INTERFACE,
        enclosingTypeId: LsiSymbolId? = null,
        enumEntries: List<LsiEnumEntry> = emptyList(),
        documentation: String? = null,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            enclosingTypeId = enclosingTypeId,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            enumEntries = enumEntries,
            documentation = documentation,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun compileSingleOperation(returnType: LsiTypeRef): ClientSchema {
        val serviceId = LsiSymbolId.type("demo.NullabilityService")
        val operation = function(
            ownerId = serviceId,
            name = "value",
            returnType = returnType,
            annotations = listOf(api()),
        )
        return LsiWorkspace(
            declarations = listOf(
                    type(
                        qualifiedName = "demo.NullabilityService",
                        annotations = listOf(api()),
                        memberIds = listOf(operation.id),
                    ),
                    operation,
            ),
        ).toClientSchema(
            EMPTY_ERROR_SCHEMA.clientDependencies(),
        )
    }

    private fun function(
        ownerId: LsiSymbolId,
        name: String,
        parameters: List<ParameterSpec> = emptyList(),
        returnType: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
        annotations: List<LsiAnnotation> = emptyList(),
        thrownTypes: List<LsiTypeRef> = emptyList(),
        documentation: String? = null,
        visibility: LsiVisibility = LsiVisibility.PUBLIC,
        static: Boolean = false,
        generic: Boolean = false,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiFunction {
        val functionId = LsiSymbolId.function(
            owner = ownerId,
            name = name,
            parameterTypeSignatures = parameters.map { parameter -> parameter.type.stableSignature() },
        )
        val lsiParameters = parameters.mapIndexed { index, parameter ->
            LsiParameter(
                id = LsiSymbolId.parameter(functionId, index, parameter.name),
                name = parameter.name,
                callableId = functionId,
                index = index,
                type = parameter.type,
                annotations = parameter.annotations,
                origin = origin,
            )
        }
        return LsiFunction(
            id = functionId,
            name = name,
            ownerId = ownerId,
            returnType = returnType,
            parameters = lsiParameters,
            typeParameters = if (generic) {
                listOf(LsiTypeParameter(LsiSymbolId.typeParameter(functionId, "T"), "T"))
            } else {
                emptyList()
            },
            thrownTypes = thrownTypes,
            static = static,
            visibility = visibility,
            documentation = documentation,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        getterName: String = name,
        type: LsiTypeRef = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
        annotations: List<LsiAnnotation> = emptyList(),
        documentation: String? = null,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiProperty {
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            getterName = getterName,
            type = type,
            documentation = documentation,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun fetcher(
        ownerId: LsiSymbolId,
        name: String,
        entityTypeId: LsiSymbolId,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiField {
        return LsiField(
            id = LsiSymbolId.field(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = LsiDeclaredType(
                declarationId = FETCHER,
                arguments = listOf(
                    LsiTypeArgument.invariant(LsiDeclaredType(entityTypeId)),
                ),
            ),
            static = true,
            origin = origin,
        )
    }

    private fun api(vararg groups: String): LsiAnnotation {
        return annotation(
            type = API,
            arguments = mapOf(
                "value" to LsiAnnotationValue.ArrayValue(
                    groups.map(LsiAnnotationValue::StringValue)
                )
            ),
        )
    }

    private fun fetchBy(
        value: String,
        nullable: Boolean = false,
    ): LsiAnnotation {
        return annotation(
            type = FETCH_BY,
            arguments = mapOf(
                "value" to LsiAnnotationValue.StringValue(value),
                "nullable" to LsiAnnotationValue.BooleanValue(nullable),
            ),
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
    }

    private fun annotation(
        type: LsiSymbolId,
        arguments: Map<String, LsiAnnotationValue> = emptyMap(),
        useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = arguments.mapValues { (_, value) ->
                LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
            useSiteTarget = useSiteTarget,
        )
    }

    private fun sourceOrigin(language: LsiLanguage): LsiOrigin {
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("src/main/$extension/demo/LanguageService.$extension", language),
        )
    }

    private data class ParameterSpec(
        val name: String,
        val type: LsiTypeRef,
        val annotations: List<LsiAnnotation> = emptyList(),
    )

    companion object {
        private val EMPTY_ERROR_SCHEMA = ErrorSchema(emptyList())
        private val SERVICE_ID = LsiSymbolId.type("demo.Service")
        private val API = LsiSymbolId.type("org.babyfish.jimmer.client.meta.Api")
        private val API_IGNORE = LsiSymbolId.type("org.babyfish.jimmer.client.ApiIgnore")
        private val DEFAULT_FETCHER_OWNER =
            LsiSymbolId.type("org.babyfish.jimmer.client.meta.DefaultFetcherOwner")
        private val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        private val IMMUTABLE = LsiSymbolId.type("org.babyfish.jimmer.Immutable")
        private val GENERATED_POLYMORPHIC_BRANCH =
            LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch")
        private val FETCH_BY = LsiSymbolId.type("org.babyfish.jimmer.client.FetchBy")
        private val FETCHER = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.Fetcher")
        private val JSON_VALUE = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonValue")
        private val JSON_CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.JsonConverter")
        private val CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.Converter")
        private val GET_MAPPING =
            LsiSymbolId.type("org.springframework.web.bind.annotation.GetMapping")
        private val REST_CONTROLLER =
            LsiSymbolId.type("org.springframework.web.bind.annotation.RestController")
        private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}

private fun ErrorSchema.clientDependencies(): ClientSchemaDependencies {
    return ClientSchemaDependencies(
        immutableSchema = ImmutableSchema(emptyList()),
        errorSchema = this,
        definitionDocumentationByTypeId = emptyMap(),
    )
}

private fun errorSchema(): ErrorSchema {
    val familyId = LsiSymbolId.type("demo.BookErrorCode")
    return ErrorSchema(
        families = listOf(
            ErrorFamily(
                id = familyId,
                qualifiedName = "demo.BookErrorCode",
                packageName = "demo",
                family = "BOOK",
                exceptionTypeId = LsiSymbolId.type("demo.BookException"),
                exceptionSimpleName = "BookException",
                checkedException = true,
                documentation = "Book errors.",
                declaredFields = emptyList(),
                codes = listOf(
                    errorCode(familyId, "NOT_FOUND", "NotFound"),
                    errorCode(familyId, "FORBIDDEN", "Forbidden"),
                ),
            )
        ),
    )
}

private fun errorCode(
    familyId: LsiSymbolId,
    code: String,
    exceptionSimpleName: String,
): ErrorCode {
    return ErrorCode(
        id = LsiSymbolId("${familyId.value}#$code"),
        enumEntryName = code,
        code = code,
        creatorName = exceptionSimpleName.replaceFirstChar(Char::lowercaseChar),
        exceptionTypeId = LsiSymbolId.type("demo.BookException.$exceptionSimpleName"),
        exceptionSimpleName = exceptionSimpleName,
        documentation = "$code error.",
        declaredFields = emptyList(),
    )
}

private fun exceptionMetadata(
    typeId: LsiSymbolId,
    familyId: LsiSymbolId,
    code: String? = null,
    superTypeId: LsiSymbolId? = null,
    subTypeIds: List<LsiSymbolId> = emptyList(),
): ClientExceptionMetadata {
    return ClientExceptionMetadata(
        typeId = typeId,
        errorFamilyId = familyId,
        family = "DEMO",
        code = code,
        checked = false,
        abstract = code == null,
        superTypeId = superTypeId,
        subTypeIds = subTypeIds,
        documentation = null,
    )
}
