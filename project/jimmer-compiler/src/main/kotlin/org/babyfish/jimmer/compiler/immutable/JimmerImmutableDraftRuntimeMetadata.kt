package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

internal data class JimmerImmutableDraftRuntimeProp(
    val kind: JimmerImmutableDraftRuntimePropKind,
    val valueCategory: JimmerImmutableDraftRuntimeValueCategory,
    val associationAnnotationTypeId: LsiSymbolId?,
    val metadataElementType: LsiTypeRef,
) {
    init {
        require(
            (kind == JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE ||
                kind == JimmerImmutableDraftRuntimePropKind.ASSOCIATION) ==
                (associationAnnotationTypeId != null)
        ) {
            "Immutable draft runtime association metadata must match its property kind"
        }
        require(metadataElementType.isErasedMetadataType()) {
            "Immutable draft runtime metadata element type must be erased"
        }
    }
}

internal enum class JimmerImmutableDraftRuntimePropKind {
    ID,
    VERSION,
    LOGICAL_DELETED,
    KEY_SCALAR,
    KEY_REFERENCE,
    ASSOCIATION,
    VALUE,
}

internal enum class JimmerImmutableDraftRuntimeValueCategory {
    SCALAR,
    SCALAR_LIST,
    REFERENCE,
    REFERENCE_LIST,
}

internal fun JimmerImmutableProp.compileDraftRuntimeProp(
    elementType: LsiTypeRef,
): JimmerImmutableDraftRuntimeProp {
    val key = annotations.any { annotation ->
        annotation.type == KEY_ANNOTATION_TYPE_ID || annotation.type == KEYS_ANNOTATION_TYPE_ID
    }
    val kind = when {
        primaryMapping == JimmerImmutablePrimaryMapping.ID -> JimmerImmutableDraftRuntimePropKind.ID
        primaryMapping == JimmerImmutablePrimaryMapping.VERSION -> JimmerImmutableDraftRuntimePropKind.VERSION
        primaryMapping == JimmerImmutablePrimaryMapping.LOGICAL_DELETED -> {
            JimmerImmutableDraftRuntimePropKind.LOGICAL_DELETED
        }
        key && association -> JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE
        key -> JimmerImmutableDraftRuntimePropKind.KEY_SCALAR
        associationKind.hasRuntimeAnnotation -> JimmerImmutableDraftRuntimePropKind.ASSOCIATION
        else -> JimmerImmutableDraftRuntimePropKind.VALUE
    }
    val valueCategory = when {
        list && association -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE_LIST
        list -> JimmerImmutableDraftRuntimeValueCategory.SCALAR_LIST
        association -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE
        else -> JimmerImmutableDraftRuntimeValueCategory.SCALAR
    }
    val associationAnnotationTypeId = when (kind) {
        JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE -> {
            if (associationKind == JimmerAssociationKind.ONE_TO_ONE) {
                ONE_TO_ONE_ANNOTATION_TYPE_ID
            } else {
                MANY_TO_ONE_ANNOTATION_TYPE_ID
            }
        }
        JimmerImmutableDraftRuntimePropKind.ASSOCIATION -> associationKind.runtimeAnnotationTypeId()
        JimmerImmutableDraftRuntimePropKind.ID,
        JimmerImmutableDraftRuntimePropKind.VERSION,
        JimmerImmutableDraftRuntimePropKind.LOGICAL_DELETED,
        JimmerImmutableDraftRuntimePropKind.KEY_SCALAR,
        JimmerImmutableDraftRuntimePropKind.VALUE,
        -> null
    }
    return JimmerImmutableDraftRuntimeProp(
        kind = kind,
        valueCategory = valueCategory,
        associationAnnotationTypeId = associationAnnotationTypeId,
        metadataElementType = elementType.toErasedMetadataType(),
    )
}

private val JimmerAssociationKind.hasRuntimeAnnotation: Boolean
    get() = this != JimmerAssociationKind.NONE && this != JimmerAssociationKind.IMPLICIT

private fun JimmerAssociationKind.runtimeAnnotationTypeId(): LsiSymbolId {
    return when (this) {
        JimmerAssociationKind.ONE_TO_ONE -> ONE_TO_ONE_ANNOTATION_TYPE_ID
        JimmerAssociationKind.MANY_TO_ONE -> MANY_TO_ONE_ANNOTATION_TYPE_ID
        JimmerAssociationKind.ONE_TO_MANY -> ONE_TO_MANY_ANNOTATION_TYPE_ID
        JimmerAssociationKind.MANY_TO_MANY -> MANY_TO_MANY_ANNOTATION_TYPE_ID
        JimmerAssociationKind.MANY_TO_MANY_VIEW -> MANY_TO_MANY_VIEW_ANNOTATION_TYPE_ID
        JimmerAssociationKind.NONE,
        JimmerAssociationKind.IMPLICIT,
        -> error("Immutable association kind '$this' has no runtime annotation")
    }
}

private fun LsiTypeRef.toErasedMetadataType(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = emptyList(),
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> OBJECT_TYPE
        is LsiPrimitiveType -> copy(
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiArrayType -> copy(
            elementType = elementType.toErasedMetadataType(),
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiUnresolvedType -> error(
            "Cannot compile unresolved immutable draft metadata element type '$displayName'"
        )
    }
}

private fun LsiTypeRef.isErasedMetadataType(): Boolean {
    return when (this) {
        is LsiDeclaredType -> arguments.isEmpty() &&
            nullability == LsiNullability.NON_NULL &&
            annotations.isEmpty()
        is LsiTypeParameterRef -> false
        is LsiPrimitiveType -> nullability == LsiNullability.NON_NULL && annotations.isEmpty()
        is LsiArrayType -> nullability == LsiNullability.NON_NULL &&
            annotations.isEmpty() &&
            elementType.isErasedMetadataType()
        is LsiUnresolvedType -> false
    }
}

private val OBJECT_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.Object"))

private val KEY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")

private val KEYS_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Keys")

private val ONE_TO_ONE_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")

private val MANY_TO_ONE_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")

private val ONE_TO_MANY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")

private val MANY_TO_MANY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")

private val MANY_TO_MANY_VIEW_ANNOTATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
