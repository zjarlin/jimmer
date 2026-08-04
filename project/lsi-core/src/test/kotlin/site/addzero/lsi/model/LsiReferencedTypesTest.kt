package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiReferencedTypesTest {

    @Test
    fun `collects enclosing declaration as referenced type`() {
        val outerId = LsiSymbolId.type("sample.Outer")
        val nested = LsiTypeDeclaration(
            id = LsiSymbolId.type("sample.Outer.Nested"),
            name = "Nested",
            qualifiedName = "sample.Outer.Nested",
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = outerId,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        assertEquals(setOf(outerId), listOf(nested).referencedTypeIds())
    }

    @Test
    fun `collects annotation member types`() {
        val memberTypeId = LsiSymbolId.type("sample.Payload")
        val annotation = LsiTypeDeclaration(
            id = LsiSymbolId.type("sample.Marker"),
            name = "Marker",
            qualifiedName = "sample.Marker",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotationMembers = listOf(
                LsiAnnotationMember("value", LsiDeclaredType(memberTypeId))
            ),
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        assertEquals(setOf(memberTypeId), listOf(annotation).referencedTypeIds())
    }

    @Test
    fun `collects function components and type use annotation dependencies`() {
        val ownerId = LsiSymbolId.type("sample.Owner")
        val receiverId = LsiSymbolId.type("sample.Receiver")
        val parameterId = LsiSymbolId.type("sample.Parameter")
        val returnId = LsiSymbolId.type("sample.Result")
        val typeAnnotationId = LsiSymbolId.type("sample.TypeMarker")
        val annotationPayloadId = LsiSymbolId.type("sample.AnnotationPayload")
        val functionType = LsiFunctionType(
            receiverType = LsiDeclaredType(receiverId),
            parameterTypes = listOf(LsiDeclaredType(parameterId)),
            returnType = LsiDeclaredType(returnId),
            annotations = listOf(
                LsiAnnotation(
                    type = typeAnnotationId,
                    arguments = mapOf(
                        "payload" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ClassValue(LsiDeclaredType(annotationPayloadId)),
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        ),
                    ),
                ),
            ),
        )
        val property = LsiProperty(
            id = LsiSymbolId.property(ownerId, "callback"),
            name = "callback",
            ownerId = ownerId,
            type = functionType,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )

        assertEquals(
            setOf(receiverId, parameterId, returnId, typeAnnotationId, annotationPayloadId),
            listOf(property).referencedTypeIds(),
        )
    }
}
