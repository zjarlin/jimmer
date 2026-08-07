package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.FunSpec
import org.babyfish.jimmer.compiler.dto.DTO_TO_STRING_POET_TYPE_NAMES
import org.babyfish.jimmer.compiler.dto.toDtoToStringPoetFunction
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 DTO toString 函数渲染为 KotlinPoet 方法。 */
internal object KspDtoToStringRenderer {

    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        generatedSimpleNamePath: String,
    ): FunSpec {
        return LsiKotlinPoetRenderer().renderFunction(
            dtoType.toDtoToStringPoetFunction(
                graph = graph,
                targetLanguage = LsiLanguage.KOTLIN,
                generatedSimpleNamePath = generatedSimpleNamePath,
            ),
            DTO_TO_STRING_POET_TYPE_NAMES,
        )
    }
}
