package site.addzero.lsi.frontend

import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.mergeLsiTypeSeeds

data class LsiTypeSeedFixedPointResult(
    val workspace: LsiWorkspace,
    val seeds: List<LsiTypeSeed>,
    val iterations: Int,
)

class LsiTypeSeedFixedPointException(
    val maximumIterations: Int,
    val seeds: List<LsiTypeSeed>,
) : IllegalStateException(
    "LSI type seed requests did not reach a fixed point after $maximumIterations iterations",
)

/**
 * 在一个有效平台轮内累计类型声明请求，并以纯 LSI 工作区判断固定点。
 */
fun resolveLsiTypeSeedFixedPoint(
    initialWorkspace: LsiWorkspace,
    maximumIterations: Int = 64,
    requestSeeds: (LsiWorkspace) -> Collection<LsiTypeSeed>,
    freezeWorkspace: (List<LsiTypeSeed>) -> LsiWorkspace,
): LsiTypeSeedFixedPointResult {
    require(maximumIterations >= 1) {
        "LSI type seed maximum fixed point iterations must be positive: $maximumIterations"
    }
    var workspace = initialWorkspace
    var seeds = emptyList<LsiTypeSeed>()
    repeat(maximumIterations) { iteration ->
        val nextSeeds = (seeds + requestSeeds(workspace)).mergeLsiTypeSeeds()
        if (nextSeeds == seeds) {
            return LsiTypeSeedFixedPointResult(
                workspace = workspace,
                seeds = seeds,
                iterations = iteration + 1,
            )
        }
        seeds = nextSeeds
        workspace = freezeWorkspace(seeds)
    }
    throw LsiTypeSeedFixedPointException(maximumIterations, seeds)
}
