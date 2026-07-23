package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

data class LsiResolvedProperty(
    val ownerId: LsiSymbolId,
    val declaration: LsiProperty,
    val type: LsiTypeRef,
    val annotations: List<LsiAnnotation>,
    val overrideChain: List<LsiProperty>,
    val inheritanceDistance: Int,
) {

    init {
        require(overrideChain.isNotEmpty()) { "Resolved LSI property override chain cannot be empty" }
        require(overrideChain.first().id == declaration.id) {
            "Resolved LSI property override chain must start with its declaration: ${declaration.id.value}"
        }
        require(inheritanceDistance >= 0) {
            "Resolved LSI property inheritance distance cannot be negative: $inheritanceDistance"
        }
    }
}

class LsiInheritedPropertyConflictException(
    val ownerId: LsiSymbolId,
    val propertyName: String,
    val conflictingPropertyIds: List<LsiSymbolId>,
) : IllegalArgumentException(
    "Type '${ownerId.value}' inherits conflicting property '$propertyName' from " +
        conflictingPropertyIds.joinToString { id -> "'${id.value}'" },
)

/**
 * 在冻结后的声明上完成泛型替换、继承遍历和有效属性合并。
 * fallback 层级仅在当前 workspace 缺少对应类型时参与解析，供平台扩展补充内建类型关系。
 */
