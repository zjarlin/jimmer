package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeName
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
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 枚举转换渲染为可嵌入 APT DTO 的 JavaPoet 结构。 */
internal object AptDtoEnumRenderer {

    @JvmStatic
    fun renderEnumToScalarLambda(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): CodeBlock = render(
        prop.toEnumToScalarLambdaPoetCodeBlock(LsiLanguage.JAVA, graph, immutableSchema),
        workspace,
    )

    @JvmStatic
    fun renderScalarToEnumLambda(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): CodeBlock = render(
        prop.toScalarToEnumLambdaPoetCodeBlock(LsiLanguage.JAVA, graph, immutableSchema),
        workspace,
    )

    @JvmStatic
    fun renderScalarToEnumConversion(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        variableName: String,
    ): CodeBlock = render(
        prop.toScalarToEnumPoetCodeBlock(
            LsiLanguage.JAVA,
            graph,
            immutableSchema,
            variableName,
        ),
        workspace,
    )

    @JvmStatic
    fun renderScalarType(prop: DtoBaseProp, workspace: LsiWorkspace): TypeName {
        val enumType = requireNotNull(prop.enumType) {
            "DTO enum scalar type requires an enum mapping: ${prop.id.value}"
        }
        val typeName = AptDtoTypeRefRenderer.render(enumType.scalarType(LsiLanguage.JAVA), workspace)
        return if (prop.nullable && typeName.isPrimitive) typeName.box() else typeName
    }

    private fun render(codeBlock: site.addzero.lsi.poet.LsiPoetCodeBlock, workspace: LsiWorkspace): CodeBlock {
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = workspace.dtoEnumPoetTypeNames(codeBlock),
        )
    }
}
