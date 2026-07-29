package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.baseProp
import site.addzero.lsi.jimmer.dto.basePropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.bodyType
import site.addzero.lsi.jimmer.dto.boundImmutableProp
import site.addzero.lsi.jimmer.dto.foldProp
import site.addzero.lsi.jimmer.dto.foldPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.hiddenFlatPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.toLsiType
import site.addzero.lsi.jimmer.dto.typeBranchesInDeclarationOrder
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetCodeFragment
import site.addzero.lsi.poet.LsiPoetImport
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.generatedTopLevelPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将冻结 DTO 的 metadata fetcher 降低为两端共享的代码片段。 */
internal fun DtoType.toLsiMetadataFetcherPoetFragment(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    configContractResolution: DtoConfigContractResolution,
    generatedDtoTypeName: LsiPoetTypeName,
    generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    batchRootDtoTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
): LsiPoetCodeFragment {
    require(graph.typesById[id] === this) {
        "DTO metadata fetcher type does not belong to this graph: ${id.value}"
    }
    JimmerDtoPoetTypeNames.requirePlanned(
        graph = graph,
        type = this,
        typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        typeName = generatedDtoTypeName,
    )
    return MetadataFetcherPoetLowering(
        targetLanguage = targetLanguage,
        graph = graph,
        immutableSchema = immutableSchema,
        workspace = workspace,
        configContractResolution = configContractResolution,
        generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        batchRootDtoTypeNames = batchRootDtoTypeNames,
    ).lower(this, generatedDtoTypeName)
}

