package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
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
        typeName: LsiPoetTypeName,
    ) {
        require(graph.typesById[type.id] === type) {
            "Current frozen DTO type does not belong to its graph: ${type.id.value}"
        }
        val previous = typeNamesByTypeId.put(type.id, typeName)
        require(previous == null || previous == typeName) {
            "Frozen DTO type maps to conflicting generated names: " +
                "${previous?.canonicalName} and ${typeName.canonicalName}"
        }
    }
}
