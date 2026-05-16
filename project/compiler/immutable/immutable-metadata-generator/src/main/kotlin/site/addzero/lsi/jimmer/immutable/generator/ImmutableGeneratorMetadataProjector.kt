package site.addzero.lsi.jimmer.immutable.generator

import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.LogicalDeleted
import org.babyfish.jimmer.sql.ManyToMany
import org.babyfish.jimmer.sql.ManyToManyView
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.OneToOne
import org.babyfish.jimmer.sql.Transient
import org.babyfish.jimmer.sql.Version
import site.addzero.lsi.codegen.DRAFT_IMPL
import site.addzero.lsi.codegen.IMPLEMENTOR
import site.addzero.lsi.codegen.IMPL
import site.addzero.lsi.codegen.IMMUTABLE_CREATOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.MANY_TO_MANY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.MANY_TO_MANY_VIEW_LSI_CLASS_NAME
import site.addzero.lsi.codegen.MANY_TO_ONE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.NON_SHARED_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.ONE_TO_MANY_LSI_CLASS_NAME
import site.addzero.lsi.codegen.ONE_TO_ONE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.jimmer.immutable.metadata.extractor.toAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.extractor.deeperPropIdPropName
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableResolvedSource
import site.addzero.lsi.jimmer.immutable.metadata.extractor.toBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.extractor.toValidationPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.extractor.toImplementorTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableSourceMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeExpression

/**
 * immutable 生成侧的过渡 projector。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../generator/`
 *
 * 迁移说明：
 * - 将分散在多个 metadata 定义文件中的 `ImmutableType` / `ImmutableProp` -> 纯 metadata 投影逻辑集中到单点
 * - generator metadata 数据类文件本身只保留纯值定义与纯 metadata helper
 * - 当前 remaining bridge 仍是私有过渡层，后续再继续向 extractor / 更纯 metadata 边界推进
 */

internal data class ImmutableSourceGenerationPlan(
    val metadata: ImmutableSourceMetadata,
    val draftTypes: List<ImmutableDraftTypeMetadata>,
    val propsTypeMetadata: ImmutablePropsTypeMetadata?,
    val fetcherTypeMetadata: ImmutableFetcherTypeMetadata?,
)

internal fun ImmutableResolvedSource.toImmutableSourceGenerationPlan(
    excludedUserTypePrefixes: List<String>,
): ImmutableSourceGenerationPlan =
    ImmutableSourceGenerationPlan(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.generateJimmerTypes 的 file-level 编排输入
        // 迁移说明：source generation plan 仅保留 draft 投影在 generator 私域；props/fetcher metadata 已前移到 extractor 侧 resolved-source，artifact 装配文件不再自行回放目标 ImmutableType
        metadata = metadata,
        draftTypes = immutableTypes.map {
            it.toDraftTypeMetadata(excludedUserTypePrefixes)
        },
        propsTypeMetadata = propsTypeMetadata,
        fetcherTypeMetadata = fetcherTypeMetadata,
    )

internal fun ImmutableType.toDraftTypeMetadata(
    excludedUserTypePrefixes: List<String>,
): ImmutableDraftTypeMetadata =
    ImmutableDraftTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator 的 `ImmutableType` 级读取
        // 迁移说明：Draft 顶层接口、producer/builder 子生成器入口以及 add/by/copy 顶层 helper 所需的命名、继承、声明属性和函数签名统一前移为本地 metadata，generator 不再直接持有 ImmutableType
        simpleName = simpleName,
        className = lsiClassName,
        draftClassName = lsiDraftClassName,
        superDraftClassNames = superTypes.map { it.lsiDraftClassName },
        declaredProps = declaredProperties.values.mapNotNull { it.toDraftDeclaredPropMetadata() },
        producerTypeMetadata = toProducerTypeMetadata(),
        builderTypeMetadata = if (isMappedSuperclass) null else toBuilderTypeMetadata(excludedUserTypePrefixes),
        addFunMetadatas = if (isMappedSuperclass) {
            emptyList()
        } else {
            listOf(
                toDraftAddFunMetadata(withBase = false, withBlock = true),
                toDraftAddFunMetadata(withBase = true, withBlock = false),
                toDraftAddFunMetadata(withBase = true, withBlock = true),
            )
        },
        newFunMetadatas = if (isMappedSuperclass) {
            emptyList()
        } else {
            listOf(
                toDraftNewFunMetadata(withCreator = true, withBase = false, withBlock = true),
                toDraftNewFunMetadata(withCreator = true, withBase = true, withBlock = false),
                toDraftNewFunMetadata(withCreator = true, withBase = true, withBlock = true),
                toDraftNewFunMetadata(withCreator = false, withBase = false, withBlock = true),
                toDraftNewFunMetadata(withCreator = false, withBase = true, withBlock = true),
            )
        },
        copyFunMetadata = if (isMappedSuperclass) null else toDraftCopyFunMetadata(),
    )

