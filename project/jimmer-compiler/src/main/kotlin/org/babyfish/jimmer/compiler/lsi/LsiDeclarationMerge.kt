package org.babyfish.jimmer.compiler.lsi

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiProperty

internal fun Iterable<LsiDeclaration>.mergeDeclarationsById(): List<LsiDeclaration> {
    val declarationsById = linkedMapOf<LsiSymbolId, MutableList<LsiDeclaration>>()
    for (declaration in this) {
        declarationsById.getOrPut(declaration.id, ::mutableListOf) += declaration
    }
    return declarationsById.values.map(::mergeDeclarations)
}

private fun mergeDeclarations(declarations: List<LsiDeclaration>): LsiDeclaration {
    if (declarations.size == 1 || declarations.distinct().size == 1) {
        return declarations.first()
    }
    if (declarations.all { declaration -> declaration is LsiProperty }) {
        return mergeProperties(declarations.filterIsInstance<LsiProperty>())
    }
    error(
        "Cannot merge different LSI declarations with id '${declarations.first().id.value}': " +
            declarations.joinToString { declaration -> declaration::class.qualifiedName.orEmpty() }
    )
}

private fun mergeProperties(properties: List<LsiProperty>): LsiProperty {
    val preferred = properties.minWith(
        compareBy<LsiProperty>(
            { property -> property.getterPreference() },
            LsiProperty::getterName,
        )
    )
    properties.forEach { property ->
        require(property.ownerId == preferred.ownerId) {
            "Duplicate LSI property '${preferred.id.value}' has different owners"
        }
        require(property.type == preferred.type) {
            "Duplicate LSI property '${preferred.id.value}' has different types: " +
                "'${preferred.type}' and '${property.type}'"
        }
        require(property.mutable == preferred.mutable && property.static == preferred.static) {
            "Duplicate LSI property '${preferred.id.value}' has incompatible storage semantics"
        }
    }
    val overrides = properties
        .flatMap(LsiProperty::overrides)
        .groupBy(LsiOverride::declarationId)
        .map { (_, candidates) -> candidates.minBy(LsiOverride::distance) }
        .sortedWith(compareBy(LsiOverride::distance, LsiOverride::declarationId))
    return preferred.copy(
        documentation = properties.firstNotNullOfOrNull(LsiProperty::documentation),
        annotations = properties.flatMap(LsiProperty::annotations).distinct(),
        overrides = overrides,
    )
}

private fun LsiProperty.getterPreference(): Int {
    if (getterName == name) {
        return 0
    }
    if (getterName.startsWith("get") || getterName.startsWith("is")) {
        return 1
    }
    return 2
}
