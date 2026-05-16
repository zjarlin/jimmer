package site.addzero.lsi.codegen

import site.addzero.lsi.poet.LsiClassName

/**
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../org.babyfish.jimmer.ksp.JacksonTypes
 * 迁移说明：Jackson 相关 `ClassName` 聚合是纯 codegen 元数据，迁移到中立 `site.addzero.lsi.codegen` 包，避免 `Context` 之外的代码继续依赖旧 KSP 命名空间
 */
class JacksonTypes(
    val jsonIgnore: LsiClassName,
    val jsonValue: LsiClassName,
    val jsonFormat: LsiClassName,
    val jsonProperty: LsiClassName,
    val jsonPropertyOrder: LsiClassName,
    val jsonCreator: LsiClassName,
    val jsonSerializer: LsiClassName,
    val jsonSerialize: LsiClassName,
    val jsonDeserialize: LsiClassName,
    val jsonPojoBuilder: LsiClassName,
    val jsonNaming: LsiClassName,
    val jsonGenerator: LsiClassName,
    val serializeProvider: LsiClassName
)
