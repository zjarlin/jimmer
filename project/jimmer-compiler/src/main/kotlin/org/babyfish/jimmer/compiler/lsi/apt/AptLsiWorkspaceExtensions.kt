package org.babyfish.jimmer.compiler.lsi.apt

import org.babyfish.jimmer.compiler.lsi.mergeDeclarationsById
import org.babyfish.jimmer.compiler.lsi.referencedTypeIds
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
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
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiWorkspace
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind

fun RoundEnvironment.toLsiWorkspace(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    val rootTypes = rootElements.filterIsInstance<TypeElement>()
    return rootTypes.toLsiWorkspace(processingEnvironment, frontendOptions)
}

fun Collection<TypeElement>.toLsiWorkspace(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
): LsiWorkspace {
    return AptLsiWorkspaceBuilder(processingEnvironment, frontendOptions).build(this)
}

fun TypeElement.toLsiTypeDeclaration(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
): LsiTypeDeclaration {
    val workspace = listOf(this).toLsiWorkspace(processingEnvironment, frontendOptions)
    return requireNotNull(workspace[LsiSymbolId.type(qualifiedName.toString())] as? LsiTypeDeclaration)
}

/**
 * 在单个 APT 编译轮内把 javac 符号冻结为不可变 LSI 快照。
 */
class AptLsiWorkspaceBuilder(
    processingEnvironment: ProcessingEnvironment,
    frontendOptions: LsiFrontendOptions,
) {

    private val context = AptLsiContext(processingEnvironment, frontendOptions)

    fun build(rootTypes: Collection<TypeElement>): LsiWorkspace {
        val sourceTypeElements = rootTypes
            .flatMap(::collectTypeElements)
            .distinctBy { typeElement -> typeElement.qualifiedName.toString() }
        val declarations = freezeSemanticDeclarations(sourceTypeElements)
        val sources = declarations.mapNotNull { declaration -> declaration.origin.source }
        return LsiWorkspace(
            sources = sources,
            declarations = declarations,
            typeHierarchy = freezeTypeHierarchy(declarations.referencedTypeIds()),
        )
    }

    private fun freezeSemanticDeclarations(
        sourceTypeElements: Collection<TypeElement>,
    ): List<LsiDeclaration> {
        val declarationsByTypeId = linkedMapOf<LsiSymbolId, List<LsiDeclaration>>()
        sourceTypeElements
            .sortedBy { typeElement -> typeElement.qualifiedName.toString() }
            .forEach { typeElement ->
                val typeId = LsiSymbolId.type(typeElement.qualifiedName.toString())
                declarationsByTypeId[typeId] = toLsiDeclarations(typeElement)
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
                header.requiresFullExternalDeclaration()
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
            dataClass = false,
            visibility = typeElement.toLsiVisibility(),
            modality = typeElement.toLsiModality(),
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            enumEntries = enumEntries,
            documentation = context.documentation(typeElement),
            annotations = context.toLsiAnnotations(
                annotations = typeElement.annotationMirrors,
                useSiteTarget = LsiAnnotationUseSiteTarget.TYPE,
            ),
            location = context.location(typeElement),
            origin = context.origin(typeElement),
        )
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
        return LsiParameter(
            id = LsiSymbolId.parameter(callableId, index, simpleName.toString()),
            name = simpleName.toString(),
            callableId = callableId,
            index = index,
            type = context.toLsiType(asType(), typeParameterIds),
            vararg = vararg,
            documentation = context.documentation(this),
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
            annotations = context.toLsiAnnotations(
                annotations = annotationMirrors,
                useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
            ),
            location = context.location(this),
            origin = context.origin(this),
        )
    }

    private fun toLsiCallableAnnotations(method: ExecutableElement): List<site.addzero.lsi.model.LsiAnnotation> {
        val methodAnnotations = context.toLsiAnnotations(
            annotations = method.annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.METHOD,
        )
        val returnTypeAnnotations = context.toLsiAnnotations(
            annotations = method.returnType.annotationMirrors,
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
        return methodAnnotations + returnTypeAnnotations
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

private fun LsiTypeDeclaration.requiresFullExternalDeclaration(): Boolean {
    return kind == LsiTypeDeclarationKind.ANNOTATION ||
        annotations.any { annotation -> annotation.type in JIMMER_MANAGED_TYPE_ANNOTATIONS }
}

private val JIMMER_MANAGED_TYPE_ANNOTATIONS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.Immutable"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Entity"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable"),
)

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
