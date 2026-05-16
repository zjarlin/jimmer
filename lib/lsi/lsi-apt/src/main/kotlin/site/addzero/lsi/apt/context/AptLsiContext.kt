package site.addzero.lsi.apt.context

import site.addzero.lsi.apt.codegen.AptLsiFiler
import site.addzero.lsi.apt.environment.AptLsiEnvironment
import site.addzero.lsi.apt.resolver.toLsiResolver
import site.addzero.lsi.context.LsiContext
import site.addzero.lsi.resolver.LsiResolver
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.util.Elements

object AptLsiContext : LsiContext {
    lateinit var processingEnvironment: ProcessingEnvironment
        private set

    lateinit var roundEnvironment: RoundEnvironment
        private set

    private var environmentOverride: AptLsiEnvironment? = null

    private var resolverOverride: LsiResolver? = null

    val environment: AptLsiEnvironment
        get() = environmentOverride ?: AptLsiEnvironment(processingEnvironment)

    val options: Map<String, String>
        get() = processingEnvironment.options

    val elements: Elements
        get() = processingEnvironment.elementUtils

    val lsiFiler: AptLsiFiler
        get() = AptLsiFiler(processingEnvironment)

    val lsiResolver: LsiResolver
        get() = resolverOverride ?: roundEnvironment.toLsiResolver(processingEnvironment)

    fun init(
        processingEnvironment: ProcessingEnvironment,
        environment: AptLsiEnvironment?
    ) {
        this.processingEnvironment = processingEnvironment
        this.environmentOverride = environment
    }

    fun resetRound(
        roundEnvironment: RoundEnvironment,
        lsiResolver: LsiResolver?
    ) {
        this.roundEnvironment = roundEnvironment
        this.resolverOverride = lsiResolver
    }
}
