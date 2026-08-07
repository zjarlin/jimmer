package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.poet.LsiPoetCodeBlock

/** 将折叠 DTO 写回 Draft 的控制流降低为平台中立代码。 */
internal fun DtoFoldProp.toDraftApplyPoetCodeBlock(
    targetLanguage: LsiLanguage,
    draftParameterName: String,
): LsiPoetCodeBlock {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO fold Draft application requires Java or Kotlin: $targetLanguage"
    }
    require(draftParameterName.isNotBlank()) {
        "DTO fold Draft application requires a non-blank Draft parameter name"
    }
    return LsiPoetCodeBlock.build {
        if (targetLanguage == LsiLanguage.JAVA && nullable) {
            beginControlFlow {
                text("if (this.")
                name(name)
                text(" != null)")
            }
        }
        statement {
            text("this.")
            name(name)
            text(if (targetLanguage == LsiLanguage.KOTLIN && nullable) "?.__applyTo(" else ".__applyTo(")
            name(draftParameterName)
            text(")")
        }
        if (targetLanguage == LsiLanguage.JAVA && nullable) {
            endControlFlow()
        }
    }
}
