package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.JacksonFamily
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInputBuilderBuildStrategy
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.inputBuilderBackingType
import site.addzero.lsi.jimmer.dto.inputBuilderBuildStrategy
import site.addzero.lsi.jimmer.dto.inputBuilderBuiltDtoSetterNameOrNull
import site.addzero.lsi.jimmer.dto.inputBuilderJsonNamingAnnotationOrNull
import site.addzero.lsi.jimmer.dto.inputBuilderLoadedStateNameOrNull
import site.addzero.lsi.jimmer.dto.inputBuilderParameterType
import site.addzero.lsi.jimmer.dto.inputBuilderPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.inputBuilderSetterJacksonAnnotationApplications
import site.addzero.lsi.jimmer.dto.inputBuilderSetterName
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentNameStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetAnnotation
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将冻结的 Input DTO 语义降低为平台中立的 Builder 源码结构。 */
internal fun DtoType.toInputBuilderPoetType(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    currentDtoType: LsiDeclaredType,
    generatedDtoTypes: Map<DtoTypeId, LsiDeclaredType>,
    jsonPojoBuilderAnnotationTypeId: LsiSymbolId,
    jsonNamingAnnotationTypeId: LsiSymbolId,
): LsiPoetType {
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "DTO InputBuilder requires Java or Kotlin target language"
    }
    currentDtoType.declarationId.requireTypeQualifiedName()
    jsonPojoBuilderAnnotationTypeId.requireTypeQualifiedName()
    jsonNamingAnnotationTypeId.requireTypeQualifiedName()
    val builderType = LsiDeclaredType(
        LsiSymbolId.type("${currentDtoType.declarationId.requireTypeQualifiedName()}.Builder"),
    )
    val generatedDtoType: (DtoType) -> LsiDeclaredType = { type ->
        if (type.id == id) {
            currentDtoType
        } else {
            requireNotNull(generatedDtoTypes[type.id]) {
                "Missing generated DTO type for InputBuilder property: ${type.id.value}"
            }
        }
    }
    val props = inputBuilderPropsInDeclarationOrder(graph)
    val members = buildList {
        props.forEach { prop ->
            add(prop.inputBuilderStorageMember(graph, immutableSchema, targetLanguage, generatedDtoType))
            prop.inputBuilderLoadedStateMemberOrNull(graph, targetLanguage)?.let(::add)
        }
        props.mapTo(this) { prop ->
            prop.inputBuilderSetter(
                graph = graph,
                immutableSchema = immutableSchema,
                annotationContract = annotationContract,
                targetLanguage = targetLanguage,
                builderType = builderType,
                generatedDtoType = generatedDtoType,
            )
        }
        add(
            inputBuilderBuildFunction(
                graph = graph,
                targetLanguage = targetLanguage,
                currentDtoType = currentDtoType,
                props = props,
            ),
        )
    }
    return LsiPoetType(
        name = "Builder",
        kind = LsiTypeDeclarationKind.CLASS,
        annotations = inputBuilderTypeAnnotations(
            graph = graph,
            annotationContract = annotationContract,
            targetLanguage = targetLanguage,
            jsonPojoBuilderAnnotationTypeId = jsonPojoBuilderAnnotationTypeId,
            jsonNamingAnnotationTypeId = jsonNamingAnnotationTypeId,
        ),
        modifiers = if (targetLanguage == LsiLanguage.JAVA) {
            setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.STATIC)
        } else {
            emptySet()
        },
        members = members,
    )
}

