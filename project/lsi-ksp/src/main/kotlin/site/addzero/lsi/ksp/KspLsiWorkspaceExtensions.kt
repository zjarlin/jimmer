package site.addzero.lsi.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import site.addzero.lsi.model.mergeDeclarationsById
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFileAnnotationScope
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.toJvmCallableParameterType
import site.addzero.lsi.model.mergeLsiTypeSeeds
import site.addzero.lsi.model.toAnnotationMemberType

fun Resolver.toLsiWorkspace(
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    val sourceFiles = getAllFiles().toStableKspFileList()
    val fileScopePlan = sourceFiles.toKspLsiFileScopePlan()
    val rootTypes = sourceFiles.asSequence()
        .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
        .filter { declaration -> declaration.classKind != ClassKind.ENUM_ENTRY }
        .toList()
    return rootTypes.toLsiWorkspace(
        resolver = this,
        frontendOptions = frontendOptions,
        fileScopes = fileScopePlan.validScopes,
    )
}

fun Collection<KSClassDeclaration>.toLsiWorkspace(
    resolver: Resolver,
    frontendOptions: LsiFrontendOptions,
    fileScopes: Collection<KspLsiFileScopeInput>,
    additionalSeeds: Collection<LsiTypeSeed> = emptyList(),
): LsiWorkspace {
    return KspLsiWorkspaceBuilder(resolver, frontendOptions).build(
        rootTypes = this,
        fileScopes = fileScopes,
        additionalSeeds = additionalSeeds,
    )
}

fun KSClassDeclaration.toLsiTypeDeclaration(
    resolver: Resolver,
    frontendOptions: LsiFrontendOptions,
): LsiTypeDeclaration {
    val qualifiedName = requireNotNull(qualifiedName?.asString()) {
        "KSP LSI type declaration must have a qualified name"
    }
    val workspace = listOf(this).toLsiWorkspace(
        resolver = resolver,
        frontendOptions = frontendOptions,
        fileScopes = listOfNotNull(containingFile).toKspLsiFileScopePlan().validScopes,
    )
    return requireNotNull(workspace[LsiSymbolId.type(qualifiedName)] as? LsiTypeDeclaration)
}

/**
 * 在单个 KSP 编译轮内把有效符号冻结为不可变 LSI 快照。
 */
