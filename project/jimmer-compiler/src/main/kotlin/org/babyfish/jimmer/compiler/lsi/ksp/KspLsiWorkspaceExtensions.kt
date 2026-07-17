package org.babyfish.jimmer.compiler.lsi.ksp

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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import org.babyfish.jimmer.compiler.lsi.mergeDeclarationsById
import org.babyfish.jimmer.compiler.lsi.referencedTypeIds
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
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
import site.addzero.lsi.model.LsiWorkspace

fun Resolver.toLsiWorkspace(
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    val rootTypes = getAllFiles()
        .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
        .filter { declaration -> declaration.classKind != ClassKind.ENUM_ENTRY }
        .toList()
    return rootTypes.toLsiWorkspace(this, frontendOptions)
}

fun Collection<KSClassDeclaration>.toLsiWorkspace(
    resolver: Resolver,
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    return KspLsiWorkspaceBuilder(resolver, frontendOptions).build(this)
}

fun KSClassDeclaration.toLsiTypeDeclaration(
    resolver: Resolver,
    frontendOptions: LsiFrontendOptions,
): LsiTypeDeclaration {
    val qualifiedName = requireNotNull(qualifiedName?.asString()) {
        "KSP LSI type declaration must have a qualified name"
    }
    val workspace = listOf(this).toLsiWorkspace(resolver, frontendOptions)
    return requireNotNull(workspace[LsiSymbolId.type(qualifiedName)] as? LsiTypeDeclaration)
}

/**
 * 在单个 KSP 编译轮内把有效符号冻结为不可变 LSI 快照。
 */
