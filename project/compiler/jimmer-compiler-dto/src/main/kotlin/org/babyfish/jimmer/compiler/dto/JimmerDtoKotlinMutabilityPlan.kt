package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerPlatform
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoKotlinMutability
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.effectiveKotlinMutabilityByRootTypeId

internal fun JimmerDtoRendererOptions.effectiveKspMutableByRootTypeId(
    platform: CompilerPlatform,
    graphs: List<DtoGraph>,
): Map<DtoTypeId, Boolean> {
    val result = sortedMapOf<DtoTypeId, Boolean>()
    graphs.forEach { graph ->
        val mutabilityByRootTypeId = if (platform == CompilerPlatform.KSP) {
            graph.effectiveKotlinMutabilityByRootTypeId(defaultKotlinMutability())
        } else {
            graph.rootTypeIds.associateWith { DtoKotlinMutability.IMMUTABLE }
        }
        mutabilityByRootTypeId.forEach { (rootTypeId, mutability) ->
            val previous = result.put(
                rootTypeId,
                mutability == DtoKotlinMutability.MUTABLE,
            )
            require(previous == null) {
                "DTO KSP renderer plan cannot contain duplicate root type ids: ${rootTypeId.value}"
            }
        }
    }
    return result.toMap()
}

private fun JimmerDtoRendererOptions.defaultKotlinMutability(): DtoKotlinMutability {
    return if (kspMutable) {
        DtoKotlinMutability.MUTABLE
    } else {
        DtoKotlinMutability.IMMUTABLE
    }
}
