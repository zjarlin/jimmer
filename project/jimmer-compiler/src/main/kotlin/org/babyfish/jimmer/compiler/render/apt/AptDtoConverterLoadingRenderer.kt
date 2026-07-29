package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.DTO_COMMON_POET_TYPE_NAMES
import org.babyfish.jimmer.compiler.dto.toLsiConverterLoadingPoetCodeBlock
import org.babyfish.jimmer.compiler.immutable.toLsiGeneratedQueryPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO converter 加载表达式渲染为 JavaPoet 代码块。 */
internal object AptDtoConverterLoadingRenderer {

    @JvmStatic
    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        forList: Boolean,
    ): CodeBlock {
        val codeBlock = prop.toLsiConverterLoadingPoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
            forList = forList,
            typeArguments = emptyList(),
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = DTO_COMMON_POET_TYPE_NAMES + immutableSchema.toLsiGeneratedQueryPoetTypeNames(),
        )
    }
}
