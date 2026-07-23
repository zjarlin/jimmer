package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature

/** 解析 DTO 图声明的接口契约。 */
fun LsiWorkspace.resolveDtoInterfaceContracts(
    graph: DtoGraph,
): DtoInterfaceContractResolution {
    return DtoInterfaceContractResolver(this).resolve(graph)
}

private class DtoInterfaceContractResolver(
    private val workspace: LsiWorkspace,
    private val typeSystem: LsiTypeSystem = LsiTypeSystem(workspace),
) {
    private val declarationsByQualifiedName = workspace.declarations
        .filterIsInstance<LsiTypeDeclaration>()
        .associateBy(LsiTypeDeclaration::qualifiedName)

    fun resolve(graph: DtoGraph): DtoInterfaceContractResolution {
        val contracts = mutableListOf<DtoInterfaceContract>()
        val diagnostics = mutableListOf<LsiDiagnostic>()
        for (dtoType in graph.types) {
            val result = resolveType(dtoType)
            diagnostics += result.diagnostics
            result.contract?.let(contracts::add)
        }
        return DtoInterfaceContractResolution(
            contracts = contracts.sortedBy(DtoInterfaceContract::typeId),
            diagnostics = diagnostics.sortedWith(DIAGNOSTIC_COMPARATOR),
        )
    }

    private fun resolveType(dtoType: DtoType): TypeResolution {
        if (dtoType.superInterfaces.isEmpty()) {
            return TypeResolution(
                DtoInterfaceContract(dtoType.id, emptyList(), emptyList()),
                emptyList(),
            )
        }
        val diagnostics = mutableListOf<LsiDiagnostic>()
        val roots = mutableListOf<ResolvedInterfaceRoot>()
        val seenRootTypeIds = mutableSetOf<LsiSymbolId>()
        dtoType.superInterfaces.forEachIndexed { index, typeRef ->
            val root = resolveRoot(dtoType, typeRef, index, diagnostics) ?: return@forEachIndexed
            if (!seenRootTypeIds.add(root.type.declarationId)) {
                diagnostics += diagnostic(
                    code = "jimmer.dto.interface.duplicate-super-type",
                    message = "DTO type '${dtoType.id.value}' declares duplicate super interface " +
                        "'${root.type.declarationId.requireTypeQualifiedName()}'",
                    location = typeRef.location,
                    details = mapOf("dtoTypeId" to dtoType.id.value),
                )
                return@forEachIndexed
            }
            roots += root
        }
        val candidates = mutableListOf<PropCandidate>()
        roots.forEach { root ->
            collectInterface(
                dtoType = dtoType,
                interfaceType = root.type,
                rootIndex = root.index,
                distance = 0,
                activeTypeIds = linkedSetOf(),
                visitedSignatures = mutableSetOf(),
                candidates = candidates,
                diagnostics = diagnostics,
                fallbackLocation = root.location,
            )
        }
        val props = mergeCandidates(dtoType, candidates, diagnostics)
        if (diagnostics.isNotEmpty()) {
            return TypeResolution(null, diagnostics)
        }
        return TypeResolution(
            contract = DtoInterfaceContract(
                typeId = dtoType.id,
                superInterfaceTypeIds = roots.map { root -> root.type.declarationId },
                props = props,
            ),
            diagnostics = emptyList(),
        )
    }

    private fun resolveRoot(
        dtoType: DtoType,
        typeRef: DtoTypeRef,
        index: Int,
        diagnostics: MutableList<LsiDiagnostic>,
    ): ResolvedInterfaceRoot? {
        if (typeRef.nullable) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.nullable-super-type",
                message = "DTO super interface '${typeRef.typeName}' cannot be nullable",
                location = typeRef.location,
                details = mapOf("dtoTypeId" to dtoType.id.value),
            )
            return null
        }
        val resolvedType = resolveDtoTypeRef(dtoType, typeRef, diagnostics) as? LsiDeclaredType ?: return null
        val declaration = workspace[resolvedType.declarationId] as? LsiTypeDeclaration
        if (declaration == null) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.unresolved-super-type",
                message = "Cannot resolve DTO super interface '${typeRef.typeName}' from the frozen LSI workspace",
                symbolId = resolvedType.declarationId,
                location = typeRef.location,
                details = mapOf("dtoTypeId" to dtoType.id.value),
            )
            return null
        }
        if (declaration.kind != LsiTypeDeclarationKind.INTERFACE) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.not-interface",
                message = "DTO super type '${declaration.qualifiedName}' is not an interface",
                symbolId = declaration.id,
                location = typeRef.location,
                details = mapOf("dtoTypeId" to dtoType.id.value),
            )
            return null
        }
        if (!validateTypeArgumentCount(dtoType, declaration, resolvedType, typeRef.location, diagnostics)) {
            return null
        }
        return ResolvedInterfaceRoot(index, resolvedType, typeRef.location)
    }

    private fun collectInterface(
        dtoType: DtoType,
        interfaceType: LsiDeclaredType,
        rootIndex: Int,
        distance: Int,
        activeTypeIds: MutableSet<LsiSymbolId>,
        visitedSignatures: MutableSet<String>,
        candidates: MutableList<PropCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
        fallbackLocation: LsiLocation,
    ) {
        if (!activeTypeIds.add(interfaceType.declarationId)) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.cyclic-hierarchy",
                message = "Cyclic DTO super interface hierarchy at " +
                    "'${interfaceType.declarationId.requireTypeQualifiedName()}'",
                symbolId = interfaceType.declarationId,
                location = fallbackLocation,
                details = mapOf("dtoTypeId" to dtoType.id.value),
            )
            return
        }
        try {
            val signature = interfaceType.stableSignature()
            if (!visitedSignatures.add(signature)) {
                return
            }
            val declaration = workspace[interfaceType.declarationId] as? LsiTypeDeclaration
            if (declaration == null) {
                diagnostics += diagnostic(
                    code = "jimmer.dto.interface.unresolved-inherited-type",
                    message = "Cannot resolve inherited interface " +
                        "'${interfaceType.declarationId.requireTypeQualifiedName()}' from the frozen LSI workspace",
                    symbolId = interfaceType.declarationId,
                    location = fallbackLocation,
                    details = mapOf("dtoTypeId" to dtoType.id.value),
                )
                return
            }
            if (declaration.kind != LsiTypeDeclarationKind.INTERFACE) {
                diagnostics += diagnostic(
                    code = "jimmer.dto.interface.inherited-type-not-interface",
                    message = "Interface '${declaration.qualifiedName}' inherits non-interface type " +
                        "'${interfaceType.declarationId.requireTypeQualifiedName()}'",
                    symbolId = declaration.id,
                    location = declaration.location ?: fallbackLocation,
                    details = mapOf("dtoTypeId" to dtoType.id.value),
                )
                return
            }
            if (!validateTypeArgumentCount(
                    dtoType,
                    declaration,
                    interfaceType,
                    declaration.location ?: fallbackLocation,
                    diagnostics,
                )
            ) {
                return
            }
            val substitutions = declaration.typeParameters
                .zip(interfaceType.arguments)
                .associate { (parameter, argument) -> parameter.id to argument }
            declaration.memberIds
                .sorted()
                .mapNotNull { memberId ->
                    val member = workspace[memberId]
                    if (member == null) {
                        diagnostics += diagnostic(
                            code = "jimmer.dto.interface.unresolved-member",
                            message = "Cannot resolve member '${memberId.value}' of interface " +
                                "'${declaration.qualifiedName}'",
                            symbolId = memberId,
                            location = declaration.location ?: fallbackLocation,
                            details = mapOf("dtoTypeId" to dtoType.id.value),
                        )
                    }
                    member
                }
                .forEach { member ->
                    collectMember(
                        dtoType = dtoType,
                        declaringType = declaration,
                        member = member,
                        substitutions = substitutions,
                        rootIndex = rootIndex,
                        distance = distance,
                        candidates = candidates,
                        diagnostics = diagnostics,
                        fallbackLocation = fallbackLocation,
                    )
                }
            declaration.superTypes.forEach { superType ->
                val resolvedSuperType = typeSystem.substitute(superType, substitutions)
                if (resolvedSuperType.isImplicitAnyType()) {
                    return@forEach
                }
                if (resolvedSuperType !is LsiDeclaredType) {
                    diagnostics += diagnostic(
                        code = "jimmer.dto.interface.unresolved-inherited-type",
                        message = "Interface '${declaration.qualifiedName}' has unresolved inherited type " +
                            "'${resolvedSuperType.stableSignature()}'",
                        symbolId = declaration.id,
                        location = declaration.location ?: fallbackLocation,
                        details = mapOf("dtoTypeId" to dtoType.id.value),
                    )
                    return@forEach
                }
                collectInterface(
                    dtoType = dtoType,
                    interfaceType = resolvedSuperType,
                    rootIndex = rootIndex,
                    distance = distance + 1,
                    activeTypeIds = activeTypeIds,
                    visitedSignatures = visitedSignatures,
                    candidates = candidates,
                    diagnostics = diagnostics,
                    fallbackLocation = fallbackLocation,
                )
            }
        } finally {
            activeTypeIds.remove(interfaceType.declarationId)
        }
    }

    private fun collectMember(
        dtoType: DtoType,
        declaringType: LsiTypeDeclaration,
        member: LsiDeclaration,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
        rootIndex: Int,
        distance: Int,
        candidates: MutableList<PropCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
        fallbackLocation: LsiLocation,
    ) {
        when (member) {
            is LsiProperty -> collectProperty(
                dtoType,
                declaringType,
                member,
                substitutions,
                rootIndex,
                distance,
                candidates,
                diagnostics,
                fallbackLocation,
            )
            is LsiFunction -> collectFunction(
                dtoType,
                declaringType,
                member,
                substitutions,
                rootIndex,
                distance,
                candidates,
                diagnostics,
                fallbackLocation,
            )
            else -> Unit
        }
    }

    private fun collectProperty(
        dtoType: DtoType,
        declaringType: LsiTypeDeclaration,
        property: LsiProperty,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
        rootIndex: Int,
        distance: Int,
        candidates: MutableList<PropCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
        fallbackLocation: LsiLocation,
    ) {
        if (property.static || property.visibility.isPrivateContractMember()) {
            return
        }
        val type = typeSystem.substitute(property.type, substitutions)
        if (!validateResolvedMemberType(dtoType, property, type, diagnostics, fallbackLocation)) {
            return
        }
        val getter = DtoInterfaceAccessorContract(property.id, property.getterName, property.origin)
        val setter = if (property.mutable) {
            DtoInterfaceAccessorContract(property.id, property.setterName(), property.origin)
        } else {
            null
        }
        candidates += PropCandidate(
            declaringTypeId = declaringType.id,
            declarationId = property.id,
            name = property.name,
            type = type,
            getter = getter,
            setter = setter,
            origin = property.origin,
            rootIndex = rootIndex,
            distance = distance,
        )
    }

    private fun collectFunction(
        dtoType: DtoType,
        declaringType: LsiTypeDeclaration,
        function: LsiFunction,
        substitutions: Map<LsiSymbolId, LsiTypeArgument>,
        rootIndex: Int,
        distance: Int,
        candidates: MutableList<PropCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
        fallbackLocation: LsiLocation,
    ) {
        if (
            function.static ||
            function.modality != LsiModality.ABSTRACT ||
            function.visibility.isPrivateContractMember() ||
            function.isObjectMethod()
        ) {
            return
        }
        if (function.origin.language != LsiLanguage.JAVA) {
            diagnostics += illegalFunctionDiagnostic(
                dtoType,
                declaringType,
                function,
                "only a frozen Java setter method can contribute an interface property contract",
                fallbackLocation,
            )
            return
        }
        if (function.typeParameters.isNotEmpty()) {
            diagnostics += illegalFunctionDiagnostic(
                dtoType,
                declaringType,
                function,
                "abstract interface accessor cannot declare generic parameters",
                fallbackLocation,
            )
            return
        }
        if (function.receiverType != null || function.suspending) {
            diagnostics += illegalFunctionDiagnostic(
                dtoType,
                declaringType,
                function,
                "abstract interface accessor cannot have a receiver or be suspending",
                fallbackLocation,
            )
            return
        }
        val returnType = typeSystem.substitute(function.returnType, substitutions)
        val parameterTypes = function.parameters.map { parameter ->
            typeSystem.substitute(parameter.type, substitutions)
        }
        val setterName = function.setterPropertyName(returnType)
        if (setterName == null || parameterTypes.size != 1) {
            diagnostics += illegalFunctionDiagnostic(
                dtoType,
                declaringType,
                function,
                "abstract method can be considered as neither a getter nor a setter",
                fallbackLocation,
            )
            return
        }
        val parameterType = parameterTypes.single()
        if (!validateResolvedMemberType(dtoType, function, parameterType, diagnostics, fallbackLocation)) {
            return
        }
        candidates += PropCandidate(
            declaringTypeId = declaringType.id,
            declarationId = function.id,
            name = setterName,
            type = parameterType,
            getter = null,
            setter = DtoInterfaceAccessorContract(function.id, function.name, function.origin),
            origin = function.origin,
            rootIndex = rootIndex,
            distance = distance,
        )
    }

    private fun mergeCandidates(
        dtoType: DtoType,
        candidates: List<PropCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): List<DtoInterfacePropContract> {
        val result = mutableListOf<DtoInterfacePropContract>()
        for ((name, namedCandidates) in candidates.groupBy(PropCandidate::name).toSortedMap()) {
            val orderedCandidates = namedCandidates.sortedWith(PROP_CANDIDATE_COMPARATOR)
            val typesBySignature = orderedCandidates.groupBy { candidate -> candidate.type.stableSignature() }
            if (typesBySignature.size != 1) {
                diagnostics += diagnostic(
                    code = "jimmer.dto.interface.conflicting-property-type",
                    message = "DTO type '${dtoType.id.value}' inherits conflicting types for " +
                        "interface property '$name': " +
                        typesBySignature.keys.sorted().joinToString(),
                    symbolId = orderedCandidates.first().declarationId,
                    details = mapOf("dtoTypeId" to dtoType.id.value, "property" to name),
                )
                continue
            }
            val getter = mergeAccessor(
                dtoType,
                name,
                "getter",
                orderedCandidates.mapNotNull(PropCandidate::getter),
                diagnostics,
            )
            val setter = mergeAccessor(
                dtoType,
                name,
                "setter",
                orderedCandidates.mapNotNull(PropCandidate::setter),
                diagnostics,
            )
            if (getter == null && setter == null) {
                continue
            }
            if (diagnostics.any { diagnostic ->
                    diagnostic.code == "jimmer.dto.interface.conflicting-accessor" &&
                        diagnostic.details["dtoTypeId"] == dtoType.id.value &&
                        diagnostic.details["property"] == name
                }
            ) {
                continue
            }
            val primary = orderedCandidates.first()
            result += DtoInterfacePropContract(
                declaringTypeId = primary.declaringTypeId,
                name = name,
                type = primary.type,
                mutable = setter != null,
                getter = getter,
                setter = setter,
                origin = primary.origin,
            )
        }
        return result.sortedBy(DtoInterfacePropContract::name)
    }

    private fun mergeAccessor(
        dtoType: DtoType,
        propertyName: String,
        role: String,
        accessors: List<DtoInterfaceAccessorContract>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): DtoInterfaceAccessorContract? {
        if (accessors.isEmpty()) {
            return null
        }
        val names = accessors.mapTo(sortedSetOf(), DtoInterfaceAccessorContract::name)
        if (names.size > 1) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.conflicting-accessor",
                message = "DTO type '${dtoType.id.value}' inherits conflicting $role names for " +
                    "interface property '$propertyName': ${names.joinToString()}",
                symbolId = accessors.minBy { accessor -> accessor.declarationId }.declarationId,
                details = mapOf(
                    "dtoTypeId" to dtoType.id.value,
                    "property" to propertyName,
                    "role" to role,
                ),
            )
            return null
        }
        return accessors.minBy { accessor -> accessor.declarationId }
    }

    private fun validateTypeArgumentCount(
        dtoType: DtoType,
        declaration: LsiTypeDeclaration,
        type: LsiDeclaredType,
        location: LsiLocation,
        diagnostics: MutableList<LsiDiagnostic>,
    ): Boolean {
        if (declaration.typeParameters.size == type.arguments.size) {
            return true
        }
        diagnostics += diagnostic(
            code = "jimmer.dto.interface.type-argument-count",
            message = "Interface '${declaration.qualifiedName}' requires " +
                "${declaration.typeParameters.size} type arguments " +
                "but ${type.arguments.size} were supplied",
            symbolId = declaration.id,
            location = location,
            details = mapOf("dtoTypeId" to dtoType.id.value),
        )
        return false
    }

    private fun validateResolvedMemberType(
        dtoType: DtoType,
        declaration: LsiDeclaration,
        type: LsiTypeRef,
        diagnostics: MutableList<LsiDiagnostic>,
        fallbackLocation: LsiLocation,
    ): Boolean {
        val unresolved = type.firstUnresolvedType() ?: return true
        diagnostics += diagnostic(
            code = "jimmer.dto.interface.unresolved-member-type",
            message = "Interface member '${declaration.id.value}' has unresolved type " +
                "'${unresolved.stableSignature()}' after generic substitution",
            symbolId = declaration.id,
            location = declaration.location ?: fallbackLocation,
            details = mapOf("dtoTypeId" to dtoType.id.value),
        )
        return false
    }

    private fun resolveDtoTypeRef(
        dtoType: DtoType,
        typeRef: DtoTypeRef,
        diagnostics: MutableList<LsiDiagnostic>,
        validateDeclaredArity: Boolean = false,
    ): LsiTypeRef? {
        STANDARD_PRIMITIVE_TYPES[typeRef.typeName]?.let { kind ->
            if (typeRef.arguments.isNotEmpty()) {
                diagnostics += invalidDtoTypeRefDiagnostic(
                    dtoType,
                    typeRef,
                    "primitive type cannot have type arguments",
                )
                return null
            }
            return LsiPrimitiveType(kind, typeRef.nullable.toLsiNullability())
        }
        if (typeRef.typeName in STANDARD_ARRAY_TYPE_NAMES) {
            if (typeRef.arguments.size != 1 || typeRef.arguments.single().type == null) {
                diagnostics += invalidDtoTypeRefDiagnostic(
                    dtoType,
                    typeRef,
                    "array type requires one concrete argument",
                )
                return null
            }
            val elementType = resolveDtoTypeRef(
                dtoType,
                requireNotNull(typeRef.arguments.single().type),
                diagnostics,
                validateDeclaredArity = true,
            ) ?: return null
            return LsiArrayType(elementType, typeRef.nullable.toLsiNullability())
        }
        val standardCandidates = STANDARD_DECLARED_TYPES[typeRef.typeName]
        val typeId = if (standardCandidates != null) {
            selectAvailableTypeId(standardCandidates)
        } else {
            declarationsByQualifiedName[typeRef.typeName]?.id
                ?: LsiSymbolId.type(typeRef.typeName).takeIf { id ->
                    workspace.typeHierarchyEntry(id) != null
                }
        }
        if (typeId == null) {
            diagnostics += diagnostic(
                code = "jimmer.dto.interface.unresolved-type-reference",
                message = "Cannot resolve DTO type reference '${typeRef.typeName}' from the frozen LSI workspace",
                location = typeRef.location,
                details = mapOf("dtoTypeId" to dtoType.id.value),
            )
            return null
        }
        val arguments = mutableListOf<LsiTypeArgument>()
        for (argument in typeRef.arguments) {
            if (argument.variance == DtoVariance.STAR) {
                arguments += LsiTypeArgument.STAR
                continue
            }
            val argumentTypeRef = requireNotNull(argument.type)
            val argumentType = resolveDtoTypeRef(
                dtoType,
                argumentTypeRef,
                diagnostics,
                validateDeclaredArity = true,
            ) ?: return null
            arguments += LsiTypeArgument(
                variance = argument.variance.toLsiVariance(),
                type = argumentType,
            )
        }
        if (validateDeclaredArity) {
            val expectedArgumentCount = STANDARD_TYPE_ARGUMENT_COUNTS[typeRef.typeName]
                ?: (workspace[typeId] as? LsiTypeDeclaration)?.typeParameters?.size
                ?: workspace.typeHierarchyEntry(typeId)?.typeParameters?.size
            if (expectedArgumentCount != null && expectedArgumentCount != arguments.size) {
                diagnostics += invalidDtoTypeRefDiagnostic(
                    dtoType,
                    typeRef,
                    "type requires $expectedArgumentCount arguments but ${arguments.size} were supplied",
                )
                return null
            }
        }
        return LsiDeclaredType(
            declarationId = typeId,
            arguments = arguments,
            nullability = typeRef.nullable.toLsiNullability(),
        )
    }

    private fun selectAvailableTypeId(candidates: List<String>): LsiSymbolId {
        return candidates
            .map(LsiSymbolId::type)
            .firstOrNull { id -> workspace.contains(id) || workspace.typeHierarchyEntry(id) != null }
            ?: LsiSymbolId.type(candidates.first())
    }

    private fun invalidDtoTypeRefDiagnostic(
        dtoType: DtoType,
        typeRef: DtoTypeRef,
        reason: String,
    ): LsiDiagnostic {
        return diagnostic(
            code = "jimmer.dto.interface.invalid-type-reference",
            message = "Invalid DTO type reference '${typeRef.typeName}': $reason",
            location = typeRef.location,
            details = mapOf("dtoTypeId" to dtoType.id.value),
        )
    }

    private fun illegalFunctionDiagnostic(
        dtoType: DtoType,
        declaringType: LsiTypeDeclaration,
        function: LsiFunction,
        reason: String,
        fallbackLocation: LsiLocation,
    ): LsiDiagnostic {
        return diagnostic(
            code = "jimmer.dto.interface.illegal-abstract-function",
            message = "Illegal abstract method '${function.name}' in interface " +
                "'${declaringType.qualifiedName}': $reason",
            symbolId = function.id,
            location = function.location ?: fallbackLocation,
            details = mapOf("dtoTypeId" to dtoType.id.value),
        )
    }

    private fun diagnostic(
        code: String,
        message: String,
        symbolId: LsiSymbolId? = null,
        location: LsiLocation? = null,
        details: Map<String, String> = emptyMap(),
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = code,
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = symbolId,
            location = location,
            details = details,
        )
    }
}