private fun DtoType.inputBuilderTypeAnnotations(
    graph: DtoGraph,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    jsonPojoBuilderAnnotationTypeId: LsiSymbolId,
    jsonNamingAnnotationTypeId: LsiSymbolId,
): List<LsiPoetAnnotation> = buildList {
    if (targetLanguage == LsiLanguage.KOTLIN) {
        add(LsiPoetAnnotation(GENERATED_BY_ANNOTATION_TYPE_ID))
    }
    add(
        LsiPoetAnnotation(
            type = jsonPojoBuilderAnnotationTypeId,
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "withPrefix",
                    value = LsiPoetAnnotationValue.StringValue(""),
                ),
            ),
        ),
    )
    inputBuilderJsonNamingAnnotationOrNull(
        graph = graph,
        annotationContract = annotationContract,
        jsonNamingAnnotationTypeId = jsonNamingAnnotationTypeId,
    )?.let { annotation ->
        val poetAnnotation = annotation.toLsiPoetAnnotation()
        add(
            poetAnnotation.copy(
                arguments = poetAnnotation.arguments.map { argument ->
                    if (argument is LsiPoetAnnotationArgument.Named) {
                        argument.copy(nameStyle = LsiPoetAnnotationArgumentNameStyle.VERBATIM)
                    } else {
                        argument
                    }
                },
            ),
        )
    }
}

private fun DtoProp.inputBuilderStorageMember(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    generatedDtoType: (DtoType) -> LsiDeclaredType,
): LsiPoetMember {
    val type = inputBuilderBackingType(graph, immutableSchema, targetLanguage, generatedDtoType)
    return if (targetLanguage == LsiLanguage.JAVA) {
        LsiPoetField(
            name = name,
            type = type,
            modifiers = setOf(LsiPoetModifier.PRIVATE),
        )
    } else {
        LsiPoetProperty(
            name = name,
            type = type,
            mutable = true,
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            initializer = code {
                literal(if (type.isNullableBuilderStorage()) "null" else "false")
            },
        )
    }
}

private fun DtoProp.inputBuilderLoadedStateMemberOrNull(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
): LsiPoetMember? {
    val stateName = inputBuilderLoadedStateNameOrNull(graph, targetLanguage) ?: return null
    return if (targetLanguage == LsiLanguage.JAVA) {
        LsiPoetField(
            name = stateName,
            type = BOOLEAN_TYPE,
            modifiers = setOf(LsiPoetModifier.PRIVATE),
        )
    } else {
        LsiPoetProperty(
            name = stateName,
            type = BOOLEAN_TYPE,
            mutable = true,
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            initializer = code { literal("false") },
        )
    }
}

private fun DtoProp.inputBuilderSetter(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
    targetLanguage: LsiLanguage,
    builderType: LsiDeclaredType,
    generatedDtoType: (DtoType) -> LsiDeclaredType,
): LsiPoetFunction {
    val parameterType = inputBuilderParameterType(graph, immutableSchema, targetLanguage, generatedDtoType)
    val stateName = inputBuilderLoadedStateNameOrNull(graph, targetLanguage)
    return LsiPoetFunction(
        name = inputBuilderSetterName(),
        annotations = inputBuilderSetterJacksonAnnotationApplications(graph, annotationContract).map { application ->
            val dtoSourceAnnotation = if (application.origin == DtoAnnotationOrigin.DTO) {
                annotations.firstOrNull { annotation -> annotation.typeId == application.annotation.type }
                    ?: error(
                        "DTO builder annotation application has no matching source annotation: " +
                            application.annotation.type.value
                    )
            } else {
                null
            }
            application.toDtoPoetAnnotation(
                dtoSourceAnnotation = dtoSourceAnnotation,
                annotationContract = annotationContract,
                targetLanguage = targetLanguage,
            )
        },
        modifiers = if (targetLanguage == LsiLanguage.JAVA) {
            setOf(LsiPoetModifier.PUBLIC)
        } else {
            emptySet()
        },
        parameters = listOf(LsiPoetParameter(name, parameterType)),
        returnType = builderType,
        body = code {
            if (targetLanguage == LsiLanguage.JAVA && !nullable) {
                statement {
                    text("this.")
                    name(name)
                    text(" = ")
                    type(OBJECTS_TYPE)
                    text(".requireNonNull(")
                    name(name)
                    text(", ")
                    string("The property \"$name\" cannot be null")
                    text(")")
                }
            } else {
                statement {
                    text("this.")
                    name(name)
                    text(" = ")
                    name(name)
                }
            }
            if (stateName != null) {
                statement {
                    text("this.")
                    name(stateName)
                    text(" = true")
                }
            }
            returnValue { text("this") }
        },
    )
}

