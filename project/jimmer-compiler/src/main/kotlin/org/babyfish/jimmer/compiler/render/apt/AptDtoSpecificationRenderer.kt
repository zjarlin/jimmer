package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import org.babyfish.jimmer.compiler.dto.dtoSpecificationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoEntityTypePoetFunction
import org.babyfish.jimmer.compiler.dto.toSpecificationLikeOptionArgumentsPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 Specification 函数渲染为 JavaPoet 方法。 */
internal object AptDtoSpecificationRenderer {

    @JvmStatic
    fun renderLikeOptionArguments(
        prop: DtoBaseProp,
        graph: DtoGraph,
    ): CodeBlock? {
        val codeBlock = prop.toSpecificationLikeOptionArgumentsPoetCodeBlock(graph) ?: return null
        return LsiJavaPoetRenderer().renderCodeBlock(codeBlock, emptyList())
    }

    @JvmStatic
    fun renderEntityType(
        dtoType: DtoType,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): MethodSpec {
        val function = dtoType.toDtoEntityTypePoetFunction(
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
        )
        return LsiJavaPoetRenderer().renderFunction(
            function = function,
            typeNames = workspace.dtoSpecificationPoetTypeNames(function),
        )
    }
}
