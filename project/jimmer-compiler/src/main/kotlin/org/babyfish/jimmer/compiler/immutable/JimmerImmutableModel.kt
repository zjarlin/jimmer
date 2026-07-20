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

    val ownerPropIdByInversePropId: Map<LsiSymbolId, LsiSymbolId> = types
        .flatMap(JimmerImmutableType::props)
        .mapNotNull { prop ->
            prop.mappedBy?.ownerPropId?.let { ownerPropId -> prop.id to ownerPropId }
        }
        .toMap()

    val inversePropIdsByOwnerPropId: Map<LsiSymbolId, List<LsiSymbolId>> =
        ownerPropIdByInversePropId.entries
            .groupBy(
                keySelector = Map.Entry<LsiSymbolId, LsiSymbolId>::value,
                valueTransform = Map.Entry<LsiSymbolId, LsiSymbolId>::key,
            )
            .mapValues { (_, inversePropIds) -> inversePropIds.sorted() }

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

    val formulaDependencyPathsByPropId: Map<LsiSymbolId, List<List<LsiSymbolId>>> = types
        .flatMap(JimmerImmutableType::props)
        .filter { prop -> prop.formulaDependencies.isNotEmpty() }
        .associate { prop ->
            prop.id to prop.formulaDependencies.map(JimmerFormulaDependency::propIds)
        }

    val dependentFormulaPropIdsByPropId: Map<LsiSymbolId, List<LsiSymbolId>> = types
        .flatMap(JimmerImmutableType::props)
        .flatMap { formulaProp ->
            formulaProp.formulaDependencies.flatMap { dependency ->
                dependency.propIds.map { dependencyPropId -> dependencyPropId to formulaProp.id }
            }
        }
        .groupBy(
            keySelector = { (dependencyPropId, _) -> dependencyPropId },
            valueTransform = { (_, formulaPropId) -> formulaPropId },
        )
        .mapValues { (_, formulaPropIds) -> formulaPropIds.distinct().sorted() }

    init {
        require(typesById.size == types.size) { "Immutable schema cannot contain duplicate type ids" }
        require(propsById.size == types.sumOf { type -> type.props.size }) {
            "Immutable schema cannot contain duplicate property ids"
        }
        types.forEach { type ->
            require(type.props.all { prop -> prop.ownerTypeId == type.id }) {
                "Immutable schema property owner must match containing type: ${type.id.value}"
            }
            type.props.forEach { prop ->
                validateAssociationMetadata(type, prop)
                validateView(type, prop)
                validateFormulaDependencies(type, prop)
            }
        }
        inversePropIdsByOwnerPropId.forEach { (ownerPropId, inversePropIds) ->
            val originalInversePropIds = inversePropIds.map { inversePropId ->
                val inverseProp = propsById.getValue(inversePropId)
                inverseProp.overrideChain.lastOrNull() ?: inverseProp.declarationId
            }.distinct()
            require(originalInversePropIds.size == 1) {
                "Immutable association owner cannot be referenced by unrelated inverse properties: " +
                    ownerPropId.value
            }
        }
    }

    private fun validateAssociationMetadata(
        ownerType: JimmerImmutableType,
        prop: JimmerImmutableProp,
    ) {
        val hasJoinSql = prop.annotations.any { annotation -> annotation.type == JOIN_SQL_ANNOTATION }
        if (hasJoinSql) {
            require(
                prop.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION &&
                    prop.associationKind == JimmerAssociationKind.MANY_TO_MANY &&
                    prop.mappedBy == null &&
                    prop.associationStorage == JimmerAssociationStorageKind.NONE
            ) {
                "Immutable JoinSql association metadata is illegal: ${prop.id.value}"
            }
        }
        when (prop.associationStorage) {
            JimmerAssociationStorageKind.NONE -> Unit
            JimmerAssociationStorageKind.COLUMN -> require(
                prop.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION &&
                    prop.associationKind in COLUMN_ASSOCIATION_KINDS &&
                    prop.mappedBy == null
            ) {
                "Immutable column association storage is illegal: ${prop.id.value}"
            }
            JimmerAssociationStorageKind.MIDDLE_TABLE -> require(
                prop.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION &&
                    prop.associationKind in MIDDLE_TABLE_ASSOCIATION_KINDS &&
                    prop.mappedBy == null
            ) {
                "Immutable middle-table association storage is illegal: ${prop.id.value}"
            }
        }
        val mappedBy = prop.mappedBy ?: return
        require(prop.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION && prop.association) {
            "Only persistent immutable association can declare mappedBy: ${prop.id.value}"
        }
        require(prop.associationStorage == JimmerAssociationStorageKind.NONE) {
            "Inverse immutable association cannot declare storage: ${prop.id.value}"
        }
        val ownerPropId = mappedBy.ownerPropId
        if (ownerPropId == null) {
            require(ownerType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS && prop.genericTarget) {
                "Only generic mapped-superclass association can have unresolved mappedBy: ${prop.id.value}"
            }
            return
        }
        val associationOwner = requireNotNull(propsById[ownerPropId]) {
            "Immutable mappedBy owner property does not exist: ${ownerPropId.value}"
        }
        require(associationOwner.ownerTypeId == prop.targetTypeId) {
            "Immutable mappedBy owner property belongs to an unexpected type: ${prop.id.value}"
        }
        require(
            associationOwner.primaryMapping == JimmerImmutablePrimaryMapping.ASSOCIATION &&
                associationOwner.association &&
                associationOwner.mappedBy == null
        ) {
            "Immutable mappedBy must reference a direct persistent association: ${prop.id.value}"
        }
        require(
            associationOwner.associationStorage != JimmerAssociationStorageKind.NONE ||
                associationOwner.annotations.any { annotation -> annotation.type == JOIN_SQL_ANNOTATION }
        ) {
            "Immutable mappedBy must reference a stored or JoinSql association: ${prop.id.value}"
        }
        require(mappedBy.name == associationOwner.name) {
            "Immutable mappedBy owner name does not match its resolved property: ${prop.id.value}"
        }
        require(prop.associationKind.isInverseOf(associationOwner.associationKind)) {
            "Immutable mappedBy association cardinality does not match its owner: ${prop.id.value}"
        }
        val associationOwnerTargetTypeId = requireNotNull(associationOwner.targetTypeId) {
            "Immutable mappedBy owner association must have a concrete target: ${prop.id.value}"
        }
        require(
            associationOwnerTargetTypeId.isSameAsOrSubtypeOf(ownerType.id) ||
                ownerType.id.isSameAsOrSubtypeOf(associationOwnerTargetTypeId)
        ) {
            "Immutable mappedBy owner association targets an incompatible type: ${prop.id.value}"
        }
    }

    private fun LsiSymbolId.isSameAsOrSubtypeOf(superTypeId: LsiSymbolId): Boolean {
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

    private fun validateFormulaDependencies(
        ownerType: JimmerImmutableType,
        formulaProp: JimmerImmutableProp,
    ) {
        formulaProp.formulaDependencies.forEach { dependency ->
            var expectedOwnerTypeId = ownerType.id
            dependency.propIds.forEachIndexed { index, propId ->
                val prop = requireNotNull(propsById[propId]) {
                    "Immutable formula dependency property does not exist: ${propId.value}"
                }
                require(prop.ownerTypeId == expectedOwnerTypeId) {
                    "Immutable formula dependency property belongs to an unexpected owner: ${propId.value}"
                }
                if (index + 1 < dependency.propIds.size) {
                    require(prop.association || prop.embedded) {
                        "Intermediate immutable formula dependency must be an association or embedded property: " +
                            prop.id.value
                    }
                    expectedOwnerTypeId = requireNotNull(prop.targetTypeId) {
                        "Intermediate immutable formula dependency must have a concrete target: ${prop.id.value}"
                    }
                }
            }
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
    val documentation: String?,
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
    val mappedBy: JimmerMappedBy?,
    val associationStorage: JimmerAssociationStorageKind,
    val transientResolver: JimmerTransientResolver?,
    val view: JimmerImmutableView?,
    val genericTarget: Boolean,
    val remote: Boolean,
    val recursive: Boolean,
    val validations: List<JimmerValidation>,
    val converter: JimmerConverter?,
    val formulaDependencies: List<JimmerFormulaDependency> = emptyList(),
) {

    val fetchable: Boolean = primaryMapping != JimmerImmutablePrimaryMapping.ID &&
        (primaryMapping != JimmerImmutablePrimaryMapping.TRANSIENT || transientResolver != null)

    val reverse: Boolean = mappedBy != null

    init {
        require(association == (associationKind != JimmerAssociationKind.NONE)) {
            "Immutable association flag and kind must be declared together: ${id.value}"
        }
        when (associationKind) {
            JimmerAssociationKind.ONE_TO_ONE,
            JimmerAssociationKind.MANY_TO_ONE,
            -> require(!list) {
                "Immutable to-one association cannot be a list: ${id.value}"
            }
            JimmerAssociationKind.ONE_TO_MANY,
            JimmerAssociationKind.MANY_TO_MANY,
            JimmerAssociationKind.MANY_TO_MANY_VIEW,
            -> require(list) {
                "Immutable to-many association must be a list: ${id.value}"
            }
            JimmerAssociationKind.NONE,
            JimmerAssociationKind.IMPLICIT,
            -> Unit
        }
        require(!embedded || !association) {
            "Immutable property cannot be both embedded and association: ${id.value}"
        }
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
        require(formulaKind != JimmerFormulaKind.NONE || formulaDependencies.isEmpty()) {
            "Only immutable formula property can declare formula dependencies: ${id.value}"
        }
        require(formulaDependencies.distinct() == formulaDependencies) {
            "Immutable formula property cannot contain duplicate dependency paths: ${id.value}"
        }
        require(transientResolver == null || primaryMapping == JimmerImmutablePrimaryMapping.TRANSIENT) {
            "Only immutable transient property can declare a transient resolver: ${id.value}"
        }
        require(mappedBy == null || association) {
            "Only immutable association can declare mappedBy: ${id.value}"
        }
        require(mappedBy == null || associationStorage == JimmerAssociationStorageKind.NONE) {
            "Inverse immutable association cannot declare storage: ${id.value}"
        }
        require(associationStorage == JimmerAssociationStorageKind.NONE || association) {
            "Only immutable association can declare association storage: ${id.value}"
        }
    }
}

data class JimmerMappedBy(
    val name: String,
    val ownerPropId: LsiSymbolId?,
) {
    init {
        require(name.isNotEmpty()) { "Immutable mappedBy property name cannot be empty" }
    }
}

data class JimmerFormulaDependency(
    val propIds: List<LsiSymbolId>,
) {
    init {
        require(propIds.isNotEmpty()) { "Immutable formula dependency path cannot be empty" }
    }
}

sealed interface JimmerTransientResolver {

    data class Type(
        val typeId: LsiSymbolId,
    ) : JimmerTransientResolver

    data class Reference(
        val beanName: String,
    ) : JimmerTransientResolver {
        init {
            require(beanName.isNotEmpty()) { "Immutable transient resolver bean name cannot be empty" }
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

enum class JimmerAssociationStorageKind {
    NONE,
    COLUMN,
    MIDDLE_TABLE,
}

internal fun JimmerAssociationKind.isInverseOf(ownerKind: JimmerAssociationKind): Boolean {
    return when (ownerKind) {
        JimmerAssociationKind.ONE_TO_ONE -> this == JimmerAssociationKind.ONE_TO_ONE
        JimmerAssociationKind.MANY_TO_ONE -> this == JimmerAssociationKind.ONE_TO_MANY
        JimmerAssociationKind.MANY_TO_MANY -> this == JimmerAssociationKind.MANY_TO_MANY
        JimmerAssociationKind.NONE,
        JimmerAssociationKind.IMPLICIT,
        JimmerAssociationKind.ONE_TO_MANY,
        JimmerAssociationKind.MANY_TO_MANY_VIEW,
        -> false
    }
}

internal val COLUMN_ASSOCIATION_KINDS = setOf(
    JimmerAssociationKind.ONE_TO_ONE,
    JimmerAssociationKind.MANY_TO_ONE,
)

internal val MIDDLE_TABLE_ASSOCIATION_KINDS = COLUMN_ASSOCIATION_KINDS + JimmerAssociationKind.MANY_TO_MANY

internal val LIST_ASSOCIATION_KINDS = setOf(
    JimmerAssociationKind.ONE_TO_MANY,
    JimmerAssociationKind.MANY_TO_MANY,
    JimmerAssociationKind.MANY_TO_MANY_VIEW,
)

private val JOIN_SQL_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")

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
