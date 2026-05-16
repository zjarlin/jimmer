package site.addzero.lsi.clazz

import site.addzero.lsi.anno.LsiAnnotation

/**
 * 语言无关的枚举常量抽象。
 *
 * 统一 KSP `ENUM_ENTRY` 与 APT `ENUM_CONSTANT` 的最小公共语义，
 * 用于错误码等需要读取常量级注解的场景。
 */
interface LsiEnumConstant {

    /**
     * 枚举常量名称。
     */
    val name: String?

    /**
     * 枚举常量注释。
     */
    val comment: String?
        get() = null

    /**
     * 枚举常量上的注解。
     */
    val annotations: List<LsiAnnotation>

    /**
     * 所属枚举类型。
     */
    val declaringClass: LsiClass?
}
