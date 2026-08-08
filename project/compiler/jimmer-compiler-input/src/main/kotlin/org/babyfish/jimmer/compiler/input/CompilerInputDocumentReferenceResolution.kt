package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.jimmer.input.*

import site.addzero.lsi.compiler.CompilerInputDocumentReference
import site.addzero.lsi.compiler.CompilerInputDocumentTypeSelection
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
            DTO_SUBJECT_TYPE_REFERENCE_KIND,
            DTO_TARGET_TYPE_REFERENCE_KIND,
            DTO_MODEL_TYPE_REFERENCE_KIND,
            -> workspace.isImmutableType(typeId)

            DTO_REUSABLE_TYPE_REFERENCE_KIND ->
                typeId in sourceDtoTypeIds || workspace[typeId] is LsiTypeDeclaration

            DTO_ANNOTATION_TYPE_REFERENCE_KIND,
            DTO_SUPER_TYPE_REFERENCE_KIND,
            DTO_TYPE_USAGE_REFERENCE_KIND,
            DTO_CONFIG_IMPLEMENTATION_REFERENCE_KIND,
            -> workspace[typeId] is LsiTypeDeclaration

            else -> error("Unsupported Jimmer compiler input reference kind: '${kind.id}'")
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
