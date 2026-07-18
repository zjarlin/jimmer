package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiTypeRef

data class JimmerImmutableSchema(
    val types: List<JimmerImmutableType>,
) {

    val typesById: Map<LsiSymbolId, JimmerImmutableType> = types.associateBy(JimmerImmutableType::id)

    val propsById: Map<LsiSymbolId, JimmerImmutableProp> = types
        .flatMap(JimmerImmutableType::props)
        .associateBy(JimmerImmutableProp::id)

    val idViewPropIdsByBasePropId: Map<LsiSymbolId, List<LsiSymbolId>> = types
        .flatMap(JimmerImmutableType::props)
        .mapNotNull { prop ->
            val view = prop.view as? JimmerImmutableView.Id ?: return@mapNotNull null
            view.basePropId to prop.id
        }
        .groupBy({ (basePropId, _) -> basePropId }, { (_, viewPropId) -> viewPropId })
        .mapValues { (_, viewPropIds) -> viewPropIds.sorted() }

    val viewDependencyPathByPropId: Map<LsiSymbolId, List<LsiSymbolId>> = types
        .flatMap(JimmerImmutableType::props)
        .mapNotNull { prop -> prop.view?.let { view -> prop.id to view.dependencyPropIds } }
        .toMap()

    init {
        require(typesById.size == types.size) { "Immutable schema cannot contain duplicate type ids" }
        require(propsById.size == types.sumOf { type -> type.props.size }) {
            "Immutable schema cannot contain duplicate property ids"
        }
        types.forEach { type ->
            require(type.props.all { prop -> prop.ownerTypeId == type.id }) {
                "Immutable schema property owner must match containing type: ${type.id.value}"
            }
            type.props.forEach { prop -> validateView(type, prop) }
        }
    }

    private fun validateView(
        ownerType: JimmerImmutableType,
        prop: JimmerImmutableProp,
    ) {
        val view = prop.view
        require((view == null) == (prop.primaryMapping != JimmerImmutablePrimaryMapping.VIEW)) {
            "Immutable view mapping and typed view metadata must be declared together: ${prop.id.value}"
        }
        when (view) {
            null -> Unit
            is JimmerImmutableView.Id -> {
                require(!prop.association && prop.associationKind == JimmerAssociationKind.NONE) {
                    "Immutable id-view property must be scalar or scalar-list metadata: ${prop.id.value}"
                }
                val baseProp = requireNotNull(propsById[view.basePropId]) {
                    "Immutable id-view base property does not exist: ${view.basePropId.value}"
                }
                require(baseProp.ownerTypeId == ownerType.id) {
                    "Immutable id-view base property must belong to the same owner: ${prop.id.value}"
                }
                require(
                    baseProp.association &&
                        (
                            baseProp.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION ||
                                baseProp.view is JimmerImmutableView.ManyToMany
                            )
                ) {
                    "Immutable id-view base property must be a persistent association or many-to-many view: " +
                        prop.id.value
                }
                require(prop.list == baseProp.list && prop.nullable == baseProp.nullable) {
                    "Immutable id-view list and nullability must match its base property: ${prop.id.value}"
                }
                val targetIdProp = view.targetIdPropId?.let { targetIdPropId ->
                    requireNotNull(propsById[targetIdPropId]) {
                        "Immutable id-view target id property does not exist: ${targetIdPropId.value}"
                    }
                }
                if (targetIdProp == null) {
                    require(ownerType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS && baseProp.genericTarget) {
                        "Only generic mapped-superclass id-view can omit target id property: ${prop.id.value}"
                    }
                } else {
                    require(targetIdProp.primaryMapping == JimmerImmutablePrimaryMapping.ID) {
                        "Immutable id-view target property must be an id: ${targetIdProp.id.value}"
                    }
                    require(targetIdProp.ownerTypeId == baseProp.targetTypeId) {
                        "Immutable id-view target id must belong to association target: ${prop.id.value}"
                    }
                }
            }
            is JimmerImmutableView.ManyToMany -> {
                require(
                    prop.list &&
                        prop.association &&
                        prop.associationKind == JimmerAssociationKind.MANY_TO_MANY_VIEW
                ) {
                    "Immutable many-to-many view must be a list association: ${prop.id.value}"
                }
                val baseProp = requireNotNull(propsById[view.basePropId]) {
                    "Immutable many-to-many view base property does not exist: ${view.basePropId.value}"
                }
                val deeperProp = requireNotNull(propsById[view.deeperPropId]) {
                    "Immutable many-to-many view deeper property does not exist: ${view.deeperPropId.value}"
                }
                require(baseProp.ownerTypeId == ownerType.id) {
                    "Immutable many-to-many view base property must belong to the same owner: ${prop.id.value}"
                }
                require(baseProp.associationKind == JimmerAssociationKind.ONE_TO_MANY) {
                    "Immutable many-to-many view base property must be one-to-many: ${prop.id.value}"
                }
                require(deeperProp.ownerTypeId == baseProp.targetTypeId) {
                    "Immutable many-to-many view deeper property must belong to middle type: ${prop.id.value}"
                }
                require(deeperProp.targetTypeId == prop.targetTypeId) {
                    "Immutable many-to-many view deeper property must target view type: ${prop.id.value}"
                }
                require(deeperProp.associationKind == JimmerAssociationKind.MANY_TO_ONE) {
                    "Immutable many-to-many view deeper property must be many-to-one: ${prop.id.value}"
                }
            }
        }
    }
}

