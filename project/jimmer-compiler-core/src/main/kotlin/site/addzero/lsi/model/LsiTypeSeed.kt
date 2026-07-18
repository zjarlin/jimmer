package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

enum class LsiTypeSeedMode {
    HEADER,
    FULL_DECLARATION,
}

/**
 * 请求平台前端在当前有效轮冻结的类型入口。
 */
data class LsiTypeSeed(
    val typeId: LsiSymbolId,
    val mode: LsiTypeSeedMode,
) : Comparable<LsiTypeSeed> {

    init {
        typeId.requireTypeQualifiedName()
    }

    override fun compareTo(other: LsiTypeSeed): Int {
        val typeComparison = typeId.compareTo(other.typeId)
        if (typeComparison != 0) {
            return typeComparison
        }
        return mode.compareTo(other.mode)
    }
}

fun Iterable<LsiTypeSeed>.mergeLsiTypeSeeds(): List<LsiTypeSeed> {
    return groupBy(LsiTypeSeed::typeId)
        .map { (typeId, seeds) ->
            LsiTypeSeed(
                typeId = typeId,
                mode = if (seeds.any { seed -> seed.mode == LsiTypeSeedMode.FULL_DECLARATION }) {
                    LsiTypeSeedMode.FULL_DECLARATION
                } else {
                    LsiTypeSeedMode.HEADER
                },
            )
        }
        .sorted()
}
