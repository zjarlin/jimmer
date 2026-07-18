package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiNullability
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
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutablePrecompileException(
    val declarationId: LsiSymbolId,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

class JimmerImmutablePrecompiler {
    fun compile(
        workspace: LsiWorkspace,
        targetTypeIds: Set<LsiSymbolId> = workspace.immutableTypeIds(),
    ): JimmerImmutableSchema {
        val typeDeclarations = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val kindByTypeId = typeDeclarations.mapNotNull { type ->
            type.immutableKind()?.let { kind -> type.id to kind }
        }.toMap()
        val microServiceMetadataByTypeId = typeDeclarations.mapNotNull { type ->
            val kind = kindByTypeId[type.id] ?: return@mapNotNull null
            type.id to type.microServiceMetadata(kind)
        }.toMap()
        val unknownTargetTypeIds = targetTypeIds.filterNot(kindByTypeId::containsKey).sorted()
        if (unknownTargetTypeIds.isNotEmpty()) {
            val targetTypeId = unknownTargetTypeIds.first()
            throw JimmerImmutablePrecompileException(
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
        val hierarchyResolver = JimmerImmutableHierarchyResolver(
            typeDeclarations.associateBy(LsiTypeDeclaration::id),
            kindByTypeId,
        )
        val types = typeDeclarations
            .filter { type -> type.id in semanticTypeIds }
            .map { type ->
                validateType(
                    type = type,
                    kind = kindByTypeId.getValue(type.id),
                    microServiceMetadata = microServiceMetadataByTypeId.getValue(type.id),
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
            .sortedBy(JimmerImmutableType::id)
        return JimmerImmutableSchema(types)
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
        kind: JimmerImmutableTypeKind,
        microServiceMetadata: JimmerMicroServiceMetadata,
    ) {
        if (type.enclosingTypeId != null) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' must be a top-level type",
            )
        }
        if (type.kind != LsiTypeDeclarationKind.INTERFACE) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' must be an interface",
            )
        }
        if (
            type.typeParameters.isNotEmpty() &&
            kind != JimmerImmutableTypeKind.MAPPED_SUPERCLASS
        ) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot declare type parameters unless it is " +
                    "a mapped superclass",
            )
        }
        if (type.visibility in setOf(LsiVisibility.PRIVATE, LsiVisibility.PROTECTED, LsiVisibility.LOCAL)) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot be private, protected or local",
            )
        }
        if (microServiceMetadata.acrossMicroServices && microServiceMetadata.microServiceName.isNotEmpty()) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable mapped superclass '${type.qualifiedName}' cannot specify microServiceName " +
                    "when acrossMicroServices is true",
            )
        }
    }

    private fun compileType(
        type: LsiTypeDeclaration,
        kind: JimmerImmutableTypeKind,
        kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
        microServiceMetadataByTypeId: Map<LsiSymbolId, JimmerMicroServiceMetadata>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
        hierarchy: JimmerImmutableHierarchy,
    ): JimmerImmutableType {
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
            throw JimmerImmutablePrecompileException(
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
        return JimmerImmutableType(
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
            acrossMicroServices = microServiceMetadata.acrossMicroServices,
            microServiceName = microServiceMetadata.microServiceName,
        )
    }

    private fun validateMicroServiceInheritance(
        type: LsiTypeDeclaration,
        hierarchy: JimmerImmutableHierarchy,
        microServiceMetadata: JimmerMicroServiceMetadata,
        microServiceMetadataByTypeId: Map<LsiSymbolId, JimmerMicroServiceMetadata>,
    ) {
        hierarchy.directSuperTypeIds.forEach { superTypeId ->
            val superMetadata = microServiceMetadataByTypeId.getValue(superTypeId)
            if (
                !superMetadata.acrossMicroServices &&
                superMetadata.microServiceName != microServiceMetadata.microServiceName
            ) {
                throw JimmerImmutablePrecompileException(
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
        kind: JimmerImmutableTypeKind,
        hierarchy: JimmerImmutableHierarchy,
        props: List<JimmerImmutableProp>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
    ): LsiSymbolId? {
        if (kind != JimmerImmutableTypeKind.ENTITY) {
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
            throw JimmerImmutablePrecompileException(
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
                throw JimmerImmutablePrecompileException(
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
                throw JimmerImmutablePrecompileException(
                    declarationId = invalidDiscriminatorProp.declarationId,
                    message = "Immutable property '${invalidDiscriminatorProp.declarationId.value}' decorated by " +
                        "@${DISCRIMINATOR_ANNOTATION.value} cannot be declared or inherited by an inheritance " +
                        "derived type except from its inheritance root",
                )
            }
            if (discriminatorProps.size != 1) {
                throw JimmerImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Immutable inheritance derived type '${type.qualifiedName}' must have exactly one " +
                        "discriminator property inherited from its inheritance root",
                )
            }
            return discriminatorProps.single().id
        }
        if (discriminatorProps.isEmpty()) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable inheritance root '${type.qualifiedName}' must declare or inherit one property " +
                    "decorated by @${DISCRIMINATOR_ANNOTATION.value}",
            )
        }
        if (discriminatorProps.size > 1) {
            throw JimmerImmutablePrecompileException(
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
            throw JimmerImmutablePrecompileException(
                declarationId = rootTypeId,
                message = exception.message ?: "Cannot resolve immutable inheritance root '${rootTypeId.value}'",
            )
        }.filter { prop ->
            prop.annotations.hasAnnotation(DISCRIMINATOR_ANNOTATION)
        }
        if (rootDiscriminatorProps.size != 1) {
            throw JimmerImmutablePrecompileException(
                declarationId = rootTypeId,
                message = "Immutable inheritance root '${rootTypeId.value}' must have exactly one discriminator property",
            )
        }
        return rootDiscriminatorProps.single().overrideChain.last().id
    }

    private fun validateDiscriminatorProp(
        prop: JimmerImmutableProp,
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
            throw JimmerImmutablePrecompileException(
                declarationId = prop.declarationId,
                message = "Immutable property '${prop.declarationId.value}' decorated by " +
                    "@${DISCRIMINATOR_ANNOTATION.value} must be a scalar string or enum property",
            )
        }
    }

    private fun validateOverride(
        ownerType: LsiTypeDeclaration,
        ownerKind: JimmerImmutableTypeKind,
        property: LsiResolvedProperty,
        kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
        microServiceMetadataByTypeId: Map<LsiSymbolId, JimmerMicroServiceMetadata>,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
    ) {
        if (property.declaration.ownerId != ownerType.id || property.overrideChain.size < 2) {
            return
        }
        val overriddenDeclaration = property.overrideChain[1]
        val inheritedOwnerId = overriddenDeclaration.ownerId
        val declaredAnnotationTypes = property.declaration.annotations
            .mapTo(linkedSetOf(), LsiAnnotation::type)
        val shadowedAnnotationTypes = property.overrideChain
            .drop(1)
            .flatMap(LsiProperty::annotations)
            .map(LsiAnnotation::type)
            .filterTo(linkedSetOf()) { annotationType ->
                annotationType in declaredAnnotationTypes && annotationType !in NON_SEMANTIC_OVERRIDE_ANNOTATIONS
            }
        val directSuperTypeIds = ownerType.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .mapTo(linkedSetOf(), LsiDeclaredType::declarationId)
        val annotationOverrideAllowed = shadowedAnnotationTypes.isEmpty() ||
            ownerKind == JimmerImmutableTypeKind.ENTITY &&
            kindByTypeId[inheritedOwnerId] == JimmerImmutableTypeKind.MAPPED_SUPERCLASS &&
            inheritedOwnerId in directSuperTypeIds
        if (!annotationOverrideAllowed) {
            throw JimmerImmutablePrecompileException(
                declarationId = property.declaration.id,
                message = "Immutable property '${property.declaration.id.value}' can only override a property " +
                    "annotation declared directly by a mapped superclass of an entity; shadowed annotations: " +
                    shadowedAnnotationTypes.sorted().joinToString { annotationType -> annotationType.value },
            )
        }
        val inheritedOwner = workspace[inheritedOwnerId] as? LsiTypeDeclaration
            ?: throw JimmerImmutablePrecompileException(
                declarationId = property.declaration.id,
                recoverable = true,
                message = "Missing inherited immutable type '${inheritedOwnerId.value}'",
            )
        val inheritedProperty = typeSystem.effectiveProperties(inheritedOwnerId)
            .firstOrNull { inherited ->
                inherited.overrideChain.any { declaration -> declaration.id == overriddenDeclaration.id }
            }
            ?: throw JimmerImmutablePrecompileException(
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
        val currentModel = property.toImmutableProp(
            ownerTypeId = ownerType.id,
            kindByTypeId = kindByTypeId,
            microServiceMetadataByTypeId = microServiceMetadataByTypeId,
            workspace = workspace,
            typeSystem = typeSystem,
        )
        val inheritedModel = inheritedInOwner.toImmutableProp(
            ownerTypeId = ownerType.id,
            kindByTypeId = kindByTypeId,
            microServiceMetadataByTypeId = microServiceMetadataByTypeId,
            workspace = workspace,
            typeSystem = typeSystem,
        )
        val violations = buildList {
            if (currentModel.type.normalizedTypeSignature(ignoreRootNullability = true) !=
                inheritedModel.type.normalizedTypeSignature(ignoreRootNullability = true)
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
            if (currentModel.primaryAnnotationTypeId != inheritedModel.primaryAnnotationTypeId) {
                add("primary mapping annotation")
            }
            if (currentModel.formulaKind != inheritedModel.formulaKind) {
                add("formula kind")
            }
        }
        if (violations.isNotEmpty()) {
            throw JimmerImmutablePrecompileException(
                declarationId = property.declaration.id,
                message = "Immutable property '${property.declaration.id.value}' overrides annotations but changes " +
                    violations.joinToString(),
            )
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
            ?: throw JimmerImmutablePrecompileException(
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

private data class JimmerImmutableHierarchy(
    val directSuperTypeIds: List<LsiSymbolId>,
    val primarySuperTypeId: LsiSymbolId?,
    val inheritanceRootTypeId: LsiSymbolId?,
    val inheritanceStrategy: JimmerInheritanceStrategy?,
    val joinedTableDissociateAction: JimmerJoinedTableDissociateAction?,
    val instantiable: Boolean,
    val discriminatorValue: String?,
)

private data class JimmerMicroServiceMetadata(
    val acrossMicroServices: Boolean,
    val microServiceName: String,
)

private class JimmerImmutableHierarchyResolver(
    private val declarationsById: Map<LsiSymbolId, LsiTypeDeclaration>,
    private val kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
) {

    private val cache = mutableMapOf<LsiSymbolId, JimmerImmutableHierarchy>()

    private val resolving = linkedSetOf<LsiSymbolId>()

    fun resolve(typeId: LsiSymbolId): JimmerImmutableHierarchy {
        cache[typeId]?.let { hierarchy -> return hierarchy }
        val type = declarationsById[typeId]
            ?: throw JimmerImmutablePrecompileException(
                declarationId = typeId,
                recoverable = true,
                message = "Cannot resolve immutable type '${typeId.value}'",
            )
        if (!resolving.add(typeId)) {
            throw JimmerImmutablePrecompileException(
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
        kind: JimmerImmutableTypeKind,
    ): JimmerImmutableHierarchy {
        val directSuperTypeIds = type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .map(LsiDeclaredType::declarationId)
            .filter(kindByTypeId::containsKey)
        val primarySuperTypeIds = directSuperTypeIds.filter { superTypeId ->
            kindByTypeId.getValue(superTypeId) != JimmerImmutableTypeKind.MAPPED_SUPERCLASS
        }
        if (primarySuperTypeIds.size > 1) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable type '${type.qualifiedName}' cannot have more than one primary super type: " +
                    primarySuperTypeIds.joinToString { superTypeId -> superTypeId.value },
            )
        }
        val primarySuperTypeId = primarySuperTypeIds.singleOrNull()
        val inheritance = type.annotations.annotation(INHERITANCE_ANNOTATION)
        val discriminatorValue = type.annotations.annotation(DISCRIMINATOR_VALUE_ANNOTATION)
        if (kind != JimmerImmutableTypeKind.ENTITY) {
            validateNonEntityAnnotations(type, inheritance, discriminatorValue)
            return JimmerImmutableHierarchy(
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
            kindByTypeId[superTypeId] == JimmerImmutableTypeKind.ENTITY
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
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "@${DISCRIMINATOR_VALUE_ANNOTATION.value} can only be declared by inheritance entities",
            )
        }
        return JimmerImmutableHierarchy(
            directSuperTypeIds = directSuperTypeIds,
            primarySuperTypeId = primarySuperTypeId,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = determineInstantiable(type, null),
            discriminatorValue = null,
        )
    }

    private fun validateNonEntityAnnotations(
        type: LsiTypeDeclaration,
        inheritance: LsiAnnotation?,
        discriminatorValue: LsiAnnotation?,
    ) {
        if (inheritance != null) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "@${INHERITANCE_ANNOTATION.value} can only be declared by an entity type",
            )
        }
        if (discriminatorValue != null) {
            throw JimmerImmutablePrecompileException(
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
    ): JimmerImmutableHierarchy {
        if (inheritance != null) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "@${INHERITANCE_ANNOTATION.value} can only be declared by an inheritance root type",
            )
        }
        val rootTypeId = resolve(primaryEntitySuperTypeId).inheritanceRootTypeId
            ?: throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "Immutable entity '${type.qualifiedName}' cannot inherit entity " +
                    "'${primaryEntitySuperTypeId.value}' because it is not an inheritance root or derived type",
            )
        val instantiable = determineInstantiable(type, rootTypeId)
        validateDiscriminatorValue(type, discriminatorValue, instantiable)
        return JimmerImmutableHierarchy(
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
    ): JimmerImmutableHierarchy {
        val strategy = inheritance.enumEntryName(
            name = "strategy",
            expectedEnumType = INHERITANCE_TYPE,
            sourceId = type.id,
        )?.let { entryName ->
            try {
                JimmerInheritanceStrategy.valueOf(entryName)
            } catch (exception: IllegalArgumentException) {
                throw JimmerImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Unsupported immutable inheritance strategy '$entryName'",
                )
            }
        } ?: JimmerInheritanceStrategy.SINGLE_TABLE
        val joinedTableDissociateAction = inheritance.enumEntryName(
            name = "joinedTableDissociateAction",
            expectedEnumType = JOINED_TABLE_DISSOCIATE_ACTION_TYPE,
            sourceId = type.id,
        )?.let { entryName ->
            try {
                JimmerJoinedTableDissociateAction.valueOf(entryName)
            } catch (exception: IllegalArgumentException) {
                throw JimmerImmutablePrecompileException(
                    declarationId = type.id,
                    message = "Unsupported joined table dissociate action '$entryName'",
                )
            }
        } ?: JimmerJoinedTableDissociateAction.DELETE
        if (
            strategy != JimmerInheritanceStrategy.JOINED &&
            joinedTableDissociateAction != JimmerJoinedTableDissociateAction.DELETE
        ) {
            throw JimmerImmutablePrecompileException(
                declarationId = type.id,
                message = "The joinedTableDissociateAction of @${INHERITANCE_ANNOTATION.value} can only be " +
                    "LAX when the inheritance strategy is JOINED",
            )
        }
        val instantiable = determineInstantiable(type, type.id)
        validateDiscriminatorValue(type, discriminatorValue, instantiable)
        return JimmerImmutableHierarchy(
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
                    throw JimmerImmutablePrecompileException(
                        declarationId = type.id,
                        message = "@${ENTITY_ANNOTATION.value}(instantiability = ABSTRACT) can only be used by " +
                            "inheritance entity types",
                    )
                }
                false
            }
            "INSTANTIABLE" -> true
            else -> throw JimmerImmutablePrecompileException(
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
            throw JimmerImmutablePrecompileException(
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
            ?: throw JimmerImmutablePrecompileException(
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

internal fun LsiTypeDeclaration.immutableKind(): JimmerImmutableTypeKind? {
    val markers = IMMUTABLE_TYPE_ANNOTATIONS.mapNotNull { (annotationType, kind) ->
        kind.takeIf { annotations.hasAnnotation(annotationType) }
    }
    if (markers.size > 1) {
        throw JimmerImmutablePrecompileException(
            declarationId = id,
            message = "Immutable type '$qualifiedName' has conflicting immutable annotations",
        )
    }
    return markers.singleOrNull()
}

private fun LsiTypeDeclaration.microServiceMetadata(
    kind: JimmerImmutableTypeKind,
): JimmerMicroServiceMetadata {
    val marker = when (kind) {
        JimmerImmutableTypeKind.ENTITY -> annotations.annotation(ENTITY_ANNOTATION)
        JimmerImmutableTypeKind.MAPPED_SUPERCLASS -> annotations.annotation(MAPPED_SUPERCLASS_ANNOTATION)
        JimmerImmutableTypeKind.IMMUTABLE,
        JimmerImmutableTypeKind.EMBEDDABLE,
        -> null
    }
    return JimmerMicroServiceMetadata(
        acrossMicroServices = kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS &&
            marker?.booleanValue("acrossMicroServices") == true,
        microServiceName = marker?.stringValue("microServiceName").orEmpty(),
    )
}

internal fun LsiTypeDeclaration.hasImmutableMarker(): Boolean {
    return annotations.any { annotation -> annotation.type in IMMUTABLE_TYPE_ANNOTATION_IDS }
}

internal fun LsiWorkspace.immutableTypeIds(): Set<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .filter(LsiTypeDeclaration::hasImmutableMarker)
        .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
}

private fun managedTypeClosure(
    targetTypeIds: Set<LsiSymbolId>,
    typeDeclarations: List<LsiTypeDeclaration>,
    kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
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
    kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
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
            .filter(LsiTypeDeclaration::hasImmutableMarker)
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
    kindByTypeId: Map<LsiSymbolId, JimmerImmutableTypeKind>,
    microServiceMetadataByTypeId: Map<LsiSymbolId, JimmerMicroServiceMetadata>,
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
): JimmerImmutableProp {
    val list = type.isListType()
    val genericTarget = type.targetType(list) is LsiTypeParameterRef
    val targetTypeId = type.targetTypeId(list)
    val associationKind = associationKind()
    val targetKind = targetTypeId?.let(kindByTypeId::get)
    val association = associationKind != JimmerAssociationKind.NONE ||
        targetKind == JimmerImmutableTypeKind.ENTITY
    val nullable = type.isNullable(annotations)
    val primaryAnnotation = annotations.firstOrNull { annotation ->
        annotation.type in PRIMARY_PROP_ANNOTATIONS
    }
    val primaryMapping = primaryAnnotation?.type.toPrimaryMapping()
        ?: if (association) JimmerImmutablePrimaryMapping.ASSOCIATION
        else JimmerImmutablePrimaryMapping.SCALAR
    val viewKind = viewKind()
    val ownerMicroServiceMetadata = microServiceMetadataByTypeId.getValue(ownerTypeId)
    val targetMicroServiceMetadata = targetTypeId?.let(microServiceMetadataByTypeId::get)
    val remote = association &&
        targetKind == JimmerImmutableTypeKind.ENTITY &&
        targetMicroServiceMetadata != null &&
        targetMicroServiceMetadata.microServiceName != ownerMicroServiceMetadata.microServiceName
    validateMicroServiceAssociation(
        ownerTypeId = ownerTypeId,
        targetTypeId = targetTypeId,
        targetKind = targetKind,
        association = association,
        primaryMapping = primaryMapping,
        ownerMicroServiceMetadata = ownerMicroServiceMetadata,
        targetMicroServiceMetadata = targetMicroServiceMetadata,
        remote = remote,
    )
    val recursive = kindByTypeId[ownerTypeId] == JimmerImmutableTypeKind.ENTITY &&
        targetKind == JimmerImmutableTypeKind.ENTITY &&
        viewKind != JimmerViewKind.MANY_TO_MANY &&
        !genericTarget &&
        !remote &&
        typeSystem.resolveSuperType(requireNotNull(targetTypeId), ownerTypeId) != null
    return JimmerImmutableProp(
        id = LsiSymbolId.property(ownerTypeId, declaration.name),
        declarationId = declaration.id,
        ownerTypeId = ownerTypeId,
        declaringTypeId = declaration.ownerId,
        name = declaration.name,
        type = type,
        annotations = annotations,
        overrideChain = overrideChain.map { property -> property.id },
        inherited = declaration.ownerId != ownerTypeId,
        overridden = declaration.ownerId == ownerTypeId && overrideChain.size > 1,
        nullable = nullable,
        list = list,
        association = association,
        embedded = targetKind == JimmerImmutableTypeKind.EMBEDDABLE,
        targetTypeId = targetTypeId,
        primaryMapping = primaryMapping,
        primaryAnnotationTypeId = primaryAnnotation?.type,
        associationKind = if (associationKind == JimmerAssociationKind.NONE && association) {
            JimmerAssociationKind.IMPLICIT
        } else {
            associationKind
        },
        formulaKind = formulaKind(),
        viewKind = viewKind,
        genericTarget = genericTarget,
        remote = remote,
        recursive = recursive,
        validations = validations(workspace),
        converter = converter(workspace, typeSystem, nullable),
    )
}

private fun LsiResolvedProperty.validateMicroServiceAssociation(
    ownerTypeId: LsiSymbolId,
    targetTypeId: LsiSymbolId?,
    targetKind: JimmerImmutableTypeKind?,
    association: Boolean,
    primaryMapping: JimmerImmutablePrimaryMapping,
    ownerMicroServiceMetadata: JimmerMicroServiceMetadata,
    targetMicroServiceMetadata: JimmerMicroServiceMetadata?,
    remote: Boolean,
) {
    if (
        ownerMicroServiceMetadata.acrossMicroServices &&
        association &&
        targetKind == JimmerImmutableTypeKind.ENTITY &&
        primaryMapping != JimmerImmutablePrimaryMapping.TRANSIENT
    ) {
        throw JimmerImmutablePrecompileException(
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
        throw JimmerImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Remote association '${declaration.id.value}' requires non-empty micro service names for " +
                "both declaring type '${ownerTypeId.value}' and target type '${targetTypeId.value}'",
        )
    }
    if (annotations.hasAnnotation(JOIN_SQL_ANNOTATION)) {
        throw JimmerImmutablePrecompileException(
            declarationId = declaration.id,
            message = "Remote association '${declaration.id.value}' cannot be decorated by " +
                "@${JOIN_SQL_ANNOTATION.value}",
        )
    }
}

private fun LsiResolvedProperty.validations(workspace: LsiWorkspace): List<JimmerValidation> {
    return annotations.mapNotNull { annotation ->
        val annotationType = workspace[annotation.type] as? LsiTypeDeclaration ?: return@mapNotNull null
        val constraint = annotationType.annotations.annotation(CONSTRAINT_ANNOTATIONS) ?: return@mapNotNull null
        val validatorTypeIds = constraint.classTypeIds("validatedBy")
        if (validatorTypeIds.isEmpty()) {
            return@mapNotNull null
        }
        JimmerValidation(
            annotationTypeId = annotation.type,
            validatorTypeIds = validatorTypeIds.sorted(),
            message = annotation.stringValue("message").orEmpty(),
        )
    }.sortedBy(JimmerValidation::annotationTypeId)
}

private fun LsiResolvedProperty.converter(
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
    propertyNullable: Boolean,
): JimmerConverter? {
    val converterAnnotation = annotations.firstNotNullOfOrNull { annotation ->
        annotation.findJsonConverter(workspace, linkedSetOf())
    } ?: return null
    val converterTypeId = converterAnnotation.classTypeId("value") ?: return null
    val converterType = typeSystem.resolveSuperType(converterTypeId, CONVERTER_TYPE_ID)
    val sourceType = converterType?.arguments?.getOrNull(0)?.type
    val targetType = converterType?.arguments?.getOrNull(1)?.type
    return JimmerConverter(
        converterTypeId = converterTypeId,
        sourceType = sourceType,
        targetType = targetType,
        sourceNullable = sourceType?.nullability == LsiNullability.NULLABLE,
        targetNullable = targetType?.nullability == LsiNullability.NULLABLE,
        propertyNullable = propertyNullable,
    )
}

private fun LsiAnnotation.findJsonConverter(
    workspace: LsiWorkspace,
    visited: MutableSet<LsiSymbolId>,
): LsiAnnotation? {
    if (type == JSON_CONVERTER_ANNOTATION) {
        return this
    }
    if (!visited.add(type)) {
        return null
    }
    val annotationType = workspace[type] as? LsiTypeDeclaration ?: return null
    return annotationType.annotations.firstNotNullOfOrNull { annotation ->
        annotation.findJsonConverter(workspace, visited)
    }
}

private fun LsiResolvedProperty.associationKind(): JimmerAssociationKind {
    return when {
        annotations.hasAnnotation(ONE_TO_ONE_ANNOTATION) -> JimmerAssociationKind.ONE_TO_ONE
        annotations.hasAnnotation(MANY_TO_ONE_ANNOTATION) -> JimmerAssociationKind.MANY_TO_ONE
        annotations.hasAnnotation(ONE_TO_MANY_ANNOTATION) -> JimmerAssociationKind.ONE_TO_MANY
        annotations.hasAnnotation(MANY_TO_MANY_ANNOTATION) -> JimmerAssociationKind.MANY_TO_MANY
        annotations.hasAnnotation(MANY_TO_MANY_VIEW_ANNOTATION) -> JimmerAssociationKind.MANY_TO_MANY_VIEW
        else -> JimmerAssociationKind.NONE
    }
}

private fun LsiResolvedProperty.formulaKind(): JimmerFormulaKind {
    val formula = annotations.annotation(FORMULA_ANNOTATION) ?: return JimmerFormulaKind.NONE
    if (!formula.stringValue("sql").isNullOrBlank()) {
        return JimmerFormulaKind.SQL
    }
    return if (declaration.modality == LsiModality.ABSTRACT) {
        JimmerFormulaKind.ABSTRACT
    } else {
        JimmerFormulaKind.LANGUAGE
    }
}

private fun LsiResolvedProperty.viewKind(): JimmerViewKind {
    return when {
        annotations.hasAnnotation(ID_VIEW_ANNOTATION) -> JimmerViewKind.ID
        annotations.hasAnnotation(MANY_TO_MANY_VIEW_ANNOTATION) -> JimmerViewKind.MANY_TO_MANY
        else -> JimmerViewKind.NONE
    }
}

private fun LsiTypeRef.isListType(): Boolean {
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

private fun LsiTypeRef.isNullable(annotations: List<LsiAnnotation>): Boolean {
    return nullability == LsiNullability.NULLABLE ||
        annotations.any { annotation -> annotation.type in NULLABLE_ANNOTATIONS }
}

private fun LsiSymbolId?.toPrimaryMapping(): JimmerImmutablePrimaryMapping? {
    return when (this) {
        ID_ANNOTATION -> JimmerImmutablePrimaryMapping.ID
        VERSION_ANNOTATION -> JimmerImmutablePrimaryMapping.VERSION
        LOGICAL_DELETED_ANNOTATION -> JimmerImmutablePrimaryMapping.LOGICAL_DELETED
        DISCRIMINATOR_ANNOTATION -> JimmerImmutablePrimaryMapping.DISCRIMINATOR
        ONE_TO_ONE_ANNOTATION,
        MANY_TO_ONE_ANNOTATION,
        ONE_TO_MANY_ANNOTATION,
        MANY_TO_MANY_ANNOTATION,
        -> JimmerImmutablePrimaryMapping.ASSOCIATION
        FORMULA_ANNOTATION -> JimmerImmutablePrimaryMapping.FORMULA
        TRANSIENT_ANNOTATION -> JimmerImmutablePrimaryMapping.TRANSIENT
        ID_VIEW_ANNOTATION,
        MANY_TO_MANY_VIEW_ANNOTATION,
        -> JimmerImmutablePrimaryMapping.VIEW
        else -> null
    }
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
        ?: throw JimmerImmutablePrecompileException(
            declarationId = sourceId,
            message = "Annotation argument '${type.value}.$name' must be a typed enum value",
        )
    if (enumValue.enumType != expectedEnumType) {
        throw JimmerImmutablePrecompileException(
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

private fun LsiAnnotation.classTypeIds(name: String): List<LsiSymbolId> {
    return when (val value = arguments[name]?.value) {
        is LsiAnnotationValue.ClassValue -> listOfNotNull((value.type as? LsiDeclaredType)?.declarationId)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            val classValue = element as? LsiAnnotationValue.ClassValue ?: return@mapNotNull null
            (classValue.type as? LsiDeclaredType)?.declarationId
        }
        else -> emptyList()
    }
}

internal fun LsiTypeRef.normalizedTypeSignature(
    ignoreRootNullability: Boolean = false,
): String {
    return normalizedTypeSignature(ignoreNullability = ignoreRootNullability, root = true)
}

private fun LsiTypeRef.normalizedTypeSignature(
    ignoreNullability: Boolean,
    root: Boolean,
): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.normalizedTypeSignature() })
                append('>')
            }
        }
        is LsiPrimitiveType -> "primitive:${kind.name.lowercase()}"
        is LsiArrayType -> "array:${elementType.normalizedTypeSignature()}"
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiUnresolvedType -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    if (root && ignoreNullability) {
        return base
    }
    return base + if (nullability == LsiNullability.NULLABLE) "?" else "!"
}

private fun LsiTypeArgument.normalizedTypeSignature(): String {
    return when (variance) {
        site.addzero.lsi.model.LsiVariance.STAR -> "*"
        site.addzero.lsi.model.LsiVariance.INVARIANT -> requireNotNull(type).normalizedTypeSignature()
        site.addzero.lsi.model.LsiVariance.IN -> "in:${requireNotNull(type).normalizedTypeSignature()}"
        site.addzero.lsi.model.LsiVariance.OUT -> "out:${requireNotNull(type).normalizedTypeSignature()}"
    }
}

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
private val ONE_TO_ONE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")
private val MANY_TO_ONE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
private val ONE_TO_MANY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")
private val MANY_TO_MANY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")
private val JOIN_SQL_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")
private val FORMULA_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.Formula")
private val TRANSIENT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Transient")
private val ID_VIEW_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.IdView")
private val MANY_TO_MANY_VIEW_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
private val JSON_CONVERTER_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.jackson.JsonConverter")
private val CONVERTER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.jackson.Converter")

private val CONSTRAINT_ANNOTATIONS = setOf(
    LsiSymbolId.type("jakarta.validation.Constraint"),
    LsiSymbolId.type("javax.validation.Constraint"),
)

private val IMMUTABLE_TYPE_ANNOTATIONS = listOf(
    IMMUTABLE_ANNOTATION to JimmerImmutableTypeKind.IMMUTABLE,
    ENTITY_ANNOTATION to JimmerImmutableTypeKind.ENTITY,
    MAPPED_SUPERCLASS_ANNOTATION to JimmerImmutableTypeKind.MAPPED_SUPERCLASS,
    EMBEDDABLE_ANNOTATION to JimmerImmutableTypeKind.EMBEDDABLE,
)

private val IMMUTABLE_TYPE_ANNOTATION_IDS = IMMUTABLE_TYPE_ANNOTATIONS
    .mapTo(linkedSetOf()) { (annotationType, _) -> annotationType }

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
    "kotlin.collections.MutableList",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val STRING_TYPE_IDS = setOf(
    "java.lang.String",
    "kotlin.String",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val NULLABLE_ANNOTATIONS = setOf(
    "edu.umd.cs.findbugs.annotations.Nullable",
    "jakarta.annotation.Nullable",
    "javax.annotation.Nullable",
    "org.babyfish.jimmer.client.TNullable",
    "org.jetbrains.annotations.Nullable",
    "org.jspecify.annotations.Nullable",
    "org.springframework.lang.Nullable",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val NON_SEMANTIC_OVERRIDE_ANNOTATIONS = setOf(
    "java.lang.Override",
    "java.lang.SuppressWarnings",
    "kotlin.Suppress",
).mapTo(linkedSetOf(), LsiSymbolId::type)
