package org.babyfish.jimmer.compiler.lsi.apt

import site.addzero.lsi.core.LsiSymbolId
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ErrorType
import javax.lang.model.type.IntersectionType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType

fun TypeMirror.toLsiType(
    processingEnvironment: ProcessingEnvironment,
): LsiTypeRef {
    return AptLsiContext(
        processingEnvironment,
        LsiFrontendOptions.from(processingEnvironment.options),
    ).toLsiType(this)
}

internal fun AptLsiContext.toLsiType(
    type: TypeMirror,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId> = emptyMap(),
): LsiTypeRef {
    return when (type) {
        is ErrorType -> toLsiErrorType(type, typeParameterIds)
        is PrimitiveType -> LsiPrimitiveType(type.kind.toLsiPrimitiveKind())
        is ArrayType -> LsiArrayType(
            elementType = toLsiType(type.componentType, typeParameterIds),
            nullability = LsiNullability.PLATFORM,
        )
        is DeclaredType -> toLsiDeclaredType(type, typeParameterIds)
        is TypeVariable -> toLsiTypeParameterRef(type, typeParameterIds)
        is NoType -> type.toLsiNoType()
        is WildcardType -> toLsiWildcardFallback(type, typeParameterIds)
        else -> LsiUnresolvedType(type.toString())
    }
}

internal fun AptLsiContext.toLsiTypeParameters(
    ownerId: LsiSymbolId,
    parameters: List<TypeParameterElement>,
    inheritedIds: Map<TypeParameterElement, LsiSymbolId> = emptyMap(),
): Pair<List<LsiTypeParameter>, Map<TypeParameterElement, LsiSymbolId>> {
    val ownIds = parameters.associateWith { parameter ->
        LsiSymbolId.typeParameter(ownerId, parameter.simpleName.toString())
    }
    val allIds = inheritedIds + ownIds
    val lsiParameters = parameters.map { parameter ->
        val bounds = parameter.bounds
            .filterNot(::isImplicitObjectBound)
            .map { bound -> toLsiType(bound, allIds) }
        LsiTypeParameter(
            id = requireNotNull(ownIds[parameter]),
            name = parameter.simpleName.toString(),
            upperBounds = bounds,
        )
    }
    return lsiParameters to allIds
}

internal fun AptLsiContext.typeParameterIdsInScope(
    element: javax.lang.model.element.Element,
): Map<TypeParameterElement, LsiSymbolId> {
    val typeOwners = generateSequence(element.enclosingElement) { current -> current.enclosingElement }
        .filterIsInstance<TypeElement>()
        .toList()
        .asReversed()
    val ids = linkedMapOf<TypeParameterElement, LsiSymbolId>()
    for (typeOwner in typeOwners) {
        val ownerId = LsiSymbolId.type(typeOwner.qualifiedName.toString())
        for (parameter in typeOwner.typeParameters) {
            ids[parameter] = LsiSymbolId.typeParameter(ownerId, parameter.simpleName.toString())
        }
    }
    return ids
}

internal fun AptLsiContext.toLsiCallableId(method: ExecutableElement): LsiSymbolId {
    val owner = method.enclosingElement as TypeElement
    val ownerId = LsiSymbolId.type(owner.qualifiedName.toString())
    val parameterSignatures = method.parameters.map { parameter ->
        parameter.asType().toAptStableSignature()
    }
    if (method.kind == ElementKind.CONSTRUCTOR) {
        return LsiSymbolId.constructor(ownerId, parameterSignatures)
    }
    if (method.isLsiPropertyGetter()) {
        return LsiSymbolId.property(ownerId, method.toLsiPropertyName(frontendOptions))
    }
    return LsiSymbolId.function(
        owner = ownerId,
        name = method.simpleName.toString(),
        parameterTypeSignatures = parameterSignatures,
    )
}

internal fun ExecutableElement.isLsiPropertyGetter(): Boolean {
    return parameters.isEmpty() &&
        returnType.kind != TypeKind.VOID &&
        typeParameters.isEmpty() &&
        javax.lang.model.element.Modifier.STATIC !in modifiers
}