private fun ImmutableProp.toDraftDeclaredPropMetadata(): ImmutableDraftDeclaredPropMetadata? {
    if (manyToManyViewBaseProp !== null) {
        return null
    }
    return ImmutableDraftDeclaredPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator.addType|addProp|addFun|addRefFun|addAssociatedIdProp 的属性级 `ImmutableProp` 读取
        // 迁移说明：Draft 顶层接口属性声明、抽象 fun/ref fun 与 associated-id 辅助入口统一前移为本地 metadata，generator 只负责排版
        name = name,
        typeName = toLsiTypeName(),
        isMutable = !isImplementationFormula,
        funReturnTypeName = if ((isAssociation(false) || isList) && !isFormula) {
            toLsiTypeName(draft = true, overrideNullable = false)
        } else {
            null
        },
        refBlockMetadata = if (isAssociation(false) && !isList && !isFormula) {
            draftCallbackMetadata(toLsiTypeName(draft = true, overrideNullable = false))
        } else {
            null
        },
        associatedIdMetadata = toAssociatedIdMetadata(),
    )
}

private fun ImmutableType.toDraftAddFunMetadata(
    withBase: Boolean,
    withBlock: Boolean,
): ImmutableDraftAddFunMetadata =
    ImmutableDraftAddFunMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator.addAddFun 的 `ImmutableType` 级读取
        // 迁移说明：Draft addBy 顶层 helper 所需的 receiver/返回类型、可选 base/block 参数与 producer 类名统一前移为 metadata
        annotationClassName = lsiClassName,
        receiverTypeName = mutableListOfType(lsiDraftClassName),
        baseParameterTypeName = lsiClassName.copyNullable(true).takeIf { withBase },
        blockMetadata = draftCallbackMetadata(lsiDraftClassName).takeIf { withBlock },
        returnTypeName = mutableListOfType(lsiDraftClassName),
        producerClassName = lsiDraftClassName(PRODUCER),
        draftClassName = lsiDraftClassName,
    )

private fun ImmutableType.toDraftNewFunMetadata(
    withCreator: Boolean,
    withBase: Boolean,
    withBlock: Boolean,
): ImmutableDraftNewFunMetadata =
    ImmutableDraftNewFunMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator.addNewByFun 的 `ImmutableType` 级读取
        // 迁移说明：Draft by/TypeName 顶层 helper 所需的函数名、receiver、可选 base/block 参数、返回类型与 producer 类名统一前移为 metadata
        name = if (withCreator) "by" else simpleName,
        annotationClassName = lsiClassName,
        receiverTypeName = LsiParameterizedTypeName(
            rawType = IMMUTABLE_CREATOR_LSI_CLASS_NAME,
            typeArguments = listOf(lsiClassName),
        ).takeIf { withCreator },
        baseParameterTypeName = lsiClassName.copyNullable(true).takeIf { withBase },
        blockMetadata = draftCallbackMetadata(lsiDraftClassName).takeIf { withBlock },
        returnTypeName = lsiClassName,
        producerClassName = lsiDraftClassName(PRODUCER),
    )

private fun ImmutableType.toDraftCopyFunMetadata(): ImmutableDraftCopyFunMetadata =
    ImmutableDraftCopyFunMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator.addCopyFun 的 `ImmutableType` 级读取
        // 迁移说明：Draft copy 顶层 helper 所需的 receiver、返回类型、block 参数与 Draft 类名统一前移为 metadata
        annotationClassName = lsiClassName,
        receiverTypeName = lsiClassName,
        blockMetadata = draftCallbackMetadata(lsiDraftClassName),
        returnTypeName = lsiClassName,
        draftClassName = lsiDraftClassName,
    )