data class JimmerImmutableType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val kind: JimmerImmutableTypeKind,
    val documentation: String?,
    val annotations: List<LsiAnnotation>,
    val typeParameterIds: List<LsiSymbolId>,
    val superTypeIds: List<LsiSymbolId>,
    val props: List<JimmerImmutableProp>,
    val primarySuperTypeId: LsiSymbolId?,
    val inheritanceRootTypeId: LsiSymbolId?,
    val inheritanceStrategy: JimmerInheritanceStrategy?,
    val joinedTableDissociateAction: JimmerJoinedTableDissociateAction?,
    val instantiable: Boolean,
    val discriminatorValue: String?,
    val discriminatorPropId: LsiSymbolId?,
    val acrossMicroServices: Boolean,
    val microServiceName: String,
) {

    init {
        require(primarySuperTypeId == null || primarySuperTypeId in superTypeIds) {
            "Primary immutable super type must be one of direct super types: ${id.value}"
        }
        require(!instantiable || kind == JimmerImmutableTypeKind.ENTITY) {
            "Only immutable entity type can be instantiable: ${id.value}"
        }
        require(inheritanceRootTypeId == null || kind == JimmerImmutableTypeKind.ENTITY) {
            "Only immutable entity type can have an inheritance root: ${id.value}"
        }
        require(inheritanceStrategy == null || inheritanceRootTypeId == id) {
            "Only immutable inheritance root can declare an inheritance strategy: ${id.value}"
        }
        require(inheritanceRootTypeId != id || inheritanceStrategy != null) {
            "Immutable inheritance root must declare an inheritance strategy: ${id.value}"
        }
        require(joinedTableDissociateAction == null || inheritanceStrategy != null) {
            "Only immutable inheritance root can declare a joined table dissociate action: ${id.value}"
        }
        require(inheritanceStrategy == null || joinedTableDissociateAction != null) {
            "Immutable inheritance root must declare a joined table dissociate action: ${id.value}"
        }
        require(
            joinedTableDissociateAction != JimmerJoinedTableDissociateAction.LAX ||
                inheritanceStrategy == JimmerInheritanceStrategy.JOINED
        ) {
            "LAX joined table dissociate action requires JOINED inheritance: ${id.value}"
        }
        require(discriminatorValue == null || inheritanceRootTypeId != null && instantiable) {
            "Only instantiable inheritance entity can have a discriminator value: ${id.value}"
        }
        require((discriminatorPropId == null) == (inheritanceRootTypeId == null)) {
            "Immutable inheritance entity must have exactly one discriminator property: ${id.value}"
        }
        require(discriminatorPropId == null || props.any { prop -> prop.id == discriminatorPropId }) {
            "Immutable discriminator property must belong to its type: ${id.value}"
        }
        require(!acrossMicroServices || kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS) {
            "Only immutable mapped superclass can be across microservices: ${id.value}"
        }
        require(!acrossMicroServices || microServiceName.isEmpty()) {
            "Immutable type across microservices cannot declare a micro service name: ${id.value}"
        }
        require(
            microServiceName.isEmpty() ||
                kind == JimmerImmutableTypeKind.ENTITY ||
                kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS
        ) {
            "Only immutable entity or mapped superclass can declare a micro service name: ${id.value}"
        }
    }
}

