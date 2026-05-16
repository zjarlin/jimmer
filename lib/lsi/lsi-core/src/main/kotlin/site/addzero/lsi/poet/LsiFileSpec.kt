package site.addzero.lsi.poet

data class LsiImportSpec(
    val packageName: String,
    val name: String,
    val alias: String? = null,
)

data class LsiFileSpec(
    val packageName: String,
    val name: String,
    val annotations: List<LsiAnnotationSpec> = emptyList(),
    val memberImports: List<LsiImportSpec> = emptyList(),
    val topLevelProperties: List<LsiPropertySpec> = emptyList(),
    val topLevelCallables: List<LsiCallableSpec> = emptyList(),
    val types: List<LsiTypeSpec> = emptyList(),
) {
    val qualifiedName: String
        get() = if (packageName.isBlank()) {
            name
        } else {
            "$packageName.$name"
        }
}
