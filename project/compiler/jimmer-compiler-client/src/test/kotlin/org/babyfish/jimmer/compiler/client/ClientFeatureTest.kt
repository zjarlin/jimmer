package org.babyfish.jimmer.compiler.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerResolutionStatus
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerSession
import site.addzero.lsi.compiler.CompilerFeatureLoader
import org.babyfish.jimmer.compiler.error.ErrorFeature
import org.babyfish.jimmer.compiler.error.ErrorFeatureStatus
import org.babyfish.jimmer.compiler.dto.DtoFeature
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.client.ClientExceptionMetadata
import site.addzero.lsi.jimmer.client.ClientService
import site.addzero.lsi.jimmer.client.fingerprint
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

class ClientFeatureTest {

    @Test
    fun `registered client feature declares dto immutable and error dependencies`() {
        val feature = CompilerFeatureLoader.load()
            .single { candidate -> candidate.key == ClientFeature.Key }

        assertEquals(
            setOf(DtoFeature.Key, ErrorFeature.Key, ImmutableFeature.Key),
            feature.dependencies,
        )
    }

    @Test
    fun `current dependency closure does not pollute processed client roots`() {
        val first = implicitApiWorkspace(
            serviceName = "demo.FirstController",
            operationName = "first",
            includeMarker = true,
            source = FIRST_SOURCE,
        )
        val second = implicitApiWorkspace(
            serviceName = "demo.SecondController",
            operationName = "second",
            includeMarker = false,
            source = SECOND_SOURCE,
        )
        val session = session("client-multi-round")

        val firstResult = session.execute(
            round(number = 0, workspace = first, currentWorkspace = first)
        ).clientResult()
        val firstState = firstResult.state
        assertEquals(ClientFeatureStatus.RESOLVED, firstState.status)
        assertTrue(firstState.explicitApi)
        assertEquals(setOf(LsiSymbolId.type("demo.FirstController")), firstState.currentServiceTypeIds)
        assertTrue(firstResult.artifacts.isEmpty())

        val cumulative = first.merge(second)
        val secondResult = session.execute(
            round(
                number = 1,
                workspace = cumulative,
                currentWorkspace = cumulative,
                currentRootTypeIds = setOf(LsiSymbolId.type("demo.SecondController")),
            )
        ).clientResult()
        val secondState = secondResult.state

        assertEquals(ClientFeatureStatus.RESOLVED, secondState.status)
        assertEquals(
            setOf(
                LsiSymbolId.type("demo.FirstController"),
                LsiSymbolId.type("demo.SecondController"),
            ),
            secondState.targetServiceTypeIds,
        )
        assertEquals(setOf(LsiSymbolId.type("demo.SecondController")), secondState.currentServiceTypeIds)
        assertEquals(listOf("FirstController", "SecondController"), secondState.schema.services.map {
            service -> service.qualifiedName.substringAfterLast('.')
        })
        assertEquals(secondState.currentServiceTypeIds, secondResult.processedSymbols)
        assertTrue(secondResult.artifacts.isEmpty())
    }

