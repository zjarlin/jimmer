package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.FunSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoEqualityPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoEqualsPoetFunction
import org.babyfish.jimmer.compiler.dto.toDtoHashCodePoetFunction
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 DTO 相等性函数渲染为 KotlinPoet 方法。 */
internal object KspDtoEqualityRenderer {

    fun renderHashCode(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
    ): FunSpec {
        return LsiKotlinPoetRenderer().renderFunction(
            dtoType.toDtoHashCodePoetFunction(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.KOTLIN,
            ),
            dtoEqualityPoetTypeNames(),
        )
    }

    fun renderEquals(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        generatedDtoPackageName: String,
        generatedDtoSimpleNames: List<String>,
    ): FunSpec {
        val generatedTypeName = JimmerDtoPoetTypeNames.create(
            generatedDtoPackageName,
            generatedDtoSimpleNames,
        )
        return LsiKotlinPoetRenderer().renderFunction(
            dtoType.toDtoEqualsPoetFunction(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.KOTLIN,
                generatedTypeName = generatedTypeName,
            ),
            dtoEqualityPoetTypeNames(generatedTypeName),
        )
    }
}
