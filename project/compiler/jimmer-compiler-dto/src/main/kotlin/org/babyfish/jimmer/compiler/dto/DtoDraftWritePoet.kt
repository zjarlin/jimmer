package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.hasEntityAssociationListDraftTarget
import site.addzero.lsi.jimmer.dto.requiresEmptyAssociationListDraftFallback
import site.addzero.lsi.jimmer.dto.usesDirectBaseAccess
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

/** 把冻结的 DTO 属性写回语义降级为访问器调用。 */
internal fun DtoBaseProp.toDraftWritePoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    accessorName: String,
    draftName: String,
    valueName: String,
    baseValueWriterName: String,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): LsiCodeBlock {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO draft write requires Java or Kotlin target language: $targetLanguage"
    }
    val direct = usesDirectBaseAccess(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
        generatedTargetType = generatedTargetType,
    )
    return LsiCodeBlock.build {
        statement {
            if (direct) {
                directDraftWrite(
                    targetLanguage = targetLanguage,
                    draftName = draftName,
                    valueName = valueName,
                    baseValueWriterName = baseValueWriterName,
                )
            } else {
                accessorDraftWrite(
                    prop = this@toDraftWritePoetCodeBlock,
                    graph = graph,
                    immutableSchema = immutableSchema,
                    targetLanguage = targetLanguage,
                    accessorName = accessorName,
                    draftName = draftName,
                    valueName = valueName,
                )
            }
        }
    }
}

private fun LsiCodeBuilder.directDraftWrite(
    targetLanguage: LsiLanguage,
    draftName: String,
    valueName: String,
    baseValueWriterName: String,
) {
    name(draftName)
    text(".")
    name(baseValueWriterName)
    when (targetLanguage) {
        LsiLanguage.JAVA -> {
            text("(this.")
            name(valueName)
            text(")")
        }
        LsiLanguage.KOTLIN -> {
            text(" = ")
            name(valueName)
        }
        LsiLanguage.UNKNOWN -> error("Unreachable")
    }
}

private fun LsiCodeBuilder.accessorDraftWrite(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    accessorName: String,
    draftName: String,
    valueName: String,
) {
    val emptyListFallback = when (targetLanguage) {
        LsiLanguage.JAVA -> prop.hasEntityAssociationListDraftTarget(graph, immutableSchema)
        LsiLanguage.KOTLIN -> prop.requiresEmptyAssociationListDraftFallback(graph, immutableSchema)
        LsiLanguage.UNKNOWN -> error("Unreachable")
    }
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

/** 为 DTO Draft 写回代码解析完整源码类型名。 */
internal fun LsiWorkspace.dtoDraftWritePoetTypeNames(
    codeBlock: LsiCodeBlock,
): List<LsiClass> {
    return toLsiClasses(
        typeIds = codeBlock.referencedTypeIds,
        additional = listOf(COLLECTIONS_POET_TYPE_NAME),
    )
}

private const val KOTLIN_COLLECTIONS_PACKAGE = "kotlin.collections"
private val COLLECTIONS_TYPE = LsiDeclaredType(LsiSymbolId.type("java.util.Collections"))
private val COLLECTIONS_POET_TYPE_NAME = LsiClass(
    typeId = COLLECTIONS_TYPE.declarationId,
    packageName = "java.util",
    simpleNames = listOf("Collections"),
)
