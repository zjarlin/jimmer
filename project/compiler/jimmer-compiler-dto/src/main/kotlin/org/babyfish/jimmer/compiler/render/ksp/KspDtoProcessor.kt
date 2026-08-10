package org.babyfish.jimmer.compiler.render.ksp

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
import site.addzero.lsi.clazz.LsiClass

internal class KspDtoProcessor(
    graphs: Collection<DtoGraph>,
    private val immutableSchema: ImmutableSchema,
    private val rendererOptions: JimmerDtoRendererOptions,
    private val effectiveMutableByRootTypeId: Map<DtoTypeId, Boolean>,
    private val workspace: LsiWorkspace,
    private val annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    private val interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    private val configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
) {
    private val graphs = graphs.toList()

    private val rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiClass> =
        JimmerDtoPoetTypeNames.roots(graphs)

    fun process(): List<GeneratedArtifact> = buildList {
        for (graph in graphs) {
            val annotationContract = annotationContractsBySource[graph.source]
                ?: throw KspDtoException(
                    "No frozen DTO annotation contract for \"${graph.source.path}\""
                )
            val interfaceContractResolution = interfaceContractsBySource[graph.source]
                ?: throw KspDtoException(
                    "No frozen DTO interface contract for \"${graph.source.path}\""
                )
            val configContractResolution = configContractsBySource[graph.source]
                ?: throw KspDtoException(
                    "No frozen DTO config contract for \"${graph.source.path}\""
                )
            val dependencySymbols = graph.dependencySymbols()
            for (rootType in graph.rootTypesInDeclarationOrder()) {
                val rootTypeName = rootDtoTypeNamesByTypeId.getValue(rootType.id)
                val content = KspDtoGenerator(
                    mutable = effectiveMutableByRootTypeId.getValue(rootType.id),
                    lsiGraph = graph,
                    lsiDtoType = rootType,
                    immutableSchema = immutableSchema,
                    rendererOptions = rendererOptions,
                    workspace = workspace,
                    annotationContract = annotationContract,
                    interfaceContractResolution = interfaceContractResolution,
                    configContractResolution = configContractResolution,
                    rootDtoTypeNamesByTypeId = rootDtoTypeNamesByTypeId,
                ).generate()
                add(
                    GeneratedArtifact.source(
                        kind = ArtifactKind.KOTLIN_SOURCE,
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
