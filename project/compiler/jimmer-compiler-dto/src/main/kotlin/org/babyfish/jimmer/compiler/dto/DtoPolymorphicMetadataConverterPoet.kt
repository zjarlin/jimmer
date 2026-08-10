package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.defaultBranch
import site.addzero.lsi.jimmer.dto.typeBranchesInDeclarationOrder
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

/** 将冻结的多态 DTO 分支转换规则降低为可由两端 Poet 渲染的代码块。 */
internal fun DtoType.toPolymorphicMetadataConverterPoetCodeBlock(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    generatedRootTypeName: LsiClass,
): LsiCodeBlock {
    require(graph.typesById[id] === this) {
        "DTO polymorphic root does not belong to this graph: ${id.value}"
    }
    val polymorphism = requireNotNull(polymorphism) {
        "DTO type is not a polymorphic root: ${id.value}"
    }
    return when (targetLanguage) {
        LsiLanguage.JAVA -> LsiCodeBlock.build {
            beginControlFlow { text("base ->") }
            statement {
                type(
                    LsiDeclaredType(
                        declarationId = JAVA_CLASS_TYPE_ID,
                        arguments = listOf(LsiTypeArgument.STAR),
                    )
                )
                text(" actualType = ((")
                type(LsiDeclaredType(IMMUTABLE_SPI_TYPE_ID))
                text(")base).__type().getJavaClass()")
            }
            polymorphism.typeBranchesInDeclarationOrder().forEach { branch ->
                javaTypeBranch(branch, generatedRootTypeName)
            }
            polymorphism.defaultBranch()?.let { branch ->
                returnValue {
                    text("new ")
                    type(generatedBranchType(branch, generatedRootTypeName))
                    text("(base)")
                }
            } ?: statement {
                text("throw new ")
                type(LsiDeclaredType(JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID))
                text("(")
                string(missingBranchMessagePrefix(generatedRootTypeName))
                text(" + actualType.getName() + ")
                string("\"")
                text(")")
            }
            endControlFlow()
        }
        LsiLanguage.KOTLIN -> LsiCodeBlock.build {
            text("{ base ->")
            indent {
                line()
                statement {
                    text("val actualType = (base as ")
                    type(LsiDeclaredType(IMMUTABLE_SPI_TYPE_ID))
                    text(").__type().javaClass")
                }
                beginControlFlow { text("when (actualType)") }
                polymorphism.typeBranchesInDeclarationOrder().forEach { branch ->
                    kotlinTypeBranch(branch, generatedRootTypeName)
                }
                polymorphism.defaultBranch()?.let { branch ->
                    statement {
                        text("else -> ")
                        type(generatedBranchType(branch, generatedRootTypeName))
                        text("(base)")
                    }
                } ?: statement {
                    text("else -> throw ")
                    type(LsiDeclaredType(JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID))
                    text("(")
                    string(missingBranchMessagePrefix(generatedRootTypeName))
                    text(" + actualType.name + ")
                    string("\"")
                    text(")")
                }
                endControlFlow()
            }
            text("}")
        }
        else -> error("DTO polymorphic metadata converter requires Java or Kotlin: $targetLanguage")
    }
}

/** 为独立多态转换代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoPolymorphicMetadataConverterPoetTypeNames(
    dtoType: DtoType,
    codeBlock: LsiCodeBlock,
    generatedRootTypeName: LsiClass,
): List<LsiClass> {
    val polymorphism = requireNotNull(dtoType.polymorphism) {
        "DTO type is not a polymorphic root: ${dtoType.id.value}"
    }
    return toLsiClasses(
        typeIds = codeBlock.referencedTypeIds,
        additional = POLYMORPHIC_CONVERTER_RUNTIME_TYPE_NAMES +
            polymorphism.branches.map { branch -> generatedBranchTypeName(branch, generatedRootTypeName) },
    )
}

private fun LsiCodeBuilder.javaTypeBranch(
    branch: DtoPolymorphicBranch,
    generatedRootTypeName: LsiClass,
) {
    val targetType = LsiDeclaredType(requireNotNull(branch.targetBaseTypeId))
    beginControlFlow {
        text("if (actualType == ")
        type(targetType)
        text(".class)")
    }
    returnValue {
        text("new ")
        type(generatedBranchType(branch, generatedRootTypeName))
        text("((")
        type(targetType)
        text(")base)")
    }
    endControlFlow()
}

private fun LsiCodeBuilder.kotlinTypeBranch(
    branch: DtoPolymorphicBranch,
    generatedRootTypeName: LsiClass,
) {
    val targetType = LsiDeclaredType(requireNotNull(branch.targetBaseTypeId))
    statement {
        type(targetType)
        text("::class.java -> ")
        type(generatedBranchType(branch, generatedRootTypeName))
        text("(base as ")
        type(targetType)
        text(")")
    }
}

private fun generatedBranchType(
    branch: DtoPolymorphicBranch,
    generatedRootTypeName: LsiClass,
): LsiDeclaredType {
    return LsiDeclaredType(generatedBranchTypeName(branch, generatedRootTypeName).id)
}

private fun generatedBranchTypeName(
    branch: DtoPolymorphicBranch,
    generatedRootTypeName: LsiClass,
): LsiClass {
    return JimmerDtoPoetTypeNames.create(
        packageName = generatedRootTypeName.packageName,
        simpleNames = generatedRootTypeName.simpleNames + branch.className,
    )
}

private fun missingBranchMessagePrefix(generatedRootTypeName: LsiClass): String {
    return "Cannot convert entity object to polymorphic DTO \"${generatedRootTypeName.canonicalName}\" " +
        "because there is no branch for actual entity type \""
}

private val JAVA_CLASS_TYPE_ID = LsiSymbolId.type("java.lang.Class")
private val IMMUTABLE_SPI_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.runtime.ImmutableSpi")
private val JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID = LsiSymbolId.type("java.lang.IllegalArgumentException")

private val POLYMORPHIC_CONVERTER_RUNTIME_TYPE_NAMES = listOf(
    LsiClass(JAVA_CLASS_TYPE_ID, "java.lang", listOf("Class")),
    LsiClass(IMMUTABLE_SPI_TYPE_ID, "org.babyfish.jimmer.runtime", listOf("ImmutableSpi")),
    LsiClass(
        JAVA_ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID,
        "java.lang",
        listOf("IllegalArgumentException"),
    ),
)
