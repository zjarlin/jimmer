package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import org.babyfish.jimmer.compiler.dto.dtoSpecificationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoEntityTypePoetFunction
import org.babyfish.jimmer.compiler.dto.toSpecificationLikeOptionArgumentsPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 Specification 函数渲染为 KotlinPoet 方法。 */
internal object KspDtoSpecificationRenderer {

    fun renderLikeOptionArguments(
        prop: DtoBaseProp,
        graph: DtoGraph,
    ): CodeBlock? {
        val codeBlock = prop.toSpecificationLikeOptionArgumentsPoetCodeBlock(graph) ?: return null
        return LsiKotlinPoetRenderer().renderCodeBlock(codeBlock, emptyList())
    }

    fun renderEntityType(
        dtoType: DtoType,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): FunSpec {
        val function = dtoType.toDtoEntityTypePoetFunction(
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
        )
        return LsiKotlinPoetRenderer().renderFunction(
            function = function,
            typeNames = workspace.dtoSpecificationPoetTypeNames(function),
        )
    }
}
