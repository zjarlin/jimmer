package site.addzero.lsi.apt

import kotlin.Metadata
import kotlin.metadata.jvm.KotlinClassMetadata
import site.addzero.lsi.model.mergeDeclarationsById
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPackageAnnotationScope
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.mergeLsiTypeSeeds
import site.addzero.lsi.model.toAnnotationMemberType
import site.addzero.lsi.model.toJvmCallableParameterType
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSource
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind

fun RoundEnvironment.toLsiWorkspace(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    val roundSymbols = toAptLsiRoundSymbols(processingEnvironment, frontendOptions)
    return roundSymbols.rootTypes.toLsiWorkspace(
        processingEnvironment = processingEnvironment,
        frontendOptions = frontendOptions,
        packageElements = roundSymbols.packageElements,
        sourceRootTypes = roundSymbols.sourceRootTypes,
        sourcePackageElements = roundSymbols.sourcePackageElements,
    )
}

fun Collection<TypeElement>.toLsiWorkspace(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
    packageElements: Collection<PackageElement>,
    additionalSeeds: Collection<LsiTypeSeed> = emptyList(),
    sourceRootTypes: Collection<TypeElement> = this,
    sourcePackageElements: Collection<PackageElement> = packageElements,
    knownSourceRootTypes: Map<String, LsiSource> = emptyMap(),
    fallbackSourceKind: LsiSourceKind = LsiSourceKind.SOURCE,
): LsiWorkspace {
    return AptLsiWorkspaceBuilder(
        processingEnvironment = processingEnvironment,
        frontendOptions = frontendOptions,
        sourceRootTypeNames = sourceRootTypes.mapTo(sortedSetOf()) { type ->
            type.topLevelEnclosingTypeName()
        },
        sourceRootPackageNames = sourcePackageElements.mapTo(sortedSetOf()) { packageElement ->
            packageElement.qualifiedName.toString()
        },
        knownSourceRootTypes = knownSourceRootTypes,
        fallbackSourceKind = fallbackSourceKind,
    ).build(
        rootTypes = this,
        packageElements = packageElements,
        additionalSeeds = additionalSeeds,
    )
}

fun TypeElement.toLsiTypeDeclaration(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
): LsiTypeDeclaration {
    val workspace = listOf(this).toLsiWorkspace(
        processingEnvironment = processingEnvironment,
        frontendOptions = frontendOptions,
        packageElements = listOf(processingEnvironment.elementUtils.getPackageOf(this)),
    )
    return requireNotNull(workspace[LsiSymbolId.type(qualifiedName.toString())] as? LsiTypeDeclaration)
}

/**
 * 在单个 APT 编译轮内把 javac 符号冻结为不可变 LSI 快照。
 */
