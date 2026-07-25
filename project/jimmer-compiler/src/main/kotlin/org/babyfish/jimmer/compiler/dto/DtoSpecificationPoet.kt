package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.isNestedSpecificationFragment
import site.addzero.lsi.jimmer.dto.specificationBaseType
import site.addzero.lsi.jimmer.dto.specificationLikeOptionArguments
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将冻结的 Specification 基础类型语义降低为平台中立的 entityType 函数。 */
internal fun DtoType.toDtoEntityTypePoetFunction(
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiPoetFunction {
    val language = targetLanguage.requireSpecificationTargetLanguage()
    val baseType = LsiDeclaredType(specificationBaseType(immutableSchema).id)
    val modifiers = buildSet {
        if (language == LsiLanguage.JAVA) {
            add(LsiPoetModifier.PUBLIC)
        }
        if (!isNestedSpecificationFragment(immutableSchema)) {
            add(LsiPoetModifier.OVERRIDE)
        }
    }
    return LsiPoetFunction(
        name = "entityType",
        modifiers = modifiers,
        returnType = LsiDeclaredType(
            declarationId = CLASS_TYPE_ID,
            arguments = listOf(LsiTypeArgument.invariant(baseType)),
        ),
        body = LsiPoetCodeBlock.build {
            returnValue {
                type(baseType)
                text(if (language == LsiLanguage.JAVA) ".class" else "::class.java")
            }
        },
        bodyStyle = LsiPoetBodyStyle.BLOCK,
    )
}

/** 将冻结的 like/notLike 匹配参数降低为调用参数片段。 */
internal fun DtoBaseProp.toSpecificationLikeOptionArgumentsPoetCodeBlock(
    graph: DtoGraph,
): LsiPoetCodeBlock? {
    val arguments = specificationLikeOptionArguments(graph) ?: return null
    return LsiPoetCodeBlock.build {
        arguments.forEach { argument ->
            text(", ")
            literal(argument.toString())
        }
    }
}

/** 解析 entityType lowering 引用的精确源码类型名称。 */
internal fun LsiWorkspace.dtoSpecificationPoetTypeNames(
    function: LsiPoetFunction,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = function.referencedTypeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + CLASS_TYPE_NAME,
    )
}

private fun LsiLanguage.requireSpecificationTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO specification methods require Java or Kotlin target language"
    }
    return this
}

private val CLASS_TYPE_ID = LsiSymbolId.type("java.lang.Class")

private val CLASS_TYPE_NAME = LsiPoetTypeName(CLASS_TYPE_ID, "java.lang", listOf("Class"))
