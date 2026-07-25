package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toBaseLoadedStateInitializerPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

internal object KspDtoLoadedStateRenderer {

    fun renderBaseInitializer(
        prop: DtoBaseProp,
        graph: DtoGraph,
        accessorName: String,
        baseParameterName: String,
    ): CodeBlock? {
        val codeBlock = prop.toBaseLoadedStateInitializerPoetCodeBlock(
            graph = graph,
            targetLanguage = LsiLanguage.KOTLIN,
            accessorName = accessorName,
            baseParameterName = baseParameterName,
        ) ?: return null
        return LsiKotlinPoetRenderer().renderCodeBlock(codeBlock, emptyList())
    }
}
