package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
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
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 属性访问器初始化表达式渲染为 KotlinPoet 代码块。 */
internal object KspDtoAccessorRenderer {

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
            targetLanguage = LsiLanguage.KOTLIN,
            acceptNull = acceptNull,
            withConverters = withConverters,
            generatedTargetType = generatedTargetType,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = initializer,
            typeNames = workspace.dtoAccessorPoetTypeNames(
                initializer = initializer,
                immutableSchema = immutableSchema,
                generatedTypeNames = generatedTypeNames,
            ),
        )
    }
}
