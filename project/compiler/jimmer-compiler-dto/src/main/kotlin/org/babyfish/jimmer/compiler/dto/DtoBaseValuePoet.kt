package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.generatedValueType
import site.addzero.lsi.jimmer.dto.requiresDtoPropAccessor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.toLsiTypeNames

/** 将 immutable-to-DTO 基础属性读取表达式降低为平台中立代码。 */
internal fun DtoBaseProp.toBaseValuePoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
    baseParameterName: String,
    accessorName: String,
    baseValueAccessorName: String,
    conversionErrorMessage: String,
    javaBaseProducerType: LsiDeclaredType? = null,
    javaBaseSlotName: String? = null,
): LsiCodeBlock {
    val language = targetLanguage.requireDtoBaseValueTargetLanguage()
    val direct = !requiresDtoPropAccessor(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = language,
        generatedTargetType = generatedTargetType,
    )
    return LsiCodeBlock.build {
        if (direct) {
            when (language) {
                LsiLanguage.JAVA -> if (nullable) {
                    javaDirectValue(
                        baseParameterName = baseParameterName,
                        baseValueAccessorName = baseValueAccessorName,
                        javaBaseProducerType = requireNotNull(javaBaseProducerType) {
                            "Java direct DTO value access requires the producer type: ${id.value}"
                        },
                        javaBaseSlotName = requireNotNull(javaBaseSlotName) {
                            "Java direct DTO value access requires the producer slot: ${id.value}"
                        },
                    )
                } else {
                    name(baseParameterName)
                    text(".")
                    name(baseValueAccessorName)
                    text("()")
                }
                LsiLanguage.KOTLIN -> {
                    name(baseParameterName)
                    text(".")
                    name(baseValueAccessorName)
                }
                LsiLanguage.UNKNOWN -> error("Unreachable")
            }
        } else {
            accessorValue(
                prop = this@toBaseValuePoetCodeBlock,
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = language,
                generatedTargetType = generatedTargetType,
                baseParameterName = baseParameterName,
                accessorName = accessorName,
                conversionErrorMessage = conversionErrorMessage,
            )
        }
    }
}

/** 解析 initializer 代码块引用的完整 DTO、运行时和 Draft 类型名。 */
internal fun LsiWorkspace.dtoBaseValuePoetTypeNames(
    codeBlock: LsiCodeBlock,
    additional: Collection<LsiTypeName> = emptyList(),
): List<LsiTypeName> {
    return toLsiTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + DTO_BASE_VALUE_POET_TYPE_NAMES + additional,
    )
}

private fun LsiCodeBuilder.javaDirectValue(
    baseParameterName: String,
    baseValueAccessorName: String,
    javaBaseProducerType: LsiDeclaredType,
    javaBaseSlotName: String,
) {
    text("((")
    type(IMMUTABLE_SPI_TYPE)
    text(")")
    name(baseParameterName)
    text(").__isLoaded(")
    type(PROP_ID_TYPE)
    text(".byIndex(")
    type(javaBaseProducerType)
    text(".")
    name(javaBaseSlotName)
    text(")) ? ")
    name(baseParameterName)
    text(".")
    name(baseValueAccessorName)
    text("() : ")
    literal("null")
}

private fun LsiCodeBuilder.accessorValue(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
    baseParameterName: String,
    accessorName: String,
    conversionErrorMessage: String,
) {
    name(accessorName)
    text(".get")
    if (targetLanguage == LsiLanguage.KOTLIN) {
        text("<")
        type(
            prop.generatedValueType(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = targetLanguage,
                generatedTargetType = generatedTargetType,
            ),
        )
        text(">")
    }
    text("(")
    if (prop.requiresNonNullBaseValueGuard()) {
        line()
        indent {
            name(baseParameterName)
            text(",")
            line()
            string(conversionErrorMessage)
        }
        line()
    } else {
        name(baseParameterName)
    }
    text(")")
}

private fun DtoBaseProp.requiresNonNullBaseValueGuard(): Boolean {
    return !nullable && baseNullable
}

private fun LsiLanguage.requireDtoBaseValueTargetLanguage(): LsiLanguage {
    return when (this) {
        LsiLanguage.JAVA,
        LsiLanguage.KOTLIN,
        -> this
        LsiLanguage.UNKNOWN -> error("DTO base value Poet requires a Java or Kotlin target language")
    }
}

private val IMMUTABLE_SPI_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.runtime.ImmutableSpi"),
)

private val PROP_ID_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.meta.PropId"),
)

private val DTO_BASE_VALUE_POET_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create(
        packageName = "org.babyfish.jimmer.runtime",
        simpleNames = listOf("ImmutableSpi"),
    ),
    JimmerDtoPoetTypeNames.create(
        packageName = "org.babyfish.jimmer.meta",
        simpleNames = listOf("PropId"),
    ),
)
