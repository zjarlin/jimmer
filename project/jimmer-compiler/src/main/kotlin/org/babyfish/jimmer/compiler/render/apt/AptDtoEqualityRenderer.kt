package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.MethodSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoEqualityPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toDtoEqualsPoetFunction
import org.babyfish.jimmer.compiler.dto.toDtoHashCodePoetFunction
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 DTO 相等性函数渲染为 JavaPoet 方法。 */
internal object AptDtoEqualityRenderer {

    @JvmStatic
    fun renderHashCode(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
    ): MethodSpec {
        return LsiJavaPoetRenderer().renderFunction(
            dtoType.toDtoHashCodePoetFunction(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.JAVA,
            ),
            dtoEqualityPoetTypeNames(),
        )
    }

    @JvmStatic
    fun renderEquals(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        generatedDtoPackageName: String,
        generatedDtoSimpleNames: List<String>,
    ): MethodSpec {
        val generatedTypeName = JimmerDtoPoetTypeNames.create(
            generatedDtoPackageName,
            generatedDtoSimpleNames,
        )
        return LsiJavaPoetRenderer().renderFunction(
            dtoType.toDtoEqualsPoetFunction(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.JAVA,
                generatedTypeName = generatedTypeName,
            ),
            dtoEqualityPoetTypeNames(generatedTypeName),
        )
    }
}
