package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.nullGuardProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将折叠 DTO 的基础对象构造表达式降低为平台中立代码。 */
internal fun DtoFoldProp.toFoldValuePoetCodeBlock(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
    baseParameterName: String,
    nullGuardAccessorName: String,
): LsiPoetCodeBlock {
    val language = targetLanguage.requireDtoFoldValueTargetLanguage()
    val targetType = generatedTargetType(this)
    val guarded = nullGuardProp(graph) != null
    return LsiPoetCodeBlock.build {
        when (language) {
            LsiLanguage.JAVA -> javaFoldValue(
                targetType = targetType,
                baseParameterName = baseParameterName,
                nullGuardAccessorName = nullGuardAccessorName,
                guarded = guarded,
            )
            LsiLanguage.KOTLIN -> kotlinFoldValue(
                targetType = targetType,
                baseParameterName = baseParameterName,
                nullGuardAccessorName = nullGuardAccessorName,
                guarded = guarded,
            )
            LsiLanguage.UNKNOWN -> error("Unreachable")
        }
    }
}

/** 解析折叠属性 initializer 引用的完整类型名。 */
internal fun LsiWorkspace.dtoFoldValuePoetTypeNames(
    codeBlock: LsiPoetCodeBlock,
    generatedTypeNames: Collection<LsiPoetTypeName>,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + generatedTypeNames,
    )
}

private fun LsiPoetCodeBuilder.javaFoldValue(
    targetType: LsiDeclaredType,
    baseParameterName: String,
    nullGuardAccessorName: String,
    guarded: Boolean,
) {
    if (guarded) {
        name(nullGuardAccessorName)
        text(".get(")
        name(baseParameterName)
        text(") != null ? ")
    }
    text("new ")
    targetConstructor(targetType, baseParameterName)
    if (guarded) {
        text(" : ")
        literal("null")
    }
}

private fun LsiPoetCodeBuilder.kotlinFoldValue(
    targetType: LsiDeclaredType,
    baseParameterName: String,
    nullGuardAccessorName: String,
    guarded: Boolean,
) {
    if (guarded) {
        name(nullGuardAccessorName)
        text(".get<")
        type(KOTLIN_NULLABLE_ANY_TYPE)
        text(">(")
        name(baseParameterName)
        text(")?.let { ")
    }
    targetConstructor(targetType, baseParameterName)
    if (guarded) {
        text(" }")
    }
}

private fun LsiPoetCodeBuilder.targetConstructor(
    targetType: LsiDeclaredType,
    baseParameterName: String,
) {
    type(targetType)
    text("(")
    name(baseParameterName)
    text(")")
}

private fun LsiLanguage.requireDtoFoldValueTargetLanguage(): LsiLanguage {
    return when (this) {
        LsiLanguage.JAVA,
        LsiLanguage.KOTLIN,
        -> this
        LsiLanguage.UNKNOWN -> error("DTO fold value Poet requires a Java or Kotlin target language")
    }
}

private val KOTLIN_NULLABLE_ANY_TYPE = LsiDeclaredType(
    declarationId = LsiSymbolId.type("kotlin.Any"),
    nullability = LsiNullability.NULLABLE,
)
