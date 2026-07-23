package site.addzero.lsi.jimmer.client

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph

/** 将 DTO 根类型文档冻结为 Client 类型定义文档。 */
fun Iterable<DtoGraph>.toClientDefinitionDocumentation(
    immutableSchema: ImmutableSchema,
): Map<LsiSymbolId, ClientDefinitionDocumentation> {
    val documentationByTypeId = linkedMapOf<LsiSymbolId, ClientDefinitionDocumentation>()
    for (graph in this) {
        for (rootTypeId in graph.rootTypeIds) {
            val type = graph.typesById.getValue(rootTypeId)
            val typeName = type.name ?: continue
            val qualifiedName = type.packageName
                .takeIf(String::isNotEmpty)
                ?.let { packageName -> "$packageName.$typeName" }
                ?: typeName
            val propertyDocumentation = type.propIds.mapNotNull { propId ->
                val prop = graph.propsById.getValue(propId)
                val documentation = if (prop is DtoBaseProp) {
                    prop.dtoDocumentation ?: run {
                        val tailProp = graph.propsById.getValue(prop.tailPropId) as DtoBaseProp
                        tailProp.baseProps.firstNotNullOfOrNull { binding ->
                            immutableSchema.propsById[binding.propId]?.documentation
                        }
                    }
                } else {
                    prop.documentation
                }
                documentation?.let { value -> prop.name to value }
            }.toMap()
            val documentation = ClientDefinitionDocumentation(
                type = type.documentation,
                properties = propertyDocumentation,
            )
            val typeId = LsiSymbolId.type(qualifiedName)
            val previous = documentationByTypeId.putIfAbsent(typeId, documentation)
            require(previous == null || previous == documentation) {
                "DTO client documentation conflicts for '${typeId.value}'"
            }
        }
    }
    return documentationByTypeId
}
