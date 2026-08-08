package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.compiler.CompilerInputDocumentKind
import site.addzero.lsi.compiler.CompilerInputDocumentProvider
import site.addzero.lsi.compiler.CompilerWiring
import site.addzero.lsi.jimmer.toJimmerLsiFrontendOptions
import site.addzero.lsi.model.LsiFrontendOptions

/**
 * 将 Jimmer 前端约定注入通用 LSI 编译流程。
 */
object JimmerCompilerWiring : CompilerWiring {

    override fun frontendOptions(options: Map<String, String>): LsiFrontendOptions {
        return options.toJimmerLsiFrontendOptions()
    }

    override fun inputDocumentProvider(
        kinds: Set<CompilerInputDocumentKind>,
        options: Map<String, String>,
    ): CompilerInputDocumentProvider {
        return CompilerInputDocumentScanner(kinds, options)
    }
}