private data class TypeResolution(
    val contract: DtoInterfaceContract?,
    val diagnostics: List<LsiDiagnostic>,
)

private data class ResolvedInterfaceRoot(
    val index: Int,
    val type: LsiDeclaredType,
    val location: LsiLocation,
)

private data class PropCandidate(
    val declaringTypeId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val getter: DtoInterfaceAccessorContract?,
    val setter: DtoInterfaceAccessorContract?,
    val origin: LsiOrigin,
    val rootIndex: Int,
    val distance: Int,
)

private fun LsiFunction.setterPropertyName(returnType: LsiTypeRef): String? {
    if (
        parameters.size != 1 ||
        !returnType.isVoidLike() ||
        !name.startsWith("set") ||
        name.length <= 3 ||
        name[3].isLowerCase()
    ) {
        return null
    }
    return name.substring(3).toDtoIdentifier()
}

private fun LsiProperty.setterName(): String {
    if (getterName == name) {
        return name
    }
    val suffix = when {
        getterName.startsWith("get") && getterName.length > 3 -> getterName.substring(3)
        getterName.startsWith("is") && getterName.length > 2 -> getterName.substring(2)
        else -> name.replaceFirstChar(Char::uppercaseChar)
    }
    return "set$suffix"
}

private fun LsiFunction.isObjectMethod(): Boolean {
    if (name == "hashCode" && parameters.isEmpty()) {
        return true
    }
    if (name == "toString" && parameters.isEmpty()) {
        return true
    }
    if (name != "equals" || parameters.size != 1) {
        return false
    }
    return parameters.single().type.isImplicitAnyType()
}