@OptIn(KspExperimental::class)
internal class KspLsiWorkspaceBuilder(
    private val resolver: Resolver,
    private val frontendOptions: LsiFrontendOptions,
) {

    private val context = KspLsiContext(resolver, frontendOptions)

    private val typeContext = KspLsiTypeContext(resolver)

    private val annotationContext = KspLsiAnnotationContext(resolver)

    fun build(
        rootTypes: Collection<KSClassDeclaration>,
        fileScopes: Collection<KspLsiFileScopeInput>,
        additionalSeeds: Collection<LsiTypeSeed> = emptyList(),
    ): LsiWorkspace {
        require(rootTypes.all(KSClassDeclaration::validate)) {
            "KSP LSI workspace can only freeze symbols that are valid in the current round"
        }
        val sourceTypeDeclarations = rootTypes
            .flatMap(::collectTypeDeclarations)
            .distinctBy { declaration -> declaration.qualifiedName?.asString() }
        val declarations = freezeSemanticDeclarations(sourceTypeDeclarations, additionalSeeds)
        val annotationScopes = freezeFileAnnotationScopes(fileScopes)
        val sources = buildList {
            declarations.mapNotNullTo(this) { declaration -> declaration.origin.source }
            annotationScopes.mapNotNullTo(this) { annotationScope -> annotationScope.origin.source }
        }
        return LsiWorkspace(
            sources = sources,
            declarations = declarations,
            typeHierarchy = freezeTypeHierarchy(declarations.referencedTypeIds()),
            annotationScopes = annotationScopes,
        )
    }

    private fun freezeFileAnnotationScopes(
        fileScopes: Collection<KspLsiFileScopeInput>,
    ): List<LsiFileAnnotationScope> {
        return fileScopes
            .sortedBy(KspLsiFileScopeInput::normalizedSourcePath)
            .map { scope ->
                val file = scope.file
                LsiFileAnnotationScope(
                    packageName = file.packageName.asString(),
                    logicalPath = scope.logicalPath,
                    annotations = annotationContext.toLsiAnnotations(
                        annotations = scope.annotations.asSequence(),
                        useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
                    ),
                    location = context.location(file),
                    origin = context.origin(file),
                )
            }
    }

    private fun freezeSemanticDeclarations(
        sourceTypeDeclarations: Collection<KSClassDeclaration>,
        additionalSeeds: Collection<LsiTypeSeed>,
    ): List<LsiDeclaration> {
        val declarationsByTypeId = linkedMapOf<LsiSymbolId, List<LsiDeclaration>>()
        sourceTypeDeclarations
            .sortedBy { declaration -> declaration.qualifiedName?.asString().orEmpty() }
            .forEach { declaration ->
                val qualifiedName = declaration.qualifiedName?.asString()?.takeIf(String::isNotBlank)
                    ?: return@forEach
                val typeId = LsiSymbolId.type(qualifiedName)
                declarationsByTypeId[typeId] = toLsiDeclarations(declaration)
            }
        additionalSeeds.mergeLsiTypeSeeds().forEach { seed ->
            if (seed.typeId in declarationsByTypeId) {
                return@forEach
            }
            val declaration = resolver.getClassDeclarationByName(
                seed.typeId.requireTypeQualifiedName(),
            ) ?: return@forEach
            if (!declaration.validate()) {
                return@forEach
            }
            val header = toLsiTypeHeader(declaration, seed.typeId)
            if (seed.mode == LsiTypeSeedMode.HEADER && !header.requiresFullExternalDeclaration(frontendOptions)) {
                declarationsByTypeId[seed.typeId] = listOf(header)
                return@forEach
            }
            collectTypeDeclarations(declaration.topLevelEnclosingType())
                .sortedBy { nestedType -> nestedType.qualifiedName?.asString().orEmpty() }
                .forEach nestedTypeLoop@{ nestedType ->
                    val qualifiedName = nestedType.qualifiedName?.asString()?.takeIf(String::isNotBlank)
                        ?: return@nestedTypeLoop
                    val nestedTypeId = LsiSymbolId.type(qualifiedName)
                    declarationsByTypeId.putIfAbsent(nestedTypeId, toLsiDeclarations(nestedType))
                }
        }
        val pendingTypeIds = ArrayDeque<LsiSymbolId>()
        declarationsByTypeId.values
            .flatten()
            .referencedTypeIds()
            .sorted()
            .forEach(pendingTypeIds::addLast)
        while (pendingTypeIds.isNotEmpty()) {
            val typeId = pendingTypeIds.removeFirst()
            if (typeId in declarationsByTypeId) {
                continue
            }
            val declaration = resolver.getClassDeclarationByName(
                typeId.requireTypeQualifiedName(),
            ) ?: continue
            if (!declaration.validate()) {
                continue
            }
            val header = toLsiTypeHeader(declaration, typeId)
            val externalDeclarations = if (
                declaration.origin == Origin.JAVA ||
                declaration.origin == Origin.KOTLIN ||
                header.requiresFullExternalDeclaration(frontendOptions)
            ) {
                toLsiDeclarations(declaration)
            } else {
                listOf(header)
            }
            declarationsByTypeId[typeId] = externalDeclarations
            externalDeclarations
                .referencedTypeIds()
                .sorted()
                .forEach(pendingTypeIds::addLast)
        }
        return declarationsByTypeId.values.flatten()
    }

    private fun freezeTypeHierarchy(seedIds: Set<LsiSymbolId>): List<LsiTypeHierarchyEntry> {
        val entries = linkedMapOf<LsiSymbolId, LsiTypeHierarchyEntry>()
        val pending = ArrayDeque(seedIds.sorted())
        while (pending.isNotEmpty()) {
            val typeId = pending.removeFirst()
            if (typeId in entries) {
                continue
            }
            val declaration = resolver.getClassDeclarationByName(typeId.requireTypeQualifiedName()) ?: continue
            val (typeParameters, typeParameterIds) = typeContext.toLsiTypeParameters(
                ownerId = typeId,
                parameters = declaration.typeParameters,
            )
            val directSuperTypes = declaration.superTypes
                .mapNotNull { superType ->
                    if (superType.resolve().isError) {
                        null
                    } else {
                        typeContext.toLsiType(superType, typeParameterIds) as? site.addzero.lsi.model.LsiDeclaredType
                    }
                }
                .filterNot { superType -> superType.declarationId == typeId }
                .filterNot { superType ->
                    declaration.classKind.isImplicitObjectSuperType(superType)
                }
                .distinct()
                .toList()
            entries[typeId] = LsiTypeHierarchyEntry(
                id = typeId,
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = declaration.classKind.toLsiTypeDeclarationKind(),
                typeParameters = typeParameters,
                directSuperTypes = directSuperTypes,
                source = context.source(declaration),
                isExternal = true,
            )
            directSuperTypes.mapTo(pending) { superType -> superType.declarationId }
        }
        return entries.values.toList()
    }

    private fun collectTypeDeclarations(rootType: KSClassDeclaration): List<KSClassDeclaration> {
        val result = mutableListOf<KSClassDeclaration>()
        val pending = ArrayDeque<KSClassDeclaration>()
        pending.add(rootType)
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            result += type
            type.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { declaration -> declaration.classKind != ClassKind.ENUM_ENTRY }
                .forEach(pending::addLast)
        }
        return result
    }

    private fun KSClassDeclaration.topLevelEnclosingType(): KSClassDeclaration {
        var topLevelType = this
        while (topLevelType.parentDeclaration is KSClassDeclaration) {
            topLevelType = topLevelType.parentDeclaration as KSClassDeclaration
        }
        return topLevelType
    }

    private fun toLsiDeclarations(typeDeclaration: KSClassDeclaration): List<LsiDeclaration> {
        val qualifiedName = requireNotNull(typeDeclaration.qualifiedName?.asString()) {
            "KSP LSI type declaration must have a qualified name"
        }
        val typeId = LsiSymbolId.type(qualifiedName)
        val declaredProperties = typeDeclaration.getDeclaredProperties().toList()
        val javaOwner = typeDeclaration.isLsiJavaDeclaration()
        val kotlinProperties = if (javaOwner) {
            emptyList()
        } else {
            declaredProperties.map { property -> property.toLsiProperty(typeDeclaration) }
        }
        val fields = declaredProperties
            .filter { field -> field.isLsiJavaField(javaOwner) }
            .map { field -> field.toLsiField(typeId) }
        val declaredFunctions = typeDeclaration.getDeclaredFunctions()
            .filterNot(KSFunctionDeclaration::isConstructor)
            .toList()
        val javaGetterProperties = declaredFunctions
            .filter(KSFunctionDeclaration::isLsiJavaPropertyGetter)
            .map { function -> function.toLsiJavaProperty(typeDeclaration) }
        val functions = declaredFunctions
            .filterNot(KSFunctionDeclaration::isLsiJavaPropertyGetter)
            .map { function -> function.toLsiFunction(typeDeclaration) }
        val constructors = typeDeclaration.getConstructors()
            .map { constructor -> constructor.toLsiConstructor(typeDeclaration) }
            .toList()
        val callables = (kotlinProperties + javaGetterProperties + functions).mergeDeclarationsById()
        val enumEntries = typeDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration -> declaration.classKind == ClassKind.ENUM_ENTRY }
            .map { entry -> entry.toLsiEnumEntry(typeId) }
            .toList()
        val lsiType = toLsiTypeDeclaration(
            typeDeclaration = typeDeclaration,
            typeId = typeId,
            memberIds = (callables + constructors + fields).map(LsiDeclaration::id),
            enumEntries = enumEntries,
        )
        return buildList {
            add(lsiType)
            addAll(callables)
            addAll(constructors)
            addAll(fields)
            addAll(enumEntries)
        }
    }

    private fun toLsiTypeHeader(
        typeDeclaration: KSClassDeclaration,
        typeId: LsiSymbolId,
    ): LsiTypeDeclaration {
        return toLsiTypeDeclaration(
            typeDeclaration = typeDeclaration,
            typeId = typeId,
            memberIds = emptyList(),
            enumEntries = emptyList(),
        )
    }

    private fun toLsiTypeDeclaration(
        typeDeclaration: KSClassDeclaration,
        typeId: LsiSymbolId,
        memberIds: List<LsiSymbolId>,
        enumEntries: List<LsiEnumEntry>,
    ): LsiTypeDeclaration {
        val inheritedTypeParameterIds = typeContext.typeParameterIdsInScope(typeDeclaration)
        val (typeParameters, typeParameterIds) = typeContext.toLsiTypeParameters(
            ownerId = typeId,
            parameters = typeDeclaration.typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val javaDeclaration = typeDeclaration.origin == Origin.JAVA || typeDeclaration.origin == Origin.JAVA_LIB
        val javaRecord = javaDeclaration && typeDeclaration.isJavaRecord()
        val enclosingDeclaration = typeDeclaration.parentDeclaration as? KSClassDeclaration
        val javaMemberClassRequiresEnclosingInstance =
            javaDeclaration &&
                typeDeclaration.classKind == ClassKind.CLASS &&
                enclosingDeclaration?.classKind in setOf(ClassKind.CLASS, ClassKind.ENUM_CLASS) &&
                Modifier.JAVA_STATIC !in typeDeclaration.modifiers &&
                !javaRecord
        return LsiTypeDeclaration(
            id = typeId,
            name = typeDeclaration.simpleName.asString(),
            qualifiedName = typeId.requireTypeQualifiedName(),
            kind = if (javaRecord) {
                LsiTypeDeclarationKind.RECORD
            } else {
                typeDeclaration.classKind.toLsiTypeDeclarationKind()
            },
            enclosingTypeId = enclosingDeclaration?.toLsiTypeId(),
            requiresEnclosingInstance =
                Modifier.INNER in typeDeclaration.modifiers || javaMemberClassRequiresEnclosingInstance,
            abstractDeclaration =
                Modifier.ABSTRACT in typeDeclaration.modifiers ||
                    (!javaDeclaration && Modifier.SEALED in typeDeclaration.modifiers) ||
                    typeDeclaration.classKind == ClassKind.INTERFACE ||
                    typeDeclaration.classKind == ClassKind.ANNOTATION_CLASS,
            dataClass = typeDeclaration.classKind == ClassKind.CLASS && Modifier.DATA in typeDeclaration.modifiers,
            visibility = typeDeclaration.toLsiVisibility(),
            modality = typeDeclaration.toLsiModality(),
            typeParameters = typeParameters,
            superTypes = typeDeclaration.superTypes
                .map { type -> typeContext.toLsiType(type, typeParameterIds) }
                .filterNot { superType ->
                    superType is LsiDeclaredType && superType.declarationId == typeId
                }
                .filterNot { superType ->
                    typeDeclaration.classKind.isImplicitObjectSuperType(superType)
                }
                .distinct()
                .toList(),
            memberIds = memberIds,
            enumEntries = enumEntries,
            annotationMembers = typeDeclaration.toLsiAnnotationMembers(typeParameterIds),
            documentation = context.documentation(typeDeclaration),
            sourceDocumentation = context.sourceDocumentation(typeDeclaration),
            annotations = annotationContext.toLsiAnnotations(
                annotations = typeDeclaration.annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.TYPE,
            ),
            location = context.location(typeDeclaration),
            origin = context.origin(typeDeclaration),
        )
    }

    private fun ClassKind.isImplicitObjectSuperType(type: LsiTypeRef): Boolean {
        return this in setOf(ClassKind.INTERFACE, ClassKind.ANNOTATION_CLASS) &&
            type is LsiDeclaredType &&
            type.declarationId == JAVA_LANG_OBJECT_ID
    }

    private fun KSClassDeclaration.isJavaRecord(): Boolean {
        return superTypes.any { superType ->
            val resolvedType = superType.resolve()
            !resolvedType.isError &&
                resolvedType.declaration.qualifiedName?.asString() == JAVA_LANG_RECORD
        }
    }

    private fun KSClassDeclaration.toLsiAnnotationMembers(
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId>,
    ): List<LsiAnnotationMember> {
        if (classKind != ClassKind.ANNOTATION_CLASS) {
            return emptyList()
        }
        val constructor = primaryConstructor ?: getConstructors().firstOrNull()
        if (origin == Origin.KOTLIN || origin == Origin.KOTLIN_LIB) {
            val parameters = constructor?.parameters.orEmpty()
            if (parameters.isNotEmpty()) {
                return parameters.mapIndexed { index, parameter ->
                    val parameterType = typeContext.toLsiType(parameter.type, typeParameterIds)
                    LsiAnnotationMember(
                        name = parameter.name?.asString()?.takeIf(String::isNotBlank)
                            ?: error("Kotlin annotation member must have a name"),
                        type = if (parameter.isVararg) {
                            LsiArrayType(parameterType).toAnnotationMemberType()
                        } else {
                            parameterType.toAnnotationMemberType()
                        },
                        vararg = parameter.isVararg,
                        hasDefault = parameter.hasDefault,
                        declarationIndex = index,
                    )
                }.sortedBy(LsiAnnotationMember::name)
            }
        }
        val propertyMembers = declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter { member -> member.getter != null }
            .mapIndexed { index, member ->
                LsiAnnotationMember(
                    name = member.simpleName.asString(),
                    type = typeContext.toLsiType(member.type, typeParameterIds).toAnnotationMemberType(),
                    declarationIndex = index,
                )
            }
            .toList()
        if (propertyMembers.isNotEmpty()) {
            return propertyMembers.sortedBy(LsiAnnotationMember::name)
        }
        return getAllFunctions()
            .filterNot(KSFunctionDeclaration::isConstructor)
            .mapNotNull { member ->
                val returnType = member.returnType ?: return@mapNotNull null
                member to LsiAnnotationMember(
                    name = member.simpleName.asString(),
                    type = typeContext.toLsiType(returnType, typeParameterIds).toAnnotationMemberType(),
                )
            }
            .toList()
            .mapIndexed { index, (_, member) -> member.copy(declarationIndex = index) }
            .sortedBy(LsiAnnotationMember::name)
    }

    private fun KSPropertyDeclaration.toLsiProperty(owner: KSClassDeclaration): LsiProperty {
        val ownerId = owner.toLsiTypeId()
        val propertyName = simpleName.asString()
        val typeParameterIds = typeContext.typeParameterIdsInScope(this)
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, propertyName),
            name = propertyName,
            ownerId = ownerId,
            type = typeContext.toLsiType(type, typeParameterIds),
            getterName = propertyName,
            mutable = isMutable,
            static = Modifier.JAVA_STATIC in modifiers,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = toLsiPropertyAnnotations(),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSPropertyDeclaration.toLsiField(ownerId: LsiSymbolId): LsiField {
        val typeParameterIds = typeContext.typeParameterIdsInScope(this)
        val declarationAnnotations = annotationContext.toLsiAnnotations(
            annotations = annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
        )
        val typeAnnotations = annotationContext.toLsiAnnotations(
            annotations = type.annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
        )
        return LsiField(
            id = LsiSymbolId.field(ownerId, simpleName.asString()),
            name = simpleName.asString(),
            ownerId = ownerId,
            type = typeContext.toLsiType(type, typeParameterIds),
            mutable = isMutable,
            static = Modifier.JAVA_STATIC in modifiers,
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = mergeProjectedAnnotationChannels(declarationAnnotations, typeAnnotations),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSFunctionDeclaration.toLsiJavaProperty(owner: KSClassDeclaration): LsiProperty {
        val ownerId = owner.toLsiTypeId()
        val propertyName = toLsiJavaPropertyName(frontendOptions)
        val typeParameterIds = typeContext.typeParameterIdsInScope(this)
        val returnType = requireNotNull(returnType)
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, propertyName),
            name = propertyName,
            ownerId = ownerId,
            type = typeContext.toLsiType(returnType, typeParameterIds),
            getterName = simpleName.asString(),
            static = false,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = toLsiFunctionAnnotations(),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSFunctionDeclaration.toLsiFunction(owner: KSClassDeclaration): LsiFunction {
        val ownerId = owner.toLsiTypeId()
        val functionId = typeContext.toLsiCallableId(this)
        val inheritedTypeParameterIds = typeContext.typeParameterIdsInScope(this)
        val (typeParameters, typeParameterIds) = typeContext.toLsiTypeParameters(
            ownerId = functionId,
            parameters = typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val lsiParameters = parameters.mapIndexed { index, parameter ->
            parameter.toLsiParameter(functionId, index, typeParameterIds)
        }
        return LsiFunction(
            id = functionId,
            name = simpleName.asString(),
            ownerId = ownerId,
            returnType = returnType?.let { returnType ->
                typeContext.toLsiType(returnType, typeParameterIds)
            } ?: LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            parameters = lsiParameters,
            receiverType = extensionReceiver?.let { receiverType ->
                typeContext.toLsiType(receiverType, typeParameterIds).toJvmCallableParameterType()
            },
            suspending = Modifier.SUSPEND in modifiers,
            typeParameters = typeParameters,
            thrownTypes = resolver.getJvmCheckedException(this).map { thrownType ->
                typeContext.toLsiType(thrownType, typeParameterIds)
            }.toList(),
            static = functionKind == FunctionKind.STATIC || Modifier.JAVA_STATIC in modifiers,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = toLsiFunctionAnnotations(),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSFunctionDeclaration.toLsiConstructor(owner: KSClassDeclaration): LsiConstructor {
        val ownerId = owner.toLsiTypeId()
        val constructorId = typeContext.toLsiCallableId(this)
        val inheritedTypeParameterIds = typeContext.typeParameterIdsInScope(this)
        val (typeParameters, typeParameterIds) = typeContext.toLsiTypeParameters(
            ownerId = constructorId,
            parameters = typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val lsiParameters = parameters.mapIndexed { index, parameter ->
            parameter.toLsiParameter(constructorId, index, typeParameterIds)
        }
        return LsiConstructor(
            id = constructorId,
            ownerId = ownerId,
            primary = owner.primaryConstructor == this,
            parameters = lsiParameters,
            typeParameters = typeParameters,
            thrownTypes = resolver.getJvmCheckedException(this).map { thrownType ->
                typeContext.toLsiType(thrownType, typeParameterIds)
            }.toList(),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = annotationContext.toLsiAnnotations(
                annotations = annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.CONSTRUCTOR,
            ),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSValueParameter.toLsiParameter(
        callableId: LsiSymbolId,
        index: Int,
        typeParameterIds: Map<KSTypeParameter, LsiSymbolId>,
    ): LsiParameter {
        val parameterName = name?.asString()?.takeIf(String::isNotBlank) ?: "p$index"
        val parameterAnnotations = annotationContext.toLsiAnnotations(
            annotations = annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.PARAMETER,
        )
        val typeAnnotations = annotationContext.toLsiAnnotations(
            annotations = type.annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.PARAMETER,
        )
        return LsiParameter(
            id = LsiSymbolId.parameter(callableId, index, parameterName),
            name = parameterName,
            callableId = callableId,
            index = index,
            type = typeContext.toLsiType(type, typeParameterIds).toJvmCallableParameterType(),
            vararg = isVararg,
            hasDefault = hasDefault,
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = mergeProjectedAnnotationChannels(parameterAnnotations, typeAnnotations),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSClassDeclaration.toLsiEnumEntry(ownerId: LsiSymbolId): LsiEnumEntry {
        return LsiEnumEntry(
            id = LsiSymbolId.enumEntry(ownerId, simpleName.asString()),
            name = simpleName.asString(),
            ownerId = ownerId,
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = annotationContext.toLsiAnnotations(
                annotations = annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
            ),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSPropertyDeclaration.toLsiPropertyAnnotations(): List<LsiAnnotation> {
        val propertyAnnotations = annotationContext.toLsiAnnotations(
            annotations = annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.PROPERTY,
        )
        val getterAnnotations = getter?.let { getter ->
            annotationContext.toLsiAnnotations(
                annotations = getter.annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
            )
        }.orEmpty()
        val typeAnnotations = annotationContext.toLsiAnnotations(
            annotations = type.annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
        val getterTypeAnnotations = getter?.returnType?.let { returnType ->
            annotationContext.toLsiAnnotations(
                annotations = returnType.annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
            )
        }.orEmpty()
        return mergeProjectedAnnotationChannels(
            propertyAnnotations,
            getterAnnotations,
            typeAnnotations,
            getterTypeAnnotations,
        )
    }

    private fun KSFunctionDeclaration.toLsiFunctionAnnotations(): List<LsiAnnotation> {
        val functionAnnotations = annotationContext.toLsiAnnotations(
            annotations = annotations,
            useSiteTarget = LsiAnnotationUseSiteTarget.METHOD,
        )
        val returnAnnotations = returnType?.let { returnType ->
            annotationContext.toLsiAnnotations(
                annotations = returnType.annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
            )
        }.orEmpty()
        return mergeProjectedAnnotationChannels(functionAnnotations, returnAnnotations)
    }

    private fun KSDeclaration.toLsiOverrides(owner: KSClassDeclaration): List<LsiOverride> {
        val overridesById = linkedMapOf<LsiSymbolId, Int>()
        for ((superType, distance) in owner.superTypesByDistance()) {
            val superDeclaration = superType.declaration as? KSClassDeclaration ?: continue
            val candidates = overrideCandidates(superDeclaration)
            for (candidate in candidates) {
                if (!resolver.overrides(this, candidate, owner)) {
                    continue
                }
                val declarationId = candidate.toLsiDeclarationId()
                val previousDistance = overridesById[declarationId]
                if (previousDistance == null || distance < previousDistance) {
                    overridesById[declarationId] = distance
                }
            }
        }
        return overridesById
            .map { (declarationId, distance) -> LsiOverride(declarationId, distance) }
            .sortedWith(compareBy(LsiOverride::distance, LsiOverride::declarationId))
    }

    private fun KSDeclaration.overrideCandidates(
        superDeclaration: KSClassDeclaration,
    ): Sequence<KSDeclaration> {
        val propertyLike = this is KSPropertyDeclaration ||
            this is KSFunctionDeclaration && isLsiJavaPropertyGetter()
        if (propertyLike) {
            return sequence {
                yieldAll(superDeclaration.getDeclaredProperties())
                yieldAll(
                    superDeclaration.getDeclaredFunctions()
                        .filterNot(KSFunctionDeclaration::isConstructor)
                        .filter(KSFunctionDeclaration::isLsiJavaPropertyGetter),
                )
            }
        }
        if (this is KSFunctionDeclaration) {
            return superDeclaration.getDeclaredFunctions()
                .filterNot(KSFunctionDeclaration::isConstructor)
                .filterNot(KSFunctionDeclaration::isLsiJavaPropertyGetter)
                .map { function -> function as KSDeclaration }
        }
        return emptySequence()
    }

    private fun KSDeclaration.toLsiDeclarationId(): LsiSymbolId {
        return when (this) {
            is KSPropertyDeclaration -> {
                val owner = parentDeclaration as KSClassDeclaration
                LsiSymbolId.property(owner.toLsiTypeId(), simpleName.asString())
            }
            is KSFunctionDeclaration -> typeContext.toLsiDeclarationId(this, frontendOptions)
            else -> error("Unsupported KSP callable declaration: ${javaClass.name}")
        }
    }

    private fun KSClassDeclaration.superTypesByDistance(): List<Pair<KSType, Int>> {
        val result = mutableListOf<Pair<KSType, Int>>()
        val pending = ArrayDeque<Pair<KSType, Int>>()
        superTypes.map { type -> type.resolve() }.mapTo(pending) { type -> type to 1 }
        val visited = mutableMapOf<String, Int>()
        while (pending.isNotEmpty()) {
            val (superType, distance) = pending.removeFirst()
            val superDeclaration = superType.declaration as? KSClassDeclaration ?: continue
            val key = superType.toKspStableSignature()
            val previousDistance = visited[key]
            if (previousDistance != null && previousDistance <= distance) {
                continue
            }
            visited[key] = distance
            result += superType to distance
            val substitutions = superDeclaration.typeParameters
                .zip(superType.arguments)
                .toMap()
            superDeclaration.superTypes
                .map { type -> typeContext.substitute(type.resolve(), substitutions) }
                .mapTo(pending) { type -> type to distance + 1 }
        }
        return result
    }
}

/** 跨 KSP 投影通道按 occurrence 合并，保留任一通道中的最大重复次数。 */
private fun mergeProjectedAnnotationChannels(
    vararg channels: List<LsiAnnotation>,
): List<LsiAnnotation> {
    val maxOccurrences = mutableMapOf<LsiAnnotation, Int>()
    return buildList {
        channels.forEach { channel ->
            val channelOccurrences = mutableMapOf<LsiAnnotation, Int>()
            channel.forEach { annotation ->
                val occurrence = channelOccurrences.getOrDefault(annotation, 0) + 1
                channelOccurrences[annotation] = occurrence
                if (occurrence > maxOccurrences.getOrDefault(annotation, 0)) {
                    add(annotation)
                }
            }
            channelOccurrences.forEach { (annotation, occurrences) ->
                val previous = maxOccurrences.getOrDefault(annotation, 0)
                if (occurrences > previous) {
                    maxOccurrences[annotation] = occurrences
                }
            }
        }
    }
}

private fun LsiTypeDeclaration.requiresFullExternalDeclaration(
    frontendOptions: LsiFrontendOptions,
): Boolean {
    return kind == LsiTypeDeclarationKind.ANNOTATION ||
        kind == LsiTypeDeclarationKind.ENUM ||
        annotations.any { annotation ->
            annotation.type in frontendOptions.fullExternalDeclarationAnnotationTypeIds
        }
}

private fun KSClassDeclaration.isLsiJavaDeclaration(): Boolean {
    return origin == Origin.JAVA || origin == Origin.JAVA_LIB
}

private fun KSPropertyDeclaration.isLsiJavaField(javaOwner: Boolean): Boolean {
    return javaOwner && getter == null
}

private const val JAVA_LANG_RECORD = "java.lang.Record"

private val JAVA_LANG_OBJECT_ID = LsiSymbolId.type("java.lang.Object")

private fun KSClassDeclaration.toLsiTypeId(): LsiSymbolId {
    val qualifiedName = requireNotNull(qualifiedName?.asString()) {
        "KSP LSI type declaration must have a qualified name"
    }
    return LsiSymbolId.type(qualifiedName)
}

private fun ClassKind.toLsiTypeDeclarationKind(): LsiTypeDeclarationKind {
    return when (this) {
        ClassKind.CLASS -> LsiTypeDeclarationKind.CLASS
        ClassKind.INTERFACE -> LsiTypeDeclarationKind.INTERFACE
        ClassKind.ENUM_CLASS -> LsiTypeDeclarationKind.ENUM
        ClassKind.ANNOTATION_CLASS -> LsiTypeDeclarationKind.ANNOTATION
        ClassKind.OBJECT -> LsiTypeDeclarationKind.OBJECT
        ClassKind.ENUM_ENTRY -> error("KSP enum entries are not type declarations")
    }
}
