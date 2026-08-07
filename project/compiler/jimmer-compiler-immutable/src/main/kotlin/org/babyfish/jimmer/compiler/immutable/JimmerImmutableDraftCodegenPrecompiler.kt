package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.FormulaDependency
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.classTypeIds
import site.addzero.lsi.jimmer.elementTypeOrSelf
import site.addzero.lsi.jimmer.generatedDraftCodegenName
import site.addzero.lsi.jimmer.generatedDraftSlotName
import site.addzero.lsi.jimmer.isImmutableReference
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.toDraftAnnotationProjection
import site.addzero.lsi.jimmer.toDraftRuntimeProp
import site.addzero.lsi.jimmer.toDraftValidationPlan
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableDraftCodegenPrecompiler {

    fun compile(
        schema: ImmutableSchema,
        workspace: LsiWorkspace,
        options: JimmerImmutableDraftCodegenOptions,
    ): JimmerImmutableDraftCodegenSchema {
        val compiledTypes = mutableMapOf<LsiSymbolId, JimmerImmutableDraftTypePlan>()

        fun compileType(typeId: LsiSymbolId): JimmerImmutableDraftTypePlan {
            compiledTypes[typeId]?.let { type -> return type }
            val type = schema.typesById[typeId]
                ?: throw ImmutablePrecompileException(
                    declarationId = typeId,
                    recoverable = true,
                    message = "Cannot resolve immutable draft type '${typeId.value}'",
                )
            val declaration = workspace[typeId] as? LsiTypeDeclaration
                ?: throw ImmutablePrecompileException(
                    declarationId = typeId,
                    recoverable = true,
                    message = "Cannot resolve LSI declaration of immutable draft type '${typeId.value}'",
                )
            val directSuperTypes = declaration.superTypes
                .filterIsInstance<LsiDeclaredType>()
                .filter { superType -> superType.declarationId in schema.typesById }
            val directSuperPlans = directSuperTypes.associate { superType ->
                superType.declarationId to compileType(superType.declarationId)
            }
            val primarySuperPlan = type.primarySuperTypeId?.let(directSuperPlans::getValue)
            val propsByName = type.props.associateBy(ImmutableProp::name)
            val primaryPropNames = primarySuperPlan
                ?.propsBySlot
                .orEmpty()
                .mapTo(linkedSetOf(), JimmerImmutableDraftPropPlan::name)
            val primaryAssignments = primarySuperPlan
                ?.propsBySlot
                .orEmpty()
                .map { superProp ->
                    val prop = requireNotNull(propsByName[superProp.name]) {
                        "Immutable primary-super property '${superProp.name}' is missing from '${type.id.value}'"
                    }
                    JimmerImmutableDraftSlotAssignment(
                        prop = prop,
                        slotIndex = superProp.slotIndex,
                        runtimeOwnerTypeId = superProp.runtimeOwnerTypeId,
                        role = JimmerImmutableDraftPropRole.INHERITED_PRIMARY,
                    )
                }
            val redefinedPropNames = linkedSetOf<String>()
            directSuperTypes.forEach { superType ->
                directSuperPlans.getValue(superType.declarationId).propsBySlot.forEach { superProp ->
                    if (superProp.name !in primaryPropNames) {
                        redefinedPropNames += superProp.name
                    }
                }
            }
            var nextSlot = primaryAssignments.size
            val redefinedAssignments = redefinedPropNames.map { propName ->
                val prop = requireNotNull(propsByName[propName]) {
                    "Immutable redefined property '$propName' is missing from '${type.id.value}'"
                }
                JimmerImmutableDraftSlotAssignment(
                    prop = prop,
                    slotIndex = nextSlot++,
                    runtimeOwnerTypeId = type.id,
                    role = JimmerImmutableDraftPropRole.REDEFINED,
                )
            }
            val declaredProps = declaration.memberIds
                .mapNotNull { memberId -> workspace[memberId] as? LsiProperty }
                .mapNotNull { property -> propsByName[property.name] }
                .filter { prop -> prop.declaringTypeId == type.id && !prop.overridden }
                .distinctBy(ImmutableProp::id)
                .let { props ->
                    val (idProps, otherProps) = props.partition { prop ->
                        prop.primaryMapping == PrimaryMapping.ID
                    }
                    idProps + otherProps
                }
            val declaredAssignments = declaredProps.map { prop ->
                JimmerImmutableDraftSlotAssignment(
                    prop = prop,
                    slotIndex = nextSlot++,
                    runtimeOwnerTypeId = type.id,
                    role = JimmerImmutableDraftPropRole.DECLARED,
                )
            }
            val assignments = primaryAssignments + redefinedAssignments + declaredAssignments
            val assignedPropIds = assignments.mapTo(linkedSetOf()) { assignment -> assignment.prop.id }
            val missingProps = type.props.filterNot { prop -> prop.id in assignedPropIds }
            if (missingProps.isNotEmpty()) {
                throw ImmutablePrecompileException(
                    declarationId = missingProps.first().declarationId,
                    message = "Cannot determine immutable draft slot for properties: " +
                        missingProps.joinToString { prop -> prop.id.value },
                )
            }
            val baseDependencyPropIds = buildSet {
                type.props.forEach { prop ->
                    prop.formulaDependencies.forEach { dependency -> addAll(dependency.propIds) }
                    prop.view?.dependencyPropIds?.forEach { dependencyPropId ->
                        if (schema.propsById[dependencyPropId]?.ownerTypeId == type.id) {
                            add(dependencyPropId)
                        }
                    }
                }
            }
            val propPlans = assignments.map { assignment ->
                assignment.toPlan(
                    ownerType = type,
                    schema = schema,
                    workspace = workspace,
                    baseDependencyPropIds = baseDependencyPropIds,
                    options = options,
                )
            }
            val runtimeDeclaredPropIds = propPlans
                .filter { prop -> prop.role == JimmerImmutableDraftPropRole.DECLARED }
                .map(JimmerImmutableDraftPropPlan::propId)
            val runtimeRedefinedPropIds = propPlans
                .filter { prop -> prop.role == JimmerImmutableDraftPropRole.REDEFINED }
                .map(JimmerImmutableDraftPropPlan::propId)
            val propPlansById = propPlans.associateBy(JimmerImmutableDraftPropPlan::propId)
            val kotlinDraftPropIds = runtimeDeclaredPropIds + runtimeRedefinedPropIds.filter { propId ->
                propPlansById.getValue(propId).genericSourceTarget
            }
            val validationAnnotations = declaration.annotations.constraintAnnotations(workspace)
            val customValidations = validationAnnotations.customValidations(workspace)
            return JimmerImmutableDraftTypePlan(
                typeId = type.id,
                qualifiedName = type.qualifiedName,
                kind = type.kind,
                sourceLanguage = declaration.origin.language,
                sourcePath = declaration.origin.source?.path,
                sourceBaseName = declaration.origin.source?.baseName(),
                documentation = declaration.documentation,
                visibility = declaration.visibility,
                typeParameters = declaration.typeParameters,
                selfType = LsiDeclaredType(
                    declarationId = type.id,
                    arguments = declaration.typeParameters.map { parameter ->
                        LsiTypeArgument.invariant(LsiTypeParameterRef(parameter.id))
                    },
                ),
                directSuperTypes = directSuperTypes,
                primarySuperTypeId = type.primarySuperTypeId,
                propsBySlot = propPlans,
                runtimeDeclaredPropIds = runtimeDeclaredPropIds,
                runtimeRedefinedPropIds = runtimeRedefinedPropIds,
                kotlinDraftPropIds = kotlinDraftPropIds,
                idPropId = type.idPropId,
                versionPropId = type.versionPropId,
                logicalDeletedPropId = type.logicalDeletedPropId,
                customValidations = customValidations,
            ).also { plan -> compiledTypes[typeId] = plan }
        }

        schema.types.forEach { type -> compileType(type.id) }
        return JimmerImmutableDraftCodegenSchema(
            jacksonFamily = options.jacksonFamily,
            types = compiledTypes.values.sortedBy(JimmerImmutableDraftTypePlan::typeId),
        )
    }
}

