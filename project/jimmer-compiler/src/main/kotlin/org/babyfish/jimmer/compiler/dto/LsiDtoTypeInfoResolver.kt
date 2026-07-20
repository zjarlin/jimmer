package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.dto.compiler.DtoTypeInfo
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeSystem

internal class LsiDtoTypeInfoResolver(
    private val registry: LsiDtoTypeRegistry,
    platform: CompilerPlatform,
) {
    private val typeSystem = LsiTypeSystem(registry.workspace)

    private val markerTypes = listOf(
        INPUT_TYPE_ID to DtoTypeKind.INPUT,
        VIEW_TYPE_ID to DtoTypeKind.VIEW,
        platform.specificationTypeId() to DtoTypeKind.SPECIFICATION,
    )

    fun resolve(qualifiedName: String): DtoTypeInfo<LsiDtoBaseType>? {
        val typeId = LsiSymbolId.type(qualifiedName)
        val (markerType, kind) = markerTypes.firstNotNullOfOrNull { (markerTypeId, kind) ->
            typeSystem.resolveSuperType(typeId, markerTypeId)?.let { superType -> superType to kind }
        } ?: return null
        val baseTypeId = (markerType.arguments.firstOrNull()?.type as? LsiDeclaredType)?.declarationId
        val baseType = baseTypeId?.let(registry::get)
            ?: throw IllegalArgumentException(
                "The entity type argument of reusable DTO type \"$qualifiedName\" is not an immutable type",
            )
        return DtoTypeInfo(baseType, kind)
    }
}

private fun CompilerPlatform.specificationTypeId(): LsiSymbolId {
    return when (this) {
        CompilerPlatform.APT -> J_SPECIFICATION_TYPE_ID
        CompilerPlatform.KSP -> K_SPECIFICATION_TYPE_ID
        CompilerPlatform.UNKNOWN -> throw IllegalArgumentException(
            "Reusable DTO type resolution requires APT or KSP platform",
        )
    }
}

private val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
private val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
private val J_SPECIFICATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")
private val K_SPECIFICATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")
