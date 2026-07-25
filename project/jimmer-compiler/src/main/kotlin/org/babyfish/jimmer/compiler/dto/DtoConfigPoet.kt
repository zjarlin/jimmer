package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractKind
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoConfigValue
import site.addzero.lsi.jimmer.dto.DtoComparisonOperator
import site.addzero.lsi.jimmer.dto.DtoFetchType
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoLimit
import site.addzero.lsi.jimmer.dto.DtoOrderItem
import site.addzero.lsi.jimmer.dto.DtoPredicate
import site.addzero.lsi.jimmer.dto.DtoPropPathNode
import site.addzero.lsi.jimmer.dto.configImplementationTypeOrNull
import site.addzero.lsi.jimmer.dto.immutableProp
import site.addzero.lsi.jimmer.dto.terminalValueProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 把冻结的 DTO 属性配置降级为可由两端 Poet 渲染的代码块。 */
internal fun DtoBaseProp.toConfigPoetCodeBlock(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    configContractResolution: DtoConfigContractResolution,
): LsiPoetCodeBlock {
    require(graph.propsById[id] == this) {
        "DTO config property does not belong to this graph: ${id.value}"
    }
    requireNotNull(config) { "DTO config code requires a configured property: ${id.value}" }
    return when (targetLanguage) {
        LsiLanguage.JAVA -> javaConfigPoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            configContractResolution = configContractResolution,
        )
        LsiLanguage.KOTLIN -> kotlinConfigPoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            configContractResolution = configContractResolution,
        )
        else -> error("DTO config code requires Java or Kotlin target language: $targetLanguage")
    }
}

