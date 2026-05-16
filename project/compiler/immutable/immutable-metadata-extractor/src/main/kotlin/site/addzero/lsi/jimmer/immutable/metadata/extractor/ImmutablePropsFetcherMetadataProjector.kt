package site.addzero.lsi.jimmer.immutable.metadata.extractor

import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import site.addzero.lsi.codegen.KOTLIN_BOOLEAN_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_FIELD_DSL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_IMPLICIT_SUB_QUERY_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_LIST_FIELD_DSL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NONNULL_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_RECURSIVE_LIST_FIELD_DSL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_RECURSIVE_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherFieldKind
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeRefMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.isSubtypeOfComparableLike
import site.addzero.lsi.type.isSubtypeOfJavaUtilDateLike
import site.addzero.lsi.type.isSubtypeOfNumberLike
import site.addzero.lsi.type.isSubtypeOfTemporalLike

private const val JOIN_TABLE_ANNOTATION_QUALIFIED_NAME = "org.babyfish.jimmer.sql.JoinTable"

internal fun ImmutableType.toPropsTypeMetadata(): ImmutablePropsTypeMetadata =
    ImmutablePropsTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator.generate 的 `ImmutableType` 级读取
        // 迁移说明：Props 生成链的类型命名、实体/嵌入式判定、id 属性与字段列表前移到 metadata-extractor，resolved-source 在进入 generator 之前就持有纯 props metadata
        simpleName = simpleName,
        className = lsiClassName,
        propsClassName = lsiPropsClassName,
        fetcherDslClassName = lsiFetcherDslClassName,
        fetchByBlockMetadata = fetcherDslBlockMetadata(lsiFetcherDslClassName),
        propExpressionClassName = lsiPropExpressionClassName,
        tableClassName = lsiTableClassName,
        tableExClassName = lsiTableExClassName,
        remoteTableClassName = lsiRemoteTableClassName,
        isEmbeddable = isEmbeddable,
        isEntity = isEntity,
        idProp = idProp?.let { idProp ->
            ImmutablePropsIdMetadata(
                name = idProp.name,
                type = idProp.lsiType.toPropsTypeRefMetadata(),
                targetType = idProp.toTargetTypeRefMetadata(),
            )
        },
        properties = properties.values.map { it.toPropsPropMetadata(this) },
    )

private fun ImmutableProp.toPropsPropMetadata(
    declaringType: ImmutableType,
): ImmutablePropsPropMetadata {
    val associationTargetType = targetType
    val associationTargetIdProp = associationTargetType?.idProp
    return ImmutablePropsPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator 的属性级 `ImmutableProp` 读取
        // 迁移说明：Props 所需的 DSL 开关、关联/id-view 投影、typed-prop 分类与类型结构统一前移为 pure metadata；generator 只消费值对象
        name = name,
        constantName = StringUtil.snake(name, SnakeCase.UPPER),
        generatedIdPropName = declaringType.getIdPropName(name),
        isNullable = isNullable,
        isList = isList,
        isTransient = isTransient,
        isRemote = isRemote,
        isEmbedded = isEmbedded,
        isAssociation = isAssociation(true),
        isReferenceList = isReferenceList,
        isReference = isReference,
        isScalarList = isScalarList,
        isDslTable = isDsl(false),
        isDslTableEx = isDsl(true),
        type = lsiType.toPropsTypeRefMetadata(),
        targetType = toTargetTypeRefMetadata(),
        associationTargetClassName = associationTargetType?.lsiClassName,
        predicateBlockMetadata = toPredicateBlockMetadata(associationTargetType),
        targetIdType = associationTargetIdProp?.lsiType?.toPropsTypeRefMetadata(),
        targetIdTargetType = associationTargetIdProp?.toTargetTypeRefMetadata(),
        targetIdIsEmbedded = associationTargetIdProp?.isEmbedded == true,
    )
}

