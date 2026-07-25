package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoHibernateValidatorEnhancedBeanType
import org.babyfish.jimmer.compiler.dto.dtoHibernateValidatorPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoHibernateValidatorPoetFunctions
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 DTO Hibernate Validator 语义渲染为 KotlinPoet 结构。 */
internal object KspDtoHibernateValidatorRenderer {

    fun renderEnhancedBeanType(workspace: LsiWorkspace): TypeName {
        return LsiKotlinPoetRenderer().renderTypeName(
            type = dtoHibernateValidatorEnhancedBeanType(),
            typeNames = workspace.dtoHibernateValidatorPoetTypeNames(emptyList()),
        )
    }

    fun renderFunctions(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): List<FunSpec> {
        val functions = dtoType.toDtoHibernateValidatorPoetFunctions(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.KOTLIN,
        )
        val typeNames = workspace.dtoHibernateValidatorPoetTypeNames(functions)
        val renderer = LsiKotlinPoetRenderer()
        return functions.map { function -> renderer.renderFunction(function, typeNames) }
    }
}