/** 为独立 DTO config 代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoConfigPoetTypeNames(
    codeBlock: LsiPoetCodeBlock,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = DTO_CONFIG_RUNTIME_TYPE_NAMES,
    )
}

private fun DtoBaseProp.javaConfigPoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    configContractResolution: DtoConfigContractResolution,
): LsiPoetCodeBlock {
    val config = requireNotNull(config)
    val filterType = configImplementationTypeOrNull(
        graph,
        configContractResolution,
        DtoConfigContractKind.FILTER,
    )
    val recursionType = configImplementationTypeOrNull(
        graph,
        configContractResolution,
        DtoConfigContractKind.RECURSION,
    )
    return LsiPoetCodeBlock.build {
        text("cfg -> cfg")
        indent {
            if (config.predicate != null || config.orderItems.isNotEmpty()) {
                javaInlineFilter(config.predicate, config.orderItems, immutableSchema)
            }
            if (config.fetchType != DtoFetchType.AUTO) {
                line()
                text(".fetchType(")
                type(LsiDeclaredType(REFERENCE_FETCH_TYPE_ID))
                text(".${config.fetchType.name})")
            }
            filterType?.let { typeRef ->
                line()
                text(".filter(new ")
                type(typeRef)
                text("())")
            }
            recursionType?.let { typeRef ->
                line()
                text(".recursive(new ")
                type(typeRef)
                text("())")
            }
            addLimits(config.limit, config.batch, config.depth, callPrefix = ".")
        }
    }
}

private fun DtoBaseProp.kotlinConfigPoetCodeBlock(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    configContractResolution: DtoConfigContractResolution,
): LsiPoetCodeBlock {
    val config = requireNotNull(config)
    val filterType = configImplementationTypeOrNull(
        graph,
        configContractResolution,
        DtoConfigContractKind.FILTER,
    )
    val recursionType = configImplementationTypeOrNull(
        graph,
        configContractResolution,
        DtoConfigContractKind.RECURSION,
    )
    return LsiPoetCodeBlock.build {
        text(" {")
        indent {
            when {
                config.predicate != null || config.orderItems.isNotEmpty() -> {
                    kotlinInlineFilter(config.predicate, config.orderItems, immutableSchema, workspace)
                }
                filterType != null -> {
                    line()
                    text("filter(")
                    type(filterType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
                    text("())")
                }
            }
            recursionType?.let { typeRef ->
                line()
                text("recursive(")
                type(typeRef, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
                text("())")
            }
            if (config.fetchType != DtoFetchType.AUTO) {
                line()
                text("fetchType(")
                type(LsiDeclaredType(REFERENCE_FETCH_TYPE_ID))
                text(".${config.fetchType.name})")
            }
            addLimits(config.limit, config.batch, config.depth, callPrefix = "")
        }
        line()
        text("}")
    }
}

private fun LsiPoetCodeBuilder.javaInlineFilter(
    predicate: DtoPredicate?,
    orderItems: List<DtoOrderItem>,
    immutableSchema: ImmutableSchema,
) {
    line()
    text(".filter(it -> it")
    indent {
        line()
        val predicates = predicate.topLevelConjuncts()
        predicates.forEach { item ->
            text(".where(")
            indent {
                line()
                javaPredicate(item, immutableSchema)
            }
            line()
            text(")")
        }
        if (orderItems.isNotEmpty()) {
            text(".orderBy(")
            indent {
                orderItems.forEachIndexed { index, item ->
                    line()
                    javaPropPath(item.path, immutableSchema)
                    text(if (item.descending) ".desc()" else ".asc()")
                    if (index != orderItems.lastIndex) {
                        text(",")
                    }
                }
            }
            line()
            text(")")
        }
    }
    line()
    text(")")
}

private fun LsiPoetCodeBuilder.kotlinInlineFilter(
    predicate: DtoPredicate?,
    orderItems: List<DtoOrderItem>,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
) {
    line()
    text("filter {")
    indent {
        predicate.topLevelConjuncts().forEach { item ->
            line()
            text("where(")
            indent {
                line()
                kotlinPredicate(item, immutableSchema, workspace)
            }
            line()
            text(")")
        }
        if (orderItems.isNotEmpty()) {
            line()
            text("orderBy(")
            indent {
                orderItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        text(", ")
                    }
                    line()
                    kotlinPropPath(item.path, immutableSchema, workspace)
                    text(".")
                    topLevelMember(EXPRESSION_PACKAGE, if (item.descending) "desc" else "asc", extension = true)
                    text("()")
                }
            }
            line()
            text(")")
        }
    }
    line()
    text("}")
}

private fun LsiPoetCodeBuilder.javaPredicate(
    predicate: DtoPredicate,
    immutableSchema: ImmutableSchema,
) {
    when (predicate) {
        is DtoPredicate.And -> javaPredicateGroup("and", predicate.predicates, immutableSchema)
        is DtoPredicate.Or -> javaPredicateGroup("or", predicate.predicates, immutableSchema)
        is DtoPredicate.Comparison -> {
            javaPropPath(predicate.path, immutableSchema)
            text(".${predicate.operator.runtimeMemberName}(")
            configValue(predicate.value, predicate.path.terminalValueProp(immutableSchema), LsiLanguage.JAVA)
            text(")")
        }
        is DtoPredicate.Nullity -> {
            javaPropPath(predicate.path, immutableSchema)
            text(if (predicate.negative) ".isNotNull()" else ".isNull()")
        }
    }
}

private fun LsiPoetCodeBuilder.javaPredicateGroup(
    memberName: String,
    predicates: List<DtoPredicate>,
    immutableSchema: ImmutableSchema,
) {
    type(LsiDeclaredType(PREDICATE_ID))
    text(".$memberName(")
    indent {
        predicates.forEachIndexed { index, predicate ->
            line()
            javaPredicate(predicate, immutableSchema)
            if (index != predicates.lastIndex) {
                text(",")
            }
        }
    }
    line()
    text(")")
}

private fun LsiPoetCodeBuilder.kotlinPredicate(
    predicate: DtoPredicate,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
) {
    when (predicate) {
        is DtoPredicate.And -> kotlinPredicateGroup("and", predicate.predicates, immutableSchema, workspace)
        is DtoPredicate.Or -> kotlinPredicateGroup("or", predicate.predicates, immutableSchema, workspace)
        is DtoPredicate.Comparison -> {
            kotlinPropPath(predicate.path, immutableSchema, workspace)
            text(" ")
            topLevelMember(EXPRESSION_PACKAGE, predicate.operator.runtimeMemberName, extension = true)
            text(" ")
            configValue(predicate.value, predicate.path.terminalValueProp(immutableSchema), LsiLanguage.KOTLIN)
        }
        is DtoPredicate.Nullity -> {
            kotlinPropPath(predicate.path, immutableSchema, workspace)
            text(".")
            topLevelMember(
                EXPRESSION_PACKAGE,
                if (predicate.negative) "isNotNull" else "isNull",
                extension = true,
            )
            text("()")
        }
    }
}

private fun LsiPoetCodeBuilder.kotlinPredicateGroup(
    memberName: String,
    predicates: List<DtoPredicate>,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
) {
    topLevelMember(EXPRESSION_PACKAGE, memberName, extension = false)
    text("(")
    indent {
        predicates.forEachIndexed { index, predicate ->
            line()
            kotlinPredicate(predicate, immutableSchema, workspace)
            if (index != predicates.lastIndex) {
                text(",")
            }
        }
    }
    line()
    text(")")
}

private fun LsiPoetCodeBuilder.javaPropPath(
    path: List<DtoPropPathNode>,
    immutableSchema: ImmutableSchema,
) {
    name("it")
    text(".getTable()")
    path.forEach { node ->
        val prop = node.immutableProp(immutableSchema)
        text(".")
        name(if (node.associatedId) "${prop.name}Id" else prop.name)
        text("()")
    }
}

private fun LsiPoetCodeBuilder.kotlinPropPath(
    path: List<DtoPropPathNode>,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
) {
    name("table")
    path.forEach { node ->
        val prop = node.immutableProp(immutableSchema)
        val packageName = workspace.toLsiPoetTypeNames(listOf(prop.ownerTypeId)).single().packageName
        text(".")
        topLevelMember(
            packageName,
            if (node.associatedId) "${prop.name}Id" else prop.name,
            extension = true,
        )
    }
}

private fun LsiPoetCodeBuilder.configValue(
    value: DtoConfigValue,
    prop: ImmutableProp,
    targetLanguage: LsiLanguage,
) {
    when (value) {
        is DtoConfigValue.BooleanValue -> literal(value.value.toString())
        is DtoConfigValue.LongValue -> literal(
            value.value.toString() + if (prop.primitiveKind == LsiPrimitiveKind.LONG) "L" else ""
        )
        is DtoConfigValue.BigIntegerValue -> constructorValue(
            BIG_INTEGER_ID,
            value.value,
            targetLanguage,
        )
        is DtoConfigValue.DecimalValue -> when {
            prop.primitiveKind == LsiPrimitiveKind.FLOAT -> literal("${value.value}F")
            prop.primitiveKind == LsiPrimitiveKind.DOUBLE -> literal("${value.value}D")
            prop.declaredTypeId == BIG_DECIMAL_ID -> constructorValue(
                BIG_DECIMAL_ID,
                value.value,
                targetLanguage,
            )
            else -> literal(value.value)
        }
        is DtoConfigValue.StringValue -> string(value.value)
    }
}

private fun LsiPoetCodeBuilder.constructorValue(
    typeId: LsiSymbolId,
    value: String,
    targetLanguage: LsiLanguage,
) {
    if (targetLanguage == LsiLanguage.JAVA) {
        text("new ")
    }
    type(LsiDeclaredType(typeId))
    text("(")
    string(value)
    text(")")
}

private fun LsiPoetCodeBuilder.addLimits(
    limit: DtoLimit?,
    batch: Int?,
    depth: Int?,
    callPrefix: String,
) {
    if (limit != null) {
        line()
        text("${callPrefix}limit(${limit.value}")
        if (limit.offset != 0) {
            text(", ${limit.offset}")
        }
        text(")")
    }
    if (batch != null) {
        line()
        text("${callPrefix}batch($batch)")
    }
    if (depth != null) {
        line()
        text("${callPrefix}depth($depth)")
    }
}

private val ImmutableProp.primitiveKind: LsiPrimitiveKind?
    get() = (type as? LsiPrimitiveType)?.kind

private val ImmutableProp.declaredTypeId: LsiSymbolId?
    get() = (type as? LsiDeclaredType)?.declarationId

private val DtoComparisonOperator.runtimeMemberName: String
    get() = when (this) {
        DtoComparisonOperator.EQ -> "eq"
        DtoComparisonOperator.NE -> "ne"
        DtoComparisonOperator.LT -> "lt"
        DtoComparisonOperator.LE -> "le"
        DtoComparisonOperator.GT -> "gt"
        DtoComparisonOperator.GE -> "ge"
        DtoComparisonOperator.LIKE -> "like"
        DtoComparisonOperator.ILIKE -> "ilike"
    }

private fun DtoPredicate?.topLevelConjuncts(): List<DtoPredicate> = when (this) {
    null -> emptyList()
    is DtoPredicate.And -> predicates
    else -> listOf(this)
}

private const val EXPRESSION_PACKAGE = "org.babyfish.jimmer.sql.kt.ast.expression"

private val REFERENCE_FETCH_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.ReferenceFetchType")

private val PREDICATE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Predicate")

private val BIG_INTEGER_ID = LsiSymbolId.type("java.math.BigInteger")

private val BIG_DECIMAL_ID = LsiSymbolId.type("java.math.BigDecimal")

private val DTO_CONFIG_RUNTIME_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer.sql.fetcher", listOf("ReferenceFetchType")),
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer.sql.ast", listOf("Predicate")),
    JimmerDtoPoetTypeNames.create("java.math", listOf("BigInteger")),
    JimmerDtoPoetTypeNames.create("java.math", listOf("BigDecimal")),
)
