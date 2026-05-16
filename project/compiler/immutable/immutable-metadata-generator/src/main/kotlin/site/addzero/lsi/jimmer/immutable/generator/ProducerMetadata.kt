package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata

data class ImmutableProducerTypeMetadata(
    val className: LsiClassName,
    val draftClassName: LsiClassName,
    val draftImplClassName: LsiClassName,
    val draftCallbackMetadata: ImmutableCallbackMetadata,
    val isMappedSuperclass: Boolean,
    val superProducerClassNames: List<LsiClassName>,
    val redefinedProps: List<ImmutableProducerRedefinedPropMetadata>,
    val declaredProps: List<ImmutableProducerPropMetadata>,
    val slots: List<ImmutableProducerSlotMetadata>,
    val implementorTypeMetadata: ImmutableImplementorTypeMetadata?,
    val implTypeMetadata: ImmutableImplTypeMetadata?,
    val draftImplTypeMetadata: ImmutableDraftImplTypeMetadata?,
)

data class ImmutableProducerRedefinedPropMetadata(
    val name: String,
    val slotName: String,
)

data class ImmutableProducerPropMetadata(
    val propIdLiteral: String,
    val name: String,
    val kind: ImmutableProducerPropKind,
    val targetClassName: LsiClassName,
    val isNullable: Boolean,
    val categoryName: String? = null,
    val annotationClassName: LsiClassName? = null,
)

data class ImmutableProducerSlotMetadata(
    val slotName: String,
    val localId: Int?,
    val inheritedOwnerProducerClassName: LsiClassName?,
)

enum class ImmutableProducerPropKind {
    ID,
    VERSION,
    LOGICAL_DELETED,
    KEY_REFERENCE,
    KEY,
    ID_VIEW,
    SQL,
    ADD,
}
