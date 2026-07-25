package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.hasEntityAssociationListDraftTarget
import site.addzero.lsi.jimmer.dto.requiresEmptyAssociationListDraftFallback
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 把冻结的 DTO 属性写回语义降级为访问器调用。 */
internal fun DtoBaseProp.toDraftWritePoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    accessorName: String,
    draftName: String,
    valueName: String,
): LsiPoetCodeBlock {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO draft write requires Java or Kotlin target language: $targetLanguage"
    }
    val emptyListFallback = when (targetLanguage) {
        LsiLanguage.JAVA -> hasEntityAssociationListDraftTarget(graph, immutableSchema)
        LsiLanguage.KOTLIN -> requiresEmptyAssociationListDraftFallback(graph, immutableSchema)
        LsiLanguage.UNKNOWN -> error("Unreachable")
    }
    return LsiPoetCodeBlock.build {
        statement {
            name(accessorName)
            text(".set(")
            name(draftName)
            text(", ")
            when (targetLanguage) {
                LsiLanguage.JAVA -> {
                    text("this.")
                    name(valueName)
                    if (emptyListFallback) {
                        text(" != null ? this.")
                        name(valueName)
                        text(" : ")
                        type(COLLECTIONS_TYPE)
                        text(".emptyList()")
                    }
                }
                LsiLanguage.KOTLIN -> {
                    name(valueName)
                    if (emptyListFallback) {
                        text(".")
                        topLevelMember(KOTLIN_COLLECTIONS_PACKAGE, "orEmpty", extension = true)
                        text("()")
                    }
                }
                LsiLanguage.UNKNOWN -> error("Unreachable")
            }
            text(")")
        }
    }
}

/** 为 DTO Draft 写回代码解析完整源码类型名。 */
internal fun LsiWorkspace.dtoDraftWritePoetTypeNames(
    codeBlock: LsiPoetCodeBlock,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = listOf(COLLECTIONS_POET_TYPE_NAME),
    )
}

private const val KOTLIN_COLLECTIONS_PACKAGE = "kotlin.collections"
private val COLLECTIONS_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.Collections"))
private val COLLECTIONS_POET_TYPE_NAME = LsiPoetTypeName(
    typeId = COLLECTIONS_TYPE.declarationId,
    packageName = "java.util",
    simpleNames = listOf("Collections"),
)
