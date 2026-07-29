package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import org.babyfish.jimmer.compiler.dto.DTO_LOADED_STATE_POET_TYPE_NAMES
import org.babyfish.jimmer.compiler.dto.toBaseLoadedStateInitializerPoetCodeBlock
import org.babyfish.jimmer.compiler.dto.toLoadedStateStoragePoetPropertyOrNull
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

internal object KspDtoLoadedStateRenderer {

    /** 将冻结的加载状态语义渲染为 Kotlin DTO 属性。 */
    fun renderStorageProperty(
        prop: DtoProp,
        graph: DtoGraph,
        mutable: Boolean,
    ): PropertySpec? {
        val property = prop.toLoadedStateStoragePoetPropertyOrNull(
            graph = graph,
            mutable = mutable,
        ) ?: return null
        return LsiKotlinPoetRenderer().renderProperty(
            property = property,
            typeNames = DTO_LOADED_STATE_POET_TYPE_NAMES,
        )
    }

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
