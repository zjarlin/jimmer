package org.babyfish.jimmer.processor.spi

import site.addzero.util.lsi.clazz.LsiClass

/**
 * Jimmer 实体元数据消费者 SPI 接口。
 * 当 ImmutableProcessor 完成所有 `@Entity`/`@MappedSuperclass`/`@Embeddable`/`@Immutable`
 * 类型的代码生成后，会通过 [java.util.ServiceLoader] 发现并调用所有实现了此接口的消费者。
 *
 * 参数为 [LsiClass] 列表，消费者通过 `lsi-jimmer` 模块提供的扩展函数访问 Jimmer ORM 语义
 * （如 `isJimmerEntity`、`jimmerIdField`、`jimmerAssociationFields` 等），
 * 无需依赖任何预编译框架（KSP/APT）。
 *
 * SPI interface for consuming Jimmer entity metadata.
 * After ImmutableProcessor finishes generating code for all
 * `@Entity`/`@MappedSuperclass`/`@Embeddable`/`@Immutable` types,
 * all implementations discovered via [java.util.ServiceLoader] will be invoked.
 *
 * The parameter is a list of [LsiClass]. Consumers access Jimmer ORM semantics via
 * extension functions from the `lsi-jimmer` module (e.g. `isJimmerEntity`,
 * `jimmerIdField`, `jimmerAssociationFields`), with no dependency on KSP or APT.
 *
 * ## 注册方式 / Registration
 * 在模块的 `resources/META-INF/services/` 目录下创建文件：
 * Create a file under `resources/META-INF/services/` in your module:
 * ```
 * org.babyfish.jimmer.processor.spi.EntityMetaConsumerSpi
 * ```
 * 文件内容为实现类的全限定名。/ File content is the fully-qualified name of your implementation.
 *
 * ## 示例 / Example
 * ```kotlin
 * // 需依赖 lsi-jimmer 模块 / Requires lsi-jimmer dependency
 * class MyConsumer : EntityMetaConsumerSpi {
 *     override fun consume(entities: List<LsiClass>) {
 *         entities.filter { it.isJimmerEntity }.forEach { entity ->
 *             println("Entity: ${entity.qualifiedName}")
 *             entity.jimmerIdField?.let { id ->
 *                 println("  ID prop: ${id.name} : ${id.typeName}")
 *             }
 *             entity.jimmerAssociationFields.forEach { assoc ->
 *                 println("  Association: ${assoc.name} -> ${assoc.fieldTypeClass?.qualifiedName}")
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface EntityMetaConsumerSpi {

    /**
     * 接收本轮处理中发现的所有 Jimmer 实体类型的 [LsiClass] 列表，在代码生成完成后被调用。
     * 列表中的每个元素均已通过 include/exclude 过滤规则。
     * 通过 `lsi-jimmer` 扩展可访问完整的 Jimmer ORM 语义。
     *
     * Receives the list of [LsiClass] for all Jimmer entity types found in this processing round,
     * called after code generation is complete.
     * Full Jimmer ORM semantics are accessible via `lsi-jimmer` extensions.
     *
     * @param entities 本轮处理中发现的所有 Jimmer 实体类型（含四种：Entity/MappedSuperclass/Embeddable/Immutable）。
     *                 All Jimmer entity types found in this round (all four kinds).
     */
    fun consume(entities: List<LsiClass>)
}
