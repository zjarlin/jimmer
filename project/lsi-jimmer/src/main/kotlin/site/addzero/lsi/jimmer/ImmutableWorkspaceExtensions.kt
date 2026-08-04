package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiResolvedProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class ImmutablePrecompileException(
    val declarationId: LsiSymbolId,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

fun LsiWorkspace.toImmutableSchema(
    targetTypeIds: Set<LsiSymbolId> = jimmerImmutableTypeIds(),
): ImmutableSchema {
    return ImmutableSchemaBuilder().build(this, targetTypeIds)
}

fun LsiWorkspace.unresolvedJimmerImmutableTypeIds(
    targetTypeIds: Set<LsiSymbolId>,
): Set<LsiSymbolId> {
    return ImmutableSchemaBuilder().unresolvedTargetTypeIds(this, targetTypeIds)
}

private class ImmutableSchemaBuilder {
    fun build(
        workspace: LsiWorkspace,
        targetTypeIds: Set<LsiSymbolId>,
    ): ImmutableSchema {
        val typeDeclarations = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val kindByTypeId = typeDeclarations.mapNotNull { type ->
            type.immutableTypeKind()?.let { kind -> type.id to kind }
        }.toMap()
        val microServiceMetadataByTypeId = typeDeclarations.mapNotNull { type ->
            val kind = kindByTypeId[type.id] ?: return@mapNotNull null
            type.id to type.microServiceMetadata(kind)
        }.toMap()
        val unknownTargetTypeIds = targetTypeIds.filterNot(kindByTypeId::containsKey).sorted()
        if (unknownTargetTypeIds.isNotEmpty()) {
            val targetTypeId = unknownTargetTypeIds.first()
            throw ImmutablePrecompileException(
                declarationId = targetTypeId,
                recoverable = true,
                message = "Cannot resolve immutable target type '${targetTypeId.value}'",
            )
        }
        val typeSystem = LsiTypeSystem(workspace)
        val semanticTypeIds = managedTypeClosure(
            targetTypeIds = targetTypeIds,
            typeDeclarations = typeDeclarations,
            kindByTypeId = kindByTypeId,
            workspace = workspace,
            typeSystem = typeSystem,
        )
        val hierarchyResolver = ImmutableHierarchyResolver(
            typeDeclarations.associateBy(LsiTypeDeclaration::id),
            kindByTypeId,
        )
        val preliminaryTypes = typeDeclarations
            .filter { type -> type.id in semanticTypeIds }
            .map { type ->
                validateType(
                    type = type,
                    kind = kindByTypeId.getValue(type.id),
                    microServiceMetadata = microServiceMetadataByTypeId.getValue(type.id),
                    workspace = workspace,
                )
                compileType(
                    type = type,
                    kind = kindByTypeId.getValue(type.id),
                    kindByTypeId = kindByTypeId,
                    microServiceMetadataByTypeId = microServiceMetadataByTypeId,
                    typeSystem = typeSystem,
                    workspace = workspace,
                    hierarchy = hierarchyResolver.resolve(type.id),
                )
            }
            .sortedBy(ImmutableType::id)
        val associationTypes = resolveAssociations(preliminaryTypes)
        val types = resolveFormulaDependencies(resolveViews(associationTypes))
        return ImmutableSchema(types)
    }

    private fun resolveAssociations(
        types: List<ImmutableType>,
    ): List<ImmutableType> {
        val typesById = types.associateBy(ImmutableType::id)
        val propsByTypeAndName = types.associate { type ->
            type.id to type.props.associateBy(ImmutableProp::name)
        }
        val resolvedTypes = types.map { type ->
            type.copy(
                props = type.props.map { prop ->
                    val mappedBy = prop.mappedBy ?: return@map prop
                    val targetTypeId = prop.targetTypeId
                    if (targetTypeId == null) {
                        if (type.kind == ImmutableTypeKind.MAPPED_SUPERCLASS && prop.genericTarget) {
                            return@map prop
                        }
                        throw invalidAssociation(
                            prop,
                            "cannot resolve mappedBy '${mappedBy.name}' without a concrete target type",
                        )
                    }
                    val targetType = typesById[targetTypeId]
                        ?: throw ImmutablePrecompileException(
                            declarationId = prop.declarationId,
                            recoverable = true,
                            message = "Cannot resolve association target type '${targetTypeId.value}' of immutable " +
                                "property '${prop.id.value}'",
                        )
                    val associationOwner = propsByTypeAndName.getValue(targetType.id)[mappedBy.name]
                        ?: throw invalidAssociation(
                            prop,
                            "cannot find mappedBy property '${mappedBy.name}' in '${targetType.qualifiedName}'",
                        )
                    validateAssociationOwner(
                        inverseType = type,
                        inverseProp = prop,
                        associationOwner = associationOwner,
                        typesById = typesById,
                    )
                    prop.copy(mappedBy = mappedBy.copy(ownerPropId = associationOwner.id))
                },
            )
        }
        val resolvedPropsById = resolvedTypes
            .flatMap(ImmutableType::props)
            .associateBy(ImmutableProp::id)
        resolvedTypes
            .flatMap(ImmutableType::props)
            .mapNotNull { prop -> prop.mappedBy?.ownerPropId?.let { ownerPropId -> ownerPropId to prop } }
            .groupBy({ (ownerPropId, _) -> ownerPropId }, { (_, inverseProp) -> inverseProp })
            .forEach { (ownerPropId, inverseProps) ->
                val inversePropsByOriginalId = inverseProps.groupBy { inverseProp ->
                    inverseProp.overrideChain.lastOrNull() ?: inverseProp.declarationId
                }
                if (inversePropsByOriginalId.size > 1) {
                    val conflictingProp = inversePropsByOriginalId.values
                        .map(List<ImmutableProp>::first)
                        .sortedBy(ImmutableProp::id)[1]
                    throw invalidAssociation(
                        conflictingProp,
                        "cannot reference association owner '${resolvedPropsById.getValue(ownerPropId).id.value}' " +
                            "because an unrelated inverse property already references it",
                    )
                }
            }
        return resolvedTypes
    }

    private fun validateAssociationOwner(
        inverseType: ImmutableType,
        inverseProp: ImmutableProp,
        associationOwner: ImmutableProp,
        typesById: Map<LsiSymbolId, ImmutableType>,
    ) {
        val mappedBy = requireNotNull(inverseProp.mappedBy)
        if (
            associationOwner.primaryMapping != PrimaryMapping.ASSOCIATION ||
            !associationOwner.association
        ) {
            throw invalidAssociation(
                inverseProp,
                "mappedBy '${mappedBy.name}' is not a persistent association",
            )
        }
        if (associationOwner.mappedBy != null) {
            throw invalidAssociation(
                inverseProp,
                "mappedBy '${mappedBy.name}' is itself an inverse association",
            )
        }
        if (
            associationOwner.associationStorage == AssociationStorageKind.NONE &&
            !associationOwner.annotations.hasAnnotation(JOIN_SQL_ANNOTATION)
        ) {
            throw invalidAssociation(
                inverseProp,
                "mappedBy '${mappedBy.name}' has no persistent association storage",
            )
        }
        if (!inverseProp.associationKind.isInverseOf(associationOwner.associationKind)) {
            throw invalidAssociation(
                inverseProp,
                "association kind ${inverseProp.associationKind} does not match mappedBy owner kind " +
                    associationOwner.associationKind,
            )
        }
        val associationOwnerTargetTypeId = associationOwner.targetTypeId
            ?: throw invalidAssociation(
                inverseProp,
                "mappedBy owner '${associationOwner.id.value}' has no concrete target type",
            )
        if (
            !associationOwnerTargetTypeId.isSameAsOrSubtypeOf(inverseType.id, typesById) &&
            !inverseType.id.isSameAsOrSubtypeOf(associationOwnerTargetTypeId, typesById)
        ) {
            throw invalidAssociation(
                inverseProp,
                "mappedBy owner '${associationOwner.id.value}' targets incompatible type " +
                    "'${associationOwnerTargetTypeId.value}'",
            )
        }
    }

    private fun invalidAssociation(
        prop: ImmutableProp,
        message: String,
    ): ImmutablePrecompileException {
        return ImmutablePrecompileException(
            declarationId = prop.declarationId,
            message = "Immutable association property '${prop.id.value}' $message",
        )
    }

    private fun LsiSymbolId.isSameAsOrSubtypeOf(
        superTypeId: LsiSymbolId,
        typesById: Map<LsiSymbolId, ImmutableType>,
    ): Boolean {
        if (this == superTypeId) {
            return true
        }
        val visited = mutableSetOf<LsiSymbolId>()
        val pending = ArrayDeque<LsiSymbolId>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val typeId = pending.removeFirst()
            if (!visited.add(typeId)) {
                continue
            }
            val superTypeIds = typesById[typeId]?.superTypeIds.orEmpty()
            if (superTypeId in superTypeIds) {
                return true
            }
            pending.addAll(superTypeIds)
        }
        return false
    }

    private fun resolveFormulaDependencies(
        types: List<ImmutableType>,
    ): List<ImmutableType> {
        val typesById = types.associateBy(ImmutableType::id)
        val propsByTypeAndName = types.associate { type ->
            type.id to type.props.associateBy(ImmutableProp::name)
        }
        return types.map { type ->
            type.copy(
                props = type.props.map { prop ->
                    prop.copy(
                        formulaDependencies = resolveFormulaDependencies(
                            ownerType = type,
                            formulaProp = prop,
                            typesById = typesById,
                            propsByTypeAndName = propsByTypeAndName,
                        )
                    )
                }
            )
        }
    }

    private fun resolveFormulaDependencies(
        ownerType: ImmutableType,
        formulaProp: ImmutableProp,
        typesById: Map<LsiSymbolId, ImmutableType>,
        propsByTypeAndName: Map<LsiSymbolId, Map<String, ImmutableProp>>,
    ): List<FormulaDependency> {
        val formula = formulaProp.annotations.annotation(FORMULA_ANNOTATION) ?: return emptyList()
        return formula.stringValues("dependencies", formulaProp.declarationId)
            .map { dependency ->
                resolveFormulaDependency(
                    ownerType = ownerType,
                    formulaProp = formulaProp,
                    dependency = dependency,
                    typesById = typesById,
                    propsByTypeAndName = propsByTypeAndName,
                )
            }
            .distinct()
    }

    private fun resolveFormulaDependency(
        ownerType: ImmutableType,
        formulaProp: ImmutableProp,
        dependency: String,
        typesById: Map<LsiSymbolId, ImmutableType>,
        propsByTypeAndName: Map<LsiSymbolId, Map<String, ImmutableProp>>,
    ): FormulaDependency {
        var declaringType = ownerType
        val propNames = dependency.split('.')
        val propIds = propNames.mapIndexed { index, propName ->
            val prop = propsByTypeAndName.getValue(declaringType.id)[propName]
                ?: throw invalidFormulaDependency(
                    formulaProp = formulaProp,
                    dependency = dependency,
                    message = "there is no property '$propName' in '${declaringType.qualifiedName}'",
                )
            if (index + 1 < propNames.size) {
                if (!prop.association && !prop.embedded) {
                    throw invalidFormulaDependency(
                        formulaProp = formulaProp,
                        dependency = dependency,
                        message = "property '${prop.id.value}' is not the last segment but is neither an " +
                            "association nor an embedded property",
                    )
                }
                val targetTypeId = prop.targetTypeId
                    ?: throw invalidFormulaDependency(
                        formulaProp = formulaProp,
                        dependency = dependency,
                        message = "property '${prop.id.value}' has no concrete target type",
                    )
                declaringType = typesById[targetTypeId]
                    ?: throw invalidFormulaDependency(
                        formulaProp = formulaProp,
                        dependency = dependency,
                        message = "target type '${targetTypeId.value}' of property '${prop.id.value}' is unavailable",
                    )
            }
            prop.id
        }
        return FormulaDependency(propIds)
    }

    private fun invalidFormulaDependency(
        formulaProp: ImmutableProp,
        dependency: String,
        message: String,
    ): ImmutablePrecompileException {
        return ImmutablePrecompileException(
            declarationId = formulaProp.declarationId,
            message = "Formula dependency '$dependency' of immutable property '${formulaProp.id.value}' " + message,
        )
    }

    private fun resolveViews(
        types: List<ImmutableType>,
    ): List<ImmutableType> {
        val preliminaryTypesById = types.associateBy(ImmutableType::id)
        val preliminaryPropsByTypeAndName = types.associate { type ->
            type.id to type.props.associateBy(ImmutableProp::name)
        }
        val typesWithManyToManyViews = types.map { type ->
            type.copy(
                props = type.props.map { prop ->
                    val annotation = prop.annotations.annotation(MANY_TO_MANY_VIEW_ANNOTATION)
                    if (annotation == null) {
                        prop
                    } else {
                        prop.copy(
                            view = resolveManyToManyView(
                                ownerType = type,
                                prop = prop,
                                annotation = annotation,
                                typesById = preliminaryTypesById,
                                propsByTypeAndName = preliminaryPropsByTypeAndName,
                            )
                        )
                    }
                },
            )
        }
        val typesById = typesWithManyToManyViews.associateBy(ImmutableType::id)
        val propsById = typesWithManyToManyViews
            .flatMap(ImmutableType::props)
            .associateBy(ImmutableProp::id)
        val propsByTypeAndName = typesWithManyToManyViews.associate { type ->
            type.id to type.props.associateBy(ImmutableProp::name)
        }
        val resolvedTypes = typesWithManyToManyViews.map { type ->
            type.copy(
                props = type.props.map { prop ->
                    val annotation = prop.annotations.annotation(ID_VIEW_ANNOTATION)
                    val view = if (annotation == null) {
                        prop.view
                    } else {
                        resolveIdView(
                            ownerType = type,
                            prop = prop,
                            annotation = annotation,
                            typesById = typesById,
                            propsByTypeAndName = propsByTypeAndName,
                        )
                    }
                    val converter = if (view is ImmutableView.Id) {
                        prop.converter ?: propsById[view.basePropId]
                            ?.declaringTypeId
                            ?.let(typesById::get)
                            ?.idPropId
                            ?.let(propsById::get)
                            ?.converter
                            ?.forIdView(prop)
                    } else {
                        prop.converter
                    }
                    prop.copy(view = view, converter = converter)
                        .also(ImmutableProp::validateConverterSource)
                },
            )
        }
        validateImplicitIdViewConflicts(resolvedTypes)
        return resolvedTypes
    }

    private fun resolveIdView(
        ownerType: ImmutableType,
        prop: ImmutableProp,
        annotation: LsiAnnotation,
        typesById: Map<LsiSymbolId, ImmutableType>,
        propsByTypeAndName: Map<LsiSymbolId, Map<String, ImmutableProp>>,
    ): ImmutableView.Id {
        var basePropName = annotation.stringValue("value").orEmpty()
        if (basePropName.isEmpty()) {
            basePropName = prop.defaultIdViewBasePropName()
                ?: throw invalidView(
                    prop,
                    "cannot determine the id-view base property automatically; specify @IdView value",
                )
        }
        if (basePropName == prop.name) {
            throw invalidView(prop, "cannot use itself as id-view base property")
        }
        val baseProp = propsByTypeAndName.getValue(ownerType.id)[basePropName]
            ?: throw invalidView(prop, "cannot find id-view base property '$basePropName'")
        val baseTargetType = baseProp.targetTypeId?.let(typesById::get)
        val persistentAssociation =
            baseProp.primaryMapping == PrimaryMapping.ASSOCIATION ||
                baseProp.view is ImmutableView.ManyToMany
        if (!baseProp.association || !persistentAssociation ||
            (!baseProp.genericTarget && baseTargetType?.kind != ImmutableTypeKind.ENTITY)
        ) {
            throw invalidView(prop, "base property '${baseProp.name}' is not a persistent entity association")
        }
        if (prop.list != baseProp.list) {
            throw invalidView(
                prop,
                "list category does not match id-view base property '${baseProp.name}'",
            )
        }
        if (prop.nullable != baseProp.nullable) {
            throw invalidView(
                prop,
                "nullability does not match id-view base property '${baseProp.name}'",
            )
        }
        if (baseProp.genericTarget) {
            if (ownerType.kind != ImmutableTypeKind.MAPPED_SUPERCLASS) {
                throw invalidView(
                    prop,
                    "base property '${baseProp.name}' has a generic target whose id cannot be resolved",
                )
            }
            return ImmutableView.Id(baseProp.id, null)
        }
        val targetType = requireNotNull(baseTargetType)
        val targetIdProp = targetType.idPropId?.let { targetIdPropId ->
            targetType.props.single { candidate -> candidate.id == targetIdPropId }
        } ?: throw invalidView(
            prop,
            "base property '${baseProp.name}' targets '${targetType.qualifiedName}' without an id property",
        )
        val ignoreValueNullability = !prop.list
        if (
            prop.valueType().boxedTypeSignature(ignoreValueNullability) !=
            targetIdProp.valueType().boxedTypeSignature(ignoreValueNullability)
        ) {
            throw invalidView(
                prop,
                "type does not match id '${targetIdProp.name}' of association target '${targetType.qualifiedName}'",
            )
        }
        return ImmutableView.Id(baseProp.id, targetIdProp.id)
    }

    private fun resolveManyToManyView(
        ownerType: ImmutableType,
        prop: ImmutableProp,
        annotation: LsiAnnotation,
        typesById: Map<LsiSymbolId, ImmutableType>,
        propsByTypeAndName: Map<LsiSymbolId, Map<String, ImmutableProp>>,
    ): ImmutableView.ManyToMany {
        val targetType = prop.targetTypeId?.let(typesById::get)
        if (!prop.list || targetType?.kind != ImmutableTypeKind.ENTITY) {
            throw invalidView(prop, "must be a list of entities")
        }
        val basePropName = annotation.stringValue("prop").orEmpty()
        if (basePropName.isEmpty()) {
            throw invalidView(prop, "must specify @ManyToManyView prop")
        }
        val baseProp = propsByTypeAndName.getValue(ownerType.id)[basePropName]
            ?: throw invalidView(prop, "cannot find many-to-many view base property '$basePropName'")
        if (baseProp.associationKind != AssociationKind.ONE_TO_MANY) {
            throw invalidView(prop, "base property '${baseProp.name}' is not a one-to-many association")
        }
        val middleType = baseProp.targetTypeId?.let(typesById::get)
            ?: throw invalidView(prop, "base property '${baseProp.name}' has no concrete middle entity type")
        if (middleType.kind != ImmutableTypeKind.ENTITY) {
            throw invalidView(prop, "base property '${baseProp.name}' does not target a middle entity")
        }
        val deeperPropName = annotation.stringValue("deeperProp").orEmpty()
        val deeperProp = if (deeperPropName.isNotEmpty()) {
            propsByTypeAndName.getValue(middleType.id)[deeperPropName]
                ?.takeIf { candidate ->
                    candidate.associationKind == AssociationKind.MANY_TO_ONE &&
                        candidate.targetTypeId == targetType.id
                }
                ?: throw invalidView(
                    prop,
                    "cannot find many-to-one deeper property '$deeperPropName' from " +
                        "'${middleType.qualifiedName}' to '${targetType.qualifiedName}'",
                )
        } else {
            val candidates = middleType.props.filter { candidate ->
                candidate.associationKind == AssociationKind.MANY_TO_ONE &&
                    candidate.targetTypeId == targetType.id
            }
            if (candidates.size != 1) {
                throw invalidView(
                    prop,
                    "requires exactly one automatic many-to-one deeper property from " +
                        "'${middleType.qualifiedName}' to '${targetType.qualifiedName}', found ${candidates.size}",
                )
            }
            candidates.single()
        }
        return ImmutableView.ManyToMany(baseProp.id, deeperProp.id)
    }

    private fun invalidView(
        prop: ImmutableProp,
        message: String,
    ): ImmutablePrecompileException {
        return ImmutablePrecompileException(
            declarationId = prop.declarationId,
            message = "Immutable view property '${prop.id.value}' $message",
        )
    }

    private fun validateImplicitIdViewConflicts(types: List<ImmutableType>) {
        types.forEach { type ->
            val propsByName = type.props.associateBy(ImmutableProp::name)
            val idViewBasePropIds = type.props.mapNotNullTo(linkedSetOf()) { prop ->
                (prop.view as? ImmutableView.Id)?.basePropId
            }
            val ownerIdProp = type.idPropId?.let { idPropId ->
                type.props.single { prop -> prop.id == idPropId }
            }
            type.props.forEach { associationProp ->
                if (
                    associationProp.associationKind !in setOf(
                        AssociationKind.ONE_TO_ONE,
                        AssociationKind.MANY_TO_ONE,
                    ) ||
                    associationProp.reverse ||
                    associationProp.id in idViewBasePropIds
                ) {
                    return@forEach
                }
                val expectedProp = propsByName["${associationProp.name}Id"] ?: return@forEach
                if (associationProp.allowsMapsIdNameConflict(expectedProp, ownerIdProp)) {
                    return@forEach
                }
                throw ImmutablePrecompileException(
                    declarationId = expectedProp.declarationId,
                    message = "Immutable property '${expectedProp.id.value}' looks like an id view of association " +
                        "'${associationProp.id.value}'; add @${ID_VIEW_ANNOTATION.value}",
                )
            }
        }
    }

    fun unresolvedTargetTypeIds(
        workspace: LsiWorkspace,
        targetTypeIds: Set<LsiSymbolId>,
    ): Set<LsiSymbolId> {
        return targetTypeIds.filterTo(sortedSetOf()) { targetTypeId ->
            workspace.hasUnresolvedImmutableType(targetTypeId)
        }
    }

    private fun validateType(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
        microServiceMetadata: MicroServiceMetadata,
        workspace: LsiWorkspace,
    ) {
        if (type.enclosingTypeId != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' must be a top-level type",
            )
        }
        if (type.kind != LsiTypeDeclarationKind.INTERFACE) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' must be an interface",
            )
        }
        if (
            type.typeParameters.isNotEmpty() &&
            kind != ImmutableTypeKind.MAPPED_SUPERCLASS
        ) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot declare type parameters unless it is " +
                    "a mapped superclass",
            )
        }
        if (type.visibility in setOf(LsiVisibility.PRIVATE, LsiVisibility.PROTECTED, LsiVisibility.LOCAL)) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot be private, protected or local",
            )
        }
        if (microServiceMetadata.acrossMicroServices && microServiceMetadata.microServiceName.isNotEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable mapped superclass '${type.qualifiedName}' cannot specify microServiceName " +
                    "when acrossMicroServices is true",
            )
        }
        validateDeclaredFunctions(type, workspace)
    }

    private fun validateDeclaredFunctions(
        type: LsiTypeDeclaration,
        workspace: LsiWorkspace,
    ) {
        type.memberIds
            .mapNotNull { memberId -> workspace[memberId] as? LsiFunction }
            .sortedBy(LsiFunction::id)
            .forEach { function ->
                if (function.modality == LsiModality.ABSTRACT) {
                    throw ImmutablePrecompileException(
                        declarationId = function.id,
                        message = "Immutable type '${type.qualifiedName}' cannot declare abstract function " +
                            "'${function.name}'",
                    )
                }
                val jimmerAnnotationName = function.annotations
                    .filter { annotation -> annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD }
                    .map { annotation -> annotation.type.requireTypeQualifiedName() }
                    .filter { annotationName -> annotationName.startsWith(JIMMER_PACKAGE_PREFIX) }
                    .minOrNull()
                if (jimmerAnnotationName != null) {
                    throw ImmutablePrecompileException(
                        declarationId = function.id,
                        message = "Immutable non-abstract function '${function.name}' declared by " +
                            "'${type.qualifiedName}' cannot be decorated by Jimmer annotation " +
                            "@$jimmerAnnotationName",
                    )
                }
            }
    }

    private fun compileType(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
        kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
        microServiceMetadataByTypeId: Map<LsiSymbolId, MicroServiceMetadata>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
        hierarchy: ImmutableHierarchy,
    ): ImmutableType {
        val microServiceMetadata = microServiceMetadataByTypeId.getValue(type.id)
        validateMicroServiceInheritance(
            type = type,
            hierarchy = hierarchy,
            microServiceMetadata = microServiceMetadata,
            microServiceMetadataByTypeId = microServiceMetadataByTypeId,
        )
        val resolvedProps = try {
            typeSystem.effectiveProperties(type.id)
        } catch (exception: IllegalArgumentException) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = exception.message ?: "Cannot resolve immutable type '${type.qualifiedName}'",
            )
        }
        resolvedProps.forEach { property ->
            validateOverride(
                ownerType = type,
                ownerKind = kind,
                property = property,
                kindByTypeId = kindByTypeId,
                microServiceMetadataByTypeId = microServiceMetadataByTypeId,
                typeSystem = typeSystem,
                workspace = workspace,
            )
        }
        val orderedProps = orderResolvedProperties(type, resolvedProps, workspace)
        val props = orderedProps.map { property ->
            property.toImmutableProp(
                ownerTypeId = type.id,
                kindByTypeId = kindByTypeId,
                microServiceMetadataByTypeId = microServiceMetadataByTypeId,
                workspace = workspace,
                typeSystem = typeSystem,
            )
        }
        val discriminatorPropId = discriminatorPropId(
            type = type,
            kind = kind,
            hierarchy = hierarchy,
            props = props,
            typeSystem = typeSystem,
            workspace = workspace,
        )
        val identity = identity(
            type = type,
            kind = kind,
            hierarchy = hierarchy,
            props = props,
            workspace = workspace,
        )
        return ImmutableType(
            id = type.id,
            qualifiedName = type.qualifiedName,
            kind = kind,
            documentation = type.documentation,
            annotations = type.annotations,
            typeParameterIds = type.typeParameters.map { parameter -> parameter.id },
            superTypeIds = hierarchy.directSuperTypeIds,
            props = props,
            primarySuperTypeId = hierarchy.primarySuperTypeId,
            inheritanceRootTypeId = hierarchy.inheritanceRootTypeId,
            inheritanceStrategy = hierarchy.inheritanceStrategy,
            joinedTableDissociateAction = hierarchy.joinedTableDissociateAction,
            instantiable = hierarchy.instantiable,
            discriminatorValue = hierarchy.discriminatorValue,
            discriminatorPropId = discriminatorPropId,
            idPropId = identity.idPropId,
            versionPropId = identity.versionPropId,
            logicalDeletedPropId = identity.logicalDeletedPropId,
            acrossMicroServices = microServiceMetadata.acrossMicroServices,
            microServiceName = microServiceMetadata.microServiceName,
        )
    }

    private fun identity(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
        hierarchy: ImmutableHierarchy,
        props: List<ImmutableProp>,
        workspace: LsiWorkspace,
    ): ImmutableIdentity {
        val idProp = identityProp(type, props, PrimaryMapping.ID)
        val versionProp = identityProp(type, props, PrimaryMapping.VERSION)
        val logicalDeletedProp = identityProp(type, props, PrimaryMapping.LOGICAL_DELETED)
        val identityProps = listOfNotNull(idProp, versionProp, logicalDeletedProp)
        if (
            identityProps.isNotEmpty() &&
            kind !in setOf(ImmutableTypeKind.ENTITY, ImmutableTypeKind.MAPPED_SUPERCLASS)
        ) {
            val invalidProp = identityProps.first()
            throw ImmutablePrecompileException(
                declarationId = invalidProp.declarationId,
                message = "Immutable property '${invalidProp.id.value}' cannot be an identity property because " +
                    "'${type.qualifiedName}' is neither an entity nor a mapped superclass",
            )
        }
        if (kind == ImmutableTypeKind.ENTITY && idProp == null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable entity '${type.qualifiedName}' must have exactly one " +
                    "@${ID_ANNOTATION.requireTypeQualifiedName()} property",
            )
        }
        identityProps.forEach { prop -> validateIdentityProp(prop, workspace) }
        if (kind == ImmutableTypeKind.ENTITY && hierarchy.inheritanceRootTypeId != null &&
            hierarchy.inheritanceRootTypeId != type.id
        ) {
            listOfNotNull(versionProp, logicalDeletedProp).firstOrNull { prop ->
                prop.declaringTypeId == type.id && prop.declaresPrimaryMapping(workspace)
            }?.let { invalidProp ->
                throw ImmutablePrecompileException(
                    declarationId = invalidProp.declarationId,
                    message = "Immutable inheritance derived type '${type.qualifiedName}' cannot declare " +
                        "@${requireNotNull(invalidProp.primaryAnnotationTypeId).requireTypeQualifiedName()}",
                )
            }
        }
        return ImmutableIdentity(
            idPropId = idProp?.id,
            versionPropId = versionProp?.id,
            logicalDeletedPropId = logicalDeletedProp?.id,
        )
    }

    private fun identityProp(
        type: LsiTypeDeclaration,
        props: List<ImmutableProp>,
        mapping: PrimaryMapping,
    ): ImmutableProp? {
        val candidates = props.filter { prop -> prop.primaryMapping == mapping }
        if (candidates.size > 1) {
            val annotationType = mapping.identityAnnotationType()
            throw ImmutablePrecompileException(
                declarationId = candidates[1].declarationId,
                message = "Immutable type '${type.qualifiedName}' has multiple properties decorated by " +
                    "@${annotationType.requireTypeQualifiedName()}: " +
                    candidates.joinToString { prop -> "'${prop.name}'" },
            )
        }
        return candidates.singleOrNull()
    }

    private fun validateIdentityProp(
        prop: ImmutableProp,
        workspace: LsiWorkspace,
    ) {
        val annotationType = prop.primaryMapping.identityAnnotationType()
        if (prop.list || prop.association) {
            throw invalidIdentityProp(
                prop,
                annotationType,
                "must be a scalar property",
            )
        }
        when (prop.primaryMapping) {
            PrimaryMapping.ID -> {
                if (prop.nullable) {
                    throw invalidIdentityProp(prop, annotationType, "cannot be nullable")
                }
            }
            PrimaryMapping.VERSION -> {
                val primitiveType = prop.type as? LsiPrimitiveType
                if (
                    prop.nullable ||
                    primitiveType?.kind != LsiPrimitiveKind.INT ||
                    primitiveType.boxed
                ) {
                    throw invalidIdentityProp(prop, annotationType, "must be a non-null Int property")
                }
            }
            PrimaryMapping.LOGICAL_DELETED -> {
                validateLogicalDeletedProp(prop, annotationType, workspace)
            }
            else -> error("Primary mapping ${prop.primaryMapping} is not an identity mapping")
        }
    }

    private fun validateLogicalDeletedProp(
        prop: ImmutableProp,
        annotationType: LsiSymbolId,
        workspace: LsiWorkspace,
    ) {
        val type = prop.type
        val primitiveType = type as? LsiPrimitiveType
        val primitiveKind = primitiveType?.kind
        if (primitiveKind == LsiPrimitiveKind.BOOLEAN || primitiveKind == LsiPrimitiveKind.INT) {
            if (prop.nullable || primitiveType.boxed) {
                throw invalidIdentityProp(
                    prop,
                    annotationType,
                    "must use a non-null primitive Boolean or Int",
                )
            }
            return
        }
        if (primitiveKind == LsiPrimitiveKind.LONG) {
            return
        }
        val declaredType = type as? LsiDeclaredType
        val typeId = declaredType?.declarationId
        if (typeId == BOXED_LONG_TYPE_ID || typeId == UUID_TYPE_ID) {
            return
        }
        val declaration = typeId?.let { candidateTypeId -> workspace[candidateTypeId] as? LsiTypeDeclaration }
        if (declaration?.kind == LsiTypeDeclarationKind.ENUM) {
            return
        }
        if (typeId in LOGICAL_DELETED_TIME_TYPE_IDS) {
            if (!prop.nullable) {
                throw invalidIdentityProp(prop, annotationType, "must be nullable for a time type")
            }
            return
        }
        throw invalidIdentityProp(
            prop,
            annotationType,
            "must be Boolean, Int, enum, Long, UUID or a supported time type",
        )
    }

    private fun invalidIdentityProp(
        prop: ImmutableProp,
        annotationType: LsiSymbolId,
        message: String,
    ): ImmutablePrecompileException {
        return ImmutablePrecompileException(
            declarationId = prop.declarationId,
            message = "Immutable property '${prop.id.value}' decorated by " +
                "@${annotationType.requireTypeQualifiedName()} $message",
        )
    }

    private fun validateMicroServiceInheritance(
        type: LsiTypeDeclaration,
        hierarchy: ImmutableHierarchy,
        microServiceMetadata: MicroServiceMetadata,
        microServiceMetadataByTypeId: Map<LsiSymbolId, MicroServiceMetadata>,
    ) {
        hierarchy.directSuperTypeIds.forEach { superTypeId ->
            val superMetadata = microServiceMetadataByTypeId.getValue(superTypeId)
            if (
                !superMetadata.acrossMicroServices &&
                superMetadata.microServiceName != microServiceMetadata.microServiceName
            ) {
                throw ImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Immutable type '${type.qualifiedName}' has micro service name " +
                        "'${microServiceMetadata.microServiceName}', but its super type '${superTypeId.value}' has " +
                        "micro service name '${superMetadata.microServiceName}'",
                )
            }
        }
    }

    private fun discriminatorPropId(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
        hierarchy: ImmutableHierarchy,
        props: List<ImmutableProp>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
    ): LsiSymbolId? {
        if (kind != ImmutableTypeKind.ENTITY) {
            return null
        }
        val discriminatorProps = props.filter { prop ->
            prop.annotations.hasAnnotation(DISCRIMINATOR_ANNOTATION)
        }
        discriminatorProps.forEach { prop ->
            validateDiscriminatorProp(prop, workspace)
        }
        val inheritanceRootTypeId = hierarchy.inheritanceRootTypeId
        if (inheritanceRootTypeId == null) {
            val discriminatorProp = discriminatorProps.firstOrNull() ?: return null
            throw ImmutablePrecompileException(
                declarationId = discriminatorProp.declarationId,
                message = "Immutable property '${discriminatorProp.declarationId.value}' decorated by " +
                    "@${DISCRIMINATOR_ANNOTATION.value} can only be used by an inheritance entity",
            )
        }
        if (inheritanceRootTypeId != type.id) {
            val declaredDiscriminatorProp = discriminatorProps.firstOrNull { prop ->
                prop.declaringTypeId == type.id
            }
            if (declaredDiscriminatorProp != null) {
                throw ImmutablePrecompileException(
                    declarationId = declaredDiscriminatorProp.declarationId,
                    message = "Immutable property '${declaredDiscriminatorProp.declarationId.value}' decorated by " +
                        "@${DISCRIMINATOR_ANNOTATION.value} cannot be declared by an inheritance derived type",
                )
            }
            val rootDiscriminatorOriginalId = rootDiscriminatorOriginalId(
                rootTypeId = inheritanceRootTypeId,
                typeSystem = typeSystem,
            )
            val invalidDiscriminatorProp = discriminatorProps.firstOrNull { prop ->
                prop.overrideChain.lastOrNull() != rootDiscriminatorOriginalId
            }
            if (invalidDiscriminatorProp != null) {
                throw ImmutablePrecompileException(
                    declarationId = invalidDiscriminatorProp.declarationId,
                    message = "Immutable property '${invalidDiscriminatorProp.declarationId.value}' decorated by " +
                        "@${DISCRIMINATOR_ANNOTATION.value} cannot be declared or inherited by an inheritance " +
                        "derived type except from its inheritance root",
                )
            }
            if (discriminatorProps.size != 1) {
                throw ImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Immutable inheritance derived type '${type.qualifiedName}' must have exactly one " +
                        "discriminator property inherited from its inheritance root",
                )
            }
            return discriminatorProps.single().id
        }
        if (discriminatorProps.isEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable inheritance root '${type.qualifiedName}' must declare or inherit one property " +
                    "decorated by @${DISCRIMINATOR_ANNOTATION.value}",
            )
        }
        if (discriminatorProps.size > 1) {
            throw ImmutablePrecompileException(
                declarationId = discriminatorProps[1].declarationId,
                message = "Immutable inheritance root '${type.qualifiedName}' has multiple discriminator properties",
            )
        }
        return discriminatorProps.single().id
    }

    private fun rootDiscriminatorOriginalId(
        rootTypeId: LsiSymbolId,
        typeSystem: LsiTypeSystem,
    ): LsiSymbolId {
        val rootDiscriminatorProps = try {
            typeSystem.effectiveProperties(rootTypeId)
        } catch (exception: IllegalArgumentException) {
            throw ImmutablePrecompileException(
                declarationId = rootTypeId,
                message = exception.message ?: "Cannot resolve immutable inheritance root '${rootTypeId.value}'",
            )
        }.filter { prop ->
            prop.annotations.hasAnnotation(DISCRIMINATOR_ANNOTATION)
        }
        if (rootDiscriminatorProps.size != 1) {
            throw ImmutablePrecompileException(
                declarationId = rootTypeId,
                message = "Immutable inheritance root '${rootTypeId.value}' must have exactly one discriminator property",
            )
        }
        return rootDiscriminatorProps.single().overrideChain.last().id
    }

    private fun validateDiscriminatorProp(
        prop: ImmutableProp,
        workspace: LsiWorkspace,
    ) {
        val declaredType = prop.type as? LsiDeclaredType
        val validType = declaredType != null && (
            declaredType.arguments.isEmpty() && declaredType.declarationId in STRING_TYPE_IDS ||
                (workspace[declaredType.declarationId] as? LsiTypeDeclaration)?.kind == LsiTypeDeclarationKind.ENUM
        )
        val conflictingPrimaryAnnotation = prop.annotations.firstOrNull { annotation ->
            annotation.type in PRIMARY_PROP_ANNOTATIONS && annotation.type != DISCRIMINATOR_ANNOTATION
        }
        if (prop.list || prop.association || !validType || conflictingPrimaryAnnotation != null) {
            throw ImmutablePrecompileException(
                declarationId = prop.declarationId,
                message = "Immutable property '${prop.declarationId.value}' decorated by " +
                    "@${DISCRIMINATOR_ANNOTATION.value} must be a scalar string or enum property",
            )
        }
    }

    private fun validateOverride(
        ownerType: LsiTypeDeclaration,
        ownerKind: ImmutableTypeKind,
        property: LsiResolvedProperty,
        kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
        microServiceMetadataByTypeId: Map<LsiSymbolId, MicroServiceMetadata>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
    ) {
        if (property.declaration.ownerId != ownerType.id || property.overrideChain.size < 2) {
            return
        }
        val overriddenDeclarations = property.overrideChain.drop(1)
        val directSuperTypeIds = ownerType.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .mapTo(linkedSetOf(), LsiDeclaredType::declarationId)
        val overrideAllowed = ownerKind == ImmutableTypeKind.ENTITY &&
            overriddenDeclarations.all { overriddenDeclaration ->
                val inheritedOwnerId = overriddenDeclaration.ownerId
                kindByTypeId[inheritedOwnerId] == ImmutableTypeKind.MAPPED_SUPERCLASS &&
                    inheritedOwnerId in directSuperTypeIds
            }
        if (!overrideAllowed) {
            throw ImmutablePrecompileException(
                declarationId = property.declaration.id,
                message = "Immutable property '${property.declaration.id.value}' can only override a property " +
                    "declared directly by a mapped superclass of an entity",
            )
        }
        val currentModel = property.toImmutableProp(
            ownerTypeId = ownerType.id,
            kindByTypeId = kindByTypeId,
            microServiceMetadataByTypeId = microServiceMetadataByTypeId,
            workspace = workspace,
            typeSystem = typeSystem,
        )
        for (overriddenDeclaration in overriddenDeclarations) {
            val inheritedOwnerId = overriddenDeclaration.ownerId
            val inheritedOwner = workspace[inheritedOwnerId] as? LsiTypeDeclaration
                ?: throw ImmutablePrecompileException(
                    declarationId = property.declaration.id,
                    recoverable = true,
                    message = "Missing inherited immutable type '${inheritedOwnerId.value}'",
                )
            val inheritedProperty = typeSystem.effectiveProperties(inheritedOwnerId)
                .firstOrNull { inherited ->
                    inherited.overrideChain.any { declaration -> declaration.id == overriddenDeclaration.id }
                }
                ?: throw ImmutablePrecompileException(
                    declarationId = property.declaration.id,
                    message = "Cannot resolve inherited property '${overriddenDeclaration.id.value}'",
                )
            val inheritedType = resolveInheritedPropertyType(
                ownerTypeId = ownerType.id,
                inheritedOwner = inheritedOwner,
                inheritedType = inheritedProperty.type,
                typeSystem = typeSystem,
                sourceId = property.declaration.id,
            )
            val inheritedInOwner = inheritedProperty.copy(
                ownerId = ownerType.id,
                type = inheritedType,
            )
            val inheritedModel = inheritedInOwner.toImmutableProp(
                ownerTypeId = ownerType.id,
                kindByTypeId = kindByTypeId,
                microServiceMetadataByTypeId = microServiceMetadataByTypeId,
                workspace = workspace,
                typeSystem = typeSystem,
            )
            val violations = buildList {
                if (currentModel.type.jimmerTypeSignature(ignoreRootNullability = true) !=
                    inheritedModel.type.jimmerTypeSignature(ignoreRootNullability = true)
                ) {
                    add("resolved type")
                }
                if (currentModel.nullable != inheritedModel.nullable) {
                    add("nullability")
                }
                if (currentModel.list != inheritedModel.list) {
                    add("list category")
                }
                if (currentModel.association != inheritedModel.association) {
                    add("association category")
                }
                if (currentModel.associationKind != inheritedModel.associationKind) {
                    add("association kind")
                }
                if (currentModel.primaryAnnotationTypeId != inheritedModel.primaryAnnotationTypeId) {
                    add("primary mapping annotation")
                }
                if (currentModel.mappedBy?.name != inheritedModel.mappedBy?.name) {
                    add("mappedBy ownership")
                }
                if (currentModel.associationStorage != inheritedModel.associationStorage) {
                    add("association storage")
                }
                if (currentModel.formulaKind != inheritedModel.formulaKind) {
                    add("formula kind")
                }
            }
            if (violations.isNotEmpty()) {
                throw ImmutablePrecompileException(
                    declarationId = property.declaration.id,
                    message = "Immutable property '${property.declaration.id.value}' overrides annotations but changes " +
                        violations.joinToString(),
                )
            }
        }
    }

    private fun resolveInheritedPropertyType(
        ownerTypeId: LsiSymbolId,
        inheritedOwner: LsiTypeDeclaration,
        inheritedType: LsiTypeRef,
        typeSystem: LsiTypeSystem,
        sourceId: LsiSymbolId,
    ): LsiTypeRef {
        val resolvedSuperType = typeSystem.resolveSuperType(ownerTypeId, inheritedOwner.id)
            ?: throw ImmutablePrecompileException(
                declarationId = sourceId,
                recoverable = true,
                message = "Cannot resolve inherited immutable type '${inheritedOwner.id.value}'",
            )
        val substitutions = inheritedOwner.typeParameters
            .zip(resolvedSuperType.arguments)
            .associate { (parameter, argument) -> parameter.id to argument }
        return typeSystem.substitute(inheritedType, substitutions)
    }
}

