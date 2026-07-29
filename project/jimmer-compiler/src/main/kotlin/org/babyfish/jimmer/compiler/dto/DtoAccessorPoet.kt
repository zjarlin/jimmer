package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.immutable.toLsiGeneratedQueryPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.generatedDraftSlotName
import site.addzero.lsi.jimmer.dto.DtoAccessorConversionKind
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.accessorConversionKind
import site.addzero.lsi.jimmer.dto.accessorPath
import site.addzero.lsi.jimmer.dto.generatedElementValueType
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 把冻结 DTO 属性降低为完整的平台中立访问器初始化表达式。 */
internal fun DtoBaseProp.toAccessorInitializerPoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    targetLanguage: LsiLanguage,
    acceptNull: Boolean,
    withConverters: Boolean,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
): LsiPoetCodeBlock {
    val language = targetLanguage.requireDtoAccessorTargetLanguage()
    val ownerType = graph.typesById.getValue(ownerTypeId)
    val baseTypeId = requireNotNull(ownerType.baseTypeId) {
        "DTO accessor requires an immutable base type: ${ownerType.id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "DTO accessor immutable base type does not exist: ${baseTypeId.value}"
    }
    val path = accessorPath(graph, immutableSchema)
    val tailProp = tailProp(graph)
    val tailImmutableProp = path.last()
    val specification = DtoModifier.SPECIFICATION in ownerType.modifiers
    return LsiPoetCodeBlock.build {
        if (language == LsiLanguage.JAVA) {
            text("new ")
        }
        type(DTO_PROP_ACCESSOR_TYPE)
        text("(")
        indent {
            line()
            literal(acceptNull.toString())
            text(",")
            line()
            accessorSlotPath(
                path = path,
                baseType = baseType,
                immutableSchema = immutableSchema,
                workspace = workspace,
                targetLanguage = language,
            )
            if (withConverters) {
                accessorConverters(
                    prop = this@toAccessorInitializerPoetCodeBlock,
                    graph = graph,
                    immutableSchema = immutableSchema,
                    targetLanguage = language,
                    specification = specification,
                    tailProp = tailProp,
                    tailImmutableProp = tailImmutableProp,
                    generatedTargetType = generatedTargetType,
                )
            }
        }
        line()
        text(")")
    }
}

/** 解析访问器初始化表达式引用的运行时、Draft 与生成 DTO 精确源码名称。 */
internal fun LsiWorkspace.dtoAccessorPoetTypeNames(
    initializer: LsiPoetCodeBlock,
    immutableSchema: ImmutableSchema,
    generatedTypeNames: Collection<LsiPoetTypeName> = emptyList(),
): List<LsiPoetTypeName> {
    val candidateTypeNames = buildList {
        addAll(DTO_COMMON_POET_TYPE_NAMES)
        add(DTO_PROP_ACCESSOR_TYPE_NAME)
        addAll(immutableSchema.toLsiGeneratedQueryPoetTypeNames())
        immutableSchema.types.forEach { type ->
            add(type.dtoDraftSlotOwnerPoetTypeName("Producer"))
            add(type.dtoDraftSlotOwnerPoetTypeName("$"))
        }
        addAll(generatedTypeNames)
    }
    val conflictingTypeNames = candidateTypeNames
        .groupBy(LsiPoetTypeName::typeId)
        .filterValues { names -> names.distinct().size > 1 }
    require(conflictingTypeNames.isEmpty()) {
        "DTO accessor Poet type ids have conflicting source names: " +
            conflictingTypeNames.keys.sorted().joinToString { typeId -> typeId.value }
    }
    val additionalTypeNames = candidateTypeNames.distinctBy(LsiPoetTypeName::typeId)
    return toLsiPoetTypeNames(
        typeIds = initializer.referencedTypeIds,
        additional = additionalTypeNames,
    )
}

private fun LsiPoetCodeBuilder.accessorSlotPath(
    path: List<ImmutableProp>,
    baseType: ImmutableType,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    targetLanguage: LsiLanguage,
) {
    val singleSlot = path.size == 1
    text(if (targetLanguage == LsiLanguage.JAVA) "new int[] {" else "intArrayOf(")
    if (singleSlot) {
        if (targetLanguage == LsiLanguage.JAVA) {
            text(" ")
        }
        accessorSlot(
            prop = path.single(),
            ownerType = baseType,
            workspace = workspace,
            targetLanguage = targetLanguage,
        )
        text(if (targetLanguage == LsiLanguage.JAVA) " }" else ")")
        return
    }
    indent {
        path.forEachIndexed { index, prop ->
            if (index != 0) {
                text(",")
            }
            line()
            val ownerType = requireNotNull(immutableSchema.typesById[prop.declaringTypeId]) {
                "DTO accessor path declaring type does not exist: ${prop.declaringTypeId.value}"
            }
            accessorSlot(prop, ownerType, workspace, targetLanguage)
        }
    }
    line()
    text(if (targetLanguage == LsiLanguage.JAVA) "}" else ")")
}

