package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId

internal class JimmerImmutableDraftArtifactMetadata(
    schema: JimmerImmutableDraftCodegenSchema,
) {

    private val typesById = schema.typesById

    fun generatedTypes(currentTypeIds: Set<LsiSymbolId>): List<JimmerImmutableDraftTypePlan> {
        return currentTypeIds.mapNotNull(typesById::get).sortedBy(JimmerImmutableDraftTypePlan::typeId)
    }

    fun javaQualifiedName(type: JimmerImmutableDraftTypePlan): String =
        "${type.qualifiedName}Draft"

    fun kotlinQualifiedFileName(type: JimmerImmutableDraftTypePlan): String {
        val fileName = "${requireNotNull(type.sourceBaseName) {
            "Kotlin immutable draft source requires a source basename: ${type.typeId.value}"
        }}Draft"
        val packageName = type.qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
        return if (packageName.isEmpty()) fileName else "$packageName.$fileName"
    }

    fun aggregationMode(type: JimmerImmutableDraftTypePlan): ArtifactAggregationMode {
        val originatingPaths = type.artifactOriginatingSources.mapTo(hashSetOf(), LsiSource::path)
        return if (dependencySources(type).all { source -> source.path in originatingPaths }) {
            ArtifactAggregationMode.ISOLATING
        } else {
            ArtifactAggregationMode.AGGREGATING
        }
    }

    fun dependencySources(type: JimmerImmutableDraftTypePlan): Set<LsiSource> {
        return type.dependencySources
            .filterTo(sortedSetOf()) { source -> source.kind != LsiSourceKind.BINARY }
    }
}
