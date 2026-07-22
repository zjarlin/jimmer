package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
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

internal fun ImmutableProp.compileDraftRuntimeProp(
    elementType: LsiTypeRef,
    immutableReference: Boolean,
): JimmerImmutableDraftRuntimeProp {
    val key = annotations.any { annotation ->
        annotation.type == KEY_ANNOTATION_TYPE_ID || annotation.type == KEYS_ANNOTATION_TYPE_ID
    }
    val kind = when {
        primaryMapping == PrimaryMapping.ID -> JimmerImmutableDraftRuntimePropKind.ID
        primaryMapping == PrimaryMapping.VERSION -> JimmerImmutableDraftRuntimePropKind.VERSION
        primaryMapping == PrimaryMapping.LOGICAL_DELETED -> {
            JimmerImmutableDraftRuntimePropKind.LOGICAL_DELETED
        }
        key && immutableReference -> JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE
        key -> JimmerImmutableDraftRuntimePropKind.KEY_SCALAR
        associationKind.hasRuntimeAnnotation -> JimmerImmutableDraftRuntimePropKind.ASSOCIATION
        else -> JimmerImmutableDraftRuntimePropKind.VALUE
    }
    val valueCategory = when {
        list && immutableReference -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE_LIST
        list -> JimmerImmutableDraftRuntimeValueCategory.SCALAR_LIST
        immutableReference -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE
        else -> JimmerImmutableDraftRuntimeValueCategory.SCALAR
    }
    val associationAnnotationTypeId = when (kind) {
        JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE -> {
            if (associationKind == AssociationKind.ONE_TO_ONE) {
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

private val AssociationKind.hasRuntimeAnnotation: Boolean
    get() = this != AssociationKind.NONE && this != AssociationKind.IMPLICIT

private fun AssociationKind.runtimeAnnotationTypeId(): LsiSymbolId {
    return when (this) {
        AssociationKind.ONE_TO_ONE -> ONE_TO_ONE_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_ONE -> MANY_TO_ONE_ANNOTATION_TYPE_ID
        AssociationKind.ONE_TO_MANY -> ONE_TO_MANY_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_MANY -> MANY_TO_MANY_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_MANY_VIEW -> MANY_TO_MANY_VIEW_ANNOTATION_TYPE_ID
        AssociationKind.NONE,
        AssociationKind.IMPLICIT,
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
        is LsiFunctionType -> error(
            "Cannot compile function type as immutable draft runtime metadata",
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
        is LsiFunctionType -> false
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
