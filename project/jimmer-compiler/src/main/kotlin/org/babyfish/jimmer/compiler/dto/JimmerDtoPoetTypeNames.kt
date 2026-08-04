package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.basePropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.defaultBranch
import site.addzero.lsi.jimmer.dto.foldPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.promotedPolymorphicRootPropOrNull
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.jimmer.dto.toLsiType
import site.addzero.lsi.jimmer.dto.typeBranchesInDeclarationOrder
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetTypeName

/** 使用显式包名和简单名链创建 DTO 生成声明的精确源码名称。 */
internal object JimmerDtoPoetTypeNames {

    /** 为一次 DTO 处理批次建立唯一的根声明名称索引。 */
    @JvmStatic
    fun roots(graphs: Collection<DtoGraph>): Map<DtoTypeId, LsiPoetTypeName> {
        val result = linkedMapOf<DtoTypeId, LsiPoetTypeName>()
        val typeIdsByCanonicalName = linkedMapOf<String, DtoTypeId>()
        graphs
            .flatMap { graph -> graph.rootTypeIds.map(graph.typesById::getValue) }
            .sortedBy(DtoType::id)
            .forEach { type ->
                val simpleName = requireNotNull(type.name) {
                    "Frozen root DTO type must have a generated name: ${type.id.value}"
                }
                val typeName = create(type.packageName, listOf(simpleName))
                require(result.put(type.id, typeName) == null) {
                    "Duplicate frozen root DTO type id: ${type.id.value}"
                }
                require(typeIdsByCanonicalName.put(typeName.canonicalName, type.id) == null) {
                    "Duplicate generated root DTO type name: ${typeName.canonicalName}"
                }
            }
        return result
    }