private fun DtoType.inputBuilderBuildFunction(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
    currentDtoType: LsiDeclaredType,
    props: List<DtoProp>,
): LsiPoetFunction {
    return LsiPoetFunction(
        name = "build",
        modifiers = if (targetLanguage == LsiLanguage.JAVA) {
            setOf(LsiPoetModifier.PUBLIC)
        } else {
            emptySet()
        },
        returnType = currentDtoType,
        body = if (targetLanguage == LsiLanguage.JAVA) {
            javaInputBuilderBuildBody(graph, currentDtoType, props)
        } else {
            kotlinInputBuilderBuildBody(graph, currentDtoType, props)
        },
        bodyStyle = if (targetLanguage == LsiLanguage.KOTLIN) {
            LsiPoetBodyStyle.EXPRESSION
        } else {
            LsiPoetBodyStyle.BLOCK
        },
    )
}

private fun javaInputBuilderBuildBody(
    graph: DtoGraph,
    currentDtoType: LsiDeclaredType,
    props: List<DtoProp>,
): LsiPoetCodeBlock = code {
    statement {
        type(currentDtoType)
        text(" _input = new ")
        type(currentDtoType)
        text("()")
    }
    props.forEach { prop ->
        val setterName = requireNotNull(prop.inputBuilderBuiltDtoSetterNameOrNull(LsiLanguage.JAVA))
        val stateName = prop.inputBuilderLoadedStateNameOrNull(graph, LsiLanguage.JAVA)
        when (prop.inputBuilderBuildStrategy(graph, LsiLanguage.JAVA)) {
            DtoInputBuilderBuildStrategy.JAVA_REQUIRE_NON_NULL_AND_SET -> {
                beginControlFlow {
                    text("if (")
                    name(prop.name)
                    text(" == null)")
                }
                unknownPropertyThrow(currentDtoType, prop.name, nullable = false, LsiLanguage.JAVA)
                endControlFlow()
                inputSetterStatement(setterName, prop.name)
            }
            DtoInputBuilderBuildStrategy.JAVA_REQUIRE_LOADED_AND_SET -> {
                beginControlFlow {
                    text("if (!")
                    name(requireNotNull(stateName))
                    text(")")
                }
                unknownPropertyThrow(currentDtoType, prop.name, nullable = true, LsiLanguage.JAVA)
                endControlFlow()
                inputSetterStatement(setterName, prop.name)
            }
            DtoInputBuilderBuildStrategy.JAVA_SET -> inputSetterStatement(setterName, prop.name)
            DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_LOADED -> {
                beginControlFlow {
                    text("if (")
                    name(requireNotNull(stateName))
                    text(")")
                }
                inputSetterStatement(setterName, prop.name)
                endControlFlow()
            }
            DtoInputBuilderBuildStrategy.JAVA_SET_WHEN_NON_NULL -> {
                beginControlFlow {
                    text("if (")
                    name(prop.name)
                    text(" != null)")
                }
                inputSetterStatement(setterName, prop.name)
                endControlFlow()
            }
            else -> error("Java InputBuilder received a Kotlin build strategy")
        }
    }
    returnValue { name("_input") }
}