/** 为独立 metadata fetcher 代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoMetadataFetcherPoetTypeNames(
    fragment: LsiPoetCodeFragment,
    immutableSchema: ImmutableSchema,
    generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
): List<LsiPoetTypeName> {
    val generatedFetcherTypeNames = immutableSchema.types.map { type ->
        generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}Fetcher")
    }
    return toLsiPoetTypeNames(
        typeIds = fragment.codeBlock.referencedTypeIds,
        additional = (
            DTO_CONFIG_RUNTIME_TYPE_NAMES +
                generatedFetcherTypeNames +
                generatedDtoTypeIdsByTypeName.keys
            ).distinctBy(LsiPoetTypeName::typeId),
    )
}

private class MetadataFetcherPoetLowering(
    private val targetLanguage: LsiLanguage,
    private val graph: DtoGraph,
    private val immutableSchema: ImmutableSchema,
    private val workspace: LsiWorkspace,
    private val configContractResolution: DtoConfigContractResolution,
    private val generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    private val batchRootDtoTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
) {

    init {
        require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
            "DTO metadata fetcher requires Java or Kotlin: $targetLanguage"
        }
    }

    fun lower(
        rootType: DtoType,
        generatedRootTypeName: LsiPoetTypeName,
    ): LsiPoetCodeFragment {
        val codeBlock = LsiPoetCodeBlock.build {
            when (targetLanguage) {
                LsiLanguage.JAVA -> javaRoot(rootType, generatedRootTypeName)
                LsiLanguage.KOTLIN -> kotlinRoot(rootType, generatedRootTypeName)
                else -> error("DTO metadata fetcher requires Java or Kotlin: $targetLanguage")
            }
        }
        val imports = when (targetLanguage) {
            LsiLanguage.JAVA -> emptyList()
            LsiLanguage.KOTLIN -> listOf(
                LsiPoetImport(rootType.immutableBaseType().packageName, "by")
            )
            else -> error("DTO metadata fetcher requires Java or Kotlin: $targetLanguage")
        }
        return LsiPoetCodeFragment(codeBlock, imports)
    }

    private fun LsiPoetCodeBuilder.javaRoot(
        rootType: DtoType,
        generatedRootTypeName: LsiPoetTypeName,
    ) {
        val baseType = rootType.immutableBaseType()
        type(fetcherType(baseType))
        text(".$")
        indent {
            javaFields(rootType, rootType, GeneratedOwner(generatedRootTypeName))
            rootType.typeBranches(generatedRootTypeName) {
                    branchType,
                    generatedBranchType,
                    generatedBranchTypeName,
                    targetTypeId ->
                if (targetTypeId == rootType.baseTypeId) {
                    javaFields(branchType, generatedBranchType, GeneratedOwner(generatedBranchTypeName))
                } else {
                    val targetType = immutableSchema.typesById.getValue(targetTypeId)
                    line()
                    text(".forType(")
                    type(fetcherType(targetType))
                    text(".$")
                    indent {
                        javaFields(branchType, generatedBranchType, GeneratedOwner(generatedBranchTypeName))
                    }
                    line()
                    text(")")
                }
            }
        }
    }

    private fun LsiPoetCodeBuilder.kotlinRoot(
        rootType: DtoType,
        generatedRootTypeName: LsiPoetTypeName,
    ) {
        val baseType = rootType.immutableBaseType()
        topLevelMember(NEW_FETCHER_PACKAGE, "newFetcher", extension = false)
        text("(")
        type(LsiDeclaredType(baseType.id))
        text("::class).")
        text("by")
        text(" {")
        indent {
            line()
            kotlinFields(rootType, rootType, GeneratedOwner(generatedRootTypeName))
            rootType.typeBranches(generatedRootTypeName) {
                    branchType,
                    generatedBranchType,
                    generatedBranchTypeName,
                    targetTypeId ->
                if (targetTypeId == rootType.baseTypeId) {
                    kotlinFields(branchType, generatedBranchType, GeneratedOwner(generatedBranchTypeName))
                } else {
                    text("forType(")
                    type(LsiDeclaredType(targetTypeId))
                    text("::class) {")
                    indent {
                        line()
                        kotlinFields(branchType, generatedBranchType, GeneratedOwner(generatedBranchTypeName))
                    }
                    text("}")
                    line()
                }
            }
        }
        text("}")
    }

    private inline fun DtoType.typeBranches(
        generatedOwnerTypeName: LsiPoetTypeName,
        block: (
            branchType: DtoType,
            generatedBranchType: DtoType,
            generatedBranchTypeName: LsiPoetTypeName,
            targetTypeId: LsiSymbolId,
        ) -> Unit,
    ) {
        polymorphism?.typeBranchesInDeclarationOrder()?.forEach { branch ->
            val generatedBranchType = branch.mergedType(graph)
            val generatedBranchTypeName = directChildOccurrence(
                ownerTypeName = generatedOwnerTypeName,
                targetType = generatedBranchType,
            )
            block(
                branch.bodyType(graph),
                generatedBranchType,
                generatedBranchTypeName,
                requireNotNull(branch.targetBaseTypeId),
            )
        }
    }

    private fun LsiPoetCodeBuilder.javaFields(
        semanticType: DtoType,
        generatedType: DtoType,
        generatedOwner: GeneratedOwner,
    ) {
        semanticType.basePropsInDeclarationOrder(graph)
            .filter { prop -> prop.nextPropId == null }
            .forEach { prop ->
                val generatedProp = generatedType.baseProp(graph, prop.name)
                if (!generatedOwner.currentTypeHasOccurrence && prop.functionName == "flat") {
                    javaHiddenField(prop, generatedProp, generatedOwner)
                } else {
                    javaField(prop, generatedProp, generatedOwner)
                }
            }
        val generatedHiddenProps = generatedType.hiddenFlatPropsInDeclarationOrder(graph)
        semanticType.hiddenFlatPropsInDeclarationOrder(graph).forEach { prop ->
            if (!prop.isId()) {
                javaHiddenField(
                    prop,
                    generatedHiddenProps.single { generatedProp -> generatedProp.name == prop.name },
                    generatedOwner,
                )
            }
        }
        semanticType.foldPropsInDeclarationOrder(graph).forEach { prop ->
            val generatedProp = generatedType.foldProp(graph, prop.name)
            val semanticTargetType = prop.generatedTargetType(graph)
            val generatedTargetType = generatedProp.generatedTargetType(graph)
            javaFields(semanticTargetType, generatedTargetType, generatedOwner.foldTarget(generatedTargetType))
        }
    }

    private fun LsiPoetCodeBuilder.kotlinFields(
        semanticType: DtoType,
        generatedType: DtoType,
        generatedOwner: GeneratedOwner,
    ) {
        semanticType.basePropsInDeclarationOrder(graph)
            .filter { prop -> prop.nextPropId == null }
            .forEach { prop ->
                val generatedProp = generatedType.baseProp(graph, prop.name)
                if (!generatedOwner.currentTypeHasOccurrence && prop.functionName == "flat") {
                    kotlinHiddenField(prop, generatedProp, generatedOwner)
                } else {
                    kotlinField(prop, generatedProp, generatedOwner)
                }
            }
        val generatedHiddenProps = generatedType.hiddenFlatPropsInDeclarationOrder(graph)
        semanticType.hiddenFlatPropsInDeclarationOrder(graph).forEach { prop ->
            if (!prop.isId()) {
                kotlinHiddenField(
                    prop,
                    generatedHiddenProps.single { generatedProp -> generatedProp.name == prop.name },
                    generatedOwner,
                )
            }
        }
        semanticType.foldPropsInDeclarationOrder(graph).forEach { prop ->
            val generatedProp = generatedType.foldProp(graph, prop.name)
            val semanticTargetType = prop.generatedTargetType(graph)
            val generatedTargetType = generatedProp.generatedTargetType(graph)
            kotlinFields(semanticTargetType, generatedTargetType, generatedOwner.foldTarget(generatedTargetType))
        }
    }

    private fun LsiPoetCodeBuilder.javaField(
        semanticProp: DtoBaseProp,
        generatedProp: DtoBaseProp,
        generatedOwner: GeneratedOwner,
    ) {
        if (semanticProp.isId()) {
            return
        }
        val configured = semanticProp.config != null
        val targetDtoType = if (semanticProp.recursive) {
            null
        } else {
            generatedProp.targetDtoTypeOrNull(generatedOwner)
        }
        val methodName = if (semanticProp.recursive) {
            StringUtil.identifier("recursive", semanticProp.fetcherPropName())
        } else {
            semanticProp.fetcherPropName()
        }
        line()
        text(".")
        name(methodName)
        text("(")
        if (configured) {
            indent {
                line()
                if (targetDtoType != null) {
                    javaTargetMetadata(targetDtoType)
                    text(", ")
                    line()
                }
                add(semanticProp.configCodeBlock())
            }
            line()
        } else if (targetDtoType != null) {
            javaTargetMetadata(targetDtoType)
        }
        text(")")
    }

    private fun LsiPoetCodeBuilder.kotlinField(
        semanticProp: DtoBaseProp,
        generatedProp: DtoBaseProp,
        generatedOwner: GeneratedOwner,
    ) {
        if (semanticProp.isId()) {
            return
        }
        name(
            if (semanticProp.recursive) {
                "${semanticProp.fetcherPropName()}*"
            } else {
                semanticProp.fetcherPropName()
            }
        )
        val targetDtoType = if (semanticProp.recursive) {
            null
        } else {
            generatedProp.targetDtoTypeOrNull(generatedOwner)
        }
        if (targetDtoType != null) {
            text("(")
            kotlinTargetMetadata(targetDtoType)
            text(")")
        } else if (semanticProp.config == null) {
            text("()")
        }
        semanticProp.config?.let { add(semanticProp.configCodeBlock()) }
        line()
    }

    private fun LsiPoetCodeBuilder.javaHiddenField(
        semanticProp: DtoBaseProp,
        generatedProp: DtoBaseProp,
        generatedOwner: GeneratedOwner,
    ) {
        val semanticTargetType = semanticProp.generatedTargetType(graph)
        val generatedTargetType = generatedProp.generatedTargetType(graph)
        if (semanticTargetType == null || generatedTargetType == null) {
            javaField(semanticProp, generatedProp, generatedOwner)
            return
        }
        line()
        text(".")
        name(semanticProp.fetcherPropName())
        text("(")
        indent {
            type(fetcherType(semanticProp.flatImmutableTargetType()))
            text(".$")
            indent {
                javaFields(
                    semanticTargetType,
                    generatedTargetType,
                    generatedOwner.copy(currentTypeHasOccurrence = false),
                )
            }
        }
        line()
        text(")")
    }

    private fun LsiPoetCodeBuilder.kotlinHiddenField(
        semanticProp: DtoBaseProp,
        generatedProp: DtoBaseProp,
        generatedOwner: GeneratedOwner,
    ) {
        val semanticTargetType = semanticProp.generatedTargetType(graph)
        val generatedTargetType = generatedProp.generatedTargetType(graph)
        if (semanticTargetType == null || generatedTargetType == null) {
            kotlinField(semanticProp, generatedProp, generatedOwner)
            return
        }
        name(semanticProp.fetcherPropName())
        text(" {")
        indent {
            line()
            kotlinFields(
                semanticTargetType,
                generatedTargetType,
                generatedOwner.copy(currentTypeHasOccurrence = false),
            )
        }
        line()
        text("}")
        line()
    }

    private fun LsiPoetCodeBuilder.javaTargetMetadata(
        targetDtoType: LsiDeclaredType,
    ) {
        type(targetDtoType)
        text(".METADATA.getFetcher()")
    }

    private fun LsiPoetCodeBuilder.kotlinTargetMetadata(
        targetDtoType: LsiDeclaredType,
    ) {
        type(targetDtoType)
        text(".METADATA.fetcher")
    }

    private fun DtoBaseProp.targetDtoTypeOrNull(
        generatedOwner: GeneratedOwner,
    ): LsiDeclaredType? {
        targetTypeReference?.let { reference ->
            val generatedTypeName = JimmerDtoPoetTypeNames.reusableTarget(
                reference,
                batchRootDtoTypeNames,
            )
            return LsiDeclaredType(generatedTypeName?.typeId ?: reference.toLsiType().declarationId)
        }
        val targetType = generatedTargetType(graph) ?: return null
        val targetOccurrence = if (generatedOwner.currentTypeHasOccurrence) {
            directChildOccurrence(generatedOwner.typeName, targetType)
        } else {
            directChildOccurrenceOrNull(generatedOwner.typeName, targetType) ?: return null
        }
        return LsiDeclaredType(targetOccurrence.typeId)
    }

    private fun directChildOccurrence(
        ownerTypeName: LsiPoetTypeName,
        targetType: DtoType,
    ): LsiPoetTypeName = JimmerDtoPoetTypeNames.requireDirectChildOccurrence(
        ownerTypeName = ownerTypeName,
        targetTypeId = targetType.id,
        typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
    )

    private fun directChildOccurrenceOrNull(
        ownerTypeName: LsiPoetTypeName,
        targetType: DtoType,
    ): LsiPoetTypeName? = JimmerDtoPoetTypeNames.directChildOccurrenceOrNull(
        ownerTypeName = ownerTypeName,
        targetTypeId = targetType.id,
        typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
    )

    private fun GeneratedOwner.foldTarget(targetType: DtoType): GeneratedOwner {
        val targetOccurrence = if (currentTypeHasOccurrence) {
            directChildOccurrence(typeName, targetType)
        } else {
            directChildOccurrenceOrNull(typeName, targetType)
        }
        return if (targetOccurrence != null) {
            GeneratedOwner(targetOccurrence)
        } else {
            this
        }
    }

    private fun DtoType.immutableBaseType(): ImmutableType {
        val baseTypeId = requireNotNull(baseTypeId) {
            "DTO metadata fetcher type has no immutable base type: ${id.value}"
        }
        return immutableSchema.typesById.getValue(baseTypeId)
    }

    private fun DtoBaseProp.isId(): Boolean {
        val immutablePropId = baseProps.first().propId
        return immutableSchema.propsById.getValue(immutablePropId).primaryMapping == PrimaryMapping.ID
    }

    private fun DtoBaseProp.fetcherPropName(): String = baseProps.first().name

    private fun DtoBaseProp.configCodeBlock(): LsiPoetCodeBlock = toConfigPoetCodeBlock(
        targetLanguage = targetLanguage,
        graph = graph,
        immutableSchema = immutableSchema,
        workspace = workspace,
        configContractResolution = configContractResolution,
    )

    private fun DtoBaseProp.flatImmutableTargetType(): ImmutableType {
        val targetTypeId = requireNotNull(boundImmutableProp(graph, immutableSchema).targetTypeId) {
            "Frozen flat DTO property has no immutable target: ${id.value}"
        }
        return immutableSchema.typesById.getValue(targetTypeId)
    }

    private fun fetcherType(type: ImmutableType): LsiDeclaredType = LsiDeclaredType(
        generatedTopLevelPoetTypeName(type.packageName, "${type.simpleName}Fetcher").typeId
    )

    private companion object {
        const val NEW_FETCHER_PACKAGE = "org.babyfish.jimmer.sql.kt.fetcher"
    }

    private data class GeneratedOwner(
        val typeName: LsiPoetTypeName,
        val currentTypeHasOccurrence: Boolean = true,
    )
}
