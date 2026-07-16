package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

enum class LsiNullability {
    NON_NULL,
    NULLABLE,
    PLATFORM,
    UNKNOWN
}

enum class LsiVariance {
    INVARIANT,
    IN,
    OUT,
    STAR
}

sealed interface LsiTypeRef {
    val nullability: LsiNullability
}

data class LsiTypeArgument(
    val variance: LsiVariance = LsiVariance.INVARIANT,
    val type: LsiTypeRef? = null
) {

    init {
        if (variance == LsiVariance.STAR) {
            require(type == null) { "Star-projected LSI type argument cannot have a type" }
        } else {
            requireNotNull(type) { "Non-star LSI type argument requires a type" }
        }
    }

    companion object {
        val STAR: LsiTypeArgument = LsiTypeArgument(LsiVariance.STAR)

        fun invariant(type: LsiTypeRef): LsiTypeArgument = LsiTypeArgument(LsiVariance.INVARIANT, type)

        fun input(type: LsiTypeRef): LsiTypeArgument = LsiTypeArgument(LsiVariance.IN, type)

        fun output(type: LsiTypeRef): LsiTypeArgument = LsiTypeArgument(LsiVariance.OUT, type)
    }
}

data class LsiDeclaredType(
    val declarationId: LsiSymbolId,
    val arguments: List<LsiTypeArgument> = emptyList(),
    override val nullability: LsiNullability = LsiNullability.NON_NULL
) : LsiTypeRef

data class LsiTypeParameterRef(
    val parameterId: LsiSymbolId,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN
) : LsiTypeRef

enum class LsiPrimitiveKind {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    CHAR,
    FLOAT,
    DOUBLE,
    UNIT,
    VOID
}

data class LsiPrimitiveType(
    val kind: LsiPrimitiveKind,
    override val nullability: LsiNullability = LsiNullability.NON_NULL
) : LsiTypeRef

data class LsiArrayType(
    val elementType: LsiTypeRef,
    override val nullability: LsiNullability = LsiNullability.NON_NULL
) : LsiTypeRef

/**
 * 前端暂时无法闭合的类型，不允许渲染器把它当作合法类型消费。
 */
data class LsiUnresolvedType(
    val displayName: String,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN
) : LsiTypeRef {

    init {
        require(displayName.isNotBlank()) { "Unresolved LSI type display name cannot be blank" }
    }
}

data class LsiTypeParameter(
    val id: LsiSymbolId,
    val name: String,
    val variance: LsiVariance = LsiVariance.INVARIANT,
    val upperBounds: List<LsiTypeRef> = emptyList()
) {

    init {
        require(name.isNotBlank()) { "LSI type parameter name cannot be blank" }
        require(variance != LsiVariance.STAR) { "LSI type parameter declaration cannot use star variance" }
    }
}

/**
 * 提供不依赖平台类型对象的确定性类型签名。
 */
fun LsiTypeRef.stableSignature(): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.stableSignature() })
                append('>')
            }
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiPrimitiveType -> "primitive:${kind.name.lowercase()}"
        is LsiArrayType -> "array:${elementType.stableSignature()}"
        is LsiUnresolvedType -> "unresolved:$displayName"
    }
    return base + nullability.stableSuffix()
}

private fun LsiTypeArgument.stableSignature(): String = when (variance) {
    LsiVariance.STAR -> "*"
    LsiVariance.INVARIANT -> requireNotNull(type).stableSignature()
    LsiVariance.IN -> "in:${requireNotNull(type).stableSignature()}"
    LsiVariance.OUT -> "out:${requireNotNull(type).stableSignature()}"
}

private fun LsiNullability.stableSuffix(): String = when (this) {
    LsiNullability.NON_NULL -> "!non-null"
    LsiNullability.NULLABLE -> "?nullable"
    LsiNullability.PLATFORM -> "!platform"
    LsiNullability.UNKNOWN -> "?unknown"
}
