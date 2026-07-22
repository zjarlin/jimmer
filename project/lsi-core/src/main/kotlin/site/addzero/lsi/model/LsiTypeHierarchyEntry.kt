package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 不依赖完整声明即可参与继承解析的类型层级骨架。
 */
data class LsiTypeHierarchyEntry(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val kind: LsiTypeDeclarationKind,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val directSuperTypes: List<LsiDeclaredType> = emptyList(),
    val source: LsiSource? = null,
    val isExternal: Boolean = true,
) {

    init {
        require(qualifiedName.isNotBlank()) { "LSI type hierarchy qualified name cannot be blank" }
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI type hierarchy entry cannot contain duplicate type parameter ids: ${id.value}"
        }
        require(directSuperTypes.none { superType -> superType.declarationId == id }) {
            "LSI type hierarchy entry cannot directly inherit itself: ${id.value}"
        }
        require(directSuperTypes.map(LsiDeclaredType::stableSignature).distinct().size == directSuperTypes.size) {
            "LSI type hierarchy entry cannot contain duplicate direct super types: ${id.value}"
        }
    }

    companion object {

        fun from(declaration: LsiTypeDeclaration): LsiTypeHierarchyEntry {
            return LsiTypeHierarchyEntry(
                id = declaration.id,
                qualifiedName = declaration.qualifiedName,
                kind = declaration.kind,
                typeParameters = declaration.typeParameters,
                directSuperTypes = declaration.superTypes.filterIsInstance<LsiDeclaredType>(),
                source = declaration.origin.source,
                isExternal = false,
            )
        }
    }
}
