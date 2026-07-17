package org.babyfish.jimmer.compiler.module

import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleResourceRenderer {

    fun render(
        schema: JimmerModuleSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        return schema.resources.map { resource -> resource.render(workspace) }
    }

    private fun JimmerModuleResource.render(workspace: LsiWorkspace): GeneratedArtifact {
        val originatingSymbols = dependencies.originatingTypeIds.toSet()
        val content = if (qualifiedTypeNames.isEmpty()) {
            ""
        } else {
            qualifiedTypeNames.joinToString(separator = "\n", postfix = "\n")
        }
        return GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = path,
            content = content,
            aggregationMode = dependencies.aggregationMode,
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }
}
