package site.addzero.lsi.poet

enum class LsiTypeSpecKind {
    CLASS,
    INTERFACE,
    ENUM,
    OBJECT,
    ANNOTATION,
}

data class LsiTypeSpec(
    val name: String,
    val kind: LsiTypeSpecKind,
    val annotations: List<LsiAnnotationSpec> = emptyList(),
    val modifiers: Set<LsiModifier> = emptySet(),
    val superClass: LsiTypeName? = null,
    val superInterfaces: List<LsiTypeName> = emptyList(),
    val superTypes: List<LsiTypeName> = emptyList(),
    val typeVariables: List<LsiTypeVariableName> = emptyList(),
    val properties: List<LsiPropertySpec> = emptyList(),
    val callables: List<LsiCallableSpec> = emptyList(),
    val nestedTypes: List<LsiTypeSpec> = emptyList(),
    val originatingClassName: LsiClassName? = null,
)
