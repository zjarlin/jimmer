package org.babyfish.jimmer.compiler.input

import site.addzero.lsi.jimmer.input.*

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact

class CompilerInputDocumentBundleRenderer {

    fun render(
        bundleId: String,
        snapshots: Collection<CompilerInputDocumentSnapshot>,
    ): List<GeneratedArtifact> {
        require(BUNDLE_ID_REGEX.matches(bundleId)) { "DTO bundle id is invalid: '$bundleId'" }
        val documents = snapshots
            .map { snapshot -> snapshot.document }
            .filter { document ->
                document.kind == DTO_INPUT_DOCUMENT_KIND &&
                    document.origin is CompilerInputDocumentOrigin.Project
            }
            .sorted()
        require(documents.distinctBy { document -> document.source.path }.size == documents.size) {
            "DTO bundle '$bundleId' cannot contain duplicate document sources"
        }
        require(
            documents.distinctBy { document ->
                val origin = document.origin as CompilerInputDocumentOrigin.Project
                Triple(document.sourceSet, origin.sourceRoot, document.relativePath)
            }.size == documents.size
        ) {
            "DTO bundle '$bundleId' cannot contain duplicate logical document paths"
        }
        val bundlePath = sha256(bundleId)
        val renderedDocuments = documents.map { document ->
            val origin = document.origin as CompilerInputDocumentOrigin.Project
            val contentSha256 = sha256(document.content)
            val logicalIdentity = listOf(
                document.sourceSet.name,
                origin.sourceRoot,
                document.relativePath,
            ).joinToString("\u0000")
            RenderedBundleDocument(
                sourceSet = document.sourceSet.name,
                sourceRoot = origin.sourceRoot,
                relativePath = document.relativePath,
                resourcePath = "$BUNDLE_RESOURCE_ROOT/$bundlePath/${sha256(logicalIdentity)}.dto",
                contentSha256 = contentSha256,
                content = document.content,
                source = document.source,
            )
        }
        val allSources = renderedDocuments.mapTo(sortedSetOf(), RenderedBundleDocument::source)
        val manifest = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = CompilerInputDocumentBundleReader.MARKER_PATH,
            content = manifestContent(bundleId, renderedDocuments),
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            originatingSources = allSources,
        )
        val resources = renderedDocuments.map { document ->
            GeneratedArtifact.create(
                kind = ArtifactKind.RESOURCE,
                path = document.resourcePath,
                content = document.content,
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
                originatingSources = setOf(document.source),
            )
        }
        return (listOf(manifest) + resources).sortedBy(GeneratedArtifact::key)
    }

    private fun manifestContent(
        bundleId: String,
        documents: List<RenderedBundleDocument>,
    ): String = buildString {
        append("format=2\n")
        append("bundleId=")
        append(bundleId)
        append('\n')
        append("document.count=")
        append(documents.size)
        append('\n')
        documents.forEachIndexed { index, document ->
            append("document.")
            append(index)
            append(".sourceSet=")
            append(document.sourceSet)
            append('\n')
            append("document.")
            append(index)
            append(".sourceRoot=")
            append(document.sourceRoot)
            append('\n')
            append("document.")
            append(index)
            append(".relativePath=")
            append(document.relativePath)
            append('\n')
            append("document.")
            append(index)
            append(".resource=")
            append(document.resourcePath)
            append('\n')
            append("document.")
            append(index)
            append(".sha256=")
            append(document.contentSha256)
            append('\n')
        }
    }

    private data class RenderedBundleDocument(
        val sourceSet: String,
        val sourceRoot: String,
        val relativePath: String,
        val resourcePath: String,
        val contentSha256: String,
        val content: String,
        val source: site.addzero.lsi.core.LsiSource,
    )

    companion object {
        const val BUNDLE_ID_OPTION = "jimmer.dto.bundle.id"

        private const val BUNDLE_RESOURCE_ROOT = "META-INF/jimmer/dto-bundles"

        private val BUNDLE_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*")
    }
}

private fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