    /** 返回冻结根 DTO 在当前处理批次中已经规划的精确源码名称。 */
    @JvmStatic
    fun rootTypeName(
        rootType: DtoType,
        batchRootTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiPoetTypeName = requireNotNull(batchRootTypeNames[rootType.id]) {
        "Frozen root DTO type has no batch generated name: ${rootType.id.value}"
    }

    /** 为单个根 DTO 预计算本轮将生成的全部声明名称。 */
    @JvmStatic
    fun forRoot(
        graph: DtoGraph,
        rootType: DtoType,
        batchRootTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
    ): Map<LsiPoetTypeName, DtoTypeId> {
        require(graph.typesById[rootType.id] === rootType) {
            "Frozen root DTO type does not belong to its graph: ${rootType.id.value}"
        }
        require(rootType.id in graph.rootTypeIds) {
            "Frozen DTO type is not a graph root: ${rootType.id.value}"
        }
        val rootName = requireNotNull(rootType.name) {
            "Frozen root DTO type must have a generated name: ${rootType.id.value}"
        }
        val rootTypeName = rootTypeName(rootType, batchRootTypeNames)
        require(rootTypeName == create(rootType.packageName, listOf(rootName))) {
            "Frozen root DTO type has an unexpected batch generated name: ${rootTypeName.canonicalName}"
        }
        val typeIdsByTypeName = linkedMapOf<LsiPoetTypeName, DtoTypeId>()
        batchRootTypeNames.forEach { (typeId, typeName) ->
            val previousTypeId = typeIdsByTypeName.put(typeName, typeId)
            require(previousTypeId == null || previousTypeId == typeId) {
                "Generated root DTO name '${typeName.canonicalName}' is shared by frozen types " +
                    "'${previousTypeId?.value}' and '${typeId.value}'"
            }
        }
        return RootPlanner(graph, typeIdsByTypeName).plan(rootType, rootTypeName)
    }

    @JvmStatic
    fun create(
        packageName: String,
        simpleNames: List<String>,
    ): LsiPoetTypeName {
        val qualifiedName = buildList {
            if (packageName.isNotEmpty()) {
                add(packageName)
            }
            addAll(simpleNames)
        }.joinToString(".")
        return LsiPoetTypeName(
            typeId = LsiSymbolId.type(qualifiedName),
            packageName = packageName,
            simpleNames = simpleNames,
        )
    }

    /** 在单个生成位置内按稳定语义 ID 注册精确源码名称。 */
    @JvmStatic
    fun register(
        graph: DtoGraph,
        type: DtoType,
        typeNamesByTypeId: MutableMap<DtoTypeId, LsiPoetTypeName>,
        locallyRegisteredTypeIds: MutableSet<DtoTypeId>,
        typeName: LsiPoetTypeName,
    ) {
        require(graph.typesById[type.id] === type) {
            "Current frozen DTO type does not belong to its graph: ${type.id.value}"
        }
        val previous = typeNamesByTypeId[type.id]
        require(type.id !in locallyRegisteredTypeIds || previous == typeName) {
            "Frozen DTO type maps to conflicting generated names: " +
                "${previous?.canonicalName} and ${typeName.canonicalName}"
        }
        val canonicalConflict = typeNamesByTypeId.entries.firstOrNull { (typeId, existingName) ->
            typeId != type.id && existingName.canonicalName == typeName.canonicalName
        }
        require(canonicalConflict == null) {
            "Generated DTO canonical name '${typeName.canonicalName}' is already mapped to " +
                "frozen type '${canonicalConflict?.key?.value}'"
        }
        typeNamesByTypeId[type.id] = typeName
        locallyRegisteredTypeIds += type.id
    }

    /** 校验当前生成位置已由根 occurrence 索引精确规划。 */
    @JvmStatic
    fun requirePlanned(
        graph: DtoGraph,
        type: DtoType,
        typeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
        typeName: LsiPoetTypeName,
    ) {
        require(graph.typesById[type.id] === type) {
            "Current frozen DTO type does not belong to its graph: ${type.id.value}"
        }
        val plannedTypeId = typeIdsByTypeName[typeName]
        require(plannedTypeId == type.id) {
            "Generated DTO occurrence '${typeName.canonicalName}' is planned for " +
                "'${plannedTypeId?.value}', not '${type.id.value}'"
        }
    }

    /** 返回指定生成声明直属子级中的唯一目标 occurrence。 */
    @JvmStatic
    fun requireDirectChildOccurrence(
        ownerTypeName: LsiPoetTypeName,
        targetTypeId: DtoTypeId,
        typeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    ): LsiPoetTypeName {
        return requireNotNull(
            directChildOccurrenceOrNull(ownerTypeName, targetTypeId, typeIdsByTypeName)
        ) {
            "Generated DTO owner '${ownerTypeName.canonicalName}' must contain exactly one direct " +
                "occurrence of frozen type '${targetTypeId.value}'"
        }
    }

    /** 返回指定生成声明直属目标 occurrence 的最后一级简单名。 */
    @JvmStatic
    fun requireDirectChildSimpleName(
        ownerTypeName: LsiPoetTypeName,
        targetType: DtoType,
        typeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    ): String {
        return requireDirectChildOccurrence(
            ownerTypeName = ownerTypeName,
            targetTypeId = targetType.id,
            typeIdsByTypeName = typeIdsByTypeName,
        ).simpleNames.last()
    }

    /** 返回指定生成声明直属子级中的可选目标 occurrence。 */
    @JvmStatic
    fun directChildOccurrenceOrNull(
        ownerTypeName: LsiPoetTypeName,
        targetTypeId: DtoTypeId,
        typeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    ): LsiPoetTypeName? {
        val matches = typeIdsByTypeName.entries.filter { (typeName, typeId) ->
            typeId == targetTypeId &&
                typeName.packageName == ownerTypeName.packageName &&
                typeName.simpleNames.size == ownerTypeName.simpleNames.size + 1 &&
                typeName.simpleNames.dropLast(1) == ownerTypeName.simpleNames
        }
        require(matches.size <= 1) {
            "Generated DTO owner '${ownerTypeName.canonicalName}' contains multiple direct occurrences " +
                "of frozen type '${targetTypeId.value}'"
        }
        return matches.singleOrNull()?.key
    }

    /** 返回冻结 DTO 类型已经注册的精确生成名称。 */
    @JvmStatic
    fun requireRegistered(
        type: DtoType,
        typeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiPoetTypeName {
        return requireNotNull(typeNamesByTypeId[type.id]) {
            "Frozen DTO type has no registered generated name: ${type.id.value}"
        }
    }

    /** 返回可复用 DTO 目标在当前生成批次中的精确源码名称。 */
    @JvmStatic
    fun reusableTarget(
        reference: DtoReusableTypeReference,
        rootTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiPoetTypeName? {
        val matches = rootTypeNamesByTypeId.values.filter { typeName ->
            typeName.canonicalName == reference.qualifiedName
        }
        require(matches.size <= 1) {
            "Reusable DTO target has duplicate generated names: ${reference.qualifiedName}"
        }
        return matches.singleOrNull()
    }

    /** 将当前生成位置中的 DTO 属性目标解析为纯 LSI 声明类型。 */
    @JvmStatic
    fun toLsiGeneratedTargetType(
        graph: DtoGraph,
        prop: DtoProp,
        generatedOwnerTypeName: LsiPoetTypeName,
        generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiDeclaredType {
        val generatedTypeName = generatedTargetTypeNameOrNull(
            graph = graph,
            prop = prop,
            generatedOwnerTypeName = generatedOwnerTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames = batchRootDtoTypeNames,
        )
        if (generatedTypeName != null) {
            return LsiDeclaredType(generatedTypeName.typeId)
        }
        promotedRootPropAndOwnerOccurrenceOrNull(
            graph = graph,
            prop = prop,
            generatedOwnerTypeName = generatedOwnerTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        )?.let { (promotedProp, promotedOwnerTypeName) ->
            return toLsiGeneratedTargetType(
                graph = graph,
                prop = promotedProp,
                generatedOwnerTypeName = promotedOwnerTypeName,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                batchRootDtoTypeNames = batchRootDtoTypeNames,
            )
        }
        val reference = (prop as? DtoBaseProp)?.tailProp(graph)?.targetTypeReference
        return requireNotNull(reference) {
            "Frozen DTO property has no generated target type: ${prop.id.value}"
        }.toLsiType()
    }

    /** 返回当前生成位置中的精确目标名称；外部 reusable 类型交给 workspace 解析。 */
    @JvmStatic
    fun generatedTargetTypeNameOrNull(
        graph: DtoGraph,
        prop: DtoProp,
        generatedOwnerTypeName: LsiPoetTypeName,
        generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiPoetTypeName? {
        require(graph.propsById[prop.id] === prop) {
            "Frozen DTO property does not belong to its graph: ${prop.id.value}"
        }
        require(generatedDtoTypeIdsByTypeName[generatedOwnerTypeName] == prop.ownerTypeId) {
            "Generated DTO owner '${generatedOwnerTypeName.canonicalName}' is not an occurrence of " +
                "'${prop.ownerTypeId.value}' for property '${prop.id.value}'"
        }
        promotedRootPropAndOwnerOccurrenceOrNull(
            graph = graph,
            prop = prop,
            generatedOwnerTypeName = generatedOwnerTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        )?.let { (promotedProp, promotedOwnerTypeName) ->
            return generatedTargetTypeNameOrNull(
                graph = graph,
                prop = promotedProp,
                generatedOwnerTypeName = promotedOwnerTypeName,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                batchRootDtoTypeNames = batchRootDtoTypeNames,
            )
        }
        return when (prop) {
            is DtoBaseProp -> prop.generatedTargetTypeNameOrNull(
                graph = graph,
                generatedOwnerTypeName = generatedOwnerTypeName,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                batchRootDtoTypeNames = batchRootDtoTypeNames,
            )
            is DtoFoldProp -> generatedTargetTypeName(
                graph = graph,
                targetType = graph.typesById.getValue(prop.targetTypeId),
                generatedOwnerTypeName = generatedOwnerTypeName,
                generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            )
            is DtoUserProp -> throw IllegalArgumentException(
                "DTO user property has no generated target type: ${prop.id.value}",
            )
        }
    }

    private fun DtoBaseProp.generatedTargetTypeNameOrNull(
        graph: DtoGraph,
        generatedOwnerTypeName: LsiPoetTypeName,
        generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiPoetTypeName>,
    ): LsiPoetTypeName? {
        val targetProp = tailProp(graph)
        targetProp.targetTypeReference?.let { reference ->
            return reusableTarget(reference, batchRootDtoTypeNames)
        }
        val targetTypeId = requireNotNull(targetProp.targetTypeId) {
            "Frozen DTO base property has no generated target type: ${targetProp.id.value}"
        }
        val targetType = graph.typesById.getValue(targetTypeId)
        if (targetProp.recursive && !targetType.focusedRecursion) {
            return generatedOwnerTypeName
        }
        return generatedTargetTypeName(
            graph = graph,
            targetType = targetType,
            generatedOwnerTypeName = generatedOwnerTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        )
    }

    private fun generatedTargetTypeName(
        graph: DtoGraph,
        targetType: DtoType,
        generatedOwnerTypeName: LsiPoetTypeName,
        generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    ): LsiPoetTypeName {
        require(graph.typesById[targetType.id] === targetType) {
            "Frozen DTO target type does not belong to its graph: ${targetType.id.value}"
        }
        require(targetType.name == null) {
            "Generated nested DTO target must be anonymous: ${targetType.id.value}"
        }
        return requireDirectChildOccurrence(
            ownerTypeName = generatedOwnerTypeName,
            targetTypeId = targetType.id,
            typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
        )
    }

    private fun promotedRootPropAndOwnerOccurrenceOrNull(
        graph: DtoGraph,
        prop: DtoProp,
        generatedOwnerTypeName: LsiPoetTypeName,
        generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId>,
    ): Pair<DtoProp, LsiPoetTypeName>? {
        val polymorphicParents = graph.types.filter { candidate ->
            candidate.polymorphism?.branches?.any { branch ->
                branch.mergedTypeId == prop.ownerTypeId
            } == true
        }
        require(polymorphicParents.size <= 1) {
            "Frozen DTO type '${prop.ownerTypeId.value}' has multiple polymorphic parents"
        }
        val polymorphicParent = polymorphicParents.singleOrNull() ?: return null
        val promotedProp = polymorphicParent.promotedPolymorphicRootPropOrNull(graph, prop) ?: return null
        require(generatedOwnerTypeName.simpleNames.size > 1) {
            "Generated polymorphic DTO occurrence has no parent: ${generatedOwnerTypeName.canonicalName}"
        }
        val generatedParentTypeName = create(
            packageName = generatedOwnerTypeName.packageName,
            simpleNames = generatedOwnerTypeName.simpleNames.dropLast(1),
        )
        require(generatedDtoTypeIdsByTypeName[generatedParentTypeName] == polymorphicParent.id) {
            "Generated polymorphic parent '${generatedParentTypeName.canonicalName}' is not an occurrence of " +
                "'${polymorphicParent.id.value}'"
        }
        return promotedProp to generatedParentTypeName
    }

    private class RootPlanner(
        private val graph: DtoGraph,
        private val typeIdsByTypeName: MutableMap<LsiPoetTypeName, DtoTypeId>,
    ) {
        private val typeNamesByCanonicalName = linkedMapOf<String, LsiPoetTypeName>()

        init {
            typeIdsByTypeName.keys.forEach(::registerCanonicalName)
        }

        fun plan(
            rootType: DtoType,
            rootTypeName: LsiPoetTypeName,
        ): Map<LsiPoetTypeName, DtoTypeId> {
            visit(rootType, rootTypeName, null)
            return typeIdsByTypeName.toMap()
        }

        private fun visit(
            type: DtoType,
            typeName: LsiPoetTypeName,
            polymorphicRoot: DtoType?,
        ) {
            require(graph.typesById[type.id] === type) {
                "Current frozen DTO type does not belong to its graph: ${type.id.value}"
            }
            registerCanonicalName(typeName)
            val previousTypeId = typeIdsByTypeName.put(typeName, type.id)
            require(previousTypeId == null || previousTypeId == type.id) {
                "Generated DTO occurrence '${typeName.canonicalName}' is shared by frozen types " +
                    "'${previousTypeId?.value}' and '${type.id.value}'"
            }
            for (prop in type.basePropsInDeclarationOrder(graph)) {
                if (polymorphicRoot?.promotedPolymorphicRootPropOrNull(graph, prop) != null) {
                    continue
                }
                val targetType = prop.generatedTargetTypeForRegistration() ?: continue
                visitTarget(targetType, typeName, prop.name)
            }
            for (prop in type.foldPropsInDeclarationOrder(graph)) {
                if (polymorphicRoot?.promotedPolymorphicRootPropOrNull(graph, prop) != null) {
                    continue
                }
                visitTarget(prop.generatedTargetType(graph), typeName, prop.name)
            }
            type.polymorphism?.let { polymorphism ->
                polymorphism.defaultBranch()?.let { branch ->
                    visit(
                        branch.mergedType(graph),
                        nested(typeName, branch.className),
                        type,
                    )
                }
                for (branch in polymorphism.typeBranchesInDeclarationOrder()) {
                    visit(
                        branch.mergedType(graph),
                        nested(typeName, branch.className),
                        type,
                    )
                }
            }
        }

        private fun registerCanonicalName(typeName: LsiPoetTypeName) {
            val previousTypeName = typeNamesByCanonicalName.put(typeName.canonicalName, typeName)
            require(previousTypeName == null || previousTypeName == typeName) {
                "Generated DTO canonical name '${typeName.canonicalName}' has conflicting source structures"
            }
        }

        private fun visitTarget(
            targetType: DtoType,
            ownerTypeName: LsiPoetTypeName,
            propName: String,
        ) {
            require(targetType.name == null) {
                "Generated nested DTO type must be anonymous: ${targetType.id.value}"
            }
            val targetSimpleName = availableTargetSimpleName(
                "TargetOf_$propName",
                ownerTypeName.simpleNames,
            )
            visit(targetType, nested(ownerTypeName, targetSimpleName), null)
        }

        private fun DtoBaseProp.generatedTargetTypeForRegistration(): DtoType? {
            val targetType = generatedTargetType(graph)
            if (nextPropId != null) {
                val tailTargetType = tailProp(graph).generatedTargetType(graph)
                require(targetType?.id == tailTargetType?.id) {
                    "Frozen flattened DTO property '${id.value}' has different head and tail generated targets"
                }
            }
            return targetType
        }

        private fun nested(
            ownerTypeName: LsiPoetTypeName,
            simpleName: String,
        ): LsiPoetTypeName = create(
            ownerTypeName.packageName,
            ownerTypeName.simpleNames + simpleName,
        )

        private fun availableTargetSimpleName(
            targetSimpleName: String,
            ownerSimpleNames: List<String>,
        ): String {
            if (targetSimpleName !in ownerSimpleNames) {
                return targetSimpleName
            }
            for (index in 2..99) {
                val candidate = "${targetSimpleName}_$index"
                if (candidate !in ownerSimpleNames) {
                    return candidate
                }
            }
            error("Cannot allocate generated DTO target name below '${ownerSimpleNames.joinToString(".")}'")
        }
    }
}