private data class ImmutableHierarchy(
    val directSuperTypeIds: List<LsiSymbolId>,
    val primarySuperTypeId: LsiSymbolId?,
    val inheritanceRootTypeId: LsiSymbolId?,
    val inheritanceStrategy: InheritanceStrategy?,
    val joinedTableDissociateAction: JoinedTableDissociateAction?,
    val instantiable: Boolean,
    val discriminatorValue: String?,
)

private data class ImmutableIdentity(
    val idPropId: LsiSymbolId?,
    val versionPropId: LsiSymbolId?,
    val logicalDeletedPropId: LsiSymbolId?,
)

private data class MicroServiceMetadata(
    val acrossMicroServices: Boolean,
    val microServiceName: String,
)

private class ImmutableHierarchyResolver(
    private val declarationsById: Map<LsiSymbolId, LsiTypeDeclaration>,
    private val kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
) {

    private val cache = mutableMapOf<LsiSymbolId, ImmutableHierarchy>()

    private val resolving = linkedSetOf<LsiSymbolId>()

    fun resolve(typeId: LsiSymbolId): ImmutableHierarchy {
        cache[typeId]?.let { hierarchy -> return hierarchy }
        val type = declarationsById[typeId]
            ?: throw ImmutablePrecompileException(
                declarationId = typeId,
                recoverable = true,
                message = "Cannot resolve immutable type '${typeId.value}'",
            )
        if (!resolving.add(typeId)) {
            throw ImmutablePrecompileException(
                declarationId = typeId,
                message = "Immutable inheritance cycle detected: " +
                    (resolving + typeId).joinToString(" -> ") { id -> id.value },
            )
        }
        val hierarchy = try {
            resolve(type, kindByTypeId.getValue(typeId))
        } finally {
            resolving.remove(typeId)
        }
        cache[typeId] = hierarchy
        return hierarchy
    }

    private fun resolve(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
    ): ImmutableHierarchy {
        val directSuperTypeIds = type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .map(LsiDeclaredType::declarationId)
            .filter(kindByTypeId::containsKey)
        validateSuperTypeKinds(type, kind, directSuperTypeIds)
        val primarySuperTypeIds = directSuperTypeIds.filter { superTypeId ->
            kindByTypeId.getValue(superTypeId) != ImmutableTypeKind.MAPPED_SUPERCLASS
        }
        if (primarySuperTypeIds.size > 1) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot have more than one primary super type: " +
                    primarySuperTypeIds.joinToString { superTypeId -> superTypeId.value },
            )
        }
        val primarySuperTypeId = primarySuperTypeIds.singleOrNull()
        val inheritance = type.annotations.annotation(INHERITANCE_ANNOTATION)
        val discriminatorValue = type.annotations.annotation(DISCRIMINATOR_VALUE_ANNOTATION)
        if (kind != ImmutableTypeKind.ENTITY) {
            validateNonEntityAnnotations(type, inheritance, discriminatorValue)
            return ImmutableHierarchy(
                directSuperTypeIds = directSuperTypeIds,
                primarySuperTypeId = primarySuperTypeId,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = false,
                discriminatorValue = null,
            )
        }
        val primaryEntitySuperTypeId = primarySuperTypeId?.takeIf { superTypeId ->
            kindByTypeId[superTypeId] == ImmutableTypeKind.ENTITY
        }
        if (primaryEntitySuperTypeId != null) {
            return derivedEntityHierarchy(
                type = type,
                directSuperTypeIds = directSuperTypeIds,
                primarySuperTypeId = primarySuperTypeId,
                primaryEntitySuperTypeId = primaryEntitySuperTypeId,
                inheritance = inheritance,
                discriminatorValue = discriminatorValue,
            )
        }
        if (inheritance != null) {
            return rootEntityHierarchy(
                type = type,
                directSuperTypeIds = directSuperTypeIds,
                primarySuperTypeId = primarySuperTypeId,
                inheritance = inheritance,
                discriminatorValue = discriminatorValue,
            )
        }
        if (discriminatorValue != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${DISCRIMINATOR_VALUE_ANNOTATION.value} can only be declared by inheritance entities",
            )
        }
        return ImmutableHierarchy(
            directSuperTypeIds = directSuperTypeIds,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = determineInstantiable(type, null),
            discriminatorValue = null,
        )
    }

    private fun validateSuperTypeKinds(
        type: LsiTypeDeclaration,
        kind: ImmutableTypeKind,
        directSuperTypeIds: List<LsiSymbolId>,
    ) {
        if (directSuperTypeIds.isEmpty()) {
            return
        }
        when (kind) {
            ImmutableTypeKind.ENTITY -> {
                val invalidSuperTypeId = directSuperTypeIds.firstOrNull { superTypeId ->
                    kindByTypeId.getValue(superTypeId) !in setOf(
                        ImmutableTypeKind.ENTITY,
                        ImmutableTypeKind.MAPPED_SUPERCLASS,
                    )
                } ?: return
                throw invalidSuperTypeKind(
                    type = type,
                    superTypeId = invalidSuperTypeId,
                    expected = "an entity or mapped superclass",
                )
            }
            ImmutableTypeKind.MAPPED_SUPERCLASS -> {
                val invalidSuperTypeId = directSuperTypeIds.firstOrNull { superTypeId ->
                    kindByTypeId.getValue(superTypeId) != ImmutableTypeKind.MAPPED_SUPERCLASS
                } ?: return
                throw invalidSuperTypeKind(
                    type = type,
                    superTypeId = invalidSuperTypeId,
                    expected = "a mapped superclass",
                )
            }
            ImmutableTypeKind.EMBEDDABLE -> {
                throw ImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Embeddable immutable type '${type.qualifiedName}' does not support inheritance",
                )
            }
            ImmutableTypeKind.IMMUTABLE -> {
                if (directSuperTypeIds.size > 1) {
                    throw ImmutablePrecompileException(
                        declarationId = type.id,
                        message = "Simple immutable type '${type.qualifiedName}' does not support multiple " +
                            "inheritance",
                    )
                }
                val superTypeId = directSuperTypeIds.single()
                if (kindByTypeId.getValue(superTypeId) != ImmutableTypeKind.IMMUTABLE) {
                    throw invalidSuperTypeKind(
                        type = type,
                        superTypeId = superTypeId,
                        expected = "a simple immutable type",
                    )
                }
            }
        }
    }

    private fun invalidSuperTypeKind(
        type: LsiTypeDeclaration,
        superTypeId: LsiSymbolId,
        expected: String,
    ): ImmutablePrecompileException {
        val superKind = kindByTypeId.getValue(superTypeId)
        return ImmutablePrecompileException(
            declarationId = type.id,
            message = "Immutable type '${type.qualifiedName}' can only inherit $expected, but super type " +
                "'${superTypeId.value}' is ${superKind.description()}",
        )
    }

    private fun validateNonEntityAnnotations(
        type: LsiTypeDeclaration,
        inheritance: LsiAnnotation?,
        discriminatorValue: LsiAnnotation?,
    ) {
        if (inheritance != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${INHERITANCE_ANNOTATION.value} can only be declared by an entity type",
            )
        }
        if (discriminatorValue != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${DISCRIMINATOR_VALUE_ANNOTATION.value} can only be declared by an entity type",
            )
        }
    }

    private fun derivedEntityHierarchy(
        type: LsiTypeDeclaration,
        directSuperTypeIds: List<LsiSymbolId>,
        primarySuperTypeId: LsiSymbolId,
        primaryEntitySuperTypeId: LsiSymbolId,
        inheritance: LsiAnnotation?,
        discriminatorValue: LsiAnnotation?,
    ): ImmutableHierarchy {
        if (inheritance != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${INHERITANCE_ANNOTATION.value} can only be declared by an inheritance root type",
            )
        }
        val rootTypeId = resolve(primaryEntitySuperTypeId).inheritanceRootTypeId
            ?: throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable entity '${type.qualifiedName}' cannot inherit entity " +
                    "'${primaryEntitySuperTypeId.value}' because it is not an inheritance root or derived type",
            )
        val instantiable = determineInstantiable(type, rootTypeId)
        validateDiscriminatorValue(type, discriminatorValue, instantiable)
        return ImmutableHierarchy(
            directSuperTypeIds = directSuperTypeIds,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = rootTypeId,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue(type, discriminatorValue, instantiable),
        )
    }

    private fun rootEntityHierarchy(
        type: LsiTypeDeclaration,
        directSuperTypeIds: List<LsiSymbolId>,
        primarySuperTypeId: LsiSymbolId?,
        inheritance: LsiAnnotation,
        discriminatorValue: LsiAnnotation?,
    ): ImmutableHierarchy {
        val strategy = inheritance.enumEntryName(
            name = "strategy",
            expectedEnumType = INHERITANCE_TYPE,
            sourceId = type.id,
        )?.let { entryName ->
            try {
                InheritanceStrategy.valueOf(entryName)
            } catch (exception: IllegalArgumentException) {
                throw ImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Unsupported immutable inheritance strategy '$entryName'",
                )
            }
        } ?: InheritanceStrategy.SINGLE_TABLE
        val joinedTableDissociateAction = inheritance.enumEntryName(
            name = "joinedTableDissociateAction",
            expectedEnumType = JOINED_TABLE_DISSOCIATE_ACTION_TYPE,
            sourceId = type.id,
        )?.let { entryName ->
            try {
                JoinedTableDissociateAction.valueOf(entryName)
            } catch (exception: IllegalArgumentException) {
                throw ImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Unsupported joined table dissociate action '$entryName'",
                )
            }
        } ?: JoinedTableDissociateAction.DELETE
        if (
            strategy != InheritanceStrategy.JOINED &&
            joinedTableDissociateAction != JoinedTableDissociateAction.DELETE
        ) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "The joinedTableDissociateAction of @${INHERITANCE_ANNOTATION.value} can only be " +
                    "LAX when the inheritance strategy is JOINED",
            )
        }
        val instantiable = determineInstantiable(type, type.id)
        validateDiscriminatorValue(type, discriminatorValue, instantiable)
        return ImmutableHierarchy(
            directSuperTypeIds = directSuperTypeIds,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = type.id,
            inheritanceStrategy = strategy,
            joinedTableDissociateAction = joinedTableDissociateAction,
            instantiable = instantiable,
            discriminatorValue = discriminatorValue(type, discriminatorValue, instantiable),
        )
    }

    private fun determineInstantiable(
        type: LsiTypeDeclaration,
        inheritanceRootTypeId: LsiSymbolId?,
    ): Boolean {
        val entity = requireNotNull(type.annotations.annotation(ENTITY_ANNOTATION)) {
            "Immutable entity '${type.qualifiedName}' must be decorated by @${ENTITY_ANNOTATION.value}"
        }
        val entryName = entity.enumEntryName(
            name = "instantiability",
            expectedEnumType = ENTITY_INSTANTIABILITY_TYPE,
            sourceId = type.id,
        ) ?: "AUTO"
        return when (entryName) {
            "AUTO" -> inheritanceRootTypeId == null || inheritanceRootTypeId != type.id
            "ABSTRACT" -> {
                if (inheritanceRootTypeId == null) {
                    throw ImmutablePrecompileException(
                        declarationId = type.id,
                        message = "@${ENTITY_ANNOTATION.value}(instantiability = ABSTRACT) can only be used by " +
                            "inheritance entity types",
                    )
                }
                false
            }
            "INSTANTIABLE" -> true
            else -> throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "Unsupported entity instantiability '$entryName'",
            )
        }
    }

    private fun validateDiscriminatorValue(
        type: LsiTypeDeclaration,
        discriminatorValue: LsiAnnotation?,
        instantiable: Boolean,
    ) {
        if (!instantiable && discriminatorValue != null) {
            throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${DISCRIMINATOR_VALUE_ANNOTATION.value} can only be declared by instantiable " +
                    "inheritance entity types",
            )
        }
    }

    private fun discriminatorValue(
        type: LsiTypeDeclaration,
        annotation: LsiAnnotation?,
        instantiable: Boolean,
    ): String? {
        if (!instantiable) {
            return null
        }
        if (annotation == null) {
            return type.name
        }
        return annotation.stringValue("value")
            ?: throw ImmutablePrecompileException(
                declarationId = type.id,
                message = "@${DISCRIMINATOR_VALUE_ANNOTATION.value} must declare its typed string value",
            )
    }
}

