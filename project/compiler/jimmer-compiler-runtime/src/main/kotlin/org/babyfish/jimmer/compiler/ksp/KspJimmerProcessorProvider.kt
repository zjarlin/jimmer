package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * 聚合所有编译功能的通用 KSP 生命周期入口。
 */
open class KspJimmerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val lsiDriver = KspLsiCompilerDriver(environment)
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> = lsiDriver.process(resolver)

            override fun finish() {
                lsiDriver.finish()
            }
        }
    }
}