private fun LsiTypeRef.isVoidLike(): Boolean {
    return when (this) {
        is LsiPrimitiveType -> kind == LsiPrimitiveKind.UNIT || kind == LsiPrimitiveKind.VOID
        is LsiDeclaredType -> declarationId in VOID_TYPE_IDS
        else -> false
    }
}

private fun LsiTypeRef.isImplicitAnyType(): Boolean {
    return this is LsiDeclaredType && declarationId in ANY_TYPE_IDS
}

private fun LsiTypeRef.firstUnresolvedType(): LsiTypeRef? {
    return when (this) {
        is LsiUnresolvedType,
        is LsiTypeParameterRef,
        -> this
        is LsiDeclaredType -> arguments.firstNotNullOfOrNull { argument ->
            argument.type?.firstUnresolvedType()
        }
        is LsiArrayType -> elementType.firstUnresolvedType()
        is LsiFunctionType -> receiverType?.firstUnresolvedType()
            ?: parameterTypes.firstNotNullOfOrNull(LsiTypeRef::firstUnresolvedType)
            ?: returnType.firstUnresolvedType()
        is LsiPrimitiveType -> null
    }
}

private fun LsiVisibility.isPrivateContractMember(): Boolean {
    return this == LsiVisibility.PRIVATE || this == LsiVisibility.LOCAL
}