private fun orderResolvedProperties(
    type: LsiTypeDeclaration,
    resolvedProps: List<LsiResolvedProperty>,
    workspace: LsiWorkspace,
): List<LsiResolvedProperty> {
    val resolvedPropsByName = resolvedProps.associateBy { property -> property.declaration.name }
    val orderedNames = linkedSetOf<String>()
    val visitedTypeIds = mutableSetOf<LsiSymbolId>()

    fun collectSlots(typeDeclaration: LsiTypeDeclaration) {
        if (!visitedTypeIds.add(typeDeclaration.id)) {
            return
        }
        typeDeclaration.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .mapNotNull { superType -> workspace[superType.declarationId] as? LsiTypeDeclaration }
            .forEach(::collectSlots)
        typeDeclaration.memberIds
            .mapNotNull { memberId -> workspace[memberId] as? LsiProperty }
            .forEach { property -> orderedNames += property.name }
    }

    collectSlots(type)
    val orderedProps = orderedNames.mapNotNull(resolvedPropsByName::get)
    check(orderedProps.size == resolvedProps.size) {
        "Cannot determine stable immutable property order for '${type.qualifiedName}'"
    }
    return orderedProps
}

fun LsiTypeDeclaration.immutableTypeKind(): ImmutableTypeKind? {
    val markers = IMMUTABLE_TYPE_ANNOTATIONS.mapNotNull { (annotationType, kind) ->
        kind.takeIf { annotations.hasAnnotation(annotationType) }
    }
    if (markers.size > 1) {
        throw ImmutablePrecompileException(
            declarationId = id,
            message = "Immutable type '$qualifiedName' has conflicting immutable annotations",
        )
    }
    return markers.singleOrNull()
}

