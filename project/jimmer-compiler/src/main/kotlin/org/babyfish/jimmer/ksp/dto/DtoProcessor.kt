package org.babyfish.jimmer.ksp.dto

import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.ksp.Context
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.rootTypesInDeclarationOrder
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName

internal class DtoProcessor(
    private val ctx: Context,
    graphs: Collection<DtoGraph>,
    private val immutableSchema: ImmutableSchema,
    private val jacksonVersion: JimmerDtoJacksonVersion,
    private val hibernateValidatorEnhancement: Boolean,
    private val effectiveMutableByRootTypeId: Map<DtoTypeId, Boolean>,
    private val workspace: LsiWorkspace,
    private val annotationContractsBySource: Map<LsiSource, DtoAnnotationContract>,
    private val interfaceContractsBySource: Map<LsiSource, DtoInterfaceContractResolution>,
    private val configContractsBySource: Map<LsiSource, DtoConfigContractResolution>,
) {
    private val graphs = graphs.toList()

    private val rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName> =
        JimmerDtoPoetTypeNames.roots(graphs)

    fun process(): Boolean {
        val allFiles = ctx.resolver.getAllFiles().toList()
        var generated = false
        for (graph in graphs) {
            val annotationContract = annotationContractsBySource[graph.source]
                ?: throw DtoException(
                    "No frozen DTO annotation contract for \"${graph.source.path}\""
                )
            val interfaceContractResolution = interfaceContractsBySource[graph.source]
                ?: throw DtoException(
                    "No frozen DTO interface contract for \"${graph.source.path}\""
                )
            val configContractResolution = configContractsBySource[graph.source]
                ?: throw DtoException(
                    "No frozen DTO config contract for \"${graph.source.path}\""
                )
            for (rootType in graph.rootTypesInDeclarationOrder()) {
                DtoGenerator(
                    ctx = ctx,
                    mutable = effectiveMutableByRootTypeId.getValue(rootType.id),
                    codeGenerator = ctx.environment.codeGenerator,
                    lsiGraph = graph,
                    lsiDtoType = rootType,
                    immutableSchema = immutableSchema,
                    jacksonVersion = jacksonVersion,
                    hibernateValidatorEnhancement = hibernateValidatorEnhancement,
                    workspace = workspace,
                    annotationContract = annotationContract,
                    interfaceContractResolution = interfaceContractResolution,
                    configContractResolution = configContractResolution,
                    rootDtoTypeNamesByTypeId = rootDtoTypeNamesByTypeId,
                ).generate(allFiles)
                generated = true
            }
        }
        return generated
    }
}
