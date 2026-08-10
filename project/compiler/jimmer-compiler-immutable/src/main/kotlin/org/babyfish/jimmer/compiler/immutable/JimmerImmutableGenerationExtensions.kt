package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutablePropValueCategory
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.isEntityAssociation
import site.addzero.lsi.jimmer.isImmutableReference
import site.addzero.lsi.jimmer.lineageRootId
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

internal fun ImmutableSchema.generatedFetcherTypes(
    currentTypeIds: Set<LsiSymbolId>,
): List<ImmutableType> {
    return currentTypeIds
        .mapNotNull(typesById::get)
        .filter { type ->
            type.kind == ImmutableTypeKind.ENTITY ||
                type.kind == ImmutableTypeKind.EMBEDDABLE
        }
        .sortedBy(ImmutableType::qualifiedName)
}

internal fun ImmutableSchema.generatedEmbeddableTypes(
    currentTypeIds: Set<LsiSymbolId>,
): List<ImmutableType> {
    return currentTypeIds
        .mapNotNull(typesById::get)
        .filter { type ->
            type.kind == ImmutableTypeKind.EMBEDDABLE && type.typeParameterIds.isEmpty()
        }
        .sortedBy(ImmutableType::qualifiedName)
}

internal fun ImmutableSchema.generatedPropsTypes(
    currentTypeIds: Set<LsiSymbolId>,
): List<ImmutableType> {
    return currentTypeIds
        .mapNotNull(typesById::get)
        .filter { type ->
            type.kind in PROPS_TYPE_KINDS && type.typeParameterIds.isEmpty()
        }
        .sortedBy(ImmutableType::qualifiedName)
}

internal fun ImmutableSchema.generatedQueryTypes(
    currentTypeIds: Set<LsiSymbolId>,
): List<ImmutableType> {
    return currentTypeIds
        .mapNotNull(typesById::get)
        .filter { type ->
            type.kind in SQL_QUERY_TYPE_KINDS && type.typeParameterIds.isEmpty()
        }
        .sortedBy(ImmutableType::qualifiedName)
}

internal fun ImmutableSchema.validateFetcherGenerationContracts(
    currentTypeIds: Set<LsiSymbolId>,
) {
    generatedFetcherTypes(currentTypeIds).forEach { type ->
        if (strictPrimarySubtypesOf(type).isEmpty()) {
            return@forEach
        }
        val conflictProp = type.props.firstOrNull { prop -> prop.name == "forType" }
            ?: return@forEach
        throw ImmutablePrecompileException(
            declarationId = conflictProp.declarationId,
            message = "Illegal property name 'forType', it conflicts with the generated fetcher method " +
                "for inheritance type branches",
        )
    }
}

internal fun ImmutableSchema.orderedProps(type: ImmutableType): List<ImmutableProp> {
    val (idProps, otherProps) = type.props.partition { prop ->
        prop.primaryMapping == PrimaryMapping.ID
    }
    return idProps + otherProps
}

internal fun ImmutableSchema.isDsl(
    prop: ImmutableProp,
    workspace: LsiWorkspace,
    tableEx: Boolean,
): Boolean {
    if (
        prop.view is ImmutableView.Id ||
        prop.isLanguageFormula(workspace) ||
        prop.primaryMapping == PrimaryMapping.TRANSIENT
    ) {
        return false
    }
    if (prop.remote && prop.reverse) {
        return false
    }
    val entityAssociation = isEntityAssociation(prop)
    if (tableEx && !entityAssociation) {
        return false
    }
    if (prop.remote && !prop.list && tableEx) {
        return false
    }
    if (prop.list && entityAssociation) {
        return tableEx
    }
    return true
}

internal fun ImmutableSchema.propsSuperTypes(type: ImmutableType): List<ImmutableType> {
    return type.superTypeIds
        .mapNotNull(typesById::get)
        .filter { superType ->
            superType.kind != ImmutableTypeKind.ENTITY && superType.typeParameterIds.isEmpty()
        }
}

internal fun ImmutableSchema.propsMethodProps(type: ImmutableType): List<ImmutableProp> {
    val propsByLineage = type.props.associateBy(ImmutableProp::lineageRootId)
    val primaryProps = type.primarySuperTypeId
        ?.let(typesById::get)
        ?.let(::orderedProps)
        .orEmpty()
        .mapNotNull { primaryProp -> propsByLineage[primaryProp.lineageRootId()] }
    val selectedIds = primaryProps.mapTo(linkedSetOf(), ImmutableProp::id)
    val orderedProps = orderedProps(type)
    val declaredProps = orderedProps.filter { prop ->
        !prop.overridden && prop.declaringTypeId == type.id && prop.id !in selectedIds
    }
    declaredProps.mapTo(selectedIds, ImmutableProp::id)
    val genericRedefinedProps = orderedProps.filter { prop ->
        prop.id !in selectedIds &&
            typesById[prop.declaringTypeId]?.typeParameterIds?.isNotEmpty() == true
    }
    return primaryProps + declaredProps + genericRedefinedProps
}

internal fun ImmutableSchema.associatedIdPropName(
    type: ImmutableType,
    prop: ImmutableProp,
): String? {
    return associatedIdPropNames(type)[prop.id]
}

internal fun ImmutableSchema.typedPropValueCategory(prop: ImmutableProp): ImmutablePropValueCategory {
    val reference = isImmutableReference(prop)
    return when {
        reference && prop.list -> ImmutablePropValueCategory.REFERENCE_LIST
        reference -> ImmutablePropValueCategory.REFERENCE
        prop.list -> ImmutablePropValueCategory.SCALAR_LIST
        else -> ImmutablePropValueCategory.SCALAR
    }
}