    @Test
    fun `apt defers unresolved roots while preserving resolved services`() {
        val valid = apiWorkspace("demo.ValidService", "valid", STRING_TYPE, FIRST_SOURCE)
        val broken = apiWorkspace(
            serviceName = "demo.BrokenService",
            operationName = "broken",
            returnType = LsiUnresolvedType("demo.GeneratedPayload"),
            source = SECOND_SOURCE,
        )
        val workspace = valid.merge(broken)
        val session = session("client-apt-unresolved")

        val deferredResult = session.execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.APT,
            )
        ).clientResult()
        val deferredState = deferredResult.state

        assertEquals(ClientFeatureStatus.DEFERRED, deferredState.status)
        assertEquals(setOf(LsiSymbolId.type("demo.BrokenService")), deferredState.unresolvedRootTypeIds)
        assertEquals(listOf("demo.ValidService"), deferredState.schema.services.map(ClientService::qualifiedName))
        assertEquals(deferredState.unresolvedRootTypeIds, deferredResult.unresolvedSymbols)
        assertTrue(deferredResult.diagnostics.isEmpty())

        val finalResult = session.execute(
            round(
                number = 1,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.APT,
                isFinal = true,
            )
        ).clientResult()
        val finalState = finalResult.state

        assertEquals(ClientFeatureStatus.INVALID, finalState.status)
        assertTrue(finalResult.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.client.unresolved", finalResult.diagnostics.single().code)
        assertEquals(listOf("demo.ValidService"), finalState.schema.services.map(ClientService::qualifiedName))
    }

    @Test
    fun `ksp reports valid unresolved service instead of forcing another round`() {
        val workspace = apiWorkspace(
            serviceName = "demo.BrokenService",
            operationName = "broken",
            returnType = LsiUnresolvedType("demo.GeneratedPayload"),
            source = KOTLIN_SOURCE,
        )

        val result = session("client-ksp-unresolved").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
            )
        ).clientResult()
        val state = result.state

        assertEquals(ClientFeatureStatus.INVALID, state.status)
        assertTrue(result.unresolvedSymbols.isEmpty())
        assertEquals("jimmer.client.unresolved", result.diagnostics.single().code)
    }

    @Test
    fun `invalid root does not erase the valid part of client schema`() {
        val valid = apiWorkspace("demo.ValidService", "valid", STRING_TYPE, FIRST_SOURCE)
        val nestedOperation = operation(
            ownerId = NESTED_SERVICE_ID,
            name = "invalid",
            returnType = STRING_TYPE,
            annotations = listOf(annotation(API)),
            origin = SECOND_ORIGIN,
        )
        val invalid = LsiWorkspace(
            sources = listOf(SECOND_SOURCE),
            declarations = listOf(
                type("demo.Outer", origin = SECOND_ORIGIN),
                type(
                    qualifiedName = "demo.Outer.NestedService",
                    memberIds = listOf(nestedOperation.id),
                    annotations = listOf(annotation(API)),
                    origin = SECOND_ORIGIN,
                ),
                nestedOperation,
            ),
        )

        val result = session("client-invalid-root").execute(
            round(number = 0, workspace = valid.merge(invalid), currentWorkspace = valid.merge(invalid))
        ).clientResult()
        val state = result.state

        assertEquals(ClientFeatureStatus.INVALID, state.status)
        assertEquals(setOf(NESTED_SERVICE_ID), state.invalidRootTypeIds)
        assertEquals(listOf("demo.ValidService"), state.schema.services.map(ClientService::qualifiedName))
        assertEquals("jimmer.client.invalid", result.diagnostics.single().code)
    }

    @Test
    fun `consumes error schema into recursive operation exception metadata`() {
        val client = apiWorkspace(
            serviceName = "demo.ErrorService",
            operationName = "execute",
            returnType = STRING_TYPE,
            source = FIRST_SOURCE,
            thrownTypes = listOf(
                LsiDeclaredType(BOOK_EXCEPTION_ID),
                LsiDeclaredType(BOOK_NOT_FOUND_EXCEPTION_ID),
                LsiDeclaredType(BOOK_EXCEPTION_ID),
            ),
        )
        val oneCodeWorkspace = client.merge(errorFamilyWorkspace("NOT_FOUND"))
        val oneCodeState = session("client-error-schema-one-code").execute(
            round(number = 0, workspace = oneCodeWorkspace, currentWorkspace = oneCodeWorkspace)
        ).clientResult().state
        val operation = oneCodeState.schema.services.single().operations.single()

        assertEquals(ClientFeatureStatus.RESOLVED, oneCodeState.status)
        assertEquals(
            listOf(BOOK_EXCEPTION_ID, BOOK_NOT_FOUND_EXCEPTION_ID),
            operation.declaredExceptionTypeIds,
        )
        assertEquals(listOf(BOOK_NOT_FOUND_EXCEPTION_ID), operation.exceptionTypeIds)
        assertEquals(
            listOf(BOOK_EXCEPTION_ID, BOOK_NOT_FOUND_EXCEPTION_ID),
            operation.exceptionMetadata.map(ClientExceptionMetadata::typeId),
        )
        assertEquals("BOOK", operation.exceptionMetadata.first().family)
        assertEquals("NOT_FOUND", operation.exceptionMetadata.last().code)

        val twoCodeWorkspace = client.merge(errorFamilyWorkspace("NOT_FOUND", "FORBIDDEN"))
        val twoCodeState = session("client-error-schema-two-codes").execute(
            round(number = 0, workspace = twoCodeWorkspace, currentWorkspace = twoCodeWorkspace)
        ).clientResult().state

        assertEquals(
            listOf(BOOK_NOT_FOUND_EXCEPTION_ID, BOOK_FORBIDDEN_EXCEPTION_ID),
            twoCodeState.schema.services.single().operations.single().exceptionTypeIds,
        )
        assertFalse(oneCodeState.schema.fingerprint() == twoCodeState.schema.fingerprint())
        assertFalse(oneCodeState.fingerprint == twoCodeState.fingerprint)
    }

    @Test
    fun `propagates deferred and invalid dependency states into client fingerprint`() {
        val client = apiWorkspace(
            serviceName = "demo.Service",
            operationName = "execute",
            returnType = STRING_TYPE,
            source = FIRST_SOURCE,
            thrownTypes = listOf(LsiDeclaredType(BOOK_EXCEPTION_ID)),
        )
        val deferredImmutable = unresolvedImmutableWorkspace()
            .merge(errorFamilyWorkspace("NOT_FOUND"))
            .merge(client)
        val deferredResult = session("client-dependency-deferred").execute(
            round(
                number = 0,
                workspace = deferredImmutable,
                currentWorkspace = deferredImmutable,
                platform = CompilerPlatform.APT,
            )
        ).clientResult()
        val deferredState = deferredResult.state

        assertEquals(ClientFeatureStatus.DEPENDENCY_DEFERRED, deferredState.status)
        assertEquals(CompilerResolutionStatus.DEFERRED, deferredState.dependencyStatus)
        assertFalse(deferredState.renderable)
        assertTrue(deferredState.immutableDependencyFingerprint.isNotBlank())
        assertTrue(deferredState.fingerprint.contains(deferredState.immutableDependencyFingerprint))
        assertTrue(deferredState.fingerprint.contains(deferredState.errorDependencyFingerprint))
        assertTrue(deferredState.fingerprint.contains(deferredState.dtoDependencyFingerprint))
        assertEquals(
            listOf(BOOK_NOT_FOUND_EXCEPTION_ID),
            deferredState.schema.services.single().operations.single().exceptionTypeIds,
        )

        val invalidError = invalidErrorWorkspace().merge(client)
        val invalidRoundResult = session("client-dependency-invalid").execute(
            round(number = 0, workspace = invalidError, currentWorkspace = invalidError)
        )
        val errorResult = invalidRoundResult.featureResults.getValue(ErrorFeature.Key)
        val errorState = errorResult.state
        assertEquals(ErrorFeatureStatus.INVALID, errorState.status)
        assertTrue(errorResult.artifacts.isEmpty())

        val invalidResult = invalidRoundResult.clientResult()
        val invalidState = invalidResult.state

        assertEquals(ClientFeatureStatus.DEPENDENCY_INVALID, invalidState.status)
        assertEquals(CompilerResolutionStatus.INVALID, invalidState.dependencyStatus)
        assertFalse(invalidState.renderable)
        assertTrue(invalidState.errorDependencyFingerprint.startsWith("INVALID:"))
        assertTrue(invalidState.fingerprint.contains(invalidState.immutableDependencyFingerprint))
        assertTrue(invalidState.fingerprint.contains(invalidState.errorDependencyFingerprint))
        assertTrue(invalidState.fingerprint.contains(invalidState.dtoDependencyFingerprint))
        assertTrue(invalidState.schema.services.single().operations.single().exceptionTypeIds.isEmpty())
        assertTrue(invalidState.schema.services.single().operations.single().exceptionMetadata.isEmpty())
    }

    @Test
    fun `ignore resource generation produces explicit disabled state`() {
        val workspace = apiWorkspace("demo.Service", "execute", STRING_TYPE, FIRST_SOURCE)

        val result = session("client-disabled").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                options = mapOf("jimmer.buddy.ignoreResourceGeneration" to "true"),
            )
        ).clientResult()
        val state = result.state

        assertEquals(ClientFeatureStatus.DISABLED, state.status)
        assertTrue(state.targetServiceTypeIds.isEmpty())
        assertTrue(state.schema.services.isEmpty())
        assertTrue(result.processedSymbols.isEmpty())
    }

    @Test
    fun `source filter applies to cumulative and current client roots`() {
        val included = apiWorkspace("demo.api.Service", "included", STRING_TYPE, FIRST_SOURCE)
        val excluded = apiWorkspace("demo.internal.Service", "excluded", STRING_TYPE, SECOND_SOURCE)
        val workspace = included.merge(excluded)

        val result = session("client-source-filter").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                options = mapOf("jimmer.source.includes" to "demo.api"),
            )
        ).clientResult()
        val state = result.state

        assertEquals(setOf(LsiSymbolId.type("demo.api.Service")), state.targetServiceTypeIds)
        assertEquals(state.targetServiceTypeIds, state.currentServiceTypeIds)
        assertEquals(listOf("demo.api.Service"), state.schema.services.map(ClientService::qualifiedName))
        assertEquals(state.targetServiceTypeIds, result.processedSymbols)
    }

    @Test
    fun `ksp excludes java services from mixed source aggregation`() {
        val javaService = apiWorkspace("demo.JavaService", "javaCall", STRING_TYPE, FIRST_SOURCE)
        val kotlinService = apiWorkspace("demo.KotlinService", "kotlinCall", STRING_TYPE, KOTLIN_SERVICE_SOURCE)
        val workspace = javaService.merge(kotlinService)

        val result = session("client-ksp-mixed-source").execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                platform = CompilerPlatform.KSP,
            )
        ).clientResult()
        val state = result.state

        assertEquals(setOf(LsiSymbolId.type("demo.KotlinService")), state.targetServiceTypeIds)
        assertEquals(listOf("demo.KotlinService"), state.schema.services.map(ClientService::qualifiedName))
        assertEquals(state.targetServiceTypeIds, result.processedSymbols)
    }

    private fun session(id: String): CompilerSession {
        return CompilerSession(
            id = id,
            features = listOf(
                ErrorFeature(),
                ImmutableFeature(),
                DtoFeature(),
                ClientFeature(),
            ),
        )
    }

    private fun site.addzero.lsi.compiler.CompilerRoundResult.clientResult() =
        featureResults.getValue(ClientFeature.Key)

    private fun round(
        number: Int,
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace,
        currentRootTypeIds: Set<LsiSymbolId> = currentWorkspace.declarations
            .filterIsInstance<LsiTypeDeclaration>()
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
        platform: CompilerPlatform = CompilerPlatform.APT,
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
            inputDocumentSnapshots = emptyList(),
        )
    }

    private fun implicitApiWorkspace(
        serviceName: String,
        operationName: String,
        includeMarker: Boolean,
        source: LsiSource,
    ): LsiWorkspace {
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val serviceId = LsiSymbolId.type(serviceName)
        val function = operation(
            ownerId = serviceId,
            name = operationName,
            returnType = STRING_TYPE,
            annotations = listOf(annotation(GET_MAPPING)),
            origin = origin,
        )
        val declarations = mutableListOf(
            type(
                qualifiedName = serviceName,
                memberIds = listOf(function.id),
                annotations = listOf(annotation(REST_CONTROLLER)),
                origin = origin,
            ),
            function,
        )
        if (includeMarker) {
            declarations += type(
                qualifiedName = "demo.Application",
                annotations = listOf(annotation(ENABLE_IMPLICIT_API)),
                origin = origin,
            )
        }
        return LsiWorkspace(sources = listOf(source), declarations = declarations)
    }

    private fun apiWorkspace(
        serviceName: String,
        operationName: String,
        returnType: LsiTypeRef,
        source: LsiSource,
        thrownTypes: List<LsiTypeRef> = emptyList(),
    ): LsiWorkspace {
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val serviceId = LsiSymbolId.type(serviceName)
        val function = operation(
            ownerId = serviceId,
            name = operationName,
            returnType = returnType,
            annotations = listOf(annotation(API)),
            origin = origin,
            thrownTypes = thrownTypes,
        )
        return LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                type(
                    qualifiedName = serviceName,
                    memberIds = listOf(function.id),
                    annotations = listOf(annotation(API)),
                    origin = origin,
                ),
                function,
            ),
        )
    }

    private fun unresolvedImmutableWorkspace(): LsiWorkspace {
        val typeId = LsiSymbolId.type("demo.BrokenEntity")
        val propertyId = LsiSymbolId.property(typeId, "value")
        return LsiWorkspace(
            sources = listOf(SECOND_SOURCE),
            declarations = listOf(
                type(
                    qualifiedName = "demo.BrokenEntity",
                    memberIds = listOf(propertyId),
                    annotations = listOf(annotation(ENTITY)),
                    origin = SECOND_ORIGIN,
                ),
                LsiProperty(
                    id = propertyId,
                    name = "value",
                    ownerId = typeId,
                    type = LsiUnresolvedType("demo.GeneratedValue"),
                    origin = SECOND_ORIGIN,
                ),
            ),
        )
    }

    private fun invalidErrorWorkspace(): LsiWorkspace {
        return LsiWorkspace(
            sources = listOf(SECOND_SOURCE),
            declarations = listOf(
                type(
                    qualifiedName = "demo.InvalidErrorFamily",
                    annotations = listOf(annotation(ERROR_FAMILY)),
                    origin = SECOND_ORIGIN,
                )
            ),
        )
    }

    private fun errorFamilyWorkspace(
        vararg codeNames: String,
    ): LsiWorkspace {
        val familyId = LsiSymbolId.type("demo.BookErrorCode")
        val entries = codeNames.map { codeName ->
            LsiEnumEntry(
                id = LsiSymbolId("${familyId.value}#$codeName"),
                name = codeName,
                ownerId = familyId,
                origin = SECOND_ORIGIN,
            )
        }
        val family = LsiTypeDeclaration(
            id = familyId,
            name = "BookErrorCode",
            qualifiedName = "demo.BookErrorCode",
            kind = LsiTypeDeclarationKind.ENUM,
            enumEntries = entries,
            annotations = listOf(annotation(ERROR_FAMILY)),
            origin = SECOND_ORIGIN,
        )
        return LsiWorkspace(
            sources = listOf(SECOND_SOURCE),
            declarations = listOf(family),
        )
    }

    private fun type(
        qualifiedName: String,
        memberIds: List<LsiSymbolId> = emptyList(),
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            memberIds = memberIds,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun operation(
        ownerId: LsiSymbolId,
        name: String,
        returnType: LsiTypeRef,
        annotations: List<LsiAnnotation>,
        origin: LsiOrigin,
        thrownTypes: List<LsiTypeRef> = emptyList(),
    ): LsiFunction {
        return LsiFunction(
            id = LsiSymbolId.function(ownerId, name),
            name = name,
            ownerId = ownerId,
            returnType = returnType,
            thrownTypes = thrownTypes,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun annotation(type: LsiSymbolId): LsiAnnotation = LsiAnnotation(type)

    private companion object {
        val FIRST_SOURCE = LsiSource.of("src/main/java/demo/First.java", LsiLanguage.JAVA)
        val SECOND_SOURCE = LsiSource.of("src/main/java/demo/Second.java", LsiLanguage.JAVA)
        val KOTLIN_SOURCE = LsiSource.of("src/main/kotlin/demo/Broken.kt", LsiLanguage.KOTLIN)
        val KOTLIN_SERVICE_SOURCE = LsiSource.of("src/main/kotlin/demo/Service.kt", LsiLanguage.KOTLIN)
        val SECOND_ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, SECOND_SOURCE)
        val STRING_TYPE = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val NESTED_SERVICE_ID = LsiSymbolId.type("demo.Outer.NestedService")
        val API = LsiSymbolId.type("org.babyfish.jimmer.client.meta.Api")
        val ENABLE_IMPLICIT_API = LsiSymbolId.type("org.babyfish.jimmer.client.EnableImplicitApi")
        val GET_MAPPING = LsiSymbolId.type("org.springframework.web.bind.annotation.GetMapping")
        val REST_CONTROLLER = LsiSymbolId.type("org.springframework.web.bind.annotation.RestController")
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val ERROR_FAMILY = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFamily")
        val BOOK_EXCEPTION_ID = LsiSymbolId.type("demo.BookException")
        val BOOK_FORBIDDEN_EXCEPTION_ID = LsiSymbolId.type("demo.BookException.Forbidden")
        val BOOK_NOT_FOUND_EXCEPTION_ID = LsiSymbolId.type("demo.BookException.NotFound")
    }
}
