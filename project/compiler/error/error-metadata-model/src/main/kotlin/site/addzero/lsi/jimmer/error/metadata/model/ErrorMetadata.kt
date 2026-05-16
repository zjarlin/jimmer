package site.addzero.lsi.jimmer.error.metadata.model

/**
 * 错误码类型元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiType`
 * - 不暴露 `TypeElement` / `KS*`
 * - 不暴露 `SchemaBuilder`
 */
data class ErrorTypeMetadata(
    val id: String,
    val enumSimpleName: String,
    val enumQualifiedName: String,
    val packageName: String,
    val family: String,
    val exceptionSimpleName: String,
    val exceptionQualifiedName: String,
    val doc: String?,
    val declaredFields: List<ErrorFieldMetadata>,
    val items: List<ErrorItemMetadata>
)

data class ErrorItemMetadata(
    val id: String,
    val ownerTypeId: String,
    val enumConstantName: String,
    val exceptionSimpleName: String,
    val code: String,
    val doc: String?,
    val declaredFields: List<ErrorFieldMetadata>
)

data class ErrorFieldMetadata(
    val id: String,
    val ownerId: String,
    val name: String,
    val typeName: String,
    val nullable: Boolean,
    val list: Boolean,
    val doc: String?
)
