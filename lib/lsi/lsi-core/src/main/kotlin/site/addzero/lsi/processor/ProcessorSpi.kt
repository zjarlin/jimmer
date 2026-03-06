package site.addzero.lsi.processor

/**
 * 处理器编排所需的 SPI。
 *
 * [dependsOn] 用于声明执行依赖关系。
 */
interface ProcessorSpi<T, R> {
    /** 处理器唯一标识，默认使用实现类全限定名。 */
    val id: String
        get() = this::class.qualifiedName ?: this::class.java.name

    /** 当前处理器依赖的上游处理器 ID。 */
    val dependsOn: Set<String>
        get() = emptySet()

    /** 由编排器在调用 [process] 之前注入共享上下文。 */
    var ctx: T

    /** 执行处理逻辑。 */
    fun process(): R
}
