package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoEnumPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toEnumToScalarLambdaPoetCodeBlock
import org.babyfish.jimmer.compiler.dto.toScalarToEnumLambdaPoetCodeBlock
import org.babyfish.jimmer.compiler.dto.toScalarToEnumPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.scalarType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 枚举转换渲染为可嵌入 KSP DTO 的 KotlinPoet 结构。 */
internal object KspDtoEnumRenderer {

    fun renderEnumToScalarLambda(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): CodeBlock = render(
        prop.toEnumToScalarLambdaPoetCodeBlock(LsiLanguage.KOTLIN, graph, immutableSchema),
        workspace,
    )

    fun appendEnumToScalarLambda(
        builder: CodeBlock.Builder,
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ) {
        append(
            builder,
            prop.toEnumToScalarLambdaPoetCodeBlock(LsiLanguage.KOTLIN, graph, immutableSchema),
            workspace,
        )
    }

    fun renderScalarToEnumLambda(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): CodeBlock = render(
        prop.toScalarToEnumLambdaPoetCodeBlock(LsiLanguage.KOTLIN, graph, immutableSchema),
        workspace,
    )

    fun appendScalarToEnumLambda(
        builder: CodeBlock.Builder,
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ) {
        append(
            builder,
            prop.toScalarToEnumLambdaPoetCodeBlock(LsiLanguage.KOTLIN, graph, immutableSchema),
            workspace,
        )
    }

    fun renderScalarToEnumConversion(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        variableName: String,
    ): CodeBlock = render(
        prop.toScalarToEnumPoetCodeBlock(
            LsiLanguage.KOTLIN,
            graph,
            immutableSchema,
            variableName,
        ),
        workspace,
    )

    fun appendScalarToEnumConversion(
        builder: CodeBlock.Builder,
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        variableName: String,
    ) {
        append(
            builder,
            prop.toScalarToEnumPoetCodeBlock(
                LsiLanguage.KOTLIN,
                graph,
                immutableSchema,
                variableName,
            ),
            workspace,
        )
    }

    fun renderScalarType(prop: DtoBaseProp, workspace: LsiWorkspace): TypeName {
        val enumType = requireNotNull(prop.enumType) {
            "DTO enum scalar type requires an enum mapping: ${prop.id.value}"
        }
        return KspDtoTypeRefRenderer
            .render(enumType.scalarType(LsiLanguage.KOTLIN), workspace)
            .copy(nullable = prop.nullable)
    }

    private fun render(codeBlock: LsiPoetCodeBlock, workspace: LsiWorkspace): CodeBlock {
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoEnumPoetTypeNames(codeBlock),
        )
    }

    private fun append(
        builder: CodeBlock.Builder,
        codeBlock: LsiPoetCodeBlock,
        workspace: LsiWorkspace,
    ) {
        LsiKotlinPoetRenderer().appendCodeBlock(
            builder = builder,
            codeBlock = codeBlock,
            typeNames = workspace.dtoEnumPoetTypeNames(codeBlock),
        )
    }
}
