package org.babyfish.jimmer.ksp.dto

import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.KspDtoCompiler
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.resolveDtoTypeInfo
import site.addzero.lsi.jimmer.dto.rootType
import site.addzero.lsi.jimmer.dto.toLsiDtoTypeRegistry
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName

internal class DtoProcessor(
    private val ctx: Context,
    private val dtoFiles: Collection<DtoFile>,
    private val defaultNullableInputModifier: DtoModifier,
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
    private val graphBySourcePath = graphs.associateBy { graph -> graph.source.path }.also { graphMap ->
        require(graphMap.size == graphs.size) { "Frozen DTO graph source paths must be unique" }
    }

    private val rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName> =
        JimmerDtoPoetTypeNames.roots(graphs)

    private val dtoTypeRegistry = immutableSchema.toLsiDtoTypeRegistry(workspace)

    fun process(): Boolean {
        val dtoTypes = findDtoTypes()
        generateDtoTypes(dtoTypes)
        return dtoTypes.isNotEmpty()
    }

    private fun findDtoTypes(): List<DtoType<ImmutableType, ImmutableProp>> {
        val compilers = mutableListOf<KspDtoCompiler>()
        for (dtoFile in dtoFiles) {
            val compiler = try {
                KspDtoCompiler(dtoFile, ctx, defaultNullableInputModifier, immutableSchema)
            } catch (ex: DtoAstException) {
                throw DtoException(
                    "Failed to parse \"" +
                            dtoFile.sourcePath +
                            "\": " +
                            ex.message,
                    ex
                )
            } catch (ex: Throwable) {
                throw DtoException(
                    "Failed to read \"" +
                            dtoFile.sourcePath +
                            "\": " +
                            ex.message,
                    ex
                )
            }
            compilers += compiler
        }
        val dtoTypes = DtoCompiler.compileAll(compilers, ctx::includeDtoTarget).values.flatten()
        DtoTypeLinker.link(dtoTypes, ::resolveDtoType)
        ctx.resolve()
        return dtoTypes
    }

    private fun resolveDtoType(qualifiedName: String): DtoTypeInfo<ImmutableType>? {
        val frozenTypeInfo = dtoTypeRegistry.resolveDtoTypeInfo(qualifiedName, LsiLanguage.KOTLIN)
            ?: return null
        val baseType = ctx.immutableTypeOf(frozenTypeInfo.baseType.qualifiedName)
        if (baseType == null) {
            throw DtoException(
                "The entity type argument of reusable DTO type \"$qualifiedName\" " +
                        "is not an immutable type"
            )
        }
        return DtoTypeInfo(baseType, frozenTypeInfo.kind)
    }

    private fun generateDtoTypes(
        dtoTypes: List<DtoType<ImmutableType, ImmutableProp>>
    ) {
        val allFiles = ctx.resolver.getAllFiles().toList()
        for (dtoType in dtoTypes) {
            val graph = graphBySourcePath[dtoType.dtoFile.sourcePath]
                ?: throw DtoException("No frozen DTO graph for \"${dtoType.dtoFile.sourcePath}\"")
            val qualifiedName = requireNotNull(dtoType.qualifiedName) {
                "Root DTO type must have a qualified name"
            }
            val lsiDtoType = graph.rootType(qualifiedName)
            val mutable = effectiveMutableByRootTypeId.getValue(lsiDtoType.id)
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
            DtoGenerator(
                ctx = ctx,
                mutable = mutable,
                dtoType = dtoType,
                codeGenerator = ctx.environment.codeGenerator,
                lsiGraph = graph,
                lsiDtoType = lsiDtoType,
                immutableSchema = immutableSchema,
                jacksonVersion = jacksonVersion,
                hibernateValidatorEnhancement = hibernateValidatorEnhancement,
                workspace = workspace,
                annotationContract = annotationContract,
                interfaceContractResolution = interfaceContractResolution,
                configContractResolution = configContractResolution,
                rootDtoTypeNamesByTypeId = rootDtoTypeNamesByTypeId,
            ).generate(allFiles)
        }
    }
}
