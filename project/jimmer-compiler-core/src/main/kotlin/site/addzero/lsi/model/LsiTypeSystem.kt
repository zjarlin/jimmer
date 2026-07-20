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
 */
class LsiTypeSystem(
    private val workspace: LsiWorkspace,
) {

    fun substitute(
        type: LsiTypeRef,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
    ): LsiTypeRef {
        return when (type) {
            is LsiDeclaredType -> type.copy(
                arguments = type.arguments.map { argument -> substitute(argument, substitutions) },
            )
            is LsiTypeParameterRef -> {
                val replacement = substitutions[type.parameterId]?.type ?: return type
                replacement.withUseSiteMetadata(type.nullability, type.annotations)
            }
            is LsiArrayType -> type.copy(
                elementType = substitute(type.elementType, substitutions),
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
        val type = workspace.typeHierarchyEntry(typeId) ?: return null
        if (typeId == superTypeId) {
            return type.selfType()
        }
        val pending = ArrayDeque<LsiDeclaredType>()
        type.directSuperTypes
            .mapTo(pending) { superType -> substitute(superType, type.identitySubstitutions()) as LsiDeclaredType }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current.stableSignature())) {
                continue
            }
            if (current.declarationId == superTypeId) {
                return current
            }
            val hierarchyEntry = workspace.typeHierarchyEntry(current.declarationId) ?: continue
            val substitutions = hierarchyEntry.substitutionsFrom(current)
            hierarchyEntry.directSuperTypes
                .mapTo(pending) { inheritedType ->
                    substitute(inheritedType, substitutions) as LsiDeclaredType
                }
        }
        return null
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
        return argument.copy(type = substitute(type, substitutions))
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
        return typeParameters.substitutionsFrom(resolvedType)
    }

    private fun LsiTypeDeclaration.substitutionsFrom(
        resolvedType: LsiDeclaredType,
    ): Map<LsiSymbolId, LsiTypeArgument> {
        return typeParameters.substitutionsFrom(resolvedType)
    }

    private fun List<LsiTypeParameter>.substitutionsFrom(
        resolvedType: LsiDeclaredType,
    ): Map<LsiSymbolId, LsiTypeArgument> {
        return zip(resolvedType.arguments).associate { (parameter, argument) ->
            parameter.id to argument
        }
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
        )
        is LsiArrayType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
        is LsiUnresolvedType -> copy(
            nullability = resolvedNullability,
            annotations = resolvedAnnotations,
        )
    }
}
