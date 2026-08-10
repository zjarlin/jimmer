package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.dtoFoldValuePoetTypeNames
import org.babyfish.jimmer.compiler.dto.toFoldValuePoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将折叠 DTO 的基础对象构造表达式渲染为 JavaPoet 代码块。 */
internal object AptDtoFoldValueRenderer {

    @JvmStatic
    fun render(
        prop: DtoFoldProp,
        graph: DtoGraph,
        workspace: LsiWorkspace,
        baseParameterName: String,
        nullGuardAccessorName: String,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
        generatedTypeNames: Collection<LsiTypeName>,
    ): CodeBlock {
        val initializer = prop.toFoldValuePoetCodeBlock(
            graph = graph,
            targetLanguage = LsiLanguage.JAVA,
            generatedTargetType = generatedTargetType,
            baseParameterName = baseParameterName,
            nullGuardAccessorName = nullGuardAccessorName,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = initializer,
            typeNames = workspace.dtoFoldValuePoetTypeNames(initializer, generatedTypeNames),
        )
    }
}
