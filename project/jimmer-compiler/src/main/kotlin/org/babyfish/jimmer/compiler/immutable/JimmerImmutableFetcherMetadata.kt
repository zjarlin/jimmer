package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerImmutableFetcherMetadata(
    private val schema: JimmerImmutableSchema,
) {

    fun generatedTypes(currentTypeIds: Set<LsiSymbolId>): List<JimmerImmutableType> {
        return currentTypeIds
            .mapNotNull(schema.typesById::get)
            .filter { type ->
                type.kind == JimmerImmutableTypeKind.ENTITY ||
                    type.kind == JimmerImmutableTypeKind.EMBEDDABLE
            }
            .sortedBy(JimmerImmutableType::qualifiedName)
    }

    fun validateGenerationContracts(currentTypeIds: Set<LsiSymbolId>) {
        generatedTypes(currentTypeIds).forEach { type ->
            if (strictTypeBranches(type).isEmpty()) {
                return@forEach
            }
            val conflictProp = type.props.firstOrNull { prop -> prop.name == "forType" }
                ?: return@forEach
            throw JimmerImmutablePrecompileException(
                declarationId = conflictProp.declarationId,
                message = "Illegal property name 'forType', it conflicts with the generated fetcher method " +
                    "for inheritance type branches",
            )
        }
    }

    fun targetType(prop: JimmerImmutableProp): JimmerImmutableType? {
        return prop.targetTypeId?.let(schema.typesById::get)
    }

    fun isEntityAssociation(prop: JimmerImmutableProp): Boolean {
        return prop.association && targetType(prop)?.kind == JimmerImmutableTypeKind.ENTITY
    }

    fun hasAnnotation(prop: JimmerImmutableProp, annotationTypeId: LsiSymbolId): Boolean {
        return prop.annotations.any { annotation -> annotation.type == annotationTypeId }
    }

    fun idOnlyAssociationProp(prop: JimmerImmutableProp): JimmerImmutableProp {
        val view = prop.view as? JimmerImmutableView.Id ?: return prop
        return schema.propsById.getValue(view.basePropId)
    }

    fun strictTypeBranches(type: JimmerImmutableType): List<JimmerImmutableType> {
        if (type.kind != JimmerImmutableTypeKind.ENTITY || type.inheritanceRootTypeId == null) {
            return emptyList()
        }
        return schema.types
            .filter { candidate -> candidate.id != type.id && candidate.isPrimarySubtypeOf(type.id) }
            .sortedBy(JimmerImmutableType::qualifiedName)
    }

    fun aggregationMode(type: JimmerImmutableType): ArtifactAggregationMode {
        return if (branchDependent(type)) {
            ArtifactAggregationMode.AGGREGATING
        } else {
            ArtifactAggregationMode.ISOLATING
        }
    }

    fun branchDependent(type: JimmerImmutableType): Boolean {
        return type.kind == JimmerImmutableTypeKind.ENTITY && type.inheritanceRootTypeId != null
    }

    fun originatingSymbols(type: JimmerImmutableType): Set<LsiSymbolId> {
        return buildSet {
            add(type.id)
            strictTypeBranches(type).mapTo(this, JimmerImmutableType::id)
        }
    }

    fun sourceBaseName(type: JimmerImmutableType, workspace: LsiWorkspace): String {
        val declaration = workspace[type.id] as? LsiTypeDeclaration
            ?: error("Cannot resolve immutable source declaration '${type.id.value}'")
        val source = declaration.origin.source
            ?: error("Immutable generation target '${type.id.value}' has no source")
        return source.fileNameWithoutExtension()
    }

    private fun JimmerImmutableType.isPrimarySubtypeOf(superTypeId: LsiSymbolId): Boolean {
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

internal val JimmerImmutableType.packageName: String
    get() = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

internal val JimmerImmutableType.simpleName: String
    get() = qualifiedName.substringAfterLast('.')

private fun LsiSource.fileNameWithoutExtension(): String {
    return path.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = path.substringAfterLast('/'))
}
