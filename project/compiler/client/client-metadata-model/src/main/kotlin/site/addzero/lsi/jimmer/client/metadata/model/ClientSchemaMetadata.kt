package site.addzero.lsi.jimmer.client.metadata.model

/**
 * Client schema 元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiType`
 * - 不暴露 `SchemaBuilder` / `Schema` / `TypeRefImpl`
 * - 不暴露 `Context` / `LsiFiler`
 */
data class ClientSchemaMetadata(
    val services: List<ClientServiceMetadata>,
    val definitions: List<ClientTypeDefinitionMetadata>,
)

data class ClientServiceMetadata(
    val typeName: String,
    val groups: List<String>,
    val doc: String?,
    val operations: List<ClientOperationMetadata>,
)

data class ClientOperationMetadata(
    val name: String,
    val key: String,
    val groups: List<String>,
    val doc: String?,
    val parameters: List<ClientParameterMetadata>,
    val returnType: ClientTypeRefMetadata?,
    val exceptionTypeNames: List<String>,
)

data class ClientParameterMetadata(
    val name: String,
    val originalIndex: Int,
    val type: ClientTypeRefMetadata,
)

data class ClientTypeDefinitionMetadata(
    val typeName: String,
    val kind: ClientTypeDefinitionKindMetadata,
    val apiIgnore: Boolean,
    val doc: String?,
    val error: ClientTypeDefinitionErrorMetadata?,
    val groups: List<String>,
    val properties: List<ClientPropertyMetadata>,
    val superTypes: List<ClientTypeRefMetadata>,
    val enumConstants: List<ClientEnumConstantMetadata>,
)

enum class ClientTypeDefinitionKindMetadata {
    IMMUTABLE,
    OBJECT,
    ENUM,
}

data class ClientTypeDefinitionErrorMetadata(
    val family: String,
    val code: String,
)

data class ClientPropertyMetadata(
    val name: String,
    val doc: String?,
    val type: ClientTypeRefMetadata,
)

data class ClientEnumConstantMetadata(
    val name: String,
    val doc: String?,
)

data class ClientTypeRefMetadata(
    val typeName: String,
    val nullable: Boolean,
    val arguments: List<ClientTypeRefMetadata>,
    val fetchBy: String?,
    val fetcherOwnerTypeName: String?,
    val fetcherDoc: String?,
)