internal fun ImmutableType.toImplTypeMetadata(): ImmutableImplTypeMetadata =
    ImmutableImplTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplGenerator 的 `ImmutableType` 级读取
        // 迁移说明：Impl 生成链的类名、实现接口名、backing field 列表、属性 getter 分支语义、状态机分支、比较/hash 所需属性语义与构造期 visibility 初始化槽位统一前移为本地 metadata，generator 不再直接持有 ImmutableType
        className = lsiClassName,
        implementorClassName = lsiDraftClassName(PRODUCER, IMPLEMENTOR),
        draftProducerImplClassName = lsiDraftClassName(PRODUCER, IMPL),
        draftProducerImplementorClassName = lsiDraftClassName(PRODUCER, IMPLEMENTOR),
        propsSize = properties.size,
        typeDescription = toString(),
        fieldProps = properties.values.map { it.toImplFieldMetadata() },
        getterProps = properties.values.mapNotNull { it.toImplGetterPropMetadata() },
        stateProps = properties.values.map { it.toImplStatePropMetadata() },
        hiddenSlotNames = properties.values
            .filter { it.valueFieldName == null }
            .map { it.slotName },
    )

private fun ImmutableProp.toImplFieldMetadata(): ImmutableImplFieldMetadata =
    ImmutableImplFieldMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplGenerator.addFields 的属性级 `ImmutableProp` 读取
        // 迁移说明：Impl backing field 所需的字段名、字段类型、默认值与 loaded 标记统一前移到 metadata，generator 仅负责成员声明排版
        valueFieldName = valueFieldName,
        valueFieldTypeName = valueFieldName?.let {
            if (isList) {
                LsiParameterizedTypeName(
                    rawType = NON_SHARED_LIST_LSI_CLASS_NAME,
                    typeArguments = listOf(toTargetLsiTypeName()),
                    nullable = true,
                )
            } else {
                toLsiTypeName().copyNullable(!isPrimitive)
            }
        },
        valueFieldDefaultValueKind = valueFieldName?.let {
            if (isPrimitive) {
                ImmutableImplDefaultValueKind.PRIMITIVE_DEFAULT
            } else {
                ImmutableImplDefaultValueKind.NULL
            }
        },
        valueFieldDefaultValueTypeName = valueFieldName?.let {
            if (isPrimitive) {
                toLsiTypeName()
            } else {
                null
            }
        },
        loadedFieldName = loadedFieldName,
    )

private fun ImmutableProp.toImplGetterPropMetadata(): ImmutableImplGetterPropMetadata? {
    if (isImplementationFormula) {
        return null
    }
    return ImmutableImplGetterPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplGenerator.addProp 的属性级 `ImmutableProp` 读取
        // 迁移说明：Impl getter 所需的注释文本、返回类型、id-view/many-to-many-view/普通属性分支判定与错误提示字段统一前移为 metadata，generator 仅负责分支排版
        name = name,
        typeName = toLsiTypeName(),
        description = lsiComment,
        kind = when {
            idViewBaseProp !== null && isList -> ImmutableImplGetterPropKind.ID_VIEW_LIST
            idViewBaseProp !== null -> ImmutableImplGetterPropKind.ID_VIEW_SCALAR
            manyToManyViewBaseProp !== null -> ImmutableImplGetterPropKind.MANY_TO_MANY_VIEW
            else -> ImmutableImplGetterPropKind.STANDARD
        },
        isNullable = isNullable,
        valueFieldName = valueFieldName,
        loadedFieldName = loadedFieldName,
        declaringTypeClassName = declaringType.lsiClassName,
        idViewBaseName = idViewBaseProp?.name,
        idViewBaseTypeName = idViewBaseProp?.toLsiTypeName(),
        idViewBaseTargetProducerClassName = idViewBaseProp?.targetType?.lsiDraftClassName(PRODUCER),
        idViewTargetIdPropName = idViewBaseProp?.targetType?.idProp?.name,
        manyToManyViewBaseName = manyToManyViewBaseProp?.name,
        manyToManyViewBaseTypeName = manyToManyViewBaseProp?.toLsiTypeName(),
        deeperPropConstantName = deeperPropIdPropName(),
    )
}

