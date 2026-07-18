package org.babyfish.jimmer.compiler.lsi

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeRef

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
        require(property.type.isCompatibleGetterType(preferred.type)) {
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
    if (
        getterName.startsWith("is") &&
        type.primitiveKind() == LsiPrimitiveKind.BOOLEAN
    ) {
        return 1
    }
    if (getterName.startsWith("get")) {
        return 2
    }
    if (getterName.startsWith("is")) {
        return 3
    }
    return 4
}

private fun LsiTypeRef.isCompatibleGetterType(other: LsiTypeRef): Boolean {
    if (this == other) {
        return true
    }
    val primitiveKind = primitiveKind() ?: return false
    return primitiveKind == other.primitiveKind()
}

private fun LsiTypeRef.primitiveKind(): LsiPrimitiveKind? {
    return when (this) {
        is LsiPrimitiveType -> kind
        is LsiDeclaredType -> BOXED_PRIMITIVE_KINDS[declarationId]
        else -> null
    }
}

private val BOXED_PRIMITIVE_KINDS = mapOf(
    LsiSymbolId.type("java.lang.Boolean") to LsiPrimitiveKind.BOOLEAN,
    LsiSymbolId.type("java.lang.Byte") to LsiPrimitiveKind.BYTE,
    LsiSymbolId.type("java.lang.Short") to LsiPrimitiveKind.SHORT,
    LsiSymbolId.type("java.lang.Integer") to LsiPrimitiveKind.INT,
    LsiSymbolId.type("java.lang.Long") to LsiPrimitiveKind.LONG,
    LsiSymbolId.type("java.lang.Character") to LsiPrimitiveKind.CHAR,
    LsiSymbolId.type("java.lang.Float") to LsiPrimitiveKind.FLOAT,
    LsiSymbolId.type("java.lang.Double") to LsiPrimitiveKind.DOUBLE,
    LsiSymbolId.type("java.lang.Void") to LsiPrimitiveKind.VOID,
)
