package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerImmutableQueryMetadata(
    private val schema: ImmutableSchema,
    private val workspace: LsiWorkspace,
) {

    private val typeSystem = LsiTypeSystem(workspace)

    fun generatedEmbeddableTypes(currentTypeIds: Set<LsiSymbolId>): List<ImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind == ImmutableTypeKind.EMBEDDABLE && type.typeParameterIds.isEmpty()
            }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun generatedPropsTypes(currentTypeIds: Set<LsiSymbolId>): List<ImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind in PROPS_TYPE_KINDS && type.typeParameterIds.isEmpty()
            }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun generatedQueryTypes(currentTypeIds: Set<LsiSymbolId>): List<ImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind in SQL_QUERY_TYPE_KINDS && type.typeParameterIds.isEmpty()
            }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun generatedEntityTypes(currentTypeIds: Set<LsiSymbolId>): List<ImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind == ImmutableTypeKind.ENTITY && type.typeParameterIds.isEmpty()
            }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun targetType(prop: ImmutableProp): ImmutableType? {
        return prop.targetTypeId?.let(schema.typesById::get)
    }

    fun targetIdProp(prop: ImmutableProp): ImmutableProp? {
        return targetType(prop)?.idPropId?.let(schema.propsById::get)
    }

    fun isEntityAssociation(prop: ImmutableProp): Boolean {
        return prop.association &&
            (prop.genericTarget || targetType(prop)?.kind == ImmutableTypeKind.ENTITY)
    }

    fun isImmutableReference(prop: ImmutableProp): Boolean {
        return prop.association || prop.embedded || targetType(prop)?.kind == ImmutableTypeKind.IMMUTABLE
    }

    fun orderedProps(type: ImmutableType): List<ImmutableProp> {
        val (idProps, otherProps) = type.props.partition { prop ->
            prop.primaryMapping == PrimaryMapping.ID
        }
        return idProps + otherProps
    }

    fun isDsl(prop: ImmutableProp, tableEx: Boolean): Boolean {
        if (prop.view is ImmutableView.Id || prop.isLanguageFormula() ||
            prop.primaryMapping == PrimaryMapping.TRANSIENT) {
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

    fun propsSuperTypes(type: ImmutableType): List<ImmutableType> {
        return type.superTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { superType ->
                superType.kind != ImmutableTypeKind.ENTITY && superType.typeParameterIds.isEmpty()
            }
    }

    fun propsMethodProps(type: ImmutableType): List<ImmutableProp> {
        val propsByLineage = type.props.associateBy { prop -> prop.lineageRootId() }
        val primaryProps = type.primarySuperTypeId
            ?.let(schema.typesById::get)
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
                schema.typesById[prop.declaringTypeId]?.typeParameterIds?.isNotEmpty() == true
        }
        return primaryProps + declaredProps + genericRedefinedProps
    }

    fun runtimePropsOwnerType(
        type: ImmutableType,
        prop: ImmutableProp,
    ): ImmutableType {
        if (prop.declaringTypeId == type.id) {
            return type
        }
        val primaryType = type.primarySuperTypeId?.let(schema.typesById::get) ?: return type
        val primaryProp = primaryType.props.firstOrNull { candidate ->
            candidate.lineageRootId() == prop.lineageRootId()
        } ?: return type
        return runtimePropsOwnerType(primaryType, primaryProp)
    }

    fun associatedIdPropName(
        type: ImmutableType,
        prop: ImmutableProp,
    ): String? {
        return associatedIdPropNames(type)[prop.id]
    }

    fun strictTypeBranches(type: ImmutableType): List<ImmutableType> {
        if (type.kind != ImmutableTypeKind.ENTITY || type.inheritanceRootTypeId == null) {
            return emptyList()
        }
        return schema.types
            .filter { candidate -> candidate.id != type.id && candidate.isPrimarySubtypeOf(type.id) }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun queryAggregationMode(type: ImmutableType): ArtifactAggregationMode {
        return if (branchDependent(type)) {
            ArtifactAggregationMode.AGGREGATING
        } else {
            ArtifactAggregationMode.ISOLATING
        }
    }

    fun branchDependent(type: ImmutableType): Boolean {
        return type.kind == ImmutableTypeKind.ENTITY && type.inheritanceRootTypeId != null
    }

    fun queryOriginatingSymbols(type: ImmutableType): Set<LsiSymbolId> {
        return buildSet {
            add(type.id)
            strictTypeBranches(type).mapTo(this, ImmutableType::id)
        }
    }

    fun typedPropKind(prop: ImmutableProp): JimmerImmutableTypedPropKind {
        val reference = isImmutableReference(prop)
        return when {
            reference && prop.list -> JimmerImmutableTypedPropKind.REFERENCE_LIST
            reference -> JimmerImmutableTypedPropKind.REFERENCE
            prop.list -> JimmerImmutableTypedPropKind.SCALAR_LIST
            else -> JimmerImmutableTypedPropKind.SCALAR
        }
    }

    fun typedPropElementType(prop: ImmutableProp): LsiTypeRef {
        if (!prop.list) {
            return prop.type
        }
        val listType = prop.type as? LsiDeclaredType
            ?: error("List immutable property '${prop.id.value}' must use a declared list type")
        return listType.arguments.singleOrNull()?.type
            ?: error("List immutable property '${prop.id.value}' must declare one element type")
    }

    fun expressionKind(prop: ImmutableProp): JimmerImmutablePropExpressionKind {
        return when (val type = prop.type) {
            is LsiPrimitiveType -> type.expressionKind()
            is LsiDeclaredType -> type.expressionKind()
            is LsiArrayType,
            is LsiTypeParameterRef,
            -> JimmerImmutablePropExpressionKind.GENERIC
            is LsiUnresolvedType -> throw ImmutablePrecompileException(
                declarationId = prop.declarationId,
                recoverable = true,
                message = "Cannot resolve embedded property expression type of '${prop.id.value}'",
            )
        }
    }

    fun fieldName(prop: ImmutableProp): String {
        return StringUtil.snake(prop.name, StringUtil.SnakeCase.UPPER)
    }

    fun sourceBaseName(type: ImmutableType): String {
        val declaration = workspace[type.id] as? LsiTypeDeclaration
            ?: error("Cannot resolve immutable source declaration '${type.id.value}'")
        val source = declaration.origin.source
            ?: error("Immutable generation target '${type.id.value}' has no source")
        return source.path
            .substringAfterLast('/')
            .substringBeforeLast('.', missingDelimiterValue = source.path.substringAfterLast('/'))
    }

    fun aggregationMode(): ArtifactAggregationMode {
        return ArtifactAggregationMode.ISOLATING
    }

    fun originatingSymbols(type: ImmutableType): Set<LsiSymbolId> {
        return setOf(type.id)
    }

    private fun associatedIdPropNames(type: ImmutableType): Map<LsiSymbolId, String> {
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

    private fun ImmutableProp.isLanguageFormula(): Boolean {
        if (formulaKind == FormulaKind.LANGUAGE) {
            return true
        }
        if (formulaKind != FormulaKind.ABSTRACT) {
            return false
        }
        return (workspace[declarationId] as? LsiProperty)?.origin?.language == LsiLanguage.JAVA
    }

    private fun ImmutableProp.lineageRootId(): LsiSymbolId {
        return overrideChain.lastOrNull() ?: declarationId
    }

    private fun ImmutableType.isPrimarySubtypeOf(superTypeId: LsiSymbolId): Boolean {
        var currentTypeId = primarySuperTypeId
        val visited = mutableSetOf<LsiSymbolId>()
        while (currentTypeId != null && visited.add(currentTypeId)) {
            if (currentTypeId == superTypeId) {
                return true
            }
            currentTypeId = schema.typesById[currentTypeId]?.primarySuperTypeId
        }
        return false
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

    private fun LsiDeclaredType.expressionKind(): JimmerImmutablePropExpressionKind {
        return when {
            declarationId == STRING_TYPE_ID -> JimmerImmutablePropExpressionKind.STRING
            isSubtypeOf(NUMBER_TYPE_ID) -> JimmerImmutablePropExpressionKind.NUMERIC
            isSubtypeOf(DATE_TYPE_ID) -> JimmerImmutablePropExpressionKind.DATE
            isSubtypeOf(TEMPORAL_TYPE_ID) -> JimmerImmutablePropExpressionKind.TEMPORAL
            isSubtypeOf(COMPARABLE_TYPE_ID) -> JimmerImmutablePropExpressionKind.COMPARABLE
            else -> JimmerImmutablePropExpressionKind.GENERIC
        }
    }

    private fun LsiDeclaredType.isSubtypeOf(superTypeId: LsiSymbolId): Boolean {
        return declarationId == superTypeId || typeSystem.resolveSuperType(declarationId, superTypeId) != null
    }
}

internal enum class JimmerImmutableTypedPropKind {
    SCALAR,
    SCALAR_LIST,
    REFERENCE,
    REFERENCE_LIST,
}

internal enum class JimmerImmutablePropExpressionKind {
    GENERIC,
    NUMERIC,
    STRING,
    DATE,
    TEMPORAL,
    COMPARABLE,
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
