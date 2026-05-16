package site.addzero.lsi.method

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiType

/**
 * 语言无关的方法结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiMethod {
    /**
     * 获取方法名称
     */
    val name: String?

    /**
     * 获取方法返回类型
     */
    val returnType: LsiType?

    /**
     * 获取方法返回类型名称
     */
    val returnTypeName: String?

    /**
     * 获取方法注释
     */
    val comment: String?

    /**
     * 获取方法相关的全部注解。
     *
     * 语义约定：
     * - APT: 至少应统一包含方法本体与返回类型上的注解
     * - KSP: 至少应统一包含函数本体与返回类型上的注解
     * - 无额外落点的平台，至少返回方法本体注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为静态方法
     */
    val isStatic: Boolean

    /**
     * 判断是否为抽象方法
     */
    val isAbstract: Boolean

    /**
     * 判断是否为 public 方法。
     */
    val isPublic: Boolean
        get() = true

    /**
     * 判断是否为 protected 方法/构造器。
     */
    val isProtected: Boolean
        get() = false

    /**
     * 判断是否为 Kotlin internal 方法/构造器。
     */
    val isInternal: Boolean
        get() = false

    /**
     * 判断是否为 private 方法/构造器。
     */
    val isPrivate: Boolean
        get() = false

    /**
     * 判断是否为 open 方法。
     */
    val isOpen: Boolean
        get() = false

    /**
     * 泛型参数数量。
     */
    val typeParameterCount: Int
        get() = 0

    /**
     * 获取显式声明的异常类型。
     *
     * 语义约定：
     * - Java/APT: 对应 `throws Xxx`
     * - Kotlin/KSP: 对应 `@Throws(...)` / `@kotlin.jvm.Throws(...)`
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.getExceptionTypeNames`
     * - APT: `project/jimmer-apt/.../transactional/TxGenerator.addMethods`
     *
     * 迁移说明：
     * - 将“方法声明异常”提升为统一符号语义，避免 client/tx 业务层分别直连 `ExecutableElement.getThrownTypes`
     *   与 Kotlin `@Throws` 注解解析。
     */
    val thrownTypes: List<LsiType>
        get() = emptyList()

    /**
     * 判断是否为构造器。
     */
    val isConstructor: Boolean
        get() = false

    /**
     * 获取方法参数列表
     */
    val parameters: List<LsiParameter>

    /**
     * 获取声明该方法的类
     */
    val declaringClass: LsiClass?

}

/**
 * 语言无关的方法参数抽象接口
 */
interface LsiParameter {
    /**
     * 获取参数名称
     */
    val name: String?

    /**
     * 获取参数类型
     */
    val type: LsiType?

    /**
     * 获取参数类型名称
     */
    val typeName: String?

    /**
     * 获取参数上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断参数是否有默认值
     */
    val hasDefault: Boolean
        get() = false

    /**
     * 判断参数是否为可变参数（vararg）。
     */
    val isVararg: Boolean
        get() = false
}
