package site.addzero.lsi.jimmer.processor.spi

import site.addzero.lsi.clazz.LsiClass

/**
 * Jimmer 实体元数据消费者 SPI。
 *
 * 覆盖来源（Jimmer 源码）：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.notifyEntityMetaConsumers`
 *
 * 迁移说明：
 * - 该 SPI 现在由 compiler 的 APT/KSP immutable 壳层共同消费，不属于 `lsi-jimmer` 语义扩展本身
 * - 因此它保留在 compiler shared 层，而不是放回任一平台 adapter
 */
interface EntityMetaConsumerSpi {
    fun consume(entities: List<LsiClass>)
}
