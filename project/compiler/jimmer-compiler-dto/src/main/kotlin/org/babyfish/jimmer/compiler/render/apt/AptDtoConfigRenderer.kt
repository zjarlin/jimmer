package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoConfigPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toConfigPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 属性配置渲染为可嵌入 APT DTO 的 JavaPoet 代码块。 */
internal object AptDtoConfigRenderer {

    @JvmStatic
    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        configContractResolution: DtoConfigContractResolution,
    ): CodeBlock {
        val codeBlock = prop.toConfigPoetCodeBlock(
            targetLanguage = LsiLanguage.JAVA,
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            configContractResolution = configContractResolution,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoConfigPoetTypeNames(codeBlock),
        )
    }
}