private fun ImmutableProp.toImplStatePropMetadata(): ImmutableImplStatePropMetadata =
    ImmutableImplStatePropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplGenerator.addCloneFun|addIsLoadedFun|addIsVisibleFun|addHashCodeFun|addEqualsFun 的属性级 `ImmutableProp` 读取
        // 迁移说明：Impl 状态机与比较路径所需的 slot/name、loaded 条件、id/association/nullability 判定、公式依赖链和 id-view/many-to-many 特殊分支统一前移为 metadata，generator 只保留排版
        name = name,
        typeName = toLsiTypeName(),
        slotName = slotName,
        valueFieldName = valueFieldName,
        loadedFieldName = loadedFieldName,
        isNullable = isNullable,
        isAssociation = isAssociation(false),
        isId = isId,
        loadKind = when {
            idViewBaseProp !== null && isList -> ImmutableImplLoadKind.ID_VIEW_LIST
            idViewBaseProp !== null -> ImmutableImplLoadKind.ID_VIEW_SCALAR
            manyToManyViewBaseProp !== null -> ImmutableImplLoadKind.MANY_TO_MANY_VIEW
            isImplementationFormula -> ImmutableImplLoadKind.FORMULA
            else -> ImmutableImplLoadKind.STANDARD
        },
        basePropSlotName = idViewBaseProp?.slotName,
        basePropName = idViewBaseProp?.name ?: manyToManyViewBaseProp?.name,
        basePropTypeName = idViewBaseProp?.toLsiTypeName() ?: manyToManyViewBaseProp?.toLsiTypeName(),
        basePropNullable = idViewBaseProp?.isNullable ?: false,
        baseTargetDraftClassName = idViewBaseProp?.targetType?.lsiDraftClassName(PRODUCER),
        baseTargetIdSlotName = idViewBaseProp?.targetType?.idProp?.slotName,
        deeperPropConstantName = manyToManyViewBaseProp?.let { deeperPropIdPropName() },
        formulaDependencies = dependencies.map { dependency ->
            ImmutableImplLoadDependencyMetadata(
                slotRefs = dependency.props.map { depProp ->
                    ImmutableImplLoadSlotRefMetadata(
                        slotName = depProp.slotName,
                        declaringTypeDraftClassName = depProp.declaringType.lsiDraftClassName(PRODUCER),
                    )
                }
            )
        },
    )

internal fun ImmutableType.toProducerTypeMetadata(): ImmutableProducerTypeMetadata =
    ImmutableProducerTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ProducerGenerator 的 `ImmutableType` 级读取
        // 迁移说明：Producer 生成链所需的类型命名、超类型引用、重定义属性、声明属性、slot 初始化以及下游 implementor/impl/draft-impl 子生成器输入统一前移为本地 metadata，generator 不再直接读取这些 ImmutableType 细节
        className = lsiClassName,
        draftClassName = lsiDraftClassName,
        draftImplClassName = lsiDraftClassName(PRODUCER, DRAFT_IMPL),
        draftCallbackMetadata = draftCallbackMetadata(lsiDraftClassName),
        isMappedSuperclass = isMappedSuperclass,
        superProducerClassNames = superTypes.map { it.lsiDraftClassName(PRODUCER) },
        redefinedProps = redefinedProps.values.map {
            ImmutableProducerRedefinedPropMetadata(
                name = it.name,
                slotName = it.slotName,
            )
        },
        declaredProps = declaredProperties.values.map { it.toProducerPropMetadata(this) },
        slots = properties.values.map { it.toProducerSlotMetadata(this) },
        implementorTypeMetadata = if (isMappedSuperclass) null else toImplementorTypeMetadata(),
        implTypeMetadata = if (isMappedSuperclass) null else toImplTypeMetadata(),
        draftImplTypeMetadata = if (isMappedSuperclass) null else toDraftImplTypeMetadata(),
    )

