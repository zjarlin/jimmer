package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoPolymorphicInputPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toTypedPolymorphicInputDiscriminatorValidationPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的多态输入语义渲染为可嵌入 KSP DTO 的 KotlinPoet 代码块。 */
internal object KspDtoPolymorphicInputRenderer {

    fun renderTypedDiscriminatorValidation(
        dtoType: DtoType,
        branch: DtoPolymorphicBranch,
        discriminatorProp: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        generatedPackageName: String,
        generatedSimpleNames: List<String>,
    ): CodeBlock {
        val generatedDtoTypeName = JimmerDtoPoetTypeNames.create(
            packageName = generatedPackageName,
            simpleNames = generatedSimpleNames,
        )
        val codeBlock = dtoType.toTypedPolymorphicInputDiscriminatorValidationPoetCodeBlock(
            targetLanguage = LsiLanguage.KOTLIN,
            branch = branch,
            discriminatorProp = discriminatorProp,
            graph = graph,
            immutableSchema = immutableSchema,
            generatedDtoTypeName = generatedDtoTypeName,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoPolymorphicInputPoetTypeNames(codeBlock),
        )
    }
}