class LsiTypeSystem(
    private val workspace: LsiWorkspace,
    fallbackTypeHierarchy: Collection<LsiTypeHierarchyEntry> = emptyList(),
) {

    private val fallbackTypeHierarchyById: Map<LsiSymbolId, LsiTypeHierarchyEntry>

    init {
        val duplicateFallbackIds = fallbackTypeHierarchy
            .groupingBy(LsiTypeHierarchyEntry::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateFallbackIds.isEmpty()) {
            "Duplicate fallback LSI type hierarchy ids: " +
                duplicateFallbackIds.joinToString { id -> id.value }
        }
        fallbackTypeHierarchyById = fallbackTypeHierarchy.associateBy(LsiTypeHierarchyEntry::id)
    }

    fun substitute(
        type: LsiTypeRef,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
    ): LsiTypeRef {
        return when (type) {
            is LsiDeclaredType -> type.copy(
                arguments = type.arguments.map { argument -> substitute(argument, substitutions) },
            )
            is LsiTypeParameterRef -> {
                val replacement = substitutions[type.parameterId] ?: return type
                val replacementType = replacement.type ?: return LsiUnresolvedType(
                    displayName = "star projection of ${type.parameterId.value}",
                    nullability = type.nullability,
                    annotations = type.annotations,
                )
                if (replacement.variance != LsiVariance.INVARIANT) {
                    return LsiUnresolvedType(
                        displayName = "${replacement.variance.name.lowercase()} projection of " +
                            replacementType.stableSignature(),
                        nullability = type.nullability,
                        annotations = type.annotations,
                    )
                }
                replacementType.withUseSiteMetadata(type.nullability, type.annotations)
            }
            is LsiArrayType -> type.copy(
                elementType = substitute(type.elementType, substitutions),
            )
            is LsiFunctionType -> type.copy(
                returnType = substitute(type.returnType, substitutions),
                receiverType = type.receiverType?.let { receiver -> substitute(receiver, substitutions) },
                parameterTypes = type.parameterTypes.map { parameter -> substitute(parameter, substitutions) },
            )
            is LsiPrimitiveType,
            is LsiUnresolvedType,
            -> type
        }
    }

    fun resolveSuperType(
        typeId: LsiSymbolId,
        superTypeId: LsiSymbolId,
    ): LsiDeclaredType? {
        val type = typeHierarchyEntry(typeId)?.selfType() ?: return null
        return resolveSuperType(type, superTypeId)
    }

    /** 从带实参的声明类型解析目标父类型，并在整条继承链上保留泛型替换。 */
    fun resolveSuperType(
        type: LsiDeclaredType,
        superTypeId: LsiSymbolId,
    ): LsiDeclaredType? {
        val hierarchyEntry = typeHierarchyEntry(type.declarationId) ?: return null
        val normalizedType = hierarchyEntry.normalizeArguments(type)
        if (normalizedType.declarationId == superTypeId) {
            return normalizedType
        }
        val substitutions = hierarchyEntry.substitutionsFrom(normalizedType)
        val pending = ArrayDeque<LsiDeclaredType>()
        hierarchyEntry.directSuperTypes
            .mapTo(pending) { superType ->
                (substitute(superType, substitutions) as LsiDeclaredType).copy(
                    nullability = normalizedType.nullability,
                )
            }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val currentHierarchyEntry = typeHierarchyEntry(current.declarationId)
            val normalizedCurrent = currentHierarchyEntry?.normalizeArguments(current) ?: current
            if (!visited.add(normalizedCurrent.stableSignature())) {
                continue
            }
            if (normalizedCurrent.declarationId == superTypeId) {
                return normalizedCurrent
            }
            val hierarchyEntry = currentHierarchyEntry ?: continue
            val substitutions = hierarchyEntry.substitutionsFrom(normalizedCurrent)
            hierarchyEntry.directSuperTypes
                .mapTo(pending) { inheritedType ->
                    (substitute(inheritedType, substitutions) as LsiDeclaredType).copy(
                        nullability = normalizedCurrent.nullability,
                    )
                }
        }
        return null
    }

    /** 判断 source 类型的值能否赋给 target 类型，不依赖 javac 或 KSP 类型对象。 */
    fun isAssignable(
        source: LsiTypeRef,
        target: LsiTypeRef,
    ): Boolean {
        return isAssignable(source, target, mutableSetOf())
    }

    fun effectiveProperties(typeId: LsiSymbolId): List<LsiResolvedProperty> {
        val type = workspace[typeId] as? LsiTypeDeclaration
            ?: throw IllegalArgumentException("No LSI type declaration '${typeId.value}'")
        return resolveProperties(
            type = type,
            substitutions = type.identitySubstitutions(),
            ownerId = typeId,
            distance = 0,
            visiting = linkedSetOf(),
        ).values.sortedBy { property -> property.declaration.id }
    }

    private fun resolveProperties(
        type: LsiTypeDeclaration,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
        ownerId: LsiSymbolId,
        distance: Int,
        visiting: MutableSet<LsiSymbolId>,
    ): Map<String, LsiResolvedProperty> {
        check(visiting.add(type.id)) { "Cyclic LSI type hierarchy at '${type.id.value}'" }
        try {
            val inheritedByName = linkedMapOf<String, MutableList<LsiResolvedProperty>>()
            for (superType in type.superTypes.filterIsInstance<LsiDeclaredType>()) {
                val resolvedSuperType = substitute(superType, substitutions) as LsiDeclaredType
                val superDeclaration = workspace[resolvedSuperType.declarationId] as? LsiTypeDeclaration ?: continue
                val superProperties = resolveProperties(
                    type = superDeclaration,
                    substitutions = superDeclaration.substitutionsFrom(resolvedSuperType),
                    ownerId = ownerId,
                    distance = distance + 1,
                    visiting = visiting,
                )
                for ((name, property) in superProperties) {
                    inheritedByName.getOrPut(name, ::mutableListOf) += property
                }
            }

            val declaredProperties = type.memberIds
                .mapNotNull { memberId -> workspace[memberId] as? LsiProperty }
                .associateBy(LsiProperty::name)
            val result = linkedMapOf<String, LsiResolvedProperty>()
            for ((name, candidates) in inheritedByName) {
                if (name !in declaredProperties) {
                    result[name] = selectInheritedProperty(ownerId, name, candidates)
                }
            }
            for ((name, declaration) in declaredProperties) {
                val inherited = inheritedByName[name].orEmpty()
                val overriddenIds = declaration.overrides.mapTo(linkedSetOf(), LsiOverride::declarationId)
                val overriddenProperties = inherited
                    .filter { property ->
                        property.overrideChain.any { overridden -> overridden.id in overriddenIds }
                    }
                    .sortedWith(
                        compareBy<LsiResolvedProperty>(LsiResolvedProperty::inheritanceDistance)
                            .thenBy { property -> property.declaration.id },
                    )
                val inheritedAnnotations = overriddenProperties
                    .flatMap(LsiResolvedProperty::annotations)
                val overrideChain = buildList {
                    add(declaration)
                    overriddenProperties
                        .flatMap(LsiResolvedProperty::overrideChain)
                        .distinctBy(LsiProperty::id)
                        .let(::addAll)
                }
                result[name] = LsiResolvedProperty(
                    ownerId = ownerId,
                    declaration = declaration,
                    type = substitute(declaration.type, substitutions),
                    annotations = mergeAnnotations(declaration.annotations, inheritedAnnotations),
                    overrideChain = overrideChain,
                    inheritanceDistance = distance,
                )
            }
            return result
        } finally {
            visiting.remove(type.id)
        }
    }

    private fun selectInheritedProperty(
        ownerId: LsiSymbolId,
        name: String,
        candidates: List<LsiResolvedProperty>,
    ): LsiResolvedProperty {
        val distinctCandidates = candidates
            .distinctBy { property -> property.overrideChain.last().id }
            .sortedWith(
                compareBy<LsiResolvedProperty>(LsiResolvedProperty::inheritanceDistance)
                    .thenBy { property -> property.declaration.id },
            )
        val nearestDistance = distinctCandidates.minOf(LsiResolvedProperty::inheritanceDistance)
        val nearestCandidates = distinctCandidates.filter { property ->
            property.inheritanceDistance == nearestDistance
        }
        if (nearestCandidates.size > 1) {
            throw LsiInheritedPropertyConflictException(
                ownerId = ownerId,
                propertyName = name,
                conflictingPropertyIds = nearestCandidates.map { property -> property.declaration.id },
            )
        }
        val selected = nearestCandidates.single()
        val inheritedAnnotations = distinctCandidates
            .filterNot { property -> property === selected }
            .flatMap(LsiResolvedProperty::annotations)
        val overrideChain = buildList {
            addAll(selected.overrideChain)
            distinctCandidates
                .filterNot { property -> property === selected }
                .flatMap(LsiResolvedProperty::overrideChain)
                .distinctBy(LsiProperty::id)
                .filterNot { property -> any { existing -> existing.id == property.id } }
                .let(::addAll)
        }
        return selected.copy(
            annotations = mergeAnnotations(selected.annotations, inheritedAnnotations),
            overrideChain = overrideChain,
        )
    }

    private fun substitute(
        argument: LsiTypeArgument,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
    ): LsiTypeArgument {
        val type = argument.type ?: return argument
        if (type is LsiTypeParameterRef) {
            val replacement = substitutions[type.parameterId] ?: return argument
            val variance = combineVariance(argument.variance, replacement.variance)
            if (variance == LsiVariance.STAR) {
                return LsiTypeArgument.STAR
            }
            return LsiTypeArgument(
                variance = variance,
                type = requireNotNull(replacement.type).withUseSiteMetadata(
                    type.nullability,
                    type.annotations,
                ),
            )
        }
        return argument.copy(type = substitute(type, substitutions))
    }

    private fun isAssignable(
        source: LsiTypeRef,
        target: LsiTypeRef,
        visiting: MutableSet<Pair<String, String>>,
    ): Boolean {
        val nullabilityProvenByTypeParameterBounds =
            source is LsiTypeParameterRef && source.nullability == LsiNullability.UNKNOWN
        if (
            !nullabilityProvenByTypeParameterBounds &&
            !source.nullability.isAssignableTo(target.nullability)
        ) {
            return false
        }
        val relation = source.stableSignature() to target.stableSignature()
        if (!visiting.add(relation)) {
            return true
        }
        try {
            return when {
                source is LsiUnresolvedType || target is LsiUnresolvedType -> false
                source is LsiPrimitiveType && target is LsiPrimitiveType ->
                    source.kind == target.kind && source.boxed == target.boxed
                source is LsiDeclaredType && target is LsiDeclaredType ->
                    isDeclaredTypeAssignable(source, target, visiting)
                source is LsiArrayType && target is LsiArrayType ->
                    areEquivalentTypes(source.elementType, target.elementType, visiting)
                source is LsiFunctionType && target is LsiFunctionType ->
                    isFunctionTypeAssignable(source, target, visiting)
                source is LsiTypeParameterRef && target is LsiTypeParameterRef ->
                    source.parameterId == target.parameterId
                source is LsiTypeParameterRef -> typeParameter(source.parameterId)
                    ?.upperBounds
                    .orEmpty()
                    .any { upperBound -> isAssignable(upperBound, target, visiting) }
                else -> false
            }
        } finally {
            visiting.remove(relation)
        }
    }

    private fun isDeclaredTypeAssignable(
        source: LsiDeclaredType,
        target: LsiDeclaredType,
        visiting: MutableSet<Pair<String, String>>,
    ): Boolean {
        val sourceHierarchyEntry = typeHierarchyEntry(source.declarationId)
        val normalizedInputSource = sourceHierarchyEntry?.normalizeArguments(source) ?: source
        val targetHierarchyEntry = typeHierarchyEntry(target.declarationId)
        targetHierarchyEntry?.normalizeArguments(target)
        val resolvedSource = if (normalizedInputSource.declarationId == target.declarationId) {
            normalizedInputSource
        } else {
            resolveSuperType(normalizedInputSource, target.declarationId) ?: return false
        }
        if (target.arguments.isEmpty()) {
            return true
        }
        val normalizedSource = targetHierarchyEntry?.normalizeArguments(resolvedSource) ?: resolvedSource
        val normalizedTarget = targetHierarchyEntry?.normalizeArguments(target) ?: target
        if (normalizedSource.arguments.size != normalizedTarget.arguments.size) {
            return false
        }
        return normalizedSource.arguments.indices.all { index ->
            val declarationVariance = targetHierarchyEntry
                ?.typeParameters
                ?.getOrNull(index)
                ?.variance
                ?: LsiVariance.INVARIANT
            isTypeArgumentAssignable(
                source = normalizedSource.arguments[index],
                target = normalizedTarget.arguments[index],
                declarationVariance = declarationVariance,
                visiting = visiting,
            )
        }
    }

    private fun isTypeArgumentAssignable(
        source: LsiTypeArgument,
        target: LsiTypeArgument,
        declarationVariance: LsiVariance,
        visiting: MutableSet<Pair<String, String>>,
    ): Boolean {
        val sourceVariance = combineVariance(declarationVariance, source.variance)
        val targetVariance = combineVariance(declarationVariance, target.variance)
        if (targetVariance == LsiVariance.STAR) {
            return true
        }
        if (sourceVariance == LsiVariance.STAR) {
            return false
        }
        val sourceType = requireNotNull(source.type)
        val targetType = requireNotNull(target.type)
        return when (targetVariance) {
            LsiVariance.INVARIANT ->
                sourceVariance == LsiVariance.INVARIANT &&
                    areEquivalentTypes(sourceType, targetType, visiting)
            LsiVariance.OUT ->
                sourceVariance in setOf(LsiVariance.INVARIANT, LsiVariance.OUT) &&
                    isAssignable(sourceType, targetType, visiting)
            LsiVariance.IN ->
                sourceVariance in setOf(LsiVariance.INVARIANT, LsiVariance.IN) &&
                    isAssignable(targetType, sourceType, visiting)
            LsiVariance.STAR -> true
        }
    }

    private fun areEquivalentTypes(
        left: LsiTypeRef,
        right: LsiTypeRef,
        visiting: MutableSet<Pair<String, String>>,
    ): Boolean {
        return isAssignable(left, right, visiting) && isAssignable(right, left, visiting)
    }

    private fun isFunctionTypeAssignable(
        source: LsiFunctionType,
        target: LsiFunctionType,
        visiting: MutableSet<Pair<String, String>>,
    ): Boolean {
        if (
            source.suspending != target.suspending ||
            source.parameterTypes.size != target.parameterTypes.size ||
            (source.receiverType == null) != (target.receiverType == null)
        ) {
            return false
        }
        if (!isAssignable(source.returnType, target.returnType, visiting)) {
            return false
        }
        if (
            source.receiverType != null &&
            !isAssignable(requireNotNull(target.receiverType), source.receiverType, visiting)
        ) {
            return false
        }
        return source.parameterTypes.indices.all { index ->
            isAssignable(target.parameterTypes[index], source.parameterTypes[index], visiting)
        }
    }

    private fun typeParameter(id: LsiSymbolId): LsiTypeParameter? {
        return (workspace.typeHierarchy + fallbackTypeHierarchyById.values)
            .asSequence()
            .flatMap { entry -> entry.typeParameters.asSequence() }
            .firstOrNull { parameter -> parameter.id == id }
    }

    private fun typeHierarchyEntry(id: LsiSymbolId): LsiTypeHierarchyEntry? {
        return workspace.typeHierarchyEntry(id) ?: fallbackTypeHierarchyById[id]
    }

    private fun LsiTypeHierarchyEntry.identitySubstitutions(): Map<LsiSymbolId, LsiTypeArgument> {
        return typeParameters.identitySubstitutions()
    }

    private fun LsiTypeDeclaration.identitySubstitutions(): Map<LsiSymbolId, LsiTypeArgument> {
        return typeParameters.identitySubstitutions()
    }

    private fun List<LsiTypeParameter>.identitySubstitutions(): Map<LsiSymbolId, LsiTypeArgument> {
        return associate { parameter ->
            parameter.id to LsiTypeArgument.invariant(LsiTypeParameterRef(parameter.id))
        }
    }

    private fun LsiTypeHierarchyEntry.selfType(): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = id,
            arguments = typeParameters.map { parameter ->
                LsiTypeArgument.invariant(LsiTypeParameterRef(parameter.id))
            },
        )
    }

    private fun LsiTypeHierarchyEntry.substitutionsFrom(
        resolvedType: LsiDeclaredType,
    ): Map<LsiSymbolId, LsiTypeArgument> {
        return typeParameters.substitutionsFrom(normalizeArguments(resolvedType))
    }

    private fun LsiTypeDeclaration.substitutionsFrom(
        resolvedType: LsiDeclaredType,
    ): Map<LsiSymbolId, LsiTypeArgument> {
        return typeParameters.substitutionsFrom(
            resolvedType.normalizeArguments(id, typeParameters.size),
        )
    }

    private fun List<LsiTypeParameter>.substitutionsFrom(
        resolvedType: LsiDeclaredType,
    ): Map<LsiSymbolId, LsiTypeArgument> {
        return zip(resolvedType.arguments).associate { (parameter, argument) ->
            parameter.id to argument
        }
    }

    private fun LsiTypeHierarchyEntry.normalizeArguments(type: LsiDeclaredType): LsiDeclaredType {
        return type.normalizeArguments(id, typeParameters.size)
    }

    private fun LsiDeclaredType.normalizeArguments(
        expectedTypeId: LsiSymbolId,
        parameterCount: Int,
    ): LsiDeclaredType {
        require(declarationId == expectedTypeId) {
            "LSI type '${declarationId.value}' does not match declaration '${expectedTypeId.value}'"
        }
        if (arguments.isEmpty() && parameterCount != 0) {
            return copy(arguments = List(parameterCount) { LsiTypeArgument.STAR })
        }
        require(arguments.size == parameterCount) {
            "LSI type '${declarationId.value}' requires either raw arguments or exactly " +
                "$parameterCount arguments, but ${arguments.size} were supplied"
        }
        return this
    }
}

