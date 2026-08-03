package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoBaseValuePoetTypeNames
import org.babyfish.jimmer.compiler.dto.toBaseValuePoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.kotlinBaseValueAccessorName
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将 immutable-to-DTO Kotlin 属性读取语义渲染为 KotlinPoet 表达式。 */
internal object KspDtoBaseValueRenderer {

    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        accessorName: String,
        baseParameterName: String,
        conversionErrorMessage: String,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
        generatedTypeNames: Collection<LsiPoetTypeName>,
    ): CodeBlock {
        val initializer = prop.toBaseValuePoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
            generatedTargetType = generatedTargetType,
            baseParameterName = baseParameterName,
            accessorName = accessorName,
            baseValueAccessorName = prop.kotlinBaseValueAccessorName(graph),
            conversionErrorMessage = conversionErrorMessage,
        )
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = initializer,
            typeNames = workspace.dtoBaseValuePoetTypeNames(
                codeBlock = initializer,
                additional = generatedTypeNames,
            ),
        )
    }
}
