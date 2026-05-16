package site.addzero.lsi.jimmer.immutable.metadata.extractor

import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorDeepPropIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorPropCaseMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType

fun ImmutableType.toImplementorTypeMetadata(): ImmutableImplementorTypeMetadata =
    ImmutableImplementorTypeMetadata(
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplementorGenerator 的 `ImmutableType` 级读取
        // 迁移说明：Implementor 生成链所需的类型命名、属性顺序、`__get` 分派 case 与 deep-prop companion 常量前移到 metadata-extractor，generator 不再自行投影这部分 ImmutableType 语义
        className = lsiClassName,
        producerClassName = lsiDraftClassName(PRODUCER),
        typeDescription = toString(),
        propertyOrderNames = propsOrderById.map { it.name },
        getCases = propsOrderById.map {
            ImmutableImplementorPropCaseMetadata(
                name = it.name,
                slotName = it.slotName,
            )
        },
        deeperPropIds = properties.values.mapNotNull { prop ->
            prop.deeperPropIdPropName()?.let { constantName ->
                ImmutableImplementorDeepPropIdMetadata(
                    constantName = constantName,
                    propName = prop.name,
                )
            }
        },
    )

fun ImmutableProp.deeperPropIdPropName(): String? =
    manyToManyViewBaseDeeperProp?.let {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImplementorGenerator.deeperPropIdPropName
        // 迁移说明：many-to-many view deeper-prop 常量命名 helper 前移到 extractor，供 implementor/impl 两条 metadata 投影链共享
        "DEEP_PROP_ID_" + StringUtil.snake(name, SnakeCase.UPPER)
    }
