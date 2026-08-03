package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoDraftWritePoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDraftWritePoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO Draft 写回语义渲染为 KotlinPoet 代码。 */
internal object KspDtoDraftWriteRenderer {

    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        accessorName: String,
        draftName: String,
        valueName: String,
        baseValueWriterName: String,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
    ): CodeBlock {
        val codeBlock = prop.toDraftWritePoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
            accessorName = accessorName,
            draftName = draftName,
            valueName = valueName,
            baseValueWriterName = baseValueWriterName,
            generatedTargetType = generatedTargetType,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoDraftWritePoetTypeNames(codeBlock),
        )
    }
}
