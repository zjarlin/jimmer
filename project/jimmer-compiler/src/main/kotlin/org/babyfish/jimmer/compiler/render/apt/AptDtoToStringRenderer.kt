package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.MethodSpec
import org.babyfish.jimmer.compiler.dto.DTO_TO_STRING_POET_TYPE_NAMES
import org.babyfish.jimmer.compiler.dto.toDtoToStringPoetFunction
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 DTO toString 函数渲染为 JavaPoet 方法。 */
internal object AptDtoToStringRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        generatedSimpleNamePath: String,
    ): MethodSpec {
        return LsiJavaPoetRenderer().renderFunction(
            dtoType.toDtoToStringPoetFunction(
                graph = graph,
                targetLanguage = LsiLanguage.JAVA,
                generatedSimpleNamePath = generatedSimpleNamePath,
            ),
            DTO_TO_STRING_POET_TYPE_NAMES,
        )
    }
}
