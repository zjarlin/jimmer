package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata
import site.addzero.lsi.poet.LsiTypeName

data class ImmutableDraftImplPropertyMetadata(
    val name: String,
    val typeName: LsiTypeName,
    val isMutable: Boolean,
    val getterKind: ImmutableDraftImplPropertyGetterKind,
    val setterKind: ImmutableDraftImplPropertySetterKind,
    val idViewBaseName: String? = null,
    val idViewBaseTypeName: LsiTypeName? = null,
    val idViewBaseTargetProducerClassName: LsiClassName? = null,
    val idViewBaseNullable: Boolean = false,
    val idViewBaseList: Boolean = false,
    val draftListElementTypeName: LsiTypeName? = null,
    val draftListAssociation: Boolean = false,
    val validationPropMetadata: ImmutableValidationPropMetadata? = null,
    val modifiedValueFieldName: String? = null,
    val modifiedLoadedFieldName: String? = null,
    val copyToNonSharedList: Boolean = false,
)

enum class ImmutableDraftImplPropertyGetterKind {
    ID_VIEW_LIST,
    DRAFT_LIST,
    DRAFT_OBJECT,
    PASSTHROUGH,
}

enum class ImmutableDraftImplPropertySetterKind {
    NONE,
    ID_VIEW_TRANSFORM,
    ID_VIEW_DIRECT,
    STANDARD,
}

data class ImmutableDraftImplDispatchTypeMetadata(
    val propsSize: Int,
    val typeDescription: String,
    val props: List<ImmutableDraftImplDispatchPropMetadata>,
)

data class ImmutableDraftImplDispatchPropMetadata(
    val name: String,
    val slotName: String,
    val unloadKind: ImmutableDraftImplUnloadKind,
    val basePropSlotName: String? = null,
    val valueFieldName: String? = null,
    val loadedFieldName: String? = null,
    val unloadValueKind: ImmutableDraftImplUnloadValueKind? = null,
    val unloadValueTypeName: LsiTypeName? = null,
    val setKind: ImmutableDraftImplSetKind,
    val setTypeName: LsiTypeName? = null,
    val isNullable: Boolean = false,
)

enum class ImmutableDraftImplUnloadKind {
    DELEGATE_BASE,
    NO_OP,
    RESET_LOADED,
    RESET_VALUE,
}

enum class ImmutableDraftImplUnloadValueKind {
    NULL,
    PRIMITIVE_DEFAULT,
}

enum class ImmutableDraftImplSetKind {
    READ_ONLY,
    ASSIGN,
}

data class ImmutableDraftImplPropFunMetadata(
    val name: String,
    val slotName: String,
    val returnTypeName: LsiTypeName,
    val castTypeName: LsiTypeName,
    val isNullable: Boolean,
    val isList: Boolean,
    val targetProducerClassName: LsiClassName?,
)

data class ImmutableDraftImplPropRefMetadata(
    val name: String,
    val blockMetadata: ImmutableCallbackMetadata,
)

data class ImmutableDraftImplTypeMetadata(
    val className: LsiClassName,
    val draftProducerImplementorClassName: LsiClassName,
    val draftClassName: LsiClassName,
    val draftProducerImplClassName: LsiClassName,
    val members: List<ImmutableDraftImplMemberMetadata>,
    val dispatchType: ImmutableDraftImplDispatchTypeMetadata,
    val resolveProps: List<ImmutableDraftImplResolvePropMetadata>,
    val typeValidators: List<ImmutableDraftImplTypeValidatorMetadata>,
    val validationProps: List<ImmutableValidationPropMetadata>,
)

data class ImmutableDraftImplMemberMetadata(
    val property: ImmutableDraftImplPropertyMetadata,
    val propFun: ImmutableDraftImplPropFunMetadata?,
    val propRefFun: ImmutableDraftImplPropRefMetadata?,
    val associatedId: ImmutableAssociatedIdMetadata?,
)

data class ImmutableDraftImplResolvePropMetadata(
    val name: String,
    val slotName: String,
    val valueFieldName: String,
    val baseResolveKind: ImmutableDraftImplResolveKind,
    val modifiedResolveKind: ImmutableDraftImplResolveKind,
)

enum class ImmutableDraftImplResolveKind {
    NONE,
    LIST,
    OBJECT,
}

data class ImmutableDraftImplTypeValidatorMetadata(
    val className: LsiClassName,
    val message: String,
)