private fun String.toDtoIdentifier(): String {
    val chars = toCharArray()
    for (index in chars.indices) {
        if (chars[index].isLowerCase()) {
            break
        }
        chars[index] = chars[index].lowercaseChar()
    }
    return String(chars)
}

private fun Boolean.toLsiNullability(): LsiNullability {
    return if (this) LsiNullability.NULLABLE else LsiNullability.NON_NULL
}

private fun DtoVariance.toLsiVariance(): LsiVariance {
    return when (this) {
        DtoVariance.INVARIANT -> LsiVariance.INVARIANT
        DtoVariance.IN -> LsiVariance.IN
        DtoVariance.OUT -> LsiVariance.OUT
        DtoVariance.STAR -> LsiVariance.STAR
    }
}

private val PROP_CANDIDATE_COMPARATOR = compareBy<PropCandidate>(
    PropCandidate::distance,
    PropCandidate::rootIndex,
    PropCandidate::declaringTypeId,
    PropCandidate::declarationId,
)

private val DIAGNOSTIC_COMPARATOR = compareBy<LsiDiagnostic>(
    LsiDiagnostic::code,
    { diagnostic -> diagnostic.symbolId?.value.orEmpty() },
    { diagnostic -> diagnostic.location?.source?.path.orEmpty() },
    { diagnostic -> diagnostic.location?.start?.line ?: 0 },
    { diagnostic -> diagnostic.location?.start?.column ?: 0 },
    LsiDiagnostic::message,
)

