package site.addzero.lsi.clazz

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.type.LsiType

/**
 * 语言无关的类结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiClass {
    /**
     * 获取类的简单名称
     */
    val simpleName: String?

    /**
     * 获取类的全限定名
     */
    val qualifiedName: String?

    /**
     * 获取包名。
     *
     * 语义约定：
     * - 适配器应返回真正的 package name，不应把外部类名误当作包名
     * - 无包类型返回 `null` 或空串皆可，业务侧按“无包”处理
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.typeName`
     * - KSP: `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.toClientTypeName`
     */
    val packageName: String?
        get() = qualifiedName?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() }

    /**
     * 获取声明类型所在 package 的注解。
     *
     * 语义约定：
     * - APT: 对应 `PackageElement` / `package-info.java` 上的注解
     * - KSP: 对应 Kotlin `@file:` package-level 注解
     * - 无法感知 package 注解的平台可返回空列表
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../client/ExportDocProcessor.pkg`
     * - KSP: `project/compiler/client/jimmer-ksp-client/.../LsiExportDocSupport.exportDocPkg`
     *
     * 迁移说明：
     * - 将 package 级别注解读取提升为统一符号语义，避免业务层继续依赖
     *   `PackageElement.getAnnotation(...)` / `KSFile.annotations`
     */
    val packageAnnotations: List<LsiAnnotation>
        get() = emptyList()

    /**
     * 获取类型简单名链。
     *
     * 示例：
     * - `com.acme.Book` -> `["Book"]`
     * - `com.acme.Book.Store` -> `["Book", "Store"]`
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.typeName`
     * - KSP: `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.toClientTypeName`
     *
     * 迁移说明：
     * - 这是纯符号能力，用于消除业务层对“qualifiedName 手拆嵌套类名”的脆弱逻辑依赖
     */
    val simpleNames: List<String>
        get() = simpleName?.let(::listOf) ?: emptyList()

    /**
     * 获取类的注释
     */
    val comment: String?

    /**
     * 获取类的所有字段
     */
    val fields: List<LsiField>

    /**
     * 获取类上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为接口
     */
    val isInterface: Boolean

    /**
     * 判断是否为普通类（非接口、非枚举、非对象）。
     */
    val isClass: Boolean

    /**
     * 判断是否为枚举
     */
    val isEnum: Boolean

    /**
     * 判断是否为集合类型
     */
    val isCollectionType: Boolean

    /**
     * 判断是否为 POJO 类
     * POJO 类的判断标准：
     * 1. 有实体注解：@Entity (JPA), @Table (Jimmer)
     * 2. 有数据类注解：@Data (Lombok/Kotlin), @Getter/@Setter (Lombok)
     * 3. 不是接口、不是枚举、不是抽象类
     */
    val isPojo: Boolean

    /**
     * 判断是否为顶层类型。
     */
    val isTopLevel: Boolean
        get() = true

    /**
     * 判断是否为静态嵌套类型。
     *
     * 语义约定：
     * - Java static nested class -> true
     * - Kotlin nested class / object / companion object -> true
     * - Java inner class / Kotlin inner class -> false
     * - top-level type -> false
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.handleDeclaredType`
     */
    val isStatic: Boolean
        get() = false

    /**
     * 判断是否为 Kotlin internal 类型。
     */
    val isInternal: Boolean
        get() = false

    /**
     * 判断是否为 protected 类型。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap` 的 `KSClassDeclaration.isProtected()`
     */
    val isProtected: Boolean
        get() = false

    /**
     * 判断是否为 private 类型。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap` 的 `KSClassDeclaration.isPrivate()`
     */
    val isPrivate: Boolean
        get() = false

    /**
     * 判断是否为抽象类/接口。
     */
    val isAbstract: Boolean
        get() = false

    /**
     * 判断是否为 final 类型（不可继承）。
     */
    val isFinal: Boolean
        get() = false

    /**
     * 判断是否为 open（可继承）类型。
     */
    val isOpen: Boolean
        get() = isClass && !isFinal

    /**
     * 判断是否为 data class（Kotlin 专有；其他平台默认 false）。
     */
    val isData: Boolean
        get() = false

    /**
     * 判断是否为 sealed class/interface（Kotlin/Java sealed）。
     */
    val isSealed: Boolean
        get() = false

    /**
     * 泛型参数数量。
     */
    val typeParameterCount: Int
        get() = 0

    /**
     * 泛型参数名称列表（例如 `T`, `ID`）。
     */
    val typeParameterNames: List<String>
        get() = emptyList()

    /**
     * 枚举常量名称列表（非枚举默认空）。
     */
    val enumEntryNames: List<String>
        get() = emptyList()

    /**
     * 枚举常量列表（非枚举默认空）。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/error/jimmer-ksp-error/.../ErrorGenerator` 的 `ClassKind.ENUM_ENTRY` 声明遍历
     * - APT: `project/jimmer-apt/.../error/ErrorGenerator` 的 `ElementKind.ENUM_CONSTANT` 遍历
     */
    val enumConstants: List<LsiEnumConstant>
        get() = emptyList()

    /**
     * 获取父类
     */
    val superClasses: List<LsiClass>

    /**
     * 获取完整父类型（保留泛型实参）。
     */
    val superTypes: List<LsiType>
        get() = emptyList()

    /**
     * 获取实现的接口
     */
    val interfaces: List<LsiClass>

    val methods: List<LsiMethod>

    /**
     * 获取构造器列表
     */
    val constructors: List<LsiMethod>
        get() = emptyList()

    /**
     * 获取主构造器（如 Kotlin primary constructor）。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/transactional/jimmer-ksp-transactional/.../TxGenerator.addConstructors`
     */
    val primaryConstructor: LsiMethod?
        get() = null



    /**
     * 获取声明文件名（不含扩展名）。
     *
     * 语义约定：
     * - KSP 等可直接感知源文件的平台，返回真实文件名
     * - APT/反射等无法准确获取时，允许返回 top-level type simple name 作为 best-effort 值
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator.generate`
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../PropsGenerator.generate`
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../FetcherGenerator.generate`
     */
    val fileName: String?
        get() = null

    /**
     * 判断是否为 Kotlin `object`。
     *
     * 其他平台默认 `false`。
     */
    val isObject: Boolean
        get() = false

    /**
     * 判断是否为 Kotlin `companion object`。
     *
     * 其他平台默认 `false`。
     */
    val isCompanionObject: Boolean
        get() = false
}
