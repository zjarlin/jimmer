package org.babyfish.jimmer.compiler.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.error.ErrorCodeModel
import org.babyfish.jimmer.compiler.error.ErrorFamilyModel
import org.babyfish.jimmer.compiler.error.ErrorPrecompiledSchema
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
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature

class ClientPrecompilerTest {

    @Test
    fun `precompiles service operation parameters exceptions and fetch by`() {
        val bookId = LsiSymbolId.type("demo.Book")
        val exceptionId = LsiSymbolId.type("demo.BookException")
        val serviceId = LsiSymbolId.type("demo.BookService")
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
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Book",
                    annotations = listOf(annotation(ENTITY)),
                ),
                type(qualifiedName = "demo.BookException"),
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

        val schema = ClientPrecompiler().compile(workspace, EMPTY_ERROR_SCHEMA)

        val service = schema.services.single()
        assertEquals(serviceId, service.id)
        assertEquals(listOf("admin", "public"), service.groups)
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
        assertTrue(fetchBy.nullable)
        assertEquals(64, schema.fingerprint().length)
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

        assertTrue(ClientPrecompiler().compile(workspace, EMPTY_ERROR_SCHEMA).services.isEmpty())
        val schema = ClientPrecompiler(ClientPrecompileOptions(explicitApi = true))
            .compile(workspace, EMPTY_ERROR_SCHEMA)

