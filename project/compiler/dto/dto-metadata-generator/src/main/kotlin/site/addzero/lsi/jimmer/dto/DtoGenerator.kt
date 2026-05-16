package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.dto.compiler.Anno.*
import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import site.addzero.context.Context
import site.addzero.lsi.codegen.*
import site.addzero.lsi.codegen.CLASS_CLASS_NAME as CLASS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DESCRIPTION_CLASS_NAME as DESCRIPTION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DTO_METADATA_CLASS_NAME as DTO_METADATA_LSI_CLASS_NAME
import site.addzero.lsi.codegen.DTO_PROP_ACCESSOR as DTO_PROP_ACCESSOR_LSI_CLASS_NAME
import site.addzero.lsi.codegen.EMBEDDED_DTO_CLASS_NAME as EMBEDDED_DTO_LSI_CLASS_NAME
import site.addzero.lsi.codegen.FIXED_INPUT_FIELD_CLASS_NAME as FIXED_INPUT_FIELD_LSI_CLASS_NAME
import site.addzero.lsi.codegen.IMMUTABLE_PROP_LSI_CLASS_NAME
import site.addzero.lsi.codegen.INPUT_CLASS_NAME as INPUT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JSON_IGNORE_CLASS_NAME as JSON_IGNORE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JVM_STATIC_CLASS_NAME as JVM_STATIC_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_LIST_LSI_CLASS_NAME
import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.VIEW_CLASS_NAME as VIEW_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getEnumListArgument
import site.addzero.context.Settings
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.codegen.ConverterMetadata
import site.addzero.lsi.codegen.GenericParser
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.doc.LsiDoc
import site.addzero.lsi.jimmer.API_IGNORE
import site.addzero.lsi.jimmer.client.DocMetadata
import site.addzero.lsi.jimmer.dto.DtoAnnotationSupport
import site.addzero.lsi.jimmer.dto.DtoException
import site.addzero.lsi.jimmer.dto.toLsiAnnotationSpec
import site.addzero.lsi.jimmer.dto.analyzeDtoInterfaceMembers
import site.addzero.lsi.jimmer.lsiFetcherClassName
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget
import site.addzero.lsi.poet.LsiArrayExpression
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableReferenceExpression
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiClassLiteralExpression
import site.addzero.lsi.poet.LsiClassAnnotationValue
import site.addzero.lsi.poet.LsiConstructorDelegateCall
import site.addzero.lsi.poet.LsiConstructorDelegateKind
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiEnumConstantExpression
import site.addzero.lsi.poet.LsiExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiImportSpec
import site.addzero.lsi.poet.LsiIntArrayExpression
import site.addzero.lsi.poet.LsiJavaClassExpression
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.LsiCodeBlock
import site.addzero.lsi.poet.LsiLiteralAnnotationValue
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiListExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiStarTypeName
import site.addzero.lsi.poet.LsiStatement
import site.addzero.lsi.poet.LsiStringAnnotationValue
import site.addzero.lsi.poet.LsiThisExpression
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiSafeCastExpression
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWildcardTypeName
import site.addzero.lsi.poet.LsiWhenCase
import site.addzero.lsi.poet.LsiWhenStatement
import site.addzero.lsi.poet.toBoxedPrimitiveLsiClassNameOrNull
import site.addzero.lsi.poet.toBuiltInLsiClassNameOrNull
import site.addzero.lsi.poet.toLsiPoet
import java.util.*
import kotlin.math.min

private val K_SPECIFICATION_LSI_CLASS_NAME =
    LsiClassName.bestGuess("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")

private val K_SPECIFICATION_ARGS_LSI_CLASS_NAME =
    LsiClassName.bestGuess("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecificationArgs")

private val PREDICATE_APPLIER_LSI_CLASS_NAME =
    LsiClassName.bestGuess("org.babyfish.jimmer.sql.ast.query.specification.PredicateApplier")

private val HIBERNATE_VALIDATOR_ENHANCED_BEAN_LSI_CLASS_NAME =
    LsiClassName.bestGuess("org.hibernate.validator.engine.HibernateValidatorEnhancedBean")

private val INT_LSI_CLASS_NAME =
    LsiClassName.bestGuess("kotlin.Int")

private val STRING_LSI_CLASS_NAME =
    LsiClassName.bestGuess("kotlin.String")

private val LONG_LSI_CLASS_NAME =
    LsiClassName.bestGuess("kotlin.Long")

private val FLOAT_LSI_CLASS_NAME =
    LsiClassName.bestGuess("kotlin.Float")

private val DOUBLE_LSI_CLASS_NAME =
    LsiClassName.bestGuess("kotlin.Double")

private val BIG_INTEGER_TARGET_LSI_CLASS_NAME =
    LsiClassName.bestGuess("java.math.BigInteger")

private val BIG_DECIMAL_TARGET_LSI_CLASS_NAME =
    LsiClassName.bestGuess("java.math.BigDecimal")

private val STRING_BUILDER_LSI_CLASS_NAME =
    LsiClassName.bestGuess("java.lang.StringBuilder")

private val ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME =
    LsiClassName.bestGuess("java.lang.IllegalArgumentException")

