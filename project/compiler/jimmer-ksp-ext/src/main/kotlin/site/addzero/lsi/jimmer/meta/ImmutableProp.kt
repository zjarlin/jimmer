package site.addzero.lsi.jimmer.meta

import site.addzero.context.Context
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.codegen.DRAFT
import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.parseValidationMessages
import site.addzero.lsi.codegen.upper
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassArgument
import site.addzero.lsi.anno.getListArgument
import site.addzero.lsi.clazz.toLsiClassName
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.codegen.ConverterMetadata
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.converterMetadataOf
import site.addzero.lsi.dto.LsiDtoBaseProp
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.*
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.isLsiImmutableListQualifiedName
import site.addzero.lsi.poet.isLsiMapQualifiedName
import site.addzero.lsi.poet.isSemanticallySameType
import site.addzero.lsi.poet.isBuiltInType
import site.addzero.lsi.poet.normalizedLsiCarrierQualifiedName
import site.addzero.lsi.poet.preferredLsiCollectionQualifiedName
import site.addzero.lsi.poet.toLsiPoet
import site.addzero.lsi.type.LsiType
import java.util.LinkedList
import java.util.regex.Pattern
import kotlin.reflect.KClass

class ImmutableProp(
    val ctx: Context,
    val declaringType: ImmutableType,
    val id: Int,
    /** LSI 字段（新体系，作为属性语义的唯一来源） */
    val lsiField: LsiField
): LsiDtoBaseProp {

    val lsiName: String by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.lsiName
        // 迁移说明：属性名来源由 KSPropertyDeclaration fallback 收敛为 LSI field 语义
        lsiField.name ?: throw MetaException(lsiField, "Cannot resolve property name from LSI field")
    }

    private val resolvedLsiType: LsiType by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.resolvedKsType
        // 迁移说明：属性类型解析入口由 `propDeclaration.type.fastResolve()` 切换为 `LsiField.type`
        lsiField.type ?: throw MetaException(lsiField, "Cannot resolve property type from LSI field")
    }

    val lsiType: LsiType?
        get() = resolvedLsiType

    val lsiComment: String?
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.lsiComment
        // 迁移说明：属性注释来源收敛为 LSI field.comment，不再回落 KS docString
        get() = lsiField.comment

    val lsiIsNullable: Boolean
        get() = lsiField.isNullable

    val lsiIsCollection: Boolean
        get() = lsiField.isCollectionType

    private val resolvedTypeQualifiedName: String? by lazy {
        lsiType?.qualifiedName
            ?: lsiType?.presentableText
                ?.substringBefore('<')
                ?.removeSuffix("?")
                ?.removeSuffix("!")
    }

    private val isResolvedEnumType: Boolean by lazy {
        lsiType?.lsiClass?.isEnum == true
    }

    private val isResolvedNullable: Boolean by lazy {
        lsiType?.isNullable ?: true
    }

    private val isResolvedValueClass: Boolean by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 value class 校验
        // 迁移说明：value class 判定由 `resolvedKsType.declaration.modifiers` 切换为 `LsiField.isValueClassType`
        lsiField.isValueClassType
    }

    private val isResolvedBooleanType: Boolean by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 isX 命名布尔校验
        // 迁移说明：布尔类型判定由 `KSType.toTypeName()` 对比切换为 LSI 类型名语义判定
        resolvedTypeQualifiedName?.normalizedLsiCarrierQualifiedName() == "kotlin.Boolean"
    }

    init {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化属性可变性校验
        // 迁移说明：属性只读校验由 KSPropertyDeclaration.isMutable 迁移为 LSI 字段语义（LsiField.isVar）
        if (lsiField.isVar) {
            throw MetaException(
                lsiField,
                "the property of immutable interface must be readonly"
            )
        }
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 type alias 校验
        // 迁移说明：type alias 判定由 `propDeclaration.type` 切换为 `LsiField.isTypeAlias`
        if (lsiField.isTypeAlias) {
            throw MetaException(
                lsiField,
                "the property of immutable interface cannot return type alias, please use real kotlin.type"
            )
        }
        if (lsiName.let { it.startsWith("is") && it.length > 2 && it[2].isUpperCase() } &&
            !isResolvedBooleanType) {
            throw MetaException(
                lsiField,
                "the property whose name starts with \"is\" return returns boolean type"
            )
        }
        if (isResolvedValueClass) {
            throw MetaException(
                lsiField,
                "the property whose type is kotlin value class is not supported now"
            )
        }
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 LogicalDeleted 校验
        // 迁移说明：注解存在性判定改为 LSI 注解读取
        if (lsiAnnotation(LOGICAL_DELETED) !== null) {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 LogicalDeleted 类型识别
            // 迁移说明：逻辑删除目标类型识别统一使用 LSI 类型语义（qualifiedName/presentableText/isEnum/isNullable）
            val typeName = if (isResolvedEnumType) {
                "<enum>"
            } else {
                resolvedTypeQualifiedName?.normalizedLsiCarrierQualifiedName() ?: "<unknown>"
            }
            when (typeName) {
                "kotlin.Boolean", "kotlin.Int", "<enum>" ->
                    if (isResolvedNullable) {
                        throw MetaException(
                            lsiField,
                            "the property decorated by \"@LogicalDeleted\" cannot be nullable " +
                                "if its type is boolean, int, or enum"
                        )
                    }
                "kotlin.Long", "java.util.UUID", "java.time.LocalDateTime", "java.time.Instant" -> {}
                else -> throw MetaException(
                    lsiField,
                    "the property decorated by \"@LogicalDeleted\" must be " +
                        "boolean, int, enum, UUID, LocalDateTime or Instant"
                )
            }
            if (lsiAnnotation(DEFAULT) !== null) {
                val isValid = when (typeName) {
                    "kotlin.Int", "<enum>" -> true
                    else -> false
                }
                if (!isValid) {
                    throw MetaException(
                        lsiField,
                        "the property cannot be decorated by both \"@Default\" and \"@LogicalDeleted\" " +
                            "unless its type is int or enum"
                    )
                }
            }
        }
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 FORBIDDEN_TYPE_NAMES 校验
        // 迁移说明：禁用类型判定由 fastResolve 路径迁移为 LSI 类型名语义（qualifiedName/presentableText）
        val expectedType = resolvedTypeQualifiedName?.preferredLsiCollectionQualifiedName()
        if (expectedType !== null) {
            throw MetaException(
                lsiField,
                "Cannot use \"${resolvedTypeQualifiedName}\", " +
                        "please use \"${expectedType}\""
            )
        }
    }

    override val name: String = lsiName.also {
        // 覆盖来源：project/jimmer-core/src/main/java/org/babyfish/jimmer/impl/util/Keywords.java ILLEGAL_PROP_NAMES
        // 迁移说明：属性关键字校验暂以内聚到 KSP-LSI 胶水层的本地等价语义，避免 `ImmutableProp` 继续依赖 jimmer-core Java 编译产物
        if (IMMUTABLE_PROP_ILLEGAL_NAMES.contains(it)) {
            throw MetaException(
                lsiField,
                "Illegal property \"$it\" which is jimmer keyword"
            )
        }
    }

    val slotName: String = "SLOT_${upper(name)}"

    override val isTransient: Boolean =
        lsiAnnotation(TRANSIENT) !== null

    override fun hasTransientResolver(): Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.hasTransientResolver
        // 迁移说明：注解参数改为 LSI 读取，移除 KSClassDeclaration -> LsiClass 的现场转换
        lsiAnnotation(TRANSIENT)?.let {
            val resolverClassName = it.getClassArgument(VALUE_ATTRIBUTE)
                ?.toLsiClassName()
            val resolverRef = it[REF_ATTRIBUTE] ?: ""
            val hasValue = resolverClassName != null && resolverClassName != KOTLIN_UNIT_LSI_CLASS_NAME
            val hasRef = resolverRef.isNotEmpty()
            if (hasValue && hasRef) {
                throw MetaException(
                    lsiField,
                    "it is decorated by @Transient, " +
                        "the `value` and `ref` are both specified, this is not allowed"
                )
            }
            hasValue || hasRef
        } ?: false

    override val isFormula: Boolean =
        lsiAnnotation(FORMULA) !== null

    val isImplementationFormula: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isKotlinFormula
        // 迁移说明：从“语言名”切换为“结构语义名”，只表达 `@Formula` 且属性自身带实现体，
        // 抽象性判定完全使用 LSI 字段语义（LsiField.isAbstract）
        lsiAnnotation(FORMULA) !== null && !lsiField.isAbstract

    @Deprecated("使用 isImplementationFormula，shared immutable metadata 不再暴露语言名语义")
    val isKotlinFormula: Boolean
        get() = isImplementationFormula

    override val isList: Boolean
        get() = if (
            isImplementationFormula ||
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isList 显式 @Scalar 判定
            // 迁移说明：显式 @Scalar 识别改为 LSI 注解递归，移除 KSAnnotation 递归依赖
            descriptorLsiAnnotations().any { isExplicitScalar(it, mutableSetOf()) }
        ) {
            false
        } else if (!lsiIsCollection) {
            false
        } else {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isList
            // 迁移说明：集合判定改为 LSI 类型语义（isCollectionType + qualifiedName），移除对 ctx.collectionType/listType/mapType 的 KSP 依赖；
            // 同时该分支错误锚点由 KS 属性声明切换为 LSI 字段锚点，便于 LSI 覆盖率检查
            val rawTypeName = lsiType?.qualifiedName ?: resolvedLsiType.qualifiedName
            if (isAssociation && rawTypeName?.isLsiMapQualifiedName() == true) {
                throw MetaException(lsiField, "it cannot be map")
            }
            if (rawTypeName == null || !rawTypeName.isLsiImmutableListQualifiedName()) {
                throw MetaException(lsiField, "collection property must be immutable list")
            }
            true
        }

    override val isReference
        get() = !isList && isAssociation

    fun isDsl(isTableEx: Boolean): Boolean =
        when {
            idViewBaseProp != null -> false
            isImplementationFormula || isTransient || (idViewBaseProp !== null && idViewBaseProp!!.isList)-> false
            isRemote && isReverse -> false
            !isList && isRemote -> !isTableEx
            else -> true
        }

    private val targetResolvedLsiType: LsiType by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 目标类型提取（原 targetDeclaration）
        // 迁移说明：目标类型提取改为 LSI 类型参数语义，移除 KSClassDeclaration/KSTypeAlias 直接依赖
        if (lsiIsCollection) {
            (lsiType?.typeParameters ?: resolvedLsiType.typeParameters).firstOrNull()
                ?: throw MetaException(
                    lsiField,
                    "can extract the generic argument from property type"
                )
        } else {
            lsiType ?: resolvedLsiType
        }
    }

    private val targetLsiClass: LsiClass by lazy {
        resolveTargetLsiClass()
    }

    val primaryAnnotationType: Class<out Annotation>?

    private val _isNullable: Boolean

    override val isNullable: Boolean
        get() = _isNullable

    init {
        val descriptor = ImmutablePropDescriptor.Builder(
                true,
                declaringType.toString(),
                declaringType.sqlAnnotationQualifiedName ?: IMMUTABLE,
                this.toString(),
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 PropDescriptor 目标类型名
                // 迁移说明：目标类型名由 LSI 目标类/类型文本提供，移除 KSClassDeclaration.fullName 依赖
                targetLsiClass.qualifiedName
                    ?: targetLsiClass.simpleName
                    ?: targetResolvedLsiType.qualifiedName
                    ?: targetResolvedLsiType.presentableText
                    ?: "",
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 PropDescriptor 目标注解类型推断
                // 迁移说明：由 targetDeclaration.annotation(...) 迁移为 LSI 语义判定（isJimmerEntity/...），并改用注解 FQ 名消除对 Java 注解类字面量的构建期依赖
                targetEntityAnnotationQualifiedName(),
                isList,
                lsiIsNullable
            ) {
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp PropDescriptor.newBuilder 的错误回调
                // 迁移说明：descriptor 回调异常锚点从 KSPropertyDeclaration 切换为 LSI 字段锚点；
                // 同时 `PropDescriptor` 本体改为本地 Kotlin 等价语义，消除对 `project:jimmer-core` Java helper 的编译期耦合
                MetaException(lsiField, it)
            }
            .apply {
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 PropDescriptor 注解采集与 mappedBy 判定
                // 迁移说明：由 KS 注解参数读取迁移到 LSI 注解 attributes 读取
                for (annotation in descriptorLsiAnnotations()) {
                    add(annotation.fullName)
                    if (ImmutablePropDescriptor.MAPPED_BY_PROVIDER_NAMES.contains(annotation.fullName) &&
                        !(annotation["mappedBy"] as? String).isNullOrEmpty()
                    ) {
                        hasMappedBy()
                    }
                }
            }
            .build()
        primaryAnnotationType = descriptor.type.annotationType
        _isNullable = descriptor.isNullable
    }

    init {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 目标类型合法性校验
        // 迁移说明：@MappedSuperclass 判定由 KS 声明注解读取迁移到 LSI 语义判定，错误锚点改为 LSI 字段
        if (targetLsiClass.isJimmerMappedSuperclass) {
            throw MetaException(
                lsiField,
                "its target type \"$targetLsiClass\" is illegal, it cannot be type decorated by @MappedSuperclass"
            )
        }
    }

    private val isAssociation: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isAssociation
        // 迁移说明：关联类型判定由 KS 注解判定迁移到 LSI 类语义判定（isInterface/isJimmerType/isJimmerEntity），错误锚点改为 LSI 字段
        targetLsiClass.isInterface && targetLsiClass.isJimmerType.also {
            if (declaringType.isAcrossMicroServices && targetLsiClass.isJimmerEntity && !isTransient) {
                throw MetaException(
                    lsiField,
                        "association property is not allowed here " +
                        "because the declaring type is decorated by \"@" +
                        MAPPED_SUPERCLASS +
                        "\" with the argument `acrossMicroServices`"
                )
            }
        }

    override val isEmbedded: Boolean
        get() = targetType?.isEmbeddable ?: false

    override fun isAssociation(entityLevel: Boolean): Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isAssociation(entityLevel)
        // 迁移说明：实体级关联判断改为 LSI 语义判定，移除 targetDeclaration.annotation(Entity) 直接读取
        isAssociation && (!entityLevel || targetLsiClass.isJimmerEntity)

    val targetLsiClassName: LsiClassName by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.targetLsiClassName
        // 迁移说明：统一复用 targetLsiClass（LSI 优先 + KSP->LSI 单向兜底）
        targetLsiClass.toLsiClassName()
    }

    fun toTargetLsiTypeName(
        draft: Boolean = false,
        overrideNullable: Boolean? = null
    ): LsiTypeName =
        targetElementLsiTypeName(draft).let {
            if (overrideNullable != null) {
                it.copyNullable(overrideNullable)
            } else {
                it
            }
        }

    fun toLsiTypeName(draft: Boolean = false, overrideNullable: Boolean? = null): LsiTypeName =
        targetElementLsiTypeName(draft).let {
            when {
                isList && draft ->
                    LsiParameterizedTypeName(
                        rawType = LsiClassName.bestGuess("kotlin.collections.MutableList"),
                        typeArguments = listOf(it)
                    )
                isList ->
                    LsiParameterizedTypeName(
                        rawType = LsiClassName.bestGuess("kotlin.collections.List"),
                        typeArguments = listOf(it)
                    )
                else -> it
            }
        }.let {
            if (overrideNullable != null) {
                it.copyNullable(overrideNullable)
            } else if (isNullable) {
                it.copyNullable(true)
            } else {
                it
            }
        }

    val clientLsiTypeName: LsiTypeName
        get() = converterMetadata?.targetTypeName?.copyNullable(isNullable) ?: toLsiTypeName()

    val targetType: ImmutableType? by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.targetType
        // 迁移说明：目标类型元模型构建入口切到 LSI（ctx.typeOf(LsiClass)）
        targetLsiClass
            .takeIf { isAssociation }
            ?.let { ctx.typeOf(it) }
    }

    val isReferenceList = isAssociation && isList

    val isScalarList = isList && !isAssociation

    override val isId: Boolean =
        primaryAnnotationType == annotationClassOf(ID)

    val isVersion: Boolean =
        primaryAnnotationType == annotationClassOf(VERSION)

    override val isLogicalDeleted: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isLogicalDeleted
        // 迁移说明：逻辑删除判定改为 LSI 注解判定
        lsiAnnotation(LOGICAL_DELETED) !== null

    override val isExcludedFromAllScalars: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isExcludedFromAllScalars
        // 迁移说明：ExcludeFromAllScalars 判定改为 LSI 注解判定
        lsiAnnotation(EXCLUDE_FROM_ALL_SCALARS) !== null

    override val isKey: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isKey
        // 迁移说明：key 判定改为 LSI 注解读取
        lsiAnnotation(KEY) !== null

    val isPrimitive: Boolean =
        !isList &&
            !isNullable &&
            toLsiTypeName(overrideNullable = false).isBuiltInType(nullable = false)

    val isRemote: Boolean by lazy {
        targetType?.takeIf {
            val remote = it.microServiceName != declaringType.microServiceName
            if (remote && lsiAnnotation(JOIN_SQL) !== null) {
                throw MetaException(
                    lsiField,
                    "remote association(micro-service names of declaring type and target type are different) " +
                        "cannot be decorated by \"@" +
                        JOIN_SQL +
                        "\""
                )
            }
            remote
        } !== null
    }

    val isReverse: Boolean =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isReverse
        // 迁移说明：mappedBy 判定由 KS 注解读取迁移为 LSI 注解参数读取
        (
            lsiAnnotation(ONE_TO_ONE)
                ?: lsiAnnotation(ONE_TO_MANY)
                ?: lsiAnnotation(MANY_TO_MANY)
        )?.get<String>(MAPPED_BY_ATTRIBUTE).isNullOrEmpty().not()

    override val isRecursive: Boolean by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.isRecursive
        // 迁移说明：由 KS asStarProjectedType 可赋值判定迁移为 LSI 继承图递归判定
        declaringType.isEntity && manyToManyViewBaseProp === null && !isRemote &&
            isSameOrSubtype(targetLsiClass, declaringType.lsiClass)
    }

    val valueFieldName: String?
        get() = if (idViewBaseProp === null && manyToManyViewBaseProp === null && !isImplementationFormula) {
            "__${name}Value"
        } else {
            null
        }

    val loadedFieldName: String? =
        if (idViewBaseProp === null && manyToManyViewBaseProp == null && !isImplementationFormula && (isNullable || isPrimitive)) {
            "__${name}Loaded"
        } else {
            null
        }

    val converterMetadata: ConverterMetadata? by lazy {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.converterMetadata
        // 迁移说明：递归注解改为 LSI 注解读取，避免 KS 注解参数在业务层传播；异常锚点改为 LSI 字段
        var jsonConverter = recursiveLsiAnnotationOf(JSON_CONVERTER)
        val jsonFormat = recursiveLsiAnnotationOf(ctx.jacksonTypes.jsonFormat.canonicalName)

        var autoApply = false
        if (jsonConverter === null) {
            resolveIdViewBaseProp()
            if (idViewBaseProp !== null) {
                autoApply = true
                jsonConverter =
                    idViewBaseProp?.declaringType?.idProp?.recursiveLsiAnnotationOf(JSON_CONVERTER)
            }
        }

        if (jsonConverter !== null && jsonFormat !== null) {
            throw MetaException(
                lsiField,
                "it cannot be decorated both \"@$JSON_CONVERTER\" " +
                        "and \"${ctx.jacksonTypes.jsonFormat.canonicalName}\""
            )
        }
        if (jsonConverter === null) {
            null
        } else {
            val declaration = jsonConverter.getClassArgument(VALUE_ATTRIBUTE)!!
            converterMetadataOf(declaration).let {
                if (autoApply && isList) {
                    it.toListMetadata()
                } else {
                    it
                }
            }.also {
                if (!it.sourceTypeName.isSemanticallySameType(toLsiTypeName(overrideNullable = false))) {
                    throw MetaException(
                        lsiField,
                        "the source type of converter " +
                                "\"${declaration.qualifiedName ?: declaration.simpleName}\" is \"" +
                                "${it.sourceTypeName}\" does not match the return type of current property"
                    )
                }
            }
        }
    }

    internal fun methodAllLsiAnnotations(): List<LsiAnnotation> =
        allLsiAnnotations()

    private fun targetElementLsiTypeName(draft: Boolean): LsiTypeName =
        if (isList) {
            (resolvedLsiType.toLsiPoet() as LsiParameterizedTypeName).typeArguments[0]
        } else {
            resolvedLsiType.toLsiPoet()
        }.let {
            if (draft && isAssociation && it is LsiClassName) {
                it.copy(simpleNames = it.simpleNames.dropLast(1) + "${it.simpleName}$DRAFT")
            } else {
                it
            }
        }

    private fun allLsiAnnotations(): List<LsiAnnotation> =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../utils.KSAnnotated.annotations(KSPropertyDeclaration)
        // 迁移说明：`LsiField.annotations` 已统一承接 property/getter/returnType 注解，删除重复的 `allAnnotations` 层
        lsiField.annotations

    fun lsiAnnotation(annotationType: KClass<out Annotation>): LsiAnnotation? =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 注解参数读取（Transient/Formula）
        // 迁移说明：统一从 LSI 全量注解读取（含 getter/returnType）；缺失时回落 KS->LSI 单向包装
        lsiAnnotation(annotationType.qualifiedName!!)

    private fun recursiveLsiAnnotationOf(annotationTypeName: String): LsiAnnotation? =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.converterMetadata 递归注解解析
        // 迁移说明：递归注解解析改为纯 LSI 注解树遍历（allLsiAnnotations），移除对 KS 递归工具的依赖
        findRecursiveLsiAnnotation(annotationTypeName)

    private fun findRecursiveLsiAnnotation(annotationTypeName: String): LsiAnnotation? {
        val stack = LinkedList<String>()
        var foundPath: List<String> = emptyList()
        var foundAnnotation: LsiAnnotation? = null
        fun visit(annotation: LsiAnnotation) {
            val qualifiedName = annotation.fullName
            if (qualifiedName.isEmpty()) {
                return
            }
            if (qualifiedName == annotationTypeName) {
                if (foundAnnotation != null && foundAnnotation !== annotation) {
                    val reason =
                        "Conflict annotation \"@$annotationTypeName\" one " +
                            declared(foundPath) +
                            " and the other one " +
                            declared(stack)
                    throw MetaException(lsiField, reason)
                }
                foundAnnotation = annotation
                foundPath = ArrayList(stack)
                return
            }
            if (stack.contains(qualifiedName)) {
                return
            }
            stack.push(qualifiedName)
            for (subAnnotation in annotation.annotations) {
                visit(subAnnotation)
            }
            stack.pop()
        }
        for (annotation in allLsiAnnotations()) {
            visit(annotation)
        }
        return foundAnnotation
    }

    private fun declared(path: List<String>): String =
        if (path.isEmpty()) {
            "is declared directly"
        } else {
            "is declared as nest annotation of $path"
        }

    private fun descriptorLsiAnnotations(): List<LsiAnnotation> =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp 初始化 PropDescriptor 注解采集
        // 迁移说明：注解采集统一走 LSI 全量注解（含 getter/returnType），仅缺失时回落 KS->LSI 单向包装
        allLsiAnnotations()

    private fun targetEntityAnnotationQualifiedName(): String? {
        val targetClass = targetLsiClass
        return when {
            targetClass.isJimmerEntity -> ENTITY
            targetClass.isJimmerMappedSuperclass -> MAPPED_SUPERCLASS
            targetClass.isJimmerEmbeddable -> EMBEDDABLE
            targetClass.isJimmerImmutable -> IMMUTABLE
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun annotationClassOf(annotationQualifiedName: String): Class<out Annotation> =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp PropDescriptor 注解类型装配
        // 迁移说明：按注解 FQ 名延迟加载 Java 注解类型，减少该元模型对 jimmer-core Java 注解类字面量的硬依赖
        Class.forName(annotationQualifiedName) as Class<out Annotation>

    private fun resolveTargetLsiClass(): LsiClass {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.targetLsiClassName/targetType/isAssociation
        // 迁移说明：目标类型解析改为纯 LSI（type.lsiClass + resolver），移除 KS -> LSI 兜底转换依赖；错误锚点改为 LSI 字段
        val candidate = targetResolvedLsiType.lsiClass
            ?: throw MetaException(
                lsiField,
                "Cannot resolve target class from property type \"${targetResolvedLsiType.presentableText ?: targetResolvedLsiType.qualifiedName ?: targetResolvedLsiType.simpleName}\""
            )
        val qualifiedName = candidate.qualifiedName ?: targetResolvedLsiType.qualifiedName
        return qualifiedName?.let { ctx.lsiResolver.findClassByQualifiedName(it) } ?: candidate
    }

    private fun isSameOrSubtype(type: LsiClass, superType: LsiClass): Boolean {
        val visited = mutableSetOf<String>()
        fun visit(current: LsiClass): Boolean {
            val key = current.qualifiedName ?: current.simpleName ?: return false
            if (!visited.add(key)) {
                return false
            }
            if (isSameClass(current, superType)) {
                return true
            }
            return current.superClasses.any { visit(it) } ||
                current.interfaces.any { visit(it) }
        }
        return visit(type)
    }

    private fun isSameClass(left: LsiClass, right: LsiClass): Boolean {
        val leftQualifiedName = left.qualifiedName
        val rightQualifiedName = right.qualifiedName
        return if (leftQualifiedName != null && rightQualifiedName != null) {
            leftQualifiedName == rightQualifiedName
        } else {
            left.simpleName != null && left.simpleName == right.simpleName
        }
    }

    fun lsiAnnotation(annotationQualifiedName: String): LsiAnnotation? =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.idPropNameMap 关联注解判定
        // 迁移说明：开放按 FQ 名查询的 LSI 注解入口，便于上层去除对 Java 注解类字面量的硬依赖
        lsiAnnotationImpl(annotationQualifiedName)

    private fun lsiAnnotationImpl(annotationQualifiedName: String): LsiAnnotation? =
        allLsiAnnotations()
            .let { annotations ->
                val matched = annotations.filter { it.fullName == annotationQualifiedName }
                if (matched.size > 1) {
                    throw MetaException(
                        lsiField,
                        "it is decorated by multiple annotations of type '@$annotationQualifiedName' from different annotation targets"
                    )
                }
                matched.firstOrNull()
            }

    val validationMessages: Map<LsiClassName, String> =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.validationMessages
        // 迁移说明：校验注解消息提取改为 LSI 全量注解读取（含 getter/returnType），属性级锚点使用 LSI field
        parseValidationMessages(
            allLsiAnnotations(),
            lsiField
        )

    override fun toString(): String =
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.toString
        // 迁移说明：属性名输出由 KSPropertyDeclaration.name 迁移为 LSI 派生属性名（name）
        "${declaringType}.${name}"

    private var _idViewBaseProp: ImmutableProp? = null

    private var _manyToManyViewBaseProp: ImmutableProp? = null

    private var _manyToManyViewBaseDeeperProp: ImmutableProp? = null

    private lateinit var _dependencies: Set<FormulaDependency>

    val idViewProp: ImmutableProp? by lazy {
        declaringType.properties.values.firstOrNull {
            it.idViewBaseProp == this
        }
    }

    val baseProp: ImmutableProp?
        get() = _idViewBaseProp ?: _manyToManyViewBaseProp

    override val idViewBaseProp: ImmutableProp?
        get() = _idViewBaseProp

    override val manyToManyViewBaseProp: ImmutableProp?
        get() = _manyToManyViewBaseProp

    val manyToManyViewBaseDeeperProp: ImmutableProp?
        get() = _manyToManyViewBaseDeeperProp

    val dependencies: Set<FormulaDependency>
        get() = _dependencies

    val isBaseProp: Boolean by lazy {
        for (otherProp in declaringType.properties.values) {
            for (dependency in otherProp.dependencies) {
                if (dependency.props.contains(this)) {
                    return@lazy true
                }
            }
            if (otherProp.idViewBaseProp == this) {
                return@lazy true
            }
            if (otherProp.manyToManyViewBaseDeeperProp == this) {
                return@lazy true
            }
        }
        false
    }

    internal fun resolve(ctx: Context, step: Int): Boolean =
        when (step) {
            0 -> {
                resolveTargetType(ctx)
                true
            }

            1 -> {
                resolveIdViewBaseProp()
                true
            }

            2 -> {
                resolveFormulaDependencies()
                true
            }

            3 -> {
                resolveManyToManyBaseViewProp()
                true
            }
            else -> false
        }

    private fun resolveTargetType(ctx: Context) {
        if (isAssociation) {
            targetType
        }
    }

    private fun resolveIdViewBaseProp() {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.resolveIdViewBaseProp
        // 迁移说明：IdView 参数读取改为 LSI 注解参数读取，错误锚点改为 LSI 字段
        val idView = lsiAnnotation(ID_VIEW) ?: return
        var base: String = idView[VALUE_ATTRIBUTE] ?: ""
        if (base.isEmpty()) {
            // 覆盖来源：project/jimmer-core/src/main/java/org/babyfish/jimmer/meta/impl/Utils.java defaultViewBasePropName
            // 迁移说明：默认 IdView 基属性推断下沉为 KSP-LSI 胶水层本地等价函数，避免 `ImmutableProp` 依赖 jimmer-core Java helper
            base = immutablePropDefaultViewBasePropName(isList, name) ?: throw MetaException(
                    lsiField,
                    "it is decorated by \"@" +
                        ID_VIEW +
                        "\", the argument of that annotation is not specified by " +
                        "the base property name cannot be determined automatically, " +
                        "please specify the argument of that annotation"
                )
        }
        if (base == name) {
            throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    ID_VIEW +
                    "\", the argument of that annotation cannot be equal to the current property name\"" +
                    name +
                    "\""
            )
        }
        val baseProp = declaringType.properties[base]
            ?: throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    ID_VIEW +
                    "\" but there is no base property \"" +
                    base +
                    "\" in the declaring type"
            )
        if (!baseProp.isAssociation(true) || baseProp.isTransient) {
            throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    ID_VIEW +
                    "\" but the base property \"" +
                    baseProp +
                    "\" is not persistence association"
            )
        }
        if (isList != baseProp.isList) {
            throw MetaException(
                    lsiField,
                    "it " +
                    (if (isList) "is" else "is not") +
                    " list and decorated by \"@" +
                    ID_VIEW +
                    "\" but the base property \"" +
                    baseProp +
                    "\" " +
                    (if (baseProp.isList) "is" else "is not") +
                    " list"
            )
        }
        if (isNullable != baseProp.isNullable) {
            throw MetaException(
                    lsiField,
                    "it " +
                    (if (isNullable) "is" else "is not") +
                    " nullable and decorated by \"@" +
                    ID_VIEW +
                    "\" but the base property \"" +
                    baseProp +
                    "\" " +
                    (if (baseProp.isNullable) "is" else "is not") +
                    " nullable"
            )
        }
        val targetIdTypeName = baseProp.targetType!!.idProp!!.toTargetLsiTypeName(
            overrideNullable = baseProp.isNullable
        )
        if (toTargetLsiTypeName() != targetIdTypeName) {
            throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    ID_VIEW +
                    "\", the base property \"" +
                    baseProp +
                    "\" returns entity type whose id is \"" +
                    targetIdTypeName +
                    "\", but the current property does not return that type"
            )
        }
        _idViewBaseProp = baseProp
    }

    private fun resolveManyToManyBaseViewProp() {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.resolveManyToManyBaseViewProp
        // 迁移说明：ManyToManyView 参数与关联注解判定改为 LSI 语义读取，错误锚点改为 LSI 字段
        val manyToManyView = lsiAnnotation(MANY_TO_MANY_VIEW) ?: return
        val propName = manyToManyView.get<String>(PROP_ATTRIBUTE)!!
        val prop = declaringType.properties[propName]
            ?: throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    MANY_TO_MANY_VIEW +
                    "\" with `prop` is \"" +
                    propName +
                    "\", but there is no such property in the declaring type"
            )
        if (prop.lsiAnnotation(ONE_TO_MANY) == null) {
            throw MetaException(
                lsiField,
                "it is decorated by \"@" +
                    MANY_TO_MANY_VIEW +
                    "\" whose `prop` is \"" +
                    prop +
                    "\", but that property is not an one-to-many association"
            )
        }
        val middleType = prop.targetType!!
        val deeperPropName = manyToManyView[DEEPER_PROP_ATTRIBUTE] ?: ""
        val deeperProp = if (deeperPropName.isEmpty()) {
            var autoFoundProp: ImmutableProp? = null
            for (middleProp in middleType.properties.values) {
                if (middleProp.targetType === targetType &&
                    middleProp.lsiAnnotation(MANY_TO_ONE) !== null) {
                    if (autoFoundProp !== null) {
                        throw MetaException(
                            lsiField,
                            "it is decorated by \"@" +
                                MANY_TO_MANY_VIEW +
                                "\" whose `deeperProp` is not specified, " +
                                "however, two many-to-one properties pointing to target type are found: \"" +
                                autoFoundProp +
                                "\" and \"" +
                                prop +
                                "\", please specify its `deeperProp` explicitly"
                        );
                    }
                    autoFoundProp = prop;
                }
            }
            autoFoundProp
                ?: throw MetaException(
                    lsiField,
                    "it is decorated by \"@" +
                        MANY_TO_MANY_VIEW +
                        "\" whose `deeperProp` is not specified, " +
                        "however, there is no many-to-one property pointing to " +
                        "target type in the middle entity type \"" +
                        middleType +
                        "\""
                )
        } else {
            middleType.properties[deeperPropName]
                ?.also {
                    if (it.targetType !== targetType || it.lsiAnnotation(MANY_TO_ONE) === null) {
                        throw MetaException(
                            lsiField,
                            "it is decorated by \"@" +
                                MANY_TO_MANY_VIEW +
                                "\" whose `deeperProp` is `" +
                                deeperPropName +
                                "`, " +
                                "however, there is no many-to-one property \"" +
                                deeperPropName +
                                "\" in the middle entity type \"" +
                                middleType +
                                "\""
                        )
                    }
                }
                ?: throw MetaException(
                    lsiField,
                    "it is decorated by \"@" +
                        MANY_TO_MANY_VIEW +
                        "\" whose `deeperProp` is `" +
                        deeperPropName +
                        "`, " +
                        "however, there is no many-to-one property \"" +
                        deeperPropName +
                        "\" in the middle entity type \"" +
                        middleType +
                        "\""
                )
        }
        _manyToManyViewBaseProp = prop
        _manyToManyViewBaseDeeperProp = deeperProp
    }

    private fun resolveFormulaDependencies() {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.resolveFormulaDependencies
        // 迁移说明：由 KSAnnotation 参数读取迁移为 LSI 注解参数读取
        val dependencies = lsiAnnotation(FORMULA)?.getListArgument<String>(DEPENDENCIES_ATTRIBUTE) ?: emptyList()
        if (dependencies.isEmpty()) {
            _dependencies = emptySet()
        } else {
            this._dependencies = dependencies.map { createFormulaDependency(this, it) }.toSet()
        }
    }

    companion object {
        private const val VALUE_ATTRIBUTE = "value"

        private const val REF_ATTRIBUTE = "ref"

        private const val MAPPED_BY_ATTRIBUTE = "mappedBy"

        private const val PROP_ATTRIBUTE = "prop"

        private const val DEEPER_PROP_ATTRIBUTE = "deeperProp"

        private const val DEPENDENCIES_ATTRIBUTE = "dependencies"

        fun isExplicitScalar(anno: LsiAnnotation, handledQualifiedNames: MutableSet<String>): Boolean {
            if (!handledQualifiedNames.add(anno.fullName)) {
                return false
            }
            if (anno.fullName == SCALAR) {
                return true
            }
            for (deeperAnno in anno.annotations) {
                if (isExplicitScalar(deeperAnno, handledQualifiedNames)) {
                    return true
                }
            }
            return false
        }

        private val DOT_PATTERN = Pattern.compile("\\.")

        private fun createFormulaDependency(formulaProp: ImmutableProp, dependency: String): FormulaDependency {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp.createFormulaDependency
            // 迁移说明：Formula 依赖解析异常锚点由 KSPropertyDeclaration 切换为 LSI 字段锚点
            val propNames = DOT_PATTERN.split(dependency)
            val len = propNames.size
            var declaringType = formulaProp.declaringType
            val props = mutableListOf<ImmutableProp>()
            for (i in 0 until len) {
                val propName = propNames[i]
                val prop = declaringType.properties[propName]
                    ?: throw MetaException(
                        formulaProp.lsiField,
                        "The dependency \"" +
                            dependency +
                            "\" cannot be resolved because there is no property \"" +
                            propName +
                            "\" in \"" +
                            dependency +
                            "\""
                    )
                props += prop
                if (i + 1 < len) {
                    val targetType = prop.targetType
                    if (targetType === null) {
                        throw MetaException(
                            formulaProp.lsiField,
                            "The dependency \"" +
                                dependency +
                                "\" cannot be resolved because \"" +
                                prop +
                                "\" is not last property but it is neither association nor embedded property"
                        )
                    }
                    declaringType = targetType
                }
            }
            return FormulaDependency(props)
        }
    }
}
