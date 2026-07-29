package site.addzero.lsi.poet

/**
 * 描述可嵌入既有源码文件的代码块及其显式导入依赖。
 */
data class LsiPoetCodeFragment(
    val codeBlock: LsiPoetCodeBlock,
    val imports: List<LsiPoetImport> = emptyList(),
) {
    init {
        require(imports.distinct() == imports) {
            "LSI Poet code fragment cannot contain duplicate explicit imports"
        }
    }
}
