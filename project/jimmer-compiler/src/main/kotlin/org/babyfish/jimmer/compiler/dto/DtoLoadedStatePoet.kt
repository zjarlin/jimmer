package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.poet.LsiPoetCodeBlock

internal fun DtoBaseProp.toBaseLoadedStateInitializerPoetCodeBlock(
    graph: DtoGraph,
    targetLanguage: LsiLanguage,
    accessorName: String,
    baseParameterName: String,
): LsiPoetCodeBlock? {
    dtoLoadedStateStorageNameOrNull(graph, targetLanguage) ?: return null
    return LsiPoetCodeBlock.build {
        name(accessorName)
        text(".")
        name("isLoaded")
        text("(")
        name(baseParameterName)
        text(")")
    }
}
