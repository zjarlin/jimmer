package site.addzero.lsi.jimmer.meta

import site.addzero.context.Context
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.codegen.parseValidationMessages
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getListArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.dto.LsiDtoBaseType
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.*
import site.addzero.lsi.type.isSameType

class ImmutableType(
    private val ctx: Context,
    val lsiClass: LsiClass
) : LsiDtoBaseType {

    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.classDeclaration
    // 迁移说明：类型元信息读取不再持有 `KSClassDeclaration` 缓存属性，统一改为 `LsiClass` 命名/注解语义
    private val lsiQualifiedNameRequired: String by lazy {
        lsiClass.qualifiedName
            ?: throw MetaException(lsiClass, "Cannot resolve qualifiedName from LSI class")
    }

    private val lsiSimpleNameRequired: String by lazy {
        lsiClass.simpleName
            ?: lsiQualifiedNameRequired.substringAfterLast('.')
    }

    private val lsiPackageNameRequired: String by lazy {
        lsiQualifiedNameRequired.substringBeforeLast('.', "")
    }

    init {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 构造期主注解冲突校验
        // 迁移说明：冲突/缺失注解报错锚点由 KSClassDeclaration 切换为 LSI class 锚点
        val conflicting = listOf(
            ENTITY,
            MAPPED_SUPERCLASS,
            EMBEDDABLE,
            IMMUTABLE,
        ).filter { fqn -> lsiClass.annotations.any { it.qualifiedName == fqn } }
        if (conflicting.size > 1) {
            throw MetaException(lsiClass, "Conflict annotations: $conflicting")
        }
        if (conflicting.isEmpty()) {
            throw MetaException(lsiClass, "No Jimmer immutable annotation found")
        }
    }

    override val isEntity: Boolean = lsiClass.isJimmerEntity

    val isMappedSuperclass: Boolean = lsiClass.isJimmerMappedSuperclass

    val isEmbeddable: Boolean = lsiClass.isJimmerEmbeddable

    val isImmutable: Boolean = lsiClass.isJimmerImmutable

    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.simpleName
    // 迁移说明：类型命名来源由 KSClassDeclaration fallback 收敛为 LSI 类命名语义
    val simpleName: String = lsiSimpleNameRequired

    val lsiClassName: LsiClassName = lsiClass.lsiClassName

    val lsiPropsClassName: LsiClassName = lsiClass.lsiPropsClassName

    val lsiDraftClassName: LsiClassName = lsiClass.lsiDraftClassName

    val lsiFetcherClassName: LsiClassName = lsiClass.lsiFetcherClassName

    val lsiProducerClassName: LsiClassName = lsiClass.lsiProducerClassName

    val lsiFetcherDslClassName: LsiClassName = lsiClass.lsiFetcherDslClassName

    val lsiPropExpressionClassName: LsiClassName = lsiClass.lsiPropExpressionClassName

    val lsiTableClassName: LsiClassName = lsiClass.lsiTableClassName

    val lsiTableExClassName: LsiClassName = lsiClass.lsiTableExClassName

    val lsiRemoteTableClassName: LsiClassName = lsiClass.lsiRemoteTableClassName

    fun lsiDraftClassName(vararg nestedNames: String): LsiClassName = lsiClass.lsiDraftClassName(*nestedNames)

    val sqlAnnotationQualifiedName: String? = run {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.sqlAnnotationType
        // 迁移说明：SQL 主注解判定改为 LSI 语义 + 注解 FQ 常量，减少该元模型对 jimmer-core Java 注解类字面量的硬依赖
        when {
            isEntity -> ENTITY
            isMappedSuperclass -> MAPPED_SUPERCLASS
            isEmbeddable -> EMBEDDABLE
            else -> null
        }
    }

    override val name: String
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.name
        // 迁移说明：类型名称读取由 KS fallback 收敛为 LSI 命名语义
        get() = lsiSimpleNameRequired

    override val packageName: String
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.packageName
        // 迁移说明：包名读取由 KS fallback 收敛为 LSI qualifiedName 派生
        get() = lsiPackageNameRequired

    override val qualifiedName: String
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.qualifiedName
        // 迁移说明：全限定名读取由 KS fallback 收敛为 LSI qualifiedName
        get() = lsiQualifiedNameRequired

    val isAcrossMicroServices: Boolean = lsiClass.isJimmerAcrossMicroServices

    val microServiceName: String = lsiClass.jimmerMicroServiceName
        .also {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType microServiceName 与 acrossMicroServices 互斥校验
            // 迁移说明：类型级错误锚点由 KSClassDeclaration 切换为 LSI class
            if (it.isNotEmpty() && isAcrossMicroServices) {
                throw MetaException(
                    lsiClass,
                    "the `acrossMicroServices` of its annotation \"@" +
                            MAPPED_SUPERCLASS +
                            "\" is true so that `microServiceName` cannot be specified"
                )
            }
        }

    val superTypes: List<ImmutableType> =
        lsiClass.superClasses
            .filter { it.isInterface }
            .filter { it.isJimmerType }
            .map { ctx.typeOf(it) }
            .also {
                if (it.isEmpty()) {
                    return@also
                }
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 超类型合法性校验
                // 迁移说明：超类型结构校验异常锚点由 KSClassDeclaration 切换为 LSI class
                when {
                    isImmutable -> if (it.size > 1) {
                        throw MetaException(
                            lsiClass,
                            "simple immutable type does not support multiple inheritance"
                        )
                    }

                    isEmbeddable -> throw MetaException(
                        lsiClass,
                        "embeddable type does not support inheritance"
                    )

                    isEntity -> for (superType in it) {
                        if (!superType.isEntity && !superType.isMappedSuperclass) {
                            throw MetaException(
                                lsiClass,
                                "the super type \"$superType\" is neither entity nor mapped super class"
                            )
                        }
                    }

                    isMappedSuperclass -> for (superType in it) {
                        if (!superType.isMappedSuperclass) {
                            throw MetaException(
                                lsiClass,
                                "the super type \"$superType\" is not mapped super class"
                            )
                        }
                    }
                }
                for (superType in it) {
                    if (!superType.isAcrossMicroServices && superType.microServiceName != microServiceName) {
                        throw MetaException(
                            lsiClass,
                            "its micro service name is \"" +
                                    microServiceName +
                                    "\" but the micro service name of its super type \"" +
                                    superType.qualifiedName +
                                    "\" is \"" +
                                    superType.microServiceName +
                                    "\""
                        )
                    }
                }
            }

    val primarySuperType: ImmutableType? =
        superTypes
            .filter { !it.isMappedSuperclass }
            .also {
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 主超类型(primary super type)约束校验
                // 迁移说明：多主超类型错误锚点由 KSClassDeclaration 切换为 LSI class
                if (it.size > 1) {
                    throw MetaException(
                        lsiClass,
                        "two many primary(not mapped super class) super types: $it"
                    )
                }
            }
            .firstOrNull()

    val declaredProperties: Map<String, ImmutableProp>

    val redefinedProps: Map<String, ImmutableProp>

    private val declaredLsiFields: List<LsiField> by lazy {
        val ownerQualifiedName = lsiClass.qualifiedName
        lsiClass.fields.filter { field ->
            !field.isStatic &&
                    !field.isConstant &&
                    (
                            ownerQualifiedName == null ||
                                    field.declaringClass?.qualifiedName == ownerQualifiedName
                            )
        }
    }

    private val declaredLsiMethods by lazy {
        val ownerQualifiedName = lsiClass.qualifiedName
        lsiClass.methods.filter { method ->
            ownerQualifiedName == null ||
                    method.declaringClass?.qualifiedName == ownerQualifiedName
        }
    }

    init {
        val superPropMap = superTypes
            .flatMap { it.properties.values }
            .groupBy { it.name }
            .toList()
            .associateBy({ it.first }) {
                if (it.second.size > 1) {
                    val prop1 = it.second[0]
                    val prop2 = it.second[1]
                    val sameTypeByLsi = prop1.lsiType.isSameType(prop2.lsiType)
                    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 超类型同名属性冲突类型判等
                    // 迁移说明：同名属性类型判等去除 KSP fastResolve 回退，统一使用 LSI 类型语义 isSameType
                    if (!sameTypeByLsi) {
                        throw MetaException(
                            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 超类型同名属性冲突校验
                            // 迁移说明：同名不同类型冲突错误锚点由 KSClassDeclaration 切换为 LSI class
                            lsiClass,
                            "There are two super properties with the same name: \"" +
                                    prop1 +
                                    "\" and \"" +
                                    prop2 +
                                    "\", but their return type are different"
                        )
                    }
                }
                it.second.first()
            }

        for (lsiField in declaredLsiFields) {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 属性覆盖校验
            // 迁移说明：属性名读取与错误锚点改为 LSI 字段语义，避免该校验阶段依赖 KSPropertyDeclaration
            val propName = lsiField.name
                ?: throw MetaException(lsiField, "Cannot resolve property name")
            val superProp = superPropMap[propName]
            if (superProp != null) {
                throw MetaException(
                    lsiField,
                    "it overrides '$superProp', this is not allowed"
                )
            }
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 公式注解读取
            // 迁移说明：公式注解与参数读取改为 LSI 注解读取（保留 KS->LSI 单向包装兜底）
            val formula = propLsiAnnotation(lsiField, FORMULA)
            if (isEmbeddable && formula !== null && (formula[FORMULA_SQL] ?: "").isNotEmpty()) {
                throw MetaException(
                    lsiField,
                    "The sql based formula property cannot be declared in embeddable type"
                )
            }
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType Formula 抽象/非抽象分支
                // 迁移说明：抽象性判定改为 LSI 字段能力（LsiField.isAbstract）
            if (lsiField.isAbstract) {
                if (formula !== null) {
                    val sql = formula[FORMULA_SQL] ?: ""
                    if (sql.isEmpty()) {
                        throw MetaException(
                            lsiField,
                            "it is abstract and decorated by @" +
                                    FORMULA +
                                    ", abstract modifier means simple calculation property based on " +
                                    "SQL expression so that the `sql` of that annotation must be specified"
                        )
                    }
                    val dependencies = formula.getListArgument<String>(FORMULA_DEPENDENCIES) ?: emptyList()
                    if (dependencies.isNotEmpty()) {
                        throw MetaException(
                            lsiField,
                            "it is abstract and decorated by @" +
                                    FORMULA +
                                    ", abstract modifier means simple calculation property based on " +
                                    "SQL expression so that the `dependencies` of that annotation cannot be specified"
                        )
                    }
                }
            } else {
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 非抽象属性 Jimmer 注解限制
                // 迁移说明：注解遍历改为 LSI 注解列表（含 KS->LSI 单向兜底）
                for (anno in propLsiAnnotations(lsiField)) {
                    if (anno.fullName.startsWith("org.babyfish.jimmer.") && anno.fullName != FORMULA_CLASS_NAME) {
                        throw MetaException(
                            lsiField,
                            "it is not abstract so that " +
                                    "it cannot be decorated by " +
                                    "any jimmer annotations except @" +
                                    FORMULA_CLASS_NAME
                        )
                    }
                    if (formula !== null) {
                        formula.get<String>(FORMULA_SQL)?.takeIf { it.isNotEmpty() }?.let {
                            throw MetaException(
                                lsiField,
                                "it is non-abstract and decorated by @" +
                                        FORMULA +
                                        ", non-abstract modifier means simple calculation property based on " +
                                        "kotlin expression so that the `sql` of that annotation cannot be specified"
                            )
                        }
                        val dependencies = formula.getListArgument<String>(FORMULA_DEPENDENCIES) ?: emptyList()
                        if (dependencies.isEmpty()) {
                            throw MetaException(
                                lsiField,
                                "it is non-abstract and decorated by @" +
                                        FORMULA +
                                        ", non-abstract modifier means simple calculation property based on " +
                                        "kotlin expression so that the `dependencies` of that annotation must be specified"
                            )
                        }
                    }
                }
            }
        }

        for (lsiMethod in declaredLsiMethods) {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 非抽象函数校验
            // 迁移说明：函数合法性校验改为直接基于 LSI 方法语义，错误锚点由 MetaException(LsiMethod, ...) 提供
            if (lsiMethod.isAbstract) {
                throw MetaException(lsiMethod, "only non-abstract function is acceptable")
            }
            for (anno in lsiMethod.annotations) {
                if (anno.fullName.startsWith("org.babyfish.jimmer.")) {
                    throw MetaException(
                        lsiMethod,
                        "Non-abstract function cannot be decorated by any jimmer annotations"
                    )
                }
            }
        }

        var propIdSequence = primarySuperType?.properties?.size ?: 0
        redefinedProps = superPropMap.filterKeys {
            primarySuperType == null || !primarySuperType.properties.containsKey(it)
        }.mapValues {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType redefinedProps 构建
            // 迁移说明：ImmutableProp 构建参数由 KSPropertyDeclaration 切换为 LSI field 单源
            ImmutableProp(ctx, this, propIdSequence++, it.value.lsiField)
        }

        declaredProperties =
            declaredLsiFields
                .filter { field ->
                    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties Id 优先排序
                    // 迁移说明：Id 判定改为 LSI 注解读取（保留 KS->LSI 单向兜底）
                    propLsiAnnotation(field, ID) != null
                }.associateBy({ field ->
                    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties 属性名索引
                    // 迁移说明：属性索引键由 `KSPropertyDeclaration.name` 切换为 `LsiField.name`
                    field.name ?: throw MetaException(field, "Cannot resolve property name")
                }) { field ->
                    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties ImmutableProp 构建
                    // 迁移说明：构建参数由 `(declaration, field)` 切换为 `field`，去除 KS 属性声明依赖链
                    ImmutableProp(ctx, this, propIdSequence++, field)
                } +
                    declaredLsiFields
                        .filter { field ->
                            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties 非 Id 排序
                            // 迁移说明：Id 判定改为 LSI 注解读取（保留 KS->LSI 单向兜底）
                            propLsiAnnotation(field, ID) == null
                        }.associateBy({ field ->
                            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties 属性名索引
                            // 迁移说明：属性索引键由 `KSPropertyDeclaration.name` 切换为 `LsiField.name`
                            field.name ?: throw MetaException(field, "Cannot resolve property name")
                        }) { field ->
                            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType declaredProperties ImmutableProp 构建
                            // 迁移说明：构建参数由 `(declaration, field)` 切换为 `field`，去除 KS 属性声明依赖链
                            ImmutableProp(ctx, this, propIdSequence++, field)
                        }
    }

    val properties: Map<String, ImmutableProp> =
        if (superTypes.isEmpty()) {
            declaredProperties
        } else {
            val map = mutableMapOf<String, ImmutableProp>()
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (!prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            map
        }

    private val idPropNameMap: Map<String, String> by lazy {
        mutableMapOf<String, String>().also { map ->
            for (prop in properties.values) {
                val baseProp = prop.idViewBaseProp
                if (baseProp !== null) {
                    map[baseProp.name] = prop.name
                }
            }
            for (prop in properties.values) {
                if (prop.isReverse) {
                    continue
                }
                // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.idPropNameMap 关联注解判定
                // 迁移说明：关联注解判定改为 ImmutableProp LSI 注解读取
                if (prop.lsiAnnotation(ONE_TO_ONE) === null && prop.lsiAnnotation(MANY_TO_ONE) === null) {
                    continue
                }
                if (map.containsKey(prop.name)) {
                    continue
                }
                val expectedPropName = "${prop.name}Id"
                properties[expectedPropName]?.let {
                    throw MetaException(
                        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.idPropNameMap 自动 IdView 提示
                        // 迁移说明：属性级提示锚点由 KSPropertyDeclaration 切换为 LSI field
                        it.lsiField,
                        "It looks like @IdView of association \"${it}\", please add the @IdView annotation"
                    )
                }
                map[prop.name] = expectedPropName
            }
        }
    }

    fun getIdPropName(prop: String): String? =
        idPropNameMap[prop]

    val propsOrderById: List<ImmutableProp> by lazy {
        properties.values.sortedBy { it.id }
    }

    val idProp: ImmutableProp? by lazy {
        val idProps = declaredProperties.values.filter { it.isId }
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType id 属性唯一性与继承约束校验
        // 迁移说明：类型级 id 规则校验错误锚点由 KSClassDeclaration 切换为 LSI class
        if (idProps.size > 1) {
            throw MetaException(
                lsiClass,
                "two many properties are decorated by \"@$ID\": " +
                        idProps
            )
        }
        val superIdProp = superTypes.firstOrNull { it.idProp !== null }?.idProp
        if (superIdProp != null && idProps.isNotEmpty()) {
            throw MetaException(
                lsiClass,
                "it cannot declare id property " +
                        "because id property has been declared by super type"
            )
        }
        val prop = idProps.firstOrNull() ?: superIdProp
        if (prop == null && isEntity) {
            throw MetaException(
                lsiClass,
                "it is decorated by \"@$ENTITY\" " +
                        "but there is no id property"
            )
        }
        prop
    }

    val validationMessages: Map<LsiClassName, String> =
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.validationMessages
        // 迁移说明：校验注解消息提取改为 LSI 注解列表读取，类型级报错锚点改为 LSI class
        parseValidationMessages(lsiClass.annotations, lsiClass)

    override fun toString(): String =
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType.toString
        // 迁移说明：字符串表示由 KS fallback 收敛为 LSI qualifiedName
        lsiQualifiedNameRequired

    internal fun resolve(ctx: Context, step: Int) {
        for (prop in declaredProperties.values) {
            prop.resolve(ctx, step)
        }
        for (prop in redefinedProps.values) {
            prop.resolve(ctx, step)
        }
    }

    private fun propLsiAnnotation(
        lsiField: LsiField,
        annotationQualifiedName: String
    ): LsiAnnotation? =
        propLsiAnnotations(lsiField)
            .firstOrNull { it.fullName == annotationQualifiedName }

    private fun propLsiAnnotations(
        lsiField: LsiField
    ): List<LsiAnnotation> {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType 属性注解收集
        // 迁移说明：注解收集统一使用 `LsiField.annotations`（含 getter/returnType），去除该路径 KS 注解补齐回退
        val merged = linkedMapOf<String, LsiAnnotation>()
        for (annotation in lsiField.annotations) {
            val key = annotation.fullName
            if (key.isNotEmpty()) {
                merged.putIfAbsent(key, annotation)
            }
        }
        return merged.values.toList()
    }

    companion object {
        @JvmStatic
        val FORMULA_CLASS_NAME = FORMULA

        private const val FORMULA_SQL = "sql"

        private const val FORMULA_DEPENDENCIES = "dependencies"
    }
}
