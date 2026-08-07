package org.babyfish.jimmer.compiler.input

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentReference
import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelector
import org.babyfish.jimmer.dto.compiler.DtoDocumentReferenceKind
import org.babyfish.jimmer.dto.compiler.DtoDocumentReferences
import org.babyfish.jimmer.dto.compiler.DtoTypeNameSelector
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId

internal class CompilerInputDocumentReferenceFreezer {

    fun freeze(document: CompilerInputDocument): CompilerInputDocumentSnapshot {
        val references = when (document.kind) {
            CompilerInputDocumentKind.DTO -> freezeDtoReferences(document)
        }
        return CompilerInputDocumentSnapshot(document, references.sorted())
    }

    private fun freezeDtoReferences(
        document: CompilerInputDocument,
    ): List<CompilerInputDocumentReference> {
        return DtoDocumentReferences.parse(document.toDtoFile()).map { reference ->
            CompilerInputDocumentReference(
                typeSelector = reference.typeSelector.toCompilerSelector(),
                kind = when (reference.kind) {
                    DtoDocumentReferenceKind.SUBJECT_TYPE ->
                        CompilerInputDocumentReferenceKind.SUBJECT_TYPE
                    DtoDocumentReferenceKind.TARGET_TYPE ->
                        CompilerInputDocumentReferenceKind.TARGET_TYPE
                    DtoDocumentReferenceKind.ANNOTATION_TYPE ->
                        CompilerInputDocumentReferenceKind.ANNOTATION_TYPE
                    DtoDocumentReferenceKind.SUPER_TYPE ->
                        CompilerInputDocumentReferenceKind.SUPER_TYPE
                    DtoDocumentReferenceKind.MODEL_TYPE ->
                        CompilerInputDocumentReferenceKind.MODEL_TYPE
                    DtoDocumentReferenceKind.REUSABLE_DTO_TYPE ->
                        CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
                    DtoDocumentReferenceKind.TYPE_USAGE ->
                        CompilerInputDocumentReferenceKind.TYPE_USAGE
                    DtoDocumentReferenceKind.CONFIG_IMPLEMENTATION ->
                        CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION
                },
                ownerTargetSelector = reference.ownerTargetSelector?.toCompilerSelector(),
                location = LsiLocation(
                    source = document.source,
                    start = LsiPosition(reference.line, reference.column),
                ),
            )
        }
    }
}

private fun DtoTypeNameSelector.toCompilerSelector(): CompilerInputDocumentTypeSelector {
    return CompilerInputDocumentTypeSelector(
        sourceName = sourceName,
        fallbackTypeId = LsiSymbolId.type(fallbackQualifiedName),
        wildcardTypeIds = wildcardQualifiedNames.map(LsiSymbolId::type),
        checksFallbackExistence = checksFallbackExistence,
    )
}
