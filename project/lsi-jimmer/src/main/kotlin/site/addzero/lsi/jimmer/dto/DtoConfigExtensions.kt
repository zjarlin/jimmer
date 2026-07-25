package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveType
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

/** 解析 DTO filter 与 recursion 实现的目标类型、构造方式和依赖闭包。 */
fun LsiWorkspace.resolveDtoConfigContracts(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    targetLanguage: LsiLanguage,
): DtoConfigContractResolution {
    return DtoConfigContractResolver(
        workspace = this,
        immutableSchema = immutableSchema,
        targetLanguage = targetLanguage,
    ).resolve(graph)
}

private class DtoConfigContractResolver(
    private val workspace: LsiWorkspace,
    private val immutableSchema: ImmutableSchema,
    private val targetLanguage: LsiLanguage,
    private val typeSystem: LsiTypeSystem = LsiTypeSystem(workspace),
) {
    init {
        require(targetLanguage in setOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN)) {
            "DTO config contract resolution requires Java or Kotlin target language"
        }
    }

    fun resolve(graph: DtoGraph): DtoConfigContractResolution {
        val contracts = mutableListOf<DtoConfigContract>()
        val diagnostics = graph.validateDtoConfigPaths(immutableSchema).toMutableList()
        val unresolvedTypeIds = sortedSetOf<LsiSymbolId>()
        graph.props.filterIsInstance<DtoBaseProp>().forEach { prop ->
            val config = prop.config ?: return@forEach
            val targetEntityTypeId = targetEntityTypeId(graph, prop)
            if (targetEntityTypeId == null) {
                listOfNotNull(
                    config.filter?.let { DtoConfigContractKind.FILTER to it },
                    config.recursion?.let { DtoConfigContractKind.RECURSION to it },
                ).forEach { (kind, typeRef) ->
                    diagnostics += diagnostic(
                        code = ARGUMENT_UNRESOLVED_CODE,
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = null,
                        message = "Cannot resolve the associated entity type for DTO ${kind.displayName} " +
                            "implementation '${typeRef.typeId.requireTypeQualifiedName()}'",
                        extraDetails = mapOf("reason" to "associated-target-unresolved"),
                    )
                }
                return@forEach
            }
            config.filter?.let { typeRef ->
                resolveContract(graph, prop, DtoConfigContractKind.FILTER, typeRef, targetEntityTypeId)
                    .applyTo(contracts, diagnostics, unresolvedTypeIds)
            }
            config.recursion?.let { typeRef ->
                resolveContract(graph, prop, DtoConfigContractKind.RECURSION, typeRef, targetEntityTypeId)
                    .applyTo(contracts, diagnostics, unresolvedTypeIds)
            }
        }
        return DtoConfigContractResolution(
            contracts = contracts.sortedWith(compareBy(DtoConfigContract::propId, DtoConfigContract::kind)),
            diagnostics = diagnostics.sortedWith(DTO_CONFIG_DIAGNOSTIC_COMPARATOR),
            unresolvedTypeIds = unresolvedTypeIds.toList(),
        )
    }

    private fun resolveContract(
        graph: DtoGraph,
        prop: DtoBaseProp,
        kind: DtoConfigContractKind,
        typeRef: DtoConfigTypeRef,
        expectedTargetTypeId: LsiSymbolId,
    ): ContractResult {
        val implementation = workspace[typeRef.typeId] as? LsiTypeDeclaration
            ?: return ContractResult.unresolved(typeRef.typeId)
        if (implementation.typeParameters.isNotEmpty()) {
            return ContractResult.failure(
                diagnostic(
                    code = GENERIC_IMPLEMENTATION_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                        "cannot declare type parameters",
                ),
            )
        }
        val contractTypeId = contractTypeId(kind)
        val expectedContractArgumentTypeId = expectedContractArgumentTypeId(kind, expectedTargetTypeId)
        val contractSearch = searchSuperTypes(
            rootType = LsiDeclaredType(implementation.id),
            targetTypeId = contractTypeId,
        )
        if (contractSearch.unresolved) {
            return ContractResult.unresolved(typeRef.typeId)
        }
        if (contractSearch.cyclicPaths.isNotEmpty()) {
            return ContractResult.failure(
                diagnostic(
                    code = CYCLIC_HIERARCHY_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "Cyclic type hierarchy prevents resolving DTO ${kind.displayName} implementation " +
                        "'${implementation.qualifiedName}'",
                    extraDetails = mapOf(
                        "path" to contractSearch.cyclicPaths.first().joinToString(" -> ") { id -> id.value },
                    ),
                ),
            )
        }
        if (contractSearch.matches.isEmpty()) {
            return ContractResult.failure(
                diagnostic(
                    code = CONTRACT_MISSING_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                        "does not implement the required ${kind.displayName} contract",
                ),
            )
        }

        val resolvedTargets = mutableListOf<ResolvedTarget>()
        for (match in contractSearch.matches) {
            if (match.type.arguments.isEmpty()) {
                return ContractResult.failure(
                    diagnostic(
                        code = RAW_CONTRACT_CODE,
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = expectedTargetTypeId,
                        message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                            "uses a raw ${kind.displayName} contract",
                        extraDetails = mapOf("path" to match.path.canonicalPath()),
                    ),
                )
            }
            if (match.type.arguments.size != 1) {
                return ContractResult.failure(
                    unresolvedArgumentDiagnostic(
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = expectedTargetTypeId,
                        reason = "contract-arity:${match.type.arguments.size}",
                        path = match.path,
                    ),
                )
            }
            val target = if (kind == DtoConfigContractKind.FILTER && targetLanguage == LsiLanguage.JAVA) {
                resolveJavaFilterTarget(match.type.arguments.single(), match.path)
            } else {
                resolveEntityTarget(match.type.arguments.single(), match.path)
            }
            when (target) {
                is TargetResolution.Resolved -> resolvedTargets += target.target
                is TargetResolution.Deferred -> return ContractResult.unresolved(typeRef.typeId)
                is TargetResolution.Raw -> return ContractResult.failure(
                    diagnostic(
                        code = RAW_CONTRACT_CODE,
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = expectedTargetTypeId,
                        message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                            "uses a raw generic contract",
                        extraDetails = mapOf("path" to target.path.canonicalPath()),
                    ),
                )
                is TargetResolution.Unresolved -> return ContractResult.failure(
                    unresolvedArgumentDiagnostic(
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = expectedTargetTypeId,
                        reason = target.reason,
                        path = target.path,
                    ),
                )
                is TargetResolution.Cyclic -> return ContractResult.failure(
                    diagnostic(
                        code = CYCLIC_HIERARCHY_CODE,
                        kind = kind,
                        prop = prop,
                        typeRef = typeRef,
                        expectedTargetTypeId = expectedTargetTypeId,
                        message = "Cyclic type hierarchy prevents resolving DTO ${kind.displayName} target " +
                            "for '${implementation.qualifiedName}'",
                        extraDetails = mapOf("path" to target.path.canonicalPath()),
                    ),
                )
            }
        }

        val actualTargetTypeIds = resolvedTargets.map(ResolvedTarget::entityTypeId).distinct().sorted()
        if (actualTargetTypeIds.size != 1) {
            return ContractResult.failure(
                diagnostic(
                    code = CONTRACT_AMBIGUOUS_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                        "resolves conflicting target entity types: " +
                        actualTargetTypeIds.joinToString { id -> "'${id.requireTypeQualifiedName()}'" },
                    extraDetails = mapOf(
                        "actualTargetTypeIds" to actualTargetTypeIds.joinToString(",") { id -> id.value },
                    ),
                ),
            )
        }
        val actualTargetTypeId = actualTargetTypeIds.single()
        val actualContractArgumentTypeIds = resolvedTargets
            .map(ResolvedTarget::contractArgumentTypeId)
            .distinct()
            .sorted()
        if (actualContractArgumentTypeIds.size != 1) {
            return ContractResult.failure(
                diagnostic(
                    code = CONTRACT_AMBIGUOUS_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                        "resolves conflicting contract argument types: " +
                        actualContractArgumentTypeIds.joinToString { id -> "'${id.requireTypeQualifiedName()}'" },
                    extraDetails = mapOf(
                        "actualContractArgumentTypeIds" to
                            actualContractArgumentTypeIds.joinToString(",") { id -> id.value },
                        "expectedContractArgumentTypeId" to expectedContractArgumentTypeId.value,
                    ),
                ),
            )
        }
        val actualContractArgumentTypeId = actualContractArgumentTypeIds.single()
        if (actualTargetTypeId != expectedTargetTypeId) {
            return ContractResult.failure(
                diagnostic(
                    code = TARGET_MISMATCH_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' targets " +
                        "'${actualTargetTypeId.requireTypeQualifiedName()}', not associated entity " +
                        "'${expectedTargetTypeId.requireTypeQualifiedName()}'",
                    extraDetails = mapOf("actualTargetTypeId" to actualTargetTypeId.value),
                ),
            )
        }
        if (actualContractArgumentTypeId != expectedContractArgumentTypeId) {
            return ContractResult.failure(
                diagnostic(
                    code = TARGET_MISMATCH_CODE,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' uses contract " +
                        "argument '${actualContractArgumentTypeId.requireTypeQualifiedName()}', not expected " +
                        "'${expectedContractArgumentTypeId.requireTypeQualifiedName()}'",
                    extraDetails = mapOf(
                        "actualContractArgumentTypeId" to actualContractArgumentTypeId.value,
                        "expectedContractArgumentTypeId" to expectedContractArgumentTypeId.value,
                    ),
                ),
            )
        }
        val constructors = implementation.memberIds
            .mapNotNull { memberId -> workspace[memberId] as? LsiConstructor }
        if (constructors.any { constructor -> constructor.containsUnresolvedType() }) {
            return ContractResult.unresolved(typeRef.typeId)
        }
        val constructionFailure = constructionFailure(
            implementation = implementation,
            targetPackageName = graph.typesById.getValue(prop.ownerTypeId).packageName,
        )
        if (constructionFailure != null) {
            return ContractResult.failure(
                diagnostic(
                    code = constructionFailure.code,
                    kind = kind,
                    prop = prop,
                    typeRef = typeRef,
                    expectedTargetTypeId = expectedTargetTypeId,
                    message = "DTO ${kind.displayName} implementation '${implementation.qualifiedName}' " +
                        "cannot be instantiated without arguments: ${constructionFailure.message}",
                    extraDetails = buildMap {
                        put("reason", constructionFailure.reason)
                        putAll(constructionFailure.details)
                    },
                ),
            )
        }
        val dependencyTypeIds = buildSet {
            add(implementation.id)
            add(expectedTargetTypeId)
            contractSearch.matches.forEach { match ->
                match.path
                    .filterNot { typeId -> typeId in PLATFORM_CONTRACT_TYPE_IDS }
                    .forEach(::add)
            }
        }.sorted()
        return ContractResult.success(
            DtoConfigContract(
                propId = prop.id,
                kind = kind,
                implementationTypeId = implementation.id,
                targetEntityTypeId = expectedTargetTypeId,
                construction = DtoConfigConstructionKind.ZERO_ARGUMENT_CONSTRUCTOR,
                dependencyTypeIds = dependencyTypeIds,
            ),
        )
    }

    private fun targetEntityTypeId(
        graph: DtoGraph,
        prop: DtoBaseProp,
    ): LsiSymbolId? {
        val tailProp = graph.propsById[prop.tailPropId] as? DtoBaseProp ?: return null
        return tailProp.baseProps
            .mapNotNull { binding -> immutableSchema.propsById[binding.propId]?.targetTypeId }
            .distinct()
            .singleOrNull()
    }

    private fun contractTypeId(kind: DtoConfigContractKind): LsiSymbolId {
        return when (kind) {
            DtoConfigContractKind.FILTER -> when (targetLanguage) {
                LsiLanguage.JAVA -> FIELD_FILTER_TYPE_ID
                LsiLanguage.KOTLIN -> K_FIELD_FILTER_TYPE_ID
                LsiLanguage.UNKNOWN -> error("Unsupported DTO config targetLanguage")
            }
            DtoConfigContractKind.RECURSION -> RECURSION_STRATEGY_TYPE_ID
        }
    }

    private fun expectedContractArgumentTypeId(
        kind: DtoConfigContractKind,
        targetEntityTypeId: LsiSymbolId,
    ): LsiSymbolId {
        if (kind != DtoConfigContractKind.FILTER || targetLanguage != LsiLanguage.JAVA) {
            return targetEntityTypeId
        }
        val targetType = requireNotNull(immutableSchema.typesById[targetEntityTypeId]) {
            "No immutable target type '${targetEntityTypeId.value}' for DTO filter contract"
        }
        val targetDeclaration = workspace[targetEntityTypeId] as? LsiTypeDeclaration
        val packageName = targetDeclaration?.packageName()
            ?: targetType.qualifiedName.substringBeforeLast('.', "")
        val simpleName = targetDeclaration?.name ?: targetType.qualifiedName.substringAfterLast('.')
        return LsiSymbolId.type(
            if (packageName.isEmpty()) simpleName + "Table" else "$packageName.${simpleName}Table",
        )
    }

    private fun resolveJavaFilterTarget(
        argument: LsiTypeArgument,
        contractPath: List<LsiSymbolId>,
    ): TargetResolution {
        val tableType = argument.resolvedDeclaredType(contractPath) ?: return argument.unresolved(contractPath)
        val tableSearch = searchSuperTypes(tableType, TABLE_TYPE_ID)
        if (tableSearch.unresolved) {
            return TargetResolution.Deferred(tableSearch.unresolvedDisplayNames.firstOrNull().orEmpty())
        }
        if (tableSearch.cyclicPaths.isNotEmpty()) {
            return TargetResolution.Cyclic(tableSearch.cyclicPaths.first())
        }
        if (tableSearch.matches.isEmpty()) {
            return TargetResolution.Unresolved(
                reason = "filter-table-contract-missing:${tableType.stableSignature()}",
                path = contractPath,
            )
        }
        val targets = mutableListOf<ResolvedTarget>()
        for (match in tableSearch.matches) {
            if (match.type.arguments.isEmpty()) {
                return TargetResolution.Raw(match.path)
            }
            if (match.type.arguments.size != 1) {
                return TargetResolution.Unresolved(
                    reason = "table-contract-arity:${match.type.arguments.size}",
                    path = match.path,
                )
            }
            val entityType = match.type.arguments.single().resolvedDeclaredType(match.path)
                ?: return match.type.arguments.single().unresolved(match.path)
            targets += ResolvedTarget(
                entityTypeId = entityType.declarationId,
                contractArgumentTypeId = tableType.declarationId,
                path = contractPath + match.path,
                type = entityType,
            )
        }
        val entityTypeIds = targets.map(ResolvedTarget::entityTypeId).distinct()
        return if (entityTypeIds.size == 1) {
            TargetResolution.Resolved(
                ResolvedTarget(
                    entityTypeId = entityTypeIds.single(),
                    contractArgumentTypeId = tableType.declarationId,
                    path = targets.flatMap(ResolvedTarget::path).distinct(),
                    type = targets.first().type,
                ),
            )
        } else {
            TargetResolution.Unresolved(
                reason = "table-target-ambiguous:${entityTypeIds.sorted().joinToString(",") { id -> id.value }}",
                path = targets.flatMap(ResolvedTarget::path).distinct(),
            )
        }
    }

    private fun resolveEntityTarget(
        argument: LsiTypeArgument,
        path: List<LsiSymbolId>,
    ): TargetResolution {
        val entityType = argument.resolvedDeclaredType(path) ?: return argument.unresolved(path)
        return TargetResolution.Resolved(
            ResolvedTarget(
                entityTypeId = entityType.declarationId,
                contractArgumentTypeId = entityType.declarationId,
                path = path,
                type = entityType,
            ),
        )
    }

    private fun LsiTypeArgument.resolvedDeclaredType(path: List<LsiSymbolId>): LsiDeclaredType? {
        if (variance != LsiVariance.INVARIANT) {
            return null
        }
        val declaredType = type as? LsiDeclaredType ?: return null
        if (declaredType.arguments.isNotEmpty() || declaredType.nullability == LsiNullability.NULLABLE) {
            return null
        }
        return declaredType
    }

    private fun LsiTypeArgument.unresolved(path: List<LsiSymbolId>): TargetResolution {
        val unresolvedType = type
        if (unresolvedType is LsiUnresolvedType) {
            return TargetResolution.Deferred(unresolvedType.displayName)
        }
        val reason = when {
            variance == LsiVariance.STAR -> "star-projection"
            variance != LsiVariance.INVARIANT -> "variant-type-argument:${variance.name}"
            unresolvedType == null -> "missing-type-argument"
            unresolvedType is LsiTypeParameterRef ->
                "residual-type-parameter:${unresolvedType.parameterId.value}"
            unresolvedType is LsiUnresolvedType -> "unresolved-type:${unresolvedType.displayName}"
            unresolvedType is LsiDeclaredType && unresolvedType.arguments.isNotEmpty() ->
                "parameterized-target:${unresolvedType.stableSignature()}"
            unresolvedType.nullability == LsiNullability.NULLABLE ->
                "nullable-target:${unresolvedType.stableSignature()}"
            unresolvedType is LsiPrimitiveType -> "primitive-target:${unresolvedType.stableSignature()}"
            unresolvedType is LsiArrayType -> "array-target:${unresolvedType.stableSignature()}"
            unresolvedType is LsiFunctionType -> "function-target:${unresolvedType.stableSignature()}"
            else -> "unsupported-target:${unresolvedType.stableSignature()}"
        }
        return TargetResolution.Unresolved(reason, path)
    }

    private fun searchSuperTypes(
        rootType: LsiDeclaredType,
        targetTypeId: LsiSymbolId,
    ): GenericSearchResult {
        val matches = mutableListOf<GenericMatch>()
        val cyclicPaths = mutableListOf<List<LsiSymbolId>>()
        val unresolvedDisplayNames = sortedSetOf<String>()
        fun visit(
            current: LsiDeclaredType,
            path: List<LsiSymbolId>,
            activeTypeIds: MutableSet<LsiSymbolId>,
        ) {
            if (!activeTypeIds.add(current.declarationId)) {
                cyclicPaths += path + current.declarationId
                return
            }
            try {
                if (current.declarationId == targetTypeId) {
                    matches += GenericMatch(current, path)
                    return
                }
                val declaration = workspace[current.declarationId] as? LsiTypeDeclaration
                val hierarchy = workspace.typeHierarchyEntry(current.declarationId)
                if (hierarchy == null && declaration == null) {
                    if (current.declarationId !in TERMINAL_TYPE_IDS) {
                        unresolvedDisplayNames += current.declarationId.requireTypeQualifiedName()
                    }
                    return
                }
                val typeParameters = declaration?.typeParameters ?: requireNotNull(hierarchy).typeParameters
                val substitutions = typeParameters
                    .zip(current.arguments)
                    .associate { (parameter, argument) -> parameter.id to argument }
                val directSuperTypes = declaration?.superTypes ?: requireNotNull(hierarchy).directSuperTypes
                directSuperTypes
                    .map { superType -> typeSystem.substitute(superType, substitutions) }
                    .sortedBy(LsiTypeRef::stableSignature)
                    .forEach { superType ->
                        when (superType) {
                            is LsiDeclaredType ->
                                visit(superType, path + superType.declarationId, activeTypeIds)
                            is LsiUnresolvedType -> unresolvedDisplayNames += superType.displayName
                            else -> Unit
                        }
                    }
            } finally {
                activeTypeIds.remove(current.declarationId)
            }
        }
        visit(rootType, listOf(rootType.declarationId), linkedSetOf())
        return GenericSearchResult(
            matches = matches.distinctBy { match -> match.type.stableSignature() to match.path }
                .sortedWith(compareBy({ match -> match.type.stableSignature() }, { match -> match.path.canonicalPath() })),
            cyclicPaths = cyclicPaths.distinct().sortedBy(List<LsiSymbolId>::canonicalPath),
            unresolvedDisplayNames = unresolvedDisplayNames.toList(),
        )
    }

    private fun constructionFailure(
        implementation: LsiTypeDeclaration,
        targetPackageName: String,
    ): ConstructionFailure? {
        val supportedKind = when (targetLanguage) {
            LsiLanguage.JAVA -> implementation.kind in setOf(
                LsiTypeDeclarationKind.CLASS,
                LsiTypeDeclarationKind.RECORD,
            )
            LsiLanguage.KOTLIN -> implementation.kind == LsiTypeDeclarationKind.CLASS
            LsiLanguage.UNKNOWN -> false
        }
        if (!supportedKind) {
            return ConstructionFailure(
                code = NOT_INSTANTIABLE_CODE,
                reason = "implementation-kind:${implementation.kind.name}",
                message = "it is not a supported implementation type",
            )
        }
        if (implementation.abstractDeclaration) {
            return ConstructionFailure(
                code = NOT_INSTANTIABLE_CODE,
                reason = "abstract-declaration",
                message = "it is abstract",
            )
        }
        if (implementation.requiresEnclosingInstance) {
            return ConstructionFailure(
                NOT_INSTANTIABLE_CODE,
                "enclosing-instance-required",
                "it requires an enclosing instance",
            )
        }
        val implementationPackage = implementation.packageName()
        if (!implementation.visibility.isAccessibleFrom(
                targetPackageName,
                implementationPackage,
                implementation.origin,
            )
        ) {
            return ConstructionFailure(
                NOT_INSTANTIABLE_CODE,
                "implementation-visibility:${implementation.visibility.name}",
                "its visibility is ${implementation.visibility.name.lowercase()}",
            )
        }
        var enclosingTypeId = implementation.enclosingTypeId
        val visitedEnclosingTypeIds = mutableSetOf<LsiSymbolId>()
        while (enclosingTypeId != null) {
            if (!visitedEnclosingTypeIds.add(enclosingTypeId)) {
                return ConstructionFailure(
                    NOT_INSTANTIABLE_CODE,
                    "enclosing-cycle:${enclosingTypeId.value}",
                    "its enclosing type chain is cyclic",
                )
            }
            val enclosingType = workspace[enclosingTypeId] as? LsiTypeDeclaration
                ?: return ConstructionFailure(
                    NOT_INSTANTIABLE_CODE,
                    "enclosing-declaration-missing:${enclosingTypeId.value}",
                    "enclosing type '${enclosingTypeId.value}' is not frozen",
                )
            val enclosingPackage = enclosingType.packageName()
            if (!enclosingType.visibility.isAccessibleFrom(
                    targetPackageName,
                    enclosingPackage,
                    enclosingType.origin,
                )
            ) {
                return ConstructionFailure(
                    NOT_INSTANTIABLE_CODE,
                    "enclosing-visibility:${enclosingType.id.value}:${enclosingType.visibility.name}",
                    "enclosing type '${enclosingType.qualifiedName}' is not accessible",
                )
            }
            enclosingTypeId = enclosingType.enclosingTypeId
        }
        val constructors = implementation.memberIds.mapNotNull { memberId -> workspace[memberId] as? LsiConstructor }
        if (constructors.isEmpty()) {
            return ConstructionFailure(
                NOT_INSTANTIABLE_CODE,
                "constructor-metadata-missing",
                "no constructor metadata is frozen",
            )
        }
        val accessibleConstructors = constructors.filter { constructor ->
            constructor.visibility.isAccessibleFrom(
                targetPackageName,
                implementationPackage,
                constructor.origin,
            )
        }
        val exactZeroConstructors = accessibleConstructors.filter { constructor -> constructor.parameters.isEmpty() }
        if (exactZeroConstructors.size == 1) {
            return exactZeroConstructors.single().checkedExceptionFailure()
        }
        if (exactZeroConstructors.size > 1) {
            return ambiguousConstructors(exactZeroConstructors)
        }
        val optionalZeroConstructors = accessibleConstructors.filter { constructor ->
            when (targetLanguage) {
                LsiLanguage.JAVA ->
                    constructor.parameters.size == 1 && constructor.parameters.single().vararg
                LsiLanguage.KOTLIN ->
                    constructor.parameters.all { parameter -> parameter.hasDefault || parameter.vararg }
                LsiLanguage.UNKNOWN -> false
            }
        }
        if (optionalZeroConstructors.isEmpty()) {
            return ConstructionFailure(
                NOT_INSTANTIABLE_CODE,
                "zero-argument-constructor-missing",
                "it has no accessible zero-argument call",
            )
        }
        val bestConstructors = when (targetLanguage) {
            LsiLanguage.JAVA -> optionalZeroConstructors.filter { candidate ->
                optionalZeroConstructors.none { other ->
                    other !== candidate &&
                        other.parameters.single().type.isStrictSubtypeOf(candidate.parameters.single().type)
                }
            }
            LsiLanguage.KOTLIN -> {
                val bestCost = optionalZeroConstructors.minOf { constructor -> constructor.zeroCallCost() }
                optionalZeroConstructors.filter { constructor -> constructor.zeroCallCost() == bestCost }
            }
            LsiLanguage.UNKNOWN -> emptyList()
        }
        if (bestConstructors.size == 1) {
            return bestConstructors.single().checkedExceptionFailure()
        }
        return ambiguousConstructors(bestConstructors)
    }

    private fun LsiConstructor.checkedExceptionFailure(): ConstructionFailure? {
        if (targetLanguage != LsiLanguage.JAVA) {
            return null
        }
        val checkedThrownTypes = thrownTypes.filterNot { thrownType ->
            thrownType.isSameOrSubtypeOf(RUNTIME_EXCEPTION_TYPE_ID) ||
                thrownType.isSameOrSubtypeOf(ERROR_TYPE_ID)
        }
        if (checkedThrownTypes.isEmpty()) {
            return null
        }
        return ConstructionFailure(
            code = NOT_INSTANTIABLE_CODE,
            reason = "checked-constructor-exception",
            message = "its zero-argument constructor declares checked exceptions",
            details = mapOf(
                "constructorId" to id.value,
                "checkedThrownTypes" to checkedThrownTypes
                    .map(LsiTypeRef::stableSignature)
                    .sorted()
                    .joinToString(","),
            ),
        )
    }

    private fun ambiguousConstructors(constructors: List<LsiConstructor>): ConstructionFailure {
        return ConstructionFailure(
            code = CONSTRUCTOR_AMBIGUOUS_CODE,
            reason = "constructor-ambiguous",
            message = "multiple constructors accept a zero-argument call",
            details = mapOf(
                "candidateConstructorIds" to constructors
                    .map(LsiConstructor::id)
                    .sorted()
                    .joinToString(",") { constructorId -> constructorId.value },
            ),
        )
    }

    private fun LsiConstructor.zeroCallCost(): ZeroCallCost = ZeroCallCost(
        varargPenalty = if (parameters.any { parameter -> parameter.vararg }) 1 else 0,
        omittedDefaultCount = parameters.count { parameter -> parameter.hasDefault },
    )

    private fun LsiTypeRef.isStrictSubtypeOf(superType: LsiTypeRef): Boolean {
        if (stableSignature() == superType.stableSignature()) {
            return false
        }
        return when {
            this is LsiArrayType && superType is LsiArrayType ->
                elementType.isSameOrSubtypeOf(superType.elementType)
            this is LsiDeclaredType && superType is LsiDeclaredType ->
                superType.declarationId == JAVA_LANG_OBJECT_TYPE_ID ||
                    typeSystem.resolveSuperType(declarationId, superType.declarationId) != null
            else -> false
        }
    }

    private fun LsiTypeRef.isSameOrSubtypeOf(superType: LsiTypeRef): Boolean =
        stableSignature() == superType.stableSignature() || isStrictSubtypeOf(superType)

    private fun LsiTypeRef.isSameOrSubtypeOf(superTypeId: LsiSymbolId): Boolean {
        val declaredType = this as? LsiDeclaredType ?: return false
        return declaredType.declarationId == superTypeId ||
            typeSystem.resolveSuperType(declaredType.declarationId, superTypeId) != null
    }

    private fun LsiConstructor.containsUnresolvedType(): Boolean =
        parameters.any { parameter -> parameter.type.containsUnresolvedType() } ||
            thrownTypes.any { thrownType -> thrownType.containsUnresolvedType() }

    private fun LsiTypeRef.containsUnresolvedType(): Boolean = when (this) {
        is LsiUnresolvedType -> true
        is LsiDeclaredType -> arguments.any { argument -> argument.type?.containsUnresolvedType() == true }
        is LsiArrayType -> elementType.containsUnresolvedType()
        is LsiFunctionType -> receiverType?.containsUnresolvedType() == true ||
            parameterTypes.any { parameterType -> parameterType.containsUnresolvedType() } ||
            returnType.containsUnresolvedType()
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        -> false
    }

    private fun LsiVisibility.isAccessibleFrom(
        targetPackageName: String,
        implementationPackageName: String,
        origin: LsiOrigin,
    ): Boolean {
        return when (this) {
            LsiVisibility.PUBLIC -> true
            LsiVisibility.INTERNAL ->
                targetLanguage == LsiLanguage.KOTLIN &&
                    origin.kind in setOf(LsiOriginKind.SOURCE, LsiOriginKind.GENERATED)
            LsiVisibility.PACKAGE_PRIVATE -> targetPackageName == implementationPackageName
            LsiVisibility.PROTECTED ->
                origin.language == LsiLanguage.JAVA && targetPackageName == implementationPackageName
            LsiVisibility.PRIVATE,
            LsiVisibility.LOCAL,
            LsiVisibility.UNKNOWN,
            -> false
        }
    }

    private fun LsiTypeDeclaration.packageName(): String {
        var topLevelType = this
        val visited = mutableSetOf<LsiSymbolId>()
        while (topLevelType.enclosingTypeId != null && visited.add(topLevelType.id)) {
            val enclosingId = topLevelType.enclosingTypeId ?: break
            val enclosingType = workspace[enclosingId] as? LsiTypeDeclaration ?: break
            topLevelType = enclosingType
        }
        if (topLevelType !== this || topLevelType.enclosingTypeId == null) {
            return topLevelType.qualifiedName.substringBeforeLast('.', "")
        }
        return requireNotNull(enclosingTypeId)
            .requireTypeQualifiedName()
            .substringBeforeLast('.', "")
    }

    private fun unresolvedArgumentDiagnostic(
        kind: DtoConfigContractKind,
        prop: DtoBaseProp,
        typeRef: DtoConfigTypeRef,
        expectedTargetTypeId: LsiSymbolId,
        reason: String,
        path: List<LsiSymbolId>,
    ): LsiDiagnostic {
        return diagnostic(
            code = ARGUMENT_UNRESOLVED_CODE,
            kind = kind,
            prop = prop,
            typeRef = typeRef,
            expectedTargetTypeId = expectedTargetTypeId,
            message = "Cannot resolve the target entity type of DTO ${kind.displayName} implementation " +
                "'${typeRef.typeId.requireTypeQualifiedName()}'",
            extraDetails = mapOf(
                "reason" to reason,
                "path" to path.canonicalPath(),
            ),
        )
    }

    private fun diagnostic(
        code: String,
        kind: DtoConfigContractKind,
        prop: DtoBaseProp,
        typeRef: DtoConfigTypeRef,
        expectedTargetTypeId: LsiSymbolId?,
        message: String,
        extraDetails: Map<String, String> = emptyMap(),
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = code,
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = typeRef.typeId,
            location = typeRef.location,
            details = buildMap {
                put("kind", kind.name)
                put("dtoPropId", prop.id.value)
                put("implementationTypeId", typeRef.typeId.value)
                expectedTargetTypeId?.let { targetTypeId -> put("expectedTargetTypeId", targetTypeId.value) }
                putAll(extraDetails)
            }.toSortedMap(),
        )
    }

    private data class GenericSearchResult(
        val matches: List<GenericMatch>,
        val cyclicPaths: List<List<LsiSymbolId>>,
        val unresolvedDisplayNames: List<String>,
    ) {
        val unresolved: Boolean = unresolvedDisplayNames.isNotEmpty()
    }

    private data class GenericMatch(
        val type: LsiDeclaredType,
        val path: List<LsiSymbolId>,
    )

    private data class ResolvedTarget(
        val entityTypeId: LsiSymbolId,
        val contractArgumentTypeId: LsiSymbolId,
        val path: List<LsiSymbolId>,
        val type: LsiDeclaredType,
    )

    private sealed interface TargetResolution {
        data class Resolved(val target: ResolvedTarget) : TargetResolution
        data class Deferred(val displayName: String) : TargetResolution
        data class Raw(val path: List<LsiSymbolId>) : TargetResolution
        data class Unresolved(val reason: String, val path: List<LsiSymbolId>) : TargetResolution
        data class Cyclic(val path: List<LsiSymbolId>) : TargetResolution
    }

    private data class ConstructionFailure(
        val code: String,
        val reason: String,
        val message: String,
        val details: Map<String, String> = emptyMap(),
    )

    private data class ZeroCallCost(
        val varargPenalty: Int,
        val omittedDefaultCount: Int,
    ) : Comparable<ZeroCallCost> {

        override fun compareTo(other: ZeroCallCost): Int =
            compareValuesBy(this, other, ZeroCallCost::varargPenalty, ZeroCallCost::omittedDefaultCount)
    }

    private data class ContractResult(
        val contract: DtoConfigContract?,
        val diagnostics: List<LsiDiagnostic>,
        val unresolvedTypeIds: List<LsiSymbolId>,
    ) {
        fun applyTo(
            contracts: MutableList<DtoConfigContract>,
            diagnostics: MutableList<LsiDiagnostic>,
            unresolvedTypeIds: MutableSet<LsiSymbolId>,
        ) {
            contract?.let(contracts::add)
            diagnostics += this.diagnostics
            unresolvedTypeIds += this.unresolvedTypeIds
        }

        companion object {
            fun success(contract: DtoConfigContract): ContractResult =
                ContractResult(contract, emptyList(), emptyList())

            fun failure(diagnostic: LsiDiagnostic): ContractResult =
                ContractResult(null, listOf(diagnostic), emptyList())

            fun unresolved(typeId: LsiSymbolId): ContractResult =
                ContractResult(null, emptyList(), listOf(typeId))
        }
    }

    companion object {
        private val FIELD_FILTER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.FieldFilter")
        private val K_FIELD_FILTER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter")
        private val RECURSION_STRATEGY_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.RecursionStrategy")
        private val TABLE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.Table")
        private val JAVA_LANG_OBJECT_TYPE_ID = LsiSymbolId.type("java.lang.Object")
        private val RUNTIME_EXCEPTION_TYPE_ID = LsiSymbolId.type("java.lang.RuntimeException")
        private val ERROR_TYPE_ID = LsiSymbolId.type("java.lang.Error")
        private val PLATFORM_CONTRACT_TYPE_IDS = setOf(
            FIELD_FILTER_TYPE_ID,
            K_FIELD_FILTER_TYPE_ID,
            RECURSION_STRATEGY_TYPE_ID,
        )
        private val TERMINAL_TYPE_IDS = setOf(
            LsiSymbolId.type("java.lang.Object"),
            LsiSymbolId.type("kotlin.Any"),
        )

        private const val GENERIC_IMPLEMENTATION_CODE = "jimmer.dto.config.generic-implementation"
        private const val CONTRACT_MISSING_CODE = "jimmer.dto.config.contract-missing"
        private const val RAW_CONTRACT_CODE = "jimmer.dto.config.raw-contract"
        private const val ARGUMENT_UNRESOLVED_CODE = "jimmer.dto.config.argument-unresolved"
        private const val CONTRACT_AMBIGUOUS_CODE = "jimmer.dto.config.contract-ambiguous"
        private const val TARGET_MISMATCH_CODE = "jimmer.dto.config.target-mismatch"
        private const val NOT_INSTANTIABLE_CODE = "jimmer.dto.config.not-instantiable"
        private const val CONSTRUCTOR_AMBIGUOUS_CODE = "jimmer.dto.config.constructor-ambiguous"
        private const val CYCLIC_HIERARCHY_CODE = "jimmer.dto.config.cyclic-hierarchy"

    }
}

private val DtoConfigContractKind.displayName: String
    get() = name.lowercase()

private fun List<LsiSymbolId>.canonicalPath(): String = joinToString(" -> ") { id -> id.value }