private fun LsiTypeDeclaration.microServiceMetadata(
    kind: ImmutableTypeKind,
): MicroServiceMetadata {
    val marker = when (kind) {
        ImmutableTypeKind.ENTITY -> annotations.annotation(ENTITY_ANNOTATION)
        ImmutableTypeKind.MAPPED_SUPERCLASS -> annotations.annotation(MAPPED_SUPERCLASS_ANNOTATION)
        ImmutableTypeKind.IMMUTABLE,
        ImmutableTypeKind.EMBEDDABLE,
        -> null
    }
    return MicroServiceMetadata(
        acrossMicroServices = kind == ImmutableTypeKind.MAPPED_SUPERCLASS &&
            marker?.booleanValue("acrossMicroServices") == true,
        microServiceName = marker?.stringValue("microServiceName").orEmpty(),
    )
}

fun LsiTypeDeclaration.isJimmerImmutableType(): Boolean {
    return annotations.any { annotation -> annotation.type in IMMUTABLE_TYPE_ANNOTATION_IDS }
}

fun LsiWorkspace.jimmerImmutableTypeIds(): Set<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .filter(LsiTypeDeclaration::isJimmerImmutableType)
        .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
}

private fun managedTypeClosure(
    targetTypeIds: Set<LsiSymbolId>,
    typeDeclarations: List<LsiTypeDeclaration>,
    kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
): Set<LsiSymbolId> {
    val declarationsById = typeDeclarations.associateBy(LsiTypeDeclaration::id)
    val result = sortedSetOf<LsiSymbolId>()
    val pending = ArrayDeque(targetTypeIds.sorted())
    while (pending.isNotEmpty()) {
        val typeId = pending.removeFirst()
        if (!result.add(typeId)) {
            continue
        }
        val type = declarationsById[typeId] ?: continue
        type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .map(LsiDeclaredType::declarationId)
            .filter { superTypeId -> superTypeId in kindByTypeId }
            .sorted()
            .forEach(pending::addLast)
        val propertyTypes = try {
            typeSystem.effectiveProperties(typeId).map(LsiResolvedProperty::type)
        } catch (_: IllegalArgumentException) {
            type.memberIds
                .mapNotNull { memberId -> workspace[memberId] as? LsiProperty }
                .map(LsiProperty::type)
        }
        propertyTypes
            .flatMap { propertyType -> propertyType.managedTypeIds(kindByTypeId) }
            .distinct()
            .sorted()
            .forEach(pending::addLast)
    }
    return result
}