private data class JimmerImmutableDraftSlotAssignment(
    val prop: ImmutableProp,
    val slotIndex: Int,
    val runtimeOwnerTypeId: LsiSymbolId,
    val role: JimmerImmutableDraftPropRole,
) {
    fun toPlan(
        ownerType: ImmutableType,
        schema: ImmutableSchema,
        workspace: LsiWorkspace,
        baseDependencyPropIds: Set<LsiSymbolId>,
        options: JimmerImmutableDraftCodegenOptions,
    ): JimmerImmutableDraftPropPlan {
        val declaration = workspace[prop.declarationId] as? LsiProperty
            ?: throw ImmutablePrecompileException(
                declarationId = prop.declarationId,
                recoverable = true,
                message = "Cannot resolve LSI declaration of immutable draft property '${prop.id.value}'",
            )
        val propType = prop.type
        val primitive = propType is LsiPrimitiveType && !propType.boxed
        val languageFormula = prop.isLanguageFormula(declaration.origin.language)
        val valueRequired = prop.view == null && !languageFormula
        val valueState = when {
            !valueRequired -> JimmerImmutableDraftValueState.NONE
            prop.nullable || primitive -> JimmerImmutableDraftValueState.VALUE_AND_LOADED
            else -> JimmerImmutableDraftValueState.VALUE_ONLY
        }
        val idView = prop.view as? ImmutableView.Id
        val manyToManyView = prop.view as? ImmutableView.ManyToMany
        val targetIdPropId = prop.targetTypeId
            ?.let(schema.typesById::get)
            ?.idPropId
        val immutableReference = schema.isImmutableReference(prop)
        val codegenName = prop.generatedDraftCodegenName(workspace)
        val associatedIdName = StringUtil.identifier(codegenName, "Id")
        val associatedId = if (
            prop.association &&
            !prop.list &&
            prop.targetTypeId?.let(schema.typesById::get)?.kind == ImmutableTypeKind.ENTITY &&
            targetIdPropId != null &&
            schema.idViewPropIdsByBasePropId[prop.id].isNullOrEmpty() &&
            ownerType.props.none { candidate ->
                candidate.generatedDraftCodegenName(workspace) == associatedIdName
            }
        ) {
            JimmerImmutableAssociatedIdContract(associatedIdName, targetIdPropId)
        } else {
            null
        }
        val accessorStyle = declaration.accessorStyle(codegenName)
        val javaMethodSuffix = declaration.javaMethodSuffix(accessorStyle)
        val javaSetterName = "set$javaMethodSuffix"
        val javaBeanGetterName = declaration.javaBeanGetterName(accessorStyle, primitive)
        val slotName = prop.generatedDraftSlotName(workspace)
        val elementType = prop.elementTypeOrSelf()
        val annotationPlan = prop.toDraftAnnotationProjection(
            workspace = workspace,
            excludedUserAnnotationPrefixes = options.excludedUserAnnotationPrefixes,
        )
        val validationPlan = prop.toDraftValidationPlan(workspace)
        val writable = !languageFormula &&
            prop.primaryMapping != PrimaryMapping.DISCRIMINATOR &&
            manyToManyView == null
        val autoCreateSupported = (immutableReference || prop.list) &&
            !prop.genericTarget &&
            manyToManyView == null &&
            prop.formulaKind == FormulaKind.NONE &&
            prop.primaryMapping != PrimaryMapping.DISCRIMINATOR
        val referenceMutationSupported = immutableReference &&
            !prop.genericTarget &&
            manyToManyView == null &&
            prop.formulaKind == FormulaKind.NONE &&
            prop.primaryMapping != PrimaryMapping.DISCRIMINATOR
        return JimmerImmutableDraftPropPlan(
            propId = prop.id,
            declarationId = prop.declarationId,
            lineageRootId = prop.overrideChain.lastOrNull() ?: prop.declarationId,
            sourceDeclaringTypeId = prop.declaringTypeId,
            runtimeOwnerTypeId = runtimeOwnerTypeId,
            slotIndex = slotIndex,
            metadataSlotIndex = slotIndex.takeUnless {
                ownerType.kind == ImmutableTypeKind.MAPPED_SUPERCLASS
            },
            role = role,
            name = prop.name,
            codegenName = codegenName,
            sourceGetterName = declaration.getterName,
            documentation = prop.documentation,
            sourceDocumentation = declaration.sourceDocumentation,
            accessorStyle = accessorStyle,
            slotName = slotName,
            javaSetterName = javaSetterName,
            javaBeanGetterName = javaBeanGetterName,
            javaApplierName = "apply$javaMethodSuffix",
            javaAdderByName = "addInto$javaMethodSuffix",
            annotationPlan = annotationPlan,
            valueFieldName = if (valueState.hasValue) "__${codegenName}Value" else null,
            loadedStateFieldName = if (valueState.hasLoadedState) "__${codegenName}Loaded" else null,
            javaDeeperPropIdName = if (manyToManyView != null) {
                "DEEPER_PROP_ID_${slotName.removePrefix("SLOT_")}"
            } else {
                null
            },
            kotlinDeeperPropIdName = if (manyToManyView != null) {
                "DEEP_PROP_ID_${prop.name.legacyUpper()}"
            } else {
                null
            },
            type = prop.type,
            elementType = elementType,
            runtimeProp = schema.toDraftRuntimeProp(prop),
            targetTypeId = prop.targetTypeId,
            targetIdPropId = targetIdPropId,
            primitive = primitive,
            nullable = prop.nullable,
            list = prop.list,
            association = prop.association,
            immutableReference = immutableReference,
            genericTarget = prop.genericTarget,
            genericSourceTarget = prop.genericSourceTarget(workspace, role, ownerType.kind),
            languageFormula = languageFormula,
            valueState = valueState,
            visibilityControllable = prop.id in baseDependencyPropIds ||
                prop.formulaDependencies.isNotEmpty() ||
                prop.view != null,
            writable = writable,
            autoCreateSupported = autoCreateSupported,
            referenceMutationSupported = referenceMutationSupported,
            idViewBasePropId = idView?.basePropId,
            manyToManyBasePropId = manyToManyView?.basePropId,
            manyToManyDeeperPropId = manyToManyView?.deeperPropId,
            formulaDependencyPaths = prop.formulaDependencies.map(FormulaDependency::propIds),
            associatedId = associatedId,
            validationPlan = validationPlan,
        )
    }
}

