package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoProp
import org.babyfish.jimmer.dto.compiler.PropConfig
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType

internal class LsiDtoPropConfigView internal constructor(
    internal val rawPropConfig: PropConfig<ImmutableProp>,
) {
    val predicate: LsiDtoPredicateView?
        get() = rawPropConfig.predicate?.toLsiDtoPredicateView()

    val orderItems: List<LsiDtoOrderItemView>
        get() = rawPropConfig.orderItems.map(::LsiDtoOrderItemView)

    val filterClassName: String?
        get() = rawPropConfig.filterClassName

    val recursionClassName: String?
        get() = rawPropConfig.recursionClassName

    val fetchType: String
        get() = rawPropConfig.fetchType

    val limit: Int
        get() = rawPropConfig.limit

    val offset: Int
        get() = rawPropConfig.offset

    val batch: Int
        get() = rawPropConfig.batch

    val depth: Int
        get() = rawPropConfig.depth
}

internal sealed interface LsiDtoPredicateView

internal class LsiDtoAndPredicateView internal constructor(
    val predicates: List<LsiDtoPredicateView>,
) : LsiDtoPredicateView

internal class LsiDtoOrPredicateView internal constructor(
    val predicates: List<LsiDtoPredicateView>,
) : LsiDtoPredicateView

internal class LsiDtoCmpPredicateView internal constructor(
    val path: List<LsiDtoPathNodeView>,
    val operator: String,
    val value: Any?,
) : LsiDtoPredicateView

internal class LsiDtoNullityPredicateView internal constructor(
    val path: List<LsiDtoPathNodeView>,
    val isNegative: Boolean,
) : LsiDtoPredicateView

internal class LsiDtoOrderItemView internal constructor(
    internal val rawOrderItem: PropConfig.OrderItem<ImmutableProp>,
) {
    val path: List<LsiDtoPathNodeView>
        get() = rawOrderItem.path.map(::LsiDtoPathNodeView)

    val isDesc: Boolean
        get() = rawOrderItem.isDesc
}

internal class LsiDtoPathNodeView internal constructor(
    internal val rawPathNode: PropConfig.PathNode<ImmutableProp>,
) {
    val prop: ImmutableProp
        get() = rawPathNode.prop

    val isAssociatedId: Boolean
        get() = rawPathNode.isAssociatedId
}

internal val DtoProp<ImmutableType, ImmutableProp>.lsiConfigView: LsiDtoPropConfigView?
    get() = getConfig()?.toLsiDtoPropConfigView()

internal fun PropConfig<ImmutableProp>.toLsiDtoPropConfigView(): LsiDtoPropConfigView =
    LsiDtoPropConfigView(this)

@Suppress("UNCHECKED_CAST")
private fun PropConfig.Predicate.toLsiDtoPredicateView(): LsiDtoPredicateView =
    when (this) {
        is PropConfig.Predicate.And -> LsiDtoAndPredicateView(predicates.map { it.toLsiDtoPredicateView() })
        is PropConfig.Predicate.Or -> LsiDtoOrPredicateView(predicates.map { it.toLsiDtoPredicateView() })
        is PropConfig.Predicate.Cmp<*> -> LsiDtoCmpPredicateView(
            path = (path as List<PropConfig.PathNode<ImmutableProp>>).map(::LsiDtoPathNodeView),
            operator = operator,
            value = value,
        )
        is PropConfig.Predicate.Nullity<*> -> LsiDtoNullityPredicateView(
            path = (path as List<PropConfig.PathNode<ImmutableProp>>).map(::LsiDtoPathNodeView),
            isNegative = isNegative,
        )
        else -> error("Unsupported predicate type: ${this::class.qualifiedName}")
    }
