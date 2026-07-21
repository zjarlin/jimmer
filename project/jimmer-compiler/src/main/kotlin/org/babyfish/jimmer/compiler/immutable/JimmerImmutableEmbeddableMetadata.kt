package org.babyfish.jimmer.compiler.immutable

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerImmutableEmbeddableMetadata(
    private val schema: JimmerImmutableSchema,
    private val workspace: LsiWorkspace,
) {

    private val typeSystem = LsiTypeSystem(workspace)

    fun generatedTypes(currentTypeIds: Set<LsiSymbolId>): List<JimmerImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind == JimmerImmutableTypeKind.EMBEDDABLE && type.typeParameterIds.isEmpty()
            }
            .sortedBy(JimmerImmutableType::qualifiedName)
    }

    fun targetType(prop: JimmerImmutableProp): JimmerImmutableType? {
        return prop.targetTypeId?.let(schema.typesById::get)
    }

    fun typedPropKind(prop: JimmerImmutableProp): JimmerImmutableTypedPropKind {
        val reference = prop.association || prop.embedded
        return when {
            reference && prop.list -> JimmerImmutableTypedPropKind.REFERENCE_LIST
            reference -> JimmerImmutableTypedPropKind.REFERENCE
            prop.list -> JimmerImmutableTypedPropKind.SCALAR_LIST
            else -> JimmerImmutableTypedPropKind.SCALAR
        }
    }

    fun typedPropElementType(prop: JimmerImmutableProp): LsiTypeRef {
        if (!prop.list) {
            return prop.type
        }
        val listType = prop.type as? LsiDeclaredType
            ?: error("List immutable property '${prop.id.value}' must use a declared list type")
        return listType.arguments.singleOrNull()?.type
            ?: error("List immutable property '${prop.id.value}' must declare one element type")
    }

    fun expressionKind(prop: JimmerImmutableProp): JimmerImmutablePropExpressionKind {
        return when (val type = prop.type) {
            is LsiPrimitiveType -> type.expressionKind()
            is LsiDeclaredType -> type.expressionKind()
            is LsiArrayType,
            is LsiTypeParameterRef,
            -> JimmerImmutablePropExpressionKind.GENERIC
            is LsiUnresolvedType -> throw JimmerImmutablePrecompileException(
                declarationId = prop.declarationId,
                recoverable = true,
                message = "Cannot resolve embedded property expression type of '${prop.id.value}'",
            )
        }
    }

    fun fieldName(prop: JimmerImmutableProp): String {
        return StringUtil.snake(prop.name, StringUtil.SnakeCase.UPPER)
    }

    fun sourceBaseName(type: JimmerImmutableType): String {
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

    fun originatingSymbols(type: JimmerImmutableType): Set<LsiSymbolId> {
        return setOf(type.id)
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
