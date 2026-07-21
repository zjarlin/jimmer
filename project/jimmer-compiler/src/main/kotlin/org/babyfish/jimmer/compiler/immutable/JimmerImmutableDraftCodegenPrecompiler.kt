package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerImmutableDraftCodegenPrecompiler {

    fun compile(
        schema: JimmerImmutableSchema,
        workspace: LsiWorkspace,
    ): JimmerImmutableDraftCodegenSchema {
        val compiledTypes = mutableMapOf<LsiSymbolId, JimmerImmutableDraftTypePlan>()

        fun compileType(typeId: LsiSymbolId): JimmerImmutableDraftTypePlan {
            compiledTypes[typeId]?.let { type -> return type }
            val type = schema.typesById[typeId]
                ?: throw JimmerImmutablePrecompileException(
                    declarationId = typeId,
                    recoverable = true,
                    message = "Cannot resolve immutable draft type '${typeId.value}'",
                )
            val declaration = workspace[typeId] as? LsiTypeDeclaration
                ?: throw JimmerImmutablePrecompileException(
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
            val propsByName = type.props.associateBy(JimmerImmutableProp::name)
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
                .distinctBy(JimmerImmutableProp::id)
                .let { props ->
                    val (idProps, otherProps) = props.partition { prop ->
                        prop.primaryMapping == JimmerImmutablePrimaryMapping.ID
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
                throw JimmerImmutablePrecompileException(
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
            val dependencySymbols = linkedSetOf<LsiSymbolId>()

            fun includeDependency(symbolId: LsiSymbolId) {
                if (!dependencySymbols.add(symbolId)) {
                    return
                }
                workspace[symbolId]?.origin?.originatingSymbols?.forEach(::includeDependency)
            }

            fun includeSemanticProp(propId: LsiSymbolId) {
                includeDependency(propId)
                val semanticProp = schema.propsById[propId] ?: return
                includeDependency(semanticProp.declarationId)
                includeDependency(semanticProp.declaringTypeId)
                semanticProp.overrideChain.forEach(::includeDependency)
            }

            includeDependency(type.id)
            declaration.annotations.forEach { annotation ->
                annotation.referencedTypeIds().forEach(::includeDependency)
            }
            declaration.typeParameters.forEach { parameter ->
                parameter.upperBounds.forEach { bound ->
                    bound.referencedTypeIds().forEach(::includeDependency)
                }
            }
            directSuperTypes.forEach { superType ->
                superType.referencedTypeIds().forEach(::includeDependency)
            }
            listOfNotNull(type.idPropId, type.versionPropId, type.logicalDeletedPropId)
                .forEach(::includeSemanticProp)
            customValidations.forEach { validation ->
                includeDependency(validation.annotationTypeId)
                validation.validatorTypeIds.forEach(::includeDependency)
            }
            propPlans.forEach { plan ->
                includeSemanticProp(plan.propId)
                includeDependency(plan.declarationId)
                includeSemanticProp(plan.lineageRootId)
                includeDependency(plan.sourceDeclaringTypeId)
                includeDependency(plan.runtimeOwnerTypeId)
                plan.type.referencedTypeIds().forEach(::includeDependency)
                plan.targetTypeId?.let(::includeDependency)
                plan.targetIdPropId?.let(::includeSemanticProp)
                plan.idViewBasePropId?.let(::includeSemanticProp)
                plan.manyToManyBasePropId?.let(::includeSemanticProp)
                plan.manyToManyDeeperPropId?.let(::includeSemanticProp)
                plan.formulaDependencyPaths.flatten().forEach(::includeSemanticProp)
                plan.associatedId?.targetIdPropId?.let(::includeSemanticProp)
                plan.validationAnnotations.forEach { annotation ->
                    annotation.referencedTypeIds().forEach(::includeDependency)
                }
                plan.customValidations.forEach { validation ->
                    includeDependency(validation.annotationTypeId)
                    validation.validatorTypeIds.forEach(::includeDependency)
                }
                val semanticProp = schema.propsById.getValue(plan.propId)
                semanticProp.annotations.forEach { annotation ->
                    annotation.referencedTypeIds().forEach(::includeDependency)
                }
                semanticProp.converter?.let { converter ->
                    includeDependency(converter.converterTypeId)
                    converter.sourceType?.referencedTypeIds()?.forEach(::includeDependency)
                    converter.targetType?.referencedTypeIds()?.forEach(::includeDependency)
                }
                (semanticProp.transientResolver as? JimmerTransientResolver.Type)
                    ?.typeId
                    ?.let(::includeDependency)
            }
            val dependencySources = dependencySymbols
                .mapNotNull { symbolId -> workspace[symbolId]?.origin?.source }
                .distinct()
                .sorted()
            return JimmerImmutableDraftTypePlan(
                typeId = type.id,
                qualifiedName = type.qualifiedName,
                kind = type.kind,
                sourceLanguage = declaration.origin.language,
                sourcePath = declaration.origin.source?.path,
                sourceBaseName = declaration.origin.source?.baseName(),
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
                validationAnnotations = validationAnnotations,
                customValidations = customValidations,
                artifactOriginatingSymbols = setOf(type.id),
                artifactOriginatingSources = listOfNotNull(declaration.origin.source),
                dependencySymbols = dependencySymbols,
                dependencySources = dependencySources,
            ).also { plan -> compiledTypes[typeId] = plan }
        }

        schema.types.forEach { type -> compileType(type.id) }
        return JimmerImmutableDraftCodegenSchema(
            types = compiledTypes.values.sortedBy(JimmerImmutableDraftTypePlan::typeId),
        )
    }
}

private data class JimmerImmutableDraftSlotAssignment(
    val prop: JimmerImmutableProp,
    val slotIndex: Int,
    val runtimeOwnerTypeId: LsiSymbolId,
    val role: JimmerImmutableDraftPropRole,
) {
    fun toPlan(
        ownerType: JimmerImmutableType,
        schema: JimmerImmutableSchema,
        workspace: LsiWorkspace,
        baseDependencyPropIds: Set<LsiSymbolId>,
    ): JimmerImmutableDraftPropPlan {
        val declaration = workspace[prop.declarationId] as? LsiProperty
            ?: throw JimmerImmutablePrecompileException(
                declarationId = prop.declarationId,
                recoverable = true,
                message = "Cannot resolve LSI declaration of immutable draft property '${prop.id.value}'",
            )
        val primitive = prop.type is LsiPrimitiveType && !prop.type.boxed
        val languageFormula = prop.isLanguageFormula(declaration.origin.language)
        val valueRequired = prop.view == null && !languageFormula
        val valueState = when {
            !valueRequired -> JimmerImmutableDraftValueState.NONE
            prop.nullable || primitive -> JimmerImmutableDraftValueState.VALUE_AND_LOADED
            else -> JimmerImmutableDraftValueState.VALUE_ONLY
        }
        val idView = prop.view as? JimmerImmutableView.Id
        val manyToManyView = prop.view as? JimmerImmutableView.ManyToMany
        val targetIdPropId = prop.targetTypeId
            ?.let(schema.typesById::get)
            ?.idPropId
        val codegenName = declaration.codegenName(prop)
        val associatedIdName = StringUtil.identifier(codegenName, "Id")
        val associatedId = if (
            prop.association &&
            !prop.list &&
            prop.targetTypeId?.let(schema.typesById::get)?.kind == JimmerImmutableTypeKind.ENTITY &&
            targetIdPropId != null &&
            schema.idViewPropIdsByBasePropId[prop.id].isNullOrEmpty() &&
            ownerType.props.none { candidate -> candidate.codegenName(workspace) == associatedIdName }
        ) {
            JimmerImmutableAssociatedIdContract(associatedIdName, targetIdPropId)
        } else {
            null
        }
        val accessorStyle = declaration.accessorStyle(codegenName)
        val javaMethodSuffix = declaration.javaMethodSuffix(accessorStyle)
        val javaSetterName = "set$javaMethodSuffix"
        val javaBeanGetterName = declaration.javaBeanGetterName(accessorStyle, primitive)
        val slotName = "SLOT_${codegenName.legacyUpper()}"
        val validationAnnotations = prop.annotations.constraintAnnotations(workspace)
        val writable = !languageFormula &&
            prop.primaryMapping != JimmerImmutablePrimaryMapping.DISCRIMINATOR &&
            manyToManyView == null
        val autoCreateSupported = (prop.association || prop.list) &&
            !prop.genericTarget &&
            manyToManyView == null &&
            prop.formulaKind == JimmerFormulaKind.NONE &&
            prop.primaryMapping != JimmerImmutablePrimaryMapping.DISCRIMINATOR
        val referenceMutationSupported = prop.association &&
            !prop.genericTarget &&
            manyToManyView == null &&
            prop.formulaKind == JimmerFormulaKind.NONE &&
            prop.primaryMapping != JimmerImmutablePrimaryMapping.DISCRIMINATOR
        return JimmerImmutableDraftPropPlan(
            propId = prop.id,
            declarationId = prop.declarationId,
            lineageRootId = prop.overrideChain.lastOrNull() ?: prop.declarationId,
            sourceDeclaringTypeId = prop.declaringTypeId,
            runtimeOwnerTypeId = runtimeOwnerTypeId,
            slotIndex = slotIndex,
            metadataSlotIndex = slotIndex.takeUnless {
                ownerType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS
            },
            role = role,
            name = prop.name,
            codegenName = codegenName,
            sourceGetterName = declaration.getterName,
            sourceDocumentation = declaration.sourceDocumentation,
            accessorStyle = accessorStyle,
            slotName = slotName,
            javaSetterName = javaSetterName,
            javaBeanGetterName = javaBeanGetterName,
            javaApplierName = "apply$javaMethodSuffix",
            javaAdderByName = "addInto$javaMethodSuffix",
            valueFieldName = if (valueState.hasValue) "__${codegenName}Value" else null,
            loadedStateFieldName = if (valueState.hasLoadedState) "__${codegenName}Loaded" else null,
            deeperPropIdName = if (manyToManyView != null) {
                "DEEPER_PROP_ID_${slotName.removePrefix("SLOT_")}"
            } else {
                null
            },
            type = prop.type,
            elementType = prop.elementType(),
            targetTypeId = prop.targetTypeId,
            targetIdPropId = targetIdPropId,
            primitive = primitive,
            nullable = prop.nullable,
            list = prop.list,
            association = prop.association,
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
            formulaDependencyPaths = prop.formulaDependencies.map(JimmerFormulaDependency::propIds),
            associatedId = associatedId,
            validationAnnotations = validationAnnotations,
            customValidations = prop.validations,
        )
    }
}

private fun JimmerImmutableProp.elementType(): LsiTypeRef {
    if (!list) {
        return type
    }
    return (type as? LsiDeclaredType)
        ?.arguments
        ?.singleOrNull()
        ?.type
        ?: type
}

private fun JimmerImmutableProp.genericSourceTarget(
    workspace: LsiWorkspace,
    role: JimmerImmutableDraftPropRole,
    ownerKind: JimmerImmutableTypeKind,
): Boolean {
    if (role == JimmerImmutableDraftPropRole.DECLARED) {
        return false
    }
    if (ownerKind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS) {
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

private fun JimmerImmutableProp.isLanguageFormula(language: LsiLanguage): Boolean {
    return formulaKind == JimmerFormulaKind.LANGUAGE ||
        formulaKind == JimmerFormulaKind.ABSTRACT && language == LsiLanguage.JAVA
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

private fun LsiProperty.codegenName(prop: JimmerImmutableProp): String {
    if (origin.language != LsiLanguage.JAVA) {
        return prop.name
    }
    if (getterName.startsWith("get") && getterName.length > 3 && getterName[3].isUpperCase()) {
        return getterName.substring(3).replaceFirstChar(Char::lowercaseChar)
    }
    if (
        prop.type is LsiPrimitiveType &&
        !prop.type.boxed &&
        prop.type.kind == LsiPrimitiveKind.BOOLEAN &&
        getterName != prop.name &&
        getterName.startsWith("is") &&
        getterName.length > 2 &&
        getterName[2].isUpperCase()
    ) {
        return getterName.substring(2).replaceFirstChar(Char::lowercaseChar)
    }
    return getterName
}

private fun JimmerImmutableProp.codegenName(workspace: LsiWorkspace): String {
    val declaration = workspace[declarationId] as? LsiProperty ?: return name
    return declaration.codegenName(this)
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

private fun List<LsiAnnotation>.customValidations(workspace: LsiWorkspace): List<JimmerValidation> {
    return mapNotNull { annotation ->
        val annotationType = workspace[annotation.type] as? LsiTypeDeclaration ?: return@mapNotNull null
        val constraint = annotationType.annotations.firstOrNull { metaAnnotation ->
            metaAnnotation.type in CONSTRAINT_ANNOTATIONS
        } ?: return@mapNotNull null
        val validatorTypeIds = constraint.classTypeIds("validatedBy")
        if (validatorTypeIds.isEmpty()) {
            return@mapNotNull null
        }
        JimmerValidation(
            annotationTypeId = annotation.type,
            validatorTypeIds = validatorTypeIds,
            message = (annotation.arguments["message"]?.value as? LsiAnnotationValue.StringValue)?.value.orEmpty(),
        )
    }
}

private fun LsiSource.baseName(): String {
    return path.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = path.substringAfterLast('/'))
}

private fun String.legacyUpper(): String {
    var previousUpper = true
    return buildString {
        for (character in this@legacyUpper) {
            val upper = character.isUpperCase()
            if (upper) {
                if (!previousUpper) {
                    append('_')
                }
                append(character)
            } else {
                append(character.uppercaseChar())
            }
            previousUpper = upper
        }
    }
}

private fun LsiAnnotation.referencedTypeIds(): Set<LsiSymbolId> {
    return buildSet {
        add(type)
        arguments.values.forEach { argument -> addAll(argument.value.referencedTypeIds()) }
    }
}

private fun LsiAnnotationValue.referencedTypeIds(): Set<LsiSymbolId> {
    return when (this) {
        is LsiAnnotationValue.ClassValue -> type.referencedTypeIds()
        is LsiAnnotationValue.EnumValue -> setOf(enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.referencedTypeIds()
        is LsiAnnotationValue.ArrayValue -> elements.flatMapTo(linkedSetOf()) { element ->
            element.referencedTypeIds()
        }
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> emptySet()
    }
}

private fun LsiTypeRef.referencedTypeIds(): Set<LsiSymbolId> {
    return when (this) {
        is LsiDeclaredType -> buildSet {
            add(declarationId)
            arguments.forEach { argument ->
                argument.type?.referencedTypeIds()?.let(::addAll)
            }
        }
        is LsiArrayType -> elementType.referencedTypeIds()
        is LsiTypeParameterRef -> setOf(parameterId)
        is LsiPrimitiveType,
        is LsiUnresolvedType,
        -> emptySet()
    }
}

private val CONSTRAINT_ANNOTATIONS = setOf(
    LsiSymbolId.type("jakarta.validation.Constraint"),
    LsiSymbolId.type("javax.validation.Constraint"),
)
