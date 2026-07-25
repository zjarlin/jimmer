package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoDraftWritePoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDraftWritePoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO Draft 写回语义渲染为 JavaPoet 代码。 */
internal object AptDtoDraftWriteRenderer {

    @JvmStatic
    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        accessorName: String,
        draftName: String,
        valueName: String,
    ): CodeBlock {
        val codeBlock = prop.toDraftWritePoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
            accessorName = accessorName,
            draftName = draftName,
            valueName = valueName,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoDraftWritePoetTypeNames(codeBlock),
        )
    }
}