private fun ImmutableProp.genericSourceTarget(
    workspace: LsiWorkspace,
    role: JimmerImmutableDraftPropRole,
    ownerKind: ImmutableTypeKind,
): Boolean {
    if (role == JimmerImmutableDraftPropRole.DECLARED) {
        return false
    }
    if (ownerKind == ImmutableTypeKind.MAPPED_SUPERCLASS) {
        return false
    }
    return (listOf(declarationId) + overrideChain)
        .distinct()
        .mapNotNull { propertyId -> workspace[propertyId] as? LsiProperty }
        .any { property -> property.type.targetType(list) is LsiTypeParameterRef }
}

private fun LsiTypeRef.targetType(list: Boolean): LsiTypeRef {
    if (!list) {
        return this
    }
    return (this as? LsiDeclaredType)
        ?.arguments
        ?.singleOrNull()
        ?.type
        ?: this
}

private fun ImmutableProp.isLanguageFormula(language: LsiLanguage): Boolean {
    return formulaKind == FormulaKind.LANGUAGE ||
        formulaKind == FormulaKind.ABSTRACT && language == LsiLanguage.JAVA
}

private fun LsiProperty.accessorStyle(propName: String): JimmerImmutableDraftAccessorStyle {
    return when (origin.language) {
        LsiLanguage.KOTLIN -> JimmerImmutableDraftAccessorStyle.KOTLIN_PROPERTY
        LsiLanguage.JAVA -> when {
            getterName != propName && getterName.startsWith("get") && getterName.length > 3 -> {
                JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET
            }
            getterName != propName && getterName.startsWith("is") && getterName.length > 2 -> {
                JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS
            }
            else -> JimmerImmutableDraftAccessorStyle.JAVA_BARE
        }
        LsiLanguage.UNKNOWN -> JimmerImmutableDraftAccessorStyle.UNKNOWN
    }
}

