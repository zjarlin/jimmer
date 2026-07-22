package org.babyfish.jimmer.compiler.client

import org.babyfish.jimmer.compiler.dto.JimmerDtoPrecompiledSchema
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoBaseProp

internal fun JimmerDtoPrecompiledSchema.toClientDefinitionDocumentation(
    immutableSchema: ImmutableSchema,
): Map<LsiSymbolId, ClientDefinitionDocumentation> {
    val documentationByTypeId = linkedMapOf<LsiSymbolId, ClientDefinitionDocumentation>()
    documents.forEach { document ->
        val graph = document.graph
        graph.rootTypeIds.forEach { rootTypeId ->
            val type = graph.typesById.getValue(rootTypeId)
            val typeName = type.name ?: return@forEach
            val qualifiedName = if (type.packageName.isEmpty()) {
                typeName
            } else {
                "${type.packageName}.$typeName"
            }
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
