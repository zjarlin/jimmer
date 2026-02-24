package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import site.addzero.context.SettingContext
import site.addzero.context.Settings
import java.util.*

class JimmerProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private var currentPhase = 0

    private val spis: List<ProcessorSpi<*, *>> by lazy {
        ServiceLoader.load(ProcessorSpi::class.java, JimmerProcessor::class.java.classLoader)
            .sortedWith(compareBy({ it.phase }, { it.order }))
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        Settings.fromOptions(environment.options)
        Context.resolver = resolver
        Context.environment = environment
        currentPhase++

        return try {
            val roundSpis = spis.filter { it.phase == currentPhase }
            if (roundSpis.isEmpty()) return emptyList()

            val deferred = mutableListOf<KSAnnotated>()
            var anyGenerated = false

            for (spi in roundSpis) {
                val result = spi.process()
                when (result) {
                    is Collection<*> -> {
                        val ksAnnotated = result.filterIsInstance<KSAnnotated>()
                        deferred += ksAnnotated
                        if (ksAnnotated.isNotEmpty()) anyGenerated = true
                    }
                    is Boolean -> if (result) anyGenerated = true
                }
            }

            if (currentPhase == 1 && anyGenerated) {
                Context.snapshotAllTypeNames()
            }

            deferred
        } catch (ex: MetaException) {
            environment.logger.error(ex.message!!, ex.declaration)
            emptyList()
        } catch (ex: DtoAstException) {
            environment.logger.error(ex.message!!)
            emptyList()
        }
    }
}