private fun LsiPoetCodeBuilder.accessorSlot(
    prop: ImmutableProp,
    ownerType: ImmutableType,
    workspace: LsiWorkspace,
    targetLanguage: LsiLanguage,
) {
    type(ownerType.dtoDraftSlotOwnerType(targetLanguage))
    text(".")
    name(prop.generatedDraftSlotName(workspace))
}

private fun LsiPoetCodeBuilder.accessorConverters(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    specification: Boolean,
    tailProp: DtoBaseProp,
    tailImmutableProp: ImmutableProp,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
) {
    val conversionKind = prop.accessorConversionKind(graph, immutableSchema)
    when (conversionKind) {
        DtoAccessorConversionKind.NONE -> Unit
        DtoAccessorConversionKind.ASSOCIATED_ID -> {
            text(",")
            line()
            if (specification) {
                literal("null")
            } else {
                associatedIdConverter(prop, graph, immutableSchema, targetLanguage, tailImmutableProp, getter = true)
                text(",")
                line()
                associatedIdConverter(prop, graph, immutableSchema, targetLanguage, tailImmutableProp, getter = false)
            }
        }
        DtoAccessorConversionKind.OBJECT_CONSTRUCTOR,
        DtoAccessorConversionKind.OBJECT_METADATA,
        -> {
            text(",")
            line()
            if (specification) {
                literal("null")
            } else {
                objectConverters(
                    prop = prop,
                    graph = graph,
                    immutableSchema = immutableSchema,
                    targetLanguage = targetLanguage,
                    tailProp = tailProp,
                    tailImmutableProp = tailImmutableProp,
                    metadata = conversionKind == DtoAccessorConversionKind.OBJECT_METADATA,
                    generatedTargetType = generatedTargetType,
                )
            }
        }
        DtoAccessorConversionKind.ENUM -> {
            text(",")
            line()
            if (specification) {
                literal("null")
            } else {
                add(prop.toEnumToScalarLambdaPoetCodeBlock(targetLanguage, graph, immutableSchema))
            }
            text(",")
            line()
            add(prop.toScalarToEnumLambdaPoetCodeBlock(targetLanguage, graph, immutableSchema))
        }
        DtoAccessorConversionKind.CONVERTER -> {
            text(",")
            line()
            scalarConverter(prop, graph, immutableSchema, targetLanguage, output = true)
            text(",")
            line()
            scalarConverter(prop, graph, immutableSchema, targetLanguage, output = false)
        }
    }
}

private fun LsiPoetCodeBuilder.associatedIdConverter(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    tailImmutableProp: ImmutableProp,
    getter: Boolean,
) {
    val targetTypeId = requireNotNull(tailImmutableProp.targetTypeId) {
        "Associated-id DTO accessor requires an immutable association target: ${prop.id.value}"
    }
    type(DTO_PROP_ACCESSOR_TYPE)
    text(".")
    name(
        buildString {
            append("id")
            if (tailImmutableProp.list) {
                append("List")
            } else {
                append("Reference")
            }
            append(if (getter) "Getter" else "Setter")
        }
    )
    text("(")
    type(LsiDeclaredType(targetTypeId))
    text(if (targetLanguage == LsiLanguage.JAVA) ".class, " else "::class.java, ")
    add(prop.converterLoading(graph, immutableSchema, targetLanguage, forList = false))
    text(")")
}

