package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoHibernateValidatorEnhancedBeanType
import org.babyfish.jimmer.compiler.dto.dtoHibernateValidatorPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoHibernateValidatorPoetFunctions
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 DTO Hibernate Validator 语义渲染为 JavaPoet 结构。 */
internal object AptDtoHibernateValidatorRenderer {

    @JvmStatic
    fun renderEnhancedBeanType(workspace: LsiWorkspace): TypeName {
        return LsiJavaPoetRenderer().renderTypeName(
            type = dtoHibernateValidatorEnhancedBeanType(),
            typeNames = workspace.dtoHibernateValidatorPoetTypeNames(emptyList()),
        )
    }

    @JvmStatic
    fun renderFunctions(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): List<MethodSpec> {
        val functions = dtoType.toDtoHibernateValidatorPoetFunctions(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
        )
        val typeNames = workspace.dtoHibernateValidatorPoetTypeNames(functions)
        val renderer = LsiJavaPoetRenderer()
        return functions.map { function -> renderer.renderFunction(function, typeNames) }
    }
}
