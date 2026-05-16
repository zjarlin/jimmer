package site.addzero.lsi.jimmer.processor.spi


/**
 * 内置处理器 ID 常量。
 *
 * 覆盖来源（Jimmer 源码）：
 * - tuple: dependsOn(dto, immutable)
 * - dto: dependsOn(immutable)
 * - client: dependsOn(immutable, tuple)
 *
 * 迁移说明：
 * - 这些常量绑定的是 compiler 内置处理器 FQCN，属于编排层常量，不属于 `lsi-jimmer` 语义扩展
 * - 因此从 `lib/lsi/lsi-jimmer` 下沉回 `project/compiler/jimmer-ksp-ext`
 */
const val IMMUTABLE_PROCESSOR = "org.babyfish.jimmer.ksp.immutable.ImmutableProcessor"
const val ERROR_PROCESSOR = "org.babyfish.jimmer.ksp.error.ErrorProcessor"
const val DTO_PROCESSOR = "org.babyfish.jimmer.ksp.dto.DtoProcessor"
const val TX_PROCESSOR = "org.babyfish.jimmer.ksp.transactional.TxProcessor"
const val EXPORT_DOC_PROCESSOR = "org.babyfish.jimmer.ksp.client.ExportDocProcessor"
const val TYPED_TUPLE_PROCESSOR = "org.babyfish.jimmer.ksp.tuple.TypedTupleProcessor"
const val CLIENT_PROCESSOR = "org.babyfish.jimmer.ksp.client.ClientProcessor"
