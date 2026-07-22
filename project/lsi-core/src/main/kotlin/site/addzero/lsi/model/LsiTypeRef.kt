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

    val annotations: List<LsiAnnotation>
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
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiTypeRef

data class LsiTypeParameterRef(
    val parameterId: LsiSymbolId,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN,
    override val annotations: List<LsiAnnotation> = emptyList(),
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
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
    val boxed: Boolean = false,
) : LsiTypeRef

data class LsiArrayType(
    val elementType: LsiTypeRef,
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiTypeRef

/**
 * 保留函数的返回值、接收者、参数和挂起语义，不提前折叠为平台声明类型。
 */
data class LsiFunctionType(
    val returnType: LsiTypeRef,
    val receiverType: LsiTypeRef? = null,
    val parameterTypes: List<LsiTypeRef> = emptyList(),
    val suspending: Boolean = false,
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiTypeRef

/**
 * 前端暂时无法闭合的类型，不允许渲染器把它当作合法类型消费。
 */
data class LsiUnresolvedType(
    val displayName: String,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN,
    override val annotations: List<LsiAnnotation> = emptyList(),
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
 * 注解成员类型在语言模型中不可空，递归消除前端投影产生的平台可空性差异。
 */
fun LsiTypeRef.toAnnotationMemberType(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toAnnotationMemberType())
            },
            nullability = LsiNullability.NON_NULL,
        )
        is LsiTypeParameterRef -> copy(nullability = LsiNullability.NON_NULL)
        is LsiPrimitiveType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiArrayType -> copy(
            elementType = elementType.toAnnotationMemberType(),
            nullability = LsiNullability.NON_NULL,
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toAnnotationMemberType(),
            receiverType = receiverType?.toAnnotationMemberType(),
            parameterTypes = parameterTypes.map(LsiTypeRef::toAnnotationMemberType),
            nullability = LsiNullability.NON_NULL,
        )
        is LsiUnresolvedType -> copy(nullability = LsiNullability.NON_NULL)
    }
}

/**
 * 提供不依赖平台类型对象的确定性类型签名，类型使用注解不参与符号标识。
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
        is LsiPrimitiveType -> buildString {
            append("primitive:")
            append(kind.name.lowercase())
            if (boxed) {
                append(":boxed")
            }
        }
        is LsiArrayType -> "array:${elementType.stableSignature()}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.stableSignature())
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter -> parameter.stableSignature() })
            append("]:return:")
            append(returnType.stableSignature())
        }
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