private fun LsiProperty.javaMethodSuffix(style: JimmerImmutableDraftAccessorStyle): String {
    return when (style) {
        JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET -> getterName.substring(3)
        JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS -> getterName.substring(2)
        JimmerImmutableDraftAccessorStyle.KOTLIN_PROPERTY,
        JimmerImmutableDraftAccessorStyle.JAVA_BARE,
        JimmerImmutableDraftAccessorStyle.UNKNOWN,
        -> getterName.replaceFirstChar(Char::uppercaseChar)
    }
}

private fun LsiProperty.javaBeanGetterName(
    style: JimmerImmutableDraftAccessorStyle,
    primitive: Boolean,
): String {
    if (
        style == JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET ||
        style == JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS
    ) {
        return getterName
    }
    val prefix = if (
        primitive &&
        (type as? LsiPrimitiveType)?.kind == LsiPrimitiveKind.BOOLEAN
    ) {
        "is"
    } else {
        "get"
    }
    return prefix + getterName.replaceFirstChar(Char::uppercaseChar)
}

private fun List<LsiAnnotation>.constraintAnnotations(workspace: LsiWorkspace): List<LsiAnnotation> {
    return filter { annotation ->
        val annotationType = workspace[annotation.type] as? LsiTypeDeclaration ?: return@filter false
        annotationType.annotations.any { metaAnnotation -> metaAnnotation.type in CONSTRAINT_ANNOTATIONS }
    }
}

private fun List<LsiAnnotation>.customValidations(workspace: LsiWorkspace): List<ImmutableValidation> {
    return mapNotNull { annotation ->
        val annotationType = workspace[annotation.type] as? LsiTypeDeclaration ?: return@mapNotNull null
        val constraint = annotationType.annotations.firstOrNull { metaAnnotation ->
            metaAnnotation.type in CONSTRAINT_ANNOTATIONS
        } ?: return@mapNotNull null
        val validatorTypeIds = constraint.classTypeIds("validatedBy")
        if (validatorTypeIds.isEmpty()) {
            return@mapNotNull null
        }
        ImmutableValidation(
            annotationTypeId = annotation.type,
            validatorTypeIds = validatorTypeIds,
            message = (annotation.arguments["message"]?.value as? LsiAnnotationValue.StringValue)?.value.orEmpty(),
            sourceAnnotationUseSiteTarget = annotation.useSiteTarget,
        )
    }
}

private fun LsiSource.baseName(): String {
    return path.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = path.substringAfterLast('/'))
}

private val CONSTRAINT_ANNOTATIONS = setOf(
    LsiSymbolId.type("jakarta.validation.Constraint"),
    LsiSymbolId.type("javax.validation.Constraint"),
)
