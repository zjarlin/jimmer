package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoPolymorphicMetadataConverterPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toPolymorphicMetadataConverterPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的多态 DTO 转换规则渲染为可嵌入 KSP DTO 的 KotlinPoet 代码块。 */
internal object KspDtoPolymorphicMetadataConverterRenderer {

    fun appendTo(
        builder: CodeBlock.Builder,
        dtoType: DtoType,
        graph: DtoGraph,
        workspace: LsiWorkspace,
        generatedPackageName: String,
        generatedRootSimpleNames: List<String>,
    ) {
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            packageName = generatedPackageName,
            simpleNames = generatedRootSimpleNames,
        )
        val codeBlock = dtoType.toPolymorphicMetadataConverterPoetCodeBlock(
            targetLanguage = LsiLanguage.KOTLIN,
            graph = graph,
            generatedRootTypeName = generatedRootTypeName,
        )
        LsiKotlinPoetRenderer().appendCodeBlock(
            builder = builder,
            codeBlock = codeBlock,
            typeNames = workspace.dtoPolymorphicMetadataConverterPoetTypeNames(
                dtoType = dtoType,
                codeBlock = codeBlock,
                generatedRootTypeName = generatedRootTypeName,
            ),
        )
    }
}