internal class DtoGenerator private constructor(
    private val docMetadata: DocMetadata,
    private val mutable: Boolean,
    internal val dtoType: LsiDtoType,
    private val parent: DtoGenerator?,
    private val innerClassName: String?,
) {
    internal val ctx: Context = Context
    private val root: DtoGenerator = parent?.root ?: this

    private val document: Document = Document()

    private val useSiteTargetMap = mutableMapOf<String, Set<LsiAnnotationUseSiteTarget>>()

    private val interfacePropNames = analyzeDtoInterfaceMembers(ctx.lsiResolver, dtoType).propertyNames

    init {
        if ((parent == null) != (innerClassName == null)) {
            throw IllegalArgumentException("The nullity values of `parent` and `innerClassName` must be same")
        }
    }

    private val typeAnnotations = mutableListOf<LsiAnnotationSpec>()
    private val typeModifiers = linkedSetOf<LsiModifier>()
    private val typeSuperInterfaces = mutableListOf<LsiTypeName>()
    private val typeProperties = mutableListOf<LsiPropertySpec>()
    private val typeCallables = mutableListOf<LsiCallableSpec>()
    private val typeNestedTypes = mutableListOf<LsiTypeSpec>()

    constructor(
        docMetadata: DocMetadata,
        mutable: Boolean,
        dtoType: LsiDtoType,
    ) : this(docMetadata, mutable, dtoType, null, null)

    internal fun getDtoLsiClassName(nestedSimpleName: String? = null): LsiClassName {
        if (innerClassName !== null) {
            val list: MutableList<String> = ArrayList()
            collectNames(list)
            return LsiClassName(
                packageName = root.dtoType.packageName,
                simpleNames = if (nestedSimpleName == null) {
                    list.toList()
                } else {
                    list + nestedSimpleName
                }
            )
        }
        if (nestedSimpleName == null) {
            return LsiClassName(
                packageName = root.dtoType.packageName,
                simpleNames = listOf(dtoType.name!!)
            )
        }
        return LsiClassName(
            packageName = root.dtoType.packageName,
            simpleNames = listOf(dtoType.name!!, nestedSimpleName)
        )
    }

    fun generate(): LsiFileSpec? {
        resetTypeState()
        typeModifiers += LsiModifier.OPEN
        addTypeAnnotation(
            if (parent == null) {
                generatedAnnotation(dtoType.dtoFile, mutable)
            } else {
                generatedAnnotation()
            }
        )
        addTypeAnnotations()
        addDoc()
        addMembers()
        val type = buildTypeSpec(innerClassName ?: dtoType.name!!)
        return if (parent == null) {
            LsiFileSpec(
                packageName = root.dtoType.packageName,
                name = dtoType.name!!,
                memberImports = collectMemberImports(dtoType),
                topLevelCallables = buildTopLevelCallables(),
                types = listOf(type)
            )
        } else if (innerClassName != null) {
            parent.addNestedType(type)
            null
        } else {
            null
        }
    }

    private fun resetTypeState() {
        typeAnnotations.clear()
        typeModifiers.clear()
        typeSuperInterfaces.clear()
        typeProperties.clear()
        typeCallables.clear()
        typeNestedTypes.clear()
    }

    private fun buildTypeSpec(name: String): LsiTypeSpec =
        LsiTypeSpec(
            name = name,
            kind = LsiTypeSpecKind.CLASS,
            annotations = typeAnnotations.toList(),
            modifiers = typeModifiers.toSet(),
            superInterfaces = typeSuperInterfaces.toList(),
            properties = typeProperties.toList(),
            callables = typeCallables.toList(),
            nestedTypes = typeNestedTypes.toList(),
        )

    private fun addTypeAnnotation(annotation: LsiAnnotationSpec) {
        typeAnnotations += annotation
    }

    private fun addTypeSuperInterface(type: LsiTypeName) {
        typeSuperInterfaces += type
    }

    private fun addTypeProperty(property: LsiPropertySpec) {
        typeProperties += property
    }

    private fun addTypeCallable(callable: LsiCallableSpec) {
        typeCallables += callable
    }

    private fun addNestedType(type: LsiTypeSpec) {
        typeNestedTypes += type
    }

    private fun collectMemberImports(dtoType: LsiDtoType): List<LsiImportSpec> =
        sortedSetOf<Pair<String, String>>(compareBy({ it.first }, { it.second })).also {
            collectImports(dtoType, it)
            it += "org.babyfish.jimmer.kt" to "new"
            it += "org.babyfish.jimmer.sql.kt.fetcher" to "newFetcher"
        }.map { importSpec ->
            LsiImportSpec(
                packageName = importSpec.first,
                name = importSpec.second,
            )
        }

    private fun collectImports(
        dtoType: LsiDtoType,
        imports: SortedSet<Pair<String, String>>,
    ) {
        imports += dtoType.baseType.lsiClassName.packageName to "by"
        for (prop in dtoType.dtoPropViews) {
            prop.configView?.let { cfg ->
                cfg.predicate?.let {
                    collectPredicateImports(it, imports)
                }
                for (orderItem in cfg.orderItems) {
                    collectPathImports(orderItem.path, imports)
                    imports += EXPRESSION_PACKAGE to if (orderItem.isDesc) "desc" else "asc"
                }
            }
            val targetType = prop.targetType
            if (targetType !== null && (!prop.isRecursive || targetType.isFocusedRecursion)) {
                collectImports(targetType, imports)
            } else {
                prop.baseProp.targetType?.lsiClassName?.packageName?.let {
                    imports += it to "by"
                }
            }
        }
    }

    private fun collectPredicateImports(
        predicate: LsiDtoPredicateView,
        imports: SortedSet<Pair<String, String>>,
    ) {
        when (predicate) {
            is LsiDtoAndPredicateView -> {
                imports += EXPRESSION_PACKAGE to "and"
                predicate.predicates.forEach {
                    collectPredicateImports(it, imports)
                }
            }
            is LsiDtoOrPredicateView -> {
                imports += EXPRESSION_PACKAGE to "or"
                predicate.predicates.forEach {
                    collectPredicateImports(it, imports)
                }
            }
            is LsiDtoCmpPredicateView -> {
                collectPathImports(predicate.path, imports)
                imports += EXPRESSION_PACKAGE to predicateOperatorName(predicate.operator)
            }
            is LsiDtoNullityPredicateView -> {
                collectPathImports(predicate.path, imports)
                imports += EXPRESSION_PACKAGE to if (predicate.isNegative) "isNotNull" else "isNull"
            }
        }
    }

    private fun collectPathImports(
        pathNodes: List<LsiDtoPathNodeView>,
        imports: SortedSet<Pair<String, String>>,
    ) {
        for (pathNode in pathNodes) {
            val prop = pathNode.prop
            imports += prop.declaringType.packageName to if (pathNode.isAssociatedId) {
                "${prop.name}Id"
            } else {
                prop.name
            }
        }
    }

    private fun addTypeAnnotations() {
        for (anno in dtoType.baseType.lsiClass.annotations) {
            if (isCopyableAnnotation(anno, dtoType.annotations)) {
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addTypeAnnotations 基类注解复制
                // 迁移说明：类注解复制由 `baseType.classDeclaration.annotations(KS)` 切换为 `baseType.lsiClass.annotations(LSI)`
                addTypeAnnotation(anno.toLsiPoet())
            }
        }
        for (anno in dtoType.annotations) {
            if (anno.qualifiedName != DtoAnnotationSupport.KOTLIN_DTO_TYPE_NAME) {
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addTypeAnnotations 的 `annotationOf(anno, ctx.resolver)`
                // 迁移说明：类型注解复制改为直接依赖 LSI 解析，不再向生成器透传 KSP Resolver
                addTypeAnnotation(lsiAnnotationOf(anno))
            }
        }
    }

    private fun addDoc() {
        (document.value ?: baseDocString)?.let {
            addTypeAnnotation(
                LsiAnnotationSpec(
                    type = DESCRIPTION_LSI_CLASS_NAME,
                    positionalArguments = listOf(LsiStringAnnotationValue(it))
                )
            )
        }
    }

    private fun addMembers() {
        if (isSerializerRequired) {
            addTypeAnnotation(
                LsiAnnotationSpec(
                    type = ctx.jacksonTypes.jsonSerialize,
                    members = mapOf(
                        "using" to LsiClassAnnotationValue(getDtoLsiClassName("Serializer"))
                    )
                )
            )
        }
        if (isBuilderRequired) {
            addTypeAnnotation(
                LsiAnnotationSpec(
                    type = ctx.jacksonTypes.jsonDeserialize,
                    members = mapOf(
                        "builder" to LsiClassAnnotationValue(getDtoLsiClassName("Builder"))
                    )
                )
            )
        }
        val isSpecification = dtoType.modifiers.contains(DtoModifier.SPECIFICATION)
        if (isImpl && dtoType.baseType.isEntity) {
            addTypeSuperInterface(
                when {
                    isSpecification ->
                        K_SPECIFICATION_LSI_CLASS_NAME

                    dtoType.modifiers.contains(DtoModifier.INPUT) ->
                        INPUT_LSI_CLASS_NAME

                    else ->
                        VIEW_LSI_CLASS_NAME
                }.parameterizedBy(dtoType.baseType.lsiClassName)
            )
        }
        if (isImpl && dtoType.baseType.isEmbeddable) {
            addTypeSuperInterface(
                EMBEDDED_DTO_LSI_CLASS_NAME.parameterizedBy(dtoType.baseType.lsiClassName)
            )
        }
        for (typeRef in dtoType.superInterfaces) {
            addTypeSuperInterface(lsiTypeName(typeRef))
        }
        if (isHibernateValidatorEnhancementRequired) {
            addTypeSuperInterface(HIBERNATE_VALIDATOR_ENHANCED_BEAN_LSI_CLASS_NAME)
        }

        addPrimaryConstructor()
        if (!isSpecification) {
            addConverterConstructor()
        }

        for (prop in dtoType.dtoPropViews) {
            addProp(prop)
            addStateProp(prop)
        }
        for (prop in dtoType.userPropViews) {
            addProp(prop)
        }

        if (isSpecification) {
            addEntityType()
            addApplyTo()
        } else {
            addToEntity()
            addToEntityEx()
            addToEntityImpl()
        }

        for (prop in dtoType.dtoPropViews) {
            addSpecificationConverter(prop)
        }

        addCopy()
        addHashCode()
        addEquals()
        addToString()

        if (!isSpecification) {
            addMetadata()
            for (prop in dtoType.dtoPropViews) {
                addAccessorField(prop)
            }
        }

        for (prop in dtoType.dtoPropViews) {
            val targetType = prop.targetType ?: continue
            if (!prop.isRecursive || targetType.isFocusedRecursion) {
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addMembers 的 `.generate(emptyList())`
                // 迁移说明：内嵌 DTO 生成不再伪造 KSFile 依赖，统一复用 LSI-only 生成入口
                DtoGenerator(
                    docMetadata = docMetadata,
                    mutable = mutable,
                    dtoType = targetType,
                    parent = this,
                    innerClassName = targetSimpleName(prop)
                ).generate()
            }
        }

        if (isHibernateValidatorEnhancementRequired) {
            addHibernateValidatorEnhancement(false)
            addHibernateValidatorEnhancement(true)
        }
        if (isSerializerRequired) {
            addNestedType(SerializerGenerator(this).generate())
        }
        if (isBuilderRequired) {
            addNestedType(InputBuilderGenerator(this).generate())
        }
    }

    private fun buildTopLevelCallables(): List<LsiCallableSpec> =
        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            emptyList()
        } else {
            listOf(
                buildToEntitiesCallable(),
                buildToEntitiesExCallable(),
            )
        }

    private fun addMetadata() {
        addTypeProperty(buildMetadataProperty())
    }

    private fun buildMetadataProperty(): LsiPropertySpec =
        LsiPropertySpec(
            name = "METADATA",
            type = metadataTypeName(getDtoLsiClassName()),
            modifiers = setOf(LsiModifier.STATIC),
            annotations = listOf(
                LsiAnnotationSpec(type = JVM_STATIC_LSI_CLASS_NAME)
            ),
            initializer = LsiNewExpression(
                type = DTO_METADATA_LSI_CLASS_NAME,
                arguments = listOf(
                    buildMetadataFetcherExpression(),
                    buildMetadataFactoryExpression(),
                )
            )
        )

    private fun metadataTypeName(dtoClassName: LsiClassName): LsiParameterizedTypeName =
        LsiParameterizedTypeName(
            rawType = DTO_METADATA_LSI_CLASS_NAME,
            typeArguments = listOf(dtoType.baseType.lsiClassName, dtoClassName),
        )

    private fun buildMetadataFetcherExpression(): LsiExpression =
        LsiCallExpression(
            receiver = LsiCallExpression(
                name = "newFetcher",
                arguments = listOf(
                    LsiClassLiteralExpression(dtoType.baseType.lsiClassName)
                )
            ),
            name = "by",
            arguments = listOf(
                LsiLambdaExpression(
                    mode = LsiLambdaMode.BLOCK,
                    statements = buildMetadataFetcherStatements(),
                )
            ),
        )

    private fun buildMetadataFetcherStatements(): List<LsiStatement> =
        buildList {
            dtoType.dtoPropViews.forEach { prop ->
                if (!prop.hasNextProp) {
                    buildFetcherFieldStatement(prop)?.let(::add)
                }
            }
            dtoType.hiddenFlatPropViews.forEach { hiddenFlatProp ->
                if (!hiddenFlatProp.baseProp.isId) {
                    add(buildHiddenFetcherFieldStatement(hiddenFlatProp))
                }
            }
        }

    private fun buildMetadataFactoryExpression(): LsiExpression =
        LsiLambdaExpression(
            mode = LsiLambdaMode.EXPRESSION,
            parameterNames = listOf("it"),
            expression = LsiNewExpression(
                type = getDtoLsiClassName(),
                arguments = listOf(LsiNameExpression("it")),
            ),
        )

    private fun buildFetcherFieldStatement(
        prop: LsiDtoPropView,
    ): LsiStatement? =
        if (prop.baseProp.isId) {
            null
        } else {
            LsiExpressionStatement(buildFetcherFieldExpression(prop))
        }

    private fun buildFetcherFieldExpression(
        prop: LsiDtoPropView,
    ): LsiExpression {
        val cfg = prop.configView
        val arguments = mutableListOf<LsiExpression>()
        if (prop.targetType !== null && !prop.isRecursive) {
            arguments += buildTargetMetadataFetcherExpression(prop)
        }
        if (cfg != null) {
            arguments += buildFetcherConfigLambda(prop, cfg)
        }
        return LsiCallExpression(
            name = if (prop.targetType !== null && prop.isRecursive) {
                "${prop.baseProp.name}*"
            } else {
                prop.baseProp.name
            },
            arguments = arguments,
        )
    }

    private fun buildTargetMetadataFetcherExpression(
        prop: LsiDtoPropView,
    ): LsiExpression {
        val targetDtoClassName = propElementLsiTypeName(prop).copyNullable(false) as LsiClassName
        return LsiPropertyGetExpression(
            receiver = LsiPropertyGetExpression(
                receiver = LsiTypeExpression(targetDtoClassName),
                name = "METADATA",
                type = metadataTypeName(targetDtoClassName),
            ),
            name = "fetcher",
            type = prop.tailBaseProp.targetType!!.lsiClass.lsiFetcherClassName,
        )
    }

    private fun buildFetcherConfigLambda(
        prop: LsiDtoPropView,
        cfg: LsiDtoPropConfigView,
    ): LsiLambdaExpression =
        LsiLambdaExpression(
            mode = LsiLambdaMode.BLOCK,
            statements = buildFetcherConfigStatements(prop, cfg),
        )

    private fun buildFetcherConfigStatements(
        prop: LsiDtoPropView,
        cfg: LsiDtoPropConfigView,
    ): List<LsiStatement> =
        buildList {
        when {
            cfg.predicate != null || cfg.orderItems.isNotEmpty() -> {
                add(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            name = "filter",
                            arguments = listOf(
                                LsiLambdaExpression(
                                    mode = LsiLambdaMode.BLOCK,
                                    statements = buildFilterStatements(cfg),
                                )
                            ),
                        )
                    )
                )
            }
            cfg.filterClassName != null -> {
                val fetcherDeclaration = ctx.lsiResolver.findClassByQualifiedName(cfg.filterClassName!!)
                    ?: throw DtoException(
                        "There is no filter class: ${cfg.filterClassName}"
                    )
                val entityTypeName = GenericParser(
                    "filter",
                    fetcherDeclaration,
                    "org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter"
                ).parse().argumentLsiTypeNames[0]
                val targetTypeName = prop.tailBaseProp.toTargetLsiTypeName(overrideNullable = false)
                if (entityTypeName != targetTypeName) {
                    throw DtoException(
                        "The filter class \"" +
                            cfg.filterClassName +
                            "\" is illegal, it specify the generic type argument of \"" +
                            "org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter" +
                            "\" as \"" +
                            entityTypeName +
                            "\", which is not associated entity type \"" +
                            targetTypeName +
                            "\""
                    )
                }
                add(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            name = "filter",
                            arguments = listOf(
                                LsiNewExpression(
                                    type = LsiClassName.bestGuess(cfg.filterClassName!!),
                                )
                            ),
                        )
                    )
                )
            }
        }
        if (cfg.recursionClassName !== null) {
            val recursionDeclaration = ctx.lsiResolver.findClassByQualifiedName(cfg.recursionClassName!!)
                ?: throw DtoException(
                    "There is no recursion class: ${cfg.recursionClassName}"
                )
            val entityTypeName = GenericParser(
                "recursion",
                recursionDeclaration,
                "org.babyfish.jimmer.sql.fetcher.RecursionStrategy"
            ).parse().argumentLsiTypeNames[0]
            val targetTypeName = prop.tailBaseProp.toTargetLsiTypeName(overrideNullable = false)
            if (entityTypeName != targetTypeName) {
                throw DtoException(
                    "The recursion class \"" +
                        cfg.recursionClassName +
                        "\" is illegal, it specify the generic type argument of \"" +
                        "org.babyfish.jimmer.sql.fetcher.RecursionStrategy" +
                        "\" as \"" +
                        entityTypeName +
                        "\", which is not associated entity type \"" +
                        targetTypeName +
                        "\""
                )
            }
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "recursive",
                        arguments = listOf(
                            LsiNewExpression(
                                type = LsiClassName.bestGuess(cfg.recursionClassName!!),
                            )
                        ),
                    )
                )
            )
        }
        if (cfg.fetchType != "AUTO") {
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "fetchType",
                        arguments = listOf(
                            LsiEnumConstantExpression(
                                type = REFERENCE_FETCH_TYPE_LSI_CLASS_NAME,
                                constantName = cfg.fetchType,
                            )
                        ),
                    )
                )
            )
        }
        if (cfg.limit != Int.MAX_VALUE) {
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "limit",
                        arguments = buildList {
                            add(LsiLiteralExpression(cfg.limit))
                            if (cfg.offset != 0) {
                                add(LsiLiteralExpression(cfg.offset))
                            }
                        },
                    )
                )
            )
        }
        if (cfg.batch != 0) {
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "batch",
                        arguments = listOf(LsiLiteralExpression(cfg.batch)),
                    )
                )
            )
        }
        if (cfg.depth != Int.MAX_VALUE) {
            add(
                LsiExpressionStatement(
                    LsiCallExpression(
                        name = "depth",
                        arguments = listOf(LsiLiteralExpression(cfg.depth)),
                    )
                )
            )
        }
    }

    private fun buildFilterStatements(cfg: LsiDtoPropConfigView): List<LsiStatement> =
        buildList {
            cfg.predicate?.let {
                val realPredicates = if (it is LsiDtoAndPredicateView) {
                    it.predicates
                } else {
                    listOf(it)
                }
                realPredicates.forEach { predicate ->
                    add(
                        LsiExpressionStatement(
                            LsiCallExpression(
                                name = "where",
                                arguments = listOf(buildPredicateExpression(predicate)),
                            )
                        )
                    )
                }
            }
            if (cfg.orderItems.isNotEmpty()) {
                add(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            name = "orderBy",
                            arguments = cfg.orderItems.map(::buildOrderItemExpression),
                        )
                    )
                )
            }
        }

    private fun buildPredicateExpression(predicate: LsiDtoPredicateView): LsiExpression =
        when (predicate) {
            is LsiDtoAndPredicateView -> LsiCallExpression(
                name = "and",
                arguments = predicate.predicates.map(::buildPredicateExpression),
            )
            is LsiDtoOrPredicateView -> LsiCallExpression(
                name = "or",
                arguments = predicate.predicates.map(::buildPredicateExpression),
            )
            is LsiDtoCmpPredicateView -> {
                val path = predicate.path
                LsiCallExpression(
                    receiver = propPathExpression(path),
                    name = predicateOperatorName(predicate.operator),
                    arguments = listOf(buildPredicateLiteralExpression(path.last().prop, predicate.value)),
                )
            }
            is LsiDtoNullityPredicateView -> {
                val path = predicate.path
                LsiCallExpression(
                    receiver = propPathExpression(path),
                    name = if (predicate.isNegative) "isNotNull" else "isNull",
                )
            }
        }

    private fun buildOrderItemExpression(
        orderItem: LsiDtoOrderItemView,
    ): LsiExpression =
        LsiCallExpression(
            receiver = propPathExpression(orderItem.path),
            name = if (orderItem.isDesc) "desc" else "asc",
        )

    private fun buildPredicateLiteralExpression(
        prop: ImmutableProp,
        value: Any?,
    ): LsiExpression =
        when {
            value is String -> LsiLiteralExpression(value)
            prop.toLsiTypeName(overrideNullable = false) == LONG_LSI_CLASS_NAME ->
                LsiLiteralExpression(value)
            prop.toLsiTypeName(overrideNullable = false) == BIG_INTEGER_TARGET_LSI_CLASS_NAME ->
                LsiNewExpression(
                    type = BIG_INTEGER_TARGET_LSI_CLASS_NAME,
                    arguments = listOf(LsiLiteralExpression(value.toString())),
                )
            prop.toLsiTypeName(overrideNullable = false) == BIG_DECIMAL_TARGET_LSI_CLASS_NAME ->
                LsiNewExpression(
                    type = BIG_DECIMAL_TARGET_LSI_CLASS_NAME,
                    arguments = listOf(LsiLiteralExpression(value.toString())),
                )
            else -> LsiLiteralExpression(value)
        }

    private fun predicateOperatorName(operator: String): String =
        when (operator) {
            "=" -> "eq"
            "<>" -> "ne"
            "<" -> "lt"
            "<=" -> "le"
            ">" -> "gt"
            ">=" -> "ge"
            else -> operator
        }

    private fun propPathExpression(pathNodes: List<LsiDtoPathNodeView>): LsiExpression =
        pathNodes.fold(LsiNameExpression("table") as LsiExpression) { receiver, pathNode ->
            val prop = pathNode.prop
            LsiPropertyAccessExpression(
                receiver = receiver,
                name = if (pathNode.isAssociatedId) {
                    "${prop.name}Id"
                } else {
                    prop.name
                },
            )
        }

    private fun buildHiddenFetcherFieldStatement(
        prop: LsiDtoPropView,
    ): LsiStatement =
        if (!prop.isFlat) {
            buildFetcherFieldStatement(prop)
                ?: error("Hidden fetcher field should not be null for prop '${prop.name}'")
        } else {
            LsiExpressionStatement(
                LsiCallExpression(
                    name = prop.baseProp.name,
                    arguments = listOf(
                        LsiLambdaExpression(
                            mode = LsiLambdaMode.BLOCK,
                            statements = prop.targetType!!.dtoPropViews.mapNotNull { childPropView ->
                                if (childPropView.isFlat) {
                                    buildHiddenFetcherFieldStatement(childPropView)
                                } else {
                                    buildFetcherFieldStatement(childPropView)
                                }
                            },
                        )
                    ),
                )
            )
        }

    private fun addStateProp(prop: LsiDtoPropView) {
        buildStateProp(prop)?.let(::addTypeProperty)
    }

    private fun addProp(prop: LsiDtoAbstractPropView) {
        val statePropName = statePropName(prop, false).takeIf { mutable }
        val backingPropName = statePropName?.let { mutableBackingPropName(prop) }
        if (backingPropName != null) {
            // 迁移说明：对 nullable input dynamic/fixed 属性，先落一个私有 backing property，
            // 公开属性只保留 accessor 语义，避免 `initializer + setter body` 冲突。
            addTypeProperty(
                LsiPropertySpec(
                    name = backingPropName,
                    type = propLsiTypeName(prop),
                    modifiers = setOf(LsiModifier.PRIVATE),
                    mutable = true,
                    initializer = LsiNameExpression(prop.name),
                )
            )
        }
        addTypeProperty(buildProp(prop, statePropName, backingPropName))
    }

    private fun buildStateProp(prop: LsiDtoPropView): LsiPropertySpec? =
        statePropName(prop, false)?.let { stateProp ->
            LsiPropertySpec(
                name = stateProp,
                type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                annotations = listOf(
                    // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addStateProp 生成 @ApiIgnore
                    // 迁移说明：生成注解类型改为共享 FQ 常量装配，移除该生成器对 `ApiIgnore::class` 的直接依赖
                    LsiAnnotationSpec(type = LsiClassName.bestGuess(API_IGNORE)),
                    LsiAnnotationSpec(
                        type = JSON_IGNORE_LSI_CLASS_NAME,
                        useSiteTarget = LsiAnnotationUseSiteTarget.GET,
                    )
                ),
                mutable = mutable,
                initializer = LsiNameExpression(stateProp),
            )
        }

    private fun buildProp(
        prop: LsiDtoAbstractPropView,
        statePropName: String?,
        backingPropName: String?,
    ): LsiPropertySpec =
        LsiPropertySpec(
            name = prop.name,
            type = propLsiTypeName(prop),
            annotations = buildPropAnnotations(prop),
            modifiers = buildPropModifiers(prop),
            mutable = mutable,
            initializer = backingPropName?.let { null } ?: LsiNameExpression(prop.name),
            getterStatements = backingPropName?.let {
                listOf(LsiReturnStatement(LsiNameExpression(it)))
            }.orEmpty(),
            setterStatements = buildPropSetterStatements(statePropName, backingPropName),
        )

    private fun buildPropModifiers(prop: LsiDtoAbstractPropView): Set<LsiModifier> =
        buildSet {
            if (interfacePropNames.contains(prop.name)) {
                add(LsiModifier.OVERRIDE)
            }
        }

    private fun buildPropAnnotations(prop: LsiDtoAbstractPropView): List<LsiAnnotationSpec> =
        buildList {
            val doc = document[prop]
                ?: prop.takeIf { it !is LsiDtoPropView || !it.hasNextProp }
                    ?.doc
            doc?.let {
                add(
                    LsiAnnotationSpec(
                        type = DESCRIPTION_LSI_CLASS_NAME,
                        positionalArguments = listOf(LsiStringAnnotationValue(it)),
                    )
                )
            }
            if (!isBuilderRequired && prop.annotations.none { it.qualifiedName == ctx.jacksonTypes.jsonProperty.canonicalName }) {
                add(
                    LsiAnnotationSpec(
                        type = ctx.jacksonTypes.jsonProperty,
                        positionalArguments = listOf(LsiStringAnnotationValue(prop.name)),
                        members = if (prop.isNullable) {
                            emptyMap()
                        } else {
                            mapOf("required" to LsiLiteralAnnotationValue(true))
                        },
                        useSiteTarget = LsiAnnotationUseSiteTarget.PARAM,
                    )
                )
            }
            if (prop is LsiDtoPropView) {
                if (dtoType.modifiers.contains(DtoModifier.INPUT) && prop.inputModifier == DtoModifier.FIXED) {
                    add(LsiAnnotationSpec(type = FIXED_INPUT_FIELD_LSI_CLASS_NAME))
                }
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addProp baseProp 注解扫描与复制
                // 迁移说明：baseProp 注解复制改为 `LsiField.annotations` 语义，统一承接 property/getter/returnType，移除该路径对 KSAnnotation 列表调用的依赖
                for (anno in prop.tailFieldAnnotations.filter {
                    isCopyableAnnotation(it, prop.annotations)
                }) {
                    val annoQualifiedName = anno.qualifiedName ?: continue
                    if (isBuilderRequired && annoQualifiedName == ctx.jacksonTypes.jsonDeserialize.canonicalName) {
                        continue
                    }
                    allowedTargets(annoQualifiedName).firstOrNull()?.let { target ->
                        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addProp baseProp 注解 use-site 覆盖
                        // 迁移说明：use-site 目标缓存改回 LSI 目标，KotlinPoet 只保留在 compat 边界
                        add(
                            normalizeAnnotationSpec(
                                anno.toLsiPoet().copy(useSiteTarget = target)
                            )
                        )
                    }
                }
            }
            for (anno in prop.annotations) {
                if (isBuilderRequired && anno.qualifiedName == ctx.jacksonTypes.jsonDeserialize.canonicalName) {
                    continue
                }
                val target = if (anno.qualifiedName.startsWith("com.fasterxml.jackson.")) {
                    LsiAnnotationUseSiteTarget.GET
                } else {
                    allowedTargets(anno.qualifiedName).firstOrNull() ?: continue
                }
                add(
                    normalizeAnnotationSpec(
                        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.addProp 的 `annotationOf(anno, ctx.resolver, ...)`
                        // 迁移说明：注解构造改为直接消费 LSI 解析结果，不再向生成器透传 KSP Resolver
                        lsiAnnotationOf(anno, target)
                    )
                )
            }
        }

    private fun buildPropSetterStatements(
        statePropName: String?,
        backingPropName: String?,
    ): List<site.addzero.lsi.poet.LsiStatement> {
        if (!mutable) {
            return emptyList()
        }
        val statements = mutableListOf<site.addzero.lsi.poet.LsiStatement>()
        when {
            backingPropName != null -> {
                statements += LsiAssignmentStatement(
                    target = LsiNameExpression(backingPropName),
                    expression = LsiNameExpression("value"),
                )
                statements += LsiAssignmentStatement(
                    target = LsiNameExpression(statePropName!!),
                    expression = LsiLiteralExpression(true),
                )
            }
            statePropName != null -> {
                statements += LsiAssignmentStatement(
                    target = LsiNameExpression("field"),
                    expression = LsiNameExpression("value"),
                )
                statements += LsiAssignmentStatement(
                    target = LsiNameExpression(statePropName),
                    expression = LsiLiteralExpression(true),
                )
            }
        }
        return statements
    }

    private fun mutableBackingPropName(prop: LsiDtoAbstractPropView): String =
        "__${prop.name}"

    private fun addPrimaryConstructor() {
        addTypeCallable(buildPrimaryConstructor())
    }

    private fun buildPrimaryConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            primary = true,
            parameters = buildPrimaryConstructorParameters(),
        )

    private fun buildPrimaryConstructorParameters(): List<LsiParameterSpec> =
        buildList {
            for (prop in dtoType.dtoPropViews) {
                add(
                    LsiParameterSpec(
                        name = prop.name,
                        type = propLsiTypeName(prop),
                        defaultValue = when {
                            prop.isNullable -> LsiCodeBlock.of("null")
                            propLsiTypeName(prop) == KOTLIN_BOOLEAN_LSI_CLASS_NAME -> LsiCodeBlock.of("false")
                            else -> null
                        }
                    )
                )
                statePropName(prop, false)?.let {
                    add(
                        LsiParameterSpec(
                            name = StringUtil.identifier("is", prop.name, "Loaded"),
                            type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                            defaultValue = if (prop.isNullable) {
                                LsiCodeBlock.of("%L !== null", LsiNameExpression(prop.name))
                            } else {
                                LsiCodeBlock.of("true")
                            }
                        )
                    )
                }
            }
            for (prop in dtoType.userPropViews) {
                add(
                    LsiParameterSpec(
                        name = prop.name,
                        type = lsiTypeName(prop.typeRef),
                        defaultValue = defaultValueCodeBlock(prop)
                    )
                )
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun addConverterConstructor() {
        addTypeCallable(buildConverterConstructor())
    }

    private fun addToEntity() {
        addTypeCallable(buildToEntityCallable())
    }

    private fun buildToEntitiesCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = if (dtoType.baseType.isEntity) "toEntities" else "toImmutables",
            receiverType = iterableOf(getDtoLsiClassName()),
            annotations = listOf(generatedAnnotation(dtoType.baseType.lsiClassName)),
            returnType = listOfType(dtoType.baseType.lsiClassName),
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "map",
                        arguments = listOf(
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.EXPRESSION,
                                parameterNames = listOf("it"),
                                expression = LsiCallExpression(
                                    receiver = LsiNameExpression("it"),
                                    name = if (dtoType.baseType.isEntity) "toEntity" else "toImmutable"
                                )
                            )
                        )
                    )
                )
            )
        )

    private fun buildToEntitiesExCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = if (dtoType.baseType.isEntity) "toEntities" else "toImmutables",
            receiverType = iterableOf(getDtoLsiClassName()),
            annotations = listOf(generatedAnnotation(dtoType.baseType.lsiClassName)),
            returnType = listOfType(dtoType.baseType.lsiClassName),
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = LsiLambdaTypeName(
                        receiverType = dtoType.baseType.lsiDraftClassName,
                        returnType = KOTLIN_UNIT_LSI_CLASS_NAME
                    )
                )
            ),
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        name = "map",
                        arguments = listOf(
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.EXPRESSION,
                                parameterNames = listOf("it"),
                                expression = LsiCallExpression(
                                    receiver = LsiNameExpression("it"),
                                    name = if (dtoType.baseType.isEntity) "toEntity" else "toImmutable",
                                    arguments = listOf(LsiNameExpression("block"))
                                )
                            )
                        )
                    )
                )
            )
        )

    private fun iterableOf(elementType: LsiTypeName): LsiParameterizedTypeName =
        LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("kotlin.collections.Iterable"),
            typeArguments = listOf(elementType)
        )

    private fun listOfType(elementType: LsiTypeName): LsiParameterizedTypeName =
        LsiParameterizedTypeName(
            rawType = KOTLIN_LIST_LSI_CLASS_NAME,
            typeArguments = listOf(elementType)
        )


    private fun addToEntityEx() {
        addTypeCallable(buildToEntityExCallable())
    }

    private fun buildConverterConstructor(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.CONSTRUCTOR,
            parameters = buildConverterConstructorParameters(),
            delegateCall = LsiConstructorDelegateCall(
                kind = LsiConstructorDelegateKind.THIS,
                arguments = buildConverterConstructorArguments(),
            ),
        )

    private fun buildConverterConstructorParameters(): List<LsiParameterSpec> =
        buildList {
            add(
                LsiParameterSpec(
                    name = "base",
                    type = dtoType.baseType.lsiClassName,
                )
            )
            for (userProp in dtoType.userPropViews) {
                add(
                    LsiParameterSpec(
                        name = userProp.name,
                        type = lsiTypeName(userProp.typeRef),
                        defaultValue = defaultValueCodeBlock(userProp),
                    )
                )
            }
        }

    private fun buildConverterConstructorArguments(): List<LsiExpression> =
        dtoType.propViews.flatMap { prop ->
            when (prop) {
                is LsiDtoPropView -> buildConverterArgumentExpressions(prop)
                is LsiUserPropView -> listOf(LsiNameExpression(prop.name))
            }
        }

    private fun buildConverterArgumentExpressions(
        prop: LsiDtoPropView,
    ): List<LsiExpression> =
        buildList {
            add(buildConverterValueExpression(prop))
            buildConverterLoadedExpression(prop)?.let(::add)
        }

    private fun buildConverterValueExpression(
        prop: LsiDtoPropView,
    ): LsiExpression =
        when {
            isSimpleProp(prop) -> LsiPropertyGetExpression(
                receiver = LsiNameExpression("base"),
                name = prop.baseProp.name,
                type = prop.baseProp.toLsiTypeName(),
            )
            !prop.isNullable && prop.isBaseNullable -> LsiCallExpression(
                receiver = accessorNameExpression(prop),
                name = "get",
                typeArguments = listOf(propLsiTypeName(prop)),
                arguments = listOf(
                    LsiNameExpression("base"),
                    LsiLiteralExpression(
                        "Cannot convert \"${dtoType.baseType.lsiClassName}\" to " +
                            "\"${getDtoLsiClassName()}\" because the cannot get non-null value for " +
                            "\"${prop.name}\""
                    ),
                ),
            )
            else -> LsiCallExpression(
                receiver = accessorNameExpression(prop),
                name = "get",
                typeArguments = listOf(propLsiTypeName(prop)),
                arguments = listOf(LsiNameExpression("base")),
            )
        }

    private fun buildConverterLoadedExpression(
        prop: LsiDtoPropView,
    ): LsiExpression? =
        statePropName(prop, false)?.let {
            if (isSimpleProp(prop)) {
                LsiCallExpression(
                    receiver = LsiPropertyAccessExpression(
                        receiver = LsiTypeExpression(dtoType.baseType.lsiPropsClassName),
                        name = StringUtil.snake(prop.baseProp.name, SnakeCase.UPPER),
                    ),
                    name = "isLoaded",
                    arguments = listOf(LsiNameExpression("base")),
                )
            } else {
                LsiCallExpression(
                    receiver = accessorNameExpression(prop),
                    name = "isLoaded",
                    arguments = listOf(LsiNameExpression("base")),
                )
            }
        }

    private fun accessorNameExpression(
        prop: LsiDtoPropView,
    ): LsiNameExpression =
        LsiNameExpression(StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER))

    private fun buildToEntityCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = if (dtoType.baseType.isEntity) "toEntity" else "toImmutable",
            modifiers = setOf(LsiModifier.OVERRIDE),
            returnType = dtoType.baseType.lsiClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiCallExpression(
                            name = "new",
                            arguments = listOf(LsiClassLiteralExpression(dtoType.baseType.lsiClassName)),
                        ),
                        name = "by",
                        arguments = listOf(
                            LsiNullExpression,
                            LsiLiteralExpression(false),
                            LsiCallableReferenceExpression(
                                receiver = LsiThisExpression,
                                name = if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl",
                                receiverLabel = innerClassName ?: dtoType.name!!,
                            ),
                        )
                    )
                )
            )
        )

    private fun buildToEntityExCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = if (dtoType.baseType.isEntity) "toEntity" else "toImmutable",
            parameters = listOf(
                LsiParameterSpec(
                    name = "block",
                    type = LsiLambdaTypeName(
                        receiverType = dtoType.baseType.lsiDraftClassName,
                        returnType = KOTLIN_UNIT_LSI_CLASS_NAME,
                    )
                )
            ),
            returnType = dtoType.baseType.lsiClassName,
            statements = listOf(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiCallExpression(
                            name = "new",
                            arguments = listOf(LsiClassLiteralExpression(dtoType.baseType.lsiClassName)),
                        ),
                        name = "by",
                        arguments = listOf(
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.UNIT,
                                statements = listOf(
                                    LsiExpressionStatement(
                                        LsiCallExpression(
                                            name = if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl",
                                            arguments = listOf(LsiThisExpression),
                                        )
                                    ),
                                    LsiExpressionStatement(
                                        LsiCallExpression(
                                            receiver = LsiNameExpression("block"),
                                            name = "invoke",
                                            arguments = listOf(LsiThisExpression),
                                        )
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        )

    private fun addToEntityImpl() {
        addTypeCallable(buildToEntityImplCallable())
    }

    private fun buildToEntityImplCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl",
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "_draft",
                    type = dtoType.baseType.lsiDraftClassName,
                )
            ),
            statements = buildToEntityImplStatements()
        )

    private fun buildToEntityImplStatements(): List<LsiStatement> =
        buildList {
            for (prop in dtoType.dtoPropViews) {
                val baseProp = prop.tailBaseProp
                if (baseProp.isImplementationFormula) {
                    continue
                }
                val assignment = buildDraftAssignmentStatement(prop, prop.name)
                val statePropName = statePropName(prop, false)
                if (statePropName !== null) {
                    add(
                        LsiIfStatement(
                            condition = LsiNameExpression(statePropName),
                            thenStatements = listOf(assignment)
                        )
                    )
                } else {
                    add(assignment)
                }
            }
        }

    private fun buildDraftAssignmentStatement(
        prop: LsiDtoPropView,
        valueExpr: String,
    ): LsiStatement {
        val baseProp = prop.tailBaseProp
        if (isSimpleProp(prop)) {
            return LsiPropertySetStatement(
                receiver = LsiNameExpression("_draft"),
                name = baseProp.name,
                expression = LsiNameExpression(valueExpr)
            )
        }
        return LsiExpressionStatement(
            LsiCallExpression(
                receiver = accessorNameExpression(prop),
                name = "set",
                arguments = listOf(
                    LsiNameExpression("_draft"),
                    LsiNameExpression(valueExpr)
                )
            )
        )
    }

    private fun addEntityType() {
        addTypeCallable(buildEntityTypeCallable())
    }

    private fun buildEntityTypeCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "entityType",
            modifiers = buildSet {
                if (isImpl) {
                    add(LsiModifier.OVERRIDE)
                }
            },
            returnType = LsiParameterizedTypeName(
                rawType = CLASS_LSI_CLASS_NAME,
                typeArguments = listOf(dtoType.baseType.lsiClassName),
            ),
            statements = listOf(
                LsiReturnStatement(
                    LsiJavaClassExpression(dtoType.baseType.lsiClassName)
                )
            )
        )

    private fun addApplyTo() {
        addTypeCallable(buildApplyToCallable())
    }

    private fun buildApplyToCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "applyTo",
            modifiers = buildSet {
                if (isImpl) {
                    add(LsiModifier.OVERRIDE)
                }
            },
            parameters = listOf(
                LsiParameterSpec(
                    name = if (isImpl) "args" else "_applier",
                    type = if (isImpl) {
                        K_SPECIFICATION_ARGS_LSI_CLASS_NAME
                            .parameterizedBy(dtoType.baseType.lsiClassName)
                    } else {
                        PREDICATE_APPLIER_LSI_CLASS_NAME
                    }
                )
            ),
            statements = buildApplyToStatements()
        )

    private fun buildApplyToStatements(): List<LsiStatement> =
        buildList {
            if (isImpl) {
                add(
                    LsiVariableDeclarationStatement(
                        name = "_applier",
                        type = PREDICATE_APPLIER_LSI_CLASS_NAME,
                        initializer = LsiPropertyGetExpression(
                            receiver = LsiNameExpression("args"),
                            name = "applier",
                            type = PREDICATE_APPLIER_LSI_CLASS_NAME,
                        )
                    )
                )
            }
            var stack = emptyList<ImmutableProp>()
            for (prop in dtoType.dtoPropViews) {
                val newStack = prop.stackBaseProps
                addAll(buildStackOperationStatements(stack, newStack))
                addAll(buildPredicateOperationStatements(prop))
                stack = newStack
            }
            addAll(buildStackOperationStatements(stack, emptyList()))
        }

    private fun buildStackOperationStatements(
        stack: List<ImmutableProp>,
        newStack: List<ImmutableProp>,
    ): List<LsiStatement> {
        val size = min(stack.size, newStack.size)
        var sameCount = size
        for (i in 0 until size) {
            if (stack[i] !== newStack[i]) {
                sameCount = i
                break
            }
        }
        return buildList {
            for (i in stack.size - sameCount downTo 1) {
                add(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            receiver = LsiNameExpression("_applier"),
                            name = "pop",
                        )
                    )
                )
            }
            for (prop in newStack.subList(sameCount, newStack.size)) {
                add(
                    LsiExpressionStatement(
                        LsiCallExpression(
                            receiver = LsiNameExpression("_applier"),
                            name = "push",
                            arguments = listOf(prop.unwrapExpression())
                        )
                    )
                )
            }
        }
    }

    private fun buildPredicateOperationStatements(
        prop: LsiDtoPropView
    ): List<LsiStatement> {
        val propName = prop.name
        val tailProp = prop.tailPropView
        val targetType = prop.targetType
        val propExpression = currentPropExpression(propName, propLsiTypeName(prop))
        if (targetType !== null) {
            return listOf(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = propExpression,
                        operator = LsiBinaryOperator.NOT_EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(
                        LsiExpressionStatement(
                            LsiCallExpression(
                                receiver = propExpression,
                                name = "applyTo",
                                arguments = listOf(
                                    if (targetType.baseType.isEntity) {
                                        LsiCallExpression(
                                            receiver = LsiNameExpression("args"),
                                            name = "child",
                                        )
                                    } else {
                                        LsiPropertyGetExpression(
                                            receiver = LsiNameExpression("args"),
                                            name = "applier",
                                            type = PREDICATE_APPLIER_LSI_CLASS_NAME,
                                        )
                                    }
                                )
                            )
                        )
                    )
                )
            )
        }

        val funcName: String = when (tailProp.funcName) {
            null -> "eq"
            "id" -> "associatedIdEq"
            else -> tailProp.funcName ?: error("Internal bug: funcName is null")
        }
        val ktFunName: String = when (funcName) {
            "null" -> "isNull"
            "notNull" -> "isNotNull"
            else -> funcName
        }

        val targetExpression = if (Constants.MULTI_ARGS_FUNC_NAMES.contains(funcName)) {
            LsiArrayExpression(
                elementType = IMMUTABLE_PROP_LSI_CLASS_NAME,
                elements = tailProp.tailBasePropMapValues.map { baseProp ->
                    baseProp.unwrapExpression()
                }
            )
        } else {
            tailProp.baseProp.unwrapExpression()
        }
        val valueExpression = if (isSpecificationConverterRequired(tailProp)) {
            LsiCallExpression(
                name = StringUtil.identifier("_convert", propName),
                arguments = listOf(propExpression),
            )
        } else {
            propExpression
        }
        val arguments = mutableListOf<LsiExpression>(
            targetExpression,
            valueExpression,
        )
        if (funcName == "like") {
            arguments += LsiLiteralExpression(tailProp.likeOptions.contains(LikeOption.INSENSITIVE))
            arguments += LsiLiteralExpression(tailProp.likeOptions.contains(LikeOption.MATCH_START))
            arguments += LsiLiteralExpression(tailProp.likeOptions.contains(LikeOption.MATCH_END))
        }
        return listOf(
            LsiExpressionStatement(
                LsiCallExpression(
                    receiver = LsiNameExpression("_applier"),
                    name = ktFunName,
                    arguments = arguments,
                )
            )
        )
    }

    private fun isSimpleProp(prop: LsiDtoPropView): Boolean {
        if (prop.hasNextProp) {
            return false
        }
        return if ((prop.isNullable && (!prop.baseProp.isNullable || dtoType.modifiers.contains(DtoModifier.SPECIFICATION))) ||
            (prop.baseProp.converterMetadata !== null &&
                    !dtoType.modifiers.contains(DtoModifier.INPUT) &&
                    !dtoType.modifiers.contains(DtoModifier.SPECIFICATION))
        ) {
            false
        } else {
            propLsiTypeName(prop) == prop.baseProp.toLsiTypeName()
        }
    }

    private fun addAccessorField(prop: LsiDtoPropView) {
        if (isSimpleProp(prop)) {
            return
        }
        addAccessorSupportCallables(prop)

        addTypeProperty(
            LsiPropertySpec(
                name = StringUtil.snake("${prop.name}Accessor", SnakeCase.UPPER),
                type = DTO_PROP_ACCESSOR_LSI_CLASS_NAME,
                modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
                initializer = buildAccessorInitializerExpression(prop)
            )
        )
    }

    private fun addAccessorSupportCallables(prop: LsiDtoPropView) {
        if (prop.enumType == null) {
            return
        }
        addTypeCallable(buildEnumToValueHelperCallable(prop))
        addTypeCallable(buildValueToEnumHelperCallable(prop))
    }

    private fun addSpecificationConverter(prop: LsiDtoPropView) {
        if (!isSpecificationConverterRequired(prop)) {
            return
        }
        val baseProp = prop.tailBaseProp
        val baseTypeName = when (prop.funcName) {
            "id" -> baseProp.targetType!!.idProp!!.toLsiTypeName().let {
                if (baseProp.isList && !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                    it.toList(true)
                } else {
                    it
                }
            }

            "valueIn", "valueNotIn" ->
                baseProp.toLsiTypeName().copyNullable(false).toList(true)

            "associatedIdEq", "associatedIdNe" ->
                baseProp.targetType!!.idProp!!.toLsiTypeName()

            "associatedIdIn", "associatedIdNotIn" ->
                baseProp.targetType!!.idProp!!.toLsiTypeName().copyNullable(false).toList(true)

            else -> baseProp.toLsiTypeName()
        }.copyNullable(prop.isNullable)
        addTypeCallable(
            LsiCallableSpec(
                kind = LsiCallableSpecKind.FUNCTION,
                name = StringUtil.identifier("_convert", prop.name),
                modifiers = setOf(LsiModifier.PUBLIC),
                parameters = listOf(
                    LsiParameterSpec(
                        name = "value",
                        type = propLsiTypeName(prop),
                    )
                ),
                returnType = baseTypeName,
                statements = buildSpecificationConverterStatements(prop, baseTypeName)
            )
        )
    }

    private fun addHibernateValidatorEnhancement(getter: Boolean) {
        addTypeCallable(buildHibernateValidatorEnhancementCallable(getter))
    }

    private fun buildHibernateValidatorEnhancementCallable(getter: Boolean): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "\$\$_hibernateValidator_get${if (getter) "Getter" else "Field"}Value",
            modifiers = setOf(LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "name",
                    type = LsiClassName.bestGuess("kotlin.String"),
                )
            ),
            returnType = LsiClassName.bestGuess("kotlin.Any", nullable = true),
            statements = buildHibernateValidatorEnhancementStatements(getter)
        )

    internal fun propLsiTypeName(prop: LsiDtoAbstractPropView): LsiTypeName =
        when (prop) {
            is LsiDtoPropView -> propLsiTypeName(prop)
            is LsiUserPropView -> lsiTypeName(prop.typeRef)
        }

    private fun propLsiTypeName(prop: LsiDtoPropView): LsiTypeName {
        val baseProp = prop.tailBaseProp
        val enumType = prop.enumType
        if (enumType !== null) {
            return (if (enumType.isNumeric) INT_LSI_CLASS_NAME else STRING_LSI_CLASS_NAME)
                .copyNullable(prop.isNullable)
        }

        val metadata = prop.dtoConverterMetadata
        val propElementName = propElementLsiTypeName(prop)
        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            val funcName = prop.tailPropView.funcName
            if (funcName != null) {
                when (funcName) {
                    "null", "notNull" ->
                        return KOTLIN_BOOLEAN_LSI_CLASS_NAME.copyNullable(prop.isNullable)

                    "valueIn", "valueNotIn" ->
                        return LsiParameterizedTypeName(
                            rawType = LsiClassName.bestGuess("kotlin.collections.Collection"),
                            typeArguments = listOf(
                                (
                                    metadata?.targetTypeName
                                        ?: propElementName.toList(baseProp.isList)
                                    ).copyNullable(false)
                            ),
                            nullable = prop.isNullable
                        )

                    "id", "associatedIdEq", "associatedIdNe" ->
                        return baseProp.targetType!!.idProp!!.clientLsiTypeName
                            .copyNullable(prop.isNullable)

                    "associatedIdIn", "associatedIdNotIn" ->
                        return LsiParameterizedTypeName(
                            rawType = LsiClassName.bestGuess("kotlin.collections.Collection"),
                            typeArguments = listOf(
                                baseProp.targetType!!.idProp!!.clientLsiTypeName
                                    .copyNullable(false)
                            ),
                            nullable = prop.isNullable
                        )
                }
            }
            if (baseProp.isAssociation(true)) {
                return propElementName.copyNullable(prop.isNullable)
            }
        }
        if (metadata != null) {
            return metadata.targetTypeName.copyNullable(prop.isNullable)
        }

        return propElementName
            .toList(
                baseProp.isList &&
                    !(propElementName is LsiParameterizedTypeName && propElementName.rawType == KOTLIN_LIST_LSI_CLASS_NAME)
            )
            .copyNullable(prop.isNullable)
    }

    private fun propElementLsiTypeName(prop: LsiDtoPropView): LsiTypeName {
        val tailProp = prop.tailPropView
        val targetType = tailProp.targetType
        if (targetType !== null) {
            if (tailProp.isRecursive && !targetType.isFocusedRecursion) {
                return getDtoLsiClassName()
            }
            if (targetType.name === null) {
                val list: MutableList<String> = ArrayList()
                collectNames(list)
                if (!prop.isRecursive || targetType.isFocusedRecursion) {
                    list.add(targetSimpleName(tailProp))
                }
                return LsiClassName(
                    packageName = root.dtoType.packageName,
                    simpleNames = buildList {
                        add(list[0])
                        addAll(list.subList(1, list.size))
                    }
                )
            }
            return LsiClassName(
                packageName = root.dtoType.packageName,
                simpleNames = listOf(targetType.name!!)
            )
        }
        val baseProp = tailProp.baseProp
        return if (tailProp.isIdOnly) {
            baseProp.targetType!!.idProp!!.clientLsiTypeName
        } else if (baseProp.idViewBaseProp !== null) {
            baseProp.idViewBaseProp!!.targetType!!.idProp!!.clientLsiTypeName
        } else {
            tailProp.baseProp.clientLsiTypeName
        }.copyNullable(false)
    }

    private fun collectNames(list: MutableList<String>) {
        if (parent == null) {
            list.add(dtoType.name!!)
        } else if (innerClassName !== null) {
            parent.collectNames(list)
            list.add(innerClassName)
        }
    }

    private fun targetSimpleName(prop: LsiDtoPropView): String {
        val targetType = prop.targetType ?: throw IllegalArgumentException("prop is not association")
        if (prop.isRecursive && !targetType.isFocusedRecursion) {
            return innerClassName ?: dtoType.name ?: error("Internal bug: No target simple name")
        }
        return standardTargetSimpleName("TargetOf_${prop.name}")
    }

    private fun standardTargetSimpleName(targetSimpleName: String): String {
        var conflict = false
        var generator: DtoGenerator? = this
        while (generator != null) {
            if ((generator.innerClassName ?: generator.dtoType.name) == targetSimpleName) {
                conflict = true
                break
            }
            generator = generator.parent
        }
        if (!conflict) {
            return targetSimpleName
        }
        for (i in 2..99) {
            conflict = false
            val newTargetSimpleName = targetSimpleName + '_' + i
            generator = this
            while (generator != null) {
                if ((generator.innerClassName ?: generator.dtoType.name) == newTargetSimpleName) {
                    conflict = true
                    break
                }
                generator = generator.parent
            }
            if (!conflict) {
                return newTargetSimpleName
            }
        }
        throw AssertionError("Dto is too deep")
    }

    private fun buildAccessorInitializerExpression(
        prop: LsiDtoPropView,
    ): LsiExpression =
        LsiNewExpression(
            type = DTO_PROP_ACCESSOR_LSI_CLASS_NAME,
            arguments = buildAccessorInitializerArguments(prop),
        )

    private fun buildAccessorInitializerArguments(
        prop: LsiDtoPropView,
    ): List<LsiExpression> =
        buildList {
            add(
                LsiLiteralExpression(
                    !(
                        prop.isNullable && (
                            !prop.tailBaseProp.isNullable ||
                                dtoType.modifiers.contains(DtoModifier.SPECIFICATION) ||
                                dtoType.modifiers.contains(DtoModifier.FUZZY) ||
                                prop.inputModifier == DtoModifier.FUZZY
                            )
                        )
                )
            )
            add(buildAccessorSlotArrayExpression(prop))
            buildAccessorConverters(prop).forEach(::add)
        }

    private fun buildAccessorSlotArrayExpression(
        prop: LsiDtoPropView,
    ): LsiIntArrayExpression =
        LsiIntArrayExpression(
            elements = prop.pathBaseProps.map { baseProp ->
                LsiPropertyAccessExpression(
                    receiver = LsiTypeExpression(baseProp.declaringType.lsiDraftClassName("$")),
                    name = baseProp.slotName,
                )
            }
        )

    private fun buildAccessorConverters(
        prop: LsiDtoPropView,
    ): List<LsiExpression> {
        val tailProp = prop.tailPropView
        val tailBaseProp = tailProp.baseProp
        return when {
            prop.isIdOnly -> {
                val targetTypeName = tailBaseProp.toTargetLsiTypeName(overrideNullable = false).copyNullable(false)
                val converter = buildConverterLoadingExpression(prop, false)
                listOf(
                    buildAccessorStaticCall(
                        name = if (tailBaseProp.isList) "idListGetter" else "idReferenceGetter",
                        arguments = listOf(
                            LsiJavaClassExpression(targetTypeName),
                            converter,
                        ),
                    ),
                    buildAccessorStaticCall(
                        name = if (tailBaseProp.isList) "idListSetter" else "idReferenceSetter",
                        arguments = listOf(
                            LsiJavaClassExpression(targetTypeName),
                            converter,
                        ),
                    ),
                )
            }
            tailProp.targetType != null -> {
                val targetTypeName = tailBaseProp.toTargetLsiTypeName(overrideNullable = false).copyNullable(false)
                val elementTypeName = propElementLsiTypeName(prop)
                listOf(
                    buildAccessorStaticCall(
                        name = if (tailBaseProp.isList) "objectListGetter" else "objectReferenceGetter",
                        typeArguments = listOf(targetTypeName, elementTypeName),
                        arguments = listOf(
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.EXPRESSION,
                                parameterNames = listOf("it"),
                                expression = LsiNewExpression(
                                    type = propElementLsiTypeName(prop).copyNullable(false) as LsiClassName,
                                    arguments = listOf(LsiNameExpression("it")),
                                ),
                            )
                        )
                    ),
                    buildAccessorStaticCall(
                        name = if (tailBaseProp.isList) "objectListSetter" else "objectReferenceSetter",
                        typeArguments = listOf(targetTypeName, elementTypeName),
                        arguments = listOf(
                            LsiLambdaExpression(
                                mode = LsiLambdaMode.EXPRESSION,
                                parameterNames = listOf("it"),
                                expression = LsiCallExpression(
                                    receiver = LsiNameExpression("it"),
                                    name = if (tailBaseProp.targetType!!.isEntity) "toEntity" else "toImmutable",
                                ),
                            )
                        )
                    ),
                )
            }
            prop.enumType != null -> listOf(
                LsiLambdaExpression(
                    mode = LsiLambdaMode.EXPRESSION,
                    parameterNames = listOf("it"),
                    expression = LsiCallExpression(
                        name = enumToValueHelperName(prop),
                        arguments = listOf(LsiNameExpression("it")),
                    ),
                ),
                LsiLambdaExpression(
                    mode = LsiLambdaMode.EXPRESSION,
                    parameterNames = listOf("it"),
                    expression = LsiCallExpression(
                        name = valueToEnumHelperName(prop),
                        arguments = listOf(LsiNameExpression("it")),
                    ),
                ),
            )
            prop.dtoConverterMetadata != null -> {
                val converter = buildConverterLoadingExpression(prop, true)
                listOf(
                    LsiLambdaExpression(
                        mode = LsiLambdaMode.EXPRESSION,
                        parameterNames = listOf("it"),
                        expression = LsiCallExpression(
                            receiver = converter,
                            name = "output",
                            arguments = listOf(LsiNameExpression("it")),
                        ),
                    ),
                    LsiLambdaExpression(
                        mode = LsiLambdaMode.EXPRESSION,
                        parameterNames = listOf("it"),
                        expression = LsiCallExpression(
                            receiver = converter,
                            name = "input",
                            arguments = listOf(LsiNameExpression("it")),
                        ),
                    ),
                )
            }
            else -> emptyList()
        }
    }

    private fun buildAccessorStaticCall(
        name: String,
        typeArguments: List<LsiTypeName> = emptyList(),
        arguments: List<LsiExpression>,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiTypeExpression(DTO_PROP_ACCESSOR_LSI_CLASS_NAME),
            name = name,
            typeArguments = typeArguments,
            arguments = arguments,
        )

    private fun enumToValueHelperName(prop: LsiDtoPropView): String =
        StringUtil.identifier("__", prop.name, "EnumToValue")

    private fun valueToEnumHelperName(prop: LsiDtoPropView): String =
        StringUtil.identifier("__", prop.name, "ValueToEnum")

    private fun buildEnumToValueHelperCallable(
        prop: LsiDtoPropView,
    ): LsiCallableSpec {
        val enumTypeName = prop.tailBaseProp.toLsiTypeName(overrideNullable = false).copyNullable(false)
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = enumToValueHelperName(prop),
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "it",
                    type = enumTypeName,
                )
            ),
            returnType = propLsiTypeName(prop).copyNullable(false),
            statements = listOf(
                LsiWhenStatement(
                    subject = LsiCastExpression(
                        type = enumTypeName,
                        expression = LsiNameExpression("it"),
                    ),
                    cases = prop.enumValueMap.map { (enumName, value) ->
                        LsiWhenCase(
                            conditions = listOf(
                                LsiEnumConstantExpression(
                                    type = enumTypeName as LsiClassName,
                                    constantName = enumName,
                                )
                            ),
                            statements = listOf(LsiReturnStatement(LsiLiteralExpression(value))),
                        )
                    },
                    elseStatements = listOf(
                        LsiThrowStatement(
                            LsiNewExpression(
                                type = ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                                arguments = listOf(
                                    illegalEnumValueMessageExpression(
                                        variableName = "it",
                                        enumTypeName = enumTypeName.toString(),
                                    )
                                ),
                            )
                        )
                    ),
                )
            ),
        )
    }

    private fun buildValueToEnumHelperCallable(
        prop: LsiDtoPropView,
    ): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = valueToEnumHelperName(prop),
            modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC),
            parameters = listOf(
                LsiParameterSpec(
                    name = "it",
                    type = propLsiTypeName(prop).copyNullable(false),
                )
            ),
            returnType = prop.tailBaseProp.toLsiTypeName(overrideNullable = false).copyNullable(false),
            statements = buildValueToEnumStatements(prop),
        )

    private fun buildSpecificationConverterStatements(
        prop: LsiDtoPropView,
        baseTypeName: LsiTypeName,
    ): List<LsiStatement> =
        buildList {
            if (prop.isNullable) {
                add(
                    LsiIfStatement(
                        condition = LsiBinaryExpression(
                            left = LsiNameExpression("value"),
                            operator = LsiBinaryOperator.IDENTITY_EQUALS,
                            right = LsiNullExpression,
                        ),
                        thenStatements = listOf(LsiReturnStatement(LsiNullExpression)),
                    )
                )
            }
            if (prop.enumType !== null) {
                addAll(buildValueToEnumStatements(prop, "value"))
            } else {
                add(
                    LsiReturnStatement(
                        LsiCallExpression(
                            receiver = buildSpecificationConverterLoadingExpression(prop, baseTypeName),
                            name = "input",
                            arguments = listOf(LsiNameExpression("value")),
                        )
                    )
                )
            }
        }

    private fun buildSpecificationConverterLoadingExpression(
        prop: LsiDtoPropView,
        baseTypeName: LsiTypeName,
    ): LsiExpression {
        val baseProp = prop.tailBaseProp
        val methodName = if (baseProp.isAssociation(true)) {
            "getAssociatedIdConverter"
        } else {
            "getConverter"
        }
        val arguments = if (baseProp.isAssociation(true) || prop.isFunc("valueIn", "valueNotIn")) {
            listOf<LsiExpression>(LsiLiteralExpression(true))
        } else {
            emptyList()
        }
        return LsiCallExpression(
            receiver = baseProp.unwrapExpression(),
            name = methodName,
            typeArguments = listOf(
                baseTypeName.copyNullable(false),
                propLsiTypeName(prop).copyNullable(false),
            ),
            arguments = arguments,
        )
    }

    private fun buildHibernateValidatorEnhancementStatements(getter: Boolean): List<LsiStatement> =
        listOf(
            LsiWhenStatement(
                subject = LsiNameExpression("name"),
                cases = dtoType.propViews.map { prop ->
                    val lookupName = if (getter) {
                        StringUtil.identifier(
                            if (propLsiTypeName(prop).copyNullable(false) == KOTLIN_BOOLEAN_LSI_CLASS_NAME) "is" else "get",
                            prop.name
                        )
                    } else {
                        prop.name
                    }
                    LsiWhenCase(
                        conditions = listOf(LsiLiteralExpression(lookupName)),
                        statements = listOf(LsiReturnStatement(LsiNameExpression(prop.name))),
                    )
                },
                elseStatements = listOf(
                    LsiThrowStatement(
                        LsiNewExpression(
                            type = ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                            arguments = listOf(
                                LsiBinaryExpression(
                                    left = LsiLiteralExpression("No ${if (getter) "getter" else "field"} named \""),
                                    operator = LsiBinaryOperator.PLUS,
                                    right = LsiBinaryExpression(
                                        left = LsiNameExpression("name"),
                                        operator = LsiBinaryOperator.PLUS,
                                        right = LsiLiteralExpression("\""),
                                    ),
                                )
                            ),
                        )
                    )
                ),
            )
        )

    private fun buildValueToEnumStatements(
        prop: LsiDtoPropView,
        variableName: String = "it"
    ): List<LsiStatement> {
        val valueTypeName = propLsiTypeName(prop).copyNullable(false)
        val enumTypeName = prop.tailBaseProp.toLsiTypeName(overrideNullable = false)
            .copyNullable(false) as LsiClassName
        return listOf(
            LsiWhenStatement(
                subject = LsiCastExpression(
                    type = valueTypeName,
                    expression = LsiNameExpression(variableName),
                ),
                cases = prop.enumConstantMap.map { (value, enumName) ->
                    LsiWhenCase(
                        conditions = listOf(LsiLiteralExpression(value)),
                        statements = listOf(
                            LsiReturnStatement(
                                LsiEnumConstantExpression(
                                    type = enumTypeName,
                                    constantName = enumName,
                                )
                            )
                        ),
                    )
                },
                elseStatements = listOf(
                    LsiThrowStatement(
                        LsiNewExpression(
                            type = ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME,
                            arguments = listOf(
                                illegalEnumValueMessageExpression(
                                    variableName = variableName,
                                    enumTypeName = enumTypeName.toString(),
                                )
                            ),
                        )
                    )
                ),
            )
        )
    }

    private fun illegalEnumValueMessageExpression(
        variableName: String,
        enumTypeName: String,
    ): LsiExpression =
        LsiBinaryExpression(
            left = LsiLiteralExpression("Illegal value \""),
            operator = LsiBinaryOperator.PLUS,
            right = LsiBinaryExpression(
                left = LsiNameExpression(variableName),
                operator = LsiBinaryOperator.PLUS,
                right = LsiBinaryExpression(
                    left = LsiLiteralExpression("\" for the enum type \""),
                    operator = LsiBinaryOperator.PLUS,
                    right = LsiBinaryExpression(
                        left = LsiLiteralExpression(enumTypeName),
                        operator = LsiBinaryOperator.PLUS,
                        right = LsiLiteralExpression("\""),
                    ),
                ),
            ),
        )

    private fun buildConverterLoadingExpression(
        prop: LsiDtoPropView,
        forList: Boolean,
    ): LsiExpression {
        val baseProp: ImmutableProp = prop.tailBaseProp
        return LsiCallExpression(
            receiver = baseProp.unwrapExpression(),
            name = if (baseProp.isAssociation(true)) {
                "getAssociatedIdConverter"
            } else {
                "getConverter"
            },
            arguments = if (baseProp.isAssociation(true)) {
                listOf(LsiLiteralExpression(forList))
            } else {
                emptyList()
            },
        )
    }

    private fun isSpecificationConverterRequired(prop: LsiDtoPropView): Boolean {
        return if (!dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            false
        } else {
            prop.enumType != null || prop.dtoConverterMetadata != null
        }
    }

    private val LsiDtoPropView.dtoConverterMetadata: ConverterMetadata?
        get() = resolveDtoConverterMetadata(dtoType.modifiers)

    private fun allowedTargets(typeName: String): Set<LsiAnnotationUseSiteTarget> =
        useSiteTargetMap.computeIfAbsent(typeName) { tn ->
            // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.allowedTargets 注解类型查找
            // 迁移说明：注解声明查找由 resolver.getClassDeclarationByName(...) 迁移为 LSI resolver
            val targets = DtoAnnotationSupport.resolveTargetsOrNull(ctx.lsiResolver, tn)
                ?: error("Internal bug, cannot resolve annotation type \"$typeName\"")
            // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.allowedTargets 的 `AnnotationUseSiteTarget` 集合缓存
            // 迁移说明：缓存值统一使用 LSI 目标，避免共享层状态直接绑定 KotlinPoet 枚举
            val useSiteTargets = mutableSetOf<LsiAnnotationUseSiteTarget>()
            if (targets.field) {
                useSiteTargets += LsiAnnotationUseSiteTarget.FIELD
            }
            if (targets.getter) {
                useSiteTargets += LsiAnnotationUseSiteTarget.GET
            }
            if (targets.setter) {
                useSiteTargets += LsiAnnotationUseSiteTarget.SET
            }
            if (targets.property) {
                useSiteTargets += LsiAnnotationUseSiteTarget.PROPERTY
            }
            useSiteTargets
        }

    private fun addCopy() {
        addTypeCallable(buildCopyCallable())
    }

    private fun buildCopyCallable(): LsiCallableSpec {
        val args = mutableListOf<LsiExpression>()
        val parameters = buildList {
            for (dtoProp in dtoType.dtoPropViews) {
                add(
                    LsiParameterSpec(
                        name = dtoProp.name,
                        type = propLsiTypeName(dtoProp),
                        defaultValue = LsiCodeBlock.of(
                            "%L",
                            LsiPropertyGetExpression(
                                receiver = LsiThisExpression,
                                name = dtoProp.name,
                                type = propLsiTypeName(dtoProp),
                            )
                        )
                    )
                )
                args += LsiNameExpression(dtoProp.name)
                statePropName(dtoProp, false)?.let {
                    add(
                        LsiParameterSpec(
                            name = it,
                            type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                            defaultValue = LsiCodeBlock.of(
                                "%L",
                                LsiPropertyGetExpression(
                                    receiver = LsiThisExpression,
                                    name = it,
                                    type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                                )
                            )
                        )
                    )
                    args += LsiNameExpression(it)
                }
            }
            for (userProp in dtoType.userPropViews) {
                val propType = lsiTypeName(userProp.typeRef)
                add(
                    LsiParameterSpec(
                        name = userProp.name,
                        type = propType,
                        defaultValue = LsiCodeBlock.of(
                            "%L",
                            LsiPropertyGetExpression(
                                receiver = LsiThisExpression,
                                name = userProp.name,
                                type = propType,
                            )
                        )
                    )
                )
                args += LsiNameExpression(userProp.name)
            }
        }
        return LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "copy",
            returnType = getDtoLsiClassName(),
            parameters = parameters,
            statements = listOf(
                LsiReturnStatement(
                    LsiNewExpression(
                        type = getDtoLsiClassName(),
                        arguments = args,
                    )
                )
            )
        )
    }

    private fun addHashCode() {
        addTypeCallable(buildHashCodeCallable())
    }

    private fun buildHashCodeCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "hashCode",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            returnType = LsiClassName.bestGuess("kotlin.Int"),
            statements = buildHashCodeStatements(),
        )

    private fun buildHashCodeStatements(): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "_hash",
                    type = INT_LSI_CLASS_NAME,
                    mutable = true,
                    initializer = LsiLiteralExpression(0),
                )
            )
            dtoType.propViews.forEachIndexed { index, prop ->
                addAll(buildHashContributionStatements(index, prop))
                statePropName(prop, false)?.let { stateProp ->
                    add(hashAccumulatorAssignment(hashCall(LsiNameExpression(stateProp), "hashCode")))
                }
            }
            add(LsiReturnStatement(LsiNameExpression("_hash")))
        }

    private fun buildHashContributionStatements(index: Int, prop: LsiDtoAbstractPropView): List<LsiStatement> {
        val hashExpression = hashCall(
            receiver = LsiNameExpression(prop.name),
            methodName = if (propLsiTypeName(prop).isArrayType()) "contentHashCode" else "hashCode",
        )
        if (!prop.isNullable) {
            return listOf(hashAccumulatorAssignment(hashExpression))
        }
        val contributionName = "__hashContribution$index"
        return listOf(
            LsiVariableDeclarationStatement(
                name = contributionName,
                type = INT_LSI_CLASS_NAME,
                mutable = true,
                initializer = LsiLiteralExpression(0),
            ),
            LsiIfStatement(
                condition = LsiBinaryExpression(
                    left = LsiNameExpression(prop.name),
                    operator = LsiBinaryOperator.NOT_EQUALS,
                    right = LsiLiteralExpression(null),
                ),
                thenStatements = listOf(
                    LsiAssignmentStatement(
                        target = LsiNameExpression(contributionName),
                        expression = hashExpression,
                    )
                ),
            ),
            hashAccumulatorAssignment(LsiNameExpression(contributionName)),
        )
    }

    private fun hashAccumulatorAssignment(expression: LsiExpression): LsiAssignmentStatement =
        LsiAssignmentStatement(
            target = LsiNameExpression("_hash"),
            expression = LsiBinaryExpression(
                left = LsiBinaryExpression(
                    left = LsiLiteralExpression(31),
                    operator = LsiBinaryOperator.TIMES,
                    right = LsiNameExpression("_hash"),
                ),
                operator = LsiBinaryOperator.PLUS,
                right = expression,
            ),
        )

    private fun hashCall(receiver: LsiExpression, methodName: String): LsiCallExpression =
        LsiCallExpression(
            receiver = receiver,
            name = methodName,
        )

    private fun addEquals() {
        addTypeCallable(buildEqualsCallable())
    }

    private fun buildEqualsCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "equals",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            parameters = listOf(
                LsiParameterSpec(
                    name = "other",
                    type = LsiClassName.bestGuess("kotlin.Any", nullable = true),
                )
            ),
            returnType = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
            statements = buildEqualsStatements(),
        )

    private fun buildEqualsStatements(): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "_other",
                    type = getDtoLsiClassName().copyNullable(true),
                    initializer = LsiSafeCastExpression(
                        type = getDtoLsiClassName(),
                        expression = LsiNameExpression("other"),
                    ),
                )
            )
            add(
                LsiIfStatement(
                    condition = LsiBinaryExpression(
                        left = LsiNameExpression("_other"),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiNullExpression,
                    ),
                    thenStatements = listOf(LsiReturnStatement(LsiLiteralExpression(false))),
                )
            )
            add(
                LsiReturnStatement(
                    buildEqualsExpression()
                )
            )
        }

    private fun buildEqualsExpression(): LsiExpression {
        val expressions = dtoType.propViews.map { prop ->
            val propExpression = equalsExpression(prop)
            val statePropName = statePropName(prop, false)
            if (statePropName == null) {
                propExpression
            } else {
                val stateEquals = LsiBinaryExpression(
                    left = LsiNameExpression(statePropName),
                    operator = LsiBinaryOperator.EQUALS,
                    right = LsiPropertyGetExpression(
                        receiver = LsiNameExpression("_other"),
                        name = statePropName,
                        type = KOTLIN_BOOLEAN_LSI_CLASS_NAME,
                    ),
                )
                val stateAllowsComparison = LsiBinaryExpression(
                    left = LsiBinaryExpression(
                        left = LsiNameExpression(statePropName),
                        operator = LsiBinaryOperator.EQUALS,
                        right = LsiLiteralExpression(false),
                    ),
                    operator = LsiBinaryOperator.OR,
                    right = propExpression,
                )
                LsiBinaryExpression(
                    left = stateEquals,
                    operator = LsiBinaryOperator.AND,
                    right = stateAllowsComparison,
                )
            }
        }
        return expressions.reduceOrNull { acc, expression ->
            LsiBinaryExpression(
                left = acc,
                operator = LsiBinaryOperator.AND,
                right = expression,
            )
        } ?: LsiLiteralExpression(true)
    }

    private fun equalsExpression(prop: LsiDtoAbstractPropView): LsiExpression {
        val otherProp = LsiPropertyGetExpression(
            receiver = LsiNameExpression("_other"),
            name = prop.name,
            type = propLsiTypeName(prop),
        )
        return if (propLsiTypeName(prop).isArrayType()) {
            LsiCallExpression(
                receiver = LsiNameExpression(prop.name),
                name = "contentEquals",
                arguments = listOf(otherProp),
            )
        } else {
            LsiBinaryExpression(
                left = LsiNameExpression(prop.name),
                operator = LsiBinaryOperator.EQUALS,
                right = otherProp,
            )
        }
    }

    private fun addToString() {
        addTypeCallable(buildToStringCallable())
    }

    private fun buildToStringCallable(): LsiCallableSpec =
        LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "toString",
            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
            returnType = LsiClassName.bestGuess("kotlin.String"),
            statements = buildToStringStatements(),
        )

    private fun buildToStringStatements(): List<LsiStatement> =
        buildList {
            add(
                LsiVariableDeclarationStatement(
                    name = "__builder",
                    type = STRING_BUILDER_LSI_CLASS_NAME,
                    initializer = LsiNewExpression(STRING_BUILDER_LSI_CLASS_NAME),
                )
            )
            add(
                LsiVariableDeclarationStatement(
                    name = "__separator",
                    type = STRING_LSI_CLASS_NAME,
                    mutable = true,
                    initializer = LsiLiteralExpression(""),
                )
            )
            add(
                LsiExpressionStatement(
                    appendCall(
                        appendCall(
                            LsiNameExpression("__builder"),
                            LsiLiteralExpression(simpleNamePart()),
                        ),
                        LsiLiteralExpression("("),
                    )
                )
            )
            for (prop in dtoType.dtoPropViews) {
                this.addToStringPropStatements(
                    label = "${prop.name}=",
                    value = currentPropExpression(prop.name, propLsiTypeName(prop)),
                    condition = toStringConditionExpression(prop),
                )
            }
            for (prop in dtoType.userPropViews) {
                this.addToStringPropStatements(
                    label = "${prop.name}=",
                    value = currentPropExpression(prop.name, lsiTypeName(prop.typeRef)),
                    condition = null,
                )
            }
            add(
                LsiExpressionStatement(
                    appendCall(
                        LsiNameExpression("__builder"),
                        LsiLiteralExpression(")"),
                    )
                )
            )
            add(
                LsiReturnStatement(
                    LsiCallExpression(
                        receiver = LsiNameExpression("__builder"),
                        name = "toString",
                    )
                )
            )
        }

    private fun MutableList<LsiStatement>.addToStringPropStatements(
        label: String,
        value: LsiExpression,
        condition: LsiExpression?,
    ) {
        val statements = listOf(
            LsiExpressionStatement(
                appendCall(
                    appendCall(
                        appendCall(
                            LsiNameExpression("__builder"),
                            LsiNameExpression("__separator"),
                        ),
                        LsiLiteralExpression(label),
                    ),
                    value,
                )
            ),
            LsiAssignmentStatement(
                target = LsiNameExpression("__separator"),
                expression = LsiLiteralExpression(", "),
            ),
        )
        if (condition == null) {
            addAll(statements)
        } else {
            add(LsiIfStatement(condition = condition, thenStatements = statements))
        }
    }

    private fun toStringConditionExpression(prop: LsiDtoPropView): LsiExpression? {
        val stateFieldName = statePropName(prop, false)
        if (stateFieldName != null) {
            return currentPropExpression(stateFieldName, KOTLIN_BOOLEAN_LSI_CLASS_NAME)
        }
        if (prop.inputModifier == DtoModifier.FUZZY) {
            return LsiBinaryExpression(
                left = currentPropExpression(prop.name, propLsiTypeName(prop)),
                operator = LsiBinaryOperator.NOT_EQUALS,
                right = LsiNullExpression,
            )
        }
        return null
    }

    private fun currentPropExpression(name: String, type: LsiTypeName): LsiExpression =
        LsiPropertyGetExpression(
            receiver = LsiThisExpression,
            name = name,
            type = type,
        )

    private fun appendCall(receiver: LsiExpression, value: LsiExpression): LsiCallExpression =
        LsiCallExpression(
            receiver = receiver,
            name = "append",
            arguments = listOf(value),
        )

    private fun simpleNamePart(): String =
        (innerClassName ?: dtoType.name!!).let { name ->
            parent
                ?.let { "${it.simpleNamePart()}.$name" }
                ?: name
        }

    private inner class Document {

        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.Document.dtoTypeDoc
        // 迁移说明：DTO 类型文档解析改为复用 LSI 文档对象，移除生成阶段对 jimmer runtime `Doc.parse` 的直接依赖
        private val dtoTypeDoc: LsiDoc? by lazy {
            LsiDoc.parse(dtoType.doc)
        }

        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.Document.baseTypeDoc
        // 迁移说明：基类文档解析同样改为 LSI 文档对象，统一 DTO 文档读取语义
        private val baseTypeDoc: LsiDoc? by lazy {
            LsiDoc.parse(baseDocString)
        }

        val value: String? by lazy {
            (dtoTypeDoc?.toString() ?: baseTypeDoc?.toString())?.replace("%", "%%")
        }

        operator fun get(prop: LsiDtoAbstractPropView): String? {
            return getImpl(prop)?.let {
                it.replace("%", "%%")
            }
        }

        private fun getImpl(prop: LsiDtoAbstractPropView): String? {
            val baseProp = (prop as? LsiDtoPropView)?.tailBaseProp
            if (prop.doc !== null) {
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.Document.getImpl prop.doc 解析
                // 迁移说明：属性内联文档解析改为复用 LSI 文档 parse
                val doc = LsiDoc.parse(prop.doc)
                if (doc != null) {
                    return doc.toString()
                }
            }
            val dtoTypeDoc = this.dtoTypeDoc
            if (dtoTypeDoc != null) {
                val name = prop.declaredAlias ?: baseProp!!.name
                val doc = dtoTypeDoc.parameterValueMap[name]
                if (doc != null) {
                    return doc
                }
            }
            if (baseProp != null) {
                // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.Document.getImpl baseProp 文档解析
                // 迁移说明：基础属性文档解析改为 LSI 文档对象，避免 DTO 生成器直连 jimmer runtime `Doc`
                val doc = LsiDoc.parse(baseDocString(baseProp))
                if (doc != null) {
                    return doc.toString()
                }
            }
            val baseTypeDoc = this.baseTypeDoc
            if (baseTypeDoc != null && baseProp != null) {
                val doc = baseTypeDoc.parameterValueMap[baseProp.name]
                if (doc != null) {
                    return doc
                }
            }
            return null
        }
    }

    private val isImpl: Boolean
        get() = dtoType.baseType.isEntity || !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)

    internal fun statePropName(prop: LsiDtoAbstractPropView, builder: Boolean): String? =
        when {
            !prop.isNullable -> null
            prop !is LsiDtoPropView -> null
            !dtoType.modifiers.contains(DtoModifier.INPUT) -> null
            else -> prop.inputModifier.takeIf {
                (it == DtoModifier.FIXED && builder) || it == DtoModifier.DYNAMIC
            }?.let {
                StringUtil.identifier("is", prop.name, "Loaded")
            }
        }

    private val isSerializerRequired: Boolean by lazy {
        dtoType.modifiers.contains(DtoModifier.INPUT) &&
                dtoType.dtoPropViews.any { it.inputModifier == DtoModifier.DYNAMIC }
    }

    private val isBuilderRequired: Boolean by lazy {
        dtoType.modifiers.contains(DtoModifier.INPUT) &&
                dtoType.dtoPropViews.any { prop ->
                    prop.inputModifier.let { it == DtoModifier.FIXED || it == DtoModifier.DYNAMIC }
                }
    }

    private val isHibernateValidatorEnhancementRequired: Boolean by lazy {
        Settings.jimmerDtoHibernateValidatorEnhancement &&
                dtoType.dtoPropViews.any { it.inputModifier == DtoModifier.DYNAMIC }
    }

    // 覆盖来源：DocMetadata.getString(LsiClass) 替换原 KSClassDeclaration 直传
    private val baseDocString: String?
        get() = docMetadata.getString(dtoType.baseType.lsiClass)

    // 覆盖来源：DocMetadata.getString(LsiField) 替换原 KSPropertyDeclaration 直传
    private fun baseDocString(prop: ImmutableProp): String? =
        // 迁移说明：文档元数据读取收敛为 LSI field，不再在 DTO 侧回落到 KS->LSI 转换
        docMetadata.getString(prop.lsiField)

    private fun String.withTrailingComma(): String =
        lineSequence()
            .toList()
            .let { lines ->
                if (lines.isEmpty()) {
                    this
                } else {
                    lines.toMutableList().apply {
                        this[lastIndex] = this[lastIndex] + ","
                    }.joinToString("\n")
                }
            }

    private fun kotlinMemberName(name: String): String =
        if (KOTLIN_PLAIN_IDENTIFIER_REGEX.matches(name) && name !in KOTLIN_KEYWORDS) {
            name
        } else {
            "`$name`"
        }

    private fun ImmutableProp.unwrapCode(): String =
        "${declaringType.lsiPropsClassName.canonicalName}.${StringUtil.snake(name, SnakeCase.UPPER)}.unwrap()"

    private fun ImmutableProp.unwrapExpression(): LsiExpression =
        LsiCallExpression(
            receiver = LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(declaringType.lsiPropsClassName),
                name = StringUtil.snake(name, SnakeCase.UPPER),
            ),
            name = "unwrap",
        )

    companion object {

        private fun isCopyableAnnotation(annotation: LsiAnnotation, dtoAnnotations: Collection<Anno>): Boolean {
            if (annotation.qualifiedName == null) {
                throw DtoException(
                    """
                    Unable to resolve qualifiedName for annotation: '$annotation'
                    Possible reasons:
                    1. The annotation's dependency is missing from compilation classpath
                    2. Required library is not included as a dependency
                    3. Dependency is declared with 'implementation' instead of 'api' configuration
                    
                    Solution: Add the corresponding dependency to your build configuration.
                    """.trimIndent()
                )
            }
            return DtoAnnotationSupport.isCopyableAnnotation(annotation, dtoAnnotations)
        }

        internal fun lsiAnnotationOf(
            anno: Anno,
            target: LsiAnnotationUseSiteTarget? = null
        ): LsiAnnotationSpec =
            anno.toLsiAnnotationSpec(
                typeRefToLsiTypeName = ::annotationTypeRefToLsiTypeName,
                annotationClassProvider = Context.lsiResolver::findClassByQualifiedName,
                useSiteTarget = target
            )

        private fun annotationTypeRefToLsiTypeName(typeRef: TypeRef) =
            if (typeRef.isNullable) {
                typeRef.typeName.toBoxedPrimitiveLsiClassNameOrNull() ?: lsiTypeName(typeRef).copyNullable(false)
            } else {
                lsiTypeName(typeRef).copyNullable(false)
            }

        fun lsiTypeName(typeRef: TypeRef?): LsiTypeName {
            val typeName = if (typeRef === null) {
                LsiStarTypeName
            } else {
                when (typeRef.typeName) {
                    TypeRef.TN_ARRAY -> LsiArrayTypeName(
                        componentType = typeRef.arguments.firstOrNull()?.typeRef?.let(::lsiTypeName) ?: LsiStarTypeName,
                        nullable = typeRef.isNullable,
                    )
                    else -> typeRef.typeName.toBuiltInLsiClassNameOrNull() ?: LsiClassName.bestGuess(typeRef.typeName)
                }
            }
            val args = typeRef
                ?.arguments
                ?.takeIf { it.isNotEmpty() && typeRef.typeName != TypeRef.TN_ARRAY }
                ?.map { arg ->
                    lsiTypeName(arg.typeRef).let {
                        when {
                            arg.isIn -> LsiWildcardTypeName(consumerTypes = listOf(it))
                            arg.isOut -> LsiWildcardTypeName(producerTypes = listOf(it))
                            else -> it
                        }
                    }
                }
            if (args == null) {
                return typeName.copyNullable(typeRef?.isNullable ?: false)
            }
            return LsiParameterizedTypeName(
                rawType = (typeName as LsiClassName).copy(nullable = false),
                typeArguments = args,
                nullable = typeRef.isNullable,
            )
        }

        private fun defaultValueCodeBlock(prop: LsiUserPropView): LsiCodeBlock? =
            defaultValueExpression(prop)?.let {
                LsiCodeBlock.of("%L", it)
            }

        private fun defaultValueExpression(prop: LsiUserPropView): LsiExpression? {
            val typeRef = prop.typeRef
            val defaultValueText = prop.defaultValueText
            return if (defaultValueText != null) {
                explicitDefaultValueExpression(typeRef, defaultValueText)
            } else if (typeRef.isNullable) {
                LsiNullExpression
            } else {
                when (typeRef.typeName) {
                    TypeRef.TN_BOOLEAN -> LsiLiteralExpression(false)
                    TypeRef.TN_CHAR -> LsiLiteralExpression('\u0000')

                    TypeRef.TN_BYTE -> LsiLiteralExpression(0.toByte())
                    TypeRef.TN_SHORT -> LsiLiteralExpression(0.toShort())
                    TypeRef.TN_INT -> LsiLiteralExpression(0)
                    TypeRef.TN_LONG -> LsiLiteralExpression(0L)
                    TypeRef.TN_FLOAT -> LsiLiteralExpression(0F)
                    TypeRef.TN_DOUBLE -> LsiLiteralExpression(0.0)

                    TypeRef.TN_STRING -> LsiLiteralExpression("")

                    TypeRef.TN_ARRAY -> LsiArrayExpression(
                        elementType = typeRef.arguments.firstOrNull()?.typeRef?.let(::lsiTypeName)
                            ?: LsiClassName.bestGuess("kotlin.Any", nullable = true),
                        elements = emptyList(),
                    )

                    TypeRef.TN_ITERABLE, TypeRef.TN_COLLECTION, TypeRef.TN_LIST ->
                        LsiListExpression(emptyList())

                    TypeRef.TN_MUTABLE_ITERABLE, TypeRef.TN_MUTABLE_COLLECTION, TypeRef.TN_MUTABLE_LIST ->
                        LsiNewExpression(LsiClassName.bestGuess("java.util.ArrayList"))

                    TypeRef.TN_SET -> LsiCallExpression(
                        receiver = LsiTypeExpression(COLLECTIONS_CLASS_NAME),
                        name = "emptySet",
                    )
                    TypeRef.TN_MUTABLE_SET -> LsiNewExpression(
                        LsiClassName.bestGuess("java.util.LinkedHashSet")
                    )

                    TypeRef.TN_MAP -> LsiCallExpression(
                        receiver = LsiTypeExpression(COLLECTIONS_CLASS_NAME),
                        name = "emptyMap",
                    )
                    TypeRef.TN_MUTABLE_MAP -> LsiNewExpression(
                        LsiClassName.bestGuess("java.util.LinkedHashMap")
                    )

                    else -> null
                }
            }
        }

        private fun explicitDefaultValueExpression(
            typeRef: TypeRef,
            defaultValueText: String,
        ): LsiExpression =
            when (typeRef.typeName) {
                TypeRef.TN_BOOLEAN -> LsiLiteralExpression(defaultValueText.toBooleanStrict())
                TypeRef.TN_BYTE -> LsiLiteralExpression(defaultValueText.toByte())
                TypeRef.TN_SHORT -> LsiLiteralExpression(defaultValueText.toShort())
                TypeRef.TN_INT -> LsiLiteralExpression(defaultValueText.toInt())
                TypeRef.TN_LONG -> LsiLiteralExpression(defaultValueText.removeSuffix("L").toLong())
                TypeRef.TN_FLOAT -> LsiLiteralExpression(defaultValueText.removeSuffix("F").toFloat())
                TypeRef.TN_DOUBLE -> LsiLiteralExpression(defaultValueText.toDouble())
                TypeRef.TN_STRING -> LsiLiteralExpression(parseDtoStringLiteral(defaultValueText))
                else -> error("Unsupported explicit default value for DTO type '${typeRef.typeName}'")
            }

        private fun parseDtoStringLiteral(text: String): String {
            require(text.length >= 2 && text.first() == '"' && text.last() == '"') {
                "Illegal dto string literal: $text"
            }
            val content = text.substring(1, text.lastIndex)
            val builder = StringBuilder(content.length)
            var index = 0
            while (index < content.length) {
                val ch = content[index]
                if (ch != '\\') {
                    builder.append(ch)
                    index += 1
                    continue
                }
                require(index + 1 < content.length) {
                    "Illegal dto string literal escape: $text"
                }
                when (val escaped = content[index + 1]) {
                    'b' -> builder.append('\b')
                    't' -> builder.append('\t')
                    'n' -> builder.append('\n')
                    'f' -> builder.append('\u000C')
                    'r' -> builder.append('\r')
                    '"', '\'', '\\' -> builder.append(escaped)
                    'u' -> {
                        var unicodeIndex = index + 1
                        while (unicodeIndex < content.length && content[unicodeIndex] == 'u') {
                            unicodeIndex += 1
                        }
                        require(unicodeIndex + 4 <= content.length) {
                            "Illegal dto unicode escape: $text"
                        }
                        builder.append(content.substring(unicodeIndex, unicodeIndex + 4).toInt(16).toChar())
                        index = unicodeIndex + 4
                        continue
                    }
                    else -> error("Unsupported dto string escape '\\$escaped' in $text")
                }
                index += 2
            }
            return builder.toString()
        }

        private fun String.simpleName() =
            lastIndexOf('.').let {
                if (it == -1) {
                    this
                } else {
                    substring(it + 1)
                }
            }

        private fun LsiTypeName.toList(isList: Boolean): LsiTypeName =
            if (isList) {
                LsiParameterizedTypeName(
                    rawType = KOTLIN_LIST_LSI_CLASS_NAME,
                    typeArguments = listOf(copyNullable(false))
                )
            } else {
                this
            }

        private fun LsiClassName.parameterizedBy(vararg typeArguments: LsiTypeName): LsiParameterizedTypeName =
            LsiParameterizedTypeName(
                rawType = copy(nullable = false),
                typeArguments = typeArguments.toList(),
                nullable = nullable,
            )

        private fun LsiTypeName.isArrayType(): Boolean =
            this is LsiArrayTypeName

        val DOC_EXPLICIT_FUN = "Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco"

        private val EXPRESSION_PACKAGE = "org.babyfish.jimmer.sql.kt.ast.expression"

        private val KOTLIN_PLAIN_IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val KOTLIN_KEYWORDS = setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "val",
            "var",
            "when",
            "while",
        )

        // Issue#1218，单 value 注解统一改成显式命名，避免后续渲染阶段出现匿名 value 形态
        private fun normalizeAnnotationSpec(spec: LsiAnnotationSpec): LsiAnnotationSpec {
            if (spec.positionalArguments.size != 1 || spec.members.containsKey("value")) {
                return spec
            }
            return spec.copy(
                positionalArguments = emptyList(),
                members = linkedMapOf("value" to spec.positionalArguments.single()).apply {
                    putAll(spec.members)
                }
            )
        }
    }
}
