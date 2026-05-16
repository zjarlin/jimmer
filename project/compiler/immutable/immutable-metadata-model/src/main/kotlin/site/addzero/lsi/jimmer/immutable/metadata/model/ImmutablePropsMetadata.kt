package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.codegen.LsiClassName

/**
 * immutable props 生成输入元数据。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator`
 *
 * 迁移说明：
 * - 将 Props 生成所需的纯值对象从 generator 私有目录前移到 metadata-model
 * - 后续 extractor 可以直接产出这组 metadata，generator 只负责排版与 artifact 装配
 */
data class ImmutablePropsTypeMetadata(
    val simpleName: String,
    val className: LsiClassName,
    val propsClassName: LsiClassName,
    val fetcherDslClassName: LsiClassName,
    val fetchByBlockMetadata: ImmutableCallbackMetadata,
    val propExpressionClassName: LsiClassName,
    val tableClassName: LsiClassName,
    val tableExClassName: LsiClassName,
    val remoteTableClassName: LsiClassName,
    val isEmbeddable: Boolean,
    val isEntity: Boolean,
    val idProp: ImmutablePropsIdMetadata?,
    val properties: List<ImmutablePropsPropMetadata>,
)

data class ImmutablePropsIdMetadata(
    val name: String,
    val type: ImmutablePropsTypeRefMetadata,
    val targetType: ImmutablePropsTypeRefMetadata,
)

data class ImmutablePropsPropMetadata(
    val name: String,
    val constantName: String,
    val generatedIdPropName: String?,
    val isNullable: Boolean,
    val isList: Boolean,
    val isTransient: Boolean,
    val isRemote: Boolean,
    val isEmbedded: Boolean,
    val isAssociation: Boolean,
    val isReferenceList: Boolean,
    val isReference: Boolean,
    val isScalarList: Boolean,
    val isDslTable: Boolean,
    val isDslTableEx: Boolean,
    val type: ImmutablePropsTypeRefMetadata,
    val targetType: ImmutablePropsTypeRefMetadata,
    val associationTargetClassName: LsiClassName?,
    val predicateBlockMetadata: ImmutableCallbackMetadata?,
    val targetIdType: ImmutablePropsTypeRefMetadata?,
    val targetIdTargetType: ImmutablePropsTypeRefMetadata?,
    val targetIdIsEmbedded: Boolean,
)

data class ImmutablePropsTypeRefMetadata(
    val qualifiedName: String?,
    val simpleName: String?,
    val presentableText: String?,
    val nullable: Boolean,
    val primitive: Boolean,
    val array: Boolean,
    val typeArguments: List<ImmutablePropsTypeRefMetadata>,
    val componentType: ImmutablePropsTypeRefMetadata?,
    val subtypeOfNumber: Boolean = false,
    val subtypeOfDate: Boolean = false,
    val subtypeOfTemporal: Boolean = false,
    val subtypeOfComparable: Boolean = false,
)
