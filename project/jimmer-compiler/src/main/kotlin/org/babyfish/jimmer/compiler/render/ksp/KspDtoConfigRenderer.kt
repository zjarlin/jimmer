package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoConfigPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toConfigPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 属性配置渲染为可嵌入 KSP DTO 的 KotlinPoet 代码块。 */
internal object KspDtoConfigRenderer {

    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        configContractResolution: DtoConfigContractResolution,
    ): CodeBlock {
        val codeBlock = prop.toConfigPoetCodeBlock(
            targetLanguage = LsiLanguage.KOTLIN,
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            configContractResolution = configContractResolution,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoConfigPoetTypeNames(codeBlock),
        )
    }
}
