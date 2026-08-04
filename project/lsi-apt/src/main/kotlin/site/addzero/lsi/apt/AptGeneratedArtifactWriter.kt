package site.addzero.lsi.apt

import java.nio.charset.StandardCharsets
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
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
        val dependencyElements = dependencySymbols.toTopLevelTypeElements(currentRoundElements)
        val originatingElements = originatingSymbols.toTopLevelTypeElements(currentRoundElements)
        val representedSourcePaths = dependencySymbols
            .mapNotNull(currentRoundSources::get)
            .mapTo(hashSetOf(), LsiSource::path)
        val unmatchedSources = dependencySources.filterNot { source -> source.path in representedSourcePaths }
        if (emissionMode == ArtifactEmissionMode.STABLE) {
            require(unmatchedSources.isEmpty()) {
                "APT stable artifact cannot depend on non-current sources: $path; " +
                    unmatchedSources.joinToString { source -> source.path }
            }
            require(dependencyElements.isNotEmpty()) {
                "APT stable artifact requires current-round originating elements: $path"
            }
        }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(unmatchedSources.isEmpty()) {
                "APT isolating artifact cannot depend on non-APT sources: $path; " +
                    unmatchedSources.joinToString { source -> source.path }
            }
            require(originatingElements.size == 1) {
                "APT isolating artifact requires one current-round originating element: $path"
            }
            val originatingSourcePaths = buildSet {
                originatingSymbols.mapNotNullTo(this) { symbolId ->
                    currentRoundSources[symbolId]?.path
                }
                originatingSources.mapTo(this, LsiSource::path)
            }
            require(dependencySources.all { source -> source.path in originatingSourcePaths }) {
                "APT isolating artifact cannot depend on another source: $path"
            }
            return originatingElements.toTypedArray()
        }
        return dependencyElements.toTypedArray()
    }

    private fun Collection<LsiSymbolId>.toTopLevelTypeElements(
        currentRoundElements: Map<LsiSymbolId, Element>,
    ): List<TypeElement> {
        return asSequence()
            .mapNotNull(currentRoundElements::get)
            .mapNotNull { element -> element.topLevelTypeElement() }
            .distinctBy { element -> element.qualifiedName.toString() }
            .sortedBy { element -> element.qualifiedName.toString() }
            .toList()
    }

    private fun Element.topLevelTypeElement(): TypeElement? {
        var current: Element? = this
        var topLevelType: TypeElement? = null
        while (current != null) {
            if (current is TypeElement) {
                topLevelType = current
            }
            current = current.enclosingElement
        }
        return topLevelType
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
