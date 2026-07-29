package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.immutable.toLsiGeneratedQueryPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedPropsConstantName
import site.addzero.lsi.jimmer.generatedPropsTypeOf
import site.addzero.lsi.jimmer.generatedTableType
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.dtoValueAccessorName
import site.addzero.lsi.jimmer.dto.hasSpecificationTarget
import site.addzero.lsi.jimmer.dto.isNestedSpecificationFragment
import site.addzero.lsi.jimmer.dto.propsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.requiresSpecificationConverter
import site.addzero.lsi.jimmer.dto.specificationArgumentProps
import site.addzero.lsi.jimmer.dto.specificationBaseType
import site.addzero.lsi.jimmer.dto.specificationConverterInputType
import site.addzero.lsi.jimmer.dto.specificationConverterName
import site.addzero.lsi.jimmer.dto.specificationConverterOutputType
import site.addzero.lsi.jimmer.dto.specificationLikeOptionArguments
import site.addzero.lsi.jimmer.dto.specificationOperationName
import site.addzero.lsi.jimmer.dto.specificationPath
import site.addzero.lsi.jimmer.dto.specificationTargetIsEntityAssociation
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.jimmer.dto.usesSpecificationPropArrayArgument
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将冻结的 Specification 基础类型语义降低为平台中立的 entityType 函数。 */
internal fun DtoType.toLsiSpecificationEntityTypePoetFunction(
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiPoetFunction {
    val language = targetLanguage.requireSpecificationTargetLanguage()
    val baseType = LsiDeclaredType(specificationBaseType(immutableSchema).id)
    val modifiers = buildSet {
        if (language == LsiLanguage.JAVA) {
            add(LsiPoetModifier.PUBLIC)
        }
        if (!isNestedSpecificationFragment(immutableSchema)) {
            add(LsiPoetModifier.OVERRIDE)
        }
    }
    return LsiPoetFunction(
        name = "entityType",
        modifiers = modifiers,
        returnType = LsiDeclaredType(
            declarationId = CLASS_TYPE_ID,
            arguments = listOf(LsiTypeArgument.invariant(baseType)),
        ),
        body = LsiPoetCodeBlock.build {
            returnValue {
                type(baseType)
                text(if (language == LsiLanguage.JAVA) ".class" else "::class.java")
            }
        },
        bodyStyle = LsiPoetBodyStyle.BLOCK,
    )
}

/** 将冻结的 Specification 谓词语义降低为平台中立的 applyTo 函数。 */
internal fun DtoType.toLsiSpecificationApplyToPoetFunction(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiPoetFunction {
    val language = targetLanguage.requireSpecificationTargetLanguage()
    require(DtoModifier.SPECIFICATION in modifiers) {
        "DTO applyTo lowering requires a specification type: ${id.value}"
    }
    val baseType = specificationBaseType(immutableSchema)
    val nested = isNestedSpecificationFragment(immutableSchema)
    val argsName = "args"
    val applierName = if (language == LsiLanguage.JAVA) "__applier" else "_applier"
    val body = LsiPoetCodeBlock.build {
        if (!nested) {
            declareSpecificationApplier(language, argsName, applierName)
        }
        var path = emptyList<ImmutableProp>()
        propsInDeclarationOrder(graph).forEach { prop ->
            when (prop) {
                is DtoBaseProp -> {
                    val nextPath = prop.specificationPath(graph, immutableSchema)
                    changeSpecificationPath(path, nextPath, immutableSchema, applierName)
                    path = nextPath
                    if (prop.hasSpecificationTarget(graph)) {
                        appendSpecificationTarget(
                            prop = prop,
                            graph = graph,
                            immutableSchema = immutableSchema,
                            targetLanguage = language,
                            nested = nested,
                            argsName = argsName,
                            applierName = applierName,
                        )
                    } else {
                        appendSpecificationOperation(
                            prop = prop,
                            graph = graph,
                            immutableSchema = immutableSchema,
                            targetLanguage = language,
                            applierName = applierName,
                        )
                    }
                }
                is DtoFoldProp -> {
                    changeSpecificationPath(path, emptyList(), immutableSchema, applierName)
                    path = emptyList()
                    appendSpecificationFold(
                        prop = prop,
                        targetLanguage = language,
                        nested = nested,
                        argsName = argsName,
                        applierName = applierName,
                    )
                }
                is DtoUserProp -> Unit
            }
        }
        changeSpecificationPath(path, emptyList(), immutableSchema, applierName)
    }
    val modifiers = buildSet {
        if (language == LsiLanguage.JAVA) {
            add(LsiPoetModifier.PUBLIC)
        }
        if (!nested) {
            add(LsiPoetModifier.OVERRIDE)
        }
    }
    return LsiPoetFunction(
        name = "applyTo",
        modifiers = modifiers,
        parameters = listOf(
            LsiPoetParameter(
                name = if (nested) applierName else argsName,
                type = specificationApplyToParameterType(language, baseType, nested),
            ),
        ),
        body = body,
        bodyStyle = LsiPoetBodyStyle.BLOCK,
    )
}

/** 将冻结的 Specification converter 语义降低为平台中立函数。 */
internal fun DtoBaseProp.toLsiSpecificationConverterPoetFunctionOrNull(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): LsiPoetFunction? {
    val language = targetLanguage.requireSpecificationTargetLanguage()
    if (!requiresSpecificationConverter(graph, immutableSchema)) {
        return null
    }
    val inputType = specificationConverterInputType(graph, immutableSchema, language)
    val outputType = specificationConverterOutputType(graph, immutableSchema, language)
    val propName = name
    val body = LsiPoetCodeBlock.build {
        if (language == LsiLanguage.JAVA || nullable) {
            beginControlFlow {
                text("if (")
                text(if (language == LsiLanguage.JAVA) propName else SPECIFICATION_CONVERTER_VALUE_NAME)
                text(if (language == LsiLanguage.JAVA) " == null)" else " === null)")
            }
            returnValue { text("null") }
            endControlFlow()
        }
        if (enumType != null) {
            if (language == LsiLanguage.KOTLIN) {
                text("return ")
            }
            add(
                toScalarToEnumPoetCodeBlock(
                    targetLanguage = language,
                    graph = graph,
                    immutableSchema = immutableSchema,
                    variableName = SPECIFICATION_CONVERTER_VALUE_NAME,
                ),
            )
        } else {
            val functionName = tailProp(graph).functionName
            val forList = functionName == "valueIn" ||
                functionName == "valueNotIn" ||
                functionName == "associatedIdIn" ||
                functionName == "associatedIdNotIn"
            returnValue {
                add(
                    toLsiConverterLoadingPoetCodeBlock(
                        graph = graph,
                        immutableSchema = immutableSchema,
                        targetLanguage = language,
                        forList = forList,
                        typeArguments = listOf(outputType, inputType.withNonNullRoot()),
                    ),
                )
                text(".input($SPECIFICATION_CONVERTER_VALUE_NAME)")
            }
        }
    }
    return LsiPoetFunction(
        name = specificationConverterName(language, graph),
        modifiers = setOf(
            if (language == LsiLanguage.JAVA) {
                LsiPoetModifier.PRIVATE
            } else {
                LsiPoetModifier.PUBLIC
            },
        ),
        parameters = listOf(
            LsiPoetParameter(
                name = SPECIFICATION_CONVERTER_VALUE_NAME,
                type = inputType,
            ),
        ),
        returnType = outputType,
        body = body,
        bodyStyle = LsiPoetBodyStyle.BLOCK,
    )
}

/** 解析 Specification lowering 引用的精确源码类型名称。 */
internal fun LsiWorkspace.dtoSpecificationPoetTypeNames(
    function: LsiPoetFunction,
    immutableSchema: ImmutableSchema,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = function.referencedTypeIds,
        additional = (
            DTO_COMMON_POET_TYPE_NAMES +
                SPECIFICATION_POET_TYPE_NAMES +
                immutableSchema.toLsiGeneratedQueryPoetTypeNames()
            ).distinctBy(LsiPoetTypeName::typeId),
    )
}

private fun LsiPoetCodeBuilder.declareSpecificationApplier(
    targetLanguage: LsiLanguage,
    argsName: String,
    applierName: String,
) {
    statement {
        if (targetLanguage == LsiLanguage.JAVA) {
            type(PREDICATE_APPLIER_TYPE)
            text(" ")
        } else {
            text("val ")
        }
        name(applierName)
        text(" = ")
        name(argsName)
        text(if (targetLanguage == LsiLanguage.JAVA) ".getApplier()" else ".applier")
    }
}

private fun LsiPoetCodeBuilder.changeSpecificationPath(
    currentPath: List<ImmutableProp>,
    nextPath: List<ImmutableProp>,
    immutableSchema: ImmutableSchema,
    applierName: String,
) {
    val sameCount = currentPath
        .zip(nextPath)
        .takeWhile { (current, next) -> current.id == next.id }
        .size
    repeat(currentPath.size - sameCount) {
        statement {
            name(applierName)
            text(".pop()")
        }
    }
    nextPath.drop(sameCount).forEach { prop ->
        statement {
            name(applierName)
            text(".push(")
            immutablePropReference(immutableSchema, prop)
            text(")")
        }
    }
}

private fun LsiPoetCodeBuilder.appendSpecificationFold(
    prop: DtoFoldProp,
    targetLanguage: LsiLanguage,
    nested: Boolean,
    argsName: String,
    applierName: String,
) {
    val targetName = if (nested) applierName else argsName
    if (targetLanguage == LsiLanguage.JAVA) {
        beginControlFlow {
            text("if (this.")
            name(prop.name)
            text(" != null)")
        }
        statement {
            text("this.")
            name(prop.name)
            text(".applyTo(")
            name(targetName)
            text(")")
        }
        endControlFlow()
    } else {
        statement {
            text("this.")
            name(prop.name)
            text("?.applyTo(")
            name(targetName)
            text(")")
        }
    }
}

private fun LsiPoetCodeBuilder.appendSpecificationTarget(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    nested: Boolean,
    argsName: String,
    applierName: String,
) {
    val entityAssociation = prop.specificationTargetIsEntityAssociation(graph, immutableSchema)
    require(!nested || !entityAssociation) {
        "Nested specification target cannot be an entity association: ${prop.id.value}"
    }
    if (targetLanguage == LsiLanguage.JAVA) {
        beginControlFlow {
            text("if (this.")
            name(prop.name)
            text(" != null)")
        }
        statement {
            text("this.")
            name(prop.name)
            text(".applyTo(")
            appendSpecificationTargetArgument(
                targetLanguage = targetLanguage,
                entityAssociation = entityAssociation,
                nested = nested,
                argsName = argsName,
                applierName = applierName,
            )
            text(")")
        }
        endControlFlow()
    } else {
        statement {
            text("this.")
            name(prop.name)
            text("?.let { it.applyTo(")
            appendSpecificationTargetArgument(
                targetLanguage = targetLanguage,
                entityAssociation = entityAssociation,
                nested = nested,
                argsName = argsName,
                applierName = applierName,
            )
            text(") }")
        }
    }
}

private fun LsiPoetCodeBuilder.appendSpecificationTargetArgument(
    targetLanguage: LsiLanguage,
    entityAssociation: Boolean,
    nested: Boolean,
    argsName: String,
    applierName: String,
) {
    when {
        entityAssociation -> {
            name(argsName)
            text(".child()")
        }
        nested -> name(applierName)
        targetLanguage == LsiLanguage.JAVA -> {
            name(argsName)
            text(".getApplier()")
        }
        else -> {
            name(argsName)
            text(".applier")
        }
    }
}

private fun LsiPoetCodeBuilder.appendSpecificationOperation(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    applierName: String,
) {
    statement {
        name(applierName)
        text(".")
        name(prop.specificationOperationName(graph))
        text("(")
        appendSpecificationPropArgument(prop, graph, immutableSchema, targetLanguage)
        text(", ")
        if (prop.requiresSpecificationConverter(graph, immutableSchema)) {
            name(prop.specificationConverterName(targetLanguage, graph))
            text("(")
            appendSpecificationValue(prop, graph, immutableSchema, targetLanguage)
            text(")")
        } else {
            appendSpecificationValue(prop, graph, immutableSchema, targetLanguage)
        }
        prop.specificationLikeOptionArguments(graph)?.forEach { argument ->
            text(", ")
            literal(argument.toString())
        }
        text(")")
    }
}

private fun LsiPoetCodeBuilder.appendSpecificationPropArgument(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
) {
    val props = prop.specificationArgumentProps(graph, immutableSchema)
    if (!prop.usesSpecificationPropArrayArgument(graph)) {
        immutablePropReference(immutableSchema, props.single())
        return
    }
    if (targetLanguage == LsiLanguage.JAVA) {
        text("new ")
        type(IMMUTABLE_PROP_TYPE)
        text("[] { ")
    } else {
        text("arrayOf(")
    }
    props.forEachIndexed { index, immutableProp ->
        if (index != 0) {
            text(", ")
        }
        immutablePropReference(immutableSchema, immutableProp)
    }
    text(if (targetLanguage == LsiLanguage.JAVA) " }" else ")")
}

private fun LsiPoetCodeBuilder.appendSpecificationValue(
    prop: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
) {
    text("this.")
    name(prop.dtoValueAccessorName(targetLanguage, graph, immutableSchema))
    if (targetLanguage == LsiLanguage.JAVA) {
        text("()")
    }
}

private fun LsiPoetCodeBuilder.immutablePropReference(
    immutableSchema: ImmutableSchema,
    prop: ImmutableProp,
) {
    type(immutableSchema.generatedPropsTypeOf(prop))
    text(".")
    name(prop.generatedPropsConstantName())
    text(".unwrap()")
}

private fun specificationApplyToParameterType(
    targetLanguage: LsiLanguage,
    baseType: ImmutableType,
    nested: Boolean,
): LsiDeclaredType {
    if (nested) {
        return PREDICATE_APPLIER_TYPE
    }
    val baseTypeRef = LsiDeclaredType(baseType.id)
    return when (targetLanguage) {
        LsiLanguage.JAVA -> LsiDeclaredType(
            declarationId = SPECIFICATION_ARGS_TYPE_ID,
            arguments = listOf(
                LsiTypeArgument.invariant(baseTypeRef),
                LsiTypeArgument.invariant(baseType.generatedTableType()),
            ),
        )
        LsiLanguage.KOTLIN -> LsiDeclaredType(
            declarationId = K_SPECIFICATION_ARGS_TYPE_ID,
            arguments = listOf(LsiTypeArgument.invariant(baseTypeRef)),
        )
        LsiLanguage.UNKNOWN -> error("DTO specification methods require Java or Kotlin target language")
    }
}

private fun LsiLanguage.requireSpecificationTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO specification methods require Java or Kotlin target language"
    }
    return this
}

