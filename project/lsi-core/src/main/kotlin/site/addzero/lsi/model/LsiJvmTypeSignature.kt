package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

sealed interface LsiJvmTypeParameterOwner {

    data class Type(
        val typeId: LsiSymbolId,
    ) : LsiJvmTypeParameterOwner

    data class Method(
        val name: String,
    ) : LsiJvmTypeParameterOwner {

        init {
            require(name.isNotBlank()) { "JVM type parameter method owner name cannot be blank" }
        }
    }
}

data class LsiJvmTypeParameterDescriptor(
    val id: LsiSymbolId,
    val owner: LsiJvmTypeParameterOwner,
    val index: Int,
    val upperBounds: List<LsiTypeRef> = emptyList(),
) {

    init {
        require(index >= 0) { "JVM type parameter index cannot be negative: $index" }
    }
}

data class LsiJvmTypeSignatureContext(
    val canonicalDeclaredTypeIds: Map<LsiSymbolId, LsiSymbolId> = emptyMap(),
    val typeParameters: Map<LsiSymbolId, LsiJvmTypeParameterDescriptor> = emptyMap(),
) {

    init {
        typeParameters.forEach { (id, descriptor) ->
            require(id == descriptor.id) {
                "JVM type parameter descriptor key '$id' does not match '${descriptor.id}'"
            }
        }
    }
}

/**
 * 将冻结类型渲染为前端无关的 JVM callable 签名，不把 nullability 和类型使用注解写入符号 ID。
 */
fun LsiTypeRef.toJvmTypeSignature(
    eraseTypeArguments: Boolean = false,
    context: LsiJvmTypeSignatureContext = LsiJvmTypeSignatureContext(),
): String {
    return JvmTypeSignatureRenderer(context).render(this, eraseTypeArguments)
}

/**
 * JVM callable 参数中的 Unit 和 Void 都是引用类型，其余 primitive 保留原始表示。
 */
fun LsiTypeRef.toJvmCallableParameterType(): LsiTypeRef {
    val primitive = this as? LsiPrimitiveType ?: return this
    return if (primitive.kind == LsiPrimitiveKind.UNIT || primitive.kind == LsiPrimitiveKind.VOID) {
        primitive.copy(boxed = true)
    } else {
        primitive
    }
}

/**
 * 泛型实参和类型参数上界只能使用 JVM 引用类型。
 */
fun LsiTypeRef.toJvmReferenceType(): LsiTypeRef {
    return if (this is LsiPrimitiveType) copy(boxed = true) else this
}

