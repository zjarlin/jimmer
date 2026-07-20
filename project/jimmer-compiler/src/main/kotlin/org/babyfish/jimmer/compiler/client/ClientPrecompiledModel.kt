package org.babyfish.jimmer.compiler.client

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiVariance

data class ClientPrecompiledSchema(
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

data class ClientPrecompileTargets(
    val serviceTypeIds: Set<LsiSymbolId>,
) {
    init {
        serviceTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
    }

    val rootTypeIds: Set<LsiSymbolId>
        get() = serviceTypeIds

    fun without(typeIds: Set<LsiSymbolId>): ClientPrecompileTargets {
        if (typeIds.isEmpty()) {
            return this
        }
        return ClientPrecompileTargets(
            serviceTypeIds = serviceTypeIds - typeIds,
        )
    }
}

data class ClientService(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val groups: List<String>,
    val doc: String?,
    val operations: List<ClientOperation>,
)

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

    val qualifiedName: String = buildString {
        packageName?.let { value ->
            append(value)
            append('.')
        }
        append(simpleNames.joinToString("."))
    }

    companion object {
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

enum class ClientDefinitionKind {
    IMMUTABLE,
    OBJECT,
    ENUM,
}

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

data class ClientDefinitionError(
    val family: String,
    val code: String,
) {
    init {
        require(family.isNotBlank()) { "Client definition error family cannot be blank" }
        require(code.isNotBlank()) { "Client definition error code cannot be blank" }
    }
}

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

data class ClientEnumConstant(
    val id: LsiSymbolId,
    val name: String,
    val doc: String?,
) {
    init {
        require(name.isNotBlank()) { "Client enum constant name cannot be blank" }
    }
}

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

data class ClientIgnoredParameter(
    val id: LsiSymbolId,
    val name: String,
    val originalIndex: Int,
) {
    init {
        require(originalIndex >= 0) { "Ignored client parameter index cannot be negative: $originalIndex" }
    }
}

sealed interface ClientTypeRef {
    val nullable: Boolean
    val fetchBy: ClientFetchBy?
}

data class ClientDeclaredTypeRef(
    val typeId: LsiSymbolId,
    val typeName: ClientTypeName,
    val arguments: List<ClientTypeArgument> = emptyList(),
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

data class ClientPrimitiveTypeRef(
    val kind: LsiPrimitiveKind,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

data class ClientArrayTypeRef(
    val elementType: ClientTypeRef,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

data class ClientTypeParameterRef(
    val parameterId: LsiSymbolId,
    val ownerTypeName: ClientTypeName,
    val name: String,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef

data class ClientUnresolvedTypeRef(
    val displayName: String,
    override val nullable: Boolean = false,
    override val fetchBy: ClientFetchBy? = null,
) : ClientTypeRef {
    init {
        require(displayName.isNotBlank()) { "Unresolved client type display name cannot be blank" }
    }
}

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

data class ClientFetchBy(
    val value: String,
    val ownerTypeId: LsiSymbolId,
    val ownerTypeName: ClientTypeName,
    val targetEntityTypeId: LsiSymbolId,
    val nullable: Boolean,
    val documentation: String?,
) {
    init {
        require(value.isNotBlank()) { "Client FetchBy value cannot be blank" }
        ownerTypeId.requireTypeQualifiedName()
        targetEntityTypeId.requireTypeQualifiedName()
    }
}
