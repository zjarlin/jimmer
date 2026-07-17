package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiTypeRef

data class JimmerImmutableSchema(
    val types: List<JimmerImmutableType>,
)

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
    val viewKind: JimmerViewKind,
    val validations: List<JimmerValidation>,
    val converter: JimmerConverter?,
)

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

enum class JimmerViewKind {
    NONE,
    ID,
    MANY_TO_MANY,
}