internal fun ImmutableProp.expressionKind(typeSystem: LsiTypeSystem): JimmerImmutablePropExpressionKind {
    return when (val propType = type) {
        is LsiPrimitiveType -> propType.expressionKind()
        is LsiDeclaredType -> propType.expressionKind(typeSystem)
        is LsiArrayType,
        is LsiFunctionType,
        is LsiTypeParameterRef,
        -> JimmerImmutablePropExpressionKind.GENERIC
        is LsiUnresolvedType -> throw ImmutablePrecompileException(
            declarationId = declarationId,
            recoverable = true,
            message = "Cannot resolve embedded property expression type of '${id.value}'",
        )
    }
}

internal fun ImmutableProp.fieldName(): String {
    return StringUtil.snake(name, StringUtil.SnakeCase.UPPER)
}


internal enum class JimmerImmutablePropExpressionKind {
    GENERIC,
    NUMERIC,
    STRING,
    DATE,
    TEMPORAL,
    COMPARABLE,
}

private fun ImmutableSchema.associatedIdPropNames(type: ImmutableType): Map<LsiSymbolId, String> {
    val namesByPropId = linkedMapOf<LsiSymbolId, String>()
    type.props.forEach { prop ->
        val view = prop.view as? ImmutableView.Id ?: return@forEach
        namesByPropId[view.basePropId] = prop.name
    }
    type.props.forEach { prop ->
        if (
            prop.reverse ||
            prop.associationKind !in TO_ONE_ASSOCIATION_KINDS ||
            prop.id in namesByPropId
        ) {
            return@forEach
        }
        val expectedPropName = "${prop.name}Id"
        val expectedProp = type.props.firstOrNull { candidate -> candidate.name == expectedPropName }
        if (expectedProp == null) {
            namesByPropId[prop.id] = expectedPropName
        }
    }
    return namesByPropId
}

private fun ImmutableProp.isLanguageFormula(workspace: LsiWorkspace): Boolean {
    if (formulaKind == FormulaKind.LANGUAGE) {
        return true
    }
    if (formulaKind != FormulaKind.ABSTRACT) {
        return false
    }
    return (workspace[declarationId] as? LsiProperty)?.origin?.language == LsiLanguage.JAVA
}

private fun LsiPrimitiveType.expressionKind(): JimmerImmutablePropExpressionKind {
    if (!boxed) {
        return when (kind) {
            LsiPrimitiveKind.BYTE,
            LsiPrimitiveKind.SHORT,
            LsiPrimitiveKind.INT,
            LsiPrimitiveKind.LONG,
            LsiPrimitiveKind.CHAR,
            LsiPrimitiveKind.FLOAT,
            LsiPrimitiveKind.DOUBLE,
            -> JimmerImmutablePropExpressionKind.NUMERIC
            LsiPrimitiveKind.BOOLEAN,
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> JimmerImmutablePropExpressionKind.GENERIC
        }
    }
    return when (kind) {
        LsiPrimitiveKind.BYTE,
        LsiPrimitiveKind.SHORT,
        LsiPrimitiveKind.INT,
        LsiPrimitiveKind.LONG,
        LsiPrimitiveKind.FLOAT,
        LsiPrimitiveKind.DOUBLE,
        -> JimmerImmutablePropExpressionKind.NUMERIC
        LsiPrimitiveKind.BOOLEAN,
        LsiPrimitiveKind.CHAR,
        -> JimmerImmutablePropExpressionKind.COMPARABLE
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> JimmerImmutablePropExpressionKind.GENERIC
    }
}

private fun LsiDeclaredType.expressionKind(typeSystem: LsiTypeSystem): JimmerImmutablePropExpressionKind {
    return when {
        declarationId == STRING_TYPE_ID -> JimmerImmutablePropExpressionKind.STRING
        isSubtypeOf(NUMBER_TYPE_ID, typeSystem) -> JimmerImmutablePropExpressionKind.NUMERIC
        isSubtypeOf(DATE_TYPE_ID, typeSystem) -> JimmerImmutablePropExpressionKind.DATE
        isSubtypeOf(TEMPORAL_TYPE_ID, typeSystem) -> JimmerImmutablePropExpressionKind.TEMPORAL
        isSubtypeOf(COMPARABLE_TYPE_ID, typeSystem) -> JimmerImmutablePropExpressionKind.COMPARABLE
        else -> JimmerImmutablePropExpressionKind.GENERIC
    }
}

private fun LsiDeclaredType.isSubtypeOf(
    superTypeId: LsiSymbolId,
    typeSystem: LsiTypeSystem,
): Boolean {
    return declarationId == superTypeId || typeSystem.resolveSuperType(declarationId, superTypeId) != null
}

private val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")

private val NUMBER_TYPE_ID = LsiSymbolId.type("java.lang.Number")

private val DATE_TYPE_ID = LsiSymbolId.type("java.util.Date")

private val TEMPORAL_TYPE_ID = LsiSymbolId.type("java.time.temporal.Temporal")

private val COMPARABLE_TYPE_ID = LsiSymbolId.type("java.lang.Comparable")

private val SQL_QUERY_TYPE_KINDS = setOf(
    ImmutableTypeKind.ENTITY,
    ImmutableTypeKind.MAPPED_SUPERCLASS,
)

private val PROPS_TYPE_KINDS = SQL_QUERY_TYPE_KINDS + ImmutableTypeKind.IMMUTABLE

private val TO_ONE_ASSOCIATION_KINDS = setOf(
    AssociationKind.ONE_TO_ONE,
    AssociationKind.MANY_TO_ONE,
)
