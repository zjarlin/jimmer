package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSClassifierReference
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
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
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.mergeAnnotations

fun KSTypeReference.toLsiType(
    resolver: Resolver,
): LsiTypeRef {
    return KspLsiTypeContext(resolver).toLsiType(this)
}

fun KSType.toLsiType(
    resolver: Resolver,
): LsiTypeRef {
    return KspLsiTypeContext(resolver).toLsiType(this)
}

internal class KspLsiTypeContext(
    val resolver: Resolver,
) {

    private val annotationContext by lazy(LazyThreadSafetyMode.NONE) {
        KspLsiAnnotationContext(resolver, this)
    }

    fun toLsiType(
        reference: KSTypeReference,
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId> = emptyMap(),
    ): LsiTypeRef {
        val type = reference.resolve()
        val annotations = mergeAnnotations(
            declared = toLsiTypeAnnotations(reference.annotations),
            inherited = toLsiTypeAnnotations(type.annotations),
        )
        return toLsiType(
            type = type,
            typeParameterIds = typeParameterIds,
            annotations = annotations,
            primitiveBoxed = reference.toLsiPrimitiveBoxedHint(),
        )
    }

    fun toLsiType(
        type: KSType,
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId> = emptyMap(),
    ): LsiTypeRef {
        return toLsiType(type, typeParameterIds, toLsiTypeAnnotations(type.annotations))
    }

    private fun toLsiType(
        type: KSType,
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId>,
        annotations: List<LsiAnnotation>,
        primitiveBoxed: Boolean? = null,
    ): LsiTypeRef {
        if (type.isError) {
            return LsiUnresolvedType(
                displayName = type.toString().ifBlank { "<error>" },
                annotations = annotations,
            )
        }
        val declaration = type.declaration
        if (declaration is KSTypeParameter) {
            return toLsiTypeParameterRef(type, declaration, typeParameterIds, annotations)
        }
        val qualifiedName = declaration.qualifiedName?.asString()
            ?: return LsiUnresolvedType(
                displayName = type.toString().ifBlank { declaration.simpleName.asString() },
                annotations = annotations,
            )
        val primitiveKind = qualifiedName.toLsiPrimitiveKind()
        if (primitiveKind != null) {
            return LsiPrimitiveType(
                kind = primitiveKind,
                nullability = type.nullability.toLsiNullability(),
                annotations = annotations,
                boxed = primitiveBoxed ?: (type.nullability != Nullability.NOT_NULL),
            )
        }
        val primitiveArrayKind = qualifiedName.toLsiPrimitiveArrayKind()
        if (primitiveArrayKind != null) {
            return LsiArrayType(
                elementType = LsiPrimitiveType(primitiveArrayKind),
                nullability = type.nullability.toLsiNullability(),
                annotations = annotations,
            )
        }
        if (qualifiedName == "kotlin.Array") {
            val elementType = type.arguments.singleOrNull()?.toLsiTypeArgument(typeParameterIds)?.type
                ?: LsiUnresolvedType("*")
            return LsiArrayType(
                elementType = elementType,
                nullability = type.nullability.toLsiNullability(),
                annotations = annotations,
            )
        }
        return LsiDeclaredType(
            declarationId = LsiSymbolId.type(qualifiedName.toCanonicalLsiTypeName()),
            arguments = type.arguments.map { argument ->
                argument.toLsiTypeArgument(typeParameterIds)
            },
            nullability = type.nullability.toLsiNullability(),
            annotations = annotations,
        )
    }

    fun toLsiTypeParameters(
        ownerId: LsiSymbolId,
        parameters: List<KSTypeParameter>,
        inheritedIds: Map<KSTypeParameter, LsiSymbolId> = emptyMap(),
    ): Pair<List<LsiTypeParameter>, Map<KSTypeParameter, LsiSymbolId>> {
        val ownIds = parameters.associateWith { parameter ->
            LsiSymbolId.typeParameter(ownerId, parameter.name.asString())
        }
        val allIds = inheritedIds + ownIds
        val lsiParameters = parameters.map { parameter ->
            val bounds = parameter.bounds
                .filterNot { bound -> isImplicitAnyBound(bound.resolve()) }
                .map { bound -> toLsiType(bound, allIds) }
                .toList()
            LsiTypeParameter(
                id = requireNotNull(ownIds[parameter]),
                name = parameter.name.asString(),
                variance = parameter.variance.toLsiVariance(),
                upperBounds = bounds,
            )
        }
        return lsiParameters to allIds
    }

    fun typeParameterIdsInScope(
        declaration: KSDeclaration,
    ): Map<KSTypeParameter, LsiSymbolId> {
        val typeOwners = generateSequence(declaration.parentDeclaration) { owner -> owner.parentDeclaration }
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .asReversed()
        val ids = linkedMapOf<KSTypeParameter, LsiSymbolId>()
        for (typeOwner in typeOwners) {
            val qualifiedName = typeOwner.qualifiedName?.asString() ?: continue
            val ownerId = LsiSymbolId.type(qualifiedName)
            for (parameter in typeOwner.typeParameters) {
                ids[parameter] = LsiSymbolId.typeParameter(ownerId, parameter.name.asString())
            }
        }
        return ids
    }

    fun toLsiCallableId(function: KSFunctionDeclaration): LsiSymbolId {
        val owner = function.parentDeclaration as? KSClassDeclaration
            ?: error("KSP LSI callable must be declared by a class: ${function.simpleName.asString()}")
        val ownerName = requireNotNull(owner.qualifiedName?.asString()) {
            "KSP LSI callable owner must have a qualified name"
        }
        val ownerId = LsiSymbolId.type(ownerName)
        val parameterTypeSignatures = buildList {
            function.extensionReceiver?.resolve()?.let { receiverType ->
                add(receiverType.toKspCallableStableSignature())
            }
            function.parameters.mapTo(this) { parameter ->
                val signature = parameter.type.resolve().toKspCallableStableSignature()
                if (parameter.isVararg) "array:$signature" else signature
            }
        }
        return if (function.isConstructor()) {
            LsiSymbolId.constructor(ownerId, parameterTypeSignatures)
        } else {
            LsiSymbolId.function(
                owner = ownerId,
                name = function.simpleName.asString(),
                parameterTypeSignatures = parameterTypeSignatures,
            )
        }
    }

    fun toLsiDeclarationId(
        function: KSFunctionDeclaration,
        frontendOptions: LsiFrontendOptions,
    ): LsiSymbolId {
        if (!function.isLsiJavaPropertyGetter()) {
            return toLsiCallableId(function)
        }
        val owner = function.parentDeclaration as? KSClassDeclaration
            ?: error("KSP LSI property getter must be declared by a class: ${function.simpleName.asString()}")
        val ownerName = requireNotNull(owner.qualifiedName?.asString()) {
            "KSP LSI property getter owner must have a qualified name"
        }
        return LsiSymbolId.property(
            owner = LsiSymbolId.type(ownerName),
            name = function.toLsiJavaPropertyName(frontendOptions),
        )
    }

    fun substitute(
        type: KSType,
        substitutions: Map<KSTypeParameter, KSTypeArgument>,
    ): KSType {
        val parameter = type.declaration as? KSTypeParameter
        if (parameter != null) {
            val replacement = substitutions[parameter]?.type?.resolve() ?: return type
            return if (type.nullability == Nullability.NULLABLE) {
                replacement.makeNullable()
            } else {
                replacement
            }
        }
        if (type.arguments.isEmpty()) {
            return type
        }
        val replacedArguments = type.arguments.map { argument ->
            val reference = argument.type ?: return@map argument
            val replacedType = substitute(reference.resolve(), substitutions)
            resolver.getTypeArgument(
                resolver.createKSTypeReferenceFromKSType(replacedType),
                argument.variance,
            )
        }
        return type.replace(replacedArguments)
    }

    private fun KSTypeArgument.toLsiTypeArgument(
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId>,
    ): LsiTypeArgument {
        if (variance == Variance.STAR || type == null) {
            return LsiTypeArgument.STAR
        }
        val reference = requireNotNull(type)
        val argumentAnnotations = toLsiTypeAnnotations(annotations)
        val lsiType = toLsiType(reference, typeParameterIds)
            .withAdditionalAnnotations(argumentAnnotations)
        return when (variance) {
            Variance.INVARIANT -> LsiTypeArgument.invariant(lsiType)
            Variance.COVARIANT -> LsiTypeArgument.output(lsiType)
            Variance.CONTRAVARIANT -> LsiTypeArgument.input(lsiType)
            Variance.STAR -> LsiTypeArgument.STAR
        }
    }

    private fun toLsiTypeParameterRef(
        type: KSType,
        parameter: KSTypeParameter,
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId>,
        annotations: List<LsiAnnotation>,
    ): LsiTypeRef {
        val parameterId = typeParameterIds[parameter] ?: parameter.toLsiTypeParameterId()
        return if (parameterId != null) {
            LsiTypeParameterRef(
                parameterId = parameterId,
                nullability = type.nullability.toLsiNullability(),
                annotations = annotations,
            )
        } else {
            LsiUnresolvedType(
                displayName = type.toString().ifBlank { parameter.name.asString() },
                annotations = annotations,
            )
        }
    }

    private fun KSTypeParameter.toLsiTypeParameterId(): LsiSymbolId? {
        val owner = parentDeclaration
        return when (owner) {
            is KSClassDeclaration -> owner.qualifiedName?.asString()?.let { qualifiedName ->
                LsiSymbolId.typeParameter(
                    owner = LsiSymbolId.type(qualifiedName),
                    name = name.asString(),
                )
            }
            is KSFunctionDeclaration -> LsiSymbolId.typeParameter(
                owner = toLsiCallableId(owner),
                name = name.asString(),
            )
            else -> null
        }
    }

    private fun isImplicitAnyBound(type: KSType): Boolean {
        return type.declaration.qualifiedName?.asString() in IMPLICIT_ANY_NAMES
    }

    private fun toLsiTypeAnnotations(
        annotations: Sequence<KSAnnotation>,
    ): List<LsiAnnotation> {
        return annotationContext.toLsiAnnotations(annotations, null)
    }

    private fun KSTypeReference.toLsiPrimitiveBoxedHint(): Boolean? {
        if (origin != Origin.JAVA && origin != Origin.JAVA_LIB) {
            return null
        }
        val referencedName = (element as? KSClassifierReference)
            ?.referencedName()
            ?.substringAfterLast('.')
            ?: return null
        return when (referencedName) {
            "boolean", "byte", "short", "int", "long", "char", "float", "double", "void" -> false
            "Boolean", "Byte", "Short", "Integer", "Long", "Character", "Float", "Double", "Void" -> true
            else -> null
        }
    }
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
        is LsiTypeParameterRef -> copy(annotations = mergedAnnotations)
        is LsiPrimitiveType -> copy(annotations = mergedAnnotations)
        is LsiArrayType -> copy(annotations = mergedAnnotations)
        is LsiUnresolvedType -> copy(annotations = mergedAnnotations)
    }
}

