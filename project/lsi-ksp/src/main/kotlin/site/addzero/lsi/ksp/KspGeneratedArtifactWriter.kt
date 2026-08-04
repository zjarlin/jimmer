package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import java.nio.charset.StandardCharsets
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 把共享层生成产物写入当前 KSP 编译轮的 code generator。
 */
class KspGeneratedArtifactWriter(
    private val codeGenerator: CodeGenerator,
) {

    fun write(
        artifact: GeneratedArtifact,
        currentRoundFiles: Map<LsiSymbolId, KSFile>,
        currentRoundSourceFiles: Collection<KSFile>,
    ) {
        require(artifact.kind != ArtifactKind.JAVA_SOURCE) {
            "KSP artifact writer cannot write Java source: ${artifact.path}"
        }
        val dependencies = artifact.dependencies(currentRoundFiles, currentRoundSourceFiles)
        val output = when (artifact.kind) {
            ArtifactKind.KOTLIN_SOURCE -> codeGenerator.createNewFileByPath(
                dependencies = dependencies,
                path = artifact.kotlinPathWithoutSuffix(),
                extensionName = KOTLIN_EXTENSION,
            )
            ArtifactKind.RESOURCE -> codeGenerator.createNewFileByPath(
                dependencies = dependencies,
                path = artifact.path,
                extensionName = "",
            )
            ArtifactKind.JAVA_SOURCE -> error("Java source was rejected before KSP output creation")
        }
        output.use { stream ->
            stream.write(artifact.content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun GeneratedArtifact.dependencies(
        currentRoundFiles: Map<LsiSymbolId, KSFile>,
        currentRoundSourceFiles: Collection<KSFile>,
    ): Dependencies {
        val allFiles = currentRoundSourceFiles.distinct()
        val hasNoExplicitDependencies = dependencySymbols.isEmpty() && dependencySources.isEmpty()
        if (
            aggregationMode == ArtifactAggregationMode.AGGREGATING &&
            (allFiles.isEmpty() || hasNoExplicitDependencies)
        ) {
            return Dependencies.ALL_FILES
        }
        val filesBySourcePath = allFiles.associateBy { file ->
            LsiSource.of(file.filePath).path
        }
        val files = linkedSetOf<KSFile>()
        dependencySymbols.sorted().mapNotNullTo(files, currentRoundFiles::get)
        dependencySources.sorted().mapNotNullTo(files) { source -> filesBySourcePath[source.path] }
        val unmatchedSources = dependencySources.filterNot { source -> source.path in filesBySourcePath }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(unmatchedSources.isEmpty()) {
                "KSP isolating artifact cannot depend on non-KSP sources: $path; " +
                    unmatchedSources.joinToString { source -> source.path }
            }
            require(files.size == 1) {
                "KSP isolating artifact requires one current-round originating file: $path"
            }
        } else if (unmatchedSources.isNotEmpty()) {
            files += allFiles
        }
        return Dependencies(
            aggregationMode == ArtifactAggregationMode.AGGREGATING,
            *files.toTypedArray(),
        )
    }

    private fun GeneratedArtifact.kotlinPathWithoutSuffix(): String {
        require(path.endsWith(KOTLIN_SUFFIX)) {
            "KSP Kotlin source artifact path must end with '$KOTLIN_SUFFIX': $path"
        }
        return path.removeSuffix(KOTLIN_SUFFIX)
    }

    companion object {
        private const val KOTLIN_SUFFIX = ".kt"
        private const val KOTLIN_EXTENSION = "kt"
    }
}
