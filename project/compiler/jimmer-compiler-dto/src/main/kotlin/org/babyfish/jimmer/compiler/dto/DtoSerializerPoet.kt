package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.requiresDynamicInputSerialization
import site.addzero.lsi.jimmer.dto.serializerLoadedAccessorNameOrNull
import site.addzero.lsi.jimmer.dto.serializerPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.dtoValueAccessorName
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeName

internal fun DtoType.toSerializerPoetType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    jacksonVersion: JacksonFamily,
    dtoType: LsiDeclaredType,
): LsiPoetType {
    require(requiresDynamicInputSerialization(graph)) {
        "DTO type does not require dynamic input serialization: ${id.value}"
    }
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO Serializer requires Java or Kotlin target language"
    }
    return LsiPoetType(
        name = "Serializer",
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = if (targetLanguage == LsiLanguage.JAVA) {
            setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.STATIC)
        } else {
            emptySet()
        },
        superClass = LsiDeclaredType(
            declarationId = jacksonVersion.serializerTypeId(),
            arguments = listOf(LsiTypeArgument.invariant(dtoType)),
        ),
        members = listOf(
            LsiPoetFunction(
                name = "serialize",
                modifiers = if (targetLanguage == LsiLanguage.JAVA) {
                    setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE)
                } else {
                    setOf(LsiPoetModifier.OVERRIDE)
                },
                parameters = listOf(
                    LsiPoetParameter("input", dtoType),
                    LsiPoetParameter("gen", LsiDeclaredType(jacksonVersion.generatorTypeId())),
                    LsiPoetParameter("provider", LsiDeclaredType(jacksonVersion.providerTypeId())),
                ),
                thrownTypes = if (
                    targetLanguage == LsiLanguage.JAVA &&
                    jacksonVersion == JacksonFamily.JACKSON_2
                ) {
                    listOf(LsiDeclaredType(JAVA_IO_EXCEPTION_TYPE_ID))
                } else {
                    emptyList()
                },
                body = serializerBody(
                    graph = graph,
                    immutableSchema = immutableSchema,
                    targetLanguage = targetLanguage,
                    jacksonVersion = jacksonVersion,
                ),
            ),
        ),
    )
}

private fun DtoType.serializerBody(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    jacksonVersion: JacksonFamily,
): LsiPoetCodeBlock = LsiPoetCodeBlock.build {
    statement {
        name("gen")
        text(".writeStartObject()")
    }
    serializerPropsInDeclarationOrder(graph).forEach { prop ->
        val loadedAccessorName = prop.serializerLoadedAccessorNameOrNull()
        if (loadedAccessorName != null) {
            beginControlFlow {
                text("if (")
                name("input")
                text(".")
                name(loadedAccessorName)
                if (targetLanguage == LsiLanguage.JAVA) {
                    text("()")
                }
                text(")")
            }
        }
        statement {
            name("provider")
            text(".")
            name(jacksonVersion.defaultSerializeMethodName())
            text("(")
            string(prop.name)
            text(", ")
            name("input")
            text(".")
            name(
                prop.dtoValueAccessorName(
                    targetLanguage = targetLanguage,
                    graph = graph,
                    immutableSchema = immutableSchema,
                ),
            )
            if (targetLanguage == LsiLanguage.JAVA) {
                text("()")
            }
            text(", ")
            name("gen")
            text(")")
        }
        if (loadedAccessorName != null) {
            endControlFlow()
        }
    }
    statement {
        name("gen")
        text(".writeEndObject()")
    }
}

private fun JacksonFamily.serializerTypeId(): LsiSymbolId = when (this) {
    JacksonFamily.JACKSON_2 -> JACKSON_2_SERIALIZER_TYPE_ID
    JacksonFamily.JACKSON_3 -> JACKSON_3_SERIALIZER_TYPE_ID
}

private fun JacksonFamily.generatorTypeId(): LsiSymbolId = when (this) {
    JacksonFamily.JACKSON_2 -> JACKSON_2_GENERATOR_TYPE_ID
    JacksonFamily.JACKSON_3 -> JACKSON_3_GENERATOR_TYPE_ID
}

private fun JacksonFamily.providerTypeId(): LsiSymbolId = when (this) {
    JacksonFamily.JACKSON_2 -> JACKSON_2_PROVIDER_TYPE_ID
    JacksonFamily.JACKSON_3 -> JACKSON_3_PROVIDER_TYPE_ID
}

private fun JacksonFamily.defaultSerializeMethodName(): String = when (this) {
    JacksonFamily.JACKSON_2 -> "defaultSerializeField"
    JacksonFamily.JACKSON_3 -> "defaultSerializeProperty"
}

private val JACKSON_2_SERIALIZER_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.databind.JsonSerializer")
private val JACKSON_2_GENERATOR_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.core.JsonGenerator")
private val JACKSON_2_PROVIDER_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.databind.SerializerProvider")
private val JACKSON_3_SERIALIZER_TYPE_ID =
    LsiSymbolId.type("tools.jackson.databind.ValueSerializer")
private val JACKSON_3_GENERATOR_TYPE_ID =
    LsiSymbolId.type("tools.jackson.core.JsonGenerator")
private val JACKSON_3_PROVIDER_TYPE_ID =
    LsiSymbolId.type("tools.jackson.databind.SerializationContext")
private val JAVA_IO_EXCEPTION_TYPE_ID = LsiSymbolId.type("java.io.IOException")

internal fun JacksonFamily.serializerPoetTypeNames(
    dtoTypeName: LsiPoetTypeName,
): List<LsiPoetTypeName> {
    val jacksonTypeNames = when (this) {
        JacksonFamily.JACKSON_2 -> JACKSON_2_POET_TYPE_NAMES
        JacksonFamily.JACKSON_3 -> JACKSON_3_POET_TYPE_NAMES
    }
    return listOf(dtoTypeName) + jacksonTypeNames + JAVA_IO_EXCEPTION_POET_TYPE_NAME
}

private val JACKSON_2_POET_TYPE_NAMES = listOf(
    LsiPoetTypeName(
        JACKSON_2_SERIALIZER_TYPE_ID,
        "com.fasterxml.jackson.databind",
        listOf("JsonSerializer"),
    ),
    LsiPoetTypeName(
        JACKSON_2_GENERATOR_TYPE_ID,
        "com.fasterxml.jackson.core",
        listOf("JsonGenerator"),
    ),
    LsiPoetTypeName(
        JACKSON_2_PROVIDER_TYPE_ID,
        "com.fasterxml.jackson.databind",
        listOf("SerializerProvider"),
    ),
)
private val JACKSON_3_POET_TYPE_NAMES = listOf(
    LsiPoetTypeName(
        JACKSON_3_SERIALIZER_TYPE_ID,
        "tools.jackson.databind",
        listOf("ValueSerializer"),
    ),
    LsiPoetTypeName(
        JACKSON_3_GENERATOR_TYPE_ID,
        "tools.jackson.core",
        listOf("JsonGenerator"),
    ),
    LsiPoetTypeName(
        JACKSON_3_PROVIDER_TYPE_ID,
        "tools.jackson.databind",
        listOf("SerializationContext"),
    ),
)
private val JAVA_IO_EXCEPTION_POET_TYPE_NAME = LsiPoetTypeName(
    JAVA_IO_EXCEPTION_TYPE_ID,
    "java.io",
    listOf("IOException"),
)
