package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.strictPrimarySubtypesOf
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiWorkspace

internal fun JimmerImmutableDraftCodegenSchema.generatedDraftTypes(
    currentTypeIds: Set<LsiSymbolId>,
): List<JimmerImmutableDraftTypePlan> {
    return currentTypeIds.mapNotNull(typesById::get).sortedBy(JimmerImmutableDraftTypePlan::typeId)
}

internal fun JimmerImmutableDraftTypePlan.javaDraftQualifiedName(): String {
    return "${qualifiedName}Draft"
}

internal fun JimmerImmutableDraftTypePlan.kotlinDraftQualifiedFileName(): String {
    val fileName = "${requireNotNull(sourceBaseName) {
        "Kotlin immutable draft source requires a source basename: ${typeId.value}"
    }}Draft"
    val packageName = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    return if (packageName.isEmpty()) fileName else "$packageName.$fileName"
}

internal fun ImmutableSchema.isBranchDependent(type: ImmutableType): Boolean {
    return type.kind == ImmutableTypeKind.ENTITY && type.inheritanceRootTypeId != null
}

internal fun ImmutableSchema.inheritanceArtifactAggregationMode(type: ImmutableType): ArtifactAggregationMode {
    return if (isBranchDependent(type)) {
        ArtifactAggregationMode.AGGREGATING
    } else {
        ArtifactAggregationMode.ISOLATING
    }
}

internal fun ImmutableSchema.inheritanceArtifactOriginatingSymbols(type: ImmutableType): Set<LsiSymbolId> {
    return buildSet {
        add(type.id)
        strictPrimarySubtypesOf(type).mapTo(this, ImmutableType::id)
    }
}

internal fun LsiWorkspace.immutableSourceBaseName(type: ImmutableType): String {
    val declaration = this[type.id] as? LsiClass
        ?: error("Cannot resolve immutable source declaration '${type.id.value}'")
    val source = declaration.origin.source
        ?: error("Immutable generation target '${type.id.value}' has no source")
    return source.path
        .substringAfterLast('/')
        .substringBeforeLast('.', missingDelimiterValue = source.path.substringAfterLast('/'))
}
