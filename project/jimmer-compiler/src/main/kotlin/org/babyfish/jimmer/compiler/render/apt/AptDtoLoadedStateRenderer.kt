package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toBaseLoadedStateInitializerPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

internal object AptDtoLoadedStateRenderer {

    @JvmStatic
    fun renderBaseInitializer(
        prop: DtoBaseProp,
        graph: DtoGraph,
        accessorName: String,
        baseParameterName: String,
    ): CodeBlock? {
        val codeBlock = prop.toBaseLoadedStateInitializerPoetCodeBlock(
            graph = graph,
            targetLanguage = LsiLanguage.JAVA,
            accessorName = accessorName,
            baseParameterName = baseParameterName,
        ) ?: return null
        return LsiJavaPoetRenderer().renderCodeBlock(codeBlock, emptyList())
    }
}
