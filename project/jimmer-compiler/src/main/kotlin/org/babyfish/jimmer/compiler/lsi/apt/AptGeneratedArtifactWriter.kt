package org.babyfish.jimmer.compiler.lsi.apt

import java.nio.charset.StandardCharsets
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.tools.StandardLocation
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 把共享层生成产物写入当前 APT 编译轮的 filer。
 */
class AptGeneratedArtifactWriter(
    private val filer: Filer,
) {

    fun write(
        artifact: GeneratedArtifact,
        currentRoundElements: Map<LsiSymbolId, Element>,
        currentRoundSources: Map<LsiSymbolId, LsiSource>,
    ) {
        require(artifact.kind != ArtifactKind.KOTLIN_SOURCE) {
            "APT artifact writer cannot write Kotlin source: ${artifact.path}"
        }
        val originatingElements = artifact.originatingElements(currentRoundElements, currentRoundSources)
        val output = when (artifact.kind) {
            ArtifactKind.JAVA_SOURCE -> filer.createSourceFile(
                artifact.javaQualifiedName(),
                *originatingElements,
            )
            ArtifactKind.RESOURCE -> filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                artifact.path,
                *originatingElements,
            )
            ArtifactKind.KOTLIN_SOURCE -> error("Kotlin source was rejected before APT output creation")
        }
        output.openOutputStream().use { stream ->
            stream.write(artifact.content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun GeneratedArtifact.originatingElements(
        currentRoundElements: Map<LsiSymbolId, Element>,
        currentRoundSources: Map<LsiSymbolId, LsiSource>,
    ): Array<Element> {
        val elements = dependencySymbols
            .sorted()
            .mapNotNull(currentRoundElements::get)
            .distinct()
        val representedSourcePaths = dependencySymbols
            .mapNotNull(currentRoundSources::get)
            .mapTo(hashSetOf(), LsiSource::path)
        val unmatchedSources = dependencySources.filterNot { source -> source.path in representedSourcePaths }
        if (emissionMode == ArtifactEmissionMode.STABLE) {
            require(unmatchedSources.isEmpty()) {
                "APT stable artifact cannot depend on non-current sources: $path; " +
                    unmatchedSources.joinToString { source -> source.path }
            }
            require(elements.isNotEmpty()) {
                "APT stable artifact requires current-round originating elements: $path"
            }
        }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(unmatchedSources.isEmpty()) {
                "APT isolating artifact cannot depend on non-APT sources: $path; " +
                    unmatchedSources.joinToString { source -> source.path }
            }
            require(elements.size == 1) {
                "APT isolating artifact requires one current-round originating element: $path"
            }
        }
        return elements.toTypedArray()
    }

    private fun GeneratedArtifact.javaQualifiedName(): String {
        require(path.endsWith(JAVA_SUFFIX)) {
            "APT Java source artifact path must end with '$JAVA_SUFFIX': $path"
        }
        return path.removeSuffix(JAVA_SUFFIX).replace('/', '.')
    }

    companion object {
        private const val JAVA_SUFFIX = ".java"
    }
}
