package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.DTO_COMMON_POET_TYPE_NAMES
import org.babyfish.jimmer.compiler.dto.toLsiConverterLoadingPoetCodeBlock
import org.babyfish.jimmer.compiler.immutable.toLsiGeneratedQueryPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO converter 加载表达式渲染为 KotlinPoet 代码块。 */
internal object KspDtoConverterLoadingRenderer {

    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        forList: Boolean,
    ): CodeBlock {
        val codeBlock = prop.toLsiConverterLoadingPoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
            forList = forList,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = DTO_COMMON_POET_TYPE_NAMES + immutableSchema.toLsiGeneratedQueryPoetTypeNames(),
        )
    }
}
