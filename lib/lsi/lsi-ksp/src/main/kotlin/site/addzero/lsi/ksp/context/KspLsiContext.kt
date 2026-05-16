package site.addzero.lsi.ksp.context

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import site.addzero.lsi.context.LsiContext

/**
 * KSP 场景的 LSI 全局上下文。
 *
 * - [init] 用于初始化生命周期内稳定对象（environment/filer）。
 * - [resetRound] 必须每轮调用，用最新 resolver 刷新轮次态对象。
 */
object KspLsiContext : LsiContext {
    lateinit var resolver: Resolver
    lateinit var environment: SymbolProcessorEnvironment

    fun init(
        environment: SymbolProcessorEnvironment,
    ) {
        this.environment = environment
    }

    fun resetRound(
        resolver: Resolver,
    ) {
        this.resolver = resolver
    }
}
