package org.babyfish.jimmer.compiler.client

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiVariance

data class ClientPrecompiledSchema(
    val services: List<ClientService>,
)

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
    val targetEntityTypeId: LsiSymbolId?,
    val nullable: Boolean,
) {
    init {
        require(value.isNotBlank()) { "Client FetchBy value cannot be blank" }
    }
}
