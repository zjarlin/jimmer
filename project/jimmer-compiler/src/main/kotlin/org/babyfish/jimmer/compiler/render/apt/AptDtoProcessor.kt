package org.babyfish.jimmer.compiler.render.apt

import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.JimmerDtoRendererOptions
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.dependencySources
import site.addzero.lsi.jimmer.dto.dependencySymbols
import site.addzero.lsi.jimmer.dto.rootTypesInDeclarationOrder
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName

internal class AptDtoProcessor(
    graphs: Collection<DtoGraph>,
    annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
    private val immutableSchema: ImmutableSchema,
    private val workspace: LsiWorkspace,
    private val rendererOptions: JimmerDtoRendererOptions,
) {
    private val graphs = graphs.toList()

    private val annotationContractsBySource = annotationContractsBySource.toMap()

    private val interfaceContractsBySource = interfaceContractsBySource.toMap()

    private val configContractsBySource = configContractsBySource.toMap()

    private val rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName> =
        JimmerDtoPoetTypeNames.roots(graphs)

    fun process(): List<GeneratedArtifact> = buildList {
        for (graph in graphs) {
            val annotationContract = annotationContractsBySource[graph.source]
                ?: throw AptDtoException(
                    "No frozen DTO annotation contract for \"${graph.source.path}\""
                )
            val interfaceContractResolution = interfaceContractsBySource[graph.source]
                ?: throw AptDtoException(
                    "No frozen DTO interface contract for \"${graph.source.path}\""
                )
            val configContractResolution = configContractsBySource[graph.source]
                ?: throw AptDtoException(
                    "No frozen DTO config contract for \"${graph.source.path}\""
                )
            val dependencySymbols = graph.dependencySymbols()
            for (rootType in graph.rootTypesInDeclarationOrder()) {
                val rootTypeName = JimmerDtoPoetTypeNames.rootTypeName(
                    rootType,
                    rootDtoTypeNamesByTypeId,
                )
                val content = AptDtoGenerator(
                    graph,
                    rootType,
                    annotationContract,
                    interfaceContractResolution,
                    configContractResolution,
                    immutableSchema,
                    workspace,
                    rootDtoTypeNamesByTypeId,
                    rendererOptions,
                ).generate()
                add(
                    GeneratedArtifact.source(
                        kind = ArtifactKind.JAVA_SOURCE,
                        qualifiedName = rootTypeName.canonicalName,
                        content = content,
                        aggregationMode = ArtifactAggregationMode.AGGREGATING,
                        emissionMode = ArtifactEmissionMode.IMMEDIATE,
                        originatingSymbols = emptySet(),
                        originatingSources = setOf(graph.source),
                        dependencySymbols = dependencySymbols,
                        dependencySources = graph.dependencySources(),
                    )
                )
            }
        }
    }
}
