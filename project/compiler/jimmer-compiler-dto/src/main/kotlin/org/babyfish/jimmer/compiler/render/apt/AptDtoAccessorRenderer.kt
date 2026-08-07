package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoAccessorPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toAccessorInitializerPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 属性访问器初始化表达式渲染为 JavaPoet 代码块。 */
internal object AptDtoAccessorRenderer {

    @JvmStatic
    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        acceptNull: Boolean,
        withConverters: Boolean,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
        generatedTypeNames: Collection<LsiPoetTypeName>,
    ): CodeBlock {
        val initializer = prop.toAccessorInitializerPoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            targetLanguage = LsiLanguage.JAVA,
            acceptNull = acceptNull,
            withConverters = withConverters,
            generatedTargetType = generatedTargetType,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = initializer,
            typeNames = workspace.dtoAccessorPoetTypeNames(
                initializer = initializer,
                immutableSchema = immutableSchema,
                generatedTypeNames = generatedTypeNames,
            ),
        )
    }
}