private fun LsiPoetCodeBuilder.objectConverters(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    tailProp: DtoBaseProp,
    tailImmutableProp: ImmutableProp,
    metadata: Boolean,
    generatedTargetType: (DtoProp) -> LsiDeclaredType,
) {
    val immutableTargetTypeId = requireNotNull(tailImmutableProp.targetTypeId) {
        "Object DTO accessor requires an immutable target: ${prop.id.value}"
    }
    val immutableTargetType = LsiDeclaredType(immutableTargetTypeId)
    val dtoElementType = prop.generatedElementValueType(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
        generatedTargetType = generatedTargetType,
    )
    objectAccessorCall(
        methodName = if (tailImmutableProp.list) "objectListGetter" else "objectReferenceGetter",
        immutableTargetType = immutableTargetType,
        dtoElementType = dtoElementType,
        targetLanguage = targetLanguage,
        explicitTypeArguments = true,
    )
    if (metadata) {
        text("(")
        type(dtoElementType)
        text(if (targetLanguage == LsiLanguage.JAVA) ".METADATA.getConverter())" else ".METADATA.converter)")
    } else if (targetLanguage == LsiLanguage.JAVA) {
        text("(")
        type(dtoElementType)
        text("::new)")
    } else {
        text(" {")
        indent {
            line()
            type(dtoElementType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
            text("(it)")
        }
        line()
        text("}")
    }
    text(",")
    line()
    objectAccessorCall(
        methodName = if (tailImmutableProp.list) "objectListSetter" else "objectReferenceSetter",
        immutableTargetType = immutableTargetType,
        dtoElementType = dtoElementType,
        targetLanguage = targetLanguage,
        explicitTypeArguments = targetLanguage == LsiLanguage.KOTLIN,
    )
    val setterName = when {
        tailProp.targetTypeReference != null -> "toImmutable"
        immutableSchema.typesById.getValue(immutableTargetTypeId).kind == ImmutableTypeKind.ENTITY -> "toEntity"
        else -> "toImmutable"
    }
    if (targetLanguage == LsiLanguage.JAVA) {
        text("(")
        type(dtoElementType)
        text("::")
        name(setterName)
        text(")")
    } else {
        text(" {")
        indent {
            line()
            text("it.")
            name(setterName)
            text("()")
        }
        line()
        text("}")
    }
}

private fun LsiPoetCodeBuilder.objectAccessorCall(
    methodName: String,
    immutableTargetType: LsiTypeRef,
    dtoElementType: LsiTypeRef,
    targetLanguage: LsiLanguage,
    explicitTypeArguments: Boolean,
) {
    type(DTO_PROP_ACCESSOR_TYPE)
    text(".")
    if (targetLanguage == LsiLanguage.JAVA && explicitTypeArguments) {
        text("<")
        type(immutableTargetType)
        text(", ")
        type(dtoElementType)
        text(">")
    }
    name(methodName)
    if (targetLanguage == LsiLanguage.KOTLIN && explicitTypeArguments) {
        text("<")
        type(immutableTargetType)
        text(", ")
        type(dtoElementType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
        text(">")
    }
}

private fun LsiPoetCodeBuilder.scalarConverter(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    output: Boolean,
) {
    if (targetLanguage == LsiLanguage.JAVA) {
        text("arg -> ")
    } else {
        text("{ ")
    }
    add(prop.converterLoading(graph, immutableSchema, targetLanguage, forList = true))
    text(if (output) ".output(" else ".input(")
    text(if (targetLanguage == LsiLanguage.JAVA) "arg)" else "it) }")
}

private fun DtoBaseProp.converterLoading(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    forList: Boolean,
): LsiPoetCodeBlock {
    return toLsiConverterLoadingPoetCodeBlock(
        graph = graph,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
        forList = forList,
        typeArguments = if (targetLanguage == LsiLanguage.KOTLIN) {
            listOf(KOTLIN_ANY_TYPE, KOTLIN_ANY_TYPE)
        } else {
            emptyList()
        },
    )
}

private fun ImmutableType.dtoDraftSlotOwnerType(targetLanguage: LsiLanguage): LsiDeclaredType {
    val nestedName = if (targetLanguage == LsiLanguage.JAVA) "Producer" else "$"
    return LsiDeclaredType(LsiSymbolId.type("${qualifiedName}Draft.$nestedName"))
}

private fun ImmutableType.dtoDraftSlotOwnerPoetTypeName(nestedName: String): LsiPoetTypeName {
    val packageName = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleName = qualifiedName.substringAfterLast('.')
    return JimmerDtoPoetTypeNames.create(
        packageName,
        listOf("${simpleName}Draft", nestedName),
    )
}

private fun LsiLanguage.requireDtoAccessorTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO accessor source generation requires Java or Kotlin: $this"
    }
    return this
}

private val DTO_PROP_ACCESSOR_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "org.babyfish.jimmer.impl.util",
    listOf("DtoPropAccessor"),
)

private val DTO_PROP_ACCESSOR_TYPE = LsiDeclaredType(DTO_PROP_ACCESSOR_TYPE_NAME.typeId)

private val KOTLIN_ANY_TYPE = LsiDeclaredType(LsiSymbolId.type("kotlin.Any"))
