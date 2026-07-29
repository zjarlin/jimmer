package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.MethodSpec
import org.babyfish.jimmer.compiler.dto.dtoSpecificationPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toLsiSpecificationApplyToPoetFunction
import org.babyfish.jimmer.compiler.dto.toLsiSpecificationEntityTypePoetFunction
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 Specification 函数渲染为 JavaPoet 方法。 */
internal object AptDtoSpecificationRenderer {

    @JvmStatic
    fun renderEntityType(
        dtoType: DtoType,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): MethodSpec {
        val function = dtoType.toLsiSpecificationEntityTypePoetFunction(
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
        )
        return LsiJavaPoetRenderer().renderFunction(
            function = function,
            typeNames = workspace.dtoSpecificationPoetTypeNames(function, immutableSchema),
        )
    }

    @JvmStatic
    fun renderApplyTo(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): MethodSpec {
        val function = dtoType.toLsiSpecificationApplyToPoetFunction(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
        )
        return LsiJavaPoetRenderer().renderFunction(
            function = function,
            typeNames = workspace.dtoSpecificationPoetTypeNames(function, immutableSchema),
        )
    }
}
