package org.babyfish.jimmer.compiler

/**
 * 描述编译功能异常在平台诊断中的稳定表示。
 */
data class CompilerFailureTranslation(
    val message: String,
    val annotationTypeName: String? = null,
    val rethrowWhenTargetMissing: Boolean = true,
)

/**
 * 由功能提供者按需实现，把领域异常转换为平台无关诊断信息。
 */
interface CompilerFailureTranslator {

    fun translateFailure(failure: Throwable): CompilerFailureTranslation?
}
