package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toDraftApplyPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将折叠 DTO 写回 Draft 的 Java 代码块渲染为 JavaPoet。 */
internal object AptDtoFoldDraftApplyRenderer {

    @JvmStatic
    fun render(
        prop: DtoFoldProp,
        draftParameterName: String,
    ): CodeBlock {
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = prop.toDraftApplyPoetCodeBlock(
                targetLanguage = LsiLanguage.JAVA,
                draftParameterName = draftParameterName,
            ),
            typeNames = emptyList(),
        )
    }
}
