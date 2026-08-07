package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

fun completeEntityProps(
    ownerTypeId: LsiSymbolId,
    props: List<ImmutableProp> = emptyList(),
): List<ImmutableProp> {
    if (props.any { prop -> prop.primaryMapping == PrimaryMapping.ID }) {
        return props
    }
    require(props.none { prop -> prop.name == "id" }) {
        "Entity test fixture cannot synthesize id over an existing property: ${ownerTypeId.value}"
    }
    val id = LsiSymbolId.property(ownerTypeId, "id")
    val idProp = ImmutableProp(
        id = id,
        declarationId = id,
        ownerTypeId = ownerTypeId,
        declaringTypeId = ownerTypeId,
        name = "id",
        documentation = null,
        type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
        annotations = listOf(LsiAnnotation(ID_ANNOTATION_TYPE)),
        overrideChain = listOf(id),
        inherited = false,
        overridden = false,
        nullable = false,
        list = false,
        association = false,
        embedded = false,
        targetTypeId = null,
        primaryMapping = PrimaryMapping.ID,
        primaryAnnotationTypeId = ID_ANNOTATION_TYPE,
        defaultContract = null,
        associationKind = AssociationKind.NONE,
        formulaKind = FormulaKind.NONE,
        mappedBy = null,
        associationStorage = AssociationStorageKind.NONE,
        transientResolver = null,
        view = null,
        genericTarget = false,
        remote = false,
        recursive = false,
        validations = emptyList(),
        converter = null,
    )
    return listOf(idProp) + props
}

fun LsiWorkspace.completeEntityIdentities(): LsiWorkspace {
    val types = declarationsOfType<LsiTypeDeclaration>()
    val typesById = types.associateBy(LsiTypeDeclaration::id)
    val propertiesById = declarationsOfType<LsiProperty>().associateBy(LsiProperty::id)
    val identityCache = mutableMapOf<LsiSymbolId, Boolean>()
    val visiting = mutableSetOf<LsiSymbolId>()

    fun hasIdentity(typeId: LsiSymbolId): Boolean {
        identityCache[typeId]?.let { cached -> return cached }
        if (!visiting.add(typeId)) {
            return false
        }
        val type = typesById[typeId]
        val declaredIdentity = type?.memberIds.orEmpty().any { memberId ->
            propertiesById[memberId]?.annotations?.any { annotation ->
                annotation.type == ID_ANNOTATION_TYPE
            } == true
        }
        val inheritedIdentity = type?.superTypes
            .orEmpty()
            .filterIsInstance<LsiDeclaredType>()
            .any { superType -> hasIdentity(superType.declarationId) }
        visiting.remove(typeId)
        return (declaredIdentity || inheritedIdentity).also { result -> identityCache[typeId] = result }
    }

    val syntheticPropsByTypeId = types.mapNotNull { type ->
        val entity = type.annotations.any { annotation -> annotation.type == ENTITY_ANNOTATION_TYPE }
        if (!entity || hasIdentity(type.id)) {
            return@mapNotNull null
        }
        val hasEntitySuper = type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .any { superType ->
                typesById[superType.declarationId]?.annotations?.any { annotation ->
                    annotation.type == ENTITY_ANNOTATION_TYPE
                } == true
            }
        if (hasEntitySuper) {
            return@mapNotNull null
        }
        val propName = generateSequence("__fixtureId") { name -> "_${name}" }
            .first { name -> LsiSymbolId.property(type.id, name) !in propertiesById }
        val propId = LsiSymbolId.property(type.id, propName)
        val prop = LsiProperty(
            id = propId,
            name = propName,
            ownerId = type.id,
            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            annotations = listOf(LsiAnnotation(ID_ANNOTATION_TYPE)),
            origin = type.origin,
        )
        type.id to prop
    }.toMap()
    if (syntheticPropsByTypeId.isEmpty()) {
        return this
    }
    val completedDeclarations = declarations.map { declaration ->
        val type = declaration as? LsiTypeDeclaration ?: return@map declaration
        val syntheticProp = syntheticPropsByTypeId[type.id] ?: return@map declaration
        type.copy(memberIds = type.memberIds + syntheticProp.id)
    } + syntheticPropsByTypeId.values
    return LsiWorkspace(
        sources = sources,
        declarations = completedDeclarations,
        typeHierarchy = typeHierarchy,
        annotationScopes = annotationScopes,
    )
}

private val ID_ANNOTATION_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")

private val ENTITY_ANNOTATION_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