internal fun KSFunctionDeclaration.isLsiJavaPropertyGetter(): Boolean {
    val owner = parentDeclaration as? KSClassDeclaration ?: return false
    if (owner.origin != Origin.JAVA && owner.origin != Origin.JAVA_LIB) {
        return false
    }
    if (
        isConstructor() ||
        parameters.isNotEmpty() ||
        typeParameters.isNotEmpty() ||
        functionKind == FunctionKind.STATIC ||
        Modifier.JAVA_STATIC in modifiers
    ) {
        return false
    }
    val resolvedReturnType = returnType?.resolve() ?: return false
    if (resolvedReturnType.declaration.qualifiedName?.asString() == "kotlin.Unit") {
        return false
    }
    val methodName = simpleName.asString()
    if (
        methodName.startsWith("get") &&
        methodName.length > 3 &&
        methodName[3].isUpperCase()
    ) {
        val booleanGetterName = "is" + methodName.substring(3)
        if (owner.getDeclaredFunctions().any { method ->
                method.simpleName.asString() == booleanGetterName &&
                    method.parameters.isEmpty() &&
                    method.typeParameters.isEmpty() &&
                    method.returnType?.resolve()?.let { returnType ->
                        !returnType.isError && returnType.isLsiBooleanType()
                    } == true
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
        resolvedReturnType.isLsiBooleanType()
    ) {
        return true
    }
    if (Modifier.PRIVATE in modifiers) {
        return false
    }
    return owner.classKind == com.google.devtools.ksp.symbol.ClassKind.INTERFACE ||
        owner.classKind == com.google.devtools.ksp.symbol.ClassKind.ANNOTATION_CLASS ||
        owner.isLsiJavaRecord()
}

private fun KSClassDeclaration.isLsiJavaRecord(): Boolean {
    return superTypes.any { superType ->
        val resolvedType = superType.resolve()
        !resolvedType.isError &&
            resolvedType.declaration.qualifiedName?.asString() == "java.lang.Record"
    }
}

internal fun KSFunctionDeclaration.toLsiJavaPropertyName(
    frontendOptions: LsiFrontendOptions,
): String {
    val methodName = simpleName.asString()
    if (methodName.startsWith("get") && methodName.length > 3 && methodName[3].isUpperCase()) {
        return java.beans.Introspector.decapitalize(methodName.substring(3))
    }
    if (
        !frontendOptions.keepJavaBooleanGetterIsPrefix &&
        methodName.startsWith("is") &&
        methodName.length > 2 &&
        methodName[2].isUpperCase() &&
        returnType?.resolve()?.isLsiBooleanType() == true
    ) {
        return java.beans.Introspector.decapitalize(methodName.substring(2))
    }
    return methodName
}

private fun KSType.isLsiBooleanType(): Boolean {
    return declaration.qualifiedName?.asString() in LSI_BOOLEAN_TYPE_NAMES
}

private fun KSType.toKspCallableStableSignature(): String {
    if (isError) {
        return "unresolved:${toString().withoutWhitespace()}"
    }
    val declaration = declaration
    if (declaration is KSTypeParameter) {
        val owner = declaration.parentDeclaration
        val parameterIndex = owner?.typeParameters?.indexOf(declaration) ?: -1
        val ownerSignature = when (owner) {
            is KSClassDeclaration -> "type:${owner.qualifiedName?.asString().orEmpty()}"
            is KSFunctionDeclaration -> "method:${owner.simpleName.asString()}"
            else -> "unknown"
        }
        val signature = "parameter:$ownerSignature:$parameterIndex"
        if (owner !is KSFunctionDeclaration) {
            return signature
        }
        val erasedBound = declaration.bounds
            .map(KSTypeReference::resolve)
            .firstOrNull()
            ?.toKspErasedStableSignature()
            ?: "type:java.lang.Object"
        return "$signature:$erasedBound"
    }
    val qualifiedName = declaration.qualifiedName?.asString()
        ?: return "unresolved:${toString().withoutWhitespace()}"
    val primitiveKind = qualifiedName.toLsiPrimitiveKind()
    if (primitiveKind != null) {
        return "primitive:${primitiveKind.name.lowercase()}"
    }
    val primitiveArrayKind = qualifiedName.toLsiPrimitiveArrayKind()
    if (primitiveArrayKind != null) {
        return "array:primitive:${primitiveArrayKind.name.lowercase()}"
    }
    if (qualifiedName == "kotlin.Array") {
        val elementSignature = arguments.singleOrNull()
            ?.toKspCallableStableSignature()
            ?: "*"
        return "array:$elementSignature"
    }
    return buildString {
        append("type:")
        append(qualifiedName.toCanonicalLsiTypeName())
        if (arguments.isNotEmpty()) {
            append('<')
            append(arguments.joinToString(",") { argument -> argument.toKspCallableStableSignature() })
            append('>')
        }
    }
}

private fun KSTypeArgument.toKspCallableStableSignature(): String {
    if (variance == Variance.STAR || type == null) {
        return "*"
    }
    val signature = requireNotNull(type).resolve().toKspCallableStableSignature()
    return when (variance) {
        Variance.INVARIANT -> signature
        Variance.COVARIANT -> "out:$signature"
        Variance.CONTRAVARIANT -> "in:$signature"
        Variance.STAR -> "*"
    }
}

private fun KSType.toKspErasedStableSignature(): String {
    if (isError) {
        return "unresolved:${toString().withoutWhitespace()}"
    }
    val declaration = declaration
    if (declaration is KSTypeParameter) {
        return declaration.bounds
            .map(KSTypeReference::resolve)
            .firstOrNull()
            ?.toKspErasedStableSignature()
            ?: "type:java.lang.Object"
    }
    val qualifiedName = declaration.qualifiedName?.asString()
        ?: return "unresolved:${toString().withoutWhitespace()}"
    val primitiveKind = qualifiedName.toLsiPrimitiveKind()
    if (primitiveKind != null) {
        return "primitive:${primitiveKind.name.lowercase()}"
    }
    val primitiveArrayKind = qualifiedName.toLsiPrimitiveArrayKind()
    if (primitiveArrayKind != null) {
        return "array:primitive:${primitiveArrayKind.name.lowercase()}"
    }
    if (qualifiedName == "kotlin.Array") {
        val elementSignature = arguments.singleOrNull()
            ?.type
            ?.resolve()
            ?.toKspErasedStableSignature()
            ?: "type:java.lang.Object"
        return "array:$elementSignature"
    }
    return "type:${qualifiedName.toCanonicalLsiTypeName()}"
}

internal fun KSType.toKspStableSignature(): String {
    if (isError) {
        return "unresolved:${toString().withoutWhitespace()}"
    }
    val declaration = declaration
    if (declaration is KSTypeParameter) {
        val owner = declaration.parentDeclaration
        val parameterIndex = owner?.typeParameters?.indexOf(declaration) ?: -1
        val ownerSignature = when (owner) {
            is KSClassDeclaration -> "type:${owner.qualifiedName?.asString().orEmpty()}"
            is KSFunctionDeclaration -> "method:${owner.simpleName.asString()}"
            else -> "unknown"
        }
        return "parameter:$ownerSignature:$parameterIndex"
    }
    val qualifiedName = declaration.qualifiedName?.asString()
        ?: return "unresolved:${toString().withoutWhitespace()}"
    val primitiveKind = qualifiedName.toLsiPrimitiveKind()
    if (primitiveKind != null) {
        return "primitive:${primitiveKind.name.lowercase()}"
    }
    val primitiveArrayKind = qualifiedName.toLsiPrimitiveArrayKind()
    if (primitiveArrayKind != null) {
        return "array:primitive:${primitiveArrayKind.name.lowercase()}"
    }
    if (qualifiedName == "kotlin.Array") {
        val elementSignature = arguments.singleOrNull()?.toKspStableSignature() ?: "*"
        return "array:$elementSignature"
    }
    return buildString {
        append("type:")
        append(qualifiedName.toCanonicalLsiTypeName())
        if (arguments.isNotEmpty()) {
            append('<')
            append(arguments.joinToString(",") { argument -> argument.toKspStableSignature() })
            append('>')
        }
    }
}

private fun KSTypeArgument.toKspStableSignature(): String {
    if (variance == Variance.STAR || type == null) {
        return "*"
    }
    val signature = requireNotNull(type).resolve().toKspStableSignature()
    return when (variance) {
        Variance.INVARIANT -> signature
        Variance.COVARIANT -> "out:$signature"
        Variance.CONTRAVARIANT -> "in:$signature"
        Variance.STAR -> "*"
    }
}

private fun Nullability.toLsiNullability(): LsiNullability {
    return when (this) {
        Nullability.NULLABLE -> LsiNullability.NULLABLE
        Nullability.NOT_NULL -> LsiNullability.NON_NULL
        Nullability.PLATFORM -> LsiNullability.PLATFORM
    }
}

private fun Variance.toLsiVariance(): LsiVariance {
    return when (this) {
        Variance.INVARIANT -> LsiVariance.INVARIANT
        Variance.COVARIANT -> LsiVariance.OUT
        Variance.CONTRAVARIANT -> LsiVariance.IN
        Variance.STAR -> error("KSP type parameter declaration cannot use star variance")
    }
}

private fun String.toLsiPrimitiveKind(): LsiPrimitiveKind? = PRIMITIVE_TYPES[this]

private fun String.toLsiPrimitiveArrayKind(): LsiPrimitiveKind? = PRIMITIVE_ARRAY_TYPES[this]

private fun String.toCanonicalLsiTypeName(): String = KOTLIN_JVM_TYPE_NAMES[this] ?: this

private fun String.withoutWhitespace(): String = filterNot(Char::isWhitespace)

private val IMPLICIT_ANY_NAMES = setOf("kotlin.Any", "java.lang.Object")

private val LSI_BOOLEAN_TYPE_NAMES = setOf("kotlin.Boolean", "java.lang.Boolean")

private val PRIMITIVE_TYPES = mapOf(
    "kotlin.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "kotlin.Byte" to LsiPrimitiveKind.BYTE,
    "kotlin.Short" to LsiPrimitiveKind.SHORT,
    "kotlin.Int" to LsiPrimitiveKind.INT,
    "kotlin.Long" to LsiPrimitiveKind.LONG,
    "kotlin.Char" to LsiPrimitiveKind.CHAR,
    "kotlin.Float" to LsiPrimitiveKind.FLOAT,
    "kotlin.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Unit" to LsiPrimitiveKind.UNIT,
    "java.lang.Void" to LsiPrimitiveKind.VOID,
)

private val PRIMITIVE_ARRAY_TYPES = mapOf(
    "kotlin.BooleanArray" to LsiPrimitiveKind.BOOLEAN,
    "kotlin.ByteArray" to LsiPrimitiveKind.BYTE,
    "kotlin.ShortArray" to LsiPrimitiveKind.SHORT,
    "kotlin.IntArray" to LsiPrimitiveKind.INT,
    "kotlin.LongArray" to LsiPrimitiveKind.LONG,
    "kotlin.CharArray" to LsiPrimitiveKind.CHAR,
    "kotlin.FloatArray" to LsiPrimitiveKind.FLOAT,
    "kotlin.DoubleArray" to LsiPrimitiveKind.DOUBLE,
)

private val KOTLIN_JVM_TYPE_NAMES = mapOf(
    "kotlin.Any" to "java.lang.Object",
    "kotlin.String" to "java.lang.String",
    "kotlin.CharSequence" to "java.lang.CharSequence",
    "kotlin.Number" to "java.lang.Number",
    "kotlin.Throwable" to "java.lang.Throwable",
    "kotlin.Comparable" to "java.lang.Comparable",
    "kotlin.Enum" to "java.lang.Enum",
    "kotlin.Annotation" to "java.lang.annotation.Annotation",
    "kotlin.collections.Iterable" to "java.lang.Iterable",
    "kotlin.collections.Collection" to "java.util.Collection",
    "kotlin.collections.MutableCollection" to "java.util.Collection",
    "kotlin.collections.List" to "java.util.List",
    "kotlin.collections.MutableList" to "java.util.List",
    "kotlin.collections.Set" to "java.util.Set",
    "kotlin.collections.MutableSet" to "java.util.Set",
    "kotlin.collections.Map" to "java.util.Map",
    "kotlin.collections.MutableMap" to "java.util.Map",
)
