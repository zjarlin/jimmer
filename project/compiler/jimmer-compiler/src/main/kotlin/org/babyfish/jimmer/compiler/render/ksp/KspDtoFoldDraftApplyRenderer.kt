package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toDraftApplyPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将折叠 DTO 写回 Draft 的 Kotlin 代码块渲染为 KotlinPoet。 */
internal object KspDtoFoldDraftApplyRenderer {

    fun render(
        prop: DtoFoldProp,
        draftParameterName: String,
    ): CodeBlock {
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = prop.toDraftApplyPoetCodeBlock(
                targetLanguage = LsiLanguage.KOTLIN,
                draftParameterName = draftParameterName,
            ),
            typeNames = emptyList(),
        )
    }
}
