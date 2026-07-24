package site.addzero.lsi.poet

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 描述尚未绑定具体 Poet 实现的源码产物。
 */
data class LsiPoetArtifact(
    val file: LsiPoetFile,
    val typeNames: List<LsiPoetTypeName>,
    val aggregationMode: ArtifactAggregationMode,
    val emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
    val originatingSymbols: Set<LsiSymbolId> = emptySet(),
    val originatingSources: Set<LsiSource> = emptySet(),
    val dependencySymbols: Set<LsiSymbolId> = originatingSymbols,
    val dependencySources: Set<LsiSource> = originatingSources,
) {
    val kind: ArtifactKind = when (file.language) {
        LsiLanguage.JAVA -> ArtifactKind.JAVA_SOURCE
        LsiLanguage.KOTLIN -> ArtifactKind.KOTLIN_SOURCE
        LsiLanguage.UNKNOWN -> error("LSI Poet artifact requires Java or Kotlin source")
    }

    val qualifiedFileName: String = if (file.packageName.isEmpty()) {
        file.fileName
    } else {
        "${file.packageName}.${file.fileName}"
    }

    val path: String = buildString {
        if (file.packageName.isNotEmpty()) {
            append(file.packageName.replace('.', '/'))
            append('/')
        }
        append(file.fileName)
        append(
            when (kind) {
                ArtifactKind.JAVA_SOURCE -> ".java"
                ArtifactKind.KOTLIN_SOURCE -> ".kt"
                ArtifactKind.RESOURCE -> error("LSI Poet artifact cannot be a resource")
            }
        )
    }

    init {
        val duplicateTypeIds = typeNames
            .groupingBy(LsiPoetTypeName::typeId)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateTypeIds.isEmpty()) {
            "Duplicate LSI Poet type ids: ${duplicateTypeIds.joinToString { id -> id.value }}"
        }
        val missingTypeIds = file.referencedTypeIds - typeNames.mapTo(hashSetOf(), LsiPoetTypeName::typeId)
        require(missingTypeIds.isEmpty()) {
            "Missing LSI Poet type names for $qualifiedFileName: " +
                missingTypeIds.joinToString { id -> id.value }
        }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(originatingSymbols.size == 1) {
                "Isolating LSI Poet artifact requires exactly one originating symbol: $qualifiedFileName"
            }
        }
        require(dependencySymbols.containsAll(originatingSymbols)) {
            "LSI Poet artifact dependencies must contain all originating symbols: $qualifiedFileName"
        }
        require(dependencySources.containsAll(originatingSources)) {
            "LSI Poet artifact dependencies must contain all originating sources: $qualifiedFileName"
        }
        require(
            emissionMode != ArtifactEmissionMode.STABLE ||
                aggregationMode == ArtifactAggregationMode.AGGREGATING
        ) {
            "Stable LSI Poet artifact must be aggregating: $qualifiedFileName"
        }
    }

    fun generatedArtifact(content: String): GeneratedArtifact {
        return GeneratedArtifact.create(
            kind = kind,
            path = path,
            content = content,
            aggregationMode = aggregationMode,
            emissionMode = emissionMode,
            originatingSymbols = originatingSymbols,
            originatingSources = originatingSources,
            dependencySymbols = dependencySymbols,
            dependencySources = dependencySources,
        )
    }
}

/**
 * 将纯 LSI Poet 模型渲染为平台 filer 可写出的产物。
 */
fun interface LsiPoetRenderer {
    fun render(artifact: LsiPoetArtifact): GeneratedArtifact
}
