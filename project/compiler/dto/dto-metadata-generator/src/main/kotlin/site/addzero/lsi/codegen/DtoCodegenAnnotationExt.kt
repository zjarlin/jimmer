package site.addzero.lsi.codegen

import site.addzero.lsi.jimmer.dto.LsiDtoFile
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiStringAnnotationValue

/**
 * DTO 文件提示文案只服务 DTO shared generator，放在 DTO 模块而不是通用扩展模块。
 */
fun generatedAnnotation(dtoFile: LsiDtoFile, mutable: Boolean): LsiAnnotationSpec =
    LsiAnnotationSpec(
        type = LsiClassName.bestGuess("org.babyfish.jimmer.internal.GeneratedBy"),
        members = mapOf(
            "file" to LsiStringAnnotationValue(dtoFile.rawDtoFile.path),
            "prompt" to LsiStringAnnotationValue(
                if (mutable) {
                    "The current DTO type is mutable. If you need to make it immutable, " +
                        "please remove the ksp argument `jimmer.dto.mutable`"
                } else {
                    "The current DTO type is immutable. If you need to make it mutable, " +
                        "please set the ksp argument `jimmer.dto.mutable` to the string \"text\""
                }
            )
        )
    )