@OptIn(KspExperimental::class)
class KspLsiWorkspaceBuilder(
    private val resolver: Resolver,
    private val frontendOptions: LsiFrontendOptions,
) {

    private val context = KspLsiContext()

    private val typeContext = KspLsiTypeContext(resolver)

    private val annotationContext = KspLsiAnnotationContext(resolver)

    fun build(rootTypes: Collection<KSClassDeclaration>): LsiWorkspace {
        require(rootTypes.all(KSClassDeclaration::validate)) {
            "KSP LSI workspace can only freeze symbols that are valid in the current round"
        }
        val sourceTypeDeclarations = rootTypes
            .flatMap(::collectTypeDeclarations)
            .distinctBy { declaration -> declaration.qualifiedName?.asString() }
        val declarations = freezeSemanticDeclarations(sourceTypeDeclarations)
        val sources = declarations.mapNotNull { declaration -> declaration.origin.source }
        return LsiWorkspace(
            sources = sources,
            declarations = declarations,
            typeHierarchy = freezeTypeHierarchy(declarations.referencedTypeIds()),
        )
    }

    private fun freezeSemanticDeclarations(
        sourceTypeDeclarations: Collection<KSClassDeclaration>,
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
                header.requiresFullExternalDeclaration()
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
                .map { type -> type.resolve() }
                .filterNot(KSType::isError)
                .mapNotNull { superType ->
                    typeContext.toLsiType(superType, typeParameterIds) as? site.addzero.lsi.model.LsiDeclaredType
                }
                .filterNot { superType -> superType.declarationId == typeId }
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

    private fun toLsiDeclarations(typeDeclaration: KSClassDeclaration): List<LsiDeclaration> {
        val qualifiedName = requireNotNull(typeDeclaration.qualifiedName?.asString()) {
            "KSP LSI type declaration must have a qualified name"
        }
        val typeId = LsiSymbolId.type(qualifiedName)
        val declaredProperties = typeDeclaration.getDeclaredProperties().toList()
        val kotlinProperties = declaredProperties
            .filterNot(KSPropertyDeclaration::isLsiJavaField)
            .map { property -> property.toLsiProperty(typeDeclaration) }
        val fields = declaredProperties
            .filter(KSPropertyDeclaration::isLsiJavaField)
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
        return LsiTypeDeclaration(
            id = typeId,
            name = typeDeclaration.simpleName.asString(),
            qualifiedName = typeId.requireTypeQualifiedName(),
            kind = typeDeclaration.classKind.toLsiTypeDeclarationKind(),
            enclosingTypeId = (typeDeclaration.parentDeclaration as? KSClassDeclaration)?.toLsiTypeId(),
            dataClass = typeDeclaration.classKind == ClassKind.CLASS && Modifier.DATA in typeDeclaration.modifiers,
            visibility = typeDeclaration.toLsiVisibility(),
            modality = typeDeclaration.toLsiModality(),
            typeParameters = typeParameters,
            superTypes = typeDeclaration.superTypes
                .map { type -> typeContext.toLsiType(type.resolve(), typeParameterIds) }
                .filterNot { superType ->
                    superType is site.addzero.lsi.model.LsiDeclaredType && superType.declarationId == typeId
                }
                .distinct()
                .toList(),
            memberIds = memberIds,
            enumEntries = enumEntries,
            documentation = context.documentation(typeDeclaration),
            annotations = annotationContext.toLsiAnnotations(
                annotations = typeDeclaration.annotations,
                useSiteTarget = LsiAnnotationUseSiteTarget.TYPE,
            ),
            location = context.location(typeDeclaration),
            origin = context.origin(typeDeclaration),
        )
    }

    private fun KSPropertyDeclaration.toLsiProperty(owner: KSClassDeclaration): LsiProperty {
        val ownerId = owner.toLsiTypeId()
        val propertyName = simpleName.asString()
        val typeParameterIds = typeContext.typeParameterIdsInScope(this)
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, propertyName),
            name = propertyName,
            ownerId = ownerId,
            type = typeContext.toLsiType(type.resolve(), typeParameterIds),
            getterName = propertyName,
            mutable = isMutable,
            static = Modifier.JAVA_STATIC in modifiers,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
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
            type = typeContext.toLsiType(type.resolve(), typeParameterIds),
            mutable = isMutable,
            static = Modifier.JAVA_STATIC in modifiers,
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            annotations = (declarationAnnotations + typeAnnotations).distinct(),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun KSFunctionDeclaration.toLsiJavaProperty(owner: KSClassDeclaration): LsiProperty {
        val ownerId = owner.toLsiTypeId()
        val propertyName = toLsiJavaPropertyName(frontendOptions)
        val typeParameterIds = typeContext.typeParameterIdsInScope(this)
        val resolvedReturnType = requireNotNull(returnType).resolve()
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, propertyName),
            name = propertyName,
            ownerId = ownerId,
            type = typeContext.toLsiType(resolvedReturnType, typeParameterIds),
            getterName = simpleName.asString(),
            static = false,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
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
            returnType = returnType?.resolve()?.let { returnType ->
                typeContext.toLsiType(returnType, typeParameterIds)
            } ?: LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            parameters = lsiParameters,
            receiverType = extensionReceiver?.resolve()?.let { receiverType ->
                typeContext.toLsiType(receiverType, typeParameterIds)
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
            type = typeContext.toLsiType(type.resolve(), typeParameterIds),
            vararg = isVararg,
            hasDefault = hasDefault,
            annotations = (parameterAnnotations + typeAnnotations).distinct(),
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
        return (propertyAnnotations + getterAnnotations + typeAnnotations + getterTypeAnnotations).distinct()
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
        return (functionAnnotations + returnAnnotations).distinct()
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

private fun LsiTypeDeclaration.requiresFullExternalDeclaration(): Boolean {
    return kind == LsiTypeDeclarationKind.ANNOTATION ||
        annotations.any { annotation -> annotation.type in JIMMER_MANAGED_TYPE_ANNOTATIONS }
}

private fun KSPropertyDeclaration.isLsiJavaField(): Boolean {
    return origin in setOf(Origin.JAVA, Origin.JAVA_LIB) && getter == null
}

private val JIMMER_MANAGED_TYPE_ANNOTATIONS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.Immutable"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Entity"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable"),
)

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