private fun LsiTypeRef.managedTypeIds(
    kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
): List<LsiSymbolId> {
    return when (this) {
        is LsiDeclaredType -> buildList {
            if (declarationId in kindByTypeId) {
                add(declarationId)
            }
            arguments.forEach { argument ->
                argument.type?.managedTypeIds(kindByTypeId)?.let(::addAll)
            }
        }
        is LsiArrayType -> elementType.managedTypeIds(kindByTypeId)
        is LsiFunctionType -> buildList {
            receiverType?.managedTypeIds(kindByTypeId)?.let(::addAll)
            parameterTypes.forEach { parameter ->
                addAll(parameter.managedTypeIds(kindByTypeId))
            }
            addAll(returnType.managedTypeIds(kindByTypeId))
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> emptyList()
    }
}

private fun LsiWorkspace.hasUnresolvedImmutableType(targetTypeId: LsiSymbolId): Boolean {
    val pending = ArrayDeque<LsiSymbolId>()
    val visited = mutableSetOf<LsiSymbolId>()
    pending += targetTypeId
    while (pending.isNotEmpty()) {
        val typeId = pending.removeFirst()
        if (!visited.add(typeId)) {
            continue
        }
        val type = this[typeId] as? LsiTypeDeclaration ?: return true
        if (
            type.superTypes.any(LsiTypeRef::containsUnresolvedType) ||
            type.typeParameters.any(LsiTypeParameter::containsUnresolvedType) ||
            type.annotations.any(LsiAnnotation::containsUnresolvedType)
        ) {
            return true
        }
        for (memberId in type.memberIds) {
            val property = this[memberId] as? LsiProperty ?: continue
            if (
                property.type.containsUnresolvedType() ||
                property.annotations.any(LsiAnnotation::containsUnresolvedType) ||
                property.overrides.any { override -> !contains(override.declarationId) }
            ) {
                return true
            }
        }
        type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .map(LsiDeclaredType::declarationId)
            .mapNotNull { superTypeId -> this[superTypeId] as? LsiTypeDeclaration }
            .filter(LsiTypeDeclaration::isJimmerImmutableType)
            .map(LsiTypeDeclaration::id)
            .sorted()
            .forEach(pending::addLast)
    }
    return false
}

private fun LsiTypeParameter.containsUnresolvedType(): Boolean {
    return upperBounds.any(LsiTypeRef::containsUnresolvedType)
}

private fun LsiTypeRef.containsUnresolvedType(): Boolean {
    return when (this) {
        is LsiUnresolvedType -> true
        is LsiDeclaredType -> arguments.any { argument -> argument.type?.containsUnresolvedType() == true }
        is LsiArrayType -> elementType.containsUnresolvedType()
        is LsiFunctionType ->
            receiverType?.containsUnresolvedType() == true ||
                parameterTypes.any(LsiTypeRef::containsUnresolvedType) ||
                returnType.containsUnresolvedType()
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        -> false
    }
}

private fun LsiAnnotation.containsUnresolvedType(): Boolean {
    return arguments.values.any { argument -> argument.value.containsUnresolvedType() }
}

private fun LsiAnnotationValue.containsUnresolvedType(): Boolean {
    return when (this) {
        is LsiAnnotationValue.ClassValue -> type.containsUnresolvedType()
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.containsUnresolvedType()
        is LsiAnnotationValue.ArrayValue -> elements.any(LsiAnnotationValue::containsUnresolvedType)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        is LsiAnnotationValue.EnumValue,
        -> false
    }
}

private fun LsiResolvedProperty.toImmutableProp(
    ownerTypeId: LsiSymbolId,
    kindByTypeId: Map<LsiSymbolId, ImmutableTypeKind>,
    microServiceMetadataByTypeId: Map<LsiSymbolId, MicroServiceMetadata>,
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
): ImmutableProp {
    val ownerKind = kindByTypeId.getValue(ownerTypeId)
    val primaryAnnotation = primaryMappingAnnotation()
    val formulaKind = formulaKind()
    val transientResolver = transientResolver()
    validateFormulaContract(
        ownerKind = ownerKind,
    )
    val explicitScalar = annotations.any { annotation ->
        annotation.findAnnotation(SCALAR_ANNOTATIONS, workspace, linkedSetOf()) != null
    }
    val languageFormula = formulaKind == FormulaKind.LANGUAGE ||
        formulaKind == FormulaKind.ABSTRACT && declaration.origin.language == LsiLanguage.JAVA
    val collection = type.isCollectionType(typeSystem)
    if (collection && !explicitScalar && !languageFormula) {
        if (!type.isImmutableListType()) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable collection property '${declaration.id.value}' must use java.util.List " +
                    "unless it has scalar or language-formula semantics",
            )
        }
        validateListShape()
    }
    val list = collection && !explicitScalar && !languageFormula
    val genericTarget = type.targetType(list) is LsiTypeParameterRef
    val targetTypeId = type.targetTypeId(list)
    val associationKind = associationKind()
    val targetKind = targetTypeId?.let(kindByTypeId::get)
    val association = associationKind != AssociationKind.NONE ||
        targetKind == ImmutableTypeKind.ENTITY
    val nullable = nullable()
    val primaryMapping = primaryAnnotation?.type.toPrimaryMapping()
        ?: if (association) PrimaryMapping.ASSOCIATION
        else PrimaryMapping.SCALAR
    val embedded = targetKind == ImmutableTypeKind.EMBEDDABLE
    val immutableDefault = immutableDefault(
        ownerKind = ownerKind,
        primaryMapping = primaryMapping,
        association = association,
        embedded = embedded,
        workspace = workspace,
    )
    val mappedBy = mappedBy(associationKind)
    val associationStorage = associationStorage(
        associationKind = associationKind,
        primaryMapping = primaryMapping,
        mappedBy = mappedBy,
        nullable = nullable,
    )
    validatePropertyCategory(
        ownerTypeId = ownerTypeId,
        ownerKind = ownerKind,
        list = list,
        genericTarget = genericTarget,
        targetTypeId = targetTypeId,
        targetKind = targetKind,
        associationKind = associationKind,
        primaryMapping = primaryMapping,
    )
    val manyToManyView = annotations.hasAnnotation(MANY_TO_MANY_VIEW_ANNOTATION)
    val ownerMicroServiceMetadata = microServiceMetadataByTypeId.getValue(ownerTypeId)
    val targetMicroServiceMetadata = targetTypeId?.let(microServiceMetadataByTypeId::get)
    val remote = association &&
        targetKind == ImmutableTypeKind.ENTITY &&
        targetMicroServiceMetadata != null &&
        targetMicroServiceMetadata.microServiceName != ownerMicroServiceMetadata.microServiceName
    validateMicroServiceAssociation(
        ownerTypeId = ownerTypeId,
        targetTypeId = targetTypeId,
        targetKind = targetKind,
        association = association,
        associationKind = associationKind,
        primaryMapping = primaryMapping,
        nullable = nullable,
        ownerMicroServiceMetadata = ownerMicroServiceMetadata,
        targetMicroServiceMetadata = targetMicroServiceMetadata,
        remote = remote,
    )
    val recursive = ownerKind == ImmutableTypeKind.ENTITY &&
        targetKind == ImmutableTypeKind.ENTITY &&
        !manyToManyView &&
        !genericTarget &&
        !remote &&
        typeSystem.resolveSuperType(requireNotNull(targetTypeId), ownerTypeId) != null
    return ImmutableProp(
        id = LsiSymbolId.property(ownerTypeId, declaration.name),
        declarationId = declaration.id,
        ownerTypeId = ownerTypeId,
        declaringTypeId = declaration.ownerId,
        name = declaration.name,
        documentation = declaration.documentation
            ?: overrideChain.drop(1).firstNotNullOfOrNull(LsiProperty::documentation),
        type = type.withRootNullability(nullable),
        annotations = annotations.filterNot { annotation ->
            annotation.type in NON_SEMANTIC_OVERRIDE_ANNOTATIONS
        },
        overrideChain = overrideChain.map { property -> property.id },
        inherited = declaration.ownerId != ownerTypeId,
        overridden = declaration.ownerId == ownerTypeId && overrideChain.size > 1,
        nullable = nullable,
        list = list,
        association = association,
        embedded = embedded,
        targetTypeId = targetTypeId,
        primaryMapping = primaryMapping,
        primaryAnnotationTypeId = primaryAnnotation?.type,
        defaultContract = immutableDefault,
        associationKind = if (associationKind == AssociationKind.NONE && association) {
            AssociationKind.IMPLICIT
        } else {
            associationKind
        },
        formulaKind = formulaKind,
        mappedBy = mappedBy,
        associationStorage = associationStorage,
        transientResolver = transientResolver,
        view = null,
        genericTarget = genericTarget,
        remote = remote,
        recursive = recursive,
        validations = validations(workspace),
        converter = converter(workspace, typeSystem, nullable, association),
    )
}

private fun LsiResolvedProperty.validateListShape() {
    val listType = type as? LsiDeclaredType
        ?: throw invalidListShape("must declare exactly one invariant, non-star element type")
    val elementArgument = listType.arguments.singleOrNull()
    if (
        elementArgument == null ||
        elementArgument.variance != LsiVariance.INVARIANT ||
        elementArgument.type == null
    ) {
        throw invalidListShape("must declare exactly one invariant, non-star element type")
    }
    val elementType = requireNotNull(elementArgument.type)
    val validElement = when (elementType) {
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        -> true
        is LsiDeclaredType -> elementType.arguments.isEmpty()
        is LsiArrayType,
        is LsiFunctionType,
        is LsiUnresolvedType,
        -> false
    }
    if (!validElement) {
        throw invalidListShape(
            "element type must be primitive, a non-parameterized declared type or a direct type parameter",
        )
    }
}

private fun LsiResolvedProperty.invalidListShape(message: String): ImmutablePrecompileException {
    return ImmutablePrecompileException(
        declarationId = declaration.id,
        message = "Immutable list property '${declaration.id.value}' $message",
    )
}

