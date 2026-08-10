package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoToStringInclusion
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.jimmer.dto.propsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.toStringInclusion
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.clazz.LsiClass

/** 将冻结的 DTO 属性顺序与包含条件降低为平台中立的 toString 函数。 */
internal fun DtoType.toDtoToStringPoetFunction(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
    generatedSimpleNamePath: String,
): LsiMethod {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO toString requires Java or Kotlin target language"
    }
    require(generatedSimpleNamePath.isNotBlank()) {
        "DTO toString generated simple name path cannot be blank"
    }
    val props = propsInDeclarationOrder(graph)
    val inclusions = props.associateWith { prop -> prop.toStringInclusion(graph) }
    val loadedStateNameByProp = props.mapNotNull { prop ->
        if (inclusions.getValue(prop) == DtoToStringInclusion.WHEN_LOADED) {
            prop to requireNotNull(prop.dtoLoadedStateStorageNameOrNull(graph, targetLanguage))
        } else {
            null
        }
    }.toMap()
    val hasConditionalProps = inclusions.values.any { inclusion ->
        inclusion != DtoToStringInclusion.ALWAYS
    }
    val body = builderBody(
        props = props,
        inclusions = inclusions,
        loadedStateNameByProp = loadedStateNameByProp,
        targetLanguage = targetLanguage,
        generatedSimpleNamePath = generatedSimpleNamePath,
        hasConditionalProps = hasConditionalProps,
    )
    return LsiMethod(
        name = "toString",
        modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
        returnType = STRING_TYPE,
        body = body,
        bodyStyle = LsiBodyStyle.BLOCK,
    )
}

private fun builderBody(
    props: List<DtoProp>,
    inclusions: Map<DtoProp, DtoToStringInclusion>,
    loadedStateNameByProp: Map<DtoProp, String>,
    targetLanguage: LsiLanguage,
    generatedSimpleNamePath: String,
    hasConditionalProps: Boolean,
): LsiCodeBlock {
    val reservedNames = (props.map(DtoProp::name) + loadedStateNameByProp.values).toMutableSet()
    val builderName = reservedNames.reserveLocalName("builder")
    val separatorName = reservedNames.reserveLocalName(
        if (targetLanguage == LsiLanguage.JAVA) "_sp" else "separator",
    )
    return LsiCodeBlock.build {
        declareBuilder(targetLanguage, builderName)
        if (targetLanguage == LsiLanguage.KOTLIN && hasConditionalProps) {
            declareSeparator(targetLanguage, separatorName)
        }
        appendTypePrefix(builderName, generatedSimpleNamePath)
        if (targetLanguage == LsiLanguage.JAVA && hasConditionalProps) {
            declareSeparator(targetLanguage, separatorName)
        }
        props.forEachIndexed { index, prop ->
            val inclusion = inclusions.getValue(prop)
            when (inclusion) {
                DtoToStringInclusion.ALWAYS -> Unit
                DtoToStringInclusion.WHEN_LOADED -> beginControlFlow {
                    text("if (")
                    name(loadedStateNameByProp.getValue(prop))
                    text(")")
                }
                DtoToStringInclusion.WHEN_NON_NULL -> beginControlFlow {
                    text("if (")
                    name(prop.name)
                    text(" != null)")
                }
            }
            statement {
                name(builderName)
                text(".append(")
                if (hasConditionalProps && (targetLanguage == LsiLanguage.KOTLIN || index != 0)) {
                    name(separatorName)
                } else {
                    string(if (index == 0) "" else ", ")
                }
                text(").append(")
                string("${prop.name}=")
                text(").append(")
                name(prop.name)
                text(")")
            }
            if (hasConditionalProps) {
                statement {
                    name(separatorName)
                    text(" = ")
                    string(", ")
                }
            }
            if (inclusion != DtoToStringInclusion.ALWAYS) {
                endControlFlow()
            }
        }
        statement {
            name(builderName)
            text(".append(")
            character(')')
            text(")")
        }
        returnValue {
            name(builderName)
            text(".toString()")
        }
    }
}

private fun LsiCodeBuilder.declareBuilder(
    targetLanguage: LsiLanguage,
    builderName: String,
) {
    statement {
        if (targetLanguage == LsiLanguage.JAVA) {
            text("StringBuilder ")
            name(builderName)
            text(" = new StringBuilder()")
        } else {
            text("val ")
            name(builderName)
            text(" = StringBuilder()")
        }
    }
}

private fun LsiCodeBuilder.appendTypePrefix(
    builderName: String,
    generatedSimpleNamePath: String,
) {
    statement {
        name(builderName)
        text(".append(")
        string(generatedSimpleNamePath)
        text(").append(")
        character('(')
        text(")")
    }
}

private fun LsiCodeBuilder.declareSeparator(
    targetLanguage: LsiLanguage,
    separatorName: String,
) {
    statement {
        if (targetLanguage == LsiLanguage.JAVA) {
            type(STRING_TYPE)
            text(" ")
        } else {
            text("var ")
        }
        name(separatorName)
        text(" = ")
        string("")
    }
}

private fun MutableSet<String>.reserveLocalName(baseName: String): String {
    var candidate = baseName
    var suffix = 2
    while (!add(candidate)) {
        candidate = "${baseName}_${suffix++}"
    }
    return candidate
}

private val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
private val STRING_TYPE = LsiDeclaredType(STRING_TYPE_ID)

internal val DTO_TO_STRING_POET_TYPE_NAMES: List<LsiClass> = listOf(
    LsiClass(STRING_TYPE_ID, "java.lang", listOf("String")),
)