        assertEquals(listOf("findAll"), schema.services.single().operations.map(ClientOperation::name))
    }

    @Test
    fun `precompiles recursive error metadata with stable sorting and deduplication`() {
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
        val schema = ClientPrecompiler().compile(workspace, errorSchema())
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
        val resolution = ClientExceptionMetadataPrecompiler(listOf(leaf, root, branch, leaf))
            .resolve(listOf(leafId, rootId, rootId), operationId)

        assertEquals(listOf(leafId), resolution.typeIds)
        assertEquals(
            listOf(rootId, branchId, leafId),
            resolution.metadata.map(ClientExceptionMetadata::typeId),
        )

        val cyclicRoot = root.copy(superTypeId = branchId)
        val cyclicBranch = branch.copy(subTypeIds = listOf(rootId))
        val exception = assertFailsWith<ClientPrecompileException> {
            ClientExceptionMetadataPrecompiler(listOf(cyclicRoot, cyclicBranch))
                .resolve(listOf(rootId), operationId)
        }
        assertEquals(operationId, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("cycle"))
    }

    @Test
    fun `operation without exceptions has empty exception semantics`() {
        val serviceId = LsiSymbolId.type("demo.PlainService")
        val operation = function(ownerId = serviceId, name = "execute", annotations = listOf(api()))
        val schema = ClientPrecompiler().compile(
            LsiWorkspace(
                declarations = listOf(
                    type(
                        qualifiedName = "demo.PlainService",
                        annotations = listOf(api()),
                        memberIds = listOf(operation.id),
                    ),
                    operation,
                ),
            ),
            errorSchema(),
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
        val nestedException = assertFailsWith<ClientPrecompileException> {
            ClientPrecompiler().compile(
                LsiWorkspace(declarations = listOf(outer, nested)),
                EMPTY_ERROR_SCHEMA,
            )
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
        val genericException = assertFailsWith<ClientPrecompileException> {
            ClientPrecompiler().compile(
                LsiWorkspace(declarations = listOf(generic)),
                EMPTY_ERROR_SCHEMA,
            )
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
    fun `exports type and property docs with nested exclusion`() {
        val outerId = LsiSymbolId.type("demo.Models")
        val outerProperty = property(
            ownerId = outerId,
            name = "name",
            documentation = "名称。",
        )
        val outer = type(
            qualifiedName = "demo.Models",
            annotations = listOf(annotation(EXPORT_DOC)),
            memberIds = listOf(outerProperty.id),
            documentation = "模型。",
        )
        val nested = type(
            qualifiedName = "demo.Models.Detail",
            documentation = "详情。",
        )
        val excluded = type(
            qualifiedName = "demo.Models.Secret",
            annotations = listOf(
                annotation(
                    EXPORT_DOC,
                    mapOf("excluded" to LsiAnnotationValue.BooleanValue(true)),
                )
            ),
            documentation = "机密。",
        )

        val schema = ClientPrecompiler().compile(
            LsiWorkspace(declarations = listOf(excluded, outerProperty, nested, outer)),
            EMPTY_ERROR_SCHEMA,
        )

        assertEquals(
            listOf("demo.Models", "demo.Models.Detail", "demo.Models.name"),
            schema.exportedDocs.map(ClientExportedDoc::key),
        )
        assertFalse(schema.exportedDocs.any { doc -> doc.key == "demo.Models.Secret" })
    }

    @Test
    fun `java getter and kotlin function produce equivalent snapshots`() {
        val javaSchema = ClientPrecompiler().compile(
            languageWorkspace(LsiLanguage.JAVA, javaGetter = true),
            EMPTY_ERROR_SCHEMA,
        )
        val kotlinSchema = ClientPrecompiler().compile(
            languageWorkspace(LsiLanguage.KOTLIN, javaGetter = false),
            EMPTY_ERROR_SCHEMA,
        )

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
        val exception = assertFailsWith<ClientPrecompileException> {
            ClientPrecompiler().compile(
                LsiWorkspace(declarations = listOf(service, operation)),
                EMPTY_ERROR_SCHEMA,
            )
        }
        assertTrue(exception.message.orEmpty().contains(messagePart))
    }

    private fun languageWorkspace(
        language: LsiLanguage,
        javaGetter: Boolean,
    ): LsiWorkspace {
        val origin = sourceOrigin(language)
        val bookId = LsiSymbolId.type("demo.Book")
        val serviceId = LsiSymbolId.type("demo.LanguageService")
        val annotations = listOf(api(), fetchBy("BOOK_FETCHER"))
        val operation = if (javaGetter) {
            property(
                ownerId = serviceId,
                name = "findBook",
                getterName = "findBook",
                type = LsiDeclaredType(bookId, nullability = LsiNullability.PLATFORM),
                annotations = annotations,
                documentation = "查找图书。",
                origin = origin,
            )
        } else {
            function(
                ownerId = serviceId,
                name = "findBook",
                returnType = LsiDeclaredType(bookId, nullability = LsiNullability.NON_NULL),
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
                    memberIds = listOf(operation.id),
                    documentation = "语言无关服务。",
                    origin = origin,
                ),
                operation,
            ),
        )
    }

    private fun type(
        qualifiedName: String,
        annotations: List<LsiAnnotation> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        typeParameters: List<LsiTypeParameter> = emptyList(),
        documentation: String? = null,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = typeParameters,
            memberIds = memberIds,
            documentation = documentation,
            annotations = annotations,
            origin = origin,
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
        private val EMPTY_ERROR_SCHEMA = ErrorPrecompiledSchema(emptyList())
        private val SERVICE_ID = LsiSymbolId.type("demo.Service")
        private val API = LsiSymbolId.type("org.babyfish.jimmer.client.meta.Api")
        private val API_IGNORE = LsiSymbolId.type("org.babyfish.jimmer.client.ApiIgnore")
        private val DEFAULT_FETCHER_OWNER =
            LsiSymbolId.type("org.babyfish.jimmer.client.meta.DefaultFetcherOwner")
        private val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        private val EXPORT_DOC = LsiSymbolId.type("org.babyfish.jimmer.client.ExportDoc")
        private val FETCH_BY = LsiSymbolId.type("org.babyfish.jimmer.client.FetchBy")
        private val GET_MAPPING =
            LsiSymbolId.type("org.springframework.web.bind.annotation.GetMapping")
        private val REST_CONTROLLER =
            LsiSymbolId.type("org.springframework.web.bind.annotation.RestController")
        private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}

private fun errorSchema(): ErrorPrecompiledSchema {
    val familyId = LsiSymbolId.type("demo.BookErrorCode")
    return ErrorPrecompiledSchema(
        families = listOf(
            ErrorFamilyModel(
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
): ErrorCodeModel {
    return ErrorCodeModel(
        id = LsiSymbolId("${familyId.value}#$code"),
        enumEntryName = code,
        code = code,
        creatorName = exceptionSimpleName.replaceFirstChar(Char::lowercaseChar),
        exceptionTypeId = LsiSymbolId.type("demo.BookException.$exceptionSimpleName"),
        exceptionSimpleName = exceptionSimpleName,
        documentation = "$code error.",
        declaredFields = emptyList(),
        fields = emptyList(),
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