private fun LsiResolvedProperty.immutableDefault(
    ownerKind: ImmutableTypeKind,
    primaryMapping: PrimaryMapping,
    association: Boolean,
    embedded: Boolean,
    workspace: LsiWorkspace,
): ImmutableDefault? {
    val application = annotations.annotation(DEFAULT_ANNOTATION)
    val database = annotations.annotation(DATABASE_DEFAULT_ANNOTATION)
    if (application != null && database != null) {
        throw invalidDefault(
            "cannot be decorated by both @${DEFAULT_ANNOTATION.requireTypeQualifiedName()} and " +
                "@${DATABASE_DEFAULT_ANNOTATION.requireTypeQualifiedName()}",
        )
    }
    if (application != null) {
        if (
            ownerKind !in setOf(ImmutableTypeKind.ENTITY, ImmutableTypeKind.MAPPED_SUPERCLASS) ||
            primaryMapping !in setOf(
                PrimaryMapping.VERSION,
                PrimaryMapping.LOGICAL_DELETED,
                PrimaryMapping.SCALAR,
            ) ||
            association ||
            embedded
        ) {
            throw invalidDefault(
                "decorated by @${DEFAULT_ANNOTATION.requireTypeQualifiedName()} must be a scalar column, " +
                    "version or logical-deleted property of an entity or mapped superclass",
            )
        }
        if (primaryMapping == PrimaryMapping.LOGICAL_DELETED &&
            !type.acceptsLogicalDeletedDefault(workspace)
        ) {
            throw invalidDefault(
                "cannot combine @${DEFAULT_ANNOTATION.requireTypeQualifiedName()} with " +
                    "@${LOGICAL_DELETED_ANNOTATION.requireTypeQualifiedName()} unless its type is Int or enum",
            )
        }
        val rawValue = application.stringValue("value")
            ?: throw invalidDefault(
                "@${DEFAULT_ANNOTATION.requireTypeQualifiedName()} must declare its typed string value",
            )
        return ImmutableDefault.Application(
            annotationValue = rawValue,
            strategy = when {
                rawValue.isNotEmpty() || primaryMapping == PrimaryMapping.VERSION -> {
                    ApplicationDefaultStrategy.DECLARED_VALUE
                }
                primaryMapping == PrimaryMapping.LOGICAL_DELETED -> {
                    ApplicationDefaultStrategy.LOGICAL_DELETED
                }
                else -> null
            },
        )
    }
    if (database != null) {
        if (
            ownerKind !in setOf(ImmutableTypeKind.ENTITY, ImmutableTypeKind.MAPPED_SUPERCLASS) ||
            primaryMapping != PrimaryMapping.SCALAR ||
            association ||
            embedded ||
            annotations.hasAnnotation(KEY_ANNOTATION) ||
            annotations.hasAnnotation(KEYS_ANNOTATION)
        ) {
            throw invalidDefault(
                "decorated by @${DATABASE_DEFAULT_ANNOTATION.requireTypeQualifiedName()} must be a scalar column " +
                    "property and cannot be id, key, version, logical deleted, association, embedded, formula, " +
                    "transient or view",
            )
        }
        return ImmutableDefault.Database(
            expression = database.databaseDefaultExpression(declaration.id),
        )
    }
    return when (primaryMapping) {
        PrimaryMapping.VERSION -> ImmutableDefault.Application(
            annotationValue = null,
            strategy = ApplicationDefaultStrategy.VERSION_ZERO,
        )
        PrimaryMapping.LOGICAL_DELETED -> ImmutableDefault.Application(
            annotationValue = null,
            strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
        )
        else -> null
    }
}

private fun LsiTypeRef.acceptsLogicalDeletedDefault(workspace: LsiWorkspace): Boolean {
    if (this is LsiPrimitiveType && kind == LsiPrimitiveKind.INT && !boxed) {
        return true
    }
    val declaredType = this as? LsiDeclaredType ?: return false
    return (workspace[declaredType.declarationId] as? LsiTypeDeclaration)?.kind == LsiTypeDeclarationKind.ENUM
}

private fun LsiResolvedProperty.invalidDefault(message: String): ImmutablePrecompileException {
    return ImmutablePrecompileException(
        declarationId = declaration.id,
        message = "Immutable property '${declaration.id.value}' $message",
    )
}

private fun LsiAnnotation.databaseDefaultExpression(propId: LsiSymbolId): String? {
    val value = arguments["value"]?.value ?: return null
    if (value !is LsiAnnotationValue.StringValue) {
        throw ImmutablePrecompileException(
            declarationId = propId,
            message = "Immutable property '${propId.value}' decorated by " +
                "@${DATABASE_DEFAULT_ANNOTATION.requireTypeQualifiedName()} must declare a typed string value",
        )
    }
    return value.value.takeIf(String::isNotBlank)
}

private fun LsiResolvedProperty.mappedBy(
    associationKind: AssociationKind,
): MappedBy? {
    val associationAnnotationType = when (associationKind) {
        AssociationKind.ONE_TO_ONE -> ONE_TO_ONE_ANNOTATION
        AssociationKind.ONE_TO_MANY -> ONE_TO_MANY_ANNOTATION
        AssociationKind.MANY_TO_MANY -> MANY_TO_MANY_ANNOTATION
        AssociationKind.NONE,
        AssociationKind.IMPLICIT,
        AssociationKind.MANY_TO_ONE,
        AssociationKind.MANY_TO_MANY_VIEW,
        -> return null
    }
    val associationAnnotation = annotations.annotation(associationAnnotationType) ?: return null
    val mappedByName = associationAnnotation.typedStringValue("mappedBy", declaration.id).orEmpty()
    if (associationKind == AssociationKind.ONE_TO_MANY &&
        associationAnnotation.arguments.containsKey("mappedBy") &&
        mappedByName.isEmpty()
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable one-to-many property '${declaration.id.value}' must declare a non-empty mappedBy",
        )
    }
    return mappedByName.takeIf(String::isNotEmpty)?.let { name ->
        MappedBy(name = name, ownerPropId = null)
    }
}

private fun LsiResolvedProperty.associationStorage(
    associationKind: AssociationKind,
    primaryMapping: PrimaryMapping,
    mappedBy: MappedBy?,
    nullable: Boolean,
): AssociationStorageKind {
    val hasJoinColumns = annotations.hasAnnotation(JOIN_COLUMN_ANNOTATION) ||
        annotations.hasAnnotation(JOIN_COLUMNS_ANNOTATION)
    val hasJoinTable = annotations.hasAnnotation(JOIN_TABLE_ANNOTATION)
    val hasJoinSql = annotations.hasAnnotation(JOIN_SQL_ANNOTATION)
    val storageAnnotations = buildList {
        if (hasJoinColumns) add(if (annotations.hasAnnotation(JOIN_COLUMN_ANNOTATION)) {
            JOIN_COLUMN_ANNOTATION
        } else {
            JOIN_COLUMNS_ANNOTATION
        })
        if (hasJoinTable) add(JOIN_TABLE_ANNOTATION)
        if (hasJoinSql) add(JOIN_SQL_ANNOTATION)
    }
    if (storageAnnotations.size > 1) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot declare conflicting association storage " +
                "annotations: ${storageAnnotations.joinToString { annotation -> "@${annotation.value}" }}",
        )
    }
    if (storageAnnotations.isNotEmpty() && primaryMapping != PrimaryMapping.ASSOCIATION) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' can only declare association storage " +
                "annotations on a persistent association",
        )
    }
    if (mappedBy != null && storageAnnotations.isNotEmpty()) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Inverse immutable association property '${declaration.id.value}' cannot declare association storage",
        )
    }
    if (hasJoinColumns && associationKind !in COLUMN_ASSOCIATION_KINDS) {
        throw invalidAssociationStorageAnnotation(JOIN_COLUMN_ANNOTATION, associationKind)
    }
    if (hasJoinTable && associationKind !in MIDDLE_TABLE_ASSOCIATION_KINDS) {
        throw invalidAssociationStorageAnnotation(JOIN_TABLE_ANNOTATION, associationKind)
    }
    if (hasJoinSql && associationKind != AssociationKind.MANY_TO_MANY) {
        throw invalidAssociationStorageAnnotation(JOIN_SQL_ANNOTATION, associationKind)
    }
    if (
        associationKind in LIST_ASSOCIATION_KINDS &&
        primaryMapping == PrimaryMapping.ASSOCIATION &&
        nullable
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable list association property '${declaration.id.value}' cannot be nullable",
        )
    }
    if (mappedBy != null && associationKind == AssociationKind.ONE_TO_ONE && !nullable) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Inverse one-to-one property '${declaration.id.value}' must be nullable",
        )
    }
    if (hasJoinTable && associationKind in COLUMN_ASSOCIATION_KINDS && !nullable) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "To-one property '${declaration.id.value}' using @${JOIN_TABLE_ANNOTATION.value} must be nullable",
        )
    }
    return when {
        primaryMapping != PrimaryMapping.ASSOCIATION -> AssociationStorageKind.NONE
        mappedBy != null || hasJoinSql -> AssociationStorageKind.NONE
        hasJoinTable || associationKind == AssociationKind.MANY_TO_MANY ->
            AssociationStorageKind.MIDDLE_TABLE
        associationKind in COLUMN_ASSOCIATION_KINDS -> AssociationStorageKind.COLUMN
        else -> AssociationStorageKind.NONE
    }
}

private fun LsiResolvedProperty.invalidAssociationStorageAnnotation(
    annotationType: LsiSymbolId,
    associationKind: AssociationKind,
): ImmutablePrecompileException {
    return ImmutablePrecompileException(
        declarationId = declaration.id,
        message = "Immutable association property '${declaration.id.value}' of kind $associationKind cannot be " +
            "decorated by @${annotationType.value}",
    )
}

private fun LsiResolvedProperty.transientResolver(): TransientResolver? {
    val transient = annotations.annotation(TRANSIENT_ANNOTATION) ?: return null
    val resolverTypeId = transient.transientResolverTypeId("value", declaration.id)
    val resolverRef = transient.typedStringValue("ref", declaration.id).orEmpty()
    if (resolverTypeId != null && resolverRef.isNotEmpty()) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable transient property '${declaration.id.value}' cannot specify both " +
                "resolver type and resolver reference",
        )
    }
    return when {
        resolverTypeId != null -> TransientResolver.Type(resolverTypeId)
        resolverRef.isNotEmpty() -> TransientResolver.Reference(resolverRef)
        else -> null
    }
}

private fun LsiResolvedProperty.validateFormulaContract(
    ownerKind: ImmutableTypeKind,
) {
    val formula = annotations.annotation(FORMULA_ANNOTATION) ?: return
    val sql = formula.stringValue("sql").orEmpty()
    val dependencies = formula.stringValues("dependencies", declaration.id)
    if (ownerKind == ImmutableTypeKind.EMBEDDABLE && sql.isNotEmpty()) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "The sql based formula property cannot be declared in embeddable type",
        )
    }
    if (declaration.modality == LsiModality.ABSTRACT) {
        if (sql.isEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable abstract formula property '${declaration.id.value}' must specify sql",
            )
        }
        if (dependencies.isNotEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable abstract formula property '${declaration.id.value}' cannot specify dependencies",
            )
        }
    } else {
        if (sql.isNotEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable non-abstract formula property '${declaration.id.value}' cannot specify sql",
            )
        }
        if (dependencies.isEmpty()) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable non-abstract formula property '${declaration.id.value}' must specify dependencies",
            )
        }
    }
}

private fun LsiResolvedProperty.validatePropertyCategory(
    ownerTypeId: LsiSymbolId,
    ownerKind: ImmutableTypeKind,
    list: Boolean,
    genericTarget: Boolean,
    targetTypeId: LsiSymbolId?,
    targetKind: ImmutableTypeKind?,
    associationKind: AssociationKind,
    primaryMapping: PrimaryMapping,
) {
    if (targetKind == ImmutableTypeKind.MAPPED_SUPERCLASS) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot target mapped superclass " +
                "'${requireNotNull(targetTypeId).value}'",
        )
    }
    if (list && targetKind == ImmutableTypeKind.EMBEDDABLE) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot be a list of embeddable type " +
                "'${requireNotNull(targetTypeId).value}'",
        )
    }
    if (associationKind != AssociationKind.NONE) {
        if (ownerKind !in ASSOCIATION_DECLARING_TYPE_KINDS) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable property '${declaration.id.value}' cannot declare " +
                    "@${associationKind.annotationType().value} because '${ownerTypeId.value}' is not an entity " +
                    "or mapped superclass",
            )
        }
        when (associationKind) {
            AssociationKind.ONE_TO_ONE,
            AssociationKind.MANY_TO_ONE,
            -> if (list) {
                throw ImmutablePrecompileException(
                    declarationId = declaration.id,
                    message = "Immutable list association property '${declaration.id.value}' must be decorated by " +
                        "@${ONE_TO_MANY_ANNOTATION.value}, @${MANY_TO_MANY_ANNOTATION.value} or " +
                        "@${MANY_TO_MANY_VIEW_ANNOTATION.value}",
                )
            }
            AssociationKind.ONE_TO_MANY,
            AssociationKind.MANY_TO_MANY,
            AssociationKind.MANY_TO_MANY_VIEW,
            -> if (!list) {
                throw ImmutablePrecompileException(
                    declarationId = declaration.id,
                    message = "Immutable property '${declaration.id.value}' is not a list, so it cannot be " +
                        "decorated by @${associationKind.annotationType().value}",
                )
            }
            AssociationKind.NONE,
            AssociationKind.IMPLICIT,
            -> Unit
        }
        if (!genericTarget && targetKind != ImmutableTypeKind.ENTITY) {
            val targetType = type.targetType(list)
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable association property '${declaration.id.value}' target type " +
                    "'${targetTypeId?.value ?: targetType?.jimmerTypeSignature() ?: "<unknown>"}' must be an entity",
            )
        }
    }
    if (
        associationKind == AssociationKind.NONE &&
        targetKind == ImmutableTypeKind.ENTITY &&
        ownerKind != ImmutableTypeKind.IMMUTABLE &&
        primaryMapping != PrimaryMapping.TRANSIENT
    ) {
        val requiredAnnotations = if (list) {
            "@${ONE_TO_MANY_ANNOTATION.value}, @${MANY_TO_MANY_ANNOTATION.value} or " +
                "@${MANY_TO_MANY_VIEW_ANNOTATION.value}"
        } else {
            "@${MANY_TO_ONE_ANNOTATION.value} or @${ONE_TO_ONE_ANNOTATION.value}"
        }
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' targets entity " +
                "'${requireNotNull(targetTypeId).value}' and must be decorated by $requiredAnnotations",
        )
    }
    if (
        associationKind == AssociationKind.NONE &&
        targetKind == ImmutableTypeKind.IMMUTABLE &&
        ownerKind != ImmutableTypeKind.IMMUTABLE &&
        primaryMapping != PrimaryMapping.TRANSIENT &&
        !list
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' target type " +
                "'${requireNotNull(targetTypeId).value}' is immutable but not embeddable",
        )
    }
}

