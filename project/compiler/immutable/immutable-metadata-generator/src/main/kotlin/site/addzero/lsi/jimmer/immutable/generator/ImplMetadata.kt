package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.poet.LsiTypeName

data class ImmutableImplTypeMetadata(
    val className: LsiClassName,
    val implementorClassName: LsiClassName,
    val draftProducerImplClassName: LsiClassName,
    val draftProducerImplementorClassName: LsiClassName,
    val propsSize: Int,
    val typeDescription: String,
    val fieldProps: List<ImmutableImplFieldMetadata>,
    val getterProps: List<ImmutableImplGetterPropMetadata>,
    val stateProps: List<ImmutableImplStatePropMetadata>,
    val hiddenSlotNames: List<String>,
)

data class ImmutableImplFieldMetadata(
    val valueFieldName: String?,
    val valueFieldTypeName: LsiTypeName?,
    val valueFieldDefaultValueKind: ImmutableImplDefaultValueKind? = null,
    val valueFieldDefaultValueTypeName: LsiTypeName? = null,
    val loadedFieldName: String?,
)

enum class ImmutableImplDefaultValueKind {
    NULL,
    PRIMITIVE_DEFAULT,
}

data class ImmutableImplGetterPropMetadata(
    val name: String,
    val typeName: LsiTypeName,
    val description: String?,
    val kind: ImmutableImplGetterPropKind,
    val isNullable: Boolean,
    val valueFieldName: String?,
    val loadedFieldName: String?,
    val declaringTypeClassName: LsiClassName,
    val idViewBaseName: String? = null,
    val idViewBaseTypeName: LsiTypeName? = null,
    val idViewBaseTargetProducerClassName: LsiClassName? = null,
    val idViewTargetIdPropName: String? = null,
    val manyToManyViewBaseName: String? = null,
    val manyToManyViewBaseTypeName: LsiTypeName? = null,
    val deeperPropConstantName: String? = null,
)

enum class ImmutableImplGetterPropKind {
    ID_VIEW_LIST,
    ID_VIEW_SCALAR,
    MANY_TO_MANY_VIEW,
    STANDARD,
}

data class ImmutableImplStatePropMetadata(
    val name: String,
    val typeName: LsiTypeName,
    val slotName: String,
    val valueFieldName: String?,
    val loadedFieldName: String?,
    val isNullable: Boolean,
    val isAssociation: Boolean,
    val isId: Boolean,
    val loadKind: ImmutableImplLoadKind,
    val basePropSlotName: String? = null,
    val basePropName: String? = null,
    val basePropTypeName: LsiTypeName? = null,
    val basePropNullable: Boolean = false,
    val baseTargetDraftClassName: LsiClassName? = null,
    val baseTargetIdSlotName: String? = null,
    val deeperPropConstantName: String? = null,
    val formulaDependencies: List<ImmutableImplLoadDependencyMetadata> = emptyList(),
)

enum class ImmutableImplLoadKind {
    ID_VIEW_LIST,
    ID_VIEW_SCALAR,
    MANY_TO_MANY_VIEW,
    FORMULA,
    STANDARD,
}

data class ImmutableImplLoadDependencyMetadata(
    val slotRefs: List<ImmutableImplLoadSlotRefMetadata>,
)

data class ImmutableImplLoadSlotRefMetadata(
    val slotName: String,
    val declaringTypeDraftClassName: LsiClassName,
)
