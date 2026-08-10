package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoValueEqualityKind
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.jimmer.dto.propsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.valueEqualityKind
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.clazz.LsiClass

/** 将冻结的 DTO 属性语义降低为平台中立的 hashCode 函数。 */
internal fun DtoType.toDtoHashCodePoetFunction(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiFunction {
    val language = targetLanguage.requireObjectMethodTargetLanguage()
    val props = propsInDeclarationOrder(graph)
    val loadedStateNames = props.associateWith { prop ->
        prop.dtoLoadedStateStorageNameOrNull(graph, language)
    }
    val reservedNames = (props.map(DtoProp::name) + loadedStateNames.values.filterNotNull()).toMutableSet()
    val hashName = reservedNames.reserveObjectMethodName(
        if (language == LsiLanguage.JAVA) "hash" else "_hash",
    )
    val body = LsiCodeBlock.build {
        if (props.isEmpty()) {
            returnValue { text("0") }
            return@build
        }
        props.forEachIndexed { index, prop ->
            val stateName = loadedStateNames.getValue(prop)
            if (index == 0) {
                declareHash(language, hashName) {
                    if (stateName == null) {
                        valueHash(prop, immutableSchema, graph)
                    } else {
                        text("0")
                    }
                }
            }
            if (stateName == null) {
                if (index != 0) {
                    appendHash(hashName) { valueHash(prop, immutableSchema, graph) }
                }
            } else {
                appendConditionalHash(
                    hashName = hashName,
                    stateName = stateName,
                    targetLanguage = language,
                ) {
                    valueHash(prop, immutableSchema, graph)
                }
                appendHash(hashName) { objectHash { thisMember(stateName) } }
            }
        }
        returnValue { name(hashName) }
    }
    return LsiFunction(
        name = "hashCode",
        modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
        returnType = INT_TYPE,
        body = body,
        bodyStyle = LsiBodyStyle.BLOCK,
    )
}

/** 将冻结的 DTO 属性语义降低为平台中立的 equals 函数。 */
internal fun DtoType.toDtoEqualsPoetFunction(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedTypeName: LsiClass,
): LsiFunction {
    val language = targetLanguage.requireObjectMethodTargetLanguage()
    val props = propsInDeclarationOrder(graph)
    val loadedStateNames = props.associateWith { prop ->
        prop.dtoLoadedStateStorageNameOrNull(graph, language)
    }
    val reservedNames = (props.map(DtoProp::name) + loadedStateNames.values.filterNotNull()).toMutableSet()
    val parameterName = reservedNames.reserveObjectMethodName(
        if (language == LsiLanguage.JAVA) "o" else "other",
    )
    val typedOtherName = reservedNames.reserveObjectMethodName(
        if (language == LsiLanguage.JAVA) "other" else "_other",
    )
    val generatedType = LsiDeclaredType(generatedTypeName.id)
    val body = LsiCodeBlock.build {
        beginControlFlow {
            text("if (")
            name(parameterName)
            text(" == null || ")
            if (language == LsiLanguage.JAVA) {
                text("this.getClass() != ")
                name(parameterName)
                text(".getClass()")
            } else {
                text("this::class != ")
                name(parameterName)
                text("::class")
            }
            text(")")
        }
        returnValue { text("false") }
        endControlFlow()
        statement {
            if (language == LsiLanguage.JAVA) {
                type(generatedType)
                text(" ")
                name(typedOtherName)
                text(" = (")
                type(generatedType)
                text(") ")
                name(parameterName)
            } else {
                text("val ")
                name(typedOtherName)
                text(" = ")
                name(parameterName)
                text(" as ")
                type(generatedType)
            }
        }
        props.forEach { prop ->
            val stateName = loadedStateNames.getValue(prop)
            if (stateName != null) {
                beginControlFlow {
                    text("if (")
                    thisMember(stateName)
                    text(" != ")
                    otherMember(typedOtherName, stateName)
                    text(")")
                }
                returnValue { text("false") }
                endControlFlow()
            }
            beginControlFlow {
                text("if (")
                if (stateName != null) {
                    thisMember(stateName)
                    text(" && ")
                }
                text("!")
                valueEquals(prop, immutableSchema, graph, typedOtherName)
                text(")")
            }
            returnValue { text("false") }
            endControlFlow()
        }
        returnValue { text("true") }
    }
    return LsiFunction(
        name = "equals",
        modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
        parameters = listOf(
            LsiParameter(
                name = parameterName,
                type = language.objectMethodParameterType(),
            ),
        ),
        returnType = BOOLEAN_TYPE,
        body = body,
        bodyStyle = LsiBodyStyle.BLOCK,
    )
}

private fun LsiCodeBuilder.declareHash(
    targetLanguage: LsiLanguage,
    hashName: String,
    value: LsiCodeBuilder.() -> Unit,
) {
    statement {
        if (targetLanguage == LsiLanguage.JAVA) {
            text("int ")
        } else {
            text("var ")
        }
        name(hashName)
        text(" = ")
        value()
    }
}

private fun LsiCodeBuilder.appendHash(
    hashName: String,
    value: LsiCodeBuilder.() -> Unit,
) {
    statement {
        name(hashName)
        text(" = ")
        name(hashName)
        text(" * 31 + ")
        value()
    }
}

private fun LsiCodeBuilder.appendConditionalHash(
    hashName: String,
    stateName: String,
    targetLanguage: LsiLanguage,
    value: LsiCodeBuilder.() -> Unit,
) {
    appendHash(hashName) {
        text("(")
        if (targetLanguage == LsiLanguage.JAVA) {
            thisMember(stateName)
            text(" ? ")
            value()
            text(" : 0")
        } else {
            text("if (")
            thisMember(stateName)
            text(") ")
            value()
            text(" else 0")
        }
        text(")")
    }
}

private fun LsiCodeBuilder.valueHash(
    prop: DtoProp,
    immutableSchema: ImmutableSchema,
    graph: DtoGraph,
) {
    when (prop.valueEqualityKind(graph, immutableSchema)) {
        DtoValueEqualityKind.ARRAY_CONTENT -> {
            type(ARRAYS_TYPE)
            text(".hashCode(")
            thisMember(prop.name)
            text(")")
        }
        DtoValueEqualityKind.VALUE -> objectHash { thisMember(prop.name) }
    }
}

private fun LsiCodeBuilder.objectHash(value: LsiCodeBuilder.() -> Unit) {
    type(OBJECTS_TYPE)
    text(".hashCode(")
    value()
    text(")")
}

private fun LsiCodeBuilder.valueEquals(
    prop: DtoProp,
    immutableSchema: ImmutableSchema,
    graph: DtoGraph,
    typedOtherName: String,
) {
    type(
        when (prop.valueEqualityKind(graph, immutableSchema)) {
            DtoValueEqualityKind.ARRAY_CONTENT -> ARRAYS_TYPE
            DtoValueEqualityKind.VALUE -> OBJECTS_TYPE
        },
    )
    text(".equals(")
    thisMember(prop.name)
    text(", ")
    otherMember(typedOtherName, prop.name)
    text(")")
}

private fun LsiCodeBuilder.thisMember(name: String) {
    text("this.")
    name(name)
}

private fun LsiCodeBuilder.otherMember(
    otherName: String,
    memberName: String,
) {
    name(otherName)
    text(".")
    name(memberName)
}

private fun MutableSet<String>.reserveObjectMethodName(baseName: String): String {
    var candidate = baseName
    var suffix = 2
    while (!add(candidate)) {
        candidate = "${baseName}_${suffix++}"
    }
    return candidate
}

private fun LsiLanguage.requireObjectMethodTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO object methods require Java or Kotlin target language"
    }
    return this
}