private fun LsiResolvedProperty.validateMicroServiceAssociation(
    ownerTypeId: LsiSymbolId,
    targetTypeId: LsiSymbolId?,
    targetKind: ImmutableTypeKind?,
    association: Boolean,
    associationKind: AssociationKind,
    primaryMapping: PrimaryMapping,
    nullable: Boolean,
    ownerMicroServiceMetadata: MicroServiceMetadata,
    targetMicroServiceMetadata: MicroServiceMetadata?,
    remote: Boolean,
) {
    if (
        ownerMicroServiceMetadata.acrossMicroServices &&
        association &&
        targetKind == ImmutableTypeKind.ENTITY &&
        primaryMapping != PrimaryMapping.TRANSIENT
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot declare an entity association because " +
                "mapped superclass '${ownerTypeId.value}' is across microservices",
        )
    }
    if (!remote) {
        return
    }
    checkNotNull(targetTypeId)
    checkNotNull(targetMicroServiceMetadata)
    if (
        ownerMicroServiceMetadata.microServiceName.isEmpty() ||
        targetMicroServiceMetadata.microServiceName.isEmpty()
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Remote association '${declaration.id.value}' requires non-empty micro service names for " +
                "both declaring type '${ownerTypeId.value}' and target type '${targetTypeId.value}'",
        )
    }
    if (associationKind in COLUMN_ASSOCIATION_KINDS && !nullable) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Remote association '${declaration.id.value}' must be nullable",
        )
    }
    if (annotations.hasAnnotation(JOIN_SQL_ANNOTATION)) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Remote association '${declaration.id.value}' cannot be decorated by " +
                "@${JOIN_SQL_ANNOTATION.value}",
        )
    }
}

private fun LsiResolvedProperty.validations(workspace: LsiWorkspace): List<ImmutableValidation> {
    return annotations.mapNotNull { annotation ->
        val annotationType = workspace[annotation.type] as? LsiTypeDeclaration ?: return@mapNotNull null
        val constraint = annotationType.annotations.annotation(CONSTRAINT_ANNOTATIONS) ?: return@mapNotNull null
        val validatorTypeIds = constraint.classTypeIds("validatedBy")
        if (validatorTypeIds.isEmpty()) {
            return@mapNotNull null
        }
        ImmutableValidation(
            annotationTypeId = annotation.type,
            validatorTypeIds = validatorTypeIds.sorted(),
            message = annotation.stringValue("message").orEmpty(),
            sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
        )
    }.sortedBy(ImmutableValidation::annotationTypeId)
}

private fun LsiResolvedProperty.converter(
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
    propertyNullable: Boolean,
    association: Boolean,
): ImmutableConverter? {
    val converterAnnotation = annotations.firstNotNullOfOrNull { annotation ->
        annotation.findAnnotation(JSON_CONVERTER_ANNOTATIONS, workspace, linkedSetOf())
    } ?: return null
    if (association) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable association property '${declaration.id.value}' cannot declare @" +
                JSON_CONVERTER_ANNOTATION.value,
        )
    }
    val jsonFormatAnnotation = annotations.firstNotNullOfOrNull { annotation ->
        annotation.findAnnotation(JSON_FORMAT_ANNOTATIONS, workspace, linkedSetOf())
    }
    if (jsonFormatAnnotation != null) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot declare both @" +
                "${converterAnnotation.type.value} and @${jsonFormatAnnotation.type.value}",
        )
    }
    val converterTypeId = converterAnnotation.classTypeId("value") ?: return null
    val converterType = typeSystem.resolveSuperType(converterTypeId, CONVERTER_TYPE_ID)
    val sourceType = converterType?.arguments?.getOrNull(0)?.type
    val targetType = converterType?.arguments?.getOrNull(1)?.type
    return ImmutableConverter(
        converterTypeId = converterTypeId,
        sourceType = sourceType,
        targetType = targetType,
        sourceNullable = sourceType?.nullability == LsiNullability.NULLABLE,
        targetNullable = targetType?.nullability == LsiNullability.NULLABLE,
        propertyNullable = propertyNullable,
    )
}

private fun LsiAnnotation.findAnnotation(
    targetTypes: Set<LsiSymbolId>,
    workspace: LsiWorkspace,
    visited: MutableSet<LsiSymbolId>,
): LsiAnnotation? {
    if (type in targetTypes) {
        return this
    }
    if (!visited.add(type)) {
        return null
    }
    val annotationType = workspace[type] as? LsiTypeDeclaration ?: return null
    return annotationType.annotations.firstNotNullOfOrNull { annotation ->
        annotation.findAnnotation(targetTypes, workspace, visited)
    }
}

private fun LsiResolvedProperty.associationKind(): AssociationKind {
    return when {
        annotations.hasAnnotation(ONE_TO_ONE_ANNOTATION) -> AssociationKind.ONE_TO_ONE
        annotations.hasAnnotation(MANY_TO_ONE_ANNOTATION) -> AssociationKind.MANY_TO_ONE
        annotations.hasAnnotation(ONE_TO_MANY_ANNOTATION) -> AssociationKind.ONE_TO_MANY
        annotations.hasAnnotation(MANY_TO_MANY_ANNOTATION) -> AssociationKind.MANY_TO_MANY
        annotations.hasAnnotation(MANY_TO_MANY_VIEW_ANNOTATION) -> AssociationKind.MANY_TO_MANY_VIEW
        else -> AssociationKind.NONE
    }
}

private fun AssociationKind.annotationType(): LsiSymbolId {
    return when (this) {
        AssociationKind.ONE_TO_ONE -> ONE_TO_ONE_ANNOTATION
        AssociationKind.MANY_TO_ONE -> MANY_TO_ONE_ANNOTATION
        AssociationKind.ONE_TO_MANY -> ONE_TO_MANY_ANNOTATION
        AssociationKind.MANY_TO_MANY -> MANY_TO_MANY_ANNOTATION
        AssociationKind.MANY_TO_MANY_VIEW -> MANY_TO_MANY_VIEW_ANNOTATION
        AssociationKind.NONE,
        AssociationKind.IMPLICIT,
        -> error("Association kind $this has no annotation type")
    }
}

private fun LsiResolvedProperty.formulaKind(): FormulaKind {
    val formula = annotations.annotation(FORMULA_ANNOTATION) ?: return FormulaKind.NONE
    if (!formula.stringValue("sql").isNullOrBlank()) {
        return FormulaKind.SQL
    }
    return if (declaration.modality == LsiModality.ABSTRACT) {
        FormulaKind.ABSTRACT
    } else {
        FormulaKind.LANGUAGE
    }
}

private fun ImmutableProp.defaultIdViewBasePropName(): String? {
    if (list || name.length <= 2 || !name.endsWith("Id")) {
        return null
    }
    if (name[name.length - 3].isUpperCase()) {
        return null
    }
    return name.dropLast(2)
}

private fun ImmutableProp.valueType(): LsiTypeRef {
    if (!list) {
        return type
    }
    return (type as? LsiDeclaredType)
        ?.arguments
        ?.singleOrNull()
        ?.type
        ?: type
}

private fun LsiTypeRef.boxedTypeSignature(
    ignoreRootNullability: Boolean,
    root: Boolean = true,
): String {
    val base = when (this) {
        is LsiPrimitiveType -> buildString {
            append("scalar:${kind.name}")
            if (!root) {
                append(if (boxed) ":boxed" else ":primitive")
            }
        }
        is LsiDeclaredType -> BOXED_PRIMITIVE_KINDS[declarationId]
            ?.let { kind ->
                buildString {
                    append("scalar:${kind.name}")
                    if (!root) {
                        append(":boxed")
                    }
                }
            }
            ?: buildString {
                append(
                    if (declarationId in LIST_TYPE_IDS || declarationId == KOTLIN_MUTABLE_LIST_TYPE_ID) {
                        CONVERTER_LIST_TYPE.value
                    } else {
                        declarationId.value
                    },
                )
                if (arguments.isNotEmpty()) {
                    append('<')
                    append(arguments.joinToString(",") { argument -> argument.boxedTypeSignature() })
                    append('>')
                }
            }
        is LsiArrayType -> "array:${elementType.boxedTypeSignature(
            ignoreRootNullability = false,
            root = false,
        )}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.boxedTypeSignature(ignoreRootNullability = false, root = false))
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter ->
                parameter.boxedTypeSignature(ignoreRootNullability = false, root = false)
            })
            append("]:return:")
            append(returnType.boxedTypeSignature(ignoreRootNullability = false, root = false))
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    return if (ignoreRootNullability) base else base + nullability.signatureSuffix()
}

private fun LsiTypeArgument.boxedTypeSignature(): String {
    return when (variance) {
        site.addzero.lsi.model.LsiVariance.STAR -> "*"
        site.addzero.lsi.model.LsiVariance.INVARIANT ->
            requireNotNull(type).boxedTypeSignature(ignoreRootNullability = false, root = false)
        site.addzero.lsi.model.LsiVariance.IN ->
            "in:${requireNotNull(type).boxedTypeSignature(ignoreRootNullability = false, root = false)}"
        site.addzero.lsi.model.LsiVariance.OUT ->
            "out:${requireNotNull(type).boxedTypeSignature(ignoreRootNullability = false, root = false)}"
    }
}

private fun LsiNullability.signatureSuffix(): String =
    if (this == LsiNullability.NULLABLE) "?" else "!"

private fun ImmutableProp.validateConverterSource() {
    val converter = converter ?: return
    val sourceType = converter.sourceType
        ?: throw ImmutablePrecompileException(
            declarationId = declarationId,
            message = "Converter '${converter.converterTypeId.value}' of immutable property '${id.value}' " +
                "does not implement a concrete converter source type",
        )
    if (
        sourceType.boxedTypeSignature(ignoreRootNullability = true) !=
        type.boxedTypeSignature(ignoreRootNullability = true)
    ) {
        throw ImmutablePrecompileException(
            declarationId = declarationId,
            message = "Converter '${converter.converterTypeId.value}' source type " +
                "'${sourceType.jimmerTypeSignature()}' does not match immutable property '${id.value}' type " +
                "'${type.jimmerTypeSignature()}'",
        )
    }
}

private fun ImmutableConverter.forIdView(prop: ImmutableProp): ImmutableConverter {
    if (!prop.list) {
        return copy(propertyNullable = prop.nullable)
    }
    return copy(
        sourceType = sourceType?.toConverterListType(),
        targetType = targetType?.toConverterListType(),
        sourceNullable = false,
        targetNullable = false,
        propertyNullable = prop.nullable,
    )
}

private fun LsiTypeRef.toConverterListType(): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = CONVERTER_LIST_TYPE,
        arguments = listOf(LsiTypeArgument.invariant(this)),
    )
}

private fun LsiTypeRef.isCollectionType(typeSystem: LsiTypeSystem): Boolean {
    val declaredType = this as? LsiDeclaredType ?: return false
    return declaredType.declarationId == COLLECTION_TYPE_ID ||
        declaredType.declarationId == KOTLIN_MUTABLE_LIST_TYPE_ID ||
        declaredType.declarationId in LIST_TYPE_IDS ||
        typeSystem.resolveSuperType(declaredType.declarationId, COLLECTION_TYPE_ID) != null
}

private fun LsiTypeRef.isImmutableListType(): Boolean {
    val declaredType = this as? LsiDeclaredType ?: return false
    return declaredType.declarationId in LIST_TYPE_IDS
}

private fun LsiTypeRef.targetTypeId(list: Boolean): LsiSymbolId? {
    return (targetType(list) as? LsiDeclaredType)?.declarationId
}

private fun LsiTypeRef.targetType(list: Boolean): LsiTypeRef? {
    val declaredType = this as? LsiDeclaredType
    if (!list) {
        return this
    }
    return declaredType?.arguments?.firstOrNull()?.type
}

private fun LsiResolvedProperty.nullable(): Boolean {
    val propertyType = type
    val explicitNullable = when (propertyType.nullability) {
        LsiNullability.NULLABLE -> true
        LsiNullability.NON_NULL -> false
        LsiNullability.PLATFORM -> when (propertyType) {
            is LsiPrimitiveType -> true
            is LsiDeclaredType -> if (propertyType.declarationId in BOXED_PRIMITIVE_KINDS) true else null
            else -> null
        }
        LsiNullability.UNKNOWN -> null
    }
    var annotationNullable: Boolean? = null
    var nullityAnnotationType: LsiSymbolId? = null
    annotations.forEach { annotation ->
        val nullable = annotation.type.annotationNullability() ?: return@forEach
        if (annotationNullable != null && annotationNullable != nullable) {
            throw ImmutablePrecompileException(
                declarationId = declaration.id,
                message = "Immutable property '${declaration.id.value}' cannot be decorated by both " +
                    "@${requireNotNull(nullityAnnotationType).value} and @${annotation.type.value}",
            )
        }
        annotationNullable = nullable
        nullityAnnotationType = annotation.type
    }
    if (
        explicitNullable != null &&
        annotationNullable != null &&
        explicitNullable != annotationNullable
    ) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' type is " +
                (if (explicitNullable) "nullable" else "non-null") +
                " but @${requireNotNull(nullityAnnotationType).value} requires it to be " +
                (if (annotationNullable) "nullable" else "non-null"),
        )
    }
    return explicitNullable ?: annotationNullable ?: false
}

internal fun LsiSymbolId.annotationNullability(): Boolean? {
    if (this == CLIENT_T_NULLABLE_ANNOTATION) {
        return true
    }
    return when {
        value.endsWith(".Null") || value.endsWith(".Nullable") -> true
        value.endsWith(".NotNull") || value.endsWith(".NonNull") -> false
        else -> null
    }
}

private fun LsiTypeRef.withRootNullability(nullable: Boolean): LsiTypeRef {
    val nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
    return when (this) {
        is LsiDeclaredType -> copy(nullability = nullability)
        is LsiTypeParameterRef -> copy(nullability = nullability)
        is LsiPrimitiveType -> copy(nullability = nullability)
        is LsiArrayType -> copy(nullability = nullability)
        is LsiFunctionType -> copy(nullability = nullability)
        is LsiUnresolvedType -> copy(nullability = nullability)
    }
}

