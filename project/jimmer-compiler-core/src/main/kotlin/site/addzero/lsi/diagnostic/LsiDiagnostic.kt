package site.addzero.lsi.diagnostic

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId

enum class LsiDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * 可由任意编译器前端重新锚定并输出的诊断。
 */
data class LsiDiagnostic(
    val code: String,
    val severity: LsiDiagnosticSeverity,
    val message: String,
    val symbolId: LsiSymbolId? = null,
    val location: LsiLocation? = null,
    val details: Map<String, String> = emptyMap()
) {

    init {
        require(code.isNotBlank()) { "LSI diagnostic code cannot be blank" }
        require(code.none(Char::isWhitespace)) { "LSI diagnostic code cannot contain whitespace: '$code'" }
        require(message.isNotBlank()) { "LSI diagnostic message cannot be blank" }
    }
}