private val VOID_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.Void"),
    LsiSymbolId.type("kotlin.Unit"),
)

private val ANY_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.Object"),
    LsiSymbolId.type("kotlin.Any"),
)

private val STANDARD_PRIMITIVE_TYPES = mapOf(
    "Boolean" to LsiPrimitiveKind.BOOLEAN,
    "boolean" to LsiPrimitiveKind.BOOLEAN,
    "java.lang.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "kotlin.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "Byte" to LsiPrimitiveKind.BYTE,
    "byte" to LsiPrimitiveKind.BYTE,
    "java.lang.Byte" to LsiPrimitiveKind.BYTE,
    "kotlin.Byte" to LsiPrimitiveKind.BYTE,
    "Short" to LsiPrimitiveKind.SHORT,
    "short" to LsiPrimitiveKind.SHORT,
    "java.lang.Short" to LsiPrimitiveKind.SHORT,
    "kotlin.Short" to LsiPrimitiveKind.SHORT,
    "Int" to LsiPrimitiveKind.INT,
    "int" to LsiPrimitiveKind.INT,
    "java.lang.Integer" to LsiPrimitiveKind.INT,
    "kotlin.Int" to LsiPrimitiveKind.INT,
    "Long" to LsiPrimitiveKind.LONG,
    "long" to LsiPrimitiveKind.LONG,
    "java.lang.Long" to LsiPrimitiveKind.LONG,
    "kotlin.Long" to LsiPrimitiveKind.LONG,
    "Char" to LsiPrimitiveKind.CHAR,
    "char" to LsiPrimitiveKind.CHAR,
    "java.lang.Character" to LsiPrimitiveKind.CHAR,
    "kotlin.Char" to LsiPrimitiveKind.CHAR,
    "Float" to LsiPrimitiveKind.FLOAT,
    "float" to LsiPrimitiveKind.FLOAT,
    "java.lang.Float" to LsiPrimitiveKind.FLOAT,
    "kotlin.Float" to LsiPrimitiveKind.FLOAT,
    "Double" to LsiPrimitiveKind.DOUBLE,
    "double" to LsiPrimitiveKind.DOUBLE,
    "java.lang.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Unit" to LsiPrimitiveKind.UNIT,
    "void" to LsiPrimitiveKind.VOID,
)