private fun ImmutableProp.toProducerPropMetadata(
    ownerType: ImmutableType,
): ImmutableProducerPropMetadata =
    ImmutableProducerPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ProducerGenerator.addProp 的属性级 `ImmutableProp` 读取
        // 迁移说明：Producer `add/id/version/key/logicalDeleted` 分派所需的 propId、目标类型、nullable、关联注解类别与属性类别统一前移为 metadata，generator 仅负责排版
        propIdLiteral = if (ownerType.isMappedSuperclass) "-1" else slotName,
        name = name,
        kind = when {
            primaryAnnotationType == Id::class.java -> ImmutableProducerPropKind.ID
            primaryAnnotationType == Version::class.java -> ImmutableProducerPropKind.VERSION
            primaryAnnotationType == LogicalDeleted::class.java -> ImmutableProducerPropKind.LOGICAL_DELETED
            isKey && isAssociation(false) -> ImmutableProducerPropKind.KEY_REFERENCE
            isKey -> ImmutableProducerPropKind.KEY
            primaryAnnotationType == IdView::class.java -> ImmutableProducerPropKind.ID_VIEW
            primaryAnnotationType != null &&
                primaryAnnotationType != Formula::class.java &&
                primaryAnnotationType != Transient::class.java -> ImmutableProducerPropKind.SQL
            else -> ImmutableProducerPropKind.ADD
        },
        targetClassName = targetLsiClassName,
        isNullable = isNullable,
        categoryName = when {
            primaryAnnotationType == IdView::class.java ->
                if (isList) {
                    "SCALAR_LIST"
                } else {
                    "SCALAR"
                }
            primaryAnnotationType == null ||
                primaryAnnotationType == Formula::class.java ||
                primaryAnnotationType == Transient::class.java ->
                when {
                    isList && isAssociation(false) -> "REFERENCE_LIST"
                    isList && !isAssociation(false) -> "SCALAR_LIST"
                    isAssociation(false) -> "REFERENCE"
                    else -> "SCALAR"
                }
            else -> null
        },
        annotationClassName = when {
            isKey && isAssociation(false) ->
                if (lsiAnnotation(OneToOne::class) !== null) {
                    ONE_TO_ONE_LSI_CLASS_NAME
                } else {
                    MANY_TO_ONE_LSI_CLASS_NAME
                }
            primaryAnnotationType == OneToOne::class.java -> ONE_TO_ONE_LSI_CLASS_NAME
            primaryAnnotationType == ManyToOne::class.java -> MANY_TO_ONE_LSI_CLASS_NAME
            primaryAnnotationType == OneToMany::class.java -> ONE_TO_MANY_LSI_CLASS_NAME
            primaryAnnotationType == ManyToMany::class.java -> MANY_TO_MANY_LSI_CLASS_NAME
            primaryAnnotationType == ManyToManyView::class.java -> MANY_TO_MANY_VIEW_LSI_CLASS_NAME
            primaryAnnotationType != null &&
                primaryAnnotationType != Formula::class.java &&
                primaryAnnotationType != Transient::class.java ->
                error("Internal bug: $this has wrong sql annotation @${primaryAnnotationType?.name}")
            else -> null
        },
    )

private fun ImmutableProp.toProducerSlotMetadata(
    ownerType: ImmutableType,
): ImmutableProducerSlotMetadata =
    ImmutableProducerSlotMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ProducerGenerator.addSlots 的属性级 `ImmutableProp` 读取
        // 迁移说明：Producer slot 常量初始化来源（本类型 id 或继承自上游 Producer 类）前移到 metadata 层，generator 只保留常量声明排版
        slotName = slotName,
        localId = if (declaringType == ownerType || declaringType.isMappedSuperclass) {
            id
        } else {
            null
        },
        inheritedOwnerProducerClassName = if (declaringType == ownerType || declaringType.isMappedSuperclass) {
            null
        } else {
            declaringType.lsiDraftClassName(PRODUCER)
        },
    )

internal fun ImmutableType.toDraftImplTypeMetadata(): ImmutableDraftImplTypeMetadata =
    ImmutableDraftImplTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator 的 `ImmutableType` 级读取
        // 迁移说明：DraftImpl 类头、成员遍历、resolve 路径、类型级 validator 与 companion 所需 validation 常量统一前移为本地 metadata，generator 不再直接持有 ImmutableType
        className = lsiClassName,
        draftProducerImplementorClassName = lsiDraftClassName(PRODUCER, IMPLEMENTOR),
        draftClassName = lsiDraftClassName,
        draftProducerImplClassName = lsiDraftClassName(PRODUCER, IMPL),
        members = properties.values.map { it.toDraftImplMemberMetadata() },
        dispatchType = toDraftImplDispatchTypeMetadata(),
        resolveProps = properties.values.mapNotNull { it.toDraftImplResolvePropMetadata() },
        typeValidators = validationMessages.map { (className, message) ->
            ImmutableDraftImplTypeValidatorMetadata(
                className = className,
                message = message,
            )
        },
        validationProps = properties.values.map { it.toValidationPropMetadata() },
    )