private fun LsiResolvedProperty.primaryMappingAnnotation(): LsiAnnotation? {
    val primaryAnnotations = annotations.filter { annotation ->
        annotation.type in PRIMARY_PROP_ANNOTATIONS
    }
    primaryAnnotations.firstOrNull { annotation ->
        annotation.type == DISCRIMINATOR_ANNOTATION
    }?.let { discriminator ->
        return discriminator
    }
    if (primaryAnnotations.size > 1) {
        throw ImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Immutable property '${declaration.id.value}' cannot declare multiple primary mapping " +
                "annotations: " + primaryAnnotations
                .map(LsiAnnotation::type)
                .distinct()
                .sorted()
                .joinToString { annotationType -> "@${annotationType.value}" },
        )
    }
    return primaryAnnotations.singleOrNull()
}

private fun ImmutableProp.allowsMapsIdNameConflict(
    expectedProp: ImmutableProp,
    ownerIdProp: ImmutableProp?,
): Boolean {
    val mapsId = annotations.annotation(MAPS_ID_ANNOTATION) ?: return false
    return mapsId.stringValue("value").orEmpty().isEmpty() &&
        !reverse &&
        primaryMapping == PrimaryMapping.ASSOCIATION &&
        ownerIdProp != null &&
        expectedProp.primaryMapping == PrimaryMapping.ID &&
        expectedProp.id == ownerIdProp.id
}

private fun LsiSymbolId?.toPrimaryMapping(): PrimaryMapping? {
    return when (this) {
        ID_ANNOTATION -> PrimaryMapping.ID
        VERSION_ANNOTATION -> PrimaryMapping.VERSION
        LOGICAL_DELETED_ANNOTATION -> PrimaryMapping.LOGICAL_DELETED
        DISCRIMINATOR_ANNOTATION -> PrimaryMapping.DISCRIMINATOR
        ONE_TO_ONE_ANNOTATION,
        MANY_TO_ONE_ANNOTATION,
        ONE_TO_MANY_ANNOTATION,
        MANY_TO_MANY_ANNOTATION,
        -> PrimaryMapping.ASSOCIATION
        FORMULA_ANNOTATION -> PrimaryMapping.FORMULA
        TRANSIENT_ANNOTATION -> PrimaryMapping.TRANSIENT
        ID_VIEW_ANNOTATION,
        MANY_TO_MANY_VIEW_ANNOTATION,
        -> PrimaryMapping.VIEW
        else -> null
    }
}

private fun PrimaryMapping.identityAnnotationType(): LsiSymbolId {
    return when (this) {
        PrimaryMapping.ID -> ID_ANNOTATION
        PrimaryMapping.VERSION -> VERSION_ANNOTATION
        PrimaryMapping.LOGICAL_DELETED -> LOGICAL_DELETED_ANNOTATION
        else -> error("Primary mapping $this is not an identity mapping")
    }
}

private fun ImmutableProp.declaresPrimaryMapping(workspace: LsiWorkspace): Boolean {
    val declaration = workspace[declarationId] as? LsiProperty ?: return false
    val annotationType = primaryMapping.identityAnnotationType()
    return declaration.annotations.hasAnnotation(annotationType)
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.annotation(types: Set<LsiSymbolId>): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type in types }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private fun LsiAnnotation.stringValue(name: String): String? {
    return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value
}

private fun LsiAnnotation.stringValues(
    name: String,
    sourceId: LsiSymbolId,
): List<String> {
    val argument = arguments[name] ?: return emptyList()
    val arrayValue = argument.value as? LsiAnnotationValue.ArrayValue
        ?: throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must be a typed string array",
        )
    val values = arrayValue.elements.map { element ->
        element as? LsiAnnotationValue.StringValue
            ?: throw ImmutablePrecompileException(
                declarationId = sourceId,
                message = "Annotation argument '${type.value}.$name' must contain only typed string values",
            )
    }
    return values.map(LsiAnnotationValue.StringValue::value)
}

private fun LsiAnnotation.booleanValue(name: String): Boolean? {
    return (arguments[name]?.value as? LsiAnnotationValue.BooleanValue)?.value
}

private fun LsiAnnotation.enumEntryName(
    name: String,
    expectedEnumType: LsiSymbolId,
    sourceId: LsiSymbolId,
): String? {
    val argument = arguments[name] ?: return null
    val enumValue = argument.value as? LsiAnnotationValue.EnumValue
        ?: throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must be a typed enum value",
        )
    if (enumValue.enumType != expectedEnumType) {
        throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must use enum '${expectedEnumType.value}'",
        )
    }
    return enumValue.entryName
}

private fun LsiAnnotation.classTypeId(name: String): LsiSymbolId? {
    val value = arguments[name]?.value as? LsiAnnotationValue.ClassValue ?: return null
    return (value.type as? LsiDeclaredType)?.declarationId
}

private fun LsiAnnotation.transientResolverTypeId(
    name: String,
    sourceId: LsiSymbolId,
): LsiSymbolId? {
    val argument = arguments[name] ?: return null
    val classValue = argument.value as? LsiAnnotationValue.ClassValue
        ?: throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must be a typed class value",
        )
    return when (val classType = classValue.type) {
        is LsiDeclaredType -> classType.declarationId.takeUnless(NO_TRANSIENT_RESOLVER_TYPE_IDS::contains)
        is LsiPrimitiveType -> if (
            classType.kind == LsiPrimitiveKind.UNIT || classType.kind == LsiPrimitiveKind.VOID
        ) {
            null
        } else {
            throw ImmutablePrecompileException(
                declarationId = sourceId,
                message = "Annotation argument '${type.value}.$name' must reference a resolver class",
            )
        }
        is LsiUnresolvedType -> throw ImmutablePrecompileException(
            declarationId = sourceId,
            recoverable = true,
            message = "Cannot resolve transient resolver type '${classType.displayName}'",
        )
        else -> throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must reference a resolver class",
        )
    }
}

private fun LsiAnnotation.typedStringValue(
    name: String,
    sourceId: LsiSymbolId,
): String? {
    val argument = arguments[name] ?: return null
    return (argument.value as? LsiAnnotationValue.StringValue)?.value
        ?: throw ImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must be a typed string value",
        )
}

fun LsiAnnotation.classTypeIds(name: String): List<LsiSymbolId> {
    return when (val value = arguments[name]?.value) {
        is LsiAnnotationValue.ClassValue -> listOfNotNull((value.type as? LsiDeclaredType)?.declarationId)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            val classValue = element as? LsiAnnotationValue.ClassValue ?: return@mapNotNull null
            (classValue.type as? LsiDeclaredType)?.declarationId
        }
        else -> emptyList()
    }
}

fun LsiTypeRef.jimmerTypeSignature(
    ignoreRootNullability: Boolean = false,
): String {
    return jimmerTypeSignature(ignoreNullability = ignoreRootNullability, root = true)
}

private fun LsiTypeRef.jimmerTypeSignature(
    ignoreNullability: Boolean,
    root: Boolean,
): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.jimmerTypeSignature() })
                append('>')
            }
        }
        is LsiPrimitiveType -> buildString {
            append("primitive:${kind.name.lowercase()}")
            if (boxed) {
                append(":boxed")
            }
        }
        is LsiArrayType -> "array:${elementType.jimmerTypeSignature()}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.jimmerTypeSignature())
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter -> parameter.jimmerTypeSignature() })
            append("]:return:")
            append(returnType.jimmerTypeSignature())
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    if (root && ignoreNullability) {
        return base
    }
    return base + if (nullability == LsiNullability.NULLABLE) "?" else "!"
}

private fun LsiTypeArgument.jimmerTypeSignature(): String {
    return when (variance) {
        site.addzero.lsi.model.LsiVariance.STAR -> "*"
        site.addzero.lsi.model.LsiVariance.INVARIANT -> requireNotNull(type).jimmerTypeSignature()
        site.addzero.lsi.model.LsiVariance.IN -> "in:${requireNotNull(type).jimmerTypeSignature()}"
        site.addzero.lsi.model.LsiVariance.OUT -> "out:${requireNotNull(type).jimmerTypeSignature()}"
    }
}

private fun ImmutableTypeKind.description(): String {
    return when (this) {
        ImmutableTypeKind.IMMUTABLE -> "a simple immutable type"
        ImmutableTypeKind.ENTITY -> "an entity"
        ImmutableTypeKind.MAPPED_SUPERCLASS -> "a mapped superclass"
        ImmutableTypeKind.EMBEDDABLE -> "an embeddable"
    }
}

private const val JIMMER_PACKAGE_PREFIX = "org.babyfish.jimmer."

private val IMMUTABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.Immutable")
private val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
private val MAPPED_SUPERCLASS_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
private val EMBEDDABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable")
private val INHERITANCE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Inheritance")
private val DISCRIMINATOR_VALUE_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.sql.DiscriminatorValue")
private val DISCRIMINATOR_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Discriminator")

private val ENTITY_INSTANTIABILITY_TYPE =
    LsiSymbolId.type("org.babyfish.jimmer.sql.EntityInstantiability")
private val INHERITANCE_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.InheritanceType")
private val JOINED_TABLE_DISSOCIATE_ACTION_TYPE =
    LsiSymbolId.type("org.babyfish.jimmer.sql.JoinedTableDissociateAction")

private val ID_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
private val VERSION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Version")
private val LOGICAL_DELETED_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.LogicalDeleted")
private val DEFAULT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
private val DATABASE_DEFAULT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.DatabaseDefault")
private val KEY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
private val KEYS_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Keys")
private val ONE_TO_ONE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")
private val MANY_TO_ONE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
private val ONE_TO_MANY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")
private val MANY_TO_MANY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")
private val JOIN_COLUMN_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinColumn")
private val JOIN_COLUMNS_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinColumns")
private val JOIN_TABLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinTable")
private val JOIN_SQL_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")
private val FORMULA_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.Formula")
private val TRANSIENT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Transient")

private val NO_TRANSIENT_RESOLVER_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.Void"),
    LsiSymbolId.type("kotlin.Unit"),
)
private val ID_VIEW_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.IdView")
private val MANY_TO_MANY_VIEW_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
private val MAPS_ID_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.MapsId")
private val JSON_CONVERTER_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.jackson.JsonConverter")
private val JSON_CONVERTER_ANNOTATIONS = setOf(JSON_CONVERTER_ANNOTATION)
private val JSON_FORMAT_ANNOTATIONS = setOf(
    LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonFormat"),
)
private val CONVERTER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.jackson.Converter")

private val CONSTRAINT_ANNOTATIONS = setOf(
    LsiSymbolId.type("jakarta.validation.Constraint"),
    LsiSymbolId.type("javax.validation.Constraint"),
)

private val IMMUTABLE_TYPE_ANNOTATIONS = listOf(
    IMMUTABLE_ANNOTATION to ImmutableTypeKind.IMMUTABLE,
    ENTITY_ANNOTATION to ImmutableTypeKind.ENTITY,
    MAPPED_SUPERCLASS_ANNOTATION to ImmutableTypeKind.MAPPED_SUPERCLASS,
    EMBEDDABLE_ANNOTATION to ImmutableTypeKind.EMBEDDABLE,
)

private val IMMUTABLE_TYPE_ANNOTATION_IDS = IMMUTABLE_TYPE_ANNOTATIONS
    .mapTo(linkedSetOf()) { (annotationType, _) -> annotationType }

private val ASSOCIATION_DECLARING_TYPE_KINDS = setOf(
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val PRIMARY_PROP_ANNOTATIONS = setOf(
    ID_ANNOTATION,
    VERSION_ANNOTATION,
    LOGICAL_DELETED_ANNOTATION,
    DISCRIMINATOR_ANNOTATION,
    ONE_TO_ONE_ANNOTATION,
    MANY_TO_ONE_ANNOTATION,
    ONE_TO_MANY_ANNOTATION,
    MANY_TO_MANY_ANNOTATION,
    FORMULA_ANNOTATION,
    TRANSIENT_ANNOTATION,
    ID_VIEW_ANNOTATION,
    MANY_TO_MANY_VIEW_ANNOTATION,
)

private val LIST_TYPE_IDS = setOf(
    "java.util.List",
    "kotlin.collections.List",
).mapTo(linkedSetOf(), LsiSymbolId::type)
private val KOTLIN_MUTABLE_LIST_TYPE_ID = LsiSymbolId.type("kotlin.collections.MutableList")

private val COLLECTION_TYPE_ID = LsiSymbolId.type("java.util.Collection")

private val CONVERTER_LIST_TYPE = LsiSymbolId.type("java.util.List")

private val BOXED_PRIMITIVE_KINDS = mapOf(
    LsiSymbolId.type("java.lang.Boolean") to LsiPrimitiveKind.BOOLEAN,
    LsiSymbolId.type("java.lang.Byte") to LsiPrimitiveKind.BYTE,
    LsiSymbolId.type("java.lang.Short") to LsiPrimitiveKind.SHORT,
    LsiSymbolId.type("java.lang.Integer") to LsiPrimitiveKind.INT,
    LsiSymbolId.type("java.lang.Long") to LsiPrimitiveKind.LONG,
    LsiSymbolId.type("java.lang.Character") to LsiPrimitiveKind.CHAR,
    LsiSymbolId.type("java.lang.Float") to LsiPrimitiveKind.FLOAT,
    LsiSymbolId.type("java.lang.Double") to LsiPrimitiveKind.DOUBLE,
    LsiSymbolId.type("java.lang.Void") to LsiPrimitiveKind.VOID,
)

private val BOXED_LONG_TYPE_ID = LsiSymbolId.type("java.lang.Long")

private val UUID_TYPE_ID = LsiSymbolId.type("java.util.UUID")

private val LOGICAL_DELETED_TIME_TYPE_IDS = setOf(
    "java.util.Date",
    "java.sql.Date",
    "java.sql.Time",
    "java.sql.Timestamp",
    "java.time.LocalDateTime",
    "java.time.LocalDate",
    "java.time.LocalTime",
    "java.time.OffsetDateTime",
    "java.time.ZonedDateTime",
    "java.time.Instant",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val STRING_TYPE_IDS = setOf(
    "java.lang.String",
    "kotlin.String",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val SCALAR_ANNOTATIONS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.Scalar"),
)

private val CLIENT_T_NULLABLE_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.client.TNullable")

private val NON_SEMANTIC_OVERRIDE_ANNOTATIONS = setOf(
    "java.lang.Override",
    "java.lang.SuppressWarnings",
    "kotlin.Suppress",
).mapTo(linkedSetOf(), LsiSymbolId::type)
