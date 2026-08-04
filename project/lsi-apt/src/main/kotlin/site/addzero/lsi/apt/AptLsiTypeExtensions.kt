package site.addzero.lsi.apt

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiJvmTypeParameterDescriptor
import site.addzero.lsi.model.LsiJvmTypeParameterOwner
import site.addzero.lsi.model.LsiJvmTypeSignatureContext
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.mergeAnnotations
import site.addzero.lsi.model.toJvmCallableParameterType
import site.addzero.lsi.model.toJvmReferenceType
import site.addzero.lsi.model.toJvmTypeSignature
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ErrorType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType

fun TypeMirror.toLsiType(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions = LsiFrontendOptions(),
): LsiTypeRef {
    return AptLsiContext(
        processingEnvironment,
        frontendOptions,
    ).toLsiType(this)
}

internal fun AptLsiContext.toLsiType(
    type: TypeMirror,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId> = emptyMap(),
): LsiTypeRef {
    return when (type) {
        is ErrorType -> toLsiErrorType(type, typeParameterIds)
        is PrimitiveType -> LsiPrimitiveType(
            kind = type.kind.toLsiPrimitiveKind(),
            nullability = type.toLsiNullability(LsiNullability.NON_NULL, frontendOptions.nullableAnnotationTypeIds),
            annotations = toLsiTypeAnnotations(type),
        )
        is ArrayType -> LsiArrayType(
            elementType = toLsiType(type.componentType, typeParameterIds),
            nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
            annotations = toLsiTypeAnnotations(type),
        )
        is DeclaredType -> toLsiDeclaredType(type, typeParameterIds)
        is TypeVariable -> toLsiTypeParameterRef(type, typeParameterIds)
        is NoType -> toLsiNoType(type)
        is WildcardType -> toLsiWildcardFallback(type, typeParameterIds)
        else -> LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
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
            .map { bound -> toLsiType(bound, allIds).toJvmReferenceType() }
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
    val provisionalCallableId = if (method.kind == ElementKind.CONSTRUCTOR) {
        LsiSymbolId.constructor(ownerId)
    } else {
        LsiSymbolId.function(ownerId, method.simpleName.toString())
    }
    val inheritedTypeParameterIds = typeParameterIdsInScope(method)
    val ownTypeParameterIds = method.typeParameters.associateWith { parameter ->
        LsiSymbolId.typeParameter(provisionalCallableId, parameter.simpleName.toString())
    }
    val typeParameterIds = inheritedTypeParameterIds + ownTypeParameterIds
    val signatureContext = toJvmTypeSignatureContext(typeParameterIds)
    val parameterSignatures = method.parameters.map { parameter ->
        toLsiType(parameter.asType(), typeParameterIds)
            .toJvmCallableParameterType()
            .toJvmTypeSignature(context = signatureContext)
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

private fun AptLsiContext.toJvmTypeSignatureContext(
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiJvmTypeSignatureContext {
    val descriptors = typeParameterIds.map { (parameter, id) ->
        val owner = parameter.genericElement
        val parameterOwner = when (owner) {
            is TypeElement -> LsiJvmTypeParameterOwner.Type(
                LsiSymbolId.type(owner.qualifiedName.toString())
            )
            is ExecutableElement -> LsiJvmTypeParameterOwner.Method(owner.simpleName.toString())
            else -> error("Unsupported APT JVM type parameter owner: $owner")
        }
        val index = when (owner) {
            is TypeElement -> owner.typeParameters.indexOf(parameter)
            is ExecutableElement -> owner.typeParameters.indexOf(parameter)
            else -> error("Unsupported APT JVM type parameter owner: $owner")
        }
        id to LsiJvmTypeParameterDescriptor(
            id = id,
            owner = parameterOwner,
            index = index,
            upperBounds = parameter.bounds
                .map { bound -> toLsiType(bound, typeParameterIds).toJvmReferenceType() },
        )
    }.toMap()
    return LsiJvmTypeSignatureContext(typeParameters = descriptors)
}

internal fun ExecutableElement.isLsiPropertyGetter(): Boolean {
    if (
        parameters.isNotEmpty() ||
        returnType.kind == TypeKind.VOID ||
        typeParameters.isNotEmpty() ||
        thrownTypes.isNotEmpty() ||
        javax.lang.model.element.Modifier.STATIC in modifiers
    ) {
        return false
    }
    val owner = enclosingElement as? TypeElement ?: return false
    val methodName = simpleName.toString()
    if (
        methodName.startsWith("get") &&
        methodName.length > 3 &&
        methodName[3].isUpperCase()
    ) {
        val booleanGetterName = "is" + methodName.substring(3)
        if (owner.enclosedElements
                .filterIsInstance<ExecutableElement>()
                .any { method ->
                    method.simpleName.contentEquals(booleanGetterName) &&
                        method.parameters.isEmpty() &&
                        method.typeParameters.isEmpty() &&
                        method.returnType.isBooleanType()
                }
        ) {
            return false
        }
        return true
    }
    if (
        methodName.startsWith("is") &&
        methodName.length > 2 &&
        methodName[2].isUpperCase() &&
        returnType.isBooleanType()
    ) {
        return true
    }
    if (javax.lang.model.element.Modifier.PRIVATE in modifiers) {
        return false
    }
    return owner.kind == ElementKind.INTERFACE ||
        owner.kind == ElementKind.ANNOTATION_TYPE ||
        owner.kind == ElementKind.RECORD
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

private fun AptLsiContext.toLsiTypeArgument(
    type: TypeMirror,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeArgument {
    if (type !is WildcardType) {
        return LsiTypeArgument.invariant(toLsiType(type, typeParameterIds))
    }
    val annotations = toLsiTypeAnnotations(type)
    val superBound = type.superBound
    if (superBound != null) {
        val boundType = toLsiType(superBound, typeParameterIds)
            .withAdditionalAnnotations(annotations)
        return LsiTypeArgument.input(boundType)
    }
    val extendsBound = type.extendsBound
    if (extendsBound != null) {
        val boundType = toLsiType(extendsBound, typeParameterIds)
            .withAdditionalAnnotations(annotations)
        return LsiTypeArgument.output(boundType)
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
): LsiTypeRef {
    val typeElement = type.asElement() as TypeElement
    val qualifiedName = typeElement.qualifiedName.toString()
    APT_BOXED_PRIMITIVE_KINDS[qualifiedName]?.let { primitiveKind ->
        return LsiPrimitiveType(
            kind = primitiveKind,
            nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
            annotations = toLsiTypeAnnotations(type),
            boxed = true,
        )
    }
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(qualifiedName),
        arguments = type.typeArguments.map { argument ->
            toLsiTypeArgument(argument, typeParameterIds)
        },
        nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
        annotations = toLsiTypeAnnotations(type),
    )
}

private fun AptLsiContext.toLsiErrorType(
    type: ErrorType,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val errorElement = type.asElement() as? TypeElement
        ?: return LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
    val qualifiedName = errorElement.qualifiedName.toString()
    val resolvedElement = qualifiedName
        .takeIf(String::isNotBlank)
        ?.let(elements::getTypeElement)
        ?: return LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
    if (resolvedElement.asType().kind == TypeKind.ERROR) {
        return LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
    }
    APT_BOXED_PRIMITIVE_KINDS[resolvedElement.qualifiedName.toString()]?.let { primitiveKind ->
        return LsiPrimitiveType(
            kind = primitiveKind,
            nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
            annotations = toLsiTypeAnnotations(type),
            boxed = true,
        )
    }
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(resolvedElement.qualifiedName.toString()),
        arguments = type.typeArguments.map { argument ->
            toLsiTypeArgument(argument, typeParameterIds)
        },
        nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
        annotations = toLsiTypeAnnotations(type),
    )
}

private fun AptLsiContext.toLsiTypeParameterRef(
    type: TypeVariable,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val parameter = type.asElement() as? TypeParameterElement
        ?: return LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
    val parameterId = typeParameterIds[parameter] ?: toLsiTypeParameterId(parameter)
    return if (parameterId != null) {
        LsiTypeParameterRef(
            parameterId = parameterId,
            nullability = type.toLsiNullability(LsiNullability.PLATFORM, frontendOptions.nullableAnnotationTypeIds),
            annotations = toLsiTypeAnnotations(type),
        )
    } else {
        LsiUnresolvedType(
            displayName = type.toString(),
            annotations = toLsiTypeAnnotations(type),
        )
    }
}

private fun AptLsiContext.toLsiNoType(type: NoType): LsiTypeRef {
    return if (type.kind == TypeKind.VOID) {
        LsiPrimitiveType(
            kind = LsiPrimitiveKind.VOID,
            annotations = toLsiTypeAnnotations(type),
        )
    } else {
        LsiUnresolvedType(
            displayName = type.toString().ifBlank { type.kind.name.lowercase() },
            annotations = toLsiTypeAnnotations(type),
        )
    }
}

private fun AptLsiContext.toLsiWildcardFallback(
    type: WildcardType,
    typeParameterIds: Map<TypeParameterElement, LsiSymbolId>,
): LsiTypeRef {
    val bound = type.superBound ?: type.extendsBound
    val annotations = toLsiTypeAnnotations(type)
    return if (bound != null) {
        toLsiType(bound, typeParameterIds).withAdditionalAnnotations(annotations)
    } else {
        LsiUnresolvedType(
            displayName = type.toString(),
            annotations = annotations,
        )
    }
}

private fun AptLsiContext.toLsiTypeAnnotations(type: TypeMirror): List<LsiAnnotation> {
    return toLsiAnnotations(type.annotationMirrors, null)
}

private fun LsiTypeRef.withAdditionalAnnotations(
    additionalAnnotations: List<LsiAnnotation>,
): LsiTypeRef {
    if (additionalAnnotations.isEmpty()) {
        return this
    }
    val mergedAnnotations = mergeAnnotations(additionalAnnotations, annotations)
    return when (this) {
        is LsiDeclaredType -> copy(annotations = mergedAnnotations)
        is LsiFunctionType -> copy(annotations = mergedAnnotations)
        is LsiTypeParameterRef -> copy(annotations = mergedAnnotations)
        is LsiPrimitiveType -> copy(annotations = mergedAnnotations)
        is LsiArrayType -> copy(annotations = mergedAnnotations)
        is LsiUnresolvedType -> copy(annotations = mergedAnnotations)
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

private fun TypeMirror.toLsiNullability(
    default: LsiNullability,
    nullableAnnotationTypeIds: Set<LsiSymbolId>,
): LsiNullability {
    var nullable = false
    var nonNull = false
    annotationMirrors.forEach { annotation ->
        val annotationType = annotation.annotationType.asElement() as? TypeElement ?: return@forEach
        val annotationName = annotationType.qualifiedName.toString()
        when (annotationName.annotationNullability(nullableAnnotationTypeIds)) {
            true -> nullable = true
            false -> nonNull = true
            null -> Unit
        }
    }
    return when {
        nullable -> LsiNullability.NULLABLE
        nonNull -> LsiNullability.NON_NULL
        else -> default
    }
}

private fun String.annotationNullability(nullableAnnotationTypeIds: Set<LsiSymbolId>): Boolean? {
    if (LsiSymbolId.type(this) in nullableAnnotationTypeIds) {
        return true
    }
    if (this in TYPE_NULLABILITY_IGNORED_ANNOTATIONS) {
        return null
    }
    return when {
        endsWith(".Null") || endsWith(".Nullable") -> true
        endsWith(".NotNull") || endsWith(".NonNull") -> false
        else -> null
    }
}

private val TYPE_NULLABILITY_IGNORED_ANNOTATIONS = setOf(
    "jakarta.validation.constraints.NotNull",
    "javax.validation.constraints.NotNull",
)

private val APT_BOXED_PRIMITIVE_KINDS = mapOf(
    "java.lang.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "java.lang.Byte" to LsiPrimitiveKind.BYTE,
    "java.lang.Short" to LsiPrimitiveKind.SHORT,
    "java.lang.Integer" to LsiPrimitiveKind.INT,
    "java.lang.Long" to LsiPrimitiveKind.LONG,
    "java.lang.Character" to LsiPrimitiveKind.CHAR,
    "java.lang.Float" to LsiPrimitiveKind.FLOAT,
    "java.lang.Double" to LsiPrimitiveKind.DOUBLE,
    "java.lang.Void" to LsiPrimitiveKind.VOID,
    "kotlin.Unit" to LsiPrimitiveKind.UNIT,
)
