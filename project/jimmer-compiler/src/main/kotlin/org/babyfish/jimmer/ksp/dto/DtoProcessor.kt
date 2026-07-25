package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.View
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.KspDtoCompiler
import org.babyfish.jimmer.ksp.immutable.generator.K_SPECIFICATION_CLASS_NAME
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.ksp.util.GenericParser
import org.babyfish.jimmer.ksp.util.fastResolve
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.rootType
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
        val declaration = ctx.resolver.getClassDeclarationByName(qualifiedName) ?: return null
        val inputType = ctx.resolver
            .getClassDeclarationByName(Input::class.qualifiedName!!)!!
            .asStarProjectedType()
        val viewType = ctx.resolver
            .getClassDeclarationByName(View::class.qualifiedName!!)!!
            .asStarProjectedType()
        val specificationType = ctx.resolver
            .getClassDeclarationByName(K_SPECIFICATION_CLASS_NAME.canonicalName)!!
            .asStarProjectedType()
        val kind: DtoTypeKind
        val superName: String
        val type = declaration.asStarProjectedType()
        if (inputType.isAssignableFrom(type)) {
            kind = DtoTypeKind.INPUT
            superName = Input::class.qualifiedName!!
        } else if (viewType.isAssignableFrom(type)) {
            kind = DtoTypeKind.VIEW
            superName = View::class.qualifiedName!!
        } else if (specificationType.isAssignableFrom(type)) {
            kind = DtoTypeKind.SPECIFICATION
            superName = K_SPECIFICATION_CLASS_NAME.canonicalName
        } else {
            return null
        }
        val baseDeclaration = GenericParser(
            "reusable DTO",
            declaration,
            superName
        ).parse().arguments[0].type!!.fastResolve().declaration as? KSClassDeclaration
            ?: throw DtoException(
                "The entity type argument of reusable DTO type \"$qualifiedName\" " +
                        "is not an immutable type"
            )
        if (ctx.typeAnnotationOf(baseDeclaration) == null) {
            throw DtoException(
                "The entity type argument of reusable DTO type \"$qualifiedName\" " +
                        "is not an immutable type"
            )
        }
        return DtoTypeInfo(ctx.typeOf(baseDeclaration), kind)
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
