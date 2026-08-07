package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.hibernateValidatorGetterName
import site.addzero.lsi.jimmer.dto.propsInDeclarationOrder
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
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

/** 将冻结的 DTO 属性访问语义降低为 Hibernate Validator 增强函数。 */
internal fun DtoType.toDtoHibernateValidatorPoetFunctions(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): List<LsiPoetFunction> {
    val language = targetLanguage.requireHibernateValidatorTargetLanguage()
    val props = propsInDeclarationOrder(graph)
    return DtoHibernateValidatorLookup.entries.map { lookup ->
        hibernateValidatorPoetFunction(
            props = props,
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = language,
            lookup = lookup,
        )
    }
}

/** 返回生成 DTO 必须实现的 Hibernate Validator 增强接口。 */
internal fun dtoHibernateValidatorEnhancedBeanType(): LsiDeclaredType {
    return HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE
}

/** 解析 Hibernate Validator lowering 引用的精确源码类型名称。 */
internal fun LsiWorkspace.dtoHibernateValidatorPoetTypeNames(
    functions: Collection<LsiPoetFunction>,
): List<LsiPoetTypeName> {
    val typeIds = buildSet {
        functions.forEach { function -> addAll(function.referencedTypeIds) }
        add(HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_ID)
    }
    return toLsiPoetTypeNames(
        typeIds = typeIds,
        additional = DTO_COMMON_POET_TYPE_NAMES + HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_NAME,
    )
}

private fun hibernateValidatorPoetFunction(
    props: List<DtoProp>,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
    lookup: DtoHibernateValidatorLookup,
): LsiPoetFunction {
    return LsiPoetFunction(
        name = "\$\$_hibernateValidator_get${lookup.methodPart}Value",
        modifiers = buildSet {
            if (targetLanguage == LsiLanguage.JAVA) {
                add(LsiPoetModifier.PUBLIC)
            }
            add(LsiPoetModifier.OVERRIDE)
        },
        parameters = listOf(
            LsiPoetParameter(
                name = LOOKUP_PARAMETER_NAME,
                type = targetLanguage.stringType(),
            ),
        ),
        returnType = targetLanguage.valueReturnType(),
        body = when (targetLanguage) {
            LsiLanguage.JAVA -> javaLookupBody(props, graph, immutableSchema, lookup)
            LsiLanguage.KOTLIN -> kotlinLookupBody(props, graph, immutableSchema, lookup)
            LsiLanguage.UNKNOWN -> error("DTO Hibernate Validator target language is unresolved")
        },
        bodyStyle = if (targetLanguage == LsiLanguage.JAVA) {
            LsiPoetBodyStyle.BLOCK
        } else {
            LsiPoetBodyStyle.EXPRESSION
        },
    )
}

private fun javaLookupBody(
    props: List<DtoProp>,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    lookup: DtoHibernateValidatorLookup,
): LsiPoetCodeBlock = LsiPoetCodeBlock.build {
    beginControlFlow {
        text("switch (")
        name(LOOKUP_PARAMETER_NAME)
        text(")")
    }
    props.forEach { prop ->
        statement {
            text("case ")
            string(lookup.key(prop, LsiLanguage.JAVA, graph, immutableSchema))
            text(": return ")
            thisMember(prop.name)
        }
    }
    statement {
        text("default: throw new IllegalArgumentException(")
        string("No ${lookup.label} named \"")
        text(" + ")
        name(LOOKUP_PARAMETER_NAME)
        text(" + ")
        string("\"")
        text(")")
    }
    endControlFlow()
}

private fun kotlinLookupBody(
    props: List<DtoProp>,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    lookup: DtoHibernateValidatorLookup,
): LsiPoetCodeBlock = LsiPoetCodeBlock.build {
    beginControlFlow {
        text("when(")
        name(LOOKUP_PARAMETER_NAME)
        text(")")
    }
    props.forEach { prop ->
        statement {
            string(lookup.key(prop, LsiLanguage.KOTLIN, graph, immutableSchema))
            text(" -> ")
            thisMember(prop.name)
        }
    }
    statement {
        text(
            """else -> throw IllegalArgumentException("No ${lookup.label} named \"${'$'}{name}\"")""",
        )
    }
    endControlFlow()
}

private fun LsiPoetCodeBuilder.thisMember(memberName: String) {
    text("this.")
    name(memberName)
}

private fun DtoHibernateValidatorLookup.key(
    prop: DtoProp,
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): String {
    return when (this) {
        DtoHibernateValidatorLookup.FIELD -> prop.name
        DtoHibernateValidatorLookup.GETTER -> prop.hibernateValidatorGetterName(
            targetLanguage = targetLanguage,
            graph = graph,
            immutableSchema = immutableSchema,
        )
    }
}

private fun LsiLanguage.requireHibernateValidatorTargetLanguage(): LsiLanguage {
    require(this == LsiLanguage.JAVA || this == LsiLanguage.KOTLIN) {
        "DTO Hibernate Validator methods require Java or Kotlin target language"
    }
    return this
}

private fun LsiLanguage.stringType(): LsiDeclaredType {
    return LsiDeclaredType(
        when (this) {
            LsiLanguage.JAVA -> JAVA_STRING_TYPE_ID
            LsiLanguage.KOTLIN -> KOTLIN_STRING_TYPE_ID
            LsiLanguage.UNKNOWN -> error("DTO Hibernate Validator target language is unresolved")
        },
    )
}

private fun LsiLanguage.valueReturnType(): LsiDeclaredType {
    return when (this) {
        LsiLanguage.JAVA -> LsiDeclaredType(JAVA_OBJECT_TYPE_ID)
        LsiLanguage.KOTLIN -> LsiDeclaredType(
            KOTLIN_ANY_TYPE_ID,
            nullability = LsiNullability.NULLABLE,
        )
        LsiLanguage.UNKNOWN -> error("DTO Hibernate Validator target language is unresolved")
    }
}

private enum class DtoHibernateValidatorLookup(
    val methodPart: String,
    val label: String,
) {
    FIELD("Field", "field"),
    GETTER("Getter", "getter"),
}

private const val LOOKUP_PARAMETER_NAME = "name"

private val JAVA_STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
private val KOTLIN_STRING_TYPE_ID = LsiSymbolId.type("kotlin.String")
private val JAVA_OBJECT_TYPE_ID = LsiSymbolId.type("java.lang.Object")
private val KOTLIN_ANY_TYPE_ID = LsiSymbolId.type("kotlin.Any")
private val HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_ID =
    LsiSymbolId.type("org.hibernate.validator.engine.HibernateValidatorEnhancedBean")
private val HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE =
    LsiDeclaredType(HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_ID)
private val HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_NAME = LsiPoetTypeName(
    typeId = HIBERNATE_VALIDATOR_ENHANCED_BEAN_TYPE_ID,
    packageName = "org.hibernate.validator.engine",
    simpleNames = listOf("HibernateValidatorEnhancedBean"),
)
