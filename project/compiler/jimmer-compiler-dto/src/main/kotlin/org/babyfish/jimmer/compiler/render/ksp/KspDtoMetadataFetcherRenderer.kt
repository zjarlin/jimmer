package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.CodeBlock
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
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结 DTO 的 metadata fetcher 渲染为 KotlinPoet 代码块。 */
internal object KspDtoMetadataFetcherRenderer {

    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        configContractResolution: DtoConfigContractResolution,
        generatedPackageName: String,
        generatedSimpleNames: List<String>,
        generatedDtoTypeIdsByTypeName: Map<LsiClass, DtoTypeId>,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiClass>,
        registerImport: (LsiImport) -> Unit,
    ): CodeBlock {
        val generatedDtoTypeName = JimmerDtoPoetTypeNames.create(
            generatedPackageName,
            generatedSimpleNames,
        )
        val fragment = dtoType.toLsiMetadataFetcherPoetFragment(
            targetLanguage = LsiLanguage.KOTLIN,
            graph = graph,
            immutableSchema = immutableSchema,
            workspace = workspace,
            configContractResolution = configContractResolution,
            generatedDtoTypeName = generatedDtoTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames = batchRootDtoTypeNames,
        )
        fragment.imports.forEach(registerImport)
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = fragment.codeBlock,
            typeNames = workspace.dtoMetadataFetcherPoetTypeNames(
                fragment,
                immutableSchema,
                generatedDtoTypeIdsByTypeName,
            ),
        )
    }
}