private fun LsiTypeRef.withNonNullRoot(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiTypeParameterRef -> copy(nullability = LsiNullability.NON_NULL)
        is LsiPrimitiveType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiArrayType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiFunctionType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiUnresolvedType -> copy(nullability = LsiNullability.NON_NULL)
    }
}

private const val SPECIFICATION_CONVERTER_VALUE_NAME = "value"

private val CLASS_TYPE_ID = LsiSymbolId.type("java.lang.Class")

private val CLASS_TYPE_NAME = LsiPoetTypeName(CLASS_TYPE_ID, "java.lang", listOf("Class"))

private val SPECIFICATION_ARGS_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.SpecificationArgs")

private val K_SPECIFICATION_ARGS_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecificationArgs")

private val PREDICATE_APPLIER_TYPE =
    LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.PredicateApplier"))

private val IMMUTABLE_PROP_TYPE = LsiDeclaredType(LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableProp"))

private val SPECIFICATION_POET_TYPE_NAMES = listOf(
    CLASS_TYPE_NAME,
    LsiPoetTypeName(
        SPECIFICATION_ARGS_TYPE_ID,
        "org.babyfish.jimmer.sql.ast.query.specification",
        listOf("SpecificationArgs"),
    ),
    LsiPoetTypeName(
        K_SPECIFICATION_ARGS_TYPE_ID,
        "org.babyfish.jimmer.sql.kt.ast.query.specification",
        listOf("KSpecificationArgs"),
    ),
    LsiPoetTypeName(
        PREDICATE_APPLIER_TYPE.declarationId,
        "org.babyfish.jimmer.sql.ast.query.specification",
        listOf("PredicateApplier"),
    ),
    LsiPoetTypeName(
        IMMUTABLE_PROP_TYPE.declarationId,
        "org.babyfish.jimmer.meta",
        listOf("ImmutableProp"),
    ),
)