internal fun ExecutableElement.toLsiPropertyName(
    frontendOptions: LsiFrontendOptions,
): String {
    val methodName = simpleName.toString()
    if (methodName.startsWith("get") && methodName.length > 3 && methodName[3].isUpperCase()) {
        return java.beans.Introspector.decapitalize(methodName.substring(3))
    }
    if (
        !frontendOptions.keepJavaBooleanGetterIsPrefix &&
        methodName.startsWith("is") &&
        methodName.length > 2 &&
        methodName[2].isUpperCase() &&
        returnType.isBooleanType()
    ) {
        return java.beans.Introspector.decapitalize(methodName.substring(2))
    }
    return methodName
}

internal fun TypeMirror.toAptStableSignature(): String {
    return when (this) {
        is PrimitiveType -> "primitive:${kind.name.lowercase()}"
        is ArrayType -> "array:${componentType.toAptStableSignature()}"
        is DeclaredType -> buildString {
            val typeElement = asElement() as? TypeElement
            append("type:")
            append(typeElement?.qualifiedName ?: toString().substringBefore('<'))
            if (typeArguments.isNotEmpty()) {
                append('<')
                append(typeArguments.joinToString(",") { argument -> argument.toAptTypeArgumentSignature() })
                append('>')
            }
        }
        is TypeVariable -> {
            val parameter = asElement() as? TypeParameterElement
            val index = parameter?.genericElement
                ?.let { owner ->
                    when (owner) {
                        is TypeElement -> owner.typeParameters.indexOf(parameter)
                        is ExecutableElement -> owner.typeParameters.indexOf(parameter)
                        else -> -1
                    }
                }
                ?: -1
            val ownerSignature = when (val owner = parameter?.genericElement) {
                is TypeElement -> "type:${owner.qualifiedName}"
                is ExecutableElement -> "method:${owner.simpleName}"
                else -> "unknown"
            }
            val signature = "parameter:$ownerSignature:$index"
            if (parameter?.genericElement is ExecutableElement) {
                "$signature:${upperBound.toAptErasedStableSignature()}"
            } else {
                signature
            }
        }
        is NoType -> if (kind == TypeKind.VOID) "primitive:void" else "none"
        is WildcardType -> toAptTypeArgumentSignature()
        else -> "${kind.name.lowercase()}:${toString().withoutWhitespace()}"
    }
}

private fun TypeMirror.toAptErasedStableSignature(): String {
    return when (this) {
        is PrimitiveType -> "primitive:${kind.name.lowercase()}"
        is ArrayType -> "array:${componentType.toAptErasedStableSignature()}"
        is DeclaredType -> {
            val typeElement = asElement() as? TypeElement
            "type:${typeElement?.qualifiedName ?: toString().substringBefore('<')}"
        }
        is TypeVariable -> upperBound.toAptErasedStableSignature()
        is IntersectionType -> bounds.firstOrNull()?.toAptErasedStableSignature() ?: "type:java.lang.Object"
        is WildcardType -> {
            extendsBound?.toAptErasedStableSignature()
                ?: superBound?.toAptErasedStableSignature()
                ?: "type:java.lang.Object"
        }
        is NoType -> if (kind == TypeKind.VOID) "primitive:void" else "none"
        else -> "${kind.name.lowercase()}:${toString().withoutWhitespace()}"
    }
}

