package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.targetTypeOf

/** 返回 Kotlin DTO 生成所需的全部 `by` 扩展包名。 */
fun DtoType.kotlinByImportPackages(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): Set<String> {
    val packages = sortedSetOf<String>()
    val visitedTypeIds = mutableSetOf<DtoTypeId>()

    fun visit(type: DtoType) {
        require(graph.typesById[type.id] == type) {
            "DTO type does not belong to this graph: ${type.id.value}"
        }
        if (!visitedTypeIds.add(type.id)) {
            return
        }
        val baseTypeId = requireNotNull(type.baseTypeId) {
            "Generated DTO type has no immutable base type: ${type.id.value}"
        }
        val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
            "Generated DTO immutable base type does not exist: ${baseTypeId.value}"
        }
        packages += baseType.packageName

        for (prop in type.basePropsInDeclarationOrder(graph)) {
            val generatedTargetType = prop.generatedTargetType(graph)
            if (generatedTargetType != null) {
                visit(generatedTargetType)
                continue
            }
            val basePropId = prop.baseProps.first().propId
            val baseProp = requireNotNull(immutableSchema.propsById[basePropId]) {
                "DTO property references a missing immutable property: ${basePropId.value}"
            }
            val targetType = immutableSchema.targetTypeOf(baseProp) ?: continue
            packages += targetType.packageName
        }
        for (prop in type.foldPropsInDeclarationOrder(graph)) {
            visit(prop.generatedTargetType(graph))
        }
        type.polymorphism?.let { polymorphism ->
            polymorphism.defaultBranch()?.let { branch -> visit(branch.bodyType(graph)) }
            polymorphism.typeBranchesInDeclarationOrder().forEach { branch ->
                visit(branch.bodyType(graph))
            }
        }
    }

    visit(this)
    return packages.toSet()
}
