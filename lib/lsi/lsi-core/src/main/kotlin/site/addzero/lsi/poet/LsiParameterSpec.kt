package site.addzero.lsi.poet

data class LsiParameterSpec(
    val name: String,
    val type: LsiTypeName,
    val annotations: List<LsiAnnotationSpec> = emptyList(),
    val modifiers: Set<LsiModifier> = emptySet(),
    val defaultValue: LsiCodeBlock? = null,
)
