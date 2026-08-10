package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.dtoMetadataFetcherPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toLsiMetadataFetcherPoetFragment
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结 DTO 的 metadata fetcher 渲染为 JavaPoet 代码块。 */
internal object AptDtoMetadataFetcherRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        configContractResolution: DtoConfigContractResolution,
        generatedPackageName: String,
        generatedSimpleNames: List<String>,
        generatedDtoTypeIdsByTypeName: Map<LsiTypeName, DtoTypeId>,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiTypeName>,
    ): CodeBlock {
        val generatedDtoTypeName = JimmerDtoPoetTypeNames.create(
            generatedPackageName,
            generatedSimpleNames,
        )
        val fragment = dtoType.toLsiMetadataFetcherPoetFragment(
            targetLanguage = LsiLanguage.JAVA,
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            configContractResolution = configContractResolution,
            generatedDtoTypeName = generatedDtoTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames = batchRootDtoTypeNames,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = fragment.codeBlock,
            typeNames = workspace.dtoMetadataFetcherPoetTypeNames(
                fragment,
                immutableSchema,
                generatedDtoTypeIdsByTypeName,
            ),
        )
    }
}
