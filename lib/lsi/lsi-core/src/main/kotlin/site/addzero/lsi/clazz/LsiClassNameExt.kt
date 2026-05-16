package site.addzero.lsi.clazz

import site.addzero.lsi.poet.LsiClassName

fun LsiClass.toLsiClassName(
    nameTransformer: (String) -> String = { it },
    nullable: Boolean = false,
): LsiClassName {
    val simpleNames = simpleNames.toMutableList().takeIf { it.isNotEmpty() }
        ?: mutableListOf(requireNotNull(simpleName) { "LsiClass.simpleName must not be null" })
    val packageName = packageName.orEmpty()
    val index = simpleNames.lastIndex
    simpleNames[index] = nameTransformer(simpleNames[index])
    return LsiClassName(
        packageName = packageName,
        simpleNames = simpleNames.toList(),
        nullable = nullable
    )
}

fun LsiClass.toLsiNestedClassName(
    namesTransformer: (List<String>) -> List<String> = { it },
    nullable: Boolean = false,
): LsiClassName {
    val baseNames = simpleNames.takeIf { it.isNotEmpty() }
        ?: listOf(requireNotNull(simpleName) { "LsiClass.simpleName must not be null" })
    val packageName = packageName.orEmpty()
    val names = namesTransformer(baseNames)
    require(names.isNotEmpty()) { "namesTransformer must return at least one name" }
    return LsiClassName(
        packageName = packageName,
        simpleNames = names,
        nullable = nullable
    )
}
