package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.basePropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.defaultBranch
import site.addzero.lsi.jimmer.dto.foldPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.promotedPolymorphicRootPropOrNull
import site.addzero.lsi.jimmer.dto.typeBranchesInDeclarationOrder
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
        val rootTypeName = requireNotNull(batchRootTypeNames[rootType.id]) {
            "Frozen root DTO type has no batch generated name: ${rootType.id.value}"
        }
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
                val targetType = prop.generatedTargetType(graph) ?: continue
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