private fun combineVariance(
    occurrence: LsiVariance,
    replacement: LsiVariance,
): LsiVariance {
    return when {
        occurrence == LsiVariance.STAR || replacement == LsiVariance.STAR -> LsiVariance.STAR
        occurrence == LsiVariance.INVARIANT -> replacement
        replacement == LsiVariance.INVARIANT -> occurrence
        occurrence == replacement -> occurrence
        else -> LsiVariance.STAR
    }
}

private fun LsiNullability.isAssignableTo(target: LsiNullability): Boolean {
    return when (target) {
        LsiNullability.NON_NULL -> this == LsiNullability.NON_NULL || this == LsiNullability.PLATFORM
        LsiNullability.NULLABLE,
        LsiNullability.PLATFORM,
        LsiNullability.UNKNOWN,
        -> true
    }
}

fun mergeAnnotations(
    declared: List<LsiAnnotation>,
    inherited: List<LsiAnnotation>,
): List<LsiAnnotation> {
    val declaredTypes = declared.mapTo(linkedSetOf(), LsiAnnotation::type)
    return declared + inherited.filter { annotation -> annotation.type !in declaredTypes }
}

private fun LsiTypeRef.withUseSiteMetadata(
    useSiteNullability: LsiNullability,
    useSiteAnnotations: List<LsiAnnotation>,
): LsiTypeRef {
    val resolvedNullability = when (useSiteNullability) {
        LsiNullability.NULLABLE -> LsiNullability.NULLABLE
        LsiNullability.PLATFORM -> LsiNullability.PLATFORM
        LsiNullability.NON_NULL,
        LsiNullability.UNKNOWN,
        -> nullability
    }
    val resolvedAnnotations = mergeAnnotations(useSiteAnnotations, annotations)
    return when (this) {
        is LsiDeclaredType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
        is LsiTypeParameterRef -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
        is LsiPrimitiveType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
            boxed = boxed || resolvedNullability == LsiNullability.NULLABLE ||
                resolvedNullability == LsiNullability.PLATFORM,
        )
        is LsiArrayType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
        is LsiFunctionType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
        is LsiUnresolvedType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
    }
}