private fun LsiPoetCodeBuilder.kotlinInputBuilderBuildBody(
    graph: DtoGraph,
    currentDtoType: LsiDeclaredType,
    props: List<DtoProp>,
) {
    type(currentDtoType)
    text("(")
    line()
    indent {
        props.forEach { prop ->
            val stateName = prop.inputBuilderLoadedStateNameOrNull(graph, LsiLanguage.KOTLIN)
            when (prop.inputBuilderBuildStrategy(graph, LsiLanguage.KOTLIN)) {
                DtoInputBuilderBuildStrategy.KOTLIN_REQUIRE_NON_NULL_ARGUMENT -> {
                    kotlinRequiredArgument(currentDtoType, prop.name)
                    text(",")
                    line()
                }
                DtoInputBuilderBuildStrategy.KOTLIN_REQUIRE_LOADED_ARGUMENT -> {
                    text("// FIXED")
                    line()
                    text("if (!")
                    name(requireNotNull(stateName))
                    text(") {")
                    line()
                    indent {
                        kotlinUnknownPropertyThrow(currentDtoType, prop.name, nullable = true)
                    }
                    text("} else {")
                    line()
                    indent {
                        kotlinValueArgument(currentDtoType, prop)
                    }
                    text("}")
                    line()
                    text(",")
                    line()
                }
                DtoInputBuilderBuildStrategy.KOTLIN_ARGUMENT -> {
                    kotlinValueArgument(currentDtoType, prop)
                    text(",")
                    line()
                }
                DtoInputBuilderBuildStrategy.KOTLIN_ARGUMENT_WITH_LOADED_STATE -> {
                    text("// DYNAMIC")
                    line()
                    kotlinValueArgument(currentDtoType, prop)
                    text(",")
                    line()
                    name(requireNotNull(stateName))
                    text(",")
                    line()
                }
                else -> error("Kotlin InputBuilder received a Java build strategy")
            }
        }
    }
    text(")")
}

private fun kotlinInputBuilderBuildBody(
    graph: DtoGraph,
    currentDtoType: LsiDeclaredType,
    props: List<DtoProp>,
): LsiPoetCodeBlock = code {
    preserveExplicitIndentation()
    kotlinInputBuilderBuildBody(graph, currentDtoType, props)
}

private fun LsiPoetCodeBuilder.kotlinValueArgument(
    currentDtoType: LsiDeclaredType,
    prop: DtoProp,
) {
    if (prop.nullable) {
        name(prop.name)
    } else {
        kotlinRequiredArgument(currentDtoType, prop.name)
    }
}

private fun LsiPoetCodeBuilder.kotlinRequiredArgument(
    currentDtoType: LsiDeclaredType,
    propName: String,
) {
    name(propName)
    text(" ?: throw ")
    type(INPUT_TYPE)
    text(".unknownNonNullProperty(")
    type(currentDtoType)
    text("::class.java, ")
    string(propName)
    text(")")
}

private fun LsiPoetCodeBuilder.kotlinUnknownPropertyThrow(
    currentDtoType: LsiDeclaredType,
    propName: String,
    nullable: Boolean,
) {
    text("throw ")
    type(INPUT_TYPE)
    text(if (nullable) ".unknownNullableProperty(" else ".unknownNonNullProperty(")
    type(currentDtoType)
    text("::class.java, ")
    string(propName)
    text(")")
}

private fun LsiPoetCodeBuilder.unknownPropertyThrow(
    currentDtoType: LsiDeclaredType,
    propName: String,
    nullable: Boolean,
    targetLanguage: LsiLanguage,
) {
    statement {
        text("throw ")
        type(INPUT_TYPE)
        text(if (nullable) ".unknownNullableProperty(" else ".unknownNonNullProperty(")
        type(currentDtoType)
        text(if (targetLanguage == LsiLanguage.JAVA) ".class, " else "::class.java, ")
        string(propName)
        text(")")
    }
}

private fun LsiPoetCodeBuilder.inputSetterStatement(
    setterName: String,
    propName: String,
) {
    statement {
        name("_input")
        text(".")
        name(setterName)
        text("(")
        name(propName)
        text(")")
    }
}

private fun LsiTypeRef.isNullableBuilderStorage(): Boolean {
    return nullability == LsiNullability.NULLABLE
}

private fun code(block: LsiPoetCodeBuilder.() -> Unit): LsiPoetCodeBlock = LsiPoetCodeBlock.build(block)