class AptLsiWorkspaceBuilder(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
    sourceRootTypeNames: Set<String> = emptySet(),
    sourceRootPackageNames: Set<String> = emptySet(),
    knownSourceRootTypes: Map<String, LsiSource> = emptyMap(),
    fallbackSourceKind: LsiSourceKind = LsiSourceKind.SOURCE,
) {

    private val context = AptLsiContext(
        processingEnvironment = processingEnvironment,
        frontendOptions = frontendOptions,
        sourceRootTypeNames = sourceRootTypeNames,
        sourceRootPackageNames = sourceRootPackageNames,
        knownSourceRootTypes = knownSourceRootTypes,
        fallbackSourceKind = fallbackSourceKind,
    )

    fun build(
        rootTypes: Collection<TypeElement>,
        packageElements: Collection<PackageElement>,
        additionalSeeds: Collection<LsiTypeSeed> = emptyList(),
    ): LsiWorkspace {
        val sourceTypeElements = rootTypes
            .flatMap(::collectTypeElements)
            .distinctBy { typeElement -> typeElement.qualifiedName.toString() }
        val declarations = freezeSemanticDeclarations(sourceTypeElements, additionalSeeds)
        val annotationScopes = freezePackageAnnotationScopes(packageElements)
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

    private fun freezePackageAnnotationScopes(
        packageElements: Collection<PackageElement>,
    ): List<LsiPackageAnnotationScope> {
        return packageElements
            .distinctBy { packageElement -> packageElement.qualifiedName.toString() }
            .sortedBy { packageElement -> packageElement.qualifiedName.toString() }
            .mapNotNull { packageElement ->
                val annotations = packageElement.annotationMirrors
                if (annotations.isEmpty()) {
                    return@mapNotNull null
                }
                LsiPackageAnnotationScope(
                    packageName = packageElement.qualifiedName.toString(),
                    annotations = context.toLsiAnnotations(
                        annotations = annotations,
                        useSiteTarget = LsiAnnotationUseSiteTarget.PACKAGE,
                    ),
                    location = context.location(packageElement),
                    origin = context.origin(packageElement),
                )
            }
    }

    private fun freezeSemanticDeclarations(
        sourceTypeElements: Collection<TypeElement>,
        additionalSeeds: Collection<LsiTypeSeed>,
    ): List<LsiDeclaration> {
        val declarationsByTypeId = linkedMapOf<LsiSymbolId, List<LsiDeclaration>>()
        sourceTypeElements
            .sortedBy { typeElement -> typeElement.qualifiedName.toString() }
            .forEach { typeElement ->
                val typeId = LsiSymbolId.type(typeElement.qualifiedName.toString())
                declarationsByTypeId[typeId] = toLsiDeclarations(typeElement)
            }
        additionalSeeds.mergeLsiTypeSeeds().forEach { seed ->
            if (seed.typeId in declarationsByTypeId) {
                return@forEach
            }
            val typeElement = context.elements.getTypeElement(
                seed.typeId.requireTypeQualifiedName(),
            ) ?: return@forEach
            val header = toLsiTypeHeader(typeElement)
            if (seed.mode == LsiTypeSeedMode.HEADER && !header.requiresFullExternalDeclaration(context.frontendOptions)) {
                declarationsByTypeId[seed.typeId] = listOf(header)
                return@forEach
            }
            collectTypeElements(typeElement.topLevelEnclosingType())
                .sortedBy { nestedType -> nestedType.qualifiedName.toString() }
                .forEach { nestedType ->
                    val nestedTypeId = LsiSymbolId.type(nestedType.qualifiedName.toString())
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
            val typeElement = context.elements.getTypeElement(
                typeId.requireTypeQualifiedName(),
            ) ?: continue
            val header = toLsiTypeHeader(typeElement)
            val externalDeclarations = if (
                context.source(typeElement) != null ||
                header.requiresFullExternalDeclaration(context.frontendOptions)
            ) {
                toLsiDeclarations(typeElement)
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
            val typeElement = context.elements.getTypeElement(typeId.requireTypeQualifiedName()) ?: continue
            val (typeParameters, typeParameterIds) = context.toLsiTypeParameters(
                ownerId = typeId,
                parameters = typeElement.typeParameters,
            )
            val directSuperTypes = context.types.directSupertypes(typeElement.asType())
                .filterIsInstance<DeclaredType>()
                .mapNotNull { superType ->
                    context.toLsiType(superType, typeParameterIds) as? site.addzero.lsi.model.LsiDeclaredType
                }
            entries[typeId] = LsiTypeHierarchyEntry(
                id = typeId,
                qualifiedName = typeElement.qualifiedName.toString(),
                kind = typeElement.kind.toLsiTypeDeclarationKind(),
                typeParameters = typeParameters,
                directSuperTypes = directSuperTypes,
                source = context.source(typeElement),
                isExternal = true,
            )
            directSuperTypes.mapTo(pending) { superType -> superType.declarationId }
        }
        return entries.values.toList()
    }

    private fun collectTypeElements(rootType: TypeElement): List<TypeElement> {
        val result = mutableListOf<TypeElement>()
        val pending = ArrayDeque<TypeElement>()
        pending.add(rootType)
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            result += type
            type.enclosedElements
                .filterIsInstance<TypeElement>()
                .forEach(pending::addLast)
        }
        return result
    }

    private fun TypeElement.topLevelEnclosingType(): TypeElement {
        var topLevelType = this
        while (topLevelType.enclosingElement is TypeElement) {
            topLevelType = topLevelType.enclosingElement as TypeElement
        }
        return topLevelType
    }

    private fun toLsiDeclarations(typeElement: TypeElement): List<LsiDeclaration> {
        val typeId = LsiSymbolId.type(typeElement.qualifiedName.toString())
        val callables = typeElement.enclosedElements
            .filterIsInstance<ExecutableElement>()
            .filter { method -> method.kind == ElementKind.METHOD }
            .map { method -> method.toLsiCallable(typeElement) }
            .mergeDeclarationsById()
        val constructors = typeElement.enclosedElements
            .filterIsInstance<ExecutableElement>()
            .filter { constructor -> constructor.kind == ElementKind.CONSTRUCTOR }
            .map { constructor -> constructor.toLsiConstructor(typeElement) }
        val fields = typeElement.enclosedElements
            .filterIsInstance<VariableElement>()
            .filter { field -> field.kind == ElementKind.FIELD }
            .map { field -> field.toLsiField(typeId) }
        val enumEntries = typeElement.enclosedElements
            .filterIsInstance<VariableElement>()
            .filter { field -> field.kind == ElementKind.ENUM_CONSTANT }
            .map { entry -> entry.toLsiEnumEntry(typeId) }
        val typeDeclaration = toLsiTypeDeclaration(
            typeElement = typeElement,
            memberIds = (callables + constructors + fields).map(LsiDeclaration::id),
            enumEntries = enumEntries,
        )
        return buildList {
            add(typeDeclaration)
            addAll(callables)
            addAll(constructors)
            addAll(fields)
            addAll(enumEntries)
        }
    }

    private fun toLsiTypeHeader(typeElement: TypeElement): LsiTypeDeclaration {
        return toLsiTypeDeclaration(
            typeElement = typeElement,
            memberIds = emptyList(),
            enumEntries = emptyList(),
        )
    }

    private fun toLsiTypeDeclaration(
        typeElement: TypeElement,
        memberIds: List<LsiSymbolId>,
        enumEntries: List<LsiEnumEntry>,
    ): LsiTypeDeclaration {
        val typeId = LsiSymbolId.type(typeElement.qualifiedName.toString())
        val inheritedTypeParameterIds = context.typeParameterIdsInScope(typeElement)
        val (typeParameters, typeParameterIds) = context.toLsiTypeParameters(
            ownerId = typeId,
            parameters = typeElement.typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val superTypes = buildList {
            val superclass = typeElement.superclass
            if (superclass.kind != TypeKind.NONE) {
                add(context.toLsiType(superclass, typeParameterIds))
            }
            typeElement.interfaces.mapTo(this) { interfaceType ->
                context.toLsiType(interfaceType, typeParameterIds)
            }
        }
        return LsiTypeDeclaration(
            id = typeId,
            name = typeElement.simpleName.toString(),
            qualifiedName = typeElement.qualifiedName.toString(),
            kind = typeElement.kind.toLsiTypeDeclarationKind(),
            enclosingTypeId = (typeElement.enclosingElement as? TypeElement)?.let { enclosingType ->
                LsiSymbolId.type(enclosingType.qualifiedName.toString())
            },
            requiresEnclosingInstance =
                typeElement.enclosingElement is TypeElement &&
                    typeElement.kind == ElementKind.CLASS &&
                    Modifier.STATIC !in typeElement.modifiers,
            abstractDeclaration =
                Modifier.ABSTRACT in typeElement.modifiers ||
                    typeElement.kind == ElementKind.INTERFACE ||
                    typeElement.kind == ElementKind.ANNOTATION_TYPE,
            dataClass = false,
            visibility = typeElement.toLsiVisibility(),
            modality = typeElement.toLsiModality(),
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            enumEntries = enumEntries,
            annotationMembers = typeElement.toLsiAnnotationMembers(typeParameterIds),
            documentation = context.documentation(typeElement),
            sourceDocumentation = context.sourceDocumentation(typeElement),
            annotations = context.toLsiAnnotations(
                annotations = typeElement.annotationMirrors,
                useSiteTarget = LsiAnnotationUseSiteTarget.TYPE,
            ),
            location = context.location(typeElement),
            origin = context.origin(typeElement),
        )
    }

    private fun TypeElement.toLsiAnnotationMembers(
        typeParameterIds: Map<javax.lang.model.element.TypeParameterElement, LsiSymbolId>,
    ): List<LsiAnnotationMember> {
        if (kind != ElementKind.ANNOTATION_TYPE) {
            return emptyList()
        }
        val kotlinMetadata = kotlinAnnotationMetadata()
        return enclosedElements
            .filterIsInstance<ExecutableElement>()
            .filter { member -> member.kind == ElementKind.METHOD }
            .mapIndexed { sourceIndex, member ->
                val type = context.toLsiType(member.returnType, typeParameterIds)
                val name = member.simpleName.toString()
                LsiAnnotationMember(
                    name = name,
                    type = type.toAnnotationMemberType(),
                    vararg = name in kotlinMetadata?.varargNames.orEmpty(),
                    hasDefault = member.defaultValue != null,
                    declarationIndex = kotlinMetadata?.declarationIndicesByName?.get(name) ?: sourceIndex,
                )
            }
            .sortedBy(LsiAnnotationMember::name)
    }

    private fun TypeElement.kotlinAnnotationMetadata(): KotlinAnnotationMetadata? {
        val metadata = getAnnotation(Metadata::class.java) ?: return null
        val classMetadata = KotlinClassMetadata.readLenient(metadata) as? KotlinClassMetadata.Class
            ?: return null
        val parameters = classMetadata.kmClass.constructors
            .maxByOrNull { constructor -> constructor.valueParameters.size }
            ?.valueParameters
            .orEmpty()
        val varargNames = parameters
            .filter { parameter -> parameter.varargElementType != null }
            .mapTo(linkedSetOf()) { parameter -> parameter.name }
        val declarationIndicesByName = parameters
            .mapIndexed { index, parameter -> parameter.name to index }
            .toMap()
        return KotlinAnnotationMetadata(varargNames, declarationIndicesByName)
    }

    private fun ExecutableElement.toLsiCallable(owner: TypeElement): LsiDeclaration {
        return if (isLsiPropertyGetter()) {
            toLsiProperty(owner)
        } else {
            toLsiFunction(owner)
        }
    }

    private fun ExecutableElement.toLsiProperty(owner: TypeElement): LsiProperty {
        val ownerId = LsiSymbolId.type(owner.qualifiedName.toString())
        val propertyName = toLsiPropertyName(context.frontendOptions)
        val typeParameterIds = context.typeParameterIdsInScope(this)
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, propertyName),
            name = propertyName,
            ownerId = ownerId,
            type = context.toLsiType(returnType, typeParameterIds),
            getterName = simpleName.toString(),
            static = Modifier.STATIC in modifiers,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = toLsiCallableAnnotations(this),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun ExecutableElement.toLsiFunction(owner: TypeElement): LsiFunction {
        val ownerId = LsiSymbolId.type(owner.qualifiedName.toString())
        val functionId = context.toLsiCallableId(this)
        val inheritedTypeParameterIds = context.typeParameterIdsInScope(this)
        val (typeParameters, typeParameterIds) = context.toLsiTypeParameters(
            ownerId = functionId,
            parameters = typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val lsiParameters = parameters.mapIndexed { index, parameter ->
            parameter.toLsiParameter(
                callableId = functionId,
                index = index,
                typeParameterIds = typeParameterIds,
                vararg = isVarArgs && index == parameters.lastIndex,
            )
        }
        return LsiFunction(
            id = functionId,
            name = simpleName.toString(),
            ownerId = ownerId,
            returnType = context.toLsiType(returnType, typeParameterIds),
            parameters = lsiParameters,
            typeParameters = typeParameters,
            thrownTypes = thrownTypes.map { thrownType ->
                context.toLsiType(thrownType, typeParameterIds)
            },
            static = Modifier.STATIC in modifiers,
            modality = toLsiModality(),
            overrides = toLsiOverrides(owner),
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = toLsiCallableAnnotations(this),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun ExecutableElement.toLsiConstructor(owner: TypeElement): LsiConstructor {
        val ownerId = LsiSymbolId.type(owner.qualifiedName.toString())
        val constructorId = context.toLsiCallableId(this)
        val inheritedTypeParameterIds = context.typeParameterIdsInScope(this)
        val (typeParameters, typeParameterIds) = context.toLsiTypeParameters(
            ownerId = constructorId,
            parameters = typeParameters,
            inheritedIds = inheritedTypeParameterIds,
        )
        val lsiParameters = parameters.mapIndexed { index, parameter ->
            parameter.toLsiParameter(
                callableId = constructorId,
                index = index,
                typeParameterIds = typeParameterIds,
                vararg = isVarArgs && index == parameters.lastIndex,
            )
        }
        return LsiConstructor(
            id = constructorId,
            ownerId = ownerId,
            parameters = lsiParameters,
            typeParameters = typeParameters,
            thrownTypes = thrownTypes.map { thrownType ->
                context.toLsiType(thrownType, typeParameterIds)
            },
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = context.toLsiAnnotations(
                annotations = annotationMirrors,
                useSiteTarget = LsiAnnotationUseSiteTarget.CONSTRUCTOR,
            ),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun VariableElement.toLsiField(ownerId: LsiSymbolId): LsiField {
        val typeParameterIds = context.typeParameterIdsInScope(this)
        val declarationAnnotations = context.toLsiAnnotations(
            annotations = annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
        )
        val typeAnnotations = context.toLsiAnnotations(
            annotations = asType().annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
        )
        return LsiField(
            id = LsiSymbolId.field(ownerId, simpleName.toString()),
            name = simpleName.toString(),
            ownerId = ownerId,
            type = context.toLsiType(asType(), typeParameterIds),
            mutable = Modifier.FINAL !in modifiers,
            static = Modifier.STATIC in modifiers,
            visibility = toLsiVisibility(),
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = (declarationAnnotations + typeAnnotations).distinct(),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun VariableElement.toLsiParameter(
        callableId: LsiSymbolId,
        index: Int,
        typeParameterIds: Map<javax.lang.model.element.TypeParameterElement, LsiSymbolId>,
        vararg: Boolean,
    ): LsiParameter {
        val declarationAnnotations = context.toLsiAnnotations(
            annotations = annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.PARAMETER,
        )
        val typeAnnotations = context.toLsiAnnotations(
            annotations = asType().annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.PARAMETER,
        )
        val parameterType = if (vararg) {
            val arrayType = asType() as? javax.lang.model.type.ArrayType
                ?: error("APT vararg parameter must have an array type: $this")
            arrayType.componentType
        } else {
            asType()
        }
        return LsiParameter(
            id = LsiSymbolId.parameter(callableId, index, simpleName.toString()),
            name = simpleName.toString(),
            callableId = callableId,
            index = index,
            type = context.toLsiType(parameterType, typeParameterIds).toJvmCallableParameterType(),
            vararg = vararg,
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = declarationAnnotations + typeAnnotations,
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun VariableElement.toLsiEnumEntry(ownerId: LsiSymbolId): LsiEnumEntry {
        return LsiEnumEntry(
            id = LsiSymbolId.enumEntry(ownerId, simpleName.toString()),
            name = simpleName.toString(),
            ownerId = ownerId,
            documentation = context.documentation(this),
            sourceDocumentation = context.sourceDocumentation(this),
            annotations = context.toLsiAnnotations(
                annotations = annotationMirrors,
                useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
            ),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun toLsiCallableAnnotations(method: ExecutableElement): List<site.addzero.lsi.model.LsiAnnotation> {
        val methodAnnotationMirrors = method.annotationMirrors
        val methodAnnotations = context.toLsiAnnotations(
            annotations = methodAnnotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.METHOD,
        )
        val returnTypeAnnotations = context.toLsiAnnotations(
            annotations = method.returnType.annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
        val unmatchedMethodAnnotations = methodAnnotations
            .map { annotation -> annotation.copy(useSiteTarget = null) }
            .toMutableList()
        val distinctReturnTypeAnnotations = returnTypeAnnotations.filter { annotation ->
            val annotationWithoutTarget = annotation.copy(useSiteTarget = null)
            val duplicateIndex = unmatchedMethodAnnotations.indexOf(annotationWithoutTarget)
            if (duplicateIndex == -1) {
                true
            } else {
                unmatchedMethodAnnotations.removeAt(duplicateIndex)
                false
            }
        }
        return methodAnnotations + distinctReturnTypeAnnotations
    }

    private fun ExecutableElement.toLsiOverrides(owner: TypeElement): List<LsiOverride> {
        val overridesById = linkedMapOf<LsiSymbolId, Int>()
        for ((superType, distance) in owner.superTypesByDistance()) {
            val superElement = superType.asElement() as? TypeElement ?: continue
            val overriddenMethods = superElement.enclosedElements
                .filterIsInstance<ExecutableElement>()
                .filter { candidate ->
                    candidate.kind == ElementKind.METHOD && context.elements.overrides(this, candidate, owner)
                }
            for (overriddenMethod in overriddenMethods) {
                val declarationId = context.toLsiCallableId(overriddenMethod)
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

    private fun TypeElement.superTypesByDistance(): List<Pair<DeclaredType, Int>> {
        val result = mutableListOf<Pair<DeclaredType, Int>>()
        val pending = ArrayDeque<Pair<DeclaredType, Int>>()
        context.types.directSupertypes(asType())
            .filterIsInstance<DeclaredType>()
            .mapTo(pending) { superType -> superType to 1 }
        val visited = mutableMapOf<String, Int>()
        while (pending.isNotEmpty()) {
            val (superType, distance) = pending.removeFirst()
            val superElement = superType.asElement() as? TypeElement ?: continue
            val key = superElement.qualifiedName.toString()
            val previousDistance = visited[key]
            if (previousDistance != null && previousDistance <= distance) {
                continue
            }
            visited[key] = distance
            result += superType to distance
            context.types.directSupertypes(superType)
                .filterIsInstance<DeclaredType>()
                .mapTo(pending) { ancestor -> ancestor to distance + 1 }
        }
        return result
    }
}

private fun TypeElement.topLevelEnclosingTypeName(): String {
    var topLevelType = this
    while (topLevelType.enclosingElement is TypeElement) {
        topLevelType = topLevelType.enclosingElement as TypeElement
    }
    return topLevelType.qualifiedName.toString()
}

private data class KotlinAnnotationMetadata(
    val varargNames: Set<String>,
    val declarationIndicesByName: Map<String, Int>,
)

private fun LsiTypeDeclaration.requiresFullExternalDeclaration(
    frontendOptions: LsiFrontendOptions,
): Boolean {
    return kind == LsiTypeDeclarationKind.ANNOTATION ||
        kind == LsiTypeDeclarationKind.ENUM ||
        annotations.any { annotation ->
            annotation.type in frontendOptions.fullExternalDeclarationAnnotationTypeIds
        }
}

private fun ElementKind.toLsiTypeDeclarationKind(): LsiTypeDeclarationKind {
    return when (this) {
        ElementKind.CLASS -> LsiTypeDeclarationKind.CLASS
        ElementKind.INTERFACE -> LsiTypeDeclarationKind.INTERFACE
        ElementKind.ENUM -> LsiTypeDeclarationKind.ENUM
        ElementKind.ANNOTATION_TYPE -> LsiTypeDeclarationKind.ANNOTATION
        ElementKind.RECORD -> LsiTypeDeclarationKind.RECORD
        else -> error("Unsupported APT type declaration kind: $this")
    }
}