private class JvmTypeSignatureRenderer(
    private val context: LsiJvmTypeSignatureContext,
) {

    fun render(
        type: LsiTypeRef,
        eraseTypeArguments: Boolean,
        erasurePath: Set<LsiSymbolId> = emptySet(),
    ): String {
        return when (type) {
            is LsiDeclaredType -> renderDeclared(type, eraseTypeArguments, erasurePath)
            is LsiPrimitiveType -> type.toJvmPrimitiveSignature()
            is LsiArrayType -> "array:${renderArrayElement(type.elementType, eraseTypeArguments, erasurePath)}"
            is LsiTypeParameterRef -> renderTypeParameter(type.parameterId, eraseTypeArguments, erasurePath)
            is LsiFunctionType -> throw IllegalArgumentException(
                "JVM type signature cannot infer the ABI of an LSI function type",
            )
            is LsiUnresolvedType -> "unresolved:${type.displayName.filterNot(Char::isWhitespace)}"
        }
    }

    private fun renderDeclared(
        type: LsiDeclaredType,
        eraseTypeArguments: Boolean,
        erasurePath: Set<LsiSymbolId>,
    ): String = buildString {
        append("type:")
        append(canonicalDeclaredTypeId(type.declarationId).requireTypeQualifiedName())
        if (!eraseTypeArguments && type.arguments.isNotEmpty()) {
            append('<')
            append(type.arguments.joinToString(",") { argument -> renderArgument(argument, erasurePath) })
            append('>')
        }
    }

    private fun renderArrayElement(
        elementType: LsiTypeRef,
        eraseTypeArguments: Boolean,
        erasurePath: Set<LsiSymbolId>,
    ): String {
        val primitive = elementType as? LsiPrimitiveType
        val normalized = if (
            primitive != null &&
            (primitive.kind == LsiPrimitiveKind.UNIT || primitive.kind == LsiPrimitiveKind.VOID)
        ) {
            primitive.toJvmReferenceType()
        } else {
            elementType
        }
        return render(normalized, eraseTypeArguments, erasurePath)
    }

    private fun renderArgument(
        argument: LsiTypeArgument,
        erasurePath: Set<LsiSymbolId>,
    ): String {
        if (argument.variance == LsiVariance.STAR) {
            return "*"
        }
        val signature = render(requireNotNull(argument.type).toJvmReferenceType(), false, erasurePath)
        return when (argument.variance) {
            LsiVariance.STAR -> error("Star projection has already been handled")
            LsiVariance.INVARIANT -> signature
            LsiVariance.IN -> "in:$signature"
            LsiVariance.OUT -> "out:$signature"
        }
    }

    private fun renderTypeParameter(
        parameterId: LsiSymbolId,
        erased: Boolean,
        erasurePath: Set<LsiSymbolId>,
    ): String {
        val descriptor = requireNotNull(context.typeParameters[parameterId]) {
            "Missing JVM type parameter descriptor for '$parameterId'"
        }
        if (erased) {
            return renderErasure(descriptor, erasurePath)
        }
        val ownerSignature = when (val owner = descriptor.owner) {
            is LsiJvmTypeParameterOwner.Type -> {
                "type:${canonicalDeclaredTypeId(owner.typeId).requireTypeQualifiedName()}"
            }
            is LsiJvmTypeParameterOwner.Method -> "method:${owner.name}"
        }
        val signature = "parameter:$ownerSignature:${descriptor.index}"
        return if (descriptor.owner is LsiJvmTypeParameterOwner.Method) {
            "$signature:${renderErasure(descriptor, erasurePath)}"
        } else {
            signature
        }
    }

    private fun renderErasure(
        descriptor: LsiJvmTypeParameterDescriptor,
        erasurePath: Set<LsiSymbolId>,
    ): String {
        check(descriptor.id !in erasurePath) {
            "Recursive JVM type parameter erasure: ${(erasurePath + descriptor.id).joinToString(" -> ")}"
        }
        val upperBound = descriptor.upperBounds.firstOrNull() ?: return "type:java.lang.Object"
        return render(
            type = upperBound.toJvmReferenceType(),
            eraseTypeArguments = true,
            erasurePath = erasurePath + descriptor.id,
        )
    }

    private fun canonicalDeclaredTypeId(typeId: LsiSymbolId): LsiSymbolId {
        val visited = linkedSetOf<LsiSymbolId>()
        var current = typeId
        while (true) {
            check(visited.add(current)) {
                "Recursive JVM declared type alias: ${(visited + current).joinToString(" -> ")}"
            }
            current = context.canonicalDeclaredTypeIds[current] ?: return current
        }
    }
}

private fun LsiPrimitiveType.toJvmPrimitiveSignature(): String {
    if (!boxed) {
        return "primitive:${kind.name.lowercase()}"
    }
    return "type:${JVM_BOXED_PRIMITIVE_TYPE_IDS.getValue(kind).requireTypeQualifiedName()}"
}

private val JVM_BOXED_PRIMITIVE_TYPE_IDS = mapOf(
    LsiPrimitiveKind.BOOLEAN to LsiSymbolId.type("java.lang.Boolean"),
    LsiPrimitiveKind.BYTE to LsiSymbolId.type("java.lang.Byte"),
    LsiPrimitiveKind.SHORT to LsiSymbolId.type("java.lang.Short"),
    LsiPrimitiveKind.INT to LsiSymbolId.type("java.lang.Integer"),
    LsiPrimitiveKind.LONG to LsiSymbolId.type("java.lang.Long"),
    LsiPrimitiveKind.CHAR to LsiSymbolId.type("java.lang.Character"),
    LsiPrimitiveKind.FLOAT to LsiSymbolId.type("java.lang.Float"),
    LsiPrimitiveKind.DOUBLE to LsiSymbolId.type("java.lang.Double"),
    LsiPrimitiveKind.UNIT to LsiSymbolId.type("kotlin.Unit"),
    LsiPrimitiveKind.VOID to LsiSymbolId.type("java.lang.Void"),
)
