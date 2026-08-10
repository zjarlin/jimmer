package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.enumTypeRef
import site.addzero.lsi.jimmer.dto.mappingsByConstant
import site.addzero.lsi.jimmer.dto.mappingsByValue
import site.addzero.lsi.jimmer.dto.scalarType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

/** 把冻结的枚举映射降级为枚举值到 DTO 标量值的转换 lambda。 */
internal fun DtoBaseProp.toEnumToScalarLambdaPoetCodeBlock(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiCodeBlock {
    val enumType = requireNotNull(enumType) {
        "DTO enum conversion requires an enum mapping: ${id.value}"
    }
    val enumTypeRef = enumTypeRef(graph, immutableSchema)
    return when (targetLanguage.requireDtoEnumTargetLanguage()) {
        LsiLanguage.JAVA -> LsiCodeBlock.build {
            text("arg -> {")
            indent {
                line()
                beginControlFlow {
                    text("switch ((")
                    type(enumTypeRef)
                    text(")arg)")
                }
                enumType.mappingsByConstant().forEach { (constant, value) ->
                    text("case ")
                    name(constant)
                    text(":")
                    line()
                    indent {
                        returnValue { literal(value) }
                    }
                }
                text("default:")
                line()
                indent {
                    statement {
                        text("throw new AssertionError(")
                        string("Internal bug")
                        text(")")
                    }
                }
                endControlFlow()
            }
            text("}")
        }
        LsiLanguage.KOTLIN -> LsiCodeBlock.build {
            text("{")
            indent {
                line()
                beginControlFlow {
                    text("when (it as ")
                    type(enumTypeRef)
                    text(")")
                }
                enumType.mappingsByConstant().forEach { (constant, value) ->
                    statement {
                        type(enumTypeRef)
                        text(".")
                        name(constant)
                        text(" -> ")
                        literal(value)
                    }
                }
                endControlFlow()
            }
            text("}")
        }
        else -> error("Unreachable")
    }
}

/** 把冻结的枚举映射降级为 DTO 标量值到枚举值的转换 lambda。 */
internal fun DtoBaseProp.toScalarToEnumLambdaPoetCodeBlock(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiCodeBlock {
    val language = targetLanguage.requireDtoEnumTargetLanguage()
    return LsiCodeBlock.build {
        text(if (language == LsiLanguage.JAVA) "arg -> {" else "{")
        indent {
            line()
            add(
                toScalarToEnumPoetCodeBlock(
                    targetLanguage = language,
                    graph = graph,
                    immutableSchema = immutableSchema,
                    variableName = if (language == LsiLanguage.JAVA) "arg" else "it",
                )
            )
        }
        text("}")
    }
}

/** 把冻结的枚举映射降级为当前作用域中的 DTO 标量值到枚举值转换。 */
internal fun DtoBaseProp.toScalarToEnumPoetCodeBlock(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    variableName: String,
): LsiCodeBlock {
    require(variableName.isNotBlank()) { "DTO enum conversion variable name cannot be blank" }
    val language = targetLanguage.requireDtoEnumTargetLanguage()
    val enumType = requireNotNull(enumType) {
        "DTO enum conversion requires an enum mapping: ${id.value}"
    }
    val enumTypeRef = enumTypeRef(graph, immutableSchema)
    val scalarType = enumType.scalarType(language)
    return LsiCodeBlock.build {
        beginControlFlow {
            text(if (language == LsiLanguage.JAVA) "switch ((" else "when (")
            if (language == LsiLanguage.JAVA) {
                type(scalarType)
                text(")")
                text(variableName)
            } else {
                text(variableName)
                text(" as ")
                type(scalarType)
            }
            text(")")
        }
        enumType.mappingsByValue().forEach { (value, constant) ->
            if (language == LsiLanguage.JAVA) {
                text("case ")
                literal(value)
                text(":")
                line()
                indent {
                    returnValue {
                        type(enumTypeRef)
                        text(".")
                        name(constant)
                    }
                }
            } else {
                statement {
                    literal(value)
                    text(" -> ")
                    type(enumTypeRef)
                    text(".")
                    name(constant)
                }
            }
        }
        if (language == LsiLanguage.JAVA) {
            javaIllegalEnumValue(variableName, enumTypeRef.declarationId.requireTypeQualifiedName())
        } else {
            kotlinIllegalEnumValue(variableName, enumTypeRef.declarationId.requireTypeQualifiedName())
        }
        endControlFlow()
    }
}

/** 为独立枚举转换代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoEnumPoetTypeNames(
    codeBlock: LsiCodeBlock,
): List<LsiClass> {
    return toLsiClasses(
        typeIds = codeBlock.referencedTypeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES,
    )
}

private fun LsiCodeBuilder.javaIllegalEnumValue(
    variableName: String,
    enumQualifiedName: String,
) {
    text("default:")
    line()
    indent {
        statement {
            text("throw new IllegalArgumentException(")
            string("Illegal value `\"")
            text(" + ")
            text(variableName)
            text(" + ")
            string("\"`for enum type: \"$enumQualifiedName\"")
            text(")")
        }
    }
}

private fun LsiCodeBuilder.kotlinIllegalEnumValue(
    variableName: String,
    enumQualifiedName: String,
) {
    text("else -> throw IllegalArgumentException(")
    line()
    indent {
        statement {
            string("Illegal value \"")
            text(" + ")
            text(variableName)
            text(" + ")
            string("\" for the enum type \"$enumQualifiedName\"")
        }
    }
    text(")")
    line()
}

private fun LsiLanguage.requireDtoEnumTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO enum conversion requires Java or Kotlin target language: $this"
    }
    return this
}
