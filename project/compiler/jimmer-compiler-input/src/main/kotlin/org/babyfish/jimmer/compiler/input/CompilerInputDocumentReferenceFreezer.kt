package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.jimmer.input.*

import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentReference
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.compiler.CompilerInputDocumentTypeSelector
import org.babyfish.jimmer.dto.compiler.DtoDocumentReferenceKind
import org.babyfish.jimmer.dto.compiler.DtoDocumentReferences
import org.babyfish.jimmer.dto.compiler.DtoTypeNameSelector
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId

class CompilerInputDocumentReferenceFreezer {

    fun freeze(document: CompilerInputDocument): CompilerInputDocumentSnapshot {
        require(document.kind == DTO_INPUT_DOCUMENT_KIND) {
            "Unsupported Jimmer compiler input document kind: '${document.kind.id}'"
        }
        require(document.relativePath.endsWith(".dto")) {
            "DTO compiler input document must use the .dto extension: '${document.relativePath}'"
        }
        val references = freezeDtoReferences(document)
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
                        DTO_SUBJECT_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.TARGET_TYPE ->
                        DTO_TARGET_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.ANNOTATION_TYPE ->
                        DTO_ANNOTATION_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.SUPER_TYPE ->
                        DTO_SUPER_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.MODEL_TYPE ->
                        DTO_MODEL_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.REUSABLE_DTO_TYPE ->
                        DTO_REUSABLE_TYPE_REFERENCE_KIND
                    DtoDocumentReferenceKind.TYPE_USAGE ->
                        DTO_TYPE_USAGE_REFERENCE_KIND
                    DtoDocumentReferenceKind.CONFIG_IMPLEMENTATION ->
                        DTO_CONFIG_IMPLEMENTATION_REFERENCE_KIND
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