private fun AptLsiContext.toLsiTypeArgument(
    type: TypeMirror,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeArgument {
    if (type !is WildcardType) {
        return LsiTypeArgument.invariant(toLsiType(type, typeParameterIds))
    }
    val superBound = type.superBound
    if (superBound != null) {
        return LsiTypeArgument.input(toLsiType(superBound, typeParameterIds))
    }
    val extendsBound = type.extendsBound
    if (extendsBound != null) {
        return LsiTypeArgument.output(toLsiType(extendsBound, typeParameterIds))
    }
    return LsiTypeArgument.STAR
}

private fun AptLsiContext.toLsiTypeParameterId(
    parameter: TypeParameterElement,
): LsiSymbolId? {
    val owner = parameter.genericElement
    return when (owner) {
        is TypeElement -> LsiSymbolId.typeParameter(
            owner = LsiSymbolId.type(owner.qualifiedName.toString()),
            name = parameter.simpleName.toString(),
        )
        is ExecutableElement -> LsiSymbolId.typeParameter(
            owner = toLsiCallableId(owner),
            name = parameter.simpleName.toString(),
        )
        else -> null
    }
}

private fun AptLsiContext.isImplicitObjectBound(type: TypeMirror): Boolean {
    if (type !is DeclaredType) {
        return false
    }
    val element = type.asElement() as? TypeElement ?: return false
    return element.qualifiedName.contentEquals("java.lang.Object")
}

private fun AptLsiContext.toLsiDeclaredType(
    type: DeclaredType,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiDeclaredType {
    val typeElement = type.asElement() as TypeElement
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(typeElement.qualifiedName.toString()),
        arguments = type.typeArguments.map { argument ->
            toLsiTypeArgument(argument, typeParameterIds)
        },
        nullability = LsiNullability.PLATFORM,
    )
}

private fun AptLsiContext.toLsiErrorType(
    type: ErrorType,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val errorElement = type.asElement() as? TypeElement
        ?: return LsiUnresolvedType(type.toString())
    val qualifiedName = errorElement.qualifiedName.toString()
    val resolvedElement = qualifiedName
        .takeIf(String::isNotBlank)
        ?.let(elements::getTypeElement)
        ?: return LsiUnresolvedType(type.toString())
    if (resolvedElement.asType().kind == TypeKind.ERROR) {
        return LsiUnresolvedType(type.toString())
    }
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(resolvedElement.qualifiedName.toString()),
        arguments = type.typeArguments.map { argument ->
            toLsiTypeArgument(argument, typeParameterIds)
        },
        nullability = LsiNullability.PLATFORM,
    )
}

private fun AptLsiContext.toLsiTypeParameterRef(
    type: TypeVariable,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val parameter = type.asElement() as? TypeParameterElement
        ?: return LsiUnresolvedType(type.toString())
    val parameterId = typeParameterIds[parameter] ?: toLsiTypeParameterId(parameter)
    return if (parameterId != null) {
        LsiTypeParameterRef(
            parameterId = parameterId,
            nullability = LsiNullability.PLATFORM,
        )
    } else {
        LsiUnresolvedType(type.toString())
    }
}

private fun NoType.toLsiNoType(): LsiTypeRef {
    return if (kind == TypeKind.VOID) {
        LsiPrimitiveType(LsiPrimitiveKind.VOID)
    } else {
        LsiUnresolvedType(toString().ifBlank { kind.name.lowercase() })
    }
}

private fun AptLsiContext.toLsiWildcardFallback(
    type: WildcardType,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val bound = type.superBound ?: type.extendsBound
    return if (bound != null) {
        toLsiType(bound, typeParameterIds)
    } else {
        LsiUnresolvedType(type.toString())
    }
}

private fun TypeKind.toLsiPrimitiveKind(): LsiPrimitiveKind {
    return when (this) {
        TypeKind.BOOLEAN -> LsiPrimitiveKind.BOOLEAN
        TypeKind.BYTE -> LsiPrimitiveKind.BYTE
        TypeKind.SHORT -> LsiPrimitiveKind.SHORT
        TypeKind.INT -> LsiPrimitiveKind.INT
        TypeKind.LONG -> LsiPrimitiveKind.LONG
        TypeKind.CHAR -> LsiPrimitiveKind.CHAR
        TypeKind.FLOAT -> LsiPrimitiveKind.FLOAT
        TypeKind.DOUBLE -> LsiPrimitiveKind.DOUBLE
        else -> error("Unsupported primitive type kind: $this")
    }
}

private fun TypeMirror.isBooleanType(): Boolean {
    if (kind == TypeKind.BOOLEAN) {
        return true
    }
    val declaredType = this as? DeclaredType ?: return false
    val element = declaredType.asElement() as? TypeElement ?: return false
    return element.qualifiedName.contentEquals("java.lang.Boolean")
}

private fun TypeMirror.toAptTypeArgumentSignature(): String {
    if (this !is WildcardType) {
        return toAptStableSignature()
    }
    val superBound = superBound
    if (superBound != null) {
        return "in:${superBound.toAptStableSignature()}"
    }
    val extendsBound = extendsBound
    if (extendsBound != null) {
        return "out:${extendsBound.toAptStableSignature()}"
    }
    return "*"
}

private fun String.withoutWhitespace(): String = filterNot(Char::isWhitespace)
