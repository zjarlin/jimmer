package site.addzero.lsi.anno

import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget

/**
 * 语言无关的注解结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiAnnotation {
    /**
     * 获取注解的全限定名
     */
    val qualifiedName: String?

    /**
     * 获取注解的简单名称
     */
    val simpleName: String?

    /**
     * 获取注解的所有属性值
     */
    val attributes: Map<String, Any?>

    /**
     * 根据属性名获取注解属性值
     */
    fun getAttribute(name: String): Any?

    /**
     * 判断是否包含指定名称的属性
     */
    fun hasAttribute(name: String): Boolean

    /**
     * Kotlin use-site target 等位置语义。
     * Java/APT 等不支持的适配器默认返回 null。
     */
    val useSiteTarget: LsiAnnotationUseSiteTarget?
        get() = null

    /**
     * 获取注解类型上的元注解列表。
     * 默认空实现，适配器可按能力提供。
     */
    val annotations: List<LsiAnnotation>
        get() = emptyList()
}