private fun ImmutableProp.toDraftImplMemberMetadata(): ImmutableDraftImplMemberMetadata =
    ImmutableDraftImplMemberMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.generate 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl 单属性链路上的 property/propFun/propRef/associatedId 四类生成入口统一并拢为本地 member metadata，generator 只保留顺序排版
        property = toDraftImplPropertyMetadata(),
        propFun = toDraftImplPropFunMetadata(),
        propRefFun = toDraftImplPropRefMetadata(),
        associatedId = toAssociatedIdMetadata(),
    )

private fun ImmutableProp.toDraftImplResolvePropMetadata(): ImmutableDraftImplResolvePropMetadata? {
    val valueFieldName = valueFieldName ?: return null
    val baseResolveKind = when {
        isAssociation(false) || isList ->
            if (isList || isScalarList) {
                ImmutableDraftImplResolveKind.LIST
            } else {
                ImmutableDraftImplResolveKind.OBJECT
            }
        else -> ImmutableDraftImplResolveKind.NONE
    }
    val modifiedResolveKind = when {
        isList -> ImmutableDraftImplResolveKind.LIST
        isReference -> ImmutableDraftImplResolveKind.OBJECT
        else -> ImmutableDraftImplResolveKind.NONE
    }
    if (baseResolveKind == ImmutableDraftImplResolveKind.NONE &&
        modifiedResolveKind == ImmutableDraftImplResolveKind.NONE
    ) {
        return null
    }
    return ImmutableDraftImplResolvePropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addResolveFun 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl resolve 阶段所需的 propId、backing field 与 list/object 解析分支统一前移为 metadata，generator 不再直接读取 ImmutableProp 语义
        name = name,
        slotName = slotName,
        valueFieldName = valueFieldName,
        baseResolveKind = baseResolveKind,
        modifiedResolveKind = modifiedResolveKind,
    )
}

internal fun ImmutableProp.toDraftImplPropertyMetadata(): ImmutableDraftImplPropertyMetadata =
    ImmutableDraftImplPropertyMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addProp 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl 属性声明与 getter/setter 分支所需的可变性、返回类型、id-view/list/object/passthrough 判定、Validation 入口和底层 modified-field 写回信息统一前移为 metadata
        name = name,
        typeName = toLsiTypeName(),
        isMutable = manyToManyViewBaseProp === null && !isImplementationFormula,
        getterKind = when {
            idViewBaseProp !== null && isList -> ImmutableDraftImplPropertyGetterKind.ID_VIEW_LIST
            isList || isScalarList -> ImmutableDraftImplPropertyGetterKind.DRAFT_LIST
            isReference -> ImmutableDraftImplPropertyGetterKind.DRAFT_OBJECT
            else -> ImmutableDraftImplPropertyGetterKind.PASSTHROUGH
        },
        setterKind = when {
            manyToManyViewBaseProp !== null || isImplementationFormula -> ImmutableDraftImplPropertySetterKind.NONE
            idViewBaseProp?.let { it.isList || it.isNullable } == true -> ImmutableDraftImplPropertySetterKind.ID_VIEW_TRANSFORM
            idViewBaseProp !== null -> ImmutableDraftImplPropertySetterKind.ID_VIEW_DIRECT
            else -> ImmutableDraftImplPropertySetterKind.STANDARD
        },
        idViewBaseName = idViewBaseProp?.name,
        idViewBaseTypeName = idViewBaseProp?.toLsiTypeName(),
        idViewBaseTargetProducerClassName = idViewBaseProp?.targetType?.lsiDraftClassName(PRODUCER),
        idViewBaseNullable = idViewBaseProp?.isNullable == true,
        idViewBaseList = idViewBaseProp?.isList == true,
        draftListElementTypeName = if (isList || isScalarList) {
            toTargetLsiTypeName()
        } else {
            null
        },
        draftListAssociation = isAssociation(false),
        validationPropMetadata = if (manyToManyViewBaseProp === null && !isImplementationFormula && idViewBaseProp == null) {
            toValidationPropMetadata()
        } else {
            null
        },
        modifiedValueFieldName = valueFieldName,
        modifiedLoadedFieldName = loadedFieldName,
        copyToNonSharedList = isList || isScalarList,
    )

