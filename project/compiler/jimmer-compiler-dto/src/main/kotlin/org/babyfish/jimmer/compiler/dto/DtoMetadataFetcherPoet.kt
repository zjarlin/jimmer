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
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiCodeFragment
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.generatedTopLevelClass
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

/** 将冻结 DTO 的 metadata fetcher 降低为两端共享的代码片段。 */
internal fun DtoType.toLsiMetadataFetcherPoetFragment(
    targetLanguage: LsiLanguage,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    configContractResolution: DtoConfigContractResolution,
    generatedDtoTypeName: LsiClass,
    generatedDtoTypeIdsByTypeName: Map<LsiClass, DtoTypeId>,
    batchRootDtoTypeNames: Map<DtoTypeId, LsiClass>,
): LsiCodeFragment {
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
    fragment: LsiCodeFragment,
    immutableSchema: ImmutableSchema,
    generatedDtoTypeIdsByTypeName: Map<LsiClass, DtoTypeId>,
): List<LsiClass> {
    val generatedFetcherTypeNames = immutableSchema.types.map { type ->
        generatedTopLevelClass(type.packageName, "${type.simpleName}Fetcher")
    }
    return toLsiClasses(
        typeIds = fragment.codeBlock.referencedTypeIds,
        additional = (
            DTO_CONFIG_RUNTIME_TYPE_NAMES +
                generatedFetcherTypeNames +
                generatedDtoTypeIdsByTypeName.keys
            ).distinctBy(LsiClass::id),
    )
}

private class MetadataFetcherPoetLowering(
    private val targetLanguage: LsiLanguage,
    private val graph: DtoGraph,
    private val immutableSchema: ImmutableSchema,
    private val workspace: LsiWorkspace,
    private val configContractResolution: DtoConfigContractResolution,
    private val generatedDtoTypeIdsByTypeName: Map<LsiClass, DtoTypeId>,
    private val batchRootDtoTypeNames: Map<DtoTypeId, LsiClass>,
) {

    init {
        require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
            "DTO metadata fetcher requires Java or Kotlin: $targetLanguage"
        }
    }

    fun lower(
        rootType: DtoType,
        generatedRootTypeName: LsiClass,
    ): LsiCodeFragment {
        val codeBlock = LsiCodeBlock.build {
            when (targetLanguage) {
                LsiLanguage.JAVA -> javaRoot(rootType, generatedRootTypeName)
                LsiLanguage.KOTLIN -> kotlinRoot(rootType, generatedRootTypeName)
                else -> error("DTO metadata fetcher requires Java or Kotlin: $targetLanguage")
            }
        }
        val imports = when (targetLanguage) {
            LsiLanguage.JAVA -> emptyList()
            LsiLanguage.KOTLIN -> listOf(
                LsiImport(rootType.immutableBaseType().packageName, "by")
            )
            else -> error("DTO metadata fetcher requires Java or Kotlin: $targetLanguage")
        }
        return LsiCodeFragment(codeBlock, imports)
    }

    private fun LsiCodeBuilder.javaRoot(
        rootType: DtoType,
        generatedRootTypeName: LsiClass,
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

    private fun LsiCodeBuilder.kotlinRoot(
        rootType: DtoType,
        generatedRootTypeName: LsiClass,
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
        generatedOwnerTypeName: LsiClass,
        block: (
            branchType: DtoType,
            generatedBranchType: DtoType,
            generatedBranchTypeName: LsiClass,
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

    private fun LsiCodeBuilder.javaFields(
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

    private fun LsiCodeBuilder.kotlinFields(
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

    private fun LsiCodeBuilder.javaField(
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

    private fun LsiCodeBuilder.kotlinField(
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

    private fun LsiCodeBuilder.javaHiddenField(
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

    private fun LsiCodeBuilder.kotlinHiddenField(
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

    private fun LsiCodeBuilder.javaTargetMetadata(
        targetDtoType: LsiDeclaredType,
    ) {
        type(targetDtoType)
        text(".METADATA.getFetcher()")
    }

    private fun LsiCodeBuilder.kotlinTargetMetadata(
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
            return LsiDeclaredType(generatedTypeName?.id ?: reference.toLsiType().declarationId)
        }
        val targetType = generatedTargetType(graph) ?: return null
        val targetOccurrence = if (generatedOwner.currentTypeHasOccurrence) {
            directChildOccurrence(generatedOwner.typeName, targetType)
        } else {
            directChildOccurrenceOrNull(generatedOwner.typeName, targetType) ?: return null
        }
        return LsiDeclaredType(targetOccurrence.id)
    }

    private fun directChildOccurrence(
        ownerTypeName: LsiClass,
        targetType: DtoType,
    ): LsiClass = JimmerDtoPoetTypeNames.requireDirectChildOccurrence(
        ownerTypeName = ownerTypeName,
        targetTypeId = targetType.id,
        typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
    )

    private fun directChildOccurrenceOrNull(
        ownerTypeName: LsiClass,
        targetType: DtoType,
    ): LsiClass? = JimmerDtoPoetTypeNames.directChildOccurrenceOrNull(
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

    private fun DtoBaseProp.configCodeBlock(): LsiCodeBlock = toConfigPoetCodeBlock(
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
        generatedTopLevelClass(type.packageName, "${type.simpleName}Fetcher").id
    )

    private companion object {
        const val NEW_FETCHER_PACKAGE = "org.babyfish.jimmer.sql.kt.fetcher"
    }

    private data class GeneratedOwner(
        val typeName: LsiClass,
        val currentTypeHasOccurrence: Boolean = true,
    )
}
