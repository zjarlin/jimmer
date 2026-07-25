package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoTypeInfo
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiWorkspace

/** 解析可复用 DTO 类型所对应的不可变类型和用途。 */
fun LsiWorkspace.resolveDtoTypeInfo(
    immutableSchema: ImmutableSchema,
    qualifiedName: String,
    targetLanguage: LsiLanguage,
): DtoTypeInfo? {
    val specificationTypeId = targetLanguage.specificationTypeId()
    val typeId = LsiSymbolId.type(qualifiedName)
    val declaration = this[typeId] as? LsiTypeDeclaration ?: return null
    require(declaration.typeParameters.isEmpty()) {
        "Reusable DTO type \"$qualifiedName\" cannot declare type parameters"
    }
    val markerTypes = listOf(
        INPUT_TYPE_ID to DtoTypeKind.INPUT,
        VIEW_TYPE_ID to DtoTypeKind.VIEW,
        specificationTypeId to DtoTypeKind.SPECIFICATION,
    )
    val typeSystem = LsiTypeSystem(this)
    val (markerType, kind) = markerTypes.firstNotNullOfOrNull { (markerTypeId, kind) ->
        typeSystem.resolveSuperType(typeId, markerTypeId)?.let { superType -> superType to kind }
    } ?: return null
    val baseTypeId = (markerType.arguments.firstOrNull()?.type as? LsiDeclaredType)?.declarationId
    val baseType = baseTypeId?.let(immutableSchema.typesById::get)
        ?: throw IllegalArgumentException(
            "The entity type argument of reusable DTO type \"$qualifiedName\" is not an immutable type",
        )
    return DtoTypeInfo(baseType.qualifiedName, kind)
}

private fun LsiLanguage.specificationTypeId(): LsiSymbolId {
    return when (this) {
        LsiLanguage.JAVA -> J_SPECIFICATION_TYPE_ID
        LsiLanguage.KOTLIN -> K_SPECIFICATION_TYPE_ID
        LsiLanguage.UNKNOWN -> throw IllegalArgumentException(
            "Reusable DTO type resolution requires Java or Kotlin target language",
        )
    }
}

private val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
private val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
private val J_SPECIFICATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")
private val K_SPECIFICATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")