private fun ImmutableProp.toTargetTypeRefMetadata(): ImmutablePropsTypeRefMetadata {
    val baseType = lsiType
    val targetType = if (isList) {
        baseType?.typeParameters?.firstOrNull() ?: baseType
    } else {
        baseType
    }
    return targetType.toPropsTypeRefMetadata()
}

private fun LsiType?.toPropsTypeRefMetadata(): ImmutablePropsTypeRefMetadata =
    if (this == null) {
        ImmutablePropsTypeRefMetadata(
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator 的 `prop.typeName()/prop.targetTypeName()` 空值兜底
            // 迁移说明：Props 类型引用的“空类型”语义前移到 extractor，generator 只再负责 TypeName 排版转换
            qualifiedName = null,
            simpleName = null,
            presentableText = null,
            nullable = true,
            primitive = false,
            array = false,
            typeArguments = emptyList(),
            componentType = null,
        )
    } else {
        ImmutablePropsTypeRefMetadata(
            // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator 的 `prop.typeName()/prop.targetTypeName()` 类型结构读取
            // 迁移说明：Props 类型树上的 qualifiedName/simpleName/nullability/泛型/数组结构统一在 extractor 完成，generator 只做末端 TypeName materialize
            qualifiedName = qualifiedName,
            simpleName = simpleName,
            presentableText = presentableText,
            nullable = isNullable,
            primitive = isPrimitive,
            array = isArray,
            typeArguments = typeParameters.map { it.toPropsTypeRefMetadata() },
            componentType = componentType?.toPropsTypeRefMetadata(),
            subtypeOfNumber = isSubtypeOfNumberLike(),
            subtypeOfDate = isSubtypeOfJavaUtilDateLike(),
            subtypeOfTemporal = isSubtypeOfTemporalLike(),
            subtypeOfComparable = isSubtypeOfComparableLike(),
        )
    }

internal fun ImmutableType.toFetcherTypeMetadata(): ImmutableFetcherTypeMetadata {
    val properties = properties.values.map { it.toFetcherPropMetadata() }
    return ImmutableFetcherTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../FetcherGenerator.generate / FetcherDslGenerator.generate 的 `type.simpleName`
        // 迁移说明：fetcher 生成链的类型命名前移到 metadata-extractor，resolved-source 在进入 generator 前就持有纯 fetcher metadata
        simpleName = simpleName,
        className = lsiClassName,
        fetcherDslClassName = lsiFetcherDslClassName,
        byBlockMetadata = fetcherDslBlockMetadata(lsiFetcherDslClassName),
        properties = properties,
    )
}

private fun ImmutableProp.toFetcherPropMetadata(): ImmutableFetcherPropMetadata {
    val associationProp = idViewBaseProp ?: this
    val targetType = targetType
    val targetClassName = targetType?.lsiClassName
    val targetTableClassName = targetType?.lsiTableClassName
    val targetIsEntity = targetType?.isEntity == true
    val targetIsEmbeddable = targetType?.isEmbeddable == true
    val configurable = !isRemote && targetIsEntity
    return ImmutableFetcherPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../FetcherDslGenerator 的属性级 `ImmutableProp` 读取
        // 迁移说明：id-only/reference/recursive/configurable 等 fetcher 行为判定前移到 extractor，generator 只消费布尔量和目标类型名
        name = name,
        isId = isId,
        isList = isList,
        supportsIdOnlyFetchType =
            !associationProp.isTransient &&
                associationProp.isAssociation(true) &&
                !isReverse &&
                !associationProp.isList &&
                associationProp.lsiAnnotation(JOIN_TABLE_ANNOTATION_QUALIFIED_NAME) === null,
        supportsReferenceFetchType = !isRemote && !isList && isAssociation(true),
        supportsRecursive = isRecursive,
        targetClassName = targetClassName,
        targetTableClassName = targetTableClassName,
        childBlockMetadata = targetType?.lsiFetcherDslClassName?.let(::fetcherDslBlockMetadata),
        fieldConfigBlockMetadata = toFieldConfigBlockMetadata(targetClassName, configurable),
        recursiveConfigBlockMetadata = toRecursiveConfigBlockMetadata(targetClassName),
        targetIsEntity = targetIsEntity,
        targetIsEmbeddable = targetIsEmbeddable,
        configurable = configurable,
        fieldKind = when {
            isList -> ImmutableFetcherFieldKind.LIST
            isAssociation(true) -> ImmutableFetcherFieldKind.REFERENCE
            else -> ImmutableFetcherFieldKind.SIMPLE
        },
    )
}