private val STANDARD_ARRAY_TYPE_NAMES = setOf("Array", "kotlin.Array")

private val STANDARD_DECLARED_TYPES = mapOf(
    "Any" to listOf("kotlin.Any", "java.lang.Object"),
    "String" to listOf("kotlin.String", "java.lang.String"),
    "Iterable" to listOf("kotlin.collections.Iterable", "java.lang.Iterable"),
    "MutableIterable" to listOf("kotlin.collections.MutableIterable", "java.lang.Iterable"),
    "Collection" to listOf("kotlin.collections.Collection", "java.util.Collection"),
    "MutableCollection" to listOf("kotlin.collections.MutableCollection", "java.util.Collection"),
    "List" to listOf("kotlin.collections.List", "java.util.List"),
    "MutableList" to listOf("kotlin.collections.MutableList", "java.util.List"),
    "Set" to listOf("kotlin.collections.Set", "java.util.Set"),
    "MutableSet" to listOf("kotlin.collections.MutableSet", "java.util.Set"),
    "Map" to listOf("kotlin.collections.Map", "java.util.Map"),
    "MutableMap" to listOf("kotlin.collections.MutableMap", "java.util.Map"),
)

private val STANDARD_TYPE_ARGUMENT_COUNTS = mapOf(
    "Any" to 0,
    "String" to 0,
    "Iterable" to 1,
    "MutableIterable" to 1,
    "Collection" to 1,
    "MutableCollection" to 1,
    "List" to 1,
    "MutableList" to 1,
    "Set" to 1,
    "MutableSet" to 1,
    "Map" to 2,
    "MutableMap" to 2,
)