/** 为嵌入式 InputBuilder 解析完整且精确的源码类型名称表。 */
internal fun LsiWorkspace.inputBuilderPoetTypeNames(
    inputBuilderType: LsiPoetType,
    currentDtoTypeName: LsiPoetTypeName,
    generatedDtoTypeNames: Collection<LsiPoetTypeName>,
    jacksonVersion: JacksonFamily,
): List<LsiPoetTypeName> {
    val builderTypeName = JimmerDtoPoetTypeNames.create(
        currentDtoTypeName.packageName,
        currentDtoTypeName.simpleNames + "Builder",
    )
    val additionalByTypeId = linkedMapOf<LsiSymbolId, LsiPoetTypeName>()
    fun add(typeName: LsiPoetTypeName) {
        val previous = additionalByTypeId.putIfAbsent(typeName.typeId, typeName)
        require(previous == null || previous == typeName) {
            "InputBuilder type '${typeName.typeId.value}' has conflicting exact source names"
        }
    }
    generatedDtoTypeNames.forEach(::add)
    add(currentDtoTypeName)
    add(builderTypeName)
    DTO_COMMON_POET_TYPE_NAMES.forEach(::add)
    jacksonVersion.inputBuilderJacksonPoetTypeNames().forEach(::add)
    return toLsiPoetTypeNames(
        typeIds = inputBuilderType.referencedTypeIds,
        additional = additionalByTypeId.values,
    )
}

internal fun JacksonFamily.inputBuilderJsonPojoBuilderAnnotationTypeId(): LsiSymbolId {
    return when (this) {
        JacksonFamily.JACKSON_2 -> JACKSON_2_JSON_POJO_BUILDER_TYPE_ID
        JacksonFamily.JACKSON_3 -> JACKSON_3_JSON_POJO_BUILDER_TYPE_ID
    }
}

internal fun JacksonFamily.inputBuilderJsonNamingAnnotationTypeId(): LsiSymbolId {
    return when (this) {
        JacksonFamily.JACKSON_2 -> JACKSON_2_JSON_NAMING_TYPE_ID
        JacksonFamily.JACKSON_3 -> JACKSON_3_JSON_NAMING_TYPE_ID
    }
}

private fun JacksonFamily.inputBuilderJacksonPoetTypeNames(): List<LsiPoetTypeName> {
    return when (this) {
        JacksonFamily.JACKSON_2 -> JACKSON_2_INPUT_BUILDER_POET_TYPE_NAMES
        JacksonFamily.JACKSON_3 -> JACKSON_3_INPUT_BUILDER_POET_TYPE_NAMES
    }
}

private val BOOLEAN_TYPE = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)
private val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
private val OBJECTS_TYPE_ID = LsiSymbolId.type("java.util.Objects")
private val GENERATED_BY_ANNOTATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val INPUT_TYPE = LsiDeclaredType(INPUT_TYPE_ID)
private val OBJECTS_TYPE = LsiDeclaredType(OBJECTS_TYPE_ID)

private val JACKSON_2_JSON_POJO_BUILDER_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder")
private val JACKSON_2_JSON_NAMING_TYPE_ID =
    LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonNaming")
private val JACKSON_3_JSON_POJO_BUILDER_TYPE_ID =
    LsiSymbolId.type("tools.jackson.databind.annotation.JsonPOJOBuilder")
private val JACKSON_3_JSON_NAMING_TYPE_ID =
    LsiSymbolId.type("tools.jackson.databind.annotation.JsonNaming")

private val JACKSON_2_INPUT_BUILDER_POET_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create(
        "com.fasterxml.jackson.databind.annotation",
        listOf("JsonPOJOBuilder"),
    ),
    JimmerDtoPoetTypeNames.create(
        "com.fasterxml.jackson.databind.annotation",
        listOf("JsonNaming"),
    ),
)

private val JACKSON_3_INPUT_BUILDER_POET_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create(
        "tools.jackson.databind.annotation",
        listOf("JsonPOJOBuilder"),
    ),
    JimmerDtoPoetTypeNames.create(
        "tools.jackson.databind.annotation",
        listOf("JsonNaming"),
    ),
)
