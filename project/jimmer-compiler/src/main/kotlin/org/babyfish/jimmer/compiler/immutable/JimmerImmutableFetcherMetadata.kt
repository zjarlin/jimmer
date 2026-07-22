package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerImmutableFetcherMetadata(
    private val schema: ImmutableSchema,
) {

    fun generatedTypes(currentTypeIds: Set<LsiSymbolId>): List<ImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind == ImmutableTypeKind.ENTITY ||
                    type.kind == ImmutableTypeKind.EMBEDDABLE
            }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun validateGenerationContracts(currentTypeIds: Set<LsiSymbolId>) {
        generatedTypes(currentTypeIds).forEach { type ->
            if (strictTypeBranches(type).isEmpty()) {
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

    fun targetType(prop: ImmutableProp): ImmutableType? {
        return prop.targetTypeId?.let(schema.typesById::get)
    }

    fun isEntityAssociation(prop: ImmutableProp): Boolean {
        return prop.association && targetType(prop)?.kind == ImmutableTypeKind.ENTITY
    }

    fun hasAnnotation(prop: ImmutableProp, annotationTypeId: LsiSymbolId): Boolean {
        return prop.annotations.any { annotation -> annotation.type == annotationTypeId }
    }

    fun idOnlyAssociationProp(prop: ImmutableProp): ImmutableProp {
        val view = prop.view as? ImmutableView.Id ?: return prop
        return schema.propsById.getValue(view.basePropId)
    }

    fun strictTypeBranches(type: ImmutableType): List<ImmutableType> {
        if (type.kind != ImmutableTypeKind.ENTITY || type.inheritanceRootTypeId == null) {
            return emptyList()
        }
        return schema.types
            .filter { candidate -> candidate.id != type.id && candidate.isPrimarySubtypeOf(type.id) }
            .sortedBy(ImmutableType::qualifiedName)
    }

    fun aggregationMode(type: ImmutableType): ArtifactAggregationMode {
        return if (branchDependent(type)) {
            ArtifactAggregationMode.AGGREGATING
        } else {
            ArtifactAggregationMode.ISOLATING
        }
    }

    fun branchDependent(type: ImmutableType): Boolean {
        return type.kind == ImmutableTypeKind.ENTITY && type.inheritanceRootTypeId != null
    }

    fun originatingSymbols(type: ImmutableType): Set<LsiSymbolId> {
        return buildSet {
            add(type.id)
            strictTypeBranches(type).mapTo(this, ImmutableType::id)
        }
    }

    fun sourceBaseName(type: ImmutableType, workspace: LsiWorkspace): String {
        val declaration = workspace[type.id] as? LsiTypeDeclaration
            ?: error("Cannot resolve immutable source declaration '${type.id.value}'")
        val source = declaration.origin.source
            ?: error("Immutable generation target '${type.id.value}' has no source")
        return source.fileNameWithoutExtension()
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
}

internal val ImmutableType.packageName: String
    get() = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

internal val ImmutableType.simpleName: String
    get() = qualifiedName.substringAfterLast('.')

private fun LsiSource.fileNameWithoutExtension(): String {
    return path.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = path.substringAfterLast('/'))
}
