package org.babyfish.jimmer.compiler.input

import org.babyfish.jimmer.compiler.CompilerInputDocumentReference
import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelection
import site.addzero.lsi.jimmer.isJimmerImmutableType
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

fun CompilerInputDocumentReference.selectType(
    workspace: LsiWorkspace,
    sourceDtoTypeIds: Set<LsiSymbolId> = emptySet(),
): CompilerInputDocumentTypeSelection {
    return typeSelector.select { typeId ->
        when (kind) {
            CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
            CompilerInputDocumentReferenceKind.TARGET_TYPE,
            CompilerInputDocumentReferenceKind.MODEL_TYPE,
            -> workspace.isImmutableType(typeId)

            CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE ->
                typeId in sourceDtoTypeIds || workspace[typeId] is LsiTypeDeclaration

            CompilerInputDocumentReferenceKind.ANNOTATION_TYPE,
            CompilerInputDocumentReferenceKind.SUPER_TYPE,
            CompilerInputDocumentReferenceKind.TYPE_USAGE,
            CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION,
            -> workspace[typeId] is LsiTypeDeclaration
        }
    }
}

fun CompilerInputDocumentReference.selectOwnerTarget(
    workspace: LsiWorkspace,
): CompilerInputDocumentTypeSelection? {
    return ownerTargetSelector?.select(workspace::isImmutableType)
}

private fun LsiWorkspace.isImmutableType(typeId: LsiSymbolId): Boolean {
    return (this[typeId] as? LsiTypeDeclaration)?.isJimmerImmutableType() == true
}
