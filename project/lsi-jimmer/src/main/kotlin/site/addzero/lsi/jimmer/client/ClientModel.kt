package site.addzero.lsi.jimmer.client

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiVariance

/** 与语言前端无关的完整 Client 语义模型。 */
data class ClientSchema(
    val services: List<ClientService>,
    val definitions: List<ClientTypeDefinition>,
) {
    init {
        require(services == services.sortedBy(ClientService::id)) {
            "Client services must use stable id order"
        }
        require(definitions == definitions.sortedBy(ClientTypeDefinition::id)) {
            "Client definitions must use stable id order"
        }
        require(services.map(ClientService::id).distinct().size == services.size) {
            "Client schema cannot contain duplicate services"
        }
        require(definitions.map(ClientTypeDefinition::id).distinct().size == definitions.size) {
            "Client schema cannot contain duplicate definitions"
        }
    }
}

/** 一次 Client 解析所包含的服务目标。 */
data class ClientTargets(
    val serviceTypeIds: Set<LsiSymbolId>,
) {
    init {
        serviceTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
    }

    /** 需要由前端完整解析的根类型。 */
    val rootTypeIds: Set<LsiSymbolId>
        get() = serviceTypeIds

    /** 排除本轮无法解析的目标。 */
    fun without(typeIds: Set<LsiSymbolId>): ClientTargets {
        if (typeIds.isEmpty()) {
            return this
        }
        return ClientTargets(
            serviceTypeIds = serviceTypeIds - typeIds,
        )
    }
}

/** 可生成 Client 资源的服务定义。 */
data class ClientService(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val groups: List<String>,
    val doc: String?,
    val operations: List<ClientOperation>,
)

/** 保留嵌套层级的 Client 类型名称。 */
data class ClientTypeName(
    val packageName: String?,
    val simpleNames: List<String>,
) {
    init {
        require(packageName == packageName?.trim()?.takeIf(String::isNotEmpty)) {
            "Client type package name must be normalized: '$packageName'"
        }
        require(simpleNames.isNotEmpty()) { "Client type name requires at least one simple name" }
        require(simpleNames.all { name -> name.isNotBlank() && name == name.trim() }) {
            "Client type simple names must be non-blank and normalized"
        }
    }

    /** 点分隔的完整类型名。 */
    val qualifiedName: String = buildString {
        packageName?.let { value ->
            append(value)
            append('.')
        }
        append(simpleNames.joinToString("."))
    }

    companion object {
        /** 从完整类型名创建 Client 类型名称。 */
        fun parse(qualifiedName: String): ClientTypeName {
            require(qualifiedName.isNotBlank()) { "Client qualified type name cannot be blank" }
            val packageSeparator = qualifiedName.lastIndexOf('.')
            return if (packageSeparator == -1) {
                ClientTypeName(packageName = null, simpleNames = listOf(qualifiedName))
            } else {
                ClientTypeName(
                    packageName = qualifiedName.substring(0, packageSeparator).takeIf(String::isNotEmpty),
                    simpleNames = listOf(qualifiedName.substring(packageSeparator + 1)),
                )
            }
        }
    }
}

/** Client 类型定义的结构类别。 */
enum class ClientDefinitionKind {
    IMMUTABLE,
    OBJECT,
    ENUM,
}

/** Client 资源中的类型定义。 */
data class ClientTypeDefinition(
    val id: LsiSymbolId,
    val typeName: ClientTypeName,
    val kind: ClientDefinitionKind,
    val apiIgnore: Boolean,
    val doc: String?,
    val error: ClientDefinitionError?,
    val properties: List<ClientDefinitionProperty>,
    val superTypes: List<ClientTypeRef>,
    val polymorphicBranches: List<ClientDeclaredTypeRef>,
    val enumConstants: List<ClientEnumConstant>,
) {
    init {
        id.requireTypeQualifiedName()
        require(properties.map(ClientDefinitionProperty::name).distinct().size == properties.size) {
            "Client definition cannot contain duplicate property names: ${id.value}"
        }
        require(polymorphicBranches.map(ClientDeclaredTypeRef::typeId).distinct().size == polymorphicBranches.size) {
            "Client definition cannot contain duplicate polymorphic branches: ${id.value}"
        }
        require(enumConstants.map(ClientEnumConstant::name).distinct().size == enumConstants.size) {
            "Client definition cannot contain duplicate enum constants: ${id.value}"
        }
        require(kind == ClientDefinitionKind.ENUM || enumConstants.isEmpty()) {
            "Only enum client definition can contain constants: ${id.value}"
        }
        require(kind != ClientDefinitionKind.ENUM || properties.isEmpty()) {
            "Enum client definition cannot contain properties: ${id.value}"
        }
    }
}

/** 错误类型定义关联的 family 与 code。 */
data class ClientDefinitionError(
    val family: String,
    val code: String,
) {
    init {
        require(family.isNotBlank()) { "Client definition error family cannot be blank" }
        require(code.isNotBlank()) { "Client definition error code cannot be blank" }
    }
}