internal fun ImmutableType.toDraftImplDispatchTypeMetadata(): ImmutableDraftImplDispatchTypeMetadata =
    ImmutableDraftImplDispatchTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addUnloadFun|addSetFun|addShowFun 的 `ImmutableType` 级读取
        // 迁移说明：DraftImpl unload/set/show 三个 dispatch 分派所需的属性顺序、类型描述与 visibility 大小统一前移为 metadata，generator 仅负责排版
        propsSize = properties.size,
        typeDescription = toString(),
        props = propsOrderById.map { it.toDraftImplDispatchPropMetadata() },
    )

private fun ImmutableProp.toDraftImplDispatchPropMetadata(): ImmutableDraftImplDispatchPropMetadata =
    ImmutableDraftImplDispatchPropMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addUnloadFun|addSetFun|addShowFun 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl dispatch 所需的 slot/name、base-prop 卸载代理、readonly 判定、赋值类型与默认卸载值统一前移为 metadata，generator 不再直接读取这些属性语义
        name = name,
        slotName = slotName,
        unloadKind = when {
            baseProp !== null -> ImmutableDraftImplUnloadKind.DELEGATE_BASE
            isImplementationFormula -> ImmutableDraftImplUnloadKind.NO_OP
            loadedFieldName !== null -> ImmutableDraftImplUnloadKind.RESET_LOADED
            else -> ImmutableDraftImplUnloadKind.RESET_VALUE
        },
        basePropSlotName = baseProp?.slotName,
        valueFieldName = valueFieldName,
        loadedFieldName = loadedFieldName,
        unloadValueKind = when {
            loadedFieldName == null -> null
            isPrimitive -> ImmutableDraftImplUnloadValueKind.PRIMITIVE_DEFAULT
            else -> ImmutableDraftImplUnloadValueKind.NULL
        },
        unloadValueTypeName = toLsiTypeName().takeIf { loadedFieldName != null && isPrimitive },
        setKind = if (isImplementationFormula || manyToManyViewBaseProp != null) {
            ImmutableDraftImplSetKind.READ_ONLY
        } else {
            ImmutableDraftImplSetKind.ASSIGN
        },
        setTypeName = if (isImplementationFormula || manyToManyViewBaseProp != null) {
            null
        } else {
            toLsiTypeName(overrideNullable = false)
        },
        isNullable = isNullable,
    )

internal fun ImmutableProp.toDraftImplPropFunMetadata(): ImmutableDraftImplPropFunMetadata? {
    if ((!isAssociation(false) && !isList) || manyToManyViewBaseProp != null || isFormula) {
        return null
    }
    return ImmutableDraftImplPropFunMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addPropFun 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl 关联/集合辅助访问器所需的 propId、返回类型、初始化分支与目标 Producer 类型统一前移为 metadata，generator 仅负责排版
        name = name,
        slotName = slotName,
        returnTypeName = toLsiTypeName(draft = true, overrideNullable = false),
        castTypeName = toLsiTypeName(true, overrideNullable = false),
        isNullable = isNullable,
        isList = isList,
        targetProducerClassName = targetType?.lsiDraftClassName(PRODUCER),
    )
}

internal fun ImmutableProp.toDraftImplPropRefMetadata(): ImmutableDraftImplPropRefMetadata? {
    if (!isAssociation(false) || isList || isFormula) {
        return null
    }
    return ImmutableDraftImplPropRefMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftImplGenerator.addPropRefFun 的属性级 `ImmutableProp` 读取
        // 迁移说明：DraftImpl 引用型 block 访问器所需的属性名与 block 参数类型统一前移为 metadata，generator 仅负责排版
        name = name,
        blockMetadata = draftCallbackMetadata(toLsiTypeName(draft = true, overrideNullable = false)),
    )
}

private fun mutableListOfType(elementType: LsiTypeName): LsiParameterizedTypeName =
    LsiParameterizedTypeName(
        rawType = LsiClassName.bestGuess("kotlin.collections.MutableList"),
        typeArguments = listOf(elementType),
    )
