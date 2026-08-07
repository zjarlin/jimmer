package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoPolymorphicMetadataConverterPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toPolymorphicMetadataConverterPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的多态 DTO 转换规则渲染为可嵌入 APT DTO 的 JavaPoet 代码块。 */
internal object AptDtoPolymorphicMetadataConverterRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        workspace: LsiWorkspace,
        generatedPackageName: String,
        generatedRootSimpleNames: List<String>,
    ): CodeBlock {
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            packageName = generatedPackageName,
            simpleNames = generatedRootSimpleNames,
        )
        val codeBlock = dtoType.toPolymorphicMetadataConverterPoetCodeBlock(
            targetLanguage = LsiLanguage.JAVA,
            graph = graph,
            generatedRootTypeName = generatedRootTypeName,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoPolymorphicMetadataConverterPoetTypeNames(
                dtoType = dtoType,
                codeBlock = codeBlock,
                generatedRootTypeName = generatedRootTypeName,
            ),
        )
    }
}