private fun LsiLanguage.objectMethodParameterType(): LsiDeclaredType {
    return when (this) {
        LsiLanguage.JAVA -> LsiDeclaredType(OBJECT_TYPE_ID, nullability = LsiNullability.NULLABLE)
        LsiLanguage.KOTLIN -> LsiDeclaredType(ANY_TYPE_ID, nullability = LsiNullability.NULLABLE)
        LsiLanguage.UNKNOWN -> error("DTO object methods require Java or Kotlin target language")
    }
}

internal fun dtoEqualityPoetTypeNames(
    generatedTypeName: LsiClass? = null,
): List<LsiClass> {
    return buildList {
        add(OBJECT_TYPE_NAME)
        add(ANY_TYPE_NAME)
        add(OBJECTS_TYPE_NAME)
        add(ARRAYS_TYPE_NAME)
        generatedTypeName?.let(::add)
    }.distinctBy(LsiClass::id)
}

private val INT_TYPE = LsiPrimitiveType(LsiPrimitiveKind.INT)
private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
private val OBJECT_TYPE_ID = LsiSymbolId.type("java.lang.Object")
private val ANY_TYPE_ID = LsiSymbolId.type("kotlin.Any")
private val OBJECTS_TYPE_ID = LsiSymbolId.type("java.util.Objects")
private val ARRAYS_TYPE_ID = LsiSymbolId.type("java.util.Arrays")
private val OBJECTS_TYPE = LsiDeclaredType(OBJECTS_TYPE_ID)
private val ARRAYS_TYPE = LsiDeclaredType(ARRAYS_TYPE_ID)

private val OBJECT_TYPE_NAME = LsiClass(OBJECT_TYPE_ID, "java.lang", listOf("Object"))
private val ANY_TYPE_NAME = LsiClass(ANY_TYPE_ID, "kotlin", listOf("Any"))
private val OBJECTS_TYPE_NAME = LsiClass(OBJECTS_TYPE_ID, "java.util", listOf("Objects"))
private val ARRAYS_TYPE_NAME = LsiClass(ARRAYS_TYPE_ID, "java.util", listOf("Arrays"))
