package site.addzero.lsi.jimmer.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class ClientExceptionMetadataResolverTest {

    @Test
    fun `parses manual client exception hierarchy and preserves declaration order`() {
        val operation = compileOperation(
            thrownTypeIds = listOf(ROOT_EXCEPTION),
            exceptionTypes = validManualHierarchy(),
        )

        assertEquals(listOf(ROOT_EXCEPTION), operation.declaredExceptionTypeIds)
        assertEquals(
            listOf(SECOND_EXCEPTION, FIRST_EXCEPTION),
            operation.exceptionTypeIds,
        )
        assertEquals(
            listOf(ROOT_EXCEPTION, SECOND_EXCEPTION, FIRST_EXCEPTION),
            operation.exceptionMetadata.map(ClientExceptionMetadata::typeId),
        )
        operation.exceptionMetadata.forEach { metadata ->
            assertEquals("MANUAL", metadata.family)
            assertTrue(metadata.checked)
            assertNull(metadata.errorFamilyId)
        }
        val root = operation.exceptionMetadata.first()
        assertTrue(root.abstract)
        assertEquals(
            listOf(SECOND_EXCEPTION, FIRST_EXCEPTION),
            root.subTypeIds,
        )
    }

    @Test
    fun `leaf exception keeps its leaf set and closes the complete ancestor tree`() {
        val operation = compileOperation(
            thrownTypeIds = listOf(FIRST_EXCEPTION),
            exceptionTypes = validManualHierarchy(),
        )

        assertEquals(listOf(FIRST_EXCEPTION), operation.exceptionTypeIds)
        assertEquals(
            listOf(ROOT_EXCEPTION, SECOND_EXCEPTION, FIRST_EXCEPTION),
            operation.exceptionMetadata.map(ClientExceptionMetadata::typeId),
        )
        val metadataByTypeId = operation.exceptionMetadata.associateBy(ClientExceptionMetadata::typeId)
        operation.exceptionMetadata.forEach { metadata ->
            metadata.superTypeId?.let { superTypeId -> assertTrue(superTypeId in metadataByTypeId) }
            metadata.subTypeIds.forEach { subTypeId -> assertTrue(subTypeId in metadataByTypeId) }
        }
    }

    @Test
    fun `rejects subtype declarations that are not direct children`() {
        val root = exceptionType(
            typeId = ROOT_EXCEPTION,
            modality = LsiModality.ABSTRACT,
            superTypeId = CODE_BASED_EXCEPTION,
            annotation = clientException(
                family = "MANUAL",
                subTypeIds = listOf(FIRST_EXCEPTION),
            ),
        )
        val detachedLeaf = exceptionType(
            typeId = FIRST_EXCEPTION,
            superTypeId = CODE_BASED_EXCEPTION,
            annotation = clientException(code = "FIRST"),
        )

        val exception = assertFailsWith<ClientValidationException> {
            compileOperation(
                thrownTypeIds = listOf(ROOT_EXCEPTION),
                exceptionTypes = listOf(root, detachedLeaf),
            )
        }

        assertTrue(exception.message.orEmpty().contains("does not directly extend"))
    }

    @Test
    fun `rejects duplicate family code and illegal branch shapes`() {
        val first = exceptionType(
            typeId = FIRST_EXCEPTION,
            superTypeId = CODE_BASED_RUNTIME_EXCEPTION,
            annotation = clientException(family = "DUPLICATE", code = "SAME"),
        )
        val second = exceptionType(
            typeId = SECOND_EXCEPTION,
            superTypeId = CODE_BASED_RUNTIME_EXCEPTION,
            annotation = clientException(family = "DUPLICATE", code = "SAME"),
        )
        val duplicateException = assertFailsWith<ClientValidationException> {
            compileOperation(
                thrownTypeIds = listOf(SECOND_EXCEPTION, FIRST_EXCEPTION),
                exceptionTypes = listOf(first, second),
            )
        }
        assertTrue(duplicateException.message.orEmpty().contains("share family"))

        val illegalRoot = exceptionType(
            typeId = ROOT_EXCEPTION,
            modality = LsiModality.ABSTRACT,
            superTypeId = CODE_BASED_EXCEPTION,
            annotation = clientException(
                family = "ILLEGAL",
                code = "ROOT",
                subTypeIds = listOf(FIRST_EXCEPTION),
            ),
        )
        val leaf = exceptionType(
            typeId = FIRST_EXCEPTION,
            superTypeId = ROOT_EXCEPTION,
            annotation = clientException(code = "FIRST"),
        )
        val shapeException = assertFailsWith<ClientValidationException> {
            compileOperation(
                thrownTypeIds = listOf(ROOT_EXCEPTION),
                exceptionTypes = listOf(illegalRoot, leaf),
            )
        }
        assertTrue(shapeException.message.orEmpty().contains("either code or subtypes"))
    }

    @Test
    fun `rejects family and checked-state changes inside a hierarchy`() {
        val operationId = LsiSymbolId.function(SERVICE_ID, "execute")
        val familyRoot = metadata(
            typeId = ROOT_EXCEPTION,
            family = "ROOT",
            checked = true,
            abstract = true,
            subTypeIds = listOf(FIRST_EXCEPTION),
        )
        val differentFamilyLeaf = metadata(
            typeId = FIRST_EXCEPTION,
            family = "CHILD",
            code = "FIRST",
            checked = true,
            superTypeId = ROOT_EXCEPTION,
        )
        val familyException = assertFailsWith<ClientValidationException> {
            ClientExceptionMetadataResolver(listOf(familyRoot, differentFamilyLeaf))
                .resolve(listOf(ROOT_EXCEPTION), operationId)
        }
        assertTrue(familyException.message.orEmpty().contains("belongs to family"))

        val differentCheckedLeaf = differentFamilyLeaf.copy(
            family = "ROOT",
            checked = false,
        )
        val checkedException = assertFailsWith<ClientValidationException> {
            ClientExceptionMetadataResolver(listOf(familyRoot, differentCheckedLeaf))
                .resolve(listOf(ROOT_EXCEPTION), operationId)
        }
        assertTrue(checkedException.message.orEmpty().contains("checked state"))
    }

    private fun compileOperation(
        thrownTypeIds: List<LsiSymbolId>,
        exceptionTypes: List<LsiTypeDeclaration>,
    ): ClientOperation {
        val operationId = LsiSymbolId.function(SERVICE_ID, "execute")
        val operation = LsiFunction(
            id = operationId,
            name = "execute",
            ownerId = SERVICE_ID,
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            thrownTypes = thrownTypeIds.map(::LsiDeclaredType),
            annotations = listOf(annotation(API_ANNOTATION)),
            origin = SYNTHETIC_ORIGIN,
        )
        val service = LsiTypeDeclaration(
            id = SERVICE_ID,
            name = "Service",
            qualifiedName = SERVICE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
            memberIds = listOf(operationId),
            annotations = listOf(annotation(API_ANNOTATION)),
            origin = SYNTHETIC_ORIGIN,
        )
        val schema = LsiWorkspace(declarations = exceptionTypes + service + operation).toClientSchema(
            dependencies = ClientSchemaDependencies(
                immutableSchema = ImmutableSchema(emptyList()),
                errorSchema = ErrorSchema(emptyList()),
                definitionDocumentationByTypeId = emptyMap(),
            ),
        )
        return schema.services.single().operations.single()
    }

    private fun validManualHierarchy(): List<LsiTypeDeclaration> {
        return listOf(
            exceptionType(
                typeId = ROOT_EXCEPTION,
                modality = LsiModality.ABSTRACT,
                superTypeId = CODE_BASED_EXCEPTION,
                annotation = clientException(
                    family = "MANUAL",
                    subTypeIds = listOf(SECOND_EXCEPTION, FIRST_EXCEPTION),
                ),
            ),
            exceptionType(
                typeId = FIRST_EXCEPTION,
                superTypeId = ROOT_EXCEPTION,
                annotation = clientException(code = "FIRST"),
            ),
            exceptionType(
                typeId = SECOND_EXCEPTION,
                superTypeId = ROOT_EXCEPTION,
                annotation = clientException(code = "SECOND"),
            ),
        )
    }

    private fun exceptionType(
        typeId: LsiSymbolId,
        superTypeId: LsiSymbolId,
        annotation: LsiAnnotation,
        modality: LsiModality = LsiModality.FINAL,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = typeId,
            name = typeId.requireTypeQualifiedName().substringAfterLast('.'),
            qualifiedName = typeId.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            modality = modality,
            superTypes = listOf(LsiDeclaredType(superTypeId)),
            annotations = listOf(annotation),
            origin = SYNTHETIC_ORIGIN,
        )
    }

    private fun clientException(
        family: String = "",
        code: String = "",
        subTypeIds: List<LsiSymbolId> = emptyList(),
    ): LsiAnnotation {
        return annotation(
            type = CLIENT_EXCEPTION_ANNOTATION,
            arguments = mapOf(
                "family" to LsiAnnotationValue.StringValue(family),
                "code" to LsiAnnotationValue.StringValue(code),
                "subTypes" to LsiAnnotationValue.ArrayValue(
                    subTypeIds.map { typeId ->
                        LsiAnnotationValue.ClassValue(LsiDeclaredType(typeId))
                    }
                ),
            ),
        )
    }

    private fun annotation(
        type: LsiSymbolId,
        arguments: Map<String, LsiAnnotationValue> = emptyMap(),
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = arguments.mapValues { (_, value) ->
                LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
        )
    }

    private fun metadata(
        typeId: LsiSymbolId,
        family: String,
        code: String? = null,
        checked: Boolean,
        abstract: Boolean = false,
        superTypeId: LsiSymbolId? = null,
        subTypeIds: List<LsiSymbolId> = emptyList(),
    ): ClientExceptionMetadata {
        return ClientExceptionMetadata(
            typeId = typeId,
            errorFamilyId = null,
            family = family,
            code = code,
            checked = checked,
            abstract = abstract,
            superTypeId = superTypeId,
            subTypeIds = subTypeIds,
            documentation = null,
        )
    }

    private companion object {
        val SERVICE_ID = LsiSymbolId.type("demo.Service")
        val ROOT_EXCEPTION = LsiSymbolId.type("demo.RootException")
        val FIRST_EXCEPTION = LsiSymbolId.type("demo.FirstException")
        val SECOND_EXCEPTION = LsiSymbolId.type("demo.SecondException")
        val API_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.meta.Api")
        val CLIENT_EXCEPTION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.ClientException")
        val CODE_BASED_EXCEPTION = LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedException")
        val CODE_BASED_RUNTIME_EXCEPTION =
            LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedRuntimeException")
        val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
