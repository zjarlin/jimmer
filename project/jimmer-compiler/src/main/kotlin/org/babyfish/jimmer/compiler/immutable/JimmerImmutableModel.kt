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
    val typeParameterIds: List<LsiSymbolId>,
    val superTypeIds: List<LsiSymbolId>,
    val props: List<JimmerImmutableProp>,
    val primarySuperTypeId: LsiSymbolId? = null,
) {

    init {
        require(primarySuperTypeId == null || primarySuperTypeId in superTypeIds) {
            "Primary immutable super type must be one of direct super types: ${id.value}"
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

enum class JimmerImmutablePrimaryMapping {
    ID,
    VERSION,
    LOGICAL_DELETED,
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
