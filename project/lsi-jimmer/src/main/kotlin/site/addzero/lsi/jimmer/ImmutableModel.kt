package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiTypeRef

data class ImmutableSchema(
    val types: List<ImmutableType>,
) {

    val typesById: Map<LsiSymbolId, ImmutableType> = types.associateBy(ImmutableType::id)

    val propsById: Map<LsiSymbolId, ImmutableProp> = types
        .flatMap(ImmutableType::props)
        .associateBy(ImmutableProp::id)

    val ownerPropIdByInversePropId: Map<LsiSymbolId, LsiSymbolId> = types
        .flatMap(ImmutableType::props)
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
        .flatMap(ImmutableType::props)
        .mapNotNull { prop ->
            val view = prop.view as? ImmutableView.Id ?: return@mapNotNull null
            view.basePropId to prop.id
        }
        .groupBy({ (basePropId, _) -> basePropId }, { (_, viewPropId) -> viewPropId })
        .mapValues { (_, viewPropIds) -> viewPropIds.sorted() }

    val viewDependencyPathByPropId: Map<LsiSymbolId, List<LsiSymbolId>> = types
        .flatMap(ImmutableType::props)
        .mapNotNull { prop -> prop.view?.let { view -> prop.id to view.dependencyPropIds } }
        .toMap()

    val formulaDependencyPathsByPropId: Map<LsiSymbolId, List<List<LsiSymbolId>>> = types
        .flatMap(ImmutableType::props)
        .filter { prop -> prop.formulaDependencies.isNotEmpty() }
        .associate { prop ->
            prop.id to prop.formulaDependencies.map(FormulaDependency::propIds)
        }

    val dependentFormulaPropIdsByPropId: Map<LsiSymbolId, List<LsiSymbolId>> = types
        .flatMap(ImmutableType::props)
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
        ownerType: ImmutableType,
        prop: ImmutableProp,
    ) {
        val hasJoinSql = prop.annotations.any { annotation -> annotation.type == JOIN_SQL_ANNOTATION }
        if (hasJoinSql) {
            require(
                prop.primaryMapping == PrimaryMapping.ASSOCIATION &&
                    prop.associationKind == AssociationKind.MANY_TO_MANY &&
                    prop.mappedBy == null &&
                    prop.associationStorage == AssociationStorageKind.NONE
            ) {
                "Immutable JoinSql association metadata is illegal: ${prop.id.value}"
            }
        }
        when (prop.associationStorage) {
            AssociationStorageKind.NONE -> Unit
            AssociationStorageKind.COLUMN -> require(
                prop.primaryMapping == PrimaryMapping.ASSOCIATION &&
                    prop.associationKind in COLUMN_ASSOCIATION_KINDS &&
                    prop.mappedBy == null
            ) {
                "Immutable column association storage is illegal: ${prop.id.value}"
            }
            AssociationStorageKind.MIDDLE_TABLE -> require(
                prop.primaryMapping == PrimaryMapping.ASSOCIATION &&
                    prop.associationKind in MIDDLE_TABLE_ASSOCIATION_KINDS &&
                    prop.mappedBy == null
            ) {
                "Immutable middle-table association storage is illegal: ${prop.id.value}"
            }
        }
        val mappedBy = prop.mappedBy ?: return
        require(prop.primaryMapping == PrimaryMapping.ASSOCIATION && prop.association) {
            "Only persistent immutable association can declare mappedBy: ${prop.id.value}"
        }
        require(prop.associationStorage == AssociationStorageKind.NONE) {
            "Inverse immutable association cannot declare storage: ${prop.id.value}"
        }
        val ownerPropId = mappedBy.ownerPropId
        if (ownerPropId == null) {
            require(ownerType.kind == ImmutableTypeKind.MAPPED_SUPERCLASS && prop.genericTarget) {
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
            associationOwner.primaryMapping == PrimaryMapping.ASSOCIATION &&
                associationOwner.association &&
                associationOwner.mappedBy == null
        ) {
            "Immutable mappedBy must reference a direct persistent association: ${prop.id.value}"
        }
        require(
            associationOwner.associationStorage != AssociationStorageKind.NONE ||
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
        ownerType: ImmutableType,
        formulaProp: ImmutableProp,
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
        ownerType: ImmutableType,
        prop: ImmutableProp,
    ) {
        val view = prop.view
        require((view == null) == (prop.primaryMapping != PrimaryMapping.VIEW)) {
            "Immutable view mapping and typed view metadata must be declared together: ${prop.id.value}"
        }
        when (view) {
            null -> Unit
            is ImmutableView.Id -> {
                require(!prop.association && prop.associationKind == AssociationKind.NONE) {
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
                            baseProp.primaryMapping == PrimaryMapping.ASSOCIATION ||
                                baseProp.view is ImmutableView.ManyToMany
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
                    require(ownerType.kind == ImmutableTypeKind.MAPPED_SUPERCLASS && baseProp.genericTarget) {
                        "Only generic mapped-superclass id-view can omit target id property: ${prop.id.value}"
                    }
                } else {
                    require(targetIdProp.primaryMapping == PrimaryMapping.ID) {
                        "Immutable id-view target property must be an id: ${targetIdProp.id.value}"
                    }
                    require(targetIdProp.ownerTypeId == baseProp.targetTypeId) {
                        "Immutable id-view target id must belong to association target: ${prop.id.value}"
                    }
                }
            }
            is ImmutableView.ManyToMany -> {
                require(
                    prop.list &&
                        prop.association &&
                        prop.associationKind == AssociationKind.MANY_TO_MANY_VIEW
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
                require(baseProp.associationKind == AssociationKind.ONE_TO_MANY) {
                    "Immutable many-to-many view base property must be one-to-many: ${prop.id.value}"
                }
                require(deeperProp.ownerTypeId == baseProp.targetTypeId) {
                    "Immutable many-to-many view deeper property must belong to middle type: ${prop.id.value}"
                }
                require(deeperProp.targetTypeId == prop.targetTypeId) {
                    "Immutable many-to-many view deeper property must target view type: ${prop.id.value}"
                }
                require(deeperProp.associationKind == AssociationKind.MANY_TO_ONE) {
                    "Immutable many-to-many view deeper property must be many-to-one: ${prop.id.value}"
                }
            }
        }
    }
}

data class ImmutableType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val kind: ImmutableTypeKind,
    val documentation: String?,
    val annotations: List<LsiAnnotation>,
    val typeParameterIds: List<LsiSymbolId>,
    val superTypeIds: List<LsiSymbolId>,
    val props: List<ImmutableProp>,
    val primarySuperTypeId: LsiSymbolId?,
    val inheritanceRootTypeId: LsiSymbolId?,
    val inheritanceStrategy: InheritanceStrategy?,
    val joinedTableDissociateAction: JoinedTableDissociateAction?,
    val instantiable: Boolean,
    val discriminatorValue: String?,
    val discriminatorPropId: LsiSymbolId?,
    val idPropId: LsiSymbolId?,
    val versionPropId: LsiSymbolId?,
    val logicalDeletedPropId: LsiSymbolId?,
    val acrossMicroServices: Boolean,
    val microServiceName: String,
) {

    init {
        require(primarySuperTypeId == null || primarySuperTypeId in superTypeIds) {
            "Primary immutable super type must be one of direct super types: ${id.value}"
        }
        require(!instantiable || kind == ImmutableTypeKind.ENTITY) {
            "Only immutable entity type can be instantiable: ${id.value}"
        }
        require(inheritanceRootTypeId == null || kind == ImmutableTypeKind.ENTITY) {
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
            joinedTableDissociateAction != JoinedTableDissociateAction.LAX ||
                inheritanceStrategy == InheritanceStrategy.JOINED
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
        require(
            kind != ImmutableTypeKind.ENTITY ||
                props.filter { prop -> prop.primaryMapping == PrimaryMapping.DISCRIMINATOR }
                    .map(ImmutableProp::id) == listOfNotNull(discriminatorPropId)
        ) {
            "Immutable discriminator property metadata must match its effective property: ${id.value}"
        }
        require(
            props.filter { prop -> prop.primaryMapping == PrimaryMapping.ID }
                .map(ImmutableProp::id) == listOfNotNull(idPropId)
        ) {
            "Immutable id property metadata must match its effective property: ${id.value}"
        }
        require(
            props.filter { prop -> prop.primaryMapping == PrimaryMapping.VERSION }
                .map(ImmutableProp::id) == listOfNotNull(versionPropId)
        ) {
            "Immutable version property metadata must match its effective property: ${id.value}"
        }
        require(
            props.filter { prop -> prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED }
                .map(ImmutableProp::id) == listOfNotNull(logicalDeletedPropId)
        ) {
            "Immutable logical-deleted property metadata must match its effective property: ${id.value}"
        }
        require(
            kind in setOf(ImmutableTypeKind.ENTITY, ImmutableTypeKind.MAPPED_SUPERCLASS) ||
                idPropId == null && versionPropId == null && logicalDeletedPropId == null
        ) {
            "Only immutable entity or mapped superclass can declare identity properties: ${id.value}"
        }
        require(kind != ImmutableTypeKind.ENTITY || idPropId != null) {
            "Immutable entity must have an id property: ${id.value}"
        }
        require(
            kind in setOf(ImmutableTypeKind.ENTITY, ImmutableTypeKind.MAPPED_SUPERCLASS) ||
                props.none { prop -> prop.defaultContract != null }
        ) {
            "Only immutable entity or mapped superclass can declare property defaults: ${id.value}"
        }
        require(!acrossMicroServices || kind == ImmutableTypeKind.MAPPED_SUPERCLASS) {
            "Only immutable mapped superclass can be across microservices: ${id.value}"
        }
        require(!acrossMicroServices || microServiceName.isEmpty()) {
            "Immutable type across microservices cannot declare a micro service name: ${id.value}"
        }
        require(
            microServiceName.isEmpty() ||
                kind == ImmutableTypeKind.ENTITY ||
                kind == ImmutableTypeKind.MAPPED_SUPERCLASS
        ) {
            "Only immutable entity or mapped superclass can declare a micro service name: ${id.value}"
        }
    }
}

data class ImmutableProp(
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
    val primaryMapping: PrimaryMapping,
    val primaryAnnotationTypeId: LsiSymbolId?,
    val defaultContract: ImmutableDefault?,
    val associationKind: AssociationKind,
    val formulaKind: FormulaKind,
    val mappedBy: MappedBy?,
    val associationStorage: AssociationStorageKind,
    val transientResolver: TransientResolver?,
    val view: ImmutableView?,
    val genericTarget: Boolean,
    val remote: Boolean,
    val recursive: Boolean,
    val validations: List<ImmutableValidation>,
    val converter: ImmutableConverter?,
    val formulaDependencies: List<FormulaDependency> = emptyList(),
) {

    val fetchable: Boolean = primaryMapping != PrimaryMapping.ID &&
        (primaryMapping != PrimaryMapping.TRANSIENT || transientResolver != null)

    val reverse: Boolean = mappedBy != null

    init {
        val applicationDefaultAnnotations = annotations.filter { annotation ->
            annotation.type == DEFAULT_ANNOTATION
        }
        val databaseDefaultAnnotations = annotations.filter { annotation ->
            annotation.type == DATABASE_DEFAULT_ANNOTATION
        }
        require(applicationDefaultAnnotations.size <= 1 && databaseDefaultAnnotations.size <= 1) {
            "Immutable property cannot contain duplicate default annotations: ${id.value}"
        }
        require(applicationDefaultAnnotations.isEmpty() || databaseDefaultAnnotations.isEmpty()) {
            "Immutable property cannot contain application and database defaults together: ${id.value}"
        }
        val expectedDefault = when {
            applicationDefaultAnnotations.isNotEmpty() -> {
                val annotationValue = requireNotNull(applicationDefaultAnnotations.single().stringValue("value")) {
                    "Immutable application default must declare a typed string value: ${id.value}"
                }
                ImmutableDefault.Application(
                    annotationValue = annotationValue,
                    strategy = when {
                        annotationValue.isNotEmpty() ||
                            primaryMapping == PrimaryMapping.VERSION -> {
                            ApplicationDefaultStrategy.DECLARED_VALUE
                        }
                        primaryMapping == PrimaryMapping.LOGICAL_DELETED -> {
                            ApplicationDefaultStrategy.LOGICAL_DELETED
                        }
                        else -> null
                    },
                )
            }
            databaseDefaultAnnotations.isNotEmpty() -> ImmutableDefault.Database(
                expression = databaseDefaultAnnotations.single().databaseDefaultExpression(id),
            )
            primaryMapping == PrimaryMapping.VERSION -> ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.VERSION_ZERO,
            )
            primaryMapping == PrimaryMapping.LOGICAL_DELETED -> ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
            )
            else -> null
        }
        require(defaultContract == expectedDefault) {
            "Immutable default metadata must match its effective annotations: ${id.value}"
        }
        require(association == (associationKind != AssociationKind.NONE)) {
            "Immutable association flag and kind must be declared together: ${id.value}"
        }
        when (associationKind) {
            AssociationKind.ONE_TO_ONE,
            AssociationKind.MANY_TO_ONE,
            -> require(!list) {
                "Immutable to-one association cannot be a list: ${id.value}"
            }
            AssociationKind.ONE_TO_MANY,
            AssociationKind.MANY_TO_MANY,
            AssociationKind.MANY_TO_MANY_VIEW,
            -> require(list) {
                "Immutable to-many association must be a list: ${id.value}"
            }
            AssociationKind.NONE,
            AssociationKind.IMPLICIT,
            -> Unit
        }
        require(!embedded || !association) {
            "Immutable property cannot be both embedded and association: ${id.value}"
        }
        when (val default = defaultContract) {
            null -> Unit
            is ImmutableDefault.Application -> {
                require(
                    primaryMapping in setOf(
                        PrimaryMapping.VERSION,
                        PrimaryMapping.LOGICAL_DELETED,
                        PrimaryMapping.SCALAR,
                    ) && !association && !embedded
                ) {
                    "Application default must belong to a scalar, version or logical-deleted property: ${id.value}"
                }
                when (default.strategy) {
                    null -> require(
                        primaryMapping == PrimaryMapping.SCALAR &&
                            default.annotationValue == ""
                    ) {
                        "Empty application default marker must belong to a scalar property: ${id.value}"
                    }
                    ApplicationDefaultStrategy.DECLARED_VALUE -> require(
                        default.annotationValue != null &&
                            (default.annotationValue.isNotEmpty() ||
                                primaryMapping == PrimaryMapping.VERSION)
                    ) {
                        "Declared application default must preserve its annotation value: ${id.value}"
                    }
                    ApplicationDefaultStrategy.VERSION_ZERO -> require(
                        primaryMapping == PrimaryMapping.VERSION && default.annotationValue == null
                    ) {
                        "Implicit version default must belong to a version property: ${id.value}"
                    }
                    ApplicationDefaultStrategy.LOGICAL_DELETED -> require(
                        primaryMapping == PrimaryMapping.LOGICAL_DELETED &&
                            default.annotationValue?.isNotEmpty() != true
                    ) {
                        "Logical-deleted default must belong to a logical-deleted property: ${id.value}"
                    }
                }
            }
            is ImmutableDefault.Database -> require(
                primaryMapping == PrimaryMapping.SCALAR &&
                    !association &&
                    !embedded &&
                    annotations.none { annotation ->
                        annotation.type == KEY_ANNOTATION || annotation.type == KEYS_ANNOTATION
                    }
            ) {
                "Database default must belong to a scalar column property: ${id.value}"
            }
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
        require(!recursive || view !is ImmutableView.ManyToMany) {
            "Many-to-many view property cannot be recursive: ${id.value}"
        }
        require(formulaKind != FormulaKind.NONE || formulaDependencies.isEmpty()) {
            "Only immutable formula property can declare formula dependencies: ${id.value}"
        }
        require(formulaDependencies.distinct() == formulaDependencies) {
            "Immutable formula property cannot contain duplicate dependency paths: ${id.value}"
        }
        require(transientResolver == null || primaryMapping == PrimaryMapping.TRANSIENT) {
            "Only immutable transient property can declare a transient resolver: ${id.value}"
        }
        require(mappedBy == null || association) {
            "Only immutable association can declare mappedBy: ${id.value}"
        }
        require(mappedBy == null || associationStorage == AssociationStorageKind.NONE) {
            "Inverse immutable association cannot declare storage: ${id.value}"
        }
        require(associationStorage == AssociationStorageKind.NONE || association) {
            "Only immutable association can declare association storage: ${id.value}"
        }
    }
}

data class MappedBy(
    val name: String,
    val ownerPropId: LsiSymbolId?,
) {
    init {
        require(name.isNotEmpty()) { "Immutable mappedBy property name cannot be empty" }
    }
}

data class FormulaDependency(
    val propIds: List<LsiSymbolId>,
) {
    init {
        require(propIds.isNotEmpty()) { "Immutable formula dependency path cannot be empty" }
    }
}

sealed interface TransientResolver {

    data class Type(
        val typeId: LsiSymbolId,
    ) : TransientResolver

    data class Reference(
        val beanName: String,
    ) : TransientResolver {
        init {
            require(beanName.isNotEmpty()) { "Immutable transient resolver bean name cannot be empty" }
        }
    }
}

data class ImmutableValidation(
    val annotationTypeId: LsiSymbolId,
    val validatorTypeIds: List<LsiSymbolId>,
    val message: String,
    val sourceAnnotationUseSiteTarget: LsiAnnotationUseSiteTarget?,
)

data class ImmutableConverter(
    val converterTypeId: LsiSymbolId,
    val sourceType: LsiTypeRef?,
    val targetType: LsiTypeRef?,
    val sourceNullable: Boolean,
    val targetNullable: Boolean,
    val propertyNullable: Boolean,
)

sealed interface ImmutableDefault {

    data class Application(
        val annotationValue: String?,
        val strategy: ApplicationDefaultStrategy?,
    ) : ImmutableDefault {
        init {
            require(annotationValue != null || strategy != null) {
                "Immutable application default must declare an annotation value or insert strategy"
            }
        }
    }

    data class Database(
        val expression: String?,
    ) : ImmutableDefault
}

enum class ApplicationDefaultStrategy {
    DECLARED_VALUE,
    VERSION_ZERO,
    LOGICAL_DELETED,
}

enum class ImmutableTypeKind {
    IMMUTABLE,
    ENTITY,
    MAPPED_SUPERCLASS,
    EMBEDDABLE,
}

enum class InheritanceStrategy {
    SINGLE_TABLE,
    JOINED,
}

enum class JoinedTableDissociateAction {
    DELETE,
    LAX,
}

enum class PrimaryMapping {
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

enum class AssociationKind {
    NONE,
    IMPLICIT,
    ONE_TO_ONE,
    MANY_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_MANY,
    MANY_TO_MANY_VIEW,
}

enum class AssociationStorageKind {
    NONE,
    COLUMN,
    MIDDLE_TABLE,
}

internal fun AssociationKind.isInverseOf(ownerKind: AssociationKind): Boolean {
    return when (ownerKind) {
        AssociationKind.ONE_TO_ONE -> this == AssociationKind.ONE_TO_ONE
        AssociationKind.MANY_TO_ONE -> this == AssociationKind.ONE_TO_MANY
        AssociationKind.MANY_TO_MANY -> this == AssociationKind.MANY_TO_MANY
        AssociationKind.NONE,
        AssociationKind.IMPLICIT,
        AssociationKind.ONE_TO_MANY,
        AssociationKind.MANY_TO_MANY_VIEW,
        -> false
    }
}

internal val COLUMN_ASSOCIATION_KINDS = setOf(
    AssociationKind.ONE_TO_ONE,
    AssociationKind.MANY_TO_ONE,
)

internal val MIDDLE_TABLE_ASSOCIATION_KINDS = COLUMN_ASSOCIATION_KINDS + AssociationKind.MANY_TO_MANY

internal val LIST_ASSOCIATION_KINDS = setOf(
    AssociationKind.ONE_TO_MANY,
    AssociationKind.MANY_TO_MANY,
    AssociationKind.MANY_TO_MANY_VIEW,
)

private val JOIN_SQL_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")

private val DEFAULT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")

private val DATABASE_DEFAULT_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.DatabaseDefault")

private val KEY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")

private val KEYS_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Keys")

private fun LsiAnnotation.stringValue(name: String): String? {
    return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value
}

private fun LsiAnnotation.databaseDefaultExpression(propId: LsiSymbolId): String? {
    val value = arguments["value"]?.value ?: return null
    require(value is LsiAnnotationValue.StringValue) {
        "Immutable database default must declare a typed string value: ${propId.value}"
    }
    return value.value.takeIf(String::isNotBlank)
}

enum class FormulaKind {
    NONE,
    SQL,
    LANGUAGE,
    ABSTRACT,
}

sealed interface ImmutableView {

    val dependencyPropIds: List<LsiSymbolId>

    data class Id(
        val basePropId: LsiSymbolId,
        val targetIdPropId: LsiSymbolId?,
    ) : ImmutableView {
        override val dependencyPropIds: List<LsiSymbolId> =
            listOfNotNull(basePropId, targetIdPropId)
    }

    data class ManyToMany(
        val basePropId: LsiSymbolId,
        val deeperPropId: LsiSymbolId,
    ) : ImmutableView {
        override val dependencyPropIds: List<LsiSymbolId> = listOf(basePropId, deeperPropId)
    }
}