private fun fetcherDslBlockMetadata(fetcherDslClassName: site.addzero.lsi.codegen.LsiClassName): ImmutableCallbackMetadata =
    ImmutableCallbackMetadata(
        receiverTypeName = fetcherDslClassName,
        returnTypeName = KOTLIN_UNIT_LSI_CLASS_NAME,
    )

private fun ImmutableProp.toPredicateBlockMetadata(
    associationTargetType: ImmutableType?,
): ImmutableCallbackMetadata? {
    if (!(isList && isAssociation(true))) {
        return null
    }
    val associationTargetClassName = associationTargetType?.lsiClassName ?: return null
    return ImmutableCallbackMetadata(
        receiverTypeName = LsiParameterizedTypeName(
            rawType = K_IMPLICIT_SUB_QUERY_TABLE_LSI_CLASS_NAME,
            typeArguments = listOf(associationTargetClassName),
        ),
        returnTypeName = nullableBooleanExpressionTypeName(),
    )
}

private fun ImmutableProp.toFieldConfigBlockMetadata(
    targetClassName: site.addzero.lsi.codegen.LsiClassName?,
    configurable: Boolean,
): ImmutableCallbackMetadata? {
    if (!configurable) {
        return null
    }
    val resolvedTargetClassName = targetClassName ?: return null
    val configDslClassName = when {
        isList -> K_LIST_FIELD_DSL_LSI_CLASS_NAME
        isAssociation(true) -> K_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
        else -> K_FIELD_DSL_LSI_CLASS_NAME
    }
    return callbackMetadata(
        receiverTypeName = LsiParameterizedTypeName(
            rawType = configDslClassName,
            typeArguments = listOf(resolvedTargetClassName),
        ),
        nullable = true,
    )
}

private fun ImmutableProp.toRecursiveConfigBlockMetadata(
    targetClassName: site.addzero.lsi.codegen.LsiClassName?,
): ImmutableCallbackMetadata? {
    if (!isRecursive) {
        return null
    }
    val resolvedTargetClassName = targetClassName ?: return null
    val configDslClassName = if (isList) {
        K_RECURSIVE_LIST_FIELD_DSL_LSI_CLASS_NAME
    } else {
        K_RECURSIVE_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
    }
    return callbackMetadata(
        receiverTypeName = LsiParameterizedTypeName(
            rawType = configDslClassName,
            typeArguments = listOf(resolvedTargetClassName),
        ),
        nullable = true,
    )
}

private fun callbackMetadata(
    receiverTypeName: site.addzero.lsi.poet.LsiTypeName,
    nullable: Boolean = false,
): ImmutableCallbackMetadata =
    ImmutableCallbackMetadata(
        receiverTypeName = receiverTypeName,
        returnTypeName = KOTLIN_UNIT_LSI_CLASS_NAME,
        nullable = nullable,
    )

private fun nullableBooleanExpressionTypeName(): site.addzero.lsi.poet.LsiTypeName =
    LsiParameterizedTypeName(
        rawType = K_NONNULL_EXPRESSION_LSI_CLASS_NAME,
        typeArguments = listOf(KOTLIN_BOOLEAN_LSI_CLASS_NAME),
        nullable = true,
    )