/** Client 类型定义中的属性。 */
data class ClientDefinitionProperty(
    val id: LsiSymbolId,
    val name: String,
    val type: ClientTypeRef,
    val doc: String?,
) {
    init {
        require(name.isNotBlank()) { "Client definition property name cannot be blank" }
    }
}

/** Client 枚举定义中的常量。 */
data class ClientEnumConstant(
    val id: LsiSymbolId,
    val name: String,
    val doc: String?,
) {
    init {
        require(name.isNotBlank()) { "Client enum constant name cannot be blank" }
    }
}

/** Client 服务公开的操作。 */
data class ClientOperation(
    val id: LsiSymbolId,
    val name: String,
    val groups: List<String>,
    val doc: String?,
    val parameters: List<ClientParameter>,
    val ignoredParameters: List<ClientIgnoredParameter>,
    val returnType: ClientTypeRef?,
    val declaredExceptionTypeIds: List<LsiSymbolId>,
    val exceptionTypeIds: List<LsiSymbolId>,
    val exceptionMetadata: List<ClientExceptionMetadata>,
)

/** Client 操作可见的异常层级元数据。 */
data class ClientExceptionMetadata(
    val typeId: LsiSymbolId,
    val errorFamilyId: LsiSymbolId?,
    val family: String,
    val code: String?,
    val checked: Boolean,
    val abstract: Boolean,
    val superTypeId: LsiSymbolId?,
    val subTypeIds: List<LsiSymbolId>,
    val documentation: String?,
) {
    init {
        typeId.requireTypeQualifiedName()
        errorFamilyId?.requireTypeQualifiedName()
        require(family.isNotBlank()) { "Client exception family cannot be blank" }
        require(code == null || code.isNotBlank()) { "Client exception code cannot be blank" }
        superTypeId?.requireTypeQualifiedName()
        require(superTypeId != typeId) {
            "Client exception metadata cannot inherit itself: ${typeId.value}"
        }
        subTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(subTypeIds.distinct().size == subTypeIds.size) {
            "Client exception subtype ids must be unique: ${typeId.value}"
        }
        require(typeId !in subTypeIds) {
            "Client exception metadata cannot directly reference itself: ${typeId.value}"
        }
    }
}

/** Client 操作中参与生成的参数。 */
data class ClientParameter(
    val id: LsiSymbolId,
    val name: String,
    val originalIndex: Int,
    val type: ClientTypeRef,
) {
    init {
        require(originalIndex >= 0) { "Client parameter index cannot be negative: $originalIndex" }
    }
}

/** Client 操作中被显式忽略的参数。 */
data class ClientIgnoredParameter(
    val id: LsiSymbolId,
    val name: String,
    val originalIndex: Int,
) {
    init {
        require(originalIndex >= 0) { "Ignored client parameter index cannot be negative: $originalIndex" }
    }
}

/** Client 资源可表达的类型引用。 */
sealed interface ClientTypeRef {
    val nullable: Boolean
    val fetchBy: ClientFetchBy?
}

/** 声明类型的 Client 引用。 */
data class ClientDeclaredTypeRef(
    val typeId: LsiSymbolId,
    val typeName: ClientTypeName,
    val arguments: List<ClientTypeArgument> = emptyList(),
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

/** 基础类型的 Client 引用。 */
data class ClientPrimitiveTypeRef(
    val kind: LsiPrimitiveKind,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

/** 数组类型的 Client 引用。 */
data class ClientArrayTypeRef(
    val elementType: ClientTypeRef,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

/** 类型参数的 Client 引用。 */
data class ClientTypeParameterRef(
    val parameterId: LsiSymbolId,
    val ownerTypeName: ClientTypeName,
    val name: String,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

/** 尚未解析的 Client 类型引用。 */
data class ClientUnresolvedTypeRef(
    val displayName: String,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef {
    init {
        require(displayName.isNotBlank()) { "Unresolved client type display name cannot be blank" }
    }
}

/** 带变型信息的 Client 类型实参。 */
data class ClientTypeArgument(
    val variance: LsiVariance,
    val type: ClientTypeRef?,
) {
    init {
        if (variance == LsiVariance.STAR) {
            require(type == null) { "Star client type argument cannot have a type" }
        } else {
            requireNotNull(type) { "Non-star client type argument requires a type" }
        }
    }
}

/** Client 类型引用上的 FetchBy 元数据。 */
data class ClientFetchBy(
    val value: String,
    val ownerTypeId: LsiSymbolId,
    val ownerTypeName: ClientTypeName,
    val targetEntityTypeId: LsiSymbolId,
    val documentation: String?,
) {
    init {
        require(value.isNotBlank()) { "Client FetchBy value cannot be blank" }
        ownerTypeId.requireTypeQualifiedName()
        targetEntityTypeId.requireTypeQualifiedName()
    }
}
