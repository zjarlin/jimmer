package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import java.nio.charset.StandardCharsets
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
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
    ) {
        require(artifact.kind != ArtifactKind.JAVA_SOURCE) {
            "KSP artifact writer cannot write Java source: ${artifact.path}"
        }
        val dependencies = artifact.dependencies(currentRoundFiles)
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
    ): Dependencies {
        val files = originatingSymbols
            .sorted()
            .mapNotNull(currentRoundFiles::get)
            .distinct()
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(files.size == 1) {
                "KSP isolating artifact requires one current-round originating file: $path"
            }
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