data class JimmerImmutableProp(
    val id: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val ownerTypeId: LsiSymbolId,
    val declaringTypeId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val annotations: List<LsiAnnotation>,
    val overrideChain: List<LsiSymbolId>,
    val inherited: Boolean,
    val overridden: Boolean,
    val nullable: Boolean,
    val list: Boolean,
    val association: Boolean,
    val embedded: Boolean,
    val targetTypeId: LsiSymbolId?,
    val primaryMapping: JimmerImmutablePrimaryMapping,
    val primaryAnnotationTypeId: LsiSymbolId?,
    val associationKind: JimmerAssociationKind,
    val formulaKind: JimmerFormulaKind,
    val view: JimmerImmutableView?,
    val genericTarget: Boolean,
    val remote: Boolean,
    val recursive: Boolean,
    val validations: List<JimmerValidation>,
    val converter: JimmerConverter?,
) {

    init {
        require(!remote || association && targetTypeId != null) {
            "Only immutable association with a concrete target can be remote: ${id.value}"
        }
        require(!genericTarget || targetTypeId == null) {
            "Immutable property with a generic target cannot have a concrete target type: ${id.value}"
        }
        require(!recursive || association && targetTypeId != null && !remote) {
            "Only local immutable association with a concrete target can be recursive: ${id.value}"
        }
        require(!recursive || !genericTarget) {
            "Immutable property with a generic target cannot be recursive: ${id.value}"
        }
        require(!recursive || view !is JimmerImmutableView.ManyToMany) {
            "Many-to-many view property cannot be recursive: ${id.value}"
        }
    }
}

data class JimmerValidation(
    val annotationTypeId: LsiSymbolId,
    val validatorTypeIds: List<LsiSymbolId>,
    val message: String,
)

data class JimmerConverter(
    val converterTypeId: LsiSymbolId,
    val sourceType: LsiTypeRef?,
    val targetType: LsiTypeRef?,
    val sourceNullable: Boolean,
    val targetNullable: Boolean,
    val propertyNullable: Boolean,
)

enum class JimmerImmutableTypeKind {
    IMMUTABLE,
    ENTITY,
    MAPPED_SUPERCLASS,
    EMBEDDABLE,
}

enum class JimmerInheritanceStrategy {
    SINGLE_TABLE,
    JOINED,
}

enum class JimmerJoinedTableDissociateAction {
    DELETE,
    LAX,
}

enum class JimmerImmutablePrimaryMapping {
    ID,
    VERSION,
    LOGICAL_DELETED,
    DISCRIMINATOR,
    ASSOCIATION,
    FORMULA,
    TRANSIENT,
    VIEW,
    SCALAR,
}

enum class JimmerAssociationKind {
    NONE,
    IMPLICIT,
    ONE_TO_ONE,
    MANY_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_MANY,
    MANY_TO_MANY_VIEW,
}

enum class JimmerFormulaKind {
    NONE,
    SQL,
    LANGUAGE,
    ABSTRACT,
}

sealed interface JimmerImmutableView {

    val dependencyPropIds: List<LsiSymbolId>

    data class Id(
        val basePropId: LsiSymbolId,
        val targetIdPropId: LsiSymbolId?,
    ) : JimmerImmutableView {
        override val dependencyPropIds: List<LsiSymbolId> =
            listOfNotNull(basePropId, targetIdPropId)
    }

    data class ManyToMany(
        val basePropId: LsiSymbolId,
        val deeperPropId: LsiSymbolId,
    ) : JimmerImmutableView {
        override val dependencyPropIds: List<LsiSymbolId> = listOf(basePropId, deeperPropId)
    }
}
